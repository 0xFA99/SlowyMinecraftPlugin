package dev.slowy.core.worth;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class WorthManager implements Lifecycle {

    public static final class SellHolder implements InventoryHolder {
        private @Nullable Inventory inventory;
        private boolean confirmed = false;

        public SellHolder(@Nullable Inventory inventory) {
            this.inventory = inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed) {
            this.confirmed = confirmed;
        }

        @Override
        public Inventory getInventory() {
            return Objects.requireNonNull(inventory, "inventory cannot be null");
        }
    }

    public record TotalResult(int itemCount, double totalEarnings) {}

    private final SlowyCore plugin;
    private final DatabaseManager databaseManager;
    private final double[] prices = new double[Material.values().length];
    private final ConcurrentHashMap<UUID, Boolean> worthSettings = new ConcurrentHashMap<>();

    public WorthManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        WorthPrices.populate(prices);
    }

    @Override
    public void onDisable() {
        worthSettings.clear();
    }

    public double getPrice(@Nullable Material material) {
        if (material == null) return 0.0;
        int ord = material.ordinal();
        return (ord >= 0 && ord < prices.length) ? prices[ord] : 0.0;
    }

    public double getPrice(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return 0.0;
        return getPrice(item.getType());
    }

    public boolean isWorthDisplayEnabled(UUID uuid) {
        return worthSettings.getOrDefault(uuid, CoreConfig.WORTH_DISPLAY_DEFAULT_ENABLED);
    }

    public boolean toggleWorthDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        boolean newState = !isWorthDisplayEnabled(uuid);
        worthSettings.put(uuid, newState);

        databaseManager.executeUpdateAsync(
                "INSERT INTO player_worth_settings (uuid, enabled, updated_at) VALUES (?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET enabled = excluded.enabled, updated_at = excluded.updated_at;",
                uuid.toString(), newState ? 1 : 0, System.currentTimeMillis()
        );

        syncInventory(player);
        return newState;
    }

    public void loadPlayerSettings(Player player) {
        UUID uuid = player.getUniqueId();
        databaseManager.supplyAsync(con -> {
            try (PreparedStatement ps = con.prepareStatement("SELECT enabled FROM player_worth_settings WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("enabled") == 1;
                    }
                }
            } catch (Exception e) {
                plugin.getSlf4jLogger().warn("Failed to load worth settings for {}: {}", player.getName(), e.getMessage());
            }
            return CoreConfig.WORTH_DISPLAY_DEFAULT_ENABLED;
        }).thenAccept(enabled -> {
            worthSettings.put(uuid, enabled);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    syncInventory(player);
                }
            });
        });
    }

    public void unloadPlayerSettings(UUID uuid) {
        worthSettings.remove(uuid);
    }

    public static String formatAmount(double val) {
        if (val == (long) val) {
            return String.format(Locale.US, "%,d", (long) val);
        } else {
            return String.format(Locale.US, "%,.2f", val);
        }
    }

    public static String formatMaterialName(@Nullable Material mat) {
        if (mat == null) return "";
        String name = mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                capitalize = true;
                sb.append(c);
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public ItemStack applyWorthDisplay(Player player, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item != null ? item : new ItemStack(Material.AIR);
        }

        if (!isWorthDisplayEnabled(player.getUniqueId())) {
            return stripWorthDisplay(item);
        }

        double unitPrice = getPrice(item.getType());
        if (unitPrice <= 0.0) {
            return stripWorthDisplay(item);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        int amount = item.getAmount();
        double total = unitPrice * amount;

        String rawFormat = amount > 1 ?
                CoreConfig.WORTH_FORMAT_MULTIPLE :
                CoreConfig.WORTH_FORMAT_SINGLE;

        String lineText = rawFormat
                .replace("%total%", formatAmount(total))
                .replace("%unit%", formatAmount(unitPrice))
                .replace("%amount%", String.valueOf(amount));

        Component worthComp = ColorUtils.parseItem(lineText);
        String plainTarget = PlainTextComponentSerializer.plainText().serialize(worthComp).trim().toLowerCase(Locale.ROOT);

        List<Component> currentLore = meta.lore();
        List<Component> newLore = new ArrayList<>();
        boolean alreadyMatches = false;

        if (currentLore != null && !currentLore.isEmpty()) {
            Component lastComp = currentLore.get(currentLore.size() - 1);
            String plainLast = PlainTextComponentSerializer.plainText().serialize(lastComp).trim().toLowerCase(Locale.ROOT);
            if (plainLast.equals(plainTarget) && currentLore.size() == 1) {
                return item;
            }

            for (Component line : currentLore) {
                String plain = PlainTextComponentSerializer.plainText().serialize(line).trim().toLowerCase(Locale.ROOT);
                if (!plain.startsWith("worth:")) {
                    newLore.add(line);
                }
            }
        }

        newLore.add(worthComp);
        meta.lore(newLore);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack stripWorthDisplay(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return item != null ? item : new ItemStack(Material.AIR);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return item;
        }

        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return item;
        }

        boolean removed = false;
        List<Component> cleaned = new ArrayList<>();
        for (Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line).trim().toLowerCase(Locale.ROOT);
            if (plain.startsWith("worth:")) {
                removed = true;
            } else {
                cleaned.add(line);
            }
        }

        if (removed) {
            meta.lore(cleaned.isEmpty() ? null : cleaned);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isSimilarIgnoringWorth(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        ItemStack strippedA = stripWorthDisplay(a.clone());
        ItemStack strippedB = stripWorthDisplay(b.clone());
        return strippedA.isSimilar(strippedB);
    }

    public void stripStorageForPickup(@Nullable Player player, @Nullable Material mat) {
        if (player == null || !player.isOnline() || mat == null) return;
        Inventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;

        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            if (stack != null && stack.getType() == mat) {
                ItemStack stripped = stripWorthDisplay(stack);
                if (stripped != stack) {
                    storage[i] = stripped;
                    changed = true;
                }
            }
        }

        if (changed) {
            inv.setStorageContents(storage);
        }
    }

    public boolean mergePlayerStorageStacks(@Nullable Player player) {
        if (player == null || !player.isOnline()) return false;

        Inventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean modified = false;

        for (int i = 0; i < storage.length; i++) {
            ItemStack stack1 = storage[i];
            if (stack1 == null || stack1.getType().isAir()) continue;

            int maxStack = stack1.getMaxStackSize();
            if (maxStack <= 1 || stack1.getAmount() >= maxStack) continue;

            ItemStack stripped1 = stripWorthDisplay(stack1.clone());

            for (int j = i + 1; j < storage.length; j++) {
                ItemStack stack2 = storage[j];
                if (stack2 == null || stack2.getType().isAir()) continue;

                ItemStack stripped2 = stripWorthDisplay(stack2.clone());

                if (stripped1.isSimilar(stripped2)) {
                    int space = maxStack - stack1.getAmount();
                    int move = Math.min(space, stack2.getAmount());

                    if (move > 0) {
                        stack1.setAmount(stack1.getAmount() + move);
                        int remaining = stack2.getAmount() - move;

                        if (remaining <= 0) {
                            storage[j] = null;
                            inv.setItem(j, null);
                        } else {
                            stack2.setAmount(remaining);
                            applyWorthDisplay(player, stack2);
                            storage[j] = stack2;
                            inv.setItem(j, stack2);
                        }

                        applyWorthDisplay(player, stack1);
                        storage[i] = stack1;
                        inv.setItem(i, stack1);
                        modified = true;

                        if (stack1.getAmount() >= maxStack) {
                            break;
                        }
                    }
                }
            }
        }

        return modified;
    }

    public void syncInventory(@Nullable Player player) {
        if (player == null || !player.isOnline()) return;

        boolean enabled = isWorthDisplayEnabled(player.getUniqueId());
        Inventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean modified = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && !stack.getType().isAir()) {
                ItemStack updated = enabled ? applyWorthDisplay(player, stack) : stripWorthDisplay(stack);
                if (updated != stack) {
                    contents[i] = updated;
                    modified = true;
                }
            }
        }

        if (modified) {
            inv.setContents(contents);
        }
    }

    public void clearInventory(@Nullable Player player) {
        if (player == null || !player.isOnline()) return;

        Inventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean modified = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && !stack.getType().isAir()) {
                ItemStack updated = stripWorthDisplay(stack);
                if (updated != stack) {
                    contents[i] = updated;
                    modified = true;
                }
            }
        }

        if (modified) {
            inv.setContents(contents);
        }
    }

    public void openSellGui(Player player) {
        if (!player.isOnline()) return;

        SellHolder holder = new SellHolder(null);
        Component initialTitle = ColorUtils.parse(String.format(CoreConfig.SELL_GUI_TITLE, "0"));
        Inventory inv = Bukkit.createInventory(holder, 54, initialTitle);
        holder.setInventory(inv);

        ItemStack cancelItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(ColorUtils.parseItem("<red>ᴄᴀɴᴄᴇʟ</red>"));
            cancelItem.setItemMeta(cancelMeta);
        }
        inv.setItem(CoreConfig.SELL_CANCEL_SLOT, cancelItem);

        ItemStack confirmItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.displayName(ColorUtils.parseItem("<green>ᴄᴏɴꜰɪʀᴍ</green>"));
            confirmItem.setItemMeta(confirmMeta);
        }
        inv.setItem(CoreConfig.SELL_CONFIRM_SLOT, confirmItem);

        player.openInventory(inv);
    }

    public TotalResult calculateTotal(Inventory inv) {
        int itemCount = 0;
        double totalEarnings = 0.0;

        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot == CoreConfig.SELL_CANCEL_SLOT || slot == CoreConfig.SELL_CONFIRM_SLOT) continue;
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            double unitPrice = getPrice(item.getType());
            if (unitPrice > 0.0) {
                int amount = item.getAmount();
                itemCount += amount;
                totalEarnings += unitPrice * amount;
            }
        }
        return new TotalResult(itemCount, totalEarnings);
    }

    @SuppressWarnings("deprecation")
    public void updateGuiTitle(Player player, @Nullable InventoryView view) {
        if (!player.isOnline() || view == null) return;
        Inventory top = view.getTopInventory();
        if (!(top.getHolder() instanceof SellHolder)) return;

        TotalResult result = calculateTotal(top);
        String formattedMoney = plugin.getEconomyManager().formatNicest(result.totalEarnings());
        String titleRaw = result.itemCount() > 0 ?
                String.format(CoreConfig.SELL_GUI_TITLE_WITH_COUNT, formattedMoney, result.itemCount()) :
                String.format(CoreConfig.SELL_GUI_TITLE, "0");

        try {
            view.setTitle(LegacyComponentSerializer.legacySection().serialize(ColorUtils.parse(titleRaw)));
        } catch (Throwable ignored) {}
    }

    public void handleSellGuiClose(Player player, @Nullable Inventory inv) {
        if (inv == null || !(inv.getHolder() instanceof SellHolder holder)) {
            return;
        }

        inv.setItem(CoreConfig.SELL_CANCEL_SLOT, null);
        inv.setItem(CoreConfig.SELL_CONFIRM_SLOT, null);

        if (!holder.isConfirmed()) {
            for (ItemStack item : inv.getContents()) {
                if (item == null || item.getType().isAir()) continue;
                var rem = player.getInventory().addItem(stripWorthDisplay(item));
                for (ItemStack leftover : rem.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            player.sendActionBar(ColorUtils.parse("<red>Sale cancelled.</red>"));
            syncInventory(player);
            return;
        }

        TotalResult result = calculateTotal(inv);
        List<ItemStack> unsellable = new ArrayList<>();

        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (getPrice(item.getType()) <= 0.0) {
                unsellable.add(stripWorthDisplay(item));
            }
        }

        for (ItemStack item : unsellable) {
            var rem = player.getInventory().addItem(item);
            for (ItemStack leftover : rem.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        if (result.itemCount() > 0) {
            plugin.getEconomyManager().deposit(player.getUniqueId(), result.totalEarnings());
            plugin.getEconomyManager().addTotalSold(player.getUniqueId(), result.totalEarnings(), result.itemCount());
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            } catch (Throwable ignored) {}

            String formattedMoney = plugin.getEconomyManager().formatNicest(result.totalEarnings());
            player.sendActionBar(ColorUtils.parse(
                    "<#39FF14>Sold <#FFE600>" + String.format(Locale.US, "%,d", result.itemCount()) +
                            " items</#FFE600> for <#39FF14>$" + formattedMoney + "</#39FF14>!</#39FF14>"
            ));
        }

        syncInventory(player);
    }
}

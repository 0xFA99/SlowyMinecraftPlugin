package dev.slowy.core.shop;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.utils.ColorUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.*;

@NullMarked
public final class ShopManager implements Lifecycle {

    public enum CurrencyType {
        MONEY,
        SHARD
    }

    public record ShopCategory(
            String key,
            Material material,
            String displayName,
            int slot,
            List<String> lore,
            String openMenu,
            boolean enabled,
            ItemStack cachedItem
    ) {}

    public record ShopItem(
            String key,
            String menuKey,
            Material material,
            String displayName,
            int slot,
            double pricePerUnit,
            CurrencyType currency,
            List<String> lore,
            String command,
            boolean giveItem,
            ItemStack cachedItem
    ) {}

    public record CategoryMenu(
            String key,
            String title,
            int size,
            Map<Integer, ShopItem> items,
            int backButtonSlot,
            ItemStack[] templateContents
    ) {}

    public static class ShopHolder implements InventoryHolder {
        public enum MenuType {
            MAIN,
            CATEGORY,
            PURCHASE
        }

        private final MenuType type;
        private final String menuKey;
        private final @Nullable ShopItem selectedItem;
        private int quantity;
        private @Nullable Inventory inventory;

        public ShopHolder(MenuType type, String menuKey, @Nullable ShopItem selectedItem, int quantity) {
            this.type = type;
            this.menuKey = menuKey;
            this.selectedItem = selectedItem;
            this.quantity = quantity;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return Objects.requireNonNull(inventory, "inventory cannot be null");
        }

        public MenuType getType() {
            return type;
        }

        public String getMenuKey() {
            return menuKey;
        }

        public @Nullable ShopItem getSelectedItem() {
            return selectedItem;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##");

    // Pre-cached static buttons for the purchase menu
    private static final ItemStack BTN_MINUS_10 = createStaticItem(Material.RED_STAINED_GLASS_PANE, "<red>-10</red>");
    private static final ItemStack BTN_MINUS_1  = createStaticItem(Material.RED_STAINED_GLASS_PANE, "<red>-1</red>");
    private static final ItemStack BTN_SET_1    = createStaticItem(Material.STONE_BUTTON, "<yellow>1x</yellow>");
    private static final ItemStack BTN_SET_64   = createStaticItem(Material.STONE_BUTTON, "<yellow>64x</yellow>");
    private static final ItemStack BTN_PLUS_1   = createStaticItem(Material.LIME_STAINED_GLASS_PANE, "<green>+1</green>");
    private static final ItemStack BTN_PLUS_10  = createStaticItem(Material.LIME_STAINED_GLASS_PANE, "<green>+10</green>");
    private static final ItemStack BTN_CANCEL   = createStaticItem(Material.BARRIER, "<red>ᴄᴀɴᴄᴇʟ</red>");
    private static final ItemStack BTN_CONFIRM  = createStaticItem(Material.EMERALD, "<green>ᴄᴏɴꜰɪʀᴍ</green>");
    private static final ItemStack BTN_BACK     = createStaticItem(Material.RED_STAINED_GLASS_PANE, "<red>ʙᴀᴄᴋ</red>");

    private final SlowyCore plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Map<String, CategoryMenu> categoryMenus = new LinkedHashMap<>();
    private String mainTitle = "ꜱᴇʀᴠᴇʀ ꜱʜᴏᴘ";
    private int mainSize = 27;
    private ItemStack[] mainTemplateContents = new ItemStack[27];

    public ShopManager(SlowyCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        reloadShop();
    }

    public synchronized void reloadShop() {
        categories.clear();
        categoryMenus.clear();

        FileConfiguration config = ShopCatalog.config();

        // 1. Muat Kategori Utama
        ConfigurationSection catSec = config.getConfigurationSection("CATEGORIES");
        if (catSec != null) {
            mainTitle = catSec.getString("MENU-TITLE", "ꜱᴇʀᴠᴇʀ ꜱʜᴏᴘ");
            mainSize = catSec.getInt("MENU-SIZE", 27);
            mainTemplateContents = new ItemStack[mainSize];

            for (String catKey : catSec.getKeys(false)) {
                if (catKey.equalsIgnoreCase("MENU-TITLE") || catKey.equalsIgnoreCase("MENU-SIZE")) continue;

                ConfigurationSection entry = catSec.getConfigurationSection(catKey);
                if (entry == null) continue;

                boolean enabled = entry.getBoolean("ENABLED", true);
                if (!enabled) continue;

                Material mat = Material.matchMaterial(entry.getString("MATERIAL", "CHEST"));
                if (mat == null) mat = Material.CHEST;

                String name = entry.getString("DISPLAY-NAME", catKey);
                int slot = entry.getInt("SLOT", 0);
                List<String> lore = entry.getStringList("LORE");
                String openMenu = entry.getString("OPEN-MENU", "").replace("{", "").replace("}", "").toUpperCase(Locale.ROOT);

                ItemStack cachedItem = createItem(mat, name, lore);
                if (slot >= 0 && slot < mainSize) {
                    mainTemplateContents[slot] = cachedItem;
                }

                categories.put(catKey.toUpperCase(Locale.ROOT), new ShopCategory(
                        catKey.toUpperCase(Locale.ROOT), mat, name, slot, lore, openMenu, enabled, cachedItem
                ));
            }
        }

        // 2. Muat Setiap Menu Kategori (END-MENU, NETHER-MENU, GEAR-MENU, FOOD-MENU, SHARD-MENU)
        for (String topKey : config.getKeys(false)) {
            if (topKey.equalsIgnoreCase("CATEGORIES") || topKey.equalsIgnoreCase("BACK-BUTTON") || topKey.equalsIgnoreCase("SHOP-GUI")) {
                continue;
            }

            ConfigurationSection menuSec = config.getConfigurationSection(topKey);
            if (menuSec == null) continue;

            String title = menuSec.getString("TITLE", topKey);
            int size = menuSec.getInt("SIZE", 27);
            int backSlot = menuSec.getInt("BACK-BUTTON-SLOT", size - 9);

            ItemStack[] templateContents = new ItemStack[size];
            if (backSlot >= 0 && backSlot < size) {
                templateContents[backSlot] = BTN_BACK;
            }

            Map<Integer, ShopItem> items = new HashMap<>();

            for (String itemKey : menuSec.getKeys(false)) {
                if (itemKey.equalsIgnoreCase("TITLE") || itemKey.equalsIgnoreCase("SIZE") ||
                        itemKey.equalsIgnoreCase("CURRENCY") || itemKey.equalsIgnoreCase("BACK-BUTTON-SLOT")) {
                    continue;
                }

                ConfigurationSection itemSec = menuSec.getConfigurationSection(itemKey);
                if (itemSec == null) continue;

                String matStr = itemSec.getString("MATERIAL", "DIRT");
                Material mat = Material.matchMaterial(matStr);
                if (mat == null) mat = Material.DIRT;

                String displayName = itemSec.getString("DISPLAY-NAME", mat.name());
                int slot = itemSec.getInt("SLOT", 0);
                double price = itemSec.getDouble("PRICE-PER-UNIT", 0.0);

                String currStr = itemSec.getString("CURRENCY", menuSec.getString("CURRENCY", "MONEY")).toUpperCase(Locale.ROOT);
                CurrencyType currency = currStr.contains("SHARD") ? CurrencyType.SHARD : CurrencyType.MONEY;

                List<String> lore = itemSec.getStringList("LORE");
                String command = itemSec.getString("COMMAND", "");
                boolean giveItem = itemSec.getBoolean("GIVE-ITEM", true);

                ItemStack cachedItem = createItem(mat, displayName, lore);
                if (slot >= 0 && slot < size) {
                    templateContents[slot] = cachedItem;
                }

                items.put(slot, new ShopItem(
                        itemKey, topKey.toUpperCase(Locale.ROOT), mat, displayName, slot, price, currency, lore, command, giveItem, cachedItem
                ));
            }

            categoryMenus.put(topKey.toUpperCase(Locale.ROOT), new CategoryMenu(
                    topKey.toUpperCase(Locale.ROOT), title, size, items, backSlot, templateContents
            ));
        }
    }

    public void openMainMenu(Player player) {
        if (!player.isOnline()) return;

        ShopHolder holder = new ShopHolder(ShopHolder.MenuType.MAIN, "CATEGORIES", null, 1);
        Inventory inv = Bukkit.createInventory(holder, mainSize, ColorUtils.parse(mainTitle));
        holder.setInventory(inv);
        inv.setContents(mainTemplateContents.clone());
        player.openInventory(inv);
    }

    public void openCategoryMenu(Player player, String menuKey) {
        if (!player.isOnline()) return;

        String key = menuKey.toUpperCase(Locale.ROOT);
        CategoryMenu menu = categoryMenus.get(key);
        if (menu == null && !key.endsWith("-MENU")) {
            menu = categoryMenus.get(key + "-MENU");
        }
        if (menu == null) {
            return;
        }

        ShopHolder holder = new ShopHolder(ShopHolder.MenuType.CATEGORY, menu.key(), null, 1);
        Inventory inv = Bukkit.createInventory(holder, menu.size(), ColorUtils.parse(menu.title()));
        holder.setInventory(inv);
        inv.setContents(menu.templateContents().clone());
        player.openInventory(inv);
    }

    public void openPurchaseMenu(Player player, ShopItem item, String returnMenuKey, int quantity) {
        if (!player.isOnline()) return;

        quantity = Math.max(1, Math.min(640, quantity));

        ShopHolder holder = new ShopHolder(ShopHolder.MenuType.PURCHASE, returnMenuKey, item, quantity);
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(ColorUtils.parse(item.displayName()));
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtils.parse(plainTitle));
        holder.setInventory(inv);

        // Pasang tombol kontrol statis (pre-cached)
        inv.setItem(10, BTN_MINUS_10);
        inv.setItem(11, BTN_MINUS_1);
        inv.setItem(12, BTN_SET_1);
        inv.setItem(14, BTN_SET_64);
        inv.setItem(15, BTN_PLUS_1);
        inv.setItem(16, BTN_PLUS_10);
        inv.setItem(21, BTN_CANCEL);
        inv.setItem(23, BTN_CONFIRM);

        // Render item preview in slot 13
        updatePurchasePreview(inv, player, item, quantity);
        player.openInventory(inv);
    }

    private void updatePurchasePreview(Inventory inv, Player player, ShopItem item, int quantity) {
        double totalCost = item.pricePerUnit() * quantity;
        boolean isMoney = item.currency() == CurrencyType.MONEY;
        String total = isMoney ? "&#39FF14$" + formatAmount(totalCost) : "&#FF00BD" + (long) totalCost + "★";
        String unit = isMoney ? "&#39FF14$" + formatAmount(item.pricePerUnit()) : "&#FF00BD" + (long) item.pricePerUnit() + "★";

        List<String> previewLore = new ArrayList<>(4);
        previewLore.add("");
        previewLore.add("&7" + quantity + "x &8(@ " + unit + "&8)");
        previewLore.add("&7ᴄᴏꜱᴛ: " + total);

        if (item.giveItem() && !canFitItems(player, item.material(), quantity)) {
            previewLore.add("&#FF0055⚠ Not enough inventory space!");
        }

        ItemStack previewItem = createItem(item.material(), item.displayName(), previewLore);
        ItemMeta meta = previewItem.getItemMeta();
        if (meta != null) {
            try {
                meta.setMaxStackSize(99);
                previewItem.setItemMeta(meta);
            } catch (Throwable ignored) {}
        }
        previewItem.setAmount(Math.min(99, Math.max(1, quantity)));
        inv.setItem(13, previewItem);
    }

    public void handleClick(Player player, int rawSlot, ClickType clickType, ShopHolder holder) {
        if (!player.isOnline()) return;

        if (holder.getType() == ShopHolder.MenuType.MAIN) {
            for (ShopCategory cat : categories.values()) {
                if (cat.slot() == rawSlot) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    openCategoryMenu(player, cat.openMenu());
                    return;
                }
            }
        } else if (holder.getType() == ShopHolder.MenuType.CATEGORY) {
            CategoryMenu menu = categoryMenus.get(holder.getMenuKey());
            if (menu == null) return;

            if (rawSlot == menu.backButtonSlot()) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                openMainMenu(player);
                return;
            }

            ShopItem item = menu.items().get(rawSlot);
            if (item != null) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                openPurchaseMenu(player, item, holder.getMenuKey(), 1);
            }
        } else if (holder.getType() == ShopHolder.MenuType.PURCHASE) {
            ShopItem item = holder.getSelectedItem();
            if (item == null) return;

            int qty = holder.getQuantity();
            int newQty = qty;
            float pitch = 1.0f;

            switch (rawSlot) {
                case 10 -> { // -10 / -64
                    newQty = Math.max(1, qty - (clickType.isRightClick() ? 64 : 10));
                    pitch = 0.9f;
                }
                case 11 -> { // -1
                    newQty = Math.max(1, qty - 1);
                    pitch = 0.9f;
                }
                case 12 -> { // set 1
                    newQty = 1;
                    pitch = 1.1f;
                }
                case 14 -> { // set 64
                    newQty = 64;
                    pitch = 1.1f;
                }
                case 15 -> { // +1
                    newQty = Math.min(640, qty + 1);
                    pitch = 1.2f;
                }
                case 16 -> { // +10 / +64
                    newQty = Math.min(640, qty + (clickType.isRightClick() ? 64 : 10));
                    pitch = 1.2f;
                }
                case 21 -> { // Cancel
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    openCategoryMenu(player, holder.getMenuKey());
                    return;
                }
                case 23 -> { // Confirm Buy
                    boolean success = executePurchase(player, item, qty);
                    if (success) {
                        openCategoryMenu(player, holder.getMenuKey());
                    }
                    return;
                }
                default -> {
                    return;
                }
            }

            holder.setQuantity(newQty);
            updatePurchasePreview(holder.getInventory(), player, item, newQty);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, pitch);
        }
    }

    public boolean executePurchase(Player player, ShopItem item, int quantity) {
        if (!player.isOnline() || quantity <= 0) return false;

        // 1. Check inventory space first for physical items
        if (item.giveItem() && !canFitItems(player, item.material(), quantity)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendActionBar(ColorUtils.parse("<red>⚠ Inventory is full!</red>"));
            return false;
        }

        // 2. Check and deduct currency
        double totalCost = item.pricePerUnit() * quantity;
        UUID uuid = player.getUniqueId();

        if (item.currency() == CurrencyType.MONEY) {
            double bal = plugin.getEconomyManager().getBalance(uuid);
            if (bal < totalCost) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendActionBar(ColorUtils.parse(
                        "<#FF0055>✖ Not enough money! Need <#39FF14>$" + formatAmount(totalCost) +
                                "</#39FF14> <dark_gray>(<gray>Have: <#39FF14>$" + formatAmount(bal) + "</#39FF14></gray>)</dark_gray></#FF0055>"
                ));
                return false;
            }
            if (!plugin.getEconomyManager().withdraw(uuid, totalCost)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }
            plugin.getEconomyManager().addTotalSpent(uuid, totalCost);
        } else {
            long shards = plugin.getEconomyManager().getShards(uuid);
            long shardCost = (long) totalCost;
            if (shards < shardCost) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendActionBar(ColorUtils.parse(
                        "<#FF0055>✖ Not enough shards! Need <#FF00BD>" + shardCost + "★</#FF00BD> <dark_gray>(<gray>Have: <#FF00BD>" + shards + "★</#FF00BD></gray>)</dark_gray></#FF0055>"
                ));
                return false;
            }
            if (!plugin.getEconomyManager().withdrawShards(uuid, shardCost)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }
        }

        // 3. Give item safely
        if (item.giveItem()) {
            int remaining = quantity;
            int maxStack = item.material().getMaxStackSize();
            while (remaining > 0) {
                int toAdd = Math.min(remaining, maxStack);
                player.getInventory().addItem(new ItemStack(item.material(), toAdd));
                remaining -= toAdd;
            }
        }

        // 4. Execute command if configured
        if (item.command() != null && !item.command().trim().isEmpty()) {
            String cmd = item.command()
                    .replace("{username}", player.getName())
                    .replace("{player}", player.getName())
                    .replace("{amount}", String.valueOf(quantity));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        // 5. Sound & success notification
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);

        String costStr = item.currency() == CurrencyType.MONEY ?
                "<#39FF14>$" + formatAmount(totalCost) + "</#39FF14>" :
                "<#FF00BD>" + (long) totalCost + " ★</#FF00BD>";

        player.sendActionBar(ColorUtils.parse(
                "<#39FF14>✔ +" + quantity + "x " + item.displayName() + " <dark_gray>|</dark_gray> <gray>Cost: </gray>" + costStr + "</#39FF14>"
        ));

        // Re-sync inventory if worth display is active
        if (plugin.getWorthManager() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getWorthManager().syncInventory(player);
                }
            }, 1L);
        }

        return true;
    }

    public static boolean canFitItems(Player player, Material material, int quantity) {
        if (quantity <= 0) return true;
        int maxStack = material.getMaxStackSize();
        int space = 0;

        ItemStack[] storage = player.getInventory().getStorageContents();
        for (ItemStack is : storage) {
            if (is == null || is.getType().isAir()) {
                space += maxStack;
            } else if (is.getType() == material && is.getAmount() < maxStack) {
                space += (maxStack - is.getAmount());
            }
            if (space >= quantity) {
                return true;
            }
        }
        return false;
    }

    public static String formatAmount(double val) {
        if (val == (long) val) {
            return String.format(Locale.US, "%,d", (long) val);
        } else {
            return MONEY_FORMAT.format(val);
        }
    }

    private static ItemStack createStaticItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(ColorUtils.parseItem(name));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createItem(Material mat, String name, @Nullable List<String> lore) {
        ItemStack item = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.displayName(ColorUtils.parseItem(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.lore(ColorUtils.parseItemLore(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public Map<String, ShopCategory> getCategories() {
        return Collections.unmodifiableMap(categories);
    }

    public Map<String, CategoryMenu> getCategoryMenus() {
        return Collections.unmodifiableMap(categoryMenus);
    }

    @Override
    public void onDisable() {
        categories.clear();
        categoryMenus.clear();
    }
}

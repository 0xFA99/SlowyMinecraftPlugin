package dev.slowy.core.auction;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@NullMarked
public final class AuctionManager implements Lifecycle {

    public enum HolderType {
        BROWSER, MY_LISTINGS, BUY_CONFIRM, SHULKER_PREVIEW, INSERT_ITEM, CONFIRM_LISTING
    }

    public static class AuctionHolder implements InventoryHolder {
        private final HolderType type;
        private int page;
        private @Nullable AuctionItem auctionItem;
        private final Map<Integer, Long> slotToAuctionId = new HashMap<>();
        private @Nullable Inventory inventory;

        public AuctionHolder(HolderType type, int page) {
            this.type = type;
            this.page = page;
        }

        public AuctionHolder(HolderType type, @Nullable AuctionItem auctionItem) {
            this.type = type;
            this.auctionItem = auctionItem;
        }

        public void setInventory(@Nullable Inventory inventory) { this.inventory = inventory; }

        @Override
        public Inventory getInventory() { return inventory != null ? inventory : Bukkit.createInventory(null, 9); }
        public HolderType getType() { return type; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public @Nullable AuctionItem getAuctionItem() { return auctionItem; }
        public Map<Integer, Long> getSlotToAuctionId() { return slotToAuctionId; }
    }

    public static class PendingListing {
        private final ItemStack item;
        private double price;
        private boolean confirmed;

        public PendingListing(ItemStack item) {
            this.item = item;
            this.price = 0.0;
            this.confirmed = false;
        }

        public ItemStack getItem() { return item; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    }

    private final SlowyCore plugin;
    private final DatabaseManager databaseManager;
    private final Map<Long, AuctionItem> activeAuctions = new ConcurrentHashMap<>();
    private final Map<UUID, PendingListing> pendingListings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> reservedSlots = new ConcurrentHashMap<>();

    // Player Preferences
    private final Map<UUID, String> playerSearchQueries = new ConcurrentHashMap<>();
    private final Map<UUID, AuctionCategory> playerCategories = new ConcurrentHashMap<>();
    private final Map<UUID, AuctionSort> playerSorts = new ConcurrentHashMap<>();

    public AuctionManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        loadData();
    }

    public double getListingFee() { return AuctionText.LISTING_FEE; }
    public double getMinPrice() { return AuctionText.MIN_PRICE; }
    public double getMaxPrice() { return AuctionText.MAX_PRICE; }
    public Set<Material> getBlockedMaterials() { return AuctionText.BLOCKED_MATERIALS; }
    public int getMaxListings() { return AuctionText.MAX_ACTIVE_LISTINGS; }

    public final synchronized void loadData() {
        activeAuctions.clear();
        reservedSlots.clear();
        String sql = "SELECT id, seller_uuid, seller_name, item_data, price, created_at, expires_at, status FROM auctions WHERE status = 'ACTIVE'";
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int loaded = 0;
            while (rs.next()) {
                long id = rs.getLong("id");
                UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
                String sellerName = rs.getString("seller_name");
                byte[] bytes = rs.getBytes("item_data");
                double price = rs.getDouble("price");
                long createdAt = rs.getLong("created_at");
                long expiresAt = rs.getLong("expires_at");

                ItemStack item = ItemStack.deserializeBytes(bytes);
                AuctionItem auction = new AuctionItem(id, sellerUuid, sellerName, item, price, createdAt, expiresAt, "ACTIVE", null, null, 0);
                activeAuctions.put(id, auction);
                loaded++;
            }
            plugin.getSlf4jLogger().info("[Auction] Loaded {} active auctions from database.", loaded);
        } catch (Exception e) {
            plugin.getSlf4jLogger().warn("[Auction] Failed to load auction data: {}", e.getMessage());
        }
    }

    public int getPlayerActiveCount(UUID uuid) {
        int count = reservedSlots.getOrDefault(uuid, 0);
        for (AuctionItem item : activeAuctions.values()) {
            if (item.getSellerUuid().equals(uuid)) count++;
        }
        return count;
    }

    // ==========================================
    // UNIFIED LISTING LOGIC
    // ==========================================

    public boolean listItem(Player player, @Nullable ItemStack itemToSell, double price) {
        if (itemToSell == null || itemToSell.isEmpty()) {
            player.sendMessage(ColorUtils.parse(AuctionText.MSG_HOLD_ITEM_TO_SELL));
            return false;
        }

        boolean success = processListing(player, itemToSell, price, false);
        if (success) {
            player.getInventory().setItemInMainHand(null);
        }
        return success;
    }

    public boolean confirmAndListPending(Player player) {
        PendingListing pending = pendingListings.remove(player.getUniqueId());
        if (pending == null || pending.getItem() == null || pending.getItem().isEmpty()) {
            openMyListings(player);
            return false;
        }

        pending.setConfirmed(true);
        boolean success = processListing(player, pending.getItem(), pending.getPrice(), true);
        if (!success) {
            refundItem(player, pending.getItem());
        }
        return success;
    }

    private boolean processListing(Player player, ItemStack itemToSell, double price, boolean fromPending) {
        if (AuctionText.BLOCKED_MATERIALS.contains(itemToSell.getType())) {
            player.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_ITEM_BLOCKED, itemToSell.getType().name())));
            return false;
        }

        String sym = plugin.getEconomyManager().getCurrencySymbol();
        if (price < AuctionText.MIN_PRICE) {
            player.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_MIN_PRICE, sym, plugin.getEconomyManager().formatNicest(AuctionText.MIN_PRICE))));
            return false;
        }

        if (price > AuctionText.MAX_PRICE) {
            player.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_MAX_PRICE, sym, plugin.getEconomyManager().formatNicest(AuctionText.MAX_PRICE))));
            return false;
        }

        if (getPlayerActiveCount(player.getUniqueId()) >= AuctionText.MAX_ACTIVE_LISTINGS) {
            player.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_SLOTS_FULL, AuctionText.MAX_ACTIVE_LISTINGS)));
            return false;
        }

        reservedSlots.merge(player.getUniqueId(), 1, Integer::sum);

        ItemStack clone = itemToSell.clone();
        byte[] itemBytes = clone.serializeAsBytes();
        long now = System.currentTimeMillis();
        long expiresAt = now + (30L * 86400L * 1000L); // 30 days buffer

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String ins = "INSERT INTO auctions (seller_uuid, seller_name, item_data, price, created_at, expires_at, status) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')";
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setBytes(3, itemBytes);
                ps.setDouble(4, price);
                ps.setLong(5, now);
                ps.setLong(6, expiresAt);
                ps.executeUpdate();

                long generatedId = -1;
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) generatedId = rs.getLong(1);
                }

                if (generatedId > 0) {
                    AuctionItem auction = new AuctionItem(generatedId, player.getUniqueId(), player.getName(), clone, price, now, expiresAt, "ACTIVE", null, null, 0);
                    activeAuctions.put(generatedId, auction);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_LIST_SUCCESS,
                                AuctionText.getItemFormattedName(clone), sym, plugin.getEconomyManager().formatNicest(price))));
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
                        if (fromPending) openMyListings(player);
                    });
                }
            } catch (SQLException e) {
                plugin.getSlf4jLogger().warn("[Auction] Failed to save auction: {}", e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> refundItem(player, clone));
            } finally {
                reservedSlots.computeIfPresent(player.getUniqueId(), (u, v) -> v > 1 ? v - 1 : null);
            }
        });

        return true;
    }

    // ==========================================
    // DIRECT BUYING & TRANSACTION LOGIC
    // ==========================================

    public synchronized void buyItem(Player buyer, long auctionId) {
        AuctionItem auction = activeAuctions.get(auctionId);
        if (auction == null || !"ACTIVE".equals(auction.getStatus())) {
            buyer.sendMessage(ColorUtils.parse(AuctionText.MSG_ITEM_NO_LONGER_AVAILABLE));
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            openAuctionBrowser(buyer, 1);
            return;
        }

        if (auction.getSellerUuid().equals(buyer.getUniqueId())) {
            buyer.sendMessage(ColorUtils.parse(AuctionText.MSG_CANNOT_BUY_OWN));
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        double price = auction.getPrice();
        String sym = plugin.getEconomyManager().getCurrencySymbol();
        if (!plugin.getEconomyManager().hasBalance(buyer.getUniqueId(), price)) {
            buyer.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_INSUFFICIENT_BALANCE, sym, plugin.getEconomyManager().formatNicest(price))));
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        // Database atomic state lock: enforce ACID concurrency check
        long soldNow = System.currentTimeMillis();
        String update = "UPDATE auctions SET status = 'SOLD', buyer_uuid = ?, buyer_name = ?, sold_at = ? WHERE id = ? AND status = 'ACTIVE'";
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(update)) {
            ps.setString(1, buyer.getUniqueId().toString());
            ps.setString(2, buyer.getName());
            ps.setLong(3, soldNow);
            ps.setLong(4, auctionId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                // Another transaction or cancellation already took the item
                activeAuctions.remove(auctionId);
                buyer.sendMessage(ColorUtils.parse(AuctionText.MSG_ITEM_NO_LONGER_AVAILABLE));
                buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                openAuctionBrowser(buyer, 1);
                return;
            }
        } catch (SQLException e) {
            plugin.getSlf4jLogger().error("[Auction] Database error acquiring state lock for auction {}: {}", auctionId, e.getMessage(), e);
            buyer.sendMessage(ColorUtils.parse("<red>Terjadi kesalahan saat memproses pembelian. Coba lagi nanti.</red>"));
            return;
        }

        // Deduct from buyer
        plugin.getEconomyManager().withdraw(buyer.getUniqueId(), price);
        plugin.getEconomyManager().addTotalSpent(buyer.getUniqueId(), price);

        // Remove from active list
        activeAuctions.remove(auctionId);
        auction.setStatus("SOLD");
        auction.setBuyerUuid(buyer.getUniqueId());
        auction.setBuyerName(buyer.getName());
        auction.setSoldAt(soldNow);

        // Directly deposit money to seller (works for both online & offline players)
        plugin.getEconomyManager().deposit(auction.getSellerUuid(), price);
        plugin.getEconomyManager().addTotalSold(auction.getSellerUuid(), price, 1);

        // Give item to buyer (drop at feet if full)
        ItemStack boughtItem = auction.getItem().clone();
        HashMap<Integer, ItemStack> leftover = buyer.getInventory().addItem(boughtItem);
        if (!leftover.isEmpty()) {
            for (ItemStack rem : leftover.values()) {
                buyer.getWorld().dropItemNaturally(buyer.getLocation(), rem);
            }
            buyer.sendMessage(ColorUtils.parse(AuctionText.MSG_INVENTORY_FULL_DROPPED));
        }

        buyer.closeInventory();
        buyer.sendActionBar(ColorUtils.parse(String.format(AuctionText.MSG_BUY_SUCCESS,
                AuctionText.getItemFormattedName(boughtItem), sym, plugin.getEconomyManager().formatNicest(price))));
        buyer.playSound(buyer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f);

        // Notify seller if online
        Player seller = Bukkit.getPlayer(auction.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_SELLER_NOTIFIED,
                    AuctionText.getItemFormattedName(boughtItem), buyer.getName(), sym, plugin.getEconomyManager().formatNicest(price))));
            seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
    }

    public synchronized void cancelListing(Player player, long auctionId) {
        AuctionItem auction = activeAuctions.get(auctionId);
        if (auction == null) {
            player.sendMessage(ColorUtils.parse(AuctionText.MSG_LIST_NOT_ACTIVE));
            openMyListings(player);
            return;
        }

        if (!auction.getSellerUuid().equals(player.getUniqueId()) && !player.hasPermission("slowycore.auction.admin")) {
            player.sendMessage(ColorUtils.parse("<red>Kamu tidak memiliki izin untuk membatalkan lelang ini!</red>"));
            return;
        }

        // Atomic DB state lock: ensure status is ACTIVE before cancelling
        String update = "UPDATE auctions SET status = 'CANCELLED' WHERE id = ? AND status = 'ACTIVE'";
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(update)) {
            ps.setLong(1, auctionId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                activeAuctions.remove(auctionId);
                player.sendMessage(ColorUtils.parse(AuctionText.MSG_LIST_NOT_ACTIVE));
                openMyListings(player);
                return;
            }
        } catch (SQLException e) {
            plugin.getSlf4jLogger().error("[Auction] Database error cancelling listing {}: {}", auctionId, e.getMessage(), e);
            player.sendMessage(ColorUtils.parse("<red>Database error occurred while cancelling listing.</red>"));
            return;
        }

        activeAuctions.remove(auctionId);
        auction.setStatus("CANCELLED");

        // Return item directly to seller's inventory (drop at feet if full)
        ItemStack item = auction.getItem().clone();
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack rem : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rem);
            }
            player.sendMessage(ColorUtils.parse(AuctionText.MSG_INVENTORY_FULL_DROPPED));
        } else {
            player.sendMessage(ColorUtils.parse(AuctionText.MSG_LIST_CANCELLED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.0f);
        }

        openMyListings(player);
    }

    // ==========================================
    // USER INTERFACE & GUIS
    // ==========================================

    public void openAuctionBrowser(Player player, int page) {
        UUID uuid = player.getUniqueId();
        AuctionCategory category = playerCategories.getOrDefault(uuid, AuctionCategory.ALL);
        AuctionSort sort = playerSorts.getOrDefault(uuid, AuctionSort.RECENTLY_LISTED);
        String searchQuery = playerSearchQueries.getOrDefault(uuid, "");

        List<AuctionItem> items = activeAuctions.values().stream()
                .filter(a -> "ACTIVE".equals(a.getStatus()))
                .filter(a -> category.matches(a.getItem()))
                .filter(a -> {
                    if (searchQuery.isEmpty()) return true;
                    String target = (a.getItem().getType().name() + " " +
                            AuctionText.getItemName(a.getItem()) + " " +
                            a.getSellerName()).toLowerCase(Locale.ROOT);
                    return target.contains(searchQuery);
                })
                .sorted(sort.getComparator())
                .collect(Collectors.toList());

        int totalItems = items.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / 45));
        page = Math.max(1, Math.min(page, maxPages));

        AuctionHolder holder = new AuctionHolder(HolderType.BROWSER, page);
        String title = String.format(AuctionText.TITLE_BROWSER, page);
        Inventory inv = Bukkit.createInventory(holder, 54, ColorUtils.parse(title));
        holder.setInventory(inv);

        int startIndex = (page - 1) * 45;
        int endIndex = Math.min(startIndex + 45, totalItems);

        for (int i = startIndex; i < endIndex; i++) {
            AuctionItem item = items.get(i);
            int slot = i - startIndex;
            inv.setItem(slot, item.buildDisplayItem(plugin, player));
            holder.getSlotToAuctionId().put(slot, item.getId());
        }

        // ==========================================
        // DONUT-SMP STYLE NAVIGATION BAR (Row 6)
        // ==========================================

        if (page > 1) {
            inv.setItem(45, createItem(Material.ARROW, AuctionText.BTN_PREVIOUS, null));
        }

        List<String> sortLore = new ArrayList<>();
        for (AuctionSort s : AuctionSort.values()) {
            sortLore.add((s == sort ? AuctionText.PREFIX_OPTION_ACTIVE : AuctionText.PREFIX_OPTION_INACTIVE) + s.getDisplayName());
        }
        inv.setItem(47, createItem(Material.CAULDRON, AuctionText.BTN_SORT, sortLore));

        List<String> filterLore = new ArrayList<>();
        for (AuctionCategory c : AuctionCategory.values()) {
            filterLore.add((c == category ? AuctionText.PREFIX_OPTION_ACTIVE : AuctionText.PREFIX_OPTION_INACTIVE) + c.getDisplayName());
        }
        inv.setItem(48, createItem(Material.HOPPER, AuctionText.BTN_FILTER, filterLore));

        inv.setItem(49, createItem(Material.ANVIL, AuctionText.BTN_REFRESH, AuctionText.LORE_REFRESH));

        List<String> searchLore = new ArrayList<>(AuctionText.LORE_SEARCH);
        if (!searchQuery.isEmpty()) {
            searchLore.add("");
            searchLore.add(String.format(AuctionText.LORE_SEARCH_ACTIVE, searchQuery));
            searchLore.add(AuctionText.LORE_SEARCH_CLEAR_HINT);
        }
        inv.setItem(50, createItem(Material.SPYGLASS, AuctionText.BTN_SEARCH, searchLore));

        inv.setItem(51, createItem(Material.CHEST, AuctionText.BTN_YOUR_ITEMS, null));

        if (page < maxPages && totalItems > page * 45) {
            inv.setItem(53, createItem(Material.ARROW, AuctionText.BTN_NEXT, null));
        }

        player.openInventory(inv);
    }

    public void openSearchDialog(Player player) {
        player.closeInventory();
        String currentQuery = playerSearchQueries.getOrDefault(player.getUniqueId(), "");

        TextDialogInput searchInput = DialogInput.text("search_query", Component.text(AuctionText.DIALOG_SEARCH_PLACEHOLDER))
                .initial(currentQuery)
                .maxLength(32)
                .build();

        ActionButton searchBtn = ActionButton.builder(Component.text(AuctionText.DIALOG_SEARCH_BTN, NamedTextColor.GREEN))
                .action(DialogAction.commandTemplate("/auction dosearch $(search_query)"))
                .build();

        ActionButton cancelBtn = ActionButton.builder(Component.text(AuctionText.DIALOG_CANCEL_BTN, NamedTextColor.RED))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/auction")))
                .build();

        DialogBase base = DialogBase.builder(Component.text(AuctionText.TITLE_SEARCH_DIALOG))
                .body(List.of(DialogBody.plainMessage(Component.text(AuctionText.DIALOG_SEARCH_BODY, NamedTextColor.GRAY))))
                .inputs(List.of(searchInput))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(searchBtn, cancelBtn))
                .columns(2)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    public void handleSearchSubmit(Player player, @Nullable String query) {
        if (query == null || query.trim().isEmpty() || "__CLEAR__".equalsIgnoreCase(query.trim())) {
            playerSearchQueries.remove(player.getUniqueId());
        } else {
            String clean = query.trim().toLowerCase(Locale.ROOT);
            playerSearchQueries.put(player.getUniqueId(), clean);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
        }
        openAuctionBrowser(player, 1);
    }

    // ==========================================
    // YOUR ITEMS (18 SLOTS - 2 ROWS, NO PAGING)
    // ==========================================

    public void openMyListings(Player player) {
        UUID uuid = player.getUniqueId();
        List<AuctionItem> myList = activeAuctions.values().stream()
                .filter(a -> a.getSellerUuid().equals(uuid) && "ACTIVE".equals(a.getStatus()))
                .sorted(Comparator.comparingLong(AuctionItem::getCreatedAt).reversed())
                .limit(AuctionText.MAX_ACTIVE_LISTINGS)
                .collect(Collectors.toList());

        AuctionHolder holder = new AuctionHolder(HolderType.MY_LISTINGS, 1);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtils.parse(AuctionText.TITLE_YOUR_ITEMS));
        holder.setInventory(inv);

        // Slot 0: Sell Item (Paper)
        inv.setItem(0, createItem(Material.PAPER, AuctionText.BTN_SELL_ITEM, null));

        // Slots 1 to 17: Player active items
        for (int i = 0; i < myList.size(); i++) {
            AuctionItem item = myList.get(i);
            int slot = i + 1;
            inv.setItem(slot, item.buildMyListingDisplay(plugin));
            holder.getSlotToAuctionId().put(slot, item.getId());
        }

        // Slot 18 (Row 3): Back navigation button to browser
        inv.setItem(18, createItem(Material.ARROW, AuctionText.BTN_BACK, null));

        player.openInventory(inv);
    }

    // ==========================================
    // HOPPER GUI: INSERT ITEM
    // ==========================================

    public void openInsertItemGui(Player player) {
        cancelPendingListing(player);

        if (getPlayerActiveCount(player.getUniqueId()) >= AuctionText.MAX_ACTIVE_LISTINGS) {
            player.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_SLOTS_FULL, AuctionText.MAX_ACTIVE_LISTINGS)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        AuctionHolder holder = new AuctionHolder(HolderType.INSERT_ITEM, 1);
        Inventory inv = Bukkit.createInventory(holder, InventoryType.HOPPER, ColorUtils.parse(AuctionText.TITLE_INSERT_ITEM));
        holder.setInventory(inv);

        // Slot 0: Red glass pane (Back to Your Items)
        inv.setItem(0, createItem(Material.RED_STAINED_GLASS_PANE, AuctionText.BTN_BACK, null));

        // Slots 1 & 3: Filler
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        inv.setItem(1, filler);
        inv.setItem(3, filler);

        // Slot 4: Lime glass pane (Confirm)
        inv.setItem(4, createItem(Material.LIME_STAINED_GLASS_PANE, AuctionText.BTN_CONFIRM, null));

        player.openInventory(inv);
    }

    // ==========================================
    // PRICE INPUT DIALOG
    // ==========================================

    public void openPriceInputDialog(Player player, @Nullable String errorMessage) {
        PendingListing pending = pendingListings.get(player.getUniqueId());
        if (pending == null || pending.getItem() == null) {
            openMyListings(player);
            return;
        }

        ItemStack item = pending.getItem();

        TextDialogInput priceInput = DialogInput.text("price", Component.text(AuctionText.DIALOG_PRICE_PLACEHOLDER))
                .maxLength(20)
                .build();

        ActionButton confirmBtn = ActionButton.builder(Component.text(AuctionText.DIALOG_PRICE_CONTINUE_BTN, NamedTextColor.GREEN))
                .action(DialogAction.commandTemplate("/auction setprice $(price)"))
                .build();

        ActionButton cancelBtn = ActionButton.builder(Component.text(AuctionText.BTN_CANCEL, NamedTextColor.RED))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/auction cancelpending")))
                .build();

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.plainMessage(Component.text(String.format(AuctionText.DIALOG_PRICE_SELLING, AuctionText.getItemFormattedName(item)), NamedTextColor.GOLD)));
        bodies.add(DialogBody.plainMessage(Component.text(String.format(AuctionText.DIALOG_PRICE_LIMITS,
                plugin.getEconomyManager().formatNicest(AuctionText.MIN_PRICE), plugin.getEconomyManager().formatNicest(AuctionText.MAX_PRICE)), NamedTextColor.GRAY)));

        if (errorMessage != null && !errorMessage.isEmpty()) {
            bodies.add(DialogBody.plainMessage(Component.text("✖ " + errorMessage, NamedTextColor.RED)));
        }

        DialogBase base = DialogBase.builder(Component.text(AuctionText.TITLE_PRICE_DIALOG))
                .body(bodies)
                .inputs(List.of(priceInput))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(confirmBtn, cancelBtn))
                .columns(2)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    // ==========================================
    // CONFIRM LISTING GUI
    // ==========================================

    public void openConfirmListingGui(Player player, double price) {
        PendingListing pending = pendingListings.get(player.getUniqueId());
        if (pending == null || pending.getItem() == null) {
            openMyListings(player);
            return;
        }
        pending.setPrice(price);

        AuctionHolder holder = new AuctionHolder(HolderType.CONFIRM_LISTING, 1);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtils.parse(AuctionText.TITLE_CONFIRM_LISTING));
        holder.setInventory(inv);

        String sym = plugin.getEconomyManager().getCurrencySymbol();
        String formattedPrice = plugin.getEconomyManager().formatNicest(price);

        // Slot 11: Red glass (Cancel)
        inv.setItem(11, createItem(Material.RED_STAINED_GLASS_PANE, AuctionText.BTN_CANCEL, null));

        // Slot 13: Item display
        ItemStack displayItem = pending.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();
        List<Component> lore = meta != null && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(ColorUtils.parseItem(String.format(AuctionText.DESC_CONFIRM_LISTING_ITEM, sym, formattedPrice)));
        if (meta != null) {
            meta.lore(lore);
            displayItem.setItemMeta(meta);
        }
        inv.setItem(13, displayItem);

        // Slot 15: Lime glass (Confirm)
        inv.setItem(15, createItem(Material.LIME_STAINED_GLASS_PANE, AuctionText.BTN_CONFIRM, null));

        player.openInventory(inv);
    }

    public void startPendingListing(Player player, ItemStack item) {
        cancelPendingListing(player);
        pendingListings.put(player.getUniqueId(), new PendingListing(item));
    }

    public void cancelPendingListing(Player player) {
        PendingListing pending = pendingListings.remove(player.getUniqueId());
        if (pending != null && !pending.isConfirmed() && pending.getItem() != null) {
            refundItem(player, pending.getItem());
        }
    }

    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        playerSearchQueries.remove(uuid);
        playerCategories.remove(uuid);
        playerSorts.remove(uuid);
        PendingListing pending = pendingListings.remove(uuid);
        if (pending != null && !pending.isConfirmed() && pending.getItem() != null) {
            refundItem(player, pending.getItem());
        }
    }

    public void refundItem(Player player, @Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return;
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack rem : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rem);
            }
            player.sendMessage(ColorUtils.parse(AuctionText.MSG_INVENTORY_FULL_DROPPED));
        }
    }

    public static @Nullable Double parsePrice(@Nullable String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase(Locale.ROOT).replace("$", "").replace(",", ".").replace("_", "");
        if (s.isEmpty()) return null;

        double multiplier = 1.0;
        if (s.endsWith("k")) {
            multiplier = 1_000.0;
            s = s.substring(0, s.length() - 1).trim();
        } else if (s.endsWith("m")) {
            multiplier = 1_000_000.0;
            s = s.substring(0, s.length() - 1).trim();
        } else if (s.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            s = s.substring(0, s.length() - 1).trim();
        }

        try {
            double val = Double.parseDouble(s);
            if (Double.isNaN(val) || Double.isInfinite(val) || val <= 0) return null;
            return val * multiplier;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void openBuyConfirm(Player player, AuctionItem item) {
        AuctionHolder holder = new AuctionHolder(HolderType.BUY_CONFIRM, item);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtils.parse(AuctionText.TITLE_BUY_CONFIRM));
        holder.setInventory(inv);

        // Slot 11: Red stained glass pane (Cancel)
        inv.setItem(11, createItem(Material.RED_STAINED_GLASS_PANE, AuctionText.BTN_CANCEL, AuctionText.LORE_BUY_CANCEL));

        // Slot 13: Item display
        inv.setItem(13, item.buildDisplayItem(plugin, player));

        // Slot 15: Green stained glass pane (Confirm)
        inv.setItem(15, createItem(Material.LIME_STAINED_GLASS_PANE, AuctionText.BTN_BUY_CONFIRM, AuctionText.LORE_BUY_CONFIRM));

        player.openInventory(inv);
    }

    public void openShulkerPreview(Player player, @Nullable ItemStack shulkerItem) {
        if (shulkerItem == null || !(shulkerItem.getItemMeta() instanceof BlockStateMeta bsm)) {
            player.sendMessage(ColorUtils.parse(AuctionText.MSG_NOT_A_SHULKER));
            return;
        }

        if (!(bsm.getBlockState() instanceof ShulkerBox shulker)) {
            player.sendMessage(ColorUtils.parse(AuctionText.MSG_NOT_A_SHULKER));
            return;
        }

        AuctionHolder holder = new AuctionHolder(HolderType.SHULKER_PREVIEW, 1);
        String title = String.format(AuctionText.TITLE_SHULKER_PREVIEW, AuctionText.getItemName(shulkerItem));
        Inventory inv = Bukkit.createInventory(holder, 36, ColorUtils.parse(title));
        holder.setInventory(inv);

        ItemStack[] contents = shulker.getInventory().getContents();
        for (int i = 0; i < Math.min(27, contents.length); i++) {
            if (contents[i] != null) inv.setItem(i, contents[i].clone());
        }

        inv.setItem(31, createItem(Material.ARROW, AuctionText.BTN_SHULKER_CLOSE, null));
        player.openInventory(inv);
    }

    public void cycleCategory(Player player) {
        UUID uuid = player.getUniqueId();
        AuctionCategory current = playerCategories.getOrDefault(uuid, AuctionCategory.ALL);
        playerCategories.put(uuid, current.next());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
        openAuctionBrowser(player, 1);
    }

    public void cycleSort(Player player) {
        UUID uuid = player.getUniqueId();
        AuctionSort current = playerSorts.getOrDefault(uuid, AuctionSort.RECENTLY_LISTED);
        playerSorts.put(uuid, current.next());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
        openAuctionBrowser(player, 1);
    }

    public void clearSearch(Player player) {
        playerSearchQueries.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.0f);
        openAuctionBrowser(player, 1);
    }

    public void clearSearchQuery(UUID uuid) {
        playerSearchQueries.remove(uuid);
    }

    public @Nullable AuctionItem getAuction(long id) {
        return activeAuctions.get(id);
    }

    private ItemStack createItem(Material mat, String name, @Nullable List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.parseItem(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(ColorUtils.parseItemLore(loreLines));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void onDisable() {
        for (Map.Entry<UUID, PendingListing> entry : pendingListings.entrySet()) {
            PendingListing p = entry.getValue();
            if (p != null && !p.isConfirmed() && p.getItem() != null) {
                Player pl = Bukkit.getPlayer(entry.getKey());
                if (pl != null && pl.isOnline()) {
                    refundItem(pl, p.getItem());
                }
            }
        }
        pendingListings.clear();
        activeAuctions.clear();
        reservedSlots.clear();
    }

    @Override
    public void onReload() {
        loadData();
    }
}

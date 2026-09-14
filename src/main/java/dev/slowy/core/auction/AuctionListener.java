package dev.slowy.core.auction;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public class AuctionListener implements Listener {

    private final SlowyCore plugin;
    private final AuctionManager manager;

    public AuctionListener(SlowyCore plugin, AuctionManager manager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof AuctionManager.AuctionHolder holder)) return;

        // Cegah hotbar number key & offhand swap jika berinteraksi dengan top inventory yang bukan slot 2
        if (event.getAction() == InventoryAction.HOTBAR_SWAP) {
            if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                if (holder.getType() != AuctionManager.HolderType.INSERT_ITEM || event.getRawSlot() != 2) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (holder.getType() == AuctionManager.HolderType.INSERT_ITEM) {
            handleInsertItemClick(player, event);
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().isEmpty()) return;

        switch (holder.getType()) {
            case BROWSER -> handleBrowserClick(player, holder, slot, event.getClick());
            case MY_LISTINGS -> handleMyListingsClick(player, holder, slot);
            case BUY_CONFIRM -> handleBuyConfirmClick(player, holder, slot);
            case CONFIRM_LISTING -> handleConfirmListingClick(player, slot);
            case SHULKER_PREVIEW -> handleShulkerPreviewClick(player, slot);
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AuctionManager.AuctionHolder holder) {
            if (holder.getType() == AuctionManager.HolderType.INSERT_ITEM) {
                for (int slot : event.getRawSlots()) {
                    if (slot < 5 && slot != 2) {
                        event.setCancelled(true);
                        return;
                    }
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof AuctionManager.AuctionHolder holder)) return;

        if (holder.getType() == AuctionManager.HolderType.INSERT_ITEM) {
            ItemStack inSlot2 = event.getInventory().getItem(2);
            if (inSlot2 != null && !inSlot2.isEmpty()) {
                event.getInventory().setItem(2, null);
                manager.refundItem(player, inSlot2);
            }
        } else if (holder.getType() == AuctionManager.HolderType.CONFIRM_LISTING) {
            manager.cancelPendingListing(player);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                manager.clearSearchQuery(player.getUniqueId());
                return;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof AuctionManager.AuctionHolder)) {
                manager.clearSearchQuery(player.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer());
    }

    private void handleBrowserClick(Player player, AuctionManager.AuctionHolder holder, int slot, ClickType click) {
        if (slot >= 0 && slot < 45) {
            Long auctionId = holder.getSlotToAuctionId().get(slot);
            if (auctionId == null) return;

            AuctionItem auction = manager.getAuction(auctionId);
            if (auction == null) {
                player.sendMessage(ColorUtils.parse(AuctionText.MSG_ITEM_NO_LONGER_AVAILABLE));
                manager.openAuctionBrowser(player, holder.getPage());
                return;
            }

            if (click.isRightClick() && auction.isShulkerBox()) {
                player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 0.7f, 1.0f);
                manager.openShulkerPreview(player, auction.getItem());
                return;
            }

            if (auction.getSellerUuid().equals(player.getUniqueId())) {
                player.sendMessage(ColorUtils.parse(AuctionText.MSG_OWN_LISTING_HINT));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 1.0f);
                return;
            }

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            manager.openBuyConfirm(player, auction);
            return;
        }

        switch (slot) {
            case 45 -> {
                if (holder.getPage() > 1) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                    manager.openAuctionBrowser(player, holder.getPage() - 1);
                }
            }
            case 47 -> manager.cycleSort(player);
            case 48 -> manager.cycleCategory(player);
            case 49 -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                manager.openAuctionBrowser(player, holder.getPage());
            }
            case 50 -> {
                if (click.isRightClick()) {
                    manager.clearSearch(player);
                } else {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                    manager.openSearchDialog(player);
                }
            }
            case 51 -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                manager.openMyListings(player);
            }
            case 53 -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                manager.openAuctionBrowser(player, holder.getPage() + 1);
            }
        }
    }

    private void handleMyListingsClick(Player player, AuctionManager.AuctionHolder holder, int slot) {
        if (slot == 0) {
            if (manager.getPlayerActiveCount(player.getUniqueId()) >= AuctionText.MAX_ACTIVE_LISTINGS) {
                player.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_SLOTS_FULL, AuctionText.MAX_ACTIVE_LISTINGS)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            manager.openInsertItemGui(player);
            return;
        }

        if (slot == 18) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            manager.openAuctionBrowser(player, 1);
            return;
        }

        Long auctionId = holder.getSlotToAuctionId().get(slot);
        if (auctionId != null) {
            manager.cancelListing(player, auctionId);
        }
    }

    private void handleInsertItemClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        if (rawSlot >= 0 && rawSlot < topSize) {
            if (rawSlot == 0) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                ItemStack inSlot2 = event.getView().getTopInventory().getItem(2);
                if (inSlot2 != null && !inSlot2.isEmpty()) {
                    event.getView().getTopInventory().setItem(2, null);
                    manager.refundItem(player, inSlot2);
                }
                manager.openMyListings(player);
                return;
            }

            if (rawSlot == 1 || rawSlot == 3) {
                event.setCancelled(true);
                return;
            }

            if (rawSlot == 4) {
                event.setCancelled(true);
                ItemStack inSlot2 = event.getView().getTopInventory().getItem(2);
                if (inSlot2 == null || inSlot2.isEmpty()) {
                    player.sendMessage(ColorUtils.parse(AuctionText.MSG_INSERT_EMPTY));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                    return;
                }

                if (AuctionText.BLOCKED_MATERIALS.contains(inSlot2.getType())) {
                    player.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_ITEM_BLOCKED, inSlot2.getType().name())));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                    return;
                }

                if (manager.getPlayerActiveCount(player.getUniqueId()) >= AuctionText.MAX_ACTIVE_LISTINGS) {
                    player.sendMessage(ColorUtils.parse(String.format(AuctionText.MSG_SLOTS_FULL, AuctionText.MAX_ACTIVE_LISTINGS)));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                    return;
                }

                ItemStack itemToSell = inSlot2.clone();
                event.getView().getTopInventory().setItem(2, null);
                manager.startPendingListing(player, itemToSell);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                manager.openPriceInputDialog(player, null);
            }
            return;
        }

        if (event.isShiftClick()) {
            ItemStack inSlot2 = event.getView().getTopInventory().getItem(2);
            if (inSlot2 == null || inSlot2.isEmpty()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && !clicked.isEmpty()) {
                    event.setCancelled(true);
                    event.getView().getTopInventory().setItem(2, clicked.clone());
                    event.setCurrentItem(null);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    private void handleConfirmListingClick(Player player, int slot) {
        if (slot == 11) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
            manager.cancelPendingListing(player);
            manager.openMyListings(player);
        } else if (slot == 15) {
            manager.confirmAndListPending(player);
        }
    }

    private void handleBuyConfirmClick(Player player, AuctionManager.AuctionHolder holder, int slot) {
        AuctionItem item = holder.getAuctionItem();
        if (item == null) {
            manager.openAuctionBrowser(player, 1);
            return;
        }

        if (slot == 11) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
            manager.openAuctionBrowser(player, 1);
        } else if (slot == 15) {
            manager.buyItem(player, item.getId());
        }
    }

    private void handleShulkerPreviewClick(Player player, int slot) {
        if (slot == 31) {
            player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 0.7f, 1.0f);
            manager.openAuctionBrowser(player, 1);
        }
    }
}

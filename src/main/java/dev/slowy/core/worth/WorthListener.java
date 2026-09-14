package dev.slowy.core.worth;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.config.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class WorthListener implements Listener {

    private final SlowyCore plugin;
    private final WorthManager worthManager;

    public WorthListener(SlowyCore plugin, WorthManager worthManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.worthManager = Objects.requireNonNull(worthManager, "worthManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        worthManager.loadPlayerSettings(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        worthManager.clearInventory(player);
        worthManager.unloadPlayerSettings(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        event.getItemDrop().setItemStack(worthManager.stripWorthDisplay(item));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerAttemptPickupItem(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        ItemStack groundStack = event.getItem().getItemStack();
        if (groundStack.getType().isAir()) return;

        worthManager.stripStorageForPickup(player, groundStack.getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    worthManager.syncInventory(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        for (ItemStack item : event.getDrops()) {
            if (item != null) {
                worthManager.stripWorthDisplay(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && !item.getType().isAir()) {
                worthManager.stripWorthDisplay(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            Inventory top = event.getView().getTopInventory();
            if (top.getHolder() instanceof WorthManager.SellHolder) {
                worthManager.handleSellGuiClose(player, event.getInventory());
                return;
            }

            if (top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER) {
                for (ItemStack item : top.getContents()) {
                    if (item != null && !item.getType().isAir()) {
                        worthManager.stripWorthDisplay(item);
                    }
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    worthManager.syncInventory(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSellGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getView().getTopInventory().getHolder() instanceof WorthManager.SellHolder holder) {
            int rawSlot = event.getRawSlot();
            if (rawSlot == CoreConfig.SELL_CANCEL_SLOT) {
                event.setCancelled(true);
                holder.setConfirmed(false);
                player.closeInventory();
                return;
            }
            if (rawSlot == CoreConfig.SELL_CONFIRM_SLOT) {
                event.setCancelled(true);
                holder.setConfirmed(true);
                player.closeInventory();
                return;
            }
            if (event.isShiftClick() && (rawSlot == CoreConfig.SELL_CANCEL_SLOT || rawSlot == CoreConfig.SELL_CONFIRM_SLOT)) {
                event.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    worthManager.updateGuiTitle(player, player.getOpenInventory());
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSellGuiDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WorthManager.SellHolder) {
            if (event.getRawSlots().contains(CoreConfig.SELL_CANCEL_SLOT) ||
                    event.getRawSlots().contains(CoreConfig.SELL_CONFIRM_SLOT)) {
                event.setCancelled(true);
                return;
            }
            if (event.getWhoClicked() instanceof Player player) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        worthManager.updateGuiTitle(player, player.getOpenInventory());
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClickStacking(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            worthManager.mergePlayerStorageStacks(player);
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if (cursor.getType().isAir() || current == null || current.getType().isAir()) {
            return;
        }

        if (worthManager.isSimilarIgnoringWorth(cursor, current)) {
            ClickType click = event.getClick();
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                int maxStack = current.getMaxStackSize();
                int currentAmount = current.getAmount();
                if (currentAmount < maxStack) {
                    int cursorAmount = cursor.getAmount();
                    int toAdd = (click == ClickType.LEFT) ? Math.min(cursorAmount, maxStack - currentAmount) : 1;

                    if (toAdd > 0) {
                        event.setCancelled(true);
                        current.setAmount(currentAmount + toAdd);
                        int remaining = cursorAmount - toAdd;
                        ItemStack newCursor = (remaining <= 0) ? null : cursor.clone();
                        if (newCursor != null) {
                            newCursor.setAmount(remaining);
                        }

                        current = worthManager.stripWorthDisplay(current);
                        if (newCursor != null) {
                            newCursor = worthManager.stripWorthDisplay(newCursor);
                        }

                        if (event.getClickedInventory() != null) {
                            event.getClickedInventory().setItem(event.getSlot(), current);
                        }
                        player.setItemOnCursor(newCursor);

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                worthManager.syncInventory(player);
                            }
                        });
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClickSync(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    worthManager.syncInventory(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDragSync(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    worthManager.syncInventory(player);
                }
            });
        }
    }
}

package dev.slowy.core.shop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class ShopListener implements Listener {

    private final ShopManager shopManager;

    public ShopListener(ShopManager shopManager) {
        this.shopManager = Objects.requireNonNull(shopManager, "shopManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopMenuClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopManager.ShopHolder holder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                    shopManager.handleClick(player, event.getRawSlot(), event.getClick(), holder);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopManager.ShopHolder) {
            event.setCancelled(true);
        }
    }
}

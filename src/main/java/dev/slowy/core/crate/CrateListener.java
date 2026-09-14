package dev.slowy.core.crate;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.utils.ColorUtils;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class CrateListener implements Listener {

    private final SlowyCore plugin;
    private final CrateManager crateManager;

    public CrateListener(SlowyCore plugin, CrateManager crateManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.crateManager = Objects.requireNonNull(crateManager, "crateManager cannot be null");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        CrateDefinition crate = crateManager.getBoundCrate(block);
        if (crate == null) return;

        event.setCancelled(true);

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            crateManager.openCrate(player, crate, block);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            crateManager.tellLocationAndKeys(player, crate);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        // Pattern matching switch on InventoryHolder
        switch (holder) {
            case GachaHolder _ -> event.setCancelled(true);
            case ChooseHolder chooseHolder -> {
                event.setCancelled(true);
                if (event.getClickedInventory() == event.getView().getTopInventory()) {
                    CrateReward reward = chooseHolder.getReward(event.getRawSlot());
                    if (reward != null) {
                        crateManager.openConfirmMenu(player, chooseHolder.getCrate(), reward);
                    }
                }
            }
            case ConfirmHolder confirmHolder -> {
                event.setCancelled(true);
                if (event.getClickedInventory() == event.getView().getTopInventory()) {
                    int rawSlot = event.getRawSlot();
                    if (rawSlot == 11) {
                        crateManager.openChooseMenu(player, confirmHolder.getCrate());
                    } else if (rawSlot == 15) {
                        crateManager.handleConfirmClaim(player, confirmHolder);
                    }
                }
            }
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof GachaHolder || holder instanceof ChooseHolder || holder instanceof ConfirmHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GachaHolder && event.getPlayer() instanceof Player player) {
            crateManager.handleGachaClose(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        CrateDefinition crate = crateManager.getBoundCrate(block);
        if (crate != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtils.parse("<#FF0055>✖ This block is a Crate (" + crate.getDisplayName() + "<#FF0055>)!</#FF0055>"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        crateManager.loadPlayerDataAsync(player.getUniqueId());

        if (crateManager.getKeyAllManager() != null) {
            crateManager.getKeyAllManager().handlePlayerJoin(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        crateManager.unloadPlayerData(player.getUniqueId());

        if (crateManager.getKeyAllManager() != null) {
            crateManager.getKeyAllManager().handlePlayerQuit(player);
        }
    }
}

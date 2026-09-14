package dev.slowy.core.protection;

import dev.slowy.core.config.CoreConfig;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * Event listener enforcing hub protection and gamemode boundaries.
 */
@NullMarked
public final class HubProtectionListener implements Listener {

    private final HubProtectionManager manager;

    public HubProtectionListener(HubProtectionManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!manager.handleGameModeChange(event.getPlayer(), event.getNewGameMode())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.enforceGamemode(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && event.getTo().getWorld() != null) {
            manager.enforceGamemode(event.getPlayer(), event.getTo().getWorld());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.enforceGamemode(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            Player player = event.getPlayer();
            if (!(manager.isOwner(player) && player.getGameMode() == GameMode.CREATIVE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            Player player = event.getPlayer();
            if (!(manager.isOwner(player) && player.getGameMode() == GameMode.CREATIVE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            Player player = event.getPlayer();
            if (!(manager.isOwner(player) && player.getGameMode() == GameMode.CREATIVE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            Player player = event.getPlayer();
            if (!(manager.isOwner(player) && player.getGameMode() == GameMode.CREATIVE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!CoreConfig.isHubWorld(event.getEntity().getWorld())) return;

        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                manager.rescueVoid(player);
            }
            return;
        }

        // Non-player entities in Hub: only creative owner can damage
        if (event instanceof EntityDamageByEntityEvent edbe && edbe.getDamager() instanceof Player damager) {
            if (manager.isOwner(damager) && damager.getGameMode() == GameMode.CREATIVE) {
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (CoreConfig.isHubWorld(event.getPlayer().getWorld())) {
            Player player = event.getPlayer();
            if (!(manager.isOwner(player) && player.getGameMode() == GameMode.CREATIVE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (CoreConfig.isHubWorld(event.getEntity().getWorld())) {
            if (event instanceof HangingBreakByEntityEvent hbe && hbe.getRemover() instanceof Player player) {
                if (manager.isOwner(player) && player.getGameMode() == GameMode.CREATIVE) {
                    return;
                }
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame && CoreConfig.isHubWorld(event.getPlayer().getWorld())) {
            Player player = event.getPlayer();
            if (!(manager.isOwner(player) && player.getGameMode() == GameMode.CREATIVE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && CoreConfig.isHubWorld(player.getWorld())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (CoreConfig.isHubWorld(event.getLocation().getWorld())) {
            if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCropTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType() == Material.FARMLAND && CoreConfig.isHubWorld(event.getPlayer().getWorld())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (CoreConfig.isHubWorld(event.getLocation().getWorld())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (CoreConfig.isHubWorld(event.getWorld()) && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer());
    }
}

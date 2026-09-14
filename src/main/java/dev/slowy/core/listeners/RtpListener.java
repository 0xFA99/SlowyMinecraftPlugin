package dev.slowy.core.listeners;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.rtp.RtpMenu;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class RtpListener implements Listener {

    private final SlowyCore plugin;

    public RtpListener(SlowyCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Cek hanya ketika koordinat blok berubah untuk efisiensi CPU maksimal
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        plugin.getRtpManager().handlePlayerMove(event.getPlayer(), from, to);
        plugin.getHubProtectionManager().handlePlayerMove(event.getPlayer(), from, to);
        plugin.getAfkManager().handlePlayerMove(event.getPlayer(), from, to);
        if (plugin.getAfkManager().isInSpawnAfkPortal(to)) {
            plugin.getAfkManager().handlePortalEntry(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof RtpMenu.RtpHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                plugin.getRtpManager().getRtpMenu().handleClick(player, event.getRawSlot());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (CoreConfig.isHubWorld(player.getWorld())) {
            // Cancel vanilla portal transfer in Hub (Spawn & AFK Zone)
            event.setCancelled(true);

            Location from = event.getFrom();
            if (plugin.getAfkManager().isInSpawnAfkPortal(from) || plugin.getAfkManager().isInSpawnAfkPortal(player.getLocation())) {
                plugin.getAfkManager().handlePortalEntry(player);
                return;
            }

            plugin.getRtpManager().handlePlayerMove(player, from, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (CoreConfig.isHubWorld(event.getPlayer().getWorld())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getRtpManager().cancelPortal(event.getPlayer(), false);
        plugin.getAfkManager().onPlayerQuit(event.getPlayer());
    }
}

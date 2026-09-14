package dev.slowy.core.listeners;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.hologram.HologramManager;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

/**
 * Event listener keeping holograms synchronized and personalizing text for viewers.
 * Native support for Paper's PlayerTrackEntityEvent and AuthMe LoginEvent.
 */
public final class HologramListener implements Listener {

    private final SlowyCore plugin;
    private final HologramManager hologramManager;

    public HologramListener(SlowyCore plugin, HologramManager hologramManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.hologramManager = Objects.requireNonNull(hologramManager, "hologramManager cannot be null");
        hookAuthMe();
    }

    private void hookAuthMe() {
        try {
            Class<?> loginEventClass = Class.forName("fr.xephi.authme.events.LoginEvent");
            if (Event.class.isAssignableFrom(loginEventClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) loginEventClass;
                Bukkit.getPluginManager().registerEvent(
                        eventClass,
                        this,
                        EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Player p = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                                if (p != null) {
                                    p.getScheduler().runDelayed(plugin, task -> {
                                        if (p.isOnline()) hologramManager.updateForPlayer(p, true);
                                    }, null, 2L);
                                }
                            } catch (Throwable ignored) {}
                        },
                        plugin
                );
                plugin.getSlf4jLogger().info("Hooked into AuthMe LoginEvent for seamless hologram personalization.");
            }
        } catch (ClassNotFoundException ignored) {
            // AuthMe not installed, standard events handle tracking
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTrackEntity(PlayerTrackEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TextDisplay td) {
            hologramManager.handlePlayerTrack(event.getPlayer(), td);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerUntrackEntity(PlayerUntrackEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TextDisplay td) {
            hologramManager.handlePlayerUntrack(event.getPlayer(), td);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) hologramManager.updateForPlayer(player, true);
        }, null, 5L);

        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) hologramManager.updateForPlayer(player, true);
        }, null, 25L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        hologramManager.cleanupPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) hologramManager.updateForPlayer(player, true);
        }, null, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) hologramManager.updateForPlayer(player, true);
        }, null, 5L);
    }
}

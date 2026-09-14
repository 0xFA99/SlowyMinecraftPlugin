package dev.slowy.core.auth;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Objects;

@NullMarked
public final class AuthListener implements Listener {

    private final SlowyCore plugin;
    private final AuthManager authManager;

    public AuthListener(SlowyCore plugin, AuthManager authManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.authManager = Objects.requireNonNull(authManager, "authManager cannot be null");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        authManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        authManager.onPlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!authManager.isAuthenticated(player) && event.hasChangedBlock()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(ColorUtils.parse("<yellow>⚠ Please complete authentication to chat.</yellow>"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer())) {
            String msg = event.getMessage().toLowerCase(Locale.ROOT);
            if (!msg.startsWith("/login") && !msg.startsWith("/register") && !msg.startsWith("/auth") && !msg.startsWith("/l ") && !msg.startsWith("/reg ")) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(ColorUtils.parse("<red>✖ You must log in before using commands.</red>"));
            }
        }
    }
}

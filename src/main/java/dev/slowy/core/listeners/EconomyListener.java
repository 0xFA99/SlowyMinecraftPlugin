package dev.slowy.core.listeners;

import dev.slowy.core.economy.EconomyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class EconomyListener implements Listener {

    private final EconomyManager economyManager;

    public EconomyListener(EconomyManager economyManager) {
        this.economyManager = Objects.requireNonNull(economyManager, "economyManager cannot be null");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Memastikan akun terdaftar di in-memory cache dan username ter-update
        economyManager.getOrCreateAccount(player.getUniqueId(), player.getName());
    }
}

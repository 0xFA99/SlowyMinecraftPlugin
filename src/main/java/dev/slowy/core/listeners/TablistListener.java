package dev.slowy.core.listeners;

import dev.slowy.core.tablist.TablistManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class TablistListener implements Listener {

    private final TablistManager tablistManager;

    public TablistListener(TablistManager tablistManager) {
        this.tablistManager = Objects.requireNonNull(tablistManager, "tablistManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        tablistManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tablistManager.onPlayerQuit(event.getPlayer());
    }
}

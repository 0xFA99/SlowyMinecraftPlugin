package dev.slowy.core.listeners;

import dev.slowy.core.scoreboard.ScoreboardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class ScoreboardListener implements Listener {

    private final ScoreboardManager scoreboardManager;

    public ScoreboardListener(ScoreboardManager scoreboardManager) {
        this.scoreboardManager = Objects.requireNonNull(scoreboardManager, "scoreboardManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scoreboardManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        scoreboardManager.onPlayerQuit(event.getPlayer());
    }
}

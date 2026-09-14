package dev.slowy.core.guild;

import dev.slowy.core.SlowyCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public final class GuildListener implements Listener {

    private final SlowyCore plugin;
    private final GuildManager guildManager;

    public GuildListener(SlowyCore plugin, GuildManager guildManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.guildManager = Objects.requireNonNull(guildManager, "guildManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        guildManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        guildManager.onPlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (guildManager.isChatToggled(player.getUniqueId())) {
            event.setCancelled(true);
            String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
            guildManager.sendGuildChat(player, plainMessage);
        }
    }
}

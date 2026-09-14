package dev.slowy.core.listeners;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ChatListener implements Listener, ChatRenderer {

    // Separator statis dari config: parse sekali, reuse untuk semua pesan
    // (bukan di-parse ulang tiap kali seseorang chat).
    private @org.jspecify.annotations.Nullable Component cachedSeparator;
    private @org.jspecify.annotations.Nullable String cachedSeparatorSource;

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!CoreConfig.CHAT_FORMAT_ENABLED) {
            return;
        }
        event.renderer(this);
    }

    @Override
    public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
        Component result = Component.empty();

        if (CoreConfig.CHAT_SHOW_HEAD) {
            Component headComponent = Component.object(source.getPlayerProfile())
                    .fallback(Component.empty());
            result = result.append(headComponent).append(Component.space());
        }

        Component nameComponent = ColorUtils.parse("<white>" + source.getName() + "</white>");

        return result
                .append(nameComponent)
                .append(getSeparator())
                .append(message.colorIfAbsent(NamedTextColor.WHITE));
    }

    private Component getSeparator() {
        String current = CoreConfig.CHAT_SEPARATOR;
        // Re-parse hanya jika config berubah (mis. reload plugin), bukan tiap pesan.
        if (cachedSeparator == null || !current.equals(cachedSeparatorSource)) {
            cachedSeparator = ColorUtils.parse(current);
            cachedSeparatorSource = current;
        }
        return cachedSeparator;
    }
}

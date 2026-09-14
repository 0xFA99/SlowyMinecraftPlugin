package dev.slowy.core.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtils {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ColorUtils() {}

    public static Component parse(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        // If message has legacy hex &#RRGGBB, convert to <#RRGGBB>
        if (message.contains("&#")) {
            Matcher hexMatcher = HEX_PATTERN.matcher(message);
            message = hexMatcher.replaceAll("<#$1>");
        }

        // If message contains legacy & codes, convert to MiniMessage tags
        if (message.contains("&")) {
            message = message
                    .replace("&0", "<black>")
                    .replace("&1", "<dark_blue>")
                    .replace("&2", "<dark_green>")
                    .replace("&3", "<dark_aqua>")
                    .replace("&4", "<dark_red>")
                    .replace("&5", "<dark_purple>")
                    .replace("&6", "<gold>")
                    .replace("&7", "<gray>")
                    .replace("&8", "<dark_gray>")
                    .replace("&9", "<blue>")
                    .replace("&a", "<green>")
                    .replace("&b", "<aqua>")
                    .replace("&c", "<red>")
                    .replace("&d", "<light_purple>")
                    .replace("&e", "<yellow>")
                    .replace("&f", "<white>")
                    .replace("&l", "<bold>")
                    .replace("&o", "<italic>")
                    .replace("&m", "<strikethrough>")
                    .replace("&n", "<underlined>")
                    .replace("&k", "<obfuscated>")
                    .replace("&r", "<reset>");
        }

        try {
            return MM.deserialize(message);
        } catch (Exception e) {
            return LEGACY_SERIALIZER.deserialize(message);
        }
    }

    public static List<Component> parse(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        return messages.stream().map(ColorUtils::parse).toList();
    }

    public static Component enforceNormal(Component component) {
        if (component == null) {
            return Component.empty();
        }
        Component modified = component
                .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
                .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE);

        if (!modified.children().isEmpty()) {
            List<Component> cleanChildren = modified.children().stream()
                    .map(ColorUtils::enforceNormal)
                    .toList();
            modified = modified.children(cleanChildren);
        }
        return modified;
    }

    public static Component parseItem(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return enforceNormal(parse("<!italic><!b>" + message));
    }

    public static List<Component> parseItemLore(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream().map(ColorUtils::parseItem).toList();
    }
}

package dev.slowy.core.utils;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Pixel-accurate width calculator for Minecraft's default proportional font.
 * Used for aligning text display columns and holograms down to the exact pixel.
 */
@NullMarked
public final class FontWidthUtil {

    /**
     * Standard pixel width for Minecraft 16-character usernames (96px max text + 12px min padding).
     */
    public static final int USERNAME_TARGET_PX = 108;

    /**
     * Standard pixel width for numerical stats column before the icon.
     */
    public static final int VALUE_NUMBER_TARGET_PX = 60;

    private FontWidthUtil() {}

    /**
     * Returns the total advance width of a character in Minecraft default font.
     * (Character pixel width + 1 pixel inter-character spacing)
     */
    public static int getCharWidth(char c) {
        return switch (c) {
            case 'i', '!', '|', ':', ';', '.', ',' -> 2;
            case 'l', '\'', '`' -> 3;
            case 'I', '[', ']', 't' -> 4;
            case ' ' -> 4;
            case 'f', 'k', '(', ')', '<', '>', '{', '}', '"', '*' -> 5;
            case '@', '~' -> 7;
            default -> 6; // Standard 5px glyph + 1px spacing (a-z, A-Z, 0-9, #, $, %, etc.)
        };
    }

    /**
     * Strips MiniMessage / formatting tags (<...>) from a string.
     */
    public static String stripTags(@Nullable String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("<[^>]*>", "");
    }

    /**
     * Calculates total pixel width of a text string, ignoring MiniMessage formatting tags.
     */
    public static int getStringWidth(@Nullable String text) {
        if (text == null || text.isEmpty()) return 0;
        String clean = stripTags(text);
        int width = 0;
        for (int i = 0; i < clean.length(); i++) {
            width += getCharWidth(clean.charAt(i));
        }
        return width;
    }

    /**
     * Calculates the target column width based on the maximum string width in a list,
     * adding minimum padding (at least 12 pixels to guarantee exact 4px/5px mathematical fit).
     */
    public static int calculateTargetWidth(Iterable<String> items, int minPadding) {
        int max = 0;
        for (String item : items) {
            if (item != null) {
                max = Math.max(max, getStringWidth(item));
            }
        }
        return max + Math.max(minPadding, 12);
    }

    /**
     * Generates exact space padding using 4px normal spaces and 5px bold spaces.
     * For any remainingPixels >= 12, this guarantees 100% exact zero-remainder pixel filling.
     */
    public static String buildSpaces(int remainingPixels) {
        if (remainingPixels <= 0) {
            return "";
        }

        int boldSpaces = 0;
        int normalSpaces = 0;

        if (remainingPixels >= 12) {
            switch (remainingPixels % 4) {
                case 0 -> normalSpaces = remainingPixels / 4;
                case 1 -> {
                    boldSpaces = 1;
                    normalSpaces = (remainingPixels - 5) / 4;
                }
                case 2 -> {
                    boldSpaces = 2;
                    normalSpaces = (remainingPixels - 10) / 4;
                }
                case 3 -> {
                    boldSpaces = 3;
                    normalSpaces = (remainingPixels - 15) / 4;
                }
            }
        } else {
            // Precise fallback for smaller remainders
            switch (remainingPixels) {
                case 1, 2, 3, 4 -> normalSpaces = 1;
                case 5, 6 -> boldSpaces = 1;
                case 7, 8 -> normalSpaces = 2;
                case 9 -> { normalSpaces = 1; boldSpaces = 1; }
                case 10, 11 -> boldSpaces = 2;
            }
        }

        StringBuilder sb = new StringBuilder();
        if (normalSpaces > 0) {
            sb.append(" ".repeat(normalSpaces));
        }
        if (boldSpaces > 0) {
            sb.append("<bold>").append(" ".repeat(boldSpaces)).append("</bold>");
        }
        return sb.toString();
    }

    /**
     * Pads text on the right (Align Left) up to the specified target pixel width.
     */
    public static String padRight(String text, int targetPixels) {
        int currentWidth = getStringWidth(text);
        int remaining = targetPixels - currentWidth;
        if (remaining <= 0) return text;
        return text + buildSpaces(remaining);
    }

    /**
     * Pads text on the left (Align Right) up to the specified target pixel width.
     */
    public static String padLeft(String text, int targetPixels) {
        int currentWidth = getStringWidth(text);
        int remaining = targetPixels - currentWidth;
        if (remaining <= 0) return text;
        return buildSpaces(remaining) + text;
    }
}

package dev.slowy.core.utils;

/**
 * Standardized color palette for SlowyCore2:
 * { "#1DA1F2", "#00F5FF", "#39FF14", "#FFE600", "#FF7A00", "#FF0055", "#FF00BD", "#E0F8FF" }
 */
public final class Theme {

    private Theme() {}

    public static final String PRIMARY = "#1DA1F2";       // Main Brand Blue / Informational
    public static final String CYAN = "#00F5FF";          // Electric Cyan / Tech Accent
    public static final String GREEN = "#39FF14";         // Neon Green / Money / Success / Balance
    public static final String YELLOW = "#FFE600";        // Electric Yellow / Gold / Rank 1 / Highlight
    public static final String ORANGE = "#FF7A00";        // Vibrant Orange / Warm / Nether / Rank 3
    public static final String RED = "#FF0055";           // Neon Crimson / Errors / Cancel / Spent
    public static final String MAGENTA = "#FF00BD";       // Neon Magenta / Shards ★ / The End / Special
    public static final String ICE_WHITE = "#E0F8FF";     // Crisp Ice White / Header text / Clean

    public static final String[] PALETTE = {
            PRIMARY, CYAN, GREEN, YELLOW, ORANGE, RED, MAGENTA, ICE_WHITE
    };
}

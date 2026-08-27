package com.slowy;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.LeaderboardManager;
import com.bx.ultimateDonutSmp.managers.LeaderboardManager.LeaderboardEntry;
import com.bx.ultimateDonutSmp.managers.LeaderboardManager.LeaderboardType;
import com.bx.ultimateDonutSmp.utils.NumberUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardHologramManager implements Listener {

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,###");

    public enum HoloConfig {
        MOBS_KILLED("mobskilled", "slowy_lb_mobskilled", "       &6&lTOP MOBS KILLED\n\n", 325.5, 3.0, 301.5, LeaderboardType.MOBS_KILLED, "%economy_mobskilled%", "&#FFAA00", false),
        PLAYTIME("playtime", "slowy_lb_playtime", "         &e&lTOTAL PLAYTIME\n\n", 325.5, 3.0, 289.5, LeaderboardType.PLAYTIME, "%economy_playtime%", "&#FCE300", false),
        MONEY_SPENT("moneyspent", "slowy_lb_moneyspent", "       &c&lTOP MONEY SPENT\n\n", 407.5, 0.0, 229.5, LeaderboardType.MONEY_SPENT, "%economy_moneyspent%", "&#FF5555", true),
        MONEY_MADE("moneymade", "slowy_lb_moneymade", "       &b&lTOP MONEY MADE\n\n", 395.5, 0.0, 229.5, LeaderboardType.MONEY_MADE, "%economy_moneymade%", "&#00FC00", true);

        public final String id;
        public final String tag;
        public final String title;
        public final double x;
        public final double y;
        public final double z;
        public final LeaderboardType leaderboardType;
        public final String placeholder;
        public final String valueColor;
        public final boolean isCurrency;

        HoloConfig(String id, String tag, String title, double x, double y, double z,
                   LeaderboardType leaderboardType, String placeholder, String valueColor, boolean isCurrency) {
            this.id = id;
            this.tag = tag;
            this.title = title;
            this.x = x;
            this.y = y;
            this.z = z;
            this.leaderboardType = leaderboardType;
            this.placeholder = placeholder;
            this.valueColor = valueColor;
            this.isCurrency = isCurrency;
        }
    }

    private final SlowyPlugin plugin;
    private final String worldName = "lobby2";

    private BukkitTask updateTask;
    private final Map<String, TextDisplay> personalDisplays = new ConcurrentHashMap<>();
    private final Map<HoloConfig, List<LeaderboardEntry>> cachedEntriesMap = new ConcurrentHashMap<>();

    public LeaderboardHologramManager(SlowyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            purgeAllWorldHolograms();
            updateAll();

            updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 60L, 60L);
        }, 40L);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        clearAllPersonalDisplays();
        purgeAllWorldHolograms();
    }

    private Location getLocation(HoloConfig config) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, config.x, config.y, config.z);
    }

    public void purgeAllWorldHolograms() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        for (HoloConfig config : HoloConfig.values()) {
            Location loc = getLocation(config);
            if (loc != null && !loc.getChunk().isLoaded()) {
                loc.getChunk().load(true);
            }
        }

        for (Entity entity : world.getEntities()) {
            if (entity instanceof TextDisplay) {
                boolean shouldRemove = false;
                for (HoloConfig config : HoloConfig.values()) {
                    if (entity.getScoreboardTags().contains(config.tag) || entity.getScoreboardTags().contains("slowy_lb_money")) {
                        shouldRemove = true;
                        break;
                    }
                    Location loc = getLocation(config);
                    if (loc != null && entity.getLocation().distanceSquared(loc) <= 36.0) {
                        shouldRemove = true;
                        break;
                    }
                }
                if (shouldRemove) {
                    try {
                        entity.remove();
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    private void clearAllPersonalDisplays() {
        for (TextDisplay display : personalDisplays.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        personalDisplays.clear();
    }

    private void removePlayerDisplays(UUID uuid) {
        for (HoloConfig config : HoloConfig.values()) {
            removeSingleDisplay(uuid, config.id);
        }
        removeSingleDisplay(uuid, "money");
    }

    private void removeSingleDisplay(UUID uuid, String typeId) {
        TextDisplay d = personalDisplays.remove(uuid.toString() + ":" + typeId);
        if (d != null && d.isValid()) {
            d.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayerDisplays(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!event.getPlayer().getWorld().getName().equalsIgnoreCase(worldName)) {
            removePlayerDisplays(event.getPlayer().getUniqueId());
        }
    }

    public void updateAll() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        LeaderboardManager lbManager = null;
        if (Bukkit.getPluginManager().isPluginEnabled("UltimateDonutSmp")) {
            try {
                UltimateDonutSmp donut = UltimateDonutSmp.getInstance();
                if (donut != null && donut.getLeaderboardManager() != null) {
                    lbManager = donut.getLeaderboardManager();
                }
            } catch (Throwable ignored) {}
        }

        for (HoloConfig config : HoloConfig.values()) {
            Location loc = getLocation(config);
            if (loc != null && !loc.getChunk().isLoaded()) {
                loc.getChunk().load(true);
            }

            if (lbManager != null) {
                try {
                    List<LeaderboardEntry> fresh = lbManager.getEntries(config.leaderboardType, 0, 10);
                    if (fresh != null && !fresh.isEmpty()) {
                        cachedEntriesMap.put(config, new ArrayList<>(fresh));
                    } else {
                        lbManager.triggerAsyncRefresh(config.leaderboardType);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Gagal refresh leaderboard " + config.id + ": " + t.getMessage());
                }
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equalsIgnoreCase(worldName)) {
                for (HoloConfig config : HoloConfig.values()) {
                    Location loc = getLocation(config);
                    if (loc != null) {
                        if (player.getLocation().distanceSquared(loc) <= 4096.0) {
                            List<LeaderboardEntry> entries = cachedEntriesMap.get(config);
                            updatePlayerHologram(player, config, loc, entries, lbManager);
                        } else {
                            removeSingleDisplay(player.getUniqueId(), config.id);
                        }
                    }
                }
            } else {
                removePlayerDisplays(player.getUniqueId());
            }
        }

        personalDisplays.keySet().removeIf(key -> {
            String uuidStr = key.split(":")[0];
            try {
                UUID u = UUID.fromString(uuidStr);
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline() || !p.getWorld().getName().equalsIgnoreCase(worldName)) {
                    TextDisplay d = personalDisplays.get(key);
                    if (d != null && d.isValid()) d.remove();
                    return true;
                }
            } catch (Throwable ignored) {}
            return false;
        });
    }

    private void updatePlayerHologram(Player player, HoloConfig config, Location loc,
                                      List<LeaderboardEntry> topEntries, LeaderboardManager lbManager) {
        String key = player.getUniqueId().toString() + ":" + config.id;
        TextDisplay display = personalDisplays.get(key);
        if (display == null || !display.isValid()) {
            display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.addScoreboardTag(config.tag);
                d.setVisibleByDefault(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.LEFT);
                d.setShadowed(true);
                d.setBackgroundColor(Color.fromARGB(140, 20, 20, 25));
                d.setSeeThrough(false);
            });
            player.showEntity(plugin, display);
            personalDisplays.put(key, display);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(config.title);

        boolean inTopTen = false;

        if (topEntries != null && !topEntries.isEmpty()) {
            int rank = 1;
            for (LeaderboardEntry entry : topEntries) {
                if (rank > 10) break;
                if (entry.playerData().getUuid().equals(player.getUniqueId())) {
                    inTopTen = true;
                }
                sb.append(formatRow(rank, config, entry.playerData().getUuid(), entry.playerData().getUsername(), entry)).append("\n");
                rank++;
            }
        } else {
            sb.append("   &7Memuat data peringkat...\n");
        }

        if (!inTopTen && lbManager != null) {
            try {
                LeaderboardEntry myEntry = lbManager.getPlayerEntry(player.getUniqueId(), config.leaderboardType);
                if (myEntry != null && myEntry.position() > 10) {
                    sb.append("&8&m--------------------------------&r\n");
                    sb.append(formatRow(myEntry.position(), config, player.getUniqueId(), player.getName(), myEntry)).append("\n");
                }
            } catch (Throwable ignored) {}
        }

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(sb.toString());
        display.text(component);
    }

    private String formatRow(int rank, HoloConfig config, UUID uuid, String name, LeaderboardEntry entry) {
        boolean isTopThree = rank <= 3;
        String rawRankStr = "#" + rank;
        String rankFormatted = switch (rank) {
            case 1 -> "&e&l#1";
            case 2 -> "&f&l#2";
            case 3 -> "&6&l#3";
            default -> "&7#" + rank;
        };

        int rankWidth = getStringWidth(rawRankStr, isTopThree);
        String col1Padding = getPixelSpaces(Math.max(4, 24 - rankWidth));

        String nameColor = switch (rank) {
            case 1 -> "&e";
            case 2 -> "&f";
            case 3 -> "&6";
            default -> "&b";
        };

        String displayName = name.length() > 13 ? name.substring(0, 13) : name;
        int nameWidth = getStringWidth(displayName, false);
        String col2Padding = getPixelSpaces(Math.max(4, 88 - nameWidth));

        String formattedValue = resolveValue(config, uuid, entry);
        String valueDisplay = config.isCurrency
                ? (formattedValue.startsWith("$") || formattedValue.startsWith("Rp") ? config.valueColor + formattedValue : config.valueColor + "$ " + formattedValue)
                : config.valueColor + formattedValue;

        return rankFormatted + col1Padding + nameColor + displayName + col2Padding + valueDisplay;
    }

    private String resolveValue(HoloConfig config, UUID uuid, LeaderboardEntry entry) {
        if (config == HoloConfig.PLAYTIME) {
            long seconds = entry != null ? entry.playerData().getTotalPlaytimeSeconds() : 0L;
            return formatPlaytime(uuid, seconds);
        } else if (config == HoloConfig.MOBS_KILLED) {
            long mobs = entry != null ? entry.playerData().getMobsKilled() : 0L;
            return formatNumber(config, uuid, mobs);
        }

        double amount = 0.0;
        if (entry != null) {
            amount = switch (config) {
                case MONEY_SPENT -> entry.playerData().getMoneySpent();
                case MONEY_MADE -> entry.playerData().getMoneyMade();
                default -> 0.0;
            };
        }
        return formatCurrency(config, uuid, amount);
    }

    private String formatNumber(HoloConfig config, UUID uuid, long amount) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && uuid != null) {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String placeholderResult = PlaceholderAPI.setPlaceholders(op, config.placeholder);
                if (placeholderResult != null && !placeholderResult.equals(config.placeholder) && !placeholderResult.trim().isEmpty()) {
                    return placeholderResult.trim();
                }
            } catch (Throwable ignored) {}
        }
        return NUMBER_FORMAT.format(amount);
    }

    private String formatCurrency(HoloConfig config, UUID uuid, double money) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && uuid != null) {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String placeholderResult = PlaceholderAPI.setPlaceholders(op, config.placeholder);
                if (placeholderResult != null && !placeholderResult.equals(config.placeholder) && !placeholderResult.trim().isEmpty()) {
                    return placeholderResult.trim();
                }
            } catch (Throwable ignored) {}
        }

        if (Bukkit.getPluginManager().isPluginEnabled("UltimateDonutSmp")) {
            try {
                UltimateDonutSmp donut = UltimateDonutSmp.getInstance();
                if (donut != null && donut.getCurrencyManager() != null) {
                    String formatted = donut.getCurrencyManager().formatMoneyCompact(money);
                    if (formatted != null && !formatted.trim().isEmpty()) {
                        return formatted.trim();
                    }
                }
            } catch (Throwable ignored) {}
        }

        return formatCompactFallback(money);
    }

    private String formatPlaytime(UUID uuid, long playtimeSeconds) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && uuid != null) {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String placeholderResult = PlaceholderAPI.setPlaceholders(op, "%economy_playtime%");
                if (placeholderResult != null && !placeholderResult.equals("%economy_playtime%") && !placeholderResult.trim().isEmpty()) {
                    return placeholderResult.trim();
                }
            } catch (Throwable ignored) {}
        }

        if (Bukkit.getPluginManager().isPluginEnabled("UltimateDonutSmp")) {
            try {
                return NumberUtils.formatTimeLong(playtimeSeconds);
            } catch (Throwable ignored) {}
        }

        return formatPlaytimeFallback(playtimeSeconds);
    }

    private String formatCompactFallback(double money) {
        if (money >= 1_000_000_000_000.0) {
            return String.format("%.2fT", money / 1_000_000_000_000.0);
        } else if (money >= 1_000_000_000.0) {
            return String.format("%.2fB", money / 1_000_000_000.0);
        } else if (money >= 1_000_000.0) {
            return String.format("%.2fM", money / 1_000_000.0);
        } else if (money >= 1_000.0) {
            return String.format("%.2fk", money / 1_000.0);
        } else {
            return String.format("%.0f", money);
        }
    }

    private String formatPlaytimeFallback(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return String.format("%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    // =========================================================================
    // HELPER: PIXEL ACCURATE WIDTH
    // =========================================================================
    public static int getCharWidth(char c, boolean bold) {
        int width;
        switch (c) {
            case 'i', '!', '|', ':', ';', '.', ',', '\'', '`' -> width = 1;
            case 'l' -> width = 2;
            case 'I', 't', '[', ']', ' ', '\"' -> width = 3;
            case 'k', 'f', '{', '}', '(', ')', '<', '>' -> width = 4;
            case '@', '~' -> width = 6;
            default -> width = 5;
        }
        return (bold ? width + 1 : width) + 1;
    }

    public static int getStringWidth(String text, boolean isBold) {
        if (text == null) return 0;
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += getCharWidth(text.charAt(i), isBold);
        }
        return width;
    }

    public static String getPixelSpaces(int pixels) {
        if (pixels <= 0) return " ";
        StringBuilder sb = new StringBuilder();
        int remaining = pixels;
        while (remaining >= 4) {
            if (remaining % 4 == 0) {
                sb.append(" ");
                remaining -= 4;
            } else if (remaining >= 5) {
                sb.append("&l &r");
                remaining -= 5;
            } else {
                sb.append(" ");
                remaining -= 4;
            }
        }
        return sb.toString();
    }
}

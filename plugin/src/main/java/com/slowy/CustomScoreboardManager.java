package com.slowy;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.CurrencyManager;
import com.bx.ultimateDonutSmp.models.PlayerData;
import com.bx.ultimateDonutSmp.models.Team;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomScoreboardManager {

    private static final NumberFormat BLANK_NUMBER_FORMAT = NumberFormat.blank();
    private static final String[] LINE_ENTRIES = new String[16];

    static {
        for (int i = 0; i < 16; i++) {
            LINE_ENTRIES[i] = "§" + Integer.toHexString(i) + "§r";
        }
    }

    private final SlowyPlugin plugin;
    private final Map<UUID, Scoreboard> playerBoards = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerLastLineCounts = new ConcurrentHashMap<>();
    private BukkitTask updateTask;

    private int titleIndex = 0;

    // Cached configuration
    private long lastConfigCheck = 0L;
    private long lastConfigModified = 0L;
    private List<String> cachedTitles = Collections.emptyList();
    private List<String> cachedLines = Collections.emptyList();
    private String cachedTeamTemplate = "&#00A4FC🪓 &fTeam &#00A4FC%economy_team%";
    private String cachedBoosterTemplate = "&#A303F9⚡ &fBooster &#A303F9%economy_booster_countdown%";
    private String cachedCuboidTemplate = "&#A303F9⌛ &fShard &#A303F9%economy_shard_cuboid_display%";

    public CustomScoreboardManager(SlowyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        reloadConfigCache();

        long updateTicks = 20L;
        File sbFile = getScoreboardConfigFile();
        if (sbFile.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(sbFile);
            updateTicks = cfg.getLong("SCOREBOARD.TITLE-UPDATE-TICKS", 20L);
            if (updateTicks <= 0) updateTicks = 20L;
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, updateTicks);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeScoreboard(player);
        }
        playerBoards.clear();
        playerLastLineCounts.clear();
    }

    private File getScoreboardConfigFile() {
        return new File(Bukkit.getWorldContainer(), "plugins/UltimateDonutSmp/scoreboard.yml");
    }

    private void reloadConfigCache() {
        File sbFile = getScoreboardConfigFile();
        if (!sbFile.exists()) {
            cachedTitles = List.of("&3&lUSER INFO");
            cachedLines = List.of(
                    "",
                    "&#00FC00&l$ &fMoney &#00FC00%economy_nicestMoney%",
                    "&#A303F9★ &fShards &#A303F9%economy_shards%",
                    "&#FC0000🗡 &fKills &#FC0000%economy_kills%",
                    "&#F97603☠ &fDeaths &#F97603%economy_deaths%",
                    "&#00A4FC⌛ &fKeyall &#00A4FC%economy_keyall_countdown%",
                    "&#FCE300⌚ &fPlaytime &#FCE300%economy_playtime%",
                    "{team}",
                    "",
                    "&#00D4FF📶 &fPing &#00D4FF%economy_ping%ms"
            );
            return;
        }

        long modified = sbFile.lastModified();
        lastConfigModified = modified;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(sbFile);
            List<String> titles = config.getStringList("SCOREBOARD.TITLE");
            cachedTitles = titles.isEmpty() ? List.of("&3&lUSER INFO") : List.copyOf(titles);

            List<String> lines = config.getStringList("SCOREBOARD.LINES");
            cachedLines = lines.isEmpty() ? List.of(
                    "",
                    "&#00FC00&l$ &fMoney &#00FC00%economy_nicestMoney%",
                    "&#A303F9★ &fShards &#A303F9%economy_shards%",
                    "&#FC0000🗡 &fKills &#FC0000%economy_kills%",
                    "&#F97603☠ &fDeaths &#F97603%economy_deaths%",
                    "&#00A4FC⌛ &fKeyall &#00A4FC%economy_keyall_countdown%",
                    "&#FCE300⌚ &fPlaytime &#FCE300%economy_playtime%",
                    "{team}",
                    "",
                    "&#00D4FF📶 &fPing &#00D4FF%economy_ping%ms"
            ) : List.copyOf(lines);

            cachedTeamTemplate = config.getString("SCOREBOARD.TEAM", "&#00A4FC🪓 &fTeam &#00A4FC%economy_team%");
            cachedBoosterTemplate = config.getString("SCOREBOARD.SHARD-BOOSTER", "&#A303F9⚡ &fBooster &#A303F9%economy_booster_countdown%");
            cachedCuboidTemplate = config.getString("SCOREBOARD.SHARD-CUBOID", "&#A303F9⌛ &fShard &#A303F9%economy_shard_cuboid_display%");
        } catch (Throwable t) {
            plugin.getLogger().warning("Gagal memuat cache scoreboard.yml: " + t.getMessage());
        }
    }

    private void checkConfigReload() {
        long now = System.currentTimeMillis();
        if (now - lastConfigCheck > 5000L) {
            lastConfigCheck = now;
            File sbFile = getScoreboardConfigFile();
            if (sbFile.exists() && sbFile.lastModified() != lastConfigModified) {
                reloadConfigCache();
            }
        }
    }

    private void tick() {
        checkConfigReload();
        titleIndex++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public void updatePlayer(Player player) {
        if (!player.isOnline()) return;

        ScoreboardSettings settings = plugin.getScoreboardSettingsManager().getSettings(player);
        if (!settings.isScoreboardEnabled()) {
            removeScoreboard(player);
            return;
        }

        Scoreboard board = playerBoards.computeIfAbsent(player.getUniqueId(), uuid -> {
            Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(b);
            return b;
        });

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }

        Objective objective = board.getObjective("slowy_sb");
        if (objective == null) {
            objective = board.registerNewObjective("slowy_sb", Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // 1. Title
        String rawTitle = cachedTitles.isEmpty() ? "&3&lUSER INFO" : cachedTitles.get(Math.abs(titleIndex) % cachedTitles.size());
        objective.displayName(parseColor(rawTitle, player));

        // 2. Lines
        List<Component> renderedLines = new ArrayList<>(cachedLines.size());
        UltimateDonutSmp donut = getUltimateDonutSmp();
        PlayerData data = (donut != null && donut.getPlayerDataManager() != null) ? donut.getPlayerDataManager().get(player) : null;

        for (String line : cachedLines) {
            if (line == null) continue;
            String trimmed = line.trim();

            if (trimmed.equalsIgnoreCase("{team}")) {
                if (donut != null && donut.getTeamManager() != null) {
                    Team team = donut.getTeamManager().getTeam(player.getUniqueId());
                    if (team != null && team.getName() != null) {
                        line = cachedTeamTemplate.replace("%economy_team%", team.getName());
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
            }

            if (trimmed.equalsIgnoreCase("{shard_booster}")) {
                if (donut != null && donut.getShardManager() != null && donut.getShardManager().hasBooster(player.getUniqueId())) {
                    line = cachedBoosterTemplate;
                } else {
                    continue;
                }
            }

            if (trimmed.equalsIgnoreCase("{shard_cuboid}")) {
                if (donut != null && donut.getShardManager() != null && donut.getShardManager().shouldShowShardCuboidLine(player.getUniqueId())) {
                    line = cachedCuboidTemplate;
                } else {
                    continue;
                }
            }

            // Filter individual lines per settings
            if ((line.contains("Money") || line.contains("%economy_nicestMoney%")) && !settings.isShowMoney()) {
                continue;
            }
            if ((line.contains("Shards") || line.contains("%economy_shards%")) && !settings.isShowShards()) {
                continue;
            }
            if ((line.contains("Kills") || line.contains("%economy_kills%")) && !settings.isShowKills()) {
                continue;
            }
            if ((line.contains("Deaths") || line.contains("%economy_deaths%")) && !settings.isShowDeaths()) {
                continue;
            }
            if ((line.contains("Playtime") || line.contains("%economy_playtime%")) && !settings.isShowPlaytime()) {
                continue;
            }

            // Replace Placeholders
            if (data != null) {
                if (donut != null && donut.getCurrencyManager() != null) {
                    String moneyStr = donut.getCurrencyManager().formatCompactAmount(CurrencyManager.CurrencyType.MONEY, data.getMoney());
                    line = line.replace("%economy_nicestMoney%", moneyStr)
                               .replace("%economy_money_short%", moneyStr)
                               .replace("%economy_money_amount_short%", moneyStr);

                    String shardsStr = donut.getCurrencyManager().formatCompactAmount(CurrencyManager.CurrencyType.SHARDS, (double) data.getShards());
                    line = line.replace("%economy_nicestShards%", shardsStr)
                               .replace("%economy_shards_short%", shardsStr)
                               .replace("%economy_shards%", shardsStr);
                } else {
                    line = line.replace("%economy_nicestMoney%", String.format("%,.0f", data.getMoney()))
                               .replace("%economy_shards%", String.format("%,d", data.getShards()));
                }

                line = line.replace("%economy_kills%", String.valueOf(data.getKills()))
                           .replace("%economy_deaths%", String.valueOf(data.getDeaths()));

                long totalSecs = data.getTotalPlaytimeSeconds();
                long d = totalSecs / 86400;
                long h = (totalSecs % 86400) / 3600;
                long m = (totalSecs % 3600) / 60;
                String playtimeDisplay = d > 0 ? (d + "d " + h + "h") : (h > 0 ? (h + "h " + m + "m") : (m + "m"));
                line = line.replace("%economy_playtime%", playtimeDisplay);

                long keyall = data.getKeyAllRemainingSeconds();
                long km = keyall / 60;
                long ks = keyall % 60;
                line = line.replace("%economy_keyall_countdown%", String.format("%02d:%02d", km, ks));
            }

            line = line.replace("%economy_ping%", String.valueOf(player.getPing()));

            renderedLines.add(parseColor(line, player));
        }

        int previousCount = playerLastLineCounts.getOrDefault(player.getUniqueId(), 0);
        int currentCount = renderedLines.size();

        if (previousCount > currentCount) {
            for (int i = currentCount; i < previousCount; i++) {
                String entry = getLineEntry(i);
                board.resetScores(entry);
            }
        }
        playerLastLineCounts.put(player.getUniqueId(), currentCount);

        for (int i = 0; i < currentCount; i++) {
            String entry = getLineEntry(i);
            Score score = objective.getScore(entry);
            score.customName(renderedLines.get(i));
            score.numberFormat(BLANK_NUMBER_FORMAT);
            score.setScore(currentCount - i);
        }
    }

    private String getLineEntry(int index) {
        if (index >= 0 && index < LINE_ENTRIES.length) {
            return LINE_ENTRIES[index];
        }
        return "§" + Integer.toHexString(index % 16) + "§r";
    }

    public void removeScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        playerBoards.remove(uuid);
        playerLastLineCounts.remove(uuid);

        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private Component parseColor(String text, Player player) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        try {
            String colorized = ColorUtils.colorize(text, player);
            return LegacyComponentSerializer.legacySection().deserialize(colorized);
        } catch (Throwable t) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        }
    }

    private UltimateDonutSmp getUltimateDonutSmp() {
        if (Bukkit.getPluginManager().isPluginEnabled("UltimateDonutSmp")) {
            try {
                return UltimateDonutSmp.getInstance();
            } catch (Throwable ignored) {}
        }
        return null;
    }
}

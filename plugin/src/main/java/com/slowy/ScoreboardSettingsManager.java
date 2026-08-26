package com.slowy;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardSettingsManager {

    private final SlowyPlugin plugin;
    private final File dataFolder;
    private final Map<UUID, ScoreboardSettings> settingsCache = new ConcurrentHashMap<>();

    public ScoreboardSettingsManager(SlowyPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public ScoreboardSettings getSettings(UUID uuid) {
        return settingsCache.computeIfAbsent(uuid, this::loadSettings);
    }

    public ScoreboardSettings getSettings(Player player) {
        return getSettings(player.getUniqueId());
    }

    private ScoreboardSettings loadSettings(UUID uuid) {
        ScoreboardSettings settings = new ScoreboardSettings();
        File file = new File(dataFolder, uuid.toString() + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            settings.setScoreboardEnabled(config.getBoolean("scoreboardEnabled", true));
            settings.setShowMoney(config.getBoolean("showMoney", true));
            settings.setShowShards(config.getBoolean("showShards", true));
            settings.setShowKills(config.getBoolean("showKills", false));
            settings.setShowDeaths(config.getBoolean("showDeaths", false));
            settings.setShowPlaytime(config.getBoolean("showPlaytime", true));
        } else {
            if (Bukkit.getPluginManager().isPluginEnabled("UltimateDonutSmp")) {
                try {
                    UltimateDonutSmp donut = UltimateDonutSmp.getInstance();
                    if (donut != null && donut.getPlayerDataManager() != null) {
                        PlayerData data = donut.getPlayerDataManager().get(uuid);
                        if (data != null) {
                            settings.setScoreboardEnabled(data.isScoreboardVisible());
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return settings;
    }

    public void saveSettings(UUID uuid) {
        ScoreboardSettings settings = settingsCache.get(uuid);
        if (settings == null) return;

        boolean isScoreboard = settings.isScoreboardEnabled();
        boolean showMoney = settings.isShowMoney();
        boolean showShards = settings.isShowShards();
        boolean showKills = settings.isShowKills();
        boolean showDeaths = settings.isShowDeaths();
        boolean showPlaytime = settings.isShowPlaytime();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File file = new File(dataFolder, uuid.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            config.set("scoreboardEnabled", isScoreboard);
            config.set("showMoney", showMoney);
            config.set("showShards", showShards);
            config.set("showKills", showKills);
            config.set("showDeaths", showDeaths);
            config.set("showPlaytime", showPlaytime);

            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Gagal menyimpan scoreboard settings untuk " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void saveSettingsSync(UUID uuid) {
        ScoreboardSettings settings = settingsCache.get(uuid);
        if (settings == null) return;

        File file = new File(dataFolder, uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("scoreboardEnabled", settings.isScoreboardEnabled());
        config.set("showMoney", settings.isShowMoney());
        config.set("showShards", settings.isShowShards());
        config.set("showKills", settings.isShowKills());
        config.set("showDeaths", settings.isShowDeaths());
        config.set("showPlaytime", settings.isShowPlaytime());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan scoreboard settings untuk " + uuid + ": " + e.getMessage());
        }
    }

    public void toggleScoreboard(Player player) {
        ScoreboardSettings s = getSettings(player);
        s.setScoreboardEnabled(!s.isScoreboardEnabled());
        saveSettings(player.getUniqueId());
        plugin.getCustomScoreboardManager().updatePlayer(player);
    }

    public void toggleMoney(Player player) {
        ScoreboardSettings s = getSettings(player);
        s.setShowMoney(!s.isShowMoney());
        saveSettings(player.getUniqueId());
        plugin.getCustomScoreboardManager().updatePlayer(player);
    }

    public void toggleShards(Player player) {
        ScoreboardSettings s = getSettings(player);
        s.setShowShards(!s.isShowShards());
        saveSettings(player.getUniqueId());
        plugin.getCustomScoreboardManager().updatePlayer(player);
    }

    public void toggleKills(Player player) {
        ScoreboardSettings s = getSettings(player);
        s.setShowKills(!s.isShowKills());
        saveSettings(player.getUniqueId());
        plugin.getCustomScoreboardManager().updatePlayer(player);
    }

    public void toggleDeaths(Player player) {
        ScoreboardSettings s = getSettings(player);
        s.setShowDeaths(!s.isShowDeaths());
        saveSettings(player.getUniqueId());
        plugin.getCustomScoreboardManager().updatePlayer(player);
    }

    public void togglePlaytime(Player player) {
        ScoreboardSettings s = getSettings(player);
        s.setShowPlaytime(!s.isShowPlaytime());
        saveSettings(player.getUniqueId());
        plugin.getCustomScoreboardManager().updatePlayer(player);
    }

    public void saveAll() {
        for (UUID uuid : settingsCache.keySet()) {
            saveSettingsSync(uuid);
        }
    }
}

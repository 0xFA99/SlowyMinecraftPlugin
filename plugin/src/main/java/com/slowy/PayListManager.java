package com.slowy;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PayListManager {

    private final SlowyPlugin plugin;
    private final File dataFolder;
    private final Map<UUID, List<String>> payListsCache = new ConcurrentHashMap<>();

    public PayListManager(SlowyPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "paylists");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public List<String> getPayList(UUID uuid) {
        return payListsCache.computeIfAbsent(uuid, this::loadPayList);
    }

    public List<String> getPayList(Player player) {
        return getPayList(player.getUniqueId());
    }

    private List<String> loadPayList(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<String> list = config.getStringList("players");
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    public boolean addPlayer(UUID uuid, String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }
        String cleanName = playerName.trim();
        List<String> list = getPayList(uuid);
        for (String s : list) {
            if (s.equalsIgnoreCase(cleanName)) {
                return false;
            }
        }
        list.add(cleanName);
        savePayList(uuid);
        return true;
    }

    public boolean removePlayer(UUID uuid, String playerName) {
        List<String> list = getPayList(uuid);
        boolean removed = list.removeIf(s -> s.equalsIgnoreCase(playerName.trim()));
        if (removed) {
            savePayList(uuid);
        }
        return removed;
    }

    public void savePayList(UUID uuid) {
        List<String> list = payListsCache.get(uuid);
        if (list == null) return;
        List<String> snapshot = new ArrayList<>(list);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File file = new File(dataFolder, uuid.toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            config.set("players", snapshot);
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Gagal menyimpan pay list untuk " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void saveAllSync() {
        for (Map.Entry<UUID, List<String>> entry : payListsCache.entrySet()) {
            File file = new File(dataFolder, entry.getKey().toString() + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            config.set("players", new ArrayList<>(entry.getValue()));
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Gagal menyimpan pay list untuk " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }
}

package com.slowy;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatSettingsManager {

    private final SlowyPlugin plugin;
    private final File dataFolder;
    private final Map<UUID, ChatSettings> settingsCache = new ConcurrentHashMap<>();

    public ChatSettingsManager(SlowyPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public ChatSettings getSettings(UUID uuid) {
        return settingsCache.computeIfAbsent(uuid, this::loadSettings);
    }

    public ChatSettings getSettings(Player player) {
        return getSettings(player.getUniqueId());
    }

    private ChatSettings loadSettings(UUID uuid) {
        ChatSettings settings = new ChatSettings();
        File file = new File(dataFolder, uuid.toString() + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            settings.setPublicChat(config.getBoolean("chatPublic", true));
            settings.setPrivateMessages(ChatVisibility.fromString(config.getString("chatPrivate"), ChatVisibility.ANYONE));
            settings.setServerChatMessages(config.getBoolean("chatServer", true));
            settings.setServerHotbarMessages(config.getBoolean("chatHotbar", true));
            settings.setDeathMessages(ChatVisibility.fromString(config.getString("chatDeath"), ChatVisibility.ANYONE));
            settings.setAdvancementMessages(ChatVisibility.fromString(config.getString("chatAdvancement"), ChatVisibility.FRIENDS));
            settings.setJoinLeaveMessages(ChatVisibility.fromString(config.getString("chatJoinLeave"), ChatVisibility.FRIENDS));
        }
        return settings;
    }

    public void saveSettings(UUID uuid) {
        ChatSettings settings = settingsCache.get(uuid);
        if (settings == null) return;

        boolean publicChat = settings.isPublicChat();
        String privateMessages = settings.getPrivateMessages().name();
        boolean serverChat = settings.isServerChatMessages();
        boolean serverHotbar = settings.isServerHotbarMessages();
        String deathMessages = settings.getDeathMessages().name();
        String advancementMessages = settings.getAdvancementMessages().name();
        String joinLeaveMessages = settings.getJoinLeaveMessages().name();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            File file = new File(dataFolder, uuid.toString() + ".yml");
            YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            config.set("chatPublic", publicChat);
            config.set("chatPrivate", privateMessages);
            config.set("chatServer", serverChat);
            config.set("chatHotbar", serverHotbar);
            config.set("chatDeath", deathMessages);
            config.set("chatAdvancement", advancementMessages);
            config.set("chatJoinLeave", joinLeaveMessages);

            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Gagal menyimpan chat settings untuk " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void saveSettingsSync(UUID uuid) {
        ChatSettings settings = settingsCache.get(uuid);
        if (settings == null) return;

        File file = new File(dataFolder, uuid.toString() + ".yml");
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set("chatPublic", settings.isPublicChat());
        config.set("chatPrivate", settings.getPrivateMessages().name());
        config.set("chatServer", settings.isServerChatMessages());
        config.set("chatHotbar", settings.isServerHotbarMessages());
        config.set("chatDeath", settings.getDeathMessages().name());
        config.set("chatAdvancement", settings.getAdvancementMessages().name());
        config.set("chatJoinLeave", settings.getJoinLeaveMessages().name());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan chat settings untuk " + uuid + ": " + e.getMessage());
        }
    }

    public void togglePublicChat(Player player) {
        ChatSettings s = getSettings(player);
        s.setPublicChat(!s.isPublicChat());
        saveSettings(player.getUniqueId());
    }

    public void togglePrivateMessages(Player player) {
        ChatSettings s = getSettings(player);
        s.setPrivateMessages(s.getPrivateMessages().next());
        saveSettings(player.getUniqueId());
    }

    public void toggleServerChat(Player player) {
        ChatSettings s = getSettings(player);
        s.setServerChatMessages(!s.isServerChatMessages());
        saveSettings(player.getUniqueId());
    }

    public void toggleServerHotbar(Player player) {
        ChatSettings s = getSettings(player);
        s.setServerHotbarMessages(!s.isServerHotbarMessages());
        saveSettings(player.getUniqueId());
    }

    public void toggleDeathMessages(Player player) {
        ChatSettings s = getSettings(player);
        s.setDeathMessages(s.getDeathMessages().next());
        saveSettings(player.getUniqueId());
    }

    public void toggleAdvancementMessages(Player player) {
        ChatSettings s = getSettings(player);
        s.setAdvancementMessages(s.getAdvancementMessages().next());
        saveSettings(player.getUniqueId());
    }

    public void toggleJoinLeaveMessages(Player player) {
        ChatSettings s = getSettings(player);
        s.setJoinLeaveMessages(s.getJoinLeaveMessages().next());
        saveSettings(player.getUniqueId());
    }

    public boolean isFriend(UUID u1, UUID u2) {
        if (u1.equals(u2)) return true;
        if (Bukkit.getPluginManager().isPluginEnabled("UltimateDonutSmp")) {
            try {
                UltimateDonutSmp donut = UltimateDonutSmp.getInstance();
                if (donut != null && donut.getFriendsManager() != null) {
                    return donut.getFriendsManager().isFriend(u1, u2);
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public void saveAll() {
        for (UUID uuid : settingsCache.keySet()) {
            saveSettingsSync(uuid);
        }
    }
}

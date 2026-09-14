package dev.slowy.core.crate;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@NullMarked
public final class KeyAllManager {

    private final SlowyCore plugin;
    private final CrateManager crateManager;
    private final DatabaseManager db;
    private final Map<UUID, Integer> countdowns = new ConcurrentHashMap<>();

    public KeyAllManager(SlowyCore plugin, CrateManager crateManager, DatabaseManager db) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.crateManager = Objects.requireNonNull(crateManager, "crateManager cannot be null");
        this.db = Objects.requireNonNull(db, "db cannot be null");

        for (Player p : Bukkit.getOnlinePlayers()) {
            handlePlayerJoin(p);
        }
    }

    public void tickSecond() {
        if (!CoreConfig.KEYALL_ENABLED) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (CoreConfig.KEYALL_REQUIRE_NOT_AFK && player.getWorld().getName().equalsIgnoreCase(CoreConfig.AFK_WORLD_NAME)) {
                continue;
            }

            UUID uuid = player.getUniqueId();
            int remaining = countdowns.compute(uuid, (_, current) -> {
                int val = (current == null ? CoreConfig.KEYALL_INTERVAL_SECONDS : current) - 1;
                if (val <= 0) {
                    player.getScheduler().run(plugin, _ -> rewardPlayer(player), null);
                    return CoreConfig.KEYALL_INTERVAL_SECONDS;
                }
                return val;
            });
        }
    }

    public void rewardPlayer(Player player) {
        String crateId = selectKeyReward();
        CrateDefinition crate = crateManager.getCrate(crateId);
        if (crate == null) {
            crate = crateManager.getCrate("common");
        }
        if (crate == null) return;

        int amount = 1;
        crateManager.addKeys(player.getUniqueId(), crate.getId(), amount);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.sendActionBar(ColorUtils.parse("<#1DA1F2>You received <#FFE600>" + amount + " " + crate.getFormattedKeyName() + "</#FFE600></#1DA1F2>"));

        if (crateManager.getHologramManager() != null) {
            crateManager.getHologramManager().updateForPlayer(player);
        }
    }

    public String selectKeyReward() {
        int totalWeight = 0;
        for (int w : CoreConfig.KEYALL_WEIGHTS.values()) {
            totalWeight += w;
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        for (Map.Entry<String, Integer> entry : CoreConfig.KEYALL_WEIGHTS.entrySet()) {
            current += entry.getValue();
            if (roll < current) {
                return entry.getKey();
            }
        }
        return "common";
    }

    public int getRemainingSeconds(UUID uuid) {
        return countdowns.getOrDefault(uuid, CoreConfig.KEYALL_INTERVAL_SECONDS);
    }

    public void handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement("SELECT remaining_seconds FROM player_keyall WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int rem = rs.getInt("remaining_seconds");
                        countdowns.put(uuid, Math.max(1, rem));
                        return;
                    }
                }
            } catch (SQLException e) {
                plugin.getSlf4jLogger().warn("Failed loading player_keyall for {}: {}", uuid, e.getMessage());
            }
            countdowns.put(uuid, CoreConfig.KEYALL_INTERVAL_SECONDS);
        });
    }

    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Integer remaining = countdowns.remove(uuid);
        if (remaining != null) {
            saveCountdown(uuid, remaining);
        }
    }

    public void triggerKeyAllNow() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            rewardPlayer(p);
            countdowns.put(p.getUniqueId(), CoreConfig.KEYALL_INTERVAL_SECONDS);
        }
    }

    private void saveCountdown(UUID uuid, int remaining) {
        long now = System.currentTimeMillis();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO player_keyall (player_uuid, remaining_seconds, updated_at) VALUES (?, ?, ?) " +
                                 "ON CONFLICT(player_uuid) DO UPDATE SET remaining_seconds = excluded.remaining_seconds, updated_at = excluded.updated_at")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, remaining);
                ps.setLong(3, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getSlf4jLogger().warn("Failed saving player_keyall for {}: {}", uuid, e.getMessage());
            }
        });
    }

    public void onDisable() {
        if (countdowns.isEmpty()) return;

        long now = System.currentTimeMillis();
        // Batch DB writing on disable for extreme speed (1 connection, 1 batch)
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO player_keyall (player_uuid, remaining_seconds, updated_at) VALUES (?, ?, ?) " +
                             "ON CONFLICT(player_uuid) DO UPDATE SET remaining_seconds = excluded.remaining_seconds, updated_at = excluded.updated_at")) {

            for (Map.Entry<UUID, Integer> entry : countdowns.entrySet()) {
                ps.setString(1, entry.getKey().toString());
                ps.setInt(2, entry.getValue());
                ps.setLong(3, now);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            plugin.getSlf4jLogger().error("Error in onDisable saving keyall: {}", e.getMessage());
        } finally {
            countdowns.clear();
        }
    }
}

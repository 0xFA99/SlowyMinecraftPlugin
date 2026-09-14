package dev.slowy.core.daily;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.hologram.HologramInstance;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern Daily Reward Engine for Slowy SMP.
 * Zero GUI, right-click NPC or execute /daily to claim.
 * Real-time dynamic per-player text display on the daily NPC hologram.
 */
@NullMarked
public final class DailyManager implements Lifecycle {

    private final SlowyCore plugin;
    private final DatabaseManager databaseManager;
    private final Logger logger;

    // 7-day cyclical rewards
    public static final List<DailyReward> REWARDS = List.of(
            new DailyReward(1, 1000.0, 10),
            new DailyReward(2, 2500.0, 25),
            new DailyReward(3, 5000.0, 40),
            new DailyReward(4, 8000.0, 60),
            new DailyReward(5, 12000.0, 80),
            new DailyReward(6, 18000.0, 120),
            new DailyReward(7, 35000.0, 250)
    );

    private final Map<UUID, DailyUserData> userCache = new ConcurrentHashMap<>();
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

    public DailyManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.logger = plugin.getSlf4jLogger();

        loadAllFromDatabase();
        logger.info("DailyManager initialized ({} user records loaded).", userCache.size());
    }

    private void loadAllFromDatabase() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT uuid, streak, last_claim, total_claims FROM daily_rewards;");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int streak = rs.getInt("streak");
                    long lastClaim = rs.getLong("last_claim");
                    int totalClaims = rs.getInt("total_claims");
                    userCache.put(uuid, new DailyUserData(uuid, streak, lastClaim, totalClaims));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load daily rewards: {}", e.getMessage(), e);
        }
    }

    public DailyUserData getUserData(UUID uuid) {
        return userCache.computeIfAbsent(uuid, id -> new DailyUserData(id, 0, 0, 0));
    }

    public DailyReward getReward(int day) {
        int idx = Math.clamp(day - 1, 0, REWARDS.size() - 1);
        return REWARDS.get(idx);
    }

    public record DailyStreakRecord(UUID uuid, String name, int streak) {}

    public List<DailyStreakRecord> getAllSortedStreaks() {
        List<DailyStreakRecord> list = new ArrayList<>(userCache.size());
        for (Map.Entry<UUID, DailyUserData> entry : userCache.entrySet()) {
            int streak = entry.getValue().getEffectiveStreak();
            if (streak <= 0) {
                streak = entry.getValue().getStreak();
            }
            if (streak <= 0) continue;

            UUID uuid = entry.getKey();
            String name = "Player";
            var acc = plugin.getEconomyManager().getAccount(uuid);
            if (acc != null && acc.getUsername() != null && !acc.getUsername().isBlank()) {
                name = acc.getUsername();
            } else {
                var op = Bukkit.getOfflinePlayer(uuid);
                if (op.getName() != null) {
                    name = op.getName();
                }
            }
            list.add(new DailyStreakRecord(uuid, name, streak));
        }
        list.sort((a, b) -> Integer.compare(b.streak(), a.streak()));
        return list;
    }

    /**
     * Executes daily reward claim for the given player.
     */
    public synchronized void claimDaily(Player player) {
        if (!player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        DailyUserData data = getUserData(uuid);

        if (data.isOnCooldown()) {
            String remaining = data.formatRemainingCooldown();
            player.sendActionBar(ColorUtils.parse("<red>Next reward in <yellow>" + remaining + "</yellow></red>"));
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            } catch (Exception ignored) {
            }
            return;
        }

        int currentEffectiveStreak = data.getEffectiveStreak();
        int dayToClaim = (currentEffectiveStreak % 7) + 1;
        DailyReward reward = getReward(dayToClaim);
        int newStreak = currentEffectiveStreak + 1;
        long now = System.currentTimeMillis();

        // Update in-memory state
        data.setStreak(newStreak);
        data.setLastClaim(now);
        data.incrementTotalClaims();

        // Persist to SQLite async
        databaseManager.executeUpdateAsync("""
                INSERT INTO daily_rewards (uuid, streak, last_claim, total_claims, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    streak = excluded.streak,
                    last_claim = excluded.last_claim,
                    total_claims = excluded.total_claims,
                    updated_at = excluded.updated_at;
                """, uuid.toString(), newStreak, now, data.getTotalClaims(), now);

        // Disburse rewards
        plugin.getEconomyManager().deposit(uuid, reward.money());
        plugin.getEconomyManager().depositShards(uuid, reward.shards());

        String formattedMoney = numberFormat.format(reward.money());

        // Audio & visual feedback
        try {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.2, 0), 16, 0.4, 0.4, 0.4, 0.1);
        } catch (Exception ignored) {
        }

        // Actionbar notification
        player.sendActionBar(ColorUtils.parse("<#39FF14>✔ Claimed Day " + dayToClaim + "! <#E0F8FF>+$"
                + formattedMoney + " <gray>|</gray> <#FF00BD>★ " + reward.shards() + "</#FF00BD></#E0F8FF></#39FF14>"));

        // Instant hologram update for this player
        HologramInstance holo = plugin.getHologramManager().getHologram("npc_daily");
        if (holo != null) holo.updateForPlayer(player, true);
        HologramInstance topDaily = plugin.getHologramManager().getHologram("top_daily");
        if (topDaily != null) topDaily.updateForPlayer(player, true);
        HologramInstance topStreak = plugin.getHologramManager().getHologram("top_streak");
        if (topStreak != null) topStreak.updateForPlayer(player, true);
    }

    /**
     * Renders the 4-line hologram text tailored specifically for viewer:
     * Line 1: /daily (normal, not bold, color #1DA1F2)
     * Line 2: X days
     * Line 3: $X | ★ X
     * Line 4: "claim" / <cooldown next day>
     */
    public String renderHologram(@Nullable Player viewer) {
        if (viewer == null) {
            DailyReward r1 = getReward(1);
            String formattedMoney = numberFormat.format(r1.money());
            return "<#1DA1F2>/ᴅᴀɪʟʏ</#1DA1F2>\n" +
                    "<#FFE600>0 Days</#FFE600>\n" +
                    "<#39FF14>$" + formattedMoney + "</#39FF14> <gray>|</gray> <#FF00BD>★ " + r1.shards() + "</#FF00BD>\n" +
                    "<#39FF14>claim</#39FF14>";
        }

        DailyUserData data = getUserData(viewer.getUniqueId());
        boolean onCooldown = data.isOnCooldown();
        int streak = data.getEffectiveStreak();

        if (!onCooldown) {
            // Belum claim hari ini: rewards siap di-claim (hijau & ungu), normal font
            int dayToClaim = (streak % 7) + 1;
            DailyReward reward = getReward(dayToClaim);
            String formattedMoney = numberFormat.format(reward.money());

            return "<#1DA1F2>/ᴅᴀɪʟʏ</#1DA1F2>\n" +
                    "<#FFE600>" + streak + " Days</#FFE600>\n" +
                    "<#39FF14>$" + formattedMoney + "</#39FF14> <gray>|</gray> <#FF00BD>★ " + reward.shards() + "</#FF00BD>\n" +
                    "<#39FF14>claim</#39FF14>";
        } else {
            // Sudah claim hari ini: rewards hari kedepan dan cooldown semuanya abu-abu, normal font
            int nextDay = (streak % 7) + 1;
            DailyReward nextReward = getReward(nextDay);
            String formattedMoney = numberFormat.format(nextReward.money());
            String cooldownStr = data.formatRemainingCooldown();

            return "<#1DA1F2>/ᴅᴀɪʟʏ</#1DA1F2>\n" +
                    "<#FFE600>" + streak + " Days</#FFE600>\n" +
                    "<gray>$" + formattedMoney + " | ★ " + nextReward.shards() + "</gray>\n" +
                    "<gray>" + cooldownStr + "</gray>";
        }
    }

    @Override
    public void onDisable() {
        userCache.clear();
        logger.info("DailyManager disabled.");
    }
}

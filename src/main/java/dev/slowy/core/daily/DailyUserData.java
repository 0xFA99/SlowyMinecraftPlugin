package dev.slowy.core.daily;

import dev.slowy.core.config.CoreConfig;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

/**
 * Tracks a player's daily reward streak, cooldown, and claim history.
 */
@NullMarked
public final class DailyUserData {

    private final UUID uuid;
    private int streak;
    private long lastClaim;
    private int totalClaims;

    public DailyUserData(UUID uuid, int streak, long lastClaim, int totalClaims) {
        this.uuid = uuid;
        this.streak = streak;
        this.lastClaim = lastClaim;
        this.totalClaims = totalClaims;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public long getLastClaim() {
        return lastClaim;
    }

    public void setLastClaim(long lastClaim) {
        this.lastClaim = lastClaim;
    }

    public int getTotalClaims() {
        return totalClaims;
    }

    public void incrementTotalClaims() {
        this.totalClaims++;
    }

    public boolean isStreakExpired() {
        if (lastClaim <= 0) return false;
        return (System.currentTimeMillis() - lastClaim) > CoreConfig.DAILY_STREAK_EXPIRE_MS;
    }

    public int getEffectiveStreak() {
        if (isStreakExpired()) {
            return 0;
        }
        return streak;
    }

    public boolean isOnCooldown() {
        if (lastClaim <= 0) return false;
        return (System.currentTimeMillis() - lastClaim) < CoreConfig.DAILY_COOLDOWN_MS;
    }

    public long getRemainingCooldownMs() {
        if (!isOnCooldown()) return 0L;
        return Math.max(0L, (lastClaim + CoreConfig.DAILY_COOLDOWN_MS) - System.currentTimeMillis());
    }

    public String formatRemainingCooldown() {
        long remMs = getRemainingCooldownMs();
        if (remMs <= 0) return "0s";

        long totalSeconds = remMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}

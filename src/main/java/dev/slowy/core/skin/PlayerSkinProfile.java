package dev.slowy.core.skin;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of a player's skin profile.
 * Preserves the player's original skin forever in the database,
 * while allowing a custom MineSkin to be applied and replaced.
 */
@NullMarked
public record PlayerSkinProfile(
        UUID uuid,
        SkinData originalSkin,
        @Nullable SkinData customSkin,
        boolean useCustom,
        long lastChangeTime,
        long updatedAt
) {

    public PlayerSkinProfile {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(originalSkin, "originalSkin cannot be null");
    }

    /**
     * Gets the active skin to display for the player.
     * Returns custom skin if enabled and valid, otherwise original skin.
     */
    public SkinData getActiveSkin() {
        if (useCustom && customSkin != null && customSkin.isValid()) {
            return customSkin;
        }
        return originalSkin;
    }

    /**
     * Checks if the player is currently on cooldown for changing custom skin.
     *
     * @param cooldownMs cooldown duration in milliseconds
     * @return true if cooldown is still active
     */
    public boolean isOnCooldown(long cooldownMs) {
        if (lastChangeTime <= 0 || cooldownMs <= 0) return false;
        return (System.currentTimeMillis() - lastChangeTime) < cooldownMs;
    }

    /**
     * Gets the remaining cooldown time in milliseconds.
     */
    public long getRemainingCooldown(long cooldownMs) {
        if (lastChangeTime <= 0 || cooldownMs <= 0) return 0L;
        long elapsed = System.currentTimeMillis() - lastChangeTime;
        return Math.max(0L, cooldownMs - elapsed);
    }
}

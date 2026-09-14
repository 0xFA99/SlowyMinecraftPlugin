package dev.slowy.core.daily;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable definition of a single day's daily reward in Slowy SMP.
 * Pure money and shards reward.
 */
@NullMarked
public record DailyReward(
        int day,
        double money,
        int shards
) {}

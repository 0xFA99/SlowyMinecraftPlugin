package dev.slowy.core.economy;

import org.jspecify.annotations.NullMarked;
import java.util.UUID;

@NullMarked
public record AccountSnapshot(
        UUID uuid,
        String username,
        double balance,
        long shards,
        int rewardProgress,
        double totalSold,
        int totalItemsSold,
        double totalSpent
) {}

package dev.slowy.core.teleport;

import org.jspecify.annotations.NullMarked;

import java.util.UUID;

@NullMarked
public record TeleportRequest(
        UUID senderUuid,
        String senderName,
        UUID targetUuid,
        String targetName,
        TeleportType type,
        long expireMillis
) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expireMillis;
    }
}

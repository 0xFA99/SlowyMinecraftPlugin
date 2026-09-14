package dev.slowy.core.guild;

import org.jspecify.annotations.NullMarked;

import java.util.UUID;

@NullMarked
public record GuildInvite(
        UUID guildId,
        String guildName,
        UUID inviterUuid,
        String inviterName,
        long expiresAt
) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}

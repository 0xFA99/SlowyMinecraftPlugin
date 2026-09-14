package dev.slowy.core.guild;

import org.jspecify.annotations.NullMarked;

import java.util.UUID;

@NullMarked
public final class GuildMember {

    private final UUID uuid;
    private final UUID guildId;
    private volatile GuildRole role;
    private final long joinedAt;

    public GuildMember(UUID uuid, UUID guildId, GuildRole role, long joinedAt) {
        this.uuid = uuid;
        this.guildId = guildId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public UUID getGuildId() {
        return guildId;
    }

    public GuildRole getRole() {
        return role;
    }

    public void setRole(GuildRole role) {
        this.role = role;
    }

    public long getJoinedAt() {
        return joinedAt;
    }
}

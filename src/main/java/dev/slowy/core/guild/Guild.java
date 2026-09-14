package dev.slowy.core.guild;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class Guild {

    private final UUID id;
    private volatile String name;
    private volatile UUID leaderUuid;
    private volatile String motd;
    private volatile int maxSlots;
    private final long createdAt;
    private final Map<UUID, GuildMember> members = new ConcurrentHashMap<>();

    public Guild(UUID id, String name, UUID leaderUuid, String motd, int maxSlots, long createdAt) {
        this.id = id;
        this.name = name;
        this.leaderUuid = leaderUuid;
        this.motd = motd;
        this.maxSlots = maxSlots;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public void setMaxSlots(int maxSlots) {
        this.maxSlots = maxSlots;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Map<UUID, GuildMember> getMembersMap() {
        return members;
    }

    public Collection<GuildMember> getMembers() {
        return members.values();
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public @Nullable GuildMember getMember(UUID uuid) {
        return members.get(uuid);
    }

    public void addMember(GuildMember member) {
        members.put(member.getUuid(), member);
    }

    public @Nullable GuildMember removeMember(UUID uuid) {
        return members.remove(uuid);
    }
}

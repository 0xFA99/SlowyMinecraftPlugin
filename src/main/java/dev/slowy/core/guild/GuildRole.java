package dev.slowy.core.guild;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Guild Role Hierarchy:
 * - LEADER: Top rank, full control, disband, rename, slot upgrade, invite, kick, demote/promote.
 * - OFFICER (Staff): Moderation rank, invite, kick members/duffers, demote/promote duffers/members, update MOTD.
 * - MEMBER: Standard rank, can chat in guild chat.
 * - DUFFER: Restricted rank, CANNOT chat in guild chat.
 */
@NullMarked
public enum GuildRole {
    LEADER(4, "Leader", "<#FFE600><bold>Leader</bold></#FFE600>"),
    OFFICER(3, "Officer", "<#00F5FF><bold>Officer</bold></#00F5FF>"), // Also known as Staff
    MEMBER(2, "Member", "<#39FF14>Member</#39FF14>"),
    DUFFER(1, "Duffer", "<gray>Duffer</gray>");

    private final int weight;
    private final String displayName;
    private final String formattedName;

    GuildRole(int weight, String displayName, String formattedName) {
        this.weight = weight;
        this.displayName = displayName;
        this.formattedName = formattedName;
    }

    public int getWeight() {
        return weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFormattedName() {
        return formattedName;
    }

    public boolean isAtLeast(GuildRole other) {
        return this.weight >= other.weight;
    }

    public boolean canChat() {
        return this != DUFFER;
    }

    public boolean canInvite() {
        return this == LEADER || this == OFFICER;
    }

    public boolean canUpdateMotd() {
        return this == LEADER || this == OFFICER;
    }

    public boolean canKick(GuildRole targetRole) {
        if (this == LEADER) {
            return targetRole != LEADER;
        }
        if (this == OFFICER) {
            return targetRole == MEMBER || targetRole == DUFFER;
        }
        return false;
    }

    public static GuildRole fromString(@Nullable String str) {
        if (str == null || str.isBlank()) return MEMBER;
        return switch (str.trim().toUpperCase(Locale.ROOT)) {
            case "LEADER", "OWNER" -> LEADER;
            case "OFFICER", "STAFF", "ADMIN" -> OFFICER;
            case "DUFFER" -> DUFFER;
            default -> MEMBER;
        };
    }
}

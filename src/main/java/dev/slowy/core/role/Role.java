package dev.slowy.core.role;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Three-tier server hierarchy for Slowy SMP:
 * - OWNER: Full administrative power, Op status, unrestricted access, can manage roles.
 * - STAFF: Quiet surveillance and moderation power, access to /sp and staff tools, cannot use /role.
 * - MEMBER: Standard player gameplay role, hidden from staff commands, default role.
 */
@NullMarked
public enum Role {
    OWNER(100, "Owner", "<#FF0055><bold>Owner</bold></#FF0055>"),
    STAFF(50, "Staff", "<#00F5FF><bold>Staff</bold></#00F5FF>"),
    MEMBER(10, "Member", "<gray>Member</gray>");

    private final int weight;
    private final String displayName;
    private final String formattedName;

    Role(int weight, String displayName, String formattedName) {
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

    public boolean isAtLeast(Role other) {
        return this.weight >= other.weight;
    }

    public static Role fromString(@Nullable String str) {
        if (str == null || str.isBlank()) return MEMBER;
        return switch (str.trim().toUpperCase(Locale.ROOT)) {
            case "OWNER" -> OWNER;
            case "STAFF", "ADMIN", "MOD", "MODERATOR" -> STAFF;
            default -> MEMBER;
        };
    }
}

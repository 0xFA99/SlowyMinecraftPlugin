package dev.slowy.core.auth;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

@NullMarked
public record AuthUser(
        UUID uuid,
        String username,
        String passwordHash,
        @Nullable String ip,
        long lastLogin,
        boolean isPremium,
        long createdAt
) {
    public AuthUser {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(passwordHash, "passwordHash cannot be null");
    }

    public AuthUser updateLoginSession(@Nullable String newIp, long loginTime) {
        return new AuthUser(uuid, username, passwordHash, newIp, loginTime, isPremium, createdAt);
    }
}

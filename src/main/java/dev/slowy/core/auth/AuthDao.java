package dev.slowy.core.auth;

import dev.slowy.core.storage.DatabaseManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@NullMarked
public final class AuthDao {

    private static final String SELECT_BY_USERNAME = """
        SELECT uuid, username, password_hash, ip, last_login, is_premium, created_at
        FROM auth WHERE username = ? COLLATE NOCASE LIMIT 1;
    """;

    private static final String SELECT_BY_UUID = """
        SELECT uuid, username, password_hash, ip, last_login, is_premium, created_at
        FROM auth WHERE uuid = ? LIMIT 1;
    """;

    private static final String UPSERT_USER = """
        INSERT INTO auth (uuid, username, password_hash, ip, last_login, is_premium, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET
            username = excluded.username,
            password_hash = excluded.password_hash,
            ip = excluded.ip,
            last_login = excluded.last_login,
            is_premium = excluded.is_premium;
    """;

    private static final String UPDATE_PASSWORD = """
        UPDATE auth SET password_hash = ? WHERE username = ? COLLATE NOCASE;
    """;

    private static final String DELETE_USER = """
        DELETE FROM auth WHERE username = ? COLLATE NOCASE;
    """;

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public AuthDao(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    public CompletableFuture<Optional<AuthUser>> getUserByUsernameAsync(String username) {
        return CompletableFuture.supplyAsync(() -> getUserByUsername(username));
    }

    public Optional<AuthUser> getUserByUsername(String username) {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_USERNAME)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error reading auth user by username '{}': {}", username, e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<AuthUser> getUserByUuid(UUID uuid) {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_UUID)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error reading auth user by uuid '{}': {}", uuid, e.getMessage());
        }
        return Optional.empty();
    }

    public boolean isRegistered(String username) {
        return getUserByUsername(username).isPresent();
    }

    public CompletableFuture<Void> saveUserAsync(AuthUser user) {
        return databaseManager.executeUpdateAsync(
                UPSERT_USER,
                user.uuid().toString(),
                user.username(),
                user.passwordHash(),
                user.ip(),
                user.lastLogin(),
                user.isPremium() ? 1 : 0,
                user.createdAt()
        );
    }

    public CompletableFuture<Void> updatePasswordAsync(String username, String newPasswordHash) {
        return databaseManager.executeUpdateAsync(UPDATE_PASSWORD, newPasswordHash, username);
    }

    public CompletableFuture<Void> deleteUserAsync(String username) {
        return databaseManager.executeUpdateAsync(DELETE_USER, username);
    }

    private AuthUser mapResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String username = rs.getString("username");
        String passwordHash = rs.getString("password_hash");
        @Nullable String ip = rs.getString("ip");
        long lastLogin = rs.getLong("last_login");
        boolean isPremium = rs.getInt("is_premium") == 1;
        long createdAt = rs.getLong("created_at");

        return new AuthUser(uuid, username, passwordHash, ip, lastLogin, isPremium, createdAt);
    }
}

package dev.slowy.core.storage.dao;

import dev.slowy.core.skin.PlayerSkinProfile;
import dev.slowy.core.skin.SkinData;
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
public final class SkinDao {

    private static final String SELECT_PLAYER_SQL = """
        SELECT original_name, original_value, original_signature, original_url,
               custom_name, custom_value, custom_signature, custom_url,
               has_custom, last_change_time, updated_at
        FROM player_skins WHERE uuid = ?
    """;

    private static final String UPSERT_PLAYER_SQL = """
        INSERT INTO player_skins (
            uuid, original_name, original_value, original_signature, original_url,
            custom_name, custom_value, custom_signature, custom_url,
            has_custom, last_change_time, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET
            original_name = excluded.original_name,
            original_value = excluded.original_value,
            original_signature = excluded.original_signature,
            original_url = excluded.original_url,
            custom_name = excluded.custom_name,
            custom_value = excluded.custom_value,
            custom_signature = excluded.custom_signature,
            custom_url = excluded.custom_url,
            has_custom = excluded.has_custom,
            last_change_time = excluded.last_change_time,
            updated_at = excluded.updated_at;
    """;

    private static final String UPSERT_LEGACY_SQL = """
        INSERT INTO skins (uuid, skin_name, value, signature, texture_url, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET
            skin_name = excluded.skin_name,
            value = excluded.value,
            signature = excluded.signature,
            texture_url = excluded.texture_url,
            updated_at = excluded.updated_at;
    """;

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public SkinDao(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        migrateLegacySkinsIfPresent();
    }

    private void migrateLegacySkinsIfPresent() {
        try (Connection con = databaseManager.getConnection()) {
            // Check if player_skins already has data
            try (PreparedStatement psCheck = con.prepareStatement("SELECT 1 FROM player_skins LIMIT 1");
                 ResultSet rsCheck = psCheck.executeQuery()) {
                if (rsCheck.next()) {
                    return; // Already has records, no migration needed
                }
            }

            // Read legacy skins table
            String query = "SELECT uuid, skin_name, value, signature, texture_url, updated_at FROM skins";
            try (PreparedStatement ps = con.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    String skinName = rs.getString("skin_name");
                    String val = rs.getString("value");
                    String sig = rs.getString("signature");
                    String url = rs.getString("texture_url");
                    long updatedAt = rs.getLong("updated_at");

                    try (PreparedStatement insert = con.prepareStatement(UPSERT_PLAYER_SQL)) {
                        insert.setString(1, uuidStr);
                        // Original defaults to Steve
                        insert.setString(2, "Steve");
                        insert.setString(3, SkinData.STEVE_SKIN.value());
                        insert.setString(4, SkinData.STEVE_SKIN.signature());
                        insert.setString(5, SkinData.STEVE_SKIN.textureUrl());
                        // Custom skin from legacy
                        insert.setString(6, skinName);
                        insert.setString(7, val);
                        insert.setString(8, sig);
                        insert.setString(9, url);
                        insert.setInt(10, 1); // has_custom = true
                        insert.setLong(11, updatedAt);
                        insert.setLong(12, updatedAt);
                        insert.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            logger.debug("Migration check for player_skins completed ({})", e.getMessage());
        }
    }

    public Optional<PlayerSkinProfile> getPlayerProfile(UUID uuid) {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_PLAYER_SQL)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SkinData original = new SkinData(
                            rs.getString("original_name"),
                            rs.getString("original_value"),
                            rs.getString("original_signature"),
                            rs.getString("original_url"),
                            0L
                    );

                    @Nullable SkinData custom = null;
                    String customVal = rs.getString("custom_value");
                    String customSig = rs.getString("custom_signature");
                    if (customVal != null && !customVal.isBlank() && customSig != null && !customSig.isBlank()) {
                        custom = new SkinData(
                                rs.getString("custom_name") != null ? rs.getString("custom_name") : "custom",
                                customVal,
                                customSig,
                                rs.getString("custom_url"),
                                rs.getLong("last_change_time")
                        );
                    }

                    boolean useCustom = rs.getInt("has_custom") == 1;
                    long lastChangeTime = rs.getLong("last_change_time");
                    long updatedAt = rs.getLong("updated_at");

                    return Optional.of(new PlayerSkinProfile(
                            uuid,
                            original,
                            custom,
                            useCustom,
                            lastChangeTime,
                            updatedAt
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error reading player skin profile for uuid {}: {}", uuid, e.getMessage());
        }
        return Optional.empty();
    }

    public CompletableFuture<Void> savePlayerProfileAsync(PlayerSkinProfile profile) {
        @Nullable SkinData custom = profile.customSkin();
        return databaseManager.executeUpdateAsync(
                UPSERT_PLAYER_SQL,
                profile.uuid().toString(),
                profile.originalSkin().name(),
                profile.originalSkin().value(),
                profile.originalSkin().signature(),
                profile.originalSkin().textureUrl(),
                custom != null ? custom.name() : null,
                custom != null ? custom.value() : null,
                custom != null ? custom.signature() : null,
                custom != null ? custom.textureUrl() : null,
                profile.useCustom() ? 1 : 0,
                profile.lastChangeTime(),
                profile.updatedAt() > 0 ? profile.updatedAt() : System.currentTimeMillis()
        );
    }

    public void savePlayerProfileSync(PlayerSkinProfile profile) {
        @Nullable SkinData custom = profile.customSkin();
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(UPSERT_PLAYER_SQL)) {
            ps.setString(1, profile.uuid().toString());
            ps.setString(2, profile.originalSkin().name());
            ps.setString(3, profile.originalSkin().value());
            ps.setString(4, profile.originalSkin().signature());
            ps.setString(5, profile.originalSkin().textureUrl());
            ps.setString(6, custom != null ? custom.name() : null);
            ps.setString(7, custom != null ? custom.value() : null);
            ps.setString(8, custom != null ? custom.signature() : null);
            ps.setString(9, custom != null ? custom.textureUrl() : null);
            ps.setInt(10, profile.useCustom() ? 1 : 0);
            ps.setLong(11, profile.lastChangeTime());
            ps.setLong(12, profile.updatedAt() > 0 ? profile.updatedAt() : System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving player skin profile for uuid {}: {}", profile.uuid(), e.getMessage());
        }
    }

    // ── Legacy Compatibility ───────────────────────────────────────────────

    public Optional<SkinData> getSkin(UUID uuid) {
        return getPlayerProfile(uuid).map(PlayerSkinProfile::getActiveSkin);
    }

    public Optional<SkinData> getSkinByName(String skinName) {
        String sql = "SELECT skin_name, value, signature, texture_url, updated_at FROM skins WHERE skin_name = ? COLLATE NOCASE LIMIT 1";
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, skinName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("skin_name");
                    String value = rs.getString("value");
                    String signature = rs.getString("signature");
                    @Nullable String url = rs.getString("texture_url");
                    long updatedAt = rs.getLong("updated_at");
                    return Optional.of(new SkinData(name, value, signature, url, updatedAt));
                }
            }
        } catch (SQLException e) {
            logger.error("Error reading skin by name {}: {}", skinName, e.getMessage());
        }
        return Optional.empty();
    }

    public CompletableFuture<Void> saveSkinAsync(UUID uuid, SkinData skin) {
        return databaseManager.executeUpdateAsync(
                UPSERT_LEGACY_SQL,
                uuid.toString(),
                skin.name(),
                skin.value(),
                skin.signature(),
                skin.textureUrl(),
                skin.updatedAt() > 0 ? skin.updatedAt() : System.currentTimeMillis()
        );
    }
}

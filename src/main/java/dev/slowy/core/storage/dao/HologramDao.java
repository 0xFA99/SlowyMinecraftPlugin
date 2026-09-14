package dev.slowy.core.storage.dao;

import dev.slowy.core.hologram.HologramData;
import dev.slowy.core.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HologramDao {

    private final DatabaseManager db;

    public HologramDao(DatabaseManager db) {
        this.db = Objects.requireNonNull(db, "db cannot be null");
    }

    /**
     * Dipanggil sinkron saat plugin enable / load awal server.
     */
    public Map<String, HologramData> loadAll() {
        Map<String, HologramData> map = new HashMap<>();
        String sql = "SELECT id, world, x, y, z, yaw, pitch, entity_uuid FROM holograms";

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id").toLowerCase();
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");
                String uuidStr = rs.getString("entity_uuid");
                UUID entityUuid = (uuidStr != null && !uuidStr.isBlank()) ? UUID.fromString(uuidStr) : null;

                map.put(id, new HologramData(id, world, x, y, z, yaw, pitch, entityUuid));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load holograms: " + e.getMessage(), e);
        }
        return map;
    }

    /**
     * Save non-blocking (kembalikan CompletableFuture).
     */
    public CompletableFuture<Void> saveAsync(HologramData data) {
        String sql = "INSERT OR REPLACE INTO holograms (id, world, x, y, z, yaw, pitch, entity_uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        return db.executeUpdateAsync(sql,
                data.id().toLowerCase(),
                data.worldName(),
                data.x(),
                data.y(),
                data.z(),
                data.yaw(),
                data.pitch(),
                data.entityUuid() != null ? data.entityUuid().toString() : null
        );
    }

    public CompletableFuture<Void> updateEntityUuidAsync(String id, UUID entityUuid) {
        String sql = "UPDATE holograms SET entity_uuid = ? WHERE id = ?";
        return db.executeUpdateAsync(sql, entityUuid != null ? entityUuid.toString() : null, id.toLowerCase());
    }

    public CompletableFuture<Void> deleteAsync(String id) {
        String sql = "DELETE FROM holograms WHERE id = ?";
        return db.executeUpdateAsync(sql, id.toLowerCase());
    }
}

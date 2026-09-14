package dev.slowy.core.guild;

import dev.slowy.core.storage.DatabaseManager;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@NullMarked
public final class GuildDao {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public GuildDao(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    /**
     * Load all guilds and their members synchronously at startup.
     */
    public Map<UUID, Guild> loadAllGuilds() {
        Map<UUID, Guild> guilds = new HashMap<>();

        try (Connection con = databaseManager.getConnection()) {
            // 1. Load guilds
            try (PreparedStatement ps = con.prepareStatement("SELECT id, name, leader_uuid, motd, max_slots, created_at FROM guilds;");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID id = UUID.fromString(rs.getString("id"));
                        String name = rs.getString("name");
                        UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
                        String motd = rs.getString("motd");
                        int maxSlots = rs.getInt("max_slots");
                        long createdAt = rs.getLong("created_at");

                        Guild guild = new Guild(id, name, leaderUuid, motd, maxSlots, createdAt);
                        guilds.put(id, guild);
                    } catch (IllegalArgumentException e) {
                        logger.error("Failed parsing guild record: {}", e.getMessage());
                    }
                }
            }

            // 2. Load members
            try (PreparedStatement ps = con.prepareStatement("SELECT uuid, guild_id, role, joined_at FROM guild_members;");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID memberUuid = UUID.fromString(rs.getString("uuid"));
                        UUID guildId = UUID.fromString(rs.getString("guild_id"));
                        GuildRole role = GuildRole.fromString(rs.getString("role"));
                        long joinedAt = rs.getLong("joined_at");

                        Guild guild = guilds.get(guildId);
                        if (guild != null) {
                            guild.addMember(new GuildMember(memberUuid, guildId, role, joinedAt));
                        } else {
                            logger.warn("Found orphaned guild member {} for non-existent guild {}", memberUuid, guildId);
                        }
                    } catch (IllegalArgumentException e) {
                        logger.error("Failed parsing guild member record: {}", e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error loading guilds from database: {}", e.getMessage(), e);
        }

        return guilds;
    }

    public void insertGuildAsync(Guild guild) {
        databaseManager.executeUpdateAsync(
                "INSERT INTO guilds (id, name, leader_uuid, motd, max_slots, created_at) VALUES (?, ?, ?, ?, ?, ?);",
                guild.getId().toString(),
                guild.getName(),
                guild.getLeaderUuid().toString(),
                guild.getMotd(),
                guild.getMaxSlots(),
                guild.getCreatedAt()
        );
    }

    public void deleteGuildAsync(UUID guildId) {
        String idStr = guildId.toString();
        databaseManager.executeUpdateAsync("DELETE FROM guild_members WHERE guild_id = ?;", idStr);
        databaseManager.executeUpdateAsync("DELETE FROM guilds WHERE id = ?;", idStr);
    }

    public void updateGuildNameAsync(UUID guildId, String newName) {
        databaseManager.executeUpdateAsync(
                "UPDATE guilds SET name = ? WHERE id = ?;",
                newName, guildId.toString()
        );
    }

    public void updateGuildLeaderAsync(UUID guildId, UUID newLeaderUuid) {
        databaseManager.executeUpdateAsync(
                "UPDATE guilds SET leader_uuid = ? WHERE id = ?;",
                newLeaderUuid.toString(), guildId.toString()
        );
    }

    public void updateGuildMotdAsync(UUID guildId, String motd) {
        databaseManager.executeUpdateAsync(
                "UPDATE guilds SET motd = ? WHERE id = ?;",
                motd, guildId.toString()
        );
    }

    public void updateGuildMaxSlotsAsync(UUID guildId, int maxSlots) {
        databaseManager.executeUpdateAsync(
                "UPDATE guilds SET max_slots = ? WHERE id = ?;",
                maxSlots, guildId.toString()
        );
    }

    public void insertMemberAsync(GuildMember member) {
        databaseManager.executeUpdateAsync(
                "INSERT OR REPLACE INTO guild_members (uuid, guild_id, role, joined_at) VALUES (?, ?, ?, ?);",
                member.getUuid().toString(),
                member.getGuildId().toString(),
                member.getRole().name(),
                member.getJoinedAt()
        );
    }

    public void deleteMemberAsync(UUID memberUuid) {
        databaseManager.executeUpdateAsync(
                "DELETE FROM guild_members WHERE uuid = ?;",
                memberUuid.toString()
        );
    }

    public void updateMemberRoleAsync(UUID memberUuid, GuildRole newRole) {
        databaseManager.executeUpdateAsync(
                "UPDATE guild_members SET role = ? WHERE uuid = ?;",
                newRole.name(), memberUuid.toString()
        );
    }

    public void insertAuditLogAsync(String action, UUID guildId, String guildName, UUID actorUuid, String actorName, String details) {
        databaseManager.executeUpdateAsync(
                "INSERT INTO guild_audit_logs (timestamp, action, guild_id, guild_name, actor_uuid, actor_name, details) VALUES (?, ?, ?, ?, ?, ?, ?);",
                System.currentTimeMillis(),
                action,
                guildId.toString(),
                guildName,
                actorUuid.toString(),
                actorName,
                details
        );
    }

    public List<GuildAuditLog> getAuditLogs(int limit, int offset) {
        List<GuildAuditLog> logs = new ArrayList<>();
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, timestamp, action, guild_id, guild_name, actor_uuid, actor_name, details FROM guild_audit_logs ORDER BY id DESC LIMIT ? OFFSET ?;")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new GuildAuditLog(
                            rs.getLong("id"),
                            rs.getLong("timestamp"),
                            rs.getString("action"),
                            rs.getString("guild_id"),
                            rs.getString("guild_name"),
                            rs.getString("actor_uuid"),
                            rs.getString("actor_name"),
                            rs.getString("details")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed fetching guild audit logs: {}", e.getMessage(), e);
        }
        return logs;
    }

    public int getAuditLogCount() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM guild_audit_logs;");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed counting guild audit logs: {}", e.getMessage(), e);
        }
        return 0;
    }
}

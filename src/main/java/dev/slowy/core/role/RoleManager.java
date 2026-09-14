package dev.slowy.core.role;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native Role & Permission Engine for Slowy SMP.
 * Completely replaces external permission plugins (e.g. LuckPerms).
 * Provides lightning-fast in-memory cached checks and SQLite persistence.
 */
@NullMarked
public final class RoleManager implements Lifecycle {

    private final SlowyCore plugin;
    private final DatabaseManager databaseManager;
    private final Logger logger;

    // Fast in-memory cache: UUID -> Role
    private final Map<UUID, Role> roleCache = new ConcurrentHashMap<>();

    // Active Bukkit permission attachments: UUID -> PermissionAttachment
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public RoleManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.logger = plugin.getSlf4jLogger();

        loadAllRolesFromDatabase();
        logger.info("RoleManager initialized ({} cached player roles).", roleCache.size());
    }

    /**
     * Pre-cache all assigned roles from SQLite at startup.
     */
    private void loadAllRolesFromDatabase() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT uuid, role FROM player_roles;");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    Role role = Role.fromString(rs.getString("role"));
                    roleCache.put(uuid, role);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load roles from database: {}", e.getMessage(), e);
        }
    }

    /**
     * Get player's role from memory cache. Defaults to Role.MEMBER if not present.
     */
    public Role getRole(UUID uuid) {
        return roleCache.getOrDefault(uuid, Role.MEMBER);
    }

    public Role getRole(Player player) {
        return getRole(player.getUniqueId());
    }

    /**
     * Check if sender has OWNER authority.
     * Console always returns true.
     */
    public boolean isOwner(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        if (sender instanceof Player player) {
            return getRole(player) == Role.OWNER;
        }
        return false;
    }

    /**
     * Check if sender has at least STAFF authority (STAFF or OWNER).
     * Console always returns true.
     */
    public boolean isStaff(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        if (sender instanceof Player player) {
            return getRole(player).isAtLeast(Role.STAFF);
        }
        return false;
    }

    public boolean isOwner(Player player) {
        return getRole(player) == Role.OWNER;
    }

    public boolean isStaff(Player player) {
        return getRole(player).isAtLeast(Role.STAFF);
    }

    /**
     * Called on PlayerJoinEvent to initialize permissions.
     */
    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        // Load role if not cached yet
        if (!roleCache.containsKey(uuid)) {
            databaseManager.supplyAsync(con -> {
                try (PreparedStatement ps = con.prepareStatement("SELECT role FROM player_roles WHERE uuid = ?;")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return Role.fromString(rs.getString("role"));
                        }
                    }
                }
                return Role.MEMBER;
            }).thenAccept(role -> {
                roleCache.put(uuid, role);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        applyPermissions(player);
                    }
                });
            });
        } else {
            applyPermissions(player);
        }
    }

    /**
     * Called on PlayerQuitEvent to clean up permissions attachment.
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment att = attachments.remove(uuid);
        if (att != null) {
            try {
                player.removeAttachment(att);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Injects Bukkit permissions directly into player according to their assigned role.
     * MUST be called on the main server thread.
     */
    public void applyPermissions(Player player) {
        if (!player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        Role role = getRole(uuid);

        // Remove old attachment if present
        PermissionAttachment old = attachments.remove(uuid);
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (Exception ignored) {
            }
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        switch (role) {
            case OWNER -> {
                player.setOp(true);
                attachment.setPermission("*", true);
                attachment.setPermission("slowy.*", true);
                attachment.setPermission("slowy.owner", true);
                attachment.setPermission("slowy.staff", true);
                attachment.setPermission("slowy.admin", true);
                attachment.setPermission("slowy.admin.clearlag", true);
                attachment.setPermission("slowy.skin.admin", true);
                attachment.setPermission("slowy.npc.admin", true);
                attachment.setPermission("slowy.owner.economy", true);
                attachment.setPermission("minecraft.command.*", true);
                attachment.setPermission("minecraft.command.gamemode", true);
                attachment.setPermission("minecraft.command.teleport", true);
            }
            case STAFF -> {
                if (player.isOp()) {
                    player.setOp(false);
                }
                attachment.setPermission("slowy.staff", true);
                attachment.setPermission("slowy.admin", true);
                attachment.setPermission("slowy.admin.clearlag", true);
                attachment.setPermission("slowy.skin.admin", true);
                attachment.setPermission("slowy.npc.admin", true);
                attachment.setPermission("minecraft.command.gamemode", true);
                attachment.setPermission("minecraft.command.teleport", true);
                // Explicitly disable owner permissions for staff
                attachment.setPermission("slowy.owner", false);
                attachment.setPermission("slowy.owner.economy", false);
            }
            case MEMBER -> {
                if (player.isOp()) {
                    player.setOp(false);
                }
                attachment.setPermission("slowy.member", true);
                attachment.setPermission("slowy.staff", false);
                attachment.setPermission("slowy.owner", false);
                attachment.setPermission("slowy.admin", false);
            }
        }

        // Send updated command tree packet to client
        player.updateCommands();
    }

    /**
     * Assign a role to a player UUID asynchronously, persist to SQLite,
     * and update online state if the player is connected.
     */
    public CompletableFuture<Void> setRoleAsync(UUID uuid, Role newRole) {
        roleCache.put(uuid, newRole);
        long now = System.currentTimeMillis();

        return databaseManager.executeUpdateAsync("""
                INSERT INTO player_roles (uuid, role, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET role = excluded.role, updated_at = excluded.updated_at;
                """, uuid.toString(), newRole.name(), now).thenRun(() -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (online.isOnline()) {
                        applyPermissions(online);
                    }
                });
            }
        });
    }

    /**
     * Find a player's UUID by name, searching online players, Auth database, or Bukkit offline players.
     */
    public CompletableFuture<@Nullable UUID> findUuidByNameAsync(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getUniqueId());
        }

        return databaseManager.supplyAsync(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT uuid FROM auth WHERE username = ? COLLATE NOCASE;")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("uuid"));
                    }
                }
            }
            return null;
        }).thenApply(foundUuid -> {
            if (foundUuid != null) {
                return foundUuid;
            }
            try {
                var offline = Bukkit.getOfflinePlayer(name);
                return offline.hasPlayedBefore() || offline.isOnline() ? offline.getUniqueId() : null;
            } catch (Exception e) {
                return null;
            }
        });
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PermissionAttachment att = attachments.remove(player.getUniqueId());
            if (att != null) {
                try {
                    player.removeAttachment(att);
                } catch (Exception ignored) {
                }
            }
        }
        attachments.clear();
        roleCache.clear();
        logger.info("RoleManager disabled (all permission attachments detached).");
    }
}

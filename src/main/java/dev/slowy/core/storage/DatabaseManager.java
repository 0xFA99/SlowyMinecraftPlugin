package dev.slowy.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import org.slf4j.Logger;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class DatabaseManager implements Lifecycle {

    private final SlowyCore plugin;
    private final Logger logger;
    private final File dbFile;
    private HikariDataSource dataSource;
    private final ExecutorService ioExecutor;

    public DatabaseManager(SlowyCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.dbFile = new File(plugin.getDataFolder(), "slowy.db");
        // Virtual threads sangat cocok untuk blocking I/O JDBC
        this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
        initDatabase();
    }

    private void initDatabase() {
        File parent = dbFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create directory: " + parent);
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("SlowyCore-SQLite-Pool");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        
        // SQLite WAL: 3-5 koneksi sangat cukup (1 writer aktif, sisanya reader)
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);

        // SQLite PRAGMA tuning
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("temp_store", "MEMORY");

        this.dataSource = new HikariDataSource(config);
        createTables();
        logger.info("DatabaseManager initialized with HikariCP & Virtual Threads (WAL mode).");
    }

    private void createTables() {
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS holograms (
                    id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL DEFAULT 0.0,
                    pitch REAL DEFAULT 0.0,
                    entity_uuid TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS economy (
                    uuid TEXT PRIMARY KEY,
                    username TEXT NOT NULL,
                    balance REAL NOT NULL DEFAULT 1000.0,
                    shards INTEGER NOT NULL DEFAULT 0,
                    reward_progress INTEGER NOT NULL DEFAULT 0,
                    total_sold REAL NOT NULL DEFAULT 0.0,
                    total_items_sold INTEGER NOT NULL DEFAULT 0,
                    total_spent REAL NOT NULL DEFAULT 0.0,
                    updated_at INTEGER NOT NULL
                );
            """);

            try { stmt.execute("ALTER TABLE economy ADD COLUMN total_sold REAL NOT NULL DEFAULT 0.0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE economy ADD COLUMN total_items_sold INTEGER NOT NULL DEFAULT 0;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE economy ADD COLUMN total_spent REAL NOT NULL DEFAULT 0.0;"); } catch (SQLException ignored) {}

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_economy_username ON economy(username COLLATE NOCASE);");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS blocks_mined (
                    uuid TEXT PRIMARY KEY,
                    count INTEGER NOT NULL DEFAULT 0
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS skins (
                    uuid TEXT PRIMARY KEY,
                    skin_name TEXT NOT NULL,
                    value TEXT NOT NULL,
                    signature TEXT NOT NULL,
                    texture_url TEXT,
                    updated_at INTEGER NOT NULL
                );
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_skins_name ON skins(skin_name COLLATE NOCASE);");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_skins (
                    uuid TEXT PRIMARY KEY,
                    original_name TEXT NOT NULL,
                    original_value TEXT NOT NULL,
                    original_signature TEXT NOT NULL,
                    original_url TEXT,
                    custom_name TEXT,
                    custom_value TEXT,
                    custom_signature TEXT,
                    custom_url TEXT,
                    has_custom INTEGER NOT NULL DEFAULT 0,
                    last_change_time INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS auth (
                    uuid TEXT PRIMARY KEY,
                    username TEXT NOT NULL COLLATE NOCASE,
                    password_hash TEXT NOT NULL,
                    ip TEXT,
                    last_login INTEGER NOT NULL,
                    is_premium INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                );
            """);

            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_auth_username ON auth(username COLLATE NOCASE);");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_settings (
                    uuid TEXT PRIMARY KEY,
                    scoreboard_enabled INTEGER NOT NULL DEFAULT 1,
                    updated_at INTEGER NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_worth_settings (
                    uuid TEXT PRIMARY KEY,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    updated_at INTEGER NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS npcs (
                    id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL DEFAULT 0.0,
                    pitch REAL DEFAULT 0.0,
                    command TEXT,
                    skin_name TEXT,
                    skin_value TEXT,
                    skin_signature TEXT,
                    look_at_player INTEGER NOT NULL DEFAULT 1
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS crate_blocks (
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    crate_id TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (world, x, y, z)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_crate_keys (
                    player_uuid TEXT NOT NULL,
                    crate_id TEXT NOT NULL,
                    amount INTEGER DEFAULT 0,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (player_uuid, crate_id)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_keyall (
                    player_uuid TEXT PRIMARY KEY,
                    remaining_seconds INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS auctions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller_uuid TEXT NOT NULL,
                    seller_name TEXT NOT NULL,
                    item_data BLOB NOT NULL,
                    price REAL NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    buyer_uuid TEXT,
                    buyer_name TEXT,
                    sold_at INTEGER DEFAULT 0
                );
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_auctions_status ON auctions(status);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions(seller_uuid, status);");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS auction_claims (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    item_data BLOB,
                    money REAL DEFAULT 0.0,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                );
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_auction_claims_player ON auction_claims(player_uuid);");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_roles (
                    uuid TEXT PRIMARY KEY,
                    role TEXT NOT NULL DEFAULT 'MEMBER',
                    updated_at INTEGER NOT NULL
                );
            """);

            // Seed default owner in player_roles if empty
            try (var rs = stmt.executeQuery("SELECT count(*) FROM player_roles;")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    long now = System.currentTimeMillis();
                    // Seed known owner UUIDs (online and offline variants)
                    String[] ownerUuids = {
                            "052f37fa-d7ef-3e76-bd62-07fc7f12cdbb", // Moccamorrii (online/LP)
                            "380a2aaf-2ae4-3724-a8ef-52cac822c9f2"  // moccamorrii (auth)
                    };
                    for (String ownerUuid : ownerUuids) {
                        try (var ps = con.prepareStatement(
                                "INSERT OR IGNORE INTO player_roles (uuid, role, updated_at) VALUES (?, 'OWNER', ?);")) {
                            ps.setString(1, ownerUuid);
                            ps.setLong(2, now);
                            ps.executeUpdate();
                        }
                    }
                    logger.info("Seeded default OWNER role into player_roles database.");
                }
            }

            // Seed default crate blocks if table is empty
            try (var rs = stmt.executeQuery("SELECT count(*) FROM crate_blocks;")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    long now = System.currentTimeMillis();
                    for (var loc : dev.slowy.core.config.CoreConfig.DEFAULT_CRATE_BLOCKS) {
                        try (var ps = con.prepareStatement(
                                "INSERT OR IGNORE INTO crate_blocks (world, x, y, z, crate_id, updated_at) VALUES (?, ?, ?, ?, ?, ?);")) {
                            ps.setString(1, loc.world());
                            ps.setInt(2, loc.x());
                            ps.setInt(3, loc.y());
                            ps.setInt(4, loc.z());
                            ps.setString(5, loc.crateId());
                            ps.setLong(6, now);
                            ps.executeUpdate();
                        }
                    }
                    logger.info("Seeded {} default crate blocks into database.", dev.slowy.core.config.CoreConfig.DEFAULT_CRATE_BLOCKS.size());
                }
            }

            // Migrate player_crate_keys from old database if empty
            try (var rs = stmt.executeQuery("SELECT count(*) FROM player_crate_keys;")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    File oldDb = new File(plugin.getServer().getWorldContainer(), "plugins/SlowyCore/slowy.db");
                    if (oldDb.exists()) {
                        try (Statement attachStmt = con.createStatement()) {
                            attachStmt.execute("ATTACH DATABASE '" + oldDb.getAbsolutePath().replace("'", "''") + "' AS old_core;");
                            attachStmt.execute("INSERT OR IGNORE INTO player_crate_keys SELECT * FROM old_core.player_crate_keys;");
                            attachStmt.execute("DETACH DATABASE old_core;");
                            logger.info("Migrated player_crate_keys from plugins/SlowyCore/slowy.db!");
                        } catch (Exception ex) {
                            logger.warn("Could not auto-migrate player_crate_keys: {}", ex.getMessage());
                        }
                    }
                }
            }

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS daily_rewards (
                    uuid TEXT PRIMARY KEY,
                    streak INTEGER NOT NULL DEFAULT 0,
                    last_claim INTEGER NOT NULL DEFAULT 0,
                    total_claims INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0
                );
            """);

            // Migrate daily_rewards from old database if empty
            try (var rs = stmt.executeQuery("SELECT count(*) FROM daily_rewards;")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    File oldDb = new File(plugin.getServer().getWorldContainer(), "plugins/SlowyCore/slowy.db");
                    if (oldDb.exists()) {
                        try (Statement attachStmt = con.createStatement()) {
                            attachStmt.execute("ATTACH DATABASE '" + oldDb.getAbsolutePath().replace("'", "''") + "' AS old_core;");
                            attachStmt.execute("INSERT OR IGNORE INTO daily_rewards (uuid, streak, last_claim, total_claims, updated_at) " +
                                    "SELECT uuid, streak, last_claim, total_claims, last_claim FROM old_core.daily_rewards;");
                            attachStmt.execute("DETACH DATABASE old_core;");
                            logger.info("Migrated daily_rewards from plugins/SlowyCore/slowy.db!");
                        } catch (Exception ex) {
                            logger.warn("Could not auto-migrate daily_rewards: {}", ex.getMessage());
                        }
                    }
                }
            }

            // Clean up duplicate portal_afk in holograms table if afk_zone exists
            try {
                stmt.execute("DELETE FROM holograms WHERE id = 'portal_afk' AND EXISTS (SELECT 1 FROM holograms WHERE id = 'afk_zone');");
            } catch (SQLException ignored) {}

            // Migrate blocks_mined from old database if empty
            try (var rs = stmt.executeQuery("SELECT count(*) FROM blocks_mined;")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    File oldDb = new File(plugin.getServer().getWorldContainer(), "plugins/SlowyCore/slowy.db");
                    if (oldDb.exists()) {
                        try (Statement attachStmt = con.createStatement()) {
                            attachStmt.execute("ATTACH DATABASE '" + oldDb.getAbsolutePath().replace("'", "''") + "' AS old_core;");
                            attachStmt.execute("INSERT OR IGNORE INTO blocks_mined SELECT uuid, count FROM old_core.blocks_mined;");
                            attachStmt.execute("DETACH DATABASE old_core;");
                            logger.info("Migrated blocks_mined from plugins/SlowyCore/slowy.db!");
                        } catch (Exception ex) {
                            logger.warn("Could not auto-migrate blocks_mined: {}", ex.getMessage());
                        }
                    }
                }
            }

            // Migrate economy total_sold, total_items_sold, total_spent from old database if total_sold sum is 0
            try (var rs = stmt.executeQuery("SELECT coalesce(sum(total_sold), 0) FROM economy;")) {
                if (rs.next() && rs.getDouble(1) == 0.0) {
                    File oldDb = new File(plugin.getServer().getWorldContainer(), "plugins/SlowyCore/slowy.db");
                    if (oldDb.exists()) {
                        try (Statement attachStmt = con.createStatement()) {
                            attachStmt.execute("ATTACH DATABASE '" + oldDb.getAbsolutePath().replace("'", "''") + "' AS old_core;");
                            attachStmt.execute("""
                                INSERT OR REPLACE INTO economy (uuid, username, balance, shards, reward_progress, total_sold, total_items_sold, total_spent, updated_at)
                                SELECT
                                    old.uuid,
                                    old.username,
                                    coalesce(old.balance, 1000.0),
                                    coalesce(old.shards, 0),
                                    coalesce(old.reward_progress, 0),
                                    coalesce(old.total_sold, 0.0),
                                    coalesce(old.total_items_sold, 0),
                                    coalesce(old.total_spent, 0.0),
                                    strftime('%s', 'now') * 1000
                                FROM old_core.economy old;
                            """);
                            attachStmt.execute("DETACH DATABASE old_core;");
                        } catch (Exception ex) {
                            logger.warn("Could not auto-migrate economy total_sold/spent: {}", ex.getMessage());
                        }
                    }
                }
            }

            // ── Guilds Tables ──
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guilds (
                    id TEXT PRIMARY KEY,
                    name TEXT UNIQUE NOT NULL COLLATE NOCASE,
                    leader_uuid TEXT NOT NULL,
                    motd TEXT NOT NULL,
                    max_slots INTEGER NOT NULL DEFAULT 15,
                    created_at INTEGER NOT NULL
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_guilds_name ON guilds(name COLLATE NOCASE);");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_members (
                    uuid TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    joined_at INTEGER NOT NULL
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_guild_members_guild ON guild_members(guild_id);");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    guild_id TEXT NOT NULL,
                    guild_name TEXT NOT NULL,
                    actor_uuid TEXT NOT NULL,
                    actor_name TEXT NOT NULL,
                    details TEXT
                );
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_guild_audit_logs_time ON guild_audit_logs(timestamp DESC);");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database tables", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Eksekusi update secara asynchronous menggunakan Virtual Threads.
     */
    public CompletableFuture<Void> executeUpdateAsync(String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                bindParams(ps, params);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.error("SQL Async Exec Error: {} | {}", sql, e.getMessage(), e);
                throw new RuntimeException("SQL execution failure: " + sql, e);
            }
        }, ioExecutor);
    }

    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    /**
     * Eksekusi query fungsional async (mengambil data tanpa membebani main thread).
     */
    public <T> CompletableFuture<T> supplyAsync(SqlFunction<Connection, T> queryFunction) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection con = getConnection()) {
                return queryFunction.apply(con);
            } catch (SQLException e) {
                logger.error("Database query error: {}", e.getMessage(), e);
                throw new RuntimeException("Database query error: " + e.getMessage(), e);
            }
        }, ioExecutor);
    }

    public static void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    @Override
    public void onDisable() {
        ioExecutor.close();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

package dev.slowy.core.storage.dao;

import dev.slowy.core.economy.Account;
import dev.slowy.core.economy.AccountSnapshot;
import dev.slowy.core.storage.DatabaseManager;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@NullMarked
public final class EconomyDao {

    private static final String UPSERT_SQL = """
        INSERT INTO economy (uuid, username, balance, shards, reward_progress, total_sold, total_items_sold, total_spent, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(uuid) DO UPDATE SET
            username = excluded.username,
            balance = excluded.balance,
            shards = excluded.shards,
            reward_progress = excluded.reward_progress,
            total_sold = excluded.total_sold,
            total_items_sold = excluded.total_items_sold,
            total_spent = excluded.total_spent,
            updated_at = excluded.updated_at;
    """;

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public EconomyDao(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    /**
     * Load sinkronus saat server bootstrap.
     */
    public Map<UUID, Account> loadAllSync() {
        Map<UUID, Account> map = new HashMap<>();
        String sql = "SELECT uuid, username, balance, shards, reward_progress, total_sold, total_items_sold, total_spent FROM economy";
        
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String username = rs.getString("username");
                    double balance = rs.getDouble("balance");
                    long shards = rs.getLong("shards");
                    int rewardProgress = rs.getInt("reward_progress");
                    double totalSold = rs.getDouble("total_sold");
                    int totalItemsSold = rs.getInt("total_items_sold");
                    double totalSpent = rs.getDouble("total_spent");

                    Account account = new Account(uuid, username, balance, shards, rewardProgress, totalSold, totalItemsSold, totalSpent);
                    map.put(uuid, account);
                } catch (Exception e) {
                    logger.error("Failed to parse economy record: {}", e.getMessage());
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to load economy accounts: {}", e.getMessage(), e);
            throw new RuntimeException("Economy bootstrap failed", e);
        }
        return map;
    }

    public CompletableFuture<Void> saveSnapshotsAsync(List<AccountSnapshot> snapshots) {
        return databaseManager.supplyAsync(con -> {
            executeBatch(con, snapshots);
            return null;
        });
    }

    public void saveAllSnapshotsSync(Collection<AccountSnapshot> snapshots) {
        if (snapshots.isEmpty()) return;
        try (Connection con = databaseManager.getConnection()) {
            executeBatch(con, snapshots);
        } catch (SQLException e) {
            logger.error("Failed to flush snapshots batch: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to flush snapshots batch", e);
        }
    }

    private void executeBatch(Connection con, Collection<AccountSnapshot> snapshots) throws SQLException {
        if (snapshots.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean initialAutoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try (PreparedStatement ps = con.prepareStatement(UPSERT_SQL)) {
            for (AccountSnapshot s : snapshots) {
                ps.setString(1, s.uuid().toString());
                ps.setString(2, s.username());
                ps.setDouble(3, s.balance());
                ps.setLong(4, s.shards());
                ps.setInt(5, s.rewardProgress());
                ps.setDouble(6, s.totalSold());
                ps.setInt(7, s.totalItemsSold());
                ps.setDouble(8, s.totalSpent());
                ps.setLong(9, now);
                ps.addBatch();
            }
            ps.executeBatch();
            con.commit();
        } finally {
            con.setAutoCommit(initialAutoCommit);
        }
    }
}

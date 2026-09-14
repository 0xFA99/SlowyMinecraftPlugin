package dev.slowy.core.stats;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.economy.Account;
import dev.slowy.core.storage.DatabaseManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@NullMarked
public final class BlockMinedManager implements Lifecycle, Listener {

    public record BlockMinedEntry(UUID uuid, String name, long count) {}

    private final SlowyCore plugin;
    private final DatabaseManager databaseManager;
    private final Logger logger;

    private final Map<UUID, Long> blocksCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyBlocks = ConcurrentHashMap.newKeySet();
    private @Nullable ScheduledTask autoSaveTask;

    public BlockMinedManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.logger = plugin.getSlf4jLogger();

        loadDataSynchronously();
        startAutoSaveTask();
    }

    private void loadDataSynchronously() {
        String sql = "SELECT uuid, count FROM blocks_mined";
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    long count = rs.getLong("count");
                    blocksCache.put(uuid, Math.max(0L, count));
                } catch (Exception ignored) {}
            }
            logger.info("Loaded {} block mining statistics records.", blocksCache.size());
        } catch (SQLException e) {
            logger.error("Failed to load blocks_mined records: {}", e.getMessage(), e);
        }
    }

    private void startAutoSaveTask() {
        this.autoSaveTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> flushDirtyBlocks(),
                30, 30, TimeUnit.SECONDS
        );
    }

    public void incrementBlocks(UUID uuid) {
        if (uuid == null) return;
        blocksCache.compute(uuid, (k, v) -> (v != null ? v : 0L) + 1L);
        dirtyBlocks.add(uuid);
    }

    public long getBlocksBroken(UUID uuid) {
        if (uuid == null) return 0L;
        return blocksCache.getOrDefault(uuid, 0L);
    }

    public List<BlockMinedEntry> getAllSorted() {
        List<BlockMinedEntry> list = new ArrayList<>(blocksCache.size());
        for (Map.Entry<UUID, Long> entry : blocksCache.entrySet()) {
            if (entry.getValue() <= 0) continue;
            UUID uuid = entry.getKey();
            String name = "Player";
            Account acc = plugin.getEconomyManager().getAccount(uuid);
            if (acc != null && acc.getUsername() != null && !acc.getUsername().isBlank()) {
                name = acc.getUsername();
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                if (op.getName() != null) {
                    name = op.getName();
                }
            }
            list.add(new BlockMinedEntry(uuid, name, entry.getValue()));
        }
        list.sort((a, b) -> Long.compare(b.count(), a.count()));
        return list;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return;
        }

        if (CoreConfig.isHubWorld(event.getBlock().getWorld())) {
            return;
        }

        incrementBlocks(player.getUniqueId());
    }

    public void flushDirtyBlocks() {
        if (dirtyBlocks.isEmpty()) return;

        Set<UUID> toFlush = new HashSet<>(dirtyBlocks);
        dirtyBlocks.removeAll(toFlush);

        String upsert = "INSERT INTO blocks_mined (uuid, count) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET count = excluded.count";

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(upsert)) {

            con.setAutoCommit(false);
            for (UUID uuid : toFlush) {
                Long count = blocksCache.get(uuid);
                if (count != null) {
                    ps.setString(1, uuid.toString());
                    ps.setLong(2, count);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            con.commit();
        } catch (SQLException e) {
            logger.warn("Failed to batch save blocks_mined: {}", e.getMessage());
            dirtyBlocks.addAll(toFlush); // Re-queue on failure
        }
    }

    @Override
    public void onDisable() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        flushDirtyBlocks();
        blocksCache.clear();
        dirtyBlocks.clear();
        logger.info("BlockMinedManager saved and disabled cleanly.");
    }
}

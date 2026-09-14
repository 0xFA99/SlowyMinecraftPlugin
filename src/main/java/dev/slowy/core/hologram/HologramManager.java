package dev.slowy.core.hologram;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.storage.dao.HologramDao;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class HologramManager implements Lifecycle {

    private final SlowyCore plugin;
    private final HologramDao dao;
    private final Map<String, HologramInstance> holograms = new ConcurrentHashMap<>();
    private final Map<Integer, HologramInstance> entityIdLookup = new ConcurrentHashMap<>();
    private ScheduledTask maintenanceTask;

    public HologramManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.dao = new HologramDao(databaseManager);
        loadAll();
        startMaintenanceTask();
    }

    public HologramDao getDao() { return dao; }

    public void registerEntity(int entityId, HologramInstance instance) {
        entityIdLookup.put(entityId, instance);
    }

    public void unregisterEntity(int entityId) {
        entityIdLookup.remove(entityId);
    }

    public boolean isKnownHologram(String id) {
        return id != null && holograms.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public @Nullable HologramInstance getHologram(String id) {
        if (id == null) return null;
        return holograms.get(id.toLowerCase(Locale.ROOT));
    }

    private void loadAll() {
        Map<String, HologramData> dataMap = dao.loadAll();
        for (HologramData data : dataMap.values()) {
            HologramInstance instance = new HologramInstance(plugin, data);
            holograms.put(data.id().toLowerCase(Locale.ROOT), instance);
        }

        if (holograms.containsKey("portal_afk") && holograms.containsKey("afk_zone")) {
            HologramInstance dup = holograms.remove("portal_afk");
            if (dup != null) dup.remove();
            dao.deleteAsync("portal_afk");
        }

        // Seeding default positions
        seedDefaultHoloIfMissing("portal_overworld", "spawn", -83.5, 72.2, -25.5, -89.1f, 8.4f);
        seedDefaultHoloIfMissing("portal_nether", "spawn", -70.5, 72.2, -38.5, 178.5f, 56.6f);
        seedDefaultHoloIfMissing("portal_the_end", "spawn", -70.5, 72.2, -12.5, 177.6f, 18.0f);
        seedDefaultHoloIfMissing("afk_zone", "spawn", 20.5, 71.2, 92.5, 168.1f, 90.0f);

        seedDefaultHoloIfMissing("top_money", "spawn", -22.5, 66.2, 7.5, 75.3f, 90.0f);
        seedDefaultHoloIfMissing("top_spent", "spawn", 7.5, 69.2, -91.5, 80.9f, 30.9f);
        seedDefaultHoloIfMissing("top_sell", "spawn", -8.5, 69.2, -91.5, -84.0f, 18.6f);
        seedDefaultHoloIfMissing("top_blocks", "spawn", -64.5, 70.7, -19.5, 60.5f, 70.6f);
        seedDefaultHoloIfMissing("top_mobs_killed", "spawn", -76.5, 70.7, -19.5, 75.8f, -46.7f);
        seedDefaultHoloIfMissing("top_deaths", "spawn", -64.5, 70.7, -31.5, 40.2f, 84.8f);
        seedDefaultHoloIfMissing("top_playtime", "spawn", -76.5, 70.7, -31.5, -100.5f, 27.3f);
        seedDefaultHoloIfMissing("top_shards", "afk_zone", 41.5, -59.0, 41.5, 0.0f, 0.0f);
        seedDefaultHoloIfMissing("top_daily", "spawn", -21.5, 66.2, -8.5, -9.0f, 85.4f);

        seedDefaultHoloIfMissing("greeting", "spawn", -9.5, 68.2, 0.5, -95.4f, 90.0f);
        seedDefaultHoloIfMissing("afk_info", "afk_zone", 41.5, -60.0, 41.5, 0.0f, 0.0f);

        seedDefaultHoloIfMissing("casual_pvp", "spawn", -66.5, 72.2, 23.5, 90.9f, 2.7f);
        seedDefaultHoloIfMissing("badlands_arena", "spawn", 34.999, 72.2, -106.7, -179.8f, 90.0f);
        seedDefaultHoloIfMissing("desert_arena", "spawn", 34.984, 72.2, -56.3, -3.6f, 60.3f);
        seedDefaultHoloIfMissing("flat_arena", "spawn", 54.7, 72.2, -94.0, -88.6f, 90.0f);
        seedDefaultHoloIfMissing("plains_arena", "spawn", 54.7, 72.2, -69.0, -89.7f, 90.0f);

        seedDefaultHoloIfMissing("npc_shop", "spawn", -0.5, 69.2, -96.5, 0.0f, 0.0f);
        seedDefaultHoloIfMissing("npc_discord", "spawn", -22.5, 65.2, 5.5, -89.1f, 90.0f);
        seedDefaultHoloIfMissing("npc_daily", "spawn", -21.5, 65.2, -5.5, -9.0f, 85.4f);
        seedDefaultHoloIfMissing("npc_rtp", "spawn", -20.5, 66.2, 15.5, -76.0f, 82.9f);
        seedDefaultHoloIfMissing("npc_auction", "spawn", -72.5, 72.2, 66.5, -78.6f, 90.0f);
    }

    private void seedDefaultHoloIfMissing(String id, String worldName, double x, double y, double z, float yaw, float pitch) {
        String key = id.toLowerCase(Locale.ROOT);
        if (!holograms.containsKey(key)) {
            HologramData data = new HologramData(key, worldName, x, y, z, yaw, pitch, null);
            dao.saveAsync(data);
            HologramInstance instance = new HologramInstance(plugin, data);
            holograms.put(key, instance);
        }
    }

    private void startMaintenanceTask() {
        if (maintenanceTask != null) maintenanceTask.cancel();

        this.maintenanceTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> {
                    for (HologramInstance instance : holograms.values()) {
                        instance.tick(plugin);
                        TextDisplay td = instance.getEntity();
                        if (td != null && td.isValid()) {
                            entityIdLookup.put(td.getEntityId(), instance);
                        }
                    }
                },
                1, 2, TimeUnit.SECONDS
        );
    }

    public void handlePlayerTrack(Player player, TextDisplay entity) {
        HologramInstance instance = getByEntity(entity);
        if (instance != null) {
            instance.onPlayerTrack(plugin, player);
        }
    }

    public void handlePlayerUntrack(Player player, TextDisplay entity) {
        HologramInstance instance = getByEntity(entity);
        if (instance != null) {
            instance.onPlayerUntrack(player);
        }
    }

    public @Nullable HologramInstance getByEntity(TextDisplay entity) {
        if (entity == null) return null;
        return entityIdLookup.get(entity.getEntityId());
    }

    public void updateForPlayer(Player player, boolean force) {
        if (player == null || !player.isOnline()) return;
        for (HologramInstance instance : holograms.values()) {
            instance.updateForPlayer(player, force);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        if (uuid == null) return;
        for (HologramInstance instance : holograms.values()) {
            instance.cleanupPlayer(uuid);
        }
    }

    public HologramInstance createOrSet(String id, Location location) {
        String key = id.toLowerCase(Locale.ROOT);
        HologramInstance existing = holograms.get(key);

        if (existing != null) {
            existing.remove();
            HologramData newData = HologramData.fromLocation(key, location, null);
            existing.setData(newData);
            dao.saveAsync(newData);
            existing.spawn(plugin);
            return existing;
        }

        HologramData data = HologramData.fromLocation(key, location, null);
        dao.saveAsync(data);

        HologramInstance instance = new HologramInstance(plugin, data);
        holograms.put(key, instance);
        instance.spawn(plugin);
        return instance;
    }

    public boolean remove(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        HologramInstance instance = holograms.remove(key);
        if (instance != null) {
            instance.remove();
            dao.deleteAsync(key);
            return true;
        }
        return false;
    }

    public @Nullable HologramInstance get(String id) {
        return holograms.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<HologramInstance> getAll() {
        return holograms.values();
    }

    public void sweepAll() {
        if (!plugin.isEnabled()) return;
        for (HologramInstance instance : holograms.values()) {
            Location loc = instance.getData().toLocation();
            if (loc != null && loc.getWorld() != null) {
                try {
                    plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> instance.sanitizeArea(loc));
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void onDisable() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
        for (HologramInstance instance : holograms.values()) {
            try {
                instance.remove();
            } catch (Throwable t) {
                plugin.getSlf4jLogger().warn("Error removing hologram {}: {}", instance.getData().id(), t.getMessage());
            }
        }
        entityIdLookup.clear();
        holograms.clear();
        plugin.getSlf4jLogger().info("HologramManager disabled cleanly.");
    }
}

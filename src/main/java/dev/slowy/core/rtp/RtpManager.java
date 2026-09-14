package dev.slowy.core.rtp;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.hologram.HologramInstance;
import dev.slowy.core.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Modern High-Performance RTP Engine for SlowyCore2.
 * - Single Master Heartbeat Ticker integration (no scattered BukkitRunnables).
 * - Rotated 3D bounding-box portal detection in world 'spawn'.
 * - Async chunk loading & pre-caching with 3x3 surrounding chunk preloading.
 * - Phased warmup with zero-lag block movement detection.
 */
@NullMarked
public final class RtpManager implements Lifecycle {

    public record WarmupSession(
            UUID uuid,
            Location startLoc,
            Location targetLoc,
            RtpWorldType targetType,
            long expireTick
    ) {}

    private final SlowyCore plugin;
    private final Logger logger;
    private final RtpMenu rtpMenu;

    // Portal tracking
    private final Map<UUID, RtpWorldType> currentZone = new ConcurrentHashMap<>();
    private final Map<UUID, Long> debounceUntilTick = new ConcurrentHashMap<>();
    private final Map<UUID, RtpWorldType> debounceTarget = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Location>> activeSearches = new ConcurrentHashMap<>();
    private final Map<UUID, WarmupSession> activeWarmups = new ConcurrentHashMap<>();

    // Cooldowns
    private final Map<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> exitCooldowns = new ConcurrentHashMap<>();

    // Pre-cached safe location pools
    private final Map<RtpWorldType, Queue<Location>> safeLocationCache = new ConcurrentHashMap<>();
    private final Set<RtpWorldType> refilling = ConcurrentHashMap.newKeySet();

    public RtpManager(SlowyCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.rtpMenu = new RtpMenu(plugin);

        initCache();
        logger.info("RtpManager initialized (3 spawn portals configured, cache prefill scheduled).");
    }

    public RtpMenu getRtpMenu() {
        return rtpMenu;
    }

    private void initCache() {
        for (RtpWorldType type : RtpWorldType.values()) {
            safeLocationCache.put(type, new ConcurrentLinkedQueue<>());
        }

        // Prefill cache setelah server startup
        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
            for (RtpWorldType type : RtpWorldType.values()) {
                refillCacheAsync(type);
            }
        }, CoreConfig.RTP_CACHE_PREFILL_STARTUP_DELAY_TICKS * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void refillCacheAsync(RtpWorldType type) {
        if (!refilling.add(type)) return;

        World world = resolveTargetWorld(type);
        if (world == null) {
            refilling.remove(type);
            return;
        }

        Queue<Location> queue = safeLocationCache.computeIfAbsent(type, k -> new ConcurrentLinkedQueue<>());
        if (queue.size() >= CoreConfig.RTP_MAX_CACHE_SIZE) {
            refilling.remove(type);
            return;
        }

        findSafeLocationAsync(world, null, 0).thenAccept(loc -> {
            if (loc != null) {
                queue.offer(loc);
            }
            refilling.remove(type);
            if (queue.size() < CoreConfig.RTP_MAX_CACHE_SIZE) {
                Bukkit.getAsyncScheduler().runDelayed(plugin, task -> refillCacheAsync(type),
                        CoreConfig.RTP_CACHE_REFILL_RETRY_TICKS * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }).exceptionally(t -> {
            refilling.remove(type);
            return null;
        });
    }

    /**
     * Dipanggil setiap tick oleh HeartbeatManager (20 TPS) tanpa membuat runnable terpisah.
     */
    public void tick(long currentTick) {
        // 1. Process portal entry debounces
        if (!debounceUntilTick.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = debounceUntilTick.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID uuid = entry.getKey();
                long trigger = entry.getValue();

                if (currentTick >= trigger) {
                    it.remove();
                    RtpWorldType target = debounceTarget.remove(uuid);
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline() && target != null && currentZone.containsKey(uuid)) {
                        startPortalRtp(player, target);
                    }
                }
            }
        }

        // 2. Process active warmup sessions
        if (!activeWarmups.isEmpty()) {
            Iterator<Map.Entry<UUID, WarmupSession>> it = activeWarmups.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, WarmupSession> entry = it.next();
                UUID uuid = entry.getKey();
                WarmupSession session = entry.getValue();

                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    it.remove();
                    returnLocationToCache(session.targetType(), session.targetLoc());
                    continue;
                }

                // Check movement (cancel jika berpindah blok)
                Location currentLoc = player.getLocation();
                if (currentLoc.getBlockX() != session.startLoc().getBlockX()
                        || currentLoc.getBlockY() != session.startLoc().getBlockY()
                        || currentLoc.getBlockZ() != session.startLoc().getBlockZ()) {
                    it.remove();
                    returnLocationToCache(session.targetType(), session.targetLoc());
                    player.sendActionBar(ColorUtils.parse(CoreConfig.RTP_MSG_CANCELLED_MOVED));
                    playSoundSafely(player, Sound.UI_BUTTON_CLICK, 0.8f);
                    continue;
                }

                // Hitung mundur tiap 20 ticks (1 detik)
                long remainingTicks = session.expireTick() - currentTick;
                if (remainingTicks > 0) {
                    if (remainingTicks % 20 == 0) {
                        int secondsLeft = (int) (remainingTicks / 20);
                        String msg = CoreConfig.RTP_MSG_WARMUP.replace("{seconds}", String.valueOf(secondsLeft));
                        player.sendActionBar(ColorUtils.parse(msg));
                        playSoundSafely(player, Sound.UI_BUTTON_CLICK, 1.0f);
                    }
                } else {
                    // Warmup selesai -> eksekusi teleport
                    it.remove();
                    executeTeleport(player, session.targetLoc(), session.targetType());
                }
            }
        }
    }

    /**
     * Dipanggil dari PlayerMoveEvent saat pemain berpindah blok.
     */
    public void handlePlayerMove(Player player, Location from, Location to) {
        if (!player.isOnline() || to == null) return;
        UUID uuid = player.getUniqueId();

        // 1. Cek pembatalan warmup jika sedang ada sesi teleport
        WarmupSession warmup = activeWarmups.get(uuid);
        if (warmup != null) {
            if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
                activeWarmups.remove(uuid);
                returnLocationToCache(warmup.targetType(), warmup.targetLoc());
                player.sendActionBar(ColorUtils.parse(CoreConfig.RTP_MSG_CANCELLED_MOVED));
                playSoundSafely(player, Sound.UI_BUTTON_CLICK, 0.8f);
            }
        }

        // 2. Portal detection jika berada di world spawn/hub
        if (!CoreConfig.isHubWorld(to.getWorld())) {
            if (currentZone.containsKey(uuid) || debounceUntilTick.containsKey(uuid)) {
                cancelPortal(player, false);
            }
            return;
        }

        RtpWorldType detected = detectPortalEntry(to);
        RtpWorldType active = currentZone.get(uuid);

        if (active == null && detected != null) {
            // CASE 1: Pemain baru melangkah masuk zona portal
            long now = System.currentTimeMillis();

            Long cdUntil = teleportCooldowns.get(uuid);
            if (cdUntil != null && now < cdUntil) {
                long remaining = (cdUntil - now) / 1000 + 1;
                String msg = CoreConfig.RTP_MSG_COOLDOWN.replace("{seconds}", String.valueOf(remaining));
                player.sendActionBar(ColorUtils.parse(msg));
                return;
            }

            Long exitUntil = exitCooldowns.get(uuid);
            if (exitUntil != null && now < exitUntil) {
                return;
            }

            currentZone.put(uuid, detected);
            debounceTarget.put(uuid, detected);
            debounceUntilTick.put(uuid, plugin.getHeartbeatManager().getCurrentTick() + CoreConfig.RTP_PORTAL_ENTRY_DEBOUNCE_TICKS);

            String msg = CoreConfig.RTP_MSG_SEARCHING.replace("{world}", detected.getDisplayName());
            player.sendActionBar(ColorUtils.parse(msg));
            playSoundSafely(player, Sound.UI_BUTTON_CLICK, 1.4f);

        } else if (active != null && detected == null) {
            // CASE 2: Pemain keluar dari zona portal
            cancelPortal(player, true);

        } else if (active != null && detected != null && active != detected) {
            // CASE 3: Pindah langsung antar portal yang berbeda
            cancelPortal(player, false);
            handlePlayerMove(player, from, to);
        }
    }

    public void cancelPortal(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        debounceUntilTick.remove(uuid);
        debounceTarget.remove(uuid);

        RtpWorldType prevZone = currentZone.remove(uuid);
        CompletableFuture<Location> search = activeSearches.remove(uuid);
        if (search != null && !search.isDone()) {
            search.cancel(true);
        }

        if (prevZone != null) {
            exitCooldowns.put(uuid, System.currentTimeMillis() + CoreConfig.RTP_ZONE_EXIT_COOLDOWN_MS);
            if (notify && player.isOnline()) {
                player.sendActionBar(ColorUtils.parse(CoreConfig.RTP_MSG_CANCELLED_LEFT));
                playSoundSafely(player, Sound.UI_BUTTON_CLICK, 0.8f);
            }
        }
    }

    private void startPortalRtp(Player player, RtpWorldType targetType) {
        UUID uuid = player.getUniqueId();
        World targetWorld = resolveTargetWorld(targetType);

        if (targetWorld == null) {
            currentZone.remove(uuid);
            player.sendMessage(ColorUtils.parse(CoreConfig.RTP_MSG_WORLD_NOT_FOUND.replace("{world}", targetType.getId())));
            return;
        }

        // Cek pre-cached location untuk teleport instan
        Queue<Location> cache = safeLocationCache.get(targetType);
        Location cached = cache != null ? cache.poll() : null;
        if (cached != null && cached.getWorld() != null) {
            refillCacheAsync(targetType);
            currentZone.remove(uuid);
            prepareWarmup(player, cached, targetType);
            return;
        }

        // Cari lokasi aman async
        CompletableFuture<Location> searchFuture = findSafeLocationAsync(targetWorld, player, 0);
        activeSearches.put(uuid, searchFuture);

        searchFuture.thenAccept(location -> Bukkit.getScheduler().runTask(plugin, () -> {
            activeSearches.remove(uuid);
            RtpWorldType active = currentZone.remove(uuid);

            if (!player.isOnline() || active != targetType) {
                if (location != null && cache != null && cache.size() < CoreConfig.RTP_MAX_CACHE_SIZE) {
                    cache.offer(location);
                }
                return;
            }

            if (location == null) {
                player.sendMessage(ColorUtils.parse(CoreConfig.RTP_MSG_SEARCH_FAILED_PORTAL));
                return;
            }

            prepareWarmup(player, location, targetType);
        })).exceptionally(t -> {
            activeSearches.remove(uuid);
            currentZone.remove(uuid);
            return null;
        });
    }

    /**
     * Teleport acak aman via command (/rtp atau /wild).
     */
    public void teleportRandomSafe(Player player, RtpWorldType targetType) {
        if (!player.isOnline()) return;

        player.closeInventory();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long cdUntil = teleportCooldowns.get(uuid);
        if (cdUntil != null && now < cdUntil) {
            long remaining = (cdUntil - now) / 1000 + 1;
            String msg = CoreConfig.RTP_MSG_COOLDOWN.replace("{seconds}", String.valueOf(remaining));
            player.sendActionBar(ColorUtils.parse(msg));
            return;
        }

        World targetWorld = resolveTargetWorld(targetType);
        if (targetWorld == null) {
            player.sendMessage(ColorUtils.parse(CoreConfig.RTP_MSG_WORLD_NOT_FOUND.replace("{world}", targetType.getId())));
            return;
        }

        // Cek cache
        Queue<Location> cache = safeLocationCache.get(targetType);
        Location cached = cache != null ? cache.poll() : null;
        if (cached != null && cached.getWorld() != null) {
            refillCacheAsync(targetType);
            prepareWarmup(player, cached, targetType);
            return;
        }

        String msg = CoreConfig.RTP_MSG_SEARCHING.replace("{world}", targetType.getDisplayName());
        player.sendActionBar(ColorUtils.parse(msg));
        playSoundSafely(player, Sound.UI_BUTTON_CLICK, 1.4f);

        findSafeLocationAsync(targetWorld, player, 0).thenAccept(loc -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (loc == null) {
                player.sendMessage(ColorUtils.parse(CoreConfig.RTP_MSG_SEARCH_FAILED));
                return;
            }
            prepareWarmup(player, loc, targetType);
        }));
    }

    private void prepareWarmup(Player player, Location location, RtpWorldType targetType) {
        if (!player.isOnline()) return;
        player.closeInventory();

        UUID uuid = player.getUniqueId();
        preloadSurroundingChunks(location);

        long expireTick = plugin.getHeartbeatManager().getCurrentTick() + (CoreConfig.TELEPORT_WARMUP_SECONDS * 20L);
        activeWarmups.put(uuid, new WarmupSession(uuid, player.getLocation().clone(), location, targetType, expireTick));

        String warmupMsg = CoreConfig.RTP_MSG_WARMUP.replace("{seconds}", String.valueOf(CoreConfig.TELEPORT_WARMUP_SECONDS));
        player.sendActionBar(ColorUtils.parse(warmupMsg));
        playSoundSafely(player, Sound.UI_BUTTON_CLICK, 1.0f);
    }

    public boolean hasActiveWarmup(@Nullable Player player) {
        return player != null && activeWarmups.containsKey(player.getUniqueId());
    }

    private void returnLocationToCache(RtpWorldType type, Location loc) {
        Queue<Location> cache = safeLocationCache.get(type);
        if (cache != null && cache.size() < CoreConfig.RTP_MAX_CACHE_SIZE && loc.getWorld() != null) {
            cache.offer(loc);
        }
    }

    private void executeTeleport(Player player, Location location, RtpWorldType targetType) {
        UUID uuid = player.getUniqueId();
        teleportCooldowns.put(uuid, System.currentTimeMillis() + CoreConfig.RTP_TELEPORT_COOLDOWN_MS);

        List<CompletableFuture<?>> futures = preloadSurroundingChunks(location);
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    player.teleportAsync(location).thenRun(() -> {
                        plugin.getHubProtectionManager().enforceGamemode(player, location.getWorld());
                        playSoundSafely(player, Sound.ENTITY_PLAYER_TELEPORT, location, 1.0f);

                        String coords = location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
                        String msg = CoreConfig.RTP_MSG_TELEPORTED
                                .replace("{world}", targetType.getDisplayName())
                                .replace("{coords}", coords);
                        player.sendActionBar(ColorUtils.parse(msg));

                        logger.info("RTP successful for {} to {} (X: {}, Y: {}, Z: {})",
                                player.getName(), location.getWorld().getName(),
                                location.getBlockX(), location.getBlockY(), location.getBlockZ());
                    });
                }));
    }

    private List<CompletableFuture<?>> preloadSurroundingChunks(Location location) {
        World world = location.getWorld();
        List<CompletableFuture<?>> futures = new ArrayList<>(9);
        if (world == null) return futures;

        int cx = location.getBlockX() >> 4;
        int cz = location.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                futures.add(world.getChunkAtAsync(cx + dx, cz + dz, true));
            }
        }
        return futures;
    }

    private CompletableFuture<Location> findSafeLocationAsync(World world, @Nullable Player player, int attempt) {
        if (attempt >= CoreConfig.RTP_MAX_SEARCH_ATTEMPTS) {
            return CompletableFuture.completedFuture(null);
        }

        RtpWorldType type = RtpWorldType.fromEnvironment(world.getEnvironment());
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double angle = rand.nextDouble(0, 2 * Math.PI);
        double distance = rand.nextDouble(type.getMinRadius(), type.getMaxRadius());
        int targetX = (int) (distance * Math.cos(angle));
        int targetZ = (int) (distance * Math.sin(angle));

        int chunkX = targetX >> 4;
        int chunkZ = targetZ >> 4;

        CompletableFuture<Location> future = new CompletableFuture<>();

        world.getChunkAtAsync(chunkX, chunkZ, true).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Location safeLoc = evaluateSafeLocation(world, targetX, targetZ, world.getEnvironment(), player);
                    if (safeLoc != null) {
                        future.complete(safeLoc);
                    } else {
                        findSafeLocationAsync(world, player, attempt + 1).thenAccept(future::complete);
                    }
                })).exceptionally(t -> {
            findSafeLocationAsync(world, player, attempt + 1).thenAccept(future::complete);
            return null;
        });

        return future;
    }

    private @Nullable Location evaluateSafeLocation(World world, int x, int z, World.Environment env, @Nullable Player player) {
        float yaw = player != null ? player.getLocation().getYaw() : 0.0f;
        float pitch = player != null ? player.getLocation().getPitch() : 0.0f;

        if (env == World.Environment.NETHER) {
            for (int y = CoreConfig.RTP_NETHER_SCAN_Y_MAX; y >= CoreConfig.RTP_NETHER_SCAN_Y_MIN; y--) {
                if (isSafeStandLocation(world, x, y, z)) {
                    return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
                }
            }
            return null;
        }

        int topY = world.getHighestBlockYAt(x, z);
        if (topY <= world.getMinHeight() + CoreConfig.RTP_SURFACE_EDGE_MARGIN
                || topY >= world.getMaxHeight() - CoreConfig.RTP_SURFACE_EDGE_MARGIN) {
            return null;
        }

        int scanFloor = Math.max(world.getMinHeight() + 1, topY - CoreConfig.RTP_SURFACE_SCAN_DEPTH);
        for (int y = topY + 1; y >= scanFloor; y--) {
            if (isSafeStandLocation(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
            }
        }
        return null;
    }

    private boolean isSafeStandLocation(World world, int x, int y, int z) {
        Block ground = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);

        Material groundType = ground.getType();
        if (!groundType.isSolid() || ground.isLiquid() || groundType == Material.BEDROCK || groundType == Material.BARRIER) {
            return false;
        }
        if (groundType.name().contains("LEAVES")) {
            return false;
        }
        if (isHazardous(groundType)) {
            return false;
        }

        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        if (feet.isLiquid() || head.isLiquid()) {
            return false;
        }
        return !isHazardous(feet.getType()) && !isHazardous(head.getType());
    }

    private boolean isHazardous(Material mat) {
        if (mat == null) return true;
        String name = mat.name();
        return name.contains("LAVA") ||
                name.contains("FIRE") ||
                name.contains("MAGMA") ||
                name.contains("CACTUS") ||
                name.contains("CAMPFIRE") ||
                name.contains("SWEET_BERRY") ||
                name.contains("POWDER_SNOW") ||
                name.contains("VOID");
    }

    public @Nullable RtpWorldType detectPortalEntry(Location loc) {
        if (!CoreConfig.isHubWorld(loc.getWorld())) return null;

        for (RtpWorldType type : RtpWorldType.values()) {
            Location portalLoc = getPortalLocation(type);
            if (portalLoc != null && isInsidePortalBox(loc, portalLoc)) {
                return type;
            }
        }
        return null;
    }

    private boolean isInsidePortalBox(Location playerLoc, Location portalLoc) {
        if (playerLoc.getWorld() == null || portalLoc.getWorld() == null) return false;
        if (!playerLoc.getWorld().equals(portalLoc.getWorld())) return false;

        double dy = playerLoc.getY() - portalLoc.getY();
        if (dy < CoreConfig.RTP_PORTAL_BOX_Y_MIN || dy > CoreConfig.RTP_PORTAL_BOX_Y_MAX) {
            return false;
        }

        double dx = playerLoc.getX() - portalLoc.getX();
        double dz = playerLoc.getZ() - portalLoc.getZ();

        double rad = Math.toRadians(portalLoc.getYaw());
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double rightX = Math.cos(rad);
        double rightZ = Math.sin(rad);

        double forwardDist = dx * fwdX + dz * fwdZ;
        double sideDist = dx * rightX + dz * rightZ;

        return Math.abs(forwardDist) <= CoreConfig.RTP_PORTAL_BOX_FORWARD_DIST
                && Math.abs(sideDist) <= CoreConfig.RTP_PORTAL_BOX_SIDE_DIST;
    }

    public @Nullable Location getPortalLocation(RtpWorldType type) {
        HologramInstance holo = plugin.getHologramManager().getHologram(type.getHologramId());
        if (holo != null && holo.getData() != null) {
            Location loc = holo.getData().toLocation();
            if (loc != null) return loc;
        }

        World spawnWorld = resolveWorld(CoreConfig.RTP_PORTALS_WORLD);
        return spawnWorld != null
                ? new Location(spawnWorld, type.getPortalX(), type.getPortalY(), type.getPortalZ(), type.getPortalYaw(), 0.0f)
                : null;
    }

    public @Nullable World resolveTargetWorld(RtpWorldType type) {
        if (type == RtpWorldType.NETHER) {
            return resolveWorld(RtpWorldType.NETHER.getWorldKey());
        } else if (type == RtpWorldType.THE_END) {
            return resolveWorld(RtpWorldType.THE_END.getWorldKey());
        }
        return resolveWorld(RtpWorldType.OVERWORLD.getWorldKey());
    }

    public static @Nullable World resolveWorld(String name) {
        if (name == null) return null;
        World direct = Bukkit.getWorld(name);
        if (direct != null) return direct;
        for (World w : Bukkit.getWorlds()) {
            if (w.getName().equalsIgnoreCase(name)) {
                return w;
            }
        }
        return null;
    }

    private void playSoundSafely(Player player, Sound sound, float pitch) {
        playSoundSafely(player, sound, player.getLocation(), pitch);
    }

    private void playSoundSafely(Player player, Sound sound, Location at, float pitch) {
        try {
            player.playSound(at, sound, 0.8f, pitch);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        for (CompletableFuture<Location> future : activeSearches.values()) {
            future.cancel(true);
        }
        activeSearches.clear();
        currentZone.clear();
        debounceUntilTick.clear();
        debounceTarget.clear();
        activeWarmups.clear();
        teleportCooldowns.clear();
        exitCooldowns.clear();
        safeLocationCache.clear();
        refilling.clear();
    }
}

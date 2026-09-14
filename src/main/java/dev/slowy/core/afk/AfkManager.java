package dev.slowy.core.afk;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.economy.EconomyManager;
import dev.slowy.core.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Modern AFK Zone Engine & 4th Spawn Portal Manager.
 * Ticked every 1 second by HeartbeatManager (Zero BukkitTasks).
 * Awards shards and handles portal transitions.
 */
@NullMarked
public final class AfkManager implements Lifecycle {

    public record AfkWarmup(
            UUID uuid,
            Location startLoc,
            long expireTick
    ) {}

    private final SlowyCore plugin;
    private final Logger logger;
    private final EconomyManager economyManager;

    private final Map<UUID, Integer> afkSeconds = new ConcurrentHashMap<>();
    private final Set<UUID> activeAfkPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> portalCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, AfkWarmup> activeWarmups = new ConcurrentHashMap<>();

    public AfkManager(SlowyCore plugin, EconomyManager economyManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.economyManager = Objects.requireNonNull(economyManager, "economyManager cannot be null");

        ensureAfkWorldLoaded();
        logger.info("AfkManager initialized (4th portal & 1 shard/minute reward loop ready).");
    }

    private void ensureAfkWorldLoaded() {
        if (Bukkit.getWorld(CoreConfig.AFK_WORLD_NAME) == null) {
            try {
                Bukkit.createWorld(new WorldCreator(CoreConfig.AFK_WORLD_NAME));
            } catch (Throwable t) {
                logger.warn("Could not automatically load world {}: {}", CoreConfig.AFK_WORLD_NAME, t.getMessage());
            }
        }
    }

    /**
     * Ticked every 20 ticks (1 second) by HeartbeatManager.
     */
    public void tickSecond() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (isInAfkZone(player)) {
                if (!activeAfkPlayers.contains(uuid)) {
                    handleEnterAfkZone(player);
                    continue;
                }

                boolean hasWarmup = (plugin.getHubProtectionManager() != null && plugin.getHubProtectionManager().hasActiveWarmup(player))
                        || (plugin.getRtpManager() != null && plugin.getRtpManager().hasActiveWarmup(player))
                        || activeWarmups.containsKey(uuid);

                int current = afkSeconds.getOrDefault(uuid, 0) + 1;
                if (current >= CoreConfig.AFK_SHARD_INTERVAL_SECONDS) {
                    afkSeconds.put(uuid, 0);
                    giveAfkReward(player, !hasWarmup);
                } else {
                    afkSeconds.put(uuid, current);
                    if (!hasWarmup) {
                        int remaining = CoreConfig.AFK_SHARD_INTERVAL_SECONDS - current;
                        String msg = CoreConfig.AFK_ACTIONBAR_REMAINING.replace("{seconds}", String.valueOf(remaining));
                        player.sendActionBar(ColorUtils.parse(msg));
                    }
                }
            } else {
                if (activeAfkPlayers.contains(uuid)) {
                    handleLeaveAfkZone(player);
                }
            }
        }
    }

    /**
     * Ticked every tick by HeartbeatManager for portal warmup countdown.
     */
    public void tickWarmup(long currentTick) {
        if (activeWarmups.isEmpty()) return;

        Iterator<Map.Entry<UUID, AfkWarmup>> it = activeWarmups.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AfkWarmup> entry = it.next();
            UUID uuid = entry.getKey();
            AfkWarmup warmup = entry.getValue();

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                it.remove();
                continue;
            }

            Location currentLoc = player.getLocation();
            if (currentLoc.getBlockX() != warmup.startLoc().getBlockX()
                    || currentLoc.getBlockY() != warmup.startLoc().getBlockY()
                    || currentLoc.getBlockZ() != warmup.startLoc().getBlockZ()) {
                it.remove();
                player.sendActionBar(ColorUtils.parse(CoreConfig.RTP_MSG_CANCELLED_MOVED));
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
                } catch (Throwable ignored) {}
                continue;
            }

            long remainingTicks = warmup.expireTick() - currentTick;
            if (remainingTicks > 0) {
                if (remainingTicks % 20 == 0) {
                    int secondsLeft = (int) (remainingTicks / 20);
                    String msg = CoreConfig.RTP_MSG_WARMUP.replace("{seconds}", String.valueOf(secondsLeft));
                    player.sendActionBar(ColorUtils.parse(msg));
                    try {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                    } catch (Throwable ignored) {}
                }
            } else {
                it.remove();
                teleportToAfk(player);
            }
        }
    }

    public boolean isInAfkZone(@Nullable Player player) {
        if (player == null || !player.isOnline()) return false;
        String w = player.getWorld().getName().toLowerCase();
        return w.equals(CoreConfig.AFK_WORLD_NAME) || w.endsWith(":" + CoreConfig.AFK_WORLD_NAME);
    }

    public void handleEnterAfkZone(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeAfkPlayers.add(uuid)) {
            afkSeconds.put(uuid, 0);
            Component titleComp = ColorUtils.parse(CoreConfig.AFK_WELCOME_TITLE);
            Component subComp = ColorUtils.parse(CoreConfig.AFK_WELCOME_SUBTITLE);
            Title.Times times = Title.Times.times(Ticks.duration(10), Ticks.duration(40), Ticks.duration(10));
            player.showTitle(Title.title(titleComp, subComp, times));
        }
    }

    public void handleLeaveAfkZone(Player player) {
        UUID uuid = player.getUniqueId();
        activeAfkPlayers.remove(uuid);
        afkSeconds.remove(uuid);
        player.sendActionBar(Component.empty());
    }

    private void giveAfkReward(Player player, boolean sendActionBar) {
        economyManager.getOrCreateAccount(player.getUniqueId(), player.getName())
                .depositShards(CoreConfig.AFK_SHARD_REWARD_AMOUNT);

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
        } catch (Throwable ignored) {}

        if (sendActionBar) {
            String msg = CoreConfig.AFK_ACTIONBAR_REWARD.replace("{amount}", String.valueOf(CoreConfig.AFK_SHARD_REWARD_AMOUNT));
            player.sendActionBar(ColorUtils.parse(msg));
        }
    }

    public boolean hasActiveWarmup(@Nullable Player player) {
        return player != null && activeWarmups.containsKey(player.getUniqueId());
    }

    public boolean isInSpawnAfkPortal(Location loc) {
        if (loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().toLowerCase().contains(CoreConfig.SPAWN_WORLD_NAME)) return false;

        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        // 1. Generous bounding box for AFK portal
        if (x >= 16.5 && x <= 24.5 && z >= 88.5 && z <= 96.5 && y >= 60.0 && y <= 75.0) {
            return true;
        }

        // 2. Check End Portal block in proximity to AFK Portal
        Material mat = loc.getBlock().getType();
        if (mat == Material.END_PORTAL || mat == Material.END_GATEWAY
                || loc.clone().subtract(0, 0.5, 0).getBlock().getType() == Material.END_PORTAL) {
            double dx = x - CoreConfig.AFK_PORTAL_HOLO_X;
            double dz = z - CoreConfig.AFK_PORTAL_HOLO_Z;
            if ((dx * dx + dz * dz) <= 64.0) {
                return true;
            }
        }

        return false;
    }

    public void handlePortalEntry(Player player) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        Long cdUntil = portalCooldowns.get(uuid);
        if (cdUntil != null && now < cdUntil) {
            return;
        }
        portalCooldowns.put(uuid, now + CoreConfig.AFK_PORTAL_COOLDOWN_MS);

        // Instant teleport without delay, just like native Minecraft End portals
        activeWarmups.remove(uuid);
        teleportToAfk(player);
    }

    public @Nullable Location getRandomAfkSpot() {
        World world = Bukkit.getWorld(CoreConfig.AFK_WORLD_NAME);
        if (world == null) {
            world = Bukkit.getWorld("minecraft:" + CoreConfig.AFK_WORLD_NAME);
        }
        if (world == null) return null;

        double[][] spots = CoreConfig.AFK_SPOTS;
        int index = ThreadLocalRandom.current().nextInt(spots.length);
        double[] spot = spots[index];
        return new Location(world, spot[0], spot[1], spot[2], (float) spot[3], 0.0f);
    }

    public void teleportToAfk(Player player) {
        if (!player.isOnline()) return;

        Location target = getRandomAfkSpot();
        if (target != null) {
            player.teleportAsync(target).thenAccept(success -> {
                if (success) {
                    plugin.getHubProtectionManager().enforceGamemode(player, target.getWorld());
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    } catch (Throwable ignored) {}
                    handleEnterAfkZone(player);
                }
            });
            return;
        }

        player.sendMessage(ColorUtils.parse("<red>✖ World <yellow>" + CoreConfig.AFK_WORLD_NAME + "</yellow> not found!</red>"));
    }

    public void startAfkTeleport(Player player) {
        if (!player.isOnline()) return;

        if (isInAfkZone(player)) {
            player.sendActionBar(ColorUtils.parse("<yellow>You are already in the AFK Zone!</yellow>"));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (activeWarmups.containsKey(uuid)) return;

        long expireTick = plugin.getHeartbeatManager().getCurrentTick() + (CoreConfig.TELEPORT_WARMUP_SECONDS * 20L);
        activeWarmups.put(uuid, new AfkWarmup(uuid, player.getLocation().clone(), expireTick));

        String warmupMsg = CoreConfig.RTP_MSG_WARMUP.replace("{seconds}", String.valueOf(CoreConfig.TELEPORT_WARMUP_SECONDS));
        player.sendActionBar(ColorUtils.parse(warmupMsg));
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        } catch (Throwable ignored) {}
    }

    public void handlePlayerMove(Player player, Location from, Location to) {
        if (!player.isOnline() || to == null) return;
        AfkWarmup warmup = activeWarmups.get(player.getUniqueId());
        if (warmup != null) {
            if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
                activeWarmups.remove(player.getUniqueId());
                player.sendActionBar(ColorUtils.parse(CoreConfig.RTP_MSG_CANCELLED_MOVED));
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
                } catch (Throwable ignored) {}
            }
        }
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        activeAfkPlayers.remove(uuid);
        afkSeconds.remove(uuid);
        portalCooldowns.remove(uuid);
        activeWarmups.remove(uuid);
    }

    @Override
    public void onDisable() {
        activeAfkPlayers.clear();
        afkSeconds.clear();
        portalCooldowns.clear();
        activeWarmups.clear();
    }
}

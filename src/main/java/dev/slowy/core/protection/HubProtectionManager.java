package dev.slowy.core.protection;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance Internal Hub & Spawn Protection Engine.
 * Replaces WorldGuard with O(1) world checks.
 * Enforces gamemode rules:
 * - Owner: unrestricted.
 * - Staff: Spectator / Adventure in Hub; Spectator / Survival in Wild (NO Creative).
 * - Players: Adventure in Hub; Survival in Wild.
 */
@NullMarked
public final class HubProtectionManager implements Lifecycle {

    private final SlowyCore plugin;

    public HubProtectionManager(SlowyCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public boolean isOwner(Player player) {
        if (plugin.getRoleManager() != null) {
            return plugin.getRoleManager().isOwner(player);
        }
        return player.hasPermission(CoreConfig.PERM_OWNER);
    }

    public boolean isStaff(Player player) {
        if (plugin.getRoleManager() != null) {
            return plugin.getRoleManager().isStaff(player);
        }
        return player.hasPermission(CoreConfig.PERM_STAFF) || player.hasPermission(CoreConfig.PERM_ADMIN);
    }

    public void enforceGamemode(Player player) {
        enforceGamemode(player, player.getWorld());
    }

    public void enforceGamemode(Player player, World world) {
        if (!player.isOnline()) return;

        boolean isHub = CoreConfig.isHubWorld(world);
        boolean owner = isOwner(player);
        boolean staff = isStaff(player);

        // If owner is explicitly in Creative or Spectator, leave them alone
        if (owner && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }

        // Staff can be in Spectator anywhere
        if (staff && player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (isHub) {
            // Inside spawn or afk_zone: only ADVENTURE
            if (player.getGameMode() != GameMode.ADVENTURE) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        } else {
            // Outside hub: only SURVIVAL
            if (player.getGameMode() != GameMode.SURVIVAL) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    public boolean handleGameModeChange(Player player, GameMode targetMode) {
        if (isOwner(player)) return true;

        // Staff & regular players are NEVER allowed to enter Creative
        if (targetMode == GameMode.CREATIVE) {
            player.sendMessage(ColorUtils.parse("<red>✖ Only the server owner can use Creative mode!</red>"));
            return false;
        }

        boolean isHub = CoreConfig.isHubWorld(player.getWorld());
        boolean staff = isStaff(player);

        if (isHub) {
            if (targetMode == GameMode.SPECTATOR) {
                if (!staff) {
                    player.sendMessage(ColorUtils.parse(CoreConfig.NO_PERMISSION));
                    return false;
                }
                return true;
            }
            if (targetMode == GameMode.ADVENTURE) {
                return true;
            }
            player.sendMessage(ColorUtils.parse("<red>✖ Only Adventure or Spectator mode is allowed in the Hub!</red>"));
            return false;
        } else {
            if (targetMode == GameMode.SPECTATOR) {
                if (!staff) {
                    player.sendMessage(ColorUtils.parse(CoreConfig.NO_PERMISSION));
                    return false;
                }
                return true;
            }
            if (targetMode == GameMode.SURVIVAL) {
                return true;
            }
            return false;
        }
    }

    public Location getSpawnLocation() {
        World spawnWorld = Bukkit.getWorld(CoreConfig.SPAWN_WORLD_NAME);
        if (spawnWorld == null) {
            spawnWorld = Bukkit.getWorld("minecraft:" + CoreConfig.SPAWN_WORLD_NAME);
        }
        if (spawnWorld != null) {
            return new Location(spawnWorld, CoreConfig.SPAWN_POINT_X, CoreConfig.SPAWN_POINT_Y, CoreConfig.SPAWN_POINT_Z,
                    CoreConfig.SPAWN_POINT_YAW, CoreConfig.SPAWN_POINT_PITCH);
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public void rescueVoid(Player player) {
        if (!player.isOnline()) return;
        Location spawnLoc = getSpawnLocation();
        player.teleportAsync(spawnLoc).thenRun(() -> {
            player.setFallDistance(0f);
            player.sendActionBar(ColorUtils.parse("<green>✔ Rescued from the void!</green>"));
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            } catch (Throwable ignored) {}
        });
    }

    public record SpawnWarmup(
            UUID uuid,
            Location startLoc,
            long expireTick
    ) {}

    private final Map<UUID, SpawnWarmup> activeSpawnWarmups = new ConcurrentHashMap<>();

    public void teleportToSpawn(Player player) {
        if (!player.isOnline()) return;

        if (player.getWorld().getName().equalsIgnoreCase(CoreConfig.SPAWN_WORLD_NAME)
                && player.getLocation().distanceSquared(getSpawnLocation()) < 4.0) {
            player.sendActionBar(ColorUtils.parse("<yellow>You are already at Spawn!</yellow>"));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (activeSpawnWarmups.containsKey(uuid)) {
            return;
        }

        long expireTick = plugin.getHeartbeatManager().getCurrentTick() + (CoreConfig.TELEPORT_WARMUP_SECONDS * 20L);
        activeSpawnWarmups.put(uuid, new SpawnWarmup(uuid, player.getLocation().clone(), expireTick));

        String warmupMsg = CoreConfig.RTP_MSG_WARMUP.replace("{seconds}", String.valueOf(CoreConfig.TELEPORT_WARMUP_SECONDS));
        player.sendActionBar(ColorUtils.parse(warmupMsg));
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
        } catch (Throwable ignored) {}
    }

    public boolean hasActiveWarmup(@Nullable Player player) {
        return player != null && activeSpawnWarmups.containsKey(player.getUniqueId());
    }

    public void cancelSpawnWarmup(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        SpawnWarmup warmup = activeSpawnWarmups.remove(uuid);
        if (warmup != null && notify && player.isOnline()) {
            player.sendActionBar(ColorUtils.parse(CoreConfig.RTP_MSG_CANCELLED_MOVED));
            try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
            } catch (Throwable ignored) {}
        }
    }

    public void handlePlayerMove(Player player, Location from, Location to) {
        if (!player.isOnline() || to == null) return;
        SpawnWarmup warmup = activeSpawnWarmups.get(player.getUniqueId());
        if (warmup != null) {
            if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
                cancelSpawnWarmup(player, true);
            }
        }
    }

    public void tickWarmup(long currentTick) {
        if (activeSpawnWarmups.isEmpty()) return;

        Iterator<Map.Entry<UUID, SpawnWarmup>> it = activeSpawnWarmups.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SpawnWarmup> entry = it.next();
            UUID uuid = entry.getKey();
            SpawnWarmup warmup = entry.getValue();

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
                player.teleportAsync(getSpawnLocation()).thenAccept(success -> {
                    if (success) {
                        enforceGamemode(player, getSpawnLocation().getWorld());
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        } catch (Throwable ignored) {}
                    }
                });
            }
        }
    }

    public void onPlayerQuit(Player player) {
        activeSpawnWarmups.remove(player.getUniqueId());
    }

    @Override
    public void onDisable() {
        activeSpawnWarmups.clear();
    }
}

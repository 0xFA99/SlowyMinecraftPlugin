package dev.slowy.core.tick;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.auth.AuthManager;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.npc.NpcManager;
import dev.slowy.core.rtp.RtpManager;
import dev.slowy.core.scoreboard.ScoreboardManager;
import dev.slowy.core.tablist.TablistManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Objects;

/**
 * Master Heartbeat Ticker for SlowyCore2.
 * Unifies all periodic plugin tasks into a single 1-tick scheduler.
 * Uses phased modulo math to distribute workloads evenly across ticks (zero micro-stutter).
 */
@NullMarked
public final class HeartbeatManager implements Lifecycle {

    private final SlowyCore plugin;
    private final Logger logger;

    private final NpcManager npcManager;
    private final TablistManager tablistManager;
    private final ScoreboardManager scoreboardManager;
    private final AuthManager authManager;
    private final RtpManager rtpManager;
    private final dev.slowy.core.protection.HubProtectionManager hubProtectionManager;
    private final dev.slowy.core.afk.AfkManager afkManager;
    private final dev.slowy.core.crate.CrateHologramManager crateHologramManager;
    private final dev.slowy.core.crate.KeyAllManager keyAllManager;
    private final dev.slowy.core.teleport.TeleportManager teleportManager;

    private @Nullable BukkitTask masterTask;
    private long tickCount = 0;

    public HeartbeatManager(
            SlowyCore plugin,
            NpcManager npcManager,
            TablistManager tablistManager,
            ScoreboardManager scoreboardManager,
            AuthManager authManager,
            RtpManager rtpManager,
            dev.slowy.core.protection.HubProtectionManager hubProtectionManager,
            dev.slowy.core.afk.AfkManager afkManager,
            dev.slowy.core.crate.CrateHologramManager crateHologramManager,
            dev.slowy.core.crate.KeyAllManager keyAllManager,
            dev.slowy.core.teleport.TeleportManager teleportManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.npcManager = Objects.requireNonNull(npcManager, "npcManager cannot be null");
        this.tablistManager = Objects.requireNonNull(tablistManager, "tablistManager cannot be null");
        this.scoreboardManager = Objects.requireNonNull(scoreboardManager, "scoreboardManager cannot be null");
        this.authManager = Objects.requireNonNull(authManager, "authManager cannot be null");
        this.rtpManager = Objects.requireNonNull(rtpManager, "rtpManager cannot be null");
        this.hubProtectionManager = Objects.requireNonNull(hubProtectionManager, "hubProtectionManager cannot be null");
        this.afkManager = Objects.requireNonNull(afkManager, "afkManager cannot be null");
        this.crateHologramManager = Objects.requireNonNull(crateHologramManager, "crateHologramManager cannot be null");
        this.keyAllManager = Objects.requireNonNull(keyAllManager, "keyAllManager cannot be null");
        this.teleportManager = Objects.requireNonNull(teleportManager, "teleportManager cannot be null");

        start();
    }

    public void start() {
        stop();
        this.masterTask = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 1L, 1L);
        logger.info("HeartbeatManager started (Unified master tick loop active at 20 TPS).");
    }

    public void stop() {
        if (masterTask != null) {
            masterTask.cancel();
            masterTask = null;
        }
    }

    private void onTick() {
        long tick = this.tickCount++;

        try {
            // 1. NPC tracking & visibility: Every 2 ticks (even ticks: 0, 2, 4, 6...)
            if ((tick & 1) == 0) {
                npcManager.tick();
            }

            // 2. Tablist update: Every TABLIST_UPDATE_TICKS (offset 1: 1, 41, 81...)
            if (CoreConfig.TABLIST_ENABLED && tick % CoreConfig.TABLIST_UPDATE_TICKS == 1) {
                tablistManager.updateAll();
            }

            // 3. Scoreboard title animation: Every titleInterval ticks
            if (scoreboardManager.isTitleAnimated() && tick % scoreboardManager.getTitleInterval() == 0) {
                scoreboardManager.tickTitleAnimation();
            }

            // 4. Scoreboard lines update: Every updateInterval ticks (offset 2: 2, 62, 122...)
            if (tick % scoreboardManager.getUpdateInterval() == 2) {
                scoreboardManager.tickLines();
            }

            // 5. Auth Action Bar Nag: Every 40 ticks (offset 21: 21, 61, 101...)
            // if (tick % 40 == 21) {
            //     authManager.tickNag();
            // }

            // 6. RTP warmup & portal debounce: Every tick
            rtpManager.tick(tick);

            // 7. Spawn & Hub warmup: Every tick
            hubProtectionManager.tickWarmup(tick);

            // 8. AFK portal warmup: Every tick
            afkManager.tickWarmup(tick);

            // 9. AFK zone shard rewards & actionbar: Every 20 ticks (offset 10: 10, 30, 50...)
            if (tick % 20 == 10) {
                afkManager.tickSecond();
            }

            // 10. KeyAll distribution & timer: Every 20 ticks (offset 5: 5, 25, 45...)
            if (tick % 20 == 5) {
                keyAllManager.tickSecond();
            }

            // 11. Crate Holograms & particles: Phased check
            crateHologramManager.tick(tick);

            // 12. TPA & TPAHERE warmup countdown & request expiry: Every tick
            teleportManager.tick(tick);
        } catch (Throwable t) {
            logger.error("Error in Master Heartbeat tick {}: {}", tick, t.getMessage(), t);
        }
    }

    public long getTickCount() {
        return tickCount;
    }

    public long getCurrentTick() {
        return tickCount;
    }

    @Override
    public void onDisable() {
        stop();
        keyAllManager.onDisable();
        crateHologramManager.despawnAll();
        logger.info("HeartbeatManager stopped.");
    }
}

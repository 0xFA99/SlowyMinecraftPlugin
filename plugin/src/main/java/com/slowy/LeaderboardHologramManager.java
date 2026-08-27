package com.slowy;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.LeaderboardManager.LeaderboardEntry;
import com.bx.ultimateDonutSmp.managers.LeaderboardManager.LeaderboardType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LeaderboardHologramManager {

    private static final String TAG = "slowy_lb_money";
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,###");

    private final SlowyPlugin plugin;
    private final String worldName = "lobby2";
    private final double x = 325.5;
    private final double y = 3.0;
    private final double z = 301.5;

    private BukkitTask updateTask;
    private UUID displayUuid;
    private List<LeaderboardEntry> lastCachedEntries = new ArrayList<>();

    public LeaderboardHologramManager(SlowyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            purgeAllHolograms();
            ensureDisplay();
            updateHologram();

            updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateHologram, 60L, 100L);
        }, 40L);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        purgeAllHolograms();
    }

    private Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public void purgeAllHolograms() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location loc = new Location(world, x, y, z);
        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }

        // Thoroughly remove ALL display entities within 50 blocks of target location
        for (Entity entity : world.getEntities()) {
            if (entity instanceof TextDisplay || entity.getType().name().contains("DISPLAY") || entity.getScoreboardTags().contains(TAG)) {
                try {
                    if (entity.getLocation().distanceSquared(loc) <= 2500.0) { // 50 blocks radius
                        entity.remove();
                    }
                } catch (Throwable ignored) {}
            }
        }

        displayUuid = null;
    }

    private TextDisplay getOrFindDisplay() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        Location loc = new Location(world, x, y, z);
        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }

        if (displayUuid != null) {
            Entity entity = Bukkit.getEntity(displayUuid);
            if (entity instanceof TextDisplay textDisplay && entity.isValid()) {
                return textDisplay;
            }
        }

        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof TextDisplay textDisplay && entity.getScoreboardTags().contains(TAG)) {
                if (entity.isValid()) {
                    displayUuid = entity.getUniqueId();
                    return textDisplay;
                }
            }
        }

        return null;
    }

    private void ensureDisplay() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location loc = new Location(world, x, y, z);
        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }

        TextDisplay existing = getOrFindDisplay();
        if (existing != null && existing.isValid()) {
            return;
        }

        purgeAllHolograms();

        TextDisplay spawned = world.spawn(loc, TextDisplay.class, display -> {
            display.addScoreboardTag(TAG);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBackgroundColor(Color.fromARGB(140, 20, 20, 25));
            display.setSeeThrough(false);
        });

        displayUuid = spawned.getUniqueId();
    }

    public void updateHologram() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        ensureDisplay();

        TextDisplay display = getOrFindDisplay();
        if (display == null || !display.isValid()) return;

        List<LeaderboardEntry> entriesToDisplay = null;

        if (Bukkit.getPluginManager().isPluginEnabled("UltimateDonutSmp")) {
            try {
                UltimateDonutSmp donut = UltimateDonutSmp.getInstance();
                if (donut != null && donut.getLeaderboardManager() != null) {
                    List<LeaderboardEntry> freshEntries = donut.getLeaderboardManager().getEntries(LeaderboardType.MONEY, 0, 10);
                    if (freshEntries != null && !freshEntries.isEmpty()) {
                        lastCachedEntries = new ArrayList<>(freshEntries);
                        entriesToDisplay = freshEntries;
                    } else {
                        if (!lastCachedEntries.isEmpty()) {
                            entriesToDisplay = lastCachedEntries;
                        }
                        donut.getLeaderboardManager().triggerAsyncRefresh(LeaderboardType.MONEY);
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Gagal mengambil data leaderboard money: " + t.getMessage());
            }
        }

        if (entriesToDisplay == null && !lastCachedEntries.isEmpty()) {
            entriesToDisplay = lastCachedEntries;
        }

        StringBuilder sb = new StringBuilder();

        sb.append("\n&a&lTOP KORUPTOR\n\n");

        if (entriesToDisplay != null && !entriesToDisplay.isEmpty()) {
            int rank = 1;
            for (LeaderboardEntry entry : entriesToDisplay) {
                String name = entry.playerData().getUsername();
                double money = entry.playerData().getMoney();
                String formattedMoney = "$ " + MONEY_FORMAT.format(money);

                String prefix = switch (rank) {
                    case 1 -> "&e&l#1 ";
                    case 2 -> "&f&l#2 ";
                    case 3 -> "&6&l#3 ";
                    default -> "&7#" + rank + " ";
                };

                String nameColor = switch (rank) {
                    case 1 -> "&e";
                    case 2 -> "&f";
                    case 3 -> "&6";
                    default -> "&b";
                };

                sb.append(prefix).append(nameColor).append(name).append("&7: &#00FC00").append(formattedMoney).append("\n");
                rank++;
            }
        } else {
            sb.append("&7Memuat data peringkat...\n");
        }

        sb.append("\n");

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(sb.toString());
        display.text(component);
    }
}

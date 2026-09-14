package dev.slowy.core.hologram;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.economy.Account;
import dev.slowy.core.utils.ColorUtils;
import dev.slowy.core.utils.FontWidthUtil;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HologramInstance {

    private static final double VIEW_DISTANCE_SQ = 48.0 * 48.0;
    private static final EntityDataAccessor<net.minecraft.network.chat.Component> TEXT_ACCESSOR = resolveTextAccessor();

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<net.minecraft.network.chat.Component> resolveTextAccessor() {
        try {
            var lookup = MethodHandles.privateLookupIn(net.minecraft.world.entity.Display.TextDisplay.class, MethodHandles.lookup());
            return (EntityDataAccessor<net.minecraft.network.chat.Component>) lookup
                    .findStaticGetter(net.minecraft.world.entity.Display.TextDisplay.class, "DATA_TEXT_ID", EntityDataAccessor.class)
                    .invokeExact();
        } catch (Throwable t) {
            throw new ExceptionInInitializerError("Failed resolving TextDisplay.DATA_TEXT_ID: " + t.getMessage());
        }
    }

    private final SlowyCore plugin;
    private final NamespacedKey holoKey;
    private HologramData data;
    private volatile TextDisplay displayEntity;
    private final AtomicBoolean isSpawning = new AtomicBoolean(false);
    private final Map<UUID, String> lastSentContent = new ConcurrentHashMap<>();

    // Leaderboard Snapshot Cache: Refresh maksimal tiap 3 detik untuk mencegah lag spike
    private static volatile long lastLbCacheUpdate = 0;
    private static final Map<String, List<LeaderboardRecord>> LB_CACHE = new ConcurrentHashMap<>();
    private String lastGlobalContent = "";

    public HologramInstance(SlowyCore plugin, HologramData data) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.data = Objects.requireNonNull(data, "HologramData cannot be null");
        this.holoKey = new NamespacedKey(plugin, "hologram_id");
    }

    public String getId() { return data.id(); }
    public HologramData getData() { return data; }
    public void setData(HologramData data) { this.data = data; }
    public @Nullable TextDisplay getEntity() { return displayEntity; }

    public void tick(SlowyCore plugin) {
        if (!plugin.isEnabled()) return;
        Location loc = data.toLocation();
        if (loc == null || loc.getWorld() == null) return;
        World world = loc.getWorld();

        // Folia RegionScheduler
        try {
            plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                if (!plugin.isEnabled()) return;
                TextDisplay entity = displayEntity;
                if (entity == null || !entity.isValid()) {
                    displayEntity = null;
                    if (shouldBeActive(world, loc)) {
                        spawnInternal(world, loc);
                    }
                    return;
                }

                // Update text
                if (isPersonalized()) {
                    updateNearbyPersonalized(loc);
                } else {
                    updateGlobal(entity);
                }
            });
        } catch (Throwable ignored) {}
    }

    private boolean isPersonalized() {
        String id = data.id();
        return id.equals("greeting") || id.equals("npc_daily") || id.startsWith("top_") || id.equals("baltop");
    }

    private void updateGlobal(TextDisplay entity) {
        String currentContent = buildContent(null);
        if (!currentContent.equals(lastGlobalContent)) {
            this.lastGlobalContent = currentContent;
            entity.text(ColorUtils.parse(currentContent));
        }
    }

    private boolean shouldBeActive(World world, Location loc) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        if (world.isChunkLoaded(cx, cz)) return true;

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 64.0 * 64.0) {
                return true;
            }
        }
        return false;
    }

    public void sanitizeArea(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        int currentId = displayEntity != null ? displayEntity.getEntityId() : -1;

        for (Entity ent : world.getNearbyEntities(loc, 3.0, 3.0, 3.0)) {
            if (ent instanceof TextDisplay td) {
                if (td.getEntityId() == currentId) continue;
                String tag = td.getPersistentDataContainer().get(holoKey, PersistentDataType.STRING);
                if (tag != null && !tag.equalsIgnoreCase(data.id()) && plugin.getHologramManager().isKnownHologram(tag)) {
                    continue;
                }
                boolean isMatch = (tag != null && tag.equalsIgnoreCase(data.id())) ||
                                  (tag == null && td.getLocation().distanceSquared(loc) <= 6.25);
                if (isMatch) {
                    plugin.getHologramManager().unregisterEntity(td.getEntityId());
                    td.remove();
                }
            }
        }
    }

    public void spawn(SlowyCore plugin) {
        Location loc = data.toLocation();
        if (loc != null && loc.getWorld() != null) {
            plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> spawnInternal(loc.getWorld(), loc));
        }
    }

    private void spawnInternal(World world, Location loc) {
        if (!plugin.isEnabled()) return;
        TextDisplay existing = displayEntity;
        if (existing != null && existing.isValid()) return;
        if (!isSpawning.compareAndSet(false, true)) return;

        try {
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;

            if (world.isChunkLoaded(cx, cz)) {
                processSpawn(world, loc);
            } else {
                world.getChunkAtAsync(loc).thenAccept(chunk -> {
                    if (!plugin.isEnabled()) {
                        isSpawning.set(false);
                        return;
                    }
                    plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> processSpawn(world, loc));
                });
            }
        } catch (Throwable t) {
            isSpawning.set(false);
        }
    }

    private void processSpawn(World world, Location loc) {
        try {
            if (!plugin.isEnabled()) return;
            TextDisplay spawned = findOrSpawnInLoadedChunk(world, loc);
            this.displayEntity = spawned;
            if (spawned != null) {
                if (isPersonalized()) {
                    updateNearbyPersonalized(loc);
                } else {
                    updateGlobal(spawned);
                }
            }
        } finally {
            isSpawning.set(false);
        }
    }

    private TextDisplay findOrSpawnInLoadedChunk(World world, Location loc) {
        TextDisplay found = null;
        List<TextDisplay> duplicatesToRemove = new ArrayList<>();

        for (Entity ent : world.getNearbyEntities(loc, 3.0, 3.0, 3.0)) {
            if (ent instanceof TextDisplay td) {
                String tag = td.getPersistentDataContainer().get(holoKey, PersistentDataType.STRING);
                if (tag != null && !tag.equalsIgnoreCase(data.id()) && plugin.getHologramManager().isKnownHologram(tag)) {
                    continue;
                }

                boolean isOurHolo = (tag != null && tag.equalsIgnoreCase(data.id())) ||
                                    (tag == null && td.getLocation().distanceSquared(loc) <= 6.25);

                if (isOurHolo) {
                    if (found == null && td.isValid()) {
                        found = td;
                    } else {
                        duplicatesToRemove.add(td);
                    }
                }
            }
        }

        for (TextDisplay dup : duplicatesToRemove) {
            plugin.getHologramManager().unregisterEntity(dup.getEntityId());
            dup.remove();
        }

        if (found != null) {
            applyDisplayProperties(found);
            plugin.getHologramManager().registerEntity(found.getEntityId(), this);
            if (!found.getUniqueId().equals(data.entityUuid())) {
                updateTrackedUuid(found.getUniqueId());
            }
            return found;
        }

        TextDisplay spawned = world.spawn(loc, TextDisplay.class, this::applyDisplayProperties);
        plugin.getHologramManager().registerEntity(spawned.getEntityId(), this);
        updateTrackedUuid(spawned.getUniqueId());
        return spawned;
    }

    private void applyDisplayProperties(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(60, 0, 0, 0));
        display.setShadowed(true);
        boolean isLeaderboard = data.id().startsWith("top_") || data.id().equals("baltop");
        display.setAlignment(isLeaderboard ? TextDisplay.TextAlignment.LEFT : TextDisplay.TextAlignment.CENTER);
        display.setLineWidth(400);
        display.setPersistent(false);
        display.getPersistentDataContainer().set(holoKey, PersistentDataType.STRING, data.id());
        display.text(ColorUtils.parse(buildBaseContent()));
    }

    private void updateTrackedUuid(UUID newUuid) {
        this.data = new HologramData(
                data.id(), data.worldName(), data.x(), data.y(), data.z(),
                data.yaw(), data.pitch(), newUuid
        );
        plugin.getHologramManager().getDao().updateEntityUuidAsync(data.id(), newUuid);
    }

    public void onPlayerTrack(SlowyCore plugin, Player player) {
        if (player == null || !player.isOnline()) return;
        if (!isPersonalized()) return; // Non-personalized holograms are synced automatically by vanilla

        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                sendCustomTextToPlayer(player, true);
            }
        }, null, 1L);
    }

    public void onPlayerUntrack(Player player) {
        if (player != null) {
            lastSentContent.remove(player.getUniqueId());
        }
    }

    private void updateNearbyPersonalized(Location loc) {
        TextDisplay entity = displayEntity;
        if (entity == null || !entity.isValid()) return;

        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= VIEW_DISTANCE_SQ) {
                sendCustomTextToPlayer(player, false);
            }
        }
    }

    public void updateForPlayer(Player player, boolean force) {
        if (player == null || !player.isOnline() || !isPersonalized()) return;
        TextDisplay entity = displayEntity;
        if (entity == null || !entity.isValid()) return;

        Location loc = data.toLocation();
        if (loc == null || !Objects.equals(player.getWorld(), loc.getWorld())) return;
        if (player.getLocation().distanceSquared(loc) > VIEW_DISTANCE_SQ) return;

        sendCustomTextToPlayer(player, force);
    }

    public void sendCustomTextToPlayer(Player player, boolean force) {
        TextDisplay entity = displayEntity;
        if (entity == null || !entity.isValid() || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        String content = buildContent(player);

        if (!force && content.equals(lastSentContent.get(uuid))) {
            return;
        }
        lastSentContent.put(uuid, content);

        Component advComp = ColorUtils.parse(content);
        net.minecraft.network.chat.Component nmsComp = PaperAdventure.asVanilla(advComp);

        var dataValue = SynchedEntityData.DataValue.create(TEXT_ACCESSOR, nmsComp);
        var packet = new ClientboundSetEntityDataPacket(entity.getEntityId(), List.of(dataValue));

        ((CraftPlayer) player).getHandle().connection.send(packet);
    }

    public String buildBaseContent() {
        return buildContent(null);
    }

    public record LeaderboardRecord(
            UUID uuid,
            String username,
            double value,
            String formattedValue,
            @Nullable String rawNumber,
            @Nullable String icon,
            @Nullable String colorTag,
            boolean isMoney
    ) {
        public static LeaderboardRecord money(UUID uuid, String username, double value, String formattedValue) {
            return new LeaderboardRecord(uuid, username, value, formattedValue, null, null, "<#39FF14>", true);
        }

        public static LeaderboardRecord nonMoney(UUID uuid, String username, double value, String rawNumber, String icon, String colorTag) {
            String formatted = colorTag + rawNumber + " " + icon + "</" + colorTag.substring(1);
            return new LeaderboardRecord(uuid, username, value, formatted, rawNumber, icon, colorTag, false);
        }
    }

    private String renderLeaderboard(String title, List<LeaderboardRecord> records, @Nullable Player viewer) {
        var sb = new StringBuilder(title).append("\n\n");

        int viewerRank = -1;
        LeaderboardRecord viewerRecord = null;

        if (viewer != null) {
            UUID vUuid = viewer.getUniqueId();
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).uuid().equals(vUuid)) {
                    viewerRank = i + 1;
                    viewerRecord = records.get(i);
                    break;
                }
            }
        }

        int maxNameWidth = 0;
        int maxNumWidth = 0;
        int maxRecords = Math.min(10, records.size());

        for (int i = 0; i < maxRecords; i++) {
            LeaderboardRecord r = records.get(i);
            String uName = (r.username() != null) ? r.username() : "Player";
            if (uName.length() > 16) uName = uName.substring(0, 16);
            maxNameWidth = Math.max(maxNameWidth, FontWidthUtil.getStringWidth(uName));
            if (!r.isMoney() && r.rawNumber() != null) {
                maxNumWidth = Math.max(maxNumWidth, FontWidthUtil.getStringWidth(r.rawNumber()));
            }
        }

        if (viewer != null && viewerRank > 10 && viewerRecord != null) {
            String vName = viewer.getName();
            if (vName.length() > 16) vName = vName.substring(0, 16);
            maxNameWidth = Math.max(maxNameWidth, FontWidthUtil.getStringWidth(vName));
            if (!viewerRecord.isMoney() && viewerRecord.rawNumber() != null) {
                maxNumWidth = Math.max(maxNumWidth, FontWidthUtil.getStringWidth(viewerRecord.rawNumber()));
            }
        }

        int targetNumPx = maxNumWidth > 0 ? (maxNumWidth + 12) : 32;
        String themeColor = extractPrimaryColor(title);
        String themeEnd = "</" + themeColor.substring(1);

        for (int rank = 1; rank <= 10; rank++) {
            boolean isTop10 = (rank == 10);
            String rankStr = String.valueOf(rank);
            String rankPrefix = isTop10 ? themeColor + rankStr + themeEnd : "<bold> </bold>" + themeColor + rankStr + themeEnd;
            String gapAfterRank = isTop10 ? "  " : " <bold> </bold>";

            if (rank <= records.size()) {
                LeaderboardRecord rec = records.get(rank - 1);
                sb.append(renderLeaderboardRow(rankPrefix, gapAfterRank, rec, maxNameWidth, targetNumPx, themeColor, themeEnd)).append('\n');
            } else {
                sb.append(rankPrefix).append(gapAfterRank).append("<dark_gray>--</dark_gray>\n");
            }
        }

        if (viewer != null && viewerRank > 10 && viewerRecord != null) {
            String vRankStr = String.valueOf(viewerRank);
            int vRankWidth = FontWidthUtil.getStringWidth(vRankStr);
            int vGap = 20 - vRankWidth;
            String vGapStr = vGap > 0 ? FontWidthUtil.buildSpaces(vGap) : " ";
            String vRankPrefix = themeColor + vRankStr + themeEnd;
            sb.append('\n').append(renderLeaderboardRow(vRankPrefix, vGapStr, viewerRecord, maxNameWidth, targetNumPx, themeColor, themeEnd));
        }

        return sb.toString().trim();
    }

    private String renderLeaderboardRow(String rankPrefix, String gapAfterRank, LeaderboardRecord rec, int maxNameWidth, int targetNumPx, String themeColor, String themeEnd) {
        String rawName = rec.username() != null ? rec.username() : "Player";
        if (rawName.length() > 16) rawName = rawName.substring(0, 16);

        int nameWidth = FontWidthUtil.getStringWidth(rawName);
        int totalGapToValue = (maxNameWidth - nameWidth) + 8;
        String nameCol = "<#E0F8FF>" + rawName + "</#E0F8FF>" + FontWidthUtil.buildSpaces(totalGapToValue);

        String valueCol;
        if (rec.isMoney()) {
            valueCol = themeColor + "$" + plugin.getEconomyManager().formatNicest(rec.value()) + themeEnd;
        } else if (rec.rawNumber() == null || rec.icon() == null) {
            valueCol = rec.formattedValue();
        } else {
            int numWidth = FontWidthUtil.getStringWidth(rec.rawNumber());
            int numRem = targetNumPx - numWidth;
            valueCol = FontWidthUtil.buildSpaces(numRem) + themeColor + rec.rawNumber() + " " + rec.icon() + themeEnd;
        }

        return rankPrefix + gapAfterRank + nameCol + valueCol;
    }

    private String extractPrimaryColor(String title) {
        if (title != null && title.startsWith("<#") && title.length() >= 9 && title.charAt(8) == '>') {
            return title.substring(0, 9);
        }
        return "<#00F5FF>";
    }

    private List<LeaderboardRecord> getCachedOrFetchLeaderboard(String id) {
        long now = System.currentTimeMillis();
        if (now - lastLbCacheUpdate > 3000L) {
            LB_CACHE.clear();
            lastLbCacheUpdate = now;
        }

        return LB_CACHE.computeIfAbsent(id, key -> switch (key) {
            case "top_money", "top_balance", "baltop" -> plugin.getEconomyManager().getAllAccounts().stream()
                    .sorted(Comparator.comparingDouble(Account::getBalance).reversed())
                    .map(acc -> LeaderboardRecord.money(
                            acc.getUuid(),
                            acc.getUsername() != null ? acc.getUsername() : "Player",
                            acc.getBalance(),
                            "<#39FF14>$" + plugin.getEconomyManager().formatNicest(acc.getBalance()) + "</#39FF14>"
                    )).toList();

            case "top_shards" -> plugin.getEconomyManager().getAllAccounts().stream()
                    .sorted(Comparator.comparingLong(Account::getShards).reversed())
                    .map(acc -> LeaderboardRecord.nonMoney(
                            acc.getUuid(),
                            acc.getUsername() != null ? acc.getUsername() : "Player",
                            acc.getShards(),
                            String.format(Locale.US, "%,d", acc.getShards()),
                            "★",
                            "<#FF00BD>"
                    )).toList();

            case "top_playtime" -> Bukkit.getOnlinePlayers().stream()
                    .map(p -> {
                        int ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
                        int minutes = (ticks / 20) / 60;
                        int hours = minutes / 60;
                        int remMin = minutes % 60;
                        String timeStr = hours > 0 ? (hours + "h " + remMin + "m") : (remMin + "m");
                        return LeaderboardRecord.nonMoney(p.getUniqueId(), p.getName(), ticks, timeStr, "⏱", "<#00F5FF>");
                    })
                    .sorted(Comparator.comparingDouble(LeaderboardRecord::value).reversed())
                    .toList();

            case "top_deaths" -> Bukkit.getOnlinePlayers().stream()
                    .map(p -> {
                        int deaths = p.getStatistic(Statistic.DEATHS);
                        return LeaderboardRecord.nonMoney(p.getUniqueId(), p.getName(), deaths, String.format(Locale.US, "%,d", deaths), "☠", "<#FF0055>");
                    })
                    .sorted(Comparator.comparingDouble(LeaderboardRecord::value).reversed())
                    .toList();

            case "top_mobs_killed" -> Bukkit.getOnlinePlayers().stream()
                    .map(p -> {
                        int kills = p.getStatistic(Statistic.MOB_KILLS);
                        return LeaderboardRecord.nonMoney(p.getUniqueId(), p.getName(), kills, String.format(Locale.US, "%,d", kills), "⚔", "<#FF7A00>");
                    })
                    .sorted(Comparator.comparingDouble(LeaderboardRecord::value).reversed())
                    .toList();

            case "top_blocks" -> plugin.getBlockMinedManager().getAllSorted().stream()
                    .map(entry -> LeaderboardRecord.nonMoney(
                            entry.uuid(),
                            entry.name(),
                            entry.count(),
                            String.format(Locale.US, "%,d", entry.count()),
                            "⛏",
                            "<#00F5FF>"
                    )).toList();

            case "top_spent" -> plugin.getEconomyManager().getAllAccounts().stream()
                    .filter(acc -> acc.getTotalSpent() > 0)
                    .sorted(Comparator.comparingDouble(Account::getTotalSpent).reversed())
                    .map(acc -> LeaderboardRecord.money(
                            acc.getUuid(),
                            acc.getUsername() != null ? acc.getUsername() : "Player",
                            acc.getTotalSpent(),
                            "<#39FF14>$" + plugin.getEconomyManager().formatNicest(acc.getTotalSpent()) + "</#39FF14>"
                    )).toList();

            case "top_sell" -> plugin.getEconomyManager().getAllAccounts().stream()
                    .filter(acc -> acc.getTotalSold() > 0)
                    .sorted(Comparator.comparingDouble(Account::getTotalSold).reversed())
                    .map(acc -> LeaderboardRecord.money(
                            acc.getUuid(),
                            acc.getUsername() != null ? acc.getUsername() : "Player",
                            acc.getTotalSold(),
                            "<#39FF14>$" + plugin.getEconomyManager().formatNicest(acc.getTotalSold()) + "</#39FF14>"
                    )).toList();

            case "top_streak", "top_daily", "top_daily_streak" -> (plugin.getDailyManager() != null)
                    ? plugin.getDailyManager().getAllSortedStreaks().stream()
                    .map(entry -> LeaderboardRecord.nonMoney(
                            entry.uuid(),
                            entry.name(),
                            entry.streak(),
                            String.valueOf(entry.streak()),
                            "✦",
                            "<#1DA1F2>"
                    )).toList()
                    : Collections.emptyList();

            default -> Collections.emptyList();
        });
    }

    public String buildContent(@Nullable Player viewer) {
        String id = data.id().toLowerCase(Locale.ROOT);

        // Leaderboards via Cache
        if (id.startsWith("top_") || id.equals("baltop")) {
            return switch (id) {
                case "top_money", "top_balance", "baltop" -> renderLeaderboard("<#39FF14><bold>ᴛᴏᴘ ʙᴀʟᴀɴᴄᴇ</bold></#39FF14>", getCachedOrFetchLeaderboard(id), viewer);
                case "top_shards" -> renderLeaderboard("<#FF00BD><bold>ᴛᴏᴘ ꜱʜᴀʀᴅꜱ</bold></#FF00BD>", getCachedOrFetchLeaderboard(id), viewer);
                case "top_playtime" -> renderLeaderboard("<#00F5FF><bold>ᴛᴏᴘ ᴘʟᴀʏᴛɪᴍᴇ</bold></#00F5FF>", getCachedOrFetchLeaderboard(id), viewer);
                case "top_deaths" -> renderLeaderboard("<#FF0055><bold>ᴛᴏᴘ ᴅᴇᴀᴛʜꜱ</bold></#FF0055>", getCachedOrFetchLeaderboard(id), viewer);
                case "top_mobs_killed" -> renderLeaderboard("<#FF7A00><bold>ᴛᴏᴘ ᴍᴏʙꜱ ᴋɪʟʟᴇᴅ</bold></#FF7A00>", getCachedOrFetchLeaderboard(id), viewer);
                case "top_blocks" -> renderLeaderboard("<#00F5FF><bold>ᴛᴏᴘ ʙʟᴏᴄᴋꜱ ᴍɪɴᴇᴅ</bold></#00F5FF>", getCachedOrFetchLeaderboard(id), viewer);
                case "top_spent" -> renderLeaderboard("<#FF0055><bold>ᴛᴏᴘ ᴍᴏɴᴇʏ ꜱᴘᴇɴᴛ</bold></#FF0055>", getCachedOrFetchLeaderboard(id), viewer);
                case "top_sell" -> renderLeaderboard("<#39FF14><bold>ᴛᴏᴘ ꜱᴇʟʟ</bold></#39FF14>", getCachedOrFetchLeaderboard(id), viewer);
                case "top_streak", "top_daily", "top_daily_streak" -> renderLeaderboard("<#1DA1F2><bold>ᴛᴏᴘ ᴅᴀɪʟʏ ꜱᴛʀᴇᴀᴋ</bold></#1DA1F2>", getCachedOrFetchLeaderboard(id), viewer);
                default -> "<#1DA1F2>" + id.toUpperCase(Locale.ROOT);
            };
        }

        // Portals (Global)
        if (id.startsWith("portal_")) {
            return switch (id) {
                case "portal_overworld" -> {
                    long count = Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !CoreConfig.isHubWorld(p.getWorld()) && p.getWorld().getEnvironment() == World.Environment.NORMAL)
                            .count();
                    yield "<#39FF14><bold>ᴏᴠᴇʀᴡᴏʀʟᴅ</bold></#39FF14>\n<#E0F8FF>" + count + (count == 1 ? " Player" : " Players") + "</#E0F8FF>";
                }
                case "portal_nether" -> {
                    long count = Bukkit.getOnlinePlayers().stream().filter(p -> p.getWorld().getEnvironment() == World.Environment.NETHER).count();
                    yield "<#FF7A00><bold>ɴᴇᴛʜᴇʀ</bold></#FF7A00>\n<#E0F8FF>" + count + (count == 1 ? " Player" : " Players") + "</#E0F8FF>";
                }
                case "portal_the_end" -> {
                    long count = Bukkit.getOnlinePlayers().stream().filter(p -> p.getWorld().getEnvironment() == World.Environment.THE_END).count();
                    yield "<#FF00BD><bold>ᴛʜᴇ ᴇɴᴅ</bold></#FF00BD>\n<#E0F8FF>" + count + (count == 1 ? " Player" : " Players") + "</#E0F8FF>";
                }
                default -> "<#1DA1F2>" + id.toUpperCase(Locale.ROOT);
            };
        }

        if (id.equals("portal_afk") || id.equals("afk_zone")) {
            long count = Bukkit.getOnlinePlayers().stream().filter(p -> p.getWorld().getName().toLowerCase(Locale.ROOT).contains(CoreConfig.AFK_WORLD_NAME)).count();
            return "<#00F5FF><bold>ᴀꜰᴋ ᴢᴏɴᴇ</bold></#00F5FF>\n<#E0F8FF>" + count + (count == 1 ? " Player" : " Players") + "</#E0F8FF>";
        }

        if (id.equals("afk_info")) {
            return """
                    <#1DA1F2><bold>AFK ZONE</bold></#1DA1F2>
                    <gray>Stay here to earn <#FF00BD>Shards ★</#FF00BD></gray>
                    <dark_gray>+1 Shard every 1 minute</dark_gray>""";
        }

        if (id.equals("greeting")) {
            String name = (viewer != null && viewer.isOnline()) ? viewer.getName() : "Player";
            return """
                    <#1DA1F2><bold>SlowySMP</bold></#1DA1F2>
                    <#E0F8FF>Welcome <#FFE600>%s</#FFE600></#E0F8FF>
                    <gray>Type <#1DA1F2>/rtp</#1DA1F2> to get started!</gray>
                    <gray>Type <#39FF14>/sell</#39FF14> to start making money!</gray>""".formatted(name);
        }

        // Arenas
        if (id.endsWith("_arena") || id.equals("casual_pvp")) {
            return switch (id) {
                case "casual_pvp" -> "<#FF0055><bold>ᴄᴀꜱᴜᴀʟ ᴘᴠᴘ</bold></#FF0055>\n<#FF0055>Under Construction</#FF0055>";
                case "badlands_arena" -> "<#FF7A00><bold>ʙᴀᴅʟᴀɴᴅꜱ ᴀʀᴇɴᴀ</bold></#FF7A00>\n<#FF0055>Under Construction</#FF0055>";
                case "desert_arena" -> "<#FFE600><bold>ᴅᴇꜱᴇʀᴛ ᴀʀᴇɴᴀ</bold></#FFE600>\n<#FF0055>Under Construction</#FF0055>";
                case "flat_arena" -> "<#39FF14><bold>ꜰʟᴀᴛ ᴀʀᴇɴᴀ</bold></#39FF14>\n<#FF0055>Under Construction</#FF0055>";
                case "plains_arena" -> "<#00F5FF><bold>ᴘʟᴀɪɴꜱ ᴀʀᴇɴᴀ</bold></#00F5FF>\n<#FF0055>Under Construction</#FF0055>";
                default -> "<#1DA1F2>" + id.toUpperCase(Locale.ROOT);
            };
        }

        // NPCs
        if (id.equals("npc_daily")) {
            if (plugin.getDailyManager() != null) {
                return plugin.getDailyManager().renderHologram(viewer);
            }
            return "<#1DA1F2>/ᴅᴀɪʟʏ</#1DA1F2>\n<#FFE600>0 Days</#FFE600>\n<#39FF14>$1,000</#39FF14> <gray>|</gray> <#FF00BD>★ 10</#FF00BD>\n<#39FF14>claim</#39FF14>";
        }

        if (id.startsWith("npc_")) {
            String name = id.substring(4);
            return "<#1DA1F2>/" + toSmallCaps(name) + "</#1DA1F2>\n<#E0F8FF>‹ᴄʟɪᴄᴋ›</#E0F8FF>";
        }

        return "<#1DA1F2>" + data.id().toUpperCase(Locale.ROOT);
    }

    private String toSmallCaps(String text) {
        var sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            sb.append(switch (Character.toLowerCase(c)) {
                case 'a' -> 'ᴀ'; case 'b' -> 'ʙ'; case 'c' -> 'ᴄ'; case 'd' -> 'ᴅ';
                case 'e' -> 'ᴇ'; case 'f' -> 'ꜰ'; case 'g' -> 'ɢ'; case 'h' -> 'ʜ';
                case 'i' -> 'ɪ'; case 'j' -> 'ᴊ'; case 'k' -> 'ᴋ'; case 'l' -> 'ʟ';
                case 'm' -> 'ᴍ'; case 'n' -> 'ɴ'; case 'o' -> 'ᴏ'; case 'p' -> 'ᴘ';
                case 'q' -> 'ǫ'; case 'r' -> 'ʀ'; case 's' -> 'ꜱ'; case 't' -> 'ᴛ';
                case 'u' -> 'ᴜ'; case 'v' -> 'ᴠ'; case 'w' -> 'ᴡ'; case 'x' -> 'x';
                case 'y' -> 'ʏ'; case 'z' -> 'ᴢ'; default -> c;
            });
        }
        return sb.toString();
    }

    public void cleanupPlayer(UUID uuid) {
        lastSentContent.remove(uuid);
    }

    public void remove() {
        TextDisplay entity = displayEntity;
        displayEntity = null;
        isSpawning.set(false);
        lastSentContent.clear();

        if (entity != null && entity.isValid()) {
            plugin.getHologramManager().unregisterEntity(entity.getEntityId());
            if (!plugin.isEnabled()) {
                try { entity.remove(); } catch (Throwable ignored) {}
                return;
            }

            Location loc = entity.getLocation();
            World w = loc.getWorld();
            if (w != null) {
                try {
                    plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                        if (entity.isValid()) entity.remove();
                    });
                } catch (Throwable t) {
                    try { entity.remove(); } catch (Throwable ignored) {}
                }
            }
        }
    }
}

package dev.slowy.core.crate;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@NullMarked
public final class CrateManager implements Lifecycle {

    public record GachaSession(
            ScheduledTask task,
            CrateReward winningReward,
            CrateDefinition crate,
            Inventory inventory,
            long createdAt
    ) {}

    private final SlowyCore plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, CrateDefinition> crates = new LinkedHashMap<>();
    private final Map<CrateBlockKey, String> boundBlocks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> keyBalanceCache = new ConcurrentHashMap<>();
    private final Map<UUID, GachaSession> activeGachaSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> noKeyCooldown = new ConcurrentHashMap<>();

    private @Nullable CrateHologramManager hologramManager;
    private @Nullable KeyAllManager keyAllManager;

    public CrateManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");

        this.crates.putAll(CrateDefinition.createDefaultCrates(plugin));
        loadBoundBlocks();
    }

    public void setHologramManager(CrateHologramManager hologramManager) { this.hologramManager = hologramManager; }
    public @Nullable CrateHologramManager getHologramManager() { return hologramManager; }

    public void setKeyAllManager(KeyAllManager keyAllManager) { this.keyAllManager = keyAllManager; }
    public @Nullable KeyAllManager getKeyAllManager() { return keyAllManager; }

    public Collection<CrateDefinition> getCrates() { return Collections.unmodifiableCollection(crates.values()); }

    public @Nullable CrateDefinition getCrate(String id) {
        return crates.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<CrateBlockKey, String> getBoundBlocks() { return boundBlocks; }

    public @Nullable CrateDefinition getBoundCrate(Block block) {
        String crateId = boundBlocks.get(CrateBlockKey.fromBlock(block));
        return crateId != null ? getCrate(crateId) : null;
    }

    public void loadBoundBlocks() {
        boundBlocks.clear();
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT world, x, y, z, crate_id FROM crate_blocks;");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                boundBlocks.put(
                        new CrateBlockKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                        rs.getString("crate_id").toLowerCase(Locale.ROOT)
                );
            }
        } catch (SQLException e) {
            plugin.getSlf4jLogger().error("Failed loading crate_blocks: {}", e.getMessage());
        }
    }

    // ── Key Management (Non-blocking & Thread-Safe) ──────────────────────────

    public void loadPlayerDataAsync(UUID uuid) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            Map<String, Integer> balances = new ConcurrentHashMap<>();
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement ps = con.prepareStatement("SELECT crate_id, amount FROM player_crate_keys WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        balances.put(rs.getString("crate_id").toLowerCase(Locale.ROOT), rs.getInt("amount"));
                    }
                }
            } catch (SQLException e) {
                plugin.getSlf4jLogger().warn("Failed loading keys for {}: {}", uuid, e.getMessage());
            }
            keyBalanceCache.put(uuid, balances);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && hologramManager != null) {
                player.getScheduler().run(plugin, t -> hologramManager.updateForPlayer(player), null);
            }
        });
    }

    public void unloadPlayerData(UUID uuid) {
        keyBalanceCache.remove(uuid);
        noKeyCooldown.remove(uuid);

        GachaSession session = activeGachaSessions.remove(uuid);
        if (session != null) {
            session.task().cancel();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                session.winningReward().grant(plugin, player);
            }
        }
    }

    public int getKeyBalance(UUID uuid, String crateId) {
        Map<String, Integer> map = keyBalanceCache.get(uuid);
        if (map == null) return 0;
        return map.getOrDefault(crateId.toLowerCase(Locale.ROOT), 0);
    }

    public void addKeys(UUID uuid, String crateId, int amount) {
        if (amount <= 0) return;
        String id = crateId.toLowerCase(Locale.ROOT);
        keyBalanceCache.computeIfAbsent(uuid, _ -> new ConcurrentHashMap<>()).compute(id, (_, current) -> {
            int updated = (current == null ? 0 : current) + amount;
            persistKeyAsync(uuid, id, updated);
            return updated;
        });
    }

    public boolean takeKeys(UUID uuid, String crateId, int amount) {
        if (amount <= 0) return false;
        String id = crateId.toLowerCase(Locale.ROOT);
        Map<String, Integer> balances = keyBalanceCache.computeIfAbsent(uuid, _ -> new ConcurrentHashMap<>());

        synchronized (balances) {
            int current = balances.getOrDefault(id, 0);
            if (current < amount) return false;
            int updated = current - amount;
            balances.put(id, updated);
            persistKeyAsync(uuid, id, updated);
            return true;
        }
    }

    public void setKeys(UUID uuid, String crateId, int amount) {
        String id = crateId.toLowerCase(Locale.ROOT);
        int clean = Math.max(0, amount);
        keyBalanceCache.computeIfAbsent(uuid, _ -> new ConcurrentHashMap<>()).put(id, clean);
        persistKeyAsync(uuid, id, clean);
    }

    private void persistKeyAsync(UUID uuid, String crateId, int amount) {
        long now = System.currentTimeMillis();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO player_crate_keys (player_uuid, crate_id, amount, updated_at) VALUES (?, ?, ?, ?) " +
                                 "ON CONFLICT(player_uuid, crate_id) DO UPDATE SET amount = excluded.amount, updated_at = excluded.updated_at")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, crateId);
                ps.setInt(3, amount);
                ps.setLong(4, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getSlf4jLogger().warn("Failed saving player_crate_keys for {}: {}", uuid, e.getMessage());
            }
        });

        Player online = Bukkit.getPlayer(uuid);
        if (online != null && hologramManager != null) {
            online.getScheduler().run(plugin, t -> hologramManager.updateForPlayer(online), null);
        }
    }

    public boolean hasKey(Player player, CrateDefinition crate) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (crate.isPhysicalKey(it)) return true;
        }
        return getKeyBalance(player.getUniqueId(), crate.getId()) > 0;
    }

    public boolean consumeKey(Player player, CrateDefinition crate) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (crate.isPhysicalKey(main)) {
            main.subtract(1);
            return true;
        }

        for (ItemStack it : player.getInventory().getContents()) {
            if (crate.isPhysicalKey(it)) {
                it.subtract(1);
                return true;
            }
        }

        return takeKeys(player.getUniqueId(), crate.getId(), 1);
    }

    public void openCrate(Player player, CrateDefinition crate, Block block) {
        if (activeGachaSessions.containsKey(player.getUniqueId())) {
            player.sendActionBar(ColorUtils.parse("<#FFE600>⚠ You are already opening a crate!</#FFE600>"));
            return;
        }

        if (!hasKey(player, crate)) {
            handleNoKey(player, crate);
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            player.sendActionBar(ColorUtils.parse("<#FF0055>⚠ Inventory full! Clear at least 1 slot before opening.</#FF0055>"));
            return;
        }

        if (crate.getOpenType() == CrateDefinition.OpenType.CHOOSE_ONE) {
            openChooseMenu(player, crate);
            return;
        }

        if (!consumeKey(player, crate)) {
            handleNoKey(player, crate);
            return;
        }

        if (hologramManager != null) {
            hologramManager.updateForPlayer(player);
        }

        startGachaRoulette(player, crate, block);
    }

    public void handleNoKey(Player player, CrateDefinition crate) {
        long now = System.currentTimeMillis();
        Long last = noKeyCooldown.get(player.getUniqueId());
        if (last != null && now - last < 2500L) return;
        noKeyCooldown.put(player.getUniqueId(), now);

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
        player.sendActionBar(ColorUtils.parse("<#E0F8FF>Requires <" + crate.getColorHex() + ">1 " + crate.getFormattedKeyName() + "</" + crate.getColorHex() + "> to open</#E0F8FF>"));
    }

    public void openChooseMenu(Player player, CrateDefinition crate) {
        ChooseHolder holder = new ChooseHolder(crate);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtils.parse(crate.getDisplayName()));

        int[] slots = {11, 12, 13, 14, 15};
        List<CrateReward> rewards = crate.getRewards();
        for (int i = 0; i < rewards.size() && i < slots.length; i++) {
            int slot = slots[i];
            CrateReward reward = rewards.get(i);
            inv.setItem(slot, reward.createDisplayItem(plugin));
            holder.registerSlot(slot, reward);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    public void openConfirmMenu(Player player, CrateDefinition crate, CrateReward reward) {
        ConfirmHolder holder = new ConfirmHolder(crate, reward);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtils.parse("<dark_gray>Confirm Selection</dark_gray>"));

        ItemStack cancelItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        cancelItem.editMeta(m -> m.displayName(ColorUtils.parseItem("<#FF0055>Cancel</#FF0055>")));
        inv.setItem(11, cancelItem);

        inv.setItem(13, reward.createDisplayItem(plugin));

        ItemStack confirmItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        confirmItem.editMeta(m -> m.displayName(ColorUtils.parseItem("<#39FF14>Confirm</#39FF14>")));
        inv.setItem(15, confirmItem);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    public void handleConfirmClaim(Player player, ConfirmHolder holder) {
        CrateDefinition crate = holder.getCrate();
        CrateReward reward = holder.getSelectedReward();

        if (!hasKey(player, crate)) {
            handleNoKey(player, crate);
            player.closeInventory();
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            player.sendActionBar(ColorUtils.parse("<#FF0055>⚠ Inventory full! Clear at least 1 slot before claiming.</#FF0055>"));
            return;
        }

        if (!consumeKey(player, crate)) {
            handleNoKey(player, crate);
            player.closeInventory();
            return;
        }

        if (hologramManager != null) {
            hologramManager.updateForPlayer(player);
        }

        reward.grant(plugin, player);
        player.closeInventory();

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.05);

        player.showTitle(Title.title(
                ColorUtils.parse(crate.getDisplayName()),
                ColorUtils.parse(reward.displayName()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2200), Duration.ofMillis(500))
        ));
        player.sendActionBar(ColorUtils.parse("<#39FF14>✔ Claimed: </#39FF14>" + reward.displayName()));

        if (crate.isBroadcastOnClaim()) {
            Component bc = ColorUtils.parse("<#FF00BD>★ Legendary Crate ★</#FF00BD> <#FFE600>" +
                    player.getName() + "</#FFE600> <gray>claimed </gray>" + reward.displayName() + "<gray>!</gray>");
            plugin.getServer().sendActionBar(bc);
        }
    }

    // ── Gacha Roulette Engine (Folia-Compliant Scheduler) ─────────────────────

    private void startGachaRoulette(Player player, CrateDefinition crate, Block block) {
        List<CrateReward> allRewards = crate.getRewards();
        if (allRewards.isEmpty()) return;

        CrateReward winningReward = crate.pickRandomReward();
        GachaHolder holder = new GachaHolder(crate, winningReward);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtils.parse("<dark_gray>ʀᴏʟʟɪɴɢ...</dark_gray>"));

        ItemStack topPointer = new ItemStack(Material.HOPPER);
        topPointer.editMeta(m -> m.displayName(ColorUtils.parseItem("<#39FF14>▼ ᴡɪɴɴɪɴɢ ʀᴇᴡᴀʀᴅ ▼</#39FF14>")));
        inv.setItem(4, topPointer);

        ItemStack botPointer = new ItemStack(Material.LIME_CONCRETE);
        botPointer.editMeta(m -> m.displayName(ColorUtils.parseItem("<#39FF14>▲ ᴡɪɴɴɪɴɢ ʀᴇᴡᴀʀᴅ ▲</#39FF14>")));
        inv.setItem(22, botPointer);

        int[] rollSlots = {10, 11, 12, 13, 14, 15, 16};
        List<CrateReward> strip = new ArrayList<>(rollSlots.length);
        for (int slot : rollSlots) {
            CrateReward r = allRewards.get(ThreadLocalRandom.current().nextInt(allRewards.size()));
            strip.add(r);
            inv.setItem(slot, r.createDisplayItem(plugin));
        }

        player.openInventory(inv);

        // Folia Safe: execute block audio/particles in block's region
        Location blockLoc = block.getLocation().add(0.5, 0.5, 0.5);
        plugin.getServer().getRegionScheduler().execute(plugin, blockLoc, () -> {
            World w = block.getWorld();
            w.playSound(blockLoc, Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
            w.spawnParticle(Particle.ENCHANT, blockLoc, 20, 0.3, 0.3, 0.3, 0.05);
        });

        final int totalSteps = 36;

        ScheduledTask scheduledTask = player.getScheduler().runAtFixedRate(plugin, new java.util.function.Consumer<>() {
            int step = 0;
            int delay = 2;
            int tickCounter = 0;

            @Override
            public void accept(ScheduledTask task) {
                if (!player.isOnline()) {
                    task.cancel();
                    GachaSession s = activeGachaSessions.remove(player.getUniqueId());
                    if (s != null) s.winningReward().grant(plugin, player);
                    return;
                }

                if (!activeGachaSessions.containsKey(player.getUniqueId())) {
                    task.cancel();
                    return;
                }

                tickCounter++;
                if (tickCounter < delay) return;
                tickCounter = 0;

                step++;
                if (step > 24) delay = 3;
                if (step > 28) delay = 5;
                if (step > 32) delay = 8;
                if (step >= 35) delay = 12;

                for (int i = 0; i < rollSlots.length - 1; i++) {
                    strip.set(i, strip.get(i + 1));
                }

                CrateReward nextReward = (step == totalSteps - 3)
                        ? winningReward
                        : allRewards.get(ThreadLocalRandom.current().nextInt(allRewards.size()));
                strip.set(rollSlots.length - 1, nextReward);

                for (int i = 0; i < rollSlots.length; i++) {
                    inv.setItem(rollSlots[i], strip.get(i).createDisplayItem(plugin));
                }

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.2f);

                if (step >= totalSteps) {
                    task.cancel();
                    activeGachaSessions.remove(player.getUniqueId());

                    CrateReward finalWinner = strip.get(3);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
                    player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.05);

                    ItemStack goldBlock = new ItemStack(Material.GOLD_BLOCK);
                    goldBlock.editMeta(m -> m.displayName(ColorUtils.parseItem("<#FFE600>ᴄᴏɴɢʀᴀᴛꜱ!</#FFE600>")));
                    inv.setItem(4, goldBlock);
                    inv.setItem(22, goldBlock);

                    finalWinner.grant(plugin, player);

                    player.sendActionBar(ColorUtils.parse("<#39FF14>✔ Won: </#39FF14>" + finalWinner.displayName()));

                    if (crate.isBroadcastOnClaim()) {
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
                        Component broadcast = ColorUtils.parse(crate.getDisplayName() + " <dark_gray>»</dark_gray> <#FFE600>" +
                                player.getName() + "</#FFE600> <gray>won </gray>" + finalWinner.displayName() + "<gray>!</gray>");
                        plugin.getServer().sendActionBar(broadcast);
                    }

                    player.getScheduler().runDelayed(plugin, closeTask -> {
                        if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inv)) {
                            player.closeInventory();
                        }
                    }, null, 50L);
                }
            }
        }, null, 2L, 1L);

        activeGachaSessions.put(player.getUniqueId(), new GachaSession(scheduledTask, winningReward, crate, inv, System.currentTimeMillis()));
    }

    public void handleGachaClose(Player player) {
        GachaSession session = activeGachaSessions.get(player.getUniqueId());
        if (session == null) return;

        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline() && activeGachaSessions.containsKey(player.getUniqueId())) {
                player.openInventory(session.inventory());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
                player.sendActionBar(ColorUtils.parse("<#FF0055>⚠ Please wait until the rolling finishes!</#FF0055>"));
            }
        }, null, 1L);
    }

    public void tellLocationAndKeys(Player player, CrateDefinition crate) {
        int keys = getKeyBalance(player.getUniqueId(), crate.getId());
        if (keys <= 0) {
            handleNoKey(player, crate);
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        player.sendActionBar(ColorUtils.parse("<#E0F8FF>You have <" + crate.getColorHex() + ">" + keys + " " + crate.getFormattedKeyName() + "</" + crate.getColorHex() + "></#E0F8FF>"));
    }

    @Override
    public void onDisable() {
        for (GachaSession s : activeGachaSessions.values()) {
            s.task().cancel();
        }
        activeGachaSessions.clear();
        if (hologramManager != null) hologramManager.despawnAll();
        if (keyAllManager != null) keyAllManager.onDisable();
        keyBalanceCache.clear();
    }
}

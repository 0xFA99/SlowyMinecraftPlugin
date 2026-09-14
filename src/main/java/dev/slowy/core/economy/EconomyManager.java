package dev.slowy.core.economy;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.api.economy.EconomyService;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.storage.dao.EconomyDao;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@NullMarked
public final class EconomyManager implements Lifecycle, EconomyService {

    private final SlowyCore plugin;
    private final Logger logger;
    private final EconomyDao economyDao;

    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUuid = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyAccounts = ConcurrentHashMap.newKeySet();

    private @Nullable ScheduledTask playtimeTask;
    private @Nullable ScheduledTask autoSaveTask;

    // ThreadLocal menjamin formatters 100% thread-safe saat dipanggil dari Async task/PlaceholderAPI
    private final ThreadLocal<DecimalFormat> standardFormat = 
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)));
    private final ThreadLocal<DecimalFormat> commaFormat = 
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)));
    private final ThreadLocal<DecimalFormat> compactFormat = 
            ThreadLocal.withInitial(() -> new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US)));

    public EconomyManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.economyDao = new EconomyDao(databaseManager, logger);

        // Load awal database WAJIB synchronous saat server startup agar tidak tertimpa race condition
        loadDataSynchronously();
        startTasks();
    }

    private void loadDataSynchronously() {
        try {
            Map<UUID, Account> loaded = economyDao.loadAllSync();
            for (Account acc : loaded.values()) {
                acc.setOnDirty(() -> dirtyAccounts.add(acc.getUuid()));
            }
            accounts.putAll(loaded);
            for (Account acc : loaded.values()) {
                if (acc.getUsername() != null && !acc.getUsername().isBlank()) {
                    nameToUuid.put(acc.getUsername().toLowerCase(Locale.ROOT), acc.getUuid());
                }
            }
            logger.info("Synchronously loaded {} economy accounts.", accounts.size());
        } catch (Exception ex) {
            logger.error("Failed to load economy data: {}", ex.getMessage(), ex);
        }
    }

    private void startTasks() {
        // Interval 20L ticks = 1 detik real-time
        this.playtimeTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            checkPlaytimeRewards();
        }, 20L, 20L);

        // Async scheduler memakai satuan TimeUnit
        this.autoSaveTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
            flushDirtyAccounts();
        }, 60, 60, TimeUnit.SECONDS);
    }

    private void flushDirtyAccounts() {
        if (dirtyAccounts.isEmpty()) return;

        List<AccountSnapshot> snapshots = new ArrayList<>(dirtyAccounts.size());
        Iterator<UUID> it = dirtyAccounts.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            it.remove();
            Account acc = accounts.get(uuid);
            if (acc != null) {
                AccountSnapshot snapshot = acc.snapshotIfDirty();
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }
        }
        if (!snapshots.isEmpty()) {
            economyDao.saveSnapshotsAsync(snapshots);
        }
    }

    private void checkPlaytimeRewards() {
        int requiredSeconds = CoreConfig.SHARD_INTERVAL_MINUTES * 60;
        if (requiredSeconds <= 0) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Account account = getOrCreateAccount(player.getUniqueId(), player.getName());
            int current = account.addRewardProgressSeconds(1);

            if (current >= requiredSeconds) {
                account.setRewardProgressSeconds(current - requiredSeconds);
                account.depositShards(CoreConfig.SHARD_REWARD_AMOUNT);

                // Jalankan interaksi Player via Player Entity Scheduler agar Folia/Paper-safe
                player.getScheduler().run(plugin, entityTask -> {
                    String msg = CoreConfig.SHARD_REWARD_MESSAGE
                            .replace("{amount}", String.valueOf(CoreConfig.SHARD_REWARD_AMOUNT))
                            .replace("{minutes}", String.valueOf(CoreConfig.SHARD_INTERVAL_MINUTES));
                    player.sendActionBar(ColorUtils.parse(msg));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }, null);
            }
        }
    }

    @Override
    public Account getOrCreateAccount(UUID uuid, @Nullable String username) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Account account = accounts.computeIfAbsent(uuid, id -> {
            Account created = new Account(id, username, CoreConfig.STARTING_BALANCE, CoreConfig.STARTING_SHARDS, 0, () -> dirtyAccounts.add(id));
            dirtyAccounts.add(id);
            return created;
        });
        if (username != null && !username.isBlank()) {
            account.setUsername(username);
            nameToUuid.put(username.toLowerCase(Locale.ROOT), uuid);
        }
        return account;
    }

    @Override
    public @Nullable Account getAccount(UUID uuid) {
        if (uuid == null) return null;
        return accounts.get(uuid);
    }

    @Override
    public @Nullable Account getAccount(String username) {
        if (username == null || username.isBlank()) return null;
        UUID uuid = nameToUuid.get(username.toLowerCase(Locale.ROOT));
        if (uuid != null) return accounts.get(uuid);

        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            return accounts.get(online.getUniqueId());
        }
        return null; // Murni Query (tidak membuat akun baru tanpa izin)
    }

    @Override
    public double getBalance(UUID uuid) {
        Account account = getAccount(uuid);
        return account != null ? account.getBalance() : CoreConfig.STARTING_BALANCE;
    }

    @Override
    public boolean hasBalance(UUID uuid, double amount) {
        if (amount <= 0) return true;
        Account account = getAccount(uuid);
        return account != null && account.getBalance() >= amount;
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        if (amount <= 0) return false;
        Account account = getOrCreateAccount(uuid, null);
        return account.deposit(amount);
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0) return false;
        Account account = getAccount(uuid);
        return account != null && account.withdraw(amount);
    }

    @Override
    public void setBalance(UUID uuid, double amount) {
        Account account = getOrCreateAccount(uuid, null);
        account.setBalance(amount);
    }

    @Override
    public boolean transferMoney(UUID from, UUID to, double amount) {
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount) || from.equals(to)) {
            return false;
        }
        Account sender = getAccount(from);
        Account receiver = getAccount(to);
        if (sender == null || receiver == null) return false;

        Account first = from.compareTo(to) < 0 ? sender : receiver;
        Account second = from.compareTo(to) < 0 ? receiver : sender;

        synchronized (first) {
            synchronized (second) {
                if (sender.withdraw(amount)) {
                    receiver.deposit(amount);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public long getShards(UUID uuid) {
        Account account = getAccount(uuid);
        return account != null ? account.getShards() : CoreConfig.STARTING_SHARDS;
    }

    @Override
    public boolean hasShards(UUID uuid, long amount) {
        if (amount <= 0) return true;
        Account account = getAccount(uuid);
        return account != null && account.getShards() >= amount;
    }

    @Override
    public boolean depositShards(UUID uuid, long amount) {
        if (amount <= 0) return false;
        Account account = getOrCreateAccount(uuid, null);
        return account.depositShards(amount);
    }

    @Override
    public boolean withdrawShards(UUID uuid, long amount) {
        if (amount <= 0) return false;
        Account account = getAccount(uuid);
        return account != null && account.withdrawShards(amount);
    }

    @Override
    public void setShards(UUID uuid, long amount) {
        Account account = getOrCreateAccount(uuid, null);
        account.setShards(amount);
    }

    @Override
    public String format(double amount) {
        if (amount == Math.floor(amount) && !Double.isInfinite(amount)) {
            return commaFormat.get().format((long) amount);
        }
        return standardFormat.get().format(amount);
    }

    @Override
    public String formatNicest(double amount) {
        if (amount >= 1e12) return compactFormat.get().format(amount / 1e12) + "T";
        if (amount >= 1e9)  return compactFormat.get().format(amount / 1e9) + "B";
        if (amount >= 1e6)  return compactFormat.get().format(amount / 1e6) + "M";
        if (amount >= 1e3)  return compactFormat.get().format(amount / 1e3) + "K";
        return commaFormat.get().format(amount);
    }

    public Collection<Account> getAllAccounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    public void addTotalSold(UUID uuid, double amount, int itemsCount) {
        if (amount <= 0 || uuid == null) return;
        Account acc = getOrCreateAccount(uuid, null);
        acc.addTotalSold(amount, itemsCount);
    }

    public void addTotalSpent(UUID uuid, double amount) {
        if (amount <= 0 || uuid == null) return;
        Account acc = getOrCreateAccount(uuid, null);
        acc.addTotalSpent(amount);
    }

    public double getTotalSold(UUID uuid) {
        Account acc = getAccount(uuid);
        return acc != null ? acc.getTotalSold() : 0.0;
    }

    public double getTotalSpent(UUID uuid) {
        Account acc = getAccount(uuid);
        return acc != null ? acc.getTotalSpent() : 0.0;
    }

    public List<Account> getTopSell(int limit) {
        return accounts.values().stream()
                .filter(a -> a.getTotalSold() > 0)
                .sorted(Comparator.comparingDouble(Account::getTotalSold).reversed())
                .limit(limit)
                .toList();
    }

    public List<Account> getTopSpent(int limit) {
        return accounts.values().stream()
                .filter(a -> a.getTotalSpent() > 0)
                .sorted(Comparator.comparingDouble(Account::getTotalSpent).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public String getCurrencySymbol() {
        return CoreConfig.CURRENCY_SYMBOL;
    }

    @Override
    public void onDisable() {
        if (playtimeTask != null) playtimeTask.cancel();
        if (autoSaveTask != null) autoSaveTask.cancel();

        // Flush data dirty ke SQLite saat server mati secara synchronous
        if (!dirtyAccounts.isEmpty()) {
            List<AccountSnapshot> snapshots = new ArrayList<>();
            for (UUID uuid : dirtyAccounts) {
                Account acc = accounts.get(uuid);
                if (acc != null) {
                    snapshots.add(acc.snapshot());
                }
            }
            if (!snapshots.isEmpty()) {
                economyDao.saveAllSnapshotsSync(snapshots);
            }
            dirtyAccounts.clear();
        }

        accounts.clear();
        nameToUuid.clear();
        logger.info("EconomyManager synchronized and shut down cleanly.");
    }
}

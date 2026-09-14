package dev.slowy.core.economy;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

@NullMarked
public final class Account {

    private final UUID uuid;
    private volatile String username;
    private double balance;
    private long shards;
    private int rewardProgressSeconds;
    private double totalSold;
    private int totalItemsSold;
    private double totalSpent;
    private boolean dirty = false;
    private @Nullable Runnable onDirty;

    public Account(UUID uuid, @Nullable String username, double balance, long shards) {
        this(uuid, username, balance, shards, 0, 0.0, 0, 0.0, null);
    }

    public Account(UUID uuid, @Nullable String username, double balance, long shards, int rewardProgressSeconds) {
        this(uuid, username, balance, shards, rewardProgressSeconds, 0.0, 0, 0.0, null);
    }

    public Account(UUID uuid, @Nullable String username, double balance, long shards, int rewardProgressSeconds, @Nullable Runnable onDirty) {
        this(uuid, username, balance, shards, rewardProgressSeconds, 0.0, 0, 0.0, onDirty);
    }

    public Account(UUID uuid, @Nullable String username, double balance, long shards, int rewardProgressSeconds,
                   double totalSold, int totalItemsSold, double totalSpent) {
        this(uuid, username, balance, shards, rewardProgressSeconds, totalSold, totalItemsSold, totalSpent, null);
    }

    public Account(UUID uuid, @Nullable String username, double balance, long shards, int rewardProgressSeconds,
                   double totalSold, int totalItemsSold, double totalSpent, @Nullable Runnable onDirty) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.username = (username != null && !username.isBlank()) ? username : uuid.toString();
        this.balance = Math.max(0.0, balance);
        this.shards = Math.max(0L, shards);
        this.rewardProgressSeconds = Math.max(0, rewardProgressSeconds);
        this.totalSold = Math.max(0.0, totalSold);
        this.totalItemsSold = Math.max(0, totalItemsSold);
        this.totalSpent = Math.max(0.0, totalSpent);
        this.onDirty = onDirty;
        this.dirty = false;
    }

    public void setOnDirty(@Nullable Runnable onDirty) {
        this.onDirty = onDirty;
    }

    private void markDirty() {
        this.dirty = true;
        Runnable cb = this.onDirty;
        if (cb != null) {
            cb.run();
        }
    }

    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }

    public synchronized void setUsername(String username) {
        if (username != null && !username.isBlank() && !this.username.equals(username)) {
            this.username = username;
            markDirty();
        }
    }

    public synchronized double getBalance() { return balance; }

    public synchronized void setBalance(double balance) {
        if (Double.isNaN(balance) || Double.isInfinite(balance) || balance < 0) {
            throw new IllegalArgumentException("Invalid balance: " + balance);
        }
        this.balance = balance;
        markDirty();
    }

    public synchronized boolean deposit(double amount) {
        if (amount <= 0 || !Double.isFinite(amount)) return false;
        this.balance += amount;
        markDirty();
        return true;
    }

    public synchronized boolean withdraw(double amount) {
        if (amount <= 0 || !Double.isFinite(amount) || this.balance < amount) {
            return false;
        }
        this.balance -= amount;
        markDirty();
        return true;
    }

    public synchronized long getShards() { return shards; }

    public synchronized void setShards(long shards) {
        this.shards = Math.max(0L, shards);
        markDirty();
    }

    public synchronized boolean depositShards(long amount) {
        if (amount <= 0) return false;
        this.shards = Math.addExact(this.shards, amount); // Aman dari overflow silent
        markDirty();
        return true;
    }

    public synchronized boolean withdrawShards(long amount) {
        if (amount <= 0 || this.shards < amount) return false;
        this.shards -= amount;
        markDirty();
        return true;
    }

    public synchronized int addRewardProgressSeconds(int seconds) {
        this.rewardProgressSeconds = Math.max(0, this.rewardProgressSeconds + seconds);
        markDirty();
        return this.rewardProgressSeconds;
    }

    public synchronized void setRewardProgressSeconds(int seconds) {
        this.rewardProgressSeconds = Math.max(0, seconds);
        markDirty();
    }

    public synchronized double getTotalSold() { return totalSold; }
    public synchronized int getTotalItemsSold() { return totalItemsSold; }
    public synchronized double getTotalSpent() { return totalSpent; }

    public synchronized void addTotalSold(double amount, int itemCount) {
        if (amount > 0 && Double.isFinite(amount)) {
            this.totalSold += amount;
            this.totalItemsSold += Math.max(0, itemCount);
            markDirty();
        }
    }

    public synchronized void addTotalSpent(double amount) {
        if (amount > 0 && Double.isFinite(amount)) {
            this.totalSpent += amount;
            markDirty();
        }
    }

    public synchronized boolean isDirty() { return dirty; }

    /**
     * Mengambil snapshot dan me-reset status dirty secara atomik untuk mencegah data loss.
     */
    public synchronized @Nullable AccountSnapshot snapshotIfDirty() {
        if (!dirty) return null;
        this.dirty = false;
        return new AccountSnapshot(uuid, username, balance, shards, rewardProgressSeconds, totalSold, totalItemsSold, totalSpent);
    }

    /**
     * Snapshot paksa saat server shutdown.
     */
    public synchronized AccountSnapshot snapshot() {
        return new AccountSnapshot(uuid, username, balance, shards, rewardProgressSeconds, totalSold, totalItemsSold, totalSpent);
    }
}

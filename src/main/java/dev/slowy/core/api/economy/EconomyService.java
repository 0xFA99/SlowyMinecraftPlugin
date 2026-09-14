package dev.slowy.core.api.economy;

import dev.slowy.core.economy.Account;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Public high-performance Native Economy & Shards Service for SlowyCore2.
 * Can be retrieved via ServiceRegistry.get(EconomyService.class).
 */
@NullMarked
public interface EconomyService {

    // ── Money / Balance Operations ──
    double getBalance(UUID uuid);

    boolean hasBalance(UUID uuid, double amount);

    boolean deposit(UUID uuid, double amount);

    boolean withdraw(UUID uuid, double amount);

    void setBalance(UUID uuid, double amount);

    boolean transferMoney(UUID from, UUID to, double amount);

    // ── Shards (Non-Transferable) Operations ──
    long getShards(UUID uuid);

    boolean hasShards(UUID uuid, long amount);

    boolean depositShards(UUID uuid, long amount);

    boolean withdrawShards(UUID uuid, long amount);

    void setShards(UUID uuid, long amount);

    // ── Account Retrieval ──
    @Nullable
    Account getAccount(UUID uuid);

    @Nullable
    Account getAccount(String username);

    Account getOrCreateAccount(UUID uuid, @Nullable String username);

    // ── Formatting ──
    String format(double amount);

    String formatNicest(double amount);

    String getCurrencySymbol();
}

package dev.slowy.core.auth;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class AuthManager implements Lifecycle {

    private static final int TIMEOUT_TICKS = 60 * 20;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final SlowyCore plugin;
    private final Logger logger;
    private final AuthDao authDao;

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> timeoutTasks = new ConcurrentHashMap<>();

    public AuthManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.authDao = new AuthDao(databaseManager, logger);

        logger.info("AuthManager initialized (Modern Paper & Java 25).");
    }

    public AuthDao getAuthDao() {
        return authDao;
    }

    public boolean isAuthenticated(Player player) {
        return authenticated.contains(player.getUniqueId());
    }

    public boolean isRegistered(String username) {
        return authDao.isRegistered(username);
    }

    public void onPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        if (authenticated.contains(uuid)) return;

        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 120, 1, false, false));

        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline() && !isAuthenticated(player)) {
                openAuthScreen(player);
            }
        }, null, 5L);

        ScheduledTask timeout = player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline() && !isAuthenticated(player)) {
                player.kick(ColorUtils.parse("<red>Authentication timed out. Please reconnect and log in.</red>"));
            }
        }, null, TIMEOUT_TICKS);

        timeoutTasks.put(uuid, timeout);
    }

    public void openAuthScreen(Player player) {
        openAuthScreen(player, null);
    }

    public void openAuthScreen(Player player, @Nullable Component errorMessage) {
        if (!player.isOnline() || isAuthenticated(player)) return;

        authDao.getUserByUsernameAsync(player.getName()).thenAccept(optUser -> {
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline() || isAuthenticated(player)) return;
                if (optUser.isPresent()) {
                    AuthDialog.openLoginDialog(player, errorMessage);
                } else {
                    AuthDialog.openRegisterDialog(player, errorMessage);
                }
            }, null);
        });
    }

    public void handleRegister(Player player, String password, String confirmPassword) {
        if (isAuthenticated(player)) return;

        if (password.isBlank()) {
            openAuthScreen(player, ColorUtils.parse("<red><bold>✖ Password cannot be empty.</bold></red>"));
            return;
        }
        if (password.length() < 4) {
            openAuthScreen(player, ColorUtils.parse("<red><bold>✖ Password must be at least 4 characters long.</bold></red>"));
            return;
        }
        if (!password.equals(confirmPassword)) {
            openAuthScreen(player, ColorUtils.parse("<red><bold>✖ Passwords do not match. Please try again.</bold></red>"));
            return;
        }

        String username = player.getName();
        authDao.getUserByUsernameAsync(username).thenAccept(optUser -> {
            if (optUser.isPresent()) {
                openAuthScreen(player, ColorUtils.parse("<red><bold>✖ This account is already registered. Please log in.</bold></red>"));
                return;
            }

            AuthSecurity.hashPasswordAsync(password).thenAccept(hash -> {
                String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
                long now = System.currentTimeMillis();
                AuthUser newUser = new AuthUser(player.getUniqueId(), username, hash, ip, now, false, now);

                authDao.saveUserAsync(newUser).thenRun(() -> {
                    player.getScheduler().run(plugin, task -> {
                        authenticate(player);
                        player.sendActionBar(ColorUtils.parse("<#39FF14>Welcome <#FFE600>" + player.getName() + "</#FFE600></#39FF14>"));
                    }, null);
                });
            });
        });
    }

    public void handleLogin(Player player, String password) {
        if (isAuthenticated(player)) return;

        authDao.getUserByUsernameAsync(player.getName()).thenAccept(userOpt -> {
            if (userOpt.isEmpty()) {
                openAuthScreen(player, ColorUtils.parse("<red><bold>✖ Account is not registered yet. Please register.</bold></red>"));
                return;
            }

            AuthUser user = userOpt.get();
            AuthSecurity.checkPasswordAsync(password, user.passwordHash()).thenAccept(matches -> {
                player.getScheduler().run(plugin, task -> {
                    if (!player.isOnline() || isAuthenticated(player)) return;

                    if (!matches) {
                        int attempts = failedAttempts.merge(player.getUniqueId(), 1, Integer::sum);
                        int remaining = MAX_FAILED_ATTEMPTS - attempts;
                        if (attempts >= MAX_FAILED_ATTEMPTS) {
                            player.kick(ColorUtils.parse("<red><bold>Too many failed attempts.</bold>\nPlease reconnect and try again.</red>"));
                            return;
                        }

                        Component errorMsg = ColorUtils.parse("<red><bold>✖ Incorrect password!</bold> <gray>(" + remaining + " attempts left)</gray></red>");
                        player.sendMessage(errorMsg);
                        player.sendActionBar(ColorUtils.parse("<red>✖ Incorrect password.</red>"));
                        openAuthScreen(player, errorMsg);
                        return;
                    }

                    failedAttempts.remove(player.getUniqueId());
                    String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
                    AuthUser updatedUser = user.updateLoginSession(ip, System.currentTimeMillis());
                    authDao.saveUserAsync(updatedUser);

                    authenticate(player);
                    player.sendActionBar(ColorUtils.parse("<#39FF14>Welcome <#FFE600>" + player.getName() + "</#FFE600></#39FF14>"));
                }, null);
            });
        });
    }

    public void authenticate(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.add(uuid);

        ScheduledTask timeout = timeoutTasks.remove(uuid);
        if (timeout != null) timeout.cancel();

        player.setInvulnerable(false);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.closeDialog();

        plugin.getHologramManager().updateForPlayer(player, true);
        plugin.getSkinManager().refreshPlayerSkin(player);
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);
        failedAttempts.remove(uuid);
        player.setInvulnerable(false);

        ScheduledTask timeout = timeoutTasks.remove(uuid);
        if (timeout != null) timeout.cancel();
    }

    public CompletableFuture<Boolean> unregisterPlayer(String username) {
        return authDao.deleteUserAsync(username).thenApply(v -> true);
    }

    public CompletableFuture<Boolean> resetPassword(String username, String newPassword) {
        return AuthSecurity.hashPasswordAsync(newPassword)
                .thenCompose(hash -> authDao.updatePasswordAsync(username, hash))
                .thenApply(v -> true);
    }

    @Override
    public void onDisable() {
        authenticated.clear();
        failedAttempts.clear();
        timeoutTasks.values().forEach(ScheduledTask::cancel);
        timeoutTasks.clear();
        logger.info("AuthManager disabled.");
    }
}

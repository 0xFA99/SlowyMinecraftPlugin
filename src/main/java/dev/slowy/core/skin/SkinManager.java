package dev.slowy.core.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.storage.dao.SkinDao;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Modern Paper PlayerProfile skin engine for SlowyCore2.
 * Stores original skin permanently in database, supports custom MineSkin replacement,
 * enforces a 3-day cooldown on /skin <url>, and allows /skin reset anytime.
 */
@NullMarked
public final class SkinManager implements Lifecycle {

    public enum ChangeResult {
        SUCCESS,
        INVALID_MINESKIN_URL,
        COOLDOWN,
        FETCH_FAILED
    }

    private final SlowyCore plugin;
    private final Logger logger;
    private final SkinDao skinDao;
    private final SkinFetcher skinFetcher;

    private final Map<UUID, PlayerSkinProfile> activeProfiles = new ConcurrentHashMap<>();

    public SkinManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.skinDao = new SkinDao(databaseManager, logger);
        this.skinFetcher = new SkinFetcher(logger);

        logger.info("SkinManager initialized (Persistent player_skins engine with 3-day cooldown ready).");
    }

    public SkinDao getSkinDao() { return skinDao; }
    public SkinFetcher getSkinFetcher() { return skinFetcher; }
    public Logger getLogger() { return logger; }

    /**
     * Gets or loads the player's skin profile synchronously from memory or SQLite.
     * If the player has no record yet, fetches their original skin and persists it.
     */
    public PlayerSkinProfile getOrLoadProfileSync(UUID uuid, String name) {
        PlayerSkinProfile cached = activeProfiles.get(uuid);
        if (cached != null) {
            return cached;
        }

        Optional<PlayerSkinProfile> dbProfile = skinDao.getPlayerProfile(uuid);
        if (dbProfile.isPresent()) {
            PlayerSkinProfile profile = dbProfile.get();
            activeProfiles.put(uuid, profile);
            return profile;
        }

        // First-time player: determine their permanent original skin
        SkinData originalSkin = null;
        try {
            originalSkin = skinFetcher.fetchSkinSync(name);
        } catch (Exception ignored) {}

        if (originalSkin == null || !originalSkin.isValid()) {
            originalSkin = SkinData.STEVE_SKIN;
        }

        PlayerSkinProfile newProfile = new PlayerSkinProfile(
                uuid,
                originalSkin,
                null,
                false,
                0L,
                System.currentTimeMillis()
        );

        skinDao.savePlayerProfileSync(newProfile);
        activeProfiles.put(uuid, newProfile);
        logger.info("Created permanent original skin profile for player '{}' ({})", name, uuid);
        return newProfile;
    }

    public @Nullable PlayerSkinProfile getProfile(UUID uuid) {
        return activeProfiles.get(uuid);
    }

    public @Nullable SkinData getActiveSkin(UUID uuid) {
        PlayerSkinProfile profile = activeProfiles.get(uuid);
        return profile != null ? profile.getActiveSkin() : null;
    }

    public void applySkinToProfile(PlayerProfile profile, SkinData skin) {
        if (!skin.isValid()) return;
        profile.removeProperty("textures");
        profile.setProperty(new ProfileProperty("textures", skin.value(), skin.signature()));
    }

    /**
     * Applies a skin live to an online player entity and updates all tracking viewers.
     */
    public void applySkinLive(Player player, SkinData skin) {
        if (!player.isOnline() || !skin.isValid()) return;

        PlayerProfile profile = player.getPlayerProfile();
        applySkinToProfile(profile, skin);
        player.setPlayerProfile(profile);
        player.sendHealthUpdate();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player) && other.canSee(player)) {
                other.hidePlayer(plugin, player);
                other.showPlayer(plugin, player);
            }
        }

        // Invalidate cached head component in Tablist/Chat
        plugin.getTablistManager().invalidateHeadCache(player.getUniqueId());
        plugin.getTablistManager().updatePlayer(player, Bukkit.getOnlinePlayers().size());
    }

    /**
     * Refreshes the active skin of a player (useful on join / AuthMe login).
     */
    public void refreshPlayerSkin(Player player) {
        if (!player.isOnline()) return;
        PlayerSkinProfile profile = getOrLoadProfileSync(player.getUniqueId(), player.getName());
        SkinData activeSkin = profile.getActiveSkin();
        applySkinLive(player, activeSkin);
    }

    /**
     * Applies a new MineSkin URL for a player.
     * Validates that the input is a MineSkin URL/ID, checks 3-day cooldown,
     * replaces old custom skin in database (keeping original intact), and applies live.
     */
    public void changeSkinToMineSkinAsync(Player player, String mineSkinInput, Consumer<ChangeResult> callback) {
        String mineSkinId = SkinFetcher.extractMineSkinId(mineSkinInput);
        if (mineSkinId == null) {
            callback.accept(ChangeResult.INVALID_MINESKIN_URL);
            return;
        }

        PlayerSkinProfile profile = getOrLoadProfileSync(player.getUniqueId(), player.getName());

        boolean bypass = player.isOp() || player.hasPermission("slowy.skin.admin");
        if (!bypass && profile.isOnCooldown(CoreConfig.SKIN_COOLDOWN_MS)) {
            callback.accept(ChangeResult.COOLDOWN);
            return;
        }

        CompletableFuture.supplyAsync(() -> skinFetcher.fetchFromMineSkin(mineSkinId))
                .thenAccept(fetched -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (fetched == null || !fetched.isValid()) {
                        callback.accept(ChangeResult.FETCH_FAILED);
                        return;
                    }

                    if (!player.isOnline()) return;

                    long now = System.currentTimeMillis();
                    SkinData newCustomSkin = new SkinData(
                            mineSkinId,
                            fetched.value(),
                            fetched.signature(),
                            fetched.textureUrl(),
                            now
                    );

                    PlayerSkinProfile updated = new PlayerSkinProfile(
                            player.getUniqueId(),
                            profile.originalSkin(), // Keep original skin untouched!
                            newCustomSkin,          // Replace old custom skin!
                            true,                   // Set custom active!
                            now,                    // Update 3-day cooldown timer!
                            now
                    );

                    activeProfiles.put(player.getUniqueId(), updated);
                    skinDao.savePlayerProfileAsync(updated);

                    applySkinLive(player, newCustomSkin);
                    callback.accept(ChangeResult.SUCCESS);
                }));
    }

    /**
     * Resets player skin back to their permanent original skin.
     * Allowed anytime (no cooldown).
     */
    public void resetSkin(Player player, Consumer<Boolean> callback) {
        PlayerSkinProfile profile = getOrLoadProfileSync(player.getUniqueId(), player.getName());
        long now = System.currentTimeMillis();

        PlayerSkinProfile resetProfile = new PlayerSkinProfile(
                player.getUniqueId(),
                profile.originalSkin(),
                null,
                false,
                profile.lastChangeTime(),
                now
        );

        activeProfiles.put(player.getUniqueId(), resetProfile);
        skinDao.savePlayerProfileAsync(resetProfile);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                applySkinLive(player, profile.originalSkin());
                callback.accept(true);
            } else {
                callback.accept(false);
            }
        });
    }

    public void cleanupPlayer(UUID uuid) {
        // Keep in activeProfiles cache for seamless fast relogs
    }

    @Override
    public void onDisable() {
        activeProfiles.clear();
        logger.info("SkinManager disabled.");
    }
}

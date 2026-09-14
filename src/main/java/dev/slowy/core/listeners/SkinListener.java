package dev.slowy.core.listeners;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.slowy.core.SlowyCore;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.skin.PlayerSkinProfile;
import dev.slowy.core.skin.SkinData;
import dev.slowy.core.skin.SkinManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.UUID;

@NullMarked
public final class SkinListener implements Listener {

    private final SlowyCore plugin;
    private final SkinManager skinManager;

    public SkinListener(SlowyCore plugin, SkinManager skinManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.skinManager = Objects.requireNonNull(skinManager, "skinManager cannot be null");
        hookAuthMe();
    }

    private void hookAuthMe() {
        try {
            Class<?> loginEventClass = Class.forName("fr.xephi.authme.events.LoginEvent");
            if (Event.class.isAssignableFrom(loginEventClass)) {
                @SuppressWarnings("unchecked")
                Class<? extends Event> eventClass = (Class<? extends Event>) loginEventClass;
                Bukkit.getPluginManager().registerEvent(
                        eventClass,
                        this,
                        EventPriority.MONITOR,
                        (listener, event) -> {
                            try {
                                Player p = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                                if (p != null && p.isOnline()) {
                                    p.getScheduler().runDelayed(plugin, task -> {
                                        if (p.isOnline()) skinManager.refreshPlayerSkin(p);
                                    }, null, 2L);
                                }
                            } catch (Throwable ignored) {}
                        },
                        plugin
                );
                plugin.getSlf4jLogger().info("Hooked SkinListener into AuthMe LoginEvent for guaranteed skin retention.");
            }
        } catch (ClassNotFoundException ignored) {
            // AuthMe not present
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (!CoreConfig.SKIN_ENABLED) return;

        UUID uuid = event.getUniqueId();
        String name = event.getName();

        try {
            PlayerSkinProfile profile = skinManager.getOrLoadProfileSync(uuid, name);
            SkinData activeSkin = profile.getActiveSkin();
            if (activeSkin.isValid()) {
                PlayerProfile playerProfile = event.getPlayerProfile();
                skinManager.applySkinToProfile(playerProfile, activeSkin);
                event.setPlayerProfile(playerProfile);
            }
        } catch (Exception e) {
            skinManager.getLogger().debug("Error loading skin on pre-login for '{}': {}", name, e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!CoreConfig.SKIN_ENABLED) return;
        Player player = event.getPlayer();

        // 1. Immediately refresh skin
        skinManager.refreshPlayerSkin(player);

        // 2. Delayed check in case spawn packets reset the model
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                skinManager.refreshPlayerSkin(player);
            }
        }, null, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        skinManager.cleanupPlayer(event.getPlayer().getUniqueId());
    }
}

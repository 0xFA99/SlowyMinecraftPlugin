package dev.slowy.core.tablist;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.api.economy.EconomyService;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class TablistManager implements Lifecycle {

    private final SlowyCore plugin;
    private final Logger logger;
    private final EconomyService economy;

    private final Map<UUID, String> lastHeaders = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastFooters = new ConcurrentHashMap<>();

    // Cache Component head icon per player, hanya dibangun ulang saat skin berubah
    // (bukan tiap tick). Component.object() cukup mahal karena serialize profile texture.
    private final Map<UUID, Component> headCache = new ConcurrentHashMap<>();

    private static final Component HEAD_PLACEHOLDER = Component.empty();

    public TablistManager(SlowyCore plugin, EconomyService economy) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.economy = Objects.requireNonNull(economy, "economy cannot be null");

        cleanupLegacyTeams();
        logger.info("TablistManager initialized (cached head icons, no teams/roles).");
    }

    public void updateAll() {
        if (!CoreConfig.TABLIST_ENABLED) return;
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        int onlineCount = online.size();

        for (Player player : online) {
            updatePlayer(player, onlineCount);
        }
    }

    public void updatePlayer(Player player, int onlineCount) {
        if (!player.isOnline()) return;

        UUID uuid = player.getUniqueId();

        String rawHeader = String.join("\n", CoreConfig.TABLIST_HEADER)
                .replace("{online}", String.valueOf(onlineCount));

        double balance = economy.getBalance(uuid);
        String money = economy.formatNicest(balance);
        int ping = player.getPing();

        String rawFooter = String.join("\n", CoreConfig.TABLIST_FOOTER)
                .replace("{money}", money)
                .replace("{ping}", String.valueOf(ping));

        String prevHeader = lastHeaders.get(uuid);
        String prevFooter = lastFooters.get(uuid);

        if (!rawHeader.equals(prevHeader) || !rawFooter.equals(prevFooter)) {
            lastHeaders.put(uuid, rawHeader);
            lastFooters.put(uuid, rawFooter);
            player.sendPlayerListHeaderAndFooter(
                    ColorUtils.parse(rawHeader),
                    ColorUtils.parse(rawFooter)
            );
        }

        Component nameComponent = ColorUtils.parse("<white>" + player.getName() + "</white>");
        if (CoreConfig.TABLIST_SHOW_HEAD) {
            Component head = headCache.computeIfAbsent(uuid, id ->
                    Component.object(player.getPlayerProfile()).fallback(HEAD_PLACEHOLDER));
            player.playerListName(Component.empty().append(head).append(Component.space()).append(nameComponent));
        } else {
            player.playerListName(nameComponent);
        }
    }

    /** Panggil ini setiap kali skin player berubah (dari SkinManager#applySkinLive). */
    public void invalidateHeadCache(UUID uuid) {
        headCache.remove(uuid);
    }

    public void onPlayerJoin(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                updatePlayer(player, Bukkit.getOnlinePlayers().size());
            }
        }, 5L);
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        lastHeaders.remove(uuid);
        lastFooters.remove(uuid);
        headCache.remove(uuid);
    }

    public void cleanupLegacyTeams() {
        try {
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Team team : new HashSet<>(main.getTeams())) {
                if (team.getName().startsWith("s_")) {
                    team.unregister();
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            player.playerListName(null);
        }
        cleanupLegacyTeams();
        lastHeaders.clear();
        lastFooters.clear();
        headCache.clear();
        logger.info("TablistManager disabled and cleaned up.");
    }
}

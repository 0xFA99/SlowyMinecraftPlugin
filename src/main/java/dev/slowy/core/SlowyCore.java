package dev.slowy.core;

import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.api.ServiceRegistry;
import dev.slowy.core.api.economy.EconomyService;
import dev.slowy.core.commands.*;
import dev.slowy.core.economy.EconomyManager;
import dev.slowy.core.hologram.HologramManager;
import dev.slowy.core.listeners.EconomyListener;
import dev.slowy.core.listeners.HologramListener;
import dev.slowy.core.listeners.TablistListener;
import dev.slowy.core.skin.SkinManager;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.tablist.TablistManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SlowyCore2 - Modern High-Performance Core Plugin for PaperMC 26.2 on Java 25 LTS.
 * Zero legacy code, zero YAML/JSON configuration parsing, zero commands in plugin.yml.
 * Native SLF4J logging.
 */
public final class SlowyCore extends JavaPlugin {

    private static SlowyCore instance;

    private Logger slf4jLogger;
    private DatabaseManager databaseManager;
    private HologramManager hologramManager;
    private EconomyManager economyManager;
    private TablistManager tablistManager;
    private SkinManager skinManager;
    private dev.slowy.core.auth.AuthManager authManager;
    private dev.slowy.core.npc.NpcManager npcManager;
    private dev.slowy.core.scoreboard.ScoreboardManager scoreboardManager;
    private dev.slowy.core.rtp.RtpManager rtpManager;
    private dev.slowy.core.protection.HubProtectionManager hubProtectionManager;
    private dev.slowy.core.afk.AfkManager afkManager;
    private dev.slowy.core.crate.CrateManager crateManager;
    private dev.slowy.core.crate.CrateHologramManager crateHologramManager;
    private dev.slowy.core.crate.KeyAllManager keyAllManager;
    private dev.slowy.core.worth.WorthManager worthManager;
    private dev.slowy.core.shop.ShopManager shopManager;
    private dev.slowy.core.auction.AuctionManager auctionManager;
    private dev.slowy.core.clearlag.ClearLagManager clearLagManager;
    private dev.slowy.core.role.RoleManager roleManager;
    private dev.slowy.core.stats.BlockMinedManager blockMinedManager;
    private dev.slowy.core.daily.DailyManager dailyManager;
    private dev.slowy.core.guild.GuildManager guildManager;
    private dev.slowy.core.teleport.TeleportManager teleportManager;
    private dev.slowy.core.report.ReportManager reportManager;
    private dev.slowy.core.tick.HeartbeatManager heartbeatManager;
    private CoreCommandManager commandManager;
    private final List<Lifecycle> lifecycles = new ArrayList<>();

    public static SlowyCore getInstance() {
        return Objects.requireNonNull(instance, "SlowyCore2 instance is null! Plugin is not initialized.");
    }

    @Override
    public void onEnable() {
        instance = this;
        this.slf4jLogger = getSLF4JLogger();
        long startTime = System.currentTimeMillis();

        slf4jLogger.info("Initializing SlowyCore2 engine for Paper 26.2 on Java {}...", Runtime.version());

        // 0. Ensure all custom server dimensions are loaded (No Multiverse needed)
        loadServerWorlds();

        // 1. Initialize SQLite Database Engine
        this.databaseManager = new DatabaseManager(this);
        ServiceRegistry.register(DatabaseManager.class, databaseManager);
        lifecycles.add(databaseManager);

        // 1.1. Initialize Native Role & Permission Engine (replaces LuckPerms)
        this.roleManager = new dev.slowy.core.role.RoleManager(this, databaseManager);
        ServiceRegistry.register(dev.slowy.core.role.RoleManager.class, roleManager);
        lifecycles.add(roleManager);

        // 2. Initialize Economy & Shards Engine
        this.economyManager = new EconomyManager(this, databaseManager);
        ServiceRegistry.register(EconomyManager.class, economyManager);
        ServiceRegistry.register(dev.slowy.core.api.economy.EconomyService.class, economyManager);
        lifecycles.add(economyManager);

        // 2.1. Initialize Block Mining Stats Engine
        this.blockMinedManager = new dev.slowy.core.stats.BlockMinedManager(this, databaseManager);
        ServiceRegistry.register(dev.slowy.core.stats.BlockMinedManager.class, blockMinedManager);
        lifecycles.add(blockMinedManager);

        // 3. Initialize Hologram Engine
        this.hologramManager = new HologramManager(this, databaseManager);
        ServiceRegistry.register(HologramManager.class, hologramManager);
        lifecycles.add(hologramManager);

        // 4. Initialize Tablist Engine
        this.tablistManager = new TablistManager(this, economyManager);
        ServiceRegistry.register(TablistManager.class, tablistManager);
        lifecycles.add(tablistManager);

        // 5. Initialize Skin Engine (Paper PlayerProfile & Mojang API)
        this.skinManager = new SkinManager(this, databaseManager);
        ServiceRegistry.register(SkinManager.class, skinManager);
        lifecycles.add(skinManager);

        // 6. Initialize Native Auth Engine
        this.authManager = new dev.slowy.core.auth.AuthManager(this, databaseManager);
        ServiceRegistry.register(dev.slowy.core.auth.AuthManager.class, authManager);
        lifecycles.add(authManager);

        // 7. Initialize Native NPC Engine
        this.npcManager = new dev.slowy.core.npc.NpcManager(this, databaseManager);
        ServiceRegistry.register(dev.slowy.core.npc.NpcManager.class, npcManager);
        lifecycles.add(npcManager);

        // 8. Initialize Native Scoreboard Engine
        this.scoreboardManager = new dev.slowy.core.scoreboard.ScoreboardManager(this, economyManager, databaseManager);
        ServiceRegistry.register(dev.slowy.core.scoreboard.ScoreboardManager.class, scoreboardManager);
        lifecycles.add(scoreboardManager);

        // 9. Initialize Native RTP Engine
        this.rtpManager = new dev.slowy.core.rtp.RtpManager(this);
        ServiceRegistry.register(dev.slowy.core.rtp.RtpManager.class, rtpManager);
        lifecycles.add(rtpManager);

        // 10. Initialize Hub & Spawn Protection Engine
        this.hubProtectionManager = new dev.slowy.core.protection.HubProtectionManager(this);
        ServiceRegistry.register(dev.slowy.core.protection.HubProtectionManager.class, hubProtectionManager);
        lifecycles.add(hubProtectionManager);

        // 11. Initialize AFK Zone & 4th Portal Engine
        this.afkManager = new dev.slowy.core.afk.AfkManager(this, economyManager);
        ServiceRegistry.register(dev.slowy.core.afk.AfkManager.class, afkManager);
        lifecycles.add(afkManager);

        // 12. Initialize Crate & KeyAll Engine
        this.crateManager = new dev.slowy.core.crate.CrateManager(this, databaseManager);
        this.crateHologramManager = new dev.slowy.core.crate.CrateHologramManager(this, crateManager);
        this.keyAllManager = new dev.slowy.core.crate.KeyAllManager(this, crateManager, databaseManager);
        this.crateManager.setHologramManager(crateHologramManager);
        this.crateManager.setKeyAllManager(keyAllManager);
        ServiceRegistry.register(dev.slowy.core.crate.CrateManager.class, crateManager);
        ServiceRegistry.register(dev.slowy.core.crate.CrateHologramManager.class, crateHologramManager);
        ServiceRegistry.register(dev.slowy.core.crate.KeyAllManager.class, keyAllManager);
        lifecycles.add(crateManager);

        // 13. Initialize Worth & Pricing Engine
        this.worthManager = new dev.slowy.core.worth.WorthManager(this, databaseManager);
        ServiceRegistry.register(dev.slowy.core.worth.WorthManager.class, worthManager);
        lifecycles.add(worthManager);

        // 14. Initialize Server Shop Engine
        this.shopManager = new dev.slowy.core.shop.ShopManager(this);
        ServiceRegistry.register(dev.slowy.core.shop.ShopManager.class, shopManager);
        lifecycles.add(shopManager);

        // 15. Initialize Native Auction House Engine
        this.auctionManager = new dev.slowy.core.auction.AuctionManager(this, databaseManager);
        ServiceRegistry.register(dev.slowy.core.auction.AuctionManager.class, auctionManager);
        lifecycles.add(auctionManager);

        // 16. Initialize ClearLag Engine
        this.clearLagManager = new dev.slowy.core.clearlag.ClearLagManager(this);
        ServiceRegistry.register(dev.slowy.core.clearlag.ClearLagManager.class, clearLagManager);
        lifecycles.add(clearLagManager);

        // 17. Initialize Daily Reward Engine
        this.dailyManager = new dev.slowy.core.daily.DailyManager(this, databaseManager);
        ServiceRegistry.register(dev.slowy.core.daily.DailyManager.class, dailyManager);
        lifecycles.add(dailyManager);

        // 17.1. Initialize Native Guild Engine
        this.guildManager = new dev.slowy.core.guild.GuildManager(this, databaseManager, economyManager, roleManager, scoreboardManager);
        ServiceRegistry.register(dev.slowy.core.guild.GuildManager.class, guildManager);
        lifecycles.add(guildManager);

        // 17.2. Initialize Native Teleportation Engine (TPA & TPAHERE)
        this.teleportManager = new dev.slowy.core.teleport.TeleportManager(this);
        ServiceRegistry.register(dev.slowy.core.teleport.TeleportManager.class, teleportManager);
        lifecycles.add(teleportManager);

        // 17.3. Initialize Player Reporting Engine (Hopper GUI & Book Quill)
        this.reportManager = new dev.slowy.core.report.ReportManager(this);
        ServiceRegistry.register(dev.slowy.core.report.ReportManager.class, reportManager);
        lifecycles.add(reportManager);

        // 18. Initialize Unified Master Heartbeat Ticker
        this.heartbeatManager = new dev.slowy.core.tick.HeartbeatManager(
                this,
                npcManager,
                tablistManager,
                scoreboardManager,
                authManager,
                rtpManager,
                hubProtectionManager,
                afkManager,
                crateHologramManager,
                keyAllManager,
                teleportManager
        );
        ServiceRegistry.register(dev.slowy.core.tick.HeartbeatManager.class, heartbeatManager);
        lifecycles.add(heartbeatManager);

        // 18. Register Event Listeners
        getServer().getPluginManager().registerEvents(new HologramListener(this, hologramManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.listeners.EconomyListener(economyManager), this);
        getServer().getPluginManager().registerEvents(new TablistListener(tablistManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.listeners.SkinListener(this, skinManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.listeners.ChatListener(), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.auth.AuthListener(this, authManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.npc.NpcListener(npcManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.listeners.ScoreboardListener(scoreboardManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.listeners.RtpListener(this), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.protection.HubProtectionListener(hubProtectionManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.crate.CrateListener(this, crateManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.worth.WorthListener(this, worthManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.shop.ShopListener(shopManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.auction.AuctionListener(this, auctionManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.role.RoleListener(roleManager), this);
        getServer().getPluginManager().registerEvents(blockMinedManager, this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.guild.GuildListener(this, guildManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.guild.GuildCombatListener(guildManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.teleport.TeleportListener(teleportManager), this);
        getServer().getPluginManager().registerEvents(new dev.slowy.core.report.ReportListener(this, reportManager), this);

        // 19. Initialize Modern Paper Brigadier Command Engine (zero commands in plugin.yml)
        this.commandManager = new CoreCommandManager();
        ServiceRegistry.register(CoreCommandManager.class, commandManager);

        commandManager.register(new DiscordCommand());
        commandManager.register(new HologramCommand(hologramManager));
        commandManager.register(new SlowyCommand(hologramManager, scoreboardManager));
        commandManager.register(new dev.slowy.core.commands.BalanceCommand(economyManager));
        commandManager.register(new dev.slowy.core.commands.PayCommand(economyManager));
        commandManager.register(new dev.slowy.core.commands.ShardsCommand(economyManager));
        commandManager.register(new dev.slowy.core.commands.EconomyCommand(economyManager));
        commandManager.register(new dev.slowy.core.commands.SkinCommand(skinManager));
        commandManager.register(new dev.slowy.core.commands.AuthSubmitCommand(authManager));
        commandManager.register(new dev.slowy.core.commands.RegisterCommand(authManager));
        commandManager.register(new dev.slowy.core.commands.LoginCommand(authManager));
        commandManager.register(new dev.slowy.core.commands.ChangePasswordCommand(authManager));
        commandManager.register(new dev.slowy.core.commands.NpcCommand(npcManager));
        commandManager.register(new dev.slowy.core.commands.ScoreboardCommand(scoreboardManager));
        commandManager.register(new dev.slowy.core.commands.RtpCommand(rtpManager));
        commandManager.register(new dev.slowy.core.commands.SpawnCommand(hubProtectionManager));
        commandManager.register(new dev.slowy.core.commands.AfkCommand(afkManager));
        commandManager.register(new dev.slowy.core.commands.WorldTeleportCommand(hubProtectionManager));
        commandManager.register(new dev.slowy.core.commands.CrateCommand(this, crateManager));
        commandManager.register(new dev.slowy.core.commands.WorthCommand(worthManager));
        commandManager.register(new dev.slowy.core.commands.SellCommand(worthManager));
        commandManager.register(new dev.slowy.core.commands.ShopCommand(shopManager));
        commandManager.register(new dev.slowy.core.commands.AuctionCommand(this, auctionManager));
        commandManager.register(new dev.slowy.core.commands.RoleCommand(roleManager));
        commandManager.register(new dev.slowy.core.commands.SpectatorCommand(roleManager));
        commandManager.register(new dev.slowy.core.commands.DailyCommand(dailyManager));
        commandManager.register(new dev.slowy.core.guild.GuildCommand(this, guildManager, roleManager));
        commandManager.register(new dev.slowy.core.guild.GuildChatCommand(guildManager));
        commandManager.register(new dev.slowy.core.teleport.TpaCommand(teleportManager));
        commandManager.register(new dev.slowy.core.teleport.TpaHereCommand(teleportManager));
        commandManager.register(new dev.slowy.core.teleport.TpAcceptCommand(teleportManager));
        commandManager.register(new dev.slowy.core.teleport.TpDenyCommand(teleportManager));
        commandManager.register(new dev.slowy.core.teleport.TpCancelCommand(teleportManager));
        commandManager.register(new dev.slowy.core.report.ReportCommand(reportManager));

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands registrar = event.registrar();
            this.commandManager.registerAll(registrar);
        });

        long elapsed = System.currentTimeMillis() - startTime;
        slf4jLogger.info("SlowyCore2 enabled in {}ms! (Database WAL active, Holograms, Economy, Tablist, Skins, Screen Auth, Native NPCs & Scoreboard ready)", elapsed);
    }

    @Override
    public void onDisable() {
        if (slf4jLogger != null) {
            slf4jLogger.info("Shutting down SlowyCore2...");
        }

        // Reverse shutdown order
        for (int i = lifecycles.size() - 1; i >= 0; i--) {
            Lifecycle lifecycle = lifecycles.get(i);
            try {
                lifecycle.onDisable();
            } catch (Throwable t) {
                if (slf4jLogger != null) {
                    slf4jLogger.error("Error disabling lifecycle {}: {}", lifecycle.getClass().getSimpleName(), t.getMessage(), t);
                }
            }
        }

        lifecycles.clear();
        ServiceRegistry.clear();
        instance = null;

        if (slf4jLogger != null) {
            slf4jLogger.info("SlowyCore2 disabled cleanly.");
        }
    }

    public Logger getSlf4jLogger() {
        return (slf4jLogger != null) ? slf4jLogger : getSLF4JLogger();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public TablistManager getTablistManager() {
        return tablistManager;
    }

    public SkinManager getSkinManager() {
        return skinManager;
    }

    public dev.slowy.core.auth.AuthManager getAuthManager() {
        return authManager;
    }

    public dev.slowy.core.npc.NpcManager getNpcManager() {
        return npcManager;
    }

    public dev.slowy.core.scoreboard.ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public dev.slowy.core.rtp.RtpManager getRtpManager() {
        return rtpManager;
    }

    public dev.slowy.core.tick.HeartbeatManager getHeartbeatManager() {
        return heartbeatManager;
    }

    public dev.slowy.core.protection.HubProtectionManager getHubProtectionManager() {
        return hubProtectionManager;
    }

    public dev.slowy.core.afk.AfkManager getAfkManager() {
        return afkManager;
    }

    public dev.slowy.core.crate.CrateManager getCrateManager() {
        return crateManager;
    }

    public dev.slowy.core.crate.CrateHologramManager getCrateHologramManager() {
        return crateHologramManager;
    }

    public dev.slowy.core.crate.KeyAllManager getKeyAllManager() {
        return keyAllManager;
    }

    public dev.slowy.core.worth.WorthManager getWorthManager() {
        return worthManager;
    }

    public dev.slowy.core.shop.ShopManager getShopManager() {
        return shopManager;
    }

    public dev.slowy.core.auction.AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public dev.slowy.core.clearlag.ClearLagManager getClearLagManager() {
        return clearLagManager;
    }

    public dev.slowy.core.role.RoleManager getRoleManager() {
        return roleManager;
    }

    public dev.slowy.core.daily.DailyManager getDailyManager() {
        return dailyManager;
    }

    public dev.slowy.core.guild.GuildManager getGuildManager() {
        return guildManager;
    }

    public dev.slowy.core.teleport.TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public dev.slowy.core.report.ReportManager getReportManager() {
        return reportManager;
    }

    public dev.slowy.core.stats.BlockMinedManager getBlockMinedManager() {
        return blockMinedManager;
    }

    public CoreCommandManager getCommandManager() {
        return commandManager;
    }

    private void loadServerWorlds() {
        // Only load custom server dimensions that Paper does not load automatically.
        // Vanilla dimensions (world / minecraft:overworld, world_nether / minecraft:the_nether, world_the_end / minecraft:the_end)
        // are already booted by the server engine prior to plugin initialization.
        loadDimension("spawn", org.bukkit.World.Environment.NORMAL);
        loadDimension("afk_zone", org.bukkit.World.Environment.NORMAL);
    }

    private void loadDimension(String name, org.bukkit.World.Environment env) {
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT));
        if (org.bukkit.Bukkit.getWorld(name) != null || org.bukkit.Bukkit.getWorld(key) != null) {
            return;
        }
        try {
            org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(name).environment(env);
            org.bukkit.World w = org.bukkit.Bukkit.createWorld(creator);
            if (w != null) {
                slf4jLogger.info("Native WorldLoader: Loaded dimension '{}' ({})", name, env);
            }
        } catch (Throwable t) {
            slf4jLogger.warn("Native WorldLoader: Could not load dimension '{}': {}", name, t.getMessage());
        }
    }
}

package dev.slowy.core.guild;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.api.economy.EconomyService;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.role.Role;
import dev.slowy.core.role.RoleManager;
import dev.slowy.core.scoreboard.FastBoard;
import dev.slowy.core.scoreboard.ScoreboardManager;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class GuildManager implements Lifecycle {

    private final SlowyCore plugin;
    private final Logger logger;
    private final DatabaseManager databaseManager;
    private final EconomyService economyService;
    private final RoleManager roleManager;
    private final ScoreboardManager scoreboardManager;
    private final GuildDao dao;

    // Fast in-memory caches
    private final Map<UUID, Guild> guildsById = new ConcurrentHashMap<>();
    private final Map<String, Guild> guildsByName = new ConcurrentHashMap<>(); // lowercase key
    private final Map<UUID, UUID> playerGuildMap = new ConcurrentHashMap<>(); // Player UUID -> Guild UUID

    // Pending Invitations: Target UUID -> Map of Guild UUID to GuildInvite
    private final Map<UUID, Map<UUID, GuildInvite>> pendingInvites = new ConcurrentHashMap<>();

    // Toggled guild chat: Player UUID
    private final Set<UUID> chatToggledPlayers = ConcurrentHashMap.newKeySet();

    public GuildManager(
            SlowyCore plugin,
            DatabaseManager databaseManager,
            EconomyService economyService,
            RoleManager roleManager,
            ScoreboardManager scoreboardManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.roleManager = Objects.requireNonNull(roleManager, "roleManager cannot be null");
        this.scoreboardManager = Objects.requireNonNull(scoreboardManager, "scoreboardManager cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.dao = new GuildDao(databaseManager, logger);

        loadGuilds();
        setupMainScoreboard();
        logger.info("GuildManager initialized with {} loaded guilds.", guildsById.size());
    }

    private void loadGuilds() {
        guildsById.clear();
        guildsByName.clear();
        playerGuildMap.clear();

        Map<UUID, Guild> loaded = dao.loadAllGuilds();
        for (Guild g : loaded.values()) {
            guildsById.put(g.getId(), g);
            guildsByName.put(g.getName().toLowerCase(Locale.ROOT), g);
            for (GuildMember m : g.getMembers()) {
                playerGuildMap.put(m.getUuid(), g.getId());
            }
        }
    }

    public void setupMainScoreboard() {
        try {
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            Objective obj = main.getObjective("guild_below");
            if (obj == null) {
                obj = main.registerNewObjective("guild_below", Criteria.DUMMY, Component.empty());
                obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
            }
        } catch (Throwable t) {
            logger.warn("Could not register guild_below objective on main scoreboard: {}", t.getMessage());
        }
    }

    public GuildDao getDao() {
        return dao;
    }

    public @Nullable Guild getGuild(UUID guildId) {
        return guildsById.get(guildId);
    }

    public @Nullable Guild getGuildByName(String name) {
        if (name == null || name.isBlank()) return null;
        return guildsByName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public @Nullable Guild getGuildByPlayer(UUID playerUuid) {
        UUID guildId = playerGuildMap.get(playerUuid);
        if (guildId == null) return null;
        return guildsById.get(guildId);
    }

    public @Nullable GuildMember getMember(UUID playerUuid) {
        Guild guild = getGuildByPlayer(playerUuid);
        if (guild == null) return null;
        return guild.getMember(playerUuid);
    }

    public boolean isInGuild(UUID playerUuid) {
        return playerGuildMap.containsKey(playerUuid);
    }

    public boolean isSameGuild(UUID playerA, UUID playerB) {
        UUID guildA = playerGuildMap.get(playerA);
        if (guildA == null) return false;
        UUID guildB = playerGuildMap.get(playerB);
        return guildA.equals(guildB);
    }

    // ── Guild Lifecycle Operations ──────────────────────────────────────────

    public boolean createGuild(Player player, String name) {
        UUID uuid = player.getUniqueId();
        if (isInGuild(uuid)) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You are already in a guild!</#FF0055>"));
            return false;
        }

        String trimmed = name.trim();
        if (trimmed.length() < CoreConfig.GUILD_NAME_MIN_LENGTH || trimmed.length() > CoreConfig.GUILD_NAME_MAX_LENGTH) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Guild name must be between " + CoreConfig.GUILD_NAME_MIN_LENGTH + " and " + CoreConfig.GUILD_NAME_MAX_LENGTH + " characters long!</#FF0055>"));
            return false;
        }

        if (!CoreConfig.GUILD_NAME_PATTERN.matcher(trimmed).matches()) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Guild name can only contain alphanumeric characters and underscores [a-zA-Z0-9_]!</#FF0055>"));
            return false;
        }

        if (getGuildByName(trimmed) != null) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ A guild with that name already exists!</#FF0055>"));
            return false;
        }

        long cost = CoreConfig.GUILD_CREATE_COST;
        if (!economyService.hasShards(uuid, cost)) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You need <#FFE600>" + cost + " Shards</#FFE600> to create a guild!</#FF0055>"));
            return false;
        }

        // Deduct shards
        if (!economyService.withdrawShards(uuid, cost)) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Failed to deduct shards. Please try again.</#FF0055>"));
            return false;
        }

        UUID guildId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        String defaultMotd = "Welcome to " + trimmed + "!";
        int initialSlots = CoreConfig.GUILD_DEFAULT_SLOTS;

        Guild guild = new Guild(guildId, trimmed, uuid, defaultMotd, initialSlots, now);
        GuildMember leaderMember = new GuildMember(uuid, guildId, GuildRole.LEADER, now);
        guild.addMember(leaderMember);

        guildsById.put(guildId, guild);
        guildsByName.put(trimmed.toLowerCase(Locale.ROOT), guild);
        playerGuildMap.put(uuid, guildId);

        // Async persistence
        dao.insertGuildAsync(guild);
        dao.insertMemberAsync(leaderMember);
        dao.insertAuditLogAsync("CREATED", guildId, trimmed, uuid, player.getName(), "Guild created with " + initialSlots + " slots (Cost: " + cost + " shards)");

        // Update below-name nametag
        updateNametag(player);

        player.sendMessage(ColorUtils.parse("<#39FF14>✔ Guild <#00F5FF><bold>" + trimmed + "</bold></#00F5FF> has been successfully created!</#39FF14>"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        return true;
    }

    public boolean disbandGuild(Player player, boolean confirmed) {
        UUID uuid = player.getUniqueId();
        Guild guild = getGuildByPlayer(uuid);
        if (guild == null) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember member = guild.getMember(uuid);
        if (member == null || member.getRole() != GuildRole.LEADER) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Only the Guild Leader can disband the guild!</#FF0055>"));
            return false;
        }

        if (!confirmed) {
            player.sendMessage(ColorUtils.parse("<#FF0055>⚠ Are you sure you want to disband <#00F5FF>" + guild.getName() + "</#00F5FF>?</#FF0055>"));
            player.sendMessage(ColorUtils.parse("<gray>Type <#FFE600>/guild disband confirm</#FFE600> to permanently delete your guild.</gray>"));
            return false;
        }

        UUID guildId = guild.getId();
        String guildName = guild.getName();
        int memberCount = guild.getMemberCount();

        // Broadcast disband message to all online guild members & clear their nametags
        for (GuildMember gm : guild.getMembers()) {
            playerGuildMap.remove(gm.getUuid());
            chatToggledPlayers.remove(gm.getUuid());
            Player onlineP = Bukkit.getPlayer(gm.getUuid());
            if (onlineP != null && onlineP.isOnline()) {
                onlineP.sendMessage(ColorUtils.parse("<#FF0055>⚠ Guild <#00F5FF>" + guildName + "</#00F5FF> has been disbanded by the leader.</#FF0055>"));
                onlineP.playSound(onlineP.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.8f, 1.0f);
                updateNametag(onlineP);
            }
        }

        guildsById.remove(guildId);
        guildsByName.remove(guildName.toLowerCase(Locale.ROOT));

        // DB deletion and audit log
        dao.deleteGuildAsync(guildId);
        dao.insertAuditLogAsync("DISBANDED", guildId, guildName, uuid, player.getName(), "Guild disbanded by leader (had " + memberCount + " members)");

        player.sendMessage(ColorUtils.parse("<#39FF14>✔ Guild <#00F5FF>" + guildName + "</#00F5FF> has been disbanded.</#39FF14>"));
        return true;
    }

    public boolean adminDisbandGuild(CommandSender sender, String guildName) {
        Guild guild = getGuildByName(guildName);
        if (guild == null) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Guild '" + guildName + "' not found.</#FF0055>"));
            return false;
        }

        UUID guildId = guild.getId();
        String name = guild.getName();
        int memberCount = guild.getMemberCount();
        String actorName = (sender instanceof Player p) ? p.getName() : "CONSOLE";
        UUID actorUuid = (sender instanceof Player p) ? p.getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000");

        for (GuildMember gm : guild.getMembers()) {
            playerGuildMap.remove(gm.getUuid());
            chatToggledPlayers.remove(gm.getUuid());
            Player onlineP = Bukkit.getPlayer(gm.getUuid());
            if (onlineP != null && onlineP.isOnline()) {
                onlineP.sendMessage(ColorUtils.parse("<#FF0055>⚠ Guild <#00F5FF>" + name + "</#00F5FF> was disbanded by an Administrator.</#FF0055>"));
                updateNametag(onlineP);
            }
        }

        guildsById.remove(guildId);
        guildsByName.remove(name.toLowerCase(Locale.ROOT));

        dao.deleteGuildAsync(guildId);
        dao.insertAuditLogAsync("DISBANDED_BY_ADMIN", guildId, name, actorUuid, actorName, "Force disbanded by server admin (had " + memberCount + " members)");

        sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Guild <#00F5FF>" + name + "</#00F5FF> has been administratively disbanded.</#39FF14>"));
        return true;
    }

    public boolean renameGuild(Player player, String newName) {
        UUID uuid = player.getUniqueId();
        Guild guild = getGuildByPlayer(uuid);
        if (guild == null) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember member = guild.getMember(uuid);
        if (member == null || member.getRole() != GuildRole.LEADER) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Only the Guild Leader can rename the guild!</#FF0055>"));
            return false;
        }

        String trimmed = newName.trim();
        if (trimmed.equalsIgnoreCase(guild.getName())) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ The new name must be different from current name!</#FF0055>"));
            return false;
        }

        if (trimmed.length() < CoreConfig.GUILD_NAME_MIN_LENGTH || trimmed.length() > CoreConfig.GUILD_NAME_MAX_LENGTH) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Guild name must be between " + CoreConfig.GUILD_NAME_MIN_LENGTH + " and " + CoreConfig.GUILD_NAME_MAX_LENGTH + " characters long!</#FF0055>"));
            return false;
        }

        if (!CoreConfig.GUILD_NAME_PATTERN.matcher(trimmed).matches()) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Guild name can only contain alphanumeric characters and underscores [a-zA-Z0-9_]!</#FF0055>"));
            return false;
        }

        if (getGuildByName(trimmed) != null) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ A guild with that name already exists!</#FF0055>"));
            return false;
        }

        long cost = CoreConfig.GUILD_RENAME_COST;
        if (!economyService.hasShards(uuid, cost)) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You need <#FFE600>" + cost + " Shards</#FFE600> to rename your guild!</#FF0055>"));
            return false;
        }

        if (!economyService.withdrawShards(uuid, cost)) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Failed to deduct shards. Please try again.</#FF0055>"));
            return false;
        }

        String oldName = guild.getName();
        guildsByName.remove(oldName.toLowerCase(Locale.ROOT));
        guild.setName(trimmed);
        guildsByName.put(trimmed.toLowerCase(Locale.ROOT), guild);

        dao.updateGuildNameAsync(guild.getId(), trimmed);
        dao.insertAuditLogAsync("RENAMED", guild.getId(), trimmed, uuid, player.getName(), "Renamed from '" + oldName + "' to '" + trimmed + "' (Cost: " + cost + " shards)");

        // Update nametags of all online members
        for (GuildMember gm : guild.getMembers()) {
            Player onlineP = Bukkit.getPlayer(gm.getUuid());
            if (onlineP != null && onlineP.isOnline()) {
                updateNametag(onlineP);
                onlineP.sendMessage(ColorUtils.parse("<#39FF14>✔ Guild name changed to <#00F5FF><bold>" + trimmed + "</bold></#00F5FF>!</#39FF14>"));
            }
        }
        return true;
    }

    public boolean upgradeSlots(Player player) {
        UUID uuid = player.getUniqueId();
        Guild guild = getGuildByPlayer(uuid);
        if (guild == null) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember member = guild.getMember(uuid);
        if (member == null || member.getRole() != GuildRole.LEADER) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Only the Guild Leader can upgrade member slots!</#FF0055>"));
            return false;
        }

        if (guild.getMaxSlots() >= CoreConfig.GUILD_MAX_SLOTS) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Guild has already reached the maximum limit of " + CoreConfig.GUILD_MAX_SLOTS + " slots!</#FF0055>"));
            return false;
        }

        long cost = CoreConfig.GUILD_SLOT_UPGRADE_COST;
        if (!economyService.hasShards(uuid, cost)) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You need <#FFE600>" + cost + " Shards</#FFE600> to upgrade +1 member slot!</#FF0055>"));
            return false;
        }

        if (!economyService.withdrawShards(uuid, cost)) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Failed to deduct shards. Please try again.</#FF0055>"));
            return false;
        }

        int newSlots = guild.getMaxSlots() + 1;
        guild.setMaxSlots(newSlots);
        dao.updateGuildMaxSlotsAsync(guild.getId(), newSlots);

        broadcastToGuild(guild, "<#39FF14>✔ Guild member slots expanded to <#FFE600>" + newSlots + "/" + CoreConfig.GUILD_MAX_SLOTS + "</#FFE600> slots!</#39FF14>");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
        return true;
    }

    // ── Member Invitation & Management ──────────────────────────────────────

    public boolean invitePlayer(Player inviter, Player target) {
        UUID inviterUuid = inviter.getUniqueId();
        Guild guild = getGuildByPlayer(inviterUuid);
        if (guild == null) {
            inviter.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember inviterMember = guild.getMember(inviterUuid);
        if (inviterMember == null || !inviterMember.getRole().canInvite()) {
            inviter.sendMessage(ColorUtils.parse("<#FF0055>✖ Only Leaders and Officers can invite new members!</#FF0055>"));
            return false;
        }

        UUID targetUuid = target.getUniqueId();
        if (targetUuid.equals(inviterUuid)) {
            inviter.sendMessage(ColorUtils.parse("<#FF0055>✖ You cannot invite yourself!</#FF0055>"));
            return false;
        }

        if (isInGuild(targetUuid)) {
            inviter.sendMessage(ColorUtils.parse("<#FF0055>✖ " + target.getName() + " is already in a guild!</#FF0055>"));
            return false;
        }

        if (guild.getMemberCount() >= guild.getMaxSlots()) {
            inviter.sendMessage(ColorUtils.parse("<#FF0055>✖ Guild is full! (" + guild.getMemberCount() + "/" + guild.getMaxSlots() + " slots). Upgrade slots first.</#FF0055>"));
            return false;
        }

        Map<UUID, GuildInvite> targetInvites = pendingInvites.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>());
        GuildInvite existing = targetInvites.get(guild.getId());
        if (existing != null && !existing.isExpired()) {
            inviter.sendMessage(ColorUtils.parse("<#FF7A00>⚠ " + target.getName() + " already has a pending invitation from this guild!</#FF7A00>"));
            return false;
        }

        long expiry = System.currentTimeMillis() + (CoreConfig.GUILD_INVITE_TIMEOUT_SECONDS * 1000L);
        GuildInvite invite = new GuildInvite(guild.getId(), guild.getName(), inviterUuid, inviter.getName(), expiry);
        targetInvites.put(guild.getId(), invite);

        // Send invite confirmation to inviter
        inviter.sendMessage(ColorUtils.parse("<#39FF14>✔ Invited <#00F5FF>" + target.getName() + "</#00F5FF> to the guild! (Expires in 60s)</#39FF14>"));

        // Build interactive message for target
        Component acceptBtn = ColorUtils.parse("<#39FF14><bold>[ACCEPT]</bold></#39FF14>")
                .clickEvent(ClickEvent.runCommand("/guild accept " + guild.getName()))
                .hoverEvent(HoverEvent.showText(ColorUtils.parse("<#39FF14>Click to join <#00F5FF>" + guild.getName() + "</#00F5FF>!</#39FF14>")));

        Component denyBtn = ColorUtils.parse("<#FF0055><bold>[DENY]</bold></#FF0055>")
                .clickEvent(ClickEvent.runCommand("/guild deny " + guild.getName()))
                .hoverEvent(HoverEvent.showText(ColorUtils.parse("<#FF0055>Click to decline invitation.</#FF0055>")));

        target.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
        target.sendMessage(ColorUtils.parse("<#00F5FF><bold>GUILD INVITATION</bold></#00F5FF>"));
        target.sendMessage(ColorUtils.parse("<#E0F8FF>You have been invited to join <#00F5FF><bold>" + guild.getName() + "</bold></#00F5FF> by <#1DA1F2>" + inviter.getName() + "</#1DA1F2>!</#E0F8FF>"));
        target.sendMessage(Component.text("  ").append(acceptBtn).append(Component.text("   ")).append(denyBtn));
        target.sendMessage(ColorUtils.parse("<gray><i>This invitation will expire in 60 seconds.</i></gray>"));
        target.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
        return true;
    }

    public boolean acceptInvite(Player player, @Nullable String guildNameQuery) {
        UUID uuid = player.getUniqueId();
        if (isInGuild(uuid)) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You are already in a guild! Leave your current guild first.</#FF0055>"));
            return false;
        }

        Map<UUID, GuildInvite> invites = pendingInvites.get(uuid);
        if (invites == null || invites.isEmpty()) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You have no pending guild invitations!</#FF0055>"));
            return false;
        }

        GuildInvite targetInvite = null;
        if (guildNameQuery != null && !guildNameQuery.isBlank()) {
            for (GuildInvite inv : invites.values()) {
                if (inv.guildName().equalsIgnoreCase(guildNameQuery.trim())) {
                    targetInvite = inv;
                    break;
                }
            }
        } else {
            // Pick most recent active invite
            for (GuildInvite inv : invites.values()) {
                if (!inv.isExpired()) {
                    targetInvite = inv;
                    break;
                }
            }
        }

        if (targetInvite == null || targetInvite.isExpired()) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ That guild invitation has expired or does not exist!</#FF0055>"));
            return false;
        }

        Guild guild = getGuild(targetInvite.guildId());
        if (guild == null) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ The guild no longer exists!</#FF0055>"));
            invites.remove(targetInvite.guildId());
            return false;
        }

        if (guild.getMemberCount() >= guild.getMaxSlots()) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ That guild is currently full! (" + guild.getMemberCount() + "/" + guild.getMaxSlots() + " slots).</#FF0055>"));
            return false;
        }

        long now = System.currentTimeMillis();
        GuildMember newMember = new GuildMember(uuid, guild.getId(), GuildRole.MEMBER, now);
        guild.addMember(newMember);
        playerGuildMap.put(uuid, guild.getId());
        invites.remove(targetInvite.guildId());

        dao.insertMemberAsync(newMember);
        updateNametag(player);

        player.sendMessage(ColorUtils.parse("<#39FF14>✔ You joined <#00F5FF><bold>" + guild.getName() + "</bold></#00F5FF>!</#39FF14>"));
        player.sendMessage(ColorUtils.parse("<#00F5FF>[Guild MOTD]</#00F5FF> <#E0F8FF>" + guild.getMotd() + "</#E0F8FF>"));
        broadcastToGuild(guild, "<#39FF14>✔ <#00F5FF>" + player.getName() + "</#00F5FF> has joined the guild!</#39FF14>");
        return true;
    }

    public boolean denyInvite(Player player, @Nullable String guildNameQuery) {
        UUID uuid = player.getUniqueId();
        Map<UUID, GuildInvite> invites = pendingInvites.get(uuid);
        if (invites == null || invites.isEmpty()) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You have no pending guild invitations!</#FF0055>"));
            return false;
        }

        GuildInvite toRemove = null;
        if (guildNameQuery != null && !guildNameQuery.isBlank()) {
            for (GuildInvite inv : invites.values()) {
                if (inv.guildName().equalsIgnoreCase(guildNameQuery.trim())) {
                    toRemove = inv;
                    break;
                }
            }
        } else {
            toRemove = invites.values().stream().findFirst().orElse(null);
        }

        if (toRemove != null) {
            invites.remove(toRemove.guildId());
            player.sendMessage(ColorUtils.parse("<#FF7A00>✖ Declined invitation to <#00F5FF>" + toRemove.guildName() + "</#00F5FF>.</#FF7A00>"));
            Player inviter = Bukkit.getPlayer(toRemove.inviterUuid());
            if (inviter != null && inviter.isOnline()) {
                inviter.sendMessage(ColorUtils.parse("<#FF7A00>✖ " + player.getName() + " declined your guild invitation.</#FF7A00>"));
            }
            return true;
        }

        player.sendMessage(ColorUtils.parse("<#FF0055>✖ No matching invitation found.</#FF0055>"));
        return false;
    }

    public boolean kickMember(Player actor, String targetName) {
        UUID actorUuid = actor.getUniqueId();
        Guild guild = getGuildByPlayer(actorUuid);
        if (guild == null) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember actorMember = guild.getMember(actorUuid);
        if (actorMember == null) return false;

        GuildMember targetMember = null;
        UUID targetUuid = null;
        for (GuildMember gm : guild.getMembers()) {
            Player p = Bukkit.getPlayer(gm.getUuid());
            String name = (p != null) ? p.getName() : Bukkit.getOfflinePlayer(gm.getUuid()).getName();
            if (name != null && name.equalsIgnoreCase(targetName.trim())) {
                targetMember = gm;
                targetUuid = gm.getUuid();
                break;
            }
        }

        if (targetMember == null || targetUuid == null) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ Player '" + targetName + "' is not a member of your guild!</#FF0055>"));
            return false;
        }

        if (targetUuid.equals(actorUuid)) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ You cannot kick yourself! Use /guild leave or /guild disband.</#FF0055>"));
            return false;
        }

        if (!actorMember.getRole().canKick(targetMember.getRole())) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ You do not have permission to kick this member!</#FF0055>"));
            return false;
        }

        guild.removeMember(targetUuid);
        playerGuildMap.remove(targetUuid);
        chatToggledPlayers.remove(targetUuid);
        dao.deleteMemberAsync(targetUuid);

        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            updateNametag(onlineTarget);
            onlineTarget.sendMessage(ColorUtils.parse("<#FF0055>✖ You were kicked from guild <#00F5FF>" + guild.getName() + "</#00F5FF> by " + actor.getName() + ".</#FF0055>"));
        }

        broadcastToGuild(guild, "<#FF7A00>✖ " + targetName + " was kicked from the guild by " + actor.getName() + ".</#FF7A00>");
        return true;
    }

    public boolean promoteMember(Player actor, String targetName) {
        UUID actorUuid = actor.getUniqueId();
        Guild guild = getGuildByPlayer(actorUuid);
        if (guild == null) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember actorMember = guild.getMember(actorUuid);
        if (actorMember == null) return false;

        GuildMember targetMember = null;
        UUID targetUuid = null;
        for (GuildMember gm : guild.getMembers()) {
            Player p = Bukkit.getPlayer(gm.getUuid());
            String name = (p != null) ? p.getName() : Bukkit.getOfflinePlayer(gm.getUuid()).getName();
            if (name != null && name.equalsIgnoreCase(targetName.trim())) {
                targetMember = gm;
                targetUuid = gm.getUuid();
                break;
            }
        }

        if (targetMember == null || targetUuid == null) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ Player '" + targetName + "' is not in your guild!</#FF0055>"));
            return false;
        }

        if (targetUuid.equals(actorUuid)) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ You cannot promote yourself!</#FF0055>"));
            return false;
        }

        GuildRole currentRole = targetMember.getRole();
        GuildRole newRole = null;

        if (actorMember.getRole() == GuildRole.LEADER) {
            if (currentRole == GuildRole.DUFFER) {
                newRole = GuildRole.MEMBER;
            } else if (currentRole == GuildRole.MEMBER) {
                newRole = GuildRole.OFFICER;
            } else if (currentRole == GuildRole.OFFICER) {
                actor.sendMessage(ColorUtils.parse("<#FF7A00>⚠ " + targetName + " is already an Officer (highest promotable rank)!</#FF7A00>"));
                return false;
            }
        } else if (actorMember.getRole() == GuildRole.OFFICER) {
            if (currentRole == GuildRole.DUFFER) {
                newRole = GuildRole.MEMBER;
            } else {
                actor.sendMessage(ColorUtils.parse("<#FF0055>✖ Only the Guild Leader can promote members to Officer!</#FF0055>"));
                return false;
            }
        } else {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ Only Leaders and Officers can promote members!</#FF0055>"));
            return false;
        }

        if (newRole != null) {
            targetMember.setRole(newRole);
            dao.updateMemberRoleAsync(targetUuid, newRole);
            broadcastToGuild(guild, "<#39FF14>✔ <#00F5FF>" + targetName + "</#00F5FF> was promoted to " + newRole.getFormattedName() + "<#39FF14> by " + actor.getName() + "!</#39FF14>");
            return true;
        }

        return false;
    }

    public boolean demoteMember(Player actor, String targetName) {
        UUID actorUuid = actor.getUniqueId();
        Guild guild = getGuildByPlayer(actorUuid);
        if (guild == null) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember actorMember = guild.getMember(actorUuid);
        if (actorMember == null) return false;

        GuildMember targetMember = null;
        UUID targetUuid = null;
        for (GuildMember gm : guild.getMembers()) {
            Player p = Bukkit.getPlayer(gm.getUuid());
            String name = (p != null) ? p.getName() : Bukkit.getOfflinePlayer(gm.getUuid()).getName();
            if (name != null && name.equalsIgnoreCase(targetName.trim())) {
                targetMember = gm;
                targetUuid = gm.getUuid();
                break;
            }
        }

        if (targetMember == null || targetUuid == null) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ Player '" + targetName + "' is not in your guild!</#FF0055>"));
            return false;
        }

        if (targetUuid.equals(actorUuid)) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ You cannot demote yourself!</#FF0055>"));
            return false;
        }

        GuildRole currentRole = targetMember.getRole();
        GuildRole newRole = null;

        if (actorMember.getRole() == GuildRole.LEADER) {
            if (currentRole == GuildRole.OFFICER) {
                newRole = GuildRole.MEMBER;
            } else if (currentRole == GuildRole.MEMBER) {
                newRole = GuildRole.DUFFER;
            } else if (currentRole == GuildRole.DUFFER) {
                actor.sendMessage(ColorUtils.parse("<#FF7A00>⚠ " + targetName + " is already at the lowest rank (Duffer)!</#FF7A00>"));
                return false;
            }
        } else if (actorMember.getRole() == GuildRole.OFFICER) {
            if (currentRole == GuildRole.MEMBER) {
                newRole = GuildRole.DUFFER;
            } else {
                actor.sendMessage(ColorUtils.parse("<#FF0055>✖ Officers can only demote Members to Duffer!</#FF0055>"));
                return false;
            }
        } else {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ Only Leaders and Officers can demote members!</#FF0055>"));
            return false;
        }

        if (newRole != null) {
            targetMember.setRole(newRole);
            dao.updateMemberRoleAsync(targetUuid, newRole);
            broadcastToGuild(guild, "<#FF7A00>⚠ <#00F5FF>" + targetName + "</#00F5FF> was demoted to " + newRole.getFormattedName() + "<#FF7A00> by " + actor.getName() + ".</#FF7A00>");
            return true;
        }

        return false;
    }

    public boolean setMotd(Player actor, String newMotd) {
        UUID actorUuid = actor.getUniqueId();
        Guild guild = getGuildByPlayer(actorUuid);
        if (guild == null) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember member = guild.getMember(actorUuid);
        if (member == null || !member.getRole().canUpdateMotd()) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ Only Leaders and Officers can update the MOTD!</#FF0055>"));
            return false;
        }

        String trimmed = newMotd.trim();
        if (trimmed.length() > 100) {
            actor.sendMessage(ColorUtils.parse("<#FF0055>✖ MOTD is too long! Maximum 100 characters.</#FF0055>"));
            return false;
        }

        guild.setMotd(trimmed);
        dao.updateGuildMotdAsync(guild.getId(), trimmed);

        broadcastToGuild(guild, "<#39FF14>✔ Guild MOTD updated to: <#E0F8FF>" + trimmed + "</#E0F8FF></#39FF14>");
        return true;
    }

    public boolean leaveGuild(Player player) {
        UUID uuid = player.getUniqueId();
        Guild guild = getGuildByPlayer(uuid);
        if (guild == null) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember member = guild.getMember(uuid);
        if (member == null) return false;

        if (member.getRole() == GuildRole.LEADER) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ As the Leader, you cannot leave the guild! If you wish to quit, you must disband the guild with <#FFE600>/guild disband</#FFE600>.</#FF0055>"));
            return false;
        }

        guild.removeMember(uuid);
        playerGuildMap.remove(uuid);
        chatToggledPlayers.remove(uuid);
        dao.deleteMemberAsync(uuid);

        updateNametag(player);
        player.sendMessage(ColorUtils.parse("<#FF7A00>✖ You left <#00F5FF>" + guild.getName() + "</#00F5FF>.</#FF7A00>"));
        broadcastToGuild(guild, "<#FF7A00>✖ " + player.getName() + " has left the guild.</#FF7A00>");
        return true;
    }

    // ── Guild Chat ──────────────────────────────────────────────────────────

    public boolean sendGuildChat(Player sender, String message) {
        UUID uuid = sender.getUniqueId();
        Guild guild = getGuildByPlayer(uuid);
        if (guild == null) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember member = guild.getMember(uuid);
        if (member == null) return false;

        if (!member.getRole().canChat()) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Duffers are not permitted to speak in guild chat!</#FF0055>"));
            return false;
        }

        Component chatComp = ColorUtils.parse(
                "<#00F5FF>[GC]</#00F5FF> " + member.getRole().getFormattedName() + " <#1DA1F2>" + sender.getName() + "</#1DA1F2><gray>:</gray> <#E0F8FF>" + message + "</#E0F8FF>"
        );

        for (GuildMember gm : guild.getMembers()) {
            Player p = Bukkit.getPlayer(gm.getUuid());
            if (p != null && p.isOnline()) {
                p.sendMessage(chatComp);
            }
        }
        return true;
    }

    public boolean isChatToggled(UUID playerUuid) {
        return chatToggledPlayers.contains(playerUuid);
    }

    public boolean toggleGuildChat(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isInGuild(uuid)) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
            return false;
        }

        GuildMember m = getMember(uuid);
        if (m != null && !m.getRole().canChat()) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Duffers are not permitted to use guild chat!</#FF0055>"));
            chatToggledPlayers.remove(uuid);
            return false;
        }

        if (chatToggledPlayers.contains(uuid)) {
            chatToggledPlayers.remove(uuid);
            player.sendMessage(ColorUtils.parse("<#FFE600>Guild chat mode: <#FF0055>OFF</#FF0055> <gray>(now talking in global chat)</gray></#FFE600>"));
            return false;
        } else {
            chatToggledPlayers.add(uuid);
            player.sendMessage(ColorUtils.parse("<#FFE600>Guild chat mode: <#39FF14>ON</#39FF14> <gray>(normal chat sends to guild)</gray></#FFE600>"));
            return true;
        }
    }

    // ── Nametag & Scoreboard Synchronization ────────────────────────────────

    /**
     * Updates the below-name scoreboard display for the target player across all viewers.
     * In accordance with requirements: "Untuk nama guild dikasih dibawah setiap username member (tanpa ada role)"
     */
    public void updateNametag(Player target) {
        Guild guild = getGuildByPlayer(target.getUniqueId());
        Component text = (guild != null) ? ColorUtils.parse("<#00F5FF>" + guild.getName() + "</#00F5FF>") : null;

        // 1. Update on main scoreboard
        try {
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            Objective mainObj = main.getObjective("guild_below");
            if (mainObj != null) {
                Score s = mainObj.getScore(target.getName());
                if (text != null) {
                    s.setScore(0);
                    s.numberFormat(NumberFormat.fixed(text));
                } else {
                    s.resetScore();
                }
            }
        } catch (Throwable ignored) {}

        // 2. Update on all active FastBoard scoreboards
        for (FastBoard fb : scoreboardManager.getAllBoards()) {
            fb.setBelowName(target.getName(), text);
        }
    }

    /**
     * Called when a player's FastBoard is created to populate all existing online guild tags onto their board.
     */
    public void syncNametagsToBoard(FastBoard board) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Guild g = getGuildByPlayer(p.getUniqueId());
            if (g != null) {
                board.setBelowName(p.getName(), ColorUtils.parse("<#00F5FF>" + g.getName() + "</#00F5FF>"));
            }
        }
    }

    public void onPlayerJoin(Player player) {
        updateNametag(player);

        Guild guild = getGuildByPlayer(player.getUniqueId());
        if (guild != null) {
            player.sendMessage(ColorUtils.parse("<#00F5FF><bold>[Guild MOTD]</bold></#00F5FF> <#E0F8FF>" + guild.getMotd() + "</#E0F8FF>"));
        }
    }

    public void onPlayerQuit(Player player) {
        chatToggledPlayers.remove(player.getUniqueId());
        pendingInvites.remove(player.getUniqueId());
    }

    @Override
    public void onDisable() {
        chatToggledPlayers.clear();
        pendingInvites.clear();
        guildsById.clear();
        guildsByName.clear();
        playerGuildMap.clear();
        logger.info("GuildManager disabled.");
    }

    public void broadcastToGuild(Guild guild, String message) {
        Component comp = ColorUtils.parse(message);
        for (GuildMember gm : guild.getMembers()) {
            Player p = Bukkit.getPlayer(gm.getUuid());
            if (p != null && p.isOnline()) {
                p.sendMessage(comp);
            }
        }
    }
}

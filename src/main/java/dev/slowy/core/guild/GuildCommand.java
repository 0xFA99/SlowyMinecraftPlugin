package dev.slowy.core.guild;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.commands.SlowyBasicCommand;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.role.RoleManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.text.SimpleDateFormat;
import java.util.*;

@NullMarked
public final class GuildCommand implements SlowyBasicCommand {

    private final SlowyCore plugin;
    private final GuildManager guildManager;
    private final RoleManager roleManager;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public GuildCommand(SlowyCore plugin, GuildManager guildManager, RoleManager roleManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.guildManager = Objects.requireNonNull(guildManager, "guildManager cannot be null");
        this.roleManager = Objects.requireNonNull(roleManager, "roleManager cannot be null");
    }

    @Override
    public String name() {
        return "guild";
    }

    @Override
    public String description() {
        return "Guild management commands.";
    }

    @Override
    public List<String> aliases() {
        return List.of("g", "guilds");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length == 0) {
            if (sender instanceof Player player) {
                Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
                if (g != null) {
                    showGuildInfo(sender, g);
                    return;
                }
            }
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can create guilds.</#FF0055>"));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild create <name></#FFE600> <gray>(Cost: " + CoreConfig.GUILD_CREATE_COST + " shards)</gray>"));
                    return;
                }
                guildManager.createGuild(player, args[1]);
            }

            case "disband" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can disband guilds.</#FF0055>"));
                    return;
                }
                boolean confirmed = args.length > 1 && args[1].equalsIgnoreCase("confirm");
                guildManager.disbandGuild(player, confirmed);
            }

            case "rename" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can rename guilds.</#FF0055>"));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild rename <new_name></#FFE600> <gray>(Cost: " + CoreConfig.GUILD_RENAME_COST + " shards)</gray>"));
                    return;
                }
                guildManager.renameGuild(player, args[1]);
            }

            case "upgrade", "upgradeslots" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can upgrade guild slots.</#FF0055>"));
                    return;
                }
                guildManager.upgradeSlots(player);
            }

            case "invite" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can invite members.</#FF0055>"));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild invite <player></#FFE600>"));
                    return;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(ColorUtils.parse("<#FF0055>✖ Player '" + args[1] + "' is not online.</#FF0055>"));
                    return;
                }
                guildManager.invitePlayer(player, target);
            }

            case "accept" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can accept invites.</#FF0055>"));
                    return;
                }
                String guildQuery = args.length > 1 ? args[1] : null;
                guildManager.acceptInvite(player, guildQuery);
            }

            case "deny", "decline" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can decline invites.</#FF0055>"));
                    return;
                }
                String guildQuery = args.length > 1 ? args[1] : null;
                guildManager.denyInvite(player, guildQuery);
            }

            case "kick" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can kick members.</#FF0055>"));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild kick <player></#FFE600>"));
                    return;
                }
                guildManager.kickMember(player, args[1]);
            }

            case "promote" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can promote members.</#FF0055>"));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild promote <player></#FFE600>"));
                    return;
                }
                guildManager.promoteMember(player, args[1]);
            }

            case "demote" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can demote members.</#FF0055>"));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild demote <player></#FFE600>"));
                    return;
                }
                guildManager.demoteMember(player, args[1]);
            }

            case "motd", "setmotd" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can update MOTD.</#FF0055>"));
                    return;
                }
                if (args.length < 2) {
                    Guild g = guildManager.getGuildByPlayer(player.getUniqueId());
                    if (g != null) {
                        player.sendMessage(ColorUtils.parse("<#00F5FF>[Guild MOTD]</#00F5FF> <#E0F8FF>" + g.getMotd() + "</#E0F8FF>"));
                    } else {
                        player.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild!</#FF0055>"));
                    }
                    return;
                }
                String[] rest = Arrays.copyOfRange(args, 1, args.length);
                String motd = String.join(" ", rest);
                guildManager.setMotd(player, motd);
            }

            case "leave" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can leave guilds.</#FF0055>"));
                    return;
                }
                guildManager.leaveGuild(player);
            }

            case "info" -> {
                Guild targetGuild = null;
                if (args.length > 1) {
                    targetGuild = guildManager.getGuildByName(args[1]);
                    if (targetGuild == null) {
                        sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Guild '" + args[1] + "' not found.</#FF0055>"));
                        return;
                    }
                } else if (sender instanceof Player p) {
                    targetGuild = guildManager.getGuildByPlayer(p.getUniqueId());
                    if (targetGuild == null) {
                        sender.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild! Use <#FFE600>/guild info <name></#FFE600>.</#FF0055>"));
                        return;
                    }
                } else {
                    sender.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild info <guild></#FFE600>"));
                    return;
                }
                showGuildInfo(sender, targetGuild);
            }

            case "members", "list" -> {
                Guild targetGuild = null;
                if (args.length > 1) {
                    targetGuild = guildManager.getGuildByName(args[1]);
                    if (targetGuild == null) {
                        sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Guild '" + args[1] + "' not found.</#FF0055>"));
                        return;
                    }
                } else if (sender instanceof Player p) {
                    targetGuild = guildManager.getGuildByPlayer(p.getUniqueId());
                    if (targetGuild == null) {
                        sender.sendMessage(ColorUtils.parse("<#FF0055>✖ You are not in a guild! Use <#FFE600>/guild members <name></#FFE600>.</#FF0055>"));
                        return;
                    }
                } else {
                    sender.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild members <guild></#FFE600>"));
                    return;
                }
                showGuildMembers(sender, targetGuild);
            }

            case "chat", "c" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can use guild chat.</#FF0055>"));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild chat <message></#FFE600> (or /gc <message>)"));
                    return;
                }
                String[] rest = Arrays.copyOfRange(args, 1, args.length);
                guildManager.sendGuildChat(player, String.join(" ", rest));
            }

            case "togglechat" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can toggle guild chat.</#FF0055>"));
                    return;
                }
                guildManager.toggleGuildChat(player);
            }

            case "admin", "log" -> {
                // Strictly restricted to OWNER or Console
                if (!roleManager.isOwner(sender) && !sender.isOp() && !(sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Access denied! Only the server Owner can view guild audit logs.</#FF0055>"));
                    return;
                }

                if (sub.equals("admin") && args.length > 1 && args[1].equalsIgnoreCase("disband")) {
                    if (args.length < 3) {
                        sender.sendMessage(ColorUtils.parse("<#FFE600>Usage: /guild admin disband <guild></#FFE600>"));
                        return;
                    }
                    guildManager.adminDisbandGuild(sender, args[2]);
                    return;
                }

                int page = 1;
                int pageArgIndex = sub.equals("admin") ? 2 : 1;
                if (args.length > pageArgIndex) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[pageArgIndex]));
                    } catch (NumberFormatException ignored) {}
                }

                showAuditLogs(sender, page);
            }

            default -> sendHelp(sender);
        }
    }

    private void showGuildInfo(CommandSender sender, Guild guild) {
        String leaderName = Bukkit.getOfflinePlayer(guild.getLeaderUuid()).getName();
        if (leaderName == null) leaderName = "Unknown";

        int onlineCount = 0;
        for (GuildMember m : guild.getMembers()) {
            Player p = Bukkit.getPlayer(m.getUuid());
            if (p != null && p.isOnline()) onlineCount++;
        }

        sender.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
        sender.sendMessage(ColorUtils.parse("<#00F5FF><bold>GUILD INFO</bold></#00F5FF> <gray>•</gray> <#E0F8FF><bold>" + guild.getName() + "</bold></#E0F8FF>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>Leader:</#1DA1F2> <#FFE600>" + leaderName + "</#FFE600>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>Members:</#1DA1F2> <#39FF14>" + guild.getMemberCount() + "/" + guild.getMaxSlots() + "</#39FF14> <gray>(" + onlineCount + " online)</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>Created:</#1DA1F2> <#E0F8FF>" + DATE_FORMAT.format(new Date(guild.getCreatedAt())) + "</#E0F8FF>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>MOTD:</#1DA1F2> <#E0F8FF>" + guild.getMotd() + "</#E0F8FF>"));
        sender.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
    }

    private void showGuildMembers(CommandSender sender, Guild guild) {
        List<String> leaders = new ArrayList<>();
        List<String> officers = new ArrayList<>();
        List<String> members = new ArrayList<>();
        List<String> duffers = new ArrayList<>();

        for (GuildMember gm : guild.getMembers()) {
            Player onlineP = Bukkit.getPlayer(gm.getUuid());
            boolean isOnline = onlineP != null && onlineP.isOnline();
            String name = (onlineP != null) ? onlineP.getName() : Bukkit.getOfflinePlayer(gm.getUuid()).getName();
            if (name == null) name = "Unknown";

            String formatted = (isOnline ? "<#39FF14>●</#39FF14> " : "<gray>○</gray> ") + "<#E0F8FF>" + name + "</#E0F8FF>";
            switch (gm.getRole()) {
                case LEADER -> leaders.add(formatted);
                case OFFICER -> officers.add(formatted);
                case MEMBER -> members.add(formatted);
                case DUFFER -> duffers.add(formatted);
            }
        }

        sender.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
        sender.sendMessage(ColorUtils.parse("<#00F5FF><bold>" + guild.getName().toUpperCase(Locale.ROOT) + " MEMBERS</bold></#00F5FF> <gray>(" + guild.getMemberCount() + "/" + guild.getMaxSlots() + ")</gray>"));
        sender.sendMessage(ColorUtils.parse("<#FFE600><bold>Leader:</bold></#FFE600> " + String.join(", ", leaders)));
        sender.sendMessage(ColorUtils.parse("<#00F5FF><bold>Officers:</bold></#00F5FF> " + (officers.isEmpty() ? "<gray>None</gray>" : String.join(", ", officers))));
        sender.sendMessage(ColorUtils.parse("<#39FF14><bold>Members:</bold></#39FF14> " + (members.isEmpty() ? "<gray>None</gray>" : String.join(", ", members))));
        sender.sendMessage(ColorUtils.parse("<gray><bold>Duffers:</bold></gray> " + (duffers.isEmpty() ? "<gray>None</gray>" : String.join(", ", duffers))));
        sender.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
    }

    private void showAuditLogs(CommandSender sender, int page) {
        int limit = 8;
        int total = guildManager.getDao().getAuditLogCount();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / limit));
        if (page > totalPages) page = totalPages;
        int offset = (page - 1) * limit;

        List<GuildAuditLog> logs = guildManager.getDao().getAuditLogs(limit, offset);

        sender.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
        sender.sendMessage(ColorUtils.parse("<#00F5FF><bold>GUILD AUDIT LOGS</bold></#00F5FF> <gray>(Page " + page + "/" + totalPages + " • " + total + " total)</gray>"));

        if (logs.isEmpty()) {
            sender.sendMessage(ColorUtils.parse("<gray><i>No guild audit log entries found.</i></gray>"));
        } else {
            for (GuildAuditLog log : logs) {
                String timeStr = DATE_FORMAT.format(new Date(log.timestamp()));
                String actionBadge = switch (log.action()) {
                    case "CREATED" -> "<#39FF14>[CREATED]</#39FF14>";
                    case "DISBANDED", "DISBANDED_BY_ADMIN" -> "<#FF0055>[DISBANDED]</#FF0055>";
                    case "RENAMED" -> "<#FFE600>[RENAMED]</#FFE600>";
                    case "TRANSFERRED" -> "<#FF00BD>[TRANSFERRED]</#FF00BD>";
                    default -> "<gray>[" + log.action() + "]</gray>";
                };

                sender.sendMessage(ColorUtils.parse("<#E0F8FF>[" + timeStr + "]</#E0F8FF> " + actionBadge + " <#00F5FF>" + log.guildName() + "</#00F5FF> <gray>by</gray> <#1DA1F2>" + log.actorName() + "</#1DA1F2>"));
                sender.sendMessage(ColorUtils.parse("  <gray>» " + log.details() + "</gray>"));
            }
        }

        if (totalPages > 1) {
            sender.sendMessage(ColorUtils.parse("<gray>Type <#FFE600>/guild admin log " + (page < totalPages ? page + 1 : 1) + "</#FFE600> for more entries.</gray>"));
        }
        sender.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
        sender.sendMessage(ColorUtils.parse("<#00F5FF><bold>SLOWY GUILD COMMANDS</bold></#00F5FF>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild create <name></#1DA1F2> <gray>- Create guild (" + CoreConfig.GUILD_CREATE_COST + " shards)</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild info [guild]</#1DA1F2> <gray>- View guild info</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild members [guild]</#1DA1F2> <gray>- View member list</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild accept/deny</#1DA1F2> <gray>- Respond to invitation (60s)</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild leave</#1DA1F2> <gray>- Leave current guild</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/gc <message></#1DA1F2> <gray>- Guild chat (or toggle /gc)</gray>"));

        sender.sendMessage(ColorUtils.parse("<#00F5FF>Officer Commands:</#00F5FF>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild invite <player></#1DA1F2> <gray>- Invite player (expires in 60s)</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild kick <player></#1DA1F2> <gray>- Kick member or duffer</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild promote/demote <player></#1DA1F2> <gray>- Manage member ranks</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild motd <text></#1DA1F2> <gray>- Set guild MOTD</gray>"));

        sender.sendMessage(ColorUtils.parse("<#FFE600>Leader Commands:</#FFE600>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild rename <name></#1DA1F2> <gray>- Rename guild (" + CoreConfig.GUILD_RENAME_COST + " shards)</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild upgrade</#1DA1F2> <gray>- +1 slot (" + CoreConfig.GUILD_SLOT_UPGRADE_COST + " shards, max 26)</gray>"));
        sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild disband</#1DA1F2> <gray>- Permanently delete guild</gray>"));

        if (roleManager.isOwner(sender) || sender.isOp() || sender instanceof ConsoleCommandSender) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>Owner Commands:</#FF0055>"));
            sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild admin log [page]</#1DA1F2> <gray>- View database audit logs</gray>"));
            sender.sendMessage(ColorUtils.parse("<#1DA1F2>/guild admin disband <guild></#1DA1F2> <gray>- Force delete a guild</gray>"));
        }
        sender.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 0 || args.length == 1) {
            List<String> list = new ArrayList<>(List.of(
                    "create", "info", "members", "invite", "accept", "deny",
                    "kick", "promote", "demote", "motd", "leave", "rename",
                    "upgrade", "disband", "chat", "togglechat", "help"
            ));
            if (roleManager.isOwner(sender) || sender.isOp() || sender instanceof ConsoleCommandSender) {
                list.add("admin");
            }
            return list;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("invite")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!guildManager.isInGuild(p.getUniqueId())) {
                        players.add(p.getName());
                    }
                }
                return players;
            }
            if (sub.equals("kick") || sub.equals("promote") || sub.equals("demote")) {
                if (sender instanceof Player p) {
                    Guild g = guildManager.getGuildByPlayer(p.getUniqueId());
                    if (g != null) {
                        List<String> names = new ArrayList<>();
                        for (GuildMember gm : g.getMembers()) {
                            if (!gm.getUuid().equals(p.getUniqueId())) {
                                String n = Bukkit.getOfflinePlayer(gm.getUuid()).getName();
                                if (n != null) names.add(n);
                            }
                        }
                        return names;
                    }
                }
            }
            if (sub.equals("admin")) {
                return List.of("log", "disband");
            }
        }

        return List.of();
    }
}

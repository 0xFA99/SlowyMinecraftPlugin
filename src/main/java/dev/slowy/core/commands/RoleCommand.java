package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.role.Role;
import dev.slowy.core.role.RoleManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.*;

/**
 * Strict role management command: /role set <player> <role>, /role get <player>.
 * Strictly executable by Server Owner and Console only.
 */
@NullMarked
public final class RoleCommand implements SlowyBasicCommand {

    private final RoleManager roleManager;

    public RoleCommand(RoleManager roleManager) {
        this.roleManager = Objects.requireNonNull(roleManager, "roleManager cannot be null");
    }

    @Override
    public String name() {
        return "role";
    }

    @Override
    public String description() {
        return "Manage player server roles (Owner & Console only).";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return CoreConfig.PERM_OWNER;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return roleManager.isOwner(sender);
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (!roleManager.isOwner(sender)) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>Unknown or incomplete command, see below for error</#FF0055>"));
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>Usage: <#FFE600>/role set <player> <owner|staff|member></#FFE600></#FF0055>"));
                    return;
                }

                String targetName = args[1];
                String roleStr = args[2];
                Role targetRole = Role.fromString(roleStr);

                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                if (onlineTarget != null) {
                    roleManager.setRoleAsync(onlineTarget.getUniqueId(), targetRole).thenRun(() -> {
                        sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Successfully set role of <#FFE600>" + onlineTarget.getName()
                                + "</#FFE600> to " + targetRole.getFormattedName() + ".</#39FF14>"));
                        onlineTarget.sendActionBar(ColorUtils.parse("<gray>Your role has been set to "
                                + targetRole.getFormattedName() + "</gray>"));
                    });
                    return;
                }

                // Offline player lookup
                roleManager.findUuidByNameAsync(targetName).thenAccept(foundUuid -> {
                    if (foundUuid == null) {
                        sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Player <#FFE600>" + targetName + "</#FFE600> not found in database.</#FF0055>"));
                        return;
                    }

                    roleManager.setRoleAsync(foundUuid, targetRole).thenRun(() -> {
                        sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Successfully set role of <#FFE600>" + targetName
                                + "</#FFE600> (Offline) to " + targetRole.getFormattedName() + ".</#39FF14>"));
                    });
                });
            }

            case "get" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>Usage: <#FFE600>/role get <player></#FFE600></#FF0055>"));
                    return;
                }

                String targetName = args[1];
                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                if (onlineTarget != null) {
                    Role r = roleManager.getRole(onlineTarget);
                    sender.sendMessage(ColorUtils.parse("<gray>Player <#FFE600>" + onlineTarget.getName()
                            + "</#FFE600> has role " + r.getFormattedName() + ".</gray>"));
                    return;
                }

                roleManager.findUuidByNameAsync(targetName).thenAccept(foundUuid -> {
                    if (foundUuid == null) {
                        sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Player <#FFE600>" + targetName + "</#FFE600> not found in database.</#FF0055>"));
                        return;
                    }

                    Role r = roleManager.getRole(foundUuid);
                    sender.sendMessage(ColorUtils.parse("<gray>Player <#FFE600>" + targetName
                            + "</#FFE600> (Offline) has role " + r.getFormattedName() + ".</gray>"));
                });
            }

            default -> sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.parse("<dark_gray><strikethrough>-----------------------------------------</strikethrough></dark_gray>"));
        sender.sendMessage(ColorUtils.parse("           <#1DA1F2><bold>ROLE MANAGER</bold></#1DA1F2>"));
        sender.sendMessage(ColorUtils.parse("<dark_gray>▪</dark_gray> <gray>Set role: <#FFE600>/role set <player> <owner|staff|member></#FFE600></gray>"));
        sender.sendMessage(ColorUtils.parse("<dark_gray>▪</dark_gray> <gray>Get role: <#FFE600>/role get <player></#FFE600></gray>"));
        sender.sendMessage(ColorUtils.parse("<dark_gray><strikethrough>-----------------------------------------</strikethrough></dark_gray>"));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!roleManager.isOwner(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(List.of("set", "get"), args[0]);
        }

        if (args.length == 2) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return filter(names, args[1]);
        }

        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            return filter(List.of("owner", "staff", "member"), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

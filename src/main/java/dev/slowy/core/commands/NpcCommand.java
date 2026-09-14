package dev.slowy.core.commands;

import dev.slowy.core.npc.NpcDefinition;
import dev.slowy.core.npc.NpcManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.*;
import java.util.stream.Collectors;

@NullMarked
public final class NpcCommand implements SlowyBasicCommand {

    private final NpcManager npcManager;

    public NpcCommand(NpcManager npcManager) {
        this.npcManager = Objects.requireNonNull(npcManager, "npcManager cannot be null");
    }

    @Override
    public String name() {
        return "npc";
    }

    @Override
    public String description() {
        return "Manage server NPCs and command bindings.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        // Restricted to server owner / admins only
        if (!sender.isOp() && !sender.hasPermission("slowy.admin") && !sender.hasPermission("slowy.npc.admin")) {
            sender.sendMessage(ColorUtils.parse("<red>✖ You do not have permission to use this command.</red>"));
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<red>✖ This command can only be run in-game.</red>"));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.parse("<yellow>Usage: /npc create <id></yellow>"));
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT).trim();
                if (npcManager.getNpc(id) != null) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ An NPC with ID '<#E0F8FF>" + id + "</#E0F8FF>' already exists.</#FF0055>"));
                    return;
                }
                npcManager.createNpc(id, player.getLocation());
                sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Created NPC '<#E0F8FF>" + id + "</#E0F8FF>' at your current position.</#39FF14>"));
            }

            case "list" -> {
                Collection<NpcDefinition> all = npcManager.getAllNpcs();
                if (all.isEmpty()) {
                    sender.sendMessage(ColorUtils.parse("<#FFE600>No NPCs created yet.</#FFE600>"));
                    return;
                }
                sender.sendMessage(ColorUtils.parse("<#1DA1F2><bold>NPC List</bold></#1DA1F2> <gray>(" + all.size() + "):</gray>"));
                for (NpcDefinition npc : all) {
                    String cmd = npc.getCommand() != null ? "/" + npc.getCommand() : "<none>";
                    sender.sendMessage(ColorUtils.parse("<gray>• <#E0F8FF><bold>" + npc.getId() + "</bold></#E0F8FF> <gray>[" + npc.getWorldName() + " (" + (int) npc.getX() + ", " + (int) npc.getY() + ", " + (int) npc.getZ() + ")] <#FFE600>Bound: " + cmd + "</#FFE600></gray>"));
                }
            }

            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.parse("<#FFE600>Usage: /npc remove <id></#FFE600>"));
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT).trim();
                if (npcManager.removeNpc(id)) {
                    sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Removed NPC '<#E0F8FF>" + id + "</#E0F8FF>'.</#39FF14>"));
                } else {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ NPC '<#E0F8FF>" + id + "</#E0F8FF>' does not exist.</#FF0055>"));
                }
            }

            case "bind" -> {
                if (args.length < 3) {
                    sender.sendMessage(ColorUtils.parse("<#FFE600>Usage: /npc bind <id> <command></#FFE600>"));
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT).trim();
                NpcDefinition npc = npcManager.getNpc(id);
                if (npc == null) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ NPC '<#E0F8FF>" + id + "</#E0F8FF>' does not exist.</#FF0055>"));
                    return;
                }
                String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                npcManager.bindCommand(id, command);
                sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Bound command '<#E0F8FF>" + command + "</#E0F8FF>' to NPC '<#E0F8FF>" + id + "</#E0F8FF>'.</#39FF14>"));
            }

            case "unbind" -> {
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.parse("<#FFE600>Usage: /npc unbind <id></#FFE600>"));
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT).trim();
                if (npcManager.unbindCommand(id)) {
                    sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Unbound command from NPC '<#E0F8FF>" + id + "</#E0F8FF>'.</#39FF14>"));
                } else {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ NPC '<#E0F8FF>" + id + "</#E0F8FF>' does not exist.</#FF0055>"));
                }
            }

            case "skin" -> {
                if (args.length < 3) {
                    sender.sendMessage(ColorUtils.parse("<#FFE600>Usage: /npc skin <id> <playerName|mineskinUrl></#FFE600>"));
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT).trim();
                NpcDefinition npc = npcManager.getNpc(id);
                if (npc == null) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ NPC '<#E0F8FF>" + id + "</#E0F8FF>' does not exist.</#FF0055>"));
                    return;
                }
                String skinQuery = args[2].trim();
                sender.sendMessage(ColorUtils.parse("<gray>Fetching skin for NPC '<#E0F8FF>" + id + "</#E0F8FF>'...</gray>"));
                npcManager.fetchSkinAsync(npc, skinQuery, success -> {
                    if (success) {
                        sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Skin updated successfully for NPC '<#E0F8FF>" + id + "</#E0F8FF>'!</#39FF14>"));
                    } else {
                        sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Failed to load skin from: " + skinQuery + "</#FF0055>"));
                    }
                });
            }

            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ This command can only be run in-game.</#FF0055>"));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(ColorUtils.parse("<#FFE600>Usage: /npc tp <id></#FFE600>"));
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT).trim();
                NpcDefinition npc = npcManager.getNpc(id);
                if (npc == null || npc.getLocation() == null) {
                    sender.sendMessage(ColorUtils.parse("<#FF0055>✖ NPC '<#E0F8FF>" + id + "</#E0F8FF>' not found or world not loaded.</#FF0055>"));
                    return;
                }
                player.teleportAsync(npc.getLocation());
                player.sendMessage(ColorUtils.parse("<#39FF14>✔ Teleported to NPC '<#E0F8FF>" + id + "</#E0F8FF>'.</#39FF14>"));
            }

            default -> sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtils.parse("<#1DA1F2><bold>Slowy NPC Commands</bold></#1DA1F2>"));
        sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/npc create <id></#FFE600> - Create an NPC at your location</gray>"));
        sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/npc list</#FFE600> - List all created NPCs</gray>"));
        sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/npc remove <id></#FFE600> - Delete an NPC</gray>"));
        sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/npc bind <id> <command></#FFE600> - Bind a click command</gray>"));
        sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/npc unbind <id></#FFE600> - Unbind command from NPC</gray>"));
        sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/npc skin <id> <name|url></#FFE600> - Set NPC skin</gray>"));
        sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/npc tp <id></#FFE600> - Teleport to NPC</gray>"));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!sender.isOp() && !sender.hasPermission("slowy.admin") && !sender.hasPermission("slowy.npc.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = List.of("create", "list", "remove", "bind", "unbind", "skin", "tp");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(prefix)).toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("remove") || sub.equals("bind") || sub.equals("unbind") || sub.equals("skin") || sub.equals("tp")) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return npcManager.getAllNpcs().stream()
                        .map(NpcDefinition::getId)
                        .filter(id -> id.startsWith(prefix))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}

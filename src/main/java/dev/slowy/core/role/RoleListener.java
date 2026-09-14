package dev.slowy.core.role;

import dev.slowy.core.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Enforces role boundaries, silent surveillance, and airtight command packet filtering.
 * Cheating or malicious members cannot see or guess staff commands.
 */
@NullMarked
public final class RoleListener implements Listener {

    private final RoleManager roleManager;

    // Commands completely hidden and blocked for regular MEMBER role
    private static final Set<String> BLOCKED_FOR_MEMBERS = Set.of(
            "role", "sp", "spectator", "clearlag", "economy", "eco", "npc", "hologram", "holo", "slowy",
            "gamemode", "teleport", "tp", "op", "deop", "stop", "reload", "ban", "ban-ip", "pardon", "pardon-ip",
            "kick", "whitelist", "give", "summon", "setblock", "fill", "clone", "execute", "gamerule",
            "defaultgamemode", "difficulty", "locate", "weather", "time", "effect", "enchant", "tag", "team",
            "worldborder", "save-all", "save-on", "save-off", "datapack", "seed", "tellraw", "title", "bossbar",
            "particle", "playsound", "stopsound", "forceload", "schedule", "attribute", "item",
            "loot", "perf", "place", "return", "ride", "spectate", "spreadplayers", "trigger",
            "warden_spawn_tracker", "version", "ver", "about", "plugins", "pl", "spark", "luckperms",
            "lp", "timings", "paper", "spigot", "bukkit"
    );

    // Commands restricted exclusively to OWNER (even STAFF cannot see or execute)
    private static final Set<String> OWNER_ONLY_COMMANDS = Set.of(
            "role", "op", "deop", "slowy", "stop", "whitelist", "save-all", "save-on", "save-off"
    );

    public RoleListener(RoleManager roleManager) {
        this.roleManager = Objects.requireNonNull(roleManager, "roleManager cannot be null");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        roleManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        roleManager.onPlayerQuit(event.getPlayer());
    }

    /**
     * Filters command suggestions sent in client packet so members cannot discover staff tools.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        Role role = roleManager.getRole(player);

        if (role == Role.MEMBER) {
            event.getCommands().removeIf(cmd -> {
                String lower = cmd.toLowerCase(Locale.ROOT);
                // Strip all namespaced commands (e.g. minecraft:gamemode, slowycore2:role)
                if (lower.contains(":")) {
                    return true;
                }
                // Strip staff/admin tools
                if (BLOCKED_FOR_MEMBERS.contains(lower)) {
                    return true;
                }
                // Strip any Bukkit command that requires a permission the player lacks
                Command bukkitCmd = Bukkit.getCommandMap().getCommand(lower);
                if (bukkitCmd != null) {
                    String perm = bukkitCmd.getPermission();
                    if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
                        return true;
                    }
                }
                return false;
            });
        } else if (role == Role.STAFF) {
            // Staff can see spectator and moderation commands, but NOT owner-only commands
            event.getCommands().removeIf(cmd -> {
                String lower = cmd.toLowerCase(Locale.ROOT);
                if (lower.contains(":")) {
                    return true;
                }
                return OWNER_ONLY_COMMANDS.contains(lower);
            });
        }
    }

    /**
     * Security barrier: intercept and block manual command executions from players without permission.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Role role = roleManager.getRole(player);

        // Owners have unrestricted command access
        if (role == Role.OWNER) {
            return;
        }

        String raw = event.getMessage();
        if (!raw.startsWith("/")) return;

        String clean = raw.substring(1).trim();
        if (clean.isEmpty()) return;

        String[] parts = clean.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);

        // Extract bare command name if namespaced (e.g. "minecraft:gamemode" -> "gamemode")
        String bareLabel = label;
        if (label.contains(":")) {
            bareLabel = label.substring(label.indexOf(':') + 1);
        }

        if (role == Role.MEMBER) {
            if (BLOCKED_FOR_MEMBERS.contains(bareLabel) || label.contains(":")) {
                event.setCancelled(true);
                // Standard vanilla unknown command response: gives zero hint of command existence
                player.sendMessage(ColorUtils.parse("<red>Unknown or incomplete command, see below for error</red>"));
                return;
            }
        } else if (role == Role.STAFF) {
            if (OWNER_ONLY_COMMANDS.contains(bareLabel)) {
                event.setCancelled(true);
                player.sendMessage(ColorUtils.parse("<red>✖ Only the server owner can use this command.</red>"));
                return;
            }
        }
    }
}

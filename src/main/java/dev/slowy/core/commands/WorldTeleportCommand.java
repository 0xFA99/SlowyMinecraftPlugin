package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.protection.HubProtectionManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class WorldTeleportCommand implements SlowyBasicCommand {

    private final HubProtectionManager protectionManager;

    public WorldTeleportCommand(HubProtectionManager protectionManager) {
        this.protectionManager = Objects.requireNonNull(protectionManager, "protectionManager cannot be null");
    }

    @Override
    public String name() {
        return "goto";
    }

    @Override
    public String description() {
        return "Staff command to teleport between server worlds.";
    }

    @Override
    public List<String> aliases() {
        return List.of("world", "gotoworld", "mvtp");
    }

    @Override
    public String permission() {
        return CoreConfig.PERM_STAFF;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
            return;
        }

        if (!protectionManager.isStaff(player) && !protectionManager.isOwner(player)) {
            player.sendMessage(ColorUtils.parse(CoreConfig.NO_PERMISSION));
            return;
        }

        if (args.length < 1) {
            player.sendMessage(ColorUtils.parse("<red>Usage: /goto <world></red>"));
            return;
        }

        String worldName = args[0].toLowerCase();
        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().equalsIgnoreCase(worldName)) {
                    targetWorld = w;
                    break;
                }
            }
        }

        if (targetWorld == null) {
            player.sendMessage(ColorUtils.parse("<red>✖ World <yellow>" + args[0] + "</yellow> not found or not loaded!</red>"));
            return;
        }

        World finalTarget = targetWorld;
        player.teleportAsync(targetWorld.getSpawnLocation()).thenAccept(success -> {
            if (success) {
                protectionManager.enforceGamemode(player);
                player.sendMessage(ColorUtils.parse("<green>✔ Teleported to world <yellow>" + finalTarget.getName() + "</yellow>!</green>"));
            }
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(w -> w.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }
}

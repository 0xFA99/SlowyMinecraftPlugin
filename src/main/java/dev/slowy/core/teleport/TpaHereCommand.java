package dev.slowy.core.teleport;

import dev.slowy.core.commands.SlowyBasicCommand;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class TpaHereCommand implements SlowyBasicCommand {

    private final TeleportManager teleportManager;

    public TpaHereCommand(TeleportManager teleportManager) {
        this.teleportManager = Objects.requireNonNull(teleportManager, "teleportManager cannot be null");
    }

    @Override
    public String name() {
        return "tpahere";
    }

    @Override
    public String description() {
        return "Request another player to teleport to you.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
            return;
        }

        if (args.length < 1) {
            player.sendMessage(ColorUtils.parse("<#FFE600>Usage: /tpahere <player></#FFE600>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Player '" + args[0] + "' is not online.</#FF0055>"));
            return;
        }

        teleportManager.sendRequest(player, target, TeleportType.TPAHERE);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 0 || args.length == 1) {
            List<String> list = new ArrayList<>();
            CommandSender sender = stack.getSender();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender)) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        return List.of();
    }
}

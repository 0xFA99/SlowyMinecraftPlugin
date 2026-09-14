package dev.slowy.core.teleport;

import dev.slowy.core.commands.SlowyBasicCommand;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class TpDenyCommand implements SlowyBasicCommand {

    private final TeleportManager teleportManager;

    public TpDenyCommand(TeleportManager teleportManager) {
        this.teleportManager = Objects.requireNonNull(teleportManager, "teleportManager cannot be null");
    }

    @Override
    public String name() {
        return "tpdeny";
    }

    @Override
    public String description() {
        return "Deny a pending teleport request.";
    }

    @Override
    public List<String> aliases() {
        return List.of("tpno");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
            return;
        }

        String senderName = args.length >= 1 ? args[0] : null;
        teleportManager.denyRequest(player, senderName);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 0 || args.length == 1) {
            CommandSender sender = stack.getSender();
            if (sender instanceof Player player) {
                return teleportManager.getPendingSenderNames(player.getUniqueId());
            }
        }
        return List.of();
    }
}

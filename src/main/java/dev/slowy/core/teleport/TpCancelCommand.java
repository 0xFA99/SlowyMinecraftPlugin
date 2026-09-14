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
public final class TpCancelCommand implements SlowyBasicCommand {

    private final TeleportManager teleportManager;

    public TpCancelCommand(TeleportManager teleportManager) {
        this.teleportManager = Objects.requireNonNull(teleportManager, "teleportManager cannot be null");
    }

    @Override
    public String name() {
        return "tpcancel";
    }

    @Override
    public String description() {
        return "Cancel your active outgoing teleport request.";
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

        teleportManager.cancelRequest(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return List.of();
    }
}

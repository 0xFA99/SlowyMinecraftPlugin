package dev.slowy.core.commands;

import dev.slowy.core.afk.AfkManager;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class AfkCommand implements SlowyBasicCommand {

    private final AfkManager afkManager;

    public AfkCommand(AfkManager afkManager) {
        this.afkManager = Objects.requireNonNull(afkManager, "afkManager cannot be null");
    }

    @Override
    public String name() {
        return "afk";
    }

    @Override
    public String description() {
        return "Teleport to the AFK Zone to earn Shards.";
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

        if (afkManager.isInAfkZone(player)) {
            player.sendActionBar(ColorUtils.parse("<yellow>You are already in the AFK Zone!</yellow>"));
            return;
        }

        afkManager.startAfkTeleport(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return Collections.emptyList();
    }
}

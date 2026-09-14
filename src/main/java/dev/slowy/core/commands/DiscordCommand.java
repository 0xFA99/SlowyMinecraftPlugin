package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;

/**
 * Modern Paper Brigadier BasicCommand for /discord without aliases.
 */
@NullMarked
public class DiscordCommand implements SlowyBasicCommand {

    @Override
    public String name() {
        return "discord";
    }

    @Override
    public String description() {
        return "Display server Discord community invite link.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return "slowy.discord";
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!canUse(sender)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.NO_PERMISSION));
            return;
        }
        sender.sendMessage(ColorUtils.parse(CoreConfig.DISCORD_MESSAGE));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return List.of();
    }
}

package dev.slowy.core.guild;

import dev.slowy.core.commands.SlowyBasicCommand;
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
public final class GuildChatCommand implements SlowyBasicCommand {

    private final GuildManager guildManager;

    public GuildChatCommand(GuildManager guildManager) {
        this.guildManager = Objects.requireNonNull(guildManager, "guildManager cannot be null");
    }

    @Override
    public String name() {
        return "gc";
    }

    @Override
    public String description() {
        return "Send message to guild chat or toggle guild chat mode.";
    }

    @Override
    public List<String> aliases() {
        return List.of("guildchat");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only players can use guild chat.</#FF0055>"));
            return;
        }

        if (args.length == 0) {
            guildManager.toggleGuildChat(player);
            return;
        }

        String message = String.join(" ", args);
        guildManager.sendGuildChat(player, message);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return Collections.emptyList();
    }
}

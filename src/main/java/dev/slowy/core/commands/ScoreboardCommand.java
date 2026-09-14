package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.scoreboard.ScoreboardManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@NullMarked
public final class ScoreboardCommand implements SlowyBasicCommand {

    private final ScoreboardManager scoreboardManager;

    public ScoreboardCommand(ScoreboardManager scoreboardManager) {
        this.scoreboardManager = Objects.requireNonNull(scoreboardManager, "scoreboardManager cannot be null");
    }

    @Override
    public String name() {
        return "scoreboard";
    }

    @Override
    public String description() {
        return "Toggle your personal sidebar scoreboard.";
    }

    @Override
    public List<String> aliases() {
        return List.of("sb");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
            return;
        }

        boolean enabled = scoreboardManager.toggle(player);
        if (enabled) {
            player.sendMessage(ColorUtils.parse("<#39FF14>✔ Scoreboard has been enabled.</#39FF14>"));
        } else {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Scoreboard has been disabled.</#FF0055>"));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            return List.of("toggle").stream().filter(s -> s.startsWith(token)).toList();
        }
        return Collections.emptyList();
    }
}

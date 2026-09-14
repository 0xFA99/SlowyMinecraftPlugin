package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.hologram.HologramManager;
import dev.slowy.core.scoreboard.ScoreboardManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Modern Paper Brigadier command for /slowy [hologram|scoreboard].
 */
@NullMarked
public class SlowyCommand implements SlowyBasicCommand {

    private final HologramCommand hologramSubCommand;
    private final @Nullable ScoreboardManager scoreboardManager;

    public SlowyCommand(HologramManager hologramManager, @Nullable ScoreboardManager scoreboardManager) {
        this.hologramSubCommand = new HologramCommand("slowy", hologramManager);
        this.scoreboardManager = scoreboardManager;
    }

    public SlowyCommand(HologramManager hologramManager) {
        this(hologramManager, null);
    }

    @Override
    public String name() {
        return "slowy";
    }

    @Override
    public String description() {
        return "Main command for SlowyCore2.";
    }

    @Override
    public List<String> aliases() {
        return List.of("slowycore");
    }

    @Override
    public String permission() {
        return "slowy.use";
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        handle(stack.getSender(), args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return getTabCompletions(args);
    }

    public void handle(CommandSender sender, String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.NO_PERMISSION));
            return;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.parse("<#1DA1F2><bold>SlowyCore2</bold></#1DA1F2> <#E0F8FF>v2.0.0 running on Paper 26.2</#E0F8FF>"));
            sender.sendMessage(ColorUtils.parse("<gray>Available commands: <#FFE600>/slowy hologram <set|remove> <id></#FFE600>, <#FFE600>/slowy scoreboard</#FFE600></gray>"));
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("hologram") || sub.equals("holo")) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            hologramSubCommand.handle(sender, subArgs);
            return;
        }

        if (sub.equals("scoreboard") || sub.equals("sb")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
                return;
            }
            if (scoreboardManager == null) {
                sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Scoreboard module is not initialized.</#FF0055>"));
                return;
            }
            boolean enabled = scoreboardManager.toggle(player);
            if (enabled) {
                player.sendMessage(ColorUtils.parse("<#39FF14>✔ Scoreboard has been enabled.</#39FF14>"));
            } else {
                player.sendMessage(ColorUtils.parse("<#FF0055>✖ Scoreboard has been disabled.</#FF0055>"));
            }
            return;
        }

        sender.sendMessage(ColorUtils.parse("<#FF0055>Unknown sub-command. Type <#FFE600>/slowy</#FFE600> for help.</#FF0055>"));
    }

    private List<String> getTabCompletions(String[] args) {
        if (args.length <= 1) {
            String token = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            return List.of("hologram", "scoreboard").stream().filter(s -> s.startsWith(token)).toList();
        }
        if (args.length > 1 && (args[0].equalsIgnoreCase("hologram") || args[0].equalsIgnoreCase("holo"))) {
            return hologramSubCommand.getTabCompletions(Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }
}

package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.daily.DailyManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Modern Paper Brigadier /daily command to claim daily rewards.
 * Zero GUI, zero reload arguments, zero aliases.
 */
@NullMarked
public final class DailyCommand implements SlowyBasicCommand {

    private final DailyManager dailyManager;

    public DailyCommand(DailyManager dailyManager) {
        this.dailyManager = Objects.requireNonNull(dailyManager, "dailyManager cannot be null");
    }

    @Override
    public String name() {
        return "daily";
    }

    @Override
    public String description() {
        return "Claim your daily login streak reward.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender instanceof Player;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
            return;
        }

        dailyManager.claimDaily(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return Collections.emptyList();
    }
}

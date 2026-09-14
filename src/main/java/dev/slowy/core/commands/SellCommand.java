package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import dev.slowy.core.worth.WorthManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class SellCommand implements SlowyBasicCommand {

    private final WorthManager worthManager;

    public SellCommand(WorthManager worthManager) {
        this.worthManager = Objects.requireNonNull(worthManager, "worthManager cannot be null");
    }

    @Override
    public String name() {
        return "sell";
    }

    @Override
    public String description() {
        return "Open the Sell GUI to sell items for server currency.";
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

        worthManager.openSellGui(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return Collections.emptyList();
    }
}

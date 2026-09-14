package dev.slowy.core.commands;

import dev.slowy.core.api.economy.EconomyService;
import dev.slowy.core.economy.Account;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure /balance command with zero aliases.
 * Shows own balance or target player's balance via ActionBar.
 */
@NullMarked
public final class BalanceCommand implements SlowyBasicCommand {

    private final EconomyService economy;

    public BalanceCommand(EconomyService economy) {
        this.economy = Objects.requireNonNull(economy, "economy cannot be null");
    }

    @Override
    public String name() {
        return "balance";
    }

    @Override
    public String description() {
        return "Check your balance or another player's balance.";
    }

    @Override
    public List<String> aliases() {
        return List.of(); // Pure 1 command, no aliases
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        String symbol = economy.getCurrencySymbol();

        // /balance (own balance)
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Use <#FFE600>/balance <player></#FFE600> from console.</#FF0055>"));
                return;
            }
            double bal = economy.getBalance(player.getUniqueId());
            player.sendActionBar(ColorUtils.parse("<#39FF14><bold>$</bold></#39FF14> <#E0F8FF>Your Balance:</#E0F8FF> <#39FF14>" + symbol + economy.format(bal) + "</#39FF14>"));
            return;
        }

        // /balance <player>
        String targetName = args[0];
        Account targetAcc = economy.getAccount(targetName);
        if (targetAcc == null) {
            sendFeedback(sender, "<#FF0055>✖ Player '<#FFE600>" + targetName + "</#FFE600>' not found!</#FF0055>");
            return;
        }

        String formatted = symbol + economy.format(targetAcc.getBalance());
        sendFeedback(sender, "<gray>" + targetAcc.getUsername() + "'s Balance: <#39FF14>" + formatted + "</#39FF14></gray>");
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String token = (args.length == 1) ? args[0].toLowerCase(Locale.ROOT) : "";
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(token))
                    .toList();
        }
        return List.of();
    }

    private void sendFeedback(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            player.sendActionBar(ColorUtils.parse(message));
        } else {
            sender.sendMessage(ColorUtils.parse(message));
        }
    }
}

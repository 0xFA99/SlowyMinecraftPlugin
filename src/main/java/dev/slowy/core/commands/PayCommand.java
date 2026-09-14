package dev.slowy.core.commands;

import dev.slowy.core.api.economy.EconomyService;
import dev.slowy.core.economy.Account;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure /pay command for transferring money between players.
 * All feedback and notifications are displayed via ActionBar.
 */
@NullMarked
public final class PayCommand implements SlowyBasicCommand {

    private final EconomyService economy;

    public PayCommand(EconomyService economy) {
        this.economy = Objects.requireNonNull(economy, "economy cannot be null");
    }

    @Override
    public String name() {
        return "pay";
    }

    @Override
    public String description() {
        return "Transfer money to another player.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse("<red>✖ Only players can transfer money."));
            return;
        }

        if (args.length < 2) {
            player.sendActionBar(ColorUtils.parse("<yellow>Usage: /pay <player> <amount></yellow>"));
            return;
        }

        String targetName = args[0];
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendActionBar(ColorUtils.parse("<red>✖ You cannot transfer money to yourself!</red>"));
            return;
        }

        Account targetAcc = economy.getAccount(targetName);
        if (targetAcc == null) {
            player.sendActionBar(ColorUtils.parse("<red>✖ Player '<yellow>" + targetName + "</yellow>' not found!</red>"));
            return;
        }

        java.util.OptionalDouble parsed = dev.slowy.core.utils.AmountParser.parseMoney(args[1]);
        if (parsed.isEmpty() || parsed.getAsDouble() <= 0) {
            player.sendActionBar(ColorUtils.parse("<red>✖ Amount must be a valid positive number (e.g. 100, 10k, 1.5M)!</red>"));
            return;
        }
        double amount = parsed.getAsDouble();

        if (!economy.hasBalance(player.getUniqueId(), amount)) {
            player.sendActionBar(ColorUtils.parse("<red>✖ Insufficient balance for this transfer!</red>"));
            return;
        }

        boolean success = economy.transferMoney(player.getUniqueId(), targetAcc.getUuid(), amount);
        if (success) {
            String symbol = economy.getCurrencySymbol();
            String formatted = symbol + economy.format(amount);

            player.sendActionBar(ColorUtils.parse("<green>✔ Transferred <yellow>" + formatted + "</yellow> to <yellow>" + targetAcc.getUsername() + "</yellow></green>"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

            Player targetOnline = Bukkit.getPlayer(targetAcc.getUuid());
            if (targetOnline != null && targetOnline.isOnline()) {
                targetOnline.sendActionBar(ColorUtils.parse("<green>✔ Received <yellow>" + formatted + "</yellow> from <yellow>" + player.getName() + "</yellow></green>"));
                targetOnline.playSound(targetOnline.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            }
        } else {
            player.sendActionBar(ColorUtils.parse("<red>✖ Transfer failed. Please try again.</red>"));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length <= 1) {
            String token = (args.length == 1) ? args[0].toLowerCase(Locale.ROOT) : "";
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !(sender instanceof Player self) || !p.getUniqueId().equals(self.getUniqueId()))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(token))
                    .toList();
        }
        if (args.length == 2) {
            String token = args[1].toLowerCase(Locale.ROOT);
            return List.of("10", "50", "100", "500", "1000").stream()
                    .filter(s -> s.startsWith(token))
                    .toList();
        }
        return List.of();
    }
}

package dev.slowy.core.commands;

import dev.slowy.core.api.economy.EconomyService;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.economy.Account;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure Owner/Console command for modifying player balance and shards:
 * /economy set <money|shards> [player] <amount>
 * Zero access for staff and regular members.
 */
@NullMarked
public final class EconomyCommand implements SlowyBasicCommand {

    private final EconomyService economy;

    public EconomyCommand(EconomyService economy) {
        this.economy = Objects.requireNonNull(economy, "economy cannot be null");
    }

    @Override
    public String name() {
        return "economy";
    }

    @Override
    public String description() {
        return "Owner economy administration command.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String permission() {
        return CoreConfig.PERM_ECONOMY_OWNER;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender instanceof ConsoleCommandSender
                || sender.hasPermission(CoreConfig.PERM_ECONOMY_OWNER)
                || sender.isOp();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (!canUse(sender)) {
            sendFeedback(sender, CoreConfig.NO_PERMISSION);
            return;
        }

        // Usage: /economy set <money|shards> [player] <amount>
        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) {
            sendFeedback(sender, "<yellow>Usage: /economy set <money|shards> [player] <amount></yellow>");
            return;
        }

        String type = args[1].toLowerCase(Locale.ROOT);
        if (!type.equals("money") && !type.equals("shards")) {
            sendFeedback(sender, "<red>✖ Type must be 'money' or 'shards'!</red>");
            return;
        }

        Account targetAcc;
        String amountStr;
        boolean isSelf;

        if (args.length == 3) {
            // /economy set <money|shards> <amount> (self)
            if (!(sender instanceof Player player)) {
                sendFeedback(sender, "<red>✖ Console must specify a target player: /economy set <money|shards> <player> <amount></red>");
                return;
            }
            targetAcc = economy.getOrCreateAccount(player.getUniqueId(), player.getName());
            amountStr = args[2];
            isSelf = true;
        } else {
            // /economy set <money|shards> <player> <amount>
            String targetName = args[2];
            targetAcc = economy.getAccount(targetName);
            if (targetAcc == null) {
                sendFeedback(sender, "<red>✖ Player '<yellow>" + targetName + "</yellow>' not found!</red>");
                return;
            }
            amountStr = args[3];
            isSelf = (sender instanceof Player p && p.getName().equalsIgnoreCase(targetAcc.getUsername()));
        }

        String targetDisplayName = isSelf ? "your" : targetAcc.getUsername() + "'s";
        String symbol = economy.getCurrencySymbol();

        if (type.equals("money")) {
            java.util.OptionalDouble parsed = dev.slowy.core.utils.AmountParser.parseMoney(amountStr);
            if (parsed.isEmpty()) {
                sendFeedback(sender, "<red>✖ Amount must be a valid number (e.g. 1000, 10k, 1.5M, 2B)!</red>");
                return;
            }
            double amount = parsed.getAsDouble();

            economy.setBalance(targetAcc.getUuid(), amount);
            sendFeedback(sender, "<green>✔ Set " + targetDisplayName + " money to <yellow>" + symbol + economy.format(amount) + "</yellow></green>");

            // Notify online target if different from sender
            if (!isSelf) {
                Player targetOnline = Bukkit.getPlayer(targetAcc.getUuid());
                if (targetOnline != null && targetOnline.isOnline()) {
                    targetOnline.sendActionBar(ColorUtils.parse("<green>✔ Your money was set to <yellow>" + symbol + economy.format(amount) + "</yellow></green>"));
                }
            }
        } else {
            java.util.OptionalLong parsed = dev.slowy.core.utils.AmountParser.parseShards(amountStr);
            if (parsed.isEmpty()) {
                sendFeedback(sender, "<red>✖ Amount must be a valid integer (e.g. 10, 1k, 5M)!</red>");
                return;
            }
            long amount = parsed.getAsLong();

            economy.setShards(targetAcc.getUuid(), amount);
            sendFeedback(sender, "<green>✔ Set " + targetDisplayName + " shards to <yellow>" + amount + " ★</yellow></green>");

            // Notify online target if different from sender
            if (!isSelf) {
                Player targetOnline = Bukkit.getPlayer(targetAcc.getUuid());
                if (targetOnline != null && targetOnline.isOnline()) {
                    targetOnline.sendActionBar(ColorUtils.parse("<green>✔ Your shards were set to <yellow>" + amount + " ★</yellow></green>"));
                }
            }
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!canUse(sender)) {
            return List.of();
        }

        if (args.length <= 1) {
            String token = (args.length == 1) ? args[0].toLowerCase(Locale.ROOT) : "";
            return List.of("set").stream().filter(s -> s.startsWith(token)).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            String token = args[1].toLowerCase(Locale.ROOT);
            return List.of("money", "shards").stream().filter(s -> s.startsWith(token)).toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            String token = args[2].toLowerCase(Locale.ROOT);
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            list.add("100");
            list.add("1000");
            list.add("10000");
            return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(token)).toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            String token = args[3].toLowerCase(Locale.ROOT);
            return List.of("100", "1000", "10000", "50000").stream()
                    .filter(s -> s.startsWith(token))
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

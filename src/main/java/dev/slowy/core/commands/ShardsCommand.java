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
 * Pure /shards command with zero aliases.
 * Shards are non-transferable (soulbound).
 * Displays own shards or other player's shards via ActionBar.
 */
@NullMarked
public final class ShardsCommand implements SlowyBasicCommand {

    private final EconomyService economy;

    public ShardsCommand(EconomyService economy) {
        this.economy = Objects.requireNonNull(economy, "economy cannot be null");
    }

    @Override
    public String name() {
        return "shards";
    }

    @Override
    public String description() {
        return "View your shards or another player's shards.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        // /shards (view own shards)
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Use <#FFE600>/shards <player></#FFE600> from console.</#FF0055>"));
                return;
            }
            long shards = economy.getShards(player.getUniqueId());
            player.sendActionBar(ColorUtils.parse("<#FF00BD>★</#FF00BD> <#E0F8FF>Your Shards:</#E0F8FF> <#FF00BD>" + shards + " ★</#FF00BD>"));
            return;
        }

        // /shards <player>
        String targetName = args[0];
        Account targetAcc = economy.getAccount(targetName);
        if (targetAcc == null) {
            sendFeedback(sender, "<#FF0055>✖ Player '<#FFE600>" + targetName + "</#FFE600>' not found!</#FF0055>");
            return;
        }

        sendFeedback(sender, "<gray>" + targetAcc.getUsername() + "'s Shards: <#FF00BD>" + targetAcc.getShards() + " ★</#FF00BD></gray>");
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

package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import dev.slowy.core.worth.WorthManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.*;

@NullMarked
public final class WorthCommand implements SlowyBasicCommand {

    private final WorthManager worthManager;

    public WorthCommand(WorthManager worthManager) {
        this.worthManager = Objects.requireNonNull(worthManager, "worthManager cannot be null");
    }

    @Override
    public String name() {
        return "worth";
    }

    @Override
    public String description() {
        return "Check item sell values or toggle tooltip worth display.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
                return;
            }
            boolean newState = worthManager.toggleWorthDisplay(player);
            if (newState) {
                player.sendMessage(ColorUtils.parse("<green>✔ Worth display has been <yellow>enabled</yellow> in item tooltips.</green>"));
            } else {
                player.sendMessage(ColorUtils.parse("<red>✖ Worth display has been <yellow>disabled</yellow> in item tooltips.</red>"));
            }
            return;
        }

        if (args.length > 0) {
            String query = args[0];
            Material mat = Material.matchMaterial(query);
            if (mat == null) {
                sender.sendMessage(ColorUtils.parse("<red>✖ Material <yellow>\"" + query + "\"</yellow> not found.</red>"));
                return;
            }

            double unitPrice = worthManager.getPrice(mat);
            if (unitPrice <= 0.0) {
                sender.sendMessage(ColorUtils.parse("<gray>Item <#FFE600>" + WorthManager.formatMaterialName(mat) + "</#FFE600> <#FF0055>cannot be sold.</#FF0055></gray>"));
            } else {
                sender.sendMessage(ColorUtils.parse("<gray>Base worth of <#FFE600>" + WorthManager.formatMaterialName(mat) +
                        "</#FFE600> is <#39FF14>$" + WorthManager.formatAmount(unitPrice) + "</#39FF14> each.</gray>"));
            }
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ <#E0F8FF>You are not holding any item! Use <#FFE600>/worth toggle</#FFE600> or <#FFE600>/worth <item></#FFE600>.</#E0F8FF></#FF0055>"));
            return;
        }

        double unitPrice = worthManager.getPrice(hand);
        if (unitPrice <= 0.0) {
            player.sendMessage(ColorUtils.parse("<gray>Item <#FFE600>" + WorthManager.formatMaterialName(hand.getType()) + "</#FFE600> <#FF0055>cannot be sold.</#FF0055></gray>"));
            return;
        }

        int amount = hand.getAmount();
        double total = unitPrice * amount;
        player.sendMessage(ColorUtils.parse(
                "<gray>Worth of <#E0F8FF>" + amount + "x " + WorthManager.formatMaterialName(hand.getType()) +
                        "</#E0F8FF> is <#39FF14>$" + WorthManager.formatAmount(total) +
                        "</#39FF14> <dark_gray>(<#39FF14>$" + WorthManager.formatAmount(unitPrice) + "</#39FF14> each)</dark_gray></gray>"
        ));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> list = new ArrayList<>();
            if ("toggle".startsWith(prefix)) {
                list.add("toggle");
            }
            for (Material mat : Material.values()) {
                if (mat.isItem()) {
                    String name = mat.name().toLowerCase(Locale.ROOT);
                    if (name.startsWith(prefix)) {
                        list.add(name);
                        if (list.size() >= 30) break;
                    }
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}

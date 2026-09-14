package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.shop.ShopManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.*;

@NullMarked
public final class ShopCommand implements SlowyBasicCommand {

    private final ShopManager shopManager;

    public ShopCommand(ShopManager shopManager) {
        this.shopManager = Objects.requireNonNull(shopManager, "shopManager cannot be null");
    }

    @Override
    public String name() {
        return "shop";
    }

    @Override
    public String description() {
        return "Open the server shop menu or browse specific categories.";
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

        if (args.length > 0) {
            String target = args[0].toUpperCase(Locale.ROOT);
            for (String menuKey : shopManager.getCategoryMenus().keySet()) {
                if (menuKey.equalsIgnoreCase(target) || menuKey.equalsIgnoreCase(target + "-MENU")) {
                    shopManager.openCategoryMenu(player, menuKey);
                    return;
                }
            }
        }

        shopManager.openMainMenu(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (String catKey : shopManager.getCategories().keySet()) {
                suggestions.add(catKey.toLowerCase(Locale.ROOT));
            }
            return suggestions.stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }
}

package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.hologram.HologramInstance;
import dev.slowy.core.hologram.HologramManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Modern Paper Brigadier command for /hologram and /slowy:hologram.
 */
@NullMarked
public class HologramCommand implements SlowyBasicCommand {

    private final String name;
    private final HologramManager hologramManager;

    public HologramCommand(HologramManager hologramManager) {
        this("hologram", hologramManager);
    }

    public HologramCommand(String name, HologramManager hologramManager) {
        this.name = name;
        this.hologramManager = Objects.requireNonNull(hologramManager, "hologramManager cannot be null");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return "Manage server text display holograms.";
    }

    @Override
    public List<String> aliases() {
        return name.equals("hologram") ? List.of("holo", "slowy:hologram") : List.of();
    }

    @Override
    public String permission() {
        return "slowy.admin.holo";
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

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.parse("<red>Usage: <gray>/hologram <set|remove> <hologram_id>"));
            return;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String id = args[1].toLowerCase(Locale.ROOT);

        switch (action) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
                    return;
                }
                hologramManager.createOrSet(id, player.getLocation());
                player.sendMessage(ColorUtils.parse("<green>✔ <white>Hologram <yellow>" + id + "</yellow> set at your current location!"));
            }
            case "remove", "delete", "del" -> {
                boolean removed = hologramManager.remove(id);
                if (removed) {
                    sender.sendMessage(ColorUtils.parse("<green>✔ <white>Hologram <yellow>" + id + "</yellow> removed successfully!"));
                } else {
                    sender.sendMessage(ColorUtils.parse("<red>✖ <white>Hologram <yellow>" + id + "</yellow> was not found in database."));
                }
            }
            default -> sender.sendMessage(ColorUtils.parse("<red>Unknown action. Use <yellow>set</yellow> or <yellow>remove</yellow>."));
        }
    }

    public List<String> getTabCompletions(String[] args) {
        if (args.length <= 1) {
            String token = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            return List.of("set", "remove").stream()
                    .filter(s -> s.startsWith(token))
                    .toList();
        }
        if (args.length == 2) {
            String token = args[1].toLowerCase(Locale.ROOT);
            if (args[0].equalsIgnoreCase("set")) {
                List<String> list = new ArrayList<>(List.of("greeting"));
                for (HologramInstance h : hologramManager.getAll()) {
                    if (!list.contains(h.getId())) list.add(h.getId());
                }
                return list.stream().filter(s -> s.startsWith(token)).toList();
            }
            if (args[0].equalsIgnoreCase("remove")) {
                return hologramManager.getAll().stream()
                        .map(HologramInstance::getId)
                        .filter(s -> s.startsWith(token))
                        .toList();
            }
        }
        return List.of();
    }
}

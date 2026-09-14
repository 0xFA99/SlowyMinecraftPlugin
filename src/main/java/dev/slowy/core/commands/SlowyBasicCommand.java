package dev.slowy.core.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Modern Paper BasicCommand representation for SlowyCore2 commands.
 * Fully native with Brigadier command graph and client packet completions.
 */
public interface SlowyBasicCommand extends BasicCommand {

    String name();

    default String description() {
        return "";
    }

    default List<String> aliases() {
        return Collections.emptyList();
    }

    @Override
    default String permission() {
        return null;
    }

    @Override
    default boolean canUse(CommandSender sender) {
        String perm = permission();
        return perm == null || perm.isEmpty() || sender.hasPermission(perm);
    }
}

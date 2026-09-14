package dev.slowy.core.commands;

import io.papermc.paper.command.brigadier.Commands;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modern Paper Brigadier Command Registrar for SlowyCore2.
 */
public class CoreCommandManager {

    private final Map<String, SlowyBasicCommand> commands = new LinkedHashMap<>();

    public void register(SlowyBasicCommand cmd) {
        commands.put(cmd.name().toLowerCase(), cmd);
    }

    public void registerAll(Commands registrar) {
        for (SlowyBasicCommand cmd : commands.values()) {
            registrar.register(cmd.name(), cmd.description(), cmd.aliases(), cmd);
        }
    }

    public Collection<SlowyBasicCommand> getAllCommands() {
        return commands.values();
    }

    public SlowyBasicCommand getCommand(String name) {
        return commands.get(name.toLowerCase());
    }
}

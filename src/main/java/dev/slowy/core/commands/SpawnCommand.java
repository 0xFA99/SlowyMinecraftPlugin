package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.protection.HubProtectionManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class SpawnCommand implements SlowyBasicCommand {

    private final HubProtectionManager protectionManager;

    public SpawnCommand(HubProtectionManager protectionManager) {
        this.protectionManager = Objects.requireNonNull(protectionManager, "protectionManager cannot be null");
    }

    @Override
    public String name() {
        return "spawn";
    }

    @Override
    public String description() {
        return "Teleport to the server spawn.";
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

        protectionManager.teleportToSpawn(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return Collections.emptyList();
    }
}

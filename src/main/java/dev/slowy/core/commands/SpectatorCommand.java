package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.role.RoleManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Secret silent surveillance command for Staff and Owner: /sp and /spectator.
 * Toggles quietly without cluttering chat messages.
 */
@NullMarked
public final class SpectatorCommand implements SlowyBasicCommand {

    private final RoleManager roleManager;

    public SpectatorCommand(RoleManager roleManager) {
        this.roleManager = Objects.requireNonNull(roleManager, "roleManager cannot be null");
    }

    @Override
    public String name() {
        return "sp";
    }

    @Override
    public String description() {
        return "Toggle silent spectator mode for staff surveillance.";
    }

    @Override
    public List<String> aliases() {
        return List.of("spectator");
    }

    @Override
    public String permission() {
        return CoreConfig.PERM_STAFF;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender instanceof Player player && (roleManager.isStaff(player) || roleManager.isOwner(player));
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
            return;
        }

        if (!roleManager.isStaff(player) && !roleManager.isOwner(player)) {
            player.sendMessage(ColorUtils.parse("<red>Unknown or incomplete command, see below for error</red>"));
            return;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            // Revert back from spectator
            boolean inHub = CoreConfig.isHubWorld(player.getWorld());
            GameMode revertMode = inHub ? GameMode.ADVENTURE : GameMode.SURVIVAL;
            player.setGameMode(revertMode);

            // Silent ActionBar notification to preserve quietness
            player.sendActionBar(ColorUtils.parse("<gray>Spectator Mode: <red><bold>OFF</bold></red></gray>"));
            try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            } catch (Exception ignored) {
            }
        } else {
            // Enter spectator mode
            player.setGameMode(GameMode.SPECTATOR);

            // Silent ActionBar notification
            player.sendActionBar(ColorUtils.parse("<gray>Spectator Mode: <green><bold>ON</bold></green></gray>"));
            try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return Collections.emptyList();
    }
}

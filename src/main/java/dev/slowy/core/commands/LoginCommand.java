package dev.slowy.core.commands;

import dev.slowy.core.auth.AuthManager;
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
public final class LoginCommand implements SlowyBasicCommand {

    private final AuthManager authManager;

    public LoginCommand(AuthManager authManager) {
        this.authManager = Objects.requireNonNull(authManager, "authManager cannot be null");
    }

    @Override
    public String name() {
        return "login";
    }

    @Override
    public String description() {
        return "Log in to your registered account.";
    }

    @Override
    public List<String> aliases() {
        return List.of("l");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse("<red>This command can only be executed by players.</red>"));
            return;
        }

        if (authManager.isAuthenticated(player)) {
            player.sendMessage(ColorUtils.parse("<yellow>You are already logged in.</yellow>"));
            return;
        }

        if (args.length < 1) {
            authManager.openAuthScreen(player);
            player.sendMessage(ColorUtils.parse("<yellow>Usage: /login <password></yellow>"));
            return;
        }

        authManager.handleLogin(player, args[0]);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return Collections.emptyList();
    }
}

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
public final class AuthSubmitCommand implements SlowyBasicCommand {

    private final AuthManager authManager;

    public AuthSubmitCommand(AuthManager authManager) {
        this.authManager = Objects.requireNonNull(authManager, "authManager cannot be null");
    }

    @Override
    public String name() {
        return "auth";
    }

    @Override
    public String description() {
        return "Internal authentication command handler.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse("<red>This command can only be run in-game.</red>"));
            return;
        }

        if (args.length == 0) {
            authManager.openAuthScreen(player);
            return;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("submit_register")) {
            if (args.length < 3 || args[1].isBlank() || args[2].isBlank()) {
                authManager.openAuthScreen(player, ColorUtils.parse("<red><bold>✖ Please fill in both password fields.</bold></red>"));
                return;
            }
            authManager.handleRegister(player, args[1], args[2]);
            return;
        }

        if (sub.equals("submit_login")) {
            if (args.length < 2 || args[1].isBlank()) {
                authManager.openAuthScreen(player, ColorUtils.parse("<red><bold>✖ Password cannot be empty.</bold></red>"));
                return;
            }
            String password = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            authManager.handleLogin(player, password);
            return;
        }

        if (sub.equals("screen")) {
            authManager.openAuthScreen(player);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return Collections.emptyList();
    }
}

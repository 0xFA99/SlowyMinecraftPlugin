package dev.slowy.core.commands;

import dev.slowy.core.auth.AuthDao;
import dev.slowy.core.auth.AuthManager;
import dev.slowy.core.auth.AuthSecurity;
import dev.slowy.core.auth.AuthUser;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Player command to change their account password.
 */
@NullMarked
public final class ChangePasswordCommand implements SlowyBasicCommand {

    private final AuthManager authManager;

    public ChangePasswordCommand(AuthManager authManager) {
        this.authManager = Objects.requireNonNull(authManager, "authManager cannot be null");
    }

    @Override
    public String name() {
        return "changepassword";
    }

    @Override
    public String description() {
        return "Change your account password.";
    }

    @Override
    public List<String> aliases() {
        return List.of("cp", "changepass");
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse("<red>This command can only be executed by in-game players.</red>"));
            return;
        }

        if (!authManager.isAuthenticated(player)) {
            player.sendMessage(ColorUtils.parse("<red>✖ You must log in before changing your password.</red>"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ColorUtils.parse("<yellow>Usage: /changepassword <oldPassword> <newPassword></yellow>"));
            return;
        }

        String oldPass = args[0];
        String newPass = args[1];

        if (newPass.length() < 4) {
            player.sendMessage(ColorUtils.parse("<red><bold>✖ New password must be at least 4 characters long.</bold></red>"));
            return;
        }

        AuthDao dao = authManager.getAuthDao();
        Optional<AuthUser> userOpt = dao.getUserByUsername(player.getName());
        if (userOpt.isEmpty()) {
            player.sendMessage(ColorUtils.parse("<red>✖ Account not found.</red>"));
            return;
        }

        AuthUser user = userOpt.get();
        if (!AuthSecurity.checkPassword(oldPass, user.passwordHash())) {
            player.sendMessage(ColorUtils.parse("<#FF0055><bold>✖ Old password is incorrect!</bold></#FF0055>"));
            return;
        }

        String newHash = AuthSecurity.hashPassword(newPass);
        dao.updatePasswordAsync(player.getName(), newHash).thenRun(() -> {
            player.sendMessage(ColorUtils.parse("<#39FF14>✔ Your password has been changed successfully!</#39FF14>"));
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return Collections.emptyList();
    }
}

package dev.slowy.core.auth;

import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class AuthDialog {

    private AuthDialog() {}

    public static void openRegisterDialog(Player player) {
        openRegisterDialog(player, null);
    }

    public static void openRegisterDialog(Player player, @Nullable Component errorMessage) {
        if (!player.isOnline()) return;

        TextDialogInput passInput = DialogInput.text("password", Component.text("Password"))
                .maxLength(32)
                .build();

        TextDialogInput confirmInput = DialogInput.text("confirm_password", Component.text("Confirm Password"))
                .maxLength(32)
                .build();

        ActionButton registerBtn = ActionButton.builder(Component.text("Register", NamedTextColor.GREEN, TextDecoration.BOLD))
                .width(200)
                .action(DialogAction.commandTemplate("/auth submit_register $(password) $(confirm_password)"))
                .build();

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.plainMessage(ColorUtils.parse("<gray>Welcome to <#E0F8FF>Slowy SMP</#E0F8FF>!</gray>")));

        if (errorMessage != null) {
            bodies.add(DialogBody.plainMessage(errorMessage));
        }

        DialogBase base = DialogBase.builder(ColorUtils.parse("<#1DA1F2><bold>Account Registration</bold></#1DA1F2>"))
                .body(bodies)
                .inputs(List.of(passInput, confirmInput))
                .pause(false)
                .canCloseWithEscape(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(registerBtn))
                .columns(1)
                .build();

        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }

    public static void openLoginDialog(Player player) {
        openLoginDialog(player, null);
    }

    public static void openLoginDialog(Player player, @Nullable Component errorMessage) {
        if (!player.isOnline()) return;

        TextDialogInput passInput = DialogInput.text("password", Component.text("Password"))
                .maxLength(32)
                .build();

        ActionButton loginBtn = ActionButton.builder(Component.text("Login", NamedTextColor.GREEN))
                .width(200)
                .action(DialogAction.commandTemplate("/auth submit_login $(password)"))
                .build();

        List<DialogBody> bodies = new ArrayList<>();
        bodies.add(DialogBody.plainMessage(ColorUtils.parse("<gray>Welcome back, <#E0F8FF>" + player.getName() + "</#E0F8FF>!</gray>")));

        if (errorMessage != null) {
            bodies.add(DialogBody.plainMessage(errorMessage));
        }

        DialogBase base = DialogBase.builder(Component.text("Account Login"))
                .body(bodies)
                .inputs(List.of(passInput))
                .pause(false)
                .canCloseWithEscape(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(loginBtn))
                .columns(1)
                .build();

        player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
    }
}

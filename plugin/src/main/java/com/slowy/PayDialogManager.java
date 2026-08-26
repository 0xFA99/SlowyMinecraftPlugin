package com.slowy;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.models.PlayerData;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;

public class PayDialogManager {

    private final SlowyPlugin plugin;
    private final PayListManager payListManager;

    public PayDialogManager(SlowyPlugin plugin, PayListManager payListManager) {
        this.plugin = plugin;
        this.payListManager = payListManager;
    }

    /**
     * Screen 1: Main Pay List (2 Kolom: Daftar Player + '+ Add Player to List')
     */
    public void openMainPayScreen(Player player) {
        List<String> playerList = payListManager.getPayList(player);
        List<ActionButton> buttons = new ArrayList<>();

        for (String targetName : playerList) {
            ActionButton pBtn = ActionButton.builder(Component.text(targetName, NamedTextColor.WHITE))
                    .width(150)
                    .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay select " + targetName)))
                    .build();
            buttons.add(pBtn);
        }

        ActionButton addBtn = ActionButton.builder(Component.text("+ Add Player to List", NamedTextColor.GRAY))
                .width(150)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay addscreen")))
                .build();
        buttons.add(addBtn);

        ActionButton exitAction = ActionButton.builder(Component.translatable("gui.back"))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay backtomenu")))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Pay"))
                .body(List.of(DialogBody.plainMessage(Component.text("Click a player to pay", NamedTextColor.GRAY))))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(buttons)
                .columns(2)
                .exitAction(exitAction)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    /**
     * Screen 2: Tambah Player ke List
     */
    public void openAddPlayerScreen(Player player) {
        TextDialogInput nameInput = DialogInput.text("target_name", Component.text("Player Name"))
                .maxLength(16)
                .build();

        ActionButton addBtn = ActionButton.builder(Component.text("Add to List", NamedTextColor.GREEN))
                .action(DialogAction.commandTemplate("/custompay doadd $(target_name)"))
                .build();

        ActionButton cancelBtn = ActionButton.builder(Component.text("Batal", NamedTextColor.RED))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay")))
                .build();

        ActionButton exitAction = ActionButton.builder(Component.translatable("gui.back"))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay")))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Add Player"))
                .body(List.of(DialogBody.plainMessage(Component.text("Type a name to add to your pay list", NamedTextColor.GRAY))))
                .inputs(List.of(nameInput))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(addBtn, cancelBtn))
                .columns(2)
                .exitAction(exitAction)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    /**
     * Screen 3: Pilih Jumlah Pembayaran (3x3 grid template)
     */
    public void openSelectAmountScreen(Player player, String targetName) {
        List<ActionButton> buttons = new ArrayList<>();

        double[] amounts = {36, 100, 180, 360, 1000};
        String[] labels = {"$ 36", "$ 100", "$ 180", "$ 360", "$ 1K"};

        for (int i = 0; i < amounts.length; i++) {
            double amt = amounts[i];
            String lbl = labels[i];
            ActionButton btn = ActionButton.builder(Component.text(lbl, NamedTextColor.GREEN, TextDecoration.BOLD))
                    .width(80)
                    .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay confirm " + targetName + " " + (long)amt + " " + lbl.replace(" ", ""))))
                    .build();
            buttons.add(btn);
        }

        ActionButton customBtn = ActionButton.builder(Component.text("Custom", NamedTextColor.GOLD, TextDecoration.BOLD))
                .width(80)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay customscreen " + targetName)))
                .build();
        buttons.add(customBtn);

        ActionButton exitAction = ActionButton.builder(Component.translatable("gui.back"))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay")))
                .build();

        Component bodyMessage = Component.text(targetName, NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Choose an amount to pay", NamedTextColor.GRAY));

        DialogBase base = DialogBase.builder(Component.text("Pay " + targetName))
                .body(List.of(DialogBody.plainMessage(bodyMessage)))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(buttons)
                .columns(3)
                .exitAction(exitAction)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    /**
     * Screen 4: Input Nominal Custom
     */
    public void openCustomAmountScreen(Player player, String targetName) {
        TextDialogInput amountInput = DialogInput.text("amount_val", Component.text("Amount"))
                .maxLength(12)
                .build();

        ActionButton continueBtn = ActionButton.builder(Component.text("Continue", NamedTextColor.GREEN))
                .action(DialogAction.commandTemplate("/custompay docustom " + targetName + " ::: $(amount_val)"))
                .build();

        ActionButton cancelBtn = ActionButton.builder(Component.text("Batal", NamedTextColor.RED))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay select " + targetName)))
                .build();

        ActionButton exitAction = ActionButton.builder(Component.translatable("gui.back"))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay select " + targetName)))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Pay " + targetName))
                .body(List.of(DialogBody.plainMessage(Component.text("Type the amount to pay " + targetName, NamedTextColor.GRAY))))
                .inputs(List.of(amountInput))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(continueBtn, cancelBtn))
                .columns(2)
                .exitAction(exitAction)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    /**
     * Screen 5: Konfirmasi Transfer
     */
    public void openConfirmScreen(Player player, String targetName, double amount, String displayAmount) {
        ActionButton noBtn = ActionButton.builder(Component.text("No", NamedTextColor.RED, TextDecoration.BOLD))
                .width(100)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay select " + targetName)))
                .build();

        ActionButton yesBtn = ActionButton.builder(Component.text("Yes", NamedTextColor.GREEN, TextDecoration.BOLD))
                .width(100)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay dopay " + targetName + " " + amount)))
                .build();

        ActionButton exitAction = ActionButton.builder(Component.translatable("gui.back"))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/custompay select " + targetName)))
                .build();

        Component bodyMessage = Component.text(targetName, NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text(displayAmount, NamedTextColor.GREEN, TextDecoration.BOLD));

        DialogBase base = DialogBase.builder(Component.text("Are you sure you want to pay " + targetName + "?"))
                .body(List.of(DialogBody.plainMessage(bodyMessage)))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(noBtn, yesBtn))
                .columns(2)
                .exitAction(exitAction)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    /**
     * Eksekusi transfer uang
     */
    public void handleExecutePay(Player sender, String targetName, double amount) {
        sender.closeDialog();

        if (targetName.equalsIgnoreCase(sender.getName())) {
            sender.sendMessage(Component.text("Kamu tidak dapat mengirim uang ke dirimu sendiri!", NamedTextColor.RED));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(Component.text("Jumlah uang harus lebih dari 0!", NamedTextColor.RED));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(Component.text("Pemain '" + targetName + "' tidak ditemukan!", NamedTextColor.RED));
            return;
        }

        boolean success = false;

        // 1. Vault Economy
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null && rsp.getProvider() != null) {
            Economy econ = rsp.getProvider();
            if (econ.has(sender, amount)) {
                econ.withdrawPlayer(sender, amount);
                econ.depositPlayer(target, amount);
                success = true;
            } else {
                sender.sendMessage(Component.text("Saldo uangmu tidak cukup! Butuh: " + formatDisplayAmount(amount), NamedTextColor.RED));
                return;
            }
        }

        // 2. UltimateDonutSmp
        if (!success && Bukkit.getPluginManager().isPluginEnabled("UltimateDonutSmp")) {
            try {
                UltimateDonutSmp donut = UltimateDonutSmp.getInstance();
                if (donut != null && donut.getPlayerDataManager() != null) {
                    PlayerData sData = donut.getPlayerDataManager().get(sender);
                    if (sData != null) {
                        if (sData.getMoney() >= amount) {
                            PlayerData tData = donut.getPlayerDataManager().get(target.getUniqueId());
                            if (tData != null) {
                                sData.removeMoney(amount);
                                tData.addMoney(amount);
                                success = true;
                            }
                        } else {
                            sender.sendMessage(Component.text("Saldo uangmu tidak cukup! Butuh: " + formatDisplayAmount(amount), NamedTextColor.RED));
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Fallback ke /pay
        if (!success) {
            try {
                sender.performCommand("pay " + targetName + " " + (long) amount);
                return;
            } catch (Throwable ignored) {}
        }

        if (success) {
            sender.sendMessage(Component.text("Berhasil mengirim ", NamedTextColor.GREEN)
                    .append(Component.text(formatDisplayAmount(amount), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" kepada ", NamedTextColor.GREEN))
                    .append(Component.text(targetName, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GREEN)));

            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage(Component.text("Kamu menerima ", NamedTextColor.GREEN)
                        .append(Component.text(formatDisplayAmount(amount), NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text(" dari ", NamedTextColor.GREEN))
                        .append(Component.text(sender.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text("!", NamedTextColor.GREEN)));
            }
        } else {
            sender.sendMessage(Component.text("Gagal mengirim uang kepada " + targetName + ".", NamedTextColor.RED));
        }
    }

    public void openGreetingDialog(Player player) {
        try {
            Dialog greetingDialog = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.DIALOG)
                    .get(TypedKey.create(RegistryKey.DIALOG, Key.key("slowy", "greeting_dialog")));
            if (greetingDialog != null) {
                player.showDialog(greetingDialog);
                return;
            }
        } catch (Throwable ignored) {}
        player.performCommand("showdialog slowy:greeting_dialog");
    }

    public static double parseAmount(String input) {
        if (input == null) return -1;
        String clean = input.trim().replace("$", "").replace(",", "").toLowerCase();
        double multiplier = 1.0;
        if (clean.endsWith("k")) {
            multiplier = 1_000.0;
            clean = clean.substring(0, clean.length() - 1).trim();
        } else if (clean.endsWith("m")) {
            multiplier = 1_000_000.0;
            clean = clean.substring(0, clean.length() - 1).trim();
        } else if (clean.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            clean = clean.substring(0, clean.length() - 1).trim();
        }
        try {
            double val = Double.parseDouble(clean);
            return val * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String formatDisplayAmount(double amount) {
        if (amount >= 1_000_000_000) {
            return String.format("$ %.1fB", amount / 1_000_000_000.0).replace(".0B", "B");
        } else if (amount >= 1_000_000) {
            return String.format("$ %.1fM", amount / 1_000_000.0).replace(".0M", "M");
        } else if (amount >= 1_000) {
            return String.format("$ %.1fK", amount / 1_000.0).replace(".0K", "K");
        } else {
            return String.format("$ %.0f", amount);
        }
    }
}

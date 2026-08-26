package com.slowy;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.models.Home;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SlowyPlugin extends JavaPlugin implements CommandExecutor, Listener {

    public static final int MAX_HOME_NAME_LENGTH = 12;
    public static final int MAX_HOME_SLOTS = 3;
    public static final int BUTTON_WIDTH_12_CHARS = 80;

    private static final ItemStack BED_ITEM = new ItemStack(Material.WHITE_BED);

    private ScoreboardSettingsManager scoreboardSettingsManager;
    private CustomScoreboardManager customScoreboardManager;
    private PayListManager payListManager;
    private PayDialogManager payDialogManager;

    @Override
    public void onEnable() {
        this.scoreboardSettingsManager = new ScoreboardSettingsManager(this);
        this.customScoreboardManager = new CustomScoreboardManager(this);
        this.customScoreboardManager.start();

        this.payListManager = new PayListManager(this);
        this.payDialogManager = new PayDialogManager(this, payListManager);

        if (getCommand("customhomes") != null) {
            getCommand("customhomes").setExecutor(this);
        }
        if (getCommand("customsettings") != null) {
            getCommand("customsettings").setExecutor(this);
        }
        if (getCommand("custompay") != null) {
            getCommand("custompay").setExecutor(this);
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Slowy Plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (customScoreboardManager != null) {
            customScoreboardManager.stop();
        }
        if (scoreboardSettingsManager != null) {
            scoreboardSettingsManager.saveAll();
        }
        if (payListManager != null) {
            payListManager.saveAllSync();
        }
    }

    public CustomScoreboardManager getCustomScoreboardManager() {
        return customScoreboardManager;
    }

    public ScoreboardSettingsManager getScoreboardSettingsManager() {
        return scoreboardSettingsManager;
    }

    public PayListManager getPayListManager() {
        return payListManager;
    }

    public PayDialogManager getPayDialogManager() {
        return payDialogManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (customScoreboardManager != null) {
            getServer().getScheduler().runTaskLater(this, () -> {
                if (event.getPlayer().isOnline()) {
                    customScoreboardManager.updatePlayer(event.getPlayer());
                }
            }, 10L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (customScoreboardManager != null) {
            customScoreboardManager.removeScoreboard(event.getPlayer());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Hanya pemain yang dapat menggunakan perintah ini.", NamedTextColor.RED));
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("custompay")) {
            handlePayCommand(player, args);
            return true;
        }

        if (cmdName.equals("customsettings") || label.equalsIgnoreCase("sbsettings") || label.equalsIgnoreCase("slowysettings")) {
            handleSettingsCommand(player, args);
            return true;
        }

        // customhomes handling
        if (args.length == 0) {
            openHomesDialog(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "new" -> openCreateHomeDialog(player);
            case "manage" -> {
                if (args.length >= 2) {
                    String homeName = extractArgument(args, 1);
                    openManageHomeDialog(player, homeName);
                } else {
                    openHomesDialog(player);
                }
            }
            case "rename" -> {
                if (args.length >= 2) {
                    String homeName = extractArgument(args, 1);
                    openRenameHomeDialog(player, homeName);
                } else {
                    openHomesDialog(player);
                }
            }
            case "deleteconfirm" -> {
                if (args.length >= 2) {
                    String homeName = extractArgument(args, 1);
                    openDeleteConfirmDialog(player, homeName);
                } else {
                    openHomesDialog(player);
                }
            }
            case "dosethome" -> {
                if (args.length >= 2) {
                    String homeName = extractArgument(args, 1);
                    handleSetHome(player, homeName);
                } else {
                    player.sendMessage(Component.text("Nama home tidak boleh kosong!", NamedTextColor.RED));
                }
            }
            case "doteleport" -> {
                if (args.length >= 2) {
                    String homeName = extractArgument(args, 1);
                    handleTeleportHome(player, homeName);
                }
            }
            case "dodelete" -> {
                if (args.length >= 2) {
                    String homeName = extractArgument(args, 1);
                    handleDeleteHome(player, homeName);
                }
            }
            case "dorename" -> {
                if (args.length >= 2) {
                    String combined = extractArgument(args, 1);
                    String[] parts = combined.split(" ::: ", 2);
                    if (parts.length == 2) {
                        handleRenameHome(player, parts[0].trim(), parts[1].trim());
                    }
                }
            }
            default -> openHomesDialog(player);
        }

        return true;
    }

    private void handleSettingsCommand(Player player, String[] args) {
        if (args.length == 0) {
            openScoreboardSettingsDialog(player);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "scoreboard" -> openScoreboardSettingsDialog(player);
            case "back" -> openSettingsDialog(player);
            case "toggle" -> {
                if (args.length >= 2) {
                    String option = args[1].toLowerCase();
                    switch (option) {
                        case "scoreboard", "master" -> scoreboardSettingsManager.toggleScoreboard(player);
                        case "money" -> scoreboardSettingsManager.toggleMoney(player);
                        case "shards" -> scoreboardSettingsManager.toggleShards(player);
                        case "kills" -> scoreboardSettingsManager.toggleKills(player);
                        case "deaths" -> scoreboardSettingsManager.toggleDeaths(player);
                        case "playtime" -> scoreboardSettingsManager.togglePlaytime(player);
                    }
                }
                openScoreboardSettingsDialog(player);
            }
            default -> openScoreboardSettingsDialog(player);
        }
    }

    private void handlePayCommand(Player player, String[] args) {
        if (args.length == 0) {
            payDialogManager.openMainPayScreen(player);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "addscreen" -> payDialogManager.openAddPlayerScreen(player);
            case "doadd" -> {
                if (args.length >= 2) {
                    String targetName = extractArgument(args, 1);
                    payListManager.addPlayer(player.getUniqueId(), targetName);
                }
                payDialogManager.openMainPayScreen(player);
            }
            case "select" -> {
                if (args.length >= 2) {
                    String targetName = extractArgument(args, 1);
                    payDialogManager.openSelectAmountScreen(player, targetName);
                } else {
                    payDialogManager.openMainPayScreen(player);
                }
            }
            case "customscreen" -> {
                if (args.length >= 2) {
                    String targetName = extractArgument(args, 1);
                    payDialogManager.openCustomAmountScreen(player, targetName);
                } else {
                    payDialogManager.openMainPayScreen(player);
                }
            }
            case "docustom" -> {
                if (args.length >= 2) {
                    String combined = extractArgument(args, 1);
                    String[] parts = combined.split(" ::: ", 2);
                    if (parts.length == 2) {
                        String targetName = parts[0].trim();
                        String amountStr = parts[1].trim();
                        double amount = PayDialogManager.parseAmount(amountStr);
                        if (amount > 0) {
                            payDialogManager.openConfirmScreen(player, targetName, amount, PayDialogManager.formatDisplayAmount(amount));
                        } else {
                            player.sendMessage(Component.text("Format jumlah uang tidak valid! Contoh: 100, 10k, 1.5m", NamedTextColor.RED));
                            payDialogManager.openCustomAmountScreen(player, targetName);
                        }
                    }
                }
            }
            case "confirm" -> {
                if (args.length >= 3) {
                    String targetName = args[1];
                    try {
                        double amount = Double.parseDouble(args[2]);
                        String display = args.length >= 4 ? args[3] : PayDialogManager.formatDisplayAmount(amount);
                        payDialogManager.openConfirmScreen(player, targetName, amount, display);
                    } catch (NumberFormatException e) {
                        payDialogManager.openSelectAmountScreen(player, targetName);
                    }
                }
            }
            case "dopay" -> {
                if (args.length >= 3) {
                    String targetName = args[1];
                    try {
                        double amount = Double.parseDouble(args[2]);
                        payDialogManager.handleExecutePay(player, targetName, amount);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("Format nominal tidak valid!", NamedTextColor.RED));
                    }
                }
            }
            case "backtomenu" -> payDialogManager.openGreetingDialog(player);
            default -> payDialogManager.openMainPayScreen(player);
        }
    }

    private String extractArgument(String[] args, int startIndex) {
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length)).trim();
    }

    // ==========================================
    // SCOREBOARD SETTINGS DIALOG
    // ==========================================

    public void openScoreboardSettingsDialog(Player player) {
        ScoreboardSettings s = scoreboardSettingsManager.getSettings(player);

        List<ActionButton> buttons = new ArrayList<>();

        // 1. Scoreboard: ON/OFF
        buttons.add(ActionButton.builder(Component.text("Scoreboard: ", NamedTextColor.WHITE)
                        .append(s.isScoreboardEnabled() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle scoreboard")))
                .build());

        // 2. Show Money: ON/OFF
        buttons.add(ActionButton.builder(Component.text("Show Money: ", NamedTextColor.WHITE)
                        .append(s.isShowMoney() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle money")))
                .build());

        // 3. Show Shards: ON/OFF
        buttons.add(ActionButton.builder(Component.text("Show Shards: ", NamedTextColor.WHITE)
                        .append(s.isShowShards() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle shards")))
                .build());

        // 4. Show Kills: ON/OFF
        buttons.add(ActionButton.builder(Component.text("Show Kills: ", NamedTextColor.WHITE)
                        .append(s.isShowKills() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle kills")))
                .build());

        // 5. Show Deaths: ON/OFF
        buttons.add(ActionButton.builder(Component.text("Show Deaths: ", NamedTextColor.WHITE)
                        .append(s.isShowDeaths() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle deaths")))
                .build());

        // 6. Show Playtime: ON/OFF
        buttons.add(ActionButton.builder(Component.text("Show Playtime: ", NamedTextColor.WHITE)
                        .append(s.isShowPlaytime() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle playtime")))
                .build());

        ActionButton exitAction = ActionButton.builder(Component.translatable("gui.back"))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings back")))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Scoreboard Settings"))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(buttons)
                .columns(1)
                .exitAction(exitAction)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    public void openSettingsDialog(Player player) {
        try {
            Dialog settingsDialog = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.DIALOG)
                    .get(TypedKey.create(RegistryKey.DIALOG, Key.key("slowy", "settings_dialog")));
            if (settingsDialog != null) {
                player.showDialog(settingsDialog);
                return;
            }
        } catch (Throwable t) {
            getLogger().warning("Gagal membuka dialog settings: " + t.getMessage());
        }
        player.performCommand("showdialog slowy:settings_dialog");
    }

    // ==========================================
    // INTEGRATION & DATA HELPERS
    // ==========================================

    private UltimateDonutSmp getUltimateDonutSmp() {
        if (Bukkit.getPluginManager().isPluginEnabled("UltimateDonutSmp")) {
            try {
                return UltimateDonutSmp.getInstance();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private Essentials getEssentials() {
        if (Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
            try {
                return (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private List<String> getPlayerHomeNames(Player player) {
        List<String> homeNames = new ArrayList<>();

        UltimateDonutSmp donut = getUltimateDonutSmp();
        if (donut != null && donut.getHomeManager() != null) {
            try {
                List<Home> donutHomes = donut.getHomeManager().getHomes(player.getUniqueId());
                if (donutHomes != null) {
                    for (Home h : donutHomes) {
                        if (h != null && h.getName() != null) {
                            homeNames.add(h.getName());
                        }
                    }
                }
            } catch (Throwable t) {
                getLogger().warning("Gagal mengambil data home dari UltimateDonutSmp: " + t.getMessage());
            }
        }

        if (homeNames.isEmpty()) {
            Essentials ess = getEssentials();
            if (ess != null) {
                try {
                    User user = ess.getUser(player);
                    if (user != null && user.getHomes() != null) {
                        homeNames.addAll(user.getHomes());
                    }
                } catch (Throwable t) {
                    getLogger().warning("Gagal mengambil data home dari Essentials: " + t.getMessage());
                }
            }
        }

        return homeNames;
    }

    // ==========================================
    // HOMES DIALOG SCREENS
    // ==========================================

    public void openHomesDialog(Player player) {
        List<String> homeNames = getPlayerHomeNames(player);
        List<ActionButton> buttons = new ArrayList<>(MAX_HOME_SLOTS);

        for (int i = 0; i < MAX_HOME_SLOTS; i++) {
            if (i < homeNames.size()) {
                String homeName = homeNames.get(i);
                ActionButton homeButton = ActionButton.builder(Component.text(homeName, NamedTextColor.WHITE))
                        .width(BUTTON_WIDTH_12_CHARS)
                        .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes manage " + homeName)))
                        .build();
                buttons.add(homeButton);
            } else {
                ActionButton newHomeButton = ActionButton.builder(Component.text("New Home", NamedTextColor.GRAY))
                        .width(BUTTON_WIDTH_12_CHARS)
                        .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes new")))
                        .build();
                buttons.add(newHomeButton);
            }
        }

        DialogBase base = DialogBase.builder(Component.text("Homes"))
                .body(List.of(DialogBody.item(BED_ITEM).build()))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(buttons)
                .columns(MAX_HOME_SLOTS)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    public void openCreateHomeDialog(Player player) {
        TextDialogInput nameInput = DialogInput.text("home_name", Component.text("Nama Home (Maks. 12 Karakter)"))
                .maxLength(MAX_HOME_NAME_LENGTH)
                .build();

        ActionButton submitBtn = ActionButton.builder(Component.text("Set Home", NamedTextColor.GREEN))
                .action(DialogAction.commandTemplate("/customhomes dosethome $(home_name)"))
                .build();

        ActionButton cancelBtn = ActionButton.builder(Component.text("Batal", NamedTextColor.RED))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes")))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Create New Home"))
                .body(List.of(DialogBody.plainMessage(Component.text("Masukkan nama untuk home barumu (Maksimal 12 karakter):", NamedTextColor.GRAY))))
                .inputs(List.of(nameInput))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(submitBtn, cancelBtn))
                .columns(2)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    public void openManageHomeDialog(Player player, String homeName) {
        ActionButton teleportBtn = ActionButton.builder(Component.text("Teleport", NamedTextColor.GREEN))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes doteleport " + homeName)))
                .build();

        ActionButton renameBtn = ActionButton.builder(Component.text("Rename", NamedTextColor.GOLD))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes rename " + homeName)))
                .build();

        ActionButton deleteBtn = ActionButton.builder(Component.text("Delete", NamedTextColor.RED))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes deleteconfirm " + homeName)))
                .build();

        ActionButton backBtn = ActionButton.builder(Component.text("Kembali", NamedTextColor.GRAY))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes")))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Home: " + homeName))
                .body(List.of(DialogBody.item(BED_ITEM).build()))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(teleportBtn, renameBtn, deleteBtn, backBtn))
                .columns(2)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    public void openRenameHomeDialog(Player player, String oldHomeName) {
        String initialValue = oldHomeName.length() > MAX_HOME_NAME_LENGTH
                ? oldHomeName.substring(0, MAX_HOME_NAME_LENGTH)
                : oldHomeName;

        TextDialogInput nameInput = DialogInput.text("new_name", Component.text("Nama Baru (Maks. 12 Karakter)"))
                .initial(initialValue)
                .maxLength(MAX_HOME_NAME_LENGTH)
                .build();

        ActionButton saveBtn = ActionButton.builder(Component.text("Simpan Nama", NamedTextColor.GOLD))
                .action(DialogAction.commandTemplate("/customhomes dorename " + oldHomeName + " ::: $(new_name)"))
                .build();

        ActionButton cancelBtn = ActionButton.builder(Component.text("Batal", NamedTextColor.RED))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes manage " + oldHomeName)))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Rename: " + oldHomeName))
                .body(List.of(DialogBody.plainMessage(Component.text("Masukkan nama baru untuk home '" + oldHomeName + "' (Maksimal 12 karakter):", NamedTextColor.GRAY))))
                .inputs(List.of(nameInput))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(saveBtn, cancelBtn))
                .columns(2)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    public void openDeleteConfirmDialog(Player player, String homeName) {
        ActionButton confirmDeleteBtn = ActionButton.builder(Component.text("Ya, Hapus Home", NamedTextColor.RED))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes dodelete " + homeName)))
                .build();

        ActionButton cancelBtn = ActionButton.builder(Component.text("Batal", NamedTextColor.GRAY))
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes manage " + homeName)))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Hapus " + homeName + "?"))
                .body(List.of(DialogBody.plainMessage(Component.text("Apakah kamu yakin ingin menghapus home '" + homeName + "'?"))))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .build();

        MultiActionType type = DialogType.multiAction(List.of(confirmDeleteBtn, cancelBtn))
                .columns(2)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty().base(base).type(type));
        player.showDialog(dialog);
    }

    // ==========================================
    // ACTION HANDLERS
    // ==========================================

    private void handleSetHome(Player player, String homeName) {
        if (homeName.isEmpty()) {
            player.sendMessage(Component.text("Nama home tidak boleh kosong!", NamedTextColor.RED));
            return;
        }

        if (homeName.length() > MAX_HOME_NAME_LENGTH) {
            player.sendMessage(Component.text("Nama home maksimal 12 karakter!", NamedTextColor.RED));
            return;
        }

        boolean success = false;
        Location loc = player.getLocation();

        UltimateDonutSmp donut = getUltimateDonutSmp();
        if (donut != null && donut.getHomeManager() != null) {
            try {
                success = donut.getHomeManager().setHome(player.getUniqueId(), homeName, loc);
            } catch (Throwable ignored) {}
        }

        if (!success) {
            Essentials ess = getEssentials();
            if (ess != null) {
                try {
                    User user = ess.getUser(player);
                    if (user != null) {
                        user.setHome(homeName, loc);
                        success = true;
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (success) {
            player.sendMessage(Component.text("Home '" + homeName + "' berhasil dibuat di lokasimu!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Gagal membuat home '" + homeName + "'.", NamedTextColor.RED));
        }

        openHomesDialog(player);
    }

    private void handleTeleportHome(Player player, String homeName) {
        player.closeDialog();
        player.closeInventory();

        boolean queued = false;

        UltimateDonutSmp donut = getUltimateDonutSmp();
        if (donut != null && donut.getHomeManager() != null && donut.getTeleportManager() != null) {
            try {
                Home h = donut.getHomeManager().getHome(player.getUniqueId(), homeName);
                if (h != null && h.getLocation() != null) {
                    donut.getTeleportManager().queue(player, h.getLocation(), "HOME", p -> {
                        if (p != null && p.isOnline()) {
                            p.closeInventory();
                            p.closeDialog();
                        }
                    });
                    queued = true;
                }
            } catch (Throwable ignored) {}
        }

        if (!queued && Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
            try {
                player.performCommand("home " + homeName);
                queued = true;
            } catch (Throwable ignored) {}
        }

        if (!queued) {
            player.sendMessage(Component.text("Lokasi home '" + homeName + "' tidak ditemukan!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        player.closeInventory();
        player.closeDialog();
    }

    private void handleDeleteHome(Player player, String homeName) {
        boolean deleted = false;

        UltimateDonutSmp donut = getUltimateDonutSmp();
        if (donut != null && donut.getHomeManager() != null) {
            try {
                deleted = donut.getHomeManager().deleteHome(player.getUniqueId(), homeName);
            } catch (Throwable ignored) {}
        }

        if (!deleted) {
            Essentials ess = getEssentials();
            if (ess != null) {
                try {
                    User user = ess.getUser(player);
                    if (user != null) {
                        user.delHome(homeName);
                        deleted = true;
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (deleted) {
            player.sendMessage(Component.text("Home '" + homeName + "' berhasil dihapus!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Gagal menghapus home '" + homeName + "'.", NamedTextColor.RED));
        }

        openHomesDialog(player);
    }

    private void handleRenameHome(Player player, String oldName, String newName) {
        if (newName.isEmpty()) {
            player.sendMessage(Component.text("Nama home tidak boleh kosong!", NamedTextColor.RED));
            return;
        }

        if (newName.length() > MAX_HOME_NAME_LENGTH) {
            player.sendMessage(Component.text("Nama home maksimal 12 karakter!", NamedTextColor.RED));
            return;
        }

        boolean success = false;

        UltimateDonutSmp donut = getUltimateDonutSmp();
        if (donut != null && donut.getHomeManager() != null) {
            try {
                success = donut.getHomeManager().renameHome(player.getUniqueId(), oldName, newName);
            } catch (Throwable ignored) {}
        }

        if (!success) {
            Essentials ess = getEssentials();
            if (ess != null) {
                try {
                    User user = ess.getUser(player);
                    if (user != null) {
                        user.renameHome(oldName, newName);
                        success = true;
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (success) {
            player.sendMessage(Component.text("Home '" + oldName + "' berhasil diubah menjadi '" + newName + "'!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Gagal mengubah nama home.", NamedTextColor.RED));
        }

        openHomesDialog(player);
    }
}

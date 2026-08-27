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
import dev.geco.gsit.api.event.PrePlayerPlayerSitEvent;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class SlowyPlugin extends JavaPlugin implements CommandExecutor, Listener {

    public static final int MAX_HOME_NAME_LENGTH = 12;
    public static final int MAX_HOME_SLOTS = 6;
    public static final int BUTTON_WIDTH_12_CHARS = 150;
    public static final Set<String> BLOCKED_HOME_WORLDS = Set.of("lobby2", "minecraft:lobby2");

    private static final ItemStack BED_ITEM = new ItemStack(Material.WHITE_BED);

    private ScoreboardSettingsManager scoreboardSettingsManager;
    private ChatSettingsManager chatSettingsManager;
    private CustomScoreboardManager customScoreboardManager;
    private PayListManager payListManager;
    private PayDialogManager payDialogManager;
    private LeaderboardHologramManager leaderboardHologramManager;

    @Override
    public void onEnable() {
        this.scoreboardSettingsManager = new ScoreboardSettingsManager(this);
        this.chatSettingsManager = new ChatSettingsManager(this);
        this.customScoreboardManager = new CustomScoreboardManager(this);
        this.customScoreboardManager.start();

        this.payListManager = new PayListManager(this);
        this.payDialogManager = new PayDialogManager(this, payListManager);

        this.leaderboardHologramManager = new LeaderboardHologramManager(this);
        this.leaderboardHologramManager.start();

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
        if (leaderboardHologramManager != null) {
            leaderboardHologramManager.stop();
        }
        if (customScoreboardManager != null) {
            customScoreboardManager.stop();
        }
        if (scoreboardSettingsManager != null) {
            scoreboardSettingsManager.saveAll();
        }
        if (chatSettingsManager != null) {
            chatSettingsManager.saveAll();
        }
        if (payListManager != null) {
            payListManager.saveAllSync();
        }
    }

    public LeaderboardHologramManager getLeaderboardHologramManager() {
        return leaderboardHologramManager;
    }

    public CustomScoreboardManager getCustomScoreboardManager() {
        return customScoreboardManager;
    }

    public ScoreboardSettingsManager getScoreboardSettingsManager() {
        return scoreboardSettingsManager;
    }

    public ChatSettingsManager getChatSettingsManager() {
        return chatSettingsManager;
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

        Player joining = event.getPlayer();
        Component joinMsg = event.joinMessage();
        if (joinMsg != null) {
            event.joinMessage(null);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (shouldReceiveJoinLeave(p, joining)) {
                    p.sendMessage(joinMsg);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (customScoreboardManager != null) {
            customScoreboardManager.removeScoreboard(event.getPlayer());
        }

        Player quitting = event.getPlayer();
        Component quitMsg = event.quitMessage();
        if (quitMsg != null) {
            event.quitMessage(null);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getUniqueId().equals(quitting.getUniqueId()) && shouldReceiveJoinLeave(p, quitting)) {
                    p.sendMessage(quitMsg);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        event.viewers().removeIf(audience -> {
            if (audience instanceof Player p && chatSettingsManager != null) {
                ChatSettings s = chatSettingsManager.getSettings(p);
                return !s.isPublicChat();
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Component deathMsg = event.deathMessage();
        if (deathMsg != null) {
            event.deathMessage(null);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (shouldReceiveDeath(p, dead)) {
                    p.sendMessage(deathMsg);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        Player advPlayer = event.getPlayer();
        Component msg = event.message();
        if (msg != null) {
            event.message(null);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (shouldReceiveAdvancement(p, advPlayer)) {
                    p.sendMessage(msg);
                }
            }
        }
    }

    private boolean shouldReceiveJoinLeave(Player viewer, Player target) {
        if (chatSettingsManager == null) return true;
        ChatSettings s = chatSettingsManager.getSettings(viewer);
        ChatVisibility mode = s.getJoinLeaveMessages();
        if (mode == ChatVisibility.OFF) return false;
        if (mode == ChatVisibility.ANYONE) return true;
        return chatSettingsManager.isFriend(viewer.getUniqueId(), target.getUniqueId());
    }

    private boolean shouldReceiveDeath(Player viewer, Player target) {
        if (chatSettingsManager == null) return true;
        ChatSettings s = chatSettingsManager.getSettings(viewer);
        ChatVisibility mode = s.getDeathMessages();
        if (mode == ChatVisibility.OFF) return false;
        if (mode == ChatVisibility.ANYONE) return true;
        return chatSettingsManager.isFriend(viewer.getUniqueId(), target.getUniqueId());
    }

    private boolean shouldReceiveAdvancement(Player viewer, Player target) {
        if (chatSettingsManager == null) return true;
        ChatSettings s = chatSettingsManager.getSettings(viewer);
        ChatVisibility mode = s.getAdvancementMessages();
        if (mode == ChatVisibility.OFF) return false;
        if (mode == ChatVisibility.ANYONE) return true;
        return chatSettingsManager.isFriend(viewer.getUniqueId(), target.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getWorld().getName().toLowerCase().contains("lobby2") && event.isGliding()) {
                event.setCancelled(true);
                player.setGliding(false);
                player.sendMessage(Component.text("Kamu tidak dapat menggunakan Elytra di lobby!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrePlayerPlayerSit(PrePlayerPlayerSitEvent event) {
        Player target = event.getTarget();
        if (target != null && isCitizensNPC(target)) {
            event.setCancelled(true);
        }
    }

    public boolean isCitizensNPC(Entity entity) {
        if (entity == null) return false;
        if (entity.hasMetadata("NPC")) return true;
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                return CitizensAPI.getNPCRegistry().isNPC(entity);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean isBlockedHomeWorld(String worldName) {
        if (worldName == null) return false;
        String name = worldName.toLowerCase();
        return BLOCKED_HOME_WORLDS.contains(name) || name.contains("lobby2");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("slowy.admin")) {
            event.getCommands().remove("customsettings");
            event.getCommands().remove("slowy:customsettings");
            event.getCommands().remove("sbsettings");
            event.getCommands().remove("slowy:sbsettings");
            event.getCommands().remove("slowysettings");
            event.getCommands().remove("slowy:slowysettings");
            event.getCommands().remove("customhomes");
            event.getCommands().remove("slowy:customhomes");
            event.getCommands().remove("custompay");
            event.getCommands().remove("slowy:custompay");
            event.getCommands().remove("slowypay");
            event.getCommands().remove("slowy:slowypay");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String rawMsg = event.getMessage().trim();
        String msg = rawMsg.startsWith("/") ? rawMsg.substring(1).trim() : rawMsg;
        String[] parts = msg.split("\\s+");
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();

        // 1. Block sethome in blocked worlds (lobby2)
        if (isBlockedHomeWorld(player.getWorld().getName())) {
            if (cmd.equals("sethome") || cmd.equals("esethome") || cmd.equals("createhome")
                    || cmd.endsWith(":sethome") || cmd.endsWith(":esethome") || cmd.endsWith(":createhome")) {
                event.setCancelled(true);
                player.sendMessage(Component.text("Kamu tidak dapat membuat home di world ini!", NamedTextColor.RED));
                return;
            }
        }

        // 2. Private message privacy filtering
        if (parts.length >= 2 && chatSettingsManager != null) {
            if (cmd.equals("msg") || cmd.equals("tell") || cmd.equals("w") || cmd.equals("whisper")
                    || cmd.equals("pm") || cmd.equals("emsg") || cmd.equals("etell") || cmd.equals("ewhisper") || cmd.equals("epm")
                    || cmd.endsWith(":msg") || cmd.endsWith(":tell") || cmd.endsWith(":w") || cmd.endsWith(":whisper")) {
                String targetName = parts[1];
                Player target = Bukkit.getPlayer(targetName);
                if (target != null && target.isOnline() && !target.getUniqueId().equals(player.getUniqueId())) {
                    ChatSettings targetSettings = chatSettingsManager.getSettings(target);
                    ChatVisibility mode = targetSettings.getPrivateMessages();
                    if (mode == ChatVisibility.OFF) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("Pemain ini menonaktifkan pesan pribadi.", NamedTextColor.RED));
                    } else if (mode == ChatVisibility.FRIENDS) {
                        if (!chatSettingsManager.isFriend(target.getUniqueId(), player.getUniqueId())) {
                            event.setCancelled(true);
                            player.sendMessage(Component.text("Pemain ini hanya menerima pesan pribadi dari teman.", NamedTextColor.RED));
                        }
                    }
                }
            }
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
            case "back", "backtomenu" -> openGreetingDialog(player);
            default -> openHomesDialog(player);
        }

        return true;
    }

    private void handleSettingsCommand(Player player, String[] args) {
        if (args.length == 0) {
            openSettingsDialog(player);
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "scoreboard" -> openScoreboardSettingsDialog(player);
            case "chat" -> openChatSettingsDialog(player);
            case "back" -> openSettingsDialog(player);
            case "toggle" -> {
                if (args.length >= 2) {
                    String option = args[1].toLowerCase();
                    switch (option) {
                        case "scoreboard", "master" -> {
                            scoreboardSettingsManager.toggleScoreboard(player);
                            openScoreboardSettingsDialog(player);
                        }
                        case "money" -> {
                            scoreboardSettingsManager.toggleMoney(player);
                            openScoreboardSettingsDialog(player);
                        }
                        case "shards" -> {
                            scoreboardSettingsManager.toggleShards(player);
                            openScoreboardSettingsDialog(player);
                        }
                        case "kills" -> {
                            scoreboardSettingsManager.toggleKills(player);
                            openScoreboardSettingsDialog(player);
                        }
                        case "deaths" -> {
                            scoreboardSettingsManager.toggleDeaths(player);
                            openScoreboardSettingsDialog(player);
                        }
                        case "playtime" -> {
                            scoreboardSettingsManager.togglePlaytime(player);
                            openScoreboardSettingsDialog(player);
                        }
                        case "chat_public" -> {
                            chatSettingsManager.togglePublicChat(player);
                            openChatSettingsDialog(player);
                        }
                        case "chat_pm" -> {
                            chatSettingsManager.togglePrivateMessages(player);
                            openChatSettingsDialog(player);
                        }
                        case "chat_server" -> {
                            chatSettingsManager.toggleServerChat(player);
                            openChatSettingsDialog(player);
                        }
                        case "chat_hotbar" -> {
                            chatSettingsManager.toggleServerHotbar(player);
                            openChatSettingsDialog(player);
                        }
                        case "chat_death" -> {
                            chatSettingsManager.toggleDeathMessages(player);
                            openChatSettingsDialog(player);
                        }
                        case "chat_advancement" -> {
                            chatSettingsManager.toggleAdvancementMessages(player);
                            openChatSettingsDialog(player);
                        }
                        case "chat_joinleave" -> {
                            chatSettingsManager.toggleJoinLeaveMessages(player);
                            openChatSettingsDialog(player);
                        }
                        default -> openSettingsDialog(player);
                    }
                } else {
                    openSettingsDialog(player);
                }
            }
            default -> openSettingsDialog(player);
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

    public void openChatSettingsDialog(Player player) {
        ChatSettings s = chatSettingsManager.getSettings(player);

        List<ActionButton> buttons = new ArrayList<>();

        // 1. Public Chat: ON / OFF
        buttons.add(ActionButton.builder(Component.text("Public Chat: ", NamedTextColor.WHITE)
                        .append(s.isPublicChat() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle chat_public")))
                .build());

        // 2. Private Messages: Anyone / Friends / OFF
        buttons.add(ActionButton.builder(Component.text("Private Messages: ", NamedTextColor.WHITE)
                        .append(s.getPrivateMessages().toComponent()))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle chat_pm")))
                .build());

        // 3. Server Chat Messages: ON / OFF
        buttons.add(ActionButton.builder(Component.text("Server Chat Messages: ", NamedTextColor.WHITE)
                        .append(s.isServerChatMessages() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle chat_server")))
                .build());

        // 4. Server Hotbar Messages: ON / OFF
        buttons.add(ActionButton.builder(Component.text("Server Hotbar Messages: ", NamedTextColor.WHITE)
                        .append(s.isServerHotbarMessages() ? Component.text("ON", NamedTextColor.GREEN) : Component.text("OFF", NamedTextColor.RED)))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle chat_hotbar")))
                .build());

        // 5. Death Messages: Anyone / Friends / OFF
        buttons.add(ActionButton.builder(Component.text("Death Messages: ", NamedTextColor.WHITE)
                        .append(s.getDeathMessages().toComponent()))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle chat_death")))
                .build());

        // 6. Advancement Messages: Anyone / Friends / OFF
        buttons.add(ActionButton.builder(Component.text("Advancement Messages: ", NamedTextColor.WHITE)
                        .append(s.getAdvancementMessages().toComponent()))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle chat_advancement")))
                .build());

        // 7. Join/Leave Messages: Anyone / Friends / OFF
        buttons.add(ActionButton.builder(Component.text("Join/Leave Messages: ", NamedTextColor.WHITE)
                        .append(s.getJoinLeaveMessages().toComponent()))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings toggle chat_joinleave")))
                .build());

        ActionButton exitAction = ActionButton.builder(Component.translatable("gui.back"))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customsettings back")))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Chat Settings"))
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

        ActionButton exitAction = ActionButton.builder(Component.translatable("gui.back"))
                .width(200)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/customhomes back")))
                .build();

        DialogBase base = DialogBase.builder(Component.text("Homes"))
                .body(List.of(DialogBody.item(BED_ITEM).build()))
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

    public void openCreateHomeDialog(Player player) {
        if (isBlockedHomeWorld(player.getWorld().getName())) {
            player.sendMessage(Component.text("Kamu tidak dapat membuat home di world ini!", NamedTextColor.RED));
            return;
        }

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
        if (isBlockedHomeWorld(player.getWorld().getName())) {
            player.sendMessage(Component.text("Kamu tidak dapat membuat home di world ini!", NamedTextColor.RED));
            return;
        }

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

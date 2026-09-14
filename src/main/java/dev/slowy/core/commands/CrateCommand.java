package dev.slowy.core.commands;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.crate.CrateDefinition;
import dev.slowy.core.crate.CrateManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.*;

@NullMarked
public final class CrateCommand implements SlowyBasicCommand {

    private final SlowyCore plugin;
    private final CrateManager crateManager;

    public CrateCommand(SlowyCore plugin, CrateManager crateManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.crateManager = Objects.requireNonNull(crateManager, "crateManager cannot be null");
    }

    @Override
    public String name() {
        return "crate";
    }

    @Override
    public String description() {
        return "View your crate keys or give keys (Owner only).";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.parse("<#FF0055>Usage: /crate give <player> <crate> <amount></#FF0055>"));
                return;
            }
            sendPlayerCrateStatus(player);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("give".equals(sub)) {
            // Strict Owner permission check
            if (!sender.hasPermission(CoreConfig.PERM_OWNER) && !sender.isOp()) {
                sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Only Server Owner can execute this command.</#FF0055>"));
                return;
            }

            if (args.length < 4) {
                sender.sendMessage(ColorUtils.parse("<#FF0055>Usage: <#FFE600>/crate give <player|all> <crate> <amount></#FFE600></#FF0055>"));
                return;
            }

            String targetName = args[1];
            String crateId = args[2].toLowerCase(Locale.ROOT);
            CrateDefinition crate = crateManager.getCrate(crateId);
            if (crate == null) {
                sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Crate <#FFE600>\"" + crateId + "\"</#FFE600> not found!</#FF0055>"));
                return;
            }

            int amount = 1;
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Invalid amount: <#FFE600>" + args[3] + "</#FFE600></#FF0055>"));
                return;
            }

            if ("all".equalsIgnoreCase(targetName) || "*".equalsIgnoreCase(targetName)) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    crateManager.addKeys(online.getUniqueId(), crate.getId(), amount);
                    online.sendActionBar(ColorUtils.parse("<#1DA1F2>You received <#FFE600>" + amount + " " + crate.getFormattedKeyName() + "</#FFE600></#1DA1F2>"));
                }
                sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Gave <#FFE600>" + amount + "x " + crate.getKeyDisplayName() + "</#FFE600> to all online players!</#39FF14>"));
            } else {
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    crateManager.addKeys(target.getUniqueId(), crate.getId(), amount);
                    target.sendActionBar(ColorUtils.parse("<#1DA1F2>You received <#FFE600>" + amount + " " + crate.getFormattedKeyName() + "</#FFE600></#1DA1F2>"));
                    sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Gave <#FFE600>" + amount + "x " + crate.getKeyDisplayName() + "</#FFE600> to <#E0F8FF>" + target.getName() + "</#E0F8FF>!</#39FF14>"));
                } else {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
                    if (off.getUniqueId() != null) {
                        crateManager.addKeys(off.getUniqueId(), crate.getId(), amount);
                        sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Gave <#FFE600>" + amount + "x " + crate.getKeyDisplayName() + "</#FFE600> to offline player <#E0F8FF>" + targetName + "</#E0F8FF>!</#39FF14>"));
                    } else {
                        sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Player <#FFE600>\"" + targetName + "\"</#FFE600> not found!</#FF0055>"));
                    }
                }
            }
            return;
        }

        if (sender instanceof Player player) {
            sendPlayerCrateStatus(player);
        } else {
            sender.sendMessage(ColorUtils.parse("<#FF0055>Usage: /crate give <player> <crate> <amount></#FF0055>"));
        }
    }

    private void sendPlayerCrateStatus(Player player) {
        player.sendMessage(ColorUtils.parse("<#FF00BD>═══ [ YOUR CRATE KEYS ] ═══</#FF00BD>"));
        player.sendMessage(ColorUtils.parse("<gray>All Crates are located physically at <#FFE600>Spawn</#FFE600>!</gray>"));
        player.sendMessage(ColorUtils.parse(""));
        player.sendMessage(ColorUtils.parse("<#E0F8FF>Keys Available:</#E0F8FF>"));
        for (CrateDefinition def : crateManager.getCrates()) {
            int keys = crateManager.getKeyBalance(player.getUniqueId(), def.getId());
            player.sendMessage(ColorUtils.parse(" <dark_gray>•</dark_gray> " + def.getDisplayName() + "<gray>: </gray><#FFE600>" + keys + " Keys</#FFE600>"));
        }
        if (crateManager.getKeyAllManager() != null) {
            int remSec = crateManager.getKeyAllManager().getRemainingSeconds(player.getUniqueId());
            int mins = remSec / 60;
            int secs = remSec % 60;
            player.sendMessage(ColorUtils.parse(""));
            player.sendMessage(ColorUtils.parse("<gray>Next Playtime Key in: <#FFE600>" + mins + "m " + secs + "s</#FFE600></gray>"));
        }
        player.sendMessage(ColorUtils.parse("<#FF00BD>═══════════════════════════</#FF00BD>"));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        boolean isOwner = sender.hasPermission(CoreConfig.PERM_OWNER) || sender.isOp();

        if (!isOwner) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(List.of("give"), args[0]);
        }

        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            List<String> players = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            players.add("all");
            return filter(players, args[1]);
        }

        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return filter(crateManager.getCrates().stream().map(CrateDefinition::getId).toList(), args[2]);
        }

        if (args.length == 4 && "give".equalsIgnoreCase(args[0])) {
            return filter(List.of("1", "5", "10", "32", "64"), args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return list.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}

package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.skin.PlayerSkinProfile;
import dev.slowy.core.skin.SkinFetcher;
import dev.slowy.core.skin.SkinManager;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@NullMarked
public final class SkinCommand implements SlowyBasicCommand {

    private final SkinManager skinManager;

    public SkinCommand(SkinManager skinManager) {
        this.skinManager = Objects.requireNonNull(skinManager, "skinManager cannot be null");
    }

    @Override
    public String name() {
        return "skin";
    }

    @Override
    public String description() {
        return "Change your skin via MineSkin URL or reset to your original skin.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.parse("<#1DA1F2><bold>Skin System</bold></#1DA1F2>"));
            sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/skin <mineskin_url></#FFE600> - Ganti skin (hanya link MineSkin, cooldown 3 hari)</gray>"));
            sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/skin reset</#FFE600> - Reset skin kembali ke skin asal (kapanpun)</gray>"));
            if (sender.hasPermission("slowy.skin.admin") || sender.isOp()) {
                sender.sendMessage(ColorUtils.parse("<gray>• <#FFE600>/skin reset <player></#FFE600> - Reset skin player lain (Admin)</gray>"));
            }
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // /skin reset OR /skin <reset>
        if (sub.equals("reset") || sub.equals("<reset>") || sub.equals("clear")) {
            if (args.length > 1) {
                if (!sender.hasPermission("slowy.skin.admin") && !sender.isOp()) {
                    sender.sendMessage(ColorUtils.parse(CoreConfig.NO_PERMISSION));
                    return;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ColorUtils.parse("<red>✖ Player tidak ditemukan atau sedang offline.</red>"));
                    return;
                }
                sender.sendMessage(ColorUtils.parse("<gray>Mereset skin untuk <#E0F8FF>" + target.getName() + "</#E0F8FF>...</gray>"));
                skinManager.resetSkin(target, success -> {
                    if (success) {
                        sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Skin untuk <#E0F8FF>" + target.getName() + "</#E0F8FF> berhasil direset ke skin asal!</#39FF14>"));
                        target.sendActionBar(ColorUtils.parse("<#39FF14>✔ Skin kamu telah direset oleh admin.</#39FF14>"));
                    } else {
                        sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Gagal mereset skin untuk <#E0F8FF>" + target.getName() + "</#E0F8FF>.</#FF0055>"));
                    }
                });
                return;
            }

            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Gunakan /skin reset <player> dari console.</#FF0055>"));
                return;
            }

            player.sendActionBar(ColorUtils.parse("<gray>Mereset skin...</gray>"));
            skinManager.resetSkin(player, success -> {
                if (success) {
                    player.sendActionBar(ColorUtils.parse("<#39FF14>✔ Skin berhasil direset ke skin asal!</#39FF14>"));
                } else {
                    player.sendActionBar(ColorUtils.parse("<#FF0055>✖ Gagal mereset skin.</#FF0055>"));
                }
            });
            return;
        }

        // /skin <url> or /skin url <url>
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>✖ Hanya pemain yang dapat mengganti skin.</#FF0055>"));
            return;
        }

        String rawInput = sub.equals("url") && args.length > 1 ? args[1] : args[0];
        applyMineSkin(player, rawInput);
    }

    private void applyMineSkin(Player player, String input) {
        String cleanInput = input.trim();
        String mineSkinId = SkinFetcher.extractMineSkinId(cleanInput);

        // 1. Validasi MineSkin URL only!
        if (mineSkinId == null) {
            player.sendMessage(ColorUtils.parse("<#FF0055>✖ Hanya link MineSkin yang diperbolehkan!</#FF0055>"));
            player.sendMessage(ColorUtils.parse("<gray>Silakan upload skin di <#E0F8FF>https://mineskin.org</#E0F8FF> lalu ketik:</gray>"));
            player.sendMessage(ColorUtils.parse("<#1DA1F2>➜ /skin https://mineskin.org/skins/<id></#1DA1F2>"));
            player.sendActionBar(ColorUtils.parse("<#FF0055>✖ Hanya link MineSkin yang diperbolehkan!</#FF0055>"));
            return;
        }

        // 2. Cek Cooldown 3 hari
        PlayerSkinProfile profile = skinManager.getOrLoadProfileSync(player.getUniqueId(), player.getName());
        boolean bypass = player.isOp() || player.hasPermission("slowy.skin.admin");
        if (!bypass && profile.isOnCooldown(CoreConfig.SKIN_COOLDOWN_MS)) {
            long remainingMs = profile.getRemainingCooldown(CoreConfig.SKIN_COOLDOWN_MS);
            String formattedTime = formatRemainingTime(remainingMs);
            player.sendActionBar(ColorUtils.parse("<#FF0055>✖ Cooldown ganti skin: tunggu <#FFE600>" + formattedTime + "</#FFE600> lagi.</#FF0055>"));
            return;
        }

        player.sendActionBar(ColorUtils.parse("<gray>Mengambil skin dari <#1DA1F2>MineSkin</#1DA1F2>...</gray>"));

        skinManager.changeSkinToMineSkinAsync(player, cleanInput, result -> {
            switch (result) {
                case SUCCESS -> {
                    player.sendActionBar(ColorUtils.parse("<#39FF14>✔ Skin MineSkin berhasil diterapkan!</#39FF14>"));
                    player.sendMessage(ColorUtils.parse("<#39FF14>✔ Skin MineSkin berhasil diterapkan! (Cooldown 3 hari dimulai)</#39FF14>"));
                }
                case COOLDOWN -> {
                    long remainingMs = profile.getRemainingCooldown(CoreConfig.SKIN_COOLDOWN_MS);
                    String formattedTime = formatRemainingTime(remainingMs);
                    player.sendActionBar(ColorUtils.parse("<#FF0055>✖ Cooldown ganti skin: tunggu <#FFE600>" + formattedTime + "</#FFE600> lagi.</#FF0055>"));
                }
                case INVALID_MINESKIN_URL -> {
                    player.sendActionBar(ColorUtils.parse("<#FF0055>✖ Hanya link MineSkin yang diperbolehkan!</#FF0055>"));
                }
                case FETCH_FAILED -> {
                    player.sendActionBar(ColorUtils.parse("<#FF0055>✖ Gagal mengambil skin dari MineSkin. Pastikan ID atau link benar!</#FF0055>"));
                }
            }
        });
    }

    private String formatRemainingTime(long remainingMs) {
        long days = remainingMs / (24L * 60L * 60L * 1000L);
        long hours = (remainingMs % (24L * 60L * 60L * 1000L)) / (60L * 60L * 1000L);
        long minutes = (remainingMs % (60L * 60L * 1000L)) / (60L * 1000L);
        long seconds = (remainingMs % (60L * 1000L)) / 1000L;

        if (days > 0) {
            return days + " hari " + hours + " jam";
        } else if (hours > 0) {
            return hours + " jam " + minutes + " menit";
        } else if (minutes > 0) {
            return minutes + " menit " + seconds + " detik";
        } else {
            return seconds + " detik";
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        List<String> list = new ArrayList<>();

        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            if ("reset".startsWith(prefix)) list.add("reset");
            if ("https://mineskin.org/skins/".startsWith(prefix)) list.add("https://mineskin.org/skins/");
            if ("https://minesk.in/".startsWith(prefix)) list.add("https://minesk.in/");
            return list;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("<reset>"))) {
            if (sender.hasPermission("slowy.skin.admin") || sender.isOp()) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        list.add(p.getName());
                    }
                }
            }
            return list;
        }

        return list;
    }
}

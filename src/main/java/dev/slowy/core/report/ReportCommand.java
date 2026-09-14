package dev.slowy.core.report;

import dev.slowy.core.commands.SlowyBasicCommand;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Command /report untuk membuka Hopper GUI pelaporan player atau bug.
 * Juga menangani callback dari Paper Custom Screen Dialog (submit_msg / reopen).
 */
@NullMarked
public final class ReportCommand implements SlowyBasicCommand {

    private final ReportManager reportManager;

    public ReportCommand(ReportManager reportManager) {
        this.reportManager = Objects.requireNonNull(reportManager, "reportManager cannot be null");
    }

    @Override
    public String name() {
        return "report";
    }

    @Override
    public String description() {
        return "Buka menu pelaporan pemain atau kendala sistem.";
    }

    @Override
    public List<String> aliases() {
        return List.of("lapor");
    }

    @Override
    public String permission() {
        return "slowy.report";
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse("<red>Perintah ini hanya dapat dijalankan oleh pemain!</red>"));
            return;
        }

        if (!canUse(player)) {
            player.sendMessage(ColorUtils.parse(CoreConfig.NO_PERMISSION));
            return;
        }

        ReportDraft draft = reportManager.getOrCreateDraft(player.getUniqueId());

        // 1. Callback tombol "Kembali" dari Screen Dialog
        if (args.length == 1 && args[0].equalsIgnoreCase("reopen")) {
            draft.setInDialog(false);
            reportManager.openReportGui(player);
            return;
        }

        // 2. Callback tombol "Simpan Pesan" dari Screen Dialog (/report submit_msg <teks...>)
        if (args.length >= 1 && args[0].equalsIgnoreCase("submit_msg")) {
            draft.setInDialog(false);
            if (args.length > 1) {
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
                draft.setMessage(message.isEmpty() ? null : message);
                if (!message.isEmpty()) {
                    player.sendMessage(ColorUtils.parse(ReportText.MSG_MESSAGE_SAVED));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
                }
            } else {
                draft.setMessage(null);
            }
            reportManager.openReportGui(player);
            return;
        }

        // 3. Quick report command: /report <pesan awal...>
        if (args.length > 0) {
            String initialMessage = String.join(" ", args).trim();
            if (!initialMessage.isBlank()) {
                draft.setMessage(initialMessage);
            }
        }

        reportManager.openReportGui(player);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        return List.of();
    }
}

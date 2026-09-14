package dev.slowy.core.commands;

import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.rtp.RtpManager;
import dev.slowy.core.rtp.RtpWorldType;
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
public final class RtpCommand implements SlowyBasicCommand {

    private final RtpManager rtpManager;

    public RtpCommand(RtpManager rtpManager) {
        this.rtpManager = Objects.requireNonNull(rtpManager, "rtpManager cannot be null");
    }

    @Override
    public String name() {
        return "rtp";
    }

    @Override
    public String description() {
        return "Random teleport to a safe location or open the RTP menu.";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.parse(CoreConfig.PLAYER_ONLY));
            return;
        }

        if (args.length > 0) {
            RtpWorldType target = RtpWorldType.fromString(args[0]);
            rtpManager.teleportRandomSafe(player, target);
            return;
        }

        if (CoreConfig.isHubWorld(player.getWorld())) {
            rtpManager.getRtpMenu().open(player);
        } else {
            RtpWorldType currentType = RtpWorldType.fromEnvironment(player.getWorld().getEnvironment());
            rtpManager.teleportRandomSafe(player, currentType);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            return List.of("world", "world_nether", "world_the_end").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }
}

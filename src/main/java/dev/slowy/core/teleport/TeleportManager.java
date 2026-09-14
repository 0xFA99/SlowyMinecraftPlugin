package dev.slowy.core.teleport;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class TeleportManager implements Lifecycle {

    private final SlowyCore plugin;
    private final Logger logger;

    // Incoming requests: Target UUID -> Map of Sender UUID to TeleportRequest
    private final Map<UUID, Map<UUID, TeleportRequest>> incomingRequests = new ConcurrentHashMap<>();

    // Outgoing requests: Sender UUID -> Target UUID
    private final Map<UUID, UUID> outgoingRequests = new ConcurrentHashMap<>();

    // Active warmups: Teleporting Player UUID -> TeleportWarmup
    private final Map<UUID, TeleportWarmup> activeWarmups = new ConcurrentHashMap<>();

    public TeleportManager(SlowyCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        logger.info("TeleportManager initialized (20s request expiry, 1s grace period, 3s coordinate lock warmup).");
    }

    public boolean isTeleporting(UUID uuid) {
        return activeWarmups.containsKey(uuid);
    }

    public @Nullable TeleportWarmup getWarmup(UUID uuid) {
        return activeWarmups.get(uuid);
    }

    public List<String> getPendingSenderNames(UUID receiverUuid) {
        Map<UUID, TeleportRequest> requests = incomingRequests.get(receiverUuid);
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<String> senders = new ArrayList<>();
        for (TeleportRequest req : requests.values()) {
            if (!req.isExpired()) {
                senders.add(req.senderName());
            }
        }
        return senders;
    }

    public boolean sendRequest(Player sender, Player target, TeleportType type) {
        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        if (senderUuid.equals(targetUuid)) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>✖ You cannot send a teleport request to yourself!</#FF0055>"));
            return false;
        }

        if (isTeleporting(senderUuid)) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>✖ You are currently teleporting! Please wait.</#FF0055>"));
            return false;
        }

        long expireMillis = System.currentTimeMillis() + (CoreConfig.TELEPORT_REQUEST_TIMEOUT_SECONDS * 1000L);
        TeleportRequest request = new TeleportRequest(
                senderUuid,
                sender.getName(),
                targetUuid,
                target.getName(),
                type,
                expireMillis
        );

        Map<UUID, TeleportRequest> targetMap = incomingRequests.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>());
        targetMap.put(senderUuid, request);
        outgoingRequests.put(senderUuid, targetUuid);

        // Build interactive message for target
        String requestTitle = type == TeleportType.TPA ? "TELEPORT REQUEST" : "TELEPORT HERE REQUEST";
        String requestBody = type == TeleportType.TPA
                ? "<#1DA1F2>" + sender.getName() + "</#1DA1F2> <#E0F8FF>has requested to teleport to you.</#E0F8FF>"
                : "<#1DA1F2>" + sender.getName() + "</#1DA1F2> <#E0F8FF>has requested you to teleport to them.</#E0F8FF>";

        Component acceptBtn = ColorUtils.parse("<#39FF14><bold>[ACCEPT]</bold></#39FF14>")
                .clickEvent(ClickEvent.runCommand("/tpaccept " + sender.getName()))
                .hoverEvent(HoverEvent.showText(ColorUtils.parse("<#39FF14>Click to accept teleport request</#39FF14>")));

        Component denyBtn = ColorUtils.parse("<#FF0055><bold>[DENY]</bold></#FF0055>")
                .clickEvent(ClickEvent.runCommand("/tpdeny " + sender.getName()))
                .hoverEvent(HoverEvent.showText(ColorUtils.parse("<#FF0055>Click to decline teleport request</#FF0055>")));

        target.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));
        target.sendMessage(ColorUtils.parse("<#00F5FF><bold>" + requestTitle + "</bold></#00F5FF>"));
        target.sendMessage(ColorUtils.parse(requestBody));
        target.sendMessage(Component.text("  ").append(acceptBtn).append(Component.text("   ")).append(denyBtn));
        target.sendMessage(ColorUtils.parse("<gray><i>Expires in 20 seconds. Type /tpaccept or /tpdeny</i></gray>"));
        target.sendMessage(ColorUtils.parse("<dark_gray>--------------------------------------------------</dark_gray>"));

        try {
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
        } catch (Throwable ignored) {}

        sender.sendMessage(ColorUtils.parse("<#39FF14>✔ Sent " + (type == TeleportType.TPA ? "teleport" : "teleport-here") + " request to <#00F5FF>" + target.getName() + "</#00F5FF>! <gray>(Expires in 20s)</gray></#39FF14>"));
        return true;
    }

    public boolean acceptRequest(Player receiver, @Nullable String senderNameQuery) {
        UUID receiverUuid = receiver.getUniqueId();
        Map<UUID, TeleportRequest> requests = incomingRequests.get(receiverUuid);

        if (requests == null || requests.isEmpty()) {
            receiver.sendMessage(ColorUtils.parse("<#FF0055>✖ You have no pending teleport requests.</#FF0055>"));
            return false;
        }

        TeleportRequest selected = null;
        if (senderNameQuery != null && !senderNameQuery.isBlank()) {
            String cleanQuery = senderNameQuery.trim();
            for (TeleportRequest req : requests.values()) {
                if (req.senderName().equalsIgnoreCase(cleanQuery)) {
                    selected = req;
                    break;
                }
            }
        } else {
            // Select most recent non-expired request
            long maxExpiry = -1L;
            for (TeleportRequest req : requests.values()) {
                if (!req.isExpired() && req.expireMillis() > maxExpiry) {
                    maxExpiry = req.expireMillis();
                    selected = req;
                }
            }
        }

        if (selected == null || selected.isExpired()) {
            receiver.sendMessage(ColorUtils.parse("<#FF0055>✖ Teleport request has expired or was not found.</#FF0055>"));
            return false;
        }

        Player sender = Bukkit.getPlayer(selected.senderUuid());
        if (sender == null || !sender.isOnline()) {
            receiver.sendMessage(ColorUtils.parse("<#FF0055>✖ " + selected.senderName() + " is no longer online.</#FF0055>"));
            requests.remove(selected.senderUuid());
            outgoingRequests.remove(selected.senderUuid());
            return false;
        }

        // Clean up request
        requests.remove(selected.senderUuid());
        outgoingRequests.remove(selected.senderUuid());

        // Determine moving player and destination player
        Player teleportingPlayer;
        Player destinationPlayer;

        if (selected.type() == TeleportType.TPA) {
            // /tpa: Sender moves to Receiver
            teleportingPlayer = sender;
            destinationPlayer = receiver;
        } else {
            // /tpahere: Receiver moves to Sender
            teleportingPlayer = receiver;
            destinationPlayer = sender;
        }

        UUID movingUuid = teleportingPlayer.getUniqueId();
        if (isTeleporting(movingUuid)) {
            receiver.sendMessage(ColorUtils.parse("<#FF0055>✖ " + teleportingPlayer.getName() + " is already teleporting!</#FF0055>"));
            return false;
        }

        long currentTick = plugin.getHeartbeatManager().getCurrentTick();
        TeleportWarmup warmup = new TeleportWarmup(movingUuid, destinationPlayer.getUniqueId(), currentTick);
        activeWarmups.put(movingUuid, warmup);

        receiver.sendMessage(ColorUtils.parse("<#39FF14>✔ Accepted teleport request from <#00F5FF>" + sender.getName() + "</#00F5FF>.</#39FF14>"));
        sender.sendMessage(ColorUtils.parse("<#39FF14>✔ <#00F5FF>" + receiver.getName() + "</#00F5FF> accepted your teleport request.</#39FF14>"));

        return true;
    }

    public boolean denyRequest(Player receiver, @Nullable String senderNameQuery) {
        UUID receiverUuid = receiver.getUniqueId();
        Map<UUID, TeleportRequest> requests = incomingRequests.get(receiverUuid);

        if (requests == null || requests.isEmpty()) {
            receiver.sendMessage(ColorUtils.parse("<#FF0055>✖ You have no pending teleport requests.</#FF0055>"));
            return false;
        }

        TeleportRequest selected = null;
        if (senderNameQuery != null && !senderNameQuery.isBlank()) {
            String cleanQuery = senderNameQuery.trim();
            for (TeleportRequest req : requests.values()) {
                if (req.senderName().equalsIgnoreCase(cleanQuery)) {
                    selected = req;
                    break;
                }
            }
        } else {
            selected = requests.values().stream().findFirst().orElse(null);
        }

        if (selected != null) {
            requests.remove(selected.senderUuid());
            outgoingRequests.remove(selected.senderUuid());

            receiver.sendMessage(ColorUtils.parse("<#FF7A00>✖ Denied teleport request from <#00F5FF>" + selected.senderName() + "</#00F5FF>.</#FF7A00>"));
            Player sender = Bukkit.getPlayer(selected.senderUuid());
            if (sender != null && sender.isOnline()) {
                sender.sendMessage(ColorUtils.parse("<#FF7A00>✖ " + receiver.getName() + " denied your teleport request.</#FF7A00>"));
            }
            return true;
        }

        receiver.sendMessage(ColorUtils.parse("<#FF0055>✖ No matching teleport request found.</#FF0055>"));
        return false;
    }

    public boolean cancelRequest(Player sender) {
        UUID senderUuid = sender.getUniqueId();
        UUID targetUuid = outgoingRequests.remove(senderUuid);

        if (targetUuid == null) {
            sender.sendMessage(ColorUtils.parse("<#FF0055>✖ You have no active outgoing teleport requests.</#FF0055>"));
            return false;
        }

        Map<UUID, TeleportRequest> targetMap = incomingRequests.get(targetUuid);
        if (targetMap != null) {
            targetMap.remove(senderUuid);
        }

        sender.sendMessage(ColorUtils.parse("<#FF7A00>✖ Cancelled your outgoing teleport request.</#FF7A00>"));
        return true;
    }

    public void cancelWarmup(UUID teleportingUuid, @Nullable String reason) {
        TeleportWarmup warmup = activeWarmups.remove(teleportingUuid);
        if (warmup != null) {
            Player player = Bukkit.getPlayer(teleportingUuid);
            if (player != null && player.isOnline()) {
                if (reason != null) {
                    player.sendActionBar(ColorUtils.parse(reason));
                    try {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    } catch (Throwable ignored) {}
                }
            }

            Player dest = Bukkit.getPlayer(warmup.getDestinationUuid());
            if (dest != null && dest.isOnline() && player != null) {
                dest.sendMessage(ColorUtils.parse("<#FF7A00>⚠ Teleportation for <#00F5FF>" + player.getName() + "</#00F5FF> was cancelled.</#FF7A00>"));
            }
        }
    }

    public void onPlayerDamage(Player victim) {
        if (isTeleporting(victim.getUniqueId())) {
            cancelWarmup(victim.getUniqueId(), "<#FF0055>✖ Teleport cancelled: In combat / took damage!</#FF0055>");
        }
    }

    public void onPlayerAttack(Player attacker) {
        if (isTeleporting(attacker.getUniqueId())) {
            cancelWarmup(attacker.getUniqueId(), "<#FF0055>✖ Teleport cancelled: In combat / attacked an entity!</#FF0055>");
        }
    }

    /**
     * Master heartbeat tick handler:
     * - Phase 1: 0..19 ticks (1s grace period for deceleration)
     * - Phase 2: 20..79 ticks (3s countdown with locked anchor coordinates)
     * - Phase 3: >= 80 ticks (safe async teleportation)
     */
    public void tick(long currentTick) {
        // 1. Tick active warmups
        if (!activeWarmups.isEmpty()) {
            Iterator<Map.Entry<UUID, TeleportWarmup>> it = activeWarmups.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, TeleportWarmup> entry = it.next();
                UUID teleportingUuid = entry.getKey();
                TeleportWarmup warmup = entry.getValue();

                Player teleporting = Bukkit.getPlayer(teleportingUuid);
                Player destination = Bukkit.getPlayer(warmup.getDestinationUuid());

                if (teleporting == null || !teleporting.isOnline() || teleporting.isDead()) {
                    it.remove();
                    continue;
                }

                if (destination == null || !destination.isOnline() || destination.isDead()) {
                    it.remove();
                    teleporting.sendActionBar(ColorUtils.parse("<#FF0055>✖ Teleport cancelled: Destination player is no longer available.</#FF0055>"));
                    try {
                        teleporting.playSound(teleporting.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    } catch (Throwable ignored) {}
                    continue;
                }

                long elapsed = currentTick - warmup.getStartTick();

                // Phase 1: Grace period (0 to 19 ticks / 0.0s to 1.0s)
                if (elapsed < 20L) {
                    if (warmup.getLastActionbarSecond() != 4) {
                        warmup.setLastActionbarSecond(4);
                        teleporting.sendActionBar(ColorUtils.parse("<#FFE600>Teleporting in <#FF7A00>4s</#FF7A00>... Slow down and prepare!</#FFE600>"));
                        try {
                            teleporting.playSound(teleporting.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                        } catch (Throwable ignored) {}
                    }
                } else if (elapsed < 80L) {
                    // Phase 2: Lock coordinates & 3-second countdown (20 to 79 ticks)
                    if (warmup.getAnchorLocation() == null) {
                        // Exactly at 1 second, lock coordinates as Anchor Point
                        warmup.setAnchorLocation(teleporting.getLocation().clone());
                    }

                    Location anchor = warmup.getAnchorLocation();
                    if (!teleporting.getWorld().equals(anchor.getWorld())) {
                        it.remove();
                        teleporting.sendActionBar(ColorUtils.parse("<#FF0055>✖ Teleport cancelled: You changed worlds!</#FF0055>"));
                        try {
                            teleporting.playSound(teleporting.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                        } catch (Throwable ignored) {}
                        continue;
                    }

                    double dx = teleporting.getX() - anchor.getX();
                    double dy = teleporting.getY() - anchor.getY();
                    double dz = teleporting.getZ() - anchor.getZ();
                    double distSq = (dx * dx) + (dy * dy) + (dz * dz);

                    if (distSq > CoreConfig.TELEPORT_MAX_MOVE_DISTANCE_SQUARED) {
                        it.remove();
                        teleporting.sendActionBar(ColorUtils.parse("<#FF0055>✖ Teleport cancelled: You moved!</#FF0055>"));
                        try {
                            teleporting.playSound(teleporting.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                        } catch (Throwable ignored) {}
                        continue;
                    }

                    int remainingSec = (int) Math.ceil((80.0 - elapsed) / 20.0);
                    if (warmup.getLastActionbarSecond() != remainingSec && remainingSec >= 1) {
                        warmup.setLastActionbarSecond(remainingSec);
                        String colorTag = switch (remainingSec) {
                            case 3 -> "<#39FF14>";
                            case 2 -> "<#FFE600>";
                            default -> "<#FF7A00>";
                        };
                        teleporting.sendActionBar(ColorUtils.parse("<#FFE600>Teleporting in " + colorTag + remainingSec + "s<#FFE600>... Do not move!</#FFE600>"));
                        try {
                            teleporting.playSound(teleporting.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                        } catch (Throwable ignored) {}
                    }
                } else {
                    // Phase 3: Execute safe teleportation (Detik ke-4)
                    it.remove();
                    Location destLoc = destination.getLocation();
                    teleporting.teleportAsync(destLoc).thenRun(() -> {
                        teleporting.sendActionBar(ColorUtils.parse("<#39FF14>✔ Teleported to <#00F5FF>" + destination.getName() + "</#00F5FF>!</#39FF14>"));
                        destination.sendMessage(ColorUtils.parse("<#39FF14>✔ <#00F5FF>" + teleporting.getName() + "</#00F5FF> has teleported to you.</#39FF14>"));
                        try {
                            teleporting.playSound(teleporting.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                            destination.playSound(destination.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
                        } catch (Throwable ignored) {}
                    });
                }
            }
        }

        // 2. Clean up expired requests every 20 ticks (1 second)
        if (currentTick % 20 == 0) {
            incomingRequests.entrySet().removeIf(entry -> {
                entry.getValue().values().removeIf(TeleportRequest::isExpired);
                return entry.getValue().isEmpty();
            });
            outgoingRequests.entrySet().removeIf(entry -> {
                Map<UUID, TeleportRequest> targetMap = incomingRequests.get(entry.getValue());
                return targetMap == null || !targetMap.containsKey(entry.getKey());
            });
        }
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        cancelWarmup(uuid, null);
        incomingRequests.remove(uuid);
        outgoingRequests.remove(uuid);
    }

    @Override
    public void onDisable() {
        activeWarmups.clear();
        incomingRequests.clear();
        outgoingRequests.clear();
        logger.info("TeleportManager disabled.");
    }
}

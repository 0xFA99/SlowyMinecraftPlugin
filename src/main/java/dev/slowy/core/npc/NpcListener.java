package dev.slowy.core.npc;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class NpcListener implements Listener {

    private final NpcManager npcManager;
    private final NamespacedKey npcKey;
    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();

    private static final long CLICK_COOLDOWN_MS = 500L;

    public NpcListener(NpcManager npcManager) {
        this.npcManager = Objects.requireNonNull(npcManager, "npcManager cannot be null");
        this.npcKey = npcManager.getNpcKey();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        String npcId = interaction.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (npcId == null) return;

        event.setCancelled(true);
        handleClick(event.getPlayer(), npcId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction interaction)) return;

        String npcId = interaction.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (npcId == null) return;

        // Ensure NPC cannot take damage or be knocked back
        event.setCancelled(true);

        if (event.getDamager() instanceof Player player) {
            handleClick(player, npcId);
        }
    }

    private void handleClick(Player player, String npcId) {
        long now = System.currentTimeMillis();
        Long last = clickCooldowns.get(player.getUniqueId());
        if (last != null && now - last < CLICK_COOLDOWN_MS) {
            return;
        }
        clickCooldowns.put(player.getUniqueId(), now);

        NpcDefinition npc = npcManager.getNpc(npcId);
        if (npc == null) return;

        if ("daily".equalsIgnoreCase(npcId)) {
            dev.slowy.core.SlowyCore.getInstance().getDailyManager().claimDaily(player);
            return;
        }

        String cmd = npc.getCommand();
        if (cmd == null || cmd.isBlank()) return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);

        String trimmed = cmd.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("console:")) {
            String consoleCmd = trimmed.substring(8).trim().replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd);
        } else {
            String playerCmd = trimmed.replace("{player}", player.getName());
            if (playerCmd.startsWith("/")) {
                playerCmd = playerCmd.substring(1);
            }
            player.performCommand(playerCmd);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        npcManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clickCooldowns.remove(player.getUniqueId());
        npcManager.onPlayerQuit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        npcManager.onPlayerChangedWorld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        npcManager.onChunkLoad(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }
}

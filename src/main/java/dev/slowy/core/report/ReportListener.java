package dev.slowy.core.report;

import dev.slowy.core.SlowyCore;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/**
 * Event handling untuk Hopper GUI pelaporan player.
 */
@NullMarked
public final class ReportListener implements Listener {

    private final SlowyCore plugin;
    private final ReportManager reportManager;

    public ReportListener(SlowyCore plugin, ReportManager reportManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.reportManager = Objects.requireNonNull(reportManager, "reportManager cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ReportHolder holder)) return;

        // Kunci semua interaksi di dalam GUI Hopper
        event.setCancelled(true);

        // Hanya proses klik di top inventory (Hopper GUI 5 slot: 0-4)
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ReportDraft draft = holder.getDraft();
        int slot = event.getRawSlot();

        switch (slot) {
            case 0 -> { // [ Slot 0 ] - Batal
                reportManager.cancelReport(player);
            }
            case 1 -> { // [ Slot 1 ] - Kategori Masalah (Cycle on Click)
                draft.setCategory(draft.getCategory().next());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                reportManager.renderGui(event.getView().getTopInventory(), draft);
            }
            case 2 -> { // [ Slot 2 ] - Isi Pesan (Buka Paper Custom Screen Dialog)
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                reportManager.openMessageDialog(player, draft);
            }
            case 3 -> { // [ Slot 3 ] - Tingkat Urgensi (Cycle on Click)
                draft.setUrgency(draft.getUrgency().next());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                reportManager.renderGui(event.getView().getTopInventory(), draft);
            }
            case 4 -> { // [ Slot 4 ] - Kirim Laporan
                reportManager.submitReport(player, draft);
            }
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ReportHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ReportHolder holder)) return;

        ReportDraft draft = holder.getDraft();
        // Bersihkan cache draf jika player menutup form tanpa sedang membuka Screen Dialog
        if (!draft.isInDialog()) {
            reportManager.removeDraft(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        reportManager.removeDraft(event.getPlayer().getUniqueId());
    }
}

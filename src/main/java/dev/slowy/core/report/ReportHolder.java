package dev.slowy.core.report;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Custom InventoryHolder untuk Hopper GUI 5-Slot sistem laporan.
 */
@NullMarked
public final class ReportHolder implements InventoryHolder {

    private final ReportDraft draft;
    private @Nullable Inventory inventory;

    public ReportHolder(ReportDraft draft) {
        this.draft = draft;
    }

    public void setInventory(@Nullable Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory != null ? inventory : Bukkit.createInventory(this, InventoryType.HOPPER);
    }

    public ReportDraft getDraft() {
        return draft;
    }
}

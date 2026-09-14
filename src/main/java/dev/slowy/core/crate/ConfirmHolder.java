package dev.slowy.core.crate;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@NullMarked
public final class ConfirmHolder implements InventoryHolder {

    private final CrateDefinition crate;
    private final CrateReward selectedReward;

    public ConfirmHolder(CrateDefinition crate, CrateReward selectedReward) {
        this.crate = Objects.requireNonNull(crate, "crate cannot be null");
        this.selectedReward = Objects.requireNonNull(selectedReward, "selectedReward cannot be null");
    }

    public CrateDefinition getCrate() {
        return crate;
    }

    public CrateReward getSelectedReward() {
        return selectedReward;
    }

    @Override
    public @Nullable Inventory getInventory() {
        return null;
    }
}

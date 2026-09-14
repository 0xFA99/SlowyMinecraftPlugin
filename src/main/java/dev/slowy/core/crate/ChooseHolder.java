package dev.slowy.core.crate;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@NullMarked
public final class ChooseHolder implements InventoryHolder {

    private final CrateDefinition crate;
    private final Map<Integer, CrateReward> slotToReward = new HashMap<>();

    public ChooseHolder(CrateDefinition crate) {
        this.crate = Objects.requireNonNull(crate, "crate cannot be null");
    }

    public CrateDefinition getCrate() {
        return crate;
    }

    public void registerSlot(int slot, CrateReward reward) {
        slotToReward.put(slot, reward);
    }

    public @Nullable CrateReward getReward(int slot) {
        return slotToReward.get(slot);
    }

    @Override
    public @Nullable Inventory getInventory() {
        return null;
    }
}

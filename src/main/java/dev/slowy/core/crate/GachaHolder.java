package dev.slowy.core.crate;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@NullMarked
public record GachaHolder(CrateDefinition crate, CrateReward winningReward) implements InventoryHolder {

    public GachaHolder {
        Objects.requireNonNull(crate, "crate cannot be null");
        Objects.requireNonNull(winningReward, "winningReward cannot be null");
    }

    @Override
    public @Nullable Inventory getInventory() {
        return null;
    }
}

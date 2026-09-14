package dev.slowy.core.crate;

import org.bukkit.block.Block;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;

@NullMarked
public record CrateBlockKey(String world, int x, int y, int z) {

    public CrateBlockKey {
        Objects.requireNonNull(world, "world cannot be null");
    }

    public static CrateBlockKey fromBlock(Block block) {
        return new CrateBlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public String toKeyString() {
        return world + ':' + x + ':' + y + ':' + z;
    }
}

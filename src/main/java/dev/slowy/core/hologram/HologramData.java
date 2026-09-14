package dev.slowy.core.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record HologramData(
        String id,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        @Nullable UUID entityUuid
) {
    public @Nullable Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static HologramData fromLocation(String id, Location loc, @Nullable UUID entityUuid) {
        return new HologramData(
                id.toLowerCase(java.util.Locale.ROOT),
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                entityUuid
        );
    }
}

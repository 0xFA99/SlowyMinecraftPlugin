package dev.slowy.core.teleport;

import org.bukkit.Location;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@NullMarked
public final class TeleportWarmup {

    private final UUID teleportingUuid;
    private final UUID destinationUuid;
    private final long startTick;
    private @Nullable Location anchorLocation;
    private int lastActionbarSecond = -1;

    public TeleportWarmup(UUID teleportingUuid, UUID destinationUuid, long startTick) {
        this.teleportingUuid = teleportingUuid;
        this.destinationUuid = destinationUuid;
        this.startTick = startTick;
    }

    public UUID getTeleportingUuid() {
        return teleportingUuid;
    }

    public UUID getDestinationUuid() {
        return destinationUuid;
    }

    public long getStartTick() {
        return startTick;
    }

    public @Nullable Location getAnchorLocation() {
        return anchorLocation;
    }

    public void setAnchorLocation(@Nullable Location anchorLocation) {
        this.anchorLocation = anchorLocation;
    }

    public int getLastActionbarSecond() {
        return lastActionbarSecond;
    }

    public void setLastActionbarSecond(int lastActionbarSecond) {
        this.lastActionbarSecond = lastActionbarSecond;
    }
}

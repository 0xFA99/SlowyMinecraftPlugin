package dev.slowy.core.rtp;

import dev.slowy.core.config.CoreConfig;
import org.bukkit.World;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public enum RtpWorldType {
    OVERWORLD(
            "world", "Overworld", "world", "portal_overworld",
            CoreConfig.RTP_OVERWORLD_PORTAL_X, CoreConfig.RTP_OVERWORLD_PORTAL_Y, CoreConfig.RTP_OVERWORLD_PORTAL_Z, CoreConfig.RTP_OVERWORLD_PORTAL_YAW,
            CoreConfig.RTP_OVERWORLD_MIN_RADIUS, CoreConfig.RTP_OVERWORLD_MAX_RADIUS
    ),
    NETHER(
            "world_nether", "Nether", "world_nether", "portal_nether",
            CoreConfig.RTP_NETHER_PORTAL_X, CoreConfig.RTP_NETHER_PORTAL_Y, CoreConfig.RTP_NETHER_PORTAL_Z, CoreConfig.RTP_NETHER_PORTAL_YAW,
            CoreConfig.RTP_NETHER_MIN_RADIUS, CoreConfig.RTP_NETHER_MAX_RADIUS
    ),
    THE_END(
            "world_the_end", "The End", "world_the_end", "portal_the_end",
            CoreConfig.RTP_END_PORTAL_X, CoreConfig.RTP_END_PORTAL_Y, CoreConfig.RTP_END_PORTAL_Z, CoreConfig.RTP_END_PORTAL_YAW,
            CoreConfig.RTP_END_MIN_RADIUS, CoreConfig.RTP_END_MAX_RADIUS
    );

    private final String id;
    private final String displayName;
    private final String worldKey;
    private final String hologramId;
    private final double portalX;
    private final double portalY;
    private final double portalZ;
    private final float portalYaw;
    private final int minRadius;
    private final int maxRadius;

    RtpWorldType(String id, String displayName, String worldKey, String hologramId,
                 double portalX, double portalY, double portalZ, float portalYaw,
                 int minRadius, int maxRadius) {
        this.id = id;
        this.displayName = displayName;
        this.worldKey = worldKey;
        this.hologramId = hologramId;
        this.portalX = portalX;
        this.portalY = portalY;
        this.portalZ = portalZ;
        this.portalYaw = portalYaw;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getWorldKey() { return worldKey; }
    public String getHologramId() { return hologramId; }
    public double getPortalX() { return portalX; }
    public double getPortalY() { return portalY; }
    public double getPortalZ() { return portalZ; }
    public float getPortalYaw() { return portalYaw; }
    public int getMinRadius() { return minRadius; }
    public int getMaxRadius() { return maxRadius; }

    public static RtpWorldType fromString(@Nullable String raw) {
        if (raw == null) return OVERWORLD;
        for (RtpWorldType type : values()) {
            if (type.getId().equalsIgnoreCase(raw) || type.name().equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return OVERWORLD;
    }

    public static RtpWorldType fromEnvironment(World.Environment env) {
        if (env == World.Environment.NETHER) return NETHER;
        if (env == World.Environment.THE_END) return THE_END;
        return OVERWORLD;
    }
}

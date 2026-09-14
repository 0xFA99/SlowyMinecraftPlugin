package dev.slowy.core.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

@NullMarked
public final class NpcDefinition {

    private final String id;
    private final UUID uuid;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private @Nullable String skinName;
    private @Nullable String skinValue;
    private @Nullable String skinSignature;
    private @Nullable String command;
    private boolean lookAtPlayer;

    public NpcDefinition(String id, Location location) {
        this.id = Objects.requireNonNull(id, "id cannot be null").toLowerCase();
        this.uuid = UUID.randomUUID();
        this.worldName = location.getWorld() != null ? location.getWorld().getName() : "world";
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.lookAtPlayer = true;
    }

    public NpcDefinition(String id, UUID uuid, String worldName, double x, double y, double z,
                         float yaw, float pitch, @Nullable String skinName, @Nullable String skinValue,
                         @Nullable String skinSignature, @Nullable String command, boolean lookAtPlayer) {
        this.id = Objects.requireNonNull(id, "id cannot be null").toLowerCase();
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.skinName = skinName;
        this.skinValue = skinValue;
        this.skinSignature = skinSignature;
        this.command = command;
        this.lookAtPlayer = lookAtPlayer;
    }

    public String getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public @Nullable Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void setLocation(Location loc) {
        this.worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }

    public @Nullable String getSkinName() {
        return skinName;
    }

    public void setSkinName(@Nullable String skinName) {
        this.skinName = skinName;
    }

    public @Nullable String getSkinValue() {
        return skinValue;
    }

    public void setSkinValue(@Nullable String skinValue) {
        this.skinValue = skinValue;
    }

    public @Nullable String getSkinSignature() {
        return skinSignature;
    }

    public void setSkinSignature(@Nullable String skinSignature) {
        this.skinSignature = skinSignature;
    }

    public boolean hasValidSkin() {
        return skinValue != null && !skinValue.isBlank() && skinSignature != null && !skinSignature.isBlank();
    }

    public @Nullable String getCommand() {
        return command;
    }

    public void setCommand(@Nullable String command) {
        this.command = command;
    }

    public boolean isLookAtPlayer() {
        return lookAtPlayer;
    }

    public void setLookAtPlayer(boolean lookAtPlayer) {
        this.lookAtPlayer = lookAtPlayer;
    }

}

package dev.slowy.core.clearlag;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;

@NullMarked
public final class ClearLagManager implements Lifecycle {

    private final SlowyCore plugin;
    private @Nullable ScheduledTask currentTask;

    private boolean enabled = true;
    private int intervalMinutes = 5;
    private boolean broadcastWarnings = true;
    private boolean clearDroppedItems = true;
    private boolean clearMonsters = false;
    private boolean clearAnimals = false;
    private boolean clearProjectiles = true;

    private boolean excludeNamed = true;
    private boolean excludeTamed = true;

    // Entity types yang mutlak tidak boleh dihapus
    private static final Set<EntityType> EXCLUDED_TYPES = EnumSet.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.WARDEN,
            EntityType.ELDER_GUARDIAN,
            EntityType.IRON_GOLEM,
            EntityType.SNOW_GOLEM,
            EntityType.ALLAY,
            EntityType.VILLAGER,
            EntityType.WANDERING_TRADER,
            EntityType.ARMOR_STAND,
            EntityType.ITEM_FRAME,
            EntityType.GLOW_ITEM_FRAME,
            EntityType.PAINTING,
            EntityType.BLOCK_DISPLAY,
            EntityType.ITEM_DISPLAY,
            EntityType.TEXT_DISPLAY,
            EntityType.INTERACTION
    );

    private static final Set<Material> VALUABLE_MATERIALS = EnumSet.of(
            Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE, Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
            Material.NETHERITE_SCRAP,
            Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS, Material.DIAMOND_BLOCK,
            Material.ELYTRA, Material.BEACON, Material.NETHER_STAR, Material.TOTEM_OF_UNDYING,
            Material.ENCHANTED_GOLDEN_APPLE, Material.SPAWNER
    );

    private final String msgCountdown = "<yellow>Entities will be cleared in <white><seconds>s</white></yellow>";
    private final String msgSuccess = "<green>✔ Cleared <white><total></white> entities.</green>";

    private int secondsLeft;

    public ClearLagManager(SlowyCore plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.secondsLeft = intervalMinutes * 60;
        startTask();
    }

    public final synchronized void startTask() {
        stopTask();
        if (!enabled) return;

        this.secondsLeft = intervalMinutes * 60;

        // Paper Async Scheduler: Timer countdown tidak membebani main thread
        this.currentTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                _ -> tickCountdown(),
                1,
                1,
                TimeUnit.SECONDS
        );
    }

    public synchronized void stopTask() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
    }

    private void tickCountdown() {
        secondsLeft--;

        // O(1) JVM lookupswitch (Modern Java)
        switch (secondsLeft) {
            case 60, 30, 15, 10, 5, 4, 3, 2, 1 -> broadcastCountdown(secondsLeft);
            case 0 -> {
                // Jalankan pembersihan entitas di Global Region (Folia/Paper safe)
                Bukkit.getGlobalRegionScheduler().run(plugin, _ -> clearEntities());
                secondsLeft = intervalMinutes * 60;
            }
            default -> {}
        }
    }

    public boolean isWorldAllowed(@Nullable World world) {
        if (world == null) return false;
        String name = world.getName().toLowerCase(Locale.ROOT);
        if (name.contains("spawn") || name.contains("afk") || name.contains("duel")) {
            return false;
        }
        return name.contains("overworld") || name.equals("world")
                || name.contains("nether")
                || name.contains("void") || name.contains("end");
    }

    public int clearEntities() {
        int count = 0;

        // Targeted entity query: hanya ambil kategori yang aktif di-clear
        List<Class<? extends Entity>> targets = new ArrayList<>(4);
        if (clearDroppedItems) targets.add(Item.class);
        if (clearProjectiles) targets.add(Projectile.class);
        if (clearAnimals) targets.add(Animals.class);
        if (clearMonsters) targets.add(Enemy.class);

        if (targets.isEmpty()) return 0;

        for (World world : Bukkit.getWorlds()) {
            if (!isWorldAllowed(world)) continue;

            for (Class<? extends Entity> targetClass : targets) {
                // Mengambil spesifik class, melewati alokasi ribuan ArmorStand/Display/Marker
                for (Entity entity : world.getEntitiesByClass(targetClass)) {
                    if (shouldRemove(entity)) {
                        entity.remove();
                        count++;
                    }
                }
            }
        }

        broadcastSuccess(count);
        return count;
    }

    public boolean shouldRemove(Entity entity) {
        // Fast-path exclusion
        if (EXCLUDED_TYPES.contains(entity.getType())) return false;
        if (excludeNamed && entity.customName() != null) return false;

        if (excludeTamed && entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }

        // Cek passenger
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player) return false;
        }

        // Pattern Matching Switch (Java 21+)
        return switch (entity) {
            case Item itemEntity -> shouldRemoveItem(itemEntity.getItemStack());
            case Projectile _ -> clearProjectiles;
            case Animals _ -> clearAnimals;
            case Enemy _ -> clearMonsters; // Enemy mencakup Monster, Slime, Ghast, Phantom
            default -> false;
        };
    }

    private boolean shouldRemoveItem(ItemStack stack) {
        if (!clearDroppedItems) return false;

        Material mat = stack.getType();
        if (VALUABLE_MATERIALS.contains(mat)) return false;

        // Paper Tag API: jauh lebih cepat dibanding mat.name().endsWith(...)
        if (Tag.SHULKER_BOXES.isTagged(mat)) return false;

        if (stack.hasItemMeta()) {
            var meta = stack.getItemMeta();
            if (meta != null && (meta.hasDisplayName() || !meta.getPersistentDataContainer().isEmpty())) {
                return false;
            }
        }
        return true;
    }

    public void broadcastCountdown(int seconds) {
        if (!broadcastWarnings) return;

        // MiniMessage Placeholder template (tanpa String.replace manual berulang)
        var comp = MiniMessage.miniMessage().deserialize(
                msgCountdown,
                Placeholder.unparsed("seconds", String.valueOf(seconds))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isWorldAllowed(p.getWorld())) continue;
            p.sendActionBar(comp);
            if (seconds <= 5) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.2f);
            }
        }
    }

    public void broadcastSuccess(int total) {
        var comp = MiniMessage.miniMessage().deserialize(
                msgSuccess,
                Placeholder.unparsed("total", String.valueOf(total))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isWorldAllowed(p.getWorld())) continue;
            p.sendActionBar(comp);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
        }
    }

    public EntityCounts countEntities() {
        int totalItems = 0;
        int totalMonsters = 0;
        int totalAnimals = 0;
        int totalOther = 0;

        for (World world : Bukkit.getWorlds()) {
            if (!isWorldAllowed(world)) continue;

            for (Entity e : world.getEntities()) {
                switch (e) {
                    case Player _ -> {}
                    case Item _ -> totalItems++;
                    case Enemy _ -> totalMonsters++;
                    case Animals _ -> totalAnimals++;
                    default -> totalOther++;
                }
            }
        }

        return new EntityCounts(totalItems, totalMonsters, totalAnimals, totalOther);
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    @Override
    public void onDisable() {
        stopTask();
    }

    @Override
    public void onReload() {
        startTask();
    }
}

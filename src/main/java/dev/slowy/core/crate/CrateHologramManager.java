package dev.slowy.core.crate;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.utils.ColorUtils;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.*;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.NullMarked;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class CrateHologramManager {

    private static final double VIEW_DISTANCE_SQ = 32.0 * 32.0;
    private static final EntityDataAccessor<net.minecraft.network.chat.Component> TEXT_ACCESSOR = resolveTextAccessor();

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<net.minecraft.network.chat.Component> resolveTextAccessor() {
        try {
            Field field = net.minecraft.world.entity.Display.TextDisplay.class.getDeclaredField("DATA_TEXT_ID");
            var lookup = MethodHandles.privateLookupIn(net.minecraft.world.entity.Display.TextDisplay.class, MethodHandles.lookup());
            var varHandle = lookup.unreflectGetter(field);
            return (EntityDataAccessor<net.minecraft.network.chat.Component>) varHandle.invokeExact();
        } catch (Throwable t) {
            throw new ExceptionInInitializerError("Failed resolving TextDisplay.DATA_TEXT_ID: " + t.getMessage());
        }
    }

    private final SlowyCore plugin;
    private final CrateManager crateManager;
    private final NamespacedKey holoKey;

    private final Map<CrateBlockKey, TextDisplay> topHolograms = new ConcurrentHashMap<>();
    private final Map<CrateBlockKey, TextDisplay> bottomHolograms = new ConcurrentHashMap<>();
    private final Map<UUID, Map<CrateBlockKey, String>> lastSentKeyText = new ConcurrentHashMap<>();

    public CrateHologramManager(SlowyCore plugin, CrateManager crateManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.crateManager = Objects.requireNonNull(crateManager, "crateManager cannot be null");
        this.holoKey = new NamespacedKey(plugin, "crate_holo");
    }

    public void tick(long tick) {
        if (!plugin.isEnabled() || tick % 40 != 15) return;

        for (Map.Entry<CrateBlockKey, String> entry : crateManager.getBoundBlocks().entrySet()) {
            CrateBlockKey blockKey = entry.getKey();
            CrateDefinition crate = crateManager.getCrate(entry.getValue());
            if (crate == null) continue;

            World world = Bukkit.getWorld(blockKey.world());
            if (world == null) continue;

            Location blockLoc = new Location(world, blockKey.x() + 0.5, blockKey.y(), blockKey.z() + 0.5);

            plugin.getServer().getRegionScheduler().execute(plugin, blockLoc, () -> {
                if (!plugin.isEnabled()) return;
                getOrSpawnTopHologram(blockKey, crate, world);
                getOrSpawnBottomHologram(blockKey, crate, world);

                if (CoreConfig.CRATE_PARTICLES_ENABLED) {
                    world.spawnParticle(Particle.ENCHANT, blockLoc.clone().add(0, 0.6, 0), 4, 0.25, 0.25, 0.25, 0.05);
                }
            });
        }

        // Folia Safe: Player location and packets MUST run on player's scheduler
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> updateForPlayer(player), null);
        }
    }

    public TextDisplay getOrSpawnTopHologram(CrateBlockKey key, CrateDefinition crate, World world) {
        TextDisplay existing = topHolograms.get(key);
        if (existing != null && existing.isValid()) {
            return existing;
        }

        Location loc = new Location(world, key.x() + 0.5, key.y() + CoreConfig.CRATE_HOLO_TOP_OFFSET_Y, key.z() + 0.5);
        
        // Bersihkan sisa entitas duplicate (ghost) di titik tersebut
        cleanupGhostEntities(world, loc, key.toKeyString() + "_top");

        String tag = crate.getOpenType() == CrateDefinition.OpenType.CHOOSE_ONE ? "<#FFE600>ᴄʜᴏᴏꜱᴇ ᴏɴᴇ</#FFE600>" : "<#39FF14>ɢᴀᴄʜᴀ</#39FF14>";
        Component parsed = ColorUtils.parse(tag + "\n" + crate.getDisplayName());

        TextDisplay display = world.spawn(loc, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.getPersistentDataContainer().set(holoKey, PersistentDataType.STRING, key.toKeyString() + "_top");
            entity.text(parsed);
        });

        topHolograms.put(key, display);
        return display;
    }

    public TextDisplay getOrSpawnBottomHologram(CrateBlockKey key, CrateDefinition crate, World world) {
        TextDisplay existing = bottomHolograms.get(key);
        if (existing != null && existing.isValid()) {
            return existing;
        }

        Location loc = new Location(world, key.x() + 0.5, key.y() + CoreConfig.CRATE_HOLO_BOTTOM_OFFSET_Y, key.z() + 0.5);
        
        // Bersihkan sisa entitas duplicate (ghost) di titik tersebut
        cleanupGhostEntities(world, loc, key.toKeyString() + "_bottom");

        // PENTING: Teks default diisi kosong (Component.empty()) agar server tidak merender
        // teks "0 Keys" statis yang bertabrakan dengan custom packet per-pemain!
        TextDisplay display = world.spawn(loc, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.getPersistentDataContainer().set(holoKey, PersistentDataType.STRING, key.toKeyString() + "_bottom");
            entity.text(Component.empty());
        });

        bottomHolograms.put(key, display);

        // Reset cache packet teks pemain untuk blok ini agar dipaksa render ulang
        for (Map<CrateBlockKey, String> cache : lastSentKeyText.values()) {
            cache.remove(key);
        }

        return display;
    }

    private void cleanupGhostEntities(World world, Location loc, String expectedPdcTag) {
        for (Entity nearby : world.getNearbyEntities(loc, 1.0, 1.0, 1.0)) {
            if (nearby instanceof TextDisplay textDisplay) {
                String tag = textDisplay.getPersistentDataContainer().get(holoKey, PersistentDataType.STRING);
                if (expectedPdcTag.equals(tag)) {
                    textDisplay.remove();
                }
            }
        }
    }

    public void updateForPlayer(Player player) {
        if (!player.isOnline()) return;

        World playerWorld = player.getWorld();
        Location pLoc = player.getLocation();

        for (Map.Entry<CrateBlockKey, String> entry : crateManager.getBoundBlocks().entrySet()) {
            CrateBlockKey blockKey = entry.getKey();
            if (!blockKey.world().equalsIgnoreCase(playerWorld.getName())) continue;

            double dx = blockKey.x() + 0.5 - pLoc.getX();
            double dy = blockKey.y() - pLoc.getY();
            double dz = blockKey.z() + 0.5 - pLoc.getZ();
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);

            if (distSq > VIEW_DISTANCE_SQ) continue;

            TextDisplay bottom = bottomHolograms.get(blockKey);
            if (bottom == null || !bottom.isValid()) continue;

            CrateDefinition crate = crateManager.getCrate(entry.getValue());
            if (crate == null) continue;

            int keys = crateManager.getKeyBalance(player.getUniqueId(), crate.getId());
            String text = "<" + crate.getColorHex() + ">" + keys + "</" + crate.getColorHex() + "> <#E0F8FF>" + (keys == 1 ? "Key" : "Keys") + "</#E0F8FF>";

            sendCustomTextToPlayer(player, blockKey, bottom, text);
        }
    }

    private void sendCustomTextToPlayer(Player player, CrateBlockKey crateKey, TextDisplay display, String content) {
        Map<CrateBlockKey, String> playerCache = lastSentKeyText.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        if (content.equals(playerCache.get(crateKey))) return;
        playerCache.put(crateKey, content);

        net.minecraft.network.chat.Component nmsComp = PaperAdventure.asVanilla(ColorUtils.parse(content));
        var dataValue = SynchedEntityData.DataValue.create(TEXT_ACCESSOR, nmsComp);
        var packet = new ClientboundSetEntityDataPacket(display.getEntityId(), List.of(dataValue));

        ((CraftPlayer) player).getHandle().connection.send(packet);
    }

    public void onBlockBound(CrateBlockKey key, CrateDefinition crate) {
        World world = Bukkit.getWorld(key.world());
        if (world != null) {
            Location loc = new Location(world, key.x() + 0.5, key.y(), key.z() + 0.5);
            plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                getOrSpawnTopHologram(key, crate, world);
                getOrSpawnBottomHologram(key, crate, world);
            });
        }
    }

    public void onBlockUnbound(CrateBlockKey key) {
        TextDisplay top = topHolograms.remove(key);
        if (top != null) top.remove();
        TextDisplay bottom = bottomHolograms.remove(key);
        if (bottom != null) bottom.remove();
        for (Map<CrateBlockKey, String> cache : lastSentKeyText.values()) {
            cache.remove(key);
        }
    }

    public void despawnAll() {
        topHolograms.values().removeIf(td -> { td.remove(); return true; });
        bottomHolograms.values().removeIf(td -> { td.remove(); return true; });
        lastSentKeyText.clear();
    }
}

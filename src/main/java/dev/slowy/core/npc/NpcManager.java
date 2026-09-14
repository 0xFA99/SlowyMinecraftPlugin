package dev.slowy.core.npc;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.skin.SkinFetcher;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.utils.ColorUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@NullMarked
public final class NpcManager implements Lifecycle {

    private final SlowyCore plugin;
    private final Logger logger;
    private final DatabaseManager databaseManager;
    private final NamespacedKey npcKey;

    private final Map<String, NpcDefinition> npcs = new ConcurrentHashMap<>();
    private final Map<String, ServerPlayer> virtualEntities = new ConcurrentHashMap<>();
    private final Map<String, Interaction> hitboxes = new ConcurrentHashMap<>();

    private final Map<UUID, Set<String>> visibleToPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Boolean>> playerLookingState = new ConcurrentHashMap<>();

    private final double visDistSq = 48.0 * 48.0;
    private final double despawnDistSq = 56.0 * 56.0;
    private final double lookDistSq = 12.0 * 12.0;

    public NpcManager(SlowyCore plugin, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");
        this.npcKey = new NamespacedKey(plugin, "npc_id");

        loadDatabaseSync();
        logger.info("NpcManager initialized via SQLite ({} NPCs loaded, zero YAML).", npcs.size());
    }

    public NamespacedKey getNpcKey() {
        return npcKey;
    }

    public @Nullable NpcDefinition getNpc(String id) {
        return npcs.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<NpcDefinition> getAllNpcs() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    private void loadDatabaseSync() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, world, x, y, z, yaw, pitch, command, skin_name, skin_value, skin_signature, look_at_player FROM npcs"
             );
             ResultSet rs = ps.executeQuery()) {

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                String id = rs.getString("id").toLowerCase(Locale.ROOT);
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = (float) rs.getDouble("yaw");
                float pitch = (float) rs.getDouble("pitch");
                String command = rs.getString("command");
                String skinName = rs.getString("skin_name");
                String skinValue = rs.getString("skin_value");
                String skinSig = rs.getString("skin_signature");
                boolean lookAtPlayer = rs.getInt("look_at_player") == 1;

                UUID uuid = UUID.nameUUIDFromBytes(("NPC:" + id).getBytes(StandardCharsets.UTF_8));
                NpcDefinition npc = new NpcDefinition(id, uuid, world, x, y, z, yaw, pitch, skinName, skinValue, skinSig, command, lookAtPlayer);
                npcs.put(id, npc);
            }

            if (!hasRows) {
                seedDefaultNpcs();
            }

            rebuildAllEntities();
        } catch (SQLException e) {
            logger.error("Failed to load NPCs from database: {}", e.getMessage(), e);
        }
    }

    private void seedDefaultNpcs() {
        logger.info("Seeding initial default server NPCs into SQLite database...");

        // 1. Shop NPC
        addSeedNpc("shop", "spawn", -0.5, 67.0, -96.5, -4.239f, 0.0f, "shop", "sirte808",
                "ewogICJ0aW1lc3RhbXAiIDogMTcwNTkxOTY4NDY4OCwKICAicHJvZmlsZUlkIiA6ICIxM2Q3ZDgzZWNlYWM0ZWU2YmZiNjk0Y2JiYzdjMDQ5OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUZWRCaWRlbkluYyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS85YWY4MDE1MzY3MmE3YmU0MTVkOTZiZWUwZGNkZGJkYTkyMDljOTg1NDU0NGJhZGVlMjc1YmU0ZGZmZGRlOGYwIgogICAgfQogIH0KfQ==",
                "nTgS3uLFLbvOkTdoK/k/wDx24QBG301ET6zayMmYHamfSu2xrxkORk3SeDNyKcvJlkXH0FqXweX+Hrw2VZ3J23y17APWqLCL85nXBDtz9pMixMj/KRD9YjcjcrYs4Bu9dL2Ckr7YlAA53duZ1XYOt/wKS406MfHZIfHvRVRDjIvMd16cr2S1L5kSsmLVGKBin1Jxa+4oOF6l3t/G6aK5bDqKBOrHGs1VfrojEGu3lR6Ul7xH3mx0cSKuMXdPYxz2R8Is4T+kEKcFFHAV9lOVCTEPVpLuoeqUjySH5ZAvQ6GVYoM837kZYxxZauAc50K7heLM1zdXDpX8W9dY8flaevhTcem7lkCmPRPiBnkQpAyT1No4yk89uGFeIxUGrslSJsF6iSvoaj3Vq36SkCUHd2r+ciY9k3FZSCBzyVAR8EL+nE3QSfVisuNNcrNX3rEB7+5iplvSgj+2LGNq8aayG/t6tWwGsXtzSM5rklG5OxVOubQ2wI6if2bbzn8kxOwtU4uRR5wtU3XnPSi0111JVr7sMXKdgl9cHswxaJHtk8FKKu3PnLJE8qmI2ebB/lr6G5PNhNxQPJ5qnQyUnaJqhSqPTPSMQlvsJyYfDtPeMU3I9H5FpvU16RACHU0LDF0ZR/A8br19NGa+EAQxHTA7h637/mluqecSfxwVLa1R5bM=");

        // 2. Discord NPC
        addSeedNpc("discord", "spawn", -22.5, 63.0, 5.5, -90.0f, 0.0f, "discord", "https://mineskin.org/skins/c7759ef02ce74fdfab6ceb14f7121375",
                "ewogICJ0aW1lc3RhbXAiIDogMTcwODg4MzI0MjE0MywKICAicHJvZmlsZUlkIiA6ICI4ZGUyNDAzYTEyMjU0ZmFkOTM1OTYxYWFlYmQwNGUyOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJkZXNydHB1bWEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjgxNGZjNWQ5NzVkMmMzM2Q3NTEwNzM2MjA2NzhjNmY4ODNjZmQ1YjgwNmM3NWVjMjc4YjAxNjliMDY3OGQ5IgogICAgfQogIH0KfQ==",
                "Kv2bDkQQtyCedaRoDofnUja8pretSboT8SWHaPJYhiHSgpkCdSvtFB/Ht71BoGQv435Y16u7pyomRNe9NMRP4rar9c412QncgIY6UfqL3Y9UA7q+L7ERhVh/IN+rjHr3FsLPvGGlYwWpsPTgBH+/FF2ITaAhSM30QvE50KH3ggxq4qFeaO4KjfbioZc1RLjbvKmsw2k04tbcdWuOf2ld2oz2yiSRdY3+c2vj51AdUZxZUHrLnAtDqsIlMEVnxCxemsP5pXzMZ7h3041ZGQuWZ1GZig9mw3pb5jz5OVDmNc9j10G965s/gV0ZnTY7kAFt7kkx5IqFdMtl0q+/XZkrxxQcdwOup8Cc+LoEHVOKknh03Rx9dNQOPRtfxdJb/eYkSYlqeWiIvxAGmQ8wD7fpun4jpxajpsRCxVEBe3FWwnUV4xxGBKM5UvXTBwIKgoIyIwr3jDaX+UrcuJ6chTgtsGC8RQGJOlxZjTc4VDvCKyGgeu4Kk30Wu/IrmaUbzCY531uxXzO1dvTmPfm0m96giLZqadrU4psjxWygG6B24B3/ohyuhxYkRuHsUjCAz42iqhlURfcWzESXp8S1lVmxHx9+Uqd4jbRwFv+z5k/GIi8/OIJVH9WpGHWiHl3Tsvn9BifCAKeqUMyGNMWwuullhIA5e+EjQ40IsfdD2RexXQw=");

        // 3. AH NPC
        addSeedNpc("ah", "spawn", -72.5, 70.0, 66.5, -78.6f, 0.0f, "auction", "nellywing",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4ODYxMTIyNjAxNSwKICAicHJvZmlsZUlkIiA6ICI5NjExNjBkMjdmMzM0ZjIxYjQ3MGZlMjQ4MGM4NDBmOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJOZWxseVdpbmciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2RkZjQzOTFmYmNjZGI1OWIyZTdjN2JmMWZkNDliZmU1MzhhMjMzNzFjZDE4YmZlYmExOWE3YzI3MTYzMWJhMCIKICAgIH0KICB9Cn0=",
                "rNkr9E2rfjoG3oXkZujl9J/woaUZBuTHRWavig+hdtvUuxs88mLTcFq3aTfq3nJaPMYqqdQ0rCl+HLlPqa0Z8fNada/KoOe+BsoNosZEJkCSuHXgxW7K55N0bKWExIdODKkQ9xp4nE9wcVY0yhgvNTk6Tgv1/PylLep9BI8d9yT4NUwqniUKZCS4xh3aaRvVojWQdhaOmo9KVrehTrXnHFGcd9/hXFSaQsjhMKAP8ttIiLK9QxAJP82EIrRNadbc14emaCdY0DbZODoANjaFXpbFy7siC6kJp0Dm7aPgabTAJrJjzao/CE+k8hlRd0Of1BBF9Cj3b/db/eNJAU9vJ0Q0NwF/JMDOGFXcG+fOh+XVkL/PJnDS+xn7YomZ2kTsMjp11+d/DSVWWAL9nzhKhkql9ubr1QQ/wPTP3aWfPIZS5n35MrJLTGpgOSMj/+a05ePuRdI1LGXkljM6otw/KXqv5dqZNzvEdNyCeWclC15B7NuP3Z+76KhdLwfhLZpuLKfoPKPen8b3r+ArSrg2k8eczr7NZAq++fHTd2S1de95iITUBZKpThbJy3DvteRNC7tpaCcwbQSKZVXEgM8DCi9/5YXQApwIdHj0CR9YTDKpiWLY/Z+yHCUDuUaCRo7O8x7O6LE7RwV/XUEWM6cpPlrO5bPzMSm/fuioumexR7Y=");

        // 4. RTP NPC
        addSeedNpc("rtpp", "spawn", -20.5, 64.0, 15.5, -90.6f, -0.45f, "rtp", "_lawnchair_",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4ODYxMDQ2MjIzMiwKICAicHJvZmlsZUlkIiA6ICI3Y2FmNjI0ODU1M2Q0NmQ1OGNmMmU5MTUzMTllODg3MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJfbGF3bmNoYWlyXyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84MjdlNDA3OWE4MDZiYTUzM2RkZjA4NTNlYTVkNDY4ZGNkZWEwODEyMDljNjUyZDYzNGQyZDRiOWUxYjgxMzkiCiAgICB9LAogICAgIkNBUEUiIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzI4ZGU0YTgxNjg4YWQxOGI0OWU3MzVhMjczZTA4NmMxOGYxZTM5NjY5NTYxMjNjY2I1NzQwMzRjMDZmNWQzMzYiCiAgICB9CiAgfQp9",
                "b0A27Pn6Pzzi5Z82852fEn2F8/NNJJo56piE4//tPyqz8zMjQcI9tKmWWYU98GPNPdpRd5qUuSjXpmumu7J0v4FHjwE45XXKROHGeNit2nDQ48UQKh+Vffy/1jNkD5HB4b6khH6Fw1Rh5kGnDF1Ov0hSIMMfl6DaA5b9BnYRNV/OZZLISzd6T1ZC8EwxBjfltKqWsnh+R9hAc5NwSO+r4FKPN+NnQDvhwVd6jnyfZfqZ9cbyUOgoWor5sJ5ZzZX4I740cHpRzDotXYUhwLqUy5Xq7+1vIL8k/ZGPgd3sxgNYyQ3mp1gF7+OqRMpi3hDauWkscVkfWHtg/YIv4XjCDk3j4U0gEjp+6cuwSaS2ODP43Z1xVevMA742ECuSUqwJH/RlUC1X6JSzZ8O8N3mo2lanqf+zLChV7aKZVzToW+s8lDQAVtizV/ea3Tj6Qw6U6wyWyM5rJSINbAaa+PrIO1SOuZNERmhGSaBoADQQx8gmirolmwL99JxBooAvr4vqTJpXM/JqoEoYtnCqNs2aVqSsm4Np9j9ocp2ojbOH3kdgbEUCElqbuhRKkhe1glxQ8zmfkwaQm+SlJ89tH8uBzfUMm5AnPePXfiwYZlo4t1cNf2FLmuuUFiAHUVeKKsaGK7ax7Th08v92XLFkzhgb8Cu0Dgz/vI5Mo8aNpP/G9Xc=");

        // 5. Daily NPC
        addSeedNpc("daily", "spawn", -21.5, 63.0, -5.5, -9.0f, 85.35f, "daily", "alexsways",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4ODU4NTU4NDYzMCwKICAicHJvZmlsZUlkIiA6ICI5MThmMmY3ZDk2OGY0NTQxOTRhYWMzMWFhNTEwYjkxYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJhbGV4c3dheXMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDA0NjVmN2FhNGFjMWY1NzA2MGUwYzk1ZTFkZTRiNDc5ZjQwNGViMGU0MGFhMjQ1ZmJiYmZjNTIwYTczNTI5ZCIKICAgIH0sCiAgICAiQ0FQRSIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjhkZTRhODE2ODhhZDE4YjQ5ZTczNWEyNzNlMDg2YzE4ZjFlMzk2Njk1NjEyM2NjYjU3NDAzNGMwNmY1ZDMzNiIKICAgIH0KICB9Cn0=",
                "VROJpJ/btI0MxIyhKskgJONfDg3zGE3Xo/xCdmbaz6f+sjGMkTa0aJEpsVOIhb5NWCGHt9qqnSx4q/tPEOZa8+G10x5sj5hc+opn9z+D59Pk2jNDmGN6ZfBiXjjCzX7jRn7Zai6Flsp8yLTQtKjtaVY15+AyuZNT9p9Pb2ylp5oc1JLxcsNdAMzH27OeCztlT9G9ZCPienPKmmSZq+Kvzcxx6s4U3h1XTNiVeEVGHOMFhioDNqpCGP3xR2LZQKsSiG5o2aP6Wm6ACn8eBDNUDnriTATb8l5ihrf4NwA05a7Lu+1mcX0rwRdrSSx7RATT4GbtR8lgILdsKcZUgglq//j+h8FO5tj9NuEkcOIVVOP1O4HM0j6D4hzym3qPoQz/90kmmoxlgx2uv8KCjBs4kCV8z438JL7H2xk1eve2odK5sxzUCYjrCT5bZFUJF0GvTIRaLEw9qW6fJg4zxCUuQ/AyeiTbuSNYyg488VGKqSRuj88O+YX34s9oRHmnqDB6XF9LpzAKxC9QLRG9S8d+PjShu8jS6KBD2XFpfJ4OYc8iGNxeu6qkDjsVEeGYzdwz8vvcQuWeqAy78T7jigwlcRV9+ojbLkP3i1sdnHQ7bdYA2DHl7UR/UpF6rZajltAORkNOR1Y++/FBeBbt++FQW51tnZdJ7K2axaBBhylXxx4=");

        // 6. AFK Exit NPC
        addSeedNpc("afk_exit", "afk_zone", 41.5, -61.0, 41.5, 75.9f, 0.0f, "spawn", "alexsways",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4ODU4NTU4NDYzMCwKICAicHJvZmlsZUlkIiA6ICI5MThmMmY3ZDk2OGY0NTQxOTRhYWMzMWFhNTEwYjkxYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJhbGV4c3dheXMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDA0NjVmN2FhNGFjMWY1NzA2MGUwYzk1ZTFkZTRiNDc5ZjQwNGViMGU0MGFhMjQ1ZmJiYmZjNTIwYTczNTI5ZCIKICAgIH0sCiAgICAiQ0FQRSIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjhkZTRhODE2ODhhZDE4YjQ5ZTczNWEyNzNlMDg2YzE4ZjFlMzk2Njk1NjEyM2NjYjU3NDAzNGMwNmY1ZDMzNiIKICAgIH0KICB9Cn0=",
                "VROJpJ/btI0MxIyhKskgJONfDg3zGE3Xo/xCdmbaz6f+sjGMkTa0aJEpsVOIhb5NWCGHt9qqnSx4q/tPEOZa8+G10x5sj5hc+opn9z+D59Pk2jNDmGN6ZfBiXjjCzX7jRn7Zai6Flsp8yLTQtKjtaVY15+AyuZNT9p9Pb2ylp5oc1JLxcsNdAMzH27OeCztlT9G9ZCPienPKmmSZq+Kvzcxx6s4U3h1XTNiVeEVGHOMFhioDNqpCGP3xR2LZQKsSiG5o2aP6Wm6ACn8eBDNUDnriTATb8l5ihrf4NwA05a7Lu+1mcX0rwRdrSSx7RATT4GbtR8lgILdsKcZUgglq//j+h8FO5tj9NuEkcOIVVOP1O4HM0j6D4hzym3qPoQz/90kmmoxlgx2uv8KCjBs4kCV8z438JL7H2xk1eve2odK5sxzUCYjrCT5bZFUJF0GvTIRaLEw9qW6fJg4zxCUuQ/AyeiTbuSNYyg488VGKqSRuj88O+YX34s9oRHmnqDB6XF9LpzAKxC9QLRG9S8d+PjShu8jS6KBD2XFpfJ4OYc8iGNxeu6qkDjsVEeGYzdwz8vvcQuWeqAy78T7jigwlcRV9+ojbLkP3i1sdnHQ7bdYA2DHl7UR/UpF6rZajltAORkNOR1Y++/FBeBbt++FQW51tnZdJ7K2axaBBhylXxx4=");
    }

    private void addSeedNpc(String id, String world, double x, double y, double z, float yaw, float pitch,
                            String command, String skinName, String skinValue, String skinSig) {
        UUID uuid = UUID.nameUUIDFromBytes(("NPC:" + id).getBytes(StandardCharsets.UTF_8));
        NpcDefinition npc = new NpcDefinition(id, uuid, world, x, y, z, yaw, pitch, skinName, skinValue, skinSig, command, true);
        npcs.put(id, npc);
        saveNpcAsync(npc);
    }

    public void saveNpcAsync(NpcDefinition npc) {
        databaseManager.executeUpdateAsync("""
            INSERT INTO npcs (id, world, x, y, z, yaw, pitch, command, skin_name, skin_value, skin_signature, look_at_player)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                world = excluded.world,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z,
                yaw = excluded.yaw,
                pitch = excluded.pitch,
                command = excluded.command,
                skin_name = excluded.skin_name,
                skin_value = excluded.skin_value,
                skin_signature = excluded.skin_signature,
                look_at_player = excluded.look_at_player;
        """,
                npc.getId(),
                npc.getWorldName(),
                npc.getX(),
                npc.getY(),
                npc.getZ(),
                (double) npc.getYaw(),
                (double) npc.getPitch(),
                npc.getCommand(),
                npc.getSkinName(),
                npc.getSkinValue(),
                npc.getSkinSignature(),
                npc.isLookAtPlayer() ? 1 : 0
        );
    }

    public void deleteNpcAsync(String id) {
        databaseManager.executeUpdateAsync("DELETE FROM npcs WHERE id = ?", id.toLowerCase(Locale.ROOT));
    }

    public void rebuildAllEntities() {
        for (NpcDefinition npc : npcs.values()) {
            rebuildVirtualEntity(npc);
            getOrSpawnHitbox(npc);
        }
    }

    public void rebuildVirtualEntity(NpcDefinition npc) {
        Location loc = npc.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();
        CraftWorld craftWorld = (CraftWorld) loc.getWorld();
        if (craftWorld == null) return;
        ServerLevel level = craftWorld.getHandle();

        String profileName = npc.getId().length() > 16 ? npc.getId().substring(0, 16) : npc.getId();
        Multimap<String, Property> properties = LinkedHashMultimap.create();
        if (npc.hasValidSkin()) {
            properties.put("textures", new Property("textures", npc.getSkinValue(), npc.getSkinSignature()));
        }
        GameProfile profile = new GameProfile(npc.getUuid(), profileName, new PropertyMap(properties));

        ServerPlayer virtualPlayer = new ServerPlayer(mcServer, level, profile, ClientInformation.createDefault());
        virtualPlayer.setPos(loc.getX(), loc.getY(), loc.getZ());
        virtualPlayer.setYRot(loc.getYaw());
        virtualPlayer.setXRot(loc.getPitch());
        virtualPlayer.setYHeadRot(loc.getYaw());

        virtualEntities.put(npc.getId(), virtualPlayer);

        // Respawn for online players currently viewing
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<String> visible = visibleToPlayer.get(p.getUniqueId());
            if (visible != null && visible.contains(npc.getId())) {
                sendSpawnPackets(p, npc);
            }
        }
    }

    public @Nullable Interaction getOrSpawnHitbox(NpcDefinition npc) {
        Location loc = npc.getLocation();
        if (loc == null || loc.getWorld() == null) return null;

        Interaction existing = hitboxes.get(npc.getId());
        if (existing != null && existing.isValid()) {
            return existing;
        }

        World world = loc.getWorld();
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return null;
        }

        // Clean any leftover orphan interaction entities
        for (Entity e : world.getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
            if (e instanceof Interaction interaction) {
                String id = interaction.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
                if (npc.getId().equalsIgnoreCase(id)) {
                    interaction.remove();
                }
            }
        }

        Location spawnLoc = loc.clone().subtract(0, 0.0, 0);
        Interaction interaction = world.spawn(spawnLoc, Interaction.class, it -> {
            it.setInteractionWidth(0.8f);
            it.setInteractionHeight(1.9f);
            it.setResponsive(true);
            it.setPersistent(false); // Non-persistent: dynamically managed in memory & SQLite
            it.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, npc.getId());
        });

        hitboxes.put(npc.getId(), interaction);
        return interaction;
    }

    public NpcDefinition createNpc(String id, Location location) {
        String cleanId = id.toLowerCase(Locale.ROOT).trim();
        UUID uuid = UUID.nameUUIDFromBytes(("NPC:" + cleanId).getBytes(StandardCharsets.UTF_8));
        NpcDefinition npc = new NpcDefinition(cleanId, uuid,
                location.getWorld() != null ? location.getWorld().getName() : "world",
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                null, null, null, null, true);

        npcs.put(cleanId, npc);
        rebuildVirtualEntity(npc);
        getOrSpawnHitbox(npc);

        saveNpcAsync(npc);

        for (Player p : Bukkit.getOnlinePlayers()) {
            hideNameTagForPlayer(p, npc);
        }

        return npc;
    }

    public boolean removeNpc(String id) {
        String cleanId = id.toLowerCase(Locale.ROOT).trim();
        NpcDefinition npc = npcs.remove(cleanId);
        if (npc == null) return false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            sendDespawnPackets(p, npc);
        }

        Interaction interaction = hitboxes.remove(cleanId);
        if (interaction != null && interaction.isValid()) {
            interaction.remove();
        }

        virtualEntities.remove(cleanId);
        deleteNpcAsync(cleanId);

        return true;
    }

    public boolean bindCommand(String id, String command) {
        NpcDefinition npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) return false;
        npc.setCommand(command);
        saveNpcAsync(npc);
        return true;
    }

    public boolean unbindCommand(String id) {
        NpcDefinition npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) return false;
        npc.setCommand(null);
        saveNpcAsync(npc);
        return true;
    }

    public void setSkin(NpcDefinition npc, String skinName, @Nullable String value, @Nullable String signature) {
        npc.setSkinName(skinName);
        npc.setSkinValue(value);
        npc.setSkinSignature(signature);
        saveNpcAsync(npc);
        rebuildVirtualEntity(npc);
    }

    public void fetchSkinAsync(NpcDefinition npc, String query, Consumer<Boolean> callback) {
        CompletableFuture.supplyAsync(() -> plugin.getSkinManager().getSkinFetcher().fetchSkinSync(query))
                .thenAccept(skinData -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (skinData != null) {
                        setSkin(npc, query, skinData.value(), skinData.signature());
                        callback.accept(true);
                    } else {
                        callback.accept(false);
                    }
                }))
                .exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(false));
                    return null;
                });
    }

    public void sendSpawnPackets(Player player, NpcDefinition npc) {
        ServerPlayer virtualPlayer = virtualEntities.get(npc.getId());
        if (virtualPlayer == null) return;

        Location loc = npc.getLocation();
        if (loc == null) return;

        // 1. Send Player info packet with listed=false (prevents appearing in Tablist)
        ClientboundPlayerInfoUpdatePacket.Entry infoEntry = new ClientboundPlayerInfoUpdatePacket.Entry(
                virtualPlayer.getUUID(),
                virtualPlayer.getGameProfile(),
                false,
                0,
                GameType.SURVIVAL,
                null,
                true,
                0,
                null
        );
        ClientboundPlayerInfoUpdatePacket infoPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT
                ),
                infoEntry
        );
        sendPacket(player, infoPacket);

        // 2. Spawn entity packet
        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(
                virtualPlayer.getId(),
                virtualPlayer.getUUID(),
                virtualPlayer.getX(),
                virtualPlayer.getY(),
                virtualPlayer.getZ(),
                loc.getPitch(),
                loc.getYaw(),
                virtualPlayer.getType(),
                0,
                Vec3.ZERO,
                loc.getYaw()
        );
        sendPacket(player, spawnPacket);

        // 3. Head rotation
        byte yawByte = (byte) ((loc.getYaw() * 256.0f) / 360.0f);
        sendPacket(player, new ClientboundRotateHeadPacket(virtualPlayer, yawByte));

        // 4. Skin layers metadata (enable cape, jacket, sleeves, pants, hat)
        SynchedEntityData.DataValue<Byte> skinLayers = SynchedEntityData.DataValue.create(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 127);
        sendPacket(player, new ClientboundSetEntityDataPacket(virtualPlayer.getId(), List.of(skinLayers)));

        // 5. Hide nametag
        hideNameTagForPlayer(player, npc);
    }

    public void sendDespawnPackets(Player player, NpcDefinition npc) {
        ServerPlayer virtualPlayer = virtualEntities.get(npc.getId());
        if (virtualPlayer == null) return;

        sendPacket(player, new ClientboundRemoveEntitiesPacket(virtualPlayer.getId()));
        sendPacket(player, new ClientboundPlayerInfoRemovePacket(List.of(virtualPlayer.getUUID())));
    }

    public void hideNameTagForPlayer(Player player, NpcDefinition npc) {
        if (!player.isOnline()) return;

        String id = npc.getId();
        String teamName = "npc_" + (id.length() > 12 ? id.substring(0, 12) : id);

        Scoreboard sb = player.getScoreboard();
        Team team = sb.getTeam(teamName);
        if (team == null) {
            try {
                team = sb.registerNewTeam(teamName);
            } catch (IllegalArgumentException ignored) {
                team = sb.getTeam(teamName);
            }
        }

        if (team != null) {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

            String profileName = id.length() > 16 ? id.substring(0, 16) : id;
            if (!team.hasEntry(profileName)) team.addEntry(profileName);
            if (!team.hasEntry(id)) team.addEntry(id);
            if (!team.hasEntry(npc.getUuid().toString())) team.addEntry(npc.getUuid().toString());
        }
    }

    public void syncAllTeamsToPlayer(Player player) {
        for (NpcDefinition npc : npcs.values()) {
            hideNameTagForPlayer(player, npc);
        }
    }

    private void sendPacket(Player player, Packet<?> packet) {
        if (player.isOnline()) {
            ((CraftPlayer) player).getHandle().connection.send(packet);
        }
    }

    public void tick() {
        updateVisibility();
        updateHeadLook();
    }

    private void updateVisibility() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID pUuid = player.getUniqueId();
            Location pLoc = player.getLocation();
            World pWorld = pLoc.getWorld();
            if (pWorld == null) continue;

            Set<String> visible = visibleToPlayer.computeIfAbsent(pUuid, k -> ConcurrentHashMap.newKeySet());

            for (NpcDefinition npc : npcs.values()) {
                Location npcLoc = npc.getLocation();
                if (npcLoc == null || npcLoc.getWorld() == null || !npcLoc.getWorld().equals(pWorld)) {
                    if (visible.remove(npc.getId())) {
                        sendDespawnPackets(player, npc);
                        cleanupLookTracking(pUuid, npc.getId());
                    }
                    continue;
                }

                double distSq = pLoc.distanceSquared(npcLoc);
                if (distSq <= visDistSq) {
                    if (visible.add(npc.getId())) {
                        sendSpawnPackets(player, npc);
                    }
                } else if (distSq > despawnDistSq) {
                    if (visible.remove(npc.getId())) {
                        sendDespawnPackets(player, npc);
                        cleanupLookTracking(pUuid, npc.getId());
                    }
                }
            }
        }
    }

    private void updateHeadLook() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID pUuid = player.getUniqueId();
            Location pLoc = player.getLocation();
            World pWorld = pLoc.getWorld();
            if (pWorld == null) continue;

            Set<String> visible = visibleToPlayer.get(pUuid);
            if (visible == null || visible.isEmpty()) continue;

            Map<String, Boolean> lookStates = playerLookingState.computeIfAbsent(pUuid, k -> new HashMap<>());

            for (String npcId : visible) {
                NpcDefinition npc = npcs.get(npcId);
                if (npc == null || !npc.isLookAtPlayer()) continue;

                ServerPlayer virtualPlayer = virtualEntities.get(npcId);
                if (virtualPlayer == null) continue;

                Location npcLoc = npc.getLocation();
                if (npcLoc == null || npcLoc.getWorld() == null || !npcLoc.getWorld().equals(pWorld)) continue;

                double distSq = pLoc.distanceSquared(npcLoc);
                if (distSq <= lookDistSq) {
                    Location playerEye = player.getEyeLocation();
                    double dx = playerEye.getX() - npcLoc.getX();
                    double dy = playerEye.getY() - (npcLoc.getY() + 1.62);
                    double dz = playerEye.getZ() - npcLoc.getZ();
                    double distXZ = Math.sqrt(dx * dx + dz * dz);

                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

                    byte yawByte = (byte) ((yaw * 256.0f) / 360.0f);
                    byte pitchByte = (byte) ((pitch * 256.0f) / 360.0f);

                    sendPacket(player, new ClientboundRotateHeadPacket(virtualPlayer, yawByte));
                    sendPacket(player, new ClientboundMoveEntityPacket.Rot(virtualPlayer.getId(), yawByte, pitchByte, true));

                    lookStates.put(npcId, true);
                } else {
                    Boolean wasLooking = lookStates.get(npcId);
                    if (wasLooking != null && wasLooking) {
                        byte defaultYaw = (byte) ((npcLoc.getYaw() * 256.0f) / 360.0f);
                        byte defaultPitch = (byte) ((npcLoc.getPitch() * 256.0f) / 360.0f);

                        sendPacket(player, new ClientboundRotateHeadPacket(virtualPlayer, defaultYaw));
                        sendPacket(player, new ClientboundMoveEntityPacket.Rot(virtualPlayer.getId(), defaultYaw, defaultPitch, true));

                        lookStates.put(npcId, false);
                    }
                }
            }
        }
    }

    private void cleanupLookTracking(UUID playerUuid, String npcId) {
        Map<String, Boolean> states = playerLookingState.get(playerUuid);
        if (states != null) {
            states.remove(npcId);
        }
    }

    public void onPlayerJoin(Player player) {
        syncAllTeamsToPlayer(player);
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        visibleToPlayer.remove(uuid);
        playerLookingState.remove(uuid);
    }

    public void onPlayerChangedWorld(Player player) {
        UUID uuid = player.getUniqueId();
        Set<String> visible = visibleToPlayer.remove(uuid);
        if (visible != null) {
            for (String npcId : visible) {
                NpcDefinition npc = npcs.get(npcId);
                if (npc != null) {
                    sendDespawnPackets(player, npc);
                }
            }
        }
        playerLookingState.remove(uuid);
    }

    public void onChunkLoad(World world, int chunkX, int chunkZ) {
        for (NpcDefinition npc : npcs.values()) {
            Location loc = npc.getLocation();
            if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) continue;

            if ((loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ) {
                getOrSpawnHitbox(npc);
            }
        }
    }

    @Override
    public void onDisable() {
        // Despawn packets for all players
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (NpcDefinition npc : npcs.values()) {
                sendDespawnPackets(p, npc);
            }
        }

        // Remove all interaction hitboxes cleanly
        for (Interaction interaction : hitboxes.values()) {
            if (interaction != null && interaction.isValid()) {
                interaction.remove();
            }
        }
        hitboxes.clear();
        virtualEntities.clear();
        visibleToPlayer.clear();
        playerLookingState.clear();

        logger.info("NpcManager disabled.");
    }
}

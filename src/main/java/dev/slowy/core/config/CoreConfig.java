package dev.slowy.core.config;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.util.*;

/**
 * Single master configuration for SlowyCore2.
 * Pure Java compile-time constants — Zero external YAML/JSON configuration files.
 * Modern Kyori MiniMessage format without legacy color codes.
 */
public final class CoreConfig {

    private CoreConfig() {}

    // ── Global Messages & Prefix ────────────────────────────────────────────
    public static final String PREFIX = "";
    public static final String NO_PERMISSION = "<#FF0055>✖ <#E0F8FF>You do not have permission to use this command.";
    public static final String PLAYER_ONLY = "<#FF0055>✖ <#E0F8FF>This command can only be executed by in-game players.";

    // ── Discord ─────────────────────────────────────────────────────────────
    public static final String DISCORD_URL = "https://discord.com/invite/xkvjwsDrnx";
    public static final String DISCORD_MESSAGE = "<#1DA1F2>DISCORD: <click:open_url:'https://discord.com/invite/xkvjwsDrnx'><hover:show_text:'<gray>Click to open Discord invite!'><#1DA1F2><u>https://discord.com/invite/xkvjwsDrnx</u></#1DA1F2></hover></click>";

    // ── Economy & Currency ──────────────────────────────────────────────────
    public static final String PERM_ECONOMY_OWNER = "slowy.owner.economy";
    public static final double STARTING_BALANCE = 1000.0;
    public static final int STARTING_SHARDS = 0;
    public static final String CURRENCY_SYMBOL = "$";
    public static final String CURRENCY_SINGULAR = "Dollar";
    public static final String CURRENCY_PLURAL = "Dollars";

    // ── Shards Playtime Reward ──────────────────────────────────────────────
    public static final int SHARD_INTERVAL_MINUTES = 10;
    public static final int SHARD_REWARD_AMOUNT = 1;
    public static final String SHARD_REWARD_MESSAGE = "You received <#FF00BD>+1 Shard ★</#FF00BD>";
    public static final String SHARD_REWARD_SOUND = "ENTITY_PLAYER_LEVELUP";

    // ── Teleport & Worlds ───────────────────────────────────────────────────
    public static final int TELEPORT_WARMUP_SECONDS = 3;
    public static final int HOMES_MAX_DEFAULT = 6;
    public static final Set<String> HUB_WORLDS = Set.of("spawn", "minecraft:spawn", "afk_zone");
    public static final Set<String> HOMES_BLOCKED_WORLDS = Set.of("spawn", "afk_zone", "duels");
    public static final Set<String> PVP_ALLOWED_WORLDS = Set.of("world", "world_nether", "world_the_end");

    public static boolean isHubWorld(World world) {
        return world != null && isHubWorld(world.getName());
    }

    public static boolean isHubWorld(String worldName) {
        if (worldName == null) return false;
        String lower = worldName.toLowerCase(Locale.ROOT);
        return HUB_WORLDS.contains(lower) || lower.contains("spawn") || lower.contains("afk");
    }

    // ── PvP & Duel ──────────────────────────────────────────────────────────
    public static final int PVP_WARMUP_SECONDS = 5;
    public static final int PVP_TIMEOUT_SECONDS = 20;
    public static final int PVP_DC_RECONNECT_SECONDS = 30;
    public static final String PVP_PROTECTED_ICON = "<#39FF14>🛡 </#39FF14>";
    public static final String PVP_UNPROTECTED_ICON = "<#FF0055>⚔ </#FF0055>";

    // ── ClearLag ────────────────────────────────────────────────────────────
    public static final int CLEARLAG_INTERVAL_SECONDS = 300;
    public static final Set<EntityType> CLEARLAG_EXCLUDED_TYPES = Set.of(
            EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.IRON_GOLEM,
            EntityType.VILLAGER, EntityType.ARMOR_STAND, EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME,
            EntityType.INTERACTION, EntityType.TEXT_DISPLAY
    );
    public static final Set<Material> CLEARLAG_EXCLUDED_ITEMS = Set.of(
            Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE,
            Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE, Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
            Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS, Material.DIAMOND_BLOCK,
            Material.ELYTRA, Material.BEACON, Material.NETHER_STAR, Material.TOTEM_OF_UNDYING,
            Material.SHULKER_BOX, Material.ENCHANTED_GOLDEN_APPLE, Material.SPAWNER
    );

    // ── KeyAll ──────────────────────────────────────────────────────────────
    public static final int KEYALL_INTERVAL_SECONDS = 3600;

    // ── Auction House ───────────────────────────────────────────────────────
    public static final int AUCTION_LISTING_HOURS = 48;
    public static final int AUCTION_MAX_LISTINGS = 5;
    public static final double AUCTION_MIN_PRICE = 100.0;
    public static final double AUCTION_MAX_PRICE = 1_000_000_000.0;

    // ── Skins ───────────────────────────────────────────────────────────────
    public static final boolean SKIN_ENABLED = true;
    public static final int SKIN_COOLDOWN_DAYS = 3;
    public static final int SKIN_COOLDOWN_SECONDS = 3 * 86_400;
    public static final long SKIN_COOLDOWN_MS = 3L * 24L * 60L * 60L * 1000L; // 3 days
    public static final int SKIN_CACHE_DAYS = 7;
    public static final long SKIN_FETCH_TIMEOUT_MS = 3000L;

    // ── Tablist (MiniMessage) ────────────────────────────────────────────────
    public static final boolean TABLIST_ENABLED = true;
    public static final boolean TABLIST_SHOW_HEAD = true;
    public static final long TABLIST_UPDATE_TICKS = 40L; // 2 seconds

    public static final List<String> TABLIST_HEADER = List.of(
            "",
            "<#1DA1F2><bold>Slowy SMP</bold></#1DA1F2>",
            "<#E0F8FF>{online} Players</#E0F8FF>",
            ""
    );
    public static final List<String> TABLIST_FOOTER = List.of(
            "",
            "<#39FF14>$ <reset>{money} <#00F5FF>·</#00F5FF> {ping} ms",
            ""
    );

    // ── Scoreboard (MiniMessage) ─────────────────────────────────────────────
    public static final boolean SCOREBOARD_ENABLED = true;
    public static final long SCOREBOARD_UPDATE_TICKS = 60L; // 3 seconds
    public static final boolean SCOREBOARD_TITLE_ANIMATED = false;
    public static final long SCOREBOARD_TITLE_INTERVAL = 2L;
    public static final List<String> SCOREBOARD_TITLE_FRAMES = List.of(
            "<reset>%player_name%"
    );
    public static final List<String> SCOREBOARD_LINES = List.of(
            "<#39FF14>$</#39FF14> <reset>%economy_nicestMoney%",
            "<#FF00BD>★</#FF00BD> <reset>%economy_shards%",
            "<#FF0055>🗡</#FF0055> <reset>%economy_kills%",
            "<#FF7A00>☠</#FF7A00> <reset>%economy_deaths%",
            "<#FFE600>⌚</#FFE600> <reset>%economy_playtime%"
    );

    // ── Chat (MiniMessage) ───────────────────────────────────────────────────
    public static final boolean CHAT_FORMAT_ENABLED = true;
    public static final boolean CHAT_SHOW_HEAD = true;
    public static final String CHAT_SEPARATOR = " <#1DA1F2>»</#1DA1F2> ";

    // ── RTP & Portals ────────────────────────────────────────────────────────
    public static final boolean RTP_ENABLED = true;
    public static final int RTP_MENU_SIZE = 27;
    public static final int RTP_SLOT_OVERWORLD = 11;
    public static final int RTP_SLOT_NETHER = 13;
    public static final int RTP_SLOT_END = 15;
    // public static final int RTP_SLOT_CLOSE = 22;
    public static final String RTP_MENU_TITLE = "ʀᴛᴘ ᴍᴇɴᴜ";

    public static final String RTP_COLOR_OVERWORLD = "<#39FF14>";
    public static final String RTP_COLOR_NETHER = "<#FF7A00>";
    public static final String RTP_COLOR_END = "<#FF00BD>";

    public static final String RTP_LABEL_OVERWORLD = "ᴏᴠᴇʀᴡᴏʀʟᴅ";
    public static final String RTP_LABEL_NETHER = "ɴᴇᴛʜᴇʀ";
    public static final String RTP_LABEL_THE_END = "ᴛʜᴇ ᴇɴᴅ";
    public static final String RTP_LABEL_CLOSE = "<#FF0055>ᴄʟᴏꜱᴇ</#FF0055>";

    public static final Material RTP_ITEM_OVERWORLD = Material.GRASS_BLOCK;
    public static final Material RTP_ITEM_NETHER = Material.NETHERRACK;
    public static final Material RTP_ITEM_END = Material.END_STONE;
    public static final Material RTP_ITEM_CLOSE = Material.BARRIER;

    public static final long RTP_CACHE_PREFILL_STARTUP_DELAY_TICKS = 200L;
    public static final long RTP_CACHE_REFILL_RETRY_TICKS = 60L;
    public static final long RTP_PORTAL_ENTRY_DEBOUNCE_TICKS = 15L;
    public static final long RTP_TELEPORT_COOLDOWN_MS = 30_000L;
    public static final long RTP_ZONE_EXIT_COOLDOWN_MS = 1_500L;
    public static final int RTP_MAX_CACHE_SIZE = 3;
    public static final int RTP_MAX_SEARCH_ATTEMPTS = 30;

    public static final int RTP_OVERWORLD_MIN_RADIUS = 500;
    public static final int RTP_OVERWORLD_MAX_RADIUS = 4800;
    public static final int RTP_NETHER_MIN_RADIUS = 80;
    public static final int RTP_NETHER_MAX_RADIUS = 600;
    public static final int RTP_END_MIN_RADIUS = 150;
    public static final int RTP_END_MAX_RADIUS = 2000;

    public static final int RTP_NETHER_SCAN_Y_MIN = 35;
    public static final int RTP_NETHER_SCAN_Y_MAX = 115;
    public static final int RTP_SURFACE_EDGE_MARGIN = 5;
    public static final int RTP_SURFACE_SCAN_DEPTH = 10;

    // Portal detection bounding box (relative to portal center)
    public static final double RTP_PORTAL_BOX_Y_MIN = -3.8;
    public static final double RTP_PORTAL_BOX_Y_MAX = 3.2;
    public static final double RTP_PORTAL_BOX_FORWARD_DIST = 1.5;
    public static final double RTP_PORTAL_BOX_SIDE_DIST = 3.5;

    // Default portal locations in world "spawn"
    public static final String RTP_PORTALS_WORLD = "spawn";
    public static final double RTP_OVERWORLD_PORTAL_X = -83.5;
    public static final double RTP_OVERWORLD_PORTAL_Y = 72.2;
    public static final double RTP_OVERWORLD_PORTAL_Z = -25.5;
    public static final float RTP_OVERWORLD_PORTAL_YAW = -89.1f;

    public static final double RTP_NETHER_PORTAL_X = -70.5;
    public static final double RTP_NETHER_PORTAL_Y = 72.2;
    public static final double RTP_NETHER_PORTAL_Z = -38.5;
    public static final float RTP_NETHER_PORTAL_YAW = 178.5f;

    public static final double RTP_END_PORTAL_X = -70.5;
    public static final double RTP_END_PORTAL_Y = 72.2;
    public static final double RTP_END_PORTAL_Z = -12.5;
    public static final float RTP_END_PORTAL_YAW = 177.6f;

    // Messages
    public static final String RTP_MSG_ONLY_IN_SPAWN = "<#FF0055>✖ <#E0F8FF>You can only open the RTP menu in <#FFE600>Spawn</#FFE600> or <#00F5FF>AFK Zone</#00F5FF>!";
    public static final String RTP_MSG_COOLDOWN = "<#FF0055>✖ <#E0F8FF>Wait <#FFE600>{seconds}s</#FFE600> before using RTP again.";
    public static final String RTP_MSG_SEARCHING = "<#39FF14>Searching safe location in <#FFE600>{world}</#FFE600>...</#39FF14>";
    public static final String RTP_MSG_CANCELLED_LEFT = "<#FF0055>RTP cancelled: left portal zone</#FF0055>";
    public static final String RTP_MSG_CANCELLED_MOVED = "<#FF0055>Teleport cancelled: You moved!</#FF0055>";
    public static final String RTP_MSG_WORLD_NOT_FOUND = "<#FF0055>✖ Destination world <#FFE600>{world}</#FFE600> not found!";
    public static final String RTP_MSG_SEARCH_FAILED_PORTAL = "<#FF0055>✖ Failed to find a safe location. Please step in again!";
    public static final String RTP_MSG_SEARCH_FAILED = "<#FF0055>✖ Failed to find a safe location after multiple attempts. Please try again!";
    public static final String RTP_MSG_TELEPORTED = "<#39FF14>✔ Teleported to <#FFE600>{world}</#FFE600> <gray>({coords})</gray></#39FF14>";
    public static final String RTP_MSG_WARMUP = "<#FFE600>Teleporting in <#FF7A00>{seconds}s</#FF7A00>... Do not move!</#FFE600>";

    // ── Staff & Owner Permissions ────────────────────────────────────────────
    public static final String PERM_OWNER = "slowy.owner";
    public static final String PERM_STAFF = "slowy.staff";
    public static final String PERM_ADMIN = "slowy.admin";

    // ── Spawn Point (world: "spawn") ─────────────────────────────────────────
    public static final String SPAWN_WORLD_NAME = "spawn";
    public static final double SPAWN_POINT_X = -4.5;
    public static final double SPAWN_POINT_Y = 64.0;
    public static final double SPAWN_POINT_Z = 39.5;
    public static final float SPAWN_POINT_YAW = 180.0f;
    public static final float SPAWN_POINT_PITCH = 0.0f;

    // ── AFK Zone & 4th Portal ────────────────────────────────────────────────
    public static final String AFK_WORLD_NAME = "afk_zone";
    public static final int AFK_SHARD_INTERVAL_SECONDS = 60;
    public static final int AFK_SHARD_REWARD_AMOUNT = 1;
    public static final long AFK_PORTAL_COOLDOWN_MS = 3000L;

    // Portal AFK in world "spawn": 18.0 to 23.0, Y: 65.5 to 69.5, Z: 90.0 to 95.0
    public static final double AFK_PORTAL_MIN_X = 18.0;
    public static final double AFK_PORTAL_MAX_X = 23.0;
    public static final double AFK_PORTAL_MIN_Y = 65.5;
    public static final double AFK_PORTAL_MAX_Y = 69.5;
    public static final double AFK_PORTAL_MIN_Z = 90.0;
    public static final double AFK_PORTAL_MAX_Z = 95.0;

    // Random spots in afk_zone
    public static final double[][] AFK_SPOTS = {
            {8.5, -47.0, 8.5, -45.0f},
            {8.5, -47.0, 74.5, -135.0f},
            {74.5, -47.0, 74.5, 135.0f},
            {74.5, -47.0, 8.5, 45.0f}
    };

    public static final double AFK_PORTAL_HOLO_X = 20.5;
    public static final double AFK_PORTAL_HOLO_Y = 71.2;
    public static final double AFK_PORTAL_HOLO_Z = 92.5;
    public static final float AFK_PORTAL_HOLO_YAW = 168.1f;

    // AFK Messages & Titles
    public static final String AFK_WELCOME_TITLE = "<#FF00BD><bold>ᴀꜰᴋ</bold></#FF00BD>";
    public static final String AFK_WELCOME_SUBTITLE = "<#E0F8FF>Earn one shard per minute</#E0F8FF>";
    public static final String AFK_ACTIONBAR_REMAINING = "<#E0F8FF>Next shard in <#FF00BD>{seconds}s</#FF00BD></#E0F8FF>";
    public static final String AFK_ACTIONBAR_REWARD = "<#FF00BD><bold>+{amount} Shard ★</bold></#FF00BD>";

    // ── Crates & KeyAll ──────────────────────────────────────────────────────
    public record CrateBlockLocation(String world, int x, int y, int z, String crateId) {}

    public static final double CRATE_HOLO_TOP_OFFSET_Y = 1.25;
    public static final double CRATE_HOLO_BOTTOM_OFFSET_Y = -0.40;
    public static final boolean CRATE_PARTICLES_ENABLED = true;

    public static final boolean KEYALL_ENABLED = true;
    public static final int KEYALL_INTERVAL_MINUTES = 60;
    public static final boolean KEYALL_REQUIRE_NOT_AFK = true;

    public static final Map<String, Integer> KEYALL_WEIGHTS = Map.of(
            "common", 60,
            "uncommon", 25,
            "rare", 10,
            "epic", 4,
            "legendary", 1
    );

    public static final List<CrateBlockLocation> DEFAULT_CRATE_BLOCKS = List.of(
            new CrateBlockLocation("spawn", -54, 69, -71, "common"),
            new CrateBlockLocation("spawn", -54, 69, -81, "uncommon"),
            new CrateBlockLocation("spawn", -54, 69, -91, "rare"),
            new CrateBlockLocation("spawn", -44, 69, -91, "epic"),
            new CrateBlockLocation("spawn", -34, 69, -91, "legendary")
    );

    // ── Worth & Pricing ──────────────────────────────────────────────────────
    public static final boolean WORTH_DISPLAY_DEFAULT_ENABLED = true;
    public static final String WORTH_FORMAT_SINGLE = "<gray>Worth: </gray><#39FF14>$%total%</#39FF14>";
    public static final String WORTH_FORMAT_MULTIPLE = "<gray>Worth: </gray><#39FF14>$%total%</#39FF14> <dark_gray>(<gray>$%unit% each</gray>)</dark_gray>";

    // ── Sell GUI ─────────────────────────────────────────────────────────────
    public static final String SELL_GUI_TITLE = "sᴇʟʟ - $%s";
    public static final String SELL_GUI_TITLE_WITH_COUNT = "sᴇʟʟ - $%s (%,d item)";
    public static final int SELL_CANCEL_SLOT = 45;
    public static final int SELL_CONFIRM_SLOT = 53;

    // ── Daily Rewards ────────────────────────────────────────────────────────
    public static final long DAILY_COOLDOWN_MS = 24L * 60L * 60L * 1000L; // 24 hours
    public static final long DAILY_STREAK_EXPIRE_MS = 48L * 60L * 60L * 1000L; // 48 hours

    // ── Guild System ─────────────────────────────────────────────────────────
    public static final boolean GUILD_ENABLED = true;
    public static final long GUILD_CREATE_COST = 2000L; // 2,000 shards
    public static final long GUILD_RENAME_COST = 500L; // 500 shards
    public static final long GUILD_SLOT_UPGRADE_COST = 250L; // 250 shards per slot
    public static final int GUILD_DEFAULT_SLOTS = 15;
    public static final int GUILD_MAX_SLOTS = 26;
    public static final int GUILD_INVITE_TIMEOUT_SECONDS = 60; // 1 minute
    public static final java.util.regex.Pattern GUILD_NAME_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_]+$");
    public static final int GUILD_NAME_MIN_LENGTH = 3;
    public static final int GUILD_NAME_MAX_LENGTH = 16;

    // ── Teleportation System (TPA & TPAHERE) ──────────────────────────────────
    public static final boolean TELEPORT_ENABLED = true;
    public static final int TELEPORT_REQUEST_TIMEOUT_SECONDS = 20; // 20 seconds
    public static final int TELEPORT_GRACE_PERIOD_SECONDS = 1;     // 1 second
    public static final int TELEPORT_WARMUP_COUNTDOWN_SECONDS = 3; // 3 seconds
    public static final double TELEPORT_MAX_MOVE_DISTANCE_SQUARED = 1.0; // > 1.0 block
}


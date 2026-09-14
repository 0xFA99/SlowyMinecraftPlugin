package dev.slowy.core.scoreboard;

import dev.slowy.core.SlowyCore;
import dev.slowy.core.api.Lifecycle;
import dev.slowy.core.api.economy.EconomyService;
import dev.slowy.core.config.CoreConfig;
import dev.slowy.core.storage.DatabaseManager;
import dev.slowy.core.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern, ultra-optimized Scoreboard Manager for SlowyCore2.
 * Features:
 * - Compiled Tokenized Templates with static LUT resolution (Zero String.replace scans).
 * - ThreadLocal StringBuilder buffer to eliminate intermediate string allocations.
 * - Constant Line Component Caching (pure reference pass, zero MiniMessage parsing).
 * - Dynamic Lazy-Context Evaluation (statistics & balance queried only when referenced).
 * - PAPI bypass when no external placeholders exist.
 */
@NullMarked
public final class ScoreboardManager implements Lifecycle {

    public enum PlaceholderToken {
        PLAYER_NAME,
        PING,
        WORLD,
        KILLS,
        DEATHS,
        PLAYTIME,
        NICEST_MONEY,
        RAW_MONEY,
        SHARDS,
        ONLINE,
        MAX_PLAYERS,
        GUILD,
        GUILD_ROLE
    }

    private static final Map<String, PlaceholderToken> TOKEN_LUT = new HashMap<>();
    static {
        TOKEN_LUT.put("%player%", PlaceholderToken.PLAYER_NAME);
        TOKEN_LUT.put("%player_name%", PlaceholderToken.PLAYER_NAME);
        TOKEN_LUT.put("%displayname%", PlaceholderToken.PLAYER_NAME);
        TOKEN_LUT.put("%nick%", PlaceholderToken.PLAYER_NAME);
        TOKEN_LUT.put("<nick>", PlaceholderToken.PLAYER_NAME);
        TOKEN_LUT.put("%ping%", PlaceholderToken.PING);
        TOKEN_LUT.put("%player_ping%", PlaceholderToken.PING);
        TOKEN_LUT.put("%economy_ping%", PlaceholderToken.PING);
        TOKEN_LUT.put("%world%", PlaceholderToken.WORLD);
        TOKEN_LUT.put("%player_world%", PlaceholderToken.WORLD);
        TOKEN_LUT.put("%kills%", PlaceholderToken.KILLS);
        TOKEN_LUT.put("%economy_kills%", PlaceholderToken.KILLS);
        TOKEN_LUT.put("%deaths%", PlaceholderToken.DEATHS);
        TOKEN_LUT.put("%economy_deaths%", PlaceholderToken.DEATHS);
        TOKEN_LUT.put("%playtime%", PlaceholderToken.PLAYTIME);
        TOKEN_LUT.put("%economy_playtime%", PlaceholderToken.PLAYTIME);
        TOKEN_LUT.put("%economy_nicestMoney%", PlaceholderToken.NICEST_MONEY);
        TOKEN_LUT.put("%money%", PlaceholderToken.NICEST_MONEY);
        TOKEN_LUT.put("%economy_money%", PlaceholderToken.RAW_MONEY);
        TOKEN_LUT.put("%economy_shards%", PlaceholderToken.SHARDS);
        TOKEN_LUT.put("%shards%", PlaceholderToken.SHARDS);
        TOKEN_LUT.put("%online%", PlaceholderToken.ONLINE);
        TOKEN_LUT.put("%server_online%", PlaceholderToken.ONLINE);
        TOKEN_LUT.put("%max_players%", PlaceholderToken.MAX_PLAYERS);
        TOKEN_LUT.put("%server_max_players%", PlaceholderToken.MAX_PLAYERS);
        TOKEN_LUT.put("%guild%", PlaceholderToken.GUILD);
        TOKEN_LUT.put("%guild_name%", PlaceholderToken.GUILD);
        TOKEN_LUT.put("%guild_role%", PlaceholderToken.GUILD_ROLE);
    }

    private static final ThreadLocal<StringBuilder> LINE_BUFFER =
            ThreadLocal.withInitial(() -> new StringBuilder(256));

    private final SlowyCore plugin;
    private final Logger logger;
    private final EconomyService economyService;
    private final DatabaseManager databaseManager;

    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerSettings = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private long updateInterval = 60L;
    private boolean titleAnimated = false;
    private long titleInterval = 2L;

    private final List<ScoreboardTemplate> compiledTitleFrames = new ArrayList<>();
    private final List<ScoreboardTemplate> compiledLines = new ArrayList<>();
    private int titleFrameIndex = 0;

    public ScoreboardManager(SlowyCore plugin, EconomyService economyService, DatabaseManager databaseManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.logger = plugin.getSlf4jLogger();
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager cannot be null");

        loadConfig();
    }

    public synchronized void loadConfig() {
        this.enabled = CoreConfig.SCOREBOARD_ENABLED;
        this.updateInterval = CoreConfig.SCOREBOARD_UPDATE_TICKS;
        this.titleAnimated = CoreConfig.SCOREBOARD_TITLE_ANIMATED;
        this.titleInterval = CoreConfig.SCOREBOARD_TITLE_INTERVAL;

        this.compiledTitleFrames.clear();
        for (String frame : CoreConfig.SCOREBOARD_TITLE_FRAMES) {
            this.compiledTitleFrames.add(new ScoreboardTemplate(frame));
        }

        this.compiledLines.clear();
        for (String line : CoreConfig.SCOREBOARD_LINES) {
            this.compiledLines.add(new ScoreboardTemplate(line));
        }

        logger.info("Scoreboard loaded with compiled templates (enabled={}, interval={}t, animated={}, lines={})",
                enabled, updateInterval, titleAnimated, compiledLines.size());
    }

    public boolean isTitleAnimated() {
        return enabled && titleAnimated && !compiledTitleFrames.isEmpty();
    }

    public long getTitleInterval() {
        return Math.max(1L, titleInterval);
    }

    public long getUpdateInterval() {
        return Math.max(1L, updateInterval);
    }

    public void tickTitleAnimation() {
        if (!enabled || !titleAnimated || compiledTitleFrames.isEmpty()) return;
        titleFrameIndex = (titleFrameIndex + 1) % compiledTitleFrames.size();
        ScoreboardTemplate currentFrame = compiledTitleFrames.get(titleFrameIndex);

        // Instant broadcast jika frame adalah konstanta (tanpa placeholder per player)
        if (currentFrame.isConstant()) {
            Component titleComp = currentFrame.getStaticComponent();
            if (titleComp != null) {
                for (FastBoard board : boards.values()) {
                    if (board.getPlayer().isOnline() && !board.isDeleted()) {
                        board.updateTitle(titleComp);
                    }
                }
            }
            return;
        }

        StringBuilder sb = LINE_BUFFER.get();
        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();

        for (FastBoard board : boards.values()) {
            Player p = board.getPlayer();
            if (p.isOnline() && !board.isDeleted()) {
                ScoreboardContext ctx = new ScoreboardContext(plugin, p, economyService, online, maxPlayers);
                Component titleComp = currentFrame.render(ctx, sb);
                board.updateTitle(titleComp);
            }
        }
    }

    public void tickLines() {
        if (!enabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    public @Nullable FastBoard getBoard(UUID uuid) {
        return boards.get(uuid);
    }

    public Collection<FastBoard> getAllBoards() {
        return boards.values();
    }

    public void updatePlayer(Player player) {
        if (!enabled || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        if (!isEnabledFor(uuid)) {
            FastBoard existing = boards.remove(uuid);
            if (existing != null) {
                existing.delete();
            }
            return;
        }

        FastBoard board = boards.computeIfAbsent(uuid, k -> {
            FastBoard fb = new FastBoard(player);
            plugin.getNpcManager().syncAllTeamsToPlayer(player);
            if (plugin.getGuildManager() != null) {
                plugin.getGuildManager().syncNametagsToBoard(fb);
            }
            return fb;
        });

        StringBuilder sb = LINE_BUFFER.get();
        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        ScoreboardContext ctx = new ScoreboardContext(plugin, player, economyService, online, maxPlayers);

        // 1. Update Title
        if (!titleAnimated && !compiledTitleFrames.isEmpty()) {
            ScoreboardTemplate frame = compiledTitleFrames.get(0);
            Component titleComp = frame.render(ctx, sb);
            board.updateTitle(titleComp);
        } else if (titleAnimated && titleFrameIndex < compiledTitleFrames.size()) {
            ScoreboardTemplate frame = compiledTitleFrames.get(titleFrameIndex);
            Component titleComp = frame.render(ctx, sb);
            board.updateTitle(titleComp);
        }

        // 2. Update Lines
        List<Component> compLines = new ArrayList<>(compiledLines.size());
        for (ScoreboardTemplate lineTemplate : compiledLines) {
            compLines.add(lineTemplate.render(ctx, sb));
        }
        board.updateLines(compLines);
    }

    public String parsePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) return "";
        ScoreboardTemplate template = new ScoreboardTemplate(text);
        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        ScoreboardContext ctx = new ScoreboardContext(plugin, player, economyService, online, maxPlayers);
        return template.renderString(ctx, LINE_BUFFER.get());
    }

    public boolean isEnabledFor(UUID uuid) {
        return playerSettings.getOrDefault(uuid, true);
    }

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        boolean newState = !isEnabledFor(uuid);
        playerSettings.put(uuid, newState);

        databaseManager.executeUpdateAsync(
                "INSERT INTO player_settings (uuid, scoreboard_enabled, updated_at) VALUES (?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET scoreboard_enabled = excluded.scoreboard_enabled, updated_at = excluded.updated_at;",
                uuid.toString(), newState ? 1 : 0, System.currentTimeMillis()
        );

        if (newState) {
            updatePlayer(player);
        } else {
            FastBoard board = boards.remove(uuid);
            if (board != null) {
                board.delete();
            }
        }

        return newState;
    }

    public void onPlayerJoin(Player player) {
        if (!enabled) return;

        UUID uuid = player.getUniqueId();
        // Load setting from SQLite asynchronously
        databaseManager.supplyAsync(con -> {
            try (PreparedStatement ps = con.prepareStatement("SELECT scoreboard_enabled FROM player_settings WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("scoreboard_enabled") == 1;
                    }
                }
            }
            return true;
        }).thenAccept(sbEnabled -> {
            playerSettings.put(uuid, sbEnabled);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && sbEnabled) {
                    updatePlayer(player);
                }
            });
        });
    }

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        playerSettings.remove(uuid);
        FastBoard board = boards.remove(uuid);
        if (board != null) {
            board.delete();
        }
    }

    public void reload() {
        for (FastBoard board : boards.values()) {
            board.delete();
        }
        boards.clear();

        loadConfig();

        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayer(p);
        }
    }

    @Override
    public void onDisable() {
        for (FastBoard board : boards.values()) {
            board.delete();
        }
        boards.clear();
        playerSettings.clear();
    }

    // ── Template Engine & Token Classes ──────────────────────────────────────

    private sealed interface Segment permits LiteralSegment, TokenSegment {
        void append(StringBuilder sb, ScoreboardContext ctx);
    }

    private record LiteralSegment(String text) implements Segment {
        @Override
        public void append(StringBuilder sb, ScoreboardContext ctx) {
            sb.append(text);
        }
    }

    private record TokenSegment(PlaceholderToken token) implements Segment {
        @Override
        public void append(StringBuilder sb, ScoreboardContext ctx) {
            switch (token) {
                case PLAYER_NAME -> sb.append(ctx.getPlayerName());
                case PING -> sb.append(ctx.getPing());
                case WORLD -> sb.append(ctx.getWorld());
                case KILLS -> sb.append(ctx.getKills());
                case DEATHS -> sb.append(ctx.getDeaths());
                case PLAYTIME -> sb.append(ctx.getPlaytime());
                case NICEST_MONEY -> sb.append(ctx.getNicestMoney());
                case RAW_MONEY -> sb.append(ctx.getRawMoney());
                case SHARDS -> sb.append(ctx.getShards());
                case ONLINE -> sb.append(ctx.getOnline());
                case MAX_PLAYERS -> sb.append(ctx.getMaxPlayers());
                case GUILD -> sb.append(ctx.getGuildName());
                case GUILD_ROLE -> sb.append(ctx.getGuildRole());
            }
        }
    }

    public static final class ScoreboardTemplate {
        private final List<Segment> segments;
        private final boolean isConstant;
        private final @Nullable Component staticComponent;

        public ScoreboardTemplate(String raw) {
            if (raw == null || raw.isEmpty()) {
                this.segments = List.of();
                this.isConstant = true;
                this.staticComponent = Component.empty();
                return;
            }

            List<Segment> segs = new ArrayList<>();
            boolean foundToken = false;

            int len = raw.length();
            int cursor = 0;
            StringBuilder literalBuf = new StringBuilder();

            while (cursor < len) {
                // Check <nick>
                if (raw.startsWith("<nick>", cursor)) {
                    if (literalBuf.length() > 0) {
                        segs.add(new LiteralSegment(literalBuf.toString()));
                        literalBuf.setLength(0);
                    }
                    segs.add(new TokenSegment(PlaceholderToken.PLAYER_NAME));
                    foundToken = true;
                    cursor += 6;
                    continue;
                }

                // Check %...%
                if (raw.charAt(cursor) == '%') {
                    int nextPercent = raw.indexOf('%', cursor + 1);
                    if (nextPercent != -1) {
                        String placeholder = raw.substring(cursor, nextPercent + 1);
                        PlaceholderToken token = TOKEN_LUT.get(placeholder);
                        if (token != null) {
                            if (literalBuf.length() > 0) {
                                segs.add(new LiteralSegment(literalBuf.toString()));
                                literalBuf.setLength(0);
                            }
                            segs.add(new TokenSegment(token));
                            foundToken = true;
                            cursor = nextPercent + 1;
                            continue;
                        } else {
                            literalBuf.append(placeholder);
                            cursor = nextPercent + 1;
                            continue;
                        }
                    }
                }

                literalBuf.append(raw.charAt(cursor));
                cursor++;
            }

            if (literalBuf.length() > 0) {
                segs.add(new LiteralSegment(literalBuf.toString()));
            }

            this.segments = Collections.unmodifiableList(segs);
            this.isConstant = !foundToken;

            if (this.isConstant) {
                this.staticComponent = ColorUtils.parse(raw);
            } else {
                this.staticComponent = null;
            }
        }

        public boolean isConstant() { return isConstant; }
        public @Nullable Component getStaticComponent() { return staticComponent; }

        public Component render(ScoreboardContext ctx, StringBuilder sb) {
            if (isConstant && staticComponent != null) {
                return staticComponent;
            }
            return ColorUtils.parse(renderString(ctx, sb));
        }

        public String renderString(ScoreboardContext ctx, StringBuilder sb) {
            sb.setLength(0);
            for (Segment seg : segments) {
                seg.append(sb, ctx);
            }
            return sb.toString();
        }
    }

    public static final class ScoreboardContext {
        private final SlowyCore plugin;
        private final Player player;
        private final EconomyService economyService;
        private final int online;
        private final int maxPlayers;

        private int kills = -1;
        private int deaths = -1;
        private @Nullable String playtime;
        private @Nullable String nicestMoney;
        private @Nullable String rawMoney;
        private @Nullable Long shards;
        private @Nullable String guildName;
        private @Nullable String guildRole;

        public ScoreboardContext(SlowyCore plugin, Player player, EconomyService economyService, int online, int maxPlayers) {
            this.plugin = plugin;
            this.player = player;
            this.economyService = economyService;
            this.online = online;
            this.maxPlayers = maxPlayers;
        }

        public String getPlayerName() { return player.getName(); }
        public int getPing() { return player.getPing(); }
        public String getWorld() { return player.getWorld().getName(); }
        public int getOnline() { return online; }
        public int getMaxPlayers() { return maxPlayers; }

        public int getKills() {
            if (kills == -1) {
                try {
                    kills = player.getStatistic(Statistic.PLAYER_KILLS);
                } catch (Throwable t) {
                    kills = 0;
                }
            }
            return kills;
        }

        public int getDeaths() {
            if (deaths == -1) {
                try {
                    deaths = player.getStatistic(Statistic.DEATHS);
                } catch (Throwable t) {
                    deaths = 0;
                }
            }
            return deaths;
        }

        public String getPlaytime() {
            if (playtime == null) {
                try {
                    long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                    long totalSeconds = ticks / 20;
                    long hours = totalSeconds / 3600;
                    long minutes = (totalSeconds % 3600) / 60;
                    playtime = hours > 0 ? (hours + "h " + minutes + "m") : (minutes + "m");
                } catch (Throwable t) {
                    playtime = "0m";
                }
            }
            return playtime;
        }

        public String getNicestMoney() {
            if (nicestMoney == null) {
                nicestMoney = economyService.formatNicest(economyService.getBalance(player.getUniqueId()));
            }
            return nicestMoney;
        }

        public String getRawMoney() {
            if (rawMoney == null) {
                rawMoney = economyService.format(economyService.getBalance(player.getUniqueId()));
            }
            return rawMoney;
        }

        public long getShards() {
            if (shards == null) {
                shards = economyService.getShards(player.getUniqueId());
            }
            return shards;
        }

        public String getGuildName() {
            if (guildName == null) {
                var gm = plugin.getGuildManager();
                if (gm != null) {
                    var g = gm.getGuildByPlayer(player.getUniqueId());
                    guildName = (g != null) ? g.getName() : "-";
                } else {
                    guildName = "-";
                }
            }
            return guildName;
        }

        public String getGuildRole() {
            if (guildRole == null) {
                var gm = plugin.getGuildManager();
                if (gm != null) {
                    var m = gm.getMember(player.getUniqueId());
                    guildRole = (m != null) ? m.getRole().getDisplayName() : "-";
                } else {
                    guildRole = "-";
                }
            }
            return guildRole;
        }
    }
}

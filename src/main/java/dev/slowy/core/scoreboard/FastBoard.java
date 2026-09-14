package dev.slowy.core.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@NullMarked
public final class FastBoard {

    public static final String[] COLOR_CODES = {
            "§0§r", "§1§r", "§2§r", "§3§r", "§4§r",
            "§5§r", "§6§r", "§7§r", "§8§r", "§9§r",
            "§a§r", "§b§r", "§c§r", "§d§r", "§e§r"
    };

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final Objective belowNameObjective;
    private final @Nullable Component[] currentLines = new Component[COLOR_CODES.length];
    private @Nullable Component currentTitle = null;
    private boolean deleted = false;

    public FastBoard(Player player) {
        this.player = Objects.requireNonNull(player, "Player cannot be null");
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        this.objective = scoreboard.registerNewObjective("slowy_sb", Criteria.DUMMY, Component.empty());
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Hide red numbers on the right of scoreboard (1.20.4+ / Paper 26.2)
        try {
            this.objective.numberFormat(NumberFormat.blank());
        } catch (Throwable ignored) {}

        // Below name objective for Guild tags
        this.belowNameObjective = scoreboard.registerNewObjective("guild_below", Criteria.DUMMY, Component.empty());
        this.belowNameObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);

        // Pre-register 15 line teams
        for (int i = 0; i < COLOR_CODES.length; i++) {
            String teamName = "slowy_line_" + i;
            Team team = scoreboard.registerNewTeam(teamName);
            team.addEntry(COLOR_CODES[i]);
        }

        player.setScoreboard(scoreboard);
    }

    public Player getPlayer() {
        return player;
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public synchronized void updateTitle(Component title) {
        if (deleted || title == null) return;
        if (title.equals(currentTitle)) return;
        this.currentTitle = title;
        this.objective.displayName(title);
    }

    public synchronized void updateLines(@Nullable List<Component> lines) {
        if (deleted) return;
        if (lines == null) {
            lines = Collections.emptyList();
        }

        int maxLines = Math.min(lines.size(), COLOR_CODES.length);

        for (int i = 0; i < maxLines; i++) {
            Component line = lines.get(i);
            setLine(i, line);
        }

        for (int i = maxLines; i < COLOR_CODES.length; i++) {
            removeLine(i);
        }
    }

    private void setLine(int index, @Nullable Component component) {
        if (index < 0 || index >= COLOR_CODES.length) return;
        if (component == null) component = Component.empty();

        if (component.equals(currentLines[index])) {
            return; // No change, skip update
        }

        Team team = scoreboard.getTeam("slowy_line_" + index);
        if (team == null) {
            team = scoreboard.registerNewTeam("slowy_line_" + index);
            team.addEntry(COLOR_CODES[index]);
        }

        team.prefix(component);
        team.suffix(Component.empty());

        int scoreValue = COLOR_CODES.length - index;
        Score score = objective.getScore(COLOR_CODES[index]);
        score.setScore(scoreValue);

        try {
            score.numberFormat(NumberFormat.blank());
        } catch (Throwable ignored) {}

        currentLines[index] = component;
    }

    private void removeLine(int index) {
        if (index < 0 || index >= COLOR_CODES.length) return;
        if (currentLines[index] == null) return;

        scoreboard.resetScores(COLOR_CODES[index]);
        currentLines[index] = null;
    }

    public synchronized void setBelowName(String playerName, @Nullable Component text) {
        if (deleted) return;
        Score score = belowNameObjective.getScore(playerName);
        if (text == null) {
            try {
                score.resetScore();
            } catch (Throwable ignored) {}
        } else {
            score.setScore(0);
            try {
                score.numberFormat(NumberFormat.fixed(text));
            } catch (Throwable ignored) {}
        }
    }

    public synchronized void delete() {
        if (deleted) return;
        deleted = true;
        try {
            if (player.isOnline()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            for (Team team : scoreboard.getTeams()) {
                team.unregister();
            }
            objective.unregister();
            belowNameObjective.unregister();
        } catch (Exception ignored) {}
        Arrays.fill(currentLines, null);
    }
}


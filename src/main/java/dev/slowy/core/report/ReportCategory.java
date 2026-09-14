package dev.slowy.core.report;

import org.jspecify.annotations.NullMarked;

/**
 * Pilihan kategori masalah laporan player.
 */
@NullMarked
public enum ReportCategory {
    CHEATING("Cheating / Hack", "<red>Cheating / Hack</red>", "Penggunaan cheat atau mod ilegal"),
    BUG("Bug / Glitch", "<yellow>Bug / Glitch</yellow>", "Eksploitasi bug sistem atau duplikasi"),
    TOXIC_CHAT("Toxic Chat / Harassment", "<gold>Toxic Chat / Harassment</gold>", "Pelecehan kata atau spam chat"),
    OTHER("Lainnya", "<gray>Lainnya</gray>", "Masalah atau kendala lainnya");

    private final String displayName;
    private final String formattedName;
    private final String description;

    ReportCategory(String displayName, String formattedName, String description) {
        this.displayName = displayName;
        this.formattedName = formattedName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFormattedName() {
        return formattedName;
    }

    public String getDescription() {
        return description;
    }

    public ReportCategory next() {
        ReportCategory[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}

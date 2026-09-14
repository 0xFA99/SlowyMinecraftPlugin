package dev.slowy.core.report;

import org.bukkit.Material;
import org.jspecify.annotations.NullMarked;

/**
 * Tingkat urgensi laporan player (Rendah / Menengah / Tinggi).
 */
@NullMarked
public enum ReportUrgency {
    LOW("Rendah", "<green>Rendah</green>", "Bug visual, typo", Material.LIME_DYE),
    MEDIUM("Menengah", "<yellow>Menengah</yellow>", "Gangguan gameplay, toxic", Material.YELLOW_DYE),
    HIGH("Tinggi", "<red>Tinggi</red>", "Dupe, game-breaking, crash, hacker", Material.RED_DYE);

    private final String displayName;
    private final String formattedName;
    private final String description;
    private final Material material;

    ReportUrgency(String displayName, String formattedName, String description, Material material) {
        this.displayName = displayName;
        this.formattedName = formattedName;
        this.description = description;
        this.material = material;
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

    public Material getMaterial() {
        return material;
    }

    public ReportUrgency next() {
        ReportUrgency[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}

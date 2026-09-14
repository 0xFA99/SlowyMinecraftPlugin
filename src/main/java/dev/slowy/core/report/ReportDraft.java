package dev.slowy.core.report;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * State penampung data draf sementara laporan player sebelum dikirim.
 */
@NullMarked
public final class ReportDraft {

    private final UUID playerUuid;
    private ReportCategory category;
    private @Nullable String message;
    private ReportUrgency urgency;
    private boolean inDialog;

    public ReportDraft(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.category = ReportCategory.CHEATING;
        this.message = null;
        this.urgency = ReportUrgency.LOW;
        this.inDialog = false;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public ReportCategory getCategory() {
        return category;
    }

    public void setCategory(ReportCategory category) {
        this.category = category;
    }

    public @Nullable String getMessage() {
        return message;
    }

    public void setMessage(@Nullable String message) {
        this.message = message;
    }

    public boolean hasMessage() {
        return message != null && !message.isBlank();
    }

    public ReportUrgency getUrgency() {
        return urgency;
    }

    public void setUrgency(ReportUrgency urgency) {
        this.urgency = urgency;
    }

    public boolean isInDialog() {
        return inDialog;
    }

    public void setInDialog(boolean inDialog) {
        this.inDialog = inDialog;
    }
}

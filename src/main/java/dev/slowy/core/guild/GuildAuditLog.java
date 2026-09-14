package dev.slowy.core.guild;

import org.jspecify.annotations.NullMarked;

/**
 * Audit log entry for Guild creations, disbandings, renames, and leadership transfers.
 * Only viewable by server Owner.
 */
@NullMarked
public record GuildAuditLog(
        long id,
        long timestamp,
        String action,
        String guildId,
        String guildName,
        String actorUuid,
        String actorName,
        String details
) {}

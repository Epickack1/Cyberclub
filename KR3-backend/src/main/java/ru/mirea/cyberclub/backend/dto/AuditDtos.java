package ru.mirea.cyberclub.backend.dto;

import java.time.Instant;
import java.util.UUID;

/** DTO журнала аудита. */
public final class AuditDtos {
    private AuditDtos() {
    }

    public record AuditResponse(
            UUID id,
            Instant occurredAt,
            UUID actorId,
            String actorEmail,
            String eventType,
            String entityType,
            String entityId,
            String summary,
            String correlationId,
            String ipAddress) {
    }
}

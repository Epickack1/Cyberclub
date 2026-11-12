package ru.mirea.cyberclub.backend.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Запись журнала аудита. Только добавляется: сеттеров нет, изменить запись через приложение нельзя.
 * Кто (actor), что (eventType), над чем (entityType + entityId), когда и в рамках какого
 * HTTP-запроса (correlationId).
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email", length = 100)
    private String actorEmail;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "entity_type", length = 30)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    protected AuditEvent() {
    }

    public AuditEvent(Instant occurredAt, UUID actorId, String actorEmail, String eventType, String entityType,
                      String entityId, String summary, String correlationId, String ipAddress) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary;
        this.correlationId = correlationId;
        this.ipAddress = ipAddress;
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getEventType() { return eventType; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getSummary() { return summary; }
    public String getCorrelationId() { return correlationId; }
    public String getIpAddress() { return ipAddress; }
}

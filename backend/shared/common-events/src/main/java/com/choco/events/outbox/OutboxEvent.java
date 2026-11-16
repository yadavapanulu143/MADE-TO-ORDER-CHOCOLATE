package com.choco.events.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable record of an event that must be published.
 *
 * <p>Written inside the same transaction as the state change it describes. A relay then publishes
 * it and marks it sent. This is what prevents the classic dual-write failure: either the order row
 * and its event both exist, or neither does. Publishing directly to RabbitMQ inside the business
 * transaction would leave a window where the commit fails but the message is already gone.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OutboxEvent() {
    }

    private OutboxEvent(UUID id, String eventType, String aggregateId, int schemaVersion, String payload,
                        String correlationId, Instant occurredAt) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
        this.correlationId = correlationId;
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
        this.attempts = 0;
    }

    public static OutboxEvent pending(String eventType, String aggregateId, int schemaVersion, String payload,
                                      String correlationId, Instant occurredAt) {
        return new OutboxEvent(UUID.randomUUID(), eventType, aggregateId, schemaVersion, payload,
                correlationId, occurredAt);
    }

    public void markPublished(Instant when) {
        this.publishedAt = when;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}

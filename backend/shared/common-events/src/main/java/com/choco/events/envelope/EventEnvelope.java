package com.choco.events.envelope;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wire format for every event on the bus.
 *
 * <p>The envelope is deliberately separate from the domain event so that transport concerns
 * (correlation, tracing, schema version) never leak into the domain model, and so a consumer can
 * deduplicate by {@code eventId} before it ever deserialises a payload.
 *
 * @param eventId      unique id of this occurrence; the consumer dedupe key
 * @param eventType    routing key, e.g. {@code order.placed}
 * @param schemaVersion bumped when the payload changes incompatibly
 * @param aggregateId  id of the aggregate that produced the event
 * @param occurredAt   when the fact happened, in UTC
 * @param correlationId trace id propagated from the originating HTTP request
 * @param producer     service name, useful when debugging a rogue publisher
 * @param payload      the event body as a plain map, serialised by Jackson
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateId,
        Instant occurredAt,
        String correlationId,
        String producer,
        Map<String, Object> payload) {

    public EventEnvelope {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static EventEnvelope of(UUID eventId, String eventType, int schemaVersion, String aggregateId,
                                   Instant occurredAt, String correlationId, String producer,
                                   Map<String, Object> payload) {
        return new EventEnvelope(eventId, eventType, schemaVersion, aggregateId, occurredAt,
                correlationId, producer, payload);
    }
}

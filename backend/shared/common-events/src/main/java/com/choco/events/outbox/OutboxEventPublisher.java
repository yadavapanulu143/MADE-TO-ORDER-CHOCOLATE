package com.choco.events.outbox;

import com.choco.core.domain.DomainEvent;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter that satisfies the domain's {@link EventPublisher} port by appending to the outbox table
 * instead of talking to a broker.
 *
 * <p>Callers must invoke this inside the same transaction as the state change. The relay publishes
 * later, so a broker outage delays events but never loses them and never rolls back a business
 * transaction that has already succeeded.
 */
public class OutboxEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final String producer;
    private final Clock clock;

    public OutboxEventPublisher(OutboxEventRepository repository, ObjectMapper objectMapper,
                               String producer, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.producer = producer;
        this.clock = clock;
    }

    @Override
    public void publish(DomainEvent event) {
        Map<String, Object> payload = objectMapper.convertValue(event, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        append(event, payload);
    }

    /** Publishes an explicit payload when the domain event is not itself the wire format. */
    public void publish(String eventType, String aggregateId, int schemaVersion, Map<String, Object> payload,
                        String correlationId) {
        String json = serialise(payload);
        repository.save(OutboxEvent.pending(eventType, aggregateId, schemaVersion, json,
                correlationId, clock.now()));
    }

    private void append(DomainEvent event, Map<String, Object> payload) {
        String json = serialise(payload);
        repository.save(OutboxEvent.pending(event.eventType(), event.aggregateId(), event.schemaVersion(),
                json, null, event.occurredAt() == null ? clock.now() : event.occurredAt()));
        log.debug("Appended {} for aggregate {} to outbox", event.eventType(), event.aggregateId());
    }

    private String serialise(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialise outbox event payload", ex);
        }
    }

    public String producer() {
        return producer;
    }
}

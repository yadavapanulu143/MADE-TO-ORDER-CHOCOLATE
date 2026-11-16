package com.choco.events.outbox;

import com.choco.events.config.RabbitTopology;
import com.choco.events.envelope.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to RabbitMQ.
 *
 * <p>Each batch runs in its own transaction so that the row lock taken by {@code SKIP LOCKED} is
 * released as soon as the messages are handed to the broker. A publish failure is recorded on the
 * row and the batch stops, leaving the event to be retried on the next tick; the broker's publisher
 * confirms are not used as the sole guarantee, because the row is only marked published after
 * {@code convertAndSend} returns without error.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelay(OutboxEventRepository repository, RabbitTemplate rabbitTemplate,
                       ObjectMapper objectMapper, int batchSize, int maxAttempts) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /** One relay pass. Returns the number of events published. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int relayPendingEvents() {
        List<OutboxEvent> pending = repository.findUnpublishedForUpdate(batchSize);
        int published = 0;

        for (OutboxEvent event : pending) {
            if (event.getAttempts() >= maxAttempts) {
                // Leave it unpublished but stop retrying; the backlog metric surfaces it for an operator.
                log.error("Outbox event {} for {} exceeded {} attempts; giving up",
                        event.getId(), event.getEventType(), maxAttempts);
                continue;
            }
            try {
                publish(event);
                event.markPublished(Instant.now());
                published++;
            } catch (RuntimeException ex) {
                event.recordFailure(ex.getMessage());
                log.warn("Failed to publish outbox event {} ({}): {}", event.getId(), event.getEventType(),
                        ex.getMessage());
                // Stop the pass: the broker is probably unavailable, so retrying the rest is pointless.
                break;
            }
        }
        repository.saveAll(pending);
        return published;
    }

    private void publish(OutboxEvent event) {
        Map<String, Object> payload = deserialise(event.getPayload());
        EventEnvelope envelope = EventEnvelope.of(event.getId(), event.getEventType(), event.getSchemaVersion(),
                event.getAggregateId(), event.getOccurredAt(), event.getCorrelationId(), null, payload);

        rabbitTemplate.convertAndSend(RabbitTopology.EXCHANGE, event.getEventType(), envelope, message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(event.getId().toString());
            message.getMessageProperties().setHeader("eventType", event.getEventType());
            if (event.getCorrelationId() != null) {
                message.getMessageProperties().setHeader("X-Correlation-Id", event.getCorrelationId());
            }
            return message;
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialise(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Corrupt outbox payload for event: " + json, ex);
        }
    }
}

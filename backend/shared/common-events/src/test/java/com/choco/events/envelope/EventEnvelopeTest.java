package com.choco.events.envelope;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @Test
    void round_trips_through_json_without_losing_fields() throws Exception {
        EventEnvelope original = EventEnvelope.of(
                UUID.randomUUID(),
                EventTypes.ORDER_PLACED,
                1,
                "order-123",
                Instant.parse("2026-01-01T10:15:30Z"),
                "corr-1",
                "order-service",
                Map.of("orderId", "order-123", "totalMinor", 49900L));

        String json = objectMapper.writeValueAsString(original);
        EventEnvelope restored = objectMapper.readValue(json, EventEnvelope.class);

        assertThat(restored.eventId()).isEqualTo(original.eventId());
        assertThat(restored.eventType()).isEqualTo(EventTypes.ORDER_PLACED);
        assertThat(restored.aggregateId()).isEqualTo("order-123");
        assertThat(restored.occurredAt()).isEqualTo(Instant.parse("2026-01-01T10:15:30Z"));
        assertThat(restored.payload()).containsEntry("totalMinor", 49900);
    }

    @Test
    void payload_is_immutable() {
        EventEnvelope envelope = EventEnvelope.of(UUID.randomUUID(), "x", 1, "a", Instant.now(), null, "p", null);
        assertThat(envelope.payload()).isEmpty();
    }

    @Test
    void event_type_constants_are_unique_and_namespaced() {
        assertThat(EventTypes.ORDER_PLACED).startsWith("order.");
        assertThat(EventTypes.PAYMENT_CAPTURED).startsWith("payment.");
        assertThat(EventTypes.CUSTOMER_REGISTERED).startsWith("customer.");
    }
}

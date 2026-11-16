package com.choco.events.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void new_event_is_pending_with_no_publish_timestamp() {
        OutboxEvent event = OutboxEvent.pending("order.placed", "order-1", 1, "{}", "corr", Instant.now());

        assertThat(event.isPublished()).isFalse();
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void marking_published_records_timestamp_and_clears_error() {
        OutboxEvent event = OutboxEvent.pending("order.placed", "order-1", 1, "{}", null, Instant.now());
        event.recordFailure("broker down");
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("broker down");

        Instant publishedAt = Instant.now();
        event.markPublished(publishedAt);

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void failure_reason_is_truncated_to_fit_the_column() {
        OutboxEvent event = OutboxEvent.pending("order.placed", "order-1", 1, "{}", null, Instant.now());
        event.recordFailure("x".repeat(2000));

        assertThat(event.getLastError()).hasSize(500);
    }
}

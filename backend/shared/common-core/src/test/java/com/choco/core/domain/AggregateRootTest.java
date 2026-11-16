package com.choco.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

    record SomethingHappened(UUID eventId, Instant occurredAt, String aggregateId) implements DomainEvent {
        @Override
        public String eventType() {
            return "test.something-happened";
        }
    }

    static class SampleAggregate extends AggregateRoot {
        void doSomething() {
            registerEvent(new SomethingHappened(UUID.randomUUID(), Instant.now(), "agg-1"));
        }
    }

    @Test
    void pull_returns_events_once_and_clears_them() {
        SampleAggregate aggregate = new SampleAggregate();
        aggregate.doSomething();
        aggregate.doSomething();

        assertThat(aggregate.peekDomainEvents()).hasSize(2);

        List<DomainEvent> first = aggregate.pullDomainEvents();
        assertThat(first).hasSize(2);
        assertThat(aggregate.peekDomainEvents()).isEmpty();
        assertThat(aggregate.pullDomainEvents()).isEmpty();
    }

    @Test
    void published_event_carries_identity_and_type() {
        SampleAggregate aggregate = new SampleAggregate();
        aggregate.doSomething();
        DomainEvent event = aggregate.pullDomainEvents().getFirst();
        assertThat(event.eventType()).isEqualTo("test.something-happened");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.schemaVersion()).isEqualTo(1);
    }
}

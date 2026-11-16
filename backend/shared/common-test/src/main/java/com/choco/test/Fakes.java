package com.choco.test;

import com.choco.core.domain.DomainEvent;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.IdGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic test doubles for the outbound ports.
 *
 * <p>These are small hand-written fakes rather than Mockito stubs. A fake keeps real behaviour
 * (an advancing clock, a recording publisher), so a test asserts on outcomes instead of on which
 * methods were called in which order, which is what makes the tests survive refactoring.
 */
public final class Fakes {

    private Fakes() {
    }

    /** Clock that only moves when the test tells it to. */
    public static final class FixedClock implements Clock {
        private Instant now;

        public FixedClock(Instant now) {
            this.now = now;
        }

        public static FixedClock startingAt(String isoInstant) {
            return new FixedClock(Instant.parse(isoInstant));
        }

        @Override
        public Instant now() {
            return now;
        }

        public void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        public void advance(long amount, ChronoUnit unit) {
            now = now.plus(amount, unit);
        }
    }

    /** Id generator producing predictable, ordered UUIDs. */
    public static final class SequentialIdGenerator implements IdGenerator {
        private long counter = 0;

        @Override
        public UUID nextId() {
            counter++;
            return new UUID(0L, counter);
        }
    }

    /** Captures every event handed to it so a test can assert on what was published. */
    public static final class RecordingEventPublisher implements EventPublisher {
        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            events.add(event);
        }

        public List<DomainEvent> events() {
            return List.copyOf(events);
        }

        public List<DomainEvent> eventsOfType(String eventType) {
            return events.stream().filter(e -> e.eventType().equals(eventType)).toList();
        }

        public DomainEvent lastEvent() {
            return events.isEmpty() ? null : events.getLast();
        }

        public void clear() {
            events.clear();
        }
    }
}

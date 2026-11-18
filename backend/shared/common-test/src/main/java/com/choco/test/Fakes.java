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

    /**
     * Runs the unit of work directly, with no transaction.
     *
     * <p>Application tests exercise use-case logic, not commit behaviour — that is the database's job
     * and is covered by integration tests against a real Postgres. This fake lets the use case keep
     * its {@code TransactionRunner} dependency without dragging a transaction manager into a plain
     * unit test.
     */
    public static final class DirectTransactionRunner implements com.choco.core.port.TransactionRunner {
        @Override
        public <T> T execute(java.util.function.Supplier<T> work) {
            return work.get();
        }

        @Override
        public <T> T executeInNewTransaction(java.util.function.Supplier<T> work) {
            return work.get();
        }
    }

    /**
     * In-memory rate limiter with the same fixed-window semantics as the Redis implementation.
     *
     * <p>Counts are kept in a map and never expire, which is sufficient for a test that asserts the
     * limit is enforced. The expiry behaviour is Redis's job and is covered against a real server.
     */
    public static final class InMemoryRateLimiter implements com.choco.core.port.RateLimiter {
        private final java.util.Map<String, Long> counts = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Decision tryConsume(String key, int limit, long windowSeconds) {
            long count = counts.merge(key, 1L, Long::sum);
            if (count > limit) {
                return Decision.deny(windowSeconds);
            }
            return Decision.allow(Math.max(0, limit - count));
        }

        @Override
        public long currentCount(String key) {
            return counts.getOrDefault(key, 0L);
        }

        @Override
        public void reset(String key) {
            counts.remove(key);
        }
    }

    /**
     * In-memory revocation registry with the same semantics as the Redis implementation.
     *
     * <p>Sessions are a set, subjects map to a cutoff. Expiry is not simulated — a test that cares
     * about a record expiring uses a real Redis, where the TTL is the server's job.
     */
    public static final class InMemoryTokenRevocationRegistry
            implements com.choco.core.port.TokenRevocationRegistry {

        private final java.util.Set<String> revokedSessions = new java.util.concurrent.ConcurrentHashMap<>().newKeySet();
        private final java.util.Map<UUID, Instant> subjectCutoffs = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void revokeSession(String sessionId, Instant until) {
            revokedSessions.add(sessionId);
        }

        @Override
        public void revokeSubjectIssuedBefore(UUID subject, Instant cutoff, Instant until) {
            subjectCutoffs.merge(subject, cutoff,
                    (existing, replacement) -> replacement.isAfter(existing) ? replacement : existing);
        }

        @Override
        public boolean isRevoked(String sessionId, UUID subject, Instant issuedAt) {
            if (revokedSessions.contains(sessionId)) {
                return true;
            }
            Instant cutoff = subjectCutoffs.get(subject);
            return cutoff != null && issuedAt.isBefore(cutoff);
        }

        public boolean isSessionRevoked(String sessionId) {
            return revokedSessions.contains(sessionId);
        }
    }
}

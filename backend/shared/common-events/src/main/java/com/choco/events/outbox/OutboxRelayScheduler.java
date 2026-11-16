package com.choco.events.outbox;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the relay on a timer and records a backlog gauge.
 *
 * <p>Scheduled with a fixed delay rather than a fixed rate: if a pass takes longer than the
 * interval, the next one waits instead of piling up overlapping passes against the same rows.
 */
@Component
@ConditionalOnProperty(name = "choco.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelay relay;
    private final OutboxEventRepository repository;

    public OutboxRelayScheduler(OutboxRelay relay, OutboxEventRepository repository) {
        this.relay = relay;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${choco.outbox.relay-interval-ms:2000}")
    public void tick() {
        relay.relayPendingEvents();
    }

    /** Backlog size, exported so an alert can fire when events stop flowing. */
    public long backlogSize() {
        return repository.countByPublishedAtIsNull();
    }

    public Duration assumedMaxLag() {
        return Duration.ofMinutes(5);
    }
}

package com.choco.core.port;

import com.choco.core.domain.DomainEvent;
import java.util.Collection;

/**
 * Outbound port for publishing domain events. Adapters implement it with RabbitMQ; the domain
 * never knows a broker exists.
 */
public interface EventPublisher {

    void publish(DomainEvent event);

    /**
     * Publishes every event in order.
     *
     * <p>Convenience for draining an aggregate's recorded events after a commit. An adapter must
     * treat the whole collection as one unit so ordering within an aggregate is preserved.
     */
    default void publishAll(Collection<? extends DomainEvent> events) {
        events.forEach(this::publish);
    }
}

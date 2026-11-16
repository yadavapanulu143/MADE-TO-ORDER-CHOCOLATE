package com.choco.core.port;

import com.choco.core.domain.DomainEvent;

/**
 * Outbound port for publishing domain events. Adapters implement it with RabbitMQ; the domain
 * never knows a broker exists.
 */
public interface EventPublisher {

    void publish(DomainEvent event);
}

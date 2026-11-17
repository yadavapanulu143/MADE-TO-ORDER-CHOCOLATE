package com.choco.fulfilment.adapter.in.messaging;

import com.choco.events.config.RabbitTopology;
import com.choco.events.envelope.EventTypes;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The fulfilment service's own durable queue and its bindings.
 *
 * <p>One queue per consumer group, named after the service, bound to the events this service acts on.
 * The queue is durable and its messages persistent, so a broker restart does not lose a hand-over; the
 * dead-letter arguments route a message that repeatedly fails to the shared DLQ instead of dropping it.
 *
 * <p>Only two events are bound: {@code order.confirmed} (open the kitchen jobs) and
 * {@code order.cancelled} (abandon them and return any ingredients). Binding to {@code order.*} would
 * also pull in {@code order.placed}, which the kitchen must ignore — preparation cannot begin before a
 * payment is verified — and every extra event a consumer receives is another chance to act on it
 * wrongly.
 */
@Configuration(proxyBeanMethods = false)
class FulfilmentMessagingConfiguration {

    /**
     * The fulfilment service's queue name.
     *
     * <p>A literal, not a call to {@link RabbitTopology#queueName}, because {@code @RabbitListener}
     * needs a compile-time constant. The value matches the {@code choco.<consumer-group>} convention
     * every other service follows.
     */
    static final String QUEUE = "choco.fulfilment-service";

    @Bean
    Queue fulfilmentQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding orderConfirmedBinding(Queue fulfilmentQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(fulfilmentQueue).to(chocoEventsExchange)
                .with(EventTypes.ORDER_CONFIRMED);
    }

    @Bean
    Binding orderCancelledBinding(Queue fulfilmentQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(fulfilmentQueue).to(chocoEventsExchange)
                .with(EventTypes.ORDER_CANCELLED);
    }
}

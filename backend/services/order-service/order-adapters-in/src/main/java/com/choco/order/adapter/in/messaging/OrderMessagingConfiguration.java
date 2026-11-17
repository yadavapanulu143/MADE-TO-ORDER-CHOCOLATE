package com.choco.order.adapter.in.messaging;

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
 * The order service's own durable queue and its bindings to the payment facts it must react to.
 *
 * <p>Before this queue existed, the payment service published {@code payment.captured} into the void:
 * nothing consumed it, so an order never left {@code PAYMENT_PENDING}, preparation never unlocked, and
 * the fulfilment service — which waits for {@code order.confirmed} — was never told to make anything.
 * The shop could take money and never make chocolate. This consumer is what closes that loop, and it is
 * the only path by which a payment outcome reaches an order.
 *
 * <p>One queue per consumer group, named after the service. The queue is durable with persistent
 * messages, so a broker restart does not lose a capture; the dead-letter arguments route a message that
 * repeatedly fails to the shared DLQ instead of dropping it.
 *
 * <p>Four events are bound, and only four. {@code payment.captured} confirms the order; the others
 * record a failure, a still-in-flight attempt, or a processed refund so the order's payment track stays
 * truthful. Binding to {@code payment.*} would also pull {@code payment.dispute-raised}, which the
 * order aggregate has no transition for — every event a consumer receives is another chance to act on
 * it wrongly, so the set is explicit.
 */
@Configuration(proxyBeanMethods = false)
class OrderMessagingConfiguration {

    /**
     * The order service's queue name.
     *
     * <p>A literal, not a call to {@link RabbitTopology#queueName}, because {@code @RabbitListener}
     * needs a compile-time constant. The value matches the {@code choco.<consumer-group>} convention
     * every other service follows.
     */
    static final String QUEUE = "choco.order-service";

    @Bean
    Queue orderQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding paymentCapturedBinding(Queue orderQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(orderQueue).to(chocoEventsExchange)
                .with(EventTypes.PAYMENT_CAPTURED);
    }

    @Bean
    Binding paymentFailedBinding(Queue orderQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(orderQueue).to(chocoEventsExchange)
                .with(EventTypes.PAYMENT_FAILED);
    }

    @Bean
    Binding paymentPendingBinding(Queue orderQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(orderQueue).to(chocoEventsExchange)
                .with(EventTypes.PAYMENT_PENDING);
    }

    @Bean
    Binding refundProcessedBinding(Queue orderQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(orderQueue).to(chocoEventsExchange)
                .with(EventTypes.REFUND_PROCESSED);
    }
}

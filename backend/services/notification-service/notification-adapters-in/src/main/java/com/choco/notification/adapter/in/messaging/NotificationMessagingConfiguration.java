package com.choco.notification.adapter.in.messaging;

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
 * The notification service's durable queues and bindings.
 *
 * <p>Two queues, not one, because they carry two different kinds of message with different failure
 * semantics. The events queue carries business facts that trigger a customer or seller message; a
 * message that repeatedly fails to process there should dead-letter and be seen. The contact queue
 * carries the identity projections this service depends on; its messages are idempotent upserts, so a
 * failure there is less dangerous, but keeping it separate means a flood of contact updates cannot
 * delay a time-sensitive delivery notification.
 *
 * <p>Both queues are durable with persistent messages and dead-letter arguments, matching every other
 * consumer in the platform, so a broker restart does not lose an event and a poison message lands in
 * the shared dead-letter queue instead of being redelivered forever.
 */
@Configuration(proxyBeanMethods = false)
class NotificationMessagingConfiguration {

    /** Events that trigger a notification. */
    static final String EVENTS_QUEUE = "choco.notification-service.events";

    /** Identity contact projections. */
    static final String CONTACTS_QUEUE = "choco.notification-service.contacts";

    @Bean
    Queue notificationEventsQueue() {
        return QueueBuilder.durable(EVENTS_QUEUE)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Queue notificationContactsQueue() {
        return QueueBuilder.durable(CONTACTS_QUEUE)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    // --- Contact projection events (identity) --------------------------------------------------

    @Bean
    Binding customerRegisteredBinding(Queue notificationContactsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationContactsQueue).to(chocoEventsExchange)
                .with(EventTypes.CUSTOMER_REGISTERED);
    }

    @Bean
    Binding customerVerifiedBinding(Queue notificationContactsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationContactsQueue).to(chocoEventsExchange)
                .with(EventTypes.CUSTOMER_VERIFIED);
    }

    @Bean
    Binding customerContactChangedBinding(Queue notificationContactsQueue,
                                          TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationContactsQueue).to(chocoEventsExchange)
                .with(EventTypes.CUSTOMER_CONTACT_CHANGED);
    }

    // --- Customer notifications -----------------------------------------------------------------

    @Bean
    Binding orderPlacedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.ORDER_PLACED);
    }

    @Bean
    Binding orderConfirmedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.ORDER_CONFIRMED);
    }

    @Bean
    Binding orderCancelledBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.ORDER_CANCELLED);
    }

    @Bean
    Binding invoiceIssuedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.INVOICE_ISSUED);
    }

    @Bean
    Binding paymentCapturedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.PAYMENT_CAPTURED);
    }

    @Bean
    Binding paymentFailedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.PAYMENT_FAILED);
    }

    @Bean
    Binding paymentPendingBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.PAYMENT_PENDING);
    }

    @Bean
    Binding refundProcessedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.REFUND_PROCESSED);
    }

    // --- Seller notifications -------------------------------------------------------------------

    @Bean
    Binding refundRequestedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.REFUND_REQUESTED);
    }

    @Bean
    Binding disputeRaisedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.DISPUTE_RAISED);
    }

    @Bean
    Binding lowStockBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.LOW_STOCK_DETECTED);
    }

    // --- Production and delivery ----------------------------------------------------------------

    @Bean
    Binding productionStartedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.PRODUCTION_STARTED);
    }

    @Bean
    Binding productionCompletedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.PRODUCTION_COMPLETED);
    }

    @Bean
    Binding readyForDispatchBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.READY_FOR_DISPATCH);
    }

    @Bean
    Binding deliveryAssignedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.DELIVERY_ASSIGNED);
    }

    @Bean
    Binding outForDeliveryBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.OUT_FOR_DELIVERY);
    }

    @Bean
    Binding deliveryDeliveredBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.DELIVERY_DELIVERED);
    }

    @Bean
    Binding deliveryFailedBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.DELIVERY_FAILED);
    }

    @Bean
    Binding deliveryRescheduledBinding(Queue notificationEventsQueue, TopicExchange chocoEventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue).to(chocoEventsExchange)
                .with(EventTypes.DELIVERY_RESCHEDULED);
    }
}

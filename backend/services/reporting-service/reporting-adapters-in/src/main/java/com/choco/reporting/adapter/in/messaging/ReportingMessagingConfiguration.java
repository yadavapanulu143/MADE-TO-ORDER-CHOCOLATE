package com.choco.reporting.adapter.in.messaging;

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
 * The reporting service's durable queue and bindings.
 *
 * <p>One queue for every event it projects, because the handler is a single idempotent upsert dispatch
 * on the {@code eventType} header — there is no reason to shard a stream of upserts by type. The
 * bindings are explicit per event rather than a {@code #} wildcard, so an event this service does not
 * report on is never delivered here and never consumes a handler's attention.
 *
 * <p>The queue is durable with persistent messages and dead-letter arguments, matching every other
 * consumer. A projection is derived and could be rebuilt, but a poison message that loops forever
 * would peg a consumer and hide the fault; dead-lettering it makes the fault visible.
 */
@Configuration(proxyBeanMethods = false)
class ReportingMessagingConfiguration {

    /** Every event the reporting service projects. */
    static final String EVENTS_QUEUE = "choco.reporting-service.events";

    @Bean
    Queue reportingEventsQueue() {
        return QueueBuilder.durable(EVENTS_QUEUE)
                .deadLetterExchange(RabbitTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RabbitTopology.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    // --- Orders ---------------------------------------------------------------------------------

    @Bean
    Binding orderPlacedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.ORDER_PLACED);
    }

    @Bean
    Binding orderConfirmedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.ORDER_CONFIRMED);
    }

    @Bean
    Binding orderCancelledBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.ORDER_CANCELLED);
    }

    @Bean
    Binding invoiceIssuedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.INVOICE_ISSUED);
    }

    @Bean
    Binding refundRequestedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.REFUND_REQUESTED);
    }

    // --- Payments -------------------------------------------------------------------------------

    @Bean
    Binding paymentCapturedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.PAYMENT_CAPTURED);
    }

    @Bean
    Binding paymentFailedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.PAYMENT_FAILED);
    }

    @Bean
    Binding paymentPendingBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.PAYMENT_PENDING);
    }

    @Bean
    Binding refundProcessedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.REFUND_PROCESSED);
    }

    // --- Fulfilment -----------------------------------------------------------------------------

    @Bean
    Binding productionStartedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.PRODUCTION_STARTED);
    }

    @Bean
    Binding productionCompletedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.PRODUCTION_COMPLETED);
    }

    @Bean
    Binding readyForDispatchBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.READY_FOR_DISPATCH);
    }

    @Bean
    Binding deliveryAssignedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.DELIVERY_ASSIGNED);
    }

    @Bean
    Binding outForDeliveryBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.OUT_FOR_DELIVERY);
    }

    @Bean
    Binding deliveryDeliveredBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.DELIVERY_DELIVERED);
    }

    @Bean
    Binding deliveryFailedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.DELIVERY_FAILED);
    }

    // --- Identity -------------------------------------------------------------------------------

    @Bean
    Binding customerRegisteredBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.CUSTOMER_REGISTERED);
    }

    @Bean
    Binding customerVerifiedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.CUSTOMER_VERIFIED);
    }

    @Bean
    Binding customerContactChangedBinding(Queue reportingEventsQueue, TopicExchange chocoEventsExchange) {
        return bind(reportingEventsQueue, chocoEventsExchange, EventTypes.CUSTOMER_CONTACT_CHANGED);
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}

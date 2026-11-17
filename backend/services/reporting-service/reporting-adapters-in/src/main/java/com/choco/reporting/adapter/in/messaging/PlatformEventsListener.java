package com.choco.reporting.adapter.in.messaging;

import com.choco.events.envelope.EventTypes;
import com.choco.reporting.application.service.ProjectionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Applies platform events to the reporting read models.
 *
 * <p>This is the only way the reporting service learns anything. It has no synchronous call into the
 * order, payment, fulfilment or identity service, so a report works while those services are briefly
 * down and cannot be used to load them. Every projection is an idempotent upsert, so at-least-once
 * delivery is safe without a separate dedupe table — the read model has no append-only rows to
 * duplicate.
 *
 * <p><b>One queue, many event types, dispatched on the {@code eventType} header.</b> The bindings are
 * explicit per event rather than a {@code #} wildcard, so an event this service does not project is
 * never delivered here at all. The wire-name-to-use-case mapping lives here, in the adapter, so the
 * application layer carries no dependency on the events module. An event this service does not act on
 * is ignored rather than guessed at.
 *
 * <p>A message whose envelope cannot be read at all is rethrown, so the container's retry and
 * dead-letter policy applies: an unreadable message is something an operator should see rather than
 * silently drop.
 */
@Component
class PlatformEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PlatformEventsListener.class);

    private final ProjectionService projectionService;
    private final ObjectMapper objectMapper;

    PlatformEventsListener(ProjectionService projectionService, ObjectMapper objectMapper) {
        this.projectionService = projectionService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = ReportingMessagingConfiguration.EVENTS_QUEUE)
    void onMessage(Message message) {
        String eventType = (String) message.getMessageProperties().getHeaders().get("eventType");
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        if (eventType == null) {
            log.warn("Received a reporting message with no eventType header; ignoring");
            return;
        }
        Map<String, Object> payload = readPayload(body);
        switch (eventType) {
            case EventTypes.ORDER_PLACED -> projectionService.orderPlaced(payload);
            case EventTypes.ORDER_CONFIRMED -> projectionService.orderConfirmed(payload);
            case EventTypes.ORDER_CANCELLED -> projectionService.orderCancelled(payload);
            case EventTypes.INVOICE_ISSUED -> projectionService.invoiceIssued(payload);
            case EventTypes.REFUND_REQUESTED -> projectionService.refundRequested(payload);
            case EventTypes.PAYMENT_CAPTURED -> projectionService.paymentCaptured(payload);
            case EventTypes.PAYMENT_FAILED -> projectionService.paymentFailed(payload);
            case EventTypes.PAYMENT_PENDING -> projectionService.paymentPending(payload);
            case EventTypes.REFUND_PROCESSED -> projectionService.refundProcessed(payload);
            case EventTypes.PRODUCTION_STARTED -> projectionService.productionStarted(payload);
            case EventTypes.PRODUCTION_COMPLETED -> projectionService.productionCompleted(payload);
            case EventTypes.READY_FOR_DISPATCH -> projectionService.readyForDispatch(payload);
            case EventTypes.DELIVERY_ASSIGNED -> projectionService.deliveryAssigned(payload);
            case EventTypes.OUT_FOR_DELIVERY -> projectionService.outForDelivery(payload);
            case EventTypes.DELIVERY_DELIVERED -> projectionService.deliveryDelivered(payload);
            case EventTypes.DELIVERY_FAILED -> projectionService.deliveryFailed(payload);
            case EventTypes.CUSTOMER_REGISTERED -> projectionService.customerRegistered(payload);
            case EventTypes.CUSTOMER_VERIFIED -> projectionService.customerVerified(payload);
            case EventTypes.CUSTOMER_CONTACT_CHANGED -> projectionService.customerContactChanged(payload);
            default -> log.debug("Ignoring event {} on the reporting events queue", eventType);
        }
    }

    private Map<String, Object> readPayload(String body) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(body, new TypeReference<>() {
            });
            Object payload = envelope.get("payload");
            if (!(payload instanceof Map)) {
                throw new IllegalArgumentException("Envelope has no payload object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) payload;
            return typed;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unreadable reporting event payload", ex);
        }
    }
}

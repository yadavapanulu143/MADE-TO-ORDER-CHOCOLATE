package com.choco.order.adapter.in.messaging;

import com.choco.events.envelope.EventTypes;
import com.choco.order.application.service.OrderCommandService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the payment service's outcomes and applies them to the order.
 *
 * <p>This is the only way an order learns that a payment succeeded, failed or was refunded. There is
 * deliberately no HTTP endpoint that marks an order paid: a browser must never be able to confirm its
 * own payment, so the verified fact arrives here, from the service that verified it, and nowhere else.
 * The order service trusts this event because only the payment service can produce it — it is not a
 * client claim, it is a producer the broker authenticated.
 *
 * <p><b>The message is read as a raw JSON string, not as a bound object.</b> The wire format is the
 * shared {@code EventEnvelope}; binding it to this service's own classes would couple the consumer to
 * the producer's Java types, and a rename over there would be a runtime failure here rather than a
 * contract change. The envelope's {@code payload} is read into a local record that states exactly the
 * fields this service depends on.
 *
 * <p><b>Idempotent by construction.</b> The aggregate refuses to confirm an order twice and ignores a
 * stale failure after a capture, so a redelivered {@code payment.captured} — a broker retry, or a
 * consumer restart before the acknowledgement — changes nothing and emits no second
 * {@code order.confirmed}. That is what stops the kitchen opening a second set of jobs for one payment.
 */
@Component
class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final OrderCommandService orderCommandService;
    private final ObjectMapper objectMapper;

    PaymentEventsListener(OrderCommandService orderCommandService, ObjectMapper objectMapper) {
        this.orderCommandService = orderCommandService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = OrderMessagingConfiguration.QUEUE)
    void onMessage(Message message) {
        String eventType = (String) message.getMessageProperties().getHeaders().get("eventType");
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        if (eventType == null) {
            log.warn("Received an order message with no eventType header; ignoring");
            return;
        }
        switch (eventType) {
            case EventTypes.PAYMENT_CAPTURED -> handleCaptured(body);
            case EventTypes.PAYMENT_FAILED -> handleFailed(body);
            case EventTypes.PAYMENT_PENDING -> handlePending(body);
            case EventTypes.REFUND_PROCESSED -> handleRefundProcessed(body);
            default -> log.debug("Ignoring event {} on the order queue", eventType);
        }
    }

    private void handleCaptured(String body) {
        PaymentPayload payload = readPayload(body, PaymentPayload.class);
        UUID orderId = requireOrderId(payload);
        // The amount is passed through so the order can refuse a capture that does not match what it
        // is owed. A verified payment is only "verified" in the sense that the provider took the
        // money; whether it took the *right* money is the order's own invariant to check.
        orderCommandService.markPaid(orderId, payload.providerPaymentId(), payload.providerOrderId(),
                payload.amountMinorUnits(), payload.currency());
        log.debug("Applied payment.captured to order {}", orderId);
    }

    private void handleFailed(String body) {
        PaymentPayload payload = readPayload(body, PaymentPayload.class);
        UUID orderId = requireOrderId(payload);
        orderCommandService.markPaymentFailed(orderId, payload.reason());
        log.debug("Applied payment.failed to order {}", orderId);
    }

    private void handlePending(String body) {
        PaymentPayload payload = readPayload(body, PaymentPayload.class);
        UUID orderId = requireOrderId(payload);
        orderCommandService.markPaymentVerificationPending(orderId, payload.providerPaymentId(),
                payload.providerOrderId());
        log.debug("Applied payment.pending to order {}", orderId);
    }

    private void handleRefundProcessed(String body) {
        RefundPayload payload = readPayload(body, RefundPayload.class);
        UUID orderId = requireOrderId(payload.orderId());
        orderCommandService.markRefunded(orderId);
        log.debug("Applied payment.refund-processed to order {}", orderId);
    }

    /**
     * Reads the order id, refusing a payload that cannot be applied.
     *
     * <p>A message with no order id cannot be routed to an order, and acknowledging it would discard a
     * payment fact. Throwing lets the container's retry and dead-letter policy apply, so an operator
     * sees it rather than the event vanishing.
     */
    private UUID requireOrderId(PaymentPayload payload) {
        return requireOrderId(payload.orderId());
    }

    private UUID requireOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("A payment event arrived with no orderId");
        }
        try {
            return UUID.fromString(orderId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("A payment event arrived with an unreadable orderId", ex);
        }
    }

    private <T> T readPayload(String body, Class<T> type) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(body, new TypeReference<>() {
            });
            return objectMapper.convertValue(envelope.get("payload"), type);
        } catch (Exception ex) {
            // Rethrown so the container's retry and dead-letter policy applies: a malformed message is
            // not something to acknowledge and forget, it is something an operator needs to see.
            throw new IllegalArgumentException("Unreadable order event payload", ex);
        }
    }

    // The wire shapes, narrowed to the fields this service acts on. Nullable where a producer may not
    // send the field, so a schema addition on their side is not a crash here.

    private record PaymentPayload(
            String orderId,
            String providerPaymentId,
            String providerOrderId,
            String reason,
            Long amountMinorUnits,
            String currency) {
    }

    private record RefundPayload(String orderId) {
    }
}

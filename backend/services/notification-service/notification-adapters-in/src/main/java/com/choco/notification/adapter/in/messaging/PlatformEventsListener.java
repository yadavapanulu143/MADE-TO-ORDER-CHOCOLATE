package com.choco.notification.adapter.in.messaging;

import com.choco.events.envelope.EventTypes;
import com.choco.notification.application.command.NotifyCommand;
import com.choco.notification.application.service.NotificationDispatchService;
import com.choco.notification.application.template.Amounts;
import com.choco.notification.domain.notification.NotificationType;
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
 * Turns platform events into customer and seller messages.
 *
 * <p>This is the whole reason the notification service exists as a consumer rather than as a library
 * the other services call. Every service already publishes the facts of what happened; none of them
 * has to know that a customer should be emailed about it. Adding a message — or changing its wording —
 * is a change to this one listener, not to the order or payment service.
 *
 * <p><b>One queue, many event types, dispatched on the {@code eventType} header.</b> The bindings are
 * explicit per event rather than a {@code #} wildcard, so an event this service does not act on is
 * never delivered here at all. Each case states exactly which event it handles; an unbound or unknown
 * type is ignored rather than guessed at.
 *
 * <p><b>Every handler is idempotent through a dedupe key</b> derived from the event's own identity —
 * its event id where one is carried, otherwise a stable composite of the aggregate and the transition.
 * A redelivered event therefore finds the notification it already created and sends nothing. This is
 * what makes the listener safe to run with at-least-once delivery.
 *
 * <p><b>A handler never throws for a business reason.</b> A message that cannot be rendered — a
 * template variable an event did not carry — is a programming error and is allowed to propagate so it
 * dead-letters and an operator sees it. A provider outage, by contrast, is recorded on the notification
 * and retried, because the fact it describes has already happened elsewhere and must not be undone.
 */
@Component
class PlatformEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PlatformEventsListener.class);

    private final NotificationDispatchService dispatchService;
    private final ObjectMapper objectMapper;

    PlatformEventsListener(NotificationDispatchService dispatchService, ObjectMapper objectMapper) {
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = NotificationMessagingConfiguration.EVENTS_QUEUE)
    void onMessage(Message message) {
        String eventType = (String) message.getMessageProperties().getHeaders().get("eventType");
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        if (eventType == null) {
            log.warn("Received a notification message with no eventType header; ignoring");
            return;
        }
        Map<String, Object> payload = readPayload(body);
        switch (eventType) {
            case EventTypes.ORDER_PLACED -> orderPlaced(payload);
            case EventTypes.ORDER_CONFIRMED -> orderConfirmed(payload);
            case EventTypes.ORDER_CANCELLED -> orderCancelled(payload);
            case EventTypes.PAYMENT_CAPTURED -> paymentCaptured(payload);
            case EventTypes.PAYMENT_FAILED -> paymentFailed(payload);
            case EventTypes.PAYMENT_PENDING -> paymentPending(payload);
            case EventTypes.REFUND_PROCESSED -> refundProcessed(payload);
            case EventTypes.REFUND_REQUESTED -> refundRequested(payload);
            case EventTypes.DISPUTE_RAISED -> disputeRaised(payload);
            case EventTypes.LOW_STOCK_DETECTED -> lowStock(payload);
            case EventTypes.PRODUCTION_STARTED -> productionEvent(payload, NotificationType.PRODUCTION_STARTED);
            case EventTypes.PRODUCTION_COMPLETED ->
                    productionEvent(payload, NotificationType.PRODUCTION_COMPLETED);
            case EventTypes.READY_FOR_DISPATCH ->
                    productionEvent(payload, NotificationType.READY_FOR_DISPATCH);
            case EventTypes.DELIVERY_ASSIGNED -> deliveryEvent(payload, NotificationType.DELIVERY_ASSIGNED);
            case EventTypes.OUT_FOR_DELIVERY -> deliveryEvent(payload, NotificationType.OUT_FOR_DELIVERY);
            case EventTypes.DELIVERY_DELIVERED -> deliveryEvent(payload, NotificationType.DELIVERY_DELIVERED);
            case EventTypes.DELIVERY_FAILED -> deliveryEvent(payload, NotificationType.DELIVERY_FAILED);
            default -> log.debug("Ignoring event {} on the notification events queue", eventType);
        }
    }

    // --- Order ---------------------------------------------------------------------------------

    private void orderPlaced(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        dispatchService.notify(new NotifyCommand(NotificationType.ORDER_PLACED,
                dedupe("order.placed", orderId),
                orderId, str(payload, "customerId"),
                Map.of(
                        "orderNumber", str(payload, "orderNumber"),
                        "totalAmount", Amounts.format(lng(payload, "totalMinorUnits"),
                                str(payload, "currency")))));
    }

    private void orderConfirmed(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        dispatchService.notify(new NotifyCommand(NotificationType.ORDER_CONFIRMED,
                dedupe("order.confirmed", orderId),
                orderId, str(payload, "customerId"),
                Map.of("orderNumber", str(payload, "orderNumber"))));
    }

    private void orderCancelled(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        dispatchService.notify(new NotifyCommand(NotificationType.ORDER_CANCELLED,
                dedupe("order.cancelled", orderId),
                orderId, str(payload, "customerId"),
                Map.of(
                        "orderNumber", str(payload, "orderNumber"),
                        "reason", orDefault(str(payload, "reason"), "Not stated"))));
    }

    // --- Payment -------------------------------------------------------------------------------

    private void paymentCaptured(Map<String, Object> payload) {
        // Keyed on the provider payment id, not the order: a customer who retries a failed payment
        // gets a second capture for the same order, and each genuine capture deserves its own
        // confirmation, while a redelivery of one capture must not send a second.
        String paymentId = str(payload, "providerPaymentId");
        String orderId = str(payload, "orderId");
        dispatchService.notify(new NotifyCommand(NotificationType.PAYMENT_SUCCESS,
                dedupe("payment.captured", paymentId),
                orderId, str(payload, "customerId"),
                Map.of("amount", Amounts.format(lng(payload, "amountMinorUnits"),
                        str(payload, "currency")))));
    }

    private void paymentFailed(Map<String, Object> payload) {
        String paymentId = str(payload, "providerPaymentId");
        String orderId = str(payload, "orderId");
        dispatchService.notify(new NotifyCommand(NotificationType.PAYMENT_FAILED,
                dedupe("payment.failed", orDefault(paymentId, orderId)),
                orderId, str(payload, "customerId"),
                Map.of(
                        "amount", Amounts.format(lng(payload, "amountMinorUnits"), str(payload, "currency")),
                        "reason", orDefault(str(payload, "reason"), "The payment could not be completed"))));
    }

    private void paymentPending(Map<String, Object> payload) {
        String paymentId = str(payload, "providerPaymentId");
        String orderId = str(payload, "orderId");
        dispatchService.notify(new NotifyCommand(NotificationType.PAYMENT_PENDING,
                dedupe("payment.pending", orDefault(paymentId, orderId)),
                orderId, str(payload, "customerId"),
                Map.of("amount", Amounts.format(lng(payload, "amountMinorUnits"),
                        str(payload, "currency")))));
    }

    private void refundProcessed(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        dispatchService.notify(new NotifyCommand(NotificationType.REFUND_PROCESSED,
                dedupe("payment.refund-processed", orDefault(str(payload, "providerRefundId"), orderId)),
                orderId, str(payload, "customerId"),
                Map.of(
                        "orderNumber", orDefault(str(payload, "orderNumber"), orderId),
                        "amount", Amounts.format(lng(payload, "amountMinorUnits"),
                                str(payload, "currency")))));
    }

    // --- Seller-addressed ----------------------------------------------------------------------

    private void refundRequested(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        dispatchService.notify(new NotifyCommand(NotificationType.REFUND_REQUESTED,
                dedupe("order.refund-requested", orderId),
                orderId, null,
                Map.of(
                        "orderNumber", orDefault(str(payload, "orderNumber"), orderId),
                        "amount", Amounts.format(lng(payload, "amountMinorUnits"),
                                str(payload, "currency")),
                        "reason", orDefault(str(payload, "reason"), "Not stated"))));
    }

    private void disputeRaised(Map<String, Object> payload) {
        String paymentId = str(payload, "paymentId");
        dispatchService.notify(new NotifyCommand(NotificationType.DISPUTE_RAISED,
                dedupe("payment.dispute-raised", orDefault(str(payload, "providerDisputeId"), paymentId)),
                str(payload, "orderId"), null,
                Map.of(
                        "orderNumber", orDefault(str(payload, "orderId"), "-"),
                        "amount", Amounts.format(lng(payload, "amountMinorUnits"),
                                str(payload, "currency")),
                        "reason", orDefault(str(payload, "reason"), "Not stated"))));
    }

    private void lowStock(Map<String, Object> payload) {
        // The dedupe key is the event id, so a single crossing of the threshold alerts once even if the
        // event is redelivered, while a later crossing — restocked and low again — alerts again. Keying
        // on the ingredient id instead would suppress every crossing after the first, silently.
        String ingredientId = str(payload, "ingredientId");
        String unit = str(payload, "unit");
        java.util.Map<String, String> variables = new java.util.HashMap<>();
        variables.put("ingredientName", orDefault(str(payload, "ingredientName"), "An ingredient"));
        variables.put("onHand", formatStock(lng(payload, "onHandMilliUnits"), unit));
        variables.put("minimum", formatStock(lng(payload, "minimumMilliUnits"), unit));
        dispatchService.notify(new NotifyCommand(NotificationType.LOW_STOCK_ALERT,
                dedupe("stock.low", orDefault(str(payload, "eventId"), ingredientId)),
                ingredientId, null, variables));
    }

    // --- Production and delivery ---------------------------------------------------------------

    private void productionEvent(Map<String, Object> payload, NotificationType type) {
        String orderId = str(payload, "orderId");
        dispatchService.notify(new NotifyCommand(type,
                dedupe(type.name(), orDefault(str(payload, "jobId"), orderId)),
                orderId, str(payload, "customerId"),
                Map.of("orderNumber", orDefault(str(payload, "orderNumber"), orderId))));
    }

    private void deliveryEvent(Map<String, Object> payload, NotificationType type) {
        String orderId = str(payload, "orderId");
        java.util.Map<String, String> variables = new java.util.HashMap<>();
        variables.put("orderNumber", orDefault(str(payload, "orderNumber"), orderId));
        if (type == NotificationType.DELIVERY_FAILED) {
            // The fulfilment service's DeliveryFailed event carries the reason as failureReason; reading
            // "reason" here would silently fall back to the generic text and the customer would never
            // learn why the delivery failed.
            variables.put("reason", orDefault(str(payload, "failureReason"),
                    "The delivery could not be completed"));
        }
        dispatchService.notify(new NotifyCommand(type,
                dedupe(type.name(), orDefault(str(payload, "deliveryId"), orderId)),
                orderId, str(payload, "customerId"), variables));
    }

    // --- Helpers -------------------------------------------------------------------------------

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
            // Rethrown so the container's retry and dead-letter policy applies: a malformed message is
            // something an operator needs to see, not something to acknowledge and forget.
            throw new IllegalArgumentException("Unreadable notification event payload", ex);
        }
    }

    /**
     * Builds a dedupe key from the event type and the strongest identifier the event carries.
     *
     * <p>The key is scoped by event type so the same id under two events — an order id on both
     * {@code order.placed} and {@code order.confirmed} — produces two notifications rather than one
     * suppressing the other.
     */
    private static String dedupe(String eventType, String id) {
        return eventType + ":" + (id == null ? "unknown" : id);
    }

    private static String str(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static long lng(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Renders a stock level, carried as thousandths of the ingredient's unit, as a readable figure.
     *
     * <p>The seller sees "250 g", not "250000". The unit is free text from the ingredient record (g,
     * ml, kg, …) and is appended when present; a missing unit still yields the bare number rather than
     * an error, because an ingredient with no unit is a data-entry gap, not a reason to lose the alert.
     */
    private static String formatStock(long milliUnits, String unit) {
        java.math.BigDecimal units = java.math.BigDecimal.valueOf(milliUnits, 3).stripTrailingZeros();
        String number = units.toPlainString();
        return unit == null || unit.isBlank() ? number : number + " " + unit;
    }
}

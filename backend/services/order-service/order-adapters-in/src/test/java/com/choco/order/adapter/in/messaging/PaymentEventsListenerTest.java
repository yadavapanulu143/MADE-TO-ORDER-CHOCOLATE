package com.choco.order.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.choco.events.envelope.EventTypes;
import com.choco.order.application.service.InvoiceService;
import com.choco.order.application.service.OrderCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/**
 * The listener's parsing and dispatch contract, in isolation.
 *
 * <p>The saga is proven end to end against a real broker and database in {@code PaymentEventsIT}. This
 * test covers the awkward inputs that are tedious to arrange there and that matter most: a payload the
 * consumer cannot read, and a payment event that names no order. Both must fail loudly so the
 * container's retry and dead-letter policy applies, because acknowledging either would silently discard
 * a payment fact.
 *
 * <p>Only the collaborators the listener does not own are mocked — the use cases it calls — so the
 * assertions are about the listener's own behaviour: what it passes through, and when it refuses to act.
 */
@DisplayName("Payment events listener")
class PaymentEventsListenerTest {

    private OrderCommandService orderCommandService;
    private InvoiceService invoiceService;
    private PaymentEventsListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        orderCommandService = mock(OrderCommandService.class);
        invoiceService = mock(InvoiceService.class);
        listener = new PaymentEventsListener(orderCommandService, invoiceService, objectMapper);
    }

    @Test
    @DisplayName("a capture is passed through with the provider ids and the amount")
    void capture_is_dispatched() {
        UUID orderId = UUID.randomUUID();
        listener.onMessage(message(EventTypes.PAYMENT_CAPTURED, Map.of(
                "orderId", orderId.toString(),
                "providerPaymentId", "pay_1",
                "providerOrderId", "order_1",
                "amountMinorUnits", 54_900,
                "currency", "INR")));

        verify(orderCommandService).markPaid(eq(orderId), eq("pay_1"), eq("order_1"), eq(54_900L), eq("INR"));
        // The invoice is issued in the same consumption, after the order is confirmed.
        verify(invoiceService).issueForOrder(orderId);
    }

    @Test
    @DisplayName("a failure does not issue an invoice")
    void failure_does_not_issue_an_invoice() {
        UUID orderId = UUID.randomUUID();
        listener.onMessage(message(EventTypes.PAYMENT_FAILED, Map.of(
                "orderId", orderId.toString(),
                "reason", "card declined")));

        verify(orderCommandService).markPaymentFailed(eq(orderId), eq("card declined"));
        verify(invoiceService, never()).issueForOrder(any());
    }

    @Test
    @DisplayName("a failure is passed through with its reason")
    void failure_is_dispatched() {
        UUID orderId = UUID.randomUUID();
        listener.onMessage(message(EventTypes.PAYMENT_FAILED, Map.of(
                "orderId", orderId.toString(),
                "reason", "card declined")));

        verify(orderCommandService).markPaymentFailed(eq(orderId), eq("card declined"));
    }

    @Test
    @DisplayName("a pending event is passed through")
    void pending_is_dispatched() {
        UUID orderId = UUID.randomUUID();
        listener.onMessage(message(EventTypes.PAYMENT_PENDING, Map.of(
                "orderId", orderId.toString(),
                "providerPaymentId", "pay_1",
                "providerOrderId", "order_1")));

        verify(orderCommandService).markPaymentVerificationPending(eq(orderId), eq("pay_1"), eq("order_1"));
    }

    @Test
    @DisplayName("a processed refund is passed through")
    void refund_is_dispatched() {
        UUID orderId = UUID.randomUUID();
        listener.onMessage(message(EventTypes.REFUND_PROCESSED, Map.of("orderId", orderId.toString())));

        verify(orderCommandService).markRefunded(eq(orderId));
    }

    @Test
    @DisplayName("an unreadable payload is not acknowledged")
    void unreadable_payload_is_rejected() {
        Message message = new Message("not json at all".getBytes(StandardCharsets.UTF_8), properties(
                EventTypes.PAYMENT_CAPTURED));

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderCommandService, never()).markPaid(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a payment event naming no order is not acknowledged")
    void missing_order_id_is_rejected() {
        Message message = message(EventTypes.PAYMENT_CAPTURED, Map.of("providerPaymentId", "pay_1"));

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderCommandService, never()).markPaid(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an event this service does not handle is ignored, not failed")
    void unknown_event_is_ignored() {
        listener.onMessage(message("payment.dispute-raised", Map.of("orderId", UUID.randomUUID().toString())));

        verify(orderCommandService, never()).markPaid(any(), any(), any(), any(), any());
        verify(orderCommandService, never()).markPaymentFailed(any(), any());
    }

    // ---------------------------------------------------------------------------------------------

    /** Builds a message in the wire shape the outbox relay produces: an envelope with a payload. */
    private Message message(String eventType, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID());
        envelope.put("eventType", eventType);
        envelope.put("schemaVersion", 1);
        envelope.put("aggregateId", payload.getOrDefault("orderId", "unknown"));
        envelope.put("occurredAt", Instant.now());
        envelope.put("producer", "payment-service");
        envelope.put("payload", payload);
        try {
            return new Message(objectMapper.writeValueAsBytes(envelope), properties(eventType));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MessageProperties properties(String eventType) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("eventType", eventType);
        return properties;
    }

    @Test
    @DisplayName("a message with no eventType header is ignored rather than guessed at")
    void missing_header_is_ignored() {
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        listener.onMessage(message);
        verify(orderCommandService, never()).markPaid(any(), any(), any(), any(), any());
    }
}

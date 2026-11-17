package com.choco.fulfilment.adapter.in.messaging;

import com.choco.events.envelope.EventTypes;
import com.choco.fulfilment.application.command.OrderCancelledCommand;
import com.choco.fulfilment.application.command.OrderConfirmedCommand;
import com.choco.fulfilment.application.service.ProductionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the order service's hand-over and cancellation events.
 *
 * <p>This is the only way a production job is created. There is no endpoint, and no other event, that
 * opens one — so the rule "no chocolate is made before a payment is verified" is enforced by the shape
 * of the system, not by a check somebody could forget to add.
 *
 * <p><b>The message is read as a raw JSON string, not as a bound object.</b> The wire format is the
 * shared {@code EventEnvelope}; binding it to this service's own classes would couple the consumer to
 * the producer's Java types, and a rename over there would be a runtime failure here rather than a
 * contract change. The envelope's {@code payload} is read into a local record that states exactly the
 * fields this service depends on.
 *
 * <p><b>Idempotent by construction.</b> A redelivered {@code order.confirmed} finds the jobs it already
 * opened and opens nothing new, keyed on (order id, variant id); a redelivered {@code order.cancelled}
 * finds the jobs already cancelled and does nothing. So a broker retry, or a consumer restart before
 * the acknowledgement, cannot double the kitchen's work or return ingredients twice.
 */
@Component
class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ProductionService productionService;
    private final ObjectMapper objectMapper;

    OrderEventsListener(ProductionService productionService, ObjectMapper objectMapper) {
        this.productionService = productionService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = FulfilmentMessagingConfiguration.QUEUE)
    void onMessage(Message message) {
        String eventType = (String) message.getMessageProperties().getHeaders().get("eventType");
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        if (eventType == null) {
            log.warn("Received a fulfilment message with no eventType header; ignoring");
            return;
        }
        switch (eventType) {
            case EventTypes.ORDER_CONFIRMED -> handleConfirmed(body);
            case EventTypes.ORDER_CANCELLED -> handleCancelled(body);
            default -> log.debug("Ignoring event {} on the fulfilment queue", eventType);
        }
    }

    private void handleConfirmed(String body) {
        ConfirmedPayload payload = readPayload(body, ConfirmedPayload.class);
        productionService.acceptConfirmedOrder(new OrderConfirmedCommand(
                payload.orderId(),
                payload.customerId(),
                payload.orderNumber(),
                payload.customerName(),
                payload.deliveryAddress() == null ? null : new OrderConfirmedCommand.Address(
                        payload.deliveryAddress().recipientName(),
                        payload.deliveryAddress().mobile(),
                        payload.deliveryAddress().line1(),
                        payload.deliveryAddress().line2(),
                        payload.deliveryAddress().city(),
                        payload.deliveryAddress().state(),
                        payload.deliveryAddress().pincode(),
                        payload.deliveryAddress().deliveryInstructions()),
                payload.deliveryChargeMinorUnits(),
                payload.lines() == null ? List.of() : payload.lines().stream()
                        .map(line -> new OrderConfirmedCommand.Line(line.productId(), line.variantId(),
                                line.productName(), line.variantLabel(), line.quantity()))
                        .toList()));
    }

    private void handleCancelled(String body) {
        CancelledPayload payload = readPayload(body, CancelledPayload.class);
        productionService.handleOrderCancelled(
                new OrderCancelledCommand(payload.orderId(), payload.reason()));
    }

    private <T> T readPayload(String body, Class<T> type) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(body, new TypeReference<>() {
            });
            return objectMapper.convertValue(envelope.get("payload"), type);
        } catch (Exception ex) {
            // Rethrown so the container's retry and dead-letter policy applies: a malformed message is
            // not something to acknowledge and forget, it is something an operator needs to see.
            throw new IllegalArgumentException("Unreadable fulfilment event payload", ex);
        }
    }

    // The wire shapes, narrowed to the fields this service acts on. Nullable where a producer may not
    // send the field, so a schema addition on their side is not a crash here.

    private record ConfirmedPayload(
            String orderId,
            String customerId,
            String orderNumber,
            String customerName,
            AddressPayload deliveryAddress,
            long deliveryChargeMinorUnits,
            List<LinePayload> lines) {
    }

    private record AddressPayload(
            String recipientName,
            String mobile,
            String line1,
            String line2,
            String city,
            String state,
            String pincode,
            String deliveryInstructions) {
    }

    private record LinePayload(
            String productId,
            String variantId,
            String productName,
            String variantLabel,
            int quantity) {
    }

    private record CancelledPayload(String orderId, String reason) {
    }
}

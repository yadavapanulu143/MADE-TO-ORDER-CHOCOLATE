package com.choco.order.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Raised when a verified payment moves the order into preparation.
 *
 * <p>This is the event the made-to-order process waits for: preparation must not begin until a
 * payment has been verified, so the fulfilment service keys off this and never off {@code order.placed}.
 *
 * <p>Carries the confirmed lines and the delivery details, not just the order id. The fulfilment
 * service opens one production job per line and, once the chocolate is packed, a delivery to the
 * address that was agreed; without this detail it would have to call back into this service
 * synchronously at consumption time — an extra dependency and a failure mode where a redelivered
 * event cannot be processed because the order service is briefly down. The facts carried are the ones
 * frozen at checkout, so the fulfilment service makes and delivers exactly what the customer bought
 * to the address they chose, whatever the catalogue or their address book says later.
 */
public record OrderConfirmed(
        UUID eventId,
        Instant occurredAt,
        String orderId,
        String customerId,
        String orderNumber,
        String paymentId,
        String customerName,
        ConfirmedAddress deliveryAddress,
        long deliveryChargeMinorUnits,
        String currency,
        List<ConfirmedLine> lines) implements DomainEvent {

    public OrderConfirmed {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** One confirmed order line, in the shape the kitchen needs to open a job. */
    public record ConfirmedLine(
            String productId,
            String variantId,
            String productName,
            String variantLabel,
            int quantity) {
    }

    /** The delivery address frozen at checkout, in the shape the delivery track needs. */
    public record ConfirmedAddress(
            String recipientName,
            String mobile,
            String line1,
            String line2,
            String city,
            String state,
            String pincode,
            String deliveryInstructions) {
    }

    @Override
    public String eventType() {
        return "order.confirmed";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}

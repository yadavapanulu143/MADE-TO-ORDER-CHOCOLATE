package com.choco.order.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when an invoice is issued for a confirmed order.
 *
 * <p>Carries the invoice number and the billed total, not just the order id, so a consumer — the
 * notification service telling the customer their invoice is ready, or a reporting read model — can act
 * without calling back into this service. The total is the figure frozen on the invoice; it is a fact
 * about the document, not a live recalculation of the order.
 */
public record InvoiceIssued(
        UUID eventId,
        Instant occurredAt,
        String invoiceId,
        String orderId,
        String orderNumber,
        String invoiceNumber,
        String customerId,
        long totalMinorUnits,
        String currency) implements DomainEvent {

    @Override
    public String eventType() {
        return "order.invoice-issued";
    }

    @Override
    public String aggregateId() {
        return orderId;
    }
}

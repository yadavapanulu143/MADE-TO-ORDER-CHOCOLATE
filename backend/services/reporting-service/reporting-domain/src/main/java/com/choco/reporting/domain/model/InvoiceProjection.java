package com.choco.reporting.domain.model;

import java.time.Instant;

/**
 * The reporting view of an issued invoice, projected from the order service's event.
 *
 * <p>Carries the invoice number and the billed total, so a sales report can reconcile what was invoiced
 * against what was captured without calling back into the order service.
 */
public record InvoiceProjection(
        String invoiceId,
        String orderId,
        String orderNumber,
        String invoiceNumber,
        String customerId,
        long totalMinorUnits,
        String currency,
        Instant issuedAt) {
}

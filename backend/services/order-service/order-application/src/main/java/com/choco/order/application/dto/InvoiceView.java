package com.choco.order.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A complete invoice as presented to a customer or the seller.
 *
 * <p>Carries every figure twice — exact minor units for a programmatic caller and a decimal string for
 * display — for the same reason an order does: a client cannot reintroduce a floating-point rounding
 * error from a string. The invoice number is the reference printed on the document; the id is what the
 * API keys on.
 */
public record InvoiceView(
        UUID id,
        String invoiceNumber,
        UUID orderId,
        String orderNumber,
        UUID customerId,
        String customerName,
        String customerEmail,
        String customerMobile,
        InvoiceAddressView billingAddress,
        List<InvoiceLineView> lines,
        int totalItems,
        long itemsTotalMinorUnits,
        String itemsTotal,
        long deliveryChargeMinorUnits,
        String deliveryCharge,
        long totalMinorUnits,
        String total,
        String currency,
        Instant issuedAt) {
}

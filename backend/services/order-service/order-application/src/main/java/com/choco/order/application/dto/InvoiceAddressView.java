package com.choco.order.application.dto;

/**
 * The address an invoice is billed to, with a single-line rendering for the document.
 *
 * <p>The delivery instructions a checkout may carry are deliberately absent: they belong on a
 * delivery, not on a financial document.
 */
public record InvoiceAddressView(
        String recipientName,
        String mobile,
        String line1,
        String line2,
        String city,
        String state,
        String pincode,
        String formatted) {
}

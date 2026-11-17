package com.choco.order.application.port;

import java.time.Instant;

/**
 * Produces the human-readable invoice number shown on the document.
 *
 * <p>Separate from {@link OrderNumberGenerator} because an invoice number is a financial-document
 * reference, not an order reference: it must be unique on its own, never reused, and readable on its
 * own. The adapter backs it with a database sequence, so two concurrent issuances cannot be handed the
 * same number and deleting an invoice cannot free its number for reuse.
 */
public interface InvoiceNumberGenerator {

    String next(Instant now);
}

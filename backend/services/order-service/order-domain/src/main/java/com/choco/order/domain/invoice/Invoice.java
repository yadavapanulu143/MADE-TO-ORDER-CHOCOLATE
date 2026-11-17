package com.choco.order.domain.invoice;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.order.domain.event.InvoiceIssued;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An invoice issued for a confirmed order.
 *
 * <p>An invoice is a <em>record</em>, not a workflow. Once issued it is never edited: a correction is
 * a credit note, not a rewrite of the original, because the document is the evidence of what the
 * customer was charged. The aggregate therefore has no update methods at all — only {@link #issue},
 * which is called exactly once per order.
 *
 * <p>Everything it shows is a snapshot taken from the order at issuance: the customer's name and
 * contact, the delivery address and every priced line. The order itself is also a snapshot of the
 * catalogue, so an invoice rendered today shows the same figures as one rendered a year from now, even
 * if the product was renamed, repriced or withdrawn in the meantime.
 *
 * <p>One invoice per order is enforced both here and by a unique index on {@code order_id}: issuing a
 * second invoice for the same order is a duplicate, not a re-issue.
 */
public class Invoice extends AggregateRoot {

    private final UUID id;
    private final String invoiceNumber;
    private final UUID orderId;
    private final String orderNumber;
    private final UUID customerId;
    private final CustomerDetails customer;
    private final InvoiceAddress billingAddress;
    private final List<InvoiceLine> lines;
    private final Money itemsTotal;
    private final Money deliveryCharge;
    private final Money total;
    private final Instant issuedAt;

    private Invoice(UUID id, String invoiceNumber, UUID orderId, String orderNumber, UUID customerId,
                    CustomerDetails customer, InvoiceAddress billingAddress, List<InvoiceLine> lines,
                    Money deliveryCharge, Instant issuedAt) {
        this.id = id;
        this.invoiceNumber = requireInvoiceNumber(invoiceNumber);
        if (orderId == null) {
            throw new IllegalArgumentException("Order id is required");
        }
        this.orderId = orderId;
        this.orderNumber = requireText(orderNumber, "Order number", 40);
        if (customerId == null) {
            throw new IllegalArgumentException("Customer id is required");
        }
        this.customerId = customerId;
        if (customer == null) {
            throw new IllegalArgumentException("Customer details are required");
        }
        if (billingAddress == null) {
            throw new IllegalArgumentException("A billing address is required");
        }
        this.customer = customer;
        this.billingAddress = billingAddress;
        this.lines = new ArrayList<>(requireLines(lines));
        this.itemsTotal = sumLines(this.lines);
        this.deliveryCharge = requireCharge(deliveryCharge);
        this.total = this.itemsTotal.plus(this.deliveryCharge);
        if (issuedAt == null) {
            throw new IllegalArgumentException("Issue time is required");
        }
        this.issuedAt = issuedAt;
    }

    /**
     * Issues an invoice for a confirmed order.
     *
     * <p>The invoice number is supplied by the application layer from a database sequence, never from
     * a count of invoices: two concurrent issuances must not be handed the same number, and deleting an
     * invoice must not free its number for reuse.
     */
    public static Invoice issue(UUID id, String invoiceNumber, UUID orderId, String orderNumber,
                                UUID customerId, CustomerDetails customer, InvoiceAddress billingAddress,
                                List<InvoiceLine> lines, Money deliveryCharge, Instant now) {
        Invoice invoice = new Invoice(id, invoiceNumber, orderId, orderNumber, customerId, customer,
                billingAddress, lines, deliveryCharge, now);
        invoice.registerEvent(new InvoiceIssued(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                orderNumber, invoiceNumber, customerId.toString(), invoice.total.minorUnits(),
                invoice.total.currency().getCurrencyCode()));
        return invoice;
    }

    /**
     * Rebuilds an already-issued invoice from storage.
     *
     * <p>Never raises {@code invoice.issued}: reading a past invoice is not issuing one, and re-raising
     * the event on every load would replay it to every consumer. The distinction between {@link #issue}
     * and this method is the same one the order aggregate draws between {@code place} and
     * {@code rehydrate}.
     */
    public static Invoice rehydrate(UUID id, String invoiceNumber, UUID orderId, String orderNumber,
                                    UUID customerId, CustomerDetails customer,
                                    InvoiceAddress billingAddress, List<InvoiceLine> lines,
                                    Money deliveryCharge, Instant issuedAt) {
        return new Invoice(id, invoiceNumber, orderId, orderNumber, customerId, customer,
                billingAddress, lines, deliveryCharge, issuedAt);
    }

    public UUID id() {
        return id;
    }

    public String invoiceNumber() {
        return invoiceNumber;
    }

    public UUID orderId() {
        return orderId;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public UUID customerId() {
        return customerId;
    }

    public CustomerDetails customer() {
        return customer;
    }

    public InvoiceAddress billingAddress() {
        return billingAddress;
    }

    public List<InvoiceLine> lines() {
        return List.copyOf(lines);
    }

    public Money itemsTotal() {
        return itemsTotal;
    }

    public Money deliveryCharge() {
        return deliveryCharge;
    }

    public Money total() {
        return total;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    private static List<InvoiceLine> requireLines(List<InvoiceLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An invoice must have at least one line");
        }
        return List.copyOf(lines);
    }

    private static Money sumLines(List<InvoiceLine> lines) {
        Money sum = Money.zero(Money.INR);
        for (InvoiceLine line : lines) {
            sum = sum.plus(line.lineTotal());
        }
        return sum;
    }

    private static Money requireCharge(Money charge) {
        if (charge == null) {
            throw new IllegalArgumentException("A delivery charge is required");
        }
        if (charge.isNegative()) {
            throw new IllegalArgumentException("The delivery charge must not be negative");
        }
        return charge;
    }

    private static String requireInvoiceNumber(String value) {
        return requireText(value, "Invoice number", 40);
    }

    private static String requireText(String value, String field, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    /** The customer's contact details as billed, frozen at issuance. */
    public record CustomerDetails(String fullName, EmailAddress email, PhoneNumber mobile) {

        public CustomerDetails {
            if (fullName == null || fullName.isBlank()) {
                throw new IllegalArgumentException("Customer name is required");
            }
            fullName = fullName.trim().replaceAll("\\s+", " ");
            if (email == null) {
                throw new IllegalArgumentException("Customer email is required");
            }
            if (mobile == null) {
                throw new IllegalArgumentException("Customer mobile number is required");
            }
        }
    }

    /**
     * The address the invoice is billed to, frozen at issuance.
     *
     * <p>For this single-seller made-to-order business the billing address is the delivery address the
     * customer chose; the record is kept as its own value object so a future billing address (for a
     * business buyer, say) is a field addition rather than a redesign of the invoice.
     */
    public record InvoiceAddress(
            String recipientName,
            PhoneNumber mobile,
            String line1,
            String line2,
            String city,
            String state,
            Pincode pincode) {

        public InvoiceAddress {
            recipientName = requireText(recipientName, "Recipient name", 150);
            if (mobile == null) {
                throw new IllegalArgumentException("Recipient mobile number is required");
            }
            line1 = requireText(line1, "Address line 1", 200);
            line2 = line2 == null || line2.isBlank() ? null : requireText(line2, "Address line 2", 200);
            city = requireText(city, "City", 100);
            state = requireText(state, "State", 100);
            if (pincode == null) {
                throw new IllegalArgumentException("PIN code is required");
            }
        }

        /** A single-line rendering for the invoice document. */
        public String formatted() {
            StringBuilder builder = new StringBuilder(recipientName).append(", ").append(line1);
            if (line2 != null) {
                builder.append(", ").append(line2);
            }
            return builder.append(", ").append(city).append(", ").append(state)
                    .append(" ").append(pincode.value()).toString();
        }
    }
}
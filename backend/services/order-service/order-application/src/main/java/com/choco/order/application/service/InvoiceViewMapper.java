package com.choco.order.application.service;

import com.choco.core.domain.Money;
import com.choco.order.application.dto.InvoiceAddressView;
import com.choco.order.application.dto.InvoiceLineView;
import com.choco.order.application.dto.InvoiceView;
import com.choco.order.domain.invoice.Invoice;
import com.choco.order.domain.invoice.InvoiceLine;
import java.math.RoundingMode;

/**
 * Turns an invoice aggregate into the record the API returns.
 *
 * <p>Written by hand rather than reflected, so a new field on the aggregate is not exposed until it is
 * deliberately added here — the same reasoning as the order mapper, and the same safe default of
 * leaking nothing. Money is emitted twice, exact minor units and a decimal string, never a
 * {@code double}.
 */
public final class InvoiceViewMapper {

    private InvoiceViewMapper() {
    }

    public static InvoiceView toView(Invoice invoice) {
        return new InvoiceView(
                invoice.id(),
                invoice.invoiceNumber(),
                invoice.orderId(),
                invoice.orderNumber(),
                invoice.customerId(),
                invoice.customer().fullName(),
                invoice.customer().email().value(),
                invoice.customer().mobile().value(),
                toAddressView(invoice),
                invoice.lines().stream().map(InvoiceViewMapper::toLineView).toList(),
                totalItems(invoice),
                invoice.itemsTotal().minorUnits(),
                decimal(invoice.itemsTotal()),
                invoice.deliveryCharge().minorUnits(),
                decimal(invoice.deliveryCharge()),
                invoice.total().minorUnits(),
                decimal(invoice.total()),
                invoice.total().currency().getCurrencyCode(),
                invoice.issuedAt());
    }

    private static InvoiceAddressView toAddressView(Invoice invoice) {
        Invoice.InvoiceAddress address = invoice.billingAddress();
        return new InvoiceAddressView(
                address.recipientName(),
                address.mobile().withCountryCode(),
                address.line1(),
                address.line2(),
                address.city(),
                address.state(),
                address.pincode().value(),
                address.formatted());
    }

    private static InvoiceLineView toLineView(InvoiceLine line) {
        return new InvoiceLineView(
                line.productId(),
                line.variantId(),
                line.productName(),
                line.variantLabel(),
                line.sku(),
                line.unitPrice().minorUnits(),
                decimal(line.unitPrice()),
                line.quantity().value(),
                line.lineTotal().minorUnits(),
                decimal(line.lineTotal()));
    }

    private static int totalItems(Invoice invoice) {
        return invoice.lines().stream().mapToInt(line -> line.quantity().value()).sum();
    }

    private static String decimal(Money money) {
        return money.toMajorUnits().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}

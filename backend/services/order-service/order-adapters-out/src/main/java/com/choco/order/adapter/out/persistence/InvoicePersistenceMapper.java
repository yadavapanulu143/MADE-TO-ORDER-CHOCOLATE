package com.choco.order.adapter.out.persistence;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.order.domain.invoice.Invoice;
import com.choco.order.domain.invoice.InvoiceLine;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Translates between the invoice JPA entities and the domain aggregate.
 *
 * <p>Explicit rather than reflective, so the compiler catches a mismatch and a reviewer can see which
 * column feeds which business field. Loads go through {@link Invoice#rehydrate}, never {@code issue},
 * so reading an invoice cannot re-raise {@code invoice.issued}.
 */
final class InvoicePersistenceMapper {

    private InvoicePersistenceMapper() {
    }

    static InvoiceJpaEntity toEntity(Invoice invoice) {
        InvoiceJpaEntity entity = new InvoiceJpaEntity();
        entity.setId(invoice.id());
        entity.setInvoiceNumber(invoice.invoiceNumber());
        entity.setOrderId(invoice.orderId());
        entity.setOrderNumber(invoice.orderNumber());
        entity.setCustomerId(invoice.customerId());
        entity.setCustomerName(invoice.customer().fullName());
        entity.setCustomerEmail(invoice.customer().email().value());
        entity.setCustomerMobile(invoice.customer().mobile().value());

        Invoice.InvoiceAddress address = invoice.billingAddress();
        entity.setBillingRecipientName(address.recipientName());
        entity.setBillingMobile(address.mobile().value());
        entity.setBillingLine1(address.line1());
        entity.setBillingLine2(address.line2());
        entity.setBillingCity(address.city());
        entity.setBillingState(address.state());
        entity.setBillingPincode(address.pincode().value());

        entity.setItemsTotalMinorUnits(invoice.itemsTotal().minorUnits());
        entity.setDeliveryChargeMinorUnits(invoice.deliveryCharge().minorUnits());
        entity.setTotalMinorUnits(invoice.total().minorUnits());
        entity.setCurrency(invoice.total().currency().getCurrencyCode());
        entity.setIssuedAt(invoice.issuedAt());

        List<InvoiceLineJpaEntity> lines = new ArrayList<>();
        List<InvoiceLine> domainLines = invoice.lines();
        for (int i = 0; i < domainLines.size(); i++) {
            lines.add(toEntity(entity, domainLines.get(i), i));
        }
        entity.setLines(lines);
        return entity;
    }

    static Invoice toDomain(InvoiceJpaEntity entity) {
        Invoice.CustomerDetails customer = new Invoice.CustomerDetails(entity.getCustomerName(),
                EmailAddress.of(entity.getCustomerEmail()), PhoneNumber.of(entity.getCustomerMobile()));
        Invoice.InvoiceAddress address = new Invoice.InvoiceAddress(entity.getBillingRecipientName(),
                PhoneNumber.of(entity.getBillingMobile()), entity.getBillingLine1(),
                entity.getBillingLine2(), entity.getBillingCity(), entity.getBillingState(),
                Pincode.of(entity.getBillingPincode()));
        List<InvoiceLine> lines = entity.getLines().stream()
                .map(InvoicePersistenceMapper::toDomain)
                .toList();
        return Invoice.rehydrate(entity.getId(), entity.getInvoiceNumber(), entity.getOrderId(),
                entity.getOrderNumber(), entity.getCustomerId(), customer, address, lines,
                Money.ofMinorUnits(entity.getDeliveryChargeMinorUnits(),
                        Currency.getInstance(entity.getCurrency())),
                entity.getIssuedAt());
    }

    private static InvoiceLineJpaEntity toEntity(InvoiceJpaEntity invoice, InvoiceLine line,
                                                 int displayOrder) {
        InvoiceLineJpaEntity entity = new InvoiceLineJpaEntity();
        entity.setInvoice(invoice);
        // A line has no identity of its own in the domain, but the row needs a primary key. Deriving
        // it from the invoice and the variant keeps it stable across saves, so an update is an update
        // rather than a delete-then-insert that would trip the (invoice, variant) unique constraint.
        entity.setId(lineId(invoice.getId(), line.variantId()));
        entity.setProductId(line.productId());
        entity.setVariantId(line.variantId());
        entity.setProductName(line.productName());
        entity.setVariantLabel(line.variantLabel());
        entity.setSku(line.sku());
        entity.setUnitPriceMinorUnits(line.unitPrice().minorUnits());
        entity.setCurrency(line.unitPrice().currency().getCurrencyCode());
        entity.setQuantity(line.quantity().value());
        entity.setDisplayOrder(displayOrder);
        return entity;
    }

    private static InvoiceLine toDomain(InvoiceLineJpaEntity entity) {
        return new InvoiceLine(entity.getProductId(), entity.getVariantId(), entity.getProductName(),
                entity.getVariantLabel(), entity.getSku(),
                Money.ofMinorUnits(entity.getUnitPriceMinorUnits(),
                        Currency.getInstance(entity.getCurrency())),
                Quantity.of(entity.getQuantity()));
    }

    private static UUID lineId(UUID invoiceId, UUID variantId) {
        return UUID.nameUUIDFromBytes((invoiceId + "|" + variantId).getBytes(StandardCharsets.UTF_8));
    }
}

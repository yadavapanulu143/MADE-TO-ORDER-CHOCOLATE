package com.choco.order.domain.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.order.domain.event.InvoiceIssued;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Invoice aggregate")
class InvoiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID ORDER = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CUSTOMER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VARIANT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    @DisplayName("the total is derived from the lines and the delivery charge, never supplied")
    void totalIsDerived() {
        Invoice invoice = issue(line(Money.inr(49900), 2), Money.inr(5000));

        assertThat(invoice.itemsTotal()).isEqualTo(Money.inr(99800));
        assertThat(invoice.deliveryCharge()).isEqualTo(Money.inr(5000));
        assertThat(invoice.total()).isEqualTo(Money.inr(104800));
    }

    @Test
    @DisplayName("issuing raises invoice.issued with the number and the billed total")
    void issuingRaisesEvent() {
        Invoice invoice = issue(line(Money.inr(49900), 1), Money.inr(0));

        assertThat(invoice.peekDomainEvents()).hasSize(1);
        InvoiceIssued event = (InvoiceIssued) invoice.peekDomainEvents().getFirst();
        assertThat(event.eventType()).isEqualTo("order.invoice-issued");
        assertThat(event.invoiceNumber()).isEqualTo("INV-1001");
        assertThat(event.orderId()).isEqualTo(ORDER.toString());
        assertThat(event.totalMinorUnits()).isEqualTo(49900);
        assertThat(event.currency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("rehydrating a stored invoice raises nothing, so a read is not a re-issue")
    void rehydrateRaisesNothing() {
        Invoice invoice = Invoice.rehydrate(UUID.randomUUID(), "INV-1001", ORDER, "CHOCO-1", CUSTOMER,
                customer(), address(), List.of(line(Money.inr(49900), 1)), Money.inr(5000), T0);

        assertThat(invoice.peekDomainEvents()).isEmpty();
        assertThat(invoice.total()).isEqualTo(Money.inr(54900));
    }

    @Test
    @DisplayName("an invoice with no lines is refused")
    void noLinesRefused() {
        assertThatThrownBy(() -> Invoice.issue(UUID.randomUUID(), "INV-1", ORDER, "CHOCO-1", CUSTOMER,
                customer(), address(), List.of(), Money.inr(5000), T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    @DisplayName("a negative delivery charge is refused")
    void negativeChargeRefused() {
        assertThatThrownBy(() -> Invoice.issue(UUID.randomUUID(), "INV-1", ORDER, "CHOCO-1", CUSTOMER,
                customer(), address(), List.of(line(Money.inr(49900), 1)), Money.inr(-1), T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    @DisplayName("a zero delivery charge is allowed: free delivery is a real value")
    void zeroChargeAllowed() {
        Invoice invoice = issue(line(Money.inr(49900), 1), Money.inr(0));

        assertThat(invoice.deliveryCharge()).isEqualTo(Money.inr(0));
    }

    @Test
    @DisplayName("a blank invoice number is refused")
    void blankNumberRefused() {
        assertThatThrownBy(() -> Invoice.issue(UUID.randomUUID(), "  ", ORDER, "CHOCO-1", CUSTOMER,
                customer(), address(), List.of(line(Money.inr(49900), 1)), Money.inr(5000), T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invoice number is required");
    }

    @Test
    @DisplayName("a line with a non-positive unit price is refused")
    void nonPositiveUnitPriceRefused() {
        assertThatThrownBy(() -> new InvoiceLine(PRODUCT, VARIANT, "Dark", "200 g", "SKU-1",
                Money.inr(0), Quantity.of(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("the billing address renders on one line for the document")
    void addressRenders() {
        Invoice.InvoiceAddress address = address();

        assertThat(address.formatted())
                .isEqualTo("Asha Rao, 12 Cocoa Lane, Flat 3, Bengaluru, Karnataka 560001");
    }

    // ---------------------------------------------------------------------------------------------

    private static Invoice issue(InvoiceLine line, Money deliveryCharge) {
        return Invoice.issue(UUID.randomUUID(), "INV-1001", ORDER, "CHOCO-1", CUSTOMER, customer(),
                address(), List.of(line), deliveryCharge, T0);
    }

    private static InvoiceLine line(Money unitPrice, int quantity) {
        return new InvoiceLine(PRODUCT, VARIANT, "Dark Chocolate", "200 g", "SKU-1", unitPrice,
                Quantity.of(quantity));
    }

    private static Invoice.CustomerDetails customer() {
        return new Invoice.CustomerDetails("Asha Rao", EmailAddress.of("asha@example.com"),
                PhoneNumber.of("9876543210"));
    }

    private static Invoice.InvoiceAddress address() {
        return new Invoice.InvoiceAddress("Asha Rao", PhoneNumber.of("9876543210"), "12 Cocoa Lane",
                "Flat 3", "Bengaluru", "Karnataka", Pincode.of("560001"));
    }
}

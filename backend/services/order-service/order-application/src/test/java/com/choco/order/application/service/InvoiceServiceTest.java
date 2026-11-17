package com.choco.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.NotFoundException;
import com.choco.core.port.PageQuery;
import com.choco.order.application.dto.InvoiceView;
import com.choco.order.application.support.FixedInvoiceNumbers;
import com.choco.order.application.support.InMemoryInvoiceRepository;
import com.choco.order.application.support.InMemoryOrderRepository;
import com.choco.order.application.support.RecordingAuditLog;
import com.choco.order.domain.order.CustomerSnapshot;
import com.choco.order.domain.order.DeliveryAddress;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.order.OrderLine;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Invoice service")
class InvoiceServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID VARIANT = UUID.randomUUID();
    private static final String ADMIN = "admin-1";

    private InMemoryOrderRepository orders;
    private InMemoryInvoiceRepository invoices;
    private RecordingAuditLog audit;
    private Fakes.RecordingEventPublisher events;
    private InvoiceService service;
    private InvoiceQueryService queries;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrderRepository();
        invoices = new InMemoryInvoiceRepository();
        audit = new RecordingAuditLog();
        events = new Fakes.RecordingEventPublisher();
        Fakes.FixedClock clock = Fakes.FixedClock.startingAt("2026-01-01T10:00:00Z");
        service = new InvoiceService(orders, invoices, new FixedInvoiceNumbers(), events, audit, clock,
                new Fakes.SequentialIdGenerator(), new Fakes.DirectTransactionRunner());
        queries = new InvoiceQueryService(invoices);
    }

    @Nested
    @DisplayName("issuance")
    class Issuance {

        @Test
        @DisplayName("a paid order is invoiced, snapshotting its lines and total")
        void paidOrderIsInvoiced() {
            Order order = paidOrder(CUSTOMER);

            InvoiceView view = service.issueForOrder(order.id());

            assertThat(view.orderId()).isEqualTo(order.id());
            assertThat(view.invoiceNumber()).isEqualTo("INV-00001");
            assertThat(view.customerId()).isEqualTo(CUSTOMER);
            assertThat(view.totalMinorUnits()).isEqualTo(order.total().minorUnits());
            assertThat(view.lines()).hasSize(1);
            assertThat(view.total()).isEqualTo("549.00");
            assertThat(audit.hasAction("invoice.issue")).isTrue();
        }

        @Test
        @DisplayName("issuance raises order.invoice-issued exactly once")
        void issuanceRaisesEvent() {
            Order order = paidOrder(CUSTOMER);
            service.issueForOrder(order.id());

            assertThat(events.eventsOfType("order.invoice-issued")).hasSize(1);
        }

        @Test
        @DisplayName("issuing twice returns the same invoice and does not bill twice")
        void issuanceIsIdempotent() {
            Order order = paidOrder(CUSTOMER);

            InvoiceView first = service.issueForOrder(order.id());
            InvoiceView second = service.issueForOrder(order.id());

            assertThat(second.id()).isEqualTo(first.id());
            assertThat(second.invoiceNumber()).isEqualTo(first.invoiceNumber());
            assertThat(invoices.count()).isEqualTo(1);
            assertThat(events.eventsOfType("order.invoice-issued")).hasSize(1);
        }

        @Test
        @DisplayName("an unpaid order cannot be invoiced")
        void unpaidOrderRefused() {
            Order order = unpaidOrder(CUSTOMER);

            assertThatThrownBy(() -> service.issueForOrder(order.id()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("paid order");
            assertThat(invoices.count()).isZero();
        }

        @Test
        @DisplayName("an unknown order is not found")
        void unknownOrderNotFound() {
            assertThatThrownBy(() -> service.issueForOrder(UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("the seller can issue an invoice for a confirmed order and it is audited as ADMIN")
        void adminIssues() {
            Order order = paidOrder(CUSTOMER);

            InvoiceView view = service.issueAsAdmin(ADMIN, order.id());

            assertThat(view.orderId()).isEqualTo(order.id());
            assertThat(audit.entries().getFirst().actorType()).isEqualTo("ADMIN");
            assertThat(audit.entries().getFirst().actorId()).isEqualTo(ADMIN);
        }
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("a customer reads their own invoice")
        void ownerReads() {
            Order order = paidOrder(CUSTOMER);
            InvoiceView issued = service.issueForOrder(order.id());

            InvoiceView view = queries.ownInvoice(CUSTOMER, issued.id());

            assertThat(view.invoiceNumber()).isEqualTo(issued.invoiceNumber());
        }

        @Test
        @DisplayName("a customer cannot read another customer's invoice; it is a 404, not a 403")
        void crossCustomerIsNotFound() {
            Order order = paidOrder(CUSTOMER);
            InvoiceView issued = service.issueForOrder(order.id());

            assertThatThrownBy(() -> queries.ownInvoice(OTHER, issued.id()))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("a customer reads their invoice for an order by order id")
        void ownerReadsByOrder() {
            Order order = paidOrder(CUSTOMER);
            service.issueForOrder(order.id());

            assertThat(queries.ownInvoiceForOrder(CUSTOMER, order.id()).orderId()).isEqualTo(order.id());
        }

        @Test
        @DisplayName("another customer's order invoice is not found by order id either")
        void crossCustomerByOrderIsNotFound() {
            Order order = paidOrder(CUSTOMER);
            service.issueForOrder(order.id());

            assertThatThrownBy(() -> queries.ownInvoiceForOrder(OTHER, order.id()))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("the customer's invoice list is scoped to them")
        void listIsScoped() {
            Order mine = paidOrder(CUSTOMER);
            Order theirs = paidOrder(OTHER);
            service.issueForOrder(mine.id());
            service.issueForOrder(theirs.id());

            assertThat(queries.ownInvoices(CUSTOMER, PageQuery.of(0, 20, null, null)).content())
                    .extracting(InvoiceView::orderId)
                    .containsExactly(mine.id());
        }

        @Test
        @DisplayName("the seller sees every invoice")
        void adminSeesAll() {
            service.issueForOrder(paidOrder(CUSTOMER).id());
            service.issueForOrder(paidOrder(OTHER).id());

            assertThat(queries.allForAdmin(PageQuery.of(0, 20, null, null)).totalElements()).isEqualTo(2);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private Order paidOrder(UUID customerId) {
        Order order = seed(customerId);
        order.markPaid("pay_1", "order_1", order.total(), Instant.parse("2026-01-01T10:05:00Z"));
        return orders.save(order);
    }

    private Order unpaidOrder(UUID customerId) {
        return seed(customerId);
    }

    private Order seed(UUID customerId) {
        Order order = Order.place(UUID.randomUUID(), "CHOCO-" + Math.abs(UUID.randomUUID().hashCode()),
                customerId,
                new CustomerSnapshot("Asha Rao", EmailAddress.of("asha@example.com"),
                        PhoneNumber.of("9876543210")),
                new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), "12 Cocoa Lane", null,
                        "Bengaluru", "Karnataka", Pincode.of("560001"), null),
                List.of(new OrderLine(PRODUCT, VARIANT, "Dark Chocolate", "200 g", "SKU-1",
                        Money.inr(49900), Quantity.of(1))),
                Money.inr(5000), Instant.parse("2026-01-01T10:00:00Z"));
        return orders.save(order);
    }
}

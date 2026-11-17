package com.choco.reporting.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.reporting.application.fake.InMemoryReportingReadModel;
import com.choco.reporting.domain.model.CustomerProjection;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.model.PaymentProjection;
import com.choco.reporting.domain.model.ProductionProjection;
import com.choco.reporting.domain.model.RefundProjection;
import com.choco.test.Fakes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProjectionService")
class ProjectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final String ORDER = "11111111-1111-1111-1111-111111111111";

    private InMemoryReportingReadModel readModel;
    private ProjectionService service;

    @BeforeEach
    void setUp() {
        readModel = new InMemoryReportingReadModel();
        service = new ProjectionService(readModel, Fakes.FixedClock.startingAt(NOW.toString()),
                new Fakes.DirectTransactionRunner());
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Nested
    @DisplayName("orders")
    class Orders {

        @Test
        @DisplayName("order.placed creates a PLACED projection with the frozen total")
        void placed() {
            service.orderPlaced(payload("orderId", ORDER, "orderNumber", "ORD-1", "customerId", "cust-1",
                    "totalMinorUnits", 50_000L, "deliveryChargeMinorUnits", 5_000L, "currency", "INR",
                    "occurredAt", NOW.toString()));

            OrderProjection order = readModel.findOrder(ORDER).orElseThrow();
            assertThat(order.status()).isEqualTo(OrderProjection.PLACED);
            assertThat(order.totalMinorUnits()).isEqualTo(50_000L);
            assertThat(order.deliveryChargeMinorUnits()).isEqualTo(5_000L);
            assertThat(order.placedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("order.confirmed advances the status but keeps the placed total and instant")
        void confirmedKeepsTotal() {
            service.orderPlaced(payload("orderId", ORDER, "totalMinorUnits", 50_000L,
                    "occurredAt", NOW.toString()));
            service.orderConfirmed(payload("orderId", ORDER, "occurredAt", NOW.plusSeconds(60).toString()));

            OrderProjection order = readModel.findOrder(ORDER).orElseThrow();
            assertThat(order.status()).isEqualTo(OrderProjection.CONFIRMED);
            assertThat(order.totalMinorUnits()).isEqualTo(50_000L);
            assertThat(order.placedAt()).isEqualTo(NOW);
            assertThat(order.confirmedAt()).isEqualTo(NOW.plusSeconds(60));
            assertThat(order.countsAsRevenue()).isTrue();
        }

        @Test
        @DisplayName("a redelivered order.placed updates one row rather than adding a second")
        void placedIsIdempotent() {
            service.orderPlaced(payload("orderId", ORDER, "totalMinorUnits", 1L, "occurredAt", NOW.toString()));
            service.orderPlaced(payload("orderId", ORDER, "totalMinorUnits", 1L, "occurredAt", NOW.toString()));
            assertThat(readModel.countOrders()).isEqualTo(1);
        }

        @Test
        @DisplayName("order.cancelled records the reason and stops the order counting as revenue")
        void cancelled() {
            service.orderPlaced(payload("orderId", ORDER, "totalMinorUnits", 50_000L,
                    "occurredAt", NOW.toString()));
            service.orderConfirmed(payload("orderId", ORDER, "occurredAt", NOW.toString()));
            service.orderCancelled(payload("orderId", ORDER, "reason", "out of stock",
                    "occurredAt", NOW.plusSeconds(120).toString()));

            OrderProjection order = readModel.findOrder(ORDER).orElseThrow();
            assertThat(order.status()).isEqualTo(OrderProjection.CANCELLED);
            assertThat(order.cancellationReason()).isEqualTo("out of stock");
            assertThat(order.countsAsRevenue()).isFalse();
            assertThat(order.confirmedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("an event with no order id is ignored, not stored under a null key")
        void missingIdIgnored() {
            service.orderPlaced(payload("orderNumber", "ORD-1"));
            assertThat(readModel.countOrders()).isZero();
        }
    }

    @Nested
    @DisplayName("payments")
    class Payments {

        @Test
        @DisplayName("payment.captured stores a CAPTURED payment keyed on the provider payment id")
        void captured() {
            service.paymentCaptured(payload("providerPaymentId", "pay_1", "orderId", ORDER,
                    "customerId", "cust-1", "amountMinorUnits", 50_000L, "currency", "INR",
                    "method", "UPI", "occurredAt", NOW.toString()));

            PaymentProjection payment = readModel.findPayment("pay_1").orElseThrow();
            assertThat(payment.isPaid()).isTrue();
            assertThat(payment.method()).isEqualTo("UPI");
            assertThat(payment.amountMinorUnits()).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("payment.failed records the reason and is not counted as money received")
        void failed() {
            service.paymentFailed(payload("providerPaymentId", "pay_2", "orderId", ORDER,
                    "amountMinorUnits", 50_000L, "reason", "insufficient funds",
                    "occurredAt", NOW.toString()));

            PaymentProjection payment = readModel.findPayment("pay_2").orElseThrow();
            assertThat(payment.status()).isEqualTo(PaymentProjection.FAILED);
            assertThat(payment.isPaid()).isFalse();
            assertThat(payment.failureReason()).isEqualTo("insufficient funds");
        }

        @Test
        @DisplayName("a failed attempt and a later capture for the same order are two distinct rows")
        void twoAttemptsAreDistinctRows() {
            service.paymentFailed(payload("providerPaymentId", "pay_1", "orderId", ORDER,
                    "amountMinorUnits", 100L, "occurredAt", NOW.toString()));
            service.paymentCaptured(payload("providerPaymentId", "pay_2", "orderId", ORDER,
                    "amountMinorUnits", 100L, "occurredAt", NOW.plusSeconds(60).toString()));
            assertThat(readModel.countPayments()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("production and delivery")
    class Fulfilment {

        @Test
        @DisplayName("production.started records the job with a start instant")
        void productionStarted() {
            service.productionStarted(payload("jobId", "job-1", "orderId", ORDER, "orderNumber", "ORD-1",
                    "variantLabel", "Dark 70%", "quantity", 2, "occurredAt", NOW.toString()));

            List<ProductionProjection> jobs =
                    readModel.findProductionBetween(NOW.minusSeconds(1), NOW.plusSeconds(1), 10);
            assertThat(jobs).singleElement().satisfies(job -> {
                assertThat(job.status()).isEqualTo(ProductionProjection.PREPARATION_STARTED);
                assertThat(job.startedAt()).isEqualTo(NOW);
                assertThat(job.quantity()).isEqualTo(2);
            });
        }

        @Test
        @DisplayName("production.completed keeps the start instant set by the earlier event")
        void productionCompletedKeepsStart() {
            service.productionStarted(payload("jobId", "job-1", "occurredAt", NOW.toString()));
            service.productionCompleted(payload("jobId", "job-1", "occurredAt",
                    NOW.plusSeconds(3600).toString()));

            List<ProductionProjection> jobs =
                    readModel.findProductionBetween(NOW.minusSeconds(1), NOW.plusSeconds(7200), 10);
            assertThat(jobs).singleElement().satisfies(job -> {
                assertThat(job.status()).isEqualTo(ProductionProjection.PREPARATION_COMPLETED);
                assertThat(job.startedAt()).isEqualTo(NOW);
                assertThat(job.completedAt()).isEqualTo(NOW.plusSeconds(3600));
            });
        }

        @Test
        @DisplayName("delivery events advance one row keyed on the delivery id")
        void deliveryAdvancesOneRow() {
            service.deliveryAssigned(payload("deliveryId", "del-1", "orderId", ORDER,
                    "deliveryPersonName", "Ravi", "occurredAt", NOW.toString()));
            service.outForDelivery(payload("deliveryId", "del-1", "orderId", ORDER,
                    "occurredAt", NOW.plusSeconds(60).toString()));
            service.deliveryDelivered(payload("deliveryId", "del-1", "orderId", ORDER,
                    "occurredAt", NOW.plusSeconds(120).toString()));

            assertThat(readModel.countDeliveries()).isEqualTo(1);
            List<com.choco.reporting.domain.model.DeliveryProjection> deliveries =
                    readModel.findDeliveriesBetween(NOW.minusSeconds(1), NOW.plusSeconds(180), 10);
            assertThat(deliveries).singleElement().satisfies(delivery ->
                    assertThat(delivery.status())
                            .isEqualTo(com.choco.reporting.domain.model.DeliveryProjection.DELIVERED));
        }

        @Test
        @DisplayName("the status the event carried is copied verbatim, not re-derived")
        void statusIsCopiedFromTheEvent() {
            service.deliveryFailed(payload("deliveryId", "del-1", "orderId", ORDER,
                    "status", "ATTEMPT_FAILED", "failureReason", "customer not at home",
                    "attemptNumber", 2, "occurredAt", NOW.toString()));

            List<com.choco.reporting.domain.model.DeliveryProjection> deliveries =
                    readModel.findDeliveriesBetween(NOW.minusSeconds(1), NOW.plusSeconds(1), 10);
            assertThat(deliveries).singleElement().satisfies(delivery -> {
                assertThat(delivery.status())
                        .isEqualTo(com.choco.reporting.domain.model.DeliveryProjection.ATTEMPT_FAILED);
                assertThat(delivery.failureReason()).isEqualTo("customer not at home");
                assertThat(delivery.attemptNumber()).isEqualTo(2);
            });
        }

        @Test
        @DisplayName("a later success clears the failure reason a failed attempt recorded")
        void successClearsFailureReason() {
            service.deliveryFailed(payload("deliveryId", "del-1", "orderId", ORDER,
                    "status", "ATTEMPT_FAILED", "failureReason", "customer not at home",
                    "attemptNumber", 1, "occurredAt", NOW.toString()));
            service.deliveryDelivered(payload("deliveryId", "del-1", "orderId", ORDER,
                    "status", "DELIVERED", "occurredAt", NOW.plusSeconds(600).toString()));

            List<com.choco.reporting.domain.model.DeliveryProjection> deliveries =
                    readModel.findDeliveriesBetween(NOW.minusSeconds(1), NOW.plusSeconds(1200), 10);
            assertThat(deliveries).singleElement().satisfies(delivery -> {
                assertThat(delivery.status())
                        .isEqualTo(com.choco.reporting.domain.model.DeliveryProjection.DELIVERED);
                assertThat(delivery.failureReason()).isNull();
                assertThat(delivery.attemptNumber()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("a later delivery event does not blank the assignee the first one named")
        void laterEventKeepsAssignee() {
            service.deliveryAssigned(payload("deliveryId", "del-1", "orderId", ORDER,
                    "status", "ASSIGNED", "deliveryPersonName", "Ravi", "occurredAt", NOW.toString()));
            service.outForDelivery(payload("deliveryId", "del-1", "status", "OUT_FOR_DELIVERY",
                    "occurredAt", NOW.plusSeconds(60).toString()));

            List<com.choco.reporting.domain.model.DeliveryProjection> deliveries =
                    readModel.findDeliveriesBetween(NOW.minusSeconds(1), NOW.plusSeconds(180), 10);
            assertThat(deliveries).singleElement()
                    .satisfies(delivery -> assertThat(delivery.deliveryPersonName()).isEqualTo("Ravi"));
        }
    }

    @Nested
    @DisplayName("refunds")
    class Refunds {

        @Test
        @DisplayName("a request followed by a processed event keeps the request's identifiers")
        void processedKeepsRequestIdentifiers() {
            service.refundRequested(payload("orderId", ORDER, "orderNumber", "ORD-1",
                    "customerId", "cust-1", "amountMinorUnits", 50_000L, "reason", "damaged",
                    "occurredAt", NOW.toString()));
            service.refundProcessed(payload("orderId", ORDER, "providerRefundId", "rfnd_1",
                    "amountMinorUnits", 50_000L, "fullRefund", true,
                    "occurredAt", NOW.plusSeconds(600).toString()));

            assertThat(readModel.countRefunds()).isEqualTo(1);
        }

        @Test
        @DisplayName("a processed refund with no prior request is still recorded")
        void processedWithoutRequest() {
            service.refundProcessed(payload("orderId", ORDER, "providerRefundId", "rfnd_9",
                    "amountMinorUnits", 1_000L, "occurredAt", NOW.toString()));
            assertThat(readModel.countRefunds()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("customers")
    class Customers {

        @Test
        @DisplayName("registration records an unverified customer")
        void registered() {
            service.customerRegistered(payload("aggregateId", "cust-1", "fullName", "Asha",
                    "email", "asha@example.test", "mobile", "9000000000",
                    "occurredAt", NOW.toString()));

            CustomerProjection customer = readModel.findCustomer("cust-1").orElseThrow();
            assertThat(customer.emailVerified()).isFalse();
            assertThat(customer.mobileVerified()).isFalse();
            assertThat(customer.registeredAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("verification sets only the channel the event named")
        void verifiedSetsOneChannel() {
            service.customerRegistered(payload("aggregateId", "cust-1", "email", "a@b.test",
                    "mobile", "9000000000", "occurredAt", NOW.toString()));
            service.customerVerified(payload("aggregateId", "cust-1", "channel", "EMAIL",
                    "occurredAt", NOW.plusSeconds(30).toString()));

            CustomerProjection customer = readModel.findCustomer("cust-1").orElseThrow();
            assertThat(customer.emailVerified()).isTrue();
            assertThat(customer.mobileVerified()).isFalse();
        }

        @Test
        @DisplayName("a contact change clears that channel's verified flag")
        void contactChangeClearsVerification() {
            service.customerRegistered(payload("aggregateId", "cust-1", "email", "a@b.test",
                    "mobile", "9000000000", "occurredAt", NOW.toString()));
            service.customerVerified(payload("aggregateId", "cust-1", "channel", "EMAIL",
                    "occurredAt", NOW.toString()));
            service.customerContactChanged(payload("aggregateId", "cust-1", "email", "new@b.test",
                    "mobile", "9000000000", "occurredAt", NOW.plusSeconds(60).toString()));

            CustomerProjection customer = readModel.findCustomer("cust-1").orElseThrow();
            assertThat(customer.email()).isEqualTo("new@b.test");
            assertThat(customer.emailVerified()).isFalse();
        }

        @Test
        @DisplayName("a contact change that keeps the same email leaves verification intact")
        void unchangedEmailKeepsVerification() {
            service.customerRegistered(payload("aggregateId", "cust-1", "email", "a@b.test",
                    "occurredAt", NOW.toString()));
            service.customerVerified(payload("aggregateId", "cust-1", "channel", "EMAIL",
                    "occurredAt", NOW.toString()));
            service.customerContactChanged(payload("aggregateId", "cust-1", "email", "a@b.test",
                    "occurredAt", NOW.plusSeconds(60).toString()));

            assertThat(readModel.findCustomer("cust-1").orElseThrow().emailVerified()).isTrue();
        }
    }

    @Nested
    @DisplayName("invoices")
    class Invoices {

        @Test
        @DisplayName("invoice.issued records the number and the billed total")
        void issued() {
            service.invoiceIssued(payload("invoiceId", "inv-1", "orderId", ORDER, "orderNumber", "ORD-1",
                    "invoiceNumber", "INV-00001", "customerId", "cust-1", "totalMinorUnits", 50_000L,
                    "currency", "INR", "occurredAt", NOW.toString()));

            List<com.choco.reporting.domain.model.InvoiceProjection> invoices =
                    readModel.findInvoicesBetween(NOW.minusSeconds(1), NOW.plusSeconds(1), 10);
            assertThat(invoices).singleElement()
                    .satisfies(invoice -> {
                        assertThat(invoice.invoiceNumber()).isEqualTo("INV-00001");
                        assertThat(invoice.totalMinorUnits()).isEqualTo(50_000L);
                    });
        }
    }

    @Nested
    @DisplayName("robustness")
    class Robustness {

        @Test
        @DisplayName("a payload missing the key field is ignored rather than throwing")
        void missingKeyIgnored() {
            service.paymentCaptured(payload("amountMinorUnits", 1L));
            service.deliveryDelivered(payload("orderId", ORDER));
            service.invoiceIssued(payload("orderNumber", "ORD-1"));
            service.customerVerified(payload("channel", "EMAIL"));
            assertThat(readModel.upsertCount()).isZero();
        }

        @Test
        @DisplayName("a malformed timestamp does not lose the row")
        void malformedTimestampIsTolerated() {
            service.orderPlaced(payload("orderId", ORDER, "totalMinorUnits", 1L,
                    "occurredAt", "not-a-timestamp"));
            assertThat(readModel.findOrder(ORDER)).isPresent();
        }
    }
}

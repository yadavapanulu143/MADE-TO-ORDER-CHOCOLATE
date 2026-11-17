package com.choco.reporting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import com.choco.reporting.application.dto.ReportView;
import com.choco.reporting.application.fake.InMemoryReportingReadModel;
import com.choco.reporting.domain.model.CustomerProjection;
import com.choco.reporting.domain.model.DeliveryProjection;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.model.PaymentProjection;
import com.choco.reporting.domain.model.ProductionProjection;
import com.choco.reporting.domain.model.RefundProjection;
import com.choco.reporting.domain.report.ReportPeriod;
import com.choco.reporting.domain.report.ReportType;
import com.choco.test.Fakes;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReportService")
class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

    private InMemoryReportingReadModel readModel;
    private ReportService service;
    private ReportPeriod period;

    @BeforeEach
    void setUp() {
        readModel = new InMemoryReportingReadModel();
        service = new ReportService(readModel, Fakes.FixedClock.startingAt(NOW.toString()));
        period = new ReportPeriod(NOW.minusSeconds(86_400), NOW.plusSeconds(86_400));
    }

    private static OrderProjection order(String id, String status, long total, Instant at) {
        return new OrderProjection(id, "ORD-" + id, "cust-1", status, total, 5_000L, "INR", at, null, null,
                null);
    }

    private static PaymentProjection payment(String id, String status, long amount, Instant at) {
        return new PaymentProjection(id, "order-1", "cust-1", status, amount, "INR", "UPI", null, at);
    }

    @Nested
    @DisplayName("sales")
    class Sales {

        @Test
        @DisplayName("counts revenue from confirmed orders only, and groups by UTC day")
        void revenueCountsConfirmedOnly() {
            readModel.upsertOrder(order("1", OrderProjection.CONFIRMED, 50_000L, NOW.minusSeconds(60)));
            readModel.upsertOrder(order("2", OrderProjection.PLACED, 10_000L, NOW.minusSeconds(60)));
            readModel.upsertOrder(order("3", OrderProjection.CANCELLED, 90_000L, NOW.minusSeconds(60)));

            ReportView view = service.report(ReportType.SALES, period);

            assertThat(metric(view, "orders")).isEqualTo(3L);
            assertThat(metric(view, "confirmed")).isEqualTo(1L);
            assertThat(metric(view, "cancelled")).isEqualTo(1L);
            assertThat(metric(view, "revenueMinorUnits")).isEqualTo(50_000L);
            assertThat(metric(view, "averageOrderMinorUnits")).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("a day with no confirmed order reports a zero revenue row, not a missing one")
        void dayRowPresentWithZero() {
            readModel.upsertOrder(order("1", OrderProjection.PLACED, 1_000L, NOW.minusSeconds(60)));
            ReportView view = service.report(ReportType.SALES, period);
            assertThat(view.rows()).singleElement().satisfies(row -> {
                assertThat(row.get(1)).isEqualTo(1L);
                assertThat(row.get(2)).isEqualTo(0L);
            });
        }

        @Test
        @DisplayName("revenue never silently drops rows: a window at the ceiling is refused")
        void refusesTruncatedReport() {
            for (int i = 0; i < com.choco.reporting.domain.report.ReportLimits.MAX_REPORT_ROWS; i++) {
                readModel.upsertOrder(order("o" + i, OrderProjection.PLACED, 1L, NOW.minusSeconds(60)));
            }
            assertThatThrownBy(() -> service.report(ReportType.SALES, period))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("narrow the period");
        }
    }

    @Nested
    @DisplayName("payments")
    class Payments {

        @Test
        @DisplayName("sums only paid payments and counts failures and pending separately")
        void sumsOnlyPaid() {
            readModel.upsertPayment(payment("p1", PaymentProjection.PAID, 50_000L, NOW.minusSeconds(60)));
            readModel.upsertPayment(payment("p2", PaymentProjection.FAILED, 50_000L, NOW.minusSeconds(60)));
            readModel.upsertPayment(payment("p3", PaymentProjection.PENDING, 50_000L, NOW.minusSeconds(60)));

            ReportView view = service.report(ReportType.PAYMENTS, period);

            assertThat(metric(view, "captured")).isEqualTo(1L);
            assertThat(metric(view, "capturedMinorUnits")).isEqualTo(50_000L);
            assertThat(metric(view, "failed")).isEqualTo(1L);
            assertThat(metric(view, "pending")).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("production")
    class Production {

        @Test
        @DisplayName("groups jobs by the status the kitchen actually reported")
        void groupsByRealStatuses() {
            readModel.upsertProduction(job("j1", ProductionProjection.PREPARATION_STARTED, NOW));
            readModel.upsertProduction(job("j2", ProductionProjection.PREPARATION_COMPLETED, NOW));
            readModel.upsertProduction(job("j3", ProductionProjection.READY_FOR_DISPATCH, NOW));

            ReportView view = service.report(ReportType.PRODUCTION, period);

            assertThat(metric(view, "jobs")).isEqualTo(3L);
            assertThat(metric(view, "inProgress")).isEqualTo(1L);
            assertThat(metric(view, "completed")).isEqualTo(1L);
            assertThat(metric(view, "ready")).isEqualTo(1L);
        }

        private ProductionProjection job(String id, String status, Instant at) {
            return new ProductionProjection(id, "order-1", "ORD-1", "cust-1", "Dark 70%", 2, status, at,
                    null);
        }
    }

    @Nested
    @DisplayName("delivery")
    class Delivery {

        @Test
        @DisplayName("counts a failed attempt under the name the fulfilment service uses")
        void countsFailedAttempts() {
            readModel.upsertDelivery(delivery("d1", DeliveryProjection.DELIVERED, NOW));
            readModel.upsertDelivery(delivery("d2", DeliveryProjection.ATTEMPT_FAILED, NOW));
            readModel.upsertDelivery(delivery("d3", DeliveryProjection.OUT_FOR_DELIVERY, NOW));

            ReportView view = service.report(ReportType.DELIVERY, period);

            assertThat(metric(view, "deliveries")).isEqualTo(3L);
            assertThat(metric(view, "delivered")).isEqualTo(1L);
            assertThat(metric(view, "failed")).isEqualTo(1L);
            assertThat(metric(view, "inFlight")).isEqualTo(1L);
        }

        private DeliveryProjection delivery(String id, String status, Instant at) {
            return new DeliveryProjection(id, "order-1", "ORD-1", "cust-1", status, "Ravi", null, 0, at);
        }
    }

    @Nested
    @DisplayName("refunds")
    class Refunds {

        @Test
        @DisplayName("separates a request the customer is waiting on from money actually returned")
        void separatesRequestFromProcessed() {
            readModel.upsertRefund(new RefundProjection("r1", "o1", "ORD-1", "cust-1", 1_000L, "INR",
                    RefundProjection.REQUESTED, "damaged", false, NOW, null));
            readModel.upsertRefund(new RefundProjection("r2", "o2", "ORD-2", "cust-1", 2_000L, "INR",
                    RefundProjection.PROCESSED, null, true, NOW, NOW));

            ReportView view = service.report(ReportType.REFUNDS, period);

            assertThat(metric(view, "requested")).isEqualTo(1L);
            assertThat(metric(view, "processed")).isEqualTo(1L);
            assertThat(metric(view, "processedMinorUnits")).isEqualTo(2_000L);
        }
    }

    @Nested
    @DisplayName("customers")
    class Customers {

        @Test
        @DisplayName("masks contact details and counts only fully verified customers")
        void masksAndCountsVerified() {
            readModel.upsertCustomer(new CustomerProjection("c1", "Asha", "asha@example.test",
                    "9000000000", true, true, NOW, NOW));
            readModel.upsertCustomer(new CustomerProjection("c2", "Bala", "bala@example.test",
                    "9111111111", true, false, NOW, NOW));

            ReportView view = service.report(ReportType.CUSTOMERS, period);

            assertThat(metric(view, "registered")).isEqualTo(2L);
            assertThat(metric(view, "fullyVerified")).isEqualTo(1L);
            // The raw addresses must not survive into the report; only a masked form may.
            assertThat(view.rows()).allSatisfy(row -> {
                assertThat(row.get(1).toString()).doesNotContain("asha@example.test", "bala@example.test")
                        .contains("**");
                assertThat(row.get(2).toString()).doesNotContain("9000000000", "9111111111");
            });
        }
    }

    @Nested
    @DisplayName("default period")
    class DefaultPeriod {

        @Test
        @DisplayName("ends at the injected clock so a caller and a test agree on now")
        void anchorsOnClock() {
            ReportPeriod window = service.defaultPeriod(7);
            assertThat(window.to()).isEqualTo(NOW);
            assertThat(window.from()).isEqualTo(NOW.minusSeconds(7 * 86_400));
        }
    }

    /**
     * Reads a metric as a {@code long}. A count is stored as an {@code int} and an amount as a
     * {@code long}, and the test should not have to know which; normalising here keeps the assertions
     * about the number, not its boxed type.
     */
    private static long metric(ReportView view, String key) {
        Object value = view.metrics().stream().filter(m -> m.key().equals(key)).findFirst()
                .orElseThrow().value();
        return ((Number) value).longValue();
    }
}

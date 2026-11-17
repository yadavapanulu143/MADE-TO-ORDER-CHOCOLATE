package com.choco.reporting.application.service;

import com.choco.core.port.Clock;
import com.choco.reporting.application.dto.DashboardView;
import com.choco.reporting.domain.model.DeliveryProjection;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.model.PaymentProjection;
import com.choco.reporting.domain.model.ProductionProjection;
import com.choco.reporting.domain.model.RefundProjection;
import com.choco.reporting.domain.port.ReportingReadModel;
import com.choco.reporting.domain.report.ReportLimits;
import com.choco.reporting.domain.report.ReportMetric;
import com.choco.reporting.domain.report.ReportPeriod;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The seller's at-a-glance dashboard.
 *
 * <p>Assembled from the same projections the reports use, over the last 30 days, so the dashboard and
 * a report can never disagree about the same window. It carries counts and totals only — no customer
 * name, address or contact detail — because a dashboard is a landing page, not a place to read people.
 */
public class DashboardService {

    private static final int WINDOW_DAYS = 30;
    private static final int RECENT_LIMIT = 10;
    private final ReportingReadModel readModel;
    private final Clock clock;

    public DashboardService(ReportingReadModel readModel, Clock clock) {
        this.readModel = readModel;
        this.clock = clock;
    }

    public DashboardView dashboard() {
        Instant now = clock.now();
        ReportPeriod period = ReportPeriod.lastDays(WINDOW_DAYS, now);

        List<OrderProjection> orders = readModel.findOrdersBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(orders.size());
        List<PaymentProjection> payments =
                readModel.findPaymentsBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(payments.size());
        List<ProductionProjection> production =
                readModel.findProductionBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(production.size());
        List<DeliveryProjection> deliveries =
                readModel.findDeliveriesBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(deliveries.size());
        List<RefundProjection> refunds =
                readModel.findRefundsBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(refunds.size());

        long revenue = 0L;
        long confirmed = 0L;
        long cancelled = 0L;
        for (OrderProjection order : orders) {
            if (order.countsAsRevenue()) {
                revenue = Math.addExact(revenue, order.totalMinorUnits());
                confirmed++;
            }
            if (OrderProjection.CANCELLED.equals(order.status())) {
                cancelled++;
            }
        }

        long capturedAmount = 0L;
        long failedPayments = 0L;
        for (PaymentProjection payment : payments) {
            if (payment.isPaid()) {
                capturedAmount = Math.addExact(capturedAmount, payment.amountMinorUnits());
            } else if (PaymentProjection.FAILED.equals(payment.status())) {
                failedPayments++;
            }
        }

        long inProduction = production.stream()
                .filter(job -> ProductionProjection.PREPARATION_STARTED.equals(job.status())
                        || ProductionProjection.AWAITING_PREPARATION.equals(job.status())
                        || ProductionProjection.RECEIVED.equals(job.status())
                        || ProductionProjection.ON_HOLD.equals(job.status()))
                .count();
        long outForDelivery = deliveries.stream()
                .filter(delivery -> DeliveryProjection.OUT_FOR_DELIVERY.equals(delivery.status())
                        || DeliveryProjection.ASSIGNED.equals(delivery.status())
                        || DeliveryProjection.RESCHEDULED.equals(delivery.status()))
                .count();
        long pendingRefunds = refunds.stream()
                .filter(refund -> RefundProjection.REQUESTED.equals(refund.status()))
                .count();

        List<ReportMetric> metrics = List.of(
                new ReportMetric("orders", "Orders (30 days)", orders.size()),
                new ReportMetric("confirmed", "Confirmed", confirmed),
                new ReportMetric("cancelled", "Cancelled", cancelled),
                new ReportMetric("revenueMinorUnits", "Revenue (paise)", revenue),
                new ReportMetric("capturedMinorUnits", "Captured (paise)", capturedAmount),
                new ReportMetric("failedPayments", "Failed payments", failedPayments),
                new ReportMetric("inProduction", "In production", inProduction),
                new ReportMetric("outForDelivery", "Out for delivery", outForDelivery),
                new ReportMetric("pendingRefunds", "Pending refunds", pendingRefunds),
                new ReportMetric("customers", "Customers", readModel.countCustomers()));

        List<String> recentOrders = new ArrayList<>();
        int limit = Math.min(RECENT_LIMIT, orders.size());
        for (int i = 0; i < limit; i++) {
            OrderProjection order = orders.get(i);
            recentOrders.add(order.orderNumber() + "|" + order.status() + "|" + order.totalMinorUnits());
        }
        List<String> pending = new ArrayList<>();
        for (RefundProjection refund : refunds) {
            if (RefundProjection.REQUESTED.equals(refund.status())) {
                pending.add(refund.orderNumber() + "|" + refund.amountMinorUnits());
            }
        }

        return new DashboardView(metrics, recentOrders, pending);
    }
}

package com.choco.reporting.application.service;

import com.choco.core.port.Clock;
import com.choco.reporting.application.dto.ReportView;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.model.PaymentProjection;
import com.choco.reporting.domain.model.ProductionProjection;
import com.choco.reporting.domain.model.RefundProjection;
import com.choco.reporting.domain.model.CustomerProjection;
import com.choco.reporting.domain.model.DeliveryProjection;
import com.choco.reporting.domain.port.ReportingReadModel;
import com.choco.reporting.domain.report.ReportMetric;
import com.choco.reporting.domain.report.ReportPeriod;
import com.choco.reporting.domain.report.ReportLimits;
import com.choco.reporting.domain.report.ReportType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the seller's reports from the local read models.
 *
 * <p>Every figure is a count or a sum over rows this service already holds; nothing here calls another
 * service. A report is therefore fast, works while a downstream service is briefly unavailable, and
 * cannot be turned into a denial-of-service vector against the order or payment service.
 *
 * <p>Revenue counts only <em>confirmed</em> orders — a placed-but-unpaid order is not a sale, and a
 * cancelled one is not either. Captured payments are counted separately, so the seller can see the two
 * numbers and reconcile them; the report never silently equates "ordered" with "paid".
 */
public class ReportService {

    private final ReportingReadModel readModel;
    private final Clock clock;

    public ReportService(ReportingReadModel readModel, Clock clock) {
        this.readModel = readModel;
        this.clock = clock;
    }

    /** The last {@code days} days up to now, as a validated period. */
    public ReportPeriod defaultPeriod(int days) {
        return ReportPeriod.lastDays(days, clock.now());
    }

    public ReportView report(ReportType type, ReportPeriod period) {
        return switch (type) {
            case SALES -> sales(period);
            case ORDERS -> orders(period);
            case PAYMENTS -> payments(period);
            case PRODUCTION -> production(period);
            case DELIVERY -> delivery(period);
            case REFUNDS -> refunds(period);
            case CUSTOMERS -> customers(period);
        };
    }

    // --- Sales ----------------------------------------------------------------------------------

    private ReportView sales(ReportPeriod period) {
        List<OrderProjection> orders =
                readModel.findOrdersBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(orders.size());
        long revenue = 0L;
        long confirmed = 0L;
        long cancelled = 0L;
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (OrderProjection order : orders) {
            LocalDate day = LocalDate.ofInstant(order.placedAt(), ZoneOffset.UTC);
            long[] bucket = byDay.computeIfAbsent(day, d -> new long[2]);
            bucket[0]++;
            if (order.countsAsRevenue()) {
                revenue = Math.addExact(revenue, order.totalMinorUnits());
                confirmed++;
                bucket[1] = Math.addExact(bucket[1], order.totalMinorUnits());
            }
            if (OrderProjection.CANCELLED.equals(order.status())) {
                cancelled++;
            }
        }
        long average = confirmed == 0 ? 0L : revenue / confirmed;
        List<ReportMetric> metrics = List.of(
                new ReportMetric("orders", "Orders placed", orders.size()),
                new ReportMetric("confirmed", "Orders confirmed", confirmed),
                new ReportMetric("cancelled", "Orders cancelled", cancelled),
                new ReportMetric("revenueMinorUnits", "Revenue (paise)", revenue),
                new ReportMetric("averageOrderMinorUnits", "Average order (paise)", average));
        List<List<Object>> rows = new ArrayList<>();
        byDay.forEach((day, bucket) -> rows.add(List.of(day.toString(), bucket[0], bucket[1])));
        return new ReportView(ReportType.SALES.name(), period.from().toString(), period.to().toString(),
                metrics, List.of("Day", "Orders", "Revenue (paise)"), rows);
    }

    // --- Orders ---------------------------------------------------------------------------------

    private ReportView orders(ReportPeriod period) {
        List<OrderProjection> orders =
                readModel.findOrdersBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(orders.size());
        List<List<Object>> rows = new ArrayList<>();
        for (OrderProjection order : orders) {
            rows.add(List.of(order.orderNumber(), order.status(), order.totalMinorUnits(),
                    order.currency(), order.placedAt().toString()));
        }
        return new ReportView(ReportType.ORDERS.name(), period.from().toString(), period.to().toString(),
                List.of(new ReportMetric("orders", "Orders", orders.size())),
                List.of("Order number", "Status", "Total (paise)", "Currency", "Placed at"), rows);
    }

    // --- Payments -------------------------------------------------------------------------------

    private ReportView payments(ReportPeriod period) {
        List<PaymentProjection> payments =
                readModel.findPaymentsBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(payments.size());
        long captured = 0L;
        long capturedAmount = 0L;
        long failed = 0L;
        long pending = 0L;
        List<List<Object>> rows = new ArrayList<>();
        for (PaymentProjection payment : payments) {
            switch (payment.status()) {
                case PaymentProjection.PAID -> {
                    captured++;
                    capturedAmount = Math.addExact(capturedAmount, payment.amountMinorUnits());
                }
                case PaymentProjection.FAILED -> failed++;
                default -> pending++;
            }
            rows.add(List.of(payment.orderId(), payment.status(), payment.amountMinorUnits(),
                    payment.method() == null ? "" : payment.method(),
                    payment.failureReason() == null ? "" : payment.failureReason(),
                    payment.occurredAt().toString()));
        }
        List<ReportMetric> metrics = List.of(
                new ReportMetric("captured", "Payments captured", captured),
                new ReportMetric("capturedMinorUnits", "Captured amount (paise)", capturedAmount),
                new ReportMetric("failed", "Payments failed", failed),
                new ReportMetric("pending", "Payments pending", pending));
        return new ReportView(ReportType.PAYMENTS.name(), period.from().toString(), period.to().toString(),
                metrics,
                List.of("Order", "Status", "Amount (paise)", "Method", "Failure", "Occurred at"), rows);
    }

    // --- Production -----------------------------------------------------------------------------

    private ReportView production(ReportPeriod period) {
        List<ProductionProjection> jobs =
                readModel.findProductionBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(jobs.size());
        long started = 0L;
        long completed = 0L;
        long ready = 0L;
        List<List<Object>> rows = new ArrayList<>();
        for (ProductionProjection job : jobs) {
            switch (job.status()) {
                case ProductionProjection.PREPARATION_COMPLETED, ProductionProjection.PACKAGING ->
                        completed++;
                case ProductionProjection.READY_FOR_DISPATCH -> ready++;
                default -> started++;
            }
            rows.add(List.of(job.orderNumber(), job.variantLabel(), job.quantity(), job.status(),
                    job.startedAt() == null ? "" : job.startedAt().toString()));
        }
        List<ReportMetric> metrics = List.of(
                new ReportMetric("jobs", "Jobs", jobs.size()),
                new ReportMetric("inProgress", "In progress", started),
                new ReportMetric("completed", "Completed", completed),
                new ReportMetric("ready", "Ready for dispatch", ready));
        return new ReportView(ReportType.PRODUCTION.name(), period.from().toString(),
                period.to().toString(), metrics,
                List.of("Order number", "Variant", "Quantity", "Status", "Started at"), rows);
    }

    // --- Delivery -------------------------------------------------------------------------------

    private ReportView delivery(ReportPeriod period) {
        List<DeliveryProjection> deliveries =
                readModel.findDeliveriesBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(deliveries.size());
        long delivered = 0L;
        long failed = 0L;
        long inFlight = 0L;
        List<List<Object>> rows = new ArrayList<>();
        for (DeliveryProjection delivery : deliveries) {
            switch (delivery.status()) {
                case DeliveryProjection.DELIVERED -> delivered++;
                case DeliveryProjection.ATTEMPT_FAILED -> failed++;
                default -> inFlight++;
            }
            rows.add(List.of(delivery.orderNumber(), delivery.status(),
                    delivery.deliveryPersonName() == null ? "" : delivery.deliveryPersonName(),
                    delivery.failureReason() == null ? "" : delivery.failureReason(),
                    delivery.updatedAt() == null ? "" : delivery.updatedAt().toString()));
        }
        List<ReportMetric> metrics = List.of(
                new ReportMetric("deliveries", "Deliveries", deliveries.size()),
                new ReportMetric("delivered", "Delivered", delivered),
                new ReportMetric("failed", "Failed", failed),
                new ReportMetric("inFlight", "In flight", inFlight));
        return new ReportView(ReportType.DELIVERY.name(), period.from().toString(),
                period.to().toString(), metrics,
                List.of("Order number", "Status", "Delivered by", "Failure reason", "Updated at"), rows);
    }

    // --- Refunds --------------------------------------------------------------------------------

    private ReportView refunds(ReportPeriod period) {
        List<RefundProjection> refunds =
                readModel.findRefundsBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(refunds.size());
        long requested = 0L;
        long processed = 0L;
        long processedAmount = 0L;
        List<List<Object>> rows = new ArrayList<>();
        for (RefundProjection refund : refunds) {
            if (RefundProjection.PROCESSED.equals(refund.status())) {
                processed++;
                processedAmount = Math.addExact(processedAmount, refund.amountMinorUnits());
            } else {
                requested++;
            }
            rows.add(List.of(refund.orderNumber(), refund.status(), refund.amountMinorUnits(),
                    refund.reason() == null ? "" : refund.reason(),
                    refund.requestedAt() == null ? "" : refund.requestedAt().toString()));
        }
        List<ReportMetric> metrics = List.of(
                new ReportMetric("requested", "Refunds requested", requested),
                new ReportMetric("processed", "Refunds processed", processed),
                new ReportMetric("processedMinorUnits", "Refunded (paise)", processedAmount));
        return new ReportView(ReportType.REFUNDS.name(), period.from().toString(),
                period.to().toString(), metrics,
                List.of("Order number", "Status", "Amount (paise)", "Reason", "Requested at"), rows);
    }

    // --- Customers ------------------------------------------------------------------------------

    private ReportView customers(ReportPeriod period) {
        List<CustomerProjection> customers =
                readModel.findCustomersRegisteredBetween(period.from(), period.to(), ReportLimits.MAX_REPORT_ROWS);
        ReportLimits.requireWithinLimit(customers.size());
        long fullyVerified = 0L;
        List<List<Object>> rows = new ArrayList<>();
        for (CustomerProjection customer : customers) {
            boolean verified = customer.emailVerified() && customer.mobileVerified();
            if (verified) {
                fullyVerified++;
            }
            // The masked contact is what a report shows; the raw address never leaves the platform here.
            rows.add(List.of(customer.fullName(), Masking.email(customer.email()),
                    Masking.mobile(customer.mobile()), verified,
                    customer.registeredAt().toString()));
        }
        List<ReportMetric> metrics = List.of(
                new ReportMetric("registered", "Registrations", customers.size()),
                new ReportMetric("fullyVerified", "Fully verified", fullyVerified));
        return new ReportView(ReportType.CUSTOMERS.name(), period.from().toString(),
                period.to().toString(), metrics,
                List.of("Name", "Email", "Mobile", "Verified", "Registered at"), rows);
    }


    /** Convenience for a caller that wants the raw projections rather than a formatted report. */
    public Map<String, Long> headlineCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("orders", readModel.countOrders());
        counts.put("customers", readModel.countCustomers());
        counts.put("deliveries", readModel.countDeliveries());
        counts.put("refunds", readModel.countRefunds());
        return counts;
    }

    /** The instant the window helper anchors on; exposed so a caller and a test agree on "now". */
    public Instant now() {
        return clock.now();
    }
}

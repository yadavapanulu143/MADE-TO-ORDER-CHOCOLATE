package com.choco.reporting.application.fake;

import com.choco.reporting.domain.model.CustomerProjection;
import com.choco.reporting.domain.model.DeliveryProjection;
import com.choco.reporting.domain.model.InvoiceProjection;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.model.PaymentProjection;
import com.choco.reporting.domain.model.ProductionProjection;
import com.choco.reporting.domain.model.RefundProjection;
import com.choco.reporting.domain.port.ReportingReadModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An in-memory {@link ReportingReadModel} for application unit tests.
 *
 * <p>It is deliberately an upsert keyed on the aggregate id, matching the real adapter's primary key,
 * so a test that applies the same event twice proves the use case is idempotent rather than merely
 * that a list grew. The real JPA adapter and its constraints are exercised by the integration tests;
 * this fake exists so the projection, report and export logic can be tested with no database.
 */
public final class InMemoryReportingReadModel implements ReportingReadModel {

    private final Map<String, OrderProjection> orders = new LinkedHashMap<>();
    private final Map<String, PaymentProjection> payments = new LinkedHashMap<>();
    private final Map<String, ProductionProjection> production = new LinkedHashMap<>();
    private final Map<String, DeliveryProjection> deliveries = new LinkedHashMap<>();
    private final Map<String, RefundProjection> refunds = new LinkedHashMap<>();
    private final Map<String, CustomerProjection> customers = new LinkedHashMap<>();
    private final Map<String, InvoiceProjection> invoices = new LinkedHashMap<>();

    public int upsertCount() {
        return orders.size() + payments.size() + production.size() + deliveries.size() + refunds.size()
                + customers.size() + invoices.size();
    }

    public int countPayments() {
        return payments.size();
    }

    @Override
    public void upsertOrder(OrderProjection order) {
        orders.put(order.orderId(), order);
    }

    @Override
    public void upsertPayment(PaymentProjection payment) {
        payments.put(payment.paymentId(), payment);
    }

    @Override
    public void upsertProduction(ProductionProjection job) {
        production.put(job.jobId(), job);
    }

    @Override
    public void upsertDelivery(DeliveryProjection delivery) {
        deliveries.put(delivery.deliveryId(), delivery);
    }

    @Override
    public void upsertRefund(RefundProjection refund) {
        RefundProjection existing = refunds.get(refund.refundId());
        refunds.put(refund.refundId(), existing == null ? refund : merge(existing, refund));
    }

    @Override
    public void upsertCustomer(CustomerProjection customer) {
        customers.put(customer.customerId(), customer);
    }

    @Override
    public void upsertInvoice(InvoiceProjection invoice) {
        invoices.put(invoice.invoiceId(), invoice);
    }

    private static RefundProjection merge(RefundProjection old, RefundProjection next) {
        return new RefundProjection(next.refundId(),
                next.orderId() == null ? old.orderId() : next.orderId(),
                next.orderNumber() == null ? old.orderNumber() : next.orderNumber(),
                next.customerId() == null ? old.customerId() : next.customerId(),
                next.amountMinorUnits(), next.currency(), next.status(),
                next.reason() == null ? old.reason() : next.reason(),
                next.fullRefund(),
                next.requestedAt() == null ? old.requestedAt() : next.requestedAt(),
                next.processedAt() == null ? old.processedAt() : next.processedAt());
    }

    @Override
    public Optional<OrderProjection> findOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public Optional<PaymentProjection> findPayment(String paymentId) {
        return Optional.ofNullable(payments.get(paymentId));
    }

    @Override
    public Optional<ProductionProjection> findProduction(String jobId) {
        return Optional.ofNullable(production.get(jobId));
    }

    @Override
    public Optional<DeliveryProjection> findDelivery(String deliveryId) {
        return Optional.ofNullable(deliveries.get(deliveryId));
    }

    @Override
    public Optional<RefundProjection> findRefund(String refundId) {
        return Optional.ofNullable(refunds.get(refundId));
    }

    @Override
    public Optional<InvoiceProjection> findInvoice(String invoiceId) {
        return Optional.ofNullable(invoices.get(invoiceId));
    }

    @Override
    public Optional<CustomerProjection> findCustomer(String customerId) {
        return Optional.ofNullable(customers.get(customerId));
    }

    @Override
    public List<OrderProjection> findOrdersBetween(Instant from, Instant to, int limit) {
        return within(orders.values(), OrderProjection::placedAt, from, to, limit);
    }

    @Override
    public List<PaymentProjection> findPaymentsBetween(Instant from, Instant to, int limit) {
        return within(payments.values(), PaymentProjection::occurredAt, from, to, limit);
    }

    @Override
    public List<ProductionProjection> findProductionBetween(Instant from, Instant to, int limit) {
        return within(production.values(), ProductionProjection::startedAt, from, to, limit);
    }

    @Override
    public List<DeliveryProjection> findDeliveriesBetween(Instant from, Instant to, int limit) {
        return within(deliveries.values(), DeliveryProjection::updatedAt, from, to, limit);
    }

    @Override
    public List<RefundProjection> findRefundsBetween(Instant from, Instant to, int limit) {
        return within(refunds.values(), RefundProjection::requestedAt, from, to, limit);
    }

    @Override
    public List<CustomerProjection> findCustomersRegisteredBetween(Instant from, Instant to, int limit) {
        return within(customers.values(), CustomerProjection::registeredAt, from, to, limit);
    }

    @Override
    public List<InvoiceProjection> findInvoicesBetween(Instant from, Instant to, int limit) {
        return within(invoices.values(), InvoiceProjection::issuedAt, from, to, limit);
    }

    @Override
    public long countOrders() {
        return orders.size();
    }

    @Override
    public long countCustomers() {
        return customers.size();
    }

    @Override
    public long countDeliveries() {
        return deliveries.size();
    }

    @Override
    public long countRefunds() {
        return refunds.size();
    }

    private static <T> List<T> within(java.util.Collection<T> values,
                                      java.util.function.Function<T, Instant> timestamp,
                                      Instant from, Instant to, int limit) {
        List<T> matched = new ArrayList<>();
        for (T value : values) {
            Instant at = timestamp.apply(value);
            if (at != null && !at.isBefore(from) && at.isBefore(to)) {
                matched.add(value);
            }
        }
        matched.sort(Comparator.comparing(timestamp).reversed());
        return matched.size() > limit ? new ArrayList<>(matched.subList(0, limit)) : matched;
    }
}

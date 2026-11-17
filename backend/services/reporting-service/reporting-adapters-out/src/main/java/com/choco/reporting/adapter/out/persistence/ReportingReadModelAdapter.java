package com.choco.reporting.adapter.out.persistence;

import com.choco.reporting.domain.model.CustomerProjection;
import com.choco.reporting.domain.model.DeliveryProjection;
import com.choco.reporting.domain.model.InvoiceProjection;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.model.PaymentProjection;
import com.choco.reporting.domain.model.ProductionProjection;
import com.choco.reporting.domain.model.RefundProjection;
import com.choco.reporting.domain.port.ReportingReadModel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read models, stored in the reporting service's own PostgreSQL database.
 *
 * <p>Every write is an upsert keyed on the aggregate id the event named, so the same event applied
 * twice updates one row rather than adding a second. That is the idempotency the projection relies on,
 * and it is enforced by the primary key, not by a check the application could forget.
 *
 * <p>Reads that feed a report run in a read-only transaction so a page of rows and its count are taken
 * from one snapshot; a report that read the rows and the count in two transactions could show a total
 * that does not match its own content.
 *
 * <p>The adapter maps entities to the domain records here, inside the transaction, rather than handing
 * lazy entities out — a projection row has no lazy children, but keeping the mapping inside the
 * boundary is the same rule the other services follow and avoids a future lazy field becoming an
 * exception after the transaction has closed.
 */
@Repository
class ReportingReadModelAdapter implements ReportingReadModel {

    private final SpringDataOrderProjection orders;
    private final SpringDataPaymentProjection payments;
    private final SpringDataProductionProjection production;
    private final SpringDataDeliveryProjection deliveries;
    private final SpringDataRefundProjection refunds;
    private final SpringDataCustomerProjection customers;
    private final SpringDataInvoiceProjection invoices;

    ReportingReadModelAdapter(SpringDataOrderProjection orders, SpringDataPaymentProjection payments,
                              SpringDataProductionProjection production,
                              SpringDataDeliveryProjection deliveries,
                              SpringDataRefundProjection refunds,
                              SpringDataCustomerProjection customers,
                              SpringDataInvoiceProjection invoices) {
        this.orders = orders;
        this.payments = payments;
        this.production = production;
        this.deliveries = deliveries;
        this.refunds = refunds;
        this.customers = customers;
        this.invoices = invoices;
    }

    // --- Writes ---------------------------------------------------------------------------------

    @Override
    @Transactional
    public void upsertOrder(OrderProjection order) {
        OrderProjectionJpaEntity entity = orders.findById(order.orderId()).orElseGet(() -> {
            OrderProjectionJpaEntity created = new OrderProjectionJpaEntity();
            created.setOrderId(order.orderId());
            return created;
        });
        entity.setOrderNumber(order.orderNumber());
        entity.setCustomerId(order.customerId());
        entity.setStatus(order.status());
        entity.setTotalMinorUnits(order.totalMinorUnits());
        entity.setDeliveryChargeMinorUnits(order.deliveryChargeMinorUnits());
        entity.setCurrency(order.currency());
        entity.setPlacedAt(order.placedAt());
        entity.setConfirmedAt(order.confirmedAt());
        entity.setCancelledAt(order.cancelledAt());
        entity.setCancellationReason(order.cancellationReason());
        orders.save(entity);
    }

    @Override
    @Transactional
    public void upsertPayment(PaymentProjection payment) {
        PaymentProjectionJpaEntity entity = payments.findById(payment.paymentId()).orElseGet(() -> {
            PaymentProjectionJpaEntity created = new PaymentProjectionJpaEntity();
            created.setPaymentId(payment.paymentId());
            return created;
        });
        entity.setOrderId(payment.orderId());
        entity.setCustomerId(payment.customerId());
        entity.setStatus(payment.status());
        entity.setAmountMinorUnits(payment.amountMinorUnits());
        entity.setCurrency(payment.currency());
        entity.setMethod(payment.method());
        entity.setFailureReason(payment.failureReason());
        entity.setOccurredAt(payment.occurredAt());
        payments.save(entity);
    }

    @Override
    @Transactional
    public void upsertProduction(ProductionProjection job) {
        ProductionProjectionJpaEntity entity = production.findById(job.jobId()).orElseGet(() -> {
            ProductionProjectionJpaEntity created = new ProductionProjectionJpaEntity();
            created.setJobId(job.jobId());
            return created;
        });
        entity.setOrderId(job.orderId());
        entity.setOrderNumber(job.orderNumber());
        entity.setCustomerId(job.customerId());
        entity.setVariantLabel(job.variantLabel());
        entity.setQuantity(job.quantity());
        entity.setStatus(job.status());
        if (job.startedAt() != null) {
            entity.setStartedAt(job.startedAt());
        }
        if (job.completedAt() != null) {
            entity.setCompletedAt(job.completedAt());
        }
        production.save(entity);
    }

    @Override
    @Transactional
    public void upsertDelivery(DeliveryProjection delivery) {
        DeliveryProjectionJpaEntity entity = deliveries.findById(delivery.deliveryId()).orElseGet(() -> {
            DeliveryProjectionJpaEntity created = new DeliveryProjectionJpaEntity();
            created.setDeliveryId(delivery.deliveryId());
            return created;
        });
        entity.setOrderId(delivery.orderId());
        entity.setOrderNumber(delivery.orderNumber());
        entity.setCustomerId(delivery.customerId());
        entity.setStatus(delivery.status());
        entity.setDeliveryPersonName(delivery.deliveryPersonName());
        entity.setFailureReason(delivery.failureReason());
        entity.setAttemptNumber(delivery.attemptNumber());
        entity.setUpdatedAt(delivery.updatedAt());
        deliveries.save(entity);
    }

    @Override
    @Transactional
    public void upsertRefund(RefundProjection refund) {
        RefundProjectionJpaEntity entity = refunds.findById(refund.refundId()).orElseGet(() -> {
            RefundProjectionJpaEntity created = new RefundProjectionJpaEntity();
            created.setRefundId(refund.refundId());
            return created;
        });
        // A later processed event must not blank the identifiers an earlier request carried.
        if (refund.orderId() != null) {
            entity.setOrderId(refund.orderId());
        }
        if (refund.orderNumber() != null) {
            entity.setOrderNumber(refund.orderNumber());
        }
        if (refund.customerId() != null) {
            entity.setCustomerId(refund.customerId());
        }
        entity.setAmountMinorUnits(refund.amountMinorUnits());
        entity.setCurrency(refund.currency());
        entity.setStatus(refund.status());
        if (refund.reason() != null) {
            entity.setReason(refund.reason());
        }
        entity.setFullRefund(refund.fullRefund());
        if (refund.requestedAt() != null) {
            entity.setRequestedAt(refund.requestedAt());
        }
        if (refund.processedAt() != null) {
            entity.setProcessedAt(refund.processedAt());
        }
        refunds.save(entity);
    }

    @Override
    @Transactional
    public void upsertCustomer(CustomerProjection customer) {
        CustomerProjectionJpaEntity entity = customers.findById(customer.customerId()).orElseGet(() -> {
            CustomerProjectionJpaEntity created = new CustomerProjectionJpaEntity();
            created.setCustomerId(customer.customerId());
            return created;
        });
        entity.setFullName(customer.fullName());
        entity.setEmail(customer.email());
        entity.setMobile(customer.mobile());
        entity.setEmailVerified(customer.emailVerified());
        entity.setMobileVerified(customer.mobileVerified());
        entity.setRegisteredAt(customer.registeredAt());
        entity.setVerifiedAt(customer.verifiedAt());
        customers.save(entity);
    }

    @Override
    @Transactional
    public void upsertInvoice(InvoiceProjection invoice) {
        InvoiceProjectionJpaEntity entity = invoices.findById(invoice.invoiceId()).orElseGet(() -> {
            InvoiceProjectionJpaEntity created = new InvoiceProjectionJpaEntity();
            created.setInvoiceId(invoice.invoiceId());
            return created;
        });
        entity.setOrderId(invoice.orderId());
        entity.setOrderNumber(invoice.orderNumber());
        entity.setInvoiceNumber(invoice.invoiceNumber());
        entity.setCustomerId(invoice.customerId());
        entity.setTotalMinorUnits(invoice.totalMinorUnits());
        entity.setCurrency(invoice.currency());
        entity.setIssuedAt(invoice.issuedAt());
        invoices.save(entity);
    }

    // --- Reads ----------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderProjection> findOrder(String orderId) {
        return orders.findById(orderId).map(ReportingReadModelAdapter::toOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentProjection> findPayment(String paymentId) {
        return payments.findById(paymentId).map(ReportingReadModelAdapter::toPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductionProjection> findProduction(String jobId) {
        return production.findById(jobId).map(ReportingReadModelAdapter::toProduction);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeliveryProjection> findDelivery(String deliveryId) {
        return deliveries.findById(deliveryId).map(ReportingReadModelAdapter::toDelivery);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundProjection> findRefund(String refundId) {
        return refunds.findById(refundId).map(ReportingReadModelAdapter::toRefund);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvoiceProjection> findInvoice(String invoiceId) {
        return invoices.findById(invoiceId).map(ReportingReadModelAdapter::toInvoice);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerProjection> findCustomer(String customerId) {
        return customers.findById(customerId).map(ReportingReadModelAdapter::toCustomer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderProjection> findOrdersBetween(Instant from, Instant to, int limit) {
        return orders.findByPlacedAtGreaterThanEqualAndPlacedAtLessThan(from, to,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "placedAt")))
                .stream().map(ReportingReadModelAdapter::toOrder).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentProjection> findPaymentsBetween(Instant from, Instant to, int limit) {
        return payments.findByOccurredAtGreaterThanEqualAndOccurredAtLessThan(from, to,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .stream().map(ReportingReadModelAdapter::toPayment).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductionProjection> findProductionBetween(Instant from, Instant to, int limit) {
        return production.findByStartedAtGreaterThanEqualAndStartedAtLessThan(from, to,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startedAt")))
                .stream().map(ReportingReadModelAdapter::toProduction).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryProjection> findDeliveriesBetween(Instant from, Instant to, int limit) {
        return deliveries.findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(from, to,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .stream().map(ReportingReadModelAdapter::toDelivery).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundProjection> findRefundsBetween(Instant from, Instant to, int limit) {
        return refunds.findByRequestedAtGreaterThanEqualAndRequestedAtLessThan(from, to,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "requestedAt")))
                .stream().map(ReportingReadModelAdapter::toRefund).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerProjection> findCustomersRegisteredBetween(Instant from, Instant to, int limit) {
        return customers.findByRegisteredAtGreaterThanEqualAndRegisteredAtLessThan(from, to,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "registeredAt")))
                .stream().map(ReportingReadModelAdapter::toCustomer).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceProjection> findInvoicesBetween(Instant from, Instant to, int limit) {
        return invoices.findByIssuedAtGreaterThanEqualAndIssuedAtLessThan(from, to,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "issuedAt")))
                .stream().map(ReportingReadModelAdapter::toInvoice).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countOrders() {
        return orders.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countCustomers() {
        return customers.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countDeliveries() {
        return deliveries.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countRefunds() {
        return refunds.count();
    }


    // --- Mapping --------------------------------------------------------------------------------

    private static OrderProjection toOrder(OrderProjectionJpaEntity e) {
        return new OrderProjection(e.getOrderId(), e.getOrderNumber(), e.getCustomerId(), e.getStatus(),
                e.getTotalMinorUnits(), e.getDeliveryChargeMinorUnits(), e.getCurrency(), e.getPlacedAt(),
                e.getConfirmedAt(), e.getCancelledAt(), e.getCancellationReason());
    }

    private static PaymentProjection toPayment(PaymentProjectionJpaEntity e) {
        return new PaymentProjection(e.getPaymentId(), e.getOrderId(), e.getCustomerId(), e.getStatus(),
                e.getAmountMinorUnits(), e.getCurrency(), e.getMethod(), e.getFailureReason(),
                e.getOccurredAt());
    }

    private static ProductionProjection toProduction(ProductionProjectionJpaEntity e) {
        return new ProductionProjection(e.getJobId(), e.getOrderId(), e.getOrderNumber(), e.getCustomerId(),
                e.getVariantLabel(), e.getQuantity(), e.getStatus(), e.getStartedAt(), e.getCompletedAt());
    }

    private static DeliveryProjection toDelivery(DeliveryProjectionJpaEntity e) {
        return new DeliveryProjection(e.getDeliveryId(), e.getOrderId(), e.getOrderNumber(),
                e.getCustomerId(), e.getStatus(), e.getDeliveryPersonName(), e.getFailureReason(),
                e.getAttemptNumber(), e.getUpdatedAt());
    }

    private static RefundProjection toRefund(RefundProjectionJpaEntity e) {
        return new RefundProjection(e.getRefundId(), e.getOrderId(), e.getOrderNumber(), e.getCustomerId(),
                e.getAmountMinorUnits(), e.getCurrency(), e.getStatus(), e.getReason(), e.isFullRefund(),
                e.getRequestedAt(), e.getProcessedAt());
    }

    private static CustomerProjection toCustomer(CustomerProjectionJpaEntity e) {
        return new CustomerProjection(e.getCustomerId(), e.getFullName(), e.getEmail(), e.getMobile(),
                e.isEmailVerified(), e.isMobileVerified(), e.getRegisteredAt(), e.getVerifiedAt());
    }

    private static InvoiceProjection toInvoice(InvoiceProjectionJpaEntity e) {
        return new InvoiceProjection(e.getInvoiceId(), e.getOrderId(), e.getOrderNumber(),
                e.getInvoiceNumber(), e.getCustomerId(), e.getTotalMinorUnits(), e.getCurrency(),
                e.getIssuedAt());
    }
}

package com.choco.reporting.domain.port;

import com.choco.reporting.domain.model.CustomerProjection;
import com.choco.reporting.domain.model.DeliveryProjection;
import com.choco.reporting.domain.model.InvoiceProjection;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.model.PaymentProjection;
import com.choco.reporting.domain.model.ProductionProjection;
import com.choco.reporting.domain.model.RefundProjection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The read models the seller reports on.
 *
 * <p>One port, not seven, because every method is the same kind of thing: a local projection updated
 * from an event and read back for a report. Keeping them together makes the write side (upsert on
 * event) and the read side (range queries) a single reviewable contract, and lets a use case be written
 * against one interface.
 *
 * <p>The range reads take an explicit {@code limit} rather than a page. A report is an aggregate over
 * a whole window, not a page of a list, so the read must return every row in the window up to the
 * ceiling — a page-sized read would silently truncate the report and present a total that does not
 * match its own rows. A caller passes {@link com.choco.reporting.domain.report.ReportLimits} and treats
 * a result that fills the limit as "too large to answer honestly" rather than as a complete set.
 *
 * <p>There is deliberately no method that reaches into another service's database. Every projection
 * here is fed by an event; nothing is fetched live.
 */
public interface ReportingReadModel {

    // --- Writes (from events) -------------------------------------------------------------------

    void upsertOrder(OrderProjection order);

    void upsertPayment(PaymentProjection payment);

    void upsertProduction(ProductionProjection production);

    void upsertDelivery(DeliveryProjection delivery);

    void upsertRefund(RefundProjection refund);

    void upsertCustomer(CustomerProjection customer);

    void upsertInvoice(InvoiceProjection invoice);

    // --- Reads (for reports and exports) --------------------------------------------------------

    Optional<OrderProjection> findOrder(String orderId);

    Optional<PaymentProjection> findPayment(String paymentId);

    Optional<ProductionProjection> findProduction(String jobId);

    Optional<DeliveryProjection> findDelivery(String deliveryId);

    Optional<RefundProjection> findRefund(String refundId);

    Optional<InvoiceProjection> findInvoice(String invoiceId);

    Optional<CustomerProjection> findCustomer(String customerId);

    List<OrderProjection> findOrdersBetween(Instant from, Instant to, int limit);

    List<PaymentProjection> findPaymentsBetween(Instant from, Instant to, int limit);

    List<ProductionProjection> findProductionBetween(Instant from, Instant to, int limit);

    List<DeliveryProjection> findDeliveriesBetween(Instant from, Instant to, int limit);

    List<RefundProjection> findRefundsBetween(Instant from, Instant to, int limit);

    List<CustomerProjection> findCustomersRegisteredBetween(Instant from, Instant to, int limit);

    List<InvoiceProjection> findInvoicesBetween(Instant from, Instant to, int limit);

    long countOrders();

    long countCustomers();

    long countDeliveries();

    long countRefunds();
}

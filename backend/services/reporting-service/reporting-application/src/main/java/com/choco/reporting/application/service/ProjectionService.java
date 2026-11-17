package com.choco.reporting.application.service;

import com.choco.core.port.Clock;
import com.choco.core.port.TransactionRunner;
import com.choco.reporting.domain.model.CustomerProjection;
import com.choco.reporting.domain.model.DeliveryProjection;
import com.choco.reporting.domain.model.InvoiceProjection;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.model.PaymentProjection;
import com.choco.reporting.domain.model.ProductionProjection;
import com.choco.reporting.domain.model.RefundProjection;
import com.choco.reporting.domain.port.ReportingReadModel;
import java.time.Instant;
import java.util.Map;

/**
 * Applies a platform event to the read models.
 *
 * <p>The mapping from an event type string to one of these methods lives in the inbound adapter, not
 * here, so the application layer never imports the events module and each use case can be called from
 * a unit test with a plain map. The adapter is the only place that knows the wire names.
 *
 * <p>Every projection is an upsert keyed on the aggregate the event names, so each handler is
 * idempotent by construction: a redelivered event overwrites a row with the same values rather than
 * creating a second one. That is what makes at-least-once delivery safe without a separate dedupe
 * table — the read model has no append-only rows to duplicate.
 *
 * <p>An event missing a field it needs is ignored rather than thrown: a projection is a derived
 * convenience, and a malformed event must not poison the queue for the events that follow it.
 */
public class ProjectionService {

    private final ReportingReadModel readModel;
    private final Clock clock;
    private final TransactionRunner transactions;

    public ProjectionService(ReportingReadModel readModel, Clock clock, TransactionRunner transactions) {
        this.readModel = readModel;
        this.clock = clock;
        this.transactions = transactions;
    }

    // --- Orders ---------------------------------------------------------------------------------

    public void orderPlaced(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        if (orderId == null) {
            return;
        }
        transactions.execute(() -> {
            OrderProjection existing = readModel.findOrder(orderId).orElse(null);
            readModel.upsertOrder(new OrderProjection(orderId, str(payload, "orderNumber"),
                    str(payload, "customerId"), OrderProjection.PLACED,
                    lng(payload, "totalMinorUnits"), lng(payload, "deliveryChargeMinorUnits"),
                    orDefault(str(payload, "currency"), "INR"), instant(payload, "occurredAt"),
                    existing == null ? null : existing.confirmedAt(),
                    existing == null ? null : existing.cancelledAt(),
                    existing == null ? null : existing.cancellationReason()));
        });
    }

    public void orderConfirmed(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        if (orderId == null) {
            return;
        }
        transactions.execute(() -> {
            OrderProjection existing = readModel.findOrder(orderId).orElse(null);
            readModel.upsertOrder(new OrderProjection(orderId, str(payload, "orderNumber"),
                    str(payload, "customerId"), OrderProjection.CONFIRMED,
                    existing == null ? 0L : existing.totalMinorUnits(),
                    lngOr(payload, "deliveryChargeMinorUnits",
                            existing == null ? 0L : existing.deliveryChargeMinorUnits()),
                    existing == null ? orDefault(str(payload, "currency"), "INR") : existing.currency(),
                    existing == null ? instant(payload, "occurredAt") : existing.placedAt(),
                    instant(payload, "occurredAt"),
                    existing == null ? null : existing.cancelledAt(),
                    existing == null ? null : existing.cancellationReason()));
        });
    }

    public void orderCancelled(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        if (orderId == null) {
            return;
        }
        transactions.execute(() -> {
            OrderProjection existing = readModel.findOrder(orderId).orElse(null);
            readModel.upsertOrder(new OrderProjection(orderId, str(payload, "orderNumber"),
                    str(payload, "customerId"), OrderProjection.CANCELLED,
                    existing == null ? 0L : existing.totalMinorUnits(),
                    existing == null ? 0L : existing.deliveryChargeMinorUnits(),
                    existing == null ? "INR" : existing.currency(),
                    existing == null ? instant(payload, "occurredAt") : existing.placedAt(),
                    existing == null ? null : existing.confirmedAt(),
                    instant(payload, "occurredAt"),
                    str(payload, "reason")));
        });
    }

    // --- Payments -------------------------------------------------------------------------------

    public void paymentCaptured(Map<String, Object> payload) {
        payment(payload, PaymentProjection.PAID, null);
    }

    public void paymentFailed(Map<String, Object> payload) {
        payment(payload, PaymentProjection.FAILED, str(payload, "reason"));
    }

    public void paymentPending(Map<String, Object> payload) {
        payment(payload, PaymentProjection.PENDING, null);
    }

    private void payment(Map<String, Object> payload, String status, String failureReason) {
        String paymentId = str(payload, "providerPaymentId");
        if (paymentId == null) {
            paymentId = str(payload, "aggregateId");
        }
        if (paymentId == null) {
            return;
        }
        String resolved = paymentId;
        transactions.execute(() -> readModel.upsertPayment(new PaymentProjection(resolved,
                str(payload, "orderId"), str(payload, "customerId"), status,
                lng(payload, "amountMinorUnits"), orDefault(str(payload, "currency"), "INR"),
                str(payload, "method"), failureReason, instant(payload, "occurredAt"))));
    }

    // --- Refunds --------------------------------------------------------------------------------

    public void refundRequested(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        if (orderId == null) {
            return;
        }
        transactions.execute(() -> readModel.upsertRefund(new RefundProjection(refundId(orderId),
                orderId, str(payload, "orderNumber"), str(payload, "customerId"),
                lng(payload, "amountMinorUnits"), orDefault(str(payload, "currency"), "INR"),
                RefundProjection.REQUESTED, str(payload, "reason"), false,
                instant(payload, "occurredAt"), null)));
    }

    public void refundProcessed(Map<String, Object> payload) {
        String orderId = str(payload, "orderId");
        if (orderId == null) {
            return;
        }
        // Keyed on the order id, the same key the request used. Keying on the provider's refund id
        // instead would make a request and its processed counterpart two rows, and the seller's report
        // would count one refund twice — once as pending and once as paid.
        transactions.execute(() -> readModel.upsertRefund(new RefundProjection(refundId(orderId),
                orderId, null, null, lng(payload, "amountMinorUnits"),
                orDefault(str(payload, "currency"), "INR"), RefundProjection.PROCESSED, null,
                bool(payload, "fullRefund"), null, instant(payload, "occurredAt"))));
    }

    private static String refundId(String orderId) {
        return "refund-" + orderId;
    }

    // --- Production -----------------------------------------------------------------------------

    public void productionStarted(Map<String, Object> payload) {
        production(payload, ProductionProjection.PREPARATION_STARTED, true);
    }

    public void productionCompleted(Map<String, Object> payload) {
        production(payload, ProductionProjection.PREPARATION_COMPLETED, false);
    }

    public void readyForDispatch(Map<String, Object> payload) {
        production(payload, ProductionProjection.READY_FOR_DISPATCH, false);
    }

    private void production(Map<String, Object> payload, String fallbackStatus, boolean started) {
        String jobId = str(payload, "jobId");
        if (jobId == null) {
            return;
        }
        // The event carries the producer's own status name; copy it verbatim rather than re-deriving it,
        // so the report and the kitchen cannot disagree about what a job's status is.
        String status = orDefault(str(payload, "status"), fallbackStatus);
        transactions.execute(() -> {
            // Later events carry only the fields that changed. Merging with the row already stored keeps
            // the job's identity (order, variant, quantity) and the instant it started, so a completion
            // does not blank out everything the start recorded.
            ProductionProjection existing = readModel.findProduction(jobId).orElse(null);
            Instant occurredAt = instant(payload, "occurredAt");
            readModel.upsertProduction(new ProductionProjection(jobId,
                    orDefault(str(payload, "orderId"), existing == null ? null : existing.orderId()),
                    orDefault(str(payload, "orderNumber"), existing == null ? null : existing.orderNumber()),
                    orDefault(str(payload, "customerId"), existing == null ? null : existing.customerId()),
                    orDefault(str(payload, "variantLabel"), existing == null ? null : existing.variantLabel()),
                    (int) lngOr(payload, "quantity", existing == null ? 0L : existing.quantity()),
                    status,
                    started ? occurredAt : (existing == null ? null : existing.startedAt()),
                    ProductionProjection.PREPARATION_COMPLETED.equals(status) ? occurredAt
                            : (existing == null ? null : existing.completedAt())));
        });
    }

    // --- Delivery -------------------------------------------------------------------------------

    public void deliveryAssigned(Map<String, Object> payload) {
        delivery(payload, DeliveryProjection.ASSIGNED);
    }

    public void outForDelivery(Map<String, Object> payload) {
        delivery(payload, DeliveryProjection.OUT_FOR_DELIVERY);
    }

    public void deliveryDelivered(Map<String, Object> payload) {
        delivery(payload, DeliveryProjection.DELIVERED);
    }

    public void deliveryFailed(Map<String, Object> payload) {
        delivery(payload, DeliveryProjection.ATTEMPT_FAILED);
    }

    private void delivery(Map<String, Object> payload, String fallbackStatus) {
        String deliveryId = str(payload, "deliveryId");
        if (deliveryId == null) {
            return;
        }
        String status = orDefault(str(payload, "status"), fallbackStatus);
        transactions.execute(() -> {
            DeliveryProjection existing = readModel.findDelivery(deliveryId).orElse(null);
            readModel.upsertDelivery(new DeliveryProjection(deliveryId,
                    orDefault(str(payload, "orderId"), existing == null ? null : existing.orderId()),
                    orDefault(str(payload, "orderNumber"),
                            existing == null ? null : existing.orderNumber()),
                    orDefault(str(payload, "customerId"), existing == null ? null : existing.customerId()),
                    status,
                    orDefault(str(payload, "deliveryPersonName"),
                            existing == null ? null : existing.deliveryPersonName()),
                    // A failure reason is cleared by a later success, so a delivered row does not keep
                    // the reason from an earlier failed attempt.
                    DeliveryProjection.ATTEMPT_FAILED.equals(status) ? str(payload, "failureReason")
                            : null,
                    (int) lngOr(payload, "attemptNumber",
                            existing == null ? 0L : existing.attemptNumber()),
                    instant(payload, "occurredAt")));
        });
    }

    // --- Customers ------------------------------------------------------------------------------

    public void customerRegistered(Map<String, Object> payload) {
        String customerId = customerId(payload);
        if (customerId == null) {
            return;
        }
        transactions.execute(() -> {
            CustomerProjection existing = readModel.findCustomer(customerId).orElse(null);
            readModel.upsertCustomer(new CustomerProjection(customerId, str(payload, "fullName"),
                    str(payload, "email"), str(payload, "mobile"),
                    existing != null && existing.emailVerified(),
                    existing != null && existing.mobileVerified(),
                    instant(payload, "occurredAt"),
                    existing == null ? null : existing.verifiedAt()));
        });
    }

    public void customerVerified(Map<String, Object> payload) {
        String customerId = customerId(payload);
        if (customerId == null) {
            return;
        }
        transactions.execute(() -> {
            CustomerProjection existing = readModel.findCustomer(customerId).orElse(null);
            String channel = str(payload, "channel");
            boolean emailVerified = existing != null && existing.emailVerified();
            boolean mobileVerified = existing != null && existing.mobileVerified();
            if ("EMAIL".equalsIgnoreCase(channel)) {
                emailVerified = true;
            } else if ("MOBILE".equalsIgnoreCase(channel)) {
                mobileVerified = true;
            }
            readModel.upsertCustomer(new CustomerProjection(customerId,
                    existing == null ? null : existing.fullName(),
                    existing == null ? null : existing.email(),
                    existing == null ? null : existing.mobile(),
                    emailVerified, mobileVerified,
                    existing == null ? instant(payload, "occurredAt") : existing.registeredAt(),
                    instant(payload, "occurredAt")));
        });
    }

    public void customerContactChanged(Map<String, Object> payload) {
        String customerId = customerId(payload);
        if (customerId == null) {
            return;
        }
        transactions.execute(() -> {
            CustomerProjection existing = readModel.findCustomer(customerId).orElse(null);
            // A contact change clears the verified flag for that channel, exactly as the identity
            // service does: the new address has not been proven. Mirroring it keeps the report honest.
            String email = str(payload, "email");
            String mobile = str(payload, "mobile");
            boolean emailVerified = existing != null && existing.emailVerified()
                    && existing.email() != null && existing.email().equals(email);
            boolean mobileVerified = existing != null && existing.mobileVerified()
                    && existing.mobile() != null && existing.mobile().equals(mobile);
            readModel.upsertCustomer(new CustomerProjection(customerId, str(payload, "fullName"),
                    email, mobile, emailVerified, mobileVerified,
                    existing == null ? instant(payload, "occurredAt") : existing.registeredAt(),
                    existing == null ? null : existing.verifiedAt()));
        });
    }

    // --- Invoices -------------------------------------------------------------------------------

    public void invoiceIssued(Map<String, Object> payload) {
        String invoiceId = str(payload, "invoiceId");
        if (invoiceId == null) {
            return;
        }
        transactions.execute(() -> {
            InvoiceProjection existing = readModel.findInvoice(invoiceId).orElse(null);
            readModel.upsertInvoice(new InvoiceProjection(invoiceId,
                    orDefault(str(payload, "orderId"), existing == null ? null : existing.orderId()),
                    orDefault(str(payload, "orderNumber"),
                            existing == null ? null : existing.orderNumber()),
                    orDefault(str(payload, "invoiceNumber"),
                            existing == null ? null : existing.invoiceNumber()),
                    orDefault(str(payload, "customerId"), existing == null ? null : existing.customerId()),
                    lngOr(payload, "totalMinorUnits", existing == null ? 0L : existing.totalMinorUnits()),
                    orDefault(str(payload, "currency"),
                            existing == null ? "INR" : existing.currency()),
                    instant(payload, "occurredAt")));
        });
    }

    // --- Helpers --------------------------------------------------------------------------------

    private static String customerId(Map<String, Object> payload) {
        String customerId = str(payload, "aggregateId");
        return customerId == null ? str(payload, "customerId") : customerId;
    }

    private static String str(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long lng(Map<String, Object> payload, String key) {
        return lngOr(payload, key, 0L);
    }

    private static long lngOr(Map<String, Object> payload, String key, long fallback) {
        Object value = payload.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean bool(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static Instant instant(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.toString());
        } catch (RuntimeException e) {
            return null;
        }
    }
}

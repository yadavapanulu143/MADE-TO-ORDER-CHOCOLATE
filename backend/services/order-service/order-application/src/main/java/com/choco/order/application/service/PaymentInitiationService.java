package com.choco.order.application.service;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.order.application.dto.PaymentInitiationView;
import com.choco.order.application.port.AuditLogPort;
import com.choco.order.application.port.PaymentPort;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.port.OrderRepository;
import java.util.UUID;

/**
 * Starts a payment for one of the customer's own orders.
 *
 * <p>This closes the gap the payment service left open: the payment service can create a provider
 * order, and the order service learns the outcome from the payment events, but nothing connected the
 * two. Without this use case an order sat in {@code PAYMENT_PENDING} forever, because there was no path
 * that asked the provider to collect anything.
 *
 * <p><b>The order owns the amount.</b> The figure sent to the payment service is the order's own frozen
 * total — items plus the delivery charge captured at checkout — never a number from the request. A
 * caller cannot ask to be charged less than the order costs, and because the order's
 * {@code markPaid} also checks the captured amount against that same total, a short payment is refused
 * on the way back as well. Both ends of the wire agree on one figure.
 *
 * <p><b>Ownership is checked here, not assumed.</b> The order is loaded and asserted to belong to the
 * caller before anything is sent. The payment service independently derives the customer from the
 * forwarded token, so a bug in this check cannot be turned into starting a payment against someone
 * else's order — but the check is still made, because the storefront error a customer should see is
 * "not your order", not a payment service refusal.
 *
 * <p><b>No transaction is held across the call.</b> The order is read and validated, then the HTTP call
 * to the payment service is made outside any database transaction, so a slow provider does not pin a
 * database connection. Nothing on the order is written here: the order moves only when the payment
 * service reports a verified outcome as an event, which is the single source of truth for whether money
 * arrived.
 *
 * <p><b>Idempotent by the payment service's design.</b> The key supplied by the storefront is forwarded,
 * and the payment service additionally keys on the order id, so a retry after a timeout returns the
 * first provider order rather than creating a second. This service therefore does not need an
 * idempotency record of its own — it holds no state the retry could duplicate.
 */
public class PaymentInitiationService {

    private static final String ACTOR_CUSTOMER = "CUSTOMER";
    private static final String TARGET = "ORDER";

    private final OrderRepository orderRepository;
    private final PaymentPort paymentPort;
    private final AuditLogPort auditLog;

    public PaymentInitiationService(OrderRepository orderRepository, PaymentPort paymentPort,
                                    AuditLogPort auditLog) {
        this.orderRepository = orderRepository;
        this.paymentPort = paymentPort;
        this.auditLog = auditLog;
    }

    /**
     * Creates (or returns the existing) provider order for the caller's own order.
     *
     * @param customerId     the caller, from the token; the order must belong to them
     * @param orderId        the order to pay for
     * @param idempotencyKey a client-supplied key, forwarded so a retry reuses the first provider order
     */
    public PaymentInitiationView initiate(UUID customerId, UUID orderId, String idempotencyKey) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Order not found"));
        order.assertOwnedBy(customerId);
        order.assertPaymentInitiable();

        PaymentPort.Initiation initiation = paymentPort.initiate(orderId, order.total(),
                order.orderNumber(), idempotencyKey);

        auditLog.record(ACTOR_CUSTOMER, customerId.toString(), "order.payment.initiate", TARGET,
                orderId.toString(), "SUCCESS", initiation.providerOrderId(), null);

        return new PaymentInitiationView(
                initiation.provider(),
                initiation.providerOrderId(),
                initiation.amountMinorUnits(),
                initiation.amount(),
                initiation.currency(),
                initiation.receipt(),
                initiation.publicKey());
    }
}

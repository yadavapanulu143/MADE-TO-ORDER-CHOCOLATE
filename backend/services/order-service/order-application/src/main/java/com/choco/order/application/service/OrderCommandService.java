package com.choco.order.application.service;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.port.Clock;
import com.choco.core.port.EventPublisher;
import com.choco.core.port.TransactionRunner;
import com.choco.order.application.command.AddOrderNoteCommand;
import com.choco.order.application.command.AssignDeliveryCommand;
import com.choco.order.application.command.CancelOrderCommand;
import com.choco.order.application.command.DeliveryProgressCommand;
import com.choco.order.application.command.PreparationProgressCommand;
import com.choco.order.application.command.RequestRefundCommand;
import com.choco.order.application.dto.OrderView;
import com.choco.order.application.port.AuditLogPort;
import com.choco.order.domain.order.Order;
import com.choco.order.domain.port.OrderRepository;
import java.util.Currency;
import java.util.UUID;

/**
 * The write side of orders: the lifecycle moves the seller and the customer make, and the payment-state
 * changes the payment service reports.
 *
 * <p>Every method loads the aggregate and calls a named method on it; none sets a status directly. The
 * aggregate decides whether a move is legal, so the made-to-order rule — never prepare before a
 * verified payment — is enforced here for free, whichever endpoint arrives.
 *
 * <p>Domain events are drained and published after the transaction commits, so a consumer never sees an
 * event for an order that was rolled back.
 */
public class OrderCommandService {

    private static final String ACTOR_ADMIN = "ADMIN";
    private static final String ACTOR_CUSTOMER = "CUSTOMER";
    private static final String ACTOR_SYSTEM = "SYSTEM";
    private static final String TARGET = "ORDER";

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final AuditLogPort auditLog;
    private final Clock clock;
    private final TransactionRunner transactions;

    public OrderCommandService(OrderRepository orderRepository, EventPublisher eventPublisher,
                               AuditLogPort auditLog, Clock clock, TransactionRunner transactions) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.auditLog = auditLog;
        this.clock = clock;
        this.transactions = transactions;
    }

    // -------------------------------------------------------------------------------------------
    // Customer actions
    // -------------------------------------------------------------------------------------------

    /** A customer cancels their own order. Refused once it has left for delivery. */
    public OrderView cancelOwnOrder(UUID customerId, CancelOrderCommand command) {
        return transactions.execute(() -> {
            Order order = require(command.orderId());
            order.assertOwnedBy(customerId);
            order.cancel(command.reason(), customerId.toString(), clock.now());
            Order saved = persist(order);
            audit(ACTOR_CUSTOMER, customerId.toString(), "order.cancel", saved, "SUCCESS",
                    command.reason());
            return OrderViewMapper.toView(saved);
        });
    }

    /** A customer requests a refund for their own order. Recorded for the seller to review. */
    public OrderView requestRefund(UUID customerId, RequestRefundCommand command) {
        return transactions.execute(() -> {
            Order order = require(command.orderId());
            order.assertOwnedBy(customerId);
            order.requestRefund(command.reason(), clock.now());
            Order saved = persist(order);
            audit(ACTOR_CUSTOMER, customerId.toString(), "order.refund-request", saved, "SUCCESS",
                    command.reason());
            return OrderViewMapper.toView(saved);
        });
    }

    // -------------------------------------------------------------------------------------------
    // Seller actions
    // -------------------------------------------------------------------------------------------

    /** The seller cancels an order, e.g. because it cannot be fulfilled. */
    public OrderView cancelAsAdmin(String adminId, CancelOrderCommand command) {
        return transactions.execute(() -> {
            Order order = require(command.orderId());
            order.cancel(command.reason(), adminId, clock.now());
            Order saved = persist(order);
            audit(ACTOR_ADMIN, adminId, "order.cancel", saved, "SUCCESS", command.reason());
            return OrderViewMapper.toView(saved);
        });
    }

    /**
     * Advances made-to-order preparation.
     *
     * <p>One entry point for the preparation steps, dispatching on the requested action. The aggregate
     * refuses a step that is not legal from the current status — starting packaging before preparation
     * is complete, for instance — so the seller gets a clear error rather than a corrupted order.
     */
    public OrderView advancePreparation(String adminId, PreparationProgressCommand command) {
        return transactions.execute(() -> {
            Order order = require(command.orderId());
            switch (command.action()) {
                case START -> order.startPreparation(adminId, clock.now());
                case HOLD -> order.holdPreparation(command.reason(), adminId, clock.now());
                case RESUME -> order.resumePreparation(adminId, clock.now());
                case COMPLETE -> order.completePreparation(adminId, clock.now());
                case PACKAGING -> order.startPackaging(adminId, clock.now());
                case READY_FOR_DISPATCH -> order.markReadyForDispatch(adminId, clock.now());
            }
            Order saved = persist(order);
            audit(ACTOR_ADMIN, adminId, "order.preparation." + command.action().name().toLowerCase(),
                    saved, "SUCCESS", command.reason());
            return OrderViewMapper.toView(saved);
        });
    }

    /** The seller records who will deliver the order. */
    public OrderView assignDelivery(String adminId, AssignDeliveryCommand command) {
        return transactions.execute(() -> {
            Order order = require(command.orderId());
            order.assignDelivery(command.deliveryPersonName(), command.deliveryPersonPhone(), adminId,
                    clock.now());
            Order saved = persist(order);
            audit(ACTOR_ADMIN, adminId, "order.delivery.assign", saved, "SUCCESS",
                    command.deliveryPersonName());
            return OrderViewMapper.toView(saved);
        });
    }

    /** The seller records a delivery step: out for delivery, delivered, failed, rescheduled, returned. */
    public OrderView advanceDelivery(String adminId, DeliveryProgressCommand command) {
        return transactions.execute(() -> {
            Order order = require(command.orderId());
            switch (command.action()) {
                case OUT_FOR_DELIVERY -> order.markOutForDelivery(adminId, clock.now());
                case DELIVERED -> order.markDelivered(adminId, clock.now());
                case ATTEMPT_FAILED -> order.recordFailedDeliveryAttempt(command.reason(), adminId,
                        clock.now());
                case RESCHEDULED -> order.rescheduleDelivery(adminId, clock.now());
                case RETURNED -> order.markReturned(command.reason(), adminId, clock.now());
            }
            Order saved = persist(order);
            audit(ACTOR_ADMIN, adminId, "order.delivery." + command.action().name().toLowerCase(), saved,
                    "SUCCESS", command.reason());
            return OrderViewMapper.toView(saved);
        });
    }

    /** The seller attaches a note. */
    public OrderView addNote(String adminId, AddOrderNoteCommand command) {
        return transactions.execute(() -> {
            Order order = require(command.orderId());
            order.addNote(command.text(), adminId, clock.now());
            Order saved = persist(order);
            audit(ACTOR_ADMIN, adminId, "order.note.add", saved, "SUCCESS", null);
            return OrderViewMapper.toView(saved);
        });
    }

    // -------------------------------------------------------------------------------------------
    // Payment-service driven state (never from a browser)
    // -------------------------------------------------------------------------------------------

    /**
     * Records that the payment service has verified a successful payment.
     *
     * <p>This is the only way an order becomes {@code CONFIRMED} and the only way preparation is
     * unlocked. It is idempotent, so a webhook redelivered after the redirect already confirmed the
     * order does nothing the second time.
     *
     * <p>The amount the provider collected is passed through and checked against the order's frozen
     * total. {@code paidAmountMinorUnits} may be null when the caller has no amount to offer (an
     * internal test seam); a payment that reaches this method from the payment service always has one.
     */
    public void markPaid(UUID orderId, String paymentId, String providerOrderId,
                         Long paidAmountMinorUnits, String currency) {
        transactions.execute(() -> {
            Order order = require(orderId);
            Money paid = paidAmountMinorUnits == null ? null
                    : Money.ofMinorUnits(paidAmountMinorUnits, Currency.getInstance(
                            currency == null ? "INR" : currency));
            order.markPaid(paymentId, providerOrderId, paid, clock.now());
            persist(order);
        });
    }

    /** Records a verified payment with no amount cross-check, for callers that hold no amount. */
    public void markPaid(UUID orderId, String paymentId, String providerOrderId) {
        markPaid(orderId, paymentId, providerOrderId, null, null);
    }

    /** Records a failed or unverifiable payment. */
    public void markPaymentFailed(UUID orderId, String reason) {
        transactions.execute(() -> {
            Order order = require(orderId);
            order.markPaymentFailed(reason, clock.now());
            persist(order);
        });
    }

    /** Records that a payment attempt is in flight. */
    public void markPaymentVerificationPending(UUID orderId, String paymentId, String providerOrderId) {
        transactions.execute(() -> {
            Order order = require(orderId);
            order.markPaymentVerificationPending(paymentId, providerOrderId, clock.now());
            persist(order);
        });
    }

    /** Records that the payment service processed a refund. */
    public void markRefunded(UUID orderId) {
        transactions.execute(() -> {
            Order order = require(orderId);
            order.markRefunded(clock.now());
            persist(order);
        });
    }

    /** Returns a failed order to pending so the customer can pay again. */
    public OrderView allowPaymentRetry(String actorId, UUID orderId) {
        return transactions.execute(() -> {
            Order order = require(orderId);
            order.allowPaymentRetry(clock.now());
            Order saved = persist(order);
            audit(ACTOR_CUSTOMER, actorId, "order.payment.retry", saved, "SUCCESS", null);
            return OrderViewMapper.toView(saved);
        });
    }

    // -------------------------------------------------------------------------------------------

    private Order require(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Order not found"));
    }

    /** Saves the aggregate and publishes everything it recorded, after the save. */
    private Order persist(Order order) {
        Order saved = orderRepository.save(order);
        eventPublisher.publishAll(order.pullDomainEvents());
        return saved;
    }

    private void audit(String actorType, String actorId, String action, Order order, String outcome,
                       String detail) {
        auditLog.record(actorType, actorId, action, TARGET, order.id().toString(), outcome, detail, null);
    }
}

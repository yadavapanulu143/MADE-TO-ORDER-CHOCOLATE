package com.choco.order.domain.order;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.core.error.ForbiddenException;
import com.choco.order.domain.event.OrderCancelled;
import com.choco.order.domain.event.OrderConfirmed;
import com.choco.order.domain.event.OrderPlaced;
import com.choco.order.domain.event.OrderStatusChanged;
import com.choco.order.domain.event.RefundRequested;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A customer's order: the priced lines, the delivery address and contact details frozen at purchase,
 * the money that has been verified, and the lifecycle the seller moves it through.
 *
 * <p>The aggregate exists to protect one invariant above all others: <strong>an order is never
 * prepared before a verified payment confirms it.</strong> Every transition runs through
 * {@link #transitionTo}, which consults {@link OrderStatus#canTransitionTo}, so there is no path from
 * {@code PAYMENT_PENDING} straight to {@code PREPARATION_STARTED} — the preparation status can only be
 * advanced once the status is {@code CONFIRMED} or later. Putting the rule in one method rather than
 * at each admin screen is what stops a future endpoint from skipping it.
 *
 * <p>Three tracks are held separately — payment, preparation and delivery — and folded into the single
 * {@link #status()} the customer reads. They are kept apart because they advance on different clocks
 * and are driven by different actors: payment by the provider's verified answer, preparation and
 * delivery by the seller. A single status field could not represent "paid, waiting to be started"
 * without losing the payment fact.
 *
 * <p>The order is a snapshot. Product names, variant labels, SKUs and unit prices are copied in from
 * the catalogue at checkout and never re-read, so a later price change cannot rewrite what a customer
 * agreed to pay, and the seller can still render and invoice an order for a product they have since
 * renamed or withdrawn.
 */
public class Order extends AggregateRoot {

    /** Ceiling on lines in one order, so a single request cannot create an unbounded row set. */
    public static final int MAX_LINES = 50;

    private final UUID id;
    private final String orderNumber;
    private final UUID customerId;
    private final CustomerSnapshot customer;
    private final DeliveryAddress deliveryAddress;
    private final List<OrderLine> lines;
    private final Money itemsTotal;
    private final Money deliveryCharge;
    private final Money total;

    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private PreparationStatus preparationStatus;
    private DeliveryStatus deliveryStatus;

    /** The payment service's identifier for the charge, once it exists. Never set from a redirect. */
    private String paymentId;

    /** The provider's order reference, as recorded by the payment service. */
    private String providerOrderId;

    /** Delivery assignment details, recorded by the seller. No courier integration is implied. */
    private String deliveryPersonName;
    private String deliveryPersonPhone;

    private Instant dispatchedAt;
    private Instant deliveredAt;

    private final Instant createdAt;
    private Instant updatedAt;

    private final List<StatusChange> statusHistory = new ArrayList<>();
    private final List<OrderNote> notes = new ArrayList<>();

    private Order(UUID id, String orderNumber, UUID customerId, CustomerSnapshot customer,
                  DeliveryAddress deliveryAddress, List<OrderLine> lines, Money deliveryCharge,
                  Instant createdAt) {
        this.id = id;
        this.orderNumber = requireOrderNumber(orderNumber);
        this.customerId = customerId;
        this.customer = customer;
        this.deliveryAddress = deliveryAddress;
        this.lines = new ArrayList<>(requireLines(lines));
        this.itemsTotal = sumLines(this.lines);
        this.deliveryCharge = requireCharge(deliveryCharge);
        this.total = this.itemsTotal.plus(this.deliveryCharge);
        this.status = OrderStatus.PAYMENT_PENDING;
        this.paymentStatus = PaymentStatus.PENDING;
        this.preparationStatus = null;
        this.deliveryStatus = DeliveryStatus.PENDING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Places an order.
     *
     * <p>The order begins in {@link OrderStatus#PAYMENT_PENDING}: it is a record of intent, not yet a
     * commitment to make chocolate. The delivery charge is passed in already resolved by the
     * application layer, which owns the call to the serviceability service; the domain records it
     * exactly as quoted so the total the customer saw is the total stored.
     */
    public static Order place(UUID id, String orderNumber, UUID customerId, CustomerSnapshot customer,
                              DeliveryAddress deliveryAddress, List<OrderLine> lines, Money deliveryCharge,
                              Instant now) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer id is required");
        }
        if (customer == null) {
            throw new IllegalArgumentException("Customer details are required");
        }
        if (deliveryAddress == null) {
            throw new IllegalArgumentException("A delivery address is required");
        }
        Order order = new Order(id, orderNumber, customerId, customer, deliveryAddress, lines,
                deliveryCharge, now);
        order.statusHistory.add(StatusChange.of(UUID.randomUUID(), null, OrderStatus.PAYMENT_PENDING,
                "Order placed", customerId.toString(), now));
        order.registerEvent(new OrderPlaced(UUID.randomUUID(), now, id.toString(), customerId.toString(),
                order.orderNumber, order.total.minorUnits(), order.total.currency().getCurrencyCode(),
                order.lines.size()));
        return order;
    }

    /** Rehydrates from persistence without re-running creation rules or re-raising events. */
    public static Order rehydrate(UUID id, String orderNumber, UUID customerId, CustomerSnapshot customer,
                                  DeliveryAddress deliveryAddress, List<OrderLine> lines, Money deliveryCharge,
                                  OrderStatus status, PaymentStatus paymentStatus,
                                  PreparationStatus preparationStatus, DeliveryStatus deliveryStatus,
                                  String paymentId, String providerOrderId, String deliveryPersonName,
                                  String deliveryPersonPhone, Instant dispatchedAt, Instant deliveredAt,
                                  Instant createdAt, Instant updatedAt) {
        Order order = new Order(id, orderNumber, customerId, customer, deliveryAddress, lines,
                deliveryCharge, createdAt);
        order.status = status;
        order.paymentStatus = paymentStatus;
        order.preparationStatus = preparationStatus;
        order.deliveryStatus = deliveryStatus;
        order.paymentId = paymentId;
        order.providerOrderId = providerOrderId;
        order.deliveryPersonName = deliveryPersonName;
        order.deliveryPersonPhone = deliveryPersonPhone;
        order.dispatchedAt = dispatchedAt;
        order.deliveredAt = deliveredAt;
        order.updatedAt = updatedAt;
        return order;
    }

    /** Reattaches persisted children after the root has been loaded. Persistence mapper only. */
    public void attachStatusHistory(List<StatusChange> loaded) {
        statusHistory.clear();
        statusHistory.addAll(loaded);
    }

    public void attachNotes(List<OrderNote> loaded) {
        notes.clear();
        notes.addAll(loaded);
    }

    // -------------------------------------------------------------------------------------------
    // Payment track
    // -------------------------------------------------------------------------------------------

    /**
     * Records that a payment attempt is in flight.
     *
     * <p>Only the payment service's own confirmation may move this; a browser redirect back from the
     * provider proves nothing, so there is deliberately no method that takes a customer-supplied
     * "success" flag.
     */
    public void markPaymentVerificationPending(String paymentId, String providerOrderId, Instant now) {
        this.paymentId = paymentId;
        this.providerOrderId = providerOrderId;
        this.paymentStatus = PaymentStatus.VERIFICATION_PENDING;
        transitionTo(OrderStatus.PAYMENT_VERIFICATION_PENDING, "Payment verification started",
                customerId.toString(), now);
    }

    /**
     * Confirms a verified payment and releases the order into preparation.
     *
     * <p>This is the only door into {@link OrderStatus#CONFIRMED}. It is idempotent: a duplicate
     * verification — a webhook redelivered after the redirect already confirmed the order — returns
     * without a second transition, so the customer is not told their order was confirmed twice and no
     * duplicate event is emitted.
     */
    public void markPaid(String paymentId, String providerOrderId, Instant now) {
        // Check for a conflicting payment before the idempotency shortcut. A duplicate confirmation of
        // the same payment is a harmless replay; a confirmation naming a *different* payment for an
        // order that is already paid is a serious inconsistency and must be refused, not swallowed.
        if (this.paymentId != null && paymentId != null && !this.paymentId.equals(paymentId)) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_ALREADY_CAPTURED,
                    "This order is already linked to a different payment");
        }
        if (this.paymentStatus == PaymentStatus.PAID) {
            return;
        }
        this.paymentId = paymentId;
        this.providerOrderId = providerOrderId;
        this.paymentStatus = PaymentStatus.PAID;
        this.preparationStatus = PreparationStatus.AWAITING_PREPARATION;
        transitionTo(OrderStatus.CONFIRMED, "Payment verified", "payment-service", now);
        registerEvent(new OrderConfirmed(UUID.randomUUID(), now, id.toString(), customerId.toString(),
                orderNumber, paymentId));
    }

    /** Records a failed or unverifiable payment. Preparation remains locked. */
    public void markPaymentFailed(String reason, Instant now) {
        if (this.paymentStatus == PaymentStatus.PAID) {
            // A late failure notification must not un-pay a confirmed order.
            return;
        }
        this.paymentStatus = PaymentStatus.FAILED;
        transitionTo(OrderStatus.PAYMENT_FAILED, reason == null ? "Payment failed" : reason,
                "payment-service", now);
    }

    /** Returns a failed order to pending so the customer can attempt payment again. */
    public void allowPaymentRetry(Instant now) {
        if (this.paymentStatus != PaymentStatus.FAILED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only a failed payment can be retried");
        }
        this.paymentStatus = PaymentStatus.PENDING;
        transitionTo(OrderStatus.PAYMENT_PENDING, "Payment retry requested", customerId.toString(), now);
    }

    // -------------------------------------------------------------------------------------------
    // Preparation track (made-to-order)
    // -------------------------------------------------------------------------------------------

    /** Begins making the chocolate. Refused unless the order is paid. */
    public void startPreparation(String actorId, Instant now) {
        requirePaid("Preparation cannot begin before the payment is verified");
        this.preparationStatus = PreparationStatus.STARTED;
        transitionTo(OrderStatus.PREPARATION_STARTED, "Preparation started", actorId, now);
    }

    /** Pauses preparation, e.g. an ingredient is unavailable. */
    public void holdPreparation(String reason, String actorId, Instant now) {
        requirePreparationInProgress("Only an order in preparation can be put on hold");
        this.preparationStatus = PreparationStatus.ON_HOLD;
        transitionTo(OrderStatus.PREPARATION_ON_HOLD, reason == null ? "Preparation on hold" : reason,
                actorId, now);
    }

    /** Resumes a held order. */
    public void resumePreparation(String actorId, Instant now) {
        if (this.status != OrderStatus.PREPARATION_ON_HOLD) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "This order is not on hold");
        }
        this.preparationStatus = PreparationStatus.STARTED;
        transitionTo(OrderStatus.PREPARATION_STARTED, "Preparation resumed", actorId, now);
    }

    /** Marks the chocolate finished. */
    public void completePreparation(String actorId, Instant now) {
        if (this.status != OrderStatus.PREPARATION_STARTED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Preparation can only be completed from a started order");
        }
        this.preparationStatus = PreparationStatus.COMPLETED;
        transitionTo(OrderStatus.PREPARATION_COMPLETED, "Preparation completed", actorId, now);
    }

    /** Moves a finished order into packaging. */
    public void startPackaging(String actorId, Instant now) {
        if (this.status != OrderStatus.PREPARATION_COMPLETED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Packaging can only begin once preparation is complete");
        }
        this.preparationStatus = PreparationStatus.PACKAGING;
        transitionTo(OrderStatus.PACKAGING, "Packaging started", actorId, now);
    }

    /** Marks the order packed and ready for the seller to dispatch. */
    public void markReadyForDispatch(String actorId, Instant now) {
        if (this.status != OrderStatus.PACKAGING) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only a packed order can be marked ready for dispatch");
        }
        this.preparationStatus = PreparationStatus.READY_FOR_DISPATCH;
        transitionTo(OrderStatus.READY_FOR_DISPATCH, "Ready for dispatch", actorId, now);
    }

    // -------------------------------------------------------------------------------------------
    // Delivery track (seller-managed)
    // -------------------------------------------------------------------------------------------

    /** Records who will deliver the order and hands it to delivery. */
    public void assignDelivery(String deliveryPersonName, String deliveryPersonPhone, String actorId,
                               Instant now) {
        requireReadyForDispatch("Delivery can only be assigned once the order is ready for dispatch");
        this.deliveryPersonName = requireDeliveryPersonName(deliveryPersonName);
        this.deliveryPersonPhone = normalisePhone(deliveryPersonPhone);
        this.deliveryStatus = DeliveryStatus.ASSIGNED;
        transitionTo(OrderStatus.OUT_FOR_DELIVERY, "Delivery assigned", actorId, now);
        this.deliveryStatus = DeliveryStatus.OUT_FOR_DELIVERY;
    }

    /** Records that the order left the seller. */
    public void markOutForDelivery(String actorId, Instant now) {
        requireReadyForDispatch("Delivery can only start once the order is ready for dispatch");
        this.deliveryStatus = DeliveryStatus.OUT_FOR_DELIVERY;
        transitionTo(OrderStatus.OUT_FOR_DELIVERY, "Out for delivery", actorId, now);
    }

    /** Records a successful handover. */
    public void markDelivered(String actorId, Instant now) {
        if (this.status != OrderStatus.OUT_FOR_DELIVERY) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only an order that is out for delivery can be delivered");
        }
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.deliveredAt = now;
        transitionTo(OrderStatus.DELIVERED, "Delivered", actorId, now);
    }

    /** Records a failed attempt and why. */
    public void recordFailedDeliveryAttempt(String reason, String actorId, Instant now) {
        if (this.status != OrderStatus.OUT_FOR_DELIVERY) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A failed attempt can only be recorded for an order that is out for delivery");
        }
        this.deliveryStatus = DeliveryStatus.ATTEMPT_FAILED;
        transitionTo(OrderStatus.DELIVERY_ATTEMPT_FAILED,
                reason == null || reason.isBlank() ? "Delivery attempt failed" : reason, actorId, now);
    }

    /** Schedules a new attempt after a failure. */
    public void rescheduleDelivery(String actorId, Instant now) {
        if (this.status != OrderStatus.DELIVERY_ATTEMPT_FAILED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only a failed delivery can be rescheduled");
        }
        this.deliveryStatus = DeliveryStatus.RESCHEDULED;
        transitionTo(OrderStatus.DELIVERY_RESCHEDULED, "Delivery rescheduled", actorId, now);
    }

    /** Records that the item came back. */
    public void markReturned(String reason, String actorId, Instant now) {
        if (this.status != OrderStatus.DELIVERY_ATTEMPT_FAILED
                && this.status != OrderStatus.DELIVERY_RESCHEDULED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only a failed or rescheduled delivery can be returned");
        }
        this.deliveryStatus = DeliveryStatus.RETURNED;
        transitionTo(OrderStatus.DELIVERY_RETURNED,
                reason == null || reason.isBlank() ? "Delivery returned" : reason, actorId, now);
    }

    // -------------------------------------------------------------------------------------------
    // Cancellation and refunds
    // -------------------------------------------------------------------------------------------

    /**
     * Cancels the order.
     *
     * <p>Refused once the order is out for delivery: at that point the chocolate exists and is with
     * the seller's delivery, so the conversation is a refund rather than a cancellation. Before that,
     * a customer may cancel their own order and the seller may cancel any order.
     */
    public void cancel(String reason, String actorId, Instant now) {
        if (this.status == OrderStatus.OUT_FOR_DELIVERY || this.status == OrderStatus.DELIVERED
                || this.status == OrderStatus.DELIVERY_ATTEMPT_FAILED
                || this.status == OrderStatus.DELIVERY_RESCHEDULED
                || this.status == OrderStatus.DELIVERY_RETURNED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "An order that has left for delivery cannot be cancelled; request a refund instead");
        }
        if (this.status.isTerminal()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "This order can no longer be cancelled");
        }
        if (this.paymentStatus == PaymentStatus.PAID) {
            // Money was taken, so the cancellation is also a refund case the seller must settle.
            this.paymentStatus = PaymentStatus.REFUND_UNDER_REVIEW;
        }
        transitionTo(OrderStatus.CANCELLED, reason == null || reason.isBlank() ? "Cancelled" : reason,
                actorId, now);
        registerEvent(new OrderCancelled(UUID.randomUUID(), now, id.toString(), customerId.toString(),
                orderNumber, reason, actorId));
    }

    /**
     * Records a customer's refund request.
     *
     * <p>A request, not a decision. The seller reviews it; the business is made-to-order, so a
     * cancellation after preparation has started has a real cost, but the request is never silently
     * dropped. The status moves to {@link OrderStatus#REFUND_UNDER_REVIEW} so the seller has a queue to
     * work, and a separate event tells the payment service.
     */
    public void requestRefund(String reason, Instant now) {
        if (this.paymentStatus != PaymentStatus.PAID && this.paymentStatus != PaymentStatus.REFUND_UNDER_REVIEW) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A refund can only be requested for a paid order");
        }
        if (this.status == OrderStatus.REFUND_UNDER_REVIEW || this.status == OrderStatus.REFUNDED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A refund is already being handled for this order");
        }
        this.paymentStatus = PaymentStatus.REFUND_UNDER_REVIEW;
        if (this.status != OrderStatus.CANCELLED) {
            transitionTo(OrderStatus.REFUND_UNDER_REVIEW,
                    reason == null || reason.isBlank() ? "Refund requested" : reason,
                    customerId.toString(), now);
        }
        registerEvent(new RefundRequested(UUID.randomUUID(), now, id.toString(), customerId.toString(),
                orderNumber, total.minorUnits(), total.currency().getCurrencyCode(), reason));
    }

    /** Records that the payment service processed a refund. Terminal. */
    public void markRefunded(Instant now) {
        if (this.paymentStatus == PaymentStatus.REFUNDED) {
            return;
        }
        this.paymentStatus = PaymentStatus.REFUNDED;
        if (this.status == OrderStatus.CANCELLED) {
            // A cancelled order that is refunded stays cancelled; the refund is a payment fact.
            this.updatedAt = now;
            return;
        }
        transitionTo(OrderStatus.REFUNDED, "Refund processed", "payment-service", now);
    }

    // -------------------------------------------------------------------------------------------
    // Notes and helpers
    // -------------------------------------------------------------------------------------------

    public OrderNote addNote(String text, String authorId, Instant now) {
        OrderNote note = new OrderNote(UUID.randomUUID(), text, authorId, now);
        notes.add(note);
        this.updatedAt = now;
        return note;
    }

    /** Guards every customer-facing operation so one customer cannot touch another's order. */
    public void assertOwnedBy(UUID callerCustomerId) {
        if (callerCustomerId == null || !customerId.equals(callerCustomerId)) {
            throw new ForbiddenException("You are not allowed to access this order");
        }
    }

    /** True once the order has been paid, so preparation may be scheduled. */
    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }

    public boolean isCancellable() {
        return !status.isTerminal() && status != OrderStatus.OUT_FOR_DELIVERY && status != OrderStatus.DELIVERED;
    }

    /**
     * The one method that moves the overall status.
     *
     * <p>It checks the transition against the table in {@link OrderStatus}, appends an immutable
     * history entry and raises an {@link OrderStatusChanged} event. Because it is the only writer of
     * {@code status}, no other method can bypass the rule that an order is not prepared before it is
     * paid.
     */
    private void transitionTo(OrderStatus next, String reason, String actorId, Instant now) {
        if (status == next) {
            return;
        }
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot move an order from " + status + " to " + next);
        }
        OrderStatus previous = status;
        this.status = next;
        this.updatedAt = now;
        statusHistory.add(StatusChange.of(UUID.randomUUID(), previous, next, reason, actorId, now));
        registerEvent(new OrderStatusChanged(UUID.randomUUID(), now, id.toString(), customerId.toString(),
                previous.name(), next.name(), reason));
    }

    private void requirePaid(String message) {
        if (paymentStatus != PaymentStatus.PAID) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION, message);
        }
    }

    private void requirePreparationInProgress(String message) {
        if (status != OrderStatus.PREPARATION_STARTED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION, message);
        }
    }

    private void requireReadyForDispatch(String message) {
        if (status != OrderStatus.READY_FOR_DISPATCH && status != OrderStatus.DELIVERY_RESCHEDULED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION, message);
        }
    }

    private static List<OrderLine> requireLines(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An order must have at least one line");
        }
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("An order may have at most " + MAX_LINES + " lines");
        }
        return lines;
    }

    private static Money sumLines(List<OrderLine> lines) {
        Money sum = Money.inr(0);
        for (OrderLine line : lines) {
            sum = sum.plus(line.lineTotal());
        }
        return sum;
    }

    private static Money requireCharge(Money charge) {
        if (charge == null) {
            throw new IllegalArgumentException("A delivery charge is required");
        }
        if (charge.isNegative()) {
            throw new IllegalArgumentException("A delivery charge cannot be negative");
        }
        return charge;
    }

    private static String requireOrderNumber(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("An order number is required");
        }
        if (trimmed.length() > 40) {
            throw new IllegalArgumentException("An order number must be at most 40 characters");
        }
        return trimmed;
    }

    private static String requireDeliveryPersonName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("Delivery person name must be at most 120 characters");
        }
        return trimmed;
    }

    private static String normalisePhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    // -------------------------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------------------------

    public UUID id() {
        return id;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public UUID customerId() {
        return customerId;
    }

    public CustomerSnapshot customer() {
        return customer;
    }

    public DeliveryAddress deliveryAddress() {
        return deliveryAddress;
    }

    public List<OrderLine> lines() {
        return List.copyOf(lines);
    }

    public Money itemsTotal() {
        return itemsTotal;
    }

    public Money deliveryCharge() {
        return deliveryCharge;
    }

    public Money total() {
        return total;
    }

    public OrderStatus status() {
        return status;
    }

    public PaymentStatus paymentStatus() {
        return paymentStatus;
    }

    public PreparationStatus preparationStatus() {
        return preparationStatus;
    }

    public DeliveryStatus deliveryStatus() {
        return deliveryStatus;
    }

    public String paymentId() {
        return paymentId;
    }

    public String providerOrderId() {
        return providerOrderId;
    }

    public String deliveryPersonName() {
        return deliveryPersonName;
    }

    public String deliveryPersonPhone() {
        return deliveryPersonPhone;
    }

    public Instant dispatchedAt() {
        return dispatchedAt;
    }

    public Instant deliveredAt() {
        return deliveredAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** Status history, oldest first, so a reader sees the order's life in the order it happened. */
    public List<StatusChange> statusHistory() {
        return statusHistory.stream().sorted(Comparator.comparing(StatusChange::occurredAt)).toList();
    }

    public List<OrderNote> notes() {
        return notes.stream().sorted(Comparator.comparing(OrderNote::createdAt)).toList();
    }
}

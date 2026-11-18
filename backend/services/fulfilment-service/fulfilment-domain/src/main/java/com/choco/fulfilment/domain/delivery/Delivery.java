package com.choco.fulfilment.domain.delivery;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.fulfilment.domain.event.DeliveryAssigned;
import com.choco.fulfilment.domain.event.DeliveryDelivered;
import com.choco.fulfilment.domain.event.DeliveryFailed;
import com.choco.fulfilment.domain.event.DeliveryRescheduled;
import com.choco.fulfilment.domain.event.OutForDelivery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The seller's delivery of one order, from ready-to-dispatch to delivered or returned.
 *
 * <p>One delivery per order, because the seller hands over one parcel. The address, the delivery
 * charge and the PIN code are all copied in at creation: the charge is the figure the customer agreed
 * to pay and must survive a later change to the seller's delivery rules, exactly as an order line's
 * price does.
 *
 * <p><b>No fabricated tracking.</b> There is no courier here, so there is no tracking number and no
 * GPS position. The progress the customer sees is exactly what the seller recorded: assigned, out for
 * delivery, delivered. Inventing intermediate positions would tell the customer something the seller
 * never said.
 *
 * <p>Proof of delivery is optional and configurable. When the seller wants it, a delivery cannot be
 * marked delivered without a confirmation reference or an uploaded image; the requirement is a
 * setting, not a hard rule, so a small seller who just marks "delivered" is not blocked.
 */
public class Delivery extends AggregateRoot {

    public static final int MAX_NOTES_LENGTH = 500;

    private final UUID id;
    private final UUID orderId;
    private final String orderNumber;
    private final UUID customerId;
    private final DeliveryAddress address;
    private final Pincode pincode;
    private final Money deliveryCharge;
    private final Instant createdAt;

    private DeliveryStatus status;
    private String deliveryPersonName;
    private String deliveryPersonPhone;
    private Instant dispatchedAt;
    private Instant deliveredAt;
    private String proofReference;
    private Instant updatedAt;

    private final List<DeliveryAttempt> attempts = new ArrayList<>();

    private Delivery(UUID id, UUID orderId, String orderNumber, UUID customerId, DeliveryAddress address,
                     Money deliveryCharge, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.address = address;
        this.pincode = address.pincode();
        this.deliveryCharge = deliveryCharge == null ? Money.inr(0) : deliveryCharge;
        this.createdAt = createdAt;
        this.status = DeliveryStatus.PENDING;
        this.updatedAt = createdAt;
    }

    /**
     * Opens a delivery once production has marked the order ready to hand over.
     *
     * <p>Starts in {@code PENDING}: the parcel is packed but nobody has been assigned. No event is
     * raised — the order service already heard {@code fulfilment.ready-for-dispatch}, and "a delivery
     * record exists" is not a fact the customer needs told.
     */
    public static Delivery open(UUID id, UUID orderId, String orderNumber, UUID customerId,
                                DeliveryAddress address, Money deliveryCharge, Instant now) {
        return new Delivery(id, orderId, orderNumber, customerId, address, deliveryCharge, now);
    }

    /** Rehydrates from persistence without re-running creation rules or re-raising events. */
    public static Delivery rehydrate(UUID id, UUID orderId, String orderNumber, UUID customerId,
                                     DeliveryAddress address, Money deliveryCharge, DeliveryStatus status,
                                     String deliveryPersonName, String deliveryPersonPhone,
                                     Instant dispatchedAt, Instant deliveredAt, String proofReference,
                                     Instant createdAt, Instant updatedAt) {
        Delivery delivery = new Delivery(id, orderId, orderNumber, customerId, address, deliveryCharge,
                createdAt);
        delivery.status = status;
        delivery.deliveryPersonName = deliveryPersonName;
        delivery.deliveryPersonPhone = deliveryPersonPhone;
        delivery.dispatchedAt = dispatchedAt;
        delivery.deliveredAt = deliveredAt;
        delivery.proofReference = proofReference;
        delivery.updatedAt = updatedAt;
        return delivery;
    }

    /** Reattaches persisted attempts after the root has been loaded. Called only by the mapper. */
    public void attachAttempts(List<DeliveryAttempt> loaded) {
        attempts.clear();
        attempts.addAll(loaded);
    }

    // -------------------------------------------------------------------------------------------
    // Delivery steps
    // -------------------------------------------------------------------------------------------

    /**
     * Records who will deliver the order.
     *
     * <p>Assignment is not dispatch: the delivery stays {@code ASSIGNED} until the seller records that
     * it actually left. Re-assigning is allowed while the parcel is still with the seller, which is
     * what happens when the first person becomes unavailable.
     *
     * <p>The first assignment — the {@code PENDING → ASSIGNED} edge — raises
     * {@link DeliveryAssigned}, because "a delivery has been arranged for your order" is a fact the
     * customer is told. A later re-assignment does not: the delivery person is the seller's own staff
     * and the customer never deals with them, so swapping one for another is a change to the seller's
     * roster, not news for the customer.
     */
    public void assign(String deliveryPersonName, String deliveryPersonPhone, String actorId, Instant now) {
        String name = requireName(deliveryPersonName);
        if (status != DeliveryStatus.PENDING && status != DeliveryStatus.ASSIGNED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Delivery responsibility can only be assigned before it goes out");
        }
        boolean firstAssignment = status == DeliveryStatus.PENDING;
        if (firstAssignment) {
            moveTo(DeliveryStatus.ASSIGNED, "Assigned to " + name, actorId, now);
        }
        this.deliveryPersonName = name;
        this.deliveryPersonPhone = normalisePhone(deliveryPersonPhone);
        this.updatedAt = now;
        if (firstAssignment) {
            registerEvent(new DeliveryAssigned(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                    orderNumber, customerId.toString(), status.name(), name));
        }
    }

    /** Records that the parcel left the seller for the customer. */
    public void markOutForDelivery(String actorId, Instant now) {
        moveTo(DeliveryStatus.OUT_FOR_DELIVERY, "Out for delivery", actorId, now);
        this.dispatchedAt = now;
        registerEvent(new OutForDelivery(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                orderNumber, customerId.toString(), status.name(), deliveryPersonName));
    }

    /**
     * Records a successful delivery.
     *
     * <p>When the seller has turned on proof of delivery, a confirmation reference is required: the
     * whole point of the setting is that "delivered" is not accepted on the seller's word alone. With
     * the setting off, the step is as simple as the seller ticking it off.
     */
    public void markDelivered(String proofReference, String notes, boolean proofRequired, String actorId,
                              Instant now) {
        String proof = normaliseProof(proofReference);
        if (proofRequired && proof == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Proof of delivery is required for this delivery");
        }
        moveTo(DeliveryStatus.DELIVERED, "Delivered", actorId, now);
        this.proofReference = proof;
        this.deliveredAt = now;
        recordAttempt(DeliveryStatus.DELIVERED, null, notes, now);
        registerEvent(new DeliveryDelivered(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                orderNumber, customerId.toString(), status.name(), proof));
    }

    /** Records a failed attempt and why. A retry may follow. */
    public void recordFailedAttempt(String reason, String notes, String actorId, Instant now) {
        moveTo(DeliveryStatus.ATTEMPT_FAILED, normaliseReason(reason), actorId, now);
        recordAttempt(DeliveryStatus.ATTEMPT_FAILED, normaliseReason(reason), notes, now);
        registerEvent(new DeliveryFailed(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                orderNumber, customerId.toString(), status.name(), normaliseReason(reason),
                attempts.size()));
    }

    /** Schedules another attempt after a failure. */
    public void reschedule(String notes, String actorId, Instant now) {
        moveTo(DeliveryStatus.RESCHEDULED, "Delivery rescheduled", actorId, now);
        recordAttempt(DeliveryStatus.RESCHEDULED, null, notes, now);
        registerEvent(new DeliveryRescheduled(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                orderNumber, customerId.toString(), status.name(), attempts.size()));
    }

    /** The item came back, e.g. the customer refused it. Terminal. */
    public void markReturned(String reason, String actorId, Instant now) {
        moveTo(DeliveryStatus.RETURNED, normaliseReason(reason), actorId, now);
        recordAttempt(DeliveryStatus.RETURNED, normaliseReason(reason), null, now);
    }

    /** Calls the delivery off, e.g. the order was cancelled before dispatch. */
    public void cancel(String reason, String actorId, Instant now) {
        moveTo(DeliveryStatus.CANCELLED, reason == null || reason.isBlank() ? "Cancelled" : reason, actorId,
                now);
    }

    /** Attaches or corrects the proof of delivery for a delivery already marked delivered. */
    public void recordProof(String proofReference, Instant now) {
        if (status != DeliveryStatus.DELIVERED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Proof can only be recorded against a delivered order");
        }
        this.proofReference = normaliseProof(proofReference);
        this.updatedAt = now;
    }

    // -------------------------------------------------------------------------------------------
    // Derived reads
    // -------------------------------------------------------------------------------------------

    /** How many attempts have been recorded, successful or not. */
    public int attemptCount() {
        return attempts.size();
    }

    public List<DeliveryAttempt> attempts() {
        return attempts.stream().sorted(Comparator.comparing(DeliveryAttempt::attemptedAt)).toList();
    }

    // -------------------------------------------------------------------------------------------

    private void recordAttempt(DeliveryStatus outcome, String failureReason, String notes, Instant now) {
        attempts.add(DeliveryAttempt.of(UUID.randomUUID(), attempts.size() + 1, outcome, failureReason,
                notes, now));
    }

    private void moveTo(DeliveryStatus next, String reason, String actorId, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A delivery cannot move from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = now;
    }

    private static String requireName(String value) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Delivery person name is required");
        }
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("Delivery person name must be at most 120 characters");
        }
        return trimmed;
    }

    private static String normalisePhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
    }

    private static String normaliseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Delivery attempt failed";
        }
        String trimmed = reason.trim();
        return trimmed.length() <= DeliveryAttempt.MAX_REASON_LENGTH
                ? trimmed
                : trimmed.substring(0, DeliveryAttempt.MAX_REASON_LENGTH);
    }

    private static String normaliseProof(String proof) {
        if (proof == null || proof.isBlank()) {
            return null;
        }
        String trimmed = proof.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public UUID customerId() {
        return customerId;
    }

    public DeliveryAddress address() {
        return address;
    }

    public Pincode pincode() {
        return pincode;
    }

    public Money deliveryCharge() {
        return deliveryCharge;
    }

    public DeliveryStatus status() {
        return status;
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

    public String proofReference() {
        return proofReference;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

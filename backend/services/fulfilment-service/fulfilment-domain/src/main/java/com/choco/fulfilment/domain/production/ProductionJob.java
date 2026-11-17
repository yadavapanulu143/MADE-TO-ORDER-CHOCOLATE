package com.choco.fulfilment.domain.production;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.Money;
import com.choco.core.domain.Quantity;
import com.choco.fulfilment.domain.delivery.DeliveryAddress;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.fulfilment.domain.event.ProductionJobCompleted;
import com.choco.fulfilment.domain.event.ProductionJobReadyForDispatch;
import com.choco.fulfilment.domain.event.ProductionJobStarted;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One made-to-order job: the seller's work to turn a paid order into a packed chocolate.
 *
 * <p>One job per order line, not per order. A customer may buy two variants with different recipes
 * and different preparation times, and a job is the unit the seller actually works on, so tying it to
 * a line keeps the status meaningful. The {@code orderId} is carried alongside so all the jobs of an
 * order can be read together.
 *
 * <p><b>Why an aggregate and not a status column on the order.</b> The order service already keeps a
 * coarse preparation projection for its own lifecycle. This job is the operational record: it carries
 * the recipe snapshot, the production notes, and — once ingredients are configured — the material
 * consumption that must be written in the same transaction as the status change. Keeping it here
 * means the fulfilment service owns the process without the order service ever writing these tables.
 *
 * <p><b>Snapshots, not references.</b> The product and variant identifiers are kept only so the job
 * can be traced back to what was bought. The variant label and quantity are copied at hand-over,
 * because the seller may rename a product or withdraw a variant and none of that may change what is
 * being made.
 */
public class ProductionJob extends AggregateRoot {

    public static final int MAX_NOTE_LENGTH = 500;

    private final UUID id;
    private final UUID orderId;
    private final String orderNumber;
    private final UUID customerId;
    private final UUID productId;
    private final UUID variantId;
    private final String variantLabel;
    private final Quantity quantity;
    private final DeliveryAddress deliveryAddress;
    private final Money deliveryCharge;
    private final Instant receivedAt;

    private ProductionStatus status;
    private String holdReason;
    private boolean ingredientsConsumed;
    private Instant updatedAt;

    private final List<ProductionNote> notes = new ArrayList<>();

    private ProductionJob(UUID id, UUID orderId, String orderNumber, UUID customerId, UUID productId,
                          UUID variantId, String variantLabel, Quantity quantity,
                          DeliveryAddress deliveryAddress, Money deliveryCharge, Instant receivedAt) {
        this.id = id;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.productId = productId;
        this.variantId = variantId;
        this.variantLabel = requireText(variantLabel, "Variant label", 80);
        this.quantity = quantity;
        this.deliveryAddress = deliveryAddress;
        this.deliveryCharge = deliveryCharge == null ? Money.inr(0) : deliveryCharge;
        this.receivedAt = receivedAt;
        this.status = ProductionStatus.RECEIVED;
        this.updatedAt = receivedAt;
    }

    /**
     * Opens a job for a confirmed order line.
     *
     * <p>Starts in {@code RECEIVED}: the order has been handed over but the seller has not yet looked
     * at it. No event is raised, because "a job now exists" is not yet something the seller or the
     * customer needs told — the seller sees it in the queue, and the order service already raised
     * {@code order.confirmed}.
     */
    public static ProductionJob open(UUID id, UUID orderId, String orderNumber, UUID customerId,
                                     UUID productId, UUID variantId, String variantLabel, Quantity quantity,
                                     DeliveryAddress deliveryAddress, Money deliveryCharge, Instant now) {
        return new ProductionJob(id, orderId, orderNumber, customerId, productId, variantId,
                variantLabel, quantity, deliveryAddress, deliveryCharge, now);
    }

    /** Rehydrates from persistence without re-running creation rules or re-raising events. */
    public static ProductionJob rehydrate(UUID id, UUID orderId, String orderNumber, UUID customerId,
                                          UUID productId, UUID variantId, String variantLabel,
                                          Quantity quantity, DeliveryAddress deliveryAddress,
                                          Money deliveryCharge, ProductionStatus status, String holdReason,
                                          boolean ingredientsConsumed, Instant receivedAt,
                                          Instant updatedAt) {
        ProductionJob job = new ProductionJob(id, orderId, orderNumber, customerId, productId, variantId,
                variantLabel, quantity, deliveryAddress, deliveryCharge, receivedAt);
        job.status = status;
        job.holdReason = holdReason;
        job.ingredientsConsumed = ingredientsConsumed;
        job.updatedAt = updatedAt;
        return job;
    }

    /**
     * Marks that the recipe's ingredients have been taken out of stock for this job.
     *
     * <p>Recorded so the consumption is not repeated if preparation is restarted after a hold, and so
     * a cancellation can put the same amounts back. Held on the job rather than inferred from the
     * ledger, because the ledger is append-only history and asking it "did this job consume" on every
     * transition would be a query for a fact the job already knows.
     */
    public void markIngredientsConsumed(Instant now) {
        this.ingredientsConsumed = true;
        this.updatedAt = now;
    }

    /** Clears the consumption flag after the stock has been returned. */
    public void markIngredientsReturned(Instant now) {
        this.ingredientsConsumed = false;
        this.updatedAt = now;
    }

    /** Reattaches persisted notes after the root has been loaded. Called only by the mapper. */
    public void attachNotes(List<ProductionNote> loaded) {
        notes.clear();
        notes.addAll(loaded);
    }

    // -------------------------------------------------------------------------------------------
    // Process steps
    // -------------------------------------------------------------------------------------------

    /** The seller acknowledges the job and queues it for preparation. */
    public void acknowledge(String actorId, Instant now) {
        moveTo(ProductionStatus.AWAITING_PREPARATION, "Job acknowledged", actorId, now);
    }

    /** Preparation begins. This is the point the customer is told the chocolate is being made. */
    public void startPreparation(String actorId, Instant now) {
        moveTo(ProductionStatus.PREPARATION_STARTED, "Preparation started", actorId, now);
        registerEvent(new ProductionJobStarted(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                orderNumber, customerId.toString(), variantLabel, quantity.value(), status.name()));
    }

    /** Preparation finished; the chocolate is made but not yet packed. */
    public void completePreparation(String actorId, Instant now) {
        moveTo(ProductionStatus.PREPARATION_COMPLETED, "Preparation completed", actorId, now);
        registerEvent(new ProductionJobCompleted(UUID.randomUUID(), now, id.toString(), orderId.toString(),
                orderNumber, customerId.toString(), variantLabel, quantity.value(), status.name()));
    }

    /**
     * Packing begins.
     *
     * <p>No event: the seller starting to pack is not a fact the customer needs told, and the packing
     * step and the "ready for dispatch" step happen close together. The customer hears once, at
     * {@link #markReadyForDispatch}.
     */
    public void startPackaging(String actorId, Instant now) {
        moveTo(ProductionStatus.PACKAGING, "Packaging started", actorId, now);
    }

    /** Packed and waiting to be handed to delivery. Terminal for the production track. */
    public void markReadyForDispatch(String actorId, Instant now) {
        moveTo(ProductionStatus.READY_FOR_DISPATCH, "Ready for dispatch", actorId, now);
        registerEvent(new ProductionJobReadyForDispatch(UUID.randomUUID(), now, id.toString(),
                orderId.toString(), orderNumber, customerId.toString(), variantLabel, quantity.value(),
                status.name()));
    }

    /**
     * Pauses the job, e.g. an ingredient ran out.
     *
     * <p>Allowed only from a state where work is actually in progress. Holding a job that is already
     * packed, or one that was never acknowledged, would be a bookkeeping no-op that hides the real
     * problem rather than recording it.
     */
    public void hold(String reason, String actorId, Instant now) {
        if (status != ProductionStatus.AWAITING_PREPARATION && status != ProductionStatus.PREPARATION_STARTED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A job can only be held while it is awaiting or in preparation");
        }
        this.holdReason = normaliseNote(reason);
        moveTo(ProductionStatus.ON_HOLD, holdReason == null ? "Preparation on hold" : holdReason, actorId,
                now);
    }

    /** Resumes a held job, returning it to preparation. */
    public void resume(String actorId, Instant now) {
        if (status != ProductionStatus.ON_HOLD) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Only a held job can be resumed");
        }
        this.holdReason = null;
        moveTo(ProductionStatus.PREPARATION_STARTED, "Preparation resumed", actorId, now);
    }

    /** Abandons the job, e.g. the order was cancelled before the chocolate was made. */
    public void cancel(String reason, String actorId, Instant now) {
        if (status.isTerminal()) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "This job can no longer be cancelled");
        }
        if (status == ProductionStatus.PREPARATION_COMPLETED || status == ProductionStatus.PACKAGING) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A job whose chocolate is already made cannot be cancelled");
        }
        moveTo(ProductionStatus.CANCELLED, reason == null || reason.isBlank() ? "Cancelled" : reason,
                actorId, now);
    }

    // -------------------------------------------------------------------------------------------
    // Notes
    // -------------------------------------------------------------------------------------------

    /** Attaches a production note for the seller's own record. */
    public ProductionNote addNote(String text, String authorId, Instant now) {
        ProductionNote note = ProductionNote.of(UUID.randomUUID(), normaliseNote(text), authorId, now);
        notes.add(note);
        this.updatedAt = now;
        return note;
    }

    // -------------------------------------------------------------------------------------------
    // Derived reads
    // -------------------------------------------------------------------------------------------

    /** The units this job still keeps in the kitchen, or zero once it is done. */
    public int unitsOccupyingCapacity() {
        return status.occupiesCapacity() ? quantity.value() : 0;
    }

    public List<ProductionNote> notes() {
        return List.copyOf(notes);
    }

    // -------------------------------------------------------------------------------------------

    private void moveTo(ProductionStatus next, String reason, String actorId, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "A production job cannot move from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = now;
    }

    private static String requireText(String value, String field, int maxLength) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String normaliseNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() <= MAX_NOTE_LENGTH ? trimmed : trimmed.substring(0, MAX_NOTE_LENGTH);
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

    public UUID productId() {
        return productId;
    }

    public UUID variantId() {
        return variantId;
    }

    public String variantLabel() {
        return variantLabel;
    }

    public Quantity quantity() {
        return quantity;
    }

    public ProductionStatus status() {
        return status;
    }

    public String holdReason() {
        return holdReason;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public boolean ingredientsConsumed() {
        return ingredientsConsumed;
    }

    /** The delivery address frozen at checkout, carried so the delivery can be opened without a callback. */
    public DeliveryAddress deliveryAddress() {
        return deliveryAddress;
    }

    /** The delivery charge the customer agreed to, carried so it survives a later rule change. */
    public Money deliveryCharge() {
        return deliveryCharge;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

package com.choco.fulfilment.domain.inventory;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.fulfilment.domain.event.LowStockDetected;
import java.time.Instant;
import java.util.UUID;

/**
 * A raw material or packaging item the seller keeps track of.
 *
 * <p><b>Optional by design.</b> The requirements are explicit that ingredient tracking may not be
 * configured at first, and that made-to-order preparation must work without it. So an ingredient is a
 * separate aggregate the seller opts into: a job's preparation status advances whether or not any
 * ingredient exists, and consumption is only attempted when a recipe has been configured for the
 * variant. Nothing here is required for the core made-to-order flow.
 *
 * <p>The current level is the sum of the ledger, but it is also kept here so a low-stock check and a
 * consumption are one atomic update rather than a scan of the ledger. The ledger rows remain the
 * audit trail of how the level got where it is.
 */
public class Ingredient extends AggregateRoot {

    public static final int MAX_NAME_LENGTH = 120;

    private final UUID id;
    private final String name;
    private final UnitOfMeasure unit;
    private final Instant createdAt;

    private StockLevel onHand;
    private StockLevel minimumLevel;
    private boolean active;
    private Instant updatedAt;

    private Ingredient(UUID id, String name, UnitOfMeasure unit, StockLevel onHand,
                       StockLevel minimumLevel, boolean active, Instant createdAt) {
        this.id = id;
        this.name = requireName(name);
        this.unit = unit == null ? UnitOfMeasure.GRAM : unit;
        this.onHand = onHand == null ? StockLevel.zero() : onHand;
        this.minimumLevel = minimumLevel == null ? StockLevel.zero() : minimumLevel;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Adds a new ingredient to the seller's tracked list.
     *
     * <p>The minimum level is what triggers a low-stock alert. It defaults to zero, which means "never
     * alert", so a seller who has not thought about a threshold is not pestered with alerts for an
     * item they are not yet tracking properly.
     */
    public static Ingredient create(UUID id, String name, UnitOfMeasure unit, StockLevel openingLevel,
                                    StockLevel minimumLevel, Instant now) {
        return new Ingredient(id, name, unit, openingLevel, minimumLevel, true, now);
    }

    /** Rehydrates from persistence without re-running creation rules or re-raising events. */
    public static Ingredient rehydrate(UUID id, String name, UnitOfMeasure unit, StockLevel onHand,
                                       StockLevel minimumLevel, boolean active, Instant createdAt,
                                       Instant updatedAt) {
        Ingredient ingredient = new Ingredient(id, name, unit, onHand, minimumLevel, active, createdAt);
        ingredient.updatedAt = updatedAt;
        return ingredient;
    }

    // -------------------------------------------------------------------------------------------
    // Stock movements
    // -------------------------------------------------------------------------------------------

    /**
     * Records a delivery of stock or a manual correction upwards.
     *
     * <p>Returns whether this movement crossed the minimum level on the way up, so the caller can
     * clear a standing low-stock alert rather than leave it stale.
     */
    public void receive(StockLevel amount, String reason, Instant now) {
        requirePositive(amount);
        this.onHand = onHand.plus(amount);
        this.updatedAt = now;
    }

    /**
     * Adjusts the level by an arbitrary signed amount, e.g. a stock-take correction.
     *
     * <p>Refuses to take the level below zero. A negative level is not a real state — it means either
     * the stock was miscounted or consumption was recorded twice — and silently allowing it would hide
     * the problem while making every later alert wrong.
     */
    public void adjust(StockLevel delta, String reason, Instant now) {
        if (delta == null || delta.isZero()) {
            throw new IllegalArgumentException("A stock adjustment must be non-zero");
        }
        StockLevel next = onHand.plus(delta);
        if (next.isNegative()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "A stock adjustment cannot take the level below zero");
        }
        this.onHand = next;
        this.updatedAt = now;
    }

    /**
     * Consumes stock for a production job.
     *
     * <p>Raises a low-stock event when the level drops to or below the minimum, so the seller is told
     * to reorder before the next order arrives. The event is raised on every consumption that leaves
     * the level low rather than only on the first, because the seller's action is to reorder a
     * specific quantity and the latest shortfall is the useful figure.
     */
    public void consume(StockLevel amount, Instant now) {
        requirePositive(amount);
        StockLevel next = onHand.minus(amount);
        if (next.isNegative()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Not enough " + name + " in stock: " + onHand + " " + unit.symbol()
                            + " available, " + amount + " " + unit.symbol() + " required");
        }
        this.onHand = next;
        this.updatedAt = now;
        if (isBelowMinimum()) {
            registerEvent(new LowStockDetected(UUID.randomUUID(), now, id.toString(), name,
                    unit.symbol(), next.milliUnits(), minimumLevel.milliUnits()));
        }
    }

    /** Changes the threshold at which a low-stock alert is raised. */
    public void setMinimumLevel(StockLevel minimum, Instant now) {
        if (minimum == null || minimum.isNegative()) {
            throw new IllegalArgumentException("A minimum level cannot be negative");
        }
        this.minimumLevel = minimum;
        this.updatedAt = now;
    }

    /** Retires an ingredient from active tracking without deleting its history. */
    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        this.active = true;
        this.updatedAt = now;
    }

    // -------------------------------------------------------------------------------------------
    // Derived reads
    // -------------------------------------------------------------------------------------------

    /** True when the level has fallen to or below the configured minimum. */
    public boolean isBelowMinimum() {
        return minimumLevel.isPositive() && onHand.compareTo(minimumLevel) <= 0;
    }

    private static void requirePositive(StockLevel amount) {
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("A stock movement must be a positive amount");
        }
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Ingredient name is required");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Ingredient name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UnitOfMeasure unit() {
        return unit;
    }

    public StockLevel onHand() {
        return onHand;
    }

    public StockLevel minimumLevel() {
        return minimumLevel;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

package com.choco.fulfilment.domain.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A measured amount of an ingredient, held as a whole number of thousandths of its unit.
 *
 * <p>The same reasoning as money: a recipe that says "0.1 g of essence" and a stock that subtracts it
 * a hundred times must not drift, and {@code double} cannot represent 0.1 exactly. Thousandths give
 * three decimal places, which is finer than any kitchen scale and enough for grams, millilitres and
 * pieces alike.
 *
 * <p>Signed, because the stock ledger records both increases (a delivery, a correction) and decreases
 * (consumption). An ingredient's own level is never negative — that is enforced where the level is
 * held — but a ledger entry legitimately is.
 */
public final class StockLevel implements Comparable<StockLevel> {

    private static final int SCALE = 3;
    private static final long THOUSAND = 1_000L;

    private final long milliUnits;

    private StockLevel(long milliUnits) {
        this.milliUnits = milliUnits;
    }

    public static StockLevel ofMilliUnits(long milliUnits) {
        return new StockLevel(milliUnits);
    }

    public static StockLevel of(BigDecimal units) {
        if (units == null) {
            throw new IllegalArgumentException("A stock amount is required");
        }
        return new StockLevel(units.setScale(SCALE, RoundingMode.HALF_UP)
                .movePointRight(SCALE)
                .longValueExact());
    }

    public static StockLevel ofUnits(long units) {
        return new StockLevel(Math.multiplyExact(units, THOUSAND));
    }

    public static StockLevel zero() {
        return new StockLevel(0L);
    }

    public long milliUnits() {
        return milliUnits;
    }

    public BigDecimal toUnits() {
        return BigDecimal.valueOf(milliUnits, SCALE);
    }

    public StockLevel plus(StockLevel other) {
        return new StockLevel(Math.addExact(milliUnits, other.milliUnits));
    }

    public StockLevel minus(StockLevel other) {
        return new StockLevel(Math.subtractExact(milliUnits, other.milliUnits));
    }

    /** Multiplies by a whole unit count, for scaling a per-unit recipe to an order quantity. */
    public StockLevel multiply(int factor) {
        return new StockLevel(Math.multiplyExact(milliUnits, factor));
    }

    /** The same magnitude with the opposite sign, for a ledger entry that removes stock. */
    public StockLevel negate() {
        return new StockLevel(Math.negateExact(milliUnits));
    }

    public boolean isZero() {
        return milliUnits == 0L;
    }

    public boolean isNegative() {
        return milliUnits < 0L;
    }

    public boolean isPositive() {
        return milliUnits > 0L;
    }

    public boolean isLessThan(StockLevel other) {
        return milliUnits < other.milliUnits;
    }

    @Override
    public int compareTo(StockLevel other) {
        return Long.compare(milliUnits, other.milliUnits);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StockLevel other && milliUnits == other.milliUnits;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(milliUnits);
    }

    @Override
    public String toString() {
        return toUnits().stripTrailingZeros().toPlainString();
    }
}

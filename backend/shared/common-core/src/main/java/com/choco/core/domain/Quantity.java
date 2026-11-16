package com.choco.core.domain;

/**
 * Positive whole-unit count of a product. The upper bound is a domain invariant because the
 * seller is one person with finite daily preparation capacity.
 */
public record Quantity(int value) {

    public static final int MAX_PER_ITEM = 1_000;

    public Quantity {
        if (value < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1, was " + value);
        }
        if (value > MAX_PER_ITEM) {
            throw new IllegalArgumentException("Quantity must not exceed " + MAX_PER_ITEM + ", was " + value);
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public Quantity plus(Quantity other) {
        return new Quantity(Math.addExact(value, other.value));
    }

    public boolean isGreaterThan(Quantity other) {
        return value > other.value;
    }
}

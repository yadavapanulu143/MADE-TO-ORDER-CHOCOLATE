package com.choco.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Monetary amount stored as a whole number of minor units (paise for INR).
 *
 * <p>Floating point is never used for money: {@code double} cannot represent 0.1 exactly and
 * repeated addition of prices drifts. Integer minor units make order totals exact and let the
 * database column be {@code BIGINT}, so no rounding happens in storage.
 */
public final class Money implements Comparable<Money> {

    public static final Currency INR = Currency.getInstance("INR");
    private static final int SCALE = 2;

    private final long minorUnits;
    private final Currency currency;

    private Money(long minorUnits, Currency currency) {
        this.minorUnits = minorUnits;
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public static Money ofMinorUnits(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money inr(long paise) {
        return new Money(paise, INR);
    }

    public static Money inrRupees(String rupees) {
        return inr(fromMajorUnits(new BigDecimal(rupees), INR));
    }

    public static Money of(BigDecimal majorUnits, Currency currency) {
        return new Money(fromMajorUnits(majorUnits, currency), currency);
    }

    private static long fromMajorUnits(BigDecimal majorUnits, Currency currency) {
        Objects.requireNonNull(majorUnits, "majorUnits");
        int fractionDigits = currency.getDefaultFractionDigits();
        return majorUnits.setScale(fractionDigits, RoundingMode.HALF_UP)
                .movePointRight(fractionDigits)
                .longValueExact();
    }

    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    public long minorUnits() {
        return minorUnits;
    }

    public Currency currency() {
        return currency;
    }

    public BigDecimal toMajorUnits() {
        return BigDecimal.valueOf(minorUnits, currency.getDefaultFractionDigits());
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(minorUnits, sameCurrency(other).minorUnits), currency);
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(minorUnits, sameCurrency(other).minorUnits), currency);
    }

    public Money multiply(int quantity) {
        return new Money(Math.multiplyExact(minorUnits, quantity), currency);
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isNegative() {
        return minorUnits < 0L;
    }

    public boolean isGreaterThan(Money other) {
        return minorUnits > sameCurrency(other).minorUnits;
    }

    private Money sameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
        return other;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(minorUnits, sameCurrency(other).minorUnits);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Money other && minorUnits == other.minorUnits && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minorUnits, currency);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + toMajorUnits().toPlainString();
    }
}

package com.choco.payment.domain.payment;

import java.util.Locale;

/**
 * The method a customer paid with, as far as the provider tells us.
 *
 * <p>Deliberately coarse. The provider reports a method name such as {@code upi}, {@code card} or
 * {@code netbanking}; this service stores that classification and nothing finer. It never stores a
 * card number, an expiry, a CVV, a UPI handle or a bank account — none of those are needed to
 * reconcile a payment, and holding them would turn a routine database into a cardholder-data store.
 */
public enum PaymentMethod {

    UPI,
    CARD,
    NETBANKING,
    WALLET,
    EMI,
    PAY_LATER,
    UNKNOWN;

    /** Maps a provider method string onto this classification, defaulting to {@link #UNKNOWN}. */
    public static PaymentMethod fromProvider(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalised) {
            case "UPI" -> UPI;
            case "CARD", "CREDIT_CARD", "DEBIT_CARD" -> CARD;
            case "NETBANKING" -> NETBANKING;
            case "WALLET" -> WALLET;
            case "EMI" -> EMI;
            case "PAYLATER", "PAY_LATER" -> PAY_LATER;
            default -> UNKNOWN;
        };
    }
}

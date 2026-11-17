package com.choco.notification.application.template;

import com.choco.core.domain.Money;
import java.util.Currency;

/**
 * Formats a monetary amount for a message body.
 *
 * <p>Money is carried on the wire as an integer count of minor units, which is the right way to move it
 * and the wrong way to show it. A customer reading "49900" has to work out that it means ₹499.00, so
 * every amount is formatted here, once, rather than by each template.
 *
 * <p>Only INR is supported because the business is India-only, and a currency the platform does not
 * operate in is refused rather than printed with a guessed symbol.
 */
public final class Amounts {

    private Amounts() {
    }

    /**
     * Formats minor units as a rupee amount, e.g. {@code 49900} to {@code ₹499.00}.
     *
     * <p>A null currency is treated as INR, because an event from an older producer may omit it and the
     * platform's only currency is INR; anything else is refused, so a foreign amount is never shown with
     * a rupee sign.
     */
    public static String format(long minorUnits, String currency) {
        Currency resolved = currency == null || currency.isBlank()
                ? Money.INR
                : Currency.getInstance(currency);
        if (!Money.INR.equals(resolved)) {
            throw new IllegalArgumentException("Unsupported currency for display: " + currency);
        }
        return "₹" + Money.ofMinorUnits(minorUnits, resolved).toMajorUnits().toPlainString();
    }
}

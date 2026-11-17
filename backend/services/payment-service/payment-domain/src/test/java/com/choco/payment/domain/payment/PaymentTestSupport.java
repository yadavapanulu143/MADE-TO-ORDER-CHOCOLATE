package com.choco.payment.domain.payment;

import com.choco.core.domain.Money;
import java.time.Instant;
import java.util.UUID;

/** Shared fixtures for the payment aggregate tests. */
final class PaymentTestSupport {

    static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");
    static final Instant LATER = T0.plusSeconds(600);
    static final Money PRICE = Money.inr(49900);

    private PaymentTestSupport() {
    }

    static Payment openPayment() {
        return Payment.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PRICE,
                "rcpt_1", "razorpay", "order_provider_1", T0);
    }

    static Payment paidPayment() {
        Payment payment = openPayment();
        payment.markCaptured("pay_1", PaymentMethod.UPI, PRICE, LATER);
        payment.pullDomainEvents();
        return payment;
    }
}

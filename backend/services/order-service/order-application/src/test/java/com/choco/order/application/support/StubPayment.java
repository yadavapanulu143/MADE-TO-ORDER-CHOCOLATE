package com.choco.order.application.support;

import com.choco.core.domain.Money;
import com.choco.order.application.port.PaymentPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stand-in payment service for application tests.
 *
 * <p>Records the exact arguments the use case sent — the amount in particular — so a test can assert
 * that the order's own frozen total was the figure asked for, and can return a fixed initiation the
 * way the real service would. It can also be told to fail, to stand in for the payment service being
 * unreachable.
 */
public class StubPayment implements PaymentPort {

    public record Call(UUID orderId, Money amount, String receipt, String idempotencyKey) {
    }

    private final List<Call> calls = new ArrayList<>();
    private RuntimeException failure;
    private long providerOrderCounter = 0;

    /** Makes every call fail, as the payment service being down would. */
    public StubPayment failingWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public Initiation initiate(UUID orderId, Money amount, String receipt, String idempotencyKey) {
        calls.add(new Call(orderId, amount, receipt, idempotencyKey));
        if (failure != null) {
            throw failure;
        }
        providerOrderCounter++;
        return new Initiation("razorpay", "order_stub_" + providerOrderCounter, amount.minorUnits(),
                amount.toMajorUnits().toPlainString(), amount.currency().getCurrencyCode(), receipt,
                "rzp_test_key");
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public Call lastCall() {
        return calls.isEmpty() ? null : calls.getLast();
    }
}

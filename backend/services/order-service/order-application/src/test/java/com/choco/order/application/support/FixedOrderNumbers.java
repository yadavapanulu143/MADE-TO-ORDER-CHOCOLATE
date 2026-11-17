package com.choco.order.application.support;

import com.choco.order.application.port.OrderNumberGenerator;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic order numbers so a test can assert on the exact value the customer is shown. */
public class FixedOrderNumbers implements OrderNumberGenerator {

    private final AtomicInteger counter = new AtomicInteger(1);

    @Override
    public String next(Instant now) {
        return "CHOCO-" + String.format("%05d", counter.getAndIncrement());
    }
}

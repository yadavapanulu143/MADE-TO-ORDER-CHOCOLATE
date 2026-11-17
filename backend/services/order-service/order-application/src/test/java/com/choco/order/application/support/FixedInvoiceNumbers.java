package com.choco.order.application.support;

import com.choco.order.application.port.InvoiceNumberGenerator;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic invoice numbers so a test can assert on the exact value printed on the document. */
public class FixedInvoiceNumbers implements InvoiceNumberGenerator {

    private final AtomicInteger counter = new AtomicInteger(1);

    @Override
    public String next(Instant now) {
        return "INV-" + String.format("%05d", counter.getAndIncrement());
    }
}

package com.choco.fulfilment.application.dto;

/** The delivery board at a glance: how many deliveries sit in each stage. */
public record DeliveryCounters(
        long pending,
        long assigned,
        long outForDelivery,
        long delivered,
        long attemptFailed,
        long rescheduled,
        long cancelled,
        long returned,
        long total) {
}

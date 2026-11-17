package com.choco.payment.application.dto;

/** The payment counters the seller dashboard shows. */
public record PaymentCountersView(
        long total,
        long paid,
        long pending,
        long failed,
        long refunded,
        long disputed) {
}

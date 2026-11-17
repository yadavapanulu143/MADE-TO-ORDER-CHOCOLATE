package com.choco.order.application.command;

import java.util.UUID;

/**
 * A request to start a payment for an order.
 *
 * <p>Carries an idempotency key for the same reason as checkout: the payment provider call is exactly
 * the operation a client retries after a timeout, and it must not create two provider orders.
 */
public record InitiatePaymentCommand(UUID orderId, String idempotencyKey) {
}

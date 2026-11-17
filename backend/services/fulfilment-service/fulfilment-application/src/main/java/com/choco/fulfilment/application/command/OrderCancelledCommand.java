package com.choco.fulfilment.application.command;

/**
 * A paid order was cancelled, derived from the order service's {@code order.cancelled} event.
 *
 * <p>Lets the kitchen abandon the jobs and return any ingredients already taken, instead of making
 * chocolate for an order that no longer exists.
 */
public record OrderCancelledCommand(String orderId, String reason) {
}

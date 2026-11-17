package com.choco.order.application.command;

import java.util.UUID;

/**
 * A request to turn the customer's cart into an order.
 *
 * <p>Only the address id is taken from the caller. The price, the product names and the delivery
 * charge are all resolved server-side from the catalogue and the serviceability settings — nothing the
 * client sends can influence what it is charged.
 *
 * <p>The idempotency key is supplied by the client and reused across retries of the same logical
 * checkout, so a timeout followed by a resend does not create a second order.
 */
public record CheckoutCommand(UUID addressId, String idempotencyKey) {
}

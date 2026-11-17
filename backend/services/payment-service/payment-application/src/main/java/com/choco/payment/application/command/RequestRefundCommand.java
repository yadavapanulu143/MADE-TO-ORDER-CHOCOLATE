package com.choco.payment.application.command;

import java.util.UUID;

/**
 * A seller-initiated refund against a captured payment.
 *
 * <p>Distinct from the customer's refund <em>request</em>, which the order service records and the
 * seller reviews. This command is the seller's decision, and it is what actually returns money.
 */
public record RequestRefundCommand(
        UUID paymentId,
        long amountMinorUnits,
        String reason,
        String actorId) {
}

package com.choco.fulfilment.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A delivery as shown to the seller and, trimmed, to the customer.
 *
 * <p>There is no tracking number and no position: delivery is seller-managed, so the progress shown is
 * exactly what the seller recorded. Inventing intermediate positions would tell the customer something
 * nobody said.
 */
public record DeliveryView(
        UUID id,
        UUID orderId,
        String orderNumber,
        UUID customerId,
        DeliveryAddressView address,
        String pincode,
        long deliveryChargeMinorUnits,
        String deliveryCharge,
        String currency,
        String status,
        String deliveryPersonName,
        String deliveryPersonPhone,
        Instant dispatchedAt,
        Instant deliveredAt,
        String proofReference,
        int attemptCount,
        Instant createdAt,
        Instant updatedAt,
        List<DeliveryAttemptView> attempts) {
}

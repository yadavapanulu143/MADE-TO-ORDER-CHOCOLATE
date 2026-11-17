package com.choco.fulfilment.application.dto;

import java.time.Instant;
import java.util.UUID;

/** A delivery area the seller covers, and its charge. */
public record ServiceableAreaView(
        UUID id,
        String pincodePrefix,
        String areaName,
        long deliveryChargeMinorUnits,
        String deliveryCharge,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}

package com.choco.identity.application.dto;

import java.util.UUID;

public record AddressView(
        UUID id,
        String recipientName,
        String mobile,
        String line1,
        String line2,
        String city,
        String state,
        String pincode,
        String deliveryInstructions,
        boolean isDefault) {
}

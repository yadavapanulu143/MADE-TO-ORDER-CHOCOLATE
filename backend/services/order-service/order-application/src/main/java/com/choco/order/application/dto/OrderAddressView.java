package com.choco.order.application.dto;

public record OrderAddressView(
        String recipientName,
        String mobile,
        String line1,
        String line2,
        String city,
        String state,
        String pincode,
        String deliveryInstructions,
        String formatted) {
}

package com.choco.fulfilment.application.dto;

/** The delivery address, with a single-line rendering for the delivery sheet and invoices. */
public record DeliveryAddressView(
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

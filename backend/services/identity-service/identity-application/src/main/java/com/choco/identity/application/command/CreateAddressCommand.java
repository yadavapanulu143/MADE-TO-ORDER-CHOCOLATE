package com.choco.identity.application.command;

/**
 * Creates a delivery address for a customer.
 *
 * <p>{@code customerId} is not part of this record on purpose. It is supplied separately by the
 * controller from the authenticated token, so a request body can never choose whose address is
 * being written.
 */
public record CreateAddressCommand(
        String recipientName,
        String mobile,
        String line1,
        String line2,
        String city,
        String state,
        String pincode,
        String deliveryInstructions,
        boolean makeDefault) {
}

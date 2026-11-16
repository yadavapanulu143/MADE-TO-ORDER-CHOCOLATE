package com.choco.identity.application.command;

/** Edits an existing address. Ownership is verified against the authenticated caller. */
public record UpdateAddressCommand(
        String recipientName,
        String mobile,
        String line1,
        String line2,
        String city,
        String state,
        String pincode,
        String deliveryInstructions) {
}

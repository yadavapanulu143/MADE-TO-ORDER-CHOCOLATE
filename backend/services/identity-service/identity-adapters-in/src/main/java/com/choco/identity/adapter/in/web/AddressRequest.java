package com.choco.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Address payload for create and update.
 *
 * <p>Bean Validation rejects a malformed request before any business logic runs. The PIN code is
 * checked for shape only; whether the seller delivers to it is a separate, later check.
 */
public record AddressRequest(
        @NotBlank(message = "Recipient name is required")
        @Size(min = 2, max = 120, message = "Recipient name must be between 2 and 120 characters")
        String recipientName,

        @NotBlank(message = "Recipient mobile number is required")
        @Pattern(regexp = "^(\\+91|91|0)?[6-9][0-9]{9}$",
                message = "Enter a valid 10-digit Indian mobile number")
        String mobile,

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 200, message = "Address line 1 must be at most 200 characters")
        String line1,

        @Size(max = 200, message = "Address line 2 must be at most 200 characters")
        String line2,

        @NotBlank(message = "City is required")
        @Size(max = 200, message = "City must be at most 200 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 200, message = "State must be at most 200 characters")
        String state,

        @NotBlank(message = "PIN code is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid 6-digit PIN code")
        String pincode,

        @Size(max = 500, message = "Delivery instructions must be at most 500 characters")
        String deliveryInstructions,

        boolean makeDefault) {
}

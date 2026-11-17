package com.choco.catalog.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for replacing the website settings.
 *
 * <p>All fields are optional: the seller fills the settings in gradually, and requiring every policy
 * before anything can be saved would leave the site unconfigurable until every page is written.
 * Lengths are bounded to match the domain so an oversized policy is a clear error, not truncation.
 */
public record SettingsRequest(
        @Size(max = 150, message = "Seller name must be at most 150 characters")
        String sellerName,

        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Contact email must be at most 254 characters")
        String contactEmail,

        @Pattern(regexp = "^(\\+91|91|0)?[6-9][0-9]{9}$", message = "Enter a valid Indian mobile number")
        String contactPhone,

        @Size(max = 2000, message = "Contact address must be at most 2000 characters")
        String contactAddress,

        @Size(max = 20000, message = "Privacy policy must be at most 20000 characters")
        String privacyPolicy,

        @Size(max = 20000, message = "Terms and conditions must be at most 20000 characters")
        String termsAndConditions,

        @Size(max = 20000, message = "Shipping policy must be at most 20000 characters")
        String shippingPolicy,

        @Size(max = 20000, message = "Refund policy must be at most 20000 characters")
        String refundPolicy) {
}

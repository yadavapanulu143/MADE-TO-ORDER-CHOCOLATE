package com.choco.catalog.application.command;

/** Input for replacing the website settings. */
public record UpdateSettingsCommand(
        String sellerName,
        String contactEmail,
        String contactPhone,
        String contactAddress,
        String privacyPolicy,
        String termsAndConditions,
        String shippingPolicy,
        String refundPolicy) {
}

package com.choco.catalog.application.dto;

import java.time.Instant;

/**
 * The website settings as presented to a caller.
 *
 * <p>One shape for both the storefront and the admin dashboard. The content is public by nature — the
 * seller publishes it deliberately — so there is no field to withhold.
 */
public record SettingsView(
        String sellerName,
        String contactEmail,
        String contactPhone,
        String contactAddress,
        String privacyPolicy,
        String termsAndConditions,
        String shippingPolicy,
        String refundPolicy,
        Instant updatedAt) {
}

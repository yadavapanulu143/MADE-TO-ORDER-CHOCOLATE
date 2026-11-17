package com.choco.catalog.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A complete product as presented to a caller.
 *
 * <p>The same shape serves the storefront and the admin dashboard, but the caller decides which to
 * request: the storefront endpoint only ever returns published products, so a draft never appears
 * there even though this view could represent one.
 */
public record ProductView(
        UUID id,
        String slug,
        String name,
        String shortDescription,
        String description,
        String preparationInfo,
        String deliveryInfo,
        String status,
        long fromPriceMinorUnits,
        String fromPrice,
        String currency,
        boolean available,
        List<VariantView> variants,
        List<ImageView> images,
        List<AttributeView> attributes,
        Instant createdAt,
        Instant updatedAt) {
}

package com.choco.catalog.application.dto;

import java.util.UUID;

/**
 * A product as it appears in a list.
 *
 * <p>Deliberately smaller than the full {@link ProductView}: a catalogue listing does not need the
 * description, the attributes or every image, and returning them for each of twenty products would
 * make the list heavier than the detail page. The from-price is derived from the active variants so a
 * list can show "from ₹X" without loading them all.
 */
public record ProductSummaryView(
        UUID id,
        String slug,
        String name,
        String shortDescription,
        long fromPriceMinorUnits,
        String fromPrice,
        String currency,
        String primaryImageUrl,
        int variantCount,
        String status,
        boolean available) {
}

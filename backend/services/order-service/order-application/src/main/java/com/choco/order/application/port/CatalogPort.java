package com.choco.order.application.port;

import com.choco.core.domain.Money;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read access to the catalogue.
 *
 * <p>The order service does not own products, so it asks the catalogue for the facts it needs to price
 * and validate an order, and copies them into the order snapshot. Nothing here writes to the
 * catalogue: an order never changes a product.
 *
 * <p>The port returns plain values, not the catalogue's own view types, so the order service is not
 * coupled to another service's API shape and can be tested without standing that service up.
 */
public interface CatalogPort {

    /** A purchasable variant, as the catalogue currently describes it. */
    record VariantSnapshot(
            UUID productId,
            UUID variantId,
            String productName,
            String variantLabel,
            String sku,
            Money unitPrice,
            int minOrderQuantity,
            int maxOrderQuantity,
            boolean active,
            boolean productAvailable) {

        public boolean purchasable() {
            return active && productAvailable;
        }
    }

    /**
     * Resolves the given variant ids in one call.
     *
     * <p>Returns a map so a caller can tell "not found" from "found but unavailable": a missing key is
     * an unknown variant, whereas a present key with {@code purchasable() == false} is a product the
     * seller has withdrawn. The two need different messages.
     */
    Map<UUID, VariantSnapshot> findVariants(Collection<UUID> variantIds);

    /** Convenience for a single variant. */
    default java.util.Optional<VariantSnapshot> findVariant(UUID variantId) {
        return java.util.Optional.ofNullable(findVariants(List.of(variantId)).get(variantId));
    }
}

package com.choco.catalog.domain.event;

import com.choco.core.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a variant's price changes.
 *
 * <p>Carries the variant id and the new price so a consumer (the order service quoting a basket) can
 * react without re-reading the catalogue. Prices already captured on a placed order are unaffected:
 * an order stores its own snapshot.
 */
public record VariantPriceChanged(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        String variantId,
        long priceMinorUnits,
        String currency) implements DomainEvent {

    @Override
    public String eventType() {
        return "catalog.variant-price-changed";
    }
}

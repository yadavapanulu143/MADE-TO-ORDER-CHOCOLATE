package com.choco.order.adapter.out.client;

import com.choco.core.domain.Money;
import com.choco.order.application.port.CatalogPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the catalogue over HTTP to price and validate an order.
 *
 * <p>The catalogue owns products and prices; the order service only ever reads them and copies the
 * answer into the order snapshot. The call is a single batch of variant ids, so a cart of many lines
 * costs one round trip rather than one per line.
 *
 * <p>A variant id the catalogue does not return is simply absent from the map, which the checkout
 * reads as "unknown or unavailable". That is the same treatment a withdrawn product gets, and it is
 * the honest answer: the catalogue is the authority on what may be bought, and it decided not to
 * offer this one.
 */
@Component
class CatalogClient implements CatalogPort {

    private final RestClient client;

    CatalogClient(DownstreamProperties properties, DownstreamTokenProvider tokens) {
        this.client = DownstreamClients.build(properties.getCatalog(), properties.getConnectTimeout(),
                properties.getReadTimeout(), tokens);
    }

    @Override
    public Map<UUID, VariantSnapshot> findVariants(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Map.of();
        }
        String ids = String.join(",", variantIds.stream().map(UUID::toString).toList());
        // The keys arrive as JSON object keys (strings), so they are read as strings and parsed here
        // rather than relying on Jackson's UUID key deserializer being registered.
        Map<String, VariantLookupResponse> body = DownstreamClients.call("catalogue", () ->
                client.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/catalog/variants")
                                .queryParam("ids", ids)
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        }));

        Map<UUID, VariantSnapshot> resolved = new HashMap<>();
        if (body == null) {
            return resolved;
        }
        for (Map.Entry<String, VariantLookupResponse> entry : body.entrySet()) {
            VariantLookupResponse view = entry.getValue();
            resolved.put(UUID.fromString(entry.getKey()), new VariantSnapshot(
                    view.productId(),
                    view.variantId(),
                    view.productName(),
                    view.variantLabel(),
                    view.sku(),
                    Money.ofMinorUnits(view.priceMinorUnits(),
                            java.util.Currency.getInstance(view.currency())),
                    view.minOrderQuantity(),
                    view.maxOrderQuantity(),
                    view.variantActive(),
                    view.productAvailable()));
        }
        return resolved;
    }

    /**
     * The wire shape of one variant lookup.
     *
     * <p>A record local to this adapter, not the catalogue's own class: the order service is coupled
     * to the catalogue's JSON, which is a published contract, not to its Java types, which are not.
     */
    record VariantLookupResponse(
            UUID variantId,
            UUID productId,
            String productName,
            String variantLabel,
            String sku,
            long priceMinorUnits,
            String currency,
            int minOrderQuantity,
            int maxOrderQuantity,
            boolean variantActive,
            boolean productAvailable) {
    }
}

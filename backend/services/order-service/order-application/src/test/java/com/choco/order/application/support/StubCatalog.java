package com.choco.order.application.support;

import com.choco.core.domain.Money;
import com.choco.order.application.port.CatalogPort;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Catalogue stand-in. Variants are registered by the test, so tests read as setup then act. */
public class StubCatalog implements CatalogPort {

    private final Map<UUID, VariantSnapshot> variants = new HashMap<>();

    public StubCatalog with(UUID productId, UUID variantId, String name, String label, String sku,
                            Money price, int min, int max) {
        variants.put(variantId, new VariantSnapshot(productId, variantId, name, label, sku, price, min,
                max, true, true));
        return this;
    }

    public StubCatalog withUnavailable(UUID productId, UUID variantId, String name, String label,
                                       Money price) {
        variants.put(variantId, new VariantSnapshot(productId, variantId, name, label, "SKU-X", price, 1,
                100, false, true));
        return this;
    }

    public StubCatalog withWithdrawnProduct(UUID productId, UUID variantId, String name, String label,
                                            Money price) {
        variants.put(variantId, new VariantSnapshot(productId, variantId, name, label, "SKU-X", price, 1,
                100, true, false));
        return this;
    }

    public void remove(UUID variantId) {
        variants.remove(variantId);
    }

    @Override
    public Map<UUID, VariantSnapshot> findVariants(Collection<UUID> variantIds) {
        Map<UUID, VariantSnapshot> found = new HashMap<>();
        for (UUID id : variantIds) {
            VariantSnapshot snapshot = variants.get(id);
            if (snapshot != null) {
                found.put(id, snapshot);
            }
        }
        return found;
    }
}

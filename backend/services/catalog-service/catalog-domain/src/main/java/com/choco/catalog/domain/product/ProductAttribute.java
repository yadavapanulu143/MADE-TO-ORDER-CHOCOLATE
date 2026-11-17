package com.choco.catalog.domain.product;

import java.util.UUID;

/**
 * A labelled specification of a product: an ingredient, an allergen, a storage instruction, a
 * net weight.
 *
 * <p>Free-form key/value pairs rather than fixed columns, because the seller must be able to state
 * whatever the product warrants and the requirement explicitly forbids inventing recipes. A schema
 * with a hard-coded "cocoa_percentage" column would force a migration every time a new kind of
 * detail is published, and would imply values the seller has not supplied.
 *
 * <p>The key is normalised so "Cocoa %" and "cocoa %" cannot both exist on one product and render as
 * two confusing lines on the storefront.
 */
public class ProductAttribute {

    private static final int MAX_KEY = 60;
    private static final int MAX_VALUE = 300;

    private final UUID id;
    private final UUID productId;
    private String name;
    private String value;
    private int displayOrder;

    private ProductAttribute(UUID id, UUID productId, String name, String value, int displayOrder) {
        this.id = id;
        this.productId = productId;
        this.name = requireName(name);
        this.value = requireValue(value);
        this.displayOrder = displayOrder;
    }

    public static ProductAttribute create(UUID id, UUID productId, String name, String value, int displayOrder) {
        if (productId == null) {
            throw new IllegalArgumentException("Product id is required");
        }
        return new ProductAttribute(id, productId, name, value, displayOrder);
    }

    public static ProductAttribute rehydrate(UUID id, UUID productId, String name, String value,
                                             int displayOrder) {
        return new ProductAttribute(id, productId, name, value, displayOrder);
    }

    public void update(String name, String value, int displayOrder) {
        String newName = requireName(name);
        String newValue = requireValue(value);
        this.name = newName;
        this.value = newValue;
        this.displayOrder = displayOrder;
    }

    /** Normalised form of a name, used to detect a duplicate attribute on the same product. */
    public static String normaliseName(String name) {
        return requireName(name).toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireName(String value) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Attribute name is required");
        }
        if (trimmed.length() > MAX_KEY) {
            throw new IllegalArgumentException("Attribute name must be at most " + MAX_KEY + " characters");
        }
        return trimmed;
    }

    private static String requireValue(String value) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Attribute value is required");
        }
        if (trimmed.length() > MAX_VALUE) {
            throw new IllegalArgumentException("Attribute value must be at most " + MAX_VALUE + " characters");
        }
        return trimmed;
    }

    public UUID id() {
        return id;
    }

    public UUID productId() {
        return productId;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public int displayOrder() {
        return displayOrder;
    }
}

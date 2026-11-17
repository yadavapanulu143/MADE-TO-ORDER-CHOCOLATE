package com.choco.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Persistence model for a product variant.
 *
 * <p>Price is stored as an integer count of paise, matching the domain's {@code Money}. No column here
 * is a floating-point type, so a stored price is exact and re-reading it cannot drift.
 */
@Entity
@Table(name = "product_variants")
public class ProductVariantJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private ProductJpaEntity product;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "sku", length = 64)
    private String sku;

    /** Price in paise. Integer, never a floating-point type. */
    @Column(name = "price_minor_units", nullable = false)
    private long priceMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "min_order_quantity", nullable = false)
    private int minOrderQuantity;

    @Column(name = "max_order_quantity", nullable = false)
    private int maxOrderQuantity;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ProductVariantJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductJpaEntity getProduct() {
        return product;
    }

    public void setProduct(ProductJpaEntity product) {
        this.product = product;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public long getPriceMinorUnits() {
        return priceMinorUnits;
    }

    public void setPriceMinorUnits(long priceMinorUnits) {
        this.priceMinorUnits = priceMinorUnits;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getMinOrderQuantity() {
        return minOrderQuantity;
    }

    public void setMinOrderQuantity(int minOrderQuantity) {
        this.minOrderQuantity = minOrderQuantity;
    }

    public int getMaxOrderQuantity() {
        return maxOrderQuantity;
    }

    public void setMaxOrderQuantity(int maxOrderQuantity) {
        this.maxOrderQuantity = maxOrderQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}

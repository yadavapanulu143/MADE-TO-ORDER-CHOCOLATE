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
 * Persistence model for a product attribute.
 *
 * <p>Name/value pairs rather than fixed columns, so the seller can publish whatever details a product
 * warrants — an ingredient list, an allergen warning, a storage instruction — without a schema change.
 */
@Entity
@Table(name = "product_attributes")
public class ProductAttributeJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private ProductJpaEntity product;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "value", nullable = false, length = 300)
    private String value;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ProductAttributeJpaEntity() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}

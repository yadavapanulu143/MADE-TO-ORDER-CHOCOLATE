package com.choco.catalog.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistence model for a product and its children.
 *
 * <p>The children are mapped as a cascade-all, orphan-removal collection, so saving the product saves
 * its variants, images and attributes and a child that disappears from the collection is deleted. That
 * matches the aggregate: the product is the only way in, and a variant with no product does not exist.
 *
 * <p>Children are loaded lazily. Loading them eagerly would make a page of products fetch every child
 * of every product in one join, and — worse — Spring Data cannot apply a SQL {@code LIMIT} to a
 * collection join, so it would fetch the entire table and page it in memory. Lazy loading keeps the
 * listing query a plain {@code LIMIT}/{@code OFFSET}, and the small extra round trip is bounded by the
 * batch size configured on the persistence unit.
 *
 * <p>A {@code version} column provides optimistic locking: two administrators editing the same product
 * at once cannot silently overwrite each other, the second save fails and the caller retries with the
 * current state.
 */
@Entity
@Table(name = "products")
public class ProductJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Public address. Unique, and never regenerated when the name changes. */
    @Column(name = "slug", nullable = false, length = 80, updatable = false)
    private String slug;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "description", length = 5000)
    private String description;

    @Column(name = "preparation_info", length = 1000)
    private String preparationInfo;

    @Column(name = "delivery_info", length = 1000)
    private String deliveryInfo;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductVariantJpaEntity> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductImageJpaEntity> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductAttributeJpaEntity> attributes = new ArrayList<>();

    protected ProductJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPreparationInfo() {
        return preparationInfo;
    }

    public void setPreparationInfo(String preparationInfo) {
        this.preparationInfo = preparationInfo;
    }

    public String getDeliveryInfo() {
        return deliveryInfo;
    }

    public void setDeliveryInfo(String deliveryInfo) {
        this.deliveryInfo = deliveryInfo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public List<ProductVariantJpaEntity> getVariants() {
        return variants;
    }

    public void setVariants(List<ProductVariantJpaEntity> variants) {
        this.variants = variants;
    }

    public List<ProductImageJpaEntity> getImages() {
        return images;
    }

    public void setImages(List<ProductImageJpaEntity> images) {
        this.images = images;
    }

    public List<ProductAttributeJpaEntity> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<ProductAttributeJpaEntity> attributes) {
        this.attributes = attributes;
    }
}

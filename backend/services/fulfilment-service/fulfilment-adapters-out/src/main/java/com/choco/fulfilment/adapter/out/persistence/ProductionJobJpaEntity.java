package com.choco.fulfilment.adapter.out.persistence;

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
 * Persistence model for a production job and its notes.
 *
 * <p>Notes are cascade-all with orphan removal, matching the aggregate where the job is the only way
 * in. They are lazy: eagerly fetching them would make a page of jobs join every note of every job, and
 * Spring Data cannot put a SQL LIMIT on a collection join.
 *
 * <p>{@code version} gives optimistic locking, so two sellers advancing the same job cannot silently
 * overwrite each other's move.
 */
@Entity
@Table(name = "production_jobs")
public class ProductionJobJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, length = 40, updatable = false)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;

    @Column(name = "variant_label", nullable = false, length = 80, updatable = false)
    private String variantLabel;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "address_recipient_name", length = 150)
    private String addressRecipientName;

    @Column(name = "address_mobile", length = 20)
    private String addressMobile;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Column(name = "address_state", length = 100)
    private String addressState;

    @Column(name = "address_pincode", length = 6)
    private String addressPincode;

    @Column(name = "address_instructions", length = 500)
    private String addressInstructions;

    @Column(name = "delivery_charge_minor_units", nullable = false)
    private long deliveryChargeMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "hold_reason", length = 500)
    private String holdReason;

    @Column(name = "ingredients_consumed", nullable = false)
    private boolean ingredientsConsumed;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<ProductionNoteJpaEntity> notes = new ArrayList<>();

    protected ProductionJobJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }
    public String getVariantLabel() { return variantLabel; }
    public void setVariantLabel(String variantLabel) { this.variantLabel = variantLabel; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getAddressRecipientName() { return addressRecipientName; }
    public void setAddressRecipientName(String v) { this.addressRecipientName = v; }
    public String getAddressMobile() { return addressMobile; }
    public void setAddressMobile(String v) { this.addressMobile = v; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String v) { this.addressLine1 = v; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String v) { this.addressLine2 = v; }
    public String getAddressCity() { return addressCity; }
    public void setAddressCity(String v) { this.addressCity = v; }
    public String getAddressState() { return addressState; }
    public void setAddressState(String v) { this.addressState = v; }
    public String getAddressPincode() { return addressPincode; }
    public void setAddressPincode(String v) { this.addressPincode = v; }
    public String getAddressInstructions() { return addressInstructions; }
    public void setAddressInstructions(String v) { this.addressInstructions = v; }
    public long getDeliveryChargeMinorUnits() { return deliveryChargeMinorUnits; }
    public void setDeliveryChargeMinorUnits(long v) { this.deliveryChargeMinorUnits = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHoldReason() { return holdReason; }
    public void setHoldReason(String holdReason) { this.holdReason = holdReason; }
    public boolean isIngredientsConsumed() { return ingredientsConsumed; }
    public void setIngredientsConsumed(boolean v) { this.ingredientsConsumed = v; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public List<ProductionNoteJpaEntity> getNotes() { return notes; }
    public void setNotes(List<ProductionNoteJpaEntity> notes) { this.notes = notes; }
}

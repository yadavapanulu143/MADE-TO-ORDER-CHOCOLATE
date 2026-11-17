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
 * Persistence model for a delivery and its attempts.
 *
 * <p>Attempts are cascade-all with orphan removal, matching the aggregate. The address is stored
 * inline rather than joined to the job, so a delivery is self-contained and can be rendered even if
 * the job is later pruned.
 */
@Entity
@Table(name = "deliveries")
public class DeliveryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "order_number", nullable = false, length = 40, updatable = false)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "address_recipient_name", nullable = false, length = 150)
    private String addressRecipientName;

    @Column(name = "address_mobile", nullable = false, length = 20)
    private String addressMobile;

    @Column(name = "address_line1", nullable = false, length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "address_city", nullable = false, length = 100)
    private String addressCity;

    @Column(name = "address_state", nullable = false, length = 100)
    private String addressState;

    @Column(name = "address_pincode", nullable = false, length = 6)
    private String addressPincode;

    @Column(name = "address_instructions", length = 500)
    private String addressInstructions;

    @Column(name = "delivery_charge_minor_units", nullable = false)
    private long deliveryChargeMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "delivery_person_name", length = 120)
    private String deliveryPersonName;

    @Column(name = "delivery_person_phone", length = 20)
    private String deliveryPersonPhone;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "proof_reference", length = 500)
    private String proofReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("attemptNumber ASC")
    private List<DeliveryAttemptJpaEntity> attempts = new ArrayList<>();

    protected DeliveryJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
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
    public String getDeliveryPersonName() { return deliveryPersonName; }
    public void setDeliveryPersonName(String v) { this.deliveryPersonName = v; }
    public String getDeliveryPersonPhone() { return deliveryPersonPhone; }
    public void setDeliveryPersonPhone(String v) { this.deliveryPersonPhone = v; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(Instant dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getProofReference() { return proofReference; }
    public void setProofReference(String proofReference) { this.proofReference = proofReference; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public List<DeliveryAttemptJpaEntity> getAttempts() { return attempts; }
    public void setAttempts(List<DeliveryAttemptJpaEntity> attempts) { this.attempts = attempts; }
}

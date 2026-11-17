package com.choco.order.adapter.out.persistence;

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
 * Persistence model for an order and its children.
 *
 * <p>The lines, status history and notes are cascade-all with orphan removal, so saving the order
 * saves them and a child that disappears is deleted — matching the aggregate, where the order is the
 * only way in.
 *
 * <p>Children are lazy. Eagerly fetching them would make a page of orders join every child of every
 * order, and Spring Data cannot put a SQL {@code LIMIT} on a collection join, so the listing would
 * fetch the whole table and page it in memory. Lazy loading keeps the listing a plain
 * {@code LIMIT}/{@code OFFSET}; the extra round trips are bounded by the batch size on the unit.
 *
 * <p>{@code version} gives optimistic locking: two concurrent status updates on one order cannot
 * silently overwrite each other; the second fails and the caller retries.
 */
@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_number", nullable = false, length = 40, updatable = false)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(name = "customer_mobile", nullable = false, length = 20)
    private String customerMobile;

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

    @Column(name = "items_total_minor_units", nullable = false)
    private long itemsTotalMinorUnits;

    @Column(name = "delivery_charge_minor_units", nullable = false)
    private long deliveryChargeMinorUnits;

    @Column(name = "total_minor_units", nullable = false)
    private long totalMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "payment_status", nullable = false, length = 24)
    private String paymentStatus;

    @Column(name = "preparation_status", length = 24)
    private String preparationStatus;

    @Column(name = "delivery_status", nullable = false, length = 24)
    private String deliveryStatus;

    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "provider_order_id", length = 64)
    private String providerOrderId;

    @Column(name = "delivery_person_name", length = 150)
    private String deliveryPersonName;

    @Column(name = "delivery_person_phone", length = 20)
    private String deliveryPersonPhone;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<OrderLineJpaEntity> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<OrderStatusHistoryJpaEntity> statusHistory = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<OrderNoteJpaEntity> notes = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerMobile() {
        return customerMobile;
    }

    public void setCustomerMobile(String customerMobile) {
        this.customerMobile = customerMobile;
    }

    public String getAddressRecipientName() {
        return addressRecipientName;
    }

    public void setAddressRecipientName(String addressRecipientName) {
        this.addressRecipientName = addressRecipientName;
    }

    public String getAddressMobile() {
        return addressMobile;
    }

    public void setAddressMobile(String addressMobile) {
        this.addressMobile = addressMobile;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public void setAddressCity(String addressCity) {
        this.addressCity = addressCity;
    }

    public String getAddressState() {
        return addressState;
    }

    public void setAddressState(String addressState) {
        this.addressState = addressState;
    }

    public String getAddressPincode() {
        return addressPincode;
    }

    public void setAddressPincode(String addressPincode) {
        this.addressPincode = addressPincode;
    }

    public String getAddressInstructions() {
        return addressInstructions;
    }

    public void setAddressInstructions(String addressInstructions) {
        this.addressInstructions = addressInstructions;
    }

    public long getItemsTotalMinorUnits() {
        return itemsTotalMinorUnits;
    }

    public void setItemsTotalMinorUnits(long itemsTotalMinorUnits) {
        this.itemsTotalMinorUnits = itemsTotalMinorUnits;
    }

    public long getDeliveryChargeMinorUnits() {
        return deliveryChargeMinorUnits;
    }

    public void setDeliveryChargeMinorUnits(long deliveryChargeMinorUnits) {
        this.deliveryChargeMinorUnits = deliveryChargeMinorUnits;
    }

    public long getTotalMinorUnits() {
        return totalMinorUnits;
    }

    public void setTotalMinorUnits(long totalMinorUnits) {
        this.totalMinorUnits = totalMinorUnits;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getPreparationStatus() {
        return preparationStatus;
    }

    public void setPreparationStatus(String preparationStatus) {
        this.preparationStatus = preparationStatus;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public void setProviderOrderId(String providerOrderId) {
        this.providerOrderId = providerOrderId;
    }

    public String getDeliveryPersonName() {
        return deliveryPersonName;
    }

    public void setDeliveryPersonName(String deliveryPersonName) {
        this.deliveryPersonName = deliveryPersonName;
    }

    public String getDeliveryPersonPhone() {
        return deliveryPersonPhone;
    }

    public void setDeliveryPersonPhone(String deliveryPersonPhone) {
        this.deliveryPersonPhone = deliveryPersonPhone;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
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

    public List<OrderLineJpaEntity> getLines() {
        return lines;
    }

    public void setLines(List<OrderLineJpaEntity> lines) {
        this.lines = lines;
    }

    public List<OrderStatusHistoryJpaEntity> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<OrderStatusHistoryJpaEntity> statusHistory) {
        this.statusHistory = statusHistory;
    }

    public List<OrderNoteJpaEntity> getNotes() {
        return notes;
    }

    public void setNotes(List<OrderNoteJpaEntity> notes) {
        this.notes = notes;
    }
}

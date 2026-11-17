package com.choco.order.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistence model for an invoice and its lines.
 *
 * <p>No {@code @Version}: an invoice is never updated after issuance, so there is nothing for
 * optimistic locking to protect. A correction is a new document, not an edit.
 *
 * <p>Lines are cascade-all with orphan removal, so saving the invoice saves them and a line that
 * disappears is deleted — matching the aggregate, where the invoice is the only way in.
 */
@Entity
@Table(name = "invoices")
public class InvoiceJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, length = 40, updatable = false)
    private String invoiceNumber;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

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

    @Column(name = "billing_recipient_name", nullable = false, length = 150)
    private String billingRecipientName;

    @Column(name = "billing_mobile", nullable = false, length = 20)
    private String billingMobile;

    @Column(name = "billing_line1", nullable = false, length = 200)
    private String billingLine1;

    @Column(name = "billing_line2", length = 200)
    private String billingLine2;

    @Column(name = "billing_city", nullable = false, length = 100)
    private String billingCity;

    @Column(name = "billing_state", nullable = false, length = 100)
    private String billingState;

    @Column(name = "billing_pincode", nullable = false, length = 6)
    private String billingPincode;

    @Column(name = "items_total_minor_units", nullable = false)
    private long itemsTotalMinorUnits;

    @Column(name = "delivery_charge_minor_units", nullable = false)
    private long deliveryChargeMinorUnits;

    @Column(name = "total_minor_units", nullable = false)
    private long totalMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<InvoiceLineJpaEntity> lines = new ArrayList<>();

    protected InvoiceJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
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

    public String getBillingRecipientName() {
        return billingRecipientName;
    }

    public void setBillingRecipientName(String billingRecipientName) {
        this.billingRecipientName = billingRecipientName;
    }

    public String getBillingMobile() {
        return billingMobile;
    }

    public void setBillingMobile(String billingMobile) {
        this.billingMobile = billingMobile;
    }

    public String getBillingLine1() {
        return billingLine1;
    }

    public void setBillingLine1(String billingLine1) {
        this.billingLine1 = billingLine1;
    }

    public String getBillingLine2() {
        return billingLine2;
    }

    public void setBillingLine2(String billingLine2) {
        this.billingLine2 = billingLine2;
    }

    public String getBillingCity() {
        return billingCity;
    }

    public void setBillingCity(String billingCity) {
        this.billingCity = billingCity;
    }

    public String getBillingState() {
        return billingState;
    }

    public void setBillingState(String billingState) {
        this.billingState = billingState;
    }

    public String getBillingPincode() {
        return billingPincode;
    }

    public void setBillingPincode(String billingPincode) {
        this.billingPincode = billingPincode;
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

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public List<InvoiceLineJpaEntity> getLines() {
        return lines;
    }

    public void setLines(List<InvoiceLineJpaEntity> lines) {
        this.lines = lines;
    }
}

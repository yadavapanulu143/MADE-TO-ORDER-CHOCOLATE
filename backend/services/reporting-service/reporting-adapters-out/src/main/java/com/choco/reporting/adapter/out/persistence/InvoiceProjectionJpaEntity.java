package com.choco.reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The invoice read model as stored, keyed on the order service's invoice id.
 *
 * <p>Carries the invoice number and billed total, so a sales report can reconcile what was invoiced
 * against what was captured without calling back into the order service.
 */
@Entity
@Table(name = "invoice_projection")
public class InvoiceProjectionJpaEntity {

    @Id
    @Column(name = "invoice_id", nullable = false, updatable = false, length = 64)
    private String invoiceId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "order_number", length = 40)
    private String orderNumber;

    @Column(name = "invoice_number", length = 40)
    private String invoiceNumber;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "total_minor_units", nullable = false)
    private long totalMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected InvoiceProjectionJpaEntity() {
    }

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public long getTotalMinorUnits() { return totalMinorUnits; }
    public void setTotalMinorUnits(long totalMinorUnits) { this.totalMinorUnits = totalMinorUnits; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
}

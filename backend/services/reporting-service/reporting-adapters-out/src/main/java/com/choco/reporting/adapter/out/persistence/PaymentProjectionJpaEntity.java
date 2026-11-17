package com.choco.reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The payment read model as stored.
 *
 * <p>Keyed on the provider payment id, so a redelivered capture or a failure for the same attempt
 * updates one row. It holds an amount and a coarse method, never a card number, UPI id or any provider
 * credential — the reporting service reports on money, it does not store instruments.
 */
@Entity
@Table(name = "payment_projection")
public class PaymentProjectionJpaEntity {

    @Id
    @Column(name = "payment_id", nullable = false, updatable = false, length = 64)
    private String paymentId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "method", length = 32)
    private String method;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected PaymentProjectionJpaEntity() {
    }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getAmountMinorUnits() { return amountMinorUnits; }
    public void setAmountMinorUnits(long amountMinorUnits) { this.amountMinorUnits = amountMinorUnits; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}

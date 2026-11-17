package com.choco.reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The refund read model as stored.
 *
 * <p>A refund is requested by the order service and processed by the payment service, so the same
 * order may produce both events. Keyed on the refund id where the payment service supplies one, and on
 * a derived id otherwise, so a request and its later processing update one row rather than two.
 */
@Entity
@Table(name = "refund_projection")
public class RefundProjectionJpaEntity {

    @Id
    @Column(name = "refund_id", nullable = false, updatable = false, length = 64)
    private String refundId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "order_number", length = 40)
    private String orderNumber;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "amount_minor_units", nullable = false)
    private long amountMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "full_refund", nullable = false)
    private boolean fullRefund;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected RefundProjectionJpaEntity() {
    }

    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public long getAmountMinorUnits() { return amountMinorUnits; }
    public void setAmountMinorUnits(long amountMinorUnits) { this.amountMinorUnits = amountMinorUnits; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isFullRefund() { return fullRefund; }
    public void setFullRefund(boolean fullRefund) { this.fullRefund = fullRefund; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}

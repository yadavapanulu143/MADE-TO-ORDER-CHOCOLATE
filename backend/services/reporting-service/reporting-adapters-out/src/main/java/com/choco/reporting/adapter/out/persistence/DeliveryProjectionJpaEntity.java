package com.choco.reporting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The delivery read model as stored, keyed on the fulfilment service's delivery id.
 *
 * <p>Holds only what the delivery events carried. There is no courier tracking number or position,
 * because the business is seller-managed and has none; a column here would imply a capability that
 * does not exist.
 */
@Entity
@Table(name = "delivery_projection")
public class DeliveryProjectionJpaEntity {

    @Id
    @Column(name = "delivery_id", nullable = false, updatable = false, length = 64)
    private String deliveryId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "order_number", length = 40)
    private String orderNumber;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "delivery_person_name", length = 120)
    private String deliveryPersonName;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryProjectionJpaEntity() {
    }

    public String getDeliveryId() { return deliveryId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDeliveryPersonName() { return deliveryPersonName; }
    public void setDeliveryPersonName(String deliveryPersonName) { this.deliveryPersonName = deliveryPersonName; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

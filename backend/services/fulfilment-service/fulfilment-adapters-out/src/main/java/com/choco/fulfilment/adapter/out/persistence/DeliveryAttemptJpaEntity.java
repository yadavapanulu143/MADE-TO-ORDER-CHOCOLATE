package com.choco.fulfilment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Persistence model for one recorded delivery attempt, owned by its delivery. */
@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttemptJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private DeliveryJpaEntity delivery;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "outcome", nullable = false, length = 24)
    private String outcome;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected DeliveryAttemptJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public DeliveryJpaEntity getDelivery() { return delivery; }
    public void setDelivery(DeliveryJpaEntity delivery) { this.delivery = delivery; }
    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant attemptedAt) { this.attemptedAt = attemptedAt; }
}

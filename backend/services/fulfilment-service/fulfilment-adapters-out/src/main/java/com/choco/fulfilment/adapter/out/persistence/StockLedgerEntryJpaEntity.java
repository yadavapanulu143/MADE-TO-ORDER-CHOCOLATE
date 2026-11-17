package com.choco.fulfilment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for one stock movement.
 *
 * <p>The id is application-assigned like every other aggregate here, and the entry is immutable:
 * there is no setter path that changes a stored movement.
 */
@Entity
@Table(name = "stock_ledger_entries")
public class StockLedgerEntryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ingredient_id", nullable = false, updatable = false)
    private UUID ingredientId;

    @Column(name = "reason", nullable = false, length = 16, updatable = false)
    private String reason;

    @Column(name = "delta_milli_units", nullable = false, updatable = false)
    private long deltaMilliUnits;

    @Column(name = "balance_after_milli_units", nullable = false, updatable = false)
    private long balanceAfterMilliUnits;

    @Column(name = "production_job_id")
    private UUID productionJobId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StockLedgerEntryJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getIngredientId() { return ingredientId; }
    public void setIngredientId(UUID ingredientId) { this.ingredientId = ingredientId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getDeltaMilliUnits() { return deltaMilliUnits; }
    public void setDeltaMilliUnits(long v) { this.deltaMilliUnits = v; }
    public long getBalanceAfterMilliUnits() { return balanceAfterMilliUnits; }
    public void setBalanceAfterMilliUnits(long v) { this.balanceAfterMilliUnits = v; }
    public UUID getProductionJobId() { return productionJobId; }
    public void setProductionJobId(UUID v) { this.productionJobId = v; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}

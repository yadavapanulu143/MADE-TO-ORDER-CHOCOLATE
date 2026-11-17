package com.choco.fulfilment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Persistence model for a tracked ingredient. Amounts are thousandths of the unit. */
@Entity
@Table(name = "ingredients")
public class IngredientJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "unit", nullable = false, length = 16)
    private String unit;

    @Column(name = "on_hand_milli_units", nullable = false)
    private long onHandMilliUnits;

    @Column(name = "minimum_milli_units", nullable = false)
    private long minimumMilliUnits;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected IngredientJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public long getOnHandMilliUnits() { return onHandMilliUnits; }
    public void setOnHandMilliUnits(long v) { this.onHandMilliUnits = v; }
    public long getMinimumMilliUnits() { return minimumMilliUnits; }
    public void setMinimumMilliUnits(long v) { this.minimumMilliUnits = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}

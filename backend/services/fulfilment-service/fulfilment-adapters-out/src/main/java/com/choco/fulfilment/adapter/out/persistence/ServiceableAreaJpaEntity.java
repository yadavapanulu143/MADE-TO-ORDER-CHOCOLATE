package com.choco.fulfilment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Persistence model for a delivery area the seller covers. */
@Entity
@Table(name = "serviceable_areas")
public class ServiceableAreaJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "pincode_prefix", nullable = false, length = 6)
    private String pincodePrefix;

    @Column(name = "area_name", nullable = false, length = 120)
    private String areaName;

    @Column(name = "delivery_charge_minor_units", nullable = false)
    private long deliveryChargeMinorUnits;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ServiceableAreaJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getPincodePrefix() { return pincodePrefix; }
    public void setPincodePrefix(String v) { this.pincodePrefix = v; }
    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }
    public long getDeliveryChargeMinorUnits() { return deliveryChargeMinorUnits; }
    public void setDeliveryChargeMinorUnits(long v) { this.deliveryChargeMinorUnits = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}

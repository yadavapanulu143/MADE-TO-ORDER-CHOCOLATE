package com.choco.fulfilment.domain.delivery;

import com.choco.core.domain.AggregateRoot;
import com.choco.core.domain.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * A place the seller delivers to, and what it costs.
 *
 * <p>Delivery is seller-managed, so serviceability is a business setting the seller maintains rather
 * than a courier lookup. An area is either an exact six-digit PIN code or a prefix of one — a seller
 * who covers a whole city can enter {@code 560} once instead of every PIN in it, and the matching is
 * longest-prefix-first so a specific PIN can override the charge for the broader area it sits in.
 *
 * <p>The charge is held here rather than as one global number because a seller reasonably charges more
 * for a far-flung PIN than for the next street. A zero charge is a real value, not "unset": it means
 * free delivery to that area.
 */
public class ServiceableArea extends AggregateRoot {

    public static final int MAX_AREA_NAME_LENGTH = 120;

    private final UUID id;
    private final String pincodePrefix;
    private final Instant createdAt;

    private String areaName;
    private Money deliveryCharge;
    private boolean active;
    private Instant updatedAt;

    private ServiceableArea(UUID id, String pincodePrefix, String areaName, Money deliveryCharge,
                            boolean active, Instant createdAt) {
        this.id = id;
        this.pincodePrefix = requirePrefix(pincodePrefix);
        this.areaName = requireName(areaName);
        this.deliveryCharge = requireCharge(deliveryCharge);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static ServiceableArea create(UUID id, String pincodePrefix, String areaName,
                                         Money deliveryCharge, Instant now) {
        return new ServiceableArea(id, pincodePrefix, areaName, deliveryCharge, true, now);
    }

    public static ServiceableArea rehydrate(UUID id, String pincodePrefix, String areaName,
                                            Money deliveryCharge, boolean active, Instant createdAt,
                                            Instant updatedAt) {
        ServiceableArea area = new ServiceableArea(id, pincodePrefix, areaName, deliveryCharge, active,
                createdAt);
        area.updatedAt = updatedAt;
        return area;
    }

    /** Changes the charge for this area. */
    public void updateCharge(Money newCharge, Instant now) {
        this.deliveryCharge = requireCharge(newCharge);
        this.updatedAt = now;
    }

    /** Renames the area, e.g. from a PIN to the neighbourhood it covers. */
    public void rename(String newName, Instant now) {
        this.areaName = requireName(newName);
        this.updatedAt = now;
    }

    public void deactivate(Instant now) {
        this.active = false;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        this.active = true;
        this.updatedAt = now;
    }

    /** Whether this area covers the given PIN code. */
    public boolean covers(String pincode) {
        return active && pincode != null && pincode.startsWith(pincodePrefix);
    }

    /**
     * How specific this area is. A longer prefix beats a shorter one, so an exact PIN overrides the
     * city-wide entry, and the caller picks the highest score among the matches.
     */
    public int specificity() {
        return pincodePrefix.length();
    }

    private static String requirePrefix(String prefix) {
        String trimmed = prefix == null ? "" : prefix.trim();
        if (!trimmed.matches("[1-9][0-9]{0,5}")) {
            throw new IllegalArgumentException(
                    "A serviceable area must be a PIN code or its prefix: " + prefix);
        }
        return trimmed;
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Area name is required");
        }
        if (trimmed.length() > MAX_AREA_NAME_LENGTH) {
            throw new IllegalArgumentException("Area name must be at most " + MAX_AREA_NAME_LENGTH
                    + " characters");
        }
        return trimmed;
    }

    private static Money requireCharge(Money charge) {
        if (charge == null) {
            throw new IllegalArgumentException("A delivery charge is required");
        }
        if (charge.isNegative()) {
            throw new IllegalArgumentException("A delivery charge cannot be negative");
        }
        return charge;
    }

    public UUID id() {
        return id;
    }

    public String pincodePrefix() {
        return pincodePrefix;
    }

    public String areaName() {
        return areaName;
    }

    public Money deliveryCharge() {
        return deliveryCharge;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

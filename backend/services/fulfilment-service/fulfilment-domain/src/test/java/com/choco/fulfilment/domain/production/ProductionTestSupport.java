package com.choco.fulfilment.domain.production;

import com.choco.core.domain.Money;
import com.choco.core.domain.PhoneNumber;
import com.choco.core.domain.Pincode;
import com.choco.core.domain.Quantity;
import com.choco.fulfilment.domain.delivery.DeliveryAddress;
import java.time.Instant;
import java.util.UUID;

/** Shared fixtures for the production aggregate tests. */
final class ProductionTestSupport {

    static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");
    static final Instant LATER = T0.plusSeconds(600);
    static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID VARIANT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private ProductionTestSupport() {
    }

    static DeliveryAddress address() {
        return new DeliveryAddress("Asha Rao", PhoneNumber.of("9876543210"), "12 Cocoa Lane", null,
                "Bengaluru", "Karnataka", Pincode.of("560001"), null);
    }

    static ProductionJob openJob() {
        return openJob(2);
    }

    static ProductionJob openJob(int quantity) {
        return ProductionJob.open(UUID.randomUUID(), ORDER_ID, "CHOCO-2026001-1", CUSTOMER_ID,
                PRODUCT_ID, VARIANT_ID, "Dark 200 g", Quantity.of(quantity), address(),
                Money.inr(5000), T0);
    }
}

package com.choco.fulfilment.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Serviceable area")
class ServiceableAreaTest {

    private static final Instant T0 = Instant.parse("2026-02-01T09:00:00Z");
    private static final Instant LATER = T0.plusSeconds(600);

    private ServiceableArea area(String prefix) {
        return ServiceableArea.create(UUID.randomUUID(), prefix, "MG Road", Money.inr(5000), T0);
    }

    @Test
    @DisplayName("an exact PIN covers itself")
    void exactPinCoversItself() {
        assertThat(area("560001").covers("560001")).isTrue();
    }

    @Test
    @DisplayName("a prefix covers every PIN beneath it")
    void prefixCoversBeneathIt() {
        assertThat(area("560").covers("560001")).isTrue();
        assertThat(area("560").covers("560099")).isTrue();
    }

    @Test
    @DisplayName("a prefix does not cover a PIN that merely contains it")
    void prefixDoesNotCoverUnrelated() {
        assertThat(area("560001").covers("560002")).isFalse();
        assertThat(area("560").covers("156000")).isFalse();
    }

    @Test
    @DisplayName("a deactivated area covers nothing, however well it matches")
    void deactivatedCoversNothing() {
        ServiceableArea area = area("560001");
        area.deactivate(LATER);

        assertThat(area.covers("560001")).isFalse();
        assertThat(area.active()).isFalse();
    }

    @Test
    @DisplayName("reactivating an area restores coverage")
    void reactivateRestoresCoverage() {
        ServiceableArea area = area("560001");
        area.deactivate(LATER);
        area.activate(LATER);

        assertThat(area.covers("560001")).isTrue();
    }

    @Test
    @DisplayName("specificity is the prefix length, so an exact PIN beats a city-wide entry")
    void specificityIsPrefixLength() {
        assertThat(area("560001").specificity()).isGreaterThan(area("560").specificity());
    }

    @Test
    @DisplayName("a null or blank PIN is never covered")
    void nullOrBlankIsNotCovered() {
        assertThat(area("560001").covers(null)).isFalse();
        assertThat(area("560001").covers("")).isFalse();
    }

    @Test
    @DisplayName("a prefix must be a real PIN or its leading part")
    void prefixFormatIsEnforced() {
        assertThatThrownBy(() -> area("012345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> area("5600017")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> area("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> area(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a negative delivery charge is refused; zero means free delivery")
    void chargeRules() {
        assertThatThrownBy(() -> ServiceableArea.create(UUID.randomUUID(), "560001", "MG Road",
                Money.inr(-1), T0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(area("560001").deliveryCharge()).isEqualTo(Money.inr(5000));
        ServiceableArea free = ServiceableArea.create(UUID.randomUUID(), "560001", "MG Road",
                Money.inr(0), T0);
        assertThat(free.deliveryCharge()).isEqualTo(Money.inr(0));
    }

    @Test
    @DisplayName("a missing area name is refused")
    void nameIsRequired() {
        assertThatThrownBy(() -> ServiceableArea.create(UUID.randomUUID(), "560001", "  ",
                Money.inr(0), T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updating the charge and renaming stamp the update time")
    void updatesAreStamped() {
        ServiceableArea area = area("560001");
        area.updateCharge(Money.inr(9900), LATER);
        area.rename("MG Road, Bengaluru", LATER);

        assertThat(area.deliveryCharge()).isEqualTo(Money.inr(9900));
        assertThat(area.areaName()).isEqualTo("MG Road, Bengaluru");
        assertThat(area.updatedAt()).isEqualTo(LATER);
    }
}

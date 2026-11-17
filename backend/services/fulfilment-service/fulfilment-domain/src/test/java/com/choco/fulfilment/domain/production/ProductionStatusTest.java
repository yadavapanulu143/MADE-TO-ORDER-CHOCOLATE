package com.choco.fulfilment.domain.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Production status transitions")
class ProductionStatusTest {

    @Test
    @DisplayName("the seller cannot jump straight from received to ready for dispatch")
    void noJumpToReady() {
        assertThat(ProductionStatus.RECEIVED.canTransitionTo(ProductionStatus.READY_FOR_DISPATCH))
                .isFalse();
    }

    @Test
    @DisplayName("a job cannot transition to itself")
    void noSelfTransition() {
        for (ProductionStatus status : ProductionStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("%s must not transition to itself", status)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a null target is refused rather than treated as a no-op")
    void nullTargetRefused() {
        assertThat(ProductionStatus.RECEIVED.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("a hold can be taken while awaiting or preparing, and resumes into preparation")
    void holdAndResume() {
        assertThat(ProductionStatus.AWAITING_PREPARATION.canTransitionTo(ProductionStatus.ON_HOLD)).isTrue();
        assertThat(ProductionStatus.PREPARATION_STARTED.canTransitionTo(ProductionStatus.ON_HOLD)).isTrue();
        assertThat(ProductionStatus.ON_HOLD.canTransitionTo(ProductionStatus.PREPARATION_STARTED)).isTrue();
        assertThat(ProductionStatus.ON_HOLD.canTransitionTo(ProductionStatus.PREPARATION_COMPLETED))
                .isFalse();
    }

    @Test
    @DisplayName("cancellation is allowed before the chocolate exists and nowhere after")
    void cancellationWindow() {
        Set<ProductionStatus> cancellable = EnumSet.of(ProductionStatus.RECEIVED,
                ProductionStatus.AWAITING_PREPARATION, ProductionStatus.PREPARATION_STARTED,
                ProductionStatus.ON_HOLD);
        for (ProductionStatus status : ProductionStatus.values()) {
            assertThat(status.canTransitionTo(ProductionStatus.CANCELLED))
                    .as("cancellation from %s", status)
                    .isEqualTo(cancellable.contains(status));
        }
    }

    @Test
    @DisplayName("ready for dispatch and cancelled are terminal")
    void terminalStates() {
        assertThat(ProductionStatus.READY_FOR_DISPATCH.isTerminal()).isTrue();
        assertThat(ProductionStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(ProductionStatus.PACKAGING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("only a finished job stops occupying the kitchen")
    void capacityOccupancy() {
        assertThat(ProductionStatus.PREPARATION_STARTED.occupiesCapacity()).isTrue();
        assertThat(ProductionStatus.PACKAGING.occupiesCapacity()).isTrue();
        assertThat(ProductionStatus.READY_FOR_DISPATCH.occupiesCapacity()).isFalse();
        assertThat(ProductionStatus.CANCELLED.occupiesCapacity()).isFalse();
    }
}

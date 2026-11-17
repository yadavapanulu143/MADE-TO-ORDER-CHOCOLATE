package com.choco.fulfilment.domain.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Quantity;
import com.choco.core.error.BusinessRuleException;
import com.choco.fulfilment.domain.event.ProductionJobCompleted;
import com.choco.fulfilment.domain.event.ProductionJobReadyForDispatch;
import com.choco.fulfilment.domain.event.ProductionJobStarted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Production job")
class ProductionJobTest {

    @Test
    @DisplayName("a job opens in RECEIVED and raises no event, because it is not yet news")
    void opensInReceived() {
        ProductionJob job = ProductionTestSupport.openJob();

        assertThat(job.status()).isEqualTo(ProductionStatus.RECEIVED);
        assertThat(job.ingredientsConsumed()).isFalse();
        assertThat(job.peekDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("the snapshot is kept: the label and quantity are copied, not referenced")
    void keepsTheSnapshot() {
        ProductionJob job = ProductionTestSupport.openJob(3);

        assertThat(job.variantLabel()).isEqualTo("Dark 200 g");
        assertThat(job.quantity()).isEqualTo(Quantity.of(3));
        assertThat(job.orderId()).isEqualTo(ProductionTestSupport.ORDER_ID);
    }

    @Test
    @DisplayName("the full preparation walk raises started, completed and ready-for-dispatch in order")
    void fullWalkRaisesEvents() {
        ProductionJob job = ProductionTestSupport.openJob();

        job.acknowledge("seller", ProductionTestSupport.LATER);
        job.startPreparation("seller", ProductionTestSupport.LATER);
        job.completePreparation("seller", ProductionTestSupport.LATER);
        job.startPackaging("seller", ProductionTestSupport.LATER);
        job.markReadyForDispatch("seller", ProductionTestSupport.LATER);

        assertThat(job.status()).isEqualTo(ProductionStatus.READY_FOR_DISPATCH);
        assertThat(job.peekDomainEvents())
                .hasAtLeastOneElementOfType(ProductionJobStarted.class)
                .hasAtLeastOneElementOfType(ProductionJobCompleted.class)
                .hasAtLeastOneElementOfType(ProductionJobReadyForDispatch.class);
    }

    @Test
    @DisplayName("a step out of order is refused")
    void outOfOrderStepIsRefused() {
        ProductionJob job = ProductionTestSupport.openJob();

        assertThatThrownBy(() -> job.startPreparation("seller", ProductionTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("RECEIVED");
    }

    @Test
    @DisplayName("packing cannot start before preparation is complete")
    void packingBeforeCompletionIsRefused() {
        ProductionJob job = ProductionTestSupport.openJob();
        job.acknowledge("seller", ProductionTestSupport.LATER);
        job.startPreparation("seller", ProductionTestSupport.LATER);

        assertThatThrownBy(() -> job.startPackaging("seller", ProductionTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a job can be held while preparing and resumed back into preparation")
    void holdAndResume() {
        ProductionJob job = ProductionTestSupport.openJob();
        job.acknowledge("seller", ProductionTestSupport.LATER);
        job.startPreparation("seller", ProductionTestSupport.LATER);

        job.hold("ran out of cocoa butter", "seller", ProductionTestSupport.LATER);
        assertThat(job.status()).isEqualTo(ProductionStatus.ON_HOLD);
        assertThat(job.holdReason()).isEqualTo("ran out of cocoa butter");

        job.resume("seller", ProductionTestSupport.LATER);
        assertThat(job.status()).isEqualTo(ProductionStatus.PREPARATION_STARTED);
        assertThat(job.holdReason()).isNull();
    }

    @Test
    @DisplayName("a packed job cannot be held: it would hide the real problem")
    void cannotHoldAPackedJob() {
        ProductionJob job = ProductionTestSupport.openJob();
        job.acknowledge("seller", ProductionTestSupport.LATER);
        job.startPreparation("seller", ProductionTestSupport.LATER);
        job.completePreparation("seller", ProductionTestSupport.LATER);
        job.startPackaging("seller", ProductionTestSupport.LATER);

        assertThatThrownBy(() -> job.hold("late change", "seller", ProductionTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("only a held job can be resumed")
    void cannotResumeAJobThatIsNotHeld() {
        ProductionJob job = ProductionTestSupport.openJob();

        assertThatThrownBy(() -> job.resume("seller", ProductionTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a job can be cancelled before the chocolate is made")
    void cancelBeforeMaking() {
        ProductionJob job = ProductionTestSupport.openJob();
        job.acknowledge("seller", ProductionTestSupport.LATER);
        job.cancel("order cancelled", "seller", ProductionTestSupport.LATER);

        assertThat(job.status()).isEqualTo(ProductionStatus.CANCELLED);
    }

    @Test
    @DisplayName("a job whose chocolate is already made cannot be cancelled")
    void cannotCancelAfterMaking() {
        ProductionJob job = ProductionTestSupport.openJob();
        job.acknowledge("seller", ProductionTestSupport.LATER);
        job.startPreparation("seller", ProductionTestSupport.LATER);
        job.completePreparation("seller", ProductionTestSupport.LATER);

        assertThatThrownBy(() -> job.cancel("changed mind", "seller", ProductionTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already made");
    }

    @Test
    @DisplayName("a terminal job cannot be cancelled again")
    void cannotCancelATerminalJob() {
        ProductionJob job = ProductionTestSupport.openJob();
        job.acknowledge("seller", ProductionTestSupport.LATER);
        job.cancel("order cancelled", "seller", ProductionTestSupport.LATER);

        assertThatThrownBy(() -> job.cancel("again", "seller", ProductionTestSupport.LATER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a job occupies the kitchen only until it is ready for dispatch")
    void capacityIsReleasedWhenReady() {
        ProductionJob job = ProductionTestSupport.openJob(4);
        assertThat(job.unitsOccupyingCapacity()).isEqualTo(4);

        job.acknowledge("seller", ProductionTestSupport.LATER);
        job.startPreparation("seller", ProductionTestSupport.LATER);
        job.completePreparation("seller", ProductionTestSupport.LATER);
        job.startPackaging("seller", ProductionTestSupport.LATER);
        job.markReadyForDispatch("seller", ProductionTestSupport.LATER);

        assertThat(job.unitsOccupyingCapacity()).isZero();
    }

    @Test
    @DisplayName("a cancelled job releases its capacity")
    void cancelledJobReleasesCapacity() {
        ProductionJob job = ProductionTestSupport.openJob(4);
        job.cancel("order cancelled", "seller", ProductionTestSupport.LATER);

        assertThat(job.unitsOccupyingCapacity()).isZero();
    }

    @Test
    @DisplayName("ingredient consumption is recorded on the job so it is not repeated")
    void consumptionFlag() {
        ProductionJob job = ProductionTestSupport.openJob();
        assertThat(job.ingredientsConsumed()).isFalse();

        job.markIngredientsConsumed(ProductionTestSupport.LATER);
        assertThat(job.ingredientsConsumed()).isTrue();

        job.markIngredientsReturned(ProductionTestSupport.LATER);
        assertThat(job.ingredientsConsumed()).isFalse();
    }

    @Test
    @DisplayName("a note is attached to the job and blank text is refused")
    void notes() {
        ProductionJob job = ProductionTestSupport.openJob();

        ProductionNote note = job.addNote("customer asked for less sugar", "seller",
                ProductionTestSupport.LATER);
        assertThat(job.notes()).containsExactly(note);
        assertThatThrownBy(() -> job.addNote("   ", "seller", ProductionTestSupport.LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a rehydrated job does not re-raise events or re-run creation rules")
    void rehydrateIsQuiet() {
        ProductionJob job = ProductionJob.rehydrate(ProductionTestSupport.openJob().id(),
                ProductionTestSupport.ORDER_ID, "CHOCO-2026001-1", ProductionTestSupport.CUSTOMER_ID,
                ProductionTestSupport.PRODUCT_ID, ProductionTestSupport.VARIANT_ID, "Dark 200 g",
                Quantity.of(1), ProductionTestSupport.address(), com.choco.core.domain.Money.inr(0),
                ProductionStatus.PACKAGING, null, true, ProductionTestSupport.T0,
                ProductionTestSupport.LATER);

        assertThat(job.status()).isEqualTo(ProductionStatus.PACKAGING);
        assertThat(job.ingredientsConsumed()).isTrue();
        assertThat(job.peekDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("a missing variant label is refused at construction")
    void variantLabelIsRequired() {
        assertThatThrownBy(() -> ProductionJob.open(java.util.UUID.randomUUID(),
                ProductionTestSupport.ORDER_ID, "CHOCO-1", ProductionTestSupport.CUSTOMER_ID,
                ProductionTestSupport.PRODUCT_ID, ProductionTestSupport.VARIANT_ID, "  ",
                Quantity.of(1), ProductionTestSupport.address(), com.choco.core.domain.Money.inr(0),
                ProductionTestSupport.T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

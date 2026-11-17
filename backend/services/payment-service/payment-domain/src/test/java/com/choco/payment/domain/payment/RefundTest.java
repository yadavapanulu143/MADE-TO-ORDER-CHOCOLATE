package com.choco.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Refund")
class RefundTest {

    private static final Instant T0 = PaymentTestSupport.T0;

    @Test
    @DisplayName("starts requested, distinct from processed")
    void startsRequested() {
        Refund refund = Refund.request(UUID.randomUUID(), UUID.randomUUID(), Money.inr(10000),
                "reason", "admin", T0);

        assertThat(refund.status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.processedAt()).isNull();
    }

    @Test
    @DisplayName("processing records the provider refund id")
    void processingRecordsProviderId() {
        Refund refund = Refund.request(UUID.randomUUID(), UUID.randomUUID(), Money.inr(10000),
                "reason", "admin", T0);

        refund.markProcessing("rfnd_1");

        assertThat(refund.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(refund.providerRefundId()).isEqualTo("rfnd_1");
    }

    @Test
    @DisplayName("processed is idempotent")
    void processedIdempotent() {
        Refund refund = Refund.request(UUID.randomUUID(), UUID.randomUUID(), Money.inr(10000),
                "reason", "admin", T0);
        refund.markProcessing("rfnd_1");
        refund.markProcessed("rfnd_1", T0.plusSeconds(10));

        refund.markProcessed("rfnd_1", T0.plusSeconds(20));

        assertThat(refund.status()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(refund.processedAt()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    @DisplayName("a failed refund cannot be marked processed")
    void failedCannotBeProcessed() {
        Refund refund = Refund.request(UUID.randomUUID(), UUID.randomUUID(), Money.inr(10000),
                "reason", "admin", T0);
        refund.markFailed("declined");

        assertThatThrownBy(() -> refund.markProcessed("rfnd_1", T0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refuses a non-positive amount")
    void refusesNonPositiveAmount() {
        assertThatThrownBy(() -> Refund.request(UUID.randomUUID(), UUID.randomUUID(), Money.inr(0),
                "reason", "admin", T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

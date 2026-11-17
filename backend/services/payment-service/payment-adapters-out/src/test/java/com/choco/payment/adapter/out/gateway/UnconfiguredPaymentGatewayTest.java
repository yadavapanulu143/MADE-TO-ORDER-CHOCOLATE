package com.choco.payment.adapter.out.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.payment.domain.port.PaymentGatewayPort;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the safe default when no provider credentials are configured.
 *
 * <p>The important assertion is the negative one: an unconfigured service must never report a payment
 * as captured or accept a webhook. A stub that returned a fake success would let a developer believe a
 * payment worked, and — worse — could be reached in a misconfigured production deployment.
 */
@DisplayName("Unconfigured payment gateway")
class UnconfiguredPaymentGatewayTest {

    private final UnconfiguredPaymentGateway gateway = new UnconfiguredPaymentGateway(new RazorpayProperties());

    @Test
    @DisplayName("refuses to create a provider order, naming the missing settings")
    void refusesCreateOrder() {
        assertThatThrownBy(() -> gateway.createOrder(Money.inr(1000), "rcpt", Map.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEPENDENCY_UNAVAILABLE)
                .hasMessageContaining("choco.payment.razorpay.key-id");
    }

    @Test
    @DisplayName("refuses to fetch a payment")
    void refusesFetch() {
        assertThatThrownBy(() -> gateway.fetchPayment("pay_1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("refuses to refund")
    void refusesRefund() {
        assertThatThrownBy(() -> gateway.refund("pay_1", Money.inr(1000), Map.of()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("never verifies a checkout signature")
    void neverVerifiesCheckoutSignature() {
        assertThat(gateway.verifyCheckoutSignature("o", "p", "s")).isFalse();
    }

    @Test
    @DisplayName("never verifies a webhook signature")
    void neverVerifiesWebhookSignature() {
        assertThat(gateway.verifyWebhookSignature("{}", "s")).isFalse();
    }

    @Test
    @DisplayName("exposes an empty publishable key rather than a placeholder that could be mistaken for real")
    void emptyPublishableKey() {
        assertThat(gateway.publishableKey()).isEmpty();
    }

    @Test
    @DisplayName("the port contract is still satisfied so the service boots")
    void implementsThePort() {
        assertThat(gateway).isInstanceOf(PaymentGatewayPort.class);
    }
}

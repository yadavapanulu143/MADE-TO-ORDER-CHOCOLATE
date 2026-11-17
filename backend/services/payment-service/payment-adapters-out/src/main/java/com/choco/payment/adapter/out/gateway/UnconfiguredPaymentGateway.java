package com.choco.payment.adapter.out.gateway;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.payment.domain.port.PaymentGatewayPort;
import java.util.Map;

/**
 * A gateway that refuses every money-moving operation because no credentials are configured.
 *
 * <p>Deliberately not a stub that returns a fake success: a developer running locally must see a clear
 * failure, never a green "payment captured" that never happened. The message names the two settings
 * that are missing so the fix is obvious.
 *
 * <p>Signature verification always returns false, so a webhook cannot be accepted while the service is
 * unconfigured either — which is the only safe behaviour, because with no secret nothing can be
 * proven.
 */
class UnconfiguredPaymentGateway implements PaymentGatewayPort {

    private static final String MESSAGE =
            "The payment provider is not configured. Set choco.payment.razorpay.key-id and "
                    + "choco.payment.razorpay.key-secret (and webhook-secret) to enable payments.";

    private final RazorpayProperties properties;

    UnconfiguredPaymentGateway(RazorpayProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProviderOrder createOrder(Money amount, String receipt, Map<String, String> notes) {
        throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE, MESSAGE);
    }

    @Override
    public ProviderPayment fetchPayment(String providerPaymentId) {
        throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE, MESSAGE);
    }

    @Override
    public boolean verifyCheckoutSignature(String providerOrderId, String providerPaymentId,
                                           String signature) {
        return false;
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return false;
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody) {
        throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE, MESSAGE);
    }

    @Override
    public ProviderRefund refund(String providerPaymentId, Money amount, Map<String, String> notes) {
        throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE, MESSAGE);
    }

    @Override
    public String publishableKey() {
        return properties.getKeyId() == null ? "" : properties.getKeyId();
    }
}

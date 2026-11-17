package com.choco.payment.application.support;

import com.choco.core.domain.Money;
import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;
import com.choco.payment.domain.port.PaymentGatewayPort;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A controllable in-memory payment provider.
 *
 * <p>Lets a test drive the three states that matter without a network: a provider order is created, a
 * payment is reported captured or failed, and a webhook is delivered. Signature checks are decided by
 * a settable flag so a test can assert that a bad signature is refused before any state changes.
 *
 * <p>Hand-written rather than mocked, so the use cases exercise the same interface the real Razorpay
 * adapter implements and a change to that interface breaks the fake at compile time.
 */
public final class FakePaymentGateway implements PaymentGatewayPort {

    private final Map<String, ProviderPayment> payments = new LinkedHashMap<>();
    private boolean checkoutSignatureValid = true;
    private boolean webhookSignatureValid = true;
    private boolean unreachable;
    private boolean refundSucceeds = true;
    private String publishableKey = "rzp_test_key";
    private int orderCounter = 0;
    private int refundCounter = 0;

    /** Registers a payment the next {@code fetchPayment} will report. */
    public void report(ProviderPayment payment) {
        payments.put(payment.id(), payment);
    }

    public void setCheckoutSignatureValid(boolean valid) {
        this.checkoutSignatureValid = valid;
    }

    public void setWebhookSignatureValid(boolean valid) {
        this.webhookSignatureValid = valid;
    }

    public void setUnreachable(boolean unreachable) {
        this.unreachable = unreachable;
    }

    public void setRefundSucceeds(boolean succeeds) {
        this.refundSucceeds = succeeds;
    }

    public void setPublishableKey(String publishableKey) {
        this.publishableKey = publishableKey;
    }

    @Override
    public ProviderOrder createOrder(Money amount, String receipt, Map<String, String> notes) {
        if (unreachable) {
            throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE, "provider unreachable");
        }
        orderCounter++;
        String id = "order_fake_" + orderCounter;
        return new ProviderOrder(id, amount.minorUnits(), amount.currency().getCurrencyCode(), "created");
    }

    @Override
    public ProviderPayment fetchPayment(String providerPaymentId) {
        if (unreachable) {
            throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE, "provider unreachable");
        }
        ProviderPayment payment = payments.get(providerPaymentId);
        if (payment == null) {
            throw new BusinessRuleException(ErrorCode.NOT_FOUND, "no such payment at the provider");
        }
        return payment;
    }

    @Override
    public boolean verifyCheckoutSignature(String providerOrderId, String providerPaymentId,
                                           String signature) {
        return checkoutSignatureValid;
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return webhookSignatureValid;
    }

    private WebhookEvent nextWebhookEvent;

    /** Sets the event the next {@code parseWebhook} returns. */
    public void setNextWebhookEvent(WebhookEvent event) {
        this.nextWebhookEvent = event;
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody) {
        return nextWebhookEvent;
    }

    @Override
    public ProviderRefund refund(String providerPaymentId, Money amount, Map<String, String> notes) {
        if (!refundSucceeds) {
            throw new BusinessRuleException(ErrorCode.DEPENDENCY_UNAVAILABLE, "refund declined");
        }
        refundCounter++;
        return new ProviderRefund("rfnd_fake_" + refundCounter, providerPaymentId, amount.minorUnits(),
                amount.currency().getCurrencyCode(), "processed");
    }

    @Override
    public String publishableKey() {
        return publishableKey;
    }
}

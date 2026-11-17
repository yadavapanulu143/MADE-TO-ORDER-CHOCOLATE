package com.choco.payment.adapter.out.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay credentials and endpoints, bound from configuration.
 *
 * <p>Bound through a {@code @ConfigurationProperties} class rather than {@code @Value} on purpose: a
 * list or a missing default under {@code @Value} silently resolves to nothing, and a payment key that
 * silently resolves to nothing is a service that takes no money. A bound class either holds the value
 * or fails to start.
 *
 * <p>The key secret and the webhook secret are read from the environment in a deployment. They are
 * never logged and never returned in a response; only the key id, which is public, is exposed.
 */
@ConfigurationProperties(prefix = "choco.payment.razorpay")
public class RazorpayProperties {

    /** The publishable key id, safe to send to the browser. */
    private String keyId = "";

    /** The account secret. Used to verify checkout signatures and to sign API calls. Never exposed. */
    private String keySecret = "";

    /** The webhook secret, which is separate from the key secret. Never exposed. */
    private String webhookSecret = "";

    /**
     * Whether real provider calls are enabled.
     *
     * <p>False by default so a developer running locally does not accidentally create live provider
     * orders. When false, the service still starts and the flows still work against a test double.
     */
    private boolean enabled = false;

    /** Overrides the SDK's base URL, for a sandbox or a wire-mock in a test. */
    private String baseUrl;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Whether the credentials needed to actually call the provider are present. */
    public boolean hasCredentials() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
}

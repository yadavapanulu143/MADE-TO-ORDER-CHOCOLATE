package com.choco.payment.adapter.out.gateway;

import com.choco.payment.domain.port.PaymentGatewayPort;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the payment-gateway implementation.
 *
 * <p>Two outcomes, decided by whether credentials are configured:
 * <ul>
 *   <li>credentials present — a real {@link RazorpayPaymentGateway} backed by the SDK;
 *   <li>credentials absent — a {@link UnconfiguredPaymentGateway} that starts the service but refuses
 *       to take money, with a message that says exactly what is missing.
 * </ul>
 * The service therefore boots in a developer's checkout and in CI without live keys, and it cannot
 * accidentally create a real provider order without a real key. It never falls back to a fake that
 * <em>pretends</em> a payment succeeded, which would be the dangerous alternative.
 */
@Configuration(proxyBeanMethods = false)
class PaymentGatewayConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayConfiguration.class);

    @Bean
    PaymentGatewayPort paymentGatewayPort(RazorpayProperties properties) {
        if (!properties.hasCredentials()) {
            log.warn("Razorpay credentials are not configured; the payment service will refuse to "
                    + "create provider orders until choco.payment.razorpay.key-id and key-secret are set");
            return new UnconfiguredPaymentGateway(properties);
        }
        try {
            RazorpayClient client = new RazorpayClient(properties.getKeyId(), properties.getKeySecret());
            log.info("Razorpay payment gateway configured with key id {}", mask(properties.getKeyId()));
            return new RazorpayPaymentGateway(client, properties);
        } catch (RazorpayException ex) {
            throw new IllegalStateException("Unable to initialise the Razorpay client", ex);
        }
    }

    /** Shows only the prefix of a key id, which is already public, in a startup log line. */
    private static String mask(String keyId) {
        if (keyId == null || keyId.length() < 8) {
            return "****";
        }
        return keyId.substring(0, 8) + "****";
    }
}

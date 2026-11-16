package com.choco.identity.application.port;

import com.choco.identity.domain.customer.VerificationChannel;

/**
 * Outbound port for delivering a verification code. The channel decides whether it goes out by
 * email or SMS, so the application layer stays transport-agnostic.
 */
public interface OtpDeliveryPort {

    void deliver(VerificationChannel channel, String destination, String code, String fullName);
}

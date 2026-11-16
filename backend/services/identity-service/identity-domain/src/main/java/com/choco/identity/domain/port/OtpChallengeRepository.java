package com.choco.identity.domain.port;

import com.choco.identity.domain.otp.OtpChallenge;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository {

    OtpChallenge save(OtpChallenge challenge);

    Optional<OtpChallenge> findById(UUID id);

    /** Most recent usable challenge for a customer and channel, if one exists. */
    Optional<OtpChallenge> findLatestActive(UUID customerId, String channel);
}

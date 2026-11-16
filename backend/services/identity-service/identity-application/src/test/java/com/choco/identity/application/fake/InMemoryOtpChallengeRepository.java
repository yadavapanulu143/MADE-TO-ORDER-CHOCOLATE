package com.choco.identity.application.fake;

import com.choco.identity.domain.otp.OtpChallenge;
import com.choco.identity.domain.port.OtpChallengeRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryOtpChallengeRepository implements OtpChallengeRepository {

    private final Map<UUID, OtpChallenge> byId = new LinkedHashMap<>();

    @Override
    public OtpChallenge save(OtpChallenge challenge) {
        byId.put(challenge.id(), challenge);
        return challenge;
    }

    @Override
    public Optional<OtpChallenge> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<OtpChallenge> findLatestActive(UUID customerId, String channel) {
        return byId.values().stream()
                .filter(c -> c.customerId().equals(customerId) && c.channel().equals(channel))
                .max(Comparator.comparing(OtpChallenge::createdAt));
    }
}

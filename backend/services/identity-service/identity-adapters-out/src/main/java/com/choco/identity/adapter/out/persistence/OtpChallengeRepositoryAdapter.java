package com.choco.identity.adapter.out.persistence;

import com.choco.identity.domain.otp.OtpChallenge;
import com.choco.identity.domain.port.OtpChallengeRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class OtpChallengeRepositoryAdapter implements OtpChallengeRepository {

    private final SpringDataOtpChallengeRepository delegate;

    OtpChallengeRepositoryAdapter(SpringDataOtpChallengeRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public OtpChallenge save(OtpChallenge challenge) {
        delegate.save(PersistenceMapper.toEntity(challenge));
        return challenge;
    }

    @Override
    public Optional<OtpChallenge> findById(UUID id) {
        return delegate.findById(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<OtpChallenge> findLatestActive(UUID customerId, String channel) {
        return delegate.findLatestActive(customerId, channel).map(PersistenceMapper::toDomain);
    }
}

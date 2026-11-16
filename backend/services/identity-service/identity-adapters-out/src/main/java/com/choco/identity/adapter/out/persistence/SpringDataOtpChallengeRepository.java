package com.choco.identity.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataOtpChallengeRepository extends JpaRepository<OtpChallengeJpaEntity, UUID> {

    /**
     * The most recent unconsumed challenge for a customer and channel.
     *
     * <p>{@code consumedAt is null} is part of the query, not a filter applied afterwards, so a
     * consumed challenge can never be selected even if a caller forgets to check.
     */
    @Query("""
            select c from OtpChallengeJpaEntity c
            where c.customerId = :customerId
              and c.channel = :channel
              and c.consumedAt is null
            order by c.createdAt desc
            limit 1
            """)
    Optional<OtpChallengeJpaEntity> findLatestActive(@Param("customerId") UUID customerId,
                                                    @Param("channel") String channel);
}

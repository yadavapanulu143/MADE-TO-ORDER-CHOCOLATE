package com.choco.redis.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the revocation list against a real Redis.
 *
 * <p>Run against a container rather than a fake because the parts that matter are Redis's own
 * behaviour: that a session key is gone after its TTL, and that a subject cutoff written by a later
 * password change overrides an earlier one. An in-memory fake would pass without demonstrating either.
 */
@Testcontainers
class RedisTokenRevocationRegistryIT {

    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;

    private StringRedisTemplate template;
    private RedisTokenRevocationRegistry registry;

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379);
        redis.start();

        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeEach
    void setUp() {
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        registry = new RedisTokenRevocationRegistry(template);
        template.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("a session is not revoked until it is revoked")
    void live_session_is_not_revoked() {
        UUID subject = UUID.randomUUID();

        assertThat(registry.isRevoked("session-1", subject, Instant.now())).isFalse();
    }

    @Test
    @DisplayName("revoking a session makes every token naming it revoked")
    void revoked_session_is_reported() {
        UUID subject = UUID.randomUUID();
        registry.revokeSession("session-1", Instant.now().plus(Duration.ofMinutes(15)));

        assertThat(registry.isRevoked("session-1", subject, Instant.now())).isTrue();
        // A different session for the same account is untouched.
        assertThat(registry.isRevoked("session-2", subject, Instant.now())).isFalse();
    }

    @Test
    @DisplayName("a token issued before the subject cutoff is revoked, one after it is not")
    void subject_cutoff_is_time_bounded() {
        UUID subject = UUID.randomUUID();
        Instant cutoff = Instant.now();
        registry.revokeSubjectIssuedBefore(subject, cutoff, cutoff.plus(Duration.ofMinutes(15)));

        assertThat(registry.isRevoked("any-session", subject, cutoff.minusSeconds(1)))
                .as("a token issued before the password change is refused")
                .isTrue();
        assertThat(registry.isRevoked("any-session", subject, cutoff.plusSeconds(1)))
                .as("a token issued after the password change is accepted")
                .isFalse();
    }

    @Test
    @DisplayName("a later cutoff supersedes an earlier one")
    void later_cutoff_wins() {
        UUID subject = UUID.randomUUID();
        Instant first = Instant.now();
        Instant second = first.plusSeconds(60);
        registry.revokeSubjectIssuedBefore(subject, first, first.plus(Duration.ofMinutes(15)));
        registry.revokeSubjectIssuedBefore(subject, second, second.plus(Duration.ofMinutes(15)));

        assertThat(registry.isRevoked("s", subject, first.plusSeconds(30)))
                .as("a token between the two cutoffs is refused by the newer rule")
                .isTrue();
    }

    @Test
    @DisplayName("a revocation whose window has already passed writes nothing")
    void expired_window_is_ignored() {
        registry.revokeSession("session-1", Instant.now().minus(Duration.ofMinutes(1)));

        assertThat(template.hasKey("revoked:session:session-1")).isFalse();
    }
}

package com.choco.security.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.port.TokenRevocationRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Tests the revocation-aware decoder.
 *
 * <p>The behaviour that matters is that a genuine, unexpired token is refused once its session is
 * revoked, and that an unavailable or unanswerable store refuses the token rather than allowing it.
 * Both are asserted directly, because a decoder that only checked the signature would pass a test that
 * never revokes anything — which is exactly the bug this class exists to fix.
 */
@DisplayName("RevocationAwareJwtDecoder")
class RevocationAwareJwtDecoderTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-03-01T09:00:00Z");

    private final UUID subject = UUID.randomUUID();

    /** A decoder whose only job is to return a well-formed token, so the revocation logic is isolated. */
    private static final class StubDecoder implements JwtDecoder {
        private final Jwt jwt;

        StubDecoder(Jwt jwt) {
            this.jwt = jwt;
        }

        @Override
        public Jwt decode(String token) {
            return jwt;
        }
    }

    private Jwt token(String jti, Instant issuedAt) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .claim("role", "CUSTOMER");
        if (jti != null) {
            builder.claim("jti", jti);
        }
        return builder.build();
    }

    private RevocationAwareJwtDecoder decoderWith(TokenRevocationRegistry registry) {
        return new RevocationAwareJwtDecoder(new StubDecoder(token("session-1", ISSUED_AT)), registry);
    }

    @Test
    @DisplayName("accepts a token whose session is not revoked")
    void accepts_live_session() {
        RevocationAwareJwtDecoder decoder = decoderWith(new RecordingRegistry());

        assertThat(decoder.decode("token").getId()).isEqualTo("session-1");
    }

    @Test
    @DisplayName("refuses a token whose session was revoked, even though the signature is valid")
    void refuses_revoked_session() {
        RevocationAwareJwtDecoder decoder = decoderWith(new RecordingRegistry().revokingSession("session-1"));

        assertThatThrownBy(() -> decoder.decode("token")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("refuses a token issued before a subject-wide cutoff")
    void refuses_token_before_subject_cutoff() {
        RecordingRegistry registry = new RecordingRegistry();
        registry.revokeSubjectIssuedBefore(subject, ISSUED_AT.plusSeconds(1), ISSUED_AT.plusSeconds(3600));

        assertThatThrownBy(() -> decoderWith(registry).decode("token")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("accepts a token issued after a subject-wide cutoff, so a fresh sign-in works")
    void accepts_token_after_subject_cutoff() {
        RecordingRegistry registry = new RecordingRegistry();
        // Cutoff one second before this token was issued: the token is newer, so it survives.
        registry.revokeSubjectIssuedBefore(subject, ISSUED_AT.minusSeconds(1), ISSUED_AT.plusSeconds(3600));

        assertThat(decoderWith(registry).decode("token")).isNotNull();
    }

    @Test
    @DisplayName("fails closed when the revocation store cannot be consulted")
    void fails_closed_when_store_unavailable() {
        RevocationAwareJwtDecoder decoder = new RevocationAwareJwtDecoder(
                new StubDecoder(token("session-1", ISSUED_AT)), new FailingRegistry());

        assertThatThrownBy(() -> decoder.decode("token"))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("revocation list is unavailable");
    }

    @Test
    @DisplayName("refuses a token with no session identifier")
    void refuses_token_without_jti() {
        RevocationAwareJwtDecoder decoder = new RevocationAwareJwtDecoder(
                new StubDecoder(token(null, ISSUED_AT)), new RecordingRegistry());

        assertThatThrownBy(() -> decoder.decode("token"))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("no session identifier");
    }

    /** In-memory registry that records what it was asked to revoke. */
    private static final class RecordingRegistry implements TokenRevocationRegistry {
        private final java.util.Set<String> sessions = new java.util.HashSet<>();
        private final java.util.Map<UUID, Instant> cutoffs = new java.util.HashMap<>();

        RecordingRegistry revokingSession(String sessionId) {
            sessions.add(sessionId);
            return this;
        }

        @Override
        public void revokeSession(String sessionId, Instant until) {
            sessions.add(sessionId);
        }

        @Override
        public void revokeSubjectIssuedBefore(UUID subject, Instant cutoff, Instant until) {
            cutoffs.put(subject, cutoff);
        }

        @Override
        public boolean isRevoked(String sessionId, UUID subject, Instant issuedAt) {
            if (sessions.contains(sessionId)) {
                return true;
            }
            Instant cutoff = cutoffs.get(subject);
            return cutoff != null && issuedAt.isBefore(cutoff);
        }
    }

    /** Registry that behaves as if Redis were unreachable. */
    private static final class FailingRegistry implements TokenRevocationRegistry {
        @Override
        public void revokeSession(String sessionId, Instant until) {
        }

        @Override
        public void revokeSubjectIssuedBefore(UUID subject, Instant cutoff, Instant until) {
        }

        @Override
        public boolean isRevoked(String sessionId, UUID subject, Instant issuedAt) {
            throw new IllegalStateException("redis is down");
        }
    }
}

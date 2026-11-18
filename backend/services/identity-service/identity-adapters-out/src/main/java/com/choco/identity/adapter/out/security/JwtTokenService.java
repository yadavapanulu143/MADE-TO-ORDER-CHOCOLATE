package com.choco.identity.adapter.out.security;

import com.choco.identity.application.port.IssuedToken;
import com.choco.identity.application.port.TokenService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.Roles;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues RS256 access tokens and opaque refresh token values.
 *
 * <p>Asymmetric signing is used so that only this service holds the private key. Every other service
 * verifies tokens against the published JWKS and none of them can mint one; a compromised order
 * service therefore cannot fabricate an admin token.
 *
 * <p>The access token carries the verification flags. Downstream services check
 * {@code email_verified} and {@code mobile_verified} rather than calling back to identity on every
 * request, which keeps the checkout path free of a synchronous dependency while still refusing an
 * unverified customer.
 *
 * <p>Refresh tokens are opaque random strings, not JWTs. There is nothing to verify in them: the
 * server looks the hash up in the database, which is what makes revocation and replay detection
 * possible. A self-contained JWT refresh token could not be revoked before its expiry.
 */
@Component
class JwtTokenService implements TokenService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final SecureRandom random = new SecureRandom();

    JwtTokenService(JwtEncoder jwtEncoder,
                    @Value("${choco.security.issuer:choco-identity}") String issuer,
                    @Value("${choco.security.access-token-ttl:PT15M}") Duration accessTokenTtl) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
    }

    @Override
    public IssuedToken issueAccessToken(AuthenticatedUser user, String sessionId, Instant now) {
        Instant expiresAt = now.plus(accessTokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(sessionId)
                .subject(user.userId().toString())
                .claim("email", user.email())
                .claim("role", user.role())
                .claim("email_verified", user.emailVerified())
                .claim("mobile_verified", user.mobileVerified())
                .claim("permissions", user.permissions())
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    @Override
    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    @Override
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes a refresh token for storage.
     *
     * <p>SHA-256 is appropriate here, unlike for a password: the value is 48 bytes of
     * cryptographically random data, so there is no dictionary to attack and a slow hash would only
     * add latency to every refresh.
     */
    @Override
    public String hashRefreshToken(String tokenValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    static String customerRole() {
        return Roles.CUSTOMER;
    }
}

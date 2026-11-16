package com.choco.identity.adapter.out.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Provides the RSA key pair used to sign access tokens and the matching JWKS document.
 *
 * <p>The key is loaded from {@code choco.security.jwk.location} when that property points at a
 * readable PEM file, so a real deployment supplies a stable key and does not invalidate every
 * session on restart. When no key is configured, a key is generated at startup and a warning is
 * logged.
 *
 * <p>Generating on startup is a development convenience only, and the warning says so: a new key on
 * every restart invalidates outstanding access tokens and, worse, would silently break every
 * service verifying against a stale JWKS if the endpoints were not refreshed. Production must
 * supply a key.
 */
@Configuration
public class JwkConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwkConfiguration.class);

    /** A stable key id so rotating the key is an explicit, visible act rather than an accident. */
    private static final String KEY_ID = "choco-identity-1";

    @Bean
    RSAKey rsaKey(@Value("${choco.security.jwk.location:}") String location) {
        if (location != null && !location.isBlank()) {
            Path path = Path.of(location);
            if (Files.isReadable(path)) {
                try {
                    RSAKey key = loadFromPem(path);
                    log.info("Loaded RSA signing key from {} (kid={})", path, key.getKeyID());
                    return key;
                } catch (IOException | java.security.GeneralSecurityException | ParseException e) {
                    throw new IllegalStateException(
                            "Unable to read the configured RSA signing key at " + path
                                    + ". Provide a valid PKCS#8 private key in PEM form.", e);
                }
            }
            log.warn("choco.security.jwk.location={} is set but not readable; falling back to a "
                    + "generated key. Access tokens will not survive a restart.", location);
        } else {
            log.warn("No RSA signing key configured. Generating an ephemeral key for development. "
                    + "Access tokens will be invalidated on every restart and other services will "
                    + "fail to verify tokens until they refresh the JWKS. Set "
                    + "choco.security.jwk.location for any non-development environment.");
        }
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException("Unable to generate an RSA signing key", e);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    /** The public half only: this is what every other service fetches to verify a token. */
    @Bean
    JWKSet jwkSet(RSAKey rsaKey) {
        return new JWKSet(rsaKey.toPublicJWK());
    }

    private RSAKey loadFromPem(Path path) throws IOException, java.security.GeneralSecurityException, ParseException {
        String pem = Files.readString(path, StandardCharsets.UTF_8);
        RSAPrivateKey privateKey = readPrivateKey(pem);
        RSAPublicKey publicKey = readPublicKey(pem);
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(KEY_ID)
                .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .build();
    }

    private RSAPrivateKey readPrivateKey(String pem) throws java.security.GeneralSecurityException {
        String body = extractPem(pem, "PRIVATE KEY");
        byte[] der = Base64.getMimeDecoder().decode(body);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPublicKey readPublicKey(String pem) throws java.security.GeneralSecurityException {
        String body = extractPem(pem, "PUBLIC KEY");
        byte[] der = Base64.getMimeDecoder().decode(body);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    private String extractPem(String pem, String type) {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int from = pem.indexOf(begin);
        int to = pem.indexOf(end);
        if (from < 0 || to < 0) {
            throw new IllegalArgumentException("PEM does not contain a " + type + " block");
        }
        return pem.substring(from + begin.length(), to).replaceAll("\\s", "");
    }

    /** Exposed for tests and for tooling that needs to know the current key id. */
    static String keyId() {
        return KEY_ID;
    }

    static String randomKeyId() {
        return UUID.randomUUID().toString();
    }
}

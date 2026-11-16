package com.choco.identity.adapter.in.web;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the public signing keys.
 *
 * <p>This is the endpoint every other service points at to verify tokens. Only public key material
 * is exposed: the private key never leaves this service, which is the whole reason RS256 is used
 * instead of a shared symmetric secret.
 *
 * <p>The document is served at the conventional {@code /.well-known/jwks.json} path so a standard
 * JWT library can consume it without custom configuration.
 */
@RestController
class JwksController {

    private final JWKSet jwkSet;

    JwksController(JWKSet jwkSet) {
        this.jwkSet = jwkSet;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> keys() {
        return jwkSet.toPublicJWKSet().toJSONObject();
    }
}

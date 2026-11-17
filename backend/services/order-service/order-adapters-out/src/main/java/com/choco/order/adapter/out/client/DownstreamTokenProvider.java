package com.choco.order.adapter.out.client;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Supplies the bearer token to attach to an outbound service call.
 *
 * <p>The order service forwards the <em>caller's own</em> token rather than holding a privileged
 * service credential. That is deliberate: the catalogue and identity services then apply their own
 * authorization to the call exactly as they would to the same call from the browser, so a bug in this
 * service cannot be escalated into reading another customer's data. There is no shared service account
 * to leak or to over-grant.
 */
@Component
public class DownstreamTokenProvider {

    /** The current request's bearer token, or empty when there is no authenticated caller. */
    public Optional<String> currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.of(jwtAuthentication.getToken().getTokenValue());
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt.getTokenValue());
        }
        return Optional.empty();
    }
}

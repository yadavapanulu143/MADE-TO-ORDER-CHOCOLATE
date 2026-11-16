package com.choco.web.client;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

/**
 * Works out the address of the client that made a request.
 *
 * <p><b>Why this is not a one-line method.</b> {@code X-Forwarded-For} is supplied by the client. It
 * is only meaningful when the request arrived through a proxy that overwrote it, and a proxy only
 * overwrites it if it is configured to. Reading the header unconditionally means any caller can send
 * {@code X-Forwarded-For: 1.2.3.4} and choose their own address — harmless while the value only
 * decorates an audit row, and a bypass the moment it feeds a rate limit.
 *
 * <p>This class feeds rate limiting, so the header is trusted only when the request's immediate peer
 * is one of the configured trusted proxies. With no proxies configured — the default — the header is
 * ignored entirely and the socket address is used. A deployment behind a load balancer opts in by
 * listing that balancer's address, which is an explicit act rather than a default.
 *
 * <p>The rightmost value in the chain is the one added by the trusted proxy and therefore the only one
 * it can vouch for. Left-hand entries were supplied by whoever sent the request to the proxy and are
 * not trusted; taking the leftmost value, as is commonly done, hands the choice to the attacker.
 */
public class ClientIpResolver {

    private final List<String> trustedProxies;

    public ClientIpResolver(@Value("${choco.http.trusted-proxies:}") List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? List.of() : trustedProxies;
    }

    /** Returns the client address to use for rate limiting and auditing. */
    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();

        if (trustedProxies.isEmpty() || !isTrusted(peer)) {
            return peer;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }

        // The proxy appends the address it saw to the right of any the client sent, so the last entry
        // is the one the trusted proxy added.
        String[] hops = forwarded.split(",");
        String rightmost = hops[hops.length - 1].trim();
        return rightmost.isEmpty() ? peer : rightmost;
    }

    private boolean isTrusted(String address) {
        return address != null && trustedProxies.stream().anyMatch(address::equals);
    }
}

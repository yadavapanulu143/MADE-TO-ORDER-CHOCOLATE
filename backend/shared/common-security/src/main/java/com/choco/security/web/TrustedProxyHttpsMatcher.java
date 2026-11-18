package com.choco.security.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Decides whether a request reached the application over HTTPS, so HSTS is emitted only when it did.
 *
 * <p><b>The problem this solves.</b> Spring Security's default HSTS writer uses a matcher that trusts
 * {@code request.isSecure()} only, and behind a TLS-terminating reverse proxy every request arrives
 * over plain HTTP, so {@code isSecure()} is false and HSTS is never sent. The usual remedy is to turn
 * on {@code server.forward-headers-strategy=framework}, which rewrites {@code remoteAddr} from the
 * client-supplied {@code X-Forwarded-For} header. That would undo the deliberate rule in
 * {@code com.choco.web.client.ClientIpResolver}: a forwarded address is trusted only when the
 * immediate peer is a configured proxy, because the header is otherwise attacker-chosen and feeds
 * rate limiting. This matcher keeps the same rule for {@code X-Forwarded-Proto}.
 *
 * <p><b>How it decides.</b> A request is HTTPS when either the servlet container already reports it
 * as secure (a direct TLS listener, or {@code forward-headers-strategy} configured elsewhere), or the
 * immediate peer is a trusted proxy and that proxy says the client's scheme was {@code https}. The
 * header is read only from a trusted peer, so a client that is not behind the proxy cannot make the
 * application emit HSTS by forging the header — and, just as important, cannot make it suppress HSTS
 * by omitting one.
 *
 * <p>Trusted proxies are configured by the same {@code choco.http.trusted-proxies} property
 * {@code ClientIpResolver} uses, so the address rule and the scheme rule cannot drift apart.
 */
public final class TrustedProxyHttpsMatcher implements RequestMatcher {

    private final List<String> trustedProxies;

    public TrustedProxyHttpsMatcher(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? List.of() : trustedProxies;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        if (trustedProxies.isEmpty()) {
            return false;
        }
        String peer = request.getRemoteAddr();
        if (peer == null || trustedProxies.stream().noneMatch(peer::equals)) {
            return false;
        }
        // The trusted proxy is the immediate peer, so its own X-Forwarded-Proto is the one it saw and
        // set; there is no chain to walk as there is for the client address.
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto.trim());
    }
}

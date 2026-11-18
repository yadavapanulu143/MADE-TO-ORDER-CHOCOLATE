package com.choco.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The HSTS matcher decides whether to send {@code Strict-Transport-Security}. Both a false negative
 * (the header is never sent in production, so the protection silently does nothing) and a false
 * positive (HSTS is sent on a plain-HTTP development host, so a browser pins a scheme the host does
 * not serve) are real problems, so the boundary is pinned here.
 */
class TrustedProxyHttpsMatcherTest {

    @Test
    @DisplayName("a directly secure request is HTTPS with no proxy configured")
    void secureRequestIsHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);

        assertThat(new TrustedProxyHttpsMatcher(List.of()).matches(request)).isTrue();
    }

    @Test
    @DisplayName("a plain request with no trusted proxy is not HTTPS, and a forged header does not change that")
    void plainRequestWithoutTrustedProxyIsNotHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-Proto", "https");

        assertThat(new TrustedProxyHttpsMatcher(List.of()).matches(request)).isFalse();
    }

    @Test
    @DisplayName("a trusted proxy saying https makes the request HTTPS")
    void trustedProxySayingHttpsIsHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-Proto", "https");

        assertThat(new TrustedProxyHttpsMatcher(List.of("10.0.0.5")).matches(request)).isTrue();
    }

    @Test
    @DisplayName("a trusted proxy saying http is not HTTPS")
    void trustedProxySayingHttpIsNotHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-Proto", "http");

        assertThat(new TrustedProxyHttpsMatcher(List.of("10.0.0.5")).matches(request)).isFalse();
    }

    @Test
    @DisplayName("an untrusted peer claiming https is ignored even when a proxy is configured")
    void untrustedPeerCannotClaimHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-Proto", "https");

        assertThat(new TrustedProxyHttpsMatcher(List.of("10.0.0.5")).matches(request)).isFalse();
    }

    @Test
    @DisplayName("a trusted proxy with no scheme header is not HTTPS")
    void trustedProxyWithoutHeaderIsNotHttps() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");

        assertThat(new TrustedProxyHttpsMatcher(List.of("10.0.0.5")).matches(request)).isFalse();
    }

    @Test
    @DisplayName("the scheme comparison is case-insensitive and tolerates surrounding whitespace")
    void schemeComparisonIsLenient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-Proto", "  HTTPS  ");

        assertThat(new TrustedProxyHttpsMatcher(List.of("10.0.0.5")).matches(request)).isTrue();
    }

    @Test
    @DisplayName("a null proxy list behaves as empty rather than throwing")
    void nullProxyListIsTreatedAsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-Proto", "https");

        assertThat(new TrustedProxyHttpsMatcher(null).matches(request)).isFalse();
    }
}

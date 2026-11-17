package com.choco.order.adapter.out.client;

import com.choco.core.error.DomainException;
import com.choco.core.error.ErrorCode;
import java.net.http.HttpClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared support for the synchronous clients to other services.
 *
 * <p>Two rules are applied here so no client re-implements them, and applies them differently:
 * <ul>
 *   <li>A downstream {@code 4xx} is a legitimate answer — the customer or address genuinely is not
 *       there — so it is translated by the caller into an empty result or a business error, not a
 *       crash.</li>
 *   <li>A downstream {@code 5xx}, a timeout, or a refused connection is the platform being unable to
 *       answer. That becomes a {@link ErrorCode#DEPENDENCY_UNAVAILABLE} failure with no internal
 *       detail leaked, rather than a half-completed checkout or a stack trace on the storefront.</li>
 * </ul>
 */
final class DownstreamClients {

    private DownstreamClients() {
    }

    /**
     * Builds a {@link RestClient} for one downstream service.
     *
     * <p>The timeouts are the point. Checkout is synchronous, so a service that accepts a connection
     * and then never answers must fail within a bounded time instead of pinning the request thread.
     * Both timeouts are set: a connect timeout alone leaves the read hanging, and a read timeout alone
     * leaves a connect to an unreachable host hanging.
     */
    static RestClient build(java.net.URI baseUrl, java.time.Duration connectTimeout,
                            java.time.Duration readTimeout, DownstreamTokenProvider tokens) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    // Forward the caller's own token, so the downstream service authorizes the call
                    // exactly as it would the same call from the browser. The order service holds no
                    // privileged credential of its own.
                    tokens.currentToken().ifPresent(token ->
                            request.getHeaders().setBearerAuth(token));
                    return execution.execute(request, body);
                })
                .build();
    }

    /** Runs a downstream call, mapping a transport or 5xx failure to a clear dependency error. */
    static <T> T call(String description, java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw e;
            }
            throw unwrap(e, description);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw unwrap(e, description);
        }
    }

    /** True when the exception is a downstream "not found" rather than a real failure. */
    static boolean isNotFound(RestClientResponseException e) {
        return e.getStatusCode() == HttpStatusCode.valueOf(404);
    }

    /**
     * Maps a transport or {@code 5xx} failure to a {@link ErrorCode#DEPENDENCY_UNAVAILABLE} error.
     *
     * <p>The downstream message is deliberately not carried through: an internal error body can name
     * hosts, queries or stack frames, and none of that belongs in a storefront response.
     */
    static DomainException unwrap(RuntimeException cause, String description) {
        return new DomainException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                "The " + description + " service is temporarily unavailable. Please retry shortly.");
    }
}

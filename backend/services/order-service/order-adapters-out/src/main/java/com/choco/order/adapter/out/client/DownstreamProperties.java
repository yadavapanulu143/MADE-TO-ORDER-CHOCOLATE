package com.choco.order.adapter.out.client;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Addresses of the services the order service calls synchronously.
 *
 * <p>Bound as a typed object rather than read with {@code @Value} so a missing value fails loudly at
 * startup instead of resolving to a blank host at the first checkout. The default is the local
 * development address of each service, matching {@code docs/architecture.md}.
 */
@ConfigurationProperties(prefix = "choco.order.downstream")
public class DownstreamProperties {

    /** Catalogue service, for the current price and availability of a variant. */
    private URI catalog = URI.create("http://localhost:8082");

    /** Identity service, for a customer's contact facts and one of their addresses. */
    private URI identity = URI.create("http://localhost:8081");

    /** Fulfilment service, for PIN serviceability and the delivery charge. */
    private URI fulfilment = URI.create("http://localhost:8085");

    /**
     * How long to wait for a downstream response.
     *
     * <p>Checkout is synchronous, so a downstream service that never answers must fail the checkout
     * with a clear error rather than pin the request thread indefinitely.
     */
    private java.time.Duration readTimeout = java.time.Duration.ofSeconds(5);

    /** How long to wait for a downstream connection to be established. */
    private java.time.Duration connectTimeout = java.time.Duration.ofSeconds(2);

    public URI getCatalog() {
        return catalog;
    }

    public void setCatalog(URI catalog) {
        this.catalog = catalog;
    }

    public URI getIdentity() {
        return identity;
    }

    public void setIdentity(URI identity) {
        this.identity = identity;
    }

    public URI getFulfilment() {
        return fulfilment;
    }

    public void setFulfilment(URI fulfilment) {
        this.fulfilment = fulfilment;
    }

    public java.time.Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(java.time.Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public java.time.Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(java.time.Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}

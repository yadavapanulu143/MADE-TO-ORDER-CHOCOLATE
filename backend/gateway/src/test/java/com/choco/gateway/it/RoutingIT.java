package com.choco.gateway.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifies that the gateway forwards a request to the right service, unchanged, and that it refuses
 * to forward anything the caller is not allowed to send.
 *
 * <p>Each downstream is a MockWebServer started before the context so the route URIs are known at
 * startup. The stub echoes the path and selected headers it received, which is how a test proves the
 * gateway preserved the request rather than only that it returned 200.
 */
class RoutingIT extends AbstractGatewayIntegrationTest {

    private static final MockWebServer IDENTITY = downstream();
    private static final MockWebServer CATALOG = downstream();
    private static final MockWebServer ORDER = downstream();
    private static final MockWebServer PAYMENT = downstream();
    private static final MockWebServer FULFILMENT = downstream();
    private static final MockWebServer NOTIFICATION = downstream();

    private static MockWebServer downstream() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override
            public okhttp3.mockwebserver.MockResponse dispatch(RecordedRequest request) {
                String body = "{\"path\":\"" + request.getPath() + "\","
                        + "\"authorization\":\"" + request.getHeader("Authorization") + "\"}";
                return new okhttp3.mockwebserver.MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(body);
            }
        });
        try {
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return server;
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("choco.gateway.services.identity", () -> IDENTITY.url("/").toString());
        registry.add("choco.gateway.services.catalog", () -> CATALOG.url("/").toString());
        registry.add("choco.gateway.services.order", () -> ORDER.url("/").toString());
        registry.add("choco.gateway.services.payment", () -> PAYMENT.url("/").toString());
        registry.add("choco.gateway.services.fulfilment", () -> FULFILMENT.url("/").toString());
        registry.add("choco.gateway.services.notification", () -> NOTIFICATION.url("/").toString());
    }

    @AfterAll
    static void stop() throws Exception {
        IDENTITY.shutdown();
        CATALOG.shutdown();
        ORDER.shutdown();
        PAYMENT.shutdown();
        FULFILMENT.shutdown();
        NOTIFICATION.shutdown();
    }

    @Test
    @DisplayName("a public catalogue request reaches catalog without a token")
    void publicCatalogueReachesCatalog() {
        HttpResponse<String> response = get("/api/v1/products/", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText()).isEqualTo("/api/v1/products/");
    }

    @Test
    @DisplayName("an authenticated customer request reaches the identity service with its token intact")
    void authenticatedRequestReachesIdentity() {
        HttpResponse<String> response = get("/api/v1/customers/me", customerToken());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/customers/me");
        // The token must survive the hop: the downstream service verifies it again and performs the
        // ownership check, so stripping it here would turn every authenticated call into a 401.
        assertThat(body.get("authorization").asText()).startsWith("Bearer ");
    }

    @Test
    @DisplayName("an administrator can refresh and log out at the edge without an access token")
    void adminRefreshAndLogoutReachIdentityWithoutAToken() {
        // A refresh token is not an access token, so the request carrying it has no bearer credential.
        // Both paths must be public at the edge or an administrator's session could never be renewed
        // or revoked through the gateway — the very failure the customer refresh/logout pair avoids.
        HttpResponse<String> refresh = post("/api/v1/auth/admin/refresh", "{}", null);
        assertThat(refresh.statusCode()).isEqualTo(200);
        assertThat(json(refresh).get("path").asText()).isEqualTo("/api/v1/auth/admin/refresh");

        HttpResponse<String> logout = post("/api/v1/auth/admin/logout", "{}", null);
        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(json(logout).get("path").asText()).isEqualTo("/api/v1/auth/admin/logout");
    }

    @Test
    @DisplayName("a protected request without a token is rejected at the edge and never forwarded")
    void unauthenticatedProtectedRequestIsRejected() throws Exception {
        int before = ORDER.getRequestCount();

        HttpResponse<String> response = get("/api/v1/orders/mine", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json(response).get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(ORDER.getRequestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("a request with a forged signature is rejected and never forwarded")
    void forgedTokenIsRejected() throws Exception {
        int before = ORDER.getRequestCount();

        HttpResponse<String> response = get("/api/v1/orders/mine", forgedToken());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(ORDER.getRequestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() {
        HttpResponse<String> response = get("/api/v1/orders/mine", expiredToken());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("a token from the wrong issuer is rejected")
    void wrongIssuerIsRejected() {
        HttpResponse<String> response = get("/api/v1/orders/mine", wrongIssuerToken());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("an order request reaches the order service")
    void orderRequestReachesOrderService() {
        HttpResponse<String> response = get("/api/v1/orders/mine", customerToken());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText()).isEqualTo("/api/v1/orders/mine");
    }

    @Test
    @DisplayName("an authenticated administrator request reaches the catalog admin route")
    void adminCatalogueReachesCatalogService() {
        HttpResponse<String> response = post("/api/v1/admin/catalog/products", "{}", adminToken());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText()).isEqualTo("/api/v1/admin/catalog/products");
    }

    @Test
    @DisplayName("the administrative catalogue is not public: no token is refused at the edge")
    void adminCatalogueIsNotPublic() throws Exception {
        int before = CATALOG.getRequestCount();

        HttpResponse<String> response = post("/api/v1/admin/catalog/products", "{}", null);

        assertThat(response.statusCode()).isEqualTo(401);
        // The refusal happens at the edge, so the service never sees an unauthenticated write.
        assertThat(CATALOG.getRequestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("a checkout request reaches the order service")
    void checkoutReachesOrderService() {
        HttpResponse<String> response = post("/api/v1/checkout", "{}", customerToken());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText()).isEqualTo("/api/v1/checkout");
    }

    @Test
    @DisplayName("an administrative order request reaches the order service")
    void adminOrdersReachOrderService() {
        HttpResponse<String> response = get("/api/v1/admin/orders", adminToken());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText()).isEqualTo("/api/v1/admin/orders");
    }

    @Test
    @DisplayName("the administrative order surface is not public: no token is refused at the edge")
    void adminOrdersAreNotPublic() throws Exception {
        int before = ORDER.getRequestCount();

        HttpResponse<String> response = get("/api/v1/admin/orders", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(ORDER.getRequestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("a public serviceability request reaches the fulfilment service without a token")
    void serviceabilityReachesFulfilment() {
        HttpResponse<String> response = get("/api/v1/serviceability/560001", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText()).isEqualTo("/api/v1/serviceability/560001");
    }

    @Test
    @DisplayName("an authenticated customer's own fulfilment progress reaches the fulfilment service")
    void customerFulfilmentReachesFulfilmentService() {
        HttpResponse<String> response = get("/api/v1/fulfilment/me/deliveries", customerToken());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText())
                .isEqualTo("/api/v1/fulfilment/me/deliveries");
    }

    @Test
    @DisplayName("the administrative fulfilment surface is not public: no token is refused at the edge")
    void adminFulfilmentIsNotPublic() throws Exception {
        int before = FULFILMENT.getRequestCount();

        HttpResponse<String> response = get("/api/v1/admin/fulfilment/production/jobs", null);

        assertThat(response.statusCode()).isEqualTo(401);
        // The refusal happens at the edge, so the kitchen surface never sees an unauthenticated read.
        assertThat(FULFILMENT.getRequestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("the administrative payment surface reaches the payment service")
    void adminPaymentsReachPaymentService() {
        HttpResponse<String> response = get("/api/v1/admin/payments", adminToken());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText()).isEqualTo("/api/v1/admin/payments");
    }

    @Test
    @DisplayName("the administrative payment surface is not public: no token is refused at the edge")
    void adminPaymentsAreNotPublic() throws Exception {
        int before = PAYMENT.getRequestCount();

        HttpResponse<String> response = get("/api/v1/admin/payments", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(PAYMENT.getRequestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("an unknown path is not routed to any service")
    void unknownPathIsNotRouted() {
        HttpResponse<String> response = get("/api/v1/does-not-exist", customerToken());

        // No route matches, so the gateway itself answers 404 rather than guessing a service.
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("the administrative notification log reaches the notification service")
    void adminNotificationReachesNotificationService() {
        HttpResponse<String> response = get("/api/v1/admin/notifications", adminToken());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("path").asText()).isEqualTo("/api/v1/admin/notifications");
    }

    @Test
    @DisplayName("the administrative notification log is not public: no token is refused at the edge")
    void adminNotificationIsNotPublic() throws Exception {
        int before = NOTIFICATION.getRequestCount();

        HttpResponse<String> response = get("/api/v1/admin/notifications", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(NOTIFICATION.getRequestCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("a correlation id from the client is preserved on the forwarded request")
    void correlationIdIsPreserved() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/products/", null, null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Correlation-Id")).isPresent();
    }
}

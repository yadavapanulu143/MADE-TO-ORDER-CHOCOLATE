package com.choco.order.adapter.out.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.domain.Money;
import com.choco.core.domain.Pincode;
import com.choco.core.error.DomainException;
import com.choco.core.error.ErrorCode;
import com.choco.order.application.port.CatalogPort;
import com.choco.order.application.port.CustomerPort;
import com.choco.order.application.port.PaymentPort;
import com.choco.order.application.port.ServiceabilityPort;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the outbound clients against a real HTTP server on a loopback port.
 *
 * <p>These are the only classes in the order service that speak a wire contract, so they are the only
 * place where the exact request and the exact reaction to a response can go wrong. A mocked transport
 * would assert that the client called a method; a real server asserts what the client actually sent on
 * the wire, and lets the awkward answers — a 404, a 503 — be produced on demand.
 */
@DisplayName("Outbound service clients")
class OutboundClientTest {

    private MockWebServer server;
    private DownstreamProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        properties = new DownstreamProperties();
        properties.setCatalog(URI.create(server.url("/").toString()));
        properties.setIdentity(URI.create(server.url("/").toString()));
        properties.setFulfilment(URI.create(server.url("/").toString()));
        properties.setPayment(URI.create(server.url("/").toString()));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /** A provider with no authenticated caller: the clients must still build a request. */
    private DownstreamTokenProvider noToken() {
        return new DownstreamTokenProvider();
    }

    // ---------------------------------------------------------------------------------------------
    // Catalogue
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("resolves a variant to its price and availability")
    void catalog_resolves_a_variant() throws Exception {
        UUID variantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"%s": {"variantId": "%s", "productId": "%s", "productName": "Dark Bar",
                         "variantLabel": "200 g", "sku": "SKU-1", "priceMinorUnits": 45000,
                         "currency": "INR", "minOrderQuantity": 1, "maxOrderQuantity": 10,
                         "variantActive": true, "productAvailable": true}}
                        """.formatted(variantId, variantId, productId)));

        CatalogClient client = new CatalogClient(properties, noToken());
        Map<UUID, CatalogPort.VariantSnapshot> result = client.findVariants(List.of(variantId));

        assertThat(result).containsKey(variantId);
        CatalogPort.VariantSnapshot snapshot = result.get(variantId);
        assertThat(snapshot.productId()).isEqualTo(productId);
        assertThat(snapshot.unitPrice().minorUnits()).isEqualTo(45000);
        assertThat(snapshot.purchasable()).isTrue();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).startsWith("/api/v1/catalog/variants");
        assertThat(request.getRequestUrl().queryParameter("ids")).isEqualTo(variantId.toString());
    }

    @Test
    @DisplayName("an empty id list makes no call at all")
    void catalog_empty_request_makes_no_call() {
        CatalogClient client = new CatalogClient(properties, noToken());
        assertThat(client.findVariants(List.of())).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("a variant the catalogue does not return is absent, not an error")
    void catalog_missing_variant_is_absent() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        CatalogClient client = new CatalogClient(properties, noToken());
        assertThat(client.findVariants(List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    @DisplayName("a catalogue outage is reported as a dependency failure, not a business error")
    void catalog_outage_is_a_dependency_failure() {
        server.enqueue(new MockResponse().setResponseCode(503));

        CatalogClient client = new CatalogClient(properties, noToken());
        assertThatThrownBy(() -> client.findVariants(List.of(UUID.randomUUID())))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode())
                        .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    // ---------------------------------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("reads a customer's contact facts and verification flags")
    void identity_reads_a_customer() throws Exception {
        UUID customerId = UUID.randomUUID();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id": "%s", "fullName": "Asha Rao", "email": "asha@example.com",
                         "mobile": "9876543210", "emailVerified": true, "mobileVerified": true,
                         "addresses": []}
                        """.formatted(customerId)));

        CustomerClient client = new CustomerClient(properties, noToken());
        CustomerPort.CustomerSnapshot snapshot = client.findCustomer(customerId).orElseThrow();

        assertThat(snapshot.fullName()).isEqualTo("Asha Rao");
        assertThat(snapshot.email().value()).isEqualTo("asha@example.com");
        assertThat(snapshot.fullyVerified()).isTrue();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/customers/" + customerId);
    }

    @Test
    @DisplayName("an unknown customer is an empty result, not a failure")
    void identity_unknown_customer_is_empty() {
        server.enqueue(new MockResponse().setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"CUSTOMER_NOT_FOUND\"}"));

        CustomerClient client = new CustomerClient(properties, noToken());
        assertThat(client.findCustomer(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("an address is returned only when it belongs to the customer")
    void identity_returns_only_the_customers_own_address() {
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id": "%s", "fullName": "Asha Rao", "email": "asha@example.com",
                         "mobile": "9876543210", "emailVerified": true, "mobileVerified": true,
                         "addresses": [{"id": "%s", "recipientName": "Asha Rao", "mobile": "9876543210",
                         "line1": "12 Cocoa Lane", "line2": "Flat 3", "city": "Bengaluru",
                         "state": "Karnataka", "pincode": "560001", "deliveryInstructions": "Ring the bell"}]}
                        """.formatted(customerId, addressId)));

        CustomerClient client = new CustomerClient(properties, noToken());
        CustomerPort.AddressSnapshot address =
                client.findAddress(customerId, addressId).orElseThrow();

        assertThat(address.recipientName()).isEqualTo("Asha Rao");
        assertThat(address.pincode().value()).isEqualTo("560001");

        // An address id that is not in the profile is not returned, even though the call succeeded.
        // The profile is fetched again, so a second response is enqueued.
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id": "%s", "fullName": "Asha Rao", "email": "asha@example.com",
                         "mobile": "9876543210", "emailVerified": true, "mobileVerified": true,
                         "addresses": [{"id": "%s", "recipientName": "Asha Rao", "mobile": "9876543210",
                         "line1": "12 Cocoa Lane", "line2": "Flat 3", "city": "Bengaluru",
                         "state": "Karnataka", "pincode": "560001", "deliveryInstructions": "Ring the bell"}]}
                        """.formatted(customerId, addressId)));
        assertThat(client.findAddress(customerId, UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("an identity outage is reported as a dependency failure")
    void identity_outage_is_a_dependency_failure() {
        server.enqueue(new MockResponse().setResponseCode(503));

        CustomerClient client = new CustomerClient(properties, noToken());
        assertThatThrownBy(() -> client.findCustomer(UUID.randomUUID()))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode())
                        .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    // ---------------------------------------------------------------------------------------------
    // Delivery
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("quotes the delivery charge for a serviceable PIN code")
    void delivery_quotes_a_serviceable_pincode() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"serviceable\": true, \"deliveryChargeMinorUnits\": 5000, \"currency\": \"INR\"}"));

        ServiceabilityClient client = new ServiceabilityClient(properties, noToken());
        ServiceabilityPort.Quote quote = client.quoteFor(Pincode.of("560001"));

        assertThat(quote.serviceable()).isTrue();
        assertThat(quote.deliveryCharge().minorUnits()).isEqualTo(5000);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/serviceability/560001");
    }

    @Test
    @DisplayName("an unserviceable PIN code is reported with the seller's reason")
    void delivery_reports_an_unserviceable_pincode() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"serviceable\": false, \"deliveryChargeMinorUnits\": 0, \"currency\": \"INR\","
                        + " \"reason\": \"We do not deliver to PIN code 110001 yet\"}"));

        ServiceabilityClient client = new ServiceabilityClient(properties, noToken());
        ServiceabilityPort.Quote quote = client.quoteFor(Pincode.of("110001"));

        assertThat(quote.serviceable()).isFalse();
        assertThat(quote.reason()).contains("110001");
    }

    @Test
    @DisplayName("a delivery outage is a dependency failure, never a silent 'not serviceable'")
    void delivery_outage_is_a_dependency_failure() {
        server.enqueue(new MockResponse().setResponseCode(503));

        ServiceabilityClient client = new ServiceabilityClient(properties, noToken());
        assertThatThrownBy(() -> client.quoteFor(Pincode.of("560001")))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode())
                        .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    // ---------------------------------------------------------------------------------------------
    // Payment
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("starts a payment with the order's amount and forwards the idempotency key")
    void payment_starts_with_the_orders_amount() throws Exception {
        UUID orderId = UUID.randomUUID();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"provider": "razorpay", "providerOrderId": "order_rzp_1", "amountMinorUnits": 54900,
                         "amount": "549.00", "currency": "INR", "receipt": "CHOCO-2026001-1000",
                         "publicKey": "rzp_test_key"}
                        """));

        PaymentClient client = new PaymentClient(properties, noToken());
        PaymentPort.Initiation initiation = client.initiate(orderId, Money.inr(54900),
                "CHOCO-2026001-1000", "key-1");

        assertThat(initiation.providerOrderId()).isEqualTo("order_rzp_1");
        assertThat(initiation.amountMinorUnits()).isEqualTo(54900);
        assertThat(initiation.publicKey()).isEqualTo("rzp_test_key");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/payments/orders/" + orderId + "/initiate");
        assertThat(request.getHeader("Idempotency-Key")).isEqualTo("key-1");
        // The customer id must never be sent: the payment service takes it from the forwarded token.
        assertThat(request.getBody().readUtf8())
                .contains("\"amountMinorUnits\":54900")
                .doesNotContain("customerId");
    }

    @Test
    @DisplayName("a payment outage is reported as a dependency failure, never a false success")
    void payment_outage_is_a_dependency_failure() {
        server.enqueue(new MockResponse().setResponseCode(503));

        PaymentClient client = new PaymentClient(properties, noToken());
        assertThatThrownBy(() -> client.initiate(UUID.randomUUID(), Money.inr(100), "r", "k"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode())
                        .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    @Test
    @DisplayName("a response with no provider order id is a failure, not a half-built initiation")
    void payment_response_without_a_provider_order_is_a_failure() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"provider\": \"razorpay\", \"amountMinorUnits\": 100}"));

        PaymentClient client = new PaymentClient(properties, noToken());
        assertThatThrownBy(() -> client.initiate(UUID.randomUUID(), Money.inr(100), "r", "k"))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode())
                        .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }
}

package com.choco.identity.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.identity.application.service.AdminProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The seller's customer list and single-customer read over real HTTP against the real stack.
 *
 * <p>This is the boundary that matters: a customer must never reach the administrative customer
 * surface, and an administrator must see the accounts without ever seeing a credential. The tests
 * register real customers through the real registration path, so the data the list pages over is
 * produced by the same code the product runs.
 */
@DisplayName("Customer administration (HTTP, real stack)")
class AdminCustomerApiIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private AdminProvisioningService provisioningService;

    @Override
    protected void prepareEachTest() {
        resetDatabase();
        provisioningService.ensureBootstrapAdmin(
                BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD, "Shop Owner");
    }

    @Test
    @DisplayName("the seller lists customers and can find one by a partial search term")
    void listsAndSearchesCustomers() {
        VerifiedCustomer alice = registerVerifiedCustomer("alice");
        registerVerifiedCustomer("bob");

        HttpResponse<String> all = get("/api/v1/admin/customers", adminToken());
        assertThat(all.statusCode()).isEqualTo(200);
        JsonNode page = json(all);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        // The list must never carry credential material.
        assertThat(all.body()).doesNotContain("passwordHash");

        HttpResponse<String> search = get(
                "/api/v1/admin/customers?search=" + alice.email(), adminToken());
        assertThat(search.statusCode()).isEqualTo(200);
        JsonNode results = json(search).get("content");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("id").asText()).isEqualTo(alice.id());
        assertThat(results.get(0).get("email").asText()).isEqualTo(alice.email());
    }

    @Test
    @DisplayName("a search term containing a LIKE wildcard is matched literally, not as a wildcard")
    void searchEscapesWildcards() {
        registerVerifiedCustomer("carol");

        // A bare '%' would match every customer if it reached the query unescaped; escaped, it matches
        // nothing, because no address contains a literal percent sign.
        HttpResponse<String> response = get("/api/v1/admin/customers?search=%25", adminToken());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("the seller reads one customer's profile including their addresses")
    void readsASingleCustomer() {
        VerifiedCustomer customer = registerVerifiedCustomer("dave");

        HttpResponse<String> response =
                get("/api/v1/admin/customers/" + customer.id(), adminToken());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("email").asText()).isEqualTo(customer.email());
        assertThat(body.get("fullyVerified").asBoolean()).isTrue();
        assertThat(response.body()).doesNotContain("passwordHash");
    }

    @Test
    @DisplayName("an unknown customer id is a 404, not an empty profile")
    void unknownCustomerIsNotFound() {
        HttpResponse<String> response = get(
                "/api/v1/admin/customers/" + java.util.UUID.randomUUID(), adminToken());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("a customer token is refused at the administrative customer surface")
    void customerTokenIsRefused() {
        VerifiedCustomer customer = registerVerifiedCustomer("erin");
        String customerToken = login(customer);

        HttpResponse<String> list = get("/api/v1/admin/customers", customerToken);
        HttpResponse<String> read = get(
                "/api/v1/admin/customers/" + customer.id(), customerToken);

        assertThat(list.statusCode()).isEqualTo(403);
        assertThat(read.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("an unauthenticated read of the customer list is refused")
    void unauthenticatedReadIsRefused() {
        HttpResponse<String> response = get("/api/v1/admin/customers", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private String adminToken() {
        return json(adminLogin(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD))
                .get("tokens").get("accessToken").asText();
    }

    private HttpResponse<String> adminLogin(String email, String password) {
        return post("/api/v1/auth/admin/login", jsonBody(Map.of("email", email, "password", password)));
    }
}

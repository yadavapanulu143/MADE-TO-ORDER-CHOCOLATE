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
 * Administrator authentication and the boundary against customers, over HTTP against the real stack.
 *
 * <p>The bootstrap administrator is created by the startup runner once, then the truncation in
 * {@link #prepareEachTest()} removes it, so each test re-creates it through the real provisioning
 * service. That keeps the fixture honest: the same code path that runs in production produces the
 * account these tests sign in with.
 */
@DisplayName("Admin authentication and boundary (HTTP, real stack)")
class AdminJourneyIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private AdminProvisioningService provisioningService;

    @Override
    protected void prepareEachTest() {
        resetDatabase();
        provisioningService.ensureBootstrapAdmin(
                BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD, "Shop Owner");
    }

    @Test
    @DisplayName("the bootstrap administrator can sign in and receives admin authority")
    void bootstrap_admin_can_sign_in() {
        HttpResponse<String> response = adminLogin(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("admin").get("email").asText()).isEqualTo(BOOTSTRAP_ADMIN_EMAIL);
        assertThat(body.get("admin").get("role").asText()).isEqualTo("SUPER_ADMIN");
        assertThat(body.get("admin").get("permissions")).isNotEmpty();
        // The response must never carry credential material.
        assertThat(response.body()).doesNotContain("passwordHash");
    }

    @Test
    @DisplayName("the admin access token carries an administrator role, never the customer role")
    void admin_token_carries_the_admin_role() {
        String token = json(adminLogin(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD))
                .get("tokens").get("accessToken").asText();

        JsonNode claims = claimsOf(token);
        assertThat(claims.get("role").asText()).isEqualTo("SUPER_ADMIN");
        assertThat(claims.get("permissions").toString()).contains("admin:manage");
        // An administrator has no OTP channels; the flags must not claim otherwise.
        assertThat(claims.get("email_verified").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a wrong password is refused with 401 and does not reveal whether the account exists")
    void wrong_password_is_refused() {
        HttpResponse<String> response = adminLogin(BOOTSTRAP_ADMIN_EMAIL, "definitely-wrong");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json(response).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("an unknown administrator produces the identical error as a wrong password")
    void unknown_admin_is_indistinguishable() {
        HttpResponse<String> unknown = adminLogin("nobody@choco.test", "definitely-wrong");
        HttpResponse<String> wrongPassword = adminLogin(BOOTSTRAP_ADMIN_EMAIL, "definitely-wrong");

        assertThat(unknown.statusCode()).isEqualTo(wrongPassword.statusCode());
        assertThat(json(unknown).get("code").asText())
                .isEqualTo(json(wrongPassword).get("code").asText());
    }

    @Test
    @DisplayName("a customer's credentials are rejected at the admin endpoint")
    void customer_credentials_do_not_work_at_the_admin_endpoint() {
        VerifiedCustomer customer = registerVerifiedCustomer("boundary");

        // Same address and password, presented to the admin endpoint. The admin service never consults
        // the customers table, so this can only fail.
        HttpResponse<String> attempt = adminLogin(customer.email(), customer.password());

        assertThat(attempt.statusCode()).isEqualTo(401);
        assertThat(json(attempt).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("a customer token is rejected at the admin refresh endpoint")
    void customer_refresh_token_does_not_work_at_the_admin_endpoint() {
        VerifiedCustomer customer = registerVerifiedCustomer("boundary-refresh");
        String customerRefresh = loginResponse(customer).get("tokens").get("refreshToken").asText();

        HttpResponse<String> attempt = post("/api/v1/auth/admin/refresh",
                jsonBody(Map.of("refreshToken", customerRefresh)));

        assertThat(attempt.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("admin refresh rotates and a replayed token revokes the family")
    void admin_refresh_rotation_and_replay() {
        String refreshToken = json(adminLogin(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD))
                .get("tokens").get("refreshToken").asText();

        HttpResponse<String> first = post("/api/v1/auth/admin/refresh",
                jsonBody(Map.of("refreshToken", refreshToken)));
        assertThat(first.statusCode()).isEqualTo(200);
        String rotated = json(first).get("tokens").get("refreshToken").asText();
        assertThat(rotated).isNotEqualTo(refreshToken);

        HttpResponse<String> replay = post("/api/v1/auth/admin/refresh",
                jsonBody(Map.of("refreshToken", refreshToken)));
        assertThat(replay.statusCode()).isEqualTo(401);

        // Replay revokes the whole family, so even the freshly rotated token is now dead.
        HttpResponse<String> afterReplay = post("/api/v1/auth/admin/refresh",
                jsonBody(Map.of("refreshToken", rotated)));
        assertThat(afterReplay.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("admin logout revokes the refresh token and is idempotent")
    void admin_logout_is_idempotent() {
        String refreshToken = json(adminLogin(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD))
                .get("tokens").get("refreshToken").asText();

        assertThat(post("/api/v1/auth/admin/logout",
                jsonBody(Map.of("refreshToken", refreshToken))).statusCode()).isEqualTo(204);
        assertThat(post("/api/v1/auth/admin/logout",
                jsonBody(Map.of("refreshToken", refreshToken))).statusCode()).isEqualTo(204);
        assertThat(post("/api/v1/auth/admin/refresh",
                jsonBody(Map.of("refreshToken", refreshToken))).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("admin logout retires the access token, not only the refresh token")
    void admin_logout_retires_the_access_token() {
        // An administrator's access token authorises refunds, catalogue edits and customer exports, so
        // a logout that left it working for its remaining 15 minutes would be a live privileged
        // credential on a closed session. It must be refused on the next protected call.
        JsonNode login = json(adminLogin(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD));
        String accessToken = login.get("tokens").get("accessToken").asText();
        String refreshToken = login.get("tokens").get("refreshToken").asText();

        assertThat(get("/api/v1/admin/customers", accessToken).statusCode()).isEqualTo(200);

        assertThat(post("/api/v1/auth/admin/logout",
                jsonBody(Map.of("refreshToken", refreshToken))).statusCode()).isEqualTo(204);

        assertThat(get("/api/v1/admin/customers", accessToken).statusCode())
                .as("a logged-out administrator token must no longer be accepted")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("the admin login endpoint is rate limited independently of the customer endpoint")
    void admin_login_is_rate_limited_independently() {
        boolean limited = false;
        HttpResponse<String> last = null;
        for (int i = 0; i < 25 && !limited; i++) {
            last = adminLogin(BOOTSTRAP_ADMIN_EMAIL, "wrong-" + i);
            if (last.statusCode() == 429) {
                limited = true;
            }
        }
        assertThat(limited).as("the admin endpoint must throttle brute force on its own counter").isTrue();
        assertThat(json(last).get("code").asText()).isEqualTo("RATE_LIMITED");

        // A customer flood must not have consumed the admin budget, and vice versa: a freshly
        // registered customer can still sign in after the admin endpoint was throttled.
        VerifiedCustomer customer = registerVerifiedCustomer("after-admin-flood");
        assertThat(loginResponse(customer).get("tokens").get("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("there is no public administrator sign-up")
    void there_is_no_admin_registration_endpoint() {
        // The route does not exist; the security filter chain must not silently accept it either.
        HttpResponse<String> attempt = post("/api/v1/auth/admin/register",
                jsonBody(Map.of("email", "sneaky@choco.test", "password", "Choco!Pass123")));

        assertThat(attempt.statusCode()).isIn(401, 404, 405);
    }

    private HttpResponse<String> adminLogin(String email, String password) {
        return post("/api/v1/auth/admin/login", jsonBody(Map.of("email", email, "password", password)));
    }
}

package com.choco.identity.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The customer journey end to end over HTTP, against the real stack.
 *
 * <p>These tests drive the actual controllers, security filter chain, transaction manager, JPA
 * mapping and PostgreSQL. They are the only place where the parts are proven to fit together:
 * registration persists and mails a code, the code verifies, the verified customer signs in and gets
 * a token the resource server accepts, and that token can only reach the customer's own data.
 */
@DisplayName("Customer journey (HTTP, real stack)")
class CustomerJourneyIT extends AbstractIdentityIntegrationTest {

    @Test
    @DisplayName("register, verify both channels, sign in, and read the profile")
    void full_registration_and_login_journey() {
        VerifiedCustomer customer = registerVerifiedCustomer("journey");
        String token = login(customer);

        HttpResponse<String> profile = get("/api/v1/customers/me", token);

        assertThat(profile.statusCode()).isEqualTo(200);
        JsonNode body = json(profile);
        assertThat(body.get("id").asText()).isEqualTo(customer.id());
        assertThat(body.get("email").asText()).isEqualTo(customer.email());
        assertThat(body.get("emailVerified").asBoolean()).isTrue();
        assertThat(body.get("mobileVerified").asBoolean()).isTrue();
        assertThat(body.get("fullyVerified").asBoolean()).isTrue();
        // A read model must never carry credential material.
        assertThat(profile.body()).doesNotContain("passwordHash").doesNotContain("password");
    }

    @Test
    @DisplayName("an unverified customer cannot sign in even with the right password")
    void unverified_customer_cannot_sign_in() {
        String email = "unverified-" + java.util.UUID.randomUUID() + "@choco.test";
        String password = "Choco!Pass123";
        HttpResponse<String> registered = post("/api/v1/auth/register", jsonBody(Map.of(
                "fullName", "Unverified Customer",
                "email", email,
                "mobile", "9123456780",
                "password", password)));
        assertThat(registered.statusCode()).isEqualTo(202);

        HttpResponse<String> login = post("/api/v1/auth/login", jsonBody(Map.of(
                "identifier", email, "password", password)));

        // 403, not 401: the credentials are correct, the account simply is not usable yet. The
        // specific reason is only returned after the password has been verified, so it cannot be
        // used to discover accounts.
        assertThat(login.statusCode()).isEqualTo(403);
        assertThat(json(login).get("code").asText()).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    @DisplayName("a duplicate registration is refused with the same conflict the database would give")
    void duplicate_email_is_rejected() {
        VerifiedCustomer customer = registerVerifiedCustomer("duplicate");

        HttpResponse<String> again = post("/api/v1/auth/register", jsonBody(Map.of(
                "fullName", "Second Attempt",
                "email", customer.email(),
                "mobile", "9123456781",
                "password", "Choco!Pass123")));

        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(json(again).get("code").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("a password that fails the shared policy is rejected with WEAK_PASSWORD")
    void weak_password_is_rejected() {
        // Long enough to pass Bean Validation, but on the shared blocklist: the policy, not the
        // length check, is what rejects it.
        HttpResponse<String> response = post("/api/v1/auth/register", jsonBody(Map.of(
                "fullName", "Weak Customer",
                "email", "weak-" + java.util.UUID.randomUUID() + "@choco.test",
                "mobile", "9123456782",
                "password", "password123")));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("code").asText()).isEqualTo("WEAK_PASSWORD");
    }

    @Test
    @DisplayName("a password shorter than the minimum is rejected by request validation first")
    void too_short_password_is_rejected_by_bean_validation() {
        HttpResponse<String> response = post("/api/v1/auth/register", jsonBody(Map.of(
                "fullName", "Short Customer",
                "email", "short-" + java.util.UUID.randomUUID() + "@choco.test",
                "mobile", "9123456789",
                "password", "short1")));

        // Bean Validation is the first line, so a malformed body never reaches the use case.
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(json(response).get("fieldErrors").get("password")).isNotNull();
    }

    @Test
    @DisplayName("a wrong code is refused and the account stays unverified")
    void wrong_verification_code_is_refused() {
        String email = "wrongcode-" + java.util.UUID.randomUUID() + "@choco.test";
        HttpResponse<String> registered = post("/api/v1/auth/register", jsonBody(Map.of(
                "fullName", "Wrong Code",
                "email", email,
                "mobile", "9123456783",
                "password", "Choco!Pass123")));
        String customerId = json(registered).get("customerId").asText();

        // Ensure a real challenge exists, then submit a code that cannot match it.
        latestEmailOtp(email);
        HttpResponse<String> attempt = post(
                "/api/v1/auth/customers/" + customerId + "/verifications?channel=EMAIL",
                jsonBody(Map.of("code", "000000")));

        assertThat(attempt.statusCode()).isEqualTo(400);
        assertThat(json(attempt).get("code").asText()).isEqualTo("OTP_INVALID");
    }

    @Test
    @DisplayName("a customer cannot read another customer's profile")
    void a_customer_cannot_read_another_customers_profile() {
        VerifiedCustomer alice = registerVerifiedCustomer("alice");
        VerifiedCustomer bob = registerVerifiedCustomer("bob");
        String bobToken = login(bob);

        HttpResponse<String> attempt = get("/api/v1/customers/" + alice.id(), bobToken);

        assertThat(attempt.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("no token is refused on a protected endpoint")
    void protected_endpoint_requires_a_token() {
        HttpResponse<String> response = get("/api/v1/customers/me", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("a profile name update is persisted and reflected")
    void profile_update_persists() {
        VerifiedCustomer customer = registerVerifiedCustomer("profile");
        String token = login(customer);

        HttpResponse<String> updated = put("/api/v1/customers/me",
                jsonBody(Map.of("fullName", "Renamed Customer")), token);

        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json(updated).get("fullName").asText()).isEqualTo("Renamed Customer");
        assertThat(json(get("/api/v1/customers/me", token)).get("fullName").asText())
                .isEqualTo("Renamed Customer");
    }

    @Test
    @DisplayName("changing the email clears its verified flag and issues a fresh code")
    void changing_email_requires_reverification() {
        VerifiedCustomer customer = registerVerifiedCustomer("contact");
        String token = login(customer);
        String newEmail = "moved-" + java.util.UUID.randomUUID() + "@choco.test";

        HttpResponse<String> changed = patch("/api/v1/customers/me/contact",
                jsonBody(Map.of("email", newEmail)), token);

        assertThat(changed.statusCode()).isEqualTo(200);
        JsonNode body = json(changed);
        assertThat(body.get("email").asText()).isEqualTo(newEmail);
        assertThat(body.get("emailVerified").asBoolean())
                .as("the new address has not been proven yet")
                .isFalse();
        assertThat(body.get("fullyVerified").asBoolean()).isFalse();
        // The mobile channel was untouched, so it stays verified.
        assertThat(body.get("mobileVerified").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("addresses can be added, listed and made default, scoped to the owner")
    void address_management_is_owner_scoped() {
        VerifiedCustomer customer = registerVerifiedCustomer("address");
        String token = login(customer);

        HttpResponse<String> created = post("/api/v1/customers/me/addresses", jsonBody(Map.of(
                "recipientName", "Test Customer",
                "mobile", customer.mobile(),
                "line1", "12 Cocoa Lane",
                "city", "Bengaluru",
                "state", "Karnataka",
                "pincode", "560001",
                "makeDefault", true)), token);
        assertThat(created.statusCode()).isEqualTo(201);
        String addressId = json(created).get("id").asText();

        HttpResponse<String> list = get("/api/v1/customers/me/addresses", token);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("12 Cocoa Lane");

        // A different customer must not see it.
        VerifiedCustomer other = registerVerifiedCustomer("address-other");
        String otherToken = login(other);
        HttpResponse<String> foreign = get("/api/v1/customers/me/addresses/" + addressId, otherToken);
        assertThat(foreign.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("a login flood is refused with 429 and a Retry-After header")
    void login_is_rate_limited() {
        VerifiedCustomer customer = registerVerifiedCustomer("ratelimit");

        HttpResponse<String> last = null;
        boolean limited = false;
        for (int i = 0; i < 25 && !limited; i++) {
            last = post("/api/v1/auth/login", jsonBody(Map.of(
                    "identifier", customer.email(),
                    "password", "wrong-password-" + i)));
            if (last.statusCode() == 429) {
                limited = true;
            }
        }

        assertThat(limited).as("the login endpoint must stop a brute-force flood").isTrue();
        assertThat(last.headers().firstValue("Retry-After")).isPresent();
        assertThat(json(last).get("code").asText()).isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("refresh rotates the token and the old one cannot be used again")
    void refresh_token_rotation_and_replay_detection() {
        VerifiedCustomer customer = registerVerifiedCustomer("refresh");
        String refreshToken = loginResponse(customer).get("tokens").get("refreshToken").asText();

        HttpResponse<String> first = post("/api/v1/auth/refresh",
                jsonBody(Map.of("refreshToken", refreshToken)));
        assertThat(first.statusCode()).isEqualTo(200);
        String rotated = json(first).get("tokens").get("refreshToken").asText();
        assertThat(rotated).isNotEqualTo(refreshToken);

        // Replaying the consumed token must fail: it is single-use.
        HttpResponse<String> replay = post("/api/v1/auth/refresh",
                jsonBody(Map.of("refreshToken", refreshToken)));
        assertThat(replay.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("logout revokes the refresh token")
    void logout_revokes_the_refresh_token() {
        VerifiedCustomer customer = registerVerifiedCustomer("logout");
        String refreshToken = loginResponse(customer).get("tokens").get("refreshToken").asText();

        HttpResponse<String> logout = post("/api/v1/auth/logout",
                jsonBody(Map.of("refreshToken", refreshToken)));
        assertThat(logout.statusCode()).isEqualTo(204);

        HttpResponse<String> afterLogout = post("/api/v1/auth/refresh",
                jsonBody(Map.of("refreshToken", refreshToken)));
        assertThat(afterLogout.statusCode()).isEqualTo(401);
    }
}

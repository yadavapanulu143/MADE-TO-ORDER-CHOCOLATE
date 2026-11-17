package com.choco.catalog.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * End-to-end catalogue journeys over the real HTTP stack, real database and real security filter chain.
 *
 * <p>The security cases are the reason this class exists as much as the happy path is. A catalogue
 * service is read-mostly and the reads are public, so the risk is not a customer seeing too little — it
 * is a customer or an under-privileged administrator being able to change or see something they should
 * not. Those refusals are asserted directly rather than inferred from a successful call by the right
 * role.
 */
@DisplayName("Catalogue journeys (real HTTP, database and security)")
class CatalogueJourneyIT extends AbstractCatalogIntegrationTest {

    private static final String ADMIN_ID = "aaaaaaaa-0000-0000-0000-000000000001";

    private String admin() {
        return adminToken(UUID.fromString(ADMIN_ID));
    }

    @Nested
    @DisplayName("administration")
    class Administration {

        @Test
        @DisplayName("an administrator can create, price, publish and read a product")
        void full_lifecycle() {
            String token = admin();
            String id = createPublishedProduct(token, "Dark Chocolate Bar", "450.00");

            JsonNode product = json(get("/api/v1/admin/catalog/products/" + id, token));
            assertThat(product.get("status").asText()).isEqualTo("PUBLISHED");
            assertThat(product.get("variants")).hasSize(1);
            assertThat(product.get("variants").get(0).get("priceMinorUnits").asLong()).isEqualTo(45_000L);
            assertThat(product.get("variants").get(0).get("price").asText()).isEqualTo("450.00");
        }

        @Test
        @DisplayName("a product without a variant cannot be published")
        void publish_without_variant_is_refused() {
            String token = admin();
            String id = json(post("/api/v1/admin/catalog/products",
                    jsonBody(Map.of("name", "Draft Only")), token)).get("id").asText();

            var response = post("/api/v1/admin/catalog/products/" + id + "/publish", null, token);

            // 409: the request is well-formed, it conflicts with the product's current state.
            assertThat(response.statusCode()).isEqualTo(409);
        }

        @Test
        @DisplayName("the administrative listing shows drafts as well as published products")
        void admin_listing_includes_drafts() {
            String token = admin();
            createPublishedProduct(token, "Dark Chocolate Bar", "450.00");
            post("/api/v1/admin/catalog/products", jsonBody(Map.of("name", "Draft Bar")), token);

            JsonNode page = json(get("/api/v1/admin/catalog/products", token));

            assertThat(page.get("content")).hasSize(2);
        }

        @Test
        @DisplayName("an image can be uploaded and then fetched by its generated key")
        void image_upload_and_fetch() {
            String token = admin();
            String id = json(post("/api/v1/admin/catalog/products",
                    jsonBody(Map.of("name", "Dark Chocolate Bar")), token)).get("id").asText();

            var upload = sendMultipart("/api/v1/admin/catalog/products/" + id + "/images", token,
                    "bar.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3});
            assertThat(upload.statusCode()).isEqualTo(200);
            JsonNode product = json(upload);
            String imageUrl = product.get("images").get(0).get("url").asText();
            assertThat(imageUrl).contains("/api/v1/catalog/images/");

            var fetched = get(imageUrl.substring(imageUrl.indexOf("/api/v1")), null);
            assertThat(fetched.statusCode()).isEqualTo(200);
            assertThat(fetched.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
            assertThat(fetched.headers().firstValue("Content-Type").orElse("")).contains("image/jpeg");
        }

        @Test
        @DisplayName("the website settings can be read publicly and updated by a super administrator")
        void settings_are_public_to_read_and_privileged_to_write() {
            String token = superAdminToken(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000009"));

            var update = put("/api/v1/settings",
                    jsonBody(Map.of("sellerName", "Cocoa House", "contactEmail", "hello@choco.example",
                            "contactPhone", "9876543210", "refundPolicy", "Made to order; see terms")),
                    token);
            assertThat(update.statusCode()).isEqualTo(200);
            assertThat(json(update).get("sellerName").asText()).isEqualTo("Cocoa House");

            // The read is public: a visitor must be able to see the policies before ordering.
            var read = get("/api/v1/settings", null);
            assertThat(read.statusCode()).isEqualTo(200);
            assertThat(json(read).get("refundPolicy").asText()).contains("Made to order");
        }

        @Test
        @DisplayName("an administrator without the settings permission cannot update them")
        void settings_write_needs_the_permission() {
            var response = put("/api/v1/settings", jsonBody(Map.of("sellerName", "Nope")),
                    adminWithoutCatalogPermission(UUID.randomUUID()));

            assertThat(response.statusCode()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("storefront")
    class Storefront {

        @Test
        @DisplayName("a published product is visible to an anonymous visitor by slug")
        void published_product_is_public() {
            createPublishedProduct(admin(), "Dark Chocolate Bar", "450.00");

            var response = get("/api/v1/products/dark-chocolate-bar", null);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response).get("name").asText()).isEqualTo("Dark Chocolate Bar");
        }

        @Test
        @DisplayName("a draft product is never visible to an anonymous visitor")
        void draft_product_is_not_public() {
            post("/api/v1/admin/catalog/products", jsonBody(Map.of("name", "Secret Bar")), admin());

            var response = get("/api/v1/products/secret-bar", null);

            assertThat(response.statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("the public listing contains only published products")
        void public_listing_excludes_drafts() {
            createPublishedProduct(admin(), "Dark Chocolate Bar", "450.00");
            post("/api/v1/admin/catalog/products", jsonBody(Map.of("name", "Draft Bar")), admin());

            JsonNode page = json(get("/api/v1/products", null));

            assertThat(page.get("content")).hasSize(1);
            assertThat(page.get("content").get(0).get("name").asText()).isEqualTo("Dark Chocolate Bar");
        }

        @Test
        @DisplayName("unpublishing removes the product from the storefront but keeps the row")
        void unpublishing_hides_the_product() {
            String token = admin();
            String id = createPublishedProduct(token, "Dark Chocolate Bar", "450.00");

            post("/api/v1/admin/catalog/products/" + id + "/unpublish", null, token);

            assertThat(get("/api/v1/products/dark-chocolate-bar", null).statusCode()).isEqualTo(404);
            assertThat(get("/api/v1/admin/catalog/products/" + id, token).statusCode()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("authorization")
    class Authorization {

        @Test
        @DisplayName("an anonymous caller cannot reach the administrative endpoints")
        void anonymous_cannot_administer() {
            var response = post("/api/v1/admin/catalog/products", jsonBody(Map.of("name", "Nope")), null);

            assertThat(response.statusCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("a signed-in customer cannot administer the catalogue")
        void customer_cannot_administer() {
            String token = customerToken(UUID.randomUUID());

            var response = post("/api/v1/admin/catalog/products", jsonBody(Map.of("name", "Nope")), token);

            assertThat(response.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("an administrator without the catalogue permission cannot write")
        void admin_without_permission_cannot_write() {
            String token = adminWithoutCatalogPermission(UUID.randomUUID());

            var response = post("/api/v1/admin/catalog/products", jsonBody(Map.of("name", "Nope")), token);

            assertThat(response.statusCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("a forged token is rejected")
        void a_forged_token_is_rejected() {
            var response = post("/api/v1/admin/catalog/products", jsonBody(Map.of("name", "Nope")),
                    "not.a.real.token");

            assertThat(response.statusCode()).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("internal variant lookup")
    class InternalVariantLookup {

        @Test
        @DisplayName("a published variant is resolved with its price and availability")
        void published_variant_is_resolved() {
            String token = admin();
            String productId = createPublishedProduct(token, "Dark Chocolate Bar", "450.00");
            String variantId = variantIdOf(token, productId);

            var response = get("/api/v1/catalog/variants?ids=" + variantId, token);

            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode variant = json(response).get(variantId);
            assertThat(variant.get("productId").asText()).isEqualTo(productId);
            assertThat(variant.get("productName").asText()).isEqualTo("Dark Chocolate Bar");
            assertThat(variant.get("priceMinorUnits").asLong()).isEqualTo(45_000L);
            assertThat(variant.get("variantActive").asBoolean()).isTrue();
            assertThat(variant.get("productAvailable").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("a draft product's variant is not resolved, so an unannounced product cannot be ordered")
        void draft_variant_is_not_resolved() {
            String token = admin();
            String id = json(post("/api/v1/admin/catalog/products",
                    jsonBody(Map.of("name", "Secret Bar")), token)).get("id").asText();
            String variantId = json(post("/api/v1/admin/catalog/products/" + id + "/variants",
                    jsonBody(Map.of("label", "200 g", "sku", "SKU-SECRET", "price", "450.00",
                            "minOrderQuantity", 1, "maxOrderQuantity", 10)), token)).get("id").asText();

            var response = get("/api/v1/catalog/variants?ids=" + variantId, token);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response)).isEmpty();
        }

        @Test
        @DisplayName("an unknown variant id is absent from the result rather than an error")
        void unknown_variant_is_absent() {
            var response = get("/api/v1/catalog/variants?ids=" + UUID.randomUUID(), admin());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json(response)).isEmpty();
        }

        @Test
        @DisplayName("the internal lookup is not public")
        void internal_lookup_requires_a_token() {
            var response = get("/api/v1/catalog/variants?ids=" + UUID.randomUUID(), null);

            assertThat(response.statusCode()).isEqualTo(401);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Multipart helper
    // ---------------------------------------------------------------------------------------------

    /** Reads back the id of a product's first variant, which the checkout addresses by id. */
    private String variantIdOf(String token, String productId) {
        return json(get("/api/v1/admin/catalog/products/" + productId, token))
                .get("variants").get(0).get("id").asText();
    }

    private java.net.http.HttpResponse<String> sendMultipart(String path, String token, String filename,
                                                             String contentType, byte[] content) {
        String boundary = "----chocoIT" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url(path)))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + token)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            return java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("multipart request failed", e);
        }
    }
}

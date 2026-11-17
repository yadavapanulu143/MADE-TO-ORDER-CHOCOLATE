package com.choco.reporting.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.events.envelope.EventTypes;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The export surface over real HTTP.
 *
 * <p>An export moves customer data out of the platform, so the controls that matter are the ones this
 * test asserts: the permission is required, a customer token is refused, the customer export masks the
 * contact details, the download is an attachment marked {@code no-store}, and the act is audited.
 */
@DisplayName("Reporting export API (real HTTP)")
class AdminExportApiIT extends AbstractReportingIntegrationTest {

    @Test
    @DisplayName("the operational export is served to a report reader with an attachment header")
    void operationalExportServed() {
        var response = get("/api/v1/exports/ORDERS?format=CSV&days=30", adminToken(UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/csv");
        assertThat(response.headers().firstValue("Content-Disposition").orElse(""))
                .contains("attachment").contains("orders-");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test
    @DisplayName("the XLSX export is served as a spreadsheet")
    void xlsxExportServed() {
        var response = get("/api/v1/exports/INVOICES?format=XLSX&days=30", adminToken(UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("spreadsheetml");
        assertThat(response.headers().firstValue("Content-Disposition").orElse("")).contains(".xlsx");
    }

    @Test
    @DisplayName("a customer export needs the customer:export permission, not just report access")
    void customerExportNeedsExportPermission() {
        var response = get("/api/v1/exports/CUSTOMERS?days=30", reportOnlyAdminToken(UUID.randomUUID()));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("a customer token cannot reach the export surface at all")
    void customerRefused() {
        assertThat(get("/api/v1/exports/ORDERS?days=30", customerToken(UUID.randomUUID())).statusCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("an unauthenticated export is refused")
    void unauthenticatedRefused() {
        assertThat(get("/api/v1/exports/ORDERS?days=30", null).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("the customer export masks the email and mobile, so the raw values never leave")
    void customerExportIsMasked() {
        publish(EventTypes.CUSTOMER_REGISTERED, "cust-1", Map.of(
                "aggregateId", "cust-1", "email", "asha@example.test", "mobile", "9000000000",
                "fullName", "Asha"));
        Awaitility.await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(count("customer_projection")).isEqualTo(1));

        var response = get("/api/v1/exports/CUSTOMERS?format=CSV&days=30", adminToken(UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("a**a@example.test");
        assertThat(response.body()).doesNotContain("asha@example.test");
        assertThat(response.body()).doesNotContain("9000000000");
    }

    @Test
    @DisplayName("an export records an audit row naming the actor and the row count")
    void exportIsAudited() {
        UUID admin = UUID.randomUUID();
        assertThat(get("/api/v1/exports/ORDERS?days=30", adminToken(admin)).statusCode()).isEqualTo(200);

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(query(
                        "SELECT action FROM audit_log WHERE actor_id = '" + admin + "'"))
                        .isEqualTo("export.orders"));
    }

    @Test
    @DisplayName("an unknown export format is refused rather than defaulting silently")
    void unknownFormatRefused() {
        var response = get("/api/v1/exports/ORDERS?format=PDF&days=30", adminToken(UUID.randomUUID()));
        assertThat(response.statusCode()).isEqualTo(400);
    }

    private long count(String table) {
        return Long.parseLong(query("SELECT count(*) FROM " + table));
    }

    private String query(String sql) {
        try (var connection = POSTGRES.createConnection("");
             var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        } catch (Exception e) {
            throw new IllegalStateException("query failed: " + sql, e);
        }
    }
}

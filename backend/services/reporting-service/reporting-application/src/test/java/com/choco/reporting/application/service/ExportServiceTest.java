package com.choco.reporting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.ForbiddenException;
import com.choco.reporting.application.fake.InMemoryReportingReadModel;
import com.choco.reporting.application.port.AuditLogPort;
import com.choco.reporting.application.port.ExportWriter;
import com.choco.reporting.domain.export.ExportDataset;
import com.choco.reporting.domain.export.ExportFormat;
import com.choco.reporting.domain.export.ExportType;
import com.choco.reporting.domain.model.CustomerProjection;
import com.choco.reporting.domain.model.OrderProjection;
import com.choco.reporting.domain.report.ReportPeriod;
import com.choco.security.principal.Roles;
import com.choco.test.Fakes;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the export authorisation and minimisation rules.
 *
 * <p>The permission is checked twice on purpose — once at the controller and once here — so a second
 * caller cannot reach the data by another door. These tests drive the use case directly, which is the
 * door the controller check would not cover.
 */
@DisplayName("ExportService")
class ExportServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final String ADMIN = "22222222-2222-2222-2222-222222222222";

    private InMemoryReportingReadModel readModel;
    private RecordingAuditLog auditLog;
    private CapturingWriter writer;
    private ExportService service;
    private ReportPeriod period;

    @BeforeEach
    void setUp() {
        readModel = new InMemoryReportingReadModel();
        auditLog = new RecordingAuditLog();
        writer = new CapturingWriter();
        service = new ExportService(readModel, writer, auditLog, Fakes.FixedClock.startingAt(NOW.toString()));
        period = new ReportPeriod(NOW.minusSeconds(86_400), NOW.plusSeconds(86_400));
    }

    private void aCustomerWithContactDetails() {
        readModel.upsertCustomer(new CustomerProjection("c1", "Asha", "asha@example.test",
                "9000000000", true, true, NOW, NOW));
    }

    @Nested
    @DisplayName("authorisation")
    class Authorisation {

        @Test
        @DisplayName("a customer export needs the customer:export permission, not just report access")
        void customerExportNeedsExportPermission() {
            aCustomerWithContactDetails();
            assertThatThrownBy(() -> service.export(ExportType.CUSTOMERS, ExportFormat.CSV, period,
                    Set.of(Roles.PERM_REPORT_READ), false, ADMIN))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining(Roles.PERM_CUSTOMER_EXPORT);
        }

        @Test
        @DisplayName("the operational exports need report:read")
        void operationalExportNeedsReportRead() {
            assertThatThrownBy(() -> service.export(ExportType.ORDERS, ExportFormat.CSV, period,
                    Set.of(), false, ADMIN))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining(Roles.PERM_REPORT_READ);
        }

        @Test
        @DisplayName("a super administrator is allowed without an explicit permission")
        void superAdminAllowed() {
            aCustomerWithContactDetails();
            ExportService.ExportDocument document = service.export(ExportType.CUSTOMERS, ExportFormat.CSV,
                    period, Set.of(), true, ADMIN);
            assertThat(document.rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("null permissions are refused, not treated as unrestricted")
        void nullPermissionsRefused() {
            assertThatThrownBy(() -> service.export(ExportType.ORDERS, ExportFormat.CSV, period, null,
                    false, ADMIN))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("a refusal records no audit row, because nothing left the platform")
        void refusalIsNotAudited() {
            assertThatThrownBy(() -> service.export(ExportType.CUSTOMERS, ExportFormat.CSV, period,
                    Set.of(), false, ADMIN)).isInstanceOf(ForbiddenException.class);
            assertThat(auditLog.entries).isEmpty();
        }
    }

    @Nested
    @DisplayName("minimisation")
    class Minimisation {

        @Test
        @DisplayName("the customer export masks the email and mobile")
        void masksContactDetails() {
            aCustomerWithContactDetails();
            service.export(ExportType.CUSTOMERS, ExportFormat.CSV, period,
                    Set.of(Roles.PERM_CUSTOMER_EXPORT), false, ADMIN);

            String email = writer.dataset.rows().getFirst().get(2).toString();
            String mobile = writer.dataset.rows().getFirst().get(3).toString();
            assertThat(email).isEqualTo("a**a@example.test");
            assertThat(mobile).isEqualTo("******0000");
        }

        @Test
        @DisplayName("no column of any export names an authentication artefact")
        void noCredentialColumns() {
            aCustomerWithContactDetails();
            readModel.upsertOrder(new OrderProjection("o1", "ORD-1", "c1", OrderProjection.CONFIRMED,
                    1L, 0L, "INR", NOW, NOW, null, null));
            for (ExportType type : ExportType.values()) {
                service.export(type, ExportFormat.CSV, period,
                        Set.of(Roles.PERM_CUSTOMER_EXPORT, Roles.PERM_REPORT_READ), false, ADMIN);
                assertThat(writer.dataset.columns())
                        .as("columns of the %s export", type)
                        .allSatisfy(column -> assertThat(column.key()).doesNotContain("password", "hash",
                                "otp", "token", "secret"));
            }
        }

        @Test
        @DisplayName("an export with no rows still writes the declared header row")
        void emptyExportHasHeaders() {
            ExportService.ExportDocument document = service.export(ExportType.ORDERS, ExportFormat.CSV,
                    period, Set.of(Roles.PERM_REPORT_READ), false, ADMIN);
            assertThat(document.rowCount()).isZero();
            assertThat(writer.dataset.headers()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("audit and filename")
    class AuditAndFilename {

        @Test
        @DisplayName("a successful export records who pulled which dataset and how many rows")
        void recordsTheExport() {
            aCustomerWithContactDetails();
            service.export(ExportType.CUSTOMERS, ExportFormat.CSV, period,
                    Set.of(Roles.PERM_CUSTOMER_EXPORT), false, ADMIN);

            assertThat(auditLog.entries).singleElement().satisfies(entry -> {
                assertThat(entry.actorId).isEqualTo(ADMIN);
                assertThat(entry.action).isEqualTo("export.customers");
                assertThat(entry.detail).contains("1 rows as CSV");
            });
        }

        @Test
        @DisplayName("the suggested filename names the dataset and the window start")
        void filenameNamesDatasetAndDate() {
            ExportService.ExportDocument document = service.export(ExportType.ORDERS, ExportFormat.XLSX,
                    period, Set.of(Roles.PERM_REPORT_READ), false, ADMIN);
            assertThat(document.filename()).startsWith("orders-").endsWith(".xlsx");
            assertThat(document.contentType()).contains("spreadsheetml");
        }
    }

    /** Captures the dataset handed to the writer so the test can assert on the columns and rows. */
    private static final class CapturingWriter implements ExportWriter {
        private ExportDataset dataset;

        @Override
        public byte[] write(ExportDataset dataset, ExportFormat format) {
            this.dataset = dataset;
            return dataset.name().getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Records audit entries so a test can assert what was written and what was not. */
    private static final class RecordingAuditLog implements AuditLogPort {
        private final List<Entry> entries = new ArrayList<>();

        @Override
        public void record(String actorType, String actorId, String action, String targetType,
                           String targetId, String outcome, String detail, String correlationId) {
            entries.add(new Entry(actorType, actorId, action, targetType, targetId, outcome, detail,
                    correlationId));
        }

        private record Entry(String actorType, String actorId, String action, String targetType,
                             String targetId, String outcome, String detail, String correlationId) {
        }
    }
}

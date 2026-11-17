package com.choco.reporting.domain.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExportDataset and its columns")
class ExportDatasetTest {

    private static final ExportColumn NAME = new ExportColumn("fullName", "Name");
    private static final ExportColumn EMAIL = new ExportColumn("email", "Email");

    @Nested
    @DisplayName("ExportColumn")
    class Columns {

        @Test
        @DisplayName("trims its key and header")
        void trims() {
            ExportColumn column = new ExportColumn("  name  ", "  Name  ");
            assertThat(column.key()).isEqualTo("name");
            assertThat(column.header()).isEqualTo("Name");
        }

        @Test
        @DisplayName("refuses a blank key or header")
        void refusesBlank() {
            assertThatThrownBy(() -> new ExportColumn("", "Name"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ExportColumn("name", "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a column that names an authentication artefact")
        void refusesCredentialColumn() {
            for (String key : List.of("password", "passwordHash", "otpCode", "resetToken", "secretKey",
                    "cardCvv", "upiPin", "bcrypt_hash")) {
                assertThatThrownBy(() -> new ExportColumn(key, "Header"))
                        .as("column %s must never be exportable", key)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("authentication artefact");
            }
        }
    }

    @Nested
    @DisplayName("ExportDataset")
    class Datasets {

        @Test
        @DisplayName("keeps the declared column order and exposes matching headers")
        void keepsColumnOrder() {
            ExportDataset dataset = new ExportDataset("customers", List.of(NAME, EMAIL),
                    List.of(List.of("Asha", "a@b.test")));
            assertThat(dataset.headers()).containsExactly("Name", "Email");
            assertThat(dataset.columnCount()).isEqualTo(2);
            assertThat(dataset.rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("refuses a row whose width does not match the columns")
        void refusesRaggedRow() {
            assertThatThrownBy(() -> new ExportDataset("customers", List.of(NAME, EMAIL),
                    List.of(List.of("Asha"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exactly 2 cells");
        }

        @Test
        @DisplayName("refuses a dataset with no name or no columns")
        void refusesEmpty() {
            assertThatThrownBy(() -> new ExportDataset("", List.of(NAME), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ExportDataset("customers", List.of(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("treats a null row list as an empty dataset rather than failing")
        void nullRowsBecomeEmpty() {
            ExportDataset dataset = new ExportDataset("customers", List.of(NAME), null);
            assertThat(dataset.rows()).isEmpty();
        }
    }

    @Nested
    @DisplayName("ExportFormat")
    class Formats {

        @Test
        @DisplayName("parses case-insensitively and defaults to CSV")
        void parses() {
            assertThat(ExportFormat.from("xlsx")).isEqualTo(ExportFormat.XLSX);
            assertThat(ExportFormat.from("XLSX")).isEqualTo(ExportFormat.XLSX);
            assertThat(ExportFormat.from(null)).isEqualTo(ExportFormat.CSV);
            assertThat(ExportFormat.from("  ")).isEqualTo(ExportFormat.CSV);
        }

        @Test
        @DisplayName("refuses an unknown format instead of guessing")
        void refusesUnknown() {
            assertThatThrownBy(() -> ExportFormat.from("pdf"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported export format");
        }
    }
}

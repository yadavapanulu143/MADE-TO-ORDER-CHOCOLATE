package com.choco.reporting.adapter.out.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.choco.reporting.domain.export.ExportColumn;
import com.choco.reporting.domain.export.ExportDataset;
import com.choco.reporting.domain.export.ExportFormat;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests the CSV and XLSX writers against the properties that actually matter.
 *
 * <p>Two of these are security properties, not formatting preferences: a cell beginning with a formula
 * trigger must not become a formula, and a value containing the delimiter must not be able to add a
 * column. Both are exercised with the hostile input, because a passing test on a benign value proves
 * nothing about them.
 */
@DisplayName("CsvXlsxExportWriter")
class CsvXlsxExportWriterTest {

    private final CsvXlsxExportWriter writer = new CsvXlsxExportWriter(new XlsxWriter());

    private static ExportDataset dataset(List<List<Object>> rows) {
        return new ExportDataset("customers",
                List.of(new ExportColumn("name", "Name"), new ExportColumn("amount", "Amount")), rows);
    }

    private static String csv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        // Strip the UTF-8 BOM the writer adds so Excel reads accented text correctly.
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    @Nested
    @DisplayName("CSV")
    class Csv {

        @Test
        @DisplayName("writes the header and rows, quoted only where needed")
        void writesSimpleRows() {
            byte[] bytes = writer.write(dataset(List.of(List.of("Asha", 1000L))), ExportFormat.CSV);
            assertThat(csv(bytes)).isEqualTo("Name,Amount\r\nAsha,1000\r\n");
        }

        @Test
        @DisplayName("starts with a UTF-8 BOM so Excel does not mangle an accented name")
        void writesBom() {
            byte[] bytes = writer.write(dataset(List.of()), ExportFormat.CSV);
            assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
            assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
            assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        }

        @Test
        @DisplayName("quotes a value containing a comma, a quote or a newline")
        void quotesDelimiters() {
            byte[] bytes = writer.write(dataset(List.of(List.of("Smith, \"Bob\"", 1L))), ExportFormat.CSV);
            assertThat(csv(bytes)).isEqualTo("Name,Amount\r\n\"Smith, \"\"Bob\"\"\",1\r\n");
        }

        @Test
        @DisplayName("neutralises a value that a spreadsheet would otherwise execute as a formula")
        void neutralisesFormula() {
            byte[] bytes = writer.write(dataset(List.of(List.of("=1+1", 1L))), ExportFormat.CSV);
            assertThat(csv(bytes)).contains("'=1+1");
        }

        @Test
        @DisplayName("neutralises every formula trigger a spreadsheet honours")
        void neutralisesEveryTrigger() {
            for (String hostile : List.of("=cmd|' /C calc'!A0", "+1", "-1", "@SUM(A1)")) {
                byte[] bytes = writer.write(dataset(List.of(List.of(hostile, 1L))), ExportFormat.CSV);
                assertThat(csv(bytes)).as("hostile value %s", hostile).contains("'" + hostile);
            }
        }

        @Test
        @DisplayName("renders a null cell as empty rather than the word null")
        void rendersNullAsEmpty() {
            byte[] bytes = writer.write(dataset(java.util.Arrays.asList(
                    java.util.Arrays.asList(null, 1L))), ExportFormat.CSV);
            assertThat(csv(bytes)).isEqualTo("Name,Amount\r\n,1\r\n");
        }

        @Test
        @DisplayName("writes a header-only document for an empty dataset")
        void writesHeaderOnly() {
            byte[] bytes = writer.write(dataset(List.of()), ExportFormat.CSV);
            assertThat(csv(bytes)).isEqualTo("Name,Amount\r\n");
        }
    }

    @Nested
    @DisplayName("XLSX")
    class Xlsx {

        @Test
        @DisplayName("keeps a whole number a numeric cell so a sum works")
        void keepsNumbersNumeric() throws Exception {
            byte[] bytes = writer.write(dataset(List.of(List.of("Asha", 1000L))), ExportFormat.XLSX);
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                var sheet = workbook.getSheet("customers");
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Name");
                var amount = sheet.getRow(1).getCell(1);
                assertThat(amount.getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(amount.getNumericCellValue()).isEqualTo(1000.0);
            }
        }

        @Test
        @DisplayName("writes a header-only workbook for an empty dataset")
        void writesHeaderOnly() throws Exception {
            byte[] bytes = writer.write(dataset(List.of()), ExportFormat.XLSX);
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                var sheet = workbook.getSheet("customers");
                assertThat(sheet.getLastRowNum()).isZero();
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Name");
            }
        }

        @Test
        @DisplayName("stores a boolean as a boolean, not the string true")
        void keepsBooleans() throws Exception {
            byte[] bytes = writer.write(dataset(List.of(List.of(true, 1L))), ExportFormat.XLSX);
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                var cell = workbook.getSheet("customers").getRow(1).getCell(0);
                assertThat(cell.getCellType()).isEqualTo(CellType.BOOLEAN);
                assertThat(cell.getBooleanCellValue()).isTrue();
            }
        }
    }
}

package com.choco.reporting.adapter.out.export;

import com.choco.reporting.application.port.ExportWriter;
import com.choco.reporting.domain.export.ExportDataset;
import com.choco.reporting.domain.export.ExportFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Writes an export as CSV or XLSX.
 *
 * <p>Both writers live here, behind the {@link ExportWriter} port, so the use case that builds a
 * dataset never sees a spreadsheet library. The CSV writer is hand-rolled because it is a few lines
 * and needs one specific correctness property: a value that contains a comma, a quote or a newline is
 * quoted and its quotes doubled, so a customer name of {@code Smith, "Bob"} cannot break the columns
 * or — worse — be read as a formula. A value beginning with {@code =}, {@code +}, {@code -} or
 * {@code @} is prefixed so a spreadsheet does not execute it as a formula, which is the CSV injection
 * that turns an export into an attack on the person who opens it.
 */
@Component
class CsvXlsxExportWriter implements ExportWriter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final XlsxWriter xlsxWriter;

    CsvXlsxExportWriter(XlsxWriter xlsxWriter) {
        this.xlsxWriter = xlsxWriter;
    }

    @Override
    public byte[] write(ExportDataset dataset, ExportFormat format) {
        return switch (format) {
            case CSV -> writeCsv(dataset);
            case XLSX -> xlsxWriter.write(dataset);
        };
    }

    private byte[] writeCsv(ExportDataset dataset) {
        StringBuilder builder = new StringBuilder();
        appendRow(builder, dataset.headers());
        for (List<Object> row : dataset.rows()) {
            appendRow(builder, row.stream().map(CsvXlsxExportWriter::render).toList());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // A UTF-8 BOM so Excel opens an accented name correctly instead of mojibake. Harmless to
            // every other reader, which treats it as an encoding marker.
            out.write(UTF8_BOM);
            out.write(builder.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the CSV export", e);
        }
        return out.toByteArray();
    }

    private void appendRow(StringBuilder builder, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(cells.get(i)));
        }
        builder.append("\r\n");
    }

    private static String escape(String value) {
        String safe = neutraliseFormula(value);
        if (safe.indexOf(',') < 0 && safe.indexOf('"') < 0 && safe.indexOf('\n') < 0
                && safe.indexOf('\r') < 0) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    /** A cell that starts with a formula trigger is prefixed so a spreadsheet shows it as text. */
    private static String neutraliseFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t'
                || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    private static String render(Object cell) {
        return cell == null ? "" : String.valueOf(cell);
    }
}

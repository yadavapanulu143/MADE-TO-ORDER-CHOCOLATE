package com.choco.reporting.adapter.out.export;

import com.choco.reporting.domain.export.ExportDataset;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Writes an export as an XLSX workbook.
 *
 * <p>The cell type is preserved: a whole number becomes a numeric cell, a decimal amount a numeric
 * cell with two decimals, an instant a text cell (a spreadsheet's date handling is locale-dependent
 * and a report should not be misread by a machine set to a different one). Keeping numbers numeric is
 * the point of the format — a seller who sums the column gets a number, not a count of text.
 *
 * <p>The dataset's column guard has already refused any authentication artefact, so the writer emits
 * exactly what it is given and nothing else.
 */
@Component
class XlsxWriter {

    byte[] write(ExportDataset dataset) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(dataset.name());
            writeHeader(workbook, sheet, dataset.headers());
            int rowIndex = 1;
            for (List<Object> values : dataset.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int column = 0; column < values.size(); column++) {
                    writeCell(row.createCell(column), values.get(column));
                }
            }
            for (int column = 0; column < dataset.columnCount(); column++) {
                sheet.autoSizeColumn(column);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the XLSX export", e);
        }
    }

    private void writeHeader(Workbook workbook, Sheet sheet, List<String> headers) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        Row row = sheet.createRow(0);
        for (int column = 0; column < headers.size(); column++) {
            Cell cell = row.createCell(column);
            cell.setCellValue(headers.get(column));
            cell.setCellStyle(style);
        }
    }

    private void writeCell(Cell cell, Object value) {
        switch (value) {
            case null -> cell.setBlank();
            case Boolean b -> cell.setCellValue(b);
            case Integer i -> cell.setCellValue(i.doubleValue());
            case Long l -> cell.setCellValue(l.doubleValue());
            case Double d -> cell.setCellValue(d);
            case BigDecimal decimal -> cell.setCellValue(decimal.doubleValue());
            case Instant instant -> cell.setCellValue(instant.toString());
            case TemporalAccessor temporal -> cell.setCellValue(temporal.toString());
            default -> cell.setCellValue(String.valueOf(value));
        }
    }
}

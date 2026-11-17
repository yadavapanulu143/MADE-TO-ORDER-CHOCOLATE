package com.choco.reporting.domain.export;

import java.util.List;

/**
 * A tabular dataset ready to be written to a document.
 *
 * <p>The rows are {@code Object}s because a cell may be text, a whole number, a decimal amount or an
 * instant, and the writer must preserve the type (a number in a spreadsheet is a number, not a string
 * that Excel guesses at). The columns are the contract: a writer emits exactly these, in this order,
 * and nothing else.
 */
public record ExportDataset(String name, List<ExportColumn> columns, List<List<Object>> rows) {

    public ExportDataset {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("An export dataset needs a name");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("An export dataset needs at least one column");
        }
        columns = List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
        int width = columns.size();
        for (List<Object> row : rows) {
            if (row.size() != width) {
                throw new IllegalArgumentException(
                        "Every export row must have exactly " + width + " cells");
            }
        }
    }

    public int columnCount() {
        return columns.size();
    }

    public int rowCount() {
        return rows.size();
    }

    public List<String> headers() {
        return columns.stream().map(ExportColumn::header).toList();
    }
}

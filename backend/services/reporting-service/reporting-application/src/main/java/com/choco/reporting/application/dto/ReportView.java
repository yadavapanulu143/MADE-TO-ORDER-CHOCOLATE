package com.choco.reporting.application.dto;

import com.choco.reporting.domain.report.ReportMetric;
import java.util.List;

/**
 * A report as returned to the seller: the headline figures plus the tabular detail.
 *
 * <p>Money is emitted as exact minor units, never a {@code double} or a pre-rounded string, for the
 * same reason it is everywhere else in the platform — a client that needs to display it can format it,
 * and nothing reintroduces a floating-point error.
 *
 * <p>The window is echoed back so a caller can confirm which period the figures cover without
 * recomputing it, and the columns name the tabular fields so the client renders headers rather than
 * guessing.
 */
public record ReportView(
        String report,
        String from,
        String to,
        List<ReportMetric> metrics,
        List<String> columns,
        List<List<Object>> rows) {

    public ReportView {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}

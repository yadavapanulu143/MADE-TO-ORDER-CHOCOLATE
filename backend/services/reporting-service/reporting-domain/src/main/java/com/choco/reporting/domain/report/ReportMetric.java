package com.choco.reporting.domain.report;

/**
 * A single headline figure on a report, such as total revenue or order count.
 *
 * <p>Kept separate from the tabular part of a report so a dashboard can show the number without
 * parsing the table. The value is an {@code Object} because a metric is sometimes money (a whole number
 * of paise), sometimes a count and sometimes an average; the client formats it.
 */
public record ReportMetric(String key, String label, Object value) {
}

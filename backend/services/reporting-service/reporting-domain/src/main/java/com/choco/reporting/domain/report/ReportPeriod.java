package com.choco.reporting.domain.report;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A reporting window: a half-open interval {@code [from, to)} in UTC.
 *
 * <p>Half-open on purpose. A sale at exactly midnight belongs to one day, not to both — a closed
 * interval would count a boundary event twice, and "revenue for Tuesday" would not match the sum of
 * Tuesday's hourly figures.
 *
 * <p>The window is capped so a report cannot be asked for an unbounded scan of the whole history,
 * which is a performance and, for a customer export, a data-protection problem.
 */
public record ReportPeriod(Instant from, Instant to) {

    /** Longest window a single report may cover. */
    public static final long MAX_DAYS = 366;

    public ReportPeriod {
        if (from == null || to == null) {
            throw new IllegalArgumentException("A reporting period needs both a start and an end");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("The reporting period start must be before its end");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new IllegalArgumentException(
                    "A reporting period may not exceed " + MAX_DAYS + " days");
        }
    }

    /** The last {@code days} days up to {@code now}, ending at the current instant. */
    public static ReportPeriod lastDays(int days, Instant now) {
        if (days < 1) {
            throw new IllegalArgumentException("The window must be at least one day");
        }
        return new ReportPeriod(now.minus(days, ChronoUnit.DAYS), now);
    }

    public boolean contains(Instant instant) {
        return instant != null && !instant.isBefore(from) && instant.isBefore(to);
    }
}

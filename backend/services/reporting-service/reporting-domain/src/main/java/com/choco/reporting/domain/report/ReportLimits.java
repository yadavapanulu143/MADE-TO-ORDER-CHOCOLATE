package com.choco.reporting.domain.report;

import com.choco.core.error.BusinessRuleException;
import com.choco.core.error.ErrorCode;

/**
 * Bounds on how much a report or export may pull in one pass.
 *
 * <p>Distinct from the HTTP page-size cap. That cap exists to stop a client enumerating the whole
 * table through the public API; a report is an aggregate the seller asked for and must cover the whole
 * window, so it reads far more than a page. It still needs a ceiling: an unbounded read is a way to
 * exhaust the heap, and a report that would exceed the ceiling must say so rather than truncate
 * silently and present a wrong total as if it were right.
 */
public final class ReportLimits {

    /** Most rows a single report or export may read. */
    public static final int MAX_REPORT_ROWS = 50_000;

    private ReportLimits() {
    }

    /**
     * Refuses a report or export whose window holds more rows than one pass may read.
     *
     * <p>A result that fills the ceiling means the window probably holds more rows than were read; the
     * total computed from a truncated set would not match the rows beneath it — a wrong answer that
     * looks right. Refusing is the honest outcome, and the seller narrows the window.
     */
    public static void requireWithinLimit(int rows) {
        if (rows >= MAX_REPORT_ROWS) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "This window holds more than " + MAX_REPORT_ROWS
                            + " rows; narrow the period and try again");
        }
    }
}

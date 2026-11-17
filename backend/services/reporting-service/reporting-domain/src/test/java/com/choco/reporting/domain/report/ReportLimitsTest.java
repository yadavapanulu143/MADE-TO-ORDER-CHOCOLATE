package com.choco.reporting.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.choco.core.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReportLimits")
class ReportLimitsTest {

    @Test
    @DisplayName("a window comfortably inside the ceiling is allowed")
    void allowsSmallWindow() {
        ReportLimits.requireWithinLimit(0);
        ReportLimits.requireWithinLimit(ReportLimits.MAX_REPORT_ROWS - 1);
    }

    @Test
    @DisplayName("a window that fills the ceiling is refused, not silently truncated")
    void refusesFullWindow() {
        assertThatThrownBy(() -> ReportLimits.requireWithinLimit(ReportLimits.MAX_REPORT_ROWS))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("narrow the period");
    }
}

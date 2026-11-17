package com.choco.reporting.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReportPeriod")
class ReportPeriodTest {

    private static final Instant NOON = Instant.parse("2026-03-10T12:00:00Z");

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("accepts a well-formed window")
        void acceptsWellFormed() {
            ReportPeriod period = new ReportPeriod(NOON.minus(1, ChronoUnit.DAYS), NOON);
            assertThat(period.from()).isBefore(period.to());
        }

        @Test
        @DisplayName("refuses a missing bound")
        void refusesMissingBound() {
            assertThatThrownBy(() -> new ReportPeriod(null, NOON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("start and an end");
            assertThatThrownBy(() -> new ReportPeriod(NOON, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a window that does not advance")
        void refusesNonAdvancing() {
            assertThatThrownBy(() -> new ReportPeriod(NOON, NOON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("before its end");
            assertThatThrownBy(() -> new ReportPeriod(NOON, NOON.minus(1, ChronoUnit.HOURS)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("refuses a window longer than the cap, so no report scans all history")
        void refusesOverlongWindow() {
            assertThatThrownBy(() -> new ReportPeriod(NOON.minus(400, ChronoUnit.DAYS), NOON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("may not exceed");
        }

        @Test
        @DisplayName("accepts a window exactly at the cap")
        void acceptsWindowAtTheCap() {
            ReportPeriod period = new ReportPeriod(NOON.minus(ReportPeriod.MAX_DAYS, ChronoUnit.DAYS), NOON);
            assertThat(ChronoUnit.DAYS.between(period.from(), period.to()))
                    .isEqualTo(ReportPeriod.MAX_DAYS);
        }
    }

    @Nested
    @DisplayName("lastDays")
    class LastDays {

        @Test
        @DisplayName("ends at the supplied instant so a test can pin it")
        void endsAtSuppliedInstant() {
            ReportPeriod period = ReportPeriod.lastDays(7, NOON);
            assertThat(period.to()).isEqualTo(NOON);
            assertThat(period.from()).isEqualTo(NOON.minus(7, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("refuses a window of less than a day")
        void refusesZeroDays() {
            assertThatThrownBy(() -> ReportPeriod.lastDays(0, NOON))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one day");
        }
    }

    @Nested
    @DisplayName("contains")
    class Contains {

        private final ReportPeriod period = new ReportPeriod(NOON.minus(1, ChronoUnit.HOURS), NOON);

        @Test
        @DisplayName("is half-open: the start is inside, the end is not")
        void isHalfOpen() {
            assertThat(period.contains(period.from())).isTrue();
            assertThat(period.contains(period.to())).isFalse();
            assertThat(period.contains(period.from().minusMillis(1))).isFalse();
        }

        @Test
        @DisplayName("a null instant is never inside")
        void nullIsNeverInside() {
            assertThat(period.contains(null)).isFalse();
        }
    }
}

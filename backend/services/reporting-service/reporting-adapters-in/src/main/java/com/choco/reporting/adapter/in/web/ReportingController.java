package com.choco.reporting.adapter.in.web;

import com.choco.reporting.application.dto.DashboardView;
import com.choco.reporting.application.dto.ReportView;
import com.choco.reporting.application.service.DashboardService;
import com.choco.reporting.application.service.ReportService;
import com.choco.reporting.domain.report.ReportPeriod;
import com.choco.reporting.domain.report.ReportType;
import com.choco.security.principal.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The seller's reports and dashboard.
 *
 * <p>Every endpoint requires {@code report:read}. A customer holds no such permission, so a customer
 * token reaching this surface is refused by the method security, not by a check this controller could
 * forget to make.
 *
 * <p>The window is supplied as explicit instants or as a "last N days" shorthand. Both are validated
 * into a {@link ReportPeriod}, which caps the window so a report cannot be turned into an unbounded
 * scan of the whole history.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reporting", description = "Seller reports and dashboard")
class ReportingController {

    private final ReportService reportService;
    private final DashboardService dashboardService;

    ReportingController(ReportService reportService, DashboardService dashboardService) {
        this.reportService = reportService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('" + Roles.PERM_REPORT_READ + "')")
    @Operation(summary = "The seller's at-a-glance dashboard for the last 30 days")
    ResponseEntity<DashboardView> dashboard() {
        return ResponseEntity.ok(dashboardService.dashboard());
    }

    @GetMapping("/reports/{type}")
    @PreAuthorize("hasAuthority('" + Roles.PERM_REPORT_READ + "')")
    @Operation(summary = "Run a named report for a window")
    ResponseEntity<ReportView> report(
            @PathVariable ReportType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(reportService.report(type, period(from, to, days)));
    }

    private ReportPeriod period(Instant from, Instant to, Integer days) {
        if (from != null && to != null) {
            return new ReportPeriod(from, to);
        }
        int window = days == null ? 30 : days;
        return reportService.defaultPeriod(window);
    }
}

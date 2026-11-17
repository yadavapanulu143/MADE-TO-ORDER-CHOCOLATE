package com.choco.reporting.adapter.in.web;

import com.choco.core.port.Clock;
import com.choco.reporting.application.service.ExportService;
import com.choco.reporting.domain.export.ExportFormat;
import com.choco.reporting.domain.export.ExportType;
import com.choco.reporting.domain.report.ReportPeriod;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import com.choco.security.principal.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer and operational exports, as CSV or XLSX.
 *
 * <p>Gated by {@code report:read} at the method; the customer export additionally needs
 * {@code customer:export}, checked again in {@link ExportService} so the permission cannot be bypassed
 * by reaching the use case another way. The document is streamed with a {@code Content-Disposition}
 * attachment header and a {@code no-store} cache directive, so a browser does not keep a copy of
 * customer data in its cache.
 */
@RestController
@RequestMapping("/api/v1/exports")
@Tag(name = "Exports", description = "CSV and XLSX exports for customers and operations")
class ExportController {

    private final ExportService exportService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    ExportController(ExportService exportService, CurrentUserProvider currentUserProvider, Clock clock) {
        this.exportService = exportService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/{type}")
    @PreAuthorize("hasAuthority('" + Roles.PERM_REPORT_READ + "')")
    @Operation(summary = "Download a dataset as CSV or XLSX")
    ResponseEntity<byte[]> export(
            @PathVariable ExportType type,
            @RequestParam(required = false, defaultValue = "CSV") String format,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer days) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        ReportPeriod period = period(from, to, days);
        ExportService.ExportDocument document = exportService.export(type, ExportFormat.from(format),
                period, caller.permissions(), caller.isSuperAdmin(), caller.userId().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(document.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(document.filename()).build());
        headers.setCacheControl("no-store");
        headers.add("X-Export-Rows", Integer.toString(document.rowCount()));
        return new ResponseEntity<>(document.bytes(), headers, org.springframework.http.HttpStatus.OK);
    }

    private ReportPeriod period(Instant from, Instant to, Integer days) {
        if (from != null && to != null) {
            return new ReportPeriod(from, to);
        }
        int window = days == null ? 30 : days;
        return ReportPeriod.lastDays(window, clock.now());
    }
}

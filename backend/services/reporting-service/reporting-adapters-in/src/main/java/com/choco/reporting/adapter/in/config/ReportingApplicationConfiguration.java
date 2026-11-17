package com.choco.reporting.adapter.in.config;

import com.choco.core.port.Clock;
import com.choco.core.port.TransactionRunner;
import com.choco.reporting.application.port.AuditLogPort;
import com.choco.reporting.application.port.ExportWriter;
import com.choco.reporting.application.service.DashboardService;
import com.choco.reporting.application.service.ExportService;
import com.choco.reporting.application.service.ProjectionService;
import com.choco.reporting.application.service.ReportService;
import com.choco.reporting.domain.port.ReportingReadModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the reporting use cases.
 *
 * <p>The use cases are plain classes built here rather than {@code @Service}-annotated, so the
 * application layer carries no Spring import and each can be constructed in a unit test with no
 * container. This mirrors the other services, so a reader who knows one knows the others.
 */
@Configuration(proxyBeanMethods = false)
class ReportingApplicationConfiguration {

    @Bean
    ProjectionService projectionService(ReportingReadModel readModel, Clock clock,
                                        TransactionRunner transactions) {
        return new ProjectionService(readModel, clock, transactions);
    }

    @Bean
    ReportService reportService(ReportingReadModel readModel, Clock clock) {
        return new ReportService(readModel, clock);
    }

    @Bean
    DashboardService dashboardService(ReportingReadModel readModel, Clock clock) {
        return new DashboardService(readModel, clock);
    }

    @Bean
    ExportService exportService(ReportingReadModel readModel, ExportWriter exportWriter,
                                AuditLogPort auditLog, Clock clock) {
        return new ExportService(readModel, exportWriter, auditLog, clock);
    }
}

package com.choco.reporting.application.dto;

import com.choco.reporting.domain.report.ReportMetric;
import java.util.List;

/**
 * The seller's dashboard: the numbers that answer "how is the business doing today".
 *
 * <p>Every figure is a count or a sum over the projections this service already holds, so the
 * dashboard is a single read with no fan-out to the other services. It carries no customer detail at
 * all — a dashboard is not a place to leak a list of people.
 */
public record DashboardView(
        List<ReportMetric> metrics,
        List<String> recentOrders,
        List<String> pendingRefunds) {

    public DashboardView {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        recentOrders = recentOrders == null ? List.of() : List.copyOf(recentOrders);
        pendingRefunds = pendingRefunds == null ? List.of() : List.copyOf(pendingRefunds);
    }
}

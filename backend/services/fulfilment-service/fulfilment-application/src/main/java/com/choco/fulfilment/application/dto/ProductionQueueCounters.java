package com.choco.fulfilment.application.dto;

/**
 * The kitchen workload at a glance.
 *
 * <p>Counts by status plus the units still occupying capacity, so the seller can see both how many
 * jobs are open and how much chocolate that actually means — five jobs of one bar is a very different
 * day from five jobs of fifty.
 */
public record ProductionQueueCounters(
        long received,
        long awaitingPreparation,
        long inPreparation,
        long preparationCompleted,
        long packaging,
        long readyForDispatch,
        long onHold,
        long cancelled,
        long unitsOccupyingCapacity,
        long total) {
}

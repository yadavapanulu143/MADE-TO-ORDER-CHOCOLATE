package com.choco.fulfilment.application.service;

import com.choco.core.error.NotFoundException;
import com.choco.core.error.ErrorCode;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.fulfilment.application.dto.ProductionJobView;
import com.choco.fulfilment.application.dto.ProductionQueueCounters;
import com.choco.fulfilment.domain.port.ProductionJobRepository;
import com.choco.fulfilment.domain.production.ProductionJob;
import com.choco.fulfilment.domain.production.ProductionStatus;
import java.util.List;
import java.util.UUID;

/**
 * Read side of the kitchen: the preparation queue, the workload counters and one job in full.
 *
 * <p>Separate from {@link ProductionService} so a read never opens a transaction or touches the
 * aggregate's behaviour. The queue is the seller's working view: what is waiting, what is being made,
 * and what is packed and ready to hand over.
 */
public class ProductionQueryService {

    /** The statuses that make up the open preparation queue, in the order the seller works them. */
    private static final List<ProductionStatus> QUEUE = List.of(
            ProductionStatus.RECEIVED,
            ProductionStatus.AWAITING_PREPARATION,
            ProductionStatus.PREPARATION_STARTED,
            ProductionStatus.ON_HOLD,
            ProductionStatus.PREPARATION_COMPLETED,
            ProductionStatus.PACKAGING,
            ProductionStatus.READY_FOR_DISPATCH);

    private final ProductionJobRepository jobRepository;

    public ProductionQueryService(ProductionJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public ProductionJobView get(UUID jobId) {
        return jobRepository.findById(jobId).map(FulfilmentViewMapper::toView)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Production job not found"));
    }

    public List<ProductionJobView> forOrder(UUID orderId) {
        return jobRepository.findByOrderId(orderId).stream().map(FulfilmentViewMapper::toView).toList();
    }

    /**
     * One of the caller's own orders' jobs, so a customer sees only their own progress.
     *
     * <p>Scoped by customer id rather than trusting the order id alone: a guessed order id belonging to
     * somebody else finds nothing, which is the same answer as an order that does not exist.
     */
    public List<ProductionJobView> forCustomerOrder(UUID customerId, UUID orderId) {
        List<ProductionJob> jobs = jobRepository.findByOrderId(orderId);
        if (jobs.isEmpty() || jobs.stream().noneMatch(job -> job.customerId().equals(customerId))) {
            throw new NotFoundException(ErrorCode.NOT_FOUND,
                    "No preparation is recorded for this order");
        }
        return jobs.stream().map(FulfilmentViewMapper::toView).toList();
    }

    /** The preparation queue, optionally narrowed to specific statuses, newest work first. */
    public PageResult<ProductionJobView> queue(List<ProductionStatus> statuses, String search,
                                               PageQuery page) {
        List<ProductionStatus> filter = statuses == null || statuses.isEmpty() ? QUEUE : statuses;
        return jobRepository.findByStatuses(filter, search, page).map(FulfilmentViewMapper::toView);
    }

    /**
     * The kitchen workload at a glance.
     *
     * <p>Counts by status plus the units still occupying capacity. The units figure is what the seller
     * actually plans a day around — five jobs of one bar is a very different day from five of fifty.
     */
    public ProductionQueueCounters counters() {
        return new ProductionQueueCounters(
                jobRepository.countByStatus(ProductionStatus.RECEIVED),
                jobRepository.countByStatus(ProductionStatus.AWAITING_PREPARATION),
                jobRepository.countByStatus(ProductionStatus.PREPARATION_STARTED),
                jobRepository.countByStatus(ProductionStatus.PREPARATION_COMPLETED),
                jobRepository.countByStatus(ProductionStatus.PACKAGING),
                jobRepository.countByStatus(ProductionStatus.READY_FOR_DISPATCH),
                jobRepository.countByStatus(ProductionStatus.ON_HOLD),
                jobRepository.countByStatus(ProductionStatus.CANCELLED),
                jobRepository.countUnitsOccupyingCapacity(),
                jobRepository.count());
    }
}

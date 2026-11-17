package com.choco.notification.application.service;

import com.choco.core.error.ErrorCode;
import com.choco.core.error.NotFoundException;
import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.notification.application.dto.NotificationCounters;
import com.choco.notification.application.dto.NotificationView;
import com.choco.notification.domain.notification.Notification;
import com.choco.notification.domain.notification.NotificationStatus;
import com.choco.notification.domain.notification.NotificationType;
import com.choco.notification.domain.port.NotificationRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read side for the notification log.
 *
 * <p>Returns {@link NotificationView}, never the aggregate, so the message body and the full recipient
 * address stay out of an administrative response. The seller needs to know whether a message went out
 * and how it fared; re-reading every message a customer received is not part of running the shop.
 */
public class NotificationQueryService {

    private final NotificationRepository repository;

    public NotificationQueryService(NotificationRepository repository) {
        this.repository = repository;
    }

    public PageResult<NotificationView> search(Collection<NotificationStatus> statuses,
                                               NotificationType type, String search, PageQuery page) {
        return repository.search(statuses, type, search, page).map(NotificationView::from);
    }

    public NotificationView find(UUID id) {
        Notification notification = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Notification not found"));
        return NotificationView.from(notification);
    }

    /** The seller's at-a-glance counters for the notification dashboard. */
    public NotificationCounters counters() {
        return new NotificationCounters(
                repository.countByStatus(NotificationStatus.PENDING),
                repository.countByStatus(NotificationStatus.SENT),
                repository.countByStatus(NotificationStatus.FAILED),
                repository.countByStatus(NotificationStatus.DEAD_LETTER),
                repository.countByStatus(NotificationStatus.SUPPRESSED));
    }

    /** Convenience for a test or an operator: the types with no template would send an empty message. */
    public List<NotificationType> typesMissingTemplates() {
        return java.util.Arrays.stream(NotificationType.values())
                .filter(type -> !com.choco.notification.application.template.NotificationTemplates
                        .hasTemplate(type))
                .toList();
    }
}

package com.choco.notification.domain.port;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.notification.domain.notification.Notification;
import com.choco.notification.domain.notification.NotificationStatus;
import com.choco.notification.domain.notification.NotificationType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for notifications.
 *
 * <p>The dedupe lookup is a first-class method rather than something the caller does with a generic
 * query, because it is the mechanism that stops a redelivered event sending a duplicate message. The
 * repository must also guarantee that two concurrent saves of the same dedupe key cannot both
 * succeed; the caller checks first, but the database is the authority.
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    /** Finds the notification already created for an event, if any. The idempotency check. */
    Optional<Notification> findByDedupeKey(String dedupeKey);

    /**
     * Returns notifications that are due for a delivery attempt: still pending and last touched
     * before {@code dueBefore}.
     *
     * <p>This is the recovery path. A notification is created in one transaction and sent in another,
     * so a crash between the two — or a provider outage — leaves a pending row. The scheduler drains
     * these, which is what makes delivery durable rather than best-effort-once.
     */
    java.util.List<Notification> findDueForAttempt(Instant dueBefore, int limit);

    /** Administrative search over the notification log. */
    PageResult<Notification> search(Collection<NotificationStatus> statuses, NotificationType type,
                                    String search, PageQuery page);

    long countByStatus(NotificationStatus status);

    long countByStatusAndType(NotificationStatus status, NotificationType type);
}

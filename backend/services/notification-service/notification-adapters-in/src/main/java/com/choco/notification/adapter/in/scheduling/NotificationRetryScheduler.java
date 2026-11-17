package com.choco.notification.adapter.in.scheduling;

import com.choco.notification.application.service.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the retry of pending notifications on a timer.
 *
 * <p>Without this, a message whose first attempt failed — or a notification recorded just before the
 * process crashed — would sit pending forever. The scheduler is the safety net that turns "we tried
 * once" into "we kept trying until it went out or we gave up".
 *
 * <p>Fixed delay, not fixed rate: if a pass takes longer than the interval, the next waits rather than
 * overlapping. Two concurrent passes would each attempt the same due notifications, and while the
 * optimistic-lock version would stop them corrupting each other, the duplicated provider calls are
 * exactly what the retry design exists to avoid.
 *
 * <p>Enabled by default because a notification service that does not retry is a notification service
 * that quietly drops messages; it can be switched off only for a test that drives the retry itself.
 */
@Component
@ConditionalOnProperty(name = "choco.notifications.retry.enabled", havingValue = "true",
        matchIfMissing = true)
class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationDispatchService dispatchService;

    NotificationRetryScheduler(NotificationDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${choco.notifications.retry.interval-ms:30000}")
    void tick() {
        int attempted = dispatchService.retryPending();
        if (attempted > 0) {
            log.info("Retried {} pending notification(s)", attempted);
        }
    }
}

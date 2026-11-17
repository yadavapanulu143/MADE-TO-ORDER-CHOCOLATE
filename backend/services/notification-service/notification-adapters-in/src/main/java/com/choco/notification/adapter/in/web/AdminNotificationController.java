package com.choco.notification.adapter.in.web;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.notification.application.dto.NotificationCounters;
import com.choco.notification.application.dto.NotificationView;
import com.choco.notification.application.service.NotificationDispatchService;
import com.choco.notification.application.service.NotificationQueryService;
import com.choco.notification.domain.notification.NotificationStatus;
import com.choco.notification.domain.notification.NotificationType;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import com.choco.security.principal.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The seller's notification log and the one action available on it.
 *
 * <p>Guarded by {@code notification:read} for the log and {@code notification:write} for a resend,
 * stated per method so a read-only support account can see delivery history without also being able to
 * re-send messages to customers.
 *
 * <p>What is returned is a {@link NotificationView}: the message body and the full recipient address
 * are deliberately absent, and the recipient is masked. A log page must not become a way to read every
 * message the business ever sent or to harvest a list of customer addresses.
 *
 * <p>The only write is a resend, and only of a notification that failed or was dead-lettered. There is
 * no endpoint that sends an arbitrary message to an arbitrary address: a system that can do that is one
 * that can be used to spam, and nothing in this business needs it.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@Tag(name = "Notification administration", description = "Delivery log and failed-message resend")
class AdminNotificationController {

    private final NotificationQueryService queryService;
    private final NotificationDispatchService dispatchService;
    private final CurrentUserProvider currentUserProvider;

    AdminNotificationController(NotificationQueryService queryService,
                                NotificationDispatchService dispatchService,
                                CurrentUserProvider currentUserProvider) {
        this.queryService = queryService;
        this.dispatchService = dispatchService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Roles.PERM_NOTIFICATION_READ + "')")
    @Operation(summary = "A page of the notification log, filterable by status, type and text")
    ResponseEntity<PageResult<NotificationView>> search(
            @RequestParam(required = false) List<NotificationStatus> status,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Boolean ascending) {
        return ResponseEntity.ok(queryService.search(status, type, q,
                PageQuery.of(page, size, sort, ascending)));
    }

    @GetMapping("/counters")
    @PreAuthorize("hasAuthority('" + Roles.PERM_NOTIFICATION_READ + "')")
    @Operation(summary = "Delivery counters: pending, sent, failed, dead-lettered and suppressed")
    ResponseEntity<NotificationCounters> counters() {
        return ResponseEntity.ok(queryService.counters());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Roles.PERM_NOTIFICATION_READ + "')")
    @Operation(summary = "One notification's delivery record, by id")
    ResponseEntity<NotificationView> find(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.find(id));
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAuthority('" + Roles.PERM_NOTIFICATION_WRITE + "')")
    @Operation(summary = "Re-send a failed or dead-lettered notification")
    ResponseEntity<NotificationView> resend(@PathVariable UUID id) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(NotificationView.from(
                dispatchService.resend(id, caller.userId().toString())));
    }
}

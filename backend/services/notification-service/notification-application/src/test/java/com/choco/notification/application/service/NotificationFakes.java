package com.choco.notification.application.service;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.notification.application.port.AuditLogPort;
import com.choco.notification.application.port.MessageDeliveryException;
import com.choco.notification.application.port.MessageSender;
import com.choco.notification.application.port.OutboundMessage;
import com.choco.notification.domain.contact.CustomerContact;
import com.choco.notification.domain.notification.Notification;
import com.choco.notification.domain.notification.NotificationStatus;
import com.choco.notification.domain.notification.NotificationType;
import com.choco.notification.domain.port.ContactDirectory;
import com.choco.notification.domain.port.NotificationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Hand-written fakes for the notification use-case tests.
 *
 * <p>Each fake keeps real behaviour — a repository that enforces the dedupe key, a sender that records
 * what it was asked to send and can be made to fail — so a test asserts on outcomes rather than on
 * which methods were called. The in-memory repository enforces the same uniqueness the database index
 * does, so a test that forgets a dedupe key fails here exactly as it would in production.
 */
final class NotificationFakes {

    private NotificationFakes() {
    }

    /** In-memory notification store, enforcing the dedupe uniqueness the real index enforces. */
    static final class InMemoryNotificationRepository implements NotificationRepository {

        private final Map<UUID, Notification> byId = new HashMap<>();

        @Override
        public Notification save(Notification notification) {
            byId.values().stream()
                    .filter(other -> !other.id().equals(notification.id()))
                    .filter(other -> other.dedupeKey().equals(notification.dedupeKey()))
                    .findFirst()
                    .ifPresent(other -> {
                        throw new IllegalStateException(
                                "duplicate dedupe key: " + notification.dedupeKey());
                    });
            byId.put(notification.id(), notification);
            return notification;
        }

        @Override
        public Optional<Notification> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Notification> findByDedupeKey(String dedupeKey) {
            return byId.values().stream().filter(n -> n.dedupeKey().equals(dedupeKey)).findFirst();
        }

        @Override
        public List<Notification> findDueForAttempt(Instant dueBefore, int limit) {
            return byId.values().stream()
                    .filter(n -> n.status() == NotificationStatus.PENDING
                            || n.status() == NotificationStatus.FAILED)
                    .filter(n -> !n.updatedAt().isAfter(dueBefore))
                    .sorted(Comparator.comparing(Notification::updatedAt))
                    .limit(limit)
                    .toList();
        }

        @Override
        public PageResult<Notification> search(Collection<NotificationStatus> statuses,
                                               NotificationType type, String search, PageQuery page) {
            List<Notification> all = byId.values().stream()
                    .filter(n -> statuses == null || statuses.isEmpty() || statuses.contains(n.status()))
                    .filter(n -> type == null || n.type() == type)
                    .toList();
            return PageResult.of(all, page.page(), page.size(), all.size());
        }

        @Override
        public long countByStatus(NotificationStatus status) {
            return byId.values().stream().filter(n -> n.status() == status).count();
        }

        @Override
        public long countByStatusAndType(NotificationStatus status, NotificationType type) {
            return byId.values().stream()
                    .filter(n -> n.status() == status && n.type() == type)
                    .count();
        }

        List<Notification> all() {
            return List.copyOf(byId.values());
        }
    }

    /** Records every message handed to it, and can be told to fail the next attempt. */
    static final class RecordingMessageSender implements MessageSender {

        final List<OutboundMessage> sent = new ArrayList<>();
        private RuntimeException nextFailure;

        @Override
        public void send(OutboundMessage message) {
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            sent.add(message);
        }

        void failNextWith(RuntimeException failure) {
            this.nextFailure = failure;
        }

        void failAlwaysWith(RuntimeException failure) {
            this.nextFailure = failure;
        }
    }

    /** A contact directory backed by a map. */
    static final class InMemoryContactDirectory implements ContactDirectory {

        private final Map<UUID, CustomerContact> contacts = new HashMap<>();

        void put(CustomerContact contact) {
            contacts.put(contact.customerId(), contact);
        }

        @Override
        public Optional<CustomerContact> find(UUID customerId) {
            return Optional.ofNullable(contacts.get(customerId));
        }
    }

    /** Records audit entries so a test can assert an administrative action was logged. */
    static final class RecordingAuditLog implements AuditLogPort {

        final List<String> actions = new ArrayList<>();

        @Override
        public void record(String actorType, String actorId, String action, String targetType,
                           String targetId, String outcome, String detail, String correlationId) {
            actions.add(actorType + ":" + actorId + ":" + action + ":" + targetId);
        }
    }

    static MessageDeliveryException deliveryFailure(String message) {
        return new MessageDeliveryException(message, new RuntimeException("transport"));
    }
}

package com.choco.notification.adapter.out.persistence;

import com.choco.core.page.PageResult;
import com.choco.core.port.PageQuery;
import com.choco.notification.domain.notification.Notification;
import com.choco.notification.domain.notification.NotificationStatus;
import com.choco.notification.domain.notification.NotificationType;
import com.choco.notification.domain.port.NotificationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link NotificationRepository}.
 *
 * <p>The unique index on {@code dedupe_key} is the real idempotency guarantee, and this adapter does
 * not hide it. When two consumers handle the same event at the same instant, both can pass the
 * application's lookup and both attempt the insert; the second violates the index. That exception is
 * translated into "the notification already exists" and the existing row is returned, so the racing
 * caller behaves exactly as if it had lost the race in the application check — which it did. Catching
 * it here, at the only place that writes, is what makes the guarantee hold under concurrency rather
 * than only in the common case.
 *
 * <p>The save path carries the current optimistic-lock version across, because the entity is rebuilt
 * from the aggregate on every save.
 */
@Repository
class NotificationRepositoryAdapter implements NotificationRepository {

    private static final List<String> SORTABLE = List.of("createdAt", "updatedAt", "type", "status");

    private final SpringDataNotificationRepository delegate;

    NotificationRepositoryAdapter(SpringDataNotificationRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = NotificationPersistenceMapper.toEntity(notification);
        delegate.findVersionById(notification.id()).ifPresent(entity::setVersion);
        try {
            delegate.saveAndFlush(entity);
        } catch (DataIntegrityViolationException violation) {
            // Lost the dedupe race. Return the winner so the caller sends nothing twice.
            return delegate.findByDedupeKey(notification.dedupeKey())
                    .map(NotificationPersistenceMapper::toDomain)
                    .orElseThrow(() -> violation);
        }
        return notification;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(UUID id) {
        return delegate.findById(id).map(NotificationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findByDedupeKey(String dedupeKey) {
        return delegate.findByDedupeKey(dedupeKey).map(NotificationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findDueForAttempt(Instant dueBefore, int limit) {
        return delegate.findDueForAttempt(dueBefore, PageRequest.of(0, limit)).stream()
                .map(NotificationPersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Notification> search(Collection<NotificationStatus> statuses,
                                           NotificationType type, String search, PageQuery page) {
        List<String> statusNames = statuses == null || statuses.isEmpty()
                ? java.util.Arrays.stream(NotificationStatus.values()).map(Enum::name).toList()
                : statuses.stream().map(Enum::name).toList();
        Page<NotificationJpaEntity> result = delegate.search(statusNames,
                type == null ? null : type.name(), likePattern(search), toPageable(page));
        List<Notification> content = result.getContent().stream()
                .map(NotificationPersistenceMapper::toDomain).toList();
        return PageResult.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(NotificationStatus status) {
        return delegate.countByStatus(status.name());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatusAndType(NotificationStatus status, NotificationType type) {
        return delegate.countByStatusAndType(status.name(), type.name());
    }

    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static Pageable toPageable(PageQuery query) {
        String sortField = query.sort() == null || !SORTABLE.contains(query.sort()) ? "createdAt"
                : query.sort();
        Sort.Direction direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(query.page(), query.size(), Sort.by(direction, sortField));
    }
}

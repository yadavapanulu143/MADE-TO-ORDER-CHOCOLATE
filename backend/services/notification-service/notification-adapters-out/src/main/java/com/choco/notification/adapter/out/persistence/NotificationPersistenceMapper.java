package com.choco.notification.adapter.out.persistence;

import com.choco.notification.domain.notification.Notification;
import com.choco.notification.domain.notification.NotificationStatus;
import com.choco.notification.domain.notification.NotificationType;
import com.choco.notification.domain.notification.RecipientType;

/**
 * Translates between the JPA entity and the domain aggregate.
 *
 * <p>Explicit rather than reflective, so a column rename cannot silently change the domain and a
 * reviewer can see, in one file, exactly which column feeds which business field. Every load uses
 * {@code rehydrate}, never the creation factory, so reading a notification cannot re-run creation
 * rules or re-raise an event.
 */
final class NotificationPersistenceMapper {

    private NotificationPersistenceMapper() {
    }

    static NotificationJpaEntity toEntity(Notification notification) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.setId(notification.id());
        entity.setType(notification.type().name());
        entity.setChannel(notification.channel().name());
        entity.setRecipientType(notification.recipientType().name());
        entity.setRecipient(notification.recipient());
        entity.setSubject(notification.subject());
        entity.setBody(notification.body());
        entity.setDedupeKey(notification.dedupeKey());
        entity.setReferenceId(notification.referenceId());
        entity.setStatus(notification.status().name());
        entity.setAttempts(notification.attempts());
        entity.setLastError(notification.lastError());
        entity.setSentAt(notification.sentAt());
        entity.setCreatedAt(notification.createdAt());
        entity.setUpdatedAt(notification.updatedAt());
        return entity;
    }

    static Notification toDomain(NotificationJpaEntity entity) {
        return Notification.rehydrate(
                entity.getId(),
                NotificationType.valueOf(entity.getType()),
                RecipientType.valueOf(entity.getRecipientType()),
                entity.getRecipient(),
                entity.getSubject(),
                entity.getBody(),
                entity.getDedupeKey(),
                entity.getReferenceId(),
                NotificationStatus.valueOf(entity.getStatus()),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getSentAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

package com.choco.notification.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for a notification.
 *
 * <p>Two things about this table carry business meaning and are enforced by the schema, not only by
 * this class. First, {@code dedupe_key} is unique: it is the database-level guarantee that a
 * redelivered event cannot produce a second message, even if two consumers race past the
 * application's own lookup. Second, {@code version} gives optimistic locking, so the scheduler and an
 * immediate send cannot both advance the same row and lose one outcome.
 *
 * <p>The rendered subject and body are stored as the message was written, not re-rendered on read, so
 * a template change cannot rewrite the history of what a customer actually received.
 *
 * <p>There is no column for a password, a token or any provider secret: this table records messages
 * the business sends, and nothing it must not store.
 */
@Entity
@Table(name = "notifications")
public class NotificationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 48, updatable = false)
    private String type;

    @Column(name = "channel", nullable = false, length = 16, updatable = false)
    private String channel;

    @Column(name = "recipient_type", nullable = false, length = 16, updatable = false)
    private String recipientType;

    @Column(name = "recipient", nullable = false, length = 320, updatable = false)
    private String recipient;

    @Column(name = "subject", length = 300)
    private String subject;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "dedupe_key", nullable = false, length = 160, updatable = false)
    private String dedupeKey;

    @Column(name = "reference_id", length = 64, updatable = false)
    private String referenceId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 300)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected NotificationJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(String recipientType) {
        this.recipientType = recipientType;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}

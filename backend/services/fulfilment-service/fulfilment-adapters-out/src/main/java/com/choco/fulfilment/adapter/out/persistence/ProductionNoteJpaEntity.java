package com.choco.fulfilment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Persistence model for one production note, owned by its job. */
@Entity
@Table(name = "production_notes")
public class ProductionNoteJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ProductionJobJpaEntity job;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Column(name = "author_id", length = 64)
    private String authorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ProductionNoteJpaEntity() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProductionJobJpaEntity getJob() { return job; }
    public void setJob(ProductionJobJpaEntity job) { this.job = job; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}

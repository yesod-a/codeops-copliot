package com.codeops.copilot.review.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_outbox_events")
public class ReviewOutboxEventEntity {
    @Id @Column(length = 36) private String id;
    @Column(name = "aggregate_id", nullable = false, length = 36) private String aggregateId;
    @Column(name = "event_type", nullable = false, length = 64) private String eventType;
    @Column(nullable = false, length = 2000) private String payload;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @Column(name = "publish_attempts", nullable = false) private int publishAttempts;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    protected ReviewOutboxEventEntity() { }
    public ReviewOutboxEventEntity(String id, String aggregateId, String eventType, String payload) { this.id = id; this.aggregateId = aggregateId; this.eventType = eventType; this.payload = payload; }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public void markPublished() { publishedAt = LocalDateTime.now(); publishAttempts++; }
    public void markFailedAttempt() { publishAttempts++; }
    public String getId() { return id; } public String getAggregateId() { return aggregateId; } public String getEventType() { return eventType; } public String getPayload() { return payload; }
    public LocalDateTime getPublishedAt() { return publishedAt; } public int getPublishAttempts() { return publishAttempts; }
}

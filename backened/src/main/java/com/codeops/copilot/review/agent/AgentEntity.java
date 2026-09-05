package com.codeops.copilot.review.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "codeops_agents")
public class AgentEntity {
    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    protected AgentEntity() {
    }

    public AgentEntity(UserEntity user, String name, String tokenHash) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.name = name;
        this.tokenHash = tokenHash;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setActive(boolean active) { this.active = active; }
    public void touch() { lastSeenAt = LocalDateTime.now(); }
}

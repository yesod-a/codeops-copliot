package com.codeops.copilot.review.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "codeops_users")
public class UserEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 120)
    private String username;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "password_hash", length = 256)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected UserEntity() {
    }

    public UserEntity(String username, String displayName, AgentRole role, String passwordHash) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public AgentRole getRole() { return role; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void update(String displayName, AgentRole role, boolean active) {
        this.displayName = displayName;
        this.role = role;
        this.active = active;
    }
}

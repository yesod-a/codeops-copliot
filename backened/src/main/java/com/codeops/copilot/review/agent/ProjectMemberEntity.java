package com.codeops.copilot.review.agent;

import com.codeops.copilot.review.persistence.ProjectEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "project_members", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectMemberRole role;

    protected ProjectMemberEntity() {
    }

    public ProjectMemberEntity(ProjectEntity project, UserEntity user, ProjectMemberRole role) {
        this.project = project;
        this.user = user;
        this.role = role;
    }

    public ProjectMemberRole getRole() { return role; }
    public ProjectEntity getProject() { return project; }
    public UserEntity getUser() { return user; }
    public void setRole(ProjectMemberRole role) { this.role = role; }
}

package com.codeops.copilot.review.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_files")
public class ReviewFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private ReviewEntity review;

    @Column(nullable = false, length = 1000)
    private String path;

    @Column(name = "git_status", length = 30)
    private String gitStatus;

    @Column(nullable = false)
    private int additions;

    @Column(nullable = false)
    private int deletions;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String patch;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ReviewFileEntity() {
    }

    public ReviewFileEntity(String path, String gitStatus, int additions, int deletions,
                            String patch, String contentHash) {
        this.path = path;
        this.gitStatus = gitStatus;
        this.additions = additions;
        this.deletions = deletions;
        this.patch = patch == null ? "" : patch;
        this.contentHash = contentHash;
    }

    void attachTo(ReviewEntity review) {
        this.review = review;
    }

    public Long getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public String getGitStatus() {
        return gitStatus;
    }

    public int getAdditions() {
        return additions;
    }

    public int getDeletions() {
        return deletions;
    }

    public String getPatch() {
        return patch;
    }

    public String getContentHash() {
        return contentHash;
    }
}

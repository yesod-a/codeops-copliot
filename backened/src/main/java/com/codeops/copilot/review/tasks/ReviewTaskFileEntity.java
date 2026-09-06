package com.codeops.copilot.review.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "review_task_files")
public class ReviewTaskFileEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id") private ReviewTaskEntity task;
    @Column(nullable = false, length = 1000) private String path;
    @Column(name = "git_status", length = 30) private String gitStatus;
    @Column(nullable = false) private int additions;
    @Column(nullable = false) private int deletions;
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT") private String patch;
    @Column(name = "content_hash", length = 64) private String contentHash;
    protected ReviewTaskFileEntity() { }
    public ReviewTaskFileEntity(String path, String gitStatus, int additions, int deletions, String patch, String contentHash) {
        this.path = path; this.gitStatus = gitStatus; this.additions = additions; this.deletions = deletions; this.patch = patch == null ? "" : patch; this.contentHash = contentHash;
    }
    void attachTo(ReviewTaskEntity value) { task = value; }
    public Long getId() { return id; } public String getPath() { return path; } public String getGitStatus() { return gitStatus; }
    public int getAdditions() { return additions; } public int getDeletions() { return deletions; } public String getPatch() { return patch; } public String getContentHash() { return contentHash; }
}

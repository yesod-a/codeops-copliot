package com.codeops.copilot.review.persistence;

import com.codeops.copilot.review.Severity;
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
import java.math.BigDecimal;

@Entity
@Table(name = "review_findings")
public class ReviewFindingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private ReviewEntity review;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private ReviewFileEntity file;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(nullable = false)
    private String suggestion;

    @Column(nullable = false)
    private String evidence;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ReviewFindingEntity() {
    }

    public ReviewFindingEntity(String category, Severity severity, int lineNumber, String message,
                               String suggestion, String evidence, double confidence, ReviewFileEntity file) {
        this.category = category;
        this.severity = severity.name();
        this.lineNumber = lineNumber;
        this.message = message;
        this.suggestion = suggestion;
        this.evidence = evidence == null ? "" : evidence;
        this.confidence = BigDecimal.valueOf(confidence);
        this.file = file;
    }

    void attachTo(ReviewEntity review) {
        this.review = review;
    }

    public String getCategory() {
        return category;
    }

    public Severity getSeverity() {
        return Severity.valueOf(severity);
    }

    public String getFilePath() {
        return file == null ? "" : file.getPath();
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getEvidence() {
        return evidence;
    }

    public double getConfidence() {
        return confidence.doubleValue();
    }
}

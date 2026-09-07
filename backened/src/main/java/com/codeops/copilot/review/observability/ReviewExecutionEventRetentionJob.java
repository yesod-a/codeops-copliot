package com.codeops.copilot.review.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class ReviewExecutionEventRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(ReviewExecutionEventRetentionJob.class);
    private final ReviewExecutionEventRepository repository;
    private final int retentionDays;

    public ReviewExecutionEventRetentionJob(ReviewExecutionEventRepository repository,
                                            @Value("${codeops.observability.event-retention-days:90}") int retentionDays) {
        this.repository = repository;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(cron = "${codeops.observability.retention-cron:0 30 2 * * *}")
    @Transactional
    public void removeExpiredEvents() {
        long deleted = repository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(retentionDays));
        if (deleted > 0) log.info("Removed {} expired review execution events", deleted);
    }
}

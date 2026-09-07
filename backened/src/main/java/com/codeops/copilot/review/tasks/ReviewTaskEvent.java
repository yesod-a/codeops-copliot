package com.codeops.copilot.review.tasks;

/** Cross-container event used to notify connected task-detail clients. */
public record ReviewTaskEvent(String taskId, String type, String status,
                              Integer groupNumber, Integer completedGroups, Integer totalGroups) { }

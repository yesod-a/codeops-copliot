package com.codeops.copilot.review.tasks;

import com.codeops.copilot.review.persistence.ReviewHistoryService;

import java.util.ArrayList;
import java.util.List;

public final class ReviewTaskGrouping {
    public static final int MAX_FILES = 6;
    public static final int MAX_CHARS = 50_000;
    public static final int MAX_SINGLE_FILE_CHARS = 30_000;

    private ReviewTaskGrouping() {
    }

    public static List<List<ReviewHistoryService.FileCommand>> group(List<ReviewHistoryService.FileCommand> files) {
        List<List<ReviewHistoryService.FileCommand>> groups = new ArrayList<>();
        List<ReviewHistoryService.FileCommand> current = new ArrayList<>();
        int currentChars = 0;
        for (ReviewHistoryService.FileCommand file : files) {
            int length = file.patch() == null ? 0 : file.patch().length();
            if (length > MAX_SINGLE_FILE_CHARS) {
                if (!current.isEmpty()) {
                    groups.add(List.copyOf(current));
                    current.clear();
                    currentChars = 0;
                }
                groups.add(List.of(file));
                continue;
            }
            if (!current.isEmpty() && (current.size() >= MAX_FILES || currentChars + length > MAX_CHARS)) {
                groups.add(List.copyOf(current));
                current.clear();
                currentChars = 0;
            }
            current.add(file);
            currentChars += length;
        }
        if (!current.isEmpty()) groups.add(List.copyOf(current));
        return List.copyOf(groups);
    }
}

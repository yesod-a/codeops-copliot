package com.codeops.copilot.review.git;

public record GitChangedFile(
        String path,
        GitFileStatus status,
        int additions,
        int deletions,
        String patch,
        boolean binary,
        boolean supported,
        String skipReason
) {
}

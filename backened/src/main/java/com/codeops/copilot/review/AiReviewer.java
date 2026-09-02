package com.codeops.copilot.review;

import java.util.List;

public interface AiReviewer {
    List<ReviewFinding> review(ReviewRequest request);
}

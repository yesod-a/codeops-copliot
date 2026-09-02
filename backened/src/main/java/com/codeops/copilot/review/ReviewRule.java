package com.codeops.copilot.review;

import java.util.List;

public interface ReviewRule {
    List<ReviewFinding> inspect(ReviewRequest request);
}

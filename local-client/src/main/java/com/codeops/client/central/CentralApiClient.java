package com.codeops.client.central;

import java.io.IOException;

public interface CentralApiClient {
    ProjectResolution resolveProject(String token, String remoteUrl) throws IOException, InterruptedException;
    CentralReviewResponse submitReview(String token, CentralReviewRequest request) throws IOException, InterruptedException;
}

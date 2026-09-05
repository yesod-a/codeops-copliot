package com.codeops.client.http;

import com.codeops.client.central.ProjectResolution;
import com.codeops.client.review.ReviewResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface LocalClientOperations {
    ProjectResolution resolve(Path repositoryPath, String remoteName) throws IOException, InterruptedException;
    ReviewResult reviewPrePush(Path repositoryPath, String remoteName, List<String> updates) throws IOException, InterruptedException;
    ReviewResult reviewPreCommit(Path repositoryPath, String remoteName) throws IOException, InterruptedException;
    ReviewResult reviewPostMerge(Path repositoryPath, String remoteName) throws IOException, InterruptedException;
}

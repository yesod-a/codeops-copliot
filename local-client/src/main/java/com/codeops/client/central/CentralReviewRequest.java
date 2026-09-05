package com.codeops.client.central;

import com.codeops.client.git.ChangedFile;
import java.util.List;

public record CentralReviewRequest(Long projectId, String repositoryKey, String trigger, String title,
                                   String branch, String headCommit, String baseRef, List<ChangedFile> files) {
    public CentralReviewRequest { files = files == null ? List.of() : List.copyOf(files); }
}

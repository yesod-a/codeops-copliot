package com.codeops.client.git;

import java.nio.file.Path;

public record LocalRepository(Path root, String remoteUrl, String branch, String headCommit) { }

package com.codeops.client.git;

public record PrePushUpdate(String localRef, String localSha, String remoteRef, String remoteSha) {
    private static final String ZERO_SHA = "0000000000000000000000000000000000000000";
    public static PrePushUpdate parse(String line) {
        String[] fields = line == null ? new String[0] : line.trim().split("\\s+");
        if (fields.length != 4) throw new IllegalArgumentException("A pre-push update must contain exactly four fields");
        return new PrePushUpdate(fields[0], fields[1], fields[2], fields[3]);
    }
    public boolean isDeletion() { return ZERO_SHA.equals(localSha); }
}

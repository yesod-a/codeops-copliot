package com.codeops.client.central;

import java.io.IOException;

public final class CentralApiException extends IOException {
    private final int statusCode;
    public CentralApiException(int statusCode) { super("Central API returned HTTP " + statusCode); this.statusCode = statusCode; }
    public int statusCode() { return statusCode; }
    public boolean isAuthorizationFailure() { return statusCode == 401 || statusCode == 403; }
}

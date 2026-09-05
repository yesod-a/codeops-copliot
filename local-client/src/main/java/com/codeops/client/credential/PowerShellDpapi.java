package com.codeops.client.credential;

import java.io.IOException;

interface PowerShellDpapi {
    String protect(String token) throws IOException;
    String unprotect(String encrypted) throws IOException;
    boolean isAvailable();
}

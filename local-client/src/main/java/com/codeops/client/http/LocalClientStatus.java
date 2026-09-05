package com.codeops.client.http;

public record LocalClientStatus(boolean configurationPresent, boolean credentialPresent) {
    public String configuration() { return configurationPresent ? "present" : "missing"; }
    public String credential() { return credentialPresent ? "present" : "missing"; }
}

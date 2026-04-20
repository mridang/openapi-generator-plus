package com.example.petstore.auth;

import java.util.Collections;
import java.util.Map;

/**
 * Authenticator for HTTP Bearer token authentication.
 */
public class BearerAuthenticator implements Authenticator {

    private final String host;
    private final String token;

    public BearerAuthenticator(String host, String token) {
        this.host = host;
        this.token = token;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public Map<String, String> getAuthHeaders() {
        return Collections.singletonMap("Authorization", "Bearer " + token);
    }
}

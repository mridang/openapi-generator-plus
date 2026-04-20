package com.example.petstore.auth;

public final class InternalApiKeyAuthenticator extends ApiKeyAuthenticator {
    public InternalApiKeyAuthenticator(String host, String apiKey) {
        super(host, "X-Internal-Key", apiKey, ApiKeyLocation.HEADER);
    }
}

package com.example.petstore.auth;

public final class ApiKeyHeaderAuthenticator extends ApiKeyAuthenticator {
    public ApiKeyHeaderAuthenticator(String host, String apiKey) {
        super(host, "X-API-Key", apiKey, ApiKeyLocation.HEADER);
    }
}

package com.example.petstore.auth.oauth;

import com.example.petstore.auth.Authenticator;
import java.util.List;

public final class BrowserAuthImplicitAuthenticator extends OAuth2ImplicitAuthenticator {
    public BrowserAuthImplicitAuthenticator(String host, String clientId) {
        super(host, clientId, "https://auth.example.com/authorize",
              List.of("read"));
    }
}

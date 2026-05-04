package com.example.petstore.auth.oauth;

import com.example.petstore.auth.Authenticator;
import java.util.List;

public final class SsoAuthAuthenticator extends OpenIdConnectAuthenticator {
    public SsoAuthAuthenticator(String host, String clientId,
            String clientSecret, String redirectUri) {
        super(host, "https://auth.example.com/.well-known/openid-configuration", clientId, clientSecret, redirectUri,
              List.of());
    }
}

package com.example.petstore.auth.oauth;

import com.example.petstore.auth.Authenticator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class OAuth2AuthorizationCodeAuthenticator implements Authenticator {

  private final String host;
  private final String clientId;
  private final String clientSecret;
  private final String authorizationUrl;
  private final String tokenUrl;
  private final String redirectUri;
  private final List<String> scopes;
  private final OAuth2TokenManager tokenManager;
  private boolean tokenExchanged;

  public OAuth2AuthorizationCodeAuthenticator(
      String host,
      String clientId,
      String clientSecret,
      String authorizationUrl,
      String tokenUrl,
      String redirectUri,
      List<String> scopes) {
    this.host = host;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authorizationUrl = authorizationUrl;
    this.tokenUrl = tokenUrl;
    this.redirectUri = redirectUri;
    this.scopes = List.copyOf(scopes);
    this.tokenManager = new OAuth2TokenManager();
  }

  public String buildAuthorizationUrl(@Nullable String state) {
    StringBuilder url = new StringBuilder(authorizationUrl);
    url.append("?response_type=code");
    url.append("&client_id=").append(encode(clientId));
    url.append("&redirect_uri=").append(encode(redirectUri));
    if (!scopes.isEmpty()) {
      url.append("&scope=").append(encode(String.join(" ", scopes)));
    }
    if (state != null) {
      url.append("&state=").append(encode(state));
    }
    return url.toString();
  }

  public void exchangeCode(String code) {
    Map<String, String> params = new HashMap<>();
    params.put("grant_type", "authorization_code");
    params.put("code", code);
    params.put("client_id", clientId);
    params.put("client_secret", clientSecret);
    params.put("redirect_uri", redirectUri);
    tokenManager.getAccessToken(tokenUrl, params);
    tokenExchanged = true;
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public Map<String, String> getAuthHeaders() {
    if (!tokenExchanged) {
      throw new IllegalStateException("Must call exchangeCode() before making API requests");
    }
    Map<String, String> params = new HashMap<>();
    params.put("grant_type", "refresh_token");
    String token = tokenManager.getAccessToken(tokenUrl, params);
    return Collections.singletonMap("Authorization", "Bearer " + token);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}

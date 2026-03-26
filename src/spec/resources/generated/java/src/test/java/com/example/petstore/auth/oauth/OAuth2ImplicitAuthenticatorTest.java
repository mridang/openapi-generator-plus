package com.example.petstore.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OAuth2ImplicitAuthenticatorTest {

  @Test
  void testBuildsAuthorizationUrl() {
    var auth =
        new OAuth2ImplicitAuthenticator(
            "http://api", "my-client-id", "http://auth/authorize", List.of("read", "write"));
    String url = auth.buildAuthorizationUrl("state123");

    assertThat(url).startsWith("http://auth/authorize?");
    assertThat(url).contains("response_type=token");
  }

  @Test
  void testIncludesClientId() {
    var auth =
        new OAuth2ImplicitAuthenticator(
            "http://api", "my-client-id", "http://auth/authorize", List.of("read"));
    String url = auth.buildAuthorizationUrl("state123");

    assertThat(url)
        .describedAs(
            "Implicit flow authorization URL must include client_id per RFC 6749 section 4.2.1")
        .contains("client_id=");
  }
}

package com.example.petstore;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultApiClientTest {

  private static final String CA_CERT_PATH = "/app/certs/ca.pem";

  @Nested
  @DisplayName("TLS verification disabled")
  class TlsVerificationDisabled {

    @Test
    @DisplayName("makes HTTPS request with verifySsl=false")
    void makesHttpsRequestWithVerifySslFalse() throws ApiException {
      String wiremockUrl = WireMockContainer.getHttpsUrl();

      Configuration config = new Configuration();
      config.setBaseUrl(wiremockUrl);
      config.setVerifySsl(false);

      DefaultApiClient client = new DefaultApiClient(config);
      ApiResponse response =
          client.sendRequest("GET", wiremockUrl + "/api/test", new HashMap<>(), null);

      assertEquals(200, response.getStatusCode());
      assertTrue(response.getBody().contains("success"));
    }
  }

  @Nested
  @DisplayName("custom CA bundle")
  class CustomCaBundle {

    @Test
    @DisplayName("makes HTTPS request with custom CA cert")
    void makesHttpsRequestWithCustomCaCert() throws ApiException {
      String wiremockUrl = WireMockContainer.getHttpsUrl();

      Configuration config = new Configuration();
      config.setBaseUrl(wiremockUrl);
      config.setVerifySsl(true);
      config.setSslCaCert(CA_CERT_PATH);

      DefaultApiClient client = new DefaultApiClient(config);
      ApiResponse response =
          client.sendRequest("GET", wiremockUrl + "/api/test", new HashMap<>(), null);

      assertEquals(200, response.getStatusCode());
      assertTrue(response.getBody().contains("success"));
    }
  }

  @Nested
  @DisplayName("HTTP proxy")
  class HttpProxy {

    @Test
    @DisplayName("makes HTTP request through proxy")
    void makesHttpRequestThroughProxy() throws ApiException {
      String wiremockUrl = WireMockContainer.getHttpUrl();
      String proxyUrl = SquidContainer.getProxyUrl();

      Configuration config = new Configuration();
      config.setBaseUrl(wiremockUrl);
      config.setProxy(proxyUrl);

      DefaultApiClient client = new DefaultApiClient(config);
      ApiResponse response =
          client.sendRequest("GET", wiremockUrl + "/api/test", new HashMap<>(), null);

      assertEquals(200, response.getStatusCode());
      assertTrue(response.getBody().contains("success"));
    }
  }

  @Nested
  @DisplayName("HTTP proxy with TLS")
  class HttpProxyWithTls {

    @Test
    @DisplayName("makes HTTPS request through proxy with verifySsl=false")
    void makesHttpsRequestThroughProxyWithVerifySslFalse() throws ApiException {
      String wiremockUrl = WireMockContainer.getHttpsUrl();
      String proxyUrl = SquidContainer.getProxyUrl();

      Configuration config = new Configuration();
      config.setBaseUrl(wiremockUrl);
      config.setProxy(proxyUrl);
      config.setVerifySsl(false);

      DefaultApiClient client = new DefaultApiClient(config);
      ApiResponse response =
          client.sendRequest("GET", wiremockUrl + "/api/test", new HashMap<>(), null);

      assertEquals(200, response.getStatusCode());
      assertTrue(response.getBody().contains("success"));
    }
  }
}

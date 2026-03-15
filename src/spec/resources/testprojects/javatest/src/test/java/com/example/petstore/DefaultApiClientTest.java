package com.example.petstore;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultApiClientTest {

    private String getEnvOrSkip(String name) {
        String value = System.getenv(name);
        Assumptions.assumeTrue(value != null && !value.isEmpty(),
                "Skipping: " + name + " not set");
        return value;
    }

    @Nested
    @DisplayName("TLS verification disabled")
    class TlsVerificationDisabled {

        @Test
        @DisplayName("makes HTTPS request with verifySsl=false")
        void makesHttpsRequestWithVerifySslFalse() throws ApiException {
            String wiremockUrl = getEnvOrSkip("WIREMOCK_HTTPS_URL");

            Configuration config = new Configuration();
            config.setBaseUrl(wiremockUrl);
            config.setVerifySsl(false);

            DefaultApiClient client = new DefaultApiClient(config);
            ApiResponse response = client.sendRequest(
                    "GET", wiremockUrl + "/api/test", new HashMap<>(), null);

            assertEquals(200, response.getStatusCode());
            assertTrue(Objects.requireNonNull(response.getBody()).contains("success"));
        }
    }

    @Nested
    @DisplayName("custom CA bundle")
    class CustomCaBundle {

        @Test
        @DisplayName("makes HTTPS request with custom CA cert")
        void makesHttpsRequestWithCustomCaCert() throws ApiException {
            String wiremockUrl = getEnvOrSkip("WIREMOCK_HTTPS_URL");
            String caCertPath = getEnvOrSkip("CA_CERT_PATH");

            Configuration config = new Configuration();
            config.setBaseUrl(wiremockUrl);
            config.setVerifySsl(true);
            config.setSslCaCert(caCertPath);

            DefaultApiClient client = new DefaultApiClient(config);
            ApiResponse response = client.sendRequest(
                    "GET", wiremockUrl + "/api/test", new HashMap<>(), null);

            assertEquals(200, response.getStatusCode());
            assertTrue(Objects.requireNonNull(response.getBody()).contains("success"));
        }
    }

    @Nested
    @DisplayName("HTTP proxy")
    class HttpProxy {

        @Test
        @DisplayName("makes HTTP request through proxy")
        void makesHttpRequestThroughProxy() throws ApiException {
            String wiremockUrl = getEnvOrSkip("WIREMOCK_HTTP_URL");
            String proxyUrl = getEnvOrSkip("PROXY_URL");

            Configuration config = new Configuration();
            config.setBaseUrl(wiremockUrl);
            config.setProxy(proxyUrl);

            DefaultApiClient client = new DefaultApiClient(config);
            ApiResponse response = client.sendRequest(
                    "GET", wiremockUrl + "/api/test", new HashMap<>(), null);

            assertEquals(200, response.getStatusCode());
            assertTrue(Objects.requireNonNull(response.getBody()).contains("success"));
        }
    }

    @Nested
    @DisplayName("HTTP proxy with TLS")
    class HttpProxyWithTls {

        @Test
        @DisplayName("makes HTTPS request through proxy with verifySsl=false")
        void makesHttpsRequestThroughProxyWithVerifySslFalse() throws ApiException {
            String wiremockUrl = getEnvOrSkip("WIREMOCK_HTTPS_URL");
            String proxyUrl = getEnvOrSkip("PROXY_URL");

            Configuration config = new Configuration();
            config.setBaseUrl(wiremockUrl);
            config.setProxy(proxyUrl);
            config.setVerifySsl(false);

            DefaultApiClient client = new DefaultApiClient(config);
            ApiResponse response = client.sendRequest(
                    "GET", wiremockUrl + "/api/test", new HashMap<>(), null);

            assertEquals(200, response.getStatusCode());
            assertTrue(Objects.requireNonNull(response.getBody()).contains("success"));
        }
    }
}

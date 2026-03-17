package com.example.petstore;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;

class DefaultApiClientUnitTest {

  private static HttpServer server;
  private static String baseUrl;

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/echo",
        exchange -> {
          byte[] requestBody = exchange.getRequestBody().readAllBytes();
          String responseJson =
              String.format(
                  "{\"method\":\"%s\",\"body\":\"%s\"}",
                  exchange.getRequestMethod(),
                  new String(requestBody, StandardCharsets.UTF_8).replace("\"", "\\\""));
          exchange.getResponseHeaders().add("X-Test-Header", "test-value");
          byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
          }
        });
    server.createContext(
        "/not-found",
        exchange -> {
          byte[] response = "not found".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(404, response.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
          }
        });
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @Test
  void sendsGetRequestAndReturnsResponse() throws Exception {
    DefaultApiClient client = new DefaultApiClient();
    ApiResponse response = client.sendRequest("GET", baseUrl + "/echo", Map.of(), null);
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"method\":\"GET\""));
  }

  @Test
  void sendsPostWithJsonBody() throws Exception {
    DefaultApiClient client = new DefaultApiClient();
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    ApiResponse response =
        client.sendRequest("POST", baseUrl + "/echo", headers, "{\"key\":\"value\"}");
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"method\":\"POST\""));
    assertTrue(response.body().contains("key"));
  }

  @Test
  void returnsResponseHeaders() throws Exception {
    DefaultApiClient client = new DefaultApiClient();
    ApiResponse response = client.sendRequest("GET", baseUrl + "/echo", Map.of(), null);
    assertNotNull(response.headers());
    // Header names may be lowercased by the HTTP client
    String value =
        response.headers().entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase("X-Test-Header"))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElse(null);
    assertEquals("test-value", value);
  }

  @Test
  void returnsNon2xxStatusCode() throws Exception {
    DefaultApiClient client = new DefaultApiClient();
    ApiResponse response = client.sendRequest("GET", baseUrl + "/not-found", Map.of(), null);
    assertEquals(404, response.statusCode());
    assertEquals("not found", response.body());
  }

  @Test
  void sendsPutRequest() throws Exception {
    DefaultApiClient client = new DefaultApiClient();
    ApiResponse response = client.sendRequest("PUT", baseUrl + "/echo", Map.of(), "update");
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"method\":\"PUT\""));
  }

  @Test
  void sendsDeleteRequest() throws Exception {
    DefaultApiClient client = new DefaultApiClient();
    ApiResponse response = client.sendRequest("DELETE", baseUrl + "/echo", Map.of(), null);
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("\"method\":\"DELETE\""));
  }
}

package com.example.petstore.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.petstore.ApiClient;
import com.example.petstore.ApiException;
import com.example.petstore.ApiResponse;
import com.example.petstore.Configuration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BaseApiTest {

  private final AtomicReference<String> capturedUrl = new AtomicReference<>();

  private BaseApi createApi() {
    ApiClient mockClient =
        (method, url, headers, body) -> {
          capturedUrl.set(url);
          return new ApiResponse(200, "{}", Map.of());
        };
    Configuration config = Configuration.builder().baseUrl("http://test").build();
    return new BaseApi(mockClient, config) {};
  }

  @Test
  void testExpandsArrayQueryParams() throws ApiException {
    BaseApi api = createApi();
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("tags", List.of("dog", "cat"));
    api.invokeApi(
        "GET",
        "/pets",
        query,
        new HashMap<>(),
        null,
        new String[] {"application/json"},
        "application/json",
        null,
        null);
    String url = capturedUrl.get();
    assertThat(url)
        .describedAs(
            "Array query params should expand to tags=dog&tags=cat, not tags=[dog, cat]")
        .contains("tags=dog")
        .contains("tags=cat")
        .doesNotContain("[")
        .doesNotContain("]");
  }

  @Test
  void testSerializesBooleanQueryParams() throws ApiException {
    BaseApi api = createApi();
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("active", true);
    api.invokeApi(
        "GET",
        "/pets",
        query,
        new HashMap<>(),
        null,
        new String[] {"application/json"},
        "application/json",
        null,
        null);
    String url = capturedUrl.get();
    assertThat(url).describedAs("Boolean should serialize as 'true'").contains("active=true");
  }

  @Test
  void testSerializesNumberQueryParams() throws ApiException {
    BaseApi api = createApi();
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("limit", 10);
    api.invokeApi(
        "GET",
        "/pets",
        query,
        new HashMap<>(),
        null,
        new String[] {"application/json"},
        "application/json",
        null,
        null);
    String url = capturedUrl.get();
    assertThat(url).describedAs("Number should serialize as '10'").contains("limit=10");
  }

  @Test
  void testHandlesEmptyQueryParams() throws ApiException {
    BaseApi api = createApi();
    Map<String, Object> query = new LinkedHashMap<>();
    api.invokeApi(
        "GET",
        "/pets",
        query,
        new HashMap<>(),
        null,
        new String[] {"application/json"},
        "application/json",
        null,
        null);
    String url = capturedUrl.get();
    assertThat(url).describedAs("Empty query params should not append '?'").doesNotContain("?");
  }
}

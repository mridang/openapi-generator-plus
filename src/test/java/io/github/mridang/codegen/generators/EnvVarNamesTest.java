package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnvVarNamesTest {

  private static List<String> names(Map<String, List<String>> fields) {
    return EnvVarNames.compute("PET", fields).stream()
        .map(e -> String.valueOf(e.get("envName")))
        .collect(Collectors.toList());
  }

  @Test
  @DisplayName("camelCase, separators and punctuation fold to one upper snake name")
  void upperSnakeFoldsEverythingToAsciiWords() {
    assertEquals("CLIENT_SECRET", EnvVarNames.upperSnake("clientSecret"));
    assertEquals("API_KEY", EnvVarNames.upperSnake("api-key"));
    assertEquals("MY_API_V2", EnvVarNames.upperSnake("my.api  v2"));
    assertEquals("HTTP_CLIENT", EnvVarNames.upperSnake("HTTPClient"));
    assertEquals("A_B", EnvVarNames.upperSnake("__a&b__"));
    assertEquals("", EnvVarNames.upperSnake("---"));
  }

  @Test
  @DisplayName("an empty prefix becomes API and a leading digit is guarded")
  void prefixIsAlwaysAUsableVariableStart() {
    assertEquals("SWAGGER_PETSTORE", EnvVarNames.prefix("Swagger Petstore"));
    assertEquals("API", EnvVarNames.prefix("!!!"));
    assertEquals("API_3D_PRINT", EnvVarNames.prefix("3d print"));
  }

  @Test
  @DisplayName("a field used by one scheme is named PREFIX_FIELD")
  void aUniqueFieldKeepsThePlainName() {
    final Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("basicAuth", List.of("username", "password"));
    fields.put("keyAuth", List.of("apiKey"));
    assertEquals(List.of("PET_USERNAME", "PET_PASSWORD", "PET_API_KEY"), names(fields));
  }

  @Test
  @DisplayName("a field two schemes share is qualified by every scheme id")
  void sharedFieldsAreQualifiedBySchemeId() {
    final Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("userToken", List.of("token"));
    fields.put("serviceToken", List.of("token"));
    fields.put("other", List.of("apiKey"));
    final List<String> names = names(fields);
    assertTrue(names.contains("PET_USER_TOKEN_TOKEN"), names.toString());
    assertTrue(names.contains("PET_SERVICE_TOKEN_TOKEN"), names.toString());
    assertTrue(names.contains("PET_API_KEY"), names.toString());
  }

  @Test
  @DisplayName("scheme ids that fold to one name get a numeric suffix in id order")
  void foldedSchemeIdsStayUnique() {
    final Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("foo-bar", List.of("token"));
    fields.put("foo_bar", List.of("token"));
    assertEquals(List.of("PET_FOO_BAR_TOKEN", "PET_FOO_BAR_TOKEN_2"), names(fields));
  }

  @Test
  @DisplayName("a multi-field scheme reports scheme id, field, snake field and name")
  void entriesCarryTheTemplateData() {
    final Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("machineAuth", List.of("clientId", "clientSecret"));
    final List<Map<String, Object>> out = EnvVarNames.compute("PET", fields);
    assertEquals(2, out.size());
    assertEquals("machineAuth", out.get(1).get("schemeId"));
    assertEquals("clientSecret", out.get(1).get("field"));
    assertEquals("CLIENT_SECRET", out.get(1).get("fieldSnake"));
    assertEquals("PET_CLIENT_SECRET", out.get(1).get("envName"));
  }
}

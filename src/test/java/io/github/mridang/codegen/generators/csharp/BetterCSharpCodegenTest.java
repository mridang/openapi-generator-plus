package io.github.mridang.codegen.generators.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenMediaType;
import org.openapitools.codegen.CodegenParameter;

class BetterCSharpCodegenTest {

  @Test
  void escapesQuoteAndBackslashInAnApiKeyName() {
    assertThat(BetterCSharpCodegen.escapeStringLiteralBody("X-Api\"Key\\x"))
        .isEqualTo("X-Api\\\"Key\\\\x");
  }

  @Test
  void leavesAPlainApiKeyNameUnchanged() {
    assertThat(BetterCSharpCodegen.escapeStringLiteralBody("X-API-Key")).isEqualTo("X-API-Key");
  }
  private static Map<String, Object> arg(final String kind, final boolean nullable) {
    final Map<String, Object> arg = new HashMap<>();
    arg.put("kind", kind);
    arg.put("isOptions", "options".equals(kind));
    arg.put("isServer", "server".equals(kind));
    arg.put("nullable", nullable);
    return arg;
  }

  private static List<String> kinds(final List<Map<String, Object>> args) {
    final List<String> kinds = new ArrayList<>();
    for (final Map<String, Object> arg : args) {
      kinds.add((String) arg.get("kind"));
    }
    return kinds;
  }

  @Test
  void placesTheServerBeforeAnOptionalOptionsObject() {
    final List<Map<String, Object>> args =
        List.of(arg("path", false), arg("options", true), arg("server", true));

    assertThat(kinds(BetterCSharpCodegen.serverBeforeOptions(args)))
        .containsExactly("path", "server", "options");
  }

  @Test
  void keepsARequiredOptionsObjectBeforeTheServer() {
    final List<Map<String, Object>> args =
        List.of(arg("path", false), arg("options", false), arg("server", true));

    assertThat(kinds(BetterCSharpCodegen.serverBeforeOptions(args)))
        .containsExactly("path", "options", "server");
  }

  @Test
  void leavesAnOperationWithoutAServerUnchanged() {
    final List<Map<String, Object>> args = List.of(arg("path", false), arg("options", true));

    assertThat(kinds(BetterCSharpCodegen.serverBeforeOptions(args)))
        .containsExactly("path", "options");
  }

  private static CodegenParameter contentParameter(final String mediaType) {
    final CodegenParameter param = new CodegenParameter();
    final LinkedHashMap<String, CodegenMediaType> content = new LinkedHashMap<>();
    content.put(mediaType, new CodegenMediaType(null, null, null));
    param.setContent(content);
    return param;
  }

  @Test
  void flagsJsonContentParameters() {
    assertThat(BetterCSharpCodegen.isJsonContent(contentParameter("application/json"))).isTrue();
    assertThat(BetterCSharpCodegen.isJsonContent(contentParameter("application/vnd.api+json")))
        .isTrue();
    assertThat(
            BetterCSharpCodegen.isJsonContent(
                contentParameter("Application/JSON; charset=utf-8")))
        .isTrue();
  }

  @Test
  void doesNotFlagNonJsonOrSchemaParameters() {
    assertThat(BetterCSharpCodegen.isJsonContent(contentParameter("text/plain"))).isFalse();
    assertThat(BetterCSharpCodegen.isJsonContent(new CodegenParameter())).isFalse();
  }

  @Test
  void renamesAParameterNamedLikeAnInjectedArgument() {
    final BetterCSharpCodegen codegen = new BetterCSharpCodegen();

    assertThat(codegen.toParamName("options")).isEqualTo("optionsParam");
    assertThat(codegen.toParamName("cancellationToken")).isEqualTo("cancellationTokenParam");
    assertThat(codegen.toParamName("server")).isEqualTo("serverParam");
    assertThat(codegen.toParamName("pet_id")).isEqualTo("petId");
  }
}

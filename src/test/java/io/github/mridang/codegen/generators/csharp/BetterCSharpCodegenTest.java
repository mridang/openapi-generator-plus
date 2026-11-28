package io.github.mridang.codegen.generators.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

  @Test
  void renamesAParameterNamedLikeAPerCallOption() {
    final BetterCSharpCodegen codegen = new BetterCSharpCodegen();

    assertThat(codegen.toParamName("timeout")).isEqualTo("timeoutParam");
    assertThat(codegen.toParamName("headers")).isEqualTo("headersParam");
    assertThat(codegen.toParamName("maxRetries")).isEqualTo("maxRetriesParam");
  }

  @Test
  void namesAnIntegerEnumMemberInPascalCase() {
    final BetterCSharpCodegen codegen = new BetterCSharpCodegen();

    assertThat(codegen.toEnumVarName("1", "int")).isEqualTo("Number1");
    assertThat(codegen.toEnumVarName("-2", "long")).isEqualTo("NumberMinus2");
    assertThat(codegen.toEnumVarName("in-stock", "String")).isEqualTo("InStock");
  }

  @Test
  void writesTheReadmePathRelativeToTheProjectFolder() {
    assertThat(BetterCSharpCodegen.readmeRelativePath("src/Pkg")).isEqualTo("../../README.md");
    assertThat(BetterCSharpCodegen.readmeRelativePath("Pkg")).isEqualTo("../README.md");
  }

  @Test
  void mintsAnOptionsObjectForEveryOperation() {
    assertThat(new BetterCSharpCodegen().mintsOptionsForEveryOperation()).isTrue();
  }

  /** Exposes the shared scheme-spec builder, which is protected in the abstract generator. */
  private static final class Probe extends BetterCSharpCodegen {
    private final Map<String, SecurityScheme> schemes = new LinkedHashMap<>();

    String render(final String scheme) {
      final SecurityScheme found = Objects.requireNonNull(schemes.get(scheme));
      return renderSchemeAuthenticator(Objects.requireNonNull(buildSchemeAuthSpec(scheme, found)));
    }
  }

  private static Probe codegenWithSchemes(final String prefix, final Path outputDir) {
    final Probe codegen = new Probe();
    codegen.setOutputDir(outputDir.toString());
    codegen.schemes.put("tok", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer"));
    codegen.schemes.put("login", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"));
    codegen.schemes.put(
        "sso",
        new SecurityScheme()
            .type(SecurityScheme.Type.OPENIDCONNECT)
            .openIdConnectUrl("https://auth.example.com/.well-known/openid-configuration"));
    codegen.additionalProperties().put("envVarPrefix", prefix);
    codegen.processOpts();
    final Components components = new Components();
    codegen.schemes.forEach(components::addSecuritySchemes);
    codegen.processOpenAPI(
        new OpenAPI().info(new Info().title("Acme").version("1")).components(components));
    return codegen;
  }

  @Test
  void aSchemeAuthenticatorReadsEachCredentialFromItsSharedVariable(@TempDir final Path outputDir) {
    final String source = codegenWithSchemes("Acme SDK", outputDir).render("login");

    assertThat(source)
        .contains("string? username = null")
        .contains("ResolveCredential(username, \"ACME_SDK_USERNAME\")")
        .contains("ResolveCredential(password, \"ACME_SDK_PASSWORD\")");
  }

  @Test
  void aBearerSchemeAuthenticatorAlsoTakesATokenProvider(@TempDir final Path outputDir) {
    final String source = codegenWithSchemes("Acme SDK", outputDir).render("tok");

    assertThat(source)
        .contains("ResolveCredential(token, \"ACME_SDK_TOKEN\")")
        .contains("Func<CancellationToken, Task<string>> tokenProvider");
  }

  @Test
  void aRedirectUriIsReadAsAUri(@TempDir final Path outputDir) {
    final String source = codegenWithSchemes("Acme SDK", outputDir).render("sso");

    assertThat(source)
        .contains("Uri? redirectUri = null")
        .contains("ResolveUri(redirectUri, \"ACME_SDK_REDIRECT_URI\")")
        .contains("ResolveCredential(clientSecret, \"ACME_SDK_CLIENT_SECRET\")");
  }

  @Test
  void everySchemeAuthenticatorIsRecordedForItsTest(@TempDir final Path outputDir) {
    final Probe codegen = codegenWithSchemes("Acme SDK", outputDir);

    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> entries =
        (List<Map<String, Object>>)
            Objects.requireNonNull(codegen.additionalProperties().get("schemeAuthTests"));
    assertThat(entries).extracting(entry -> entry.get("className"))
        .containsExactly("TokAuthenticator", "LoginAuthenticator", "SsoAuthenticator");
    assertThat(entries.get(0)).containsEntry("isBearer", true);
    assertThat(entries.get(1)).containsEntry("isBasic", true);
    assertThat(entries.get(2)).containsEntry("isOAuth", true).containsEntry("namespaceSuffix", "OAuth");
  }
}

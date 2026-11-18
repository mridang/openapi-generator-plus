package io.github.mridang.codegen.generators.dart;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.CodegenProperty;

class BetterDartCodegenTest {

  @Test
  void escapesQuoteDollarAndBackslashInAnApiKeyName() {
    assertThat(BetterDartCodegen.escapeDartString("X-Api'Key$ident\\x"))
        .isEqualTo("X-Api\\'Key\\$ident\\\\x");
  }

  @Test
  void leavesAPlainApiKeyNameUnchanged() {
    assertThat(BetterDartCodegen.escapeDartString("X-API-Key")).isEqualTo("X-API-Key");
  }

  @Test
  void placesTheServerBeforeTheOptionsObject() {
    final Map<String, Object> path = Map.of("paramName", "petId");
    final Map<String, Object> options = Map.of("paramName", "options", "isOptions", true);
    final Map<String, Object> server = Map.of("paramName", "server", "isServer", true);

    assertThat(BetterDartCodegen.serverBeforeOptions(List.of(path, options, server)))
        .containsExactly(path, server, options);
  }

  @Test
  void keepsASignatureWithoutAServerUnchanged() {
    final Map<String, Object> path = Map.of("paramName", "petId");
    final Map<String, Object> options = Map.of("paramName", "options", "isOptions", true);

    assertThat(BetterDartCodegen.serverBeforeOptions(List.of(path, options)))
        .containsExactly(path, options);
  }

  @Test
  void modelsNamedLikeACoreTypeGetThePrefix() {
    final BetterDartCodegen codegen = new BetterDartCodegen();
    assertThat(codegen.toModelName("Error")).isEqualTo("ModelError");
    assertThat(codegen.toModelName("Exception")).isEqualTo("ModelException");
    assertThat(codegen.toModelName("Uri")).isEqualTo("ModelUri");
    assertThat(codegen.toModelName("Category")).isEqualTo("Category");
    assertThat(codegen.toModelName("Result")).isEqualTo("Result");
  }

  @Test
  void parametersNamedLikeInjectedArgumentsGetASuffix() {
    final BetterDartCodegen codegen = new BetterDartCodegen();
    assertThat(codegen.toParamName("options")).isEqualTo("optionsParam");
    assertThat(codegen.toParamName("server")).isEqualTo("serverParam");
    assertThat(codegen.toParamName("path")).isEqualTo("pathParam");
    assertThat(codegen.toParamName("petId")).isEqualTo("petId");
  }

  @Test
  void parametersNamedLikeAPerCallOptionGetASuffix() {
    final BetterDartCodegen codegen = new BetterDartCodegen();
    assertThat(codegen.toParamName("timeout")).isEqualTo("timeoutParam");
    assertThat(codegen.toParamName("headers")).isEqualTo("headersParam");
    assertThat(codegen.toParamName("maxRetries")).isEqualTo("maxRetriesParam");
  }

  @Test
  void packageMetadataOptionsAreRegistered() {
    final BetterDartCodegen codegen = new BetterDartCodegen();
    assertThat(codegen.cliOptions())
        .extracting(option -> option.getOpt())
        .contains("repositoryUrl", "homepageUrl", "documentationUrl", "issueTrackerUrl", "authors");
  }

  @Test
  void everyOperationGetsAnOptionsObject() {
    assertThat(new BetterDartCodegen().mintsOptionsForEveryOperation()).isTrue();
  }

  @Test
  void aSiblingDefaultOnAnEnumReferenceBecomesThePropertyDefault() {
    final CodegenProperty property = new CodegenProperty();
    property.isEnumRef = true;
    property.vendorExtensions.put("siblingDefaultIsEnum", true);
    property.vendorExtensions.put("siblingDefault", "on-hold");

    BetterDartCodegen.applySiblingEnumDefault(property);

    assertThat(property.defaultValue).isEqualTo("'on-hold'");
  }

  @Test
  void aComponentDefaultWinsOverASiblingDefault() {
    final CodegenProperty property = new CodegenProperty();
    property.isEnumRef = true;
    property.defaultValue = "'green'";
    property.vendorExtensions.put("siblingDefaultIsEnum", true);
    property.vendorExtensions.put("siblingDefault", "on-hold");

    BetterDartCodegen.applySiblingEnumDefault(property);

    assertThat(property.defaultValue).isEqualTo("'green'");
  }

  @Test
  void aSiblingDefaultOnANonEnumReferenceIsIgnored() {
    final CodegenProperty property = new CodegenProperty();
    property.isEnumRef = false;
    property.vendorExtensions.put("siblingDefaultIsEnum", false);
    property.vendorExtensions.put("siblingDefault", "x");

    BetterDartCodegen.applySiblingEnumDefault(property);

    assertThat(property.defaultValue).isNull();
  }

  private static Map<String, Object> entry(final String schemeId, final String field, final String name) {
    final Map<String, Object> entry = new HashMap<>();
    entry.put("schemeId", schemeId);
    entry.put("field", field);
    entry.put("envName", name);
    return entry;
  }

  @Test
  void basicAuthenticatorsNameTheirUsernameAndPasswordVariables() {
    final List<Map<String, Object>> arguments =
        BetterDartCodegen.environmentArguments(
            "BasicAuthenticator",
            List.of(entry("b", "username", "P_B_USERNAME"), entry("b", "password", "P_B_PASSWORD")));

    assertThat(arguments)
        .extracting(a -> a.get("argName") + "=" + a.get("envName"))
        .containsExactly("usernameVariable=P_B_USERNAME", "passwordVariable=P_B_PASSWORD");
  }

  @Test
  void bearerAndApiKeyAuthenticatorsNameOneVariable() {
    assertThat(
            BetterDartCodegen.environmentArguments(
                "BearerAuthenticator", List.of(entry("t", "token", "P_T_TOKEN"))))
        .extracting(a -> a.get("argName") + "=" + a.get("envName"))
        .containsExactly("environmentVariable=P_T_TOKEN");
    assertThat(
            BetterDartCodegen.environmentArguments(
                "ApiKeyAuthenticator", List.of(entry("k", "apiKey", "P_K_API_KEY"))))
        .extracting(a -> a.get("argName") + "=" + a.get("envName"))
        .containsExactly("environmentVariable=P_K_API_KEY");
  }

  @Test
  void anOAuthAuthenticatorNamesItsOwnVariables() {
    assertThat(
            BetterDartCodegen.environmentArguments(
                "OAuth2PasswordAuthenticator",
                List.of(
                    entry("o", "clientId", "P_O_CLIENT_ID"),
                    entry("o", "clientSecret", "P_O_CLIENT_SECRET"),
                    entry("o", "username", "P_O_USERNAME"),
                    entry("o", "password", "P_O_PASSWORD"))))
        .extracting(a -> a.get("argName") + "=" + a.get("envName"))
        .containsExactly(
            "clientIdVariable=P_O_CLIENT_ID",
            "clientSecretVariable=P_O_CLIENT_SECRET",
            "usernameVariable=P_O_USERNAME",
            "passwordVariable=P_O_PASSWORD");
    assertThat(
            BetterDartCodegen.environmentArguments(
                "OpenIdConnectAuthenticator",
                List.of(entry("s", "redirectUri", "P_S_REDIRECT_URI"))))
        .extracting(a -> a.get("argName") + "=" + a.get("envName"))
        .containsExactly("redirectUriVariable=P_S_REDIRECT_URI");
    assertThat(BetterDartCodegen.environmentArguments("BearerAuthenticator", null)).isEmpty();
  }

  @Test
  void onlyTheSchemesTheAuthenticatorsReadAreDocumented(@TempDir final Path output) {
    final OpenAPI api = new OpenAPI();
    final Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
    schemes.put("tok", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer"));
    schemes.put(
        "oauth",
        new SecurityScheme()
            .type(SecurityScheme.Type.OAUTH2)
            .flows(
                new OAuthFlows()
                    .clientCredentials(
                        new OAuthFlow().tokenUrl("https://auth.example.com/token"))));
    api.setComponents(new Components().securitySchemes(schemes));
    final BetterDartCodegen codegen = new BetterDartCodegen();
    codegen.setOutputDir(output.toString());

    codegen.processOpenAPI(api);

    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> read =
        (List<Map<String, Object>>) codegen.additionalProperties().get("credentialEnvVars");
    assertThat(read)
        .extracting(e -> e.get("schemeId") + "." + e.get("field"))
        .containsExactlyInAnyOrder("tok.token", "oauth.clientId", "oauth.clientSecret");
    assertThat(codegen.additionalProperties().get("hasCredentialEnvVars")).isEqualTo(true);
  }

  @Test
  void anOAuthWrapperIsNamedAfterItsFlow() {
    assertThat(BetterDartCodegen.wrapperClassName("machineAuth", "OAuth2ClientCredentialsAuthenticator"))
        .isEqualTo("MachineAuthClientCredentialsAuthenticator");
    assertThat(BetterDartCodegen.wrapperClassName("ssoAuth", "OpenIdConnectAuthenticator"))
        .isEqualTo("SsoAuthAuthenticator");
    assertThat(BetterDartCodegen.wrapperClassName("svc", "BearerAuthenticator"))
        .isEqualTo("SvcAuthenticator");
  }

  @Test
  void theGeneratedAuthenticatorTestGroupsVariablesByScheme() {
    final List<Map<String, Object>> schemes =
        BetterDartCodegen.credentialSchemes(
            List.of(
                entry("petBasic", "username", "P_PET_BASIC_USERNAME"),
                entry("petBasic", "password", "P_PET_BASIC_PASSWORD"),
                entry("svc", "token", "P_SVC_TOKEN")));

    assertThat(schemes).hasSize(2);
    assertThat(schemes.get(0))
        .containsEntry("className", "PetBasicAuthenticator")
        .containsEntry(
            "environment", "{'P_PET_BASIC_USERNAME': 'value', 'P_PET_BASIC_PASSWORD': 'value'}")
        .containsEntry("firstVariable", "P_PET_BASIC_USERNAME");
    assertThat(schemes.get(1)).containsEntry("className", "SvcAuthenticator");
  }

  @Test
  void theClientTakesOneCredentialPerSchemeNamedAfterIt() {
    final OpenAPI api = new OpenAPI();
    final Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
    schemes.put("petStoreBearer", new SecurityScheme().type(SecurityScheme.Type.HTTP));
    schemes.put("admin-basic", new SecurityScheme().type(SecurityScheme.Type.HTTP));
    api.setComponents(new Components().securitySchemes(schemes));

    assertThat(BetterDartCodegen.clientSchemes(api))
        .extracting(e -> e.get("schemeName") + "=" + e.get("paramName"))
        .containsExactly("petStoreBearer=petStoreBearer", "admin-basic=adminBasic");
    assertThat(BetterDartCodegen.clientSchemes(new OpenAPI())).isEmpty();
  }

  @Test
  void anOptionsObjectNeverCarriesAnAuthenticator() {
    final org.openapitools.codegen.CodegenOperation op = new org.openapitools.codegen.CodegenOperation();
    op.operationId = "addThing";
    op.hasAuthMethods = true;

    final String source = new BetterDartCodegen().generateOptionsFileContent(op, List.of(), "AddThingOptions");

    assertThat(source).doesNotContain("Authenticator").contains("final int? timeout;");
  }
}

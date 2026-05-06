package io.github.mridang.codegen.generators.elixir;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates an Elixir API client that uses Req for HTTP transport
 * and Jason for JSON serialization. Module names use PascalCase,
 * function and variable names use snake_case per Elixir convention.
 * Filenames use snake_case. The formatter pass invokes mix format
 * inside Docker to enforce canonical Elixir formatting.
 */
@SuppressWarnings("unused")
public class BetterElixirCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterElixirCodegen.class);

    protected String packageName = "petstore_client";
    protected String packageVersion = "1.0.0";
    protected String moduleName = "PetstoreClient";

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Elixir language. Type mappings convert OpenAPI
     * types to their Elixir equivalents (e.g. integer to integer,
     * string to String.t()). Reserved words are loaded from a
     * bundled word-list.
     */
    public BetterElixirCodegen() {
        outputFolder = Path.of("generated-code", "elixir").toString();
        embeddedTemplateDir = templateDir = "templates/elixir";

        modelTemplateFiles.put("models/model.mustache", ".ex");
        apiTemplateFiles.put("api/api.mustache", ".ex");

        modelPackage = "models";
        apiPackage = "api";

        typeMapping.put("string", "String.t()");
        typeMapping.put("boolean", "boolean()");
        typeMapping.put("int", "integer()");
        typeMapping.put("integer", "integer()");
        typeMapping.put("long", "integer()");
        typeMapping.put("short", "integer()");
        typeMapping.put("float", "float()");
        typeMapping.put("double", "float()");
        typeMapping.put("number", "float()");
        typeMapping.put("decimal", "float()");
        typeMapping.put("date", "String.t()");
        typeMapping.put("DateTime", "String.t()");
        typeMapping.put("array", "list");
        typeMapping.put("List", "list");
        typeMapping.put("set", "MapSet.t()");
        typeMapping.put("map", "map()");
        typeMapping.put("object", "map()");
        typeMapping.put("AnyType", "any()");
        typeMapping.put("file", "binary()");
        typeMapping.put("binary", "binary()");
        typeMapping.put("ByteArray", "binary()");
        typeMapping.put("UUID", "String.t()");
        typeMapping.put("URI", "String.t()");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "String.t()",
                                "boolean()",
                                "integer()",
                                "float()",
                                "binary()",
                                "map()",
                                "any()",
                                "atom()",
                                "list",
                                "nil"));

        reservedWords = loadReservedWords("/reserved-words/elixir.txt");
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "elixir-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Elixir client with Req and Jason.";
    }

    /**
     * Declares Elixir as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.ELIXIR;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "test/fixtures";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "test/spec";
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getVarCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getOperationIdCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getEnumCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "elixir:1.17";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {"mix format"};
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("integer()", "float()");
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '"';
    }

    /**
     * Formats an array type declaration using Elixir list syntax.
     * Returns {@code [innerType]}.
     */
    @Override
    protected String formatArrayType(String containerType, String innerType) {
        return "[" + innerType + "]";
    }

    /**
     * Formats a map type declaration using Elixir map syntax.
     * Returns {@code %{String.t() => valueType}}.
     */
    @Override
    protected String formatMapType(String containerType, String keyType, String valueType) {
        return "%{" + keyType + " => " + valueType + "}";
    }

    /**
     * Returns {@code String.t()} as the map key type because Elixir
     * maps use string keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "String.t()";
    }

    /**
     * Returns {@code any()} as the default map value type
     * for Elixir's dynamic type.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "any()";
    }

    /**
     * Resolves user-supplied options and registers all supporting
     * files for the Elixir mix project structure. Sets up the
     * source layout with models, api, errors, auth, and supporting
     * infrastructure in the lib/ directory.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        moduleName = getPropertyOrDefault("moduleName", moduleName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put("moduleName", moduleName);
        additionalProperties.put(
                "userAgentDefault", packageName + "/" + packageVersion + " (elixir)");

        final String libDir = Path.of("lib", packageName).toString();

        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", libDir, "configuration.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options.mustache", libDir, "transport_options.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache", libDir, "server_configuration.ex"));
        supportingFiles.add(
                new SupportingFile("servers.mustache", libDir, "servers.ex"));
        supportingFiles.add(
                new SupportingFile("api_error.mustache", libDir, "api_error.ex"));

        final String errorsDir = Path.of(libDir, "errors").toString();
        supportingFiles.add(
                new SupportingFile(
                        "errors/client_error.mustache", errorsDir, "client_error.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/server_error.mustache", errorsDir, "server_error.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/bad_request_error.mustache",
                        errorsDir,
                        "bad_request_error.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unauthorized_error.mustache",
                        errorsDir,
                        "unauthorized_error.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/forbidden_error.mustache",
                        errorsDir,
                        "forbidden_error.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/not_found_error.mustache",
                        errorsDir,
                        "not_found_error.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/conflict_error.mustache",
                        errorsDir,
                        "conflict_error.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unprocessable_entity_error.mustache",
                        errorsDir,
                        "unprocessable_entity_error.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/internal_server_error.mustache",
                        errorsDir,
                        "internal_server_error.ex"));

        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", libDir, "header_selector.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", libDir, "object_serializer.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "value_serializer.mustache", libDir, "value_serializer.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", libDir, "trace_context_util.ex"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", libDir, "api_response.ex"));
        supportingFiles.add(
                new SupportingFile("api_result.mustache", libDir, "api_result.ex"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", libDir, "api_client.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", libDir, "default_api_client.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        Path.of(libDir, "api").toString(),
                        "base_api.ex"));
        supportingFiles.add(
                new SupportingFile("authenticator.mustache", Path.of(libDir, "auth").toString(), "authenticator.ex"));

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", libDir, clientClassFile + ".ex"));

        supportingFiles.add(
                new SupportingFile("main_module.mustache", "lib", packageName + ".ex"));
        supportingFiles.add(
                new SupportingFile("mix_exs.mustache", "", "mix.exs"));
        supportingFiles.add(
                new SupportingFile("coveralls_json.mustache", "", "coveralls.json"));
        supportingFiles.add(
                new SupportingFile("formatter_exs.mustache", "", ".formatter.exs"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_helper.mustache", "test", "test_helper.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet_api_test.mustache", "test", "pet_api_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store_api_test.mustache",
                            "test",
                            "store_api_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "test",
                            "default_api_client_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "test",
                            "default_api_client_unit_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "test",
                            "transport_options_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "test",
                            "header_selector_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "test",
                            "object_serializer_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "test",
                            "value_serializer_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "test",
                            "trace_context_util_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache", "test", "base_api_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration_test.mustache",
                            "test",
                            "configuration_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "test",
                            "client_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache", "test", "metadata_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "test",
                            "composed_schema_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_token_manager_test.mustache",
                            "test",
                            "oauth2_token_manager_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_auth_code_authenticator_test.mustache",
                            "test",
                            "oauth2_auth_code_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_implicit_authenticator_test.mustache",
                            "test",
                            "oauth2_implicit_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_client_credentials_authenticator_test.mustache",
                            "test",
                            "oauth2_client_credentials_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_password_authenticator_test.mustache",
                            "test",
                            "oauth2_password_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/openid_connect_authenticator_test.mustache",
                            "test",
                            "openid_connect_authenticator_test.exs"));
        }
    }

    /**
     * Returns the output directory for model source files.
     * Elixir models go in lib/{package_name}/models/.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, "lib", packageName, "models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * Elixir APIs go in lib/{package_name}/api/.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, "lib", packageName, "api").toString();
    }

    /**
     * Returns nil (null) for default values because Elixir
     * uses pattern matching and default function arguments
     * rather than inline field defaults.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (resolved.getDefault() != null) {
            if (ModelUtils.isStringSchema(resolved)) {
                return "\"" + escapeText(String.valueOf(resolved.getDefault())) + "\"";
            } else if (ModelUtils.isBooleanSchema(resolved)) {
                return resolved.getDefault().toString();
            } else if (ModelUtils.isIntegerSchema(resolved)
                    || ModelUtils.isNumberSchema(resolved)) {
                return resolved.getDefault().toString();
            }
        }
        return null;
    }

    /**
     * Fixes enum default values that the base class sets to
     * Java-style enum references (e.g. "StatusEnum.Placed").
     * For Elixir, enum fields should use a string literal.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            for (final CodegenProperty prop : model.vars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.allVars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.optionalVars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.requiredVars) {
                fixEnumDefaultValue(prop);
            }
        }
        return result;
    }

    private void fixEnumDefaultValue(CodegenProperty prop) {
        if (prop.defaultValue != null && prop.isEnum && prop.defaultValue.contains(".")) {
            final String enumValue = prop.defaultValue.substring(
                    prop.defaultValue.lastIndexOf('.') + 1);
            prop.defaultValue = "\"" + enumValue.toLowerCase(java.util.Locale.ROOT) + "\"";
        }
    }

    /**
     * Registers supporting files for each authentication scheme
     * present in the OpenAPI spec. Auth files go in
     * lib/{package_name}/auth/.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String libDir = Path.of("lib", packageName).toString();
        final String authDir = Path.of(libDir, "auth").toString();
        final String oauthDir = Path.of(authDir, "oauth").toString();

        supportingFiles.add(
                new SupportingFile(
                        "auth/base_authenticator.mustache",
                        authDir,
                        "base_authenticator.ex"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        authDir,
                        "http_aware_authenticator.ex"));

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic_authenticator.mustache",
                            authDir,
                            "basic_authenticator.ex"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer_authenticator.mustache",
                            authDir,
                            "bearer_authenticator.ex"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_location.mustache",
                            authDir,
                            "api_key_location.ex"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_authenticator.mustache",
                            authDir,
                            "api_key_authenticator.ex"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_token_manager.mustache",
                            oauthDir,
                            "oauth2_token_manager.ex"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_client_credentials_authenticator.mustache",
                            oauthDir,
                            "oauth2_client_credentials_authenticator.ex"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_password_authenticator.mustache",
                            oauthDir,
                            "oauth2_password_authenticator.ex"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_auth_code_authenticator.mustache",
                            oauthDir,
                            "oauth2_auth_code_authenticator.ex"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_implicit_authenticator.mustache",
                            oauthDir,
                            "oauth2_implicit_authenticator.ex"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/openid_connect_authenticator.mustache",
                            oauthDir,
                            "openid_connect_authenticator.ex"));
        }

        if (openAPI.getComponents() == null
                || openAPI.getComponents().getSecuritySchemes() == null) {
            return;
        }

        for (final Map.Entry<String, SecurityScheme> entry :
                openAPI.getComponents().getSecuritySchemes().entrySet()) {
            final String schemeName = entry.getKey();
            final SecurityScheme scheme = entry.getValue();
            final String className = NamingConvention.PASCAL_CASE.apply(schemeName);
            final String code = generateElixirAuthClass(schemeName, className, scheme);
            if (!code.isEmpty()) {
                final boolean isOAuth =
                        scheme.getType() == SecurityScheme.Type.OAUTH2
                                || scheme.getType() == SecurityScheme.Type.OPENIDCONNECT;
                final String folder = isOAuth ? oauthDir : authDir;
                final String suffix = getOAuthSuffix(scheme);
                final String fileName =
                        NamingConvention.SNAKE_CASE.apply(className + suffix + "Authenticator")
                                + ".ex";
                final String filePath =
                        Path.of(outputFolder, folder, fileName).toString();
                writeFile(filePath, code);
                postProcessFile(Path.of(filePath).toFile(), "source");
            }
        }
    }

    private String getOAuthSuffix(SecurityScheme scheme) {
        if (scheme.getType() != SecurityScheme.Type.OAUTH2 || scheme.getFlows() == null) {
            return "";
        }
        return Optional.ofNullable(scheme.getFlows().getClientCredentials())
                .map(f -> "ClientCredentials")
                .or(() -> Optional.ofNullable(scheme.getFlows().getPassword()).map(f -> "Password"))
                .or(() ->
                        Optional.ofNullable(scheme.getFlows().getAuthorizationCode())
                                .map(f -> "AuthorizationCode"))
                .or(() -> Optional.ofNullable(scheme.getFlows().getImplicit()).map(f -> "Implicit"))
                .orElse("");
    }

    @SuppressWarnings("StringConcatenationMissingWhitespace")
    private String generateElixirAuthClass(
            String schemeName, String className, SecurityScheme scheme) {
        final String authModule = moduleName + ".Auth";
        if (scheme.getType() == SecurityScheme.Type.HTTP) {
            if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                return renderSchemeAuth(authModule, className + "Authenticator",
                        "BasicAuthenticator", List.of(),
                        List.of(p("host", "String.t()"), p("username", "String.t()"),
                                p("password", "String.t()")),
                        List.of("host", "username", "password"));
            }
            if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                return renderSchemeAuth(authModule, className + "Authenticator",
                        "BearerAuthenticator", List.of(),
                        List.of(p("host", "String.t()"), p("token", "String.t()")),
                        List.of("host", "token"));
            }
        } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
            final String location =
                    ":" + NamingConvention.SNAKE_CASE.apply(scheme.getIn().toString());
            final String paramName = scheme.getName();
            return renderSchemeAuth(authModule, className + "Authenticator",
                    "ApiKeyAuthenticator", List.of(),
                    List.of(p("host", "String.t()"), p("api_key", "String.t()")),
                    List.of("host", "\"" + paramName + "\"", "api_key",
                            location));
        } else if (scheme.getType() == SecurityScheme.Type.OAUTH2
                && scheme.getFlows() != null) {
            return generateElixirOAuthClass(className, scheme, authModule);
        } else if (scheme.getType() == SecurityScheme.Type.OPENIDCONNECT) {
            final String url = scheme.getOpenIdConnectUrl();
            return renderSchemeAuth(authModule + ".OAuth", className + "Authenticator",
                    "OpenIdConnectAuthenticator",
                    List.of(),
                    List.of(p("host", "String.t()"), p("client_id", "String.t()"),
                            p("client_secret", "String.t()"), p("redirect_uri", "String.t()")),
                    List.of("host", "\"" + url + "\"", "client_id", "client_secret",
                            "redirect_uri", "[]"));
        }
        LOGGER.warn("Unsupported security scheme type: {}", scheme.getType());
        return "";
    }

    private String generateElixirOAuthClass(
            String className, SecurityScheme scheme, String authModule) {
        if (scheme.getFlows().getClientCredentials() != null) {
            final var flow = scheme.getFlows().getClientCredentials();
            final String tokenUrl = flow.getTokenUrl();
            return renderSchemeAuth(authModule + ".OAuth",
                    className + "ClientCredentialsAuthenticator",
                    "OAuth2ClientCredentialsAuthenticator",
                    List.of(),
                    List.of(p("host", "String.t()"), p("client_id", "String.t()"),
                            p("client_secret", "String.t()")),
                    List.of("host", "client_id", "client_secret",
                            "\"" + tokenUrl + "\"", "[]"));
        }
        if (scheme.getFlows().getPassword() != null) {
            final var flow = scheme.getFlows().getPassword();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String refreshUrlArg = refreshUrl != null ? "\"" + refreshUrl + "\"" : "nil";
            return renderSchemeAuth(authModule + ".OAuth",
                    className + "PasswordAuthenticator",
                    "OAuth2PasswordAuthenticator",
                    List.of(),
                    List.of(p("host", "String.t()"), p("client_id", "String.t()"),
                            p("client_secret", "String.t()"), p("username", "String.t()"),
                            p("password", "String.t()")),
                    List.of("host", "client_id", "client_secret",
                            "\"" + tokenUrl + "\"", refreshUrlArg,
                            "username", "password", "[]"));
        }
        if (scheme.getFlows().getAuthorizationCode() != null) {
            final var flow = scheme.getFlows().getAuthorizationCode();
            final String authUrl = flow.getAuthorizationUrl();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String refreshUrlArg = refreshUrl != null ? "\"" + refreshUrl + "\"" : "nil";
            return renderSchemeAuth(authModule + ".OAuth",
                    className + "AuthorizationCodeAuthenticator",
                    "OAuth2AuthorizationCodeAuthenticator",
                    List.of(),
                    List.of(p("host", "String.t()"), p("client_id", "String.t()"),
                            p("client_secret", "String.t()"), p("redirect_uri", "String.t()")),
                    List.of("host", "client_id", "client_secret",
                            "\"" + authUrl + "\"", "\"" + tokenUrl + "\"",
                            "redirect_uri", "[]", refreshUrlArg));
        }
        if (scheme.getFlows().getImplicit() != null) {
            final var flow = scheme.getFlows().getImplicit();
            final String authUrl = flow.getAuthorizationUrl();
            return renderSchemeAuth(authModule + ".OAuth",
                    className + "ImplicitAuthenticator",
                    "OAuth2ImplicitAuthenticator",
                    List.of(),
                    List.of(p("host", "String.t()"), p("client_id", "String.t()")),
                    List.of("host", "client_id", "\"" + authUrl + "\"", "[]"));
        }
        LOGGER.warn("Unsupported OAuth2 flow for scheme: {}", className);
        return "";
    }

    private static Map<String, String> p(String name, String type) {
        final Map<String, String> param = new HashMap<>();
        param.put("name", name);
        param.put("type", type);
        return param;
    }

    private String renderSchemeAuth(String pkg, String className, String baseClass,
            List<String> imports, List<Map<String, String>> constructorParams,
            List<String> superArgs) {
        final Map<String, Object> context = new HashMap<>();
        context.put("package", pkg);
        context.put("className", className);
        context.put("baseClass", baseClass);
        context.put("imports", imports);
        context.put("constructorParams", constructorParams);
        context.put("superArgs", superArgs);
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", context);
    }

    /**
     * Collapses runs of two or more consecutive blank lines
     * in generated {@code .ex} and {@code .exs} files into a
     * single blank line.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null) {
            return;
        }
        final String name = file.getName();
        if (!name.endsWith(".ex") && !name.endsWith(".exs")) {
            return;
        }
        cleanupElixirFile(file);
    }

    /**
     * Collapses consecutive blank lines in an Elixir source file.
     */
    private static void cleanupElixirFile(File file) {
        try {
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            final List<String> result = new ArrayList<>(lines.size());
            boolean prevBlank = false;
            boolean changed = false;

            for (final String line : lines) {
                final boolean blank = line.trim().isEmpty();
                if (blank && prevBlank) {
                    changed = true;
                    continue;
                }
                result.add(line);
                prevBlank = blank;
            }

            if (changed) {
                Files.write(file.toPath(), result, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.debug(
                    "Failed to clean up Elixir file {}: {}", file.getName(), e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", NamingConvention.SNAKE_CASE.apply(p.paramName));
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            params.add(param);
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("moduleName", moduleName);
        context.put("packageName", packageName);
        context.put("operationId", op.operationId);
        context.put("params", params);
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(
                        getOutputDir(), "lib", packageName, "api", "options", fileName + ".ex")
                .toString();
    }
}

package io.github.mridang.codegen.generators.rust;

import com.samskivert.mustache.Mustache;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenDiscriminator;
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
 * Generates a Rust API client that uses reqwest for transport
 * and serde for JSON serialization. All type names use PascalCase,
 * function and variable names use snake_case per Rust convention.
 * Filenames are snake_case. The formatter pass invokes cargo fmt
 * inside Docker to enforce canonical Rust formatting.
 */
@SuppressWarnings("unused")
public class BetterRustCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRustCodegen.class);

    protected String packageName = "openapi_client";
    protected String packageVersion = "1.0.0";

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Rust language. Type mappings convert OpenAPI
     * types to their Rust equivalents (e.g. integer to i32,
     * string to String). Reserved words are loaded from a
     * bundled word-list to avoid generating identifiers that
     * clash with Rust keywords.
     */
    public BetterRustCodegen() {
        outputFolder = Path.of("generated-code", "rust").toString();
        embeddedTemplateDir = templateDir = "templates/rust";

        modelTemplateFiles.put("models/model.mustache", ".rs");
        apiTemplateFiles.put("api/api.mustache", ".rs");

        modelPackage = "models";
        apiPackage = "api";

        typeMapping.put("string", "String");
        typeMapping.put("boolean", "bool");
        typeMapping.put("int", "i32");
        typeMapping.put("integer", "i32");
        typeMapping.put("long", "i64");
        typeMapping.put("short", "i16");
        typeMapping.put("float", "f32");
        typeMapping.put("double", "f64");
        typeMapping.put("number", "f64");
        typeMapping.put("decimal", "f64");
        typeMapping.put("date", "String");
        typeMapping.put("DateTime", "String");
        typeMapping.put("array", "Vec");
        typeMapping.put("List", "Vec");
        typeMapping.put("set", "Vec");
        typeMapping.put("map", "std::collections::HashMap");
        typeMapping.put("object", "serde_json::Value");
        typeMapping.put("AnyType", "serde_json::Value");
        typeMapping.put("file", "Vec<u8>");
        typeMapping.put("binary", "Vec<u8>");
        typeMapping.put("ByteArray", "Vec<u8>");
        typeMapping.put("UUID", "String");
        typeMapping.put("URI", "String");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "String",
                                "bool",
                                "i8",
                                "i16",
                                "i32",
                                "i64",
                                "u8",
                                "u16",
                                "u32",
                                "u64",
                                "f32",
                                "f64",
                                "char",
                                "Vec",
                                "HashMap"));

        reservedWords = loadReservedWords("/reserved-words/rust.txt");
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "rust-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Rust client with reqwest and serde.";
    }

    /**
     * Declares Rust as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.RUST;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "tests/fixtures";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "tests/spec";
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
        return NamingConvention.PASCAL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "rust:1.86";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {"rustup component add rustfmt", "cargo fmt"};
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("i32", "i64", "f32", "f64");
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '"';
    }

    /**
     * Formats an array type declaration using Rust Vec syntax.
     * Returns {@code Vec<innerType>}.
     */
    @Override
    protected String formatArrayType(String containerType, String innerType) {
        return "Vec<" + innerType + ">";
    }

    /**
     * Formats a map type declaration using Rust HashMap syntax.
     * Returns {@code std::collections::HashMap<String, valueType>}.
     */
    @Override
    protected String formatMapType(String containerType, String keyType, String valueType) {
        return "std::collections::HashMap<" + keyType + ", " + valueType + ">";
    }

    /**
     * Returns {@code String} as the map key type because Rust
     * HashMaps use String keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "String";
    }

    /**
     * Returns {@code serde_json::Value} as the default map value
     * type for Rust's dynamic JSON value type.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "serde_json::Value";
    }

    /**
     * Resolves user-supplied options and registers all
     * supporting files for the Rust crate structure.
     * Sets up the source layout including models, API
     * classes, errors, auth, configuration, and serializers
     * in the src/ directory.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put("userAgentDefault", packageName + "/" + packageVersion + " (rust)");

        additionalProperties.put(
                "rustVariantName",
                (Mustache.Lambda)
                        (fragment, writer) -> {
                            String type = fragment.execute().trim();
                            StringBuilder name = new StringBuilder();
                            boolean capitalize = true;
                            for (int i = 0; i < type.length(); i++) {
                                char c = type.charAt(i);
                                if (Character.isLetterOrDigit(c)) {
                                    name.append(capitalize
                                            ? Character.toUpperCase(c) : c);
                                    capitalize = false;
                                } else {
                                    capitalize = true;
                                }
                            }
                            writer.write(name.toString());
                        });

        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", "src", "configuration.rs"));
        supportingFiles.add(
                new SupportingFile("transport_options.mustache", "src", "transport_options.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache", "src", "server_configuration.rs"));
        supportingFiles.add(new SupportingFile("servers.mustache", "src", "servers.rs"));
        supportingFiles.add(new SupportingFile("api_error.mustache", "src", "api_error.rs"));

        supportingFiles.add(
                new SupportingFile(
                        "errors/client_error.mustache", "src/errors", "client_error.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/server_error.mustache", "src/errors", "server_error.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/bad_request_error.mustache",
                        "src/errors",
                        "bad_request_error.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unauthorized_error.mustache",
                        "src/errors",
                        "unauthorized_error.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/forbidden_error.mustache", "src/errors", "forbidden_error.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/not_found_error.mustache", "src/errors", "not_found_error.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/conflict_error.mustache", "src/errors", "conflict_error.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unprocessable_entity_error.mustache",
                        "src/errors",
                        "unprocessable_entity_error.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/internal_server_error.mustache",
                        "src/errors",
                        "internal_server_error.rs"));
        supportingFiles.add(
                new SupportingFile("errors/mod.mustache", "src/errors", "mod.rs"));

        supportingFiles.add(
                new SupportingFile(
                        "models/base64_serde.mustache", "src/models", "base64_serde.rs"));
        supportingFiles.add(
                new SupportingFile("header_selector.mustache", "src", "header_selector.rs"));
        supportingFiles.add(
                new SupportingFile("object_serializer.mustache", "src", "object_serializer.rs"));
        supportingFiles.add(
                new SupportingFile("value_serializer.mustache", "src", "value_serializer.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", "src", "trace_context_util.rs"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", "src", "api_response.rs"));
        supportingFiles.add(new SupportingFile("api_result.mustache", "src", "api_result.rs"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", "src", "api_client.rs"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", "src", "default_api_client.rs"));
        supportingFiles.add(
                new SupportingFile("base_api.mustache", "src/api", "base_api.rs"));
        supportingFiles.add(
                new SupportingFile("authenticator.mustache", Path.of("src", "auth").toString(), "authenticator.rs"));
        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", "src", clientClassFile + ".rs"));
        supportingFiles.add(new SupportingFile("lib.mustache", "src", "lib.rs"));
        supportingFiles.add(new SupportingFile("cargo_toml.mustache", "", "Cargo.toml"));
        supportingFiles.add(new SupportingFile("rustfmt_toml.mustache", "", "rustfmt.toml"));
        supportingFiles.add(
                new SupportingFile(
                        "nextest_toml.mustache",
                        Path.of(".config", "nextest").toString(),
                        "default.toml"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/testcontainers_helper.mustache",
                            "tests",
                            "testcontainers_helper.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet_api_test.mustache", "tests", "pet_api_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store_api_test.mustache", "tests", "store_api_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "tests",
                            "default_api_client_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "tests",
                            "default_api_client_unit_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "tests",
                            "transport_options_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "tests",
                            "header_selector_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "tests",
                            "object_serializer_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "tests",
                            "value_serializer_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "tests",
                            "trace_context_util_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache", "tests", "base_api_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache", "tests", "metadata_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "tests",
                            "composed_schema_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration_test.mustache",
                            "tests",
                            "configuration_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "tests",
                            "client_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_token_manager_test.mustache",
                            "tests",
                            "oauth2_token_manager_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_auth_code_authenticator_test.mustache",
                            "tests",
                            "oauth2_auth_code_authenticator_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_implicit_authenticator_test.mustache",
                            "tests",
                            "oauth2_implicit_authenticator_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_client_credentials_authenticator_test.mustache",
                            "tests",
                            "oauth2_client_credentials_authenticator_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_password_authenticator_test.mustache",
                            "tests",
                            "oauth2_password_authenticator_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/openid_connect_authenticator_test.mustache",
                            "tests",
                            "openid_connect_authenticator_test.rs"));
        }
    }

    /**
     * Returns the output directory for model source files.
     * Rust models go in the {@code src/models/} subdirectory.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, "src", "models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * Rust APIs go in the {@code src/api/} subdirectory.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, "src", "api").toString();
    }

    /**
     * Returns null for default values because Rust does not use
     * inline default values in struct field declarations. The
     * Default trait implementation handles defaults instead.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (resolved.getDefault() != null) {
            if (ModelUtils.isStringSchema(resolved)) {
                return "String::from(\"" + escapeText(String.valueOf(resolved.getDefault())) + "\")";
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
     * For Rust, enum fields typed as String should use a
     * String literal default.
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
            prop.defaultValue = "String::from(\"" + enumValue.toLowerCase(java.util.Locale.ROOT) + "\")";
        }
    }

    /**
     * Registers supporting files for each authentication scheme
     * present in the OpenAPI spec. Auth files go in the
     * {@code src/auth/} subdirectory with OAuth files in
     * {@code src/auth/oauth/}.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        "src/auth",
                        "http_aware_authenticator.rs"));
        supportingFiles.add(
                new SupportingFile("auth/mod.mustache", "src/auth", "mod.rs"));

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic_authenticator.mustache",
                            "src/auth",
                            "basic_authenticator.rs"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer_authenticator.mustache",
                            "src/auth",
                            "bearer_authenticator.rs"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_location.mustache",
                            "src/auth",
                            "api_key_location.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_authenticator.mustache",
                            "src/auth",
                            "api_key_authenticator.rs"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_token_manager.mustache",
                            "src/auth/oauth",
                            "oauth2_token_manager.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/mod.mustache", "src/auth/oauth", "mod.rs"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_client_credentials_authenticator.mustache",
                            "src/auth/oauth",
                            "oauth2_client_credentials_authenticator.rs"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_password_authenticator.mustache",
                            "src/auth/oauth",
                            "oauth2_password_authenticator.rs"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_auth_code_authenticator.mustache",
                            "src/auth/oauth",
                            "oauth2_auth_code_authenticator.rs"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_implicit_authenticator.mustache",
                            "src/auth/oauth",
                            "oauth2_implicit_authenticator.rs"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/openid_connect_authenticator.mustache",
                            "src/auth/oauth",
                            "openid_connect_authenticator.rs"));
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
            final String code = generateRustAuthClass(schemeName, className, scheme);
            if (!code.isEmpty()) {
                final boolean isOAuth =
                        scheme.getType() == SecurityScheme.Type.OAUTH2
                                || scheme.getType() == SecurityScheme.Type.OPENIDCONNECT;
                final String folder = isOAuth ? "src/auth/oauth" : "src/auth";
                final String suffix = getOAuthSuffix(scheme);
                final String fileName =
                        NamingConvention.SNAKE_CASE.apply(className + suffix + "Authenticator")
                                + ".rs";
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
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only constants")
    private String generateRustAuthClass(
            String schemeName, String className, SecurityScheme scheme) {
        if (scheme.getType() == SecurityScheme.Type.HTTP) {
            if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                return renderSchemeAuth("auth", className + "Authenticator",
                        "BasicAuthenticator", List.of(),
                        List.of(p("host", "&str"), p("username", "&str"),
                                p("password", "&str")),
                        List.of("host", "username", "password"));
            }
            if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                return renderSchemeAuth("auth", className + "Authenticator",
                        "BearerAuthenticator", List.of(),
                        List.of(p("host", "&str"), p("token", "&str")),
                        List.of("host", "token"));
            }
        } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
            final String location =
                    "ApiKeyLocation::"
                            + NamingConvention.PASCAL_CASE.apply(scheme.getIn().toString());
            final String paramName = scheme.getName();
            return renderSchemeAuth("auth", className + "Authenticator",
                    "ApiKeyAuthenticator", List.of(),
                    List.of(p("host", "&str"), p("api_key", "&str")),
                    List.of("host", "\"" + paramName + "\"", "api_key",
                            location));
        } else if (scheme.getType() == SecurityScheme.Type.OAUTH2
                && scheme.getFlows() != null) {
            return generateRustOAuthClass(className, scheme);
        } else if (scheme.getType() == SecurityScheme.Type.OPENIDCONNECT) {
            final String url = scheme.getOpenIdConnectUrl();
            return renderSchemeAuth("auth::oauth", className + "Authenticator",
                    "OpenIdConnectAuthenticator",
                    List.of(),
                    List.of(p("host", "&str"), p("client_id", "&str"),
                            p("client_secret", "&str"), p("redirect_uri", "&str")),
                    List.of("host", "\"" + url + "\"", "client_id", "client_secret",
                            "redirect_uri", "&[]"));
        }
        LOGGER.warn("Unsupported security scheme type: {}", scheme.getType());
        return "";
    }

    private String generateRustOAuthClass(
            String className, SecurityScheme scheme) {
        if (scheme.getFlows().getClientCredentials() != null) {
            final var flow = scheme.getFlows().getClientCredentials();
            final String tokenUrl = flow.getTokenUrl();
            return renderSchemeAuth("auth::oauth",
                    className + "ClientCredentialsAuthenticator",
                    "OAuth2ClientCredentialsAuthenticator",
                    List.of(),
                    List.of(p("host", "&str"), p("client_id", "&str"),
                            p("client_secret", "&str")),
                    List.of("host", "client_id", "client_secret",
                            "\"" + tokenUrl + "\"", "&[]"));
        }
        if (scheme.getFlows().getPassword() != null) {
            final var flow = scheme.getFlows().getPassword();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String refreshUrlArg = refreshUrl != null ? "Some(\"" + refreshUrl + "\")" : "None";
            return renderSchemeAuth("auth::oauth",
                    className + "PasswordAuthenticator",
                    "OAuth2PasswordAuthenticator",
                    List.of(),
                    List.of(p("host", "&str"), p("client_id", "&str"),
                            p("client_secret", "&str"), p("username", "&str"),
                            p("password", "&str")),
                    List.of("host", "client_id", "client_secret",
                            "\"" + tokenUrl + "\"", refreshUrlArg,
                            "username", "password", "&[]"));
        }
        if (scheme.getFlows().getAuthorizationCode() != null) {
            final var flow = scheme.getFlows().getAuthorizationCode();
            final String authUrl = flow.getAuthorizationUrl();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String refreshUrlArg = refreshUrl != null ? "Some(\"" + refreshUrl + "\")" : "None";
            return renderSchemeAuth("auth::oauth",
                    className + "AuthorizationCodeAuthenticator",
                    "OAuth2AuthorizationCodeAuthenticator",
                    List.of(),
                    List.of(p("host", "&str"), p("client_id", "&str"),
                            p("client_secret", "&str"), p("redirect_uri", "&str")),
                    List.of("host", "client_id", "client_secret",
                            "\"" + authUrl + "\"", "\"" + tokenUrl + "\"",
                            "redirect_uri", "&[]", refreshUrlArg));
        }
        if (scheme.getFlows().getImplicit() != null) {
            final var flow = scheme.getFlows().getImplicit();
            final String authUrl = flow.getAuthorizationUrl();
            return renderSchemeAuth("auth::oauth",
                    className + "ImplicitAuthenticator",
                    "OAuth2ImplicitAuthenticator",
                    List.of(),
                    List.of(p("host", "&str"), p("client_id", "&str")),
                    List.of("host", "client_id", "\"" + authUrl + "\"", "&[]"));
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
     * Removes the discriminator property from variant structs used
     * in internally tagged enums. Serde's {@code #[serde(tag = "...")]}
     * manages the tag field itself, so the inner struct must not
     * declare it as a field. Without this, deserialization fails
     * with "missing field" and serialization produces duplicate
     * tag fields.
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();
                if (model.discriminator != null && !model.oneOf.isEmpty()) {
                    final String discPropName = model.discriminator.getPropertyBaseName();
                    for (final CodegenDiscriminator.MappedModel mapped :
                            model.discriminator.getMappedModels()) {
                        removeDiscriminatorFromChild(
                                result, mapped.getModelName(), discPropName);
                    }
                }
            }
        }
        return result;
    }

    private static void removeDiscriminatorFromChild(
            Map<String, ModelsMap> allModels, String childName, String discPropName) {
        final ModelsMap childModels = allModels.get(childName);
        if (childModels == null) {
            return;
        }
        for (final ModelMap modelMap : childModels.getModels()) {
            final CodegenModel child = modelMap.getModel();
            child.vars.removeIf(p -> discPropName.equals(p.baseName));
            child.requiredVars.removeIf(p -> discPropName.equals(p.baseName));
            child.optionalVars.removeIf(p -> discPropName.equals(p.baseName));
            child.allVars.removeIf(p -> discPropName.equals(p.baseName));
            child.readWriteVars.removeIf(p -> discPropName.equals(p.baseName));
        }
    }

    /**
     * Collapses runs of two or more consecutive blank lines
     * in generated {@code .rs} files into a single blank line.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null) {
            return;
        }
        final String name = file.getName();
        if (!name.endsWith(".rs")) {
            return;
        }
        cleanupRustFile(file);
    }

    /**
     * Collapses consecutive blank lines in a Rust source file
     * to maintain clean formatting before cargo fmt runs.
     */
    private static void cleanupRustFile(File file) {
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
                    "Failed to clean up Rust file {}: {}", file.getName(), e.getMessage());
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
        context.put("packageName", packageName);
        context.put("operationId", op.operationId);
        context.put("params", params);
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(getOutputDir(), "src", "api", "options", fileName + ".rs").toString();
    }

    /**
     * Writes the {@code mod.rs} barrel file for the Options
     * module, declaring all generated options structs as public
     * submodules with re-exports.
     */
    @Override
    protected void writeOptionsBarrelFiles(List<Map<String, String>> optionsFiles) {
        if (optionsFiles.isEmpty()) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (final Map<String, String> meta : optionsFiles) {
            final String className =
                    Objects.requireNonNull(meta.get("optionsClassName"));
            final String modName = NamingConvention.SNAKE_CASE.apply(className);
            sb.append("mod ").append(modName).append(";\n");
            sb.append("pub use ").append(modName).append("::*;\n");
        }
        final String modPath =
                Path.of(getOutputDir(), "src", "api", "options", "mod.rs").toString();
        writeFile(modPath, sb.toString());
    }

    /**
     * Scans the models and API directories after generation to
     * write Rust module declaration files ({@code mod.rs}), then
     * delegates to the parent for Options barrel files and Docker
     * formatting.
     */
    @Override
    public void postProcess() {
        writeModFile(Path.of(getOutputDir(), "src", "models"));
        writeModFile(Path.of(getOutputDir(), "src", "api"));
        super.postProcess();
    }

    /**
     * Scans a directory for {@code .rs} files (excluding
     * {@code mod.rs}) and writes a {@code mod.rs} that declares
     * and re-exports all discovered modules. For the API
     * directory, also includes the base_api and options modules.
     */
    private void writeModFile(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            final List<String> modules =
                    entries.filter(p -> p.toString().endsWith(".rs"))
                            .map(Path::getFileName)
                            .filter(Objects::nonNull)
                            .map(Path::toString)
                            .filter(name -> !name.equals("mod.rs"))
                            .map(name -> name.replace(".rs", ""))
                            .sorted()
                            .collect(Collectors.toList());

            final StringBuilder sb = new StringBuilder();
            sb.append("#![allow(unused_imports)]\n\n");
            for (final String mod : modules) {
                sb.append("mod ").append(mod).append(";\n");
                sb.append("pub use ").append(mod).append("::*;\n");
            }

            if (dir.endsWith("api") && Files.isDirectory(dir.resolve("options"))) {
                sb.append("pub mod options;\n");
            }

            writeFile(dir.resolve("mod.rs").toString(), sb.toString());
        } catch (IOException e) {
            LOGGER.warn("Failed to write mod.rs in {}: {}", dir, e.getMessage());
        }
    }
}

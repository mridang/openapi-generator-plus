package io.github.mridang.codegen.generators.kotlin;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * Generates a Kotlin API client that uses OkHttp for transport
 * and kotlinx.serialization for JSON serialization. Targets
 * Kotlin 2.0+ and follows camelCase naming for variables and
 * methods. Output is formatted with ktlint inside Docker to
 * enforce consistent style across all generated source files.
 */
@SuppressWarnings("unused")
public class BetterKotlinCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterKotlinCodegen.class);

    private static final Set<String> NUMERIC_DATA_TYPES =
            Set.of("Int", "Long", "Double", "Float", "Short", "BigDecimal");

    protected String sourceFolder = Path.of("src", "main", "kotlin").toString();
    protected String invokerPackage = "org.openapitools";

    /**
     * Initializes all Kotlin-specific type mappings, import
     * mappings, language primitives, and template file
     * registrations. Uses standard Kotlin types and
     * kotlinx.serialization annotations for model schemas.
     */
    public BetterKotlinCodegen() {
        outputFolder = "generated-code/kotlin";
        embeddedTemplateDir = templateDir = "templates/kotlin";

        modelTemplateFiles.put("models/model.mustache", ".kt");
        apiTemplateFiles.put("api/api.mustache", ".kt");

        typeMapping.put("array", "List");
        typeMapping.put("map", "Map");
        typeMapping.put("set", "Set");
        typeMapping.put("boolean", "Boolean");
        typeMapping.put("string", "String");
        typeMapping.put("int", "Int");
        typeMapping.put("integer", "Int");
        typeMapping.put("long", "Long");
        typeMapping.put("short", "Short");
        typeMapping.put("float", "Float");
        typeMapping.put("double", "Double");
        typeMapping.put("number", "BigDecimal");
        typeMapping.put("decimal", "BigDecimal");
        typeMapping.put("char", "String");
        typeMapping.put("object", "Any");
        typeMapping.put("AnyType", "Any");
        typeMapping.put("binary", "ByteArray");
        typeMapping.put("ByteArray", "ByteArray");
        typeMapping.put("byte", "ByteArray");
        typeMapping.put("file", "ByteArray");
        typeMapping.put("File", "ByteArray");
        typeMapping.put("date", "LocalDate");
        typeMapping.put("DateTime", "OffsetDateTime");
        typeMapping.put("date-time", "OffsetDateTime");
        typeMapping.put("UUID", "String");
        typeMapping.put("URI", "String");
        typeMapping.put("BigDecimal", "BigDecimal");

        importMapping.put("List", "kotlin.collections.List");
        importMapping.put("Set", "kotlin.collections.Set");
        importMapping.put("Map", "kotlin.collections.Map");
        importMapping.put("HashMap", "kotlin.collections.HashMap");
        importMapping.put("ArrayList", "kotlin.collections.ArrayList");
        importMapping.put("LinkedHashSet", "kotlin.collections.LinkedHashSet");
        importMapping.put("LocalDate", "java.time.LocalDate");
        importMapping.put("OffsetDateTime", "java.time.OffsetDateTime");
        importMapping.put("BigDecimal", "java.math.BigDecimal");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "Int",
                                "Long",
                                "Float",
                                "Double",
                                "Boolean",
                                "String",
                                "Any",
                                "ByteArray",
                                "Short",
                                "Byte",
                                "Char",
                                "Unit"));

        instantiationTypes.put("array", "ArrayList");
        instantiationTypes.put("set", "LinkedHashSet");
        instantiationTypes.put("map", "HashMap");

        reservedWords = loadReservedWords("/reserved-words/kotlin.txt");
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "kotlin-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Kotlin client with OkHttp and kotlinx.serialization.";
    }

    /** {@inheritDoc} */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.KOTLIN;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "src/test/resources";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "src/spec/kotlin";
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getVarCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getOperationIdCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getEnumCasing() {
        return NamingConvention.UPPER_SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "eclipse-temurin:21-jdk";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            "curl -sL -o /tmp/ktlint https://github.com/pinterest/ktlint/releases/download/1.5.0/ktlint",
            "chmod +x /tmp/ktlint",
            "/tmp/ktlint --format '**/*.kt'"
        };
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "LinkedHashSet<";
    }

    /**
     * Preserves all-uppercase identifiers (e.g. {@code MAX_RETRIES},
     * {@code HTTP_METHOD}) as-is instead of camelCasing them.
     */
    @Override
    protected UppercaseIdentifierStrategy getUppercaseIdentifierStrategy() {
        return UppercaseIdentifierStrategy.PRESERVE;
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return NUMERIC_DATA_TYPES;
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '"';
    }

    /**
     * Processes user-supplied codegen options after they are
     * resolved. Reads the invoker package, build coordinates,
     * and source folder, then registers all supporting files
     * for the client skeleton, exceptions, auth, serialization,
     * and optional test scaffolding.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        sourceFolder = getPropertyOrDefault("sourceFolder", sourceFolder);
        invokerPackage = getPropertyOrDefault("invokerPackage", invokerPackage);
        additionalProperties.put("invokerPackage", invokerPackage);
        additionalProperties.put("userAgentDefault", invokerPackage + "/1.0.0 (kotlin)");

        final String groupId = getPropertyOrDefault("groupId", invokerPackage);
        additionalProperties.put("groupId", groupId);
        final String artifactId =
                getPropertyOrDefault("artifactId", "openapi-kotlin-client");
        additionalProperties.put("artifactId", artifactId);
        final String artifactVersion = getPropertyOrDefault("artifactVersion", "1.0.0");
        additionalProperties.put("artifactVersion", artifactVersion);

        final String invokerFolder =
                Path.of(sourceFolder, invokerPackage.replace(".", "/")).toString();
        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.kt"));

        final String exceptionsFolder = Path.of(invokerFolder, "exceptions").toString();
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ClientException.mustache",
                        exceptionsFolder,
                        "ClientException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ServerException.mustache",
                        exceptionsFolder,
                        "ServerException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/BadRequestException.mustache",
                        exceptionsFolder,
                        "BadRequestException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/UnauthorizedException.mustache",
                        exceptionsFolder,
                        "UnauthorizedException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ForbiddenException.mustache",
                        exceptionsFolder,
                        "ForbiddenException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/NotFoundException.mustache",
                        exceptionsFolder,
                        "NotFoundException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ConflictException.mustache",
                        exceptionsFolder,
                        "ConflictException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/UnprocessableEntityException.mustache",
                        exceptionsFolder,
                        "UnprocessableEntityException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/InternalServerErrorException.mustache",
                        exceptionsFolder,
                        "InternalServerErrorException.kt"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.kt"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.kt"));
        supportingFiles.add(
                new SupportingFile("api_result.mustache", invokerFolder, "ApiResult.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        Path.of(invokerFolder, "api").toString(),
                        "BaseApi.kt"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options.mustache", invokerFolder, "TransportOptions.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache",
                        invokerFolder,
                        "ServerConfiguration.kt"));
        supportingFiles.add(
                new SupportingFile("servers.mustache", invokerFolder, "Servers.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "value_serializer.mustache", invokerFolder, "ValueSerializer.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", invokerFolder, "TraceContextUtil.kt"));

        /* JVM-specific actual implementations in jvmMain source set */
        final String jvmInvokerFolder =
                Path.of("src", "jvmMain", "kotlin", invokerPackage.replace(".", "/")).toString();
        supportingFiles.add(
                new SupportingFile(
                        "http_client_factory_jvm.mustache",
                        jvmInvokerFolder,
                        "HttpClientFactory.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util_jvm.mustache",
                        jvmInvokerFolder,
                        "TraceContextUtil.kt"));

        supportingFiles.add(
                new SupportingFile("build_gradle.mustache", "", "build.gradle.kts"));
        supportingFiles.add(
                new SupportingFile("settings_gradle.mustache", "", "settings.gradle.kts"));
        supportingFiles.add(
                new SupportingFile("gradle_properties.mustache", "", "gradle.properties"));
        supportingFiles.add(
                new SupportingFile(
                        "authenticator.mustache",
                        Path.of(invokerFolder, "auth").toString(),
                        "Authenticator.kt"));
        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".kt"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            final String testFolder =
                    Path.of("src", "test", "kotlin", invokerPackage.replace(".", "/")).toString();
            final String testApiFolder = Path.of(testFolder, "api").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/PrismContainer.mustache", testFolder, "PrismContainer.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/SquidContainer.mustache", testFolder, "SquidContainer.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/WireMockContainer.mustache",
                            testFolder,
                            "WireMockContainer.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/PetApiTest.mustache", testApiFolder, "PetApiTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/StoreApiTest.mustache", testApiFolder, "StoreApiTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientTest.mustache",
                            testFolder,
                            "DefaultApiClientTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientUnitTest.mustache",
                            testFolder,
                            "DefaultApiClientUnitTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TransportOptionsTest.mustache",
                            testFolder,
                            "TransportOptionsTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/HeaderSelectorTest.mustache",
                            testFolder,
                            "HeaderSelectorTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ObjectSerializerTest.mustache",
                            testFolder,
                            "ObjectSerializerTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ValueSerializerTest.mustache",
                            testFolder,
                            "ValueSerializerTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TraceContextUtilTest.mustache",
                            testFolder,
                            "TraceContextUtilTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/BaseApiTest.mustache", testFolder, "BaseApiTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ConfigurationTest.mustache",
                            testFolder,
                            "ConfigurationTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ClientTest.mustache",
                            testFolder,
                            "ClientTest.kt"));
            final String testModelsFolder = Path.of(testFolder, "models").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/MetadataTest.mustache",
                            testModelsFolder,
                            "MetadataTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ComposedSchemaTest.mustache",
                            testModelsFolder,
                            "ComposedSchemaTest.kt"));
            final String testAuthFolder = Path.of(testFolder, "auth", "oauth").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2TokenManagerTest.mustache",
                            testAuthFolder,
                            "OAuth2TokenManagerTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2AuthCodeAuthenticatorTest.mustache",
                            testAuthFolder,
                            "OAuth2AuthCodeAuthenticatorTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2ImplicitAuthenticatorTest.mustache",
                            testAuthFolder,
                            "OAuth2ImplicitAuthenticatorTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2ClientCredentialsAuthenticatorTest.mustache",
                            testAuthFolder,
                            "OAuth2ClientCredentialsAuthenticatorTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2PasswordAuthenticatorTest.mustache",
                            testAuthFolder,
                            "OAuth2PasswordAuthenticatorTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OpenIdConnectAuthenticatorTest.mustache",
                            testAuthFolder,
                            "OpenIdConnectAuthenticatorTest.kt"));
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
        }
    }

    /**
     * Returns the output directory for model source files by
     * combining the output folder, source folder, and model
     * package converted to a directory path.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, sourceFolder, modelPackage().replace('.', '/')).toString();
    }

    /**
     * Returns the output directory for API source files by
     * combining the output folder, source folder, and API
     * package converted to a directory path.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, sourceFolder, apiPackage().replace('.', '/')).toString();
    }

    /**
     * Returns the default value expression for a schema type.
     * Arrays default to empty mutableListOf or mutableSetOf;
     * maps default to empty mutableMapOf. All other types
     * return null to let the language default apply.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema unaliased = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (ModelUtils.isArraySchema(unaliased)) {
            if (Boolean.TRUE.equals(unaliased.getUniqueItems())) {
                return "mutableSetOf()";
            }
            return "mutableListOf()";
        } else if (ModelUtils.isMapSchema(unaliased)) {
            return "mutableMapOf()";
        }
        return null;
    }

    /**
     * Adds kotlinx.serialization annotation imports for
     * model properties. Ensures Serializable, SerialName,
     * and Contextual are imported as needed.
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
    }

    /**
     * Adds kotlinx.serialization imports to enum models.
     */
    @Override
    public ModelsMap postProcessModelsEnum(ModelsMap objs) {
        objs = super.postProcessModelsEnum(objs);
        return objs;
    }

    /**
     * Registers base auth supporting files based on which
     * security scheme types are present in the OpenAPI spec.
     * Only the auth classes actually needed by the spec are
     * emitted to keep the generated client minimal.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String invokerFolder =
                Path.of(sourceFolder, invokerPackage.replace(".", "/")).toString();
        final String authFolder = Path.of(invokerFolder, "auth").toString();
        final String oauthFolder = Path.of(authFolder, "oauth").toString();

        supportingFiles.add(
                new SupportingFile(
                        "auth/base_authenticator.mustache",
                        authFolder,
                        "BaseAuthenticator.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        authFolder,
                        "HttpAwareAuthenticator.kt"));

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic_authenticator.mustache",
                            authFolder,
                            "BasicAuthenticator.kt"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer_authenticator.mustache",
                            authFolder,
                            "BearerAuthenticator.kt"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_authenticator.mustache",
                            authFolder,
                            "ApiKeyAuthenticator.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_location.mustache",
                            authFolder,
                            "ApiKeyLocation.kt"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_token_manager.mustache",
                            oauthFolder,
                            "OAuth2TokenManager.kt"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_client_credentials_authenticator.mustache",
                            oauthFolder,
                            "OAuth2ClientCredentialsAuthenticator.kt"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_password_authenticator.mustache",
                            oauthFolder,
                            "OAuth2PasswordAuthenticator.kt"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_auth_code_authenticator.mustache",
                            oauthFolder,
                            "OAuth2AuthorizationCodeAuthenticator.kt"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_implicit_authenticator.mustache",
                            oauthFolder,
                            "OAuth2ImplicitAuthenticator.kt"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/openid_connect_authenticator.mustache",
                            oauthFolder,
                            "OpenIdConnectAuthenticator.kt"));
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
            final String code = generateKotlinAuthClass(schemeName, className, scheme);
            if (!code.isEmpty()) {
                final boolean isOAuth =
                        scheme.getType() == SecurityScheme.Type.OAUTH2
                                || scheme.getType() == SecurityScheme.Type.OPENIDCONNECT;
                final String folder = isOAuth ? oauthFolder : authFolder;
                final String suffix = getOAuthSuffix(scheme);
                final String fileName = className + suffix + "Authenticator.kt";
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
    private String generateKotlinAuthClass(
            String schemeName, String className, SecurityScheme scheme) {
        final String pkg = invokerPackage;
        if (scheme.getType() == SecurityScheme.Type.HTTP) {
            if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                return renderSchemeAuth(pkg + ".auth", className + "Authenticator",
                        "BasicAuthenticator", List.of(),
                        List.of(p("host", "String"), p("username", "String"),
                                p("password", "String")),
                        List.of("host", "username", "password"));
            }
            if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                return renderSchemeAuth(pkg + ".auth", className + "Authenticator",
                        "BearerAuthenticator", List.of(),
                        List.of(p("host", "String"), p("token", "String")),
                        List.of("host", "token"));
            }
        } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
            final String location =
                    NamingConvention.UPPER_SNAKE_CASE.apply(scheme.getIn().toString());
            final String paramName = scheme.getName();
            return renderSchemeAuth(pkg + ".auth", className + "Authenticator",
                    "ApiKeyAuthenticator", List.of(),
                    List.of(p("host", "String"), p("apiKey", "String")),
                    List.of("host", "\"" + paramName + "\"", "apiKey",
                            "ApiKeyLocation." + location));
        } else if (scheme.getType() == SecurityScheme.Type.OAUTH2
                && scheme.getFlows() != null) {
            return generateKotlinOAuthClass(className, scheme, pkg);
        } else if (scheme.getType() == SecurityScheme.Type.OPENIDCONNECT) {
            final String url = scheme.getOpenIdConnectUrl();
            return renderSchemeAuth(pkg + ".auth.oauth", className + "Authenticator",
                    "OpenIdConnectAuthenticator",
                    List.of(pkg + ".auth.Authenticator"),
                    List.of(p("host", "String"), p("clientId", "String"),
                            p("clientSecret", "String"), p("redirectUri", "String")),
                    List.of("host", "\"" + url + "\"", "clientId", "clientSecret",
                            "redirectUri", "listOf()"));
        }
        LOGGER.warn("Unsupported security scheme type: {}", scheme.getType());
        return "";
    }

    private String generateKotlinOAuthClass(
            String className, SecurityScheme scheme, String pkg) {
        if (scheme.getFlows().getClientCredentials() != null) {
            final var flow = scheme.getFlows().getClientCredentials();
            final String tokenUrl = flow.getTokenUrl();
            final String scopes = formatScopes(flow.getScopes());
            return renderSchemeAuth(pkg + ".auth.oauth",
                    className + "ClientCredentialsAuthenticator",
                    "OAuth2ClientCredentialsAuthenticator",
                    List.of(pkg + ".auth.Authenticator"),
                    List.of(p("host", "String"), p("clientId", "String"),
                            p("clientSecret", "String")),
                    List.of("host", "clientId", "clientSecret",
                            "\"" + tokenUrl + "\"", scopes));
        }
        if (scheme.getFlows().getPassword() != null) {
            final var flow = scheme.getFlows().getPassword();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String refreshUrlArg = refreshUrl != null ? "\"" + refreshUrl + "\"" : "null";
            final String scopes = formatScopes(flow.getScopes());
            return renderSchemeAuth(pkg + ".auth.oauth",
                    className + "PasswordAuthenticator",
                    "OAuth2PasswordAuthenticator",
                    List.of(pkg + ".auth.Authenticator"),
                    List.of(p("host", "String"), p("clientId", "String"),
                            p("clientSecret", "String"), p("username", "String"),
                            p("password", "String")),
                    List.of("host", "clientId", "clientSecret",
                            "\"" + tokenUrl + "\"", refreshUrlArg,
                            "username", "password", scopes));
        }
        if (scheme.getFlows().getAuthorizationCode() != null) {
            final var flow = scheme.getFlows().getAuthorizationCode();
            final String authUrl = flow.getAuthorizationUrl();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String refreshUrlArg = refreshUrl != null ? "\"" + refreshUrl + "\"" : "null";
            final String scopes = formatScopes(flow.getScopes());
            return renderSchemeAuth(pkg + ".auth.oauth",
                    className + "AuthorizationCodeAuthenticator",
                    "OAuth2AuthorizationCodeAuthenticator",
                    List.of(pkg + ".auth.Authenticator"),
                    List.of(p("host", "String"), p("clientId", "String"),
                            p("clientSecret", "String"), p("redirectUri", "String")),
                    List.of("host", "clientId", "clientSecret",
                            "\"" + authUrl + "\"", "\"" + tokenUrl + "\"",
                            "redirectUri", scopes, refreshUrlArg));
        }
        if (scheme.getFlows().getImplicit() != null) {
            final var flow = scheme.getFlows().getImplicit();
            final String authUrl = flow.getAuthorizationUrl();
            final String scopes = formatScopes(flow.getScopes());
            return renderSchemeAuth(pkg + ".auth.oauth",
                    className + "ImplicitAuthenticator",
                    "OAuth2ImplicitAuthenticator",
                    List.of(pkg + ".auth.Authenticator"),
                    List.of(p("host", "String"), p("clientId", "String")),
                    List.of("host", "clientId", "\"" + authUrl + "\"", scopes));
        }
        LOGGER.warn("Unsupported OAuth2 flow for scheme: {}", className);
        return "";
    }

    @SuppressWarnings("SameParameterValue")
    private static String formatScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "listOf()";
        }
        return "listOf(\"" + String.join("\", \"", scopes.keySet()) + "\")";
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

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        final List<Map<String, Object>> requiredParams = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            params.add(param);
            if (p.required) {
                requiredParams.add(param);
            }
        }

        final Set<String> modelTypes = new LinkedHashSet<>();
        for (final CodegenParameter p : optionsParams) {
            if (!p.isPrimitiveType
                    && !p.isArray
                    && !p.isMap
                    && p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)
                    && !typeMapping.containsValue(p.baseType)) {
                modelTypes.add(p.baseType);
            }
            if ((p.isArray || p.isMap)
                    && p.items != null
                    && p.items.baseType != null
                    && !p.items.isPrimitiveType
                    && !languageSpecificPrimitives.contains(p.items.baseType)
                    && !typeMapping.containsValue(p.items.baseType)) {
                modelTypes.add(p.items.baseType);
            }
        }

        final String apiPkg = apiPackage();
        final Map<String, Object> context = new HashMap<>();
        context.put("package", apiPkg + ".options");
        context.put("modelPackage", modelPackage());
        context.put("className", className);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("requiredParams", requiredParams);
        context.put("modelImports", new ArrayList<>(modelTypes));
        context.put("hasModelImports", !modelTypes.isEmpty());
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        return Path.of(
                        outputFolder,
                        sourceFolder,
                        apiPackage().replace('.', '/'),
                        "options",
                        optionsClassName + ".kt")
                .toString();
    }
}

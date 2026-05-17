package io.github.mridang.codegen.generators.kotlin;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
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

        cliOptions.add(CliOption.newString(CodegenConstants.SOURCE_FOLDER,
                CodegenConstants.SOURCE_FOLDER_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.INVOKER_PACKAGE,
                CodegenConstants.INVOKER_PACKAGE_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.GROUP_ID,
                CodegenConstants.GROUP_ID_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.ARTIFACT_ID,
                CodegenConstants.ARTIFACT_ID_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.ARTIFACT_VERSION,
                CodegenConstants.ARTIFACT_VERSION_DESC));
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
    protected String getArrayTypeTemplate() {
        return "%1$s<%2$s>";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "%1$s<%2$s, %3$s>";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "src/main/kotlin";
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

        final String groupId = getPropertyOrDefault("groupId", invokerPackage);
        additionalProperties.put("groupId", groupId);
        final String artifactId =
                getPropertyOrDefault("artifactId", "openapi-kotlin-client");
        additionalProperties.put("artifactId", artifactId);
        final String artifactVersion = getPropertyOrDefault("artifactVersion", "1.0.0");
        additionalProperties.put("artifactVersion", artifactVersion);
        additionalProperties.put(
                "userAgentDefault", invokerPackage + "/" + artifactVersion + " (kotlin)");

        final String invokerFolder =
                Path.of(sourceFolder, invokerPackage.replace(".", "/")).toString();
        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(
                new SupportingFile("api_error.mustache", invokerFolder, "ApiException.kt"));

        final String errorsFolder = Path.of(invokerFolder, "errors").toString();
        supportingFiles.add(
                new SupportingFile(
                        "errors/ClientException.mustache",
                        errorsFolder,
                        "ClientException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/ServerException.mustache",
                        errorsFolder,
                        "ServerException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/BadRequestException.mustache",
                        errorsFolder,
                        "BadRequestException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/UnauthorizedException.mustache",
                        errorsFolder,
                        "UnauthorizedException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/ForbiddenException.mustache",
                        errorsFolder,
                        "ForbiddenException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/NotFoundException.mustache",
                        errorsFolder,
                        "NotFoundException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/ConflictException.mustache",
                        errorsFolder,
                        "ConflictException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/UnprocessableEntityException.mustache",
                        errorsFolder,
                        "UnprocessableEntityException.kt"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/InternalServerErrorException.mustache",
                        errorsFolder,
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
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));

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
        }
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
        if (ModelUtils.isStringSchema(unaliased)
                && unaliased.getDefault() != null
                && unaliased.getEnum() != null
                && !unaliased.getEnum().isEmpty()) {
            return unaliased.getDefault().toString();
        }
        return null;
    }

    /**
     * Keeps the enum-reference default value (e.g.
     * {@code StatusEnum.PLACED}) as produced by
     * {@code updateCodegenPropertyEnum}, rather than converting it
     * to a string literal as the base-class implementation would.
     */
    @Override
    protected void fixEnumDefaultValue(CodegenProperty prop, CodegenModel model) {
        // no-op: StatusEnum.PLACED is the correct Kotlin form
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of(sourceFolder, invokerPackage.replace(".", "/"), "auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return pascalAuthFilename(stem, ".kt");
    }

    /** {@inheritDoc} */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        final String pkg = invokerPackage + (spec.isOAuth() ? ".auth.oauth" : ".auth");
        ctx.put("package", pkg);
        final List<String> imports = spec.isOAuth()
                ? List.of(invokerPackage + ".auth.Authenticator")
                : List.of();
        ctx.put("imports", imports);
        final List<Map<String, String>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, String> param = new HashMap<>();
            param.put("name", name);
            param.put("type", "String");
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", buildKotlinSuperArgs(spec));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    private static String formatKotlinScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "listOf()";
        }
        return "listOf(\"" + String.join("\", \"", scopes.keySet()) + "\")";
    }

    private List<String> buildKotlinSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.keyParamName() + "\"", "apiKey",
                    "ApiKeyLocation." + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"", formatKotlinScopes(spec.scopes()));
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "null";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"", refreshArg,
                    "username", "password", formatKotlinScopes(spec.scopes()));
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "null";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.authorizationUrl() + "\"", "\"" + spec.tokenUrl() + "\"",
                    "redirectUri", formatKotlinScopes(spec.scopes()), refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId",
                    "\"" + spec.authorizationUrl() + "\"", formatKotlinScopes(spec.scopes()));
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.openIdConnectUrl() + "\"",
                    "clientId", "clientSecret", "redirectUri", "listOf()");
        }
        return List.of();
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

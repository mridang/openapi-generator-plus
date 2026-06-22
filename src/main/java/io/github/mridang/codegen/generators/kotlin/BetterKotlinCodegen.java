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
import java.util.Optional;
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
        typeMapping.put("time", "LocalTime");
        typeMapping.put("duration", "Duration");
        typeMapping.put("UUID", "Uuid");
        typeMapping.put("URI", "Url");
        typeMapping.put("BigDecimal", "BigDecimal");

        importMapping.put("List", "kotlin.collections.List");
        importMapping.put("Set", "kotlin.collections.Set");
        importMapping.put("Map", "kotlin.collections.Map");
        importMapping.put("HashMap", "kotlin.collections.HashMap");
        importMapping.put("ArrayList", "kotlin.collections.ArrayList");
        importMapping.put("LinkedHashSet", "kotlin.collections.LinkedHashSet");
        importMapping.put("LocalDate", "java.time.LocalDate");
        importMapping.put("OffsetDateTime", "java.time.OffsetDateTime");
        importMapping.put("LocalTime", "java.time.LocalTime");
        importMapping.put("Duration", "java.time.Duration");
        importMapping.put("BigDecimal", "java.math.BigDecimal");
        importMapping.put("Uuid", "kotlin.uuid.Uuid");
        importMapping.put("Url", "io.ktor.http.Url");

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
        return "eclipse-temurin:21-jdk@sha256:b9142586f9712700c6c9e07adcedfb18608b1a3a056e4001423a3354adfa9d80";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            /* -f: fail (non-zero) on an HTTP error instead of silently saving
             * the error/redirect page to /tmp/ktlint — that "success with a
             * garbage file" is what produced the intermittent
             * "/tmp/ktlint: Syntax error: redirection unexpected" (an HTML
             * error body executed as a script). --retry rides out transient
             * GitHub-release hiccups; -S surfaces the curl error on failure. */
            "curl -fSL --retry 5 --retry-delay 2 --retry-all-errors"
                + " -o /tmp/ktlint"
                + " https://github.com/pinterest/ktlint/releases/download/1.5.0/ktlint",
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

    /** {@inheritDoc} */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /**
     * Processes user-supplied codegen options after they are
     * resolved. Reads the invoker package, build coordinates,
     * and source folder, then registers all supporting files
     * for the client skeleton, exceptions, auth, serialization,
     * and optional test scaffolding.
     */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String sf = Optional.ofNullable((String) additionalProperties.get("sourceFolder"))
                .orElse(sourceFolder);
        final String pkg = Optional.ofNullable((String) additionalProperties.get("invokerPackage"))
                .orElse(invokerPackage);
        final String invokerFolder = Path.of(sf, pkg.replace(".", "/")).toString();
        final String errorsFolder = Path.of(invokerFolder, "errors").toString();
        final String jvmInvokerFolder =
                Path.of("src", "jvmMain", "kotlin", pkg.replace(".", "/")).toString();
        return List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("zitadel_exception.mustache", invokerFolder, "ZitadelException.kt"),
            new SupportingFileSpec("api_error.mustache", invokerFolder, "ApiException.kt"),
            new SupportingFileSpec("errors/ClientException.mustache", errorsFolder, "ClientException.kt"),
            new SupportingFileSpec("errors/ServerException.mustache", errorsFolder, "ServerException.kt"),
            new SupportingFileSpec("errors/BadRequestException.mustache", errorsFolder, "BadRequestException.kt"),
            new SupportingFileSpec("errors/UnauthorizedException.mustache", errorsFolder, "UnauthorizedException.kt"),
            new SupportingFileSpec("errors/ForbiddenException.mustache", errorsFolder, "ForbiddenException.kt"),
            new SupportingFileSpec("errors/NotFoundException.mustache", errorsFolder, "NotFoundException.kt"),
            new SupportingFileSpec("errors/ConflictException.mustache", errorsFolder, "ConflictException.kt"),
            new SupportingFileSpec("errors/UnprocessableEntityException.mustache", errorsFolder, "UnprocessableEntityException.kt"),
            new SupportingFileSpec("errors/InternalServerErrorException.mustache", errorsFolder, "InternalServerErrorException.kt"),
            new SupportingFileSpec("api_client.mustache", invokerFolder, "ApiClient.kt"),
            new SupportingFileSpec("default_api_client.mustache", invokerFolder, "DefaultApiClient.kt"),
            new SupportingFileSpec("api_response.mustache", invokerFolder, "ApiHttpResponse.kt"),
            new SupportingFileSpec("api_result.mustache", invokerFolder, "ApiResult.kt"),
            new SupportingFileSpec("base_api.mustache", Path.of(invokerFolder, "api").toString(), "BaseApi.kt"),
            new SupportingFileSpec("configuration.mustache", invokerFolder, "Configuration.kt"),
            new SupportingFileSpec("transport_options.mustache", invokerFolder, "TransportOptions.kt"),
            new SupportingFileSpec("server_configuration.mustache", invokerFolder, "ServerConfiguration.kt"),
            new SupportingFileSpec("servers.mustache", invokerFolder, "Servers.kt"),
            new SupportingFileSpec("object_serializer.mustache", invokerFolder, "ObjectSerializer.kt"),
            new SupportingFileSpec("value_serializer.mustache", invokerFolder, "ValueSerializer.kt"),
            new SupportingFileSpec("header_selector.mustache", invokerFolder, "HeaderSelector.kt"),
            new SupportingFileSpec("trace_context_util.mustache", invokerFolder, "TraceContextUtil.kt"),
            new SupportingFileSpec("http_client_factory_jvm.mustache", jvmInvokerFolder, "HttpClientFactory.kt"),
            new SupportingFileSpec("trace_context_util_jvm.mustache", jvmInvokerFolder, "TraceContextUtil.kt"),
            new SupportingFileSpec("build_gradle.mustache", "", "build.gradle.kts"),
            new SupportingFileSpec("settings_gradle.mustache", "", "settings.gradle.kts"),
            new SupportingFileSpec("gradle_properties.mustache", "", "gradle.properties"),
            new SupportingFileSpec("authenticator.mustache", Path.of(invokerFolder, "auth").toString(), "Authenticator.kt"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("detekt_yml.mustache", "", "detekt.yml"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore")
        );
    }

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
        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".kt"));

        final String testFolder =
                Path.of("src", "test", "kotlin", invokerPackage.replace(".", "/")).toString();

        if (emitUnitTests()) {
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
                            "test/ConfigurationTest.mustache",
                            testFolder,
                            "ConfigurationTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ServerConfigurationTest.mustache",
                            testFolder,
                            "ServerConfigurationTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ServerVariableTest.mustache",
                            testFolder,
                            "ServerVariableTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiResultTest.mustache",
                            testFolder,
                            "ApiResultTest.kt"));
        }

        if (generateTests) {
            final String testApiFolder = Path.of(testFolder, "api").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/ChasmContainer.mustache", testFolder, "ChasmContainer.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/SquidContainer.mustache", testFolder, "SquidContainer.kt"));
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
                            "test/ObjectSerializerTest.mustache",
                            testFolder,
                            "ObjectSerializerTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/BaseApiTest.mustache", testFolder, "BaseApiTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ClientTest.mustache",
                            testFolder,
                            "ClientTest.kt"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiExceptionTest.mustache",
                            testFolder,
                            "ApiExceptionTest.kt"));
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
            final String testBasicAuthFolder = Path.of(testFolder, "auth").toString();
            final String testAuthFolder = Path.of(testFolder, "auth", "oauth").toString();
            if (hasBasicAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/BasicAuthenticatorTest.mustache",
                                testBasicAuthFolder,
                                "BasicAuthenticatorTest.kt"));
            }
            if (hasBearerAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/BearerAuthenticatorTest.mustache",
                                testBasicAuthFolder,
                                "BearerAuthenticatorTest.kt"));
            }
            if (hasApiKeyAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/ApiKeyAuthenticatorTest.mustache",
                                testBasicAuthFolder,
                                "ApiKeyAuthenticatorTest.kt"));
            }
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
        if (unaliased.getDefault() != null) {
            // A non-enum scalar default must be materialised on deserialize: an
            // absent property is populated with this value while an explicit JSON
            // null is preserved by kotlinx-serialization (the default applies only
            // when the key is missing). String defaults become quoted literals;
            // numeric and boolean defaults are emitted verbatim.
            if (ModelUtils.isStringSchema(unaliased)) {
                return "\"" + escapeText(String.valueOf(unaliased.getDefault())) + "\"";
            }
            if (ModelUtils.isLongSchema(unaliased)) {
                return String.valueOf(unaliased.getDefault()) + "L";
            }
            if (ModelUtils.isIntegerSchema(unaliased)
                    || ModelUtils.isNumberSchema(unaliased)
                    || ModelUtils.isBooleanSchema(unaliased)) {
                return String.valueOf(unaliased.getDefault());
            }
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
        final List<Map<String, Object>> optionalParams = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            params.add(param);
            if (p.required) {
                requiredParams.add(param);
            } else {
                optionalParams.add(param);
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
            // A $ref to a top-level enum carries its enum type name in dataType
            // (baseType is null); its generated type must be imported.
            if (p.isEnumRef
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)
                    && !typeMapping.containsValue(p.dataType)) {
                modelTypes.add(p.dataType);
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
        context.put("optionalParams", optionalParams);
        context.put("modelImports", new ArrayList<>(modelTypes));
        context.put("hasModelImports", !modelTypes.isEmpty());
        injectAuthFieldContext(op, context);
        context.put("authImport", invokerPackage + ".auth." + getAuthenticatorTypeName());
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

    /**
     * Restores plain {@code String} for {@code format: uri-reference} and
     * {@code format: uri-template} properties so only absolute
     * {@code format: uri} maps to {@code io.ktor.http.Url}.
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        keepStringForUriSubformats(property, "String");
        // OpenAPI Generator 7.12 has no isDuration flag on CodegenProperty, so
        // surface format:duration to the model template under a plain flag. The
        // template uses it to apply @Contextual, routing the java.time.Duration
        // property through the protobuf-JSON DurationSerializer registered in the
        // SerializersModule.
        property.vendorExtensions.put("isDuration", "duration".equals(property.dataFormat));
    }

    /**
     * Annotates raw {@code Any} occurrences in a property's data type
     * with {@code @Contextual} so kotlinx.serialization can serialize
     * them via the contextual {@code Any} serializer registered in
     * {@code ObjectSerializer.createDefaultJson()}. Without this,
     * properties produced by {@code NormalizePrefixItemsRule}
     * (which downgrades OAS 3.1 {@code prefixItems} to
     * {@code items: {}}) compile to {@code List<Any>?} and fail
     * with "Serializer has not been found for type 'Any'". Affects
     * scalar {@code Any}, {@code List<Any>}, and
     * {@code Map<String, Any>} forms.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public CodegenProperty fromProperty(
            String name, Schema p, boolean required, boolean schemaIsFromAdditionalProperties) {
        final CodegenProperty property =
                super.fromProperty(name, p, required, schemaIsFromAdditionalProperties);
        if (property != null && property.dataType != null) {
            property.dataType = annotateAnyAsContextual(property.dataType);
        }
        return property;
    }

    /**
     * Rewrites the bare token {@code Any} in a Kotlin type string to
     * {@code @Contextual Any}, leaving other identifiers untouched
     * (e.g. {@code Anywhere}, {@code MyAnyClass}). Idempotent.
     */
    private static String annotateAnyAsContextual(String dataType) {
        if (dataType.contains("@Contextual")) {
            return dataType;
        }
        // Word-boundary replace of bare "Any" not already annotated.
        return dataType.replaceAll("(?<![A-Za-z0-9_@])Any(?![A-Za-z0-9_])", "@Contextual Any");
    }
}

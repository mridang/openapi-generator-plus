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
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
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

    /**
     * Escapes the {@code $} template sigil of a Kotlin string literal; the
     * backslash, the quote and the control characters are escaped by the
     * shared enum literal escape.
     */
    @Override
    protected List<String[]> getEnumStringEscapes() {
        return List.<String[]>of(new String[] {"\\$", "\\\\\\$"});
    }

    /**
     * Escapes raw spec text for the body of a Kotlin double-quoted string
     * literal so the literal evaluates to exactly the spec's characters: the
     * backslash, the quote, the template sigil {@code $} and the control
     * characters newline, carriage return and tab are escaped.
     *
     * @param raw the unescaped text
     * @return the literal body, without the surrounding quotes
     */
    static String escapeKotlinString(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c == '$') {
                sb.append("\\$");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Splits an already-escaped string literal body into a concatenation of
     * short literals, one per line, so a long spec text stays under the line
     * length limit. A chunk never ends inside an escape sequence.
     */
    private static String chunkKotlinString(String body) {
        final int max = 80;
        if (body.length() <= max) {
            return body;
        }
        final StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < body.length()) {
            int end = Math.min(start + max, body.length());
            while (end < body.length() && end > start + 1 && endsInsideEscape(body, start, end)) {
                end--;
            }
            if (start > 0) {
                sb.append("\" +\n                        \"");
            }
            sb.append(body, start, end);
            start = end;
        }
        return sb.toString();
    }

    /** True when {@code body[start, end)} ends with an odd run of backslashes. */
    private static boolean endsInsideEscape(String body, int start, int end) {
        int count = 0;
        for (int i = end - 1; i >= start && body.charAt(i) == '\\'; i--) {
            count++;
        }
        return count % 2 != 0;
    }

    /**
     * Neutralises the comment delimiters in prose placed inside a KDoc block.
     * Kotlin block comments nest, so an opening delimiter is as harmful as a
     * closing one.
     */
    private static String escapeKdoc(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("*/", "* /").replace("/*", "/ *");
    }

    /**
     * Registers the Kotlin escaping lambdas: {@code kotlinString} escapes raw
     * text for a string literal body, {@code kotlinDollar} escapes only the
     * template sigil in text that is already string-escaped, and {@code kdoc}
     * neutralises comment delimiters in doc prose.
     */
    @Override
    protected com.google.common.collect.ImmutableMap.Builder<
                    String, com.samskivert.mustache.Mustache.Lambda>
            addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put(
                        "kotlinString",
                        (fragment, writer) ->
                                writer.write(escapeKotlinString(fragment.execute())))
                .put(
                        "kotlinDollar",
                        (fragment, writer) ->
                                writer.write(fragment.execute().replace("$", "\\$")))
                .put(
                        "kotlinChunked",
                        (fragment, writer) -> writer.write(chunkKotlinString(fragment.execute())))
                .put(
                        "kdoc",
                        (fragment, writer) -> writer.write(escapeKdoc(fragment.execute())));
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
            "[ -x /cache/ktlint-1.5.0 ] || { curl -fSL --retry 5 --retry-delay 2"
                + " --retry-all-errors -o /cache/ktlint-1.5.0.part"
                + " https://github.com/pinterest/ktlint/releases/download/1.5.0/ktlint"
                + " && chmod +x /cache/ktlint-1.5.0.part"
                + " && mv /cache/ktlint-1.5.0.part /cache/ktlint-1.5.0; }",
            "/cache/ktlint-1.5.0 --format '**/*.kt'"
        };
    }

    /**
     * A schema named like a built-in type ({@code String}, {@code Any},
     * {@code Int}) would shadow it across the whole models package, so it is
     * generated with a {@code Model} prefix.
     */
    @Override
    protected String getModelNameCollisionPrefix() {
        return "Model";
    }

    /**
     * Kotlin standard-library types that a schema name must not shadow in the
     * models package, where fields use them unqualified (a model named
     * {@code List} would turn every {@code List<T>} field into the model).
     */
    private static final Set<String> KOTLIN_BUILT_IN_TYPES =
            Set.of(
                    "Error",
                    "Exception",
                    "Throwable",
                    "Result",
                    "Nothing",
                    "Number",
                    "Array",
                    "List",
                    "Map",
                    "Set",
                    "Pair",
                    "Triple",
                    "Comparable");

    /**
     * A schema named after a standard-library type ({@code Error},
     * {@code Result}, {@code List}, ...) takes the {@code Model} prefix, like
     * the primitive names; the wire name is unchanged (F173).
     */
    @Override
    public String toModelName(String name) {
        final String modelName = super.toModelName(name);
        return KOTLIN_BUILT_IN_TYPES.contains(modelName) ? "Model" + modelName : modelName;
    }

    /**
     * Applies {@link #toModelName} to a reference to a schema named like a
     * standard-library type. The base class returns the raw schema name for a
     * {@code $ref}, so without this a property typed {@code Result} would
     * resolve to {@code kotlin.Result} while the model file declares
     * {@code ModelResult}. Only references are rewritten: the mapped
     * {@code List}, {@code Map} and {@code Set} types keep their names.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String getSchemaType(Schema schema) {
        final String type = super.getSchemaType(schema);
        if (schema != null && schema.get$ref() != null && KOTLIN_BUILT_IN_TYPES.contains(type)) {
            return toModelName(type);
        }
        return type;
    }

    /**
     * The names the generated operations add to their own signature or declare as
     * locals, so a path or body parameter that carries one is suffixed instead of
     * redeclaring it (F1055).
     */
    @Override
    protected Set<String> getInjectedArgumentNames() {
        return Set.of(
                "options",
                "server",
                "requestContentType",
                "auth",
                "path",
                "url",
                "headerParams",
                "queryParams",
                "formBody",
                "serverUrl",
                "declaredConsumes",
                "effectiveRequestContentType");
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
            new SupportingFileSpec("root_exception.mustache", errorsFolder, rootErrorName("Exception") + ".kt"),
            new SupportingFileSpec("api_error.mustache", errorsFolder, "ApiException.kt"),
            new SupportingFileSpec("errors/SerializationException.mustache", errorsFolder, "SerializationException.kt"),
            new SupportingFileSpec("errors/OAuth2ServerException.mustache", errorsFolder, "OAuth2ServerException.kt"),
            new SupportingFileSpec("errors/OAuth2TokenException.mustache", errorsFolder, "OAuth2TokenException.kt"),
            new SupportingFileSpec("errors/ClientException.mustache", errorsFolder, "ClientException.kt"),
            new SupportingFileSpec("errors/ServerException.mustache", errorsFolder, "ServerException.kt"),
            new SupportingFileSpec("errors/BadRequestException.mustache", errorsFolder, "BadRequestException.kt"),
            new SupportingFileSpec("errors/UnauthorizedException.mustache", errorsFolder, "UnauthorizedException.kt"),
            new SupportingFileSpec("errors/ForbiddenException.mustache", errorsFolder, "ForbiddenException.kt"),
            new SupportingFileSpec("errors/NotFoundException.mustache", errorsFolder, "NotFoundException.kt"),
            new SupportingFileSpec("errors/ConflictException.mustache", errorsFolder, "ConflictException.kt"),
            new SupportingFileSpec("errors/UnprocessableEntityException.mustache", errorsFolder, "UnprocessableEntityException.kt"),
            new SupportingFileSpec("errors/TooManyRequestsException.mustache", errorsFolder, "TooManyRequestsException.kt"),
            new SupportingFileSpec("errors/InternalServerErrorException.mustache", errorsFolder, "InternalServerErrorException.kt"),
            new SupportingFileSpec("errors/NetworkException.mustache", errorsFolder, "NetworkException.kt"),
            new SupportingFileSpec("errors/NetworkTimeoutException.mustache", errorsFolder, "NetworkTimeoutException.kt"),
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
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore"),
            new SupportingFileSpec(
                "dev_dependencies.mustache", ".openapi-generator", "DEV-DEPENDENCIES")
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
        // Derive the artifactId from the spec (the invoker package's last
        // segment, e.g. com.example.petstore -> petstore-client) rather than
        // the openapi-generator default "openapi-kotlin-client", so the Gradle
        // project name reflects the API like the other SDKs' package names do.
        final String defaultArtifactId =
                invokerPackage.substring(invokerPackage.lastIndexOf('.') + 1) + "-client";
        final String artifactId =
                getPropertyOrDefault("artifactId", defaultArtifactId);
        additionalProperties.put("artifactId", artifactId);
        final String artifactVersion = getPropertyOrDefault("artifactVersion", "1.0.0");
        additionalProperties.put("artifactVersion", artifactVersion);
        additionalProperties.put(
                "userAgentDefault", artifactId + "/" + artifactVersion + " (kotlin)");

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
        }
    }

    /**
     * Registers the authenticator tests once the spec has been parsed. The
     * scheme flags are only set by {@code detectSecuritySchemes}, which runs in
     * {@code processOpenAPI} after {@code processOpts}; reading them in
     * {@code processOpts} saw every flag as false and never emitted the
     * Basic, Bearer and API-key tests.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();
        if (!generateTests) {
            return;
        }
        final String testFolder =
                Path.of("src", "test", "kotlin", invokerPackage.replace(".", "/")).toString();
        final String testBasicAuthFolder = Path.of(testFolder, "auth").toString();
        final String testAuthFolder = Path.of(testFolder, "auth", "oauth").toString();
        if (hasBasicAuth) {
            supportingFiles.add(new SupportingFile(
                    "test/BasicAuthenticatorTest.mustache", testBasicAuthFolder, "BasicAuthenticatorTest.kt"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(new SupportingFile(
                    "test/BearerAuthenticatorTest.mustache", testBasicAuthFolder, "BearerAuthenticatorTest.kt"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(new SupportingFile(
                    "test/ApiKeyAuthenticatorTest.mustache", testBasicAuthFolder, "ApiKeyAuthenticatorTest.kt"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile(
                    "test/OAuth2TokenManagerTest.mustache", testAuthFolder, "OAuth2TokenManagerTest.kt"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(new SupportingFile(
                    "test/OAuth2AuthCodeAuthenticatorTest.mustache",
                    testAuthFolder,
                    "OAuth2AuthorizationCodeAuthenticatorTest.kt"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(new SupportingFile(
                    "test/OAuth2ImplicitAuthenticatorTest.mustache",
                    testAuthFolder,
                    "OAuth2ImplicitAuthenticatorTest.kt"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(new SupportingFile(
                    "test/OAuth2ClientCredentialsAuthenticatorTest.mustache",
                    testAuthFolder,
                    "OAuth2ClientCredentialsAuthenticatorTest.kt"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(new SupportingFile(
                    "test/OAuth2PasswordAuthenticatorTest.mustache",
                    testAuthFolder,
                    "OAuth2PasswordAuthenticatorTest.kt"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile(
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
                return "\"" + escapeKotlinString(String.valueOf(unaliased.getDefault())) + "\"";
            }
            final String raw = String.valueOf(unaliased.getDefault());
            if (ModelUtils.isLongSchema(unaliased)) {
                return raw + "L";
            }
            // Each numeric default is a literal of the field's exact type:
            // Float needs the f suffix, Double a decimal point, and the
            // format-less number (BigDecimal) an exact-text constructor.
            if (ModelUtils.isFloatSchema(unaliased)) {
                return withDecimalPoint(raw) + "f";
            }
            if (ModelUtils.isDoubleSchema(unaliased)) {
                return withDecimalPoint(raw);
            }
            if (ModelUtils.isNumberSchema(unaliased)) {
                return "java.math.BigDecimal(\"" + raw + "\")";
            }
            if (ModelUtils.isIntegerSchema(unaliased) || ModelUtils.isBooleanSchema(unaliased)) {
                return raw;
            }
        }
        return null;
    }

    /** Appends {@code .0} to an integral numeric literal. */
    private static String withDecimalPoint(String raw) {
        return raw.contains(".") || raw.contains("e") || raw.contains("E") ? raw : raw + ".0";
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
            return List.of("host", "\"" + escapeKotlinString(java.util.Objects.toString(spec.keyParamName(), "")) + "\"", "apiKey",
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
                    "\"" + spec.tokenUrl() + "\"",
                    "username", "password", formatKotlinScopes(spec.scopes()), refreshArg);
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
            // Thread the spec parameter's `deprecated` flag onto the Options
            // field so the Kotlin template can emit a `@Deprecated` annotation
            // and KDoc `@deprecated` tag, mirroring how model properties already
            // surface deprecation (see models/model.mustache `{{#deprecated}}`).
            // A query/header/form/cookie param marked `deprecated: true` in the
            // spec otherwise reaches the caller via the Options object with no
            // deprecation warning; the type is left unchanged (e.g. the
            // allowEmptyValue `status` string stays a String?), only the marker
            // is added.
            param.put("deprecated", p.isDeprecated);
            // Thread the spec parameter description onto the Options field so
            // the Kotlin template can emit a per-property KDoc, matching the
            // other SDKs (Java, Node, C#, Go, Swift, Dart, Rust, ...). Only set
            // when non-blank so the template's {{#description}} gate stays false
            // for parameters without a description.
            if (p.description != null && !p.description.isBlank()) {
                param.put("description", p.description);
            }
            // The named examples of the parameter go into the same KDoc, as they
            // do for a required parameter on the operation (F893).
            final List<Map<String, Object>> examples = kdocExamples(p);
            if (!examples.isEmpty()) {
                param.put("pluralExamples", examples);
                param.put("hasPluralExamples", true);
            }
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
            // An object-valued query/header parameter (style form/deepObject, or
            // OAS `content`) may carry its model type only in dataType.
            if (p.dataType != null
                    && p.dataType.matches("[A-Z][A-Za-z0-9_]*")
                    && !languageSpecificPrimitives.contains(p.dataType)
                    && !typeMapping.containsValue(p.dataType)
                    && !importMapping.containsKey(p.dataType)
                    && (p.isModel || p.isEnumRef || p.getContent() != null || p.isFreeFormObject
                            || "deepObject".equals(p.style) || "form".equals(p.style)
                            || "simple".equals(p.style))) {
                modelTypes.add(p.dataType);
            }
        }

        // Parameters typed as a JVM stdlib surface (e.g. an OffsetDateTime
        // date-time query param) need their fully-qualified import in the
        // Options class. Kotlin's collection/primitive types are auto-imported,
        // so only `java.*` mappings (java.time/java.math/java.io/java.net) are
        // emitted; List/Map/Set map to kotlin.collections and are skipped.
        final Set<String> extraImports = new LinkedHashSet<>();
        for (final CodegenParameter p : optionsParams) {
            for (final Map.Entry<String, String> mapping : importMapping.entrySet()) {
                if (!mapping.getValue().startsWith("java.")) {
                    continue;
                }
                if (typeReferencesName(p.dataType, mapping.getKey())
                        || (p.items != null
                                && typeReferencesName(p.items.dataType, mapping.getKey()))) {
                    extraImports.add(mapping.getValue());
                }
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
        context.put("extraImports", new ArrayList<>(extraImports));
        context.put("hasExtraImports", !extraImports.isEmpty());
        injectAuthFieldContext(op, context);
        context.put("authImport", invokerPackage + ".auth." + getAuthenticatorTypeName());
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /**
     * The named examples of a parameter as KDoc-safe single-line entries: a
     * comment terminator or a line break inside a summary or value would end or
     * break the comment.
     */
    private static List<Map<String, Object>> kdocExamples(CodegenParameter p) {
        final List<Map<String, Object>> result = new ArrayList<>();
        if (p.examples == null || p.examples.isEmpty()) {
            return result;
        }
        for (final Map<String, Object> example : buildPluralExamplesList(p.examples)) {
            final Map<String, Object> entry = new HashMap<>();
            entry.put("summary", kdocLine(String.valueOf(example.get("summary"))));
            entry.put("value", kdocLine(String.valueOf(example.get("value"))));
            result.add(entry);
        }
        return result;
    }

    private static String kdocLine(String text) {
        final String safe = text.replace("*/", "* /").replace("/*", "/ *");
        return safe.replaceAll("\\s*[\\r\\n]+\\s*", " ");
    }

    /**
     * Returns {@code true} when {@code type} references the simple type name
     * {@code name} as a whole word — matching a bare {@code OffsetDateTime} as
     * well as the element type inside a generic such as
     * {@code List<OffsetDateTime>}, while never matching a substring of a longer
     * identifier. Used to decide which {@code java.*} imports an Options class
     * needs for its parameter types.
     */
    private static boolean typeReferencesName(@Nullable String type, String name) {
        if (type == null) {
            return false;
        }
        int from = 0;
        while ((from = type.indexOf(name, from)) >= 0) {
            final int end = from + name.length();
            final boolean leftBoundary =
                    from == 0 || !Character.isJavaIdentifierPart(type.charAt(from - 1));
            final boolean rightBoundary =
                    end == type.length() || !Character.isJavaIdentifierPart(type.charAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            from = end;
        }
        return false;
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
        property.vendorExtensions.put("isLocalTime", "LocalTime".equals(property.dataType));
    }

    /**
     * Flags every model whose value-semantics would be broken by the
     * {@code data class}-generated {@code equals}/{@code hashCode}.
     *
     * <p>Kotlin's synthesized data-class {@code equals} compares
     * {@code List}/{@code Map} structurally but {@code Array} and
     * {@code ByteArray} by reference identity. A property typed
     * {@code ByteArray} (a {@code format: byte}/{@code binary} field), an
     * {@code Array<T>}, or a {@code List<ByteArray>} (whose structural
     * {@code List} comparison still bottoms out in reference-equal
     * {@code ByteArray} elements) therefore makes two instances decoded from
     * identical JSON compare unequal and hash differently. This breaks their
     * use as {@code Set}/{@code Map} keys and round-trip equality.
     *
     * <p>For such models a per-property equality strategy is recorded so the
     * model template can emit explicit {@code equals}/{@code hashCode} that
     * route array/byte-array fields through {@code contentEquals}/
     * {@code contentHashCode} (element-wise for {@code List<ByteArray>}) while
     * leaving every other field on the ordinary {@code ==}/{@code hashCode}.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            boolean hasContentEqualsField = false;
            for (final CodegenProperty prop : model.vars) {
                if (markEquality(prop)) {
                    hasContentEqualsField = true;
                }
                if (markPasswordRedaction(prop)) {
                    hasContentEqualsField = true;
                }
            }
            // A model with an additionalProperties overflow bucket also needs the
            // hand-written equals/hashCode: the bucket is a @Transient property in
            // the class body (not the primary constructor), so the synthesized
            // data-class equals/hashCode silently exclude it — two instances with
            // different extra keys would otherwise compare equal. Emitting the
            // override lets the template fold the bucket into the comparison.
            if (hasContentEqualsField || model.isAdditionalPropertiesTrue) {
                modelMap.put("hasContentEqualsField", true);
            }
        }
        dropSamePackageImports(result);
        return result;
    }

    /**
     * Puts the per-operation server before the optional-parameters object in
     * every operation signature: required parameters, then server, then the
     * options object. Adding security (which adds the options object) never
     * moves an existing argument.
     *
     * @param objs the operations of one API class
     * @param allModels every model
     * @return the processed operations
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        final OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);
        if (result.getOperations() != null) {
            for (final CodegenOperation op : result.getOperations().getOperation()) {
                if (op.vendorExtensions == null) {
                    continue;
                }
                final Object deco = op.vendorExtensions.get("op");
                if (deco instanceof Map) {
                    final Map<String, Object> opDeco = (Map<String, Object>) deco;
                    final Object sigArgs = opDeco.get("signatureArgs");
                    if (sigArgs instanceof List) {
                        opDeco.put(
                                "signatureArgs",
                                serverBeforeOptions((List<Map<String, Object>>) sigArgs));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Moves the server entry directly before the options entry.
     *
     * @param args the shared signature arguments (path, body, options, server)
     * @return a new list with the server entry directly before the options entry
     */
    static List<Map<String, Object>> serverBeforeOptions(final List<Map<String, Object>> args) {
        final List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> options = null;
        for (final Map<String, Object> arg : args) {
            if (Boolean.TRUE.equals(arg.get("isOptions"))) {
                options = arg;
            } else if (Boolean.TRUE.equals(arg.get("isServer"))) {
                result.add(arg);
                if (options != null) {
                    result.add(options);
                    options = null;
                }
            } else {
                result.add(arg);
            }
        }
        if (options != null) {
            result.add(options);
        }
        return result;
    }

    /**
     * Records the per-property equality strategy on {@code prop} and reports
     * whether the property forces hand-written {@code equals}/{@code hashCode}.
     *
     * @param prop the property to classify
     * @return true when the property needs content-based comparison
     */
    private static boolean markEquality(CodegenProperty prop) {
        final String dataType = prop.dataType == null ? "" : prop.dataType;
        final boolean byteArray = "ByteArray".equals(dataType);
        final boolean array = dataType.startsWith("Array<");
        final boolean listByteArray =
                dataType.startsWith("List<") && dataType.contains("ByteArray");
        // A MAP whose values are byte arrays needs the same treatment as a
        // list of them. Kotlin's Map.equals compares values with ==, which
        // for ByteArray is identity, so two maps holding equal bytes under
        // equal keys would compare unequal. Compare per key instead.
        final boolean mapByteArray = dataType.startsWith("Map<") && dataType.contains("ByteArray");
        prop.vendorExtensions.put("eqByteArray", byteArray || array);
        prop.vendorExtensions.put("eqListByteArray", listByteArray);
        prop.vendorExtensions.put("eqMapByteArray", mapByteArray);
        // BigDecimal.equals/hashCode depend on the scale (1.5 != 1.50), unlike
        // the decimal types of the other SDKs: compare by numeric value.
        final boolean decimal = "BigDecimal".equals(dataType);
        prop.vendorExtensions.put("eqDecimal", decimal);
        return byteArray || array || listByteArray || mapByteArray || decimal;
    }

    /**
     * Turns an {@code allOf} family with a discriminator into a sealed
     * interface plus data-class subtypes.
     *
     * <p>A discriminated base (a discriminator on a plain object schema, not a
     * {@code oneOf} wrapper) is flagged {@code isDiscriminatedBase}: the model
     * template emits it as a {@code sealed interface} whose serializer picks the
     * subtype by the discriminator value. Each subtype that names such a base
     * keeps its parent and gets the base fields flattened into its own
     * constructor, with {@code overridesBase} set on the ones the interface
     * declares. A subtype whose parent is not a discriminated base drops the
     * parent, so it stays an ordinary data class.
     *
     * @param objs every model, by schema name
     * @return the processed models
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        final Map<String, CodegenModel> bases = new HashMap<>();
        for (final ModelsMap models : result.values()) {
            for (final ModelMap modelMap : models.getModels()) {
                final CodegenModel model = modelMap.getModel();
                final boolean wrapper =
                        (model.oneOf != null && !model.oneOf.isEmpty())
                                || (model.anyOf != null && !model.anyOf.isEmpty());
                if (model.discriminator != null && model.parent == null && !model.isEnum && !wrapper) {
                    modelMap.put("isDiscriminatedBase", true);
                    bases.put(model.classname, model);
                }
            }
        }
        for (final ModelsMap models : result.values()) {
            for (final ModelMap modelMap : models.getModels()) {
                final CodegenModel model = modelMap.getModel();
                if (model.parent == null) {
                    continue;
                }
                final CodegenModel base = bases.get(model.parent);
                if (base == null) {
                    model.parent = null;
                    continue;
                }
                final Set<String> declared = new HashSet<>();
                for (final CodegenProperty prop : base.vars) {
                    if (!prop.isEnum) {
                        declared.add(prop.name);
                    }
                }
                final List<CodegenProperty> flattened = new ArrayList<>(model.allVars);
                boolean hasContentEqualsField = false;
                for (final CodegenProperty prop : flattened) {
                    postProcessModelProperty(model, prop);
                    prop.vendorExtensions.put("overridesBase", declared.contains(prop.name));
                    if (markEquality(prop)) {
                        hasContentEqualsField = true;
                    }
                    if (markPasswordRedaction(prop)) {
                        hasContentEqualsField = true;
                    }
                }
                model.vars = flattened;
                if (hasContentEqualsField) {
                    modelMap.put("hasContentEqualsField", true);
                }
            }
        }
        return result;
    }

    /**
     * Marks a {@code format: password} property so the model's hand-written
     * {@code toString} prints {@code ***} instead of the secret. The synthesized
     * data-class {@code toString} would print it verbatim, so a model that
     * carries such a property always gets the explicit override.
     *
     * @param prop the property to inspect
     * @return {@code true} when the property is a password
     */
    private static boolean markPasswordRedaction(final CodegenProperty prop) {
        if (prop.isPassword) {
            prop.vendorExtensions.put("redactInToString", true);
            return true;
        }
        return false;
    }

    /**
     * Drops imports that name a type in the model's own package. A model that
     * references a sibling schema gets {@code import <modelPackage>.Sibling},
     * which Kotlin resolves without it; ktlint's {@code no-unused-imports}
     * reports every one of them.
     *
     * @param models the post-processed models whose import list to prune
     */
    private void dropSamePackageImports(ModelsMap models) {
        final String ownPackagePrefix = modelPackage() + ".";
        final List<Map<String, String>> imports = models.getImports();
        if (imports == null) {
            return;
        }
        final List<Map<String, String>> kept = new ArrayList<>();
        for (final Map<String, String> entry : imports) {
            final String imported = entry.get("import");
            final boolean samePackage =
                    imported != null
                            && imported.startsWith(ownPackagePrefix)
                            && imported.indexOf('.', ownPackagePrefix.length()) < 0;
            if (!samePackage) {
                kept.add(entry);
            }
        }
        models.setImports(kept);
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

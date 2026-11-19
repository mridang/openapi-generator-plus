package io.github.mridang.codegen.generators.java;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.OpenAPI;
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
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Java API client that uses Apache HttpClient 5 for
 * transport and Jackson for JSON serialization. Targets Java 17+
 * and follows camelCase naming for variables and methods. Output
 * is formatted with Google Java Format to ensure consistent style
 * across all generated source files.
 */
@SuppressWarnings("unused")
public class BetterJavaCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterJavaCodegen.class);

    private static final Set<String> NUMERIC_DATA_TYPES =
            Set.of("Integer", "Long", "Double", "Float", "Short", "BigDecimal");

    protected String sourceFolder = Path.of("src", "main", "java").toString();
    protected String invokerPackage = "org.openapitools";

    /**
     * Initializes all Java-specific type mappings, import mappings,
     * language primitives, and template file registrations. Uses
     * standard Java types from java.time and java.math for date,
     * time, and numeric schemas.
     */
    public BetterJavaCodegen() {
        outputFolder = "generated-code/java";
        embeddedTemplateDir = templateDir = "templates/java";

        modelTemplateFiles.put("models/model.mustache", ".java");
        apiTemplateFiles.put("api/api.mustache", ".java");
        apiTemplateFiles.put("api/api_interface.mustache", "Interface.java");

        typeMapping.put("array", "List");
        typeMapping.put("map", "Map");
        typeMapping.put("set", "Set");
        typeMapping.put("boolean", "Boolean");
        typeMapping.put("string", "String");
        typeMapping.put("int", "Integer");
        typeMapping.put("integer", "Integer");
        typeMapping.put("long", "Long");
        typeMapping.put("short", "Short");
        typeMapping.put("float", "Float");
        typeMapping.put("double", "Double");
        typeMapping.put("number", "BigDecimal");
        typeMapping.put("decimal", "BigDecimal");
        typeMapping.put("char", "String");
        typeMapping.put("object", "Object");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("binary", "InputStream");
        typeMapping.put("ByteArray", "byte[]");
        typeMapping.put("byte", "byte[]");
        typeMapping.put("file", "InputStream");
        typeMapping.put("File", "InputStream");
        typeMapping.put("date", "LocalDate");
        typeMapping.put("DateTime", "OffsetDateTime");
        typeMapping.put("date-time", "OffsetDateTime");
        typeMapping.put("time", "LocalTime");
        typeMapping.put("duration", "Duration");
        typeMapping.put("UUID", "UUID");
        typeMapping.put("URI", "URI");
        typeMapping.put("BigDecimal", "BigDecimal");

        importMapping.put("List", "java.util.List");
        importMapping.put("Set", "java.util.Set");
        importMapping.put("Map", "java.util.Map");
        importMapping.put("ArrayList", "java.util.ArrayList");
        importMapping.put("Arrays", "java.util.Arrays");
        importMapping.put("LinkedHashSet", "java.util.LinkedHashSet");
        importMapping.put("HashMap", "java.util.HashMap");
        importMapping.put("LocalDate", "java.time.LocalDate");
        importMapping.put("OffsetDateTime", "java.time.OffsetDateTime");
        importMapping.put("LocalTime", "java.time.LocalTime");
        importMapping.put("Duration", "java.time.Duration");
        importMapping.put("BigDecimal", "java.math.BigDecimal");
        importMapping.put("UUID", "java.util.UUID");
        importMapping.put("URI", "java.net.URI");
        importMapping.put("File", "java.io.File");
        importMapping.put("InputStream", "java.io.InputStream");
        importMapping.put("JsonProperty", "com.fasterxml.jackson.annotation.JsonProperty");
        importMapping.put("JsonValue", "com.fasterxml.jackson.annotation.JsonValue");
        importMapping.put("JsonCreator", "com.fasterxml.jackson.annotation.JsonCreator");
        importMapping.put("JsonInclude", "com.fasterxml.jackson.annotation.JsonInclude");
        importMapping.put("JsonTypeName", "com.fasterxml.jackson.annotation.JsonTypeName");
        importMapping.put("JsonTypeInfo", "com.fasterxml.jackson.annotation.JsonTypeInfo");
        importMapping.put("JsonSubTypes", "com.fasterxml.jackson.annotation.JsonSubTypes");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "long", "float", "double", "boolean", "byte", "short",
                                "char", "Integer", "Long", "Float", "Double", "Boolean", "String",
                                "Object", "byte[]", "void"));

        instantiationTypes.put("array", "ArrayList");
        instantiationTypes.put("set", "LinkedHashSet");
        instantiationTypes.put("map", "HashMap");

        reservedWords = new HashSet<>(loadReservedWords("/reserved-words/java.txt"));
        // The literals are not keywords but are just as illegal as an
        // identifier, so a property named null/true/false must be escaped.
        reservedWords.addAll(Set.of("null", "true", "false", "_"));

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
        cliOptions.add(CliOption.newString("repositoryUrl",
                "Source repository URL, written to the pom scm and url elements and the README"));
        cliOptions.add(CliOption.newString("homepageUrl",
                "Project homepage URL (defaults to repositoryUrl)"));
        cliOptions.add(CliOption.newString("authors",
                "Comma-separated author names, written to the pom developers"));
        cliOptions.add(CliOption.newString("documentationUrl",
                "Documentation URL, written to the README"));
        cliOptions.add(CliOption.newString("issueTrackerUrl",
                "Issue tracker URL, written to the pom issueManagement and the README"));
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "java-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Java client with Jackson and Apache HttpClient.";
    }

    /** {@inheritDoc} */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.JAVA;
    }

    /**
     * Derives the per-operation {@code hasMultipleConsumes} signal that the api
     * template uses to decide whether to emit the optional request-content-type
     * selector overloads.
     *
     * <p>An operation may declare more than one request {@code Content-Type}
     * (e.g. {@code setPetAvatar} declares {@code image/jpeg}, {@code image/png}
     * and {@code application/json}). The generated method historically pinned the
     * request header to {@code effectiveConsumes} — the first declared type — so
     * the remaining declared types were unreachable. When more than one type is
     * declared, the template emits an additional overload carrying an OPTIONAL
     * {@code requestContentType} argument that overrides the header; when it is
     * omitted (or {@code null}) the method keeps sending the first declared type
     * unchanged, so existing call sites compile and behave exactly as before.
     *
     * <p>Mustache cannot test "{@code consumes} has more than one element", so we
     * derive that boolean here, mirroring the language-scoped pattern used by the
     * Go and Python generators. This is a plain vendor-extension flag (not an
     * {@code x-*} extension and not a shared cross-language decorator), scoped to
     * the Java generator only.
     *
     * @param objs the operations map for the current api file
     * @param allModels every model referenced by the operations
     * @return the (possibly mutated) operations map, after delegating to the
     *     superclass post-processing
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            final List<CodegenOperation> ops =
                    (List<CodegenOperation>) operations.get("operation");
            if (ops != null) {
                for (final CodegenOperation op : ops) {
                    if (op.vendorExtensions == null) {
                        op.vendorExtensions = new HashMap<>();
                    }
                    final boolean hasMultipleConsumes =
                            op.consumes != null && op.consumes.size() > 1;
                    op.vendorExtensions.put("hasMultipleConsumes", hasMultipleConsumes);
                }
            }
        }
        final OperationsMap processed = super.postProcessOperationsWithModels(objs, allModels);
        /* After the shared pass: it is the one that drops the inherited global security from an
         * operation, and an operation whose only security is inherited takes no options object. */
        if (operations != null) {
            final List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
            if (ops != null) {
                for (final CodegenOperation op : ops) {
                    op.vendorExtensions.put("hasOptionalBodyOnly", hasOptionalBodyOnly(op));
                }
            }
        }
        return processed;
    }

    /**
     * Whether an operation takes nothing but an optional request body: no path, query, header,
     * form or cookie parameter, no authentication, no per-operation server and one request
     * content type. Only such an operation gets the no-argument overload (F324); with any other
     * argument an overload that omits the body would make a literal {@code null} ambiguous.
     */
    static boolean hasOptionalBodyOnly(CodegenOperation op) {
        return op.bodyParam != null
                && !op.bodyParam.required
                && isEmpty(op.pathParams)
                && isEmpty(op.queryParams)
                && isEmpty(op.headerParams)
                && isEmpty(op.formParams)
                && isEmpty(op.cookieParams)
                && !op.hasAuthMethods
                && isEmpty(op.servers)
                && (op.consumes == null || op.consumes.size() <= 1);
    }

    private static boolean isEmpty(@Nullable List<?> list) {
        return list == null || list.isEmpty();
    }

    /**
     * Puts the per-operation server argument before the options argument, so an
     * operation takes its required parameters, then the server, then the
     * optional-parameters object. Adding security (which adds the options
     * object) never moves an existing argument. Also publishes
     * {@code callArgsNullServer}: the server-less argument list with a literal
     * {@code null} standing in for the server, used by the overloads that omit
     * the server.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void populateOperationDecorators(CodegenOperation op) {
        super.populateOperationDecorators(op);
        if (op == null || op.vendorExtensions == null) {
            return;
        }
        final Object decorators = op.vendorExtensions.get("op");
        if (!(decorators instanceof Map)) {
            return;
        }
        final Map<String, Object> d = (Map<String, Object>) decorators;
        for (final String key : new String[] {"signatureArgs", "signatureArgsNoOptions"}) {
            final Object list = d.get(key);
            if (list instanceof List) {
                d.put(key, serverBeforeOptions((List<Map<String, Object>>) list));
            }
        }
        final Object noServer = d.get("signatureArgsNoServer");
        if (noServer instanceof List) {
            final List<Map<String, Object>> withNullServer = new ArrayList<>();
            boolean placed = false;
            for (final Map<String, Object> arg : (List<Map<String, Object>>) noServer) {
                if (Boolean.TRUE.equals(arg.get("isOptions"))) {
                    withNullServer.add(nullServerArg());
                    placed = true;
                }
                withNullServer.add(arg);
            }
            if (!placed) {
                withNullServer.add(nullServerArg());
            }
            d.put("callArgsNullServer", withNullServer);
        }
    }

    private static Map<String, Object> nullServerArg() {
        final Map<String, Object> arg = new HashMap<>();
        arg.put("kind", "server");
        arg.put("isServer", true);
        arg.put("isOptions", false);
        arg.put("paramName", "null");
        return arg;
    }

    /** Returns a copy of the list with the server entry moved before the options entry. */
    static List<Map<String, Object>> serverBeforeOptions(List<Map<String, Object>> args) {
        final List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> server = null;
        for (final Map<String, Object> arg : args) {
            if (Boolean.TRUE.equals(arg.get("isServer"))) {
                server = arg;
            }
        }
        for (final Map<String, Object> arg : args) {
            if (arg == server) {
                continue;
            }
            if (server != null && Boolean.TRUE.equals(arg.get("isOptions"))) {
                out.add(server);
            }
            out.add(arg);
        }
        if (server != null && !out.contains(server)) {
            out.add(server);
        }
        return out;
    }

    /**
     * Names of {@code java.lang} types that generated sources use unqualified. A model of the
     * same name would shadow them across the whole models package.
     */
    private static final Set<String> JAVA_LANG_CLASHES =
            Set.of(
                    "Error", "Exception", "Throwable", "String", "Object", "Integer", "Long",
                    "Short", "Byte", "Double", "Float", "Boolean", "Character", "Number", "Void",
                    "Class", "Enum", "Record", "Override", "Deprecated", "Thread", "System");

    /**
     * Prefixes a model named like a {@code java.lang} type with {@code Model}: {@code Error}
     * would shadow {@code java.lang.Error} (error-prone JavaLangClash) and {@code String} or
     * {@code Object} would break every model that uses them. {@code Result} has no java.lang
     * counterpart and keeps its own name.
     */
    @Override
    public String toModelName(String name) {
        final String modelName = super.toModelName(name);
        if (JAVA_LANG_CLASHES.contains(modelName)) {
            return "Model" + modelName;
        }
        return modelName;
    }

    /** Applies the {@link #toModelName} prefix to $ref property and parameter types. */
    @Override
    @SuppressWarnings("rawtypes")
    public String getSchemaType(Schema schema) {
        final String type = super.getSchemaType(schema);
        if ("Error".equals(type)) {
            return "Model" + type;
        }
        return type;
    }

    /**
     * The arguments an operation adds to its own signature: the per-call {@code options} object,
     * the {@code server} override and the {@code requestContentType} selector. A path or body
     * parameter named like one is suffixed so the method never has two parameters of one name
     * (F1055).
     */
    @Override
    protected Set<String> getInjectedArgumentNames() {
        // timeout, maxRetries and headers are the per-call settings every Options object
        // carries, so a parameter named like one becomes timeoutParam and so on.
        return Set.of(
                "options",
                "server",
                "requestContentType",
                "timeout",
                "maxRetries",
                "headers");
    }

    /**
     * Every operation gets a {@code <Operation>Options} object, so each call has a slot for the
     * per-call timeout, headers and retry budget even when the operation has no query, header,
     * form or cookie parameter and no security (F827).
     */
    @Override
    protected boolean mintsOptionsForEveryOperation() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "src/test/resources";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "src/spec/java";
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

    /** Formats every Java file in place with google-java-format. */
    private static final String GJF_REPLACE =
            "find . -name '*.java' -print0 | xargs -0 java"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                    + " -jar /tmp/gjf.jar --replace";

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "eclipse-temurin:21-jdk@sha256:b9142586f9712700c6c9e07adcedfb18608b1a3a056e4001423a3354adfa9d80";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            /* -f fails on an HTTP error rather than saving the error page as
             * /tmp/gjf.jar (which would later fail as a corrupt jar); --retry
             * rides out transient GitHub-release hiccups; -S shows the error. */
            "[ -f /cache/gjf-1.28.0.jar ] || { curl -fSL --retry 5 --retry-delay 2"
                + " --retry-all-errors -o /cache/gjf-1.28.0.jar.part"
                + " https://github.com/google/google-java-format/releases/download/v1.28.0/google-java-format-1.28.0-all-deps.jar"
                + " && mv /cache/gjf-1.28.0.jar.part /cache/gjf-1.28.0.jar; }",
            "cp /cache/gjf-1.28.0.jar /tmp/gjf.jar",
            GJF_REPLACE,
            /* A second pass: removing an unused import in the first pass can
             * leave two blank lines behind, which only a second pass collapses.
             * Without it the output is not a fixed point of google-java-format
             * and `mvn spotless:check` in the generated project fails. */
            GJF_REPLACE
        };
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "LinkedHashSet<";
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

    /**
     * Processes user-supplied codegen options after they are
     * resolved. Reads the invoker package, Maven coordinates,
     * and source folder, then registers all supporting files
     * for the client skeleton, exceptions, auth, serialization,
     * and optional test scaffolding.
     */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String sf = Optional.ofNullable((String) additionalProperties.get(CodegenConstants.SOURCE_FOLDER))
                .orElse(sourceFolder);
        final String pkg = Optional.ofNullable((String) additionalProperties.get(CodegenConstants.INVOKER_PACKAGE))
                .orElse(invokerPackage);
        final String invokerFolder = Path.of(sf, pkg.replace(".", "/")).toString();
        final String errorsFolder = Path.of(invokerFolder, "errors").toString();
        /* GraalVM native-image reflection metadata. The generator knows every
         * model class it emits, so it lists them in a reflect-config.json under
         * META-INF/native-image (auto-detected by native-image) — without it,
         * Jackson cannot reflectively (de)serialize the model POJOs in a native
         * image and fails at runtime. */
        final String nativeImageFolder = Path.of(
            "src/main/resources", "META-INF", "native-image", pkg.replace(".", "/")).toString();
        return List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("reflect_config.mustache", nativeImageFolder, "reflect-config.json"),
            new SupportingFileSpec("root_exception.mustache", errorsFolder, rootErrorName("Exception") + ".java"),
            new SupportingFileSpec("api_error.mustache", errorsFolder, "ApiException.java"),
            new SupportingFileSpec("errors/ErrorFactory.mustache", errorsFolder, "ErrorFactory.java"),
            new SupportingFileSpec("errors/SerializationException.mustache", errorsFolder, "SerializationException.java"),
            new SupportingFileSpec("errors/OAuth2ServerException.mustache", errorsFolder, "OAuth2ServerException.java"),
            new SupportingFileSpec("errors/OAuth2TokenException.mustache", errorsFolder, "OAuth2TokenException.java"),
            new SupportingFileSpec("errors/ClientException.mustache", errorsFolder, "ClientException.java"),
            new SupportingFileSpec("errors/ServerException.mustache", errorsFolder, "ServerException.java"),
            new SupportingFileSpec("errors/BadRequestException.mustache", errorsFolder, "BadRequestException.java"),
            new SupportingFileSpec("errors/UnauthorizedException.mustache", errorsFolder, "UnauthorizedException.java"),
            new SupportingFileSpec("errors/ForbiddenException.mustache", errorsFolder, "ForbiddenException.java"),
            new SupportingFileSpec("errors/NotFoundException.mustache", errorsFolder, "NotFoundException.java"),
            new SupportingFileSpec("errors/ConflictException.mustache", errorsFolder, "ConflictException.java"),
            new SupportingFileSpec("errors/PaymentRequiredException.mustache", errorsFolder, "PaymentRequiredException.java"),
            new SupportingFileSpec("errors/MethodNotAllowedException.mustache", errorsFolder, "MethodNotAllowedException.java"),
            new SupportingFileSpec("errors/RequestTimeoutException.mustache", errorsFolder, "RequestTimeoutException.java"),
            new SupportingFileSpec("errors/GoneException.mustache", errorsFolder, "GoneException.java"),
            new SupportingFileSpec("errors/PayloadTooLargeException.mustache", errorsFolder, "PayloadTooLargeException.java"),
            new SupportingFileSpec("errors/UnsupportedMediaTypeException.mustache", errorsFolder, "UnsupportedMediaTypeException.java"),
            new SupportingFileSpec("errors/LockedException.mustache", errorsFolder, "LockedException.java"),
            new SupportingFileSpec("errors/NotImplementedException.mustache", errorsFolder, "NotImplementedException.java"),
            new SupportingFileSpec("errors/BadGatewayException.mustache", errorsFolder, "BadGatewayException.java"),
            new SupportingFileSpec("errors/ServiceUnavailableException.mustache", errorsFolder, "ServiceUnavailableException.java"),
            new SupportingFileSpec("errors/GatewayTimeoutException.mustache", errorsFolder, "GatewayTimeoutException.java"),
            new SupportingFileSpec("errors/UnprocessableEntityException.mustache", errorsFolder, "UnprocessableEntityException.java"),
            new SupportingFileSpec("errors/TooManyRequestsException.mustache", errorsFolder, "TooManyRequestsException.java"),
            new SupportingFileSpec("errors/InternalServerErrorException.mustache", errorsFolder, "InternalServerErrorException.java"),
            new SupportingFileSpec("errors/NetworkException.mustache", errorsFolder, "NetworkException.java"),
            new SupportingFileSpec("errors/NetworkTimeoutException.mustache", errorsFolder, "NetworkTimeoutException.java"),
            new SupportingFileSpec("api_client.mustache", invokerFolder, "ApiClient.java"),
            new SupportingFileSpec("default_api_client.mustache", invokerFolder, "DefaultApiClient.java"),
            new SupportingFileSpec("api_response.mustache", invokerFolder, "ApiHttpResponse.java"),
            new SupportingFileSpec("api_result.mustache", invokerFolder, "ApiResult.java"),
            new SupportingFileSpec("base_api.mustache", Path.of(invokerFolder, "api").toString(), "BaseApi.java"),
            new SupportingFileSpec("configuration.mustache", invokerFolder, "Configuration.java"),
            new SupportingFileSpec("transport_options.mustache", invokerFolder, "TransportOptions.java"),
            new SupportingFileSpec("server_configuration.mustache", invokerFolder, "ServerConfiguration.java"),
            new SupportingFileSpec("server_variable.mustache", invokerFolder, "ServerVariable.java"),
            new SupportingFileSpec("servers.mustache", invokerFolder, "Servers.java"),
            new SupportingFileSpec("object_serializer.mustache", invokerFolder, "ObjectSerializer.java"),
            new SupportingFileSpec("value_serializer.mustache", Path.of(invokerFolder, "api").toString(), "ValueSerializer.java"),
            new SupportingFileSpec("header_selector.mustache", Path.of(invokerFolder, "api").toString(), "HeaderSelector.java"),
            new SupportingFileSpec("trace_context_util.mustache", invokerFolder, "TraceContextUtil.java"),
            new SupportingFileSpec("call_hook.mustache", invokerFolder, "CallHook.java"),
            new SupportingFileSpec("call_info.mustache", invokerFolder, "CallInfo.java"),
            new SupportingFileSpec("call_observer.mustache", invokerFolder, "CallObserver.java"),
            new SupportingFileSpec("call_outcome.mustache", invokerFolder, "CallOutcome.java"),
            new SupportingFileSpec(
                "open_telemetry_call_hook.mustache", invokerFolder, "OpenTelemetryCallHook.java"),
            new SupportingFileSpec("pom.mustache", "", "pom.xml"),
            new SupportingFileSpec(
                "checkstyle_suppressions.mustache", "", "checkstyle-suppressions.xml"),
            new SupportingFileSpec("authenticator.mustache", Path.of(invokerFolder, "auth").toString(), "Authenticator.java"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore"),
            new SupportingFileSpec(
                "dev_dependencies.mustache", ".openapi-generator", "DEV-DEPENDENCIES"),
            new SupportingFileSpec(
                "junit_platform_properties.mustache",
                "src/test/resources",
                "junit-platform.properties")
        );
    }

    /**
     * Harvests API-key header names from the spec's {@code securitySchemes}
     * (type=apiKey, in=header) and exposes them as
     * {@code apiKeyHeaderNames} for {@code DefaultApiClient.java} so they
     * are added to the sensitive-header allowlist stripped on cross-origin
     * redirects (Bucket 3.1).
     */
    @Override
    public void processOpenAPI(OpenAPI openAPI) {
        super.processOpenAPI(openAPI);
        final List<Map<String, String>> apiKeyHeaderNames = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        if (openAPI.getComponents() != null
                && openAPI.getComponents().getSecuritySchemes() != null) {
            for (Map.Entry<String, SecurityScheme> entry :
                    openAPI.getComponents().getSecuritySchemes().entrySet()) {
                final SecurityScheme scheme = entry.getValue();
                if (scheme.getType() == SecurityScheme.Type.APIKEY
                        && scheme.getIn() == SecurityScheme.In.HEADER
                        && scheme.getName() != null
                        && !scheme.getName().isEmpty()) {
                    final String lower = scheme.getName().toLowerCase(java.util.Locale.ROOT);
                    if (seen.add(lower)) {
                        final Map<String, String> e = new HashMap<>();
                        e.put("name", escapeJavaLiteral(scheme.getName()));
                        e.put("lowerName", escapeJavaLiteral(lower));
                        apiKeyHeaderNames.add(e);
                    }
                }
            }
        }
        additionalProperties.put("apiKeyHeaderNames", apiKeyHeaderNames);
        additionalProperties.put("hasApiKeyHeaderNames", !apiKeyHeaderNames.isEmpty());
        additionalProperties.put("clientSchemes", javaClientSchemes(openAPI));
    }

    /** Names the client builder itself declares, so a scheme setter never takes one of them. */
    private static final Set<String> BUILDER_METHOD_NAMES =
            Set.of("builder", "build", "transportOptions", "scheme", "host", "schemes");

    /**
     * Adds what the java client builder needs to each shared client scheme (F857): the
     * {@code importName} of the scheme's authenticator class (OAuth 2 and OpenID Connect schemes
     * live in the {@code auth.oauth} package) and the {@code methodName} of the builder setter,
     * the camel-case scheme name with {@code Credential} appended when it is a Java reserved
     * word or one of the builder's own method names.
     */
    private List<Map<String, Object>> javaClientSchemes(final OpenAPI openAPI) {
        final List<Map<String, Object>> out = new ArrayList<>();
        final Object shared = additionalProperties.get("clientSchemes");
        if (!(shared instanceof List<?>)) {
            return out;
        }
        for (final Object item : (List<?>) shared) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            final Map<String, Object> scheme = new java.util.LinkedHashMap<>();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                scheme.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            final String schemeId = String.valueOf(scheme.get("schemeId"));
            final String paramName = String.valueOf(scheme.get("paramName"));
            final SecurityScheme definition =
                    openAPI.getComponents().getSecuritySchemes().get(schemeId);
            final boolean oauth =
                    definition != null
                            && (definition.getType() == SecurityScheme.Type.OAUTH2
                                    || definition.getType() == SecurityScheme.Type.OPENIDCONNECT);
            scheme.put(
                    "importName",
                    invokerPackage
                            + ".auth"
                            + (oauth ? ".oauth." : ".")
                            + scheme.get("className"));
            scheme.put(
                    "methodName",
                    isReservedWord(paramName) || BUILDER_METHOD_NAMES.contains(paramName)
                            ? paramName + "Credential"
                            : paramName);
            out.add(scheme);
        }
        return out;
    }

    /** Java ships an adapter on the OpenTelemetry API, an optional dependency of the SDK. */
    @Override
    protected boolean hasOpenTelemetryAdapter() {
        return true;
    }

    /**
     * Registers the {@code javadocDescription} lambda. An OpenAPI description is
     * free prose and may contain angle brackets; javadoc and checkstyle both
     * read those as HTML, and an unclosed tag makes the whole doc comment
     * unparseable.
     */
    @Override
    protected com.google.common.collect.ImmutableMap.Builder<
                    String, com.samskivert.mustache.Mustache.Lambda>
            addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put(
                        "javadocDescription",
                        (fragment, writer) ->
                                writer.write(escapeForJavadoc(fragment.execute())))
                .put(
                        "javaString",
                        (fragment, writer) ->
                                writer.write(escapeForJavaString(fragment.execute())));
    }

    /**
     * Escapes raw text for use inside a Java string literal: backslash, double
     * quote and every control character.
     *
     * @param text the raw text
     * @return the text escaped for a Java string literal
     */
    private static String escapeForJavaString(@Nullable String text) {
        if (text == null) {
            return "";
        }
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == '"') {
                out.append("\\\"");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < 0x20 || c == 0x7F) {
                out.append(String.format(java.util.Locale.ROOT, "\\u%04X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Neutralises the angle brackets and ampersands javadoc reads as HTML. An inline
     * {@code literal} tag is used rather than an HTML entity because the
     * formatting spec forbids entities in generated source, and because the
     * rendered text is then the prose the document actually wrote.
     *
     * @param text the doc-comment prose to escape
     * @return the prose with each angle bracket wrapped in an inline literal tag
     */
    private static String escapeForJavadoc(@Nullable String text) {
        if (text == null) {
            return "";
        }
        /* A literal comment terminator would end the doc comment early, so
         * its slash is moved into an inline literal tag as well. A bare
         * ampersand is a bad HTML entity to doclint, so it is wrapped too;
         * it goes first so the tags added below are not rewritten. */
        return text.replace("&", "{@literal &}")
                .replace("<", "{@literal <}")
                .replace(">", "{@literal >}")
                .replace("*/", "*{@literal /}");
    }

    @Override
    public void processOpts() {
        super.processOpts();

        sourceFolder = getPropertyOrDefault(CodegenConstants.SOURCE_FOLDER, sourceFolder);
        invokerPackage = getPropertyOrDefault(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
        additionalProperties.put("invokerPackage", invokerPackage);

        final String groupId = getPropertyOrDefault(CodegenConstants.GROUP_ID, invokerPackage);
        additionalProperties.put("groupId", groupId);
        // Derive the artifactId from the spec (the invoker package's last
        // segment, e.g. com.example.petstore -> petstore-client) rather than
        // the openapi-generator default "openapi-java-client", so the Maven
        // coordinates reflect the API like the other SDKs' package names do.
        final String defaultArtifactId =
                invokerPackage.substring(invokerPackage.lastIndexOf('.') + 1) + "-client";
        final String artifactId =
                getPropertyOrDefault(CodegenConstants.ARTIFACT_ID, defaultArtifactId);
        additionalProperties.put("artifactId", artifactId);
        final String artifactVersion =
                getPropertyOrDefault(CodegenConstants.ARTIFACT_VERSION, "1.0.0");
        additionalProperties.put("artifactVersion", artifactVersion);
        // The homepage defaults to the repository, and the comma-separated authors become a
        // list the pom renders as one developer each (F894).
        final Object repositoryUrl = additionalProperties.get("repositoryUrl");
        if (repositoryUrl != null
                && !String.valueOf(repositoryUrl).isBlank()
                && additionalProperties.get("homepageUrl") == null) {
            additionalProperties.put("homepageUrl", repositoryUrl);
        }
        final Object authors = additionalProperties.get("authors");
        if (authors != null && !String.valueOf(authors).isBlank()) {
            final List<Map<String, String>> authorList = new ArrayList<>();
            for (final String author : String.valueOf(authors).split(",", -1)) {
                if (!author.isBlank()) {
                    authorList.add(Map.of("name", author.strip()));
                }
            }
            additionalProperties.put("authorList", authorList);
        }
        // The User-Agent product token is the published Maven artifactId, the
        // name a user types into their build file, not the Java package.
        additionalProperties.put(
                "userAgentDefault", artifactId + "/" + artifactVersion + " (java)");

        final String invokerFolder =
                Path.of(sourceFolder, invokerPackage.replace(".", "/")).toString();
        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".java"));
        supportingFiles.add(
                new SupportingFile(
                        "client_interface.mustache",
                        invokerFolder,
                        clientClassName + "Interface.java"));

        if (emitUnitTests()) {
            final String testFolder =
                    Path.of("src", "test", "java", invokerPackage.replace(".", "/")).toString();
            final String testApiFolder = Path.of(testFolder, "api").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientUnitTest.mustache",
                            testFolder,
                            "DefaultApiClientUnitTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TransportOptionsTest.mustache",
                            testFolder,
                            "TransportOptionsTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/HeaderSelectorTest.mustache",
                            testApiFolder,
                            "HeaderSelectorTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ValueSerializerTest.mustache",
                            testApiFolder,
                            "ValueSerializerTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TraceContextUtilTest.mustache",
                            testFolder,
                            "TraceContextUtilTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/CallHookTest.mustache", testFolder, "CallHookTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ConfigurationTest.mustache",
                            testFolder,
                            "ConfigurationTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ServerConfigurationTest.mustache",
                            testFolder,
                            "ServerConfigurationTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ServerVariableTest.mustache",
                            testFolder,
                            "ServerVariableTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiResultTest.mustache",
                            testFolder,
                            "ApiResultTest.java"));
        }

        if (generateTests) {
            final String testFolder =
                    Path.of("src", "test", "java", invokerPackage.replace(".", "/")).toString();
            final String testApiFolder = Path.of(testFolder, "api").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/PetApiTest.mustache", testApiFolder, "PetApiTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/StoreApiTest.mustache", testApiFolder, "StoreApiTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OpenTelemetryCallHookTest.mustache",
                            testFolder,
                            "OpenTelemetryCallHookTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientTest.mustache",
                            testFolder,
                            "DefaultApiClientTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ObjectSerializerTest.mustache",
                            testFolder,
                            "ObjectSerializerTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ChasmContainer.mustache", testFolder, "ChasmContainer.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/SquidContainer.mustache", testFolder, "SquidContainer.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/BaseApiTest.mustache",
                            testFolder,
                            "BaseApiTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ClientTest.mustache",
                            testFolder,
                            "ClientTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiExceptionTest.mustache",
                            testFolder,
                            "ApiExceptionTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ReadmeTest.mustache",
                            testFolder,
                            "ReadmeTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/SkillsTest.mustache",
                            testFolder,
                            "SkillsTest.java"));
            final String testModelsFolder = Path.of(testFolder, "models").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/MetadataTest.mustache",
                            testModelsFolder,
                            "MetadataTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ComposedSchemaTest.mustache",
                            testModelsFolder,
                            "ComposedSchemaTest.java"));

        }
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return sourceFolder;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean setsDiscriminatorParent() {
        return true;
    }

    /**
     * Builds the argument list a classic allOf child passes to its base
     * constructor: the base's own properties, in the base's order, read from
     * the child's builder. A required non-nullable property is checked here,
     * so the base constructor can take it as non-null.
     */
    @Override
    protected void decorateClassicDiscriminatorChild(
            final CodegenModel child, final CodegenModel base) {
        final List<String> args = new ArrayList<>();
        for (final CodegenProperty property : base.vars) {
            final String value = "builder." + property.name;
            if (property.required && !property.isNullable) {
                args.add(
                        "java.util.Objects.requireNonNull("
                                + value
                                + ", \""
                                + property.name
                                + " is required\")");
            } else {
                args.add(value);
            }
        }
        child.vendorExtensions.put("superArgs", String.join(", ", args));
    }

    /** {@inheritDoc} */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean demotesDiscriminatorFromRequiredVars() {
        return true;
    }

    /**
     * Returns the default value expression for a schema type.
     * Arrays default to empty ArrayList or LinkedHashSet for
     * unique items; maps default to empty HashMap. String enum
     * schemas with a default return the raw value so that
     * {@code updateCodegenPropertyEnum} can match it to an enum
     * var and produce {@code StatusEnum.PLACED}. Non-enum scalar
     * schemas carrying a {@code default} render it as a Java
     * literal (quoted string, numeric, {@code L}/{@code F}-suffixed
     * long/float, or boolean) so the model field is initialized to
     * the schema default. This makes an absent JSON key fall back
     * to the default while an explicit JSON null is still preserved
     * (Jackson overwrites the initializer only when the key is
     * present). All other types return null.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema unaliased = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (ModelUtils.isArraySchema(unaliased)) {
            if (Boolean.TRUE.equals(unaliased.getUniqueItems())) {
                return "new LinkedHashSet<>()";
            }
            return "new ArrayList<>()";
        } else if (ModelUtils.isMapSchema(unaliased)) {
            return "new HashMap<>()";
        }
        if (ModelUtils.isStringSchema(unaliased)
                && unaliased.getDefault() != null
                && unaliased.getEnum() != null
                && !unaliased.getEnum().isEmpty()) {
            return unaliased.getDefault().toString();
        }
        if (unaliased.getDefault() != null) {
            if (ModelUtils.isStringSchema(unaliased)) {
                return "\"" + escapeForJavaString(String.valueOf(unaliased.getDefault())) + "\"";
            }
            if (ModelUtils.isLongSchema(unaliased)) {
                return unaliased.getDefault() + "L";
            }
            if (ModelUtils.isFloatSchema(unaliased)) {
                return unaliased.getDefault() + "F";
            }
            // A {@code number} with {@code format: double} maps to
            // {@code Double}; the D suffix makes an integral default such as
            // {@code 2} a double literal (a bare int does not box to Double).
            if (ModelUtils.isDoubleSchema(unaliased)) {
                return unaliased.getDefault() + "D";
            }
            // A format-less {@code number} (and the explicit
            // {@code decimal} type) maps to {@code BigDecimal}, which
            // has no numeric-literal conversion. A bare literal like
            // {@code 12.5} would not compile, so wrap the default in a
            // {@code BigDecimal(String)} constructor. Using a string
            // also preserves the exact decimal value.
            if (ModelUtils.isNumberSchema(unaliased)
                    || ModelUtils.isDecimalSchema(unaliased)) {
                return "new java.math.BigDecimal(\""
                        + escapeText(String.valueOf(unaliased.getDefault()))
                        + "\")";
            }
            if (ModelUtils.isIntegerSchema(unaliased)
                    || ModelUtils.isBooleanSchema(unaliased)) {
                return unaliased.getDefault().toString();
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
        // no-op: StatusEnum.PLACED is the correct Java form
    }

    /**
     * Preserves all-uppercase identifiers (e.g. {@code MAX_RETRIES},
     * {@code HTTP_METHOD}) as-is instead of camelCasing them.
     * Java treats these as intentional constant names that should
     * not be transformed. Cannot be standardized because other
     * languages either always apply casing or lowercase first.
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
    protected List<String> getUniversalModelPropertyImports() {
        return List.of("JsonProperty", "JsonInclude", "JsonTypeName");
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getEnumPropertyImports() {
        return List.of("JsonValue", "JsonCreator");
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getArrayPropertyImports() {
        return List.of("ArrayList", "Arrays");
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getUniqueArrayPropertyImports() {
        return List.of("LinkedHashSet");
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getMapPropertyImports() {
        return List.of("HashMap");
    }

    /**
     * Replaces the {@code ArrayList} default value with a
     * {@code LinkedHashSet} for unique-item array properties.
     * All import additions are handled by the base-class
     * declaration methods.
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (!model.isEnum && property.isArray && property.getUniqueItems()
                && property.defaultValue != null) {
            property.defaultValue =
                    property.defaultValue.replace("new ArrayList<>(", "new LinkedHashSet<>(");
        }
        decorateForBuilder(property);
    }

    /**
     * Records what the immutable model needs from a property: how its collection is copied and
     * the name of its builder method. A property named like a reserved word is escaped with a
     * leading underscore, which is no legal method or parameter name for Checkstyle, so its
     * builder method takes a {@code Value} suffix instead.
     */
    private static void decorateForBuilder(CodegenProperty property) {
        recordCollectionCopy(property);
        property.vendorExtensions.put(
                "builderName",
                property.name != null && property.name.startsWith("_")
                        ? property.name.replaceFirst("^_+", "") + "Value"
                        : property.name);
    }

    /**
     * Adds Jackson enum serialization imports (JsonValue,
     * JsonCreator) to enum models and properties. Cannot be
     * standardized because Jackson is Java-specific.
     */
    @Override
    public ModelsMap postProcessModelsEnum(ModelsMap objs) {
        objs = super.postProcessModelsEnum(objs);
        for (final ModelMap modelMap : objs.getModels()) {
            final CodegenModel model = modelMap.getModel();
            if (model.isEnum) {
                model.imports.add("JsonValue");
                model.imports.add("JsonCreator");
            }
            for (final CodegenProperty property : model.vars) {
                if (property.isEnum) {
                    model.imports.add("JsonValue");
                    model.imports.add("JsonCreator");
                }
            }
        }
        return objs;
    }

    /**
     * Adds Jackson discriminator and polymorphism imports
     * (JsonTypeInfo, JsonSubTypes) for models with discriminators,
     * and JsonValue/JsonCreator for oneOf/anyOf models. Cannot
     * be standardized because Jackson is Java-specific.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public CodegenModel fromModel(String name, Schema schema) {
        final CodegenModel model = super.fromModel(name, schema);
        if (model.discriminator != null) {
            model.imports.add("JsonTypeInfo");
            model.imports.add("JsonSubTypes");
        }
        if (!model.oneOf.isEmpty() || !model.anyOf.isEmpty()) {
            model.imports.add("JsonValue");
            model.imports.add("JsonCreator");
            /* oneOf/anyOf variant types (e.g. List<byte[]>) are referenced
             * only in the ONE_OF_SCHEMAS TypeReference tokens, not as model
             * fields, so the property-driven import collection misses them.
             * A pure-union wrapper has no vars, so without this its
             * collection variants compile with an unresolved `List`/`Map`/
             * `Set`. Add the simple import name (resolved to its FQN by the
             * import mapping, exactly like a normal field type). */
            final java.util.Set<String> variants = new java.util.HashSet<>();
            variants.addAll(model.oneOf);
            variants.addAll(model.anyOf);
            for (final String variant : variants) {
                if (variant.contains("List<") || variant.equals("List")) {
                    model.imports.add("List");
                }
                if (variant.contains("Map<") || variant.equals("Map")) {
                    model.imports.add("Map");
                }
                if (variant.contains("Set<") || variant.equals("Set")) {
                    model.imports.add("Set");
                }
            }
        }
        nameUnionVariants(model);
        /* requiredVars are copies of the properties the hook above decorated, so they are
         * decorated here for the required-argument constructor. */
        for (final CodegenProperty required : model.requiredVars) {
            decorateForBuilder(required);
        }
        // The model description is rendered as the type's header Javadoc, whose
        // summary sentence must end with a period under google_checks
        // (SummaryJavadoc). That Javadoc sits above the class-level
        // @SuppressWarnings, so the suppression cannot cover it; normalize the
        // spec-supplied text to be compliant instead.
        model.description = endWithPeriod(model.description);
        return model;
    }

    /**
     * Records how a collection-typed property is copied into the immutable model:
     * {@code copyKind} is {@code List}, {@code Set} or {@code Map} (the
     * {@code Collections.unmodifiableX} suffix) and {@code copyImpl} the mutable class the
     * copy is made with. Scalars get neither.
     */
    private static void recordCollectionCopy(CodegenProperty property) {
        final String type = property.isEnum ? property.datatypeWithEnum : property.dataType;
        if (type == null) {
            return;
        }
        if (type.startsWith("Set<")) {
            property.vendorExtensions.put("copyKind", "Set");
            property.vendorExtensions.put("copyImpl", "LinkedHashSet");
        } else if (type.startsWith("List<")) {
            property.vendorExtensions.put("copyKind", "List");
            property.vendorExtensions.put("copyImpl", "ArrayList");
        } else if (type.startsWith("Map<")) {
            property.vendorExtensions.put("copyKind", "Map");
            property.vendorExtensions.put("copyImpl", "LinkedHashMap");
        }
    }

    /**
     * Names the variants of a oneOf or anyOf union for the typed factories and accessors
     * (F878): {@code variantName} is the Java type name as an identifier part
     * ({@code Reservation}, {@code ListOfString}), and {@code isPlainVariant} is true when
     * the variant is a bare class name, so an {@code instanceof} test is legal for it.
     */
    static void nameUnionVariants(CodegenModel model) {
        if (model.getComposedSchemas() == null) {
            return;
        }
        final List<List<CodegenProperty>> unions = new ArrayList<>();
        if (model.getComposedSchemas().getOneOf() != null) {
            unions.add(model.getComposedSchemas().getOneOf());
        }
        if (model.getComposedSchemas().getAnyOf() != null) {
            unions.add(model.getComposedSchemas().getAnyOf());
        }
        for (final List<CodegenProperty> variants : unions) {
            for (final CodegenProperty variant : variants) {
                final String type = variant.dataType == null ? "Object" : variant.dataType;
                final StringBuilder name = new StringBuilder();
                for (final String word :
                        type.replace("[]", " Array ").replace("<", " Of ").split("[^A-Za-z0-9]+", -1)) {
                    if (!word.isEmpty()) {
                        name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                    }
                }
                variant.vendorExtensions.put("variantName", name.length() == 0 ? "Value" : name.toString());
                variant.vendorExtensions.put("isPlainVariant", type.matches("[A-Za-z_][A-Za-z0-9_.]*"));
            }
        }
    }

    /**
     * Appends a trailing period to a Javadoc summary string when it does not
     * already end with sentence-ending punctuation, so generated header Javadoc
     * satisfies google_checks {@code SummaryJavadoc}. Returns the input
     * unchanged when it is {@code null} or blank.
     */
    @Nullable
    private static String endWithPeriod(@Nullable String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.stripTrailing();
        if (trimmed.isEmpty()) {
            return text;
        }
        final char last = trimmed.charAt(trimmed.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return text;
        }
        return trimmed + ".";
    }

    /**
     * Registers base auth supporting files and, when test generation
     * is enabled, the OAuth test class files.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();

        if (generateTests) {
            final String testFolder =
                    Path.of("src", "test", "java", invokerPackage.replace(".", "/")).toString();
            final String testAuthFolder =
                    Path.of(testFolder, "auth").toString();
            final String testAuthOauthFolder =
                    Path.of(testFolder, "auth", "oauth").toString();
            if (hasBasicAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/BasicAuthenticatorTest.mustache",
                                testAuthFolder,
                                "BasicAuthenticatorTest.java"));
            }
            if (hasBearerAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/BearerAuthenticatorTest.mustache",
                                testAuthFolder,
                                "BearerAuthenticatorTest.java"));
            }
            if (hasApiKeyAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/ApiKeyAuthenticatorTest.mustache",
                                testAuthFolder,
                                "ApiKeyAuthenticatorTest.java"));
            }
            if (hasBasicAuth || hasBearerAuth || hasApiKeyAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/SchemeAuthenticatorTest.mustache",
                                testAuthFolder,
                                "SchemeAuthenticatorTest.java"));
            }
            if (hasAnyOAuth2 || hasOpenIdConnect) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2TokenManagerTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2TokenManagerTest.java"));
            }
            if (hasOAuth2AuthorizationCode) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2AuthCodeAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2AuthorizationCodeAuthenticatorTest.java"));
            }
            if (hasOAuth2Implicit) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2ImplicitAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2ImplicitAuthenticatorTest.java"));
            }
            if (hasOAuth2ClientCredentials) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2ClientCredentialsAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2ClientCredentialsAuthenticatorTest.java"));
            }
            if (hasOAuth2Password) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2PasswordAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2PasswordAuthenticatorTest.java"));
            }
            if (hasOpenIdConnect) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OpenIdConnectAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OpenIdConnectAuthenticatorTest.java"));
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of(sourceFolder, invokerPackage.replace(".", "/"), "auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return pascalAuthFilename(stem, ".java");
    }

    /**
     * Renders a per-scheme authenticator Java source file using
     * the scheme_authenticator.mustache template.
     */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("package", invokerPackage + (spec.isOAuth() ? ".auth.oauth" : ".auth"));
        final List<String> imports;
        if (spec.isOAuth()) {
            imports = List.of(invokerPackage + ".auth.Authenticator", "java.util.List");
        } else {
            imports = List.of();
        }
        ctx.put("imports", imports);
        final Map<String, String> envNameByField = new HashMap<>();
        for (final Map<String, Object> entry : schemeEnvVars(ctx)) {
            envNameByField.put((String) entry.get("field"), (String) entry.get("envName"));
        }
        final List<Map<String, Object>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, Object> param = new HashMap<>();
            param.put("type", "String");
            param.put("name", name);
            final String envName = envNameByField.get(name);
            param.put("hasEnv", envName != null);
            if (envName != null) {
                param.put("envName", envName);
            }
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        ctx.put("isBearerWrapper", "BearerAuthenticator".equals(spec.baseClass()));
        ctx.put("schemeNameLiteral", escapeJavaLiteral(spec.schemeName()));
        ctx.put("superArgs", schemeSuperArgs(spec, envNameByField));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    private static String formatJavaScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "List.of()";
        }
        return "List.of(\"" + String.join("\", \"", scopes.keySet()) + "\")";
    }

    /**
     * Escapes text for the body of a Java string literal.
     *
     * @param text raw text, possibly null
     * @return the text with backslashes, quotes and control characters escaped
     */
    static String escapeJavaLiteral(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /** The per-field environment variable entries {@link #baseSchemeContext} gave this scheme. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> schemeEnvVars(Map<String, Object> ctx) {
        final Object own = ctx.get("envVars");
        return own instanceof List ? (List<Map<String, Object>>) own : List.of();
    }

    /**
     * The arguments a scheme wrapper hands to its base authenticator: each credential that has an
     * environment variable goes through {@code resolveCredential} (the caller's value, else the
     * variable, else a refusal).
     */
    private List<String> schemeSuperArgs(SchemeAuthSpec spec, Map<String, String> envNameByField) {
        final List<String> resolved = new ArrayList<>();
        for (final String arg : buildJavaSuperArgs(spec)) {
            final String envName = envNameByField.get(arg);
            resolved.add(
                    envName == null
                            ? arg
                            : "resolveCredential("
                                    + arg
                                    + ", \""
                                    + arg
                                    + "\", \""
                                    + escapeJavaLiteral(envName)
                                    + "\", environment)");
        }
        return resolved;
    }

    private List<String> buildJavaSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + escapeJavaLiteral(java.util.Objects.toString(spec.keyParamName(), "")) + "\"", "apiKey",
                    "ApiKeyLocation." + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"", formatJavaScopes(spec.scopes()));
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "null";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"", "username", "password",
                    formatJavaScopes(spec.scopes()), refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "null";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.authorizationUrl() + "\"", "\"" + spec.tokenUrl() + "\"",
                    "redirectUri", formatJavaScopes(spec.scopes()), refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId",
                    "\"" + spec.authorizationUrl() + "\"", formatJavaScopes(spec.scopes()));
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.openIdConnectUrl() + "\"",
                    "clientId", "clientSecret", "redirectUri", "List.of()");
        }
        return List.of();
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        final List<Map<String, Object>> requiredParams = new ArrayList<>();
        /* The shared pass captures the named examples only after the Options files are written, so
         * capture them here first or an optional parameter's examples never reach its setter (F893). */
        capturePluralExamplesForParameters(optionsParams);
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("baseName", p.baseName);
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            param.put("requiredNullable", isRequiredNullableQuery(p));
            // Thread the spec parameter's deprecated flag into the Options field so
            // the generated field and accessor carry @Deprecated (and a @deprecated
            // Javadoc tag), mirroring the model-property deprecation idiom. A param
            // declared deprecated:true in the spec otherwise shows callers no
            // deprecation warning on the Options object.
            param.put("deprecated", p.isDeprecated);
            // Thread the spec parameter description into the Options field so the
            // generated accessor Javadoc documents what the parameter means,
            // matching the per-parameter documentation the other SDKs carry.
            if (p.description != null && !p.description.isBlank()) {
                param.put("description", endWithPeriod(p.description.strip()));
            }
            // Array/map params are stored and returned by reference, so SpotBugs
            // flags EI_EXPOSE_REP/EI_EXPOSE_REP2 on the generated accessor and
            // constructor. Rather than suppress (a class-level @SuppressFBWarnings
            // for a member-level bug is itself reported as US_USELESS_SUPPRESSION
            // under default SpotBugs), the template defensively copies the
            // collection into an immutable view, which SpotBugs recognizes as
            // exposure-safe — no suppression and no config relaxation needed.
            final String copyFactory = collectionCopyFactory(p);
            param.put("isMutableCollection", copyFactory != null);
            // A format: byte parameter is a mutable byte[]; it is copied in and
            // out so the options object never shares the caller's array.
            param.put("isByteArrayParam", "byte[]".equals(p.dataType));
            // Named examples of an optional parameter belong on its setter, as the
            // required ones belong on the operation (F893).
            final Object pluralExamples =
                    p.vendorExtensions == null ? null : p.vendorExtensions.get("pluralExamples");
            if (pluralExamples instanceof List && !((List<?>) pluralExamples).isEmpty()) {
                param.put("pluralExamples", pluralExamples);
            }
            if (copyFactory != null) {
                param.put("copyFactory", copyFactory);
            }
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
            // A $ref to a top-level enum carries its enum type name in dataType
            // (baseType is null); the generated enum lives in the models package
            // and must be imported by the Options class.
            if (p.isEnumRef
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)
                    && !typeMapping.containsValue(p.dataType)) {
                modelTypes.add(p.dataType);
            }
            // An object-typed (style form/deepObject) or content-encoded
            // parameter may carry the model name only in dataType.
            if (!p.isArray
                    && !p.isMap
                    && p.dataType != null
                    && p.dataType.matches("[A-Z][A-Za-z0-9_]*")
                    && !languageSpecificPrimitives.contains(p.dataType)
                    && !typeMapping.containsValue(p.dataType)
                    && !importMapping.containsKey(p.dataType)) {
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

        // Parameters typed as java.time / java.math / java.util.UUID / java.net.URI
        // surfaces (e.g. an OffsetDateTime date-time query param) need their
        // fully-qualified import in the Options class. The List/Map/Set/File/
        // InputStream types are already imported by the template header, so they
        // are skipped to avoid duplicate-import errors.
        final Set<String> headerImported =
                Set.of("List", "Map", "Set", "File", "InputStream");
        final Set<String> extraImports = new LinkedHashSet<>();
        for (final CodegenParameter p : optionsParams) {
            for (final Map.Entry<String, String> mapping : importMapping.entrySet()) {
                if (headerImported.contains(mapping.getKey())
                        || !mapping.getValue().startsWith("java.")) {
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
        context.put("modelImports", new ArrayList<>(modelTypes));
        context.put("hasModelImports", !modelTypes.isEmpty());
        context.put("extraImports", new ArrayList<>(extraImports));
        context.put("hasExtraImports", !extraImports.isEmpty());
        return renderOptionsTemplate("api/options.mustache", context);
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
            final boolean leftBoundary = from == 0 || !isJavaIdentifierPart(type.charAt(from - 1));
            final boolean rightBoundary =
                    end == type.length() || !isJavaIdentifierPart(type.charAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            from = end;
        }
        return false;
    }

    private static boolean isJavaIdentifierPart(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    /**
     * Returns the immutable-copy factory ({@code List.copyOf}, {@code Set.copyOf},
     * or {@code Map.copyOf}) for a collection-typed options parameter, or
     * {@code null} when the parameter is not a stored-by-reference collection.
     *
     * <p>Used by {@code api/options.mustache} to emit defensive copies so the
     * generated Options classes do not trip SpotBugs EI_EXPOSE_REP/EI_EXPOSE_REP2
     * under the default (no custom exclude filter) analysis.
     */
    @Nullable
    private static String collectionCopyFactory(CodegenParameter p) {
        if (p.isMap) {
            return "java.util.Map";
        }
        if (p.isArray) {
            return (p.dataType != null && p.dataType.startsWith("Set"))
                    ? "java.util.Set"
                    : "java.util.List";
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaTemplate() {
        return "errors/SchemaException.mustache";
    }

    @Override
    protected String errorSchemaFilePath(String className) {
        return Path.of(outputFolder, sourceFolder, invokerPackage.replace('.', '/'), "errors", className + ".java")
                .toString();
    }

    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        return Path.of(
                        outputFolder,
                        sourceFolder,
                        apiPackage().replace('.', '/'),
                        "options",
                        optionsClassName + ".java")
                .toString();
    }
}

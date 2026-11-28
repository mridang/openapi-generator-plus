package io.github.mridang.codegen.generators.go;

import com.google.common.base.Splitter;
import com.samskivert.mustache.Mustache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenDiscriminator;
import org.openapitools.codegen.CodegenModel;
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
 * Generates a Go API client that uses net/http for transport
 * and encoding/json for model serialization. All exported
 * identifiers use PascalCase per Go convention, while
 * unexported fields use camelCase. Filenames use snake_case.
 * The formatter pass invokes gofmt inside Docker to enforce
 * canonical Go formatting.
 */
@SuppressWarnings("unused")
public class BetterGoCodegen extends AbstractBetterCodegen {

    @Override
    protected String errorSuffix() {
        return "Error";
    }

    @Override
    protected String errorSchemaTemplate() {
        return "errors/schema_error.mustache";
    }

    @Override
    protected String errorSchemaFilePath(String className) {
        return Path.of(
                        outputFolder,
                        "pkg",
                        "errors",
                        org.openapitools.codegen.utils.StringUtils.underscore(className) + ".go")
                .toString();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterGoCodegen.class);

    /**
     * Matches a Go data type that names the generated {@code models} wrapper
     * type {@code DateTime} as a whole word, including inside {@code []},
     * {@code *} and {@code map[...]} compositions.
     */
    private static final Pattern WRAPPER_TYPE = Pattern.compile("\\bDateTime\\b");

    /**
     * The behaviour id of the environment-failure test of each named scheme authenticator:
     * authscheme004 to authscheme008 name the scheme, so one test name carries one id.
     */
    private static final Map<String, String> FAILS_NAMING_IDS = new HashMap<>();

    static {
        FAILS_NAMING_IDS.put("AdminBasicAuthenticator", "BhvAuthscheme004_");
        FAILS_NAMING_IDS.put("ApiKeyHeaderAuthenticator", "BhvAuthscheme005_");
        FAILS_NAMING_IDS.put("ApiKeyQueryAuthenticator", "BhvAuthscheme006_");
        FAILS_NAMING_IDS.put("SessionCookieAuthenticator", "BhvAuthscheme007_");
        FAILS_NAMING_IDS.put("SsoAuthAuthenticator", "BhvAuthscheme008_");
    }

    private static final String OPTION_REPOSITORY_URL = "repositoryUrl";
    private static final String OPTION_HOMEPAGE_URL = "homepageUrl";
    private static final String OPTION_AUTHORS = "authors";
    private static final String OPTION_DOCUMENTATION_URL = "documentationUrl";
    private static final String OPTION_ISSUE_TRACKER_URL = "issueTrackerUrl";

    protected String packageName = "openapiclient";
    protected String packageVersion = "1.0.0";

    /** One entry per security scheme, the fields of the generated {@code Credentials} struct. */
    private final List<Map<String, Object>> credentialSchemes = new ArrayList<>();

    /** One entry per per-scheme authenticator, read by {@code scheme_authenticator_test}. */
    private final List<Map<String, Object>> schemeAuthTests = new ArrayList<>();

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Go language. Type mappings convert OpenAPI
     * types to their Go equivalents (e.g. integer to int32,
     * DateTime to the generated models.DateTime wrapper). Reserved words are loaded from a
     * bundled word-list to avoid generating identifiers that
     * clash with Go keywords and predeclared identifiers.
     */
    public BetterGoCodegen() {
        outputFolder = Path.of("generated-code", "go").toString();
        embeddedTemplateDir = templateDir = "templates/go";

        modelTemplateFiles.put("models/model.mustache", ".go");
        apiTemplateFiles.put("api/api.mustache", ".go");

        modelPackage = "";
        apiPackage = "";

        typeMapping.put("string", "string");
        typeMapping.put("boolean", "bool");
        typeMapping.put("int", "int32");
        typeMapping.put("integer", "int32");
        typeMapping.put("long", "int64");
        typeMapping.put("short", "int32");
        typeMapping.put("float", "float32");
        typeMapping.put("double", "float64");
        typeMapping.put("number", "float64");
        // format:decimal maps to decimal.Decimal from github.com/shopspring/decimal,
        // an arbitrary-precision decimal (#157, D135). A number-shaped decimal is
        // read from the digits of the JSON number and written back as a bare
        // number in plain notation; a string-shaped one travels as a JSON string.
        // getSchemaType maps a `type: number, format: decimal` the same way.
        typeMapping.put("decimal", "decimal.Decimal");
        // format:date maps to civil.Date from cloud.google.com/go/civil, a
        // calendar date whose text form is YYYY-MM-DD. Parsing rejects a value
        // that is not a real date (2024-02-30), so no caller can send one.
        typeMapping.put("date", "civil.Date");
        // format:date-time maps to the generated models.DateTime, a time.Time
        // whose JSON and parameter form is the canonical wire string
        // (YYYY-MM-DDTHH:MM:SS.mmm+HH:MM: numeric offset, never "Z", and a
        // fixed three-digit millisecond fraction). time.Time itself
        // JSON-encodes as RFC 3339 with "Z" and a variable-width fraction.
        typeMapping.put("DateTime", "DateTime");
        // 4.8: format:time maps to string: Go's stdlib has no civil-time
        // type, and cloud.google.com/go/civil would drag in a 100MB+ dep tree
        // just for a struct. The generated iso8601.go ships FormatTimeOfDay /
        // ParseTimeOfDay for callers who want a time.Time.
        //
        // format:duration maps to the standard library's time.Duration (D145).
        // encoding/json writes a time.Duration as int64 nanoseconds, so the
        // models and the serializer rewrite it to and from the protobuf-JSON
        // google.protobuf.Duration string ("3600s") through the generated
        // internal protoduration package.
        typeMapping.put("time", "string");
        typeMapping.put("duration", "time.Duration");
        typeMapping.put("ipv4", "netip.Addr");
        typeMapping.put("ipv6", "netip.Addr");
        typeMapping.put("array", "[]");
        typeMapping.put("List", "[]");
        typeMapping.put("map", "map");
        typeMapping.put("object", "any");
        typeMapping.put("AnyType", "any");
        typeMapping.put("file", "io.Reader");
        typeMapping.put("binary", "[]byte");
        typeMapping.put("ByteArray", "[]byte");
        typeMapping.put("UUID", "uuid.UUID");
        typeMapping.put("URI", "string");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "string",
                                "bool",
                                "int32",
                                "int64",
                                "float32",
                                "float64",
                                "byte",
                                "any",
                                "error",
                                "uuid.UUID",
                                "civil.Date",
                                "netip.Addr",
                                "time.Duration",
                                "decimal.Decimal"));

        reservedWords = loadReservedWords("/reserved-words/go.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated Go module (default: 1.0.0).").defaultValue("1.0.0"));
        cliOptions.add(CliOption.newString(OPTION_REPOSITORY_URL,
                "Source repository URL, written to the README."));
        cliOptions.add(CliOption.newString(OPTION_HOMEPAGE_URL,
                "Project homepage URL, written to the README."));
        cliOptions.add(CliOption.newString(OPTION_AUTHORS,
                "Comma-separated author names, written to the README."));
        cliOptions.add(CliOption.newString(OPTION_DOCUMENTATION_URL,
                "Documentation URL, written to the README (optional)."));
        cliOptions.add(CliOption.newString(OPTION_ISSUE_TRACKER_URL,
                "Issue tracker URL, written to the README (optional)."));
    }

    /**
     * The names an operation adds to its own signature or declares as locals or
     * imports, which a path or body parameter must not reuse: the duplicate or
     * shadowing declaration would not compile (F1055).
     */
    @Override
    protected Set<String> getInjectedArgumentNames() {
        return Set.of(
                "a", "ctx", "options", "server", "callOptions", "path", "queryParams",
                "headerParams", "formBody", "requestBody", "response", "data", "dataPtr",
                "result", "err", "auth", "cookieParts", "settings", "opts", "fmt", "strings",
                "io", "context", "uuid", "declaredConsumes", "requestContentTypeValue");
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "go-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Go client with net/http.";
    }

    /**
     * Declares Go as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.GO;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "testdata";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "spec";
    }

    /** {@inheritDoc} */
    @Override
    protected boolean shouldEscapeReservedVarName(String name) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getVarCasing() {
        return NamingConvention.PASCAL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getOperationIdCasing() {
        return NamingConvention.PASCAL_CASE;
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
    protected NamingConvention getParamCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /** Go ships an adapter on the OpenTelemetry API in the subpackage {@code pkg/otelhook}. */
    @Override
    protected boolean hasOpenTelemetryAdapter() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "golang:1.26@sha256:2d6c80227255c3112a4d08e67ba98e58efd3846daf15d9d7d4c389565d881b1a";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            // goimports is built once and kept on the shared cache volume.
            "export GOBIN=/cache/go-bin GOCACHE=/cache/go-build PATH=\"$PATH:/cache/go-bin\"",
            "[ -x /cache/go-bin/goimports ] || go install golang.org/x/tools/cmd/goimports@v0.45.0",
            "go mod tidy",
            "goimports -w ."
        };
    }

    /**
     * Preserves all-uppercase identifiers like HTTP, URL, ID
     * as Go convention keeps uppercase abbreviations intact.
     */
    @Override
    protected UppercaseIdentifierStrategy getUppercaseIdentifierStrategy() {
        return UppercaseIdentifierStrategy.PRESERVE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getOperationIdReservedPrefix() {
        return "call_";
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("int32", "int64", "float32", "float64");
    }

    /** {@inheritDoc} */
    @Override
    protected String getNullLiteral() {
        return "nil";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayTypeTemplate() {
        return "[]%2$s";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "map[%2$s]%3$s";
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "Set[$1]";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayContainerPattern() {
        return "^\\[\\](.+)$";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "pkg";
    }

    /**
     * Returns {@code string} as the map key type because Go
     * maps use string keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "string";
    }

    /**
     * Returns {@code any} as the default map value
     * type for Go's universal empty interface alias.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "any";
    }

    /**
     * Resolves user-supplied options and registers all
     * supporting files for the Go package structure.
     * Sets up the package layout including models, API
     * classes, errors, auth, configuration, and serializers
     * all in the root package directory.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put("userAgentDefault", packageName + "/" + packageVersion + " (go)");

        additionalProperties.put("schemeAuthTests", schemeAuthTests);

        /* Package metadata options: the README shows only the ones that were given. */
        final List<Map<String, String>> authorsList = new ArrayList<>();
        final Object authorsOption = additionalProperties.get(OPTION_AUTHORS);
        if (authorsOption != null) {
            for (final String author : Splitter.on(',').split(String.valueOf(authorsOption))) {
                if (!author.trim().isEmpty()) {
                    authorsList.add(Map.of("name", author.trim()));
                }
            }
        }
        additionalProperties.put("authorsList", authorsList);
        additionalProperties.put("hasAuthors", !authorsList.isEmpty());
        boolean hasProjectUrls = false;
        for (final String urlOption :
                new String[] {
                    OPTION_REPOSITORY_URL,
                    OPTION_HOMEPAGE_URL,
                    OPTION_DOCUMENTATION_URL,
                    OPTION_ISSUE_TRACKER_URL
                }) {
            final Object value = additionalProperties.get(urlOption);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                hasProjectUrls = true;
            } else {
                additionalProperties.remove(urlOption);
            }
        }
        additionalProperties.put("hasProjectUrls", hasProjectUrls);

        additionalProperties.put(
                "pascalcase",
                (Mustache.Lambda)
                        (fragment, writer) ->
                                writer.write(
                                        goIdentifier(
                                                NamingConvention.PASCAL_CASE.apply(
                                                        fragment.execute()))));

        additionalProperties.put(
                "goIdentPart",
                (Mustache.Lambda)
                        (fragment, writer) ->
                                writer.write(fragment.execute().replaceAll("[^A-Za-z0-9]", "")));

        additionalProperties.put(
                "goStringLiteral",
                (Mustache.Lambda)
                        (fragment, writer) -> writer.write(goStringBody(fragment.execute())));

        additionalProperties.put(
                "goLineComment",
                (Mustache.Lambda)
                        (fragment, writer) ->
                                writer.write(fragment.execute().replaceAll("\r?\n", "\n// ")));

        additionalProperties.put(
                "goBlockComment",
                (Mustache.Lambda)
                        (fragment, writer) ->
                                writer.write(fragment.execute().replace("*/", "*\\/")));

        additionalProperties.put(
                "goVarName",
                (Mustache.Lambda)
                        (fragment, writer) -> {
                            String type = fragment.execute().trim();
                            String clean = type.replace("*", "");
                            int sliceDepth = 0;
                            while (clean.startsWith("[]")) {
                                clean = clean.substring(2);
                                sliceDepth++;
                            }
                            if (!clean.isEmpty()) {
                                clean =
                                        Character.toLowerCase(clean.charAt(0))
                                                + clean.substring(1);
                            }
                            for (int i = 0; i < sliceDepth; i++) {
                                clean += "Slice";
                            }
                            if (clean.isEmpty()) {
                                clean = "val";
                            }
                            writer.write(clean);
                        });

        additionalProperties.put(
                "goVariantName",
                (Mustache.Lambda)
                        (fragment, writer) -> writer.write(variantName(fragment.execute())));

        setModelPackage("");
        setApiPackage("");

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", "pkg", clientClassFile + ".go"));
        additionalProperties.put("credentialSchemes", credentialSchemes);
        supportingFiles.add(new SupportingFile("credentials.mustache", "pkg", "credentials.go"));

        if (generateExamples) {
            supportingFiles.add(
                    new SupportingFile(
                            "examples/quickstart.mustache", "examples/quickstart", "main.go"));
        }

        if (generateTests) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/testcontainers_helper_test.mustache",
                            "test",
                            "testcontainers_helper_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet_api_test.mustache", "test", "pet_api_test.go"));
            /* Golden only: it is written against the fixture's own operations. */
            supportingFiles.add(
                    new SupportingFile(
                            "test/paginator_test.mustache", "pkg", "paginator_test.go"));
            supportingFiles.add(
                    new SupportingFile("test/model_test.mustache", "test", "model_test.go"));
            supportingFiles.add(
                    new SupportingFile("test/readme_test.mustache", "test", "readme_test.go"));
            supportingFiles.add(
                    new SupportingFile("test/skills_test.mustache", "test", "skills_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store_api_test.mustache", "test", "store_api_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "test",
                            "default_api_client_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "pkg",
                            "object_serializer_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/iso8601_test.mustache",
                            "test",
                            "iso8601_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache",
                            "test",
                            "base_api_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache",
                            "test",
                            "metadata_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "test",
                            "composed_schema_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "test",
                            "client_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_error_test.mustache",
                            "test",
                            "api_error_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_configuration_test.mustache",
                            "test",
                            "server_configuration_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_variable_test.mustache",
                            "test",
                            "server_variable_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/otelhook_test.mustache", "test", "otelhook_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_result_test.mustache",
                            "test",
                            "api_result_test.go"));
        }

        if (emitUnitTests()) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "pkg",
                            "value_serializer_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "pkg",
                            "header_selector_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration_test.mustache",
                            "test",
                            "configuration_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "test",
                            "transport_options_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "pkg",
                            "trace_context_util_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/call_hook_test.mustache", "pkg", "call_hook_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/event_stream_test.mustache", "pkg", "event_stream_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "pkg",
                            "default_api_client_unit_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_unit_test.mustache",
                            "pkg",
                            "base_api_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/request_option_test.mustache",
                            "pkg",
                            "request_option_test.go"));
        }
    }

    /** {@inheritDoc} */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        return List.of(
                new SupportingFileSpec("readme.mustache", "", "README.md"),
                new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
                new SupportingFileSpec("doc.mustache", "pkg", "doc.go"),
                new SupportingFileSpec("configuration.mustache", "pkg", "configuration.go"),
                new SupportingFileSpec(
                        "transport_options.mustache", "pkg", "transport_options.go"),
                new SupportingFileSpec(
                        "server_configuration.mustache", "pkg", "server_configuration.go"),
                new SupportingFileSpec("servers.mustache", "pkg", "servers.go"),
                new SupportingFileSpec(
                        "errors/root_error.mustache", "pkg/errors", errorPrefixSnake() + "_error.go"),
                new SupportingFileSpec("api_error.mustache", "pkg/errors", "api_error.go"),
                new SupportingFileSpec(
                        "errors/client_error.mustache", "pkg/errors", "client_error.go"),
                new SupportingFileSpec(
                        "errors/server_error.mustache", "pkg/errors", "server_error.go"),
                new SupportingFileSpec(
                        "errors/bad_request_error.mustache",
                        "pkg/errors",
                        "bad_request_error.go"),
                new SupportingFileSpec(
                        "errors/unauthorized_error.mustache",
                        "pkg/errors",
                        "unauthorized_error.go"),
                new SupportingFileSpec(
                        "errors/forbidden_error.mustache", "pkg/errors", "forbidden_error.go"),
                new SupportingFileSpec(
                        "errors/not_found_error.mustache", "pkg/errors", "not_found_error.go"),
                new SupportingFileSpec(
                        "errors/conflict_error.mustache", "pkg/errors", "conflict_error.go"),
                new SupportingFileSpec(
                        "errors/payment_required_error.mustache", "pkg/errors", "payment_required_error.go"),
                new SupportingFileSpec(
                        "errors/method_not_allowed_error.mustache", "pkg/errors", "method_not_allowed_error.go"),
                new SupportingFileSpec(
                        "errors/request_timeout_error.mustache", "pkg/errors", "request_timeout_error.go"),
                new SupportingFileSpec(
                        "errors/gone_error.mustache", "pkg/errors", "gone_error.go"),
                new SupportingFileSpec(
                        "errors/payload_too_large_error.mustache", "pkg/errors", "payload_too_large_error.go"),
                new SupportingFileSpec(
                        "errors/unsupported_media_type_error.mustache", "pkg/errors", "unsupported_media_type_error.go"),
                new SupportingFileSpec(
                        "errors/locked_error.mustache", "pkg/errors", "locked_error.go"),
                new SupportingFileSpec(
                        "errors/not_implemented_error.mustache", "pkg/errors", "not_implemented_error.go"),
                new SupportingFileSpec(
                        "errors/bad_gateway_error.mustache", "pkg/errors", "bad_gateway_error.go"),
                new SupportingFileSpec(
                        "errors/service_unavailable_error.mustache", "pkg/errors", "service_unavailable_error.go"),
                new SupportingFileSpec(
                        "errors/gateway_timeout_error.mustache", "pkg/errors", "gateway_timeout_error.go"),
                new SupportingFileSpec(
                        "errors/unprocessable_entity_error.mustache",
                        "pkg/errors",
                        "unprocessable_entity_error.go"),
                new SupportingFileSpec(
                        "errors/too_many_requests_error.mustache",
                        "pkg/errors",
                        "too_many_requests_error.go"),
                new SupportingFileSpec(
                        "errors/internal_server_error.mustache",
                        "pkg/errors",
                        "internal_server_error.go"),
                new SupportingFileSpec(
                        "errors/network_error.mustache", "pkg/errors", "network_error.go"),
                new SupportingFileSpec(
                        "errors/network_timeout_error.mustache",
                        "pkg/errors",
                        "network_timeout_error.go"),
                new SupportingFileSpec(
                        "errors/stream_error.mustache", "pkg/errors", "stream_error.go"),
                new SupportingFileSpec(
                        "errors/response_too_large_error.mustache",
                        "pkg/errors",
                        "response_too_large_error.go"),
                new SupportingFileSpec(
                        "errors/pagination_error.mustache", "pkg/errors", "pagination_error.go"),
                new SupportingFileSpec(
                        "errors/serialization_error.mustache",
                        "pkg/errors",
                        "serialization_error.go"),
                new SupportingFileSpec(
                        "errors/oauth2_server_error.mustache",
                        "pkg/errors",
                        "oauth2_server_error.go"),
                new SupportingFileSpec(
                        "errors/oauth2_token_error.mustache",
                        "pkg/errors",
                        "oauth2_token_error.go"),
                new SupportingFileSpec("errors_exports.mustache", "pkg", "errors.go"),
                new SupportingFileSpec("header_selector.mustache", "pkg", "header_selector.go"),
                new SupportingFileSpec(
                        "object_serializer.mustache", "pkg", "object_serializer.go"),
                new SupportingFileSpec("iso8601.mustache", "pkg", "iso8601.go"),
                new SupportingFileSpec("value_serializer.mustache", "pkg", "value_serializer.go"),
                new SupportingFileSpec(
                        "trace_context_util.mustache", "pkg", "trace_context_util.go"),
                new SupportingFileSpec("call_hook.mustache", "pkg", "call_hook.go"),
                new SupportingFileSpec("otelhook.mustache", "pkg/otelhook", "otelhook.go"),
                new SupportingFileSpec("api_response.mustache", "pkg", "api_http_response.go"),
                new SupportingFileSpec("api_result.mustache", "pkg", "api_result.go"),
                new SupportingFileSpec(
                        "api_stream_response.mustache", "pkg", "api_stream_response.go"),
                new SupportingFileSpec("body_source.mustache", "pkg", "body_source.go"),
                new SupportingFileSpec("page.mustache", "pkg", "page.go"),
                new SupportingFileSpec("paginator.mustache", "pkg", "paginator.go"),
                new SupportingFileSpec("link_header.mustache", "pkg", "link_header.go"),
                new SupportingFileSpec("byte_stream.mustache", "pkg", "byte_stream.go"),
                new SupportingFileSpec("event_stream.mustache", "pkg", "event_stream.go"),
                new SupportingFileSpec(
                        "server_sent_event.mustache", "pkg", "server_sent_event.go"),
                new SupportingFileSpec(
                        "json_lines_stream.mustache", "pkg", "json_lines_stream.go"),
                new SupportingFileSpec("sse_stream.mustache", "pkg", "sse_stream.go"),
                new SupportingFileSpec("api_client.mustache", "pkg", "api_client.go"),
                new SupportingFileSpec(
                        "default_api_client.mustache", "pkg", "default_api_client.go"),
                new SupportingFileSpec("base_api.mustache", "pkg", "base_api.go"),
                new SupportingFileSpec("request_option.mustache", "pkg", "request_option.go"),
                new SupportingFileSpec("path_utils.mustache", "pkg", "path_utils.go"),
                new SupportingFileSpec("authenticator.mustache", "pkg", "authenticator.go"),
                new SupportingFileSpec("go_mod.mustache", "", "go.mod"),
                new SupportingFileSpec("makefile.mustache", "", "Makefile"),
                new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
                new SupportingFileSpec("gitignore.mustache", "", ".gitignore"),
                new SupportingFileSpec(
                        "dev_dependencies.mustache",
                        ".openapi-generator",
                        "DEV-DEPENDENCIES"),
                new SupportingFileSpec("golangci.mustache", "", ".golangci.yml"),
                new SupportingFileSpec("models/set.mustache", "pkg/models", "set.go"),
                new SupportingFileSpec(
                        "models/json_marshal.mustache", "pkg/models", "json_marshal.go"),
                new SupportingFileSpec(
                        "internal/protoduration.mustache",
                        "pkg/internal/protoduration",
                        "protoduration.go"),
                new SupportingFileSpec(
                        "internal/exactdecimal.mustache",
                        "pkg/internal/exactdecimal",
                        "exactdecimal.go"),
                new SupportingFileSpec(
                        "models/date_time.mustache", "pkg/models", "date_time.go"));
    }

    /**
     * Returns the output directory for model source files.
     * Models are placed in the {@code pkg/models/} sub-package.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(getOutputDir(), "pkg", "models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * APIs are placed in the {@code pkg/} directory alongside
     * infrastructure types (Configuration, BaseApi, ApiClient).
     */
    @Override
    public String apiFileFolder() {
        return Path.of(getOutputDir(), "pkg").toString();
    }

    /**
     * Returns the Go default value literal for the given
     * schema. Numeric and boolean defaults use their string
     * representation; string defaults are wrapped in double
     * quotes. All other types return null to omit the default.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (resolved != null && isDecimalFormat(resolved)) {
            if (resolved.getDefault() == null) {
                return null;
            }
            final Object value = resolved.getDefault();
            final String text =
                    value instanceof java.math.BigDecimal
                            ? ((java.math.BigDecimal) value).toPlainString()
                            : String.valueOf(value);
            return "decimal.RequireFromString(\"" + goStringBody(text) + "\")";
        }
        if (ModelUtils.isIntegerSchema(resolved)
                || ModelUtils.isNumberSchema(resolved)
                || ModelUtils.isBooleanSchema(resolved)) {
            if (resolved.getDefault() != null) {
                return resolved.getDefault().toString();
            }
        } else if (ModelUtils.isStringSchema(resolved)) {
            if (resolved.getDefault() != null) {
                return "\"" + goStringBody(String.valueOf(resolved.getDefault())) + "\"";
            }
        }
        return null;
    }

    /**
     * Rewrites the base-class reference default of a property that points at
     * a component enum ({@code Swatch.Green}) to the Go constant identifier
     * ({@code SwatchGreen}); inline enums keep the shared wire-literal rewrite.
     */
    @Override
    protected void fixEnumDefaultValue(CodegenProperty prop, CodegenModel model) {
        /* F866: a default written next to a $ref to a string enum is not carried by the base
         * class; the shared generator surfaces it as the sibling default, which becomes the
         * property's default here so the constructor and the decoder both apply it. */
        if ((prop.defaultValue == null || prop.defaultValue.isEmpty())
                && Boolean.TRUE.equals(prop.vendorExtensions.get("siblingDefaultIsEnum"))) {
            prop.defaultValue =
                    String.valueOf(prop.vendorExtensions.get("siblingDefaultEnumType"))
                            + prop.vendorExtensions.get("siblingDefaultEnumVar");
            return;
        }
        if (prop.defaultValue != null
                && !prop.isEnum
                && prop.defaultValue.matches("[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*")) {
            prop.defaultValue = prop.defaultValue.replace(".", "");
            return;
        }
        super.fixEnumDefaultValue(prop, model);
    }

    /**
     * Gives an enum value that sanitises to nothing (such as a single space)
     * the empty-value name, so its constant never equals the enum type name.
     */
    @Override
    public String toEnumVarName(String value, String datatype) {
        final String name = super.toEnumVarName(value, datatype);
        if (name == null || name.replace("_", "").isEmpty()) {
            return super.toEnumVarName("", datatype);
        }
        if (name.startsWith("NUMBER_")) {
            return pascalFromUpperSnake(name);
        }
        return name;
    }

    /**
     * Turns the shared integer-enum member name ({@code NUMBER_1},
     * {@code NUMBER_MINUS_1}) into the PascalCase Go uses for every other enum
     * member ({@code Number1}, {@code NumberMinus1}) (F308).
     *
     * @param name the upper snake case name
     * @return the PascalCase name
     */
    static String pascalFromUpperSnake(final String name) {
        final StringBuilder out = new StringBuilder();
        for (final String part : Splitter.on('_').split(name)) {
            if (part.isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /**
     * The name a oneOf or anyOf variant of the given Go type carries in the
     * typed constructor and accessors ({@code []Pet} becomes {@code PetSlice},
     * {@code map[string]int32} becomes {@code Int32Map}, {@code uuid.UUID}
     * becomes {@code UUID}) (F878).
     *
     * @param goType the Go type of the variant
     * @return a PascalCase identifier part
     */
    static String variantName(final String goType) {
        String clean = goType.trim().replace("*", "");
        final StringBuilder suffix = new StringBuilder();
        boolean more = true;
        while (more) {
            more = false;
            if (clean.startsWith("[]")) {
                clean = clean.substring(2);
                suffix.insert(0, "Slice");
                more = true;
            } else if (clean.startsWith("map[")) {
                final int close = clean.indexOf(']');
                clean = clean.substring(close + 1);
                suffix.insert(0, "Map");
                more = true;
            }
        }
        final int dot = clean.lastIndexOf('.');
        if (dot >= 0) {
            clean = clean.substring(dot + 1);
        }
        clean = clean.replaceAll("[^A-Za-z0-9_]", "");
        if (clean.isEmpty()) {
            clean = "Value";
        }
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1) + suffix;
    }

    /**
     * Keeps a schema named {@code Error} as {@code Error}. The shared
     * reserved-word guard turns {@code error} into {@code ModelError}, but the
     * Go predeclared identifier is lowercase, so the exported {@code Error}
     * type cannot shadow it. A schema literally named {@code ModelError} is
     * left as is.
     */
    @Override
    public String toModelName(String name) {
        final String modelName = super.toModelName(name);
        if ("ModelError".equals(modelName)
                && "Error".equals(NamingConvention.PASCAL_CASE.apply(sanitizeName(name)))) {
            return "Error";
        }
        return modelName;
    }

    /**
     * Maps a {@code type: number, format: decimal} schema to {@code decimal.Decimal},
     * the type the string form already gets through the {@code decimal} mapping.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String getSchemaType(Schema schema) {
        final String type = super.getSchemaType(schema);
        /* A number written as a decimal is the same exact decimal.Decimal as the string form. */
        if ("float64".equals(type) && schema != null && isDecimalFormat(schema)) {
            return "decimal.Decimal";
        }
        return type;
    }

    /**
     * Whether a schema is a {@code format: decimal} number or string, the
     * exact-decimal format that maps to {@code decimal.Decimal} (D135).
     *
     * @param schema the schema to check
     * @return true for {@code format: decimal}
     */
    @SuppressWarnings("rawtypes")
    private static boolean isDecimalFormat(final Schema schema) {
        return "decimal".equals(schema.getFormat())
                && ("number".equals(schema.getType())
                        || "string".equals(schema.getType())
                        || (schema.getTypes() != null
                                && (schema.getTypes().contains("number")
                                        || schema.getTypes().contains("string"))));
    }

    /**
     * Resolves a reference to a component schema through {@link #toModelName}
     * so a reference uses the same name its type is declared with. A model
     * keeps its own name (Error stays Error: it does not clash with the
     * predeclared lowercase error); toModelName only guards go keywords.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String getTypeDeclaration(Schema schema) {
        final String type = super.getTypeDeclaration(schema);
        if (schema != null && schema.get$ref() != null) {
            final String simple = ModelUtils.getSimpleRef(schema.get$ref());
            if (type.equals(simple)) {
                return toModelName(simple);
            }
        }
        return type;
    }

    /** {@inheritDoc} */
    @Override
    protected Map<String, String> getModelContextFlags() {
        return Map.ofEntries(
                Map.entry("type:uuid.UUID", "hasUuidImport"),
                Map.entry("type:netip.Addr", "hasNetipImport"),
                Map.entry("type:civil.Date", "hasCivilImport"),
                Map.entry("type:time.Duration", "hasDurationImport"),
                Map.entry("type:decimal.Decimal", "hasDecimalImport"),
                Map.entry("type:[]byte", "hasByteFields"),
                Map.entry("oneOfAnyOf", "hasFmtImport"),
                Map.entry("isEnum", "hasFmtImport"),
                Map.entry("hasInlineEnum", "hasFmtImport"),
                Map.entry("hasRequired", "hasFmtImport"),
                Map.entry("hasTypedAdditionalProperties", "hasFmtImport"),
                Map.entry("isUnevaluatedPropertiesFalse", "hasFmtImport"),
                Map.entry("hasOptionalDefault", "hasOptionalDefault"),
                Map.entry("hasPasswordProperty", "hasPasswordProperty"));
    }

    /** {@inheritDoc} */
    @Override
    protected Map<String, String> getOperationContextFlags() {
        return Map.of(
                "type:io.Reader", "hasIoImport",
                "type:uuid.UUID", "hasUuidImport",
                "type:netip.Addr", "hasNetipImport",
                "type:civil.Date", "hasCivilImport",
                "type:time.Duration", "hasTimeImport",
                "type:decimal.Decimal", "hasDecimalImport",
                "servers", "hasStringsImport",
                "cookieParams", "hasStringsImport");
    }

    /** {@inheritDoc} */
    @Override
    protected List<FileContentFixup> getFileContentFixups() {
        return List.of(new FileContentFixup(
                ".go",
                Pattern.compile(", \\)"),
                ")"));
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final Set<String> goPrimitives =
                Set.of(
                        "string", "bool", "int32", "int64", "float32", "float64",
                        "any", "byte", "[]byte");
        boolean hasModelImport = false;
        boolean hasIoImport = false;
        boolean hasUuidImport = false;
        boolean hasNetipImport = false;
        boolean hasCivilImport = false;
        boolean hasTimeImport = false;
        boolean hasDecimalImport = false;
        boolean hasIntEnum = false;
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", optionsFieldName(p.paramName));
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            param.put("requiredNullable", isRequiredNullableQuery(p));
            param.put("isReader", "io.Reader".equals(p.dataType));
            // A parameter declared `deprecated: true` in the spec must carry that
            // signal onto its Options field. The native CodegenParameter.deprecated
            // flag is threaded here so the template can emit the Go deprecation doc
            // comment, mirroring the operation (api.mustache) and model-property
            // (model.mustache) deprecation idioms.
            param.put("deprecated", p.isDeprecated);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            /* An inline enum parameter is a named type with a constant per allowed
             * value, like an inline enum on a model (F876). */
            final boolean inlineEnum =
                    p.isEnum
                            && !p.isContainer
                            && p._enum != null
                            && !p._enum.isEmpty()
                            && (p.isString || p.isInteger || p.isLong);
            if (inlineEnum) {
                final String optionsBase =
                        className.endsWith("Options")
                                ? className.substring(0, className.length() - "Options".length())
                                : className;
                final String enumType = optionsBase + optionsFieldName(p.paramName);
                final String enumBase = p.isInteger ? "int32" : p.isLong ? "int64" : "string";
                final List<Map<String, String>> enumValues = new ArrayList<>();
                final Set<String> usedNames = new HashSet<>();
                for (final String value : p._enum) {
                    final Map<String, Object> declared = declaredEnumVar(p.allowableValues, value);
                    final String memberName =
                            declared != null && Boolean.TRUE.equals(declared.get("nameSupplied"))
                                    ? String.valueOf(declared.get("name"))
                                    : toEnumVarName(value, enumBase);
                    String constName = enumType + memberName;
                    while (!usedNames.add(constName)) {
                        constName = constName + "2";
                    }
                    final Map<String, String> constant = new LinkedHashMap<>();
                    constant.put("constName", constName);
                    constant.put(
                            "literal",
                            "string".equals(enumBase) ? "\"" + goStringBody(value) + "\"" : value);
                    if (declared != null && declared.get("enumDescription") != null) {
                        constant.put("enumDescription", String.valueOf(declared.get("enumDescription")));
                    }
                    enumValues.add(constant);
                }
                param.put("enumTypeName", enumType);
                param.put("enumBase", enumBase);
                param.put("isStringEnum", "string".equals(enumBase));
                param.put("enumValues", enumValues);
                param.put("dataType", enumType);
                if (!"string".equals(enumBase)) {
                    hasIntEnum = true;
                }
            }
            params.add(param);
            // A date-time options param emits the generated
            // models.DateTime wrapper, which is reported as a
            // primitive (or sits inside a map) and would otherwise skip the model
            // import. Detected on the raw dataType, as a whole word so a user model
            // that merely ends in "DateTime" does not match.
            // Strip the []/* wrappers up front, before any of the checks below,
            // so this string modification never follows a validation of the same
            // value (avoids spotbugs MODIFICATION_AFTER_VALIDATION).
            final String dataType = p.dataType;
            final String baseType =
                    dataType == null ? "" : dataType.replace("[]", "").replace("*", "");
            if (dataType != null && WRAPPER_TYPE.matcher(dataType).find()) {
                hasModelImport = true;
            } else if (p.isFile || (dataType != null && dataType.contains("io.Reader"))) {
                hasIoImport = true;
            } else if (dataType != null && dataType.contains("uuid.UUID")) {
                hasUuidImport = true;
            } else if (dataType != null && dataType.contains("netip.Addr")) {
                hasNetipImport = true;
            } else if (dataType != null && dataType.contains("civil.Date")) {
                hasCivilImport = true;
            } else if (dataType != null && dataType.contains("time.Duration")) {
                hasTimeImport = true;
            } else if (dataType != null && dataType.contains("decimal.Decimal")) {
                hasDecimalImport = true;
            } else if (!p.isPrimitiveType
                    && dataType != null
                    && !goPrimitives.contains(baseType)
                    && !baseType.startsWith("map[")
                    && !baseType.equals("uuid.UUID")
                    && !baseType.equals("civil.Date")
                    && !baseType.equals("time.Duration")
                    && !baseType.equals("decimal.Decimal")) {
                hasModelImport = true;
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("packageName", "options");
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("hasIntEnum", hasIntEnum);
        context.put("moduleName", packageName);
        if (hasModelImport) {
            context.put("hasModelImport", true);
        }
        if (hasIoImport) {
            context.put("hasIoImport", true);
        }
        if (hasUuidImport) {
            context.put("hasUuidImport", true);
        }
        if (hasNetipImport) {
            context.put("hasNetipImport", true);
        }
        if (hasCivilImport) {
            context.put("hasCivilImport", true);
        }
        if (hasTimeImport) {
            context.put("hasTimeImport", true);
        }
        if (hasDecimalImport) {
            context.put("hasDecimalImport", true);
        }
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(getOutputDir(), "pkg", "options", fileName + ".go").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return "pkg/auth";
    }

    /** {@inheritDoc} */
    @Override
    protected String getOAuthDir() {
        return "pkg/auth/oauth";
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return stem + ".go";
    }

    /**
     * Registers the Basic, Bearer and API-key authenticator tests. They must be
     * added here rather than in processOpts: the security-scheme flags are only
     * set once the spec has been read, so a check in processOpts always saw
     * them false and the tests were never generated.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();
        if (!generateTests) {
            return;
        }
        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/basic_authenticator_test.mustache",
                            "test",
                            "basic_authenticator_test.go"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/bearer_authenticator_test.mustache",
                            "test",
                            "bearer_authenticator_test.go"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_key_authenticator_test.mustache",
                            "test",
                            "api_key_authenticator_test.go"));
        }
        if (Boolean.TRUE.equals(additionalProperties.get("hasSecuritySchemeEnvVars"))) {
            additionalProperties.put("hasSchemeBasic", hasBasicAuth);
            additionalProperties.put("hasSchemeBearer", hasBearerAuth);
            additionalProperties.put("hasSchemeApiKey", hasApiKeyAuth);
            additionalProperties.put("hasSchemeOAuth", hasAnyOAuth2 || hasOpenIdConnect);
            supportingFiles.add(
                    new SupportingFile(
                            "test/scheme_authenticator_test.mustache",
                            "test",
                            "scheme_authenticator_test.go"));
        }
    }

    /**
     * The OAuth2 and OpenID Connect tests, each gated on the scheme it
     * exercises. Like the Basic, Bearer and API-key tests they are registered
     * once the spec has been read, never from processOpts.
     */
    @Override
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        return List.of(
                new OAuthTestFileSpec(
                        "test/oauth2_token_manager_test.mustache",
                        "test",
                        "oauth2_token_manager_test.go",
                        OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec(
                        "test/oauth2_auth_code_authenticator_test.mustache",
                        "test",
                        "oauth2_authorization_code_authenticator_test.go",
                        OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec(
                        "test/oauth2_pkce_unit_test.mustache",
                        "pkg/auth/oauth",
                        "oauth2_pkce_test.go",
                        OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec(
                        "test/oauth2_implicit_authenticator_test.mustache",
                        "test",
                        "oauth2_implicit_authenticator_test.go",
                        OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec(
                        "test/oauth2_client_credentials_authenticator_test.mustache",
                        "test",
                        "oauth2_client_credentials_authenticator_test.go",
                        OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec(
                        "test/oauth2_password_authenticator_test.mustache",
                        "test",
                        "oauth2_password_authenticator_test.go",
                        OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec(
                        "test/openid_connect_authenticator_test.mustache",
                        "test",
                        "openid_connect_authenticator_test.go",
                        OAuthTestCondition.OIDC));
    }

    /**
     * Renders the per-scheme authenticator. Every constructor takes the credentials as strings:
     * an empty one is read once, in the constructor, from the scheme's environment variable
     * (F862), and a credential that is neither passed nor set returns an error wrapping
     * {@code ErrMissingCredential}, so every constructor returns {@code (T, error)}. The
     * variable names come from the shared {@code envVars} entries of {@link #baseSchemeContext},
     * so all twelve languages read the same names. A Bearer scheme also gets a
     * {@code ...WithProvider} constructor that asks a token callback for every request.
     */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> envVars =
                (List<Map<String, Object>>) Objects.requireNonNull(ctx.get("envVars"));
        final Map<String, String> envNames = new HashMap<>();
        for (final Map<String, Object> envVar : envVars) {
            envNames.put(String.valueOf(envVar.get("field")), String.valueOf(envVar.get("envName")));
        }
        final boolean isBearer = "BearerAuthenticator".equals(spec.baseClass());
        final String resolver = spec.isOAuth() ? "auth.ResolveCredential" : "ResolveCredential";
        ctx.put("package", spec.isOAuth() ? "oauth" : "auth");
        final List<String> imports = new ArrayList<>();
        if (spec.isOAuth()) {
            imports.add("\"" + packageName + "/pkg/auth\"");
        } else if (isBearer) {
            imports.add("\"fmt\"");
        }
        ctx.put("imports", imports);
        ctx.put("credentialResolver", resolver);
        ctx.put("isBearer", isBearer);
        final List<Map<String, Object>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", name);
            param.put("type", "string");
            param.put("envName", envNames.get(name));
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", buildGoSuperArgs(spec));
        // Basic, Bearer and API-key base constructors validate their credentials and
        // return (T, error); the scheme wrapper forwards that error.
        ctx.put("fallible", Set.of("BasicAuthenticator", "BearerAuthenticator", "ApiKeyAuthenticator")
                .contains(spec.baseClass()));
        recordSchemeAuthTest(spec, envVars, isBearer);
        final Map<String, Object> credentialScheme = new HashMap<>();
        credentialScheme.put("schemeId", spec.schemeName());
        credentialScheme.put("fieldName", goIdentifier(NamingConvention.PASCAL_CASE.apply(spec.schemeName())));
        credentialSchemes.add(credentialScheme);
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    /**
     * Records what {@code scheme_authenticator_test} needs to exercise one per-scheme
     * authenticator: its class and package, its base kind and its environment variables.
     */
    private void recordSchemeAuthTest(
            final SchemeAuthSpec spec,
            final List<Map<String, Object>> envVars,
            final boolean isBearer) {
        final Map<String, Object> entry = new HashMap<>();
        entry.put("className", spec.schemeClass() + spec.oauthSuffix() + "Authenticator");
        entry.put("failsNamingId", nullIfEmpty(FAILS_NAMING_IDS.getOrDefault((String) entry.get("className"), "")));
        entry.put("package", spec.isOAuth() ? "oauth" : "auth");
        final boolean isBasic = "BasicAuthenticator".equals(spec.baseClass());
        entry.put("isBasic", isBasic);
        /* The basic-credential behaviours carry one behaviour id each, so only the
         * first Basic scheme names its tests with them (one test per id). */
        final boolean firstBasic = isBasic && schemeAuthTests.stream().noneMatch(e -> Boolean.TRUE.equals(e.get("isBasic")));
        entry.put("firstBasic", firstBasic);
        /* The api-key behaviour ids name the first API-key scheme only, so one test name carries one id. */
        entry.put(
                "firstApiKey",
                "ApiKeyAuthenticator".equals(spec.baseClass())
                        && schemeAuthTests.stream().noneMatch(e -> Boolean.TRUE.equals(e.get("firstApiKey"))));
        /* authscheme001 names the first scheme that does not carry the basic id, so one
         * test name never carries two behaviour ids. */
        entry.put(
                "firstEnvScheme",
                !firstBasic && schemeAuthTests.stream().noneMatch(e -> Boolean.TRUE.equals(e.get("firstEnvScheme"))));
        /* The explicit-credential and reads-once behaviours carry one id each, so only the
         * first Bearer and the first API-key scheme name their tests with those ids. */
        final boolean firstBearer = isBearer && schemeAuthTests.stream().noneMatch(e -> Boolean.TRUE.equals(e.get("firstBearer")));
        entry.put("firstBearer", firstBearer);
        final boolean isApiKey = "ApiKeyAuthenticator".equals(spec.baseClass());
        final boolean firstApiKey = isApiKey && schemeAuthTests.stream().noneMatch(e -> Boolean.TRUE.equals(e.get("firstApiKey")));
        entry.put("firstApiKey", firstApiKey);
        entry.put("isBearer", isBearer);
        entry.put("isApiKey", isApiKey);
        entry.put("isOAuth", spec.isOAuth());
        final List<Map<String, Object>> vars = new ArrayList<>();
        for (final Map<String, Object> envVar : envVars) {
            final Map<String, Object> var = new HashMap<>();
            var.put("field", envVar.get("field"));
            var.put("envName", envVar.get("envName"));
            vars.add(var);
        }
        entry.put("envVars", vars);
        /* The environment-variable rejection carries one behaviour id per scheme kind: the four
         * OAuth2 flows, the second bearer and the second API-key scheme. */
        final long bearersBefore = schemeAuthTests.stream().filter(e -> Boolean.TRUE.equals(e.get("isBearer"))).count();
        final long apiKeysBefore = schemeAuthTests.stream().filter(e -> Boolean.TRUE.equals(e.get("isApiKey"))).count();
        entry.put(
                "failsId",
                nullIfEmpty(failsNamingId(spec.baseClass(), isBearer && bearersBefore == 1, isApiKey && apiKeysBefore == 1)));
        schemeAuthTests.add(entry);
    }

    /** Null for an absent id, so the template's section tests a real id and not an empty string. */
    private static @Nullable String nullIfEmpty(final String id) {
        return id.isEmpty() ? null : id;
    }

    /** The behaviour id that names a scheme's FailsNamingAVariableThatIsEmptyOrUnset test, or empty. */
    private static String failsNamingId(final String baseClass, final boolean secondBearer, final boolean secondApiKey) {
        switch (baseClass) {
            case "OAuth2ClientCredentialsAuthenticator":
                return "BhvAuthscheme013_";
            case "OAuth2PasswordAuthenticator":
                return "BhvAuthscheme014_";
            case "OAuth2AuthorizationCodeAuthenticator":
                return "BhvAuthscheme015_";
            case "OAuth2ImplicitAuthenticator":
                return "BhvAuthscheme016_";
            default:
                break;
        }
        if (secondBearer) {
            return "BhvAuthscheme011_";
        }
        if (secondApiKey) {
            return "BhvAuthscheme012_";
        }
        return "";
    }

    /**
     * F857: the generic parser lists an operation's security schemes in the order the
     * components declare them, not the order the operation requires them. The client
     * uses the first accepted scheme in spec order that has a credential, so the schemes
     * are put back in the order of the operation's own security requirements (the global
     * ones when it declares none).
     */
    @Override
    public CodegenOperation fromOperation(
            final String path,
            final String httpMethod,
            final io.swagger.v3.oas.models.Operation operation,
            final List<io.swagger.v3.oas.models.servers.Server> servers) {
        final CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);
        final List<io.swagger.v3.oas.models.security.SecurityRequirement> requirements =
                operation.getSecurity() != null ? operation.getSecurity() : openAPI.getSecurity();
        if (requirements == null) {
            return op;
        }
        final List<String> order = new ArrayList<>();
        for (final io.swagger.v3.oas.models.security.SecurityRequirement requirement : requirements) {
            for (final String name : requirement.keySet()) {
                if (!order.contains(name)) {
                    order.add(name);
                }
            }
        }
        op.vendorExtensions.put("x-security-order", order);
        return op;
    }

    /** Puts the operation's auth methods in the order recorded by {@link #fromOperation}. */
    @SuppressWarnings("unchecked")
    private static void sortAuthMethods(final CodegenOperation op) {
        final Object recorded = op.vendorExtensions.get("x-security-order");
        if (op.authMethods == null || !(recorded instanceof List)) {
            return;
        }
        final List<String> order = (List<String>) recorded;
        final List<org.openapitools.codegen.CodegenSecurity> sorted = new ArrayList<>(op.authMethods);
        sorted.sort(java.util.Comparator.comparingInt(m -> {
            final int index = order.indexOf(m.name);
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        op.authMethods = sorted;
    }

    /**
     * F827: Go does not mint an Options struct for an operation that has no parameter and no
     * security. Every operation, such an operation included, already ends in a variadic
     * {@code ...RequestOption} (WithTimeout, WithHeaders, WithMaxRetries), which is where Go
     * carries the optional per-call settings; an empty struct in front of it would add a
     * parameter and no capability.
     */
    @Override
    protected boolean mintsOptionsForEveryOperation() {
        return false;
    }

    /**
     * Renders the scopes a scheme declares as a Go {@code []string} literal, or
     * {@code nil} when it declares none, so each per-scheme wrapper requests the
     * spec's scopes instead of the provider's defaults.
     */
    private static String formatGoScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "nil";
        }
        final List<String> quoted = new ArrayList<>();
        for (final String scope : scopes.keySet()) {
            quoted.add("\"" + scope.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return "[]string{" + String.join(", ", quoted) + "}";
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "keyIn values are ASCII-only OpenAPI location strings (header/query/cookie)"
                    + " — toLowerCase(Locale.ROOT) is intentional and safe here")
    private List<String> buildGoSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + goStringBody(java.util.Objects.toString(spec.keyParamName(), "")) + "\"", "apiKey",
                    "ApiKeyLocation" + NamingConvention.PASCAL_CASE.apply(spec.keyIn() != null ? spec.keyIn().toLowerCase(Locale.ROOT) : "header"));
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"", formatGoScopes(spec.scopes()));
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "\"\"";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"",
                    "username", "password", formatGoScopes(spec.scopes()), refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "\"\"";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.authorizationUrl() + "\"", "\"" + spec.tokenUrl() + "\"",
                    "redirectUri", formatGoScopes(spec.scopes()), refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId",
                    "\"" + spec.authorizationUrl() + "\"", formatGoScopes(spec.scopes()));
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.openIdConnectUrl() + "\"",
                    "clientId", "clientSecret", "redirectUri", "nil");
        }
        return List.of();
    }

    /**
     * A classic allOf base is realised as a union of its subtypes: Go structs
     * cannot be subclassed, and a flat base struct could not hold the subtype
     * a discriminator names. Once the shared pass has marked the base, its
     * mapped subtypes become its {@code oneOf} set, so the union template
     * emits the wrapper, the per-subtype accessors and the discriminator
     * dispatch that rejects an unknown value (S4, issue #104).
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(final Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();
                markDurationFields(modelsMap, model);
                markDecimalFields(modelsMap, model);
                if (Boolean.TRUE.equals(model.vendorExtensions.get("x-allof-base"))) {
                    final Set<String> variants = new LinkedHashSet<>();
                    for (final CodegenDiscriminator.MappedModel mapped :
                            model.discriminator.getMappedModels()) {
                        variants.add(mapped.getModelName());
                    }
                    model.oneOf = variants;
                }
                markDecodeFields(modelsMap, modelMap, model);
            }
        }
        return result;
    }

    /**
     * Prepares the one-pass decode of a struct model (#107). Each model reads
     * its JSON object once through {@code UnmarshalJSONFrom} on a JSON v2
     * {@code jsontext.Decoder}, so the template needs to know per property what
     * to do while reading and after the object ends. Logic-less Mustache cannot
     * combine the required, nullable and default flags, so they are combined
     * here:
     *
     * <ul>
     *   <li>{@code decodeRejectsNull}: required and not nullable, so a null fails
     *   <li>{@code decodeRequiresKey}: required with no default, so a missing key fails
     *   <li>{@code decodeRequiredDefault}: required, not nullable, with a default
     *       that fills a missing key
     *   <li>{@code decodeOptionalDefault}: optional with a default that fills a
     *       missing key; only on a model that neither keeps nor rejects
     *       undeclared keys, as before
     *   <li>{@code decodeTracksPresence}: any of the three above, so the decode
     *       records whether the key was read; the model is marked {@code
     *       decodeAppliesOptionalDefaults} when one property has an optional default
     *   <li>{@code hasDecodeCheck} and {@code decodeCheck}: the check that sees
     *       the value's JSON text before it is decoded (absolute URI, IPv4,
     *       IPv6, canonical UUID, decimal shape)
     * </ul>
     *
     * <p>The file then imports {@code jsontext}, flagged as {@code
     * hasJsontextImport}. Enums and unions decode through {@code UnmarshalJSON}
     * and are left unmarked.
     *
     * @param modelsMap the file context of the model
     * @param modelMap the model context, which carries the unevaluatedProperties flag
     * @param model the model to mark
     */
    private static void markDecodeFields(
            final ModelsMap modelsMap, final ModelMap modelMap, final CodegenModel model) {
        if (model.isEnum
                || (model.oneOf != null && !model.oneOf.isEmpty())
                || (model.anyOf != null && !model.anyOf.isEmpty())) {
            return;
        }
        final boolean keepsOrRejectsExtras =
                model.isAdditionalPropertiesTrue
                        || Boolean.TRUE.equals(modelMap.get("isUnevaluatedPropertiesFalse"));
        boolean appliesOptionalDefaults = false;
        for (final CodegenProperty property : model.vars) {
            final boolean hasDefault =
                    property.defaultValue != null && !property.defaultValue.isEmpty();
            final boolean rejectsNull = property.required && !property.isNullable;
            final boolean requiresKey = property.required && !hasDefault;
            final boolean requiredDefault = rejectsNull && hasDefault;
            final boolean optionalDefault =
                    !property.required && hasDefault && !keepsOrRejectsExtras;
            property.vendorExtensions.put("decodeRejectsNull", rejectsNull);
            property.vendorExtensions.put("decodeRequiresKey", requiresKey);
            property.vendorExtensions.put("decodeRequiredDefault", requiredDefault);
            property.vendorExtensions.put("decodeOptionalDefault", optionalDefault);
            appliesOptionalDefaults |= optionalDefault;
            property.vendorExtensions.put(
                    "decodeTracksPresence", requiresKey || requiredDefault || optionalDefault);
            final String check = decodeCheck(property);
            property.vendorExtensions.put("hasDecodeCheck", !check.isEmpty());
            property.vendorExtensions.put("decodeCheck", check);
        }
        model.vendorExtensions.put("decodeAppliesOptionalDefaults", appliesOptionalDefaults);
        modelsMap.put("hasJsontextImport", true);
    }

    /**
     * The models-package function that checks a property's JSON text before it
     * is decoded, or the empty string when its type needs no check beyond the
     * decode.
     *
     * @param property the property
     * @return the check function name, or the empty string
     */
    private static String decodeCheck(final CodegenProperty property) {
        if (Boolean.TRUE.equals(property.vendorExtensions.get("isDecimalField"))) {
            return Boolean.TRUE.equals(property.vendorExtensions.get("isDecimalString"))
                    ? "checkDecimalString"
                    : "checkDecimalNumber";
        }
        if (property.isUri) {
            return "checkAbsoluteURI";
        }
        if (Boolean.TRUE.equals(property.vendorExtensions.get("isIpv4"))) {
            return "checkIPv4";
        }
        if (Boolean.TRUE.equals(property.vendorExtensions.get("isIpv6"))) {
            return "checkIPv6";
        }
        if (property.isUuid) {
            return "checkUuid";
        }
        return "";
    }

    /**
     * Marks each property whose Go type holds a {@code time.Duration} (alone or in
     * a list, map or set) with {@code isDurationField}, and the model with
     * {@code hasDurationFields} and, for a typed additional-properties bucket of
     * durations, {@code hasDurationAdditionalProperties}. encoding/json writes a
     * {@code time.Duration} as nanoseconds, so the model template rewrites these
     * keys to and from the protobuf-JSON string (D145). The file then imports the
     * internal codec, flagged as {@code hasProtoDurationImport}. Enums and unions
     * have no such fields of their own and are left unmarked.
     *
     * @param modelsMap the file context of the model
     * @param model the model to mark
     */
    private static void markDurationFields(final ModelsMap modelsMap, final CodegenModel model) {
        if (model.isEnum
                || (model.oneOf != null && !model.oneOf.isEmpty())
                || (model.anyOf != null && !model.anyOf.isEmpty())) {
            return;
        }
        boolean any = false;
        for (final CodegenProperty property : model.vars) {
            final boolean duration =
                    property.dataType != null && property.dataType.contains("time.Duration");
            property.vendorExtensions.put("isDurationField", duration);
            any |= duration;
        }
        if (any) {
            model.vendorExtensions.put("hasDurationFields", true);
            modelsMap.put("hasProtoDurationImport", true);
        }
        final Object bucket = model.vendorExtensions.get("additionalPropertiesValue");
        if (model.isAdditionalPropertiesTrue
                && bucket instanceof CodegenProperty
                && ((CodegenProperty) bucket).dataType != null
                && ((CodegenProperty) bucket).dataType.contains("time.Duration")) {
            model.vendorExtensions.put("hasDurationAdditionalProperties", true);
            modelsMap.put("hasProtoDurationImport", true);
        }
    }

    /**
     * Marks each property whose Go type holds a {@code decimal.Decimal} (alone or
     * in a list or map) with {@code isDecimalField}, a string-shaped one also with
     * {@code isDecimalString}, and the model with {@code hasDecimalFields}.
     * shopspring writes a decimal as a quoted string without its trailing zeros
     * and reads one from a number or a string alike, so the model template checks
     * the JSON shape of these keys on decode and writes their exact text on
     * encode (D135). The file then imports the internal codec, flagged as
     * {@code hasExactDecimalImport}. Enums and unions are left unmarked.
     *
     * @param modelsMap the file context of the model
     * @param model the model to mark
     */
    private static void markDecimalFields(final ModelsMap modelsMap, final CodegenModel model) {
        if (model.isEnum
                || (model.oneOf != null && !model.oneOf.isEmpty())
                || (model.anyOf != null && !model.anyOf.isEmpty())) {
            return;
        }
        boolean any = false;
        for (final CodegenProperty property : model.vars) {
            final boolean decimal =
                    property.dataType != null && property.dataType.contains("decimal.Decimal");
            property.vendorExtensions.put("isDecimalField", decimal);
            property.vendorExtensions.put(
                    "isDecimalString", decimal && isStringShaped(property));
            any |= decimal;
        }
        if (any) {
            model.vendorExtensions.put("hasDecimalFields", true);
            modelsMap.put("hasExactDecimalImport", true);
        }
        if (any || Boolean.TRUE.equals(model.vendorExtensions.get("hasDurationFields"))) {
            model.vendorExtensions.put("hasRewrittenFields", true);
        }
    }

    /**
     * Whether a decimal property travels as a JSON string ({@code type: string,
     * format: decimal}), looking through a list or map to its items.
     *
     * @param property the decimal property
     * @return true when its scalar values are JSON strings
     */
    private static boolean isStringShaped(final CodegenProperty property) {
        CodegenProperty scalar = property;
        while (scalar.items != null) {
            scalar = scalar.items;
        }
        return "string".equals(scalar.openApiType);
    }

    /**
     * Enables Gap K so polymorphic subtypes auto-emit their
     * discriminator field on serialization. The Go model template
     * honours {@code defaultValue} on optional vars in the New*
     * constructor, so the discriminator initialises to e.g.
     * {@code "dry"} without the caller supplying it.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /**
     * Demotes the discriminator out of {@code requiredVars} so the
     * generated {@code New*} constructor signature drops the
     * discriminator parameter (e.g. {@code NewDryFood(weightKg float64)}
     * instead of {@code NewDryFood(foodType string, weightKg float64)}).
     * The Go struct field stays a non-pointer {@code string} because
     * the property's {@code required} flag remains {@code true}, and
     * the constructor body assigns the default literal via the
     * optionalVars branch of {@code model.mustache}.
     */
    @Override
    protected boolean demotesDiscriminatorFromRequiredVars() {
        return true;
    }

    /**
     * Opts into the shared cycle-detection pass so a model property that
     * participates in a reference cycle (a direct self-reference, or mutual
     * recursion) is heap-indirected. Without this, a <em>required</em>
     * recursive complex field emits a plain value-type field
     * ({@code type TreeNode struct { Child TreeNode }}), which the Go compiler
     * rejects as an {@code invalid recursive type}.
     *
     * <p>Optional fields already emit {@code *T} (the model template wraps every
     * non-required field in a pointer), so only required fields need the extra
     * indirection — see {@link #markRecursiveProperty(CodegenProperty)}.
     */
    @Override
    protected boolean indirectsRecursiveProperties() {
        return true;
    }

    /**
     * Pointer-indirects a <em>required</em> recursive model property so the
     * struct has a known, finite size. A pointer is transparent to
     * {@code encoding/json}, so (de)serialization is unaffected.
     *
     * <p>Optional recursive fields are skipped: the model template already
     * renders every optional field as {@code *T}, so marking them would yield a
     * non-compiling double pointer {@code **T}. Idempotent — a property already
     * pointer-typed (it appears across several var lists) is left untouched.
     */
    @Override
    protected void markRecursiveProperty(CodegenProperty property) {
        if (property.required
                && !property.isNullable
                && property.dataType != null
                && !property.dataType.startsWith("*")) {
            property.dataType = "*" + property.dataType;
        }
    }

    /**
     * Rewrites binary ({@code format: binary}) response bodies from
     * {@code io.Reader} to the idiomatic {@code []byte}.
     *
     * <p>OpenAPI's {@code DefaultCodegen} resolves a
     * {@code type: string, format: binary} schema to the {@code file}
     * type, which {@link #typeMapping} maps to {@code io.Reader}. For a
     * response body the codegen then sets {@code op.returnType} to that
     * pointer type and the api template wraps the convenience variant in
     * a further pointer ({@code *T}), yielding a non-idiomatic
     * {@code *io.Reader} double pointer (and {@code ApiResult[io.Reader]}
     * for the WithHTTPInfo variant).
     *
     * <p>The transport layer carries the response body as a base64
     * string in {@code ApiHttpResponse.Body} and the api template's
     * deserialization path already decodes a {@code *[]byte} return type
     * via {@code decodeBinaryResponse}, so {@code []byte} is the single
     * type the transport can cleanly produce. Rewriting the return type
     * here (rather than globally in {@code getSchemaType}) leaves binary
     * <em>request</em> bodies and multipart file uploads as {@code io.Reader},
     * which is the idiomatic Go type for streaming a file from disk.
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
                    if ("io.Reader".equals(op.returnBaseType)
                            || (op.returnType != null && op.returnType.contains("io.Reader"))) {
                        op.returnType = "[]byte";
                        op.returnBaseType = "byte";
                        op.returnContainer = "array";
                        op.isArray = true;
                    }
                    // Query/header/form/cookie params live on the per-operation
                    // Options object passed to the generated method. A required one
                    // therefore implies the caller must pass a non-nil options, and
                    // the api template guards that with `if options == nil { ... }`.
                    //
                    // That guard must be emitted exactly once per operation. Emitting
                    // it inside the per-param `{{#required}}` loops (as the template
                    // previously did) produced byte-identical, unreachable duplicate
                    // guards whenever an operation had two or more required non-string
                    // params of the same kind — the second guard could never run
                    // because the first already returned. Mustache cannot express
                    // "emit once across these four sublists" without a positional or
                    // boolean signal, so derive that boolean here. It is true when the
                    // operation has any required param that is carried on the Options
                    // object (path and body params are passed as plain arguments and
                    // are validated separately, so they are deliberately excluded).
                    /* A slice, map, string or byte slice is returned by value (F877):
                     * its zero value already means "nothing", so a pointer to it adds
                     * nothing but a second nil to check. */
                    sortAuthMethods(op);
                    op.vendorExtensions.put("returnByValue", returnsByValue(op.returnType));
                    stampOptionsFieldNames(op);
                    final boolean hasRequiredOptionsParam =
                            anyRequiredOptionsParam(op);
                    op.vendorExtensions.put(
                            "hasRequiredOptionsParam", hasRequiredOptionsParam);

                    // An operation may declare more than one request Content-Type
                    // (e.g. setPetAvatar declares image/jpeg, image/png and
                    // application/json). The generated method historically pinned
                    // the header to effectiveConsumes (the first declared type),
                    // leaving the other declared types unreachable.
                    //
                    // When more than one type is declared we expose an OPTIONAL
                    // request-content-type selector on the generated method (a
                    // trailing variadic `requestContentType ...string` argument).
                    // Mustache cannot test "consumes has more than one element", so
                    // derive that boolean here, mirroring the hasRequiredOptionsParam
                    // pattern above. Existing call sites omit the variadic argument
                    // and keep sending the first declared type unchanged.
                    final boolean hasMultipleConsumes =
                            op.consumes != null && op.consumes.size() > 1;
                    op.vendorExtensions.put(
                            "hasMultipleConsumes", hasMultipleConsumes);
                    stampContentParams(op);
                    markBinaryUpload(op);
                }
            }
        }
        final OperationsMap processed = super.postProcessOperationsWithModels(objs, allModels);
        reorderServerBeforeOptions(processed);
        markBinaryStreams(processed);
        markPagination(processed);
        writeResponseHeadersFiles(processed);
        return processed;
    }

    /**
     * D168: writes one {@code pkg/options/<op>_response_headers.go} per operation whose success
     * responses declare headers, next to that operation's Options struct. Each row of the shared
     * {@code responseHeaders} decorator gets its Go item type ({@code int32}, {@code int64},
     * {@code float32} or {@code float64}, {@code bool}, else {@code string}: a header of any other
     * schema is read as a string), the unexported field and exported getter names, and the
     * lower-cased name it is looked up by.
     *
     * @param objs the processed operations map
     */
    @SuppressWarnings("unchecked")
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "HTTP header and identifier names are ASCII")
    private void writeResponseHeadersFiles(final OperationsMap objs) {
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations == null) {
            return;
        }
        final List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
        if (ops == null) {
            return;
        }
        for (final CodegenOperation op : ops) {
            final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
            if (!(decorators instanceof Map)) {
                continue;
            }
            final Map<String, Object> d = (Map<String, Object>) decorators;
            if (!Boolean.TRUE.equals(d.get("hasResponseHeaders"))) {
                continue;
            }
            final String className = String.valueOf(d.get("responseHeadersClassName"));
            final List<Map<String, Object>> rows = new ArrayList<>();
            boolean hasParsed = false;
            boolean hasNumeric = false;
            boolean hasArray = false;
            final Object declared = d.get("responseHeaders");
            if (!(declared instanceof List)) {
                continue;
            }
            for (final Map<String, Object> source : (List<Map<String, Object>>) declared) {
                final Map<String, Object> row = new HashMap<>(source);
                final String kind = String.valueOf(source.get("kind"));
                final String itemType;
                if ("integer".equals(kind)) {
                    itemType = "int32";
                } else if ("long".equals(kind)) {
                    itemType = "int64";
                } else if ("number".equals(kind)) {
                    itemType = "float32".equals(source.get("itemDataType")) ? "float32" : "float64";
                } else if ("boolean".equals(kind)) {
                    itemType = "bool";
                } else {
                    itemType = "string";
                }
                final boolean isArray = Boolean.TRUE.equals(source.get("isArray"));
                final String getter = String.valueOf(source.get("varName"));
                String field = getter.isEmpty()
                        ? "value"
                        : getter.substring(0, 1).toLowerCase(Locale.ROOT) + getter.substring(1);
                if (reservedWords.contains(field.toLowerCase(Locale.ROOT))) {
                    field = field + "Value";
                }
                row.put("goItemType", itemType);
                row.put("goFieldType", isArray ? "[]" + itemType : "*" + itemType);
                row.put("goGetterName", getter);
                row.put("goFieldName", field);
                row.put("goLookupName", String.valueOf(source.get("wireName")).toLowerCase(Locale.ROOT));
                row.put("goBitSize", "float32".equals(itemType) || "int32".equals(itemType) ? "32" : "64");
                row.put("goItemIsString", "string".equals(itemType));
                hasParsed |= !"string".equals(itemType);
                hasNumeric |= !"string".equals(itemType) && !"bool".equals(itemType);
                hasArray |= isArray;
                rows.add(row);
            }
            final Map<String, Object> context = new HashMap<>();
            context.put("className", className);
            context.put("packageName", "options");
            context.put("operationId", op.operationId);
            context.put("rows", rows);
            context.put("hasParsedHeaders", hasParsed);
            context.put("hasNumericHeaders", hasNumeric);
            context.put("hasArrayHeaders", hasArray);
            context.put("moduleName", packageName);
            final String filePath = Path.of(
                    getOutputDir(), "pkg", "options",
                    NamingConvention.SNAKE_CASE.apply(className) + ".go").toString();
            writeFile(filePath, renderOptionsTemplate("api/response_headers.mustache", context));
            postProcessFile(Path.of(filePath).toFile(), "source");
        }
    }

    /**
     * Gives a paged operation the Go names the templates need: the paginator type, the cursor type
     * ({@code NoCursor} for a list whose next page is a URL, a {@code Link} header or a body property) and the field of the
     * options struct that holds the cursor. The shared detector has already decided which operations
     * are paged.
     *
     * @param objs the processed operations map
     */
    @SuppressWarnings("unchecked")
    private static void markPagination(final OperationsMap objs) {
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations == null) {
            return;
        }
        final List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
        if (ops == null) {
            return;
        }
        for (final CodegenOperation op : ops) {
            markPaged(op);
        }
    }

    /**
     * Stamps the Go names of one paged operation.
     *
     * @param op the operation
     */
    @SuppressWarnings("unchecked")
    static void markPaged(final CodegenOperation op) {
        final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
        if (!(decorators instanceof Map)) {
            return;
        }
        final Object pagination = ((Map<String, Object>) decorators).get("pagination");
        if (!(pagination instanceof Map)) {
            return;
        }
        final Map<String, Object> p = (Map<String, Object>) pagination;
        final boolean urlPaged = Boolean.TRUE.equals(p.get("isUrlPaged"));
        final String cursorType = urlPaged ? "NoCursor" : String.valueOf(p.get("cursorType"));
        p.put("goCursorType", cursorType);
        p.put("goPaginatorType", "*Paginator[" + p.get("itemType") + ", " + cursorType + "]");
        if (!urlPaged) {
            p.put("goCursorField", optionsFieldName(String.valueOf(p.get("requestParamName"))));
        }
    }

    /**
     * A raw binary request body is a {@code *BodySource}: the operation takes it, in every
     * signature, instead of an {@code io.Reader}. It runs before the shared pass, which copies
     * the body type into the signature arguments. Multipart and every other body kind keep the
     * type the spec maps.
     *
     * @param op the operation
     */
    static void markBinaryUpload(final CodegenOperation op) {
        final CodegenParameter body = op.bodyParam;
        if (body == null
                || !body.isBinary
                || op.isMultipart
                || (op.formParams != null && !op.formParams.isEmpty())) {
            return;
        }
        body.dataType = "*BodySource";
        body.baseType = "BodySource";
        body.datatypeWithEnum = "*BodySource";
        if (op.allParams != null) {
            for (final CodegenParameter p : op.allParams) {
                if (p.isBodyParam) {
                    p.dataType = "*BodySource";
                    p.baseType = "BodySource";
                    p.datatypeWithEnum = "*BodySource";
                }
            }
        }
    }

    /**
     * A binary success response is returned as a {@code *ByteStream} by the one operation method
     * (D119), so after the shared pass has computed {@code streamKind} the return type becomes
     * {@code ByteStream} and the decorator {@code streamsBinary} tells the api template to call
     * {@code invokeApiForStream}.
     *
     * @param objs the processed operations map
     */
    @SuppressWarnings("unchecked")
    private static void markBinaryStreams(final OperationsMap objs) {
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations == null) {
            return;
        }
        final List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
        if (ops == null) {
            return;
        }
        for (final CodegenOperation op : ops) {
            markBinaryStream(op);
        }
    }

    /**
     * Rewrites one operation whose {@code streamKind} is {@code binary}.
     *
     * @param op the operation
     */
    @SuppressWarnings("unchecked")
    static void markBinaryStream(final CodegenOperation op) {
        final Object decorators = op.vendorExtensions.get("op");
        if (!(decorators instanceof Map)) {
            return;
        }
        final Map<String, Object> d = (Map<String, Object>) decorators;
        final Object kind = d.get("streamKind");
        final boolean binary = "binary".equals(kind);
        final boolean sse = "sse".equals(kind) && Boolean.TRUE.equals(d.get("streamOnly"));
        final boolean jsonLines =
                "jsonlines".equals(kind) && Boolean.TRUE.equals(d.get("streamOnly"));
        d.put("streamsBinary", binary);
        d.put("streamsSse", sse);
        d.put("streamsJsonLines", jsonLines);
        if (binary) {
            op.returnType = "ByteStream";
            op.returnBaseType = "ByteStream";
            op.returnContainer = null;
            op.isArray = false;
            op.vendorExtensions.put("returnByValue", false);
        }
        if (sse || jsonLines) {
            /* An event stream whose data is text hands it over raw; every other
             * stream decodes each line or data field as the declared schema. */
            final boolean text = sse && Boolean.TRUE.equals(d.get("streamEventIsText"));
            final Object declared = d.get("streamItemType");
            final String declaredType = declared == null ? null : declared.toString();
            final boolean typed = !text && declaredType != null;
            final String item = typed && declaredType != null ? declaredType : "string";
            d.put("streamTyped", typed);
            d.put("streamData", item);
            op.returnType = (sse ? "SseStream[" : "JsonLinesStream[") + item + "]";
            op.returnBaseType = sse ? "SseStream" : "JsonLinesStream";
            op.returnContainer = null;
            op.isArray = false;
            op.vendorExtensions.put("returnByValue", false);
        }
    }

    /**
     * Hands the README and SKILLS the first operation that returns a {@code ByteStream}, as
     * {@code binaryStreamOperation} (with {@code binaryStreamGroup}, its operations map, and
     * {@code hasBinaryStreamOperation}), so the Streaming example calls a real operation of the
     * spec under generation. Nothing is rendered when the spec has none.
     *
     * @param objs the supporting file data
     * @return the data with the stream operation added
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> postProcessSupportingFileData(final Map<String, Object> objs) {
        final String[] flags = {"streamsBinary", "streamsSse", "streamsJsonLines", "hasPagesMethod"};
        final String[] names = {"binaryStream", "sseStream", "jsonLinesStream", "paged"};
        for (int kind = 0; kind < flags.length; kind++) {
            CodegenOperation found = null;
            Object group = null;
            final Object apiInfo = objs.get("apiInfo");
            if (apiInfo instanceof Map) {
                final Object apis = ((Map<String, Object>) apiInfo).get("apis");
                if (apis instanceof List) {
                    for (final Object api : (List<Object>) apis) {
                        final Object operations = ((Map<String, Object>) api).get("operations");
                        if (!(operations instanceof Map)) {
                            continue;
                        }
                        final Object list = ((Map<String, Object>) operations).get("operation");
                        if (!(list instanceof List)) {
                            continue;
                        }
                        for (final Object candidate : (List<Object>) list) {
                            final CodegenOperation op = (CodegenOperation) candidate;
                            final Object decorators =
                                    op.vendorExtensions == null
                                            ? null
                                            : op.vendorExtensions.get("op");
                            if (found == null
                                    && decorators instanceof Map
                                    && Boolean.TRUE.equals(
                                            ((Map<String, Object>) decorators).get(flags[kind]))) {
                                found = op;
                                group = operations;
                            }
                        }
                    }
                }
            }
            final String name = names[kind];
            objs.put(
                    "has" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Operation",
                    found != null);
            if (found != null) {
                objs.put(name + "Operation", found);
                objs.put(name + "Group", group);
            }
        }
        return super.postProcessSupportingFileData(objs);
    }

    /**
     * Whether an operation returning this Go type hands it back by value: a
     * slice (byte slices included), a map or a string (F877).
     *
     * @param returnType the Go return type, or null for a void operation
     * @return true when the type is returned by value
     */
    static boolean returnsByValue(@Nullable final String returnType) {
        return returnType != null
                && (returnType.startsWith("[]")
                        || returnType.startsWith("map[")
                        || "string".equals(returnType));
    }

    /**
     * Moves the per-operation {@code server} argument in front of the
     * {@code options} argument of every operation's {@code signatureArgs}, so
     * the argument order is required parameters, then server, then the
     * optional-parameters object (decision F803): adding security to an
     * operation, which adds an options argument, never moves an existing
     * argument. The shared list is built path, body, options, server; this
     * rewrites it in place after the shared decorator pass has run.
     *
     * @param objs the processed operations map
     */
    @SuppressWarnings("unchecked")
    private static void reorderServerBeforeOptions(final OperationsMap objs) {
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations == null) {
            return;
        }
        final List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
        if (ops == null) {
            return;
        }
        for (final CodegenOperation op : ops) {
            final Object decorator = op.vendorExtensions.get("op");
            if (!(decorator instanceof Map)) {
                continue;
            }
            for (final String key : Arrays.asList("signatureArgs", "signatureArgsNoOptions")) {
                final Object list = ((Map<String, Object>) decorator).get(key);
                if (list instanceof List) {
                    moveServerBeforeOptions((List<Map<String, Object>>) list);
                }
            }
        }
    }

    static void moveServerBeforeOptions(final List<Map<String, Object>> args) {
        int optionsAt = -1;
        int serverAt = -1;
        for (int i = 0; i < args.size(); i++) {
            if (Boolean.TRUE.equals(args.get(i).get("isOptions"))) {
                optionsAt = i;
            } else if (Boolean.TRUE.equals(args.get(i).get("isServer"))) {
                serverAt = i;
            }
        }
        if (optionsAt >= 0 && serverAt > optionsAt) {
            final Map<String, Object> server = args.remove(serverAt);
            args.add(optionsAt, server);
        }
    }

    /**
     * Marks every parameter declared with {@code content} (instead of
     * {@code schema}) so the api template encodes it through the SDK's own
     * object serializer in every location (decision F054). The per-location
     * lists hold copies, so each list is stamped.
     *
     * @param op the operation whose parameters to stamp
     */
    private static void stampContentParams(final CodegenOperation op) {
        for (final List<CodegenParameter> params :
                Arrays.asList(
                        op.allParams,
                        op.pathParams,
                        op.queryParams,
                        op.headerParams,
                        op.cookieParams)) {
            if (params == null) {
                continue;
            }
            for (final CodegenParameter p : params) {
                final boolean hasContent = p.getContent() != null && !p.getContent().isEmpty();
                if (p.vendorExtensions == null) {
                    p.vendorExtensions = new HashMap<>();
                }
                p.vendorExtensions.put("goContent", hasContent);
            }
        }
    }

    /**
     * Returns whether the operation has any required parameter that is carried
     * on the per-operation Options object — that is, a required query, header,
     * form, or cookie parameter.
     *
     * <p>These are the parameters whose absence the generated method detects via
     * a nil-options check, so the api template uses this to emit that guard
     * exactly once. Path and body parameters are intentionally excluded: they
     * are passed as plain positional arguments (not on the Options object) and
     * are validated by their own dedicated checks in the template.
     *
     * @param op the operation to inspect
     * @return {@code true} if at least one required query/header/form/cookie
     *     parameter exists
     */
    private static boolean anyRequiredOptionsParam(final CodegenOperation op) {
        final List<List<CodegenParameter>> optionsBorneParams =
                Arrays.asList(
                        op.queryParams, op.headerParams, op.formParams, op.cookieParams);
        return optionsBorneParams.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .anyMatch(p -> p.required);
    }

    /**
     * The exported Go field name of a parameter on its Options struct, for
     * example {@code petId} becomes {@code PetId} and {@code 2ndPage} becomes
     * {@code N2ndPage}. The Options file and the api template both read it, so
     * the field a method dereferences is always the field the struct declares.
     *
     * @param paramName the parameter's codegen name
     * @return the Options struct field name
     */
    static String optionsFieldName(String paramName) {
        return goIdentifier(NamingConvention.PASCAL_CASE.apply(paramName));
    }

    /**
     * Sets the native {@code nameInPascalCase} of every parameter of the
     * operation to its Options struct field name, so the api template reads
     * {@code nameInPascalCase} instead of re-deriving the name with a lambda.
     * The per-location lists hold copies of the {@code allParams} entries, so
     * each list is stamped.
     *
     * @param op the operation whose parameters to stamp
     */
    private static void stampOptionsFieldNames(CodegenOperation op) {
        for (final List<CodegenParameter> params :
                Arrays.asList(
                        op.allParams,
                        op.pathParams,
                        op.queryParams,
                        op.headerParams,
                        op.formParams,
                        op.cookieParams,
                        op.bodyParams,
                        op.requiredParams,
                        op.optionalParams)) {
            if (params == null) {
                continue;
            }
            for (final CodegenParameter p : params) {
                p.nameInPascalCase = optionsFieldName(p.paramName);
            }
        }
    }

    /**
     * Turns a PascalCase name into a legal exported Go identifier: characters
     * Go does not allow in an identifier are dropped and a name that would
     * start with a digit gets an {@code N} prefix, so a spec name such as
     * {@code 2ndPage} still compiles.
     *
     * @param name the PascalCase name
     * @return a legal Go identifier
     */
    static String goIdentifier(String name) {
        final String clean = name.replaceAll("[^A-Za-z0-9_]", "");
        if (clean.isEmpty() || Character.isDigit(clean.charAt(0))) {
            return "N" + clean;
        }
        return clean;
    }

    /**
     * Escapes text for the inside of a Go interpreted string literal.
     *
     * @param text the raw text
     * @return the text with backslashes, quotes and control characters escaped
     */
    static String goStringBody(String text) {
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
            } else if (c < 0x20) {
                out.append(String.format(Locale.ROOT, "\\x%02x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}

package io.github.mridang.codegen.generators.elixir;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import org.openapitools.codegen.model.OperationsMap;
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

    @Override
    protected String errorSuffix() {
        return "Error";
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterElixirCodegen.class);

    /**
     * Types a {@code type: number} with {@code format: decimal} as the exact
     * {@code Decimal.t()} (D135); the shared mapping gives every other number
     * {@code float()}, and the base generator does not resolve that format to
     * {@code decimal} for a number.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public String getSchemaType(Schema schema) {
        final String type = super.getSchemaType(schema);
        if ("float()".equals(type) && schema != null && "decimal".equals(schema.getFormat())) {
            return "Decimal.t()";
        }
        return type;
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaTemplate() {
        return "errors/schema_error.mustache";
    }

    /**
     * After the shared pass, marks the operations whose success response is binary: the decorator
     * {@code streamsBinary} tells the templates to call the stream invoker, and the return type
     * becomes {@code <module>.ByteStream.t()} (D119).
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        final Map<String, Object> early = (Map<String, Object>) objs.get("operations");
        if (early != null && early.get("operation") != null) {
            for (final CodegenOperation op : (List<CodegenOperation>) early.get("operation")) {
                markBinaryUpload(op, moduleName);
            }
        }
        final OperationsMap processed = super.postProcessOperationsWithModels(objs, allModels);
        final Map<String, Object> operations = (Map<String, Object>) processed.get("operations");
        if (operations != null) {
            final List<CodegenOperation> ops =
                    (List<CodegenOperation>) operations.get("operation");
            if (ops != null) {
                for (final CodegenOperation op : ops) {
                    markBinaryStream(op, moduleName);
                    writeResponseHeadersFile(op);
                }
            }
        }
        return processed;
    }

    /**
     * D168: writes the {@code <OperationId>ResponseHeaders} struct of an operation whose success
     * responses declare headers, next to its options struct, from {@code api/response_headers.mustache}.
     * The flags {@code usesString} and the like tell the template which parse functions to emit, so
     * no unused private function is generated.
     */
    @SuppressWarnings("unchecked")
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "HTTP header and identifier names are ASCII")
    private void writeResponseHeadersFile(final CodegenOperation op) {
        final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
        if (!(decorators instanceof Map)) {
            return;
        }
        final Map<String, Object> d = (Map<String, Object>) decorators;
        if (!Boolean.TRUE.equals(d.get("hasResponseHeaders"))) {
            return;
        }
        final Object declared = d.get("responseHeaders");
        if (!(declared instanceof List)) {
            return;
        }
        final List<Map<String, Object>> rows = (List<Map<String, Object>>) declared;
        final String className = String.valueOf(d.get("responseHeadersClassName"));
        final List<Map<String, Object>> headers = new ArrayList<>();
        final Map<String, Object> context = new HashMap<>();
        for (final Map<String, Object> row : rows) {
            final Map<String, Object> header = new HashMap<>(row);
            final String kind = String.valueOf(row.get("kind"));
            final boolean array = Boolean.TRUE.equals(row.get("isArray"));
            final boolean text = "string".equals(kind);
            header.put("name", NamingConvention.SNAKE_CASE.apply(String.valueOf(row.get("varName"))));
            header.put("lowerName", String.valueOf(row.get("wireName")).toLowerCase(Locale.ROOT));
            header.put("parser", "long".equals(kind) ? "parse_integer" : "parse_" + kind);
            header.put("typespec", (array ? "[" : "") + headerTypespec(kind) + (array ? "]" : ""));
            header.put("plainText", text && !array);
            header.put("textList", text && array);
            header.put("parsedScalar", !text && !array);
            header.put("parsedList", !text && array);
            if (!(text && !array)) {
                context.put("usesWithValue", true);
            }
            if (array) {
                context.put("usesSplit", true);
            }
            if (!text) {
                context.put("usesParser", true);
                context.put("long".equals(kind) ? "usesInteger" : "uses" + Character.toUpperCase(kind.charAt(0))
                        + kind.substring(1), true);
            }
            headers.add(header);
        }
        context.put("className", className);
        context.put("moduleName", moduleName);
        context.put("operationId", op.operationId);
        context.put("headers", headers);
        final String filePath = Path.of(getOutputDir(), "lib", packageName, "api", "response_headers",
                NamingConvention.SNAKE_CASE.apply(className) + ".ex").toString();
        writeFile(filePath, renderOptionsTemplate("api/response_headers.mustache", context));
        postProcessFile(Path.of(filePath).toFile(), "source");
    }

    /** The typespec of one header value of the given kind (D168). */
    private static String headerTypespec(final String kind) {
        switch (kind) {
            case "integer":
            case "long":
                return "integer()";
            case "number":
                return "float()";
            case "boolean":
                return "boolean()";
            default:
                return "String.t()";
        }
    }

    /**
     * A raw binary request body is a {@code BodySource}: the operation takes it, in every
     * signature, instead of the bytes. It runs before the shared pass, which copies the body type
     * into the signature arguments. Multipart and every other body kind keep the type the spec
     * maps.
     */
    static void markBinaryUpload(final CodegenOperation op, final String moduleName) {
        final CodegenParameter body = op.bodyParam;
        if (body == null
                || !body.isBinary
                || op.isMultipart
                || (op.formParams != null && !op.formParams.isEmpty())) {
            return;
        }
        final String type = moduleName + ".BodySource.t()";
        body.dataType = type;
        body.baseType = type;
        body.datatypeWithEnum = type;
        if (op.allParams != null) {
            for (final CodegenParameter p : op.allParams) {
                if (p.isBodyParam) {
                    p.dataType = type;
                    p.baseType = type;
                    p.datatypeWithEnum = type;
                }
            }
        }
    }

    /**
     * Sets {@code streamsBinary} on {@code vendorExtensions.op} and rewrites the return type of an
     * operation whose {@code streamKind} is {@code binary}.
     */
    @SuppressWarnings("unchecked")
    static void markBinaryStream(final CodegenOperation op, final String moduleName) {
        final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
        if (!(decorators instanceof Map)) {
            return;
        }
        final Map<String, Object> d = (Map<String, Object>) decorators;
        final Object kind = d.get("streamKind");
        final boolean binary = "binary".equals(kind);
        d.put("streamsBinary", binary);
        if (binary) {
            op.returnType = moduleName + ".ByteStream.t()";
            op.returnBaseType = moduleName + ".ByteStream.t()";
        }
        final boolean only = Boolean.TRUE.equals(d.get("streamOnly"));
        final boolean sse = "sse".equals(kind) && only;
        final boolean jsonLines = "jsonlines".equals(kind) && only;
        d.put("streamsSse", sse);
        d.put("streamsJsonLines", jsonLines);
        d.put("streamsAny", binary || sse || jsonLines);
        if (sse || jsonLines) {
            final boolean text = sse && Boolean.TRUE.equals(d.get("streamEventIsText"));
            final Object declared = d.get("streamItemType");
            final boolean typed = !text && declared != null;
            d.put("streamTyped", typed);
            d.put("streamItemDecode", typed && declared != null ? declared.toString() : "");
            final String carrier = moduleName + (sse ? ".SseStream.t()" : ".JsonLinesStream.t()");
            op.returnType = carrier;
            op.returnBaseType = carrier;
        }
    }

    /**
     * Hands the README and SKILLS the first paged operation, as {@code pagedOperation}, and the first operation that returns a {@code ByteStream}, as
     * {@code binaryStreamOperation} (with {@code binaryStreamGroup}, its operations map, and
     * {@code hasBinaryStreamOperation}), so the Streaming example calls a real operation of the
     * spec under generation. Nothing is rendered when the spec has none.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
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
                                    op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
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
            objs.put("has" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Operation", found != null);
            if (found != null) {
                objs.put(name + "Operation", found);
                objs.put(name + "Group", group);
            }
        }
        return super.postProcessSupportingFileData(objs);
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaFilePath(String className) {
        final String pkg = Optional.ofNullable((String) additionalProperties.get("packageName"))
                .orElse(packageName);
        return Path.of(
                        outputFolder,
                        "lib",
                        pkg,
                        "errors",
                        org.openapitools.codegen.utils.StringUtils.underscore(className) + ".ex")
                .toString();
    }

    /**
     * The generated per-scheme authenticators that read an environment variable, with the
     * variable of each credential, for the generated unit test (F862).
     */
    private final List<Map<String, Object>> perSchemeAuthEntries = new ArrayList<>();

    protected String packageName = "openapi_client";
    protected String packageVersion = "1.0.0";
    protected String moduleName = "OpenApiClient";

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
        // format: decimal (number or string shaped) is the exact `Decimal.t()` of the
        // hex `decimal` package (#157). ObjectSerializer reads its digits from the
        // JSON text without passing through a float.
        typeMapping.put("decimal", "Decimal.t()");
        // Elixir's built-in Date/DateTime types preserve calendar
        // semantics that String.t() loses. ObjectSerializer can
        // pipe ISO-8601 wire strings through Date.from_iso8601!/1
        // and DateTime.from_iso8601/1 on decode.
        typeMapping.put("date", "Date.t()");
        typeMapping.put("DateTime", "DateTime.t()");
        // 4.8: format:time -> Elixir stdlib Time.t() (ISO-8601 HH:MM:SS[.fff]).
        // format:duration -> Elixir 1.17+ stdlib Duration.t(), carried on the
        // wire as google.protobuf.Duration protobuf-JSON ("<seconds>s", e.g.
        // "3600s", "1.5s"). Both are round-tripped through ObjectSerializer's
        // dedicated convert_to_type clauses. mix.exs targets ~> 1.18, so
        // Duration is always available; the older 1.16/Timex.Duration
        // fallback isn't shipped here because the supported floor is 1.18.
        typeMapping.put("time", "Time.t()");
        typeMapping.put("duration", "Duration.t()");
        // Native IP address types: the Erlang `:inet` tuples. ObjectSerializer
        // reads them strictly and writes the canonical RFC 5952 text.
        typeMapping.put("ipv4", ":inet.ip4_address()");
        typeMapping.put("ipv6", ":inet.ip6_address()");
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
                                "nil",
                                // 4.8: stdlib types -- treat as primitives so
                                // the codegen doesn't try to emit a model for
                                // them or wrap them in Module.t() etc.
                                "Time.t()",
                                "Duration.t()",
                                "Decimal.t()",
                                ":inet.ip4_address()",
                                ":inet.ip6_address()"));

        reservedWords = loadReservedWords("/reserved-words/elixir.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.MODULE_NAME,
                CodegenConstants.MODULE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated Elixir package (default: 1.0.0).").defaultValue("1.0.0"));
        cliOptions.add(CliOption.newString("repositoryUrl",
                "Source repository of the package, written to the Hex links and the docs source URL."));
        cliOptions.add(CliOption.newString("homepageUrl",
                "Home page of the package, written to the Hex links and the docs home page."));
        cliOptions.add(CliOption.newString("authors",
                "Comma-separated names of the package authors, written to the Hex maintainers."));
        cliOptions.add(CliOption.newString("documentationUrl",
                "Where the package is documented, written to the Hex links."));
        cliOptions.add(CliOption.newString("issueTrackerUrl",
                "Where issues are reported, written to the Hex links."));
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

    /**
     * Marks a model that has a {@code format: password} property, so the model
     * template derives an {@code Inspect} that leaves those fields out and the
     * plaintext never reaches {@code inspect/1} or a log line (F867).
     *
     * @param model    the model being processed
     * @param property the property just processed
     */
    @Override
    public void postProcessModelProperty(
            final CodegenModel model, final CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (property.isPassword) {
            model.vendorExtensions.put("x-has-password-field", true);
        }
    }

    /**
     * Neutralises the hash-brace interpolation opener of an Elixir string
     * literal (after the shared backslash doubling), so a value such as
     * hash-brace-x-brace stays the four characters it is on the wire.
     */
    @Override
    protected List<String[]> getEnumStringEscapes() {
        return List.<String[]>of(new String[] {"#\\{", "\\\\#{"});
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "elixir:1.19@sha256:4c0f76111c8224e6cd2e3f2733a38dbac31c1609a79bce1d1242a1c4acffd4d9";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        /* Twice, and with the inputs named rather than left to
         * `.formatter.exs`: a super call that ends in a keyword option is not
         * a fixed point of one pass — `mix format` emits `[], refresh_url:
         * "..."` on one line and `--check-formatted` then rejects it — and a
         * bare `mix format` consults its own manifest and skips the file it
         * just wrote. The spec runs `--check-formatted` over the committed
         * output, so it has to be settled here. */
        final String inputs = generateExamples
                ? "'{mix,.formatter}.exs' '{config,lib,test,examples}/**/*.{ex,exs}'"
                : "'{mix,.formatter}.exs' '{config,lib,test}/**/*.{ex,exs}'";
        return new String[] {"mix format " + inputs, "mix format " + inputs};
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("integer()", "float()");
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayTypeTemplate() {
        return "[%2$s]";
    }

    /**
     * Elixir uses {@code MapSet.t(Type)} for unique-item collections
     * (the idiomatic Elixir set type).
     */
    @Override
    protected String getUniqueItemsSetType() {
        return "MapSet.t($1)";
    }

    /**
     * Matches Elixir's bracket-wrapped type syntax {@code [Type]},
     * capturing the inner type for replacement by
     * {@link #getUniqueItemsSetType()}.
     */
    @Override
    protected String getArrayContainerPattern() {
        return "^\\[(.+)\\]$";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "%%{%2$s => %3$s}";
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
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String pkg = Optional.ofNullable((String) additionalProperties.get("packageName"))
                .orElse(packageName);
        final String libDir = Path.of("lib", pkg).toString();
        final String apiDir = Path.of(libDir, "api").toString();
        final String modelsDir = Path.of(libDir, "models").toString();
        final String errorsDir = Path.of(libDir, "errors").toString();
        final List<SupportingFileSpec> specs = new ArrayList<>(List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("api_module.mustache", apiDir, "api_module.ex"),
            new SupportingFileSpec("models_module.mustache", modelsDir, "models_module.ex"),
            new SupportingFileSpec("configuration.mustache", libDir, "configuration.ex"),
            new SupportingFileSpec("transport_options.mustache", libDir, "transport_options.ex"),
            new SupportingFileSpec("server_configuration.mustache", libDir, "server_configuration.ex"),
            new SupportingFileSpec("servers.mustache", libDir, "servers.ex"),
            new SupportingFileSpec("errors/api_error.mustache", errorsDir, "api_error.ex"),
            new SupportingFileSpec("errors/serialization_error.mustache", errorsDir, "serialization_error.ex"),
            new SupportingFileSpec("errors/oauth2_server_error.mustache", errorsDir, "oauth2_server_error.ex"),
            new SupportingFileSpec("errors/oauth2_token_error.mustache", errorsDir, "oauth2_token_error.ex"),
            new SupportingFileSpec("errors/client_error.mustache", errorsDir, "client_error.ex"),
            new SupportingFileSpec("errors/server_error.mustache", errorsDir, "server_error.ex"),
            new SupportingFileSpec("errors/bad_request_error.mustache", errorsDir, "bad_request_error.ex"),
            new SupportingFileSpec("errors/unauthorized_error.mustache", errorsDir, "unauthorized_error.ex"),
            new SupportingFileSpec("errors/forbidden_error.mustache", errorsDir, "forbidden_error.ex"),
            new SupportingFileSpec("errors/not_found_error.mustache", errorsDir, "not_found_error.ex"),
            new SupportingFileSpec("errors/conflict_error.mustache", errorsDir, "conflict_error.ex"),
            new SupportingFileSpec("errors/payment_required_error.mustache", errorsDir, "payment_required_error.ex"),
            new SupportingFileSpec("errors/method_not_allowed_error.mustache", errorsDir, "method_not_allowed_error.ex"),
            new SupportingFileSpec("errors/request_timeout_error.mustache", errorsDir, "request_timeout_error.ex"),
            new SupportingFileSpec("errors/gone_error.mustache", errorsDir, "gone_error.ex"),
            new SupportingFileSpec("errors/payload_too_large_error.mustache", errorsDir, "payload_too_large_error.ex"),
            new SupportingFileSpec("errors/unsupported_media_type_error.mustache", errorsDir, "unsupported_media_type_error.ex"),
            new SupportingFileSpec("errors/locked_error.mustache", errorsDir, "locked_error.ex"),
            new SupportingFileSpec("errors/not_implemented_error.mustache", errorsDir, "not_implemented_error.ex"),
            new SupportingFileSpec("errors/bad_gateway_error.mustache", errorsDir, "bad_gateway_error.ex"),
            new SupportingFileSpec("errors/service_unavailable_error.mustache", errorsDir, "service_unavailable_error.ex"),
            new SupportingFileSpec("errors/gateway_timeout_error.mustache", errorsDir, "gateway_timeout_error.ex"),
            new SupportingFileSpec("errors/unprocessable_entity_error.mustache", errorsDir, "unprocessable_entity_error.ex"),
            new SupportingFileSpec("errors/too_many_requests_error.mustache", errorsDir, "too_many_requests_error.ex"),
            new SupportingFileSpec("errors/internal_server_error.mustache", errorsDir, "internal_server_error.ex"),
            new SupportingFileSpec("errors/network_error.mustache", errorsDir, "network_error.ex"),
            new SupportingFileSpec("errors/network_timeout_error.mustache", errorsDir, "network_timeout_error.ex"),
            new SupportingFileSpec("errors/stream_error.mustache", errorsDir, "stream_error.ex"),
            new SupportingFileSpec("errors/response_too_large_error.mustache", errorsDir, "response_too_large_error.ex"),
            new SupportingFileSpec("errors/pagination_error.mustache", errorsDir, "pagination_error.ex"),
            new SupportingFileSpec("errors/root_error.mustache", errorsDir, errorPrefixSnake() + "_error.ex"),
            new SupportingFileSpec("header_selector.mustache", libDir, "header_selector.ex"),
            new SupportingFileSpec("object_serializer.mustache", libDir, "object_serializer.ex"),
            new SupportingFileSpec("value_serializer.mustache", libDir, "value_serializer.ex"),
            new SupportingFileSpec("trace_context_util.mustache", libDir, "trace_context_util.ex"),
            new SupportingFileSpec("call_info.mustache", libDir, "call_info.ex"),
            new SupportingFileSpec("call_outcome.mustache", libDir, "call_outcome.ex"),
            new SupportingFileSpec("call_hook.mustache", libDir, "call_hook.ex"),
            new SupportingFileSpec("api_response.mustache", libDir, "api_http_response.ex"),
            new SupportingFileSpec("api_result.mustache", libDir, "api_result.ex"),
            new SupportingFileSpec("api_stream_response.mustache", libDir, "api_stream_response.ex"),
            new SupportingFileSpec("body_source.mustache", libDir, "body_source.ex"),
            new SupportingFileSpec("byte_stream.mustache", libDir, "byte_stream.ex"),
            new SupportingFileSpec("stream_body.mustache", libDir, "stream_body.ex"),
            new SupportingFileSpec("event_stream.mustache", libDir, "event_stream.ex"),
            new SupportingFileSpec("server_sent_event.mustache", libDir, "server_sent_event.ex"),
            new SupportingFileSpec("sse_stream.mustache", libDir, "sse_stream.ex"),
            new SupportingFileSpec("json_lines_stream.mustache", libDir, "json_lines_stream.ex"),
            new SupportingFileSpec("paginator.mustache", libDir, "paginator.ex"),
            new SupportingFileSpec("page.mustache", libDir, "page.ex"),
            new SupportingFileSpec("api_client.mustache", libDir, "api_client.ex"),
            new SupportingFileSpec("default_api_client.mustache", libDir, "default_api_client.ex"),
            new SupportingFileSpec("base_api.mustache", Path.of(libDir, "api").toString(), "base_api.ex"),
            new SupportingFileSpec("authenticator.mustache", Path.of(libDir, "auth").toString(), "authenticator.ex"),
            new SupportingFileSpec("main_module.mustache", "lib", pkg + ".ex"),
            new SupportingFileSpec("mix_exs.mustache", "", "mix.exs"),
            new SupportingFileSpec("coveralls_json.mustache", "", "coveralls.json"),
            new SupportingFileSpec("formatter_exs.mustache", "", ".formatter.exs"),
            new SupportingFileSpec("credo_exs.mustache", "", ".credo.exs"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore"),
            new SupportingFileSpec(
                "dev_dependencies.mustache", ".openapi-generator", "DEV-DEPENDENCIES")
        ));
        if (generateExamples) {
            /* examples/ sits outside elixirc_paths, so the package never compiles or ships it;
             * `make examples` compiles it with elixirc against the built package. */
            specs.add(new SupportingFileSpec("examples/quickstart.mustache", "examples", "quickstart.ex"));
        }
        return specs;
    }

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
        additionalProperties.put("perSchemeAuthEntries", perSchemeAuthEntries);
        putManifestProperties();

        final String libDir = Path.of("lib", packageName).toString();

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", libDir, clientClassFile + ".ex"));

        // Spec-independent unit tests exercise infrastructure modules
        // (value serializer, header selector, configuration, transport
        // options, trace-context util, and the pure-unit default API
        // client) that exist in every generated client regardless of the
        // OpenAPI spec. These ship to real clients when unit tests are
        // requested. Everything else below stays golden-only because it
        // is petstore-coupled or depends on the container test harness.
        if (emitUnitTests()) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "test",
                            "value_serializer_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/event_stream_test.mustache", "test", "event_stream_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "test",
                            "header_selector_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration_test.mustache",
                            "test",
                            "configuration_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "test",
                            "transport_options_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "test",
                            "trace_context_util_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/call_hook_test.mustache",
                            "test",
                            "call_hook_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/call_hook_recipe_test.mustache",
                            "test",
                            "call_hook_recipe_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "test",
                            "default_api_client_unit_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_configuration_test.mustache",
                            "test",
                            "server_configuration_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_variable_test.mustache",
                            "test",
                            "server_variable_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_result_test.mustache", "test", "api_result_test.exs"));
        }

        if (generateTests) {
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
                            "test/object_serializer_test.mustache",
                            "test",
                            "object_serializer_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache", "test", "base_api_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "test",
                            "client_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_error_test.mustache", "test", "api_error_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache", "test", "metadata_test.exs"));
            supportingFiles.add(
                    new SupportingFile("test/readme_test.mustache", "test", "readme_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "test",
                            "composed_schema_test.exs"));
        }
    }

    /**
     * Derives the manifest values the templates read from the {@code authors},
     * {@code repositoryUrl}, {@code homepageUrl}, {@code documentationUrl} and
     * {@code issueTrackerUrl} options: the author list (each name as text and as
     * an Elixir literal), the Hex {@code links} map literal and the two docs
     * URLs. Every spec value is written through {@link #toElixirStringLiteral}.
     */
    private void putManifestProperties() {
        final List<String> names = new ArrayList<>();
        final Object authors = additionalProperties.get("authors");
        if (authors instanceof Collection) {
            for (final Object author : (Collection<?>) authors) {
                names.add(String.valueOf(author));
            }
        } else if (authors != null) {
            names.addAll(Arrays.asList(String.valueOf(authors).split(",")));
        }
        final List<Map<String, String>> authorList = new ArrayList<>();
        for (final String name : names) {
            final String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                final Map<String, String> author = new HashMap<>();
                author.put("text", trimmed);
                author.put("literal", toElixirStringLiteral(trimmed));
                authorList.add(author);
            }
        }
        additionalProperties.put("authorList", authorList);
        additionalProperties.put("hasAuthors", !authorList.isEmpty());

        final List<String> links = new ArrayList<>();
        addManifestLink(links, "Repository", "repositoryUrl");
        addManifestLink(links, "Homepage", "homepageUrl");
        addManifestLink(links, "Documentation", "documentationUrl");
        addManifestLink(links, "Issues", "issueTrackerUrl");
        additionalProperties.put("hasPackageLinks", !links.isEmpty());
        additionalProperties.put("packageLinksLiteral", "%{" + String.join(", ", links) + "}");
        for (final String option : new String[] {"repositoryUrl", "homepageUrl"}) {
            final String literal = manifestUrlLiteral(option);
            if (literal != null) {
                additionalProperties.put(option + "Literal", literal);
            }
        }
    }

    private void addManifestLink(final List<String> links, final String label, final String option) {
        final String url = manifestUrlLiteral(option);
        if (url != null) {
            links.add(toElixirStringLiteral(label) + " => " + url);
        }
    }

    @Nullable
    private String manifestUrlLiteral(final String option) {
        final Object value = additionalProperties.get(option);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return toElixirStringLiteral(String.valueOf(value).trim());
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
            // An enum field's wire default ("medium") must become its atom
            // form (:"medium") so an absent property yields the same typed
            // value the deserializer produces for a present one — the struct
            // @type declares enum fields as atoms, and ObjectSerializer
            // atomizes the wire value verbatim via String.to_atom/1. Emitting
            // the raw string here would leave the struct default as a String,
            // diverging from the present-property path.
            if (resolved.getEnum() != null && !resolved.getEnum().isEmpty()) {
                return ":\"" + escapeText(String.valueOf(resolved.getDefault())) + "\"";
            } else if ("decimal".equals(resolved.getFormat())) {
                // A Decimal default is built from its exact text, never from a float literal.
                final Object raw = resolved.getDefault();
                final String text = raw instanceof java.math.BigDecimal
                        ? ((java.math.BigDecimal) raw).toPlainString()
                        : String.valueOf(raw);
                return "Decimal.new(\"" + escapeText(text) + "\")";
            } else if (ModelUtils.isStringSchema(resolved)) {
                // The shared literal escape, so #{ cannot interpolate and the
                // default is exactly the spec string.
                return quoteEnumValue(escapeEnumStringValue(String.valueOf(resolved.getDefault())));
            } else if (ModelUtils.isBooleanSchema(resolved)) {
                return resolved.getDefault().toString();
            } else if (ModelUtils.isIntegerSchema(resolved)
                    || ModelUtils.isNumberSchema(resolved)) {
                return resolved.getDefault().toString();
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean sortVarsByDefaultValue() {
        return true;
    }

    /**
     * Keeps every defaulted property last in each struct. {@code defstruct} takes its plain
     * atoms first and its {@code key: default} pairs last, and a property whose {@code $ref}
     * carries a sibling default (F866) is written as a pair although its own
     * {@code defaultValue} is empty, so the shared sort on {@code defaultValue} cannot see it.
     * Runs after the shared pass, which re-sorts discriminator children, so the order is final.
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                modelMap.getModel().vars.sort(
                        Comparator.comparing(p -> hasStructDefault(p) ? 1 : 0));
            }
        }
        return result;
    }

    /** Whether the property is written as a {@code key: default} pair in {@code defstruct}. */
    static boolean hasStructDefault(CodegenProperty property) {
        return property.defaultValue != null
                || Boolean.TRUE.equals(property.vendorExtensions.get("siblingDefaultIsEnum"));
    }

    /**
     * Enables Gap K (auto-inject discriminator on serialise) for
     * Elixir. The Elixir {@code defstruct} template emits keyword
     * entries from {@code defaultValue}, so subtype structs gain
     * {@code defstruct [:weight_kg, food_type: "dry"]} and the
     * {@code ObjectSerializer} carries the discriminator value
     * automatically.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    protected String getNullLiteral() {
        return "nil";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "lib";
    }

    /**
     * Every operation takes an options struct (F827), so each call has a slot for the per-call
     * {@code timeout}, {@code headers} and {@code max_retries}, even an operation with no query,
     * header, form or cookie parameter and no security.
     */
    @Override
    protected boolean mintsOptionsForEveryOperation() {
        return true;
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
            param.put("typespec", toElixirTypespec(p, moduleName));
            param.put("required", p.required);
            // Propagate the parameter's deprecation flag so the Options field
            // can carry an @deprecated marker, mirroring how operations and
            // model properties already surface deprecation.
            param.put("deprecated", p.isDeprecated);
            param.put("examples", toExampleDocs(p));
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
        context.put("callSettingsOnly", params.isEmpty());
        /* F857: an operation uses the credential of the scheme its spec names, which the
         * client holds per scheme; there is no per-call authenticator, so no `auth` field. */
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /**
     * Returns the named examples of an optional parameter as heredoc-safe
     * summary and value pairs, so the options module documents them the way
     * the operation function documents the examples of a required parameter.
     */
    static List<Map<String, Object>> toExampleDocs(CodegenParameter p) {
        final List<Map<String, Object>> docs = new ArrayList<>();
        /* The options files are written before the shared pass copies the examples
           into the vendor extensions, so the examples are read from the parameter
           itself when that copy is not there yet. */
        Object plural = p.vendorExtensions == null ? null : p.vendorExtensions.get("pluralExamples");
        if (!(plural instanceof List) && p.examples != null && !p.examples.isEmpty()) {
            plural = buildPluralExamplesList(p.examples);
        }
        if (!(plural instanceof List)) {
            return docs;
        }
        final List<?> entries = (List<?>) plural;
        for (final Object entry : entries) {
            if (entry instanceof Map) {
                final Map<?, ?> example = (Map<?, ?>) entry;
                final Map<String, Object> doc = new HashMap<>();
                doc.put("summary", toHeredocText(Objects.toString(example.get("summary"), "")));
                doc.put("value", toHeredocText(Objects.toString(example.get("value"), "")));
                docs.add(doc);
            }
        }
        return docs;
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(
                        getOutputDir(), "lib", packageName, "api", "options", fileName + ".ex")
                .toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of("lib", packageName, "auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getOAuthDir() {
        return Path.of("lib", packageName, "auth", "oauth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return stem + ".ex";
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
        if (generateTests && Boolean.TRUE.equals(additionalProperties.get("hasSecuritySchemeEnvVars"))) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/scheme_authenticator_test.mustache",
                            "test",
                            "scheme_authenticator_test.exs"));
        }
        if (generateTests && hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/basic_authenticator_test.mustache",
                            "test",
                            "basic_authenticator_test.exs"));
        }
        if (generateTests && hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/bearer_authenticator_test.mustache",
                            "test",
                            "bearer_authenticator_test.exs"));
        }
        if (generateTests && hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_key_authenticator_test.mustache",
                            "test",
                            "api_key_authenticator_test.exs"));
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
                        "oauth2_token_manager_test.exs",
                        OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec(
                        "test/oauth2_auth_code_authenticator_test.mustache",
                        "test",
                        "oauth2_authorization_code_authenticator_test.exs",
                        OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec(
                        "test/oauth2_implicit_authenticator_test.mustache",
                        "test",
                        "oauth2_implicit_authenticator_test.exs",
                        OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec(
                        "test/oauth2_client_credentials_authenticator_test.mustache",
                        "test",
                        "oauth2_client_credentials_authenticator_test.exs",
                        OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec(
                        "test/oauth2_password_authenticator_test.mustache",
                        "test",
                        "oauth2_password_authenticator_test.exs",
                        OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec(
                        "test/openid_connect_authenticator_test.mustache",
                        "test",
                        "openid_connect_authenticator_test.exs",
                        OAuthTestCondition.OIDC));
    }

    /** {@inheritDoc} */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("package", moduleName + (spec.isOAuth() ? ".Auth.OAuth" : ".Auth"));
        ctx.put("imports", List.of());
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> envVars =
                Objects.requireNonNull((List<Map<String, Object>>) ctx.get("envVars"));
        final List<Map<String, Object>> constructorParams = new ArrayList<>();
        final List<Map<String, Object>> credentials = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", NamingConvention.SNAKE_CASE.apply(name));
            param.put("type", "String.t()");
            for (final Map<String, Object> env : envVars) {
                if (name.equals(env.get("field"))) {
                    param.put("isCredential", true);
                    param.put("fieldSnake", env.get("fieldSnake"));
                    param.put("envName", env.get("envName"));
                    credentials.add(param);
                    break;
                }
            }
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", buildElixirSuperArgs(spec));
        if (!credentials.isEmpty()) {
            final String className = spec.schemeClass() + spec.oauthSuffix() + "Authenticator";
            final Map<String, Object> entry = new HashMap<>();
            entry.put("className", className);
            entry.put("module", ctx.get("package") + "." + className);
            entry.put("schemeName", spec.schemeName());
            entry.put("credentials", credentials);
            entry.put("firstEnvName", credentials.get(0).get("envName"));
            entry.put("isBearer", "BearerAuthenticator".equals(spec.baseClass()));
            entry.put("isBasic", "BasicAuthenticator".equals(spec.baseClass()));
            entry.put("isApiKey", "ApiKeyAuthenticator".equals(spec.baseClass()));
            perSchemeAuthEntries.add(entry);
        }
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    /**
     * Renders a spec value as an Elixir double-quoted string literal. The
     * backslash, the quote and the interpolation opener are escaped, as are the
     * line breaks and the tab, so a header name, URL or scope that carries one
     * of them cannot break or change the meaning of the generated source.
     */
    static String toElixirStringLiteral(@Nullable String value) {
        final String text = value == null ? "" : value;
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("#{", "\\#{").replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    /**
     * Renders the spec's declared scopes as an Elixir list of strings. A scope
     * name is escaped for a double-quoted literal, including the interpolation
     * opener, so an unusual scope cannot break the generated source.
     */
    private static String formatElixirScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        final List<String> quoted = new ArrayList<>();
        for (final String scope : scopes.keySet()) {
            quoted.add(toElixirStringLiteral(scope));
        }
        return "[" + String.join(", ", quoted) + "]";
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "keyIn values are ASCII-only OpenAPI location strings (header/query/cookie)"
                    + " — toLowerCase(Locale.ROOT) is intentional and safe here")
    private List<String> buildElixirSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("host", toElixirStringLiteral(spec.keyParamName()), "api_key",
                    ":" + (spec.keyIn() != null ? spec.keyIn().toLowerCase(Locale.ROOT) : "header"));
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "client_id", "client_secret",
                    toElixirStringLiteral(spec.tokenUrl()), formatElixirScopes(spec.scopes()));
        }
        // The refresh URL is passed as the `refresh_url` keyword option; the
        // positional parameters end at the scopes list. It is written without
        // brackets, which is the form mix format settles on — after two passes,
        // which is why the formatter step runs twice.
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final List<String> args = new ArrayList<>(List.of("host", "client_id", "client_secret",
                    toElixirStringLiteral(spec.tokenUrl()), "username", "password",
                    formatElixirScopes(spec.scopes())));
            if (spec.refreshUrl() != null) {
                args.add("refresh_url: " + toElixirStringLiteral(spec.refreshUrl()));
            }
            return args;
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final List<String> args = new ArrayList<>(List.of("host", "client_id", "client_secret",
                    toElixirStringLiteral(spec.authorizationUrl()),
                    toElixirStringLiteral(spec.tokenUrl()),
                    "redirect_uri", formatElixirScopes(spec.scopes())));
            if (spec.refreshUrl() != null) {
                args.add("refresh_url: " + toElixirStringLiteral(spec.refreshUrl()));
            }
            return args;
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "client_id", toElixirStringLiteral(spec.authorizationUrl()),
                    formatElixirScopes(spec.scopes()));
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", toElixirStringLiteral(spec.openIdConnectUrl()),
                    "client_id", "client_secret", "redirect_uri",
                    formatElixirScopes(spec.scopes()));
        }
        return List.of();
    }

    /**
     * Registers the {@code elixirTypespec} Mustache lambda. The shared codegen
     * emits a model/operation data type as a bare module ALIAS in typespec
     * position (e.g. {@code Category}, {@code [Tag]}, {@code Order}). A bare
     * alias is not a valid type — Elixir's compiler and Dialyzer flag it as an
     * undefined/unknown type. This lambda rewrites any bare model alias into the
     * fully-qualified struct type {@code <ModuleName>.Models.<Name>.t()},
     * recursing through list wrappers ({@code [Tag]} becomes
     * {@code [<ModuleName>.Models.Tag.t()]}). Stdlib/primitive descriptors
     * (anything already carrying {@code (}, a {@code %{...}} map, {@code nil},
     * or {@code term()}) pass through unchanged so {@code integer()},
     * {@code String.t()}, {@code DateTime.t()}, {@code MapSet.t(...)},
     * {@code %{String.t() => any()}} and the like are left intact. The lambda is
     * the single source of truth for typespec rendering shared by
     * {@code models/model.mustache} and {@code api/api.mustache}.
     */
    @Override
    protected ImmutableMap.Builder<String, Mustache.Lambda> addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put(
                        "elixirTypespec",
                        (fragment, writer) ->
                                writer.write(toModuleTypespec(fragment.execute(), moduleName)))
                .put(
                        "elixirSnake",
                        (fragment, writer) ->
                                writer.write(NamingConvention.SNAKE_CASE.apply(fragment.execute())))
                .put(
                        "elixirComment",
                        (fragment, writer) -> writer.write(toLineComment(fragment.execute())))
                .put(
                        "elixirHeredoc",
                        (fragment, writer) -> writer.write(toHeredocText(fragment.execute())))
                .put(
                        "elixirRawText",
                        (fragment, writer) ->
                                writer.write(fragment.execute().replace("\\#{", "#{")))
                .put(
                        "elixirString",
                        (fragment, writer) ->
                                writer.write(toStringLiteralBody(fragment.execute())))
                .put(
                        "elixirEnumWire",
                        (fragment, writer) -> writer.write(toEnumWireToken(fragment.execute())));
    }

    /**
     * Names an operation function binds itself (its receiver, the options
     * struct, the keyword list and the locals the request is built in). A
     * path or body parameter that carried one of them would be shadowed or
     * would shadow the SDK's own binding, so the shared {@code toParamName}
     * suffixes it. The wire name is unaffected: it travels in {@code baseName}.
     */
    @Override
    protected Set<String> getInjectedArgumentNames() {
        return Set.of(
                "api", "opts", "options", "auth", "path", "query_params", "header_params",
                "request_body", "cookie_parts", "declared_content_types", "server",
                "server_module", "server_url", "call", "timeout", "headers", "max_retries");
    }

    /** {@inheritDoc} */
    @Override
    protected String getInjectedArgumentSuffix() {
        return "_param";
    }

    /**
     * Neutralises the hash-brace interpolation opener as well as the shared
     * comment-closer rule. Text that went through escapeText lands in
     * interpolating double-quoted literals and heredocs (an operation's
     * {@code @doc}, a oneOf {@code @moduledoc}), where a description that
     * contains a hash-brace sequence would otherwise interpolate an undefined
     * variable and fail compilation.
     */
    @Override
    public String escapeUnsafeCharacters(String input) {
        return super.escapeUnsafeCharacters(input).replace("#{", "\\#{");
    }

    /**
     * Escapes spec text for the inside of a double-quoted Elixir string: the
     * backslash, the quote, the interpolation opener and the control characters,
     * so the literal evaluates to exactly the spec's bytes.
     */
    static String toStringLiteralBody(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("#{", "\\#{")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Escapes raw spec prose for the body of an interpolating heredoc: a
     * backslash is doubled and the hash-brace opener is escaped, so the prose
     * renders verbatim in the generated documentation.
     */
    static String toHeredocText(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("#{", "\\#{");
    }

    /**
     * Turns an inline enum member's Elixir literal (a quoted, escaped string
     * or a bare number) into the token the inline-enum descriptor of
     * {@code openapi_types/0} carries after each member name. A string becomes
     * its raw wire value percent-encoded (every byte outside letters, digits
     * and {@code -_.~} is written as {@code %XX}), so the descriptor
     * separators, quotes and interpolation openers never appear inside it; a
     * number is prefixed with {@code #}, which a percent-encoded string never
     * starts with.
     */
    static String toEnumWireToken(@Nullable String literal) {
        if (literal == null) {
            return "";
        }
        final String t = literal.trim();
        if (t.length() < 2 || t.charAt(0) != '"' || t.charAt(t.length() - 1) != '"') {
            return "#" + t;
        }
        final String body = t.substring(1, t.length() - 1);
        final StringBuilder raw = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            final char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                i++;
                final char n = body.charAt(i);
                if (n == 'n') {
                    raw.append('\n');
                } else if (n == 'r') {
                    raw.append('\r');
                } else if (n == 't') {
                    raw.append('\t');
                } else {
                    raw.append(n);
                }
            } else {
                raw.append(c);
            }
        }
        final StringBuilder out = new StringBuilder();
        for (final byte b : raw.toString().getBytes(StandardCharsets.UTF_8)) {
            final int u = b & 0xFF;
            if ((u >= 'a' && u <= 'z') || (u >= 'A' && u <= 'Z') || (u >= '0' && u <= '9')
                    || u == '-' || u == '_' || u == '.' || u == '~') {
                out.append((char) u);
            } else {
                out.append('%').append(String.format(Locale.ROOT, "%02X", u));
            }
        }
        return out.toString();
    }

    /** Widest comment line Credo's MaxLineLength (120) accepts, prefix included. */
    private static final int COMMENT_WIDTH = 120;

    /**
     * Renders spec prose as a block of {@code #} line comments. The string-literal
     * escaping the shared codegen applies (a quote or backslash preceded by a
     * backslash) is reversed, each source line becomes its own comment line, and
     * a line longer than Credo's 120-column limit is word-wrapped onto further
     * comment lines.
     */
    static String toLineComment(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return "#";
        }
        final String raw = text.replace("\\\"", "\"").replace("\\#{", "#{")
                .replace("\\\\", "\\");
        final List<String> out = new ArrayList<>();
        for (final String line : raw.strip().split("\\r?\\n", -1)) {
            final String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                out.add("#");
                continue;
            }
            final StringBuilder current = new StringBuilder("#");
            for (final String word : trimmed.split("\\s+", -1)) {
                if (current.length() > 1 && current.length() + 1 + word.length() > COMMENT_WIDTH) {
                    out.add(current.toString());
                    current.setLength(1);
                }
                current.append(' ').append(word);
            }
            out.add(current.toString());
        }
        return String.join("\n", out);
    }

    /**
     * Rewrites a single Elixir type descriptor into valid typespec form.
     *
     * <p>A descriptor that is already a stdlib/primitive type (it contains a
     * parenthesis like {@code integer()}/{@code String.t()}/{@code MapSet.t(_)},
     * is a {@code %{...}} map, or is {@code nil}/{@code term()}) is returned
     * verbatim. A list wrapper {@code [Inner]} is rewritten by recursing on its
     * inner descriptor. Anything else is a bare model alias and is qualified to
     * {@code <moduleName>.Models.<Alias>.t()}. An empty fragment yields the empty
     * string so {@code {{^returnType}}nil{{/returnType}}} fallbacks are
     * unaffected.
     */
    static String toModuleTypespec(@Nullable String rawType, String moduleName) {
        if (rawType == null) {
            return "";
        }
        final String t = rawType.trim();
        if (t.isEmpty()) {
            return "";
        }
        // List wrapper: [Inner] -> [<transformed Inner>]. This also covers
        // nested arrays such as [[integer()]].
        if (t.startsWith("[") && t.endsWith("]")) {
            return "[" + toModuleTypespec(t.substring(1, t.length() - 1), moduleName) + "]";
        }
        // Already a valid stdlib/primitive descriptor — leave untouched.
        // A parenthesis covers integer()/String.t()/Date.t()/MapSet.t(...);
        // a brace covers %{...} map descriptors; nil/term() are bare but valid.
        if (t.indexOf('(') >= 0 || t.indexOf('{') >= 0 || "nil".equals(t) || "term()".equals(t)) {
            return t;
        }
        // Bare PascalCase model alias — qualify to a struct typespec.
        return moduleName + ".Models." + t + ".t()";
    }

    /**
     * Maps a CodegenParameter to an Elixir typespec string suitable for
     * {@code @type t :: %__MODULE__{...}}. Optional fields are unioned with
     * {@code nil} so Dialyzer and editor tooling pick up the real shape.
     */
    private static String toElixirTypespec(CodegenParameter p, String moduleName) {
        final String base = elixirBaseType(p, moduleName);
        return p.required && !isRequiredNullableQuery(p) ? base : base + " | nil";
    }

    private static String elixirBaseType(CodegenParameter p, String moduleName) {
        if (p.isMap) {
            return "map()";
        }
        if (p.dataType == null || p.dataType.isEmpty()) {
            return "any()";
        }
        final String dt = p.dataType;
        // Elixir array container is rendered as [Inner] by the codegen.
        if (p.isArray && dt.startsWith("[") && dt.endsWith("]")) {
            return "list(" + elementTypespec(dt.substring(1, dt.length() - 1), moduleName) + ")";
        }
        if (p.isArray) {
            return "list()";
        }
        // typeMapping already returns Elixir-shaped strings for primitives
        // (String.t(), integer(), boolean(), float(), binary(), MapSet.t(),
        // map(), any()). Anything else is a model name — render it as the
        // fully-qualified Model.t(), which is the only form Dialyzer resolves.
        if (dt.endsWith(")") || "list".equals(dt)) {
            return "list".equals(dt) ? "list()" : dt;
        }
        return toModuleTypespec(dt, moduleName);
    }

    private static String elementTypespec(String inner, String moduleName) {
        final String t = inner.trim();
        if (t.isEmpty()) {
            return "any()";
        }
        return toModuleTypespec(t, moduleName);
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getReservedEnumMemberNames() {
        return Set.of("all_values", "ALL_VALUES");
    }
}

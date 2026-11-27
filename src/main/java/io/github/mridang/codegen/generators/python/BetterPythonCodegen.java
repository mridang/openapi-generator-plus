package io.github.mridang.codegen.generators.python;


import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.BarrelFileEmitter;
import io.github.mridang.codegen.generators.NamingConvention;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
 * Generates a Python 3.10+ API client that uses urllib3 for
 * HTTP transport and Pydantic v2 for model serialization.
 * All identifiers follow snake_case conventions enforced by
 * the Ruff formatter. Variable names, operation IDs, and
 * filenames all use {@code SNAKE_CASE} naming.
 */
@SuppressWarnings("unused")
public class BetterPythonCodegen extends AbstractBetterCodegen implements BarrelFileEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterPythonCodegen.class);


    /** An identifier inside a Python type annotation (dotted names kept whole). */
    private static final String OPTION_REPOSITORY_URL = "repositoryUrl";
    private static final String OPTION_HOMEPAGE_URL = "homepageUrl";
    private static final String OPTION_AUTHORS = "authors";
    private static final String OPTION_DOCUMENTATION_URL = "documentationUrl";
    private static final String OPTION_ISSUE_TRACKER_URL = "issueTrackerUrl";

    private static final Pattern TYPE_NAME_TOKEN =Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    /** Generic names an options annotation may use, imported from typing. */
    private static final Set<String> TYPING_NAMES = Set.of("List", "Dict", "Set", "Optional", "Any", "Union");

    /** Maps Python built-in datatype names to their required import statement. */
    private static final Map<String, String> TYPE_IMPORTS;

    static {
        final Map<String, String> imports = new HashMap<>();
        imports.put("date", "from datetime import date");
        // These datatypes are emitted module-qualified ("datetime.time",
        // "datetime.timedelta") by typeMapping and the object serializer, so
        // the module itself must be imported. A `from datetime import time`
        // would bind the bare name and leave `datetime.time` unresolved
        // (NameError) on every model that carries a time/duration field.
        imports.put("datetime.time", "import datetime");
        imports.put("datetime.timedelta", "import datetime");
        imports.put("Decimal", "from decimal import Decimal");
        imports.put("uuid.UUID", "import uuid");
        // Pydantic 2 native types
        imports.put("HttpUrl", "from pydantic import HttpUrl");
        imports.put("SecretStr", "from pydantic import SecretStr");
        imports.put("AwareDatetime", "from pydantic import AwareDatetime");
        // format: byte (and OAS 3.1 contentEncoding: base64) → Base64Bytes. The
        // key stays here so the primitive checks recognise it; the real import
        // is the package-qualified `<pkg>._types` alias set in
        // getPropertyTypeImportMap, because pydantic's own Base64Bytes rejects
        // raw bytes on construction and decodes malformed base64 leniently.
        imports.put("Base64Bytes", "from pydantic import Base64Bytes");
        imports.put("StrictInt", "from pydantic import StrictInt");
        imports.put("StrictStr", "from pydantic import StrictStr");
        imports.put("StrictBool", "from pydantic import StrictBool");
        imports.put("StrictFloat", "from pydantic import StrictFloat");
        TYPE_IMPORTS = Map.copyOf(imports);
    }

    protected String packageName = "openapi_client";
    protected String packageVersion = "1.0.0";

    /**
     * Initializes the Python codegen with type mappings,
     * language primitives, and reserved words. Clears
     * inherited defaults and configures the template
     * directory for Python-specific Mustache templates.
     */
    public BetterPythonCodegen() {
        outputFolder = "generated-code/python";
        embeddedTemplateDir = templateDir = "templates/python";

        modelTemplateFiles.put("models/model.mustache", ".py");
        apiTemplateFiles.put("api/api.mustache", ".py");

        // Item 8: pydantic 2 strict primitives — kills lax JSON coercion
        typeMapping.put("integer", "StrictInt");
        typeMapping.put("long", "StrictInt");
        // `format: float`/`double` → LaxFloat, a StrictFloat that additionally
        // accepts an integral JSON number (`5` for a double). JSON has one
        // number type, so a server may legitimately emit `5` for a double
        // field; bare StrictFloat rejects it because the parsed Python value
        // is an `int`. LaxFloat widens only that lossless `int -> float` case
        // and keeps the rest of StrictFloat's strictness (rejects str/bool).
        typeMapping.put("float", "LaxFloat");
        typeMapping.put("double", "LaxFloat");
        // A bare `type: number` (no `format`) is a decimal-precision surface
        // per the typed-everywhere policy: decimal.Decimal in Python (matching
        // BigDecimal in Java/Kotlin and decimal.js Decimal in Node). `format:
        // float`/`format: double` keep StrictFloat above for IEEE-754 fields.
        // JsonNumber is the branded Decimal alias from `<pkg>._types`: it
        // decodes to a full-precision Decimal but serializes UNQUOTED (a JSON
        // number, `1.5` not `"1.5"`) via a json-only PlainSerializer, because
        // the OpenAPI `type: number` is a JSON number on the wire. A plain
        // `Decimal` would emit a quoted string, violating the spec and
        // diverging from the SDKs that put a bare number on the wire.
        typeMapping.put("number", "JsonNumber");
        typeMapping.put("boolean", "StrictBool");
        typeMapping.put("string", "StrictStr");
        typeMapping.put("byte", "bytes");
        typeMapping.put("binary", "bytes");
        typeMapping.put("ByteArray", "bytes");
        typeMapping.put("date", "date");
        // Item 5: format: date-time → AwareDatetime (rejects naive datetimes)
        typeMapping.put("DateTime", "AwareDatetime");
        typeMapping.put("time", "datetime.time");
        // format: duration → a pydantic Annotated alias (datetime.timedelta +
        // Before/PlainSerializer) so model_dump_json emits the protobuf-JSON
        // form "3600s" instead of pydantic's default ISO-8601 "PT1H". The
        // alias and its helpers live in the dependency-free `_duration` module.
        typeMapping.put("duration", "ProtobufDuration");
        typeMapping.put("UUID", "uuid.UUID");
        // Item 1: format: uri → UrlStr, a `str` whose AfterValidator asserts
        // the value is an absolute RFC 3986 URI (any scheme) but returns it verbatim.
        // pydantic.HttpUrl validates AND normalizes (lowercases host, strips a
        // default port, appends a trailing slash), which makes the JSON
        // round-trip lossy — `https://example.com` would re-encode as
        // `https://example.com/`. UrlStr keeps the wire value byte-identical
        // while still rejecting non-URLs. (uri-reference/uri-template are
        // downgraded to StrictStr in postProcessModelProperty.)
        typeMapping.put("URI", "UrlStr");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "object");
        typeMapping.put("array", "List");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "Dict");
        typeMapping.put("file", "bytes");
        typeMapping.put("File", "bytes");
        typeMapping.put("decimal", "Decimal");
        // Native address types: ipaddress.IPv4Address / IPv6Address wrapped in
        // the `<pkg>._types` Annotated aliases that gate the text strictly.
        typeMapping.put("ipv4", "Ipv4Address");
        typeMapping.put("ipv6", "Ipv6Address");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "float", "bool", "str", "bytes", "object",
                                "date", "datetime", "datetime.time", "datetime.timedelta",
                                // ProtobufDuration is the Annotated alias for
                                // format: duration; treated as a primitive so the
                                // generic-import machinery doesn't try to emit
                                // `from <pkg>.models.ProtobufDuration import ...`.
                                // Its real import comes from getPropertyTypeImportMap.
                                "ProtobufDuration",
                                // LaxFloat (format: float/double) and UrlStr
                                // (format: uri) are package-qualified Annotated
                                // aliases living in `<pkg>._types`; treat them
                                // as primitives so the generic-import machinery
                                // does not try to emit a `<pkg>.models.X` import.
                                // Their real imports come from
                                // getPropertyTypeImportMap. JsonNumber (bare
                                // type: number) is likewise an `<pkg>._types`
                                // alias and belongs in this group.
                                "LaxFloat", "UrlStr", "JsonNumber",
                                "Ipv4Address", "Ipv6Address", "DecimalStr",
                                // OffsetDateTime (format: date-time) is the
                                // `<pkg>._types` Annotated alias that wraps
                                // AwareDatetime with a json-only PlainSerializer
                                // pinning `.SSS+00:00`; treat it as a primitive
                                // so the generic-import machinery does not emit
                                // `<pkg>.models.OffsetDateTime`. Its real import
                                // comes from getPropertyTypeImportMap.
                                "OffsetDateTime",
                                // TimeOfDay (format: time) is the `<pkg>._types`
                                // alias over datetime.time that writes an RFC 3339
                                // partial-time; its import comes from
                                // getPropertyTypeImportMap.
                                "TimeOfDay",
                                // PasswordStr (format: password) is the
                                // `<pkg>._types` SecretStr alias that writes the
                                // real value on the JSON wire; its import comes
                                // from getPropertyTypeImportMap.
                                "PasswordStr",
                                "uuid.UUID", "List", "Dict", "Set",
                                "Tuple", "Optional",
                                // Pydantic 2 native types treated as primitives so the
                                // generic-import machinery does not try to emit
                                // `from petstore_client.models.HttpUrl import HttpUrl`.
                                "HttpUrl", "SecretStr", "AwareDatetime",
                                "Base64Bytes",
                                "StrictInt", "StrictStr", "StrictBool", "StrictFloat",
                                "Decimal", "JsonNumber"));

        reservedWords = loadReservedWords("/reserved-words/python.txt");

        // Only carry an extras bag (`additional_properties` + capture
        // validator) on models whose schema *explicitly* declares
        // `additionalProperties`, matching every other SDK in this repo.
        // With this set to `true` (the upstream default), `isAdditionalProp-
        // ertiesTrue` is true only for those models (e.g. Metadata) and false
        // for plain object models (Pet, Order, Tag, Category) and for strict
        // models (StrictTag: unevaluatedProperties:false), which then get
        // pure `extra='forbid'` rejection with no contradictory capture bag.
        this.setDisallowAdditionalPropertiesIfNotPresent(true);
        this.setLegacyDiscriminatorBehavior(false);

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated package (default: 1.0.0).").defaultValue("1.0.0"));
        cliOptions.add(CliOption.newString(OPTION_REPOSITORY_URL,
                "Source repository URL, written to the manifest and README."));
        cliOptions.add(CliOption.newString(OPTION_HOMEPAGE_URL,
                "Project homepage URL, written to the manifest and README."));
        cliOptions.add(CliOption.newString(OPTION_AUTHORS,
                "Comma-separated author names, written to the manifest."));
        cliOptions.add(CliOption.newString(OPTION_DOCUMENTATION_URL,
                "Documentation URL, written to the manifest and README (optional)."));
        cliOptions.add(CliOption.newString(OPTION_ISSUE_TRACKER_URL,
                "Issue tracker URL, written to the manifest and README (optional)."));
    }

    /** The generated per-scheme authenticators that read an environment variable, for the unit test. */
    private final List<Map<String, Object>> perSchemeAuthEntries = new ArrayList<>();

    /** Python ships the OpenTelemetry call hook adapter, built on the optional {@code opentelemetry-api} package. */
    @Override
    protected boolean hasOpenTelemetryAdapter() {
        return true;
    }

    /** The names the generated operations add to their own signature, which a path or body parameter must not reuse (F1055). */
    @Override
    protected Set<String> getInjectedArgumentNames() {
        return Set.of(
                "self", "options", "server", "request_content_type", "timeout", "headers", "max_retries");
    }

    /** A parameter named like an injected argument gets this suffix, snake-cased like the rest. */
    @Override
    protected String getInjectedArgumentSuffix() {
        return "_param";
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "python-plus";
    }

    /**
     * Escapes a reserved word with a <em>trailing</em> underscore (PEP 8
     * convention: {@code class_}, {@code and_}), overriding the base
     * generator's leading-underscore scheme. A leading underscore makes the
     * attribute private as far as pydantic v2 is concerned — it refuses to
     * register a model field whose name starts with {@code _}, so a property
     * literally named {@code and}/{@code or}/{@code not} would crash at import
     * time. The wire name is preserved separately via the field's
     * {@code alias}, so only the Python attribute name changes.
     */
    @Override
    public String escapeReservedWord(String name) {
        return name + "_";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Python client with pydantic models.";
    }

    /** {@inheritDoc} */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.PYTHON;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "test/fixtures";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "spec";
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
        return NamingConvention.UPPER_SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "python:3-slim@sha256:c845af9399020c7e562969a13689e929074a10fd057acd1b1fad06a2fb068e97";
    }

    /**
     * {@inheritDoc}
     *
     * <p>The import-pruning pass is what lets the model, API and errors
     * modules ship without a {@code # ruff: noqa: F401, F811} pragma. Those
     * modules emit a fixed import block — every strict scalar, every typing
     * container, every referenced model — because a Mustache template cannot
     * know which of them a given schema actually uses, and the footer
     * {@code imports} block re-imports names the header already brought in.
     * Rather than guess the set in Java (and risk a NameError when the guess
     * is short), the block is emitted wide and ruff deletes exactly the names
     * nothing in the rendered file references. Ruff resolves deferred
     * annotations, so a name used only inside a {@code from __future__ import
     * annotations} annotation is kept.
     *
     * <p>{@code --exit-zero} so a finding ruff cannot fix does not abort
     * generation before {@code ruff format} runs; the linting spec is the gate
     * that fails on anything left over.
     */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            "PIP_CACHE_DIR=/cache/pip pip install --quiet \"ruff>=0.15,<0.16\"",
            "ruff check --select F401,F811 --fix --exit-zero --quiet .",
            "ruff format ."
        };
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "set[";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayContainerPattern() {
        return "^[Ll]ist\\[";
    }

    /**
     * Derives the per-operation {@code hasMultipleConsumes} signal that the api
     * template uses to decide whether to emit the optional request-content-type
     * selector.
     *
     * <p>An operation may declare more than one request {@code Content-Type}
     * (e.g. {@code setPetAvatar} declares {@code image/jpeg}, {@code image/png}
     * and {@code application/json}). The generated method historically pinned the
     * request header to {@code effectiveConsumes} — the first declared type — so
     * the remaining declared types were unreachable. When more than one type is
     * declared, the template exposes an OPTIONAL {@code request_content_type}
     * keyword argument that overrides the header; when it is omitted the method
     * keeps sending the first declared type unchanged, so existing call sites
     * compile and behave exactly as before.
     *
     * <p>Mustache cannot test "{@code consumes} has more than one element", so we
     * derive that boolean here, mirroring the language-scoped pattern used by the
     * Go generator. This is a plain vendor-extension flag (not an {@code x-*}
     * extension and not a shared cross-language decorator), scoped to the Python
     * generator only.
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
        final Map<String, Object> operations =
                (Map<String, Object>) objs.get("operations");
        final Set<String> scalarImports = new TreeSet<>();
        if (operations != null) {
            final List<CodegenOperation> ops =
                    (List<CodegenOperation>) operations.get("operation");
            if (ops != null) {
                for (final CodegenOperation op : ops) {
                    if (op.vendorExtensions == null) {
                        op.vendorExtensions = new HashMap<>();
                    }
                    addSchemeLiterals(op);
                    final boolean hasMultipleConsumes =
                            op.consumes != null && op.consumes.size() > 1;
                    op.vendorExtensions.put(
                            "hasMultipleConsumes", hasMultipleConsumes);
                    markBinaryUpload(op);
                    for (final CodegenParameter param : op.allParams) {
                        if (param.isDate) {
                            scalarImports.add("from datetime import date");
                        }
                        /* uuid.UUID, datetime.time and datetime.timedelta are
                         * lowercase dotted names the superclass import cleanup
                         * drops just like date. */
                        if (param.dataType != null) {
                            final java.util.regex.Matcher names =
                                    TYPE_NAME_TOKEN.matcher(param.dataType);
                            while (names.find()) {
                                final String name = names.group();
                                if (name.indexOf('.') > 0 && TYPE_IMPORTS.containsKey(name)) {
                                    scalarImports.add(TYPE_IMPORTS.get(name));
                                }
                            }
                        }
                    }
                }
            }
        }
        final OperationsMap processed =
                super.postProcessOperationsWithModels(objs, allModels);
        /* After the shared pass: it is the one that fills the operation decorators. */
        if (operations != null) {
            final List<CodegenOperation> marked = (List<CodegenOperation>) operations.get("operation");
            if (marked != null) {
                for (final CodegenOperation op : marked) {
                    markBinaryStream(op);
                }
            }
        }
        if (hasBinaryStream(operations)) {
            scalarImports.add("from " + packageName + ".byte_stream import ByteStream");
        }
        addPagedImports(operations, scalarImports);
        if (hasBinaryUpload(operations)) {
            scalarImports.add("from " + packageName + ".body_source import BodySource");
        }
        if (hasStream(operations, "streamsSse")) {
            scalarImports.add("from " + packageName + ".sse_stream import SseStream");
        }
        if (hasStream(operations, "streamsJsonLines")) {
            scalarImports.add("from " + packageName + ".json_lines_stream import JsonLinesStream");
        }
        if (hasStream(operations, "streamsSse") || hasStream(operations, "streamsJsonLines")) {
            scalarImports.add("from " + packageName + ".stream_options import StreamOptions");
            addStreamItemImports(operations, scalarImports);
        }
        // A format:date parameter (query, header, path, or form) types the
        // method signature as `date`, but openapi-generator only collects
        // model imports from body/response schemas, never from scalar
        // parameters. Without this, `pet_api.py` would reference `date`
        // without importing it (mypy name-defined / ruff F821). The options
        // file gets the import for free via its model reference; the api file
        // needs it added explicitly here. This runs *after* super, because the
        // superclass's cleanupBadImports pass strips any import whose class
        // name starts lowercase (a heuristic for bogus primitive imports), and
        // `date` is a legitimate lowercase class name it would otherwise drop.
        // Deduped against imports the model machinery already added.
        final List<Map<String, String>> imports =
                (List<Map<String, String>>) processed.get("imports");
        if (imports != null) {
            for (final String scalarImport : scalarImports) {
                final boolean alreadyPresent = imports.stream()
                        .anyMatch(m -> scalarImport.equals(m.get("import")));
                if (!alreadyPresent) {
                    final Map<String, String> entry = new HashMap<>();
                    entry.put("import", scalarImport);
                    imports.add(entry);
                }
            }
        }
        return processed;
    }

    /**
     * A raw binary request body is a {@code BodySource}: the operation takes it, in every
     * signature, instead of {@code bytes}. It runs before the shared pass, which copies the body
     * type into the signature arguments. Multipart and every other body kind keep the type the
     * spec maps.
     */
    static void markBinaryUpload(final CodegenOperation op) {
        final CodegenParameter body = op.bodyParam;
        if (body == null
                || !body.isBinary
                || op.isMultipart
                || (op.formParams != null && !op.formParams.isEmpty())) {
            return;
        }
        body.dataType = "BodySource";
        body.baseType = "BodySource";
        body.datatypeWithEnum = "BodySource";
        if (op.allParams != null) {
            for (final CodegenParameter p : op.allParams) {
                if (p.isBodyParam) {
                    p.dataType = "BodySource";
                    p.baseType = "BodySource";
                    p.datatypeWithEnum = "BodySource";
                }
            }
        }
        op.vendorExtensions.put("uploadsBodySource", true);
    }

    /**
     * What the {@code <op>_pages} methods of an api file need: the {@code Paginator}, the item
     * model (the operation's own imports cover the page model, not the model of its items) and,
     * for a request that carries a cursor (token, id, offset or page), {@code dataclasses.replace},
     * which gives each page its own cursor.
     */
    @SuppressWarnings("unchecked")
    private void addPagedImports(
            @Nullable final Map<String, Object> operations, final Set<String> imports) {
        if (operations == null || !(operations.get("operation") instanceof List)) {
            return;
        }
        for (final CodegenOperation op : (List<CodegenOperation>) operations.get("operation")) {
            final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
            if (!(decorators instanceof Map)
                    || !Boolean.TRUE.equals(((Map<String, Object>) decorators).get("hasPagesMethod"))) {
                continue;
            }
            imports.add("from " + packageName + ".paginator import Paginator");
            final Object pagination = ((Map<String, Object>) decorators).get("pagination");
            if (!(pagination instanceof Map)) {
                continue;
            }
            final Map<String, Object> p = (Map<String, Object>) pagination;
            if (!Boolean.TRUE.equals(p.get("isUrlPaged"))) {
                imports.add("from dataclasses import replace as _replace_options");
            }
            final Object model = p.get("itemModel");
            if (model != null) {
                imports.add("from " + modelPackage + "." + toModelFilename(model.toString())
                        + " import " + model);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasBinaryUpload(@Nullable final Map<String, Object> operations) {
        if (operations == null || !(operations.get("operation") instanceof List)) {
            return false;
        }
        for (final CodegenOperation op : (List<CodegenOperation>) operations.get("operation")) {
            if (op.vendorExtensions != null
                    && Boolean.TRUE.equals(op.vendorExtensions.get("uploadsBodySource"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A binary success response is returned as a {@code ByteStream} by the one operation method
     * (D119), so the return type becomes {@code ByteStream} and the decorator {@code streamsBinary}
     * tells {@code api.mustache} to call {@code _invoke_api_for_stream}.
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
        final boolean jsonLines = "jsonlines".equals(kind) && Boolean.TRUE.equals(d.get("streamOnly"));
        d.put("streamsBinary", binary);
        d.put("streamsSse", sse);
        d.put("streamsJsonLines", jsonLines);
        if (binary) {
            op.returnType = "ByteStream";
            op.returnBaseType = "ByteStream";
        }
        if (sse || jsonLines) {
            final boolean text = sse && Boolean.TRUE.equals(d.get("streamEventIsText"));
            final Object declared = d.get("streamItemType");
            final String item = text || declared == null ? "str" : declared.toString();
            final boolean typed = !"str".equals(item) && !"StrictStr".equals(item);
            d.put("streamTyped", typed);
            d.put("streamItemPython", typed ? item : "str");
            d.put("streamMethodPython", op.operationId + "_stream");
            op.returnType = (sse ? "SseStream[" : "JsonLinesStream[") + (typed ? item : "str") + "]";
            op.returnBaseType = sse ? "SseStream" : "JsonLinesStream";
        }
    }

    @SuppressWarnings("unchecked")
    private void addStreamItemImports(
            @Nullable final Map<String, Object> operations, final Set<String> imports) {
        if (operations == null || !(operations.get("operation") instanceof List)) {
            return;
        }
        for (final CodegenOperation op : (List<CodegenOperation>) operations.get("operation")) {
            final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
            if (decorators instanceof Map
                    && Boolean.TRUE.equals(((Map<String, Object>) decorators).get("streamTyped"))) {
                final String item = (String) ((Map<String, Object>) decorators).get("streamItemPython");
                final java.util.regex.Matcher names = TYPE_NAME_TOKEN.matcher(item);
                while (names.find()) {
                    final String token = names.group();
                    if (TYPING_NAMES.contains(token)) {
                        imports.add("from typing import " + token);
                    } else if (TYPE_IMPORTS.containsKey(token)) {
                        imports.add(TYPE_IMPORTS.get(token));
                    } else if (Character.isUpperCase(token.charAt(0))
                            && token.indexOf('.') < 0
                            && !languageSpecificPrimitives.contains(token)) {
                        imports.add(
                                "from " + modelPackage + "." + toModelFilename(token) + " import " + token);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasBinaryStream(@Nullable final Map<String, Object> operations) {
        return hasStream(operations, "streamsBinary");
    }

    @SuppressWarnings("unchecked")
    private static boolean hasStream(
            @Nullable final Map<String, Object> operations, final String flag) {
        if (operations == null || !(operations.get("operation") instanceof List)) {
            return false;
        }
        for (final CodegenOperation op : (List<CodegenOperation>) operations.get("operation")) {
            final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
            if (decorators instanceof Map
                    && Boolean.TRUE.equals(((Map<String, Object>) decorators).get(flag))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands the README and SKILLS the first operation that returns a {@code ByteStream}, as
     * {@code binaryStreamOperation} (with {@code binaryStreamGroup}, its operations map,
     * {@code hasBinaryStreamOperation} and {@code binaryStreamExample}, the imports, typed
     * parameters and call arguments of a function that calls it), so the Streaming example calls a
     * real operation of the spec under generation. Nothing is rendered when the spec has none.
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
            final String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            objs.put("has" + cap + "Operation", found != null);
            if (found != null) {
                objs.put(name + "Operation", found);
                objs.put(name + "Group", group);
                objs.put(name + "Example", binaryStreamExample(found));
            }
        }
        return super.postProcessSupportingFileData(objs);
    }

    /**
     * The pieces of the Streaming example for {@code op}: the import lines its parameter types
     * need ({@code imports}), the typed parameters of the function ({@code parameters}) and the
     * arguments of the call ({@code arguments}). Only what the caller must pass is included: the
     * path parameters, a required body and a required Options object.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> binaryStreamExample(final CodegenOperation op) {
        final Object decorators = op.vendorExtensions.get("op");
        final Object args =
                decorators instanceof Map
                        ? ((Map<String, Object>) decorators).get("signatureArgsNoServer")
                        : null;
        final TreeSet<String> imports = new TreeSet<>();
        final TreeSet<String> typing = new TreeSet<>();
        final List<String> parameters = new ArrayList<>();
        final List<String> arguments = new ArrayList<>();
        final List<Map<String, Object>> signature =
                args instanceof List ? (List<Map<String, Object>>) args : List.of();
        for (final Map<String, Object> arg : signature) {
            if (Boolean.TRUE.equals(arg.get("nullable"))) {
                continue;
            }
            final String dataType = (String) arg.get("dataType");
            final String name = (String) arg.get("paramName");
            parameters.add(name + ": " + dataType);
            arguments.add(name);
            if (Boolean.TRUE.equals(arg.get("isOptions"))) {
                imports.add("from " + packageName + ".api.options import " + dataType);
                continue;
            }
            final java.util.regex.Matcher names = TYPE_NAME_TOKEN.matcher(dataType);
            while (names.find()) {
                final String token = names.group();
                if (TYPING_NAMES.contains(token)) {
                    typing.add(token);
                } else if (TYPE_IMPORTS.containsKey(token)) {
                    imports.add(TYPE_IMPORTS.get(token));
                } else if (Character.isUpperCase(token.charAt(0))
                        && token.indexOf('.') < 0
                        && !languageSpecificPrimitives.contains(token)) {
                    imports.add(
                            "from " + modelPackage + "." + toModelFilename(token) + " import " + token);
                }
            }
        }
        if (!typing.isEmpty()) {
            imports.add("from typing import " + String.join(", ", typing));
        }
        final Map<String, Object> example = new HashMap<>();
        example.put("imports", new ArrayList<>(imports));
        example.put("parameters", String.join(", ", parameters));
        example.put("arguments", String.join(", ", arguments));
        example.put("hasParameters", !parameters.isEmpty());
        return example;
    }

    /**
     * Adds {@code schemeLiteral} (the scheme id ready for a double-quoted literal) to every entry
     * of the operation's ordered security schemes (F857), so the generated call can name them.
     */
    @SuppressWarnings("unchecked")
    private static void addSchemeLiterals(final CodegenOperation op) {
        final Object decorator = op.vendorExtensions.get("op");
        if (!(decorator instanceof Map)) {
            return;
        }
        final Object order = ((Map<String, Object>) decorator).get("securitySchemeOrder");
        if (!(order instanceof List)) {
            return;
        }
        for (final Object entry : (List<Object>) order) {
            final Map<String, Object> scheme = (Map<String, Object>) entry;
            scheme.put(
                    "schemeLiteral",
                    pythonStringBody(Objects.requireNonNull((String) scheme.get("schemeId"))));
        }
        final Object alternatives = ((Map<String, Object>) decorator).get("securityAlternatives");
        if (alternatives instanceof List) {
            for (final Object alternative : (List<Object>) alternatives) {
                final Object schemes = ((Map<String, Object>) alternative).get("schemes");
                if (!(schemes instanceof List)) {
                    continue;
                }
                for (final Object entry : (List<Object>) schemes) {
                    final Map<String, Object> scheme = (Map<String, Object>) entry;
                    scheme.put(
                            "schemeLiteral",
                            pythonStringBody(
                                    Objects.requireNonNull((String) scheme.get("schemeId"))));
                }
            }
        }
    }

    /**
     * Every operation gets an Options object, so even one with no parameters and no security
     * has the slot for the per-call timeout, headers and retry budget (F827).
     */
    @Override
    protected boolean mintsOptionsForEveryOperation() {
        return true;
    }

    /**
     * Takes the environment-variable prefix the shared pass computed, so the client's own
     * variables and those of the per-scheme authenticators share one prefix (F862).
     */
    @Override
    public void processOpenAPI(OpenAPI openAPI) {
        super.processOpenAPI(openAPI);
        final Object shared = additionalProperties.get("envVarPrefix");
        if (shared != null && !shared.toString().isBlank()) {
            additionalProperties.put("envPrefix", shared.toString());
        }
        additionalProperties.put("clientSchemes", pythonClientSchemes(sharedClientSchemes()));
    }

    /** The shared per-scheme data of the client (F857), as the shared pass recorded it. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sharedClientSchemes() {
        final Object schemes = additionalProperties.get("clientSchemes");
        return schemes == null ? List.of() : (List<Map<String, Object>>) schemes;
    }

    /**
     * Names the client's per-scheme credentials the Python way (F857): the keyword argument is
     * the scheme id in snake case, and gets a trailing underscore when it is a Python keyword
     * or would shadow one of the client's own arguments, and two ids that read the same in snake
     * case stay apart. {@code schemeLiteral} is the id ready for a double-quoted literal.
     *
     * @param shared the shared entries ({@code schemeId}, {@code paramName}, {@code className})
     * @return the same entries with the Python {@code paramName} and a {@code schemeLiteral}
     */
    List<Map<String, Object>> pythonClientSchemes(final List<Map<String, Object>> shared) {
        final List<Map<String, Object>> out = new ArrayList<>();
        final Set<String> taken = new HashSet<>();
        for (final Map<String, Object> scheme : shared) {
            final Map<String, Object> entry = new LinkedHashMap<>(scheme);
            final String schemeId = Objects.requireNonNull((String) scheme.get("schemeId"));
            String paramName = schemeParamName(schemeId);
            while (!taken.add(paramName)) {
                paramName = paramName + "_";
            }
            entry.put("paramName", paramName);
            entry.put("schemeLiteral", pythonStringBody(schemeId));
            out.add(entry);
        }
        return out;
    }

    /** The client arguments a scheme credential must not shadow. */
    private static final Set<String> CLIENT_ARGUMENT_NAMES =
            Set.of("self", "authenticator", "transport_options", "pool_manager", "call_hook");

    /**
     * The keyword argument that carries the credential of a security scheme.
     *
     * @param schemeId the scheme id in the spec
     * @return the id as a Python identifier in snake case, with a trailing underscore when it is
     *     reserved or taken
     */
    String schemeParamName(final String schemeId) {
        String snake = NamingConvention.SNAKE_CASE.apply(schemeId).replaceAll("[^A-Za-z0-9_]", "_");
        if (snake.isEmpty() || Character.isDigit(snake.charAt(0))) {
            snake = "scheme_" + snake;
        }
        return isReservedWord(snake) || CLIENT_ARGUMENT_NAMES.contains(snake) ? snake + "_" : snake;
    }

    /**
     * Resolves user-supplied options and registers all
     * supporting files for the Python package structure.
     * Sets up the package layout including models, API
     * classes, exceptions, auth, and configuration modules.
     */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String pkg = Optional.ofNullable((String) additionalProperties.get("packageName"))
                .orElse(packageName);
        final String packagePath = pkg.replace('.', File.separatorChar);
        final String apiPath = Path.of(packagePath, "api").toString();
        final String modelPath = Path.of(packagePath, "models").toString();
        final String errorsPath = Path.of(packagePath, "errors").toString();
        final String authPath = Path.of(packagePath, "auth").toString();
        final List<SupportingFileSpec> specs = new ArrayList<>(List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("models/__init__.mustache", modelPath, "__init__.py"),
            new SupportingFileSpec("api/__init__.mustache", apiPath, "__init__.py"),
            new SupportingFileSpec("__init__.mustache", packagePath, "__init__.py"),
            new SupportingFileSpec("py_typed.mustache", packagePath, "py.typed"),
            new SupportingFileSpec("api_client.mustache", packagePath, "api_client.py"),
            new SupportingFileSpec("default_api_client.mustache", packagePath, "default_api_client.py"),
            new SupportingFileSpec("api_response.mustache", packagePath, "api_http_response.py"),
            new SupportingFileSpec("api_result.mustache", packagePath, "api_result.py"),
            new SupportingFileSpec("api_stream_response.mustache", packagePath, "api_stream_response.py"),
            new SupportingFileSpec("body_source.mustache", packagePath, "body_source.py"),
            new SupportingFileSpec("paginator.mustache", packagePath, "paginator.py"),
            new SupportingFileSpec("page.mustache", packagePath, "page.py"),
            new SupportingFileSpec("link_header.mustache", packagePath, "link_header.py"),
            new SupportingFileSpec("byte_stream.mustache", packagePath, "byte_stream.py"),
            new SupportingFileSpec("event_stream.mustache", packagePath, "event_stream.py"),
            new SupportingFileSpec("server_sent_event.mustache", packagePath, "server_sent_event.py"),
            new SupportingFileSpec("stream_options.mustache", packagePath, "stream_options.py"),
            new SupportingFileSpec("json_lines_stream.mustache", packagePath, "json_lines_stream.py"),
            new SupportingFileSpec("sse_stream.mustache", packagePath, "sse_stream.py"),
            new SupportingFileSpec("base_api.mustache", apiPath, "base_api.py"),
            new SupportingFileSpec("configuration.mustache", packagePath, "configuration.py"),
            new SupportingFileSpec("errors.mustache", errorsPath, "__init__.py"),
            new SupportingFileSpec("errors/client_exception.mustache", errorsPath, "client_exception.py"),
            new SupportingFileSpec("errors/server_exception.mustache", errorsPath, "server_exception.py"),
            new SupportingFileSpec("errors/bad_request_exception.mustache", errorsPath, "bad_request_exception.py"),
            new SupportingFileSpec("errors/unauthorized_exception.mustache", errorsPath, "unauthorized_exception.py"),
            new SupportingFileSpec("errors/forbidden_exception.mustache", errorsPath, "forbidden_exception.py"),
            new SupportingFileSpec("errors/not_found_exception.mustache", errorsPath, "not_found_exception.py"),
            new SupportingFileSpec("errors/conflict_exception.mustache", errorsPath, "conflict_exception.py"),
            new SupportingFileSpec("errors/payment_required_exception.mustache", errorsPath, "payment_required_exception.py"),
            new SupportingFileSpec("errors/method_not_allowed_exception.mustache", errorsPath, "method_not_allowed_exception.py"),
            new SupportingFileSpec("errors/request_timeout_exception.mustache", errorsPath, "request_timeout_exception.py"),
            new SupportingFileSpec("errors/gone_exception.mustache", errorsPath, "gone_exception.py"),
            new SupportingFileSpec("errors/payload_too_large_exception.mustache", errorsPath, "payload_too_large_exception.py"),
            new SupportingFileSpec("errors/unsupported_media_type_exception.mustache", errorsPath, "unsupported_media_type_exception.py"),
            new SupportingFileSpec("errors/locked_exception.mustache", errorsPath, "locked_exception.py"),
            new SupportingFileSpec("errors/not_implemented_exception.mustache", errorsPath, "not_implemented_exception.py"),
            new SupportingFileSpec("errors/bad_gateway_exception.mustache", errorsPath, "bad_gateway_exception.py"),
            new SupportingFileSpec("errors/service_unavailable_exception.mustache", errorsPath, "service_unavailable_exception.py"),
            new SupportingFileSpec("errors/gateway_timeout_exception.mustache", errorsPath, "gateway_timeout_exception.py"),
            new SupportingFileSpec("errors/unprocessable_entity_exception.mustache", errorsPath, "unprocessable_entity_exception.py"),
            new SupportingFileSpec("errors/too_many_requests_exception.mustache", errorsPath, "too_many_requests_exception.py"),
            new SupportingFileSpec("errors/internal_server_error_exception.mustache", errorsPath, "internal_server_error_exception.py"),
            new SupportingFileSpec("errors/network_exception.mustache", errorsPath, "network_exception.py"),
            new SupportingFileSpec("errors/network_timeout_exception.mustache", errorsPath, "network_timeout_exception.py"),
            new SupportingFileSpec("errors/stream_exception.mustache", errorsPath, "stream_exception.py"),
            new SupportingFileSpec("errors/response_too_large_exception.mustache", errorsPath, "response_too_large_exception.py"),
            new SupportingFileSpec("errors/pagination_exception.mustache", errorsPath, "pagination_exception.py"),
            new SupportingFileSpec("errors/serialization_exception.mustache", errorsPath, "serialization_exception.py"),
            new SupportingFileSpec("errors/oauth2_server_exception.mustache", errorsPath, "oauth2_server_exception.py"),
            new SupportingFileSpec("errors/oauth2_token_exception.mustache", errorsPath, "oauth2_token_exception.py"),
            new SupportingFileSpec("_duration.mustache", packagePath, "_duration.py"),
            new SupportingFileSpec("_types.mustache", packagePath, "_types.py"),
            new SupportingFileSpec("_constraint_validator.mustache", packagePath, "_constraint_validator.py"),
            new SupportingFileSpec("object_serializer.mustache", packagePath, "object_serializer.py"),
            new SupportingFileSpec("value_serializer.mustache", packagePath, "value_serializer.py"),
            new SupportingFileSpec("header_selector.mustache", packagePath, "header_selector.py"),
            new SupportingFileSpec("trace_context_util.mustache", packagePath, "trace_context_util.py"),
            new SupportingFileSpec("call_hook.mustache", packagePath, "call_hook.py"),
            new SupportingFileSpec("opentelemetry_call_hook.mustache", packagePath, "opentelemetry_call_hook.py"),
            new SupportingFileSpec("transport_options.mustache", packagePath, "transport_options.py"),
            new SupportingFileSpec("server_configuration.mustache", packagePath, "server_configuration.py"),
            new SupportingFileSpec("servers.mustache", packagePath, "servers.py"),
            new SupportingFileSpec("auth/__init__.mustache", authPath, "__init__.py"),
            new SupportingFileSpec("authenticator.mustache", authPath, "authenticator.py"),
            new SupportingFileSpec("auth/base_authenticator.mustache", authPath, "base_authenticator.py"),
            new SupportingFileSpec("pyproject_toml.mustache", "", "pyproject.toml"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore")
        ));
        if (generateExamples) {
            specs.add(new SupportingFileSpec("examples/quickstart.mustache", "examples", "quickstart.py"));
        }
        return specs;
    }

    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion =
                getPropertyOrDefault(CodegenConstants.PACKAGE_VERSION, packageVersion);
        /* The product token is the published PyPI distribution name (the
         * pyproject name), not the import package, so the UA names what users
         * install. */
        final String distributionName = getPropertyOrDefault("projectName", packageName);
        additionalProperties.put(
                "userAgentDefault", distributionName + "/" + packageVersion + " (python)");

        /* The environment-variable prefix of the credentials the client reads
         * once at construction ({PREFIX}_ACCESS_TOKEN, {PREFIX}_BASE_URL). The shared
         * prefix (the envVarPrefix option, else the API title) replaces this fallback in
         * processOpenAPI, so the client and every per-scheme authenticator agree. */
        additionalProperties.put(
                "envPrefix", NamingConvention.SNAKE_CASE.apply(distributionName).toUpperCase(Locale.ROOT));
        additionalProperties.put("perSchemeAuthEntries", perSchemeAuthEntries);

        /* Package metadata options: the manifest and the README show only the
         * ones that were given. */
        final List<Map<String, String>> authorsList = new ArrayList<>();
        final Object authorsOption = additionalProperties.get(OPTION_AUTHORS);
        if (authorsOption != null) {
            for (final String author : Splitter.on(',').split(String.valueOf(authorsOption))) {
                if (!author.trim().isEmpty()) {
                    authorsList.add(Map.of("name", author.trim().replace("\\", "\\\\").replace("\"", "\\\"")));
                }
            }
        }
        additionalProperties.put("authorsList", authorsList);
        additionalProperties.put("hasAuthors", !authorsList.isEmpty());
        boolean hasProjectUrls = false;
        for (final String urlOption : new String[] {
            OPTION_REPOSITORY_URL, OPTION_HOMEPAGE_URL, OPTION_DOCUMENTATION_URL, OPTION_ISSUE_TRACKER_URL}) {
            final Object value = additionalProperties.get(urlOption);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                hasProjectUrls = true;
            } else {
                additionalProperties.remove(urlOption);
            }
        }
        additionalProperties.put("hasProjectUrls", hasProjectUrls);

        modelPackage = packageName + ".models";
        apiPackage = packageName + ".api";

        final String packagePath = packageName.replace('.', File.separatorChar);

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", packagePath, clientClassFile + ".py"));

        // Spec-independent unit tests: they reference only runtime plumbing
        // (serializer, transport, header-selector, configuration, client), never
        // spec-derived models or APIs, so they compile against any generated SDK.
        // Emitted for the full golden suite and for real clients that opt in via
        // generateUnitTests.
        if (emitUnitTests()) {
            // The dev-dependency manifest: one line per dependency an emitted test
            // imports. Every client keep-lists its own package manifest, so this
            // generator-owned file is the only thing keeping the two in step.
            supportingFiles.add(
                    new SupportingFile(
                            "dev_dependencies.mustache",
                            ".openapi-generator",
                            "DEV-DEPENDENCIES"));
            supportingFiles.add(new SupportingFile("test/conftest.py", "", "conftest.py"));
            supportingFiles.add(
                    new SupportingFile("test/tests_init.py", "test", "__init__.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_default_api_client_unit.mustache",
                            "test",
                            "test_default_api_client_unit.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_event_stream.mustache", "test", "test_event_stream.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_transport_options.mustache",
                            "test",
                            "test_transport_options.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_header_selector.mustache",
                            "test",
                            "test_header_selector.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_value_serializer.mustache",
                            "test",
                            "test_value_serializer.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_trace_context_util.mustache",
                            "test",
                            "test_trace_context_util.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_call_hook.mustache",
                            "test",
                            "test_call_hook.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_opentelemetry_call_hook.mustache",
                            "test",
                            "test_opentelemetry_call_hook.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_configuration.mustache",
                            "test",
                            "test_configuration.py"));
            // ServerConfiguration/ServerVariable/ApiResult are spec-independent
            // runtime plumbing (server_configuration.py, api_result.py), so their
            // tests compile against any generated SDK and ship alongside the other
            // unit tests.
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_server_configuration.mustache",
                            "test",
                            "test_server_configuration.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_server_variable.mustache",
                            "test",
                            "test_server_variable.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_api_result.mustache",
                            "test",
                            "test_api_result.py"));
        }

        // Petstore-coupled tests: they import spec-derived models/APIs
        // (Category, OrderStatusEnum, EdgeCases, PetApi, …) that exist only in
        // the petstore golden, so they are emitted only for the generator's own
        // golden validation, never shipped into real clients.
        if (generateTests) {
            final String testApiPath = Path.of("test", "api").toString();
            supportingFiles.add(
                    new SupportingFile("test/api_init.py", testApiPath, "__init__.py"));
            // Chasm/squid container transport integration test — needs the
            // generator's fixture harness (openapi.yaml, certs, squid.conf)
            // written by writeTestFixtures(), so it is golden-only.
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_default_api_client.mustache",
                            "test",
                            "test_default_api_client.py"));
            // Exercises every generated authenticator (ApiKey/Bearer/OAuth2…)
            // whose set varies by the spec's security schemes — golden-only.
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_client.mustache",
                            "test",
                            "test_client.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/test_pet_api.mustache",
                            testApiPath,
                            "test_pet_api.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/test_store_api.mustache",
                            testApiPath,
                            "test_store_api.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_object_serializer.mustache",
                            "test",
                            "test_object_serializer.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_base_api.mustache",
                            "test",
                            "test_base_api.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_metadata.mustache",
                            "test",
                            "test_metadata.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_composed_schema.mustache",
                            "test",
                            "test_composed_schema.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_api_error.mustache",
                            "test",
                            "test_api_error.py"));
        }
    }

    /**
     * Returns the output directory for model source files by
     * combining the output folder and the model package
     * converted to a directory path.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, modelPackage.replace('.', '/')).toString();
    }

    /**
     * Returns the output directory for API source files by
     * combining the output folder and the API package
     * converted to a directory path.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, apiPackage.replace('.', '/')).toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayTypeTemplate() {
        return "%1$s[%2$s]";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "%1$s[%2$s, %3$s]";
    }

    /** {@inheritDoc} */
    @Override
    protected String getNullLiteral() {
        return "None";
    }

    /** {@inheritDoc} */
    @Override
    protected String getTrueLiteral() {
        return "True";
    }

    /** {@inheritDoc} */
    @Override
    protected String getFalseLiteral() {
        return "False";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "";
    }

    /**
     * Returns {@code str} as the map key type because Python
     * dictionaries use string keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "str";
    }

    /**
     * Overrides the base class because Python's universal base
     * type is {@code object}, not {@code Object}. Cannot be
     * standardized because the capitalization differs from the
     * Java-style default.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "object";
    }

    /**
     * Overrides the base class because Python needs
     * {@code from X.Y import Z} syntax for model imports.
     * Cannot be standardized because no other language uses
     * this import form.
     */
    @Override
    public String toModelImport(String name) {
        if (name.startsWith("import") || name.startsWith("from")) {
            return name;
        }
        return "from "
                + modelPackage()
                + "."
                + toModelFilename(name)
                + " import "
                + name;
    }

    /**
     * Overrides the base class to skip the PascalCase camelize
     * that DefaultCodegen applies, since Python API class names
     * need snake_case-friendly tags. Cannot be standardized
     * because other languages want PascalCase tag names.
     */
    @Override
    public String sanitizeTag(String tag) {
        return sanitizeName(tag);
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '\'';
    }

    /** {@inheritDoc} */
    @Override
    protected boolean shouldEscapeQuotationMark() {
        return false;
    }

    /**
     * Keeps spec prose verbatim. Python has no block comments, so the base
     * class's comment-close rule does not apply, and every docstring the
     * templates emit is double-quoted while escapeText already turns each
     * double quote into an escaped one, so no prose sequence can close a
     * docstring early. A triple single quote is therefore harmless and must
     * not be rewritten.
     *
     * @param input the escaped prose
     * @return the prose unchanged
     */
    @Override
    public String escapeUnsafeCharacters(String input) {
        return input;
    }

    /**
     * Registers the {@code rstText} Mustache lambda, which escapes reStructuredText
     * inline markup in spec prose so that Sphinx, run with warnings as errors by
     * {@code make docs}, reads a tag description or link text as plain words.
     *
     * @return the base lambdas plus {@code rstText}
     */
    @Override
    protected ImmutableMap.Builder<String, Mustache.Lambda> addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put("rstText", (fragment, writer) -> writer.write(toRstText(fragment.execute())));
    }

    /**
     * Backslash-escapes the characters that reStructuredText reads as inline markup
     * (emphasis and strong stars, interpreted-text backquotes, substitution bars and
     * the trailing underscore of a reference), so spec text such as {@code /pets/**}
     * renders as written.
     *
     * @param text the raw spec prose
     * @return the prose with every markup character escaped
     */
    static String toRstText(String text) {
        return text.replaceAll("([\\\\*`|_])", "\\\\$1");
    }

    /**
     * Keeps the spec pattern as written. The upstream default wraps it in
     * Perl-style slash delimiters, which Python's re treats as literal
     * characters, so no value could ever match.
     *
     * @param pattern the spec pattern
     * @return the pattern unchanged
     */
    @Override
    public String toRegularExpression(String pattern) {
        return pattern;
    }

    /**
     * Converts a schema default value to valid Python syntax.
     * Transforms boolean defaults to Python's capitalized
     * True/False form and passes other defaults through
     * unchanged.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema unaliased = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (unaliased.getDefault() != null) {
            /* A default written next to a $ref to an enum component (OAS 3.1
             * sibling keywords): construct the enum member from its wire
             * value, never the bare text (on-hold would parse as on - hold). */
            String enumRef = unaliased.get$ref();
            if (enumRef == null
                    && unaliased.getAllOf() != null
                    && unaliased.getAllOf().size() == 1) {
                enumRef = ((Schema) unaliased.getAllOf().get(0)).get$ref();
            }
            if (enumRef != null && unaliased.getDefault() instanceof String) {
                final Schema target = ModelUtils.getSchema(
                        this.openAPI, ModelUtils.getSimpleRef(enumRef));
                if (target != null && target.getEnum() != null) {
                    return toModelName(ModelUtils.getSimpleRef(enumRef)) + "("
                            + quoteEnumValue(escapeEnumStringValue(
                                    unaliased.getDefault().toString())) + ")";
                }
            }
            if (ModelUtils.isBooleanSchema(unaliased)) {
                return Boolean.parseBoolean(unaliased.getDefault().toString()) ? "True" : "False";
            }
            if (ModelUtils.isStringSchema(unaliased)) {
                // Same literal escaping as a string enum constant: backslash,
                // quote and control characters, so the default evaluates to
                // exactly the spec string.
                return quoteEnumValue(escapeEnumStringValue(unaliased.getDefault().toString()));
            }
            return unaliased.getDefault().toString();
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        // LaxFloat is the `format: float`/`double` mapping (a StrictFloat that
        // also accepts an integral JSON number); it is numeric like StrictFloat.
        return Set.of(
                "int", "float", "StrictInt", "StrictFloat", "LaxFloat", "Decimal",
                "JsonNumber");
    }

    /**
     * Overrides the base class to honour OAS {@code format} hints that the
     * default typeMapping cannot express (one mapping per OAS type only).
     *
     * <ul>
     *   <li>{@code format: uri-reference} / {@code uri-template} downgrade
     *       {@link io.github.mridang.codegen.generators.AbstractBetterCodegen#keepStringForUriSubformats}
     *       the type from {@code HttpUrl} back to {@code str} — these may
     *       be relative or contain RFC 6570 placeholders.</li>
     *   <li>{@code format: password} → the {@code <pkg>._types}
     *       {@code PasswordStr} alias (Item 3): a {@code SecretStr} that redacts
     *       in {@code __repr__}/{@code __str__} but writes the real value on
     *       the JSON wire.</li>
     *   <li>{@code format: email}, {@code ipv4} and {@code ipv6} stay plain
     *       strings, as in the other eleven SDKs.</li>
     * </ul>
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        // S5: a required nullable property keeps its key on the wire as an
        // explicit null, so the model template adds a serializer for it.
        if (property.required && property.isNullable) {
            model.vendorExtensions.put("hasRequiredNullable", true);
        }

        // Item 1 — uri subformat guard. The typeMapping rewrites `URI` to
        // `HttpUrl`; revert to `str` for non-absolute subformats. Use the
        // strict primitive so that the lax-coercion guarantee is preserved.
        keepStringForUriSubformats(property, "StrictStr");

        // A `type: string` with `format: decimal` is a Decimal that travels as a
        // JSON string; DecimalStr reads only a string and writes plain notation.
        if ("Decimal".equals(property.dataType)) {
            property.dataType = "DecimalStr";
            property.datatypeWithEnum = "DecimalStr";
        }

        // `format: byte` (and the OAS 3.1 `contentEncoding: base64` form, which
        // ContentEncodingRule normalizes onto `format: byte`) carries base64 on
        // the wire. The typeMapping resolves both `byte` and `binary` to plain
        // `bytes`, but plain `bytes` skips base64 transcoding: it stores the
        // literal base64 ASCII on validate and raises on dump for real binary.
        // Promote the base64 variant to the `<pkg>._types` `Base64Bytes` alias
        // so it decodes/encodes correctly on the JSON round-trip. Raw
        // `format: binary` keeps plain `bytes` (raw body, never JSON base64).
        promoteBase64Bytes(property);

        // Item 5 — date-time model-body parity. The typeMapping resolves
        // `format: date-time` to pydantic's bare `AwareDatetime`, which keeps
        // the naive-datetime rejection but serializes via model_dump_json with a
        // trailing `Z` and a variable-width fraction (`...45Z`, `...05.123000Z`).
        // The other 11 SDKs pin EXACTLY `.SSS+00:00` (fixed 3-digit fraction,
        // numeric offset, never `Z`). Rewrite date-time MODEL fields to the
        // `<pkg>._types` `OffsetDateTime` alias, which wraps AwareDatetime with a
        // json-only PlainSerializer emitting `isoformat(timespec='milliseconds')`
        // so the body path matches the stringify/_sanitize paths that already
        // pin that form. Parameters and operation return-type strings are
        // untouched (they still carry `AwareDatetime`, which the ObjectSerializer
        // name-keyed stringify/_deserialize fallback already handles correctly).
        promoteOffsetDateTime(property);
        promoteTimeOfDay(property);

        final String fmt = property.dataFormat;
        if (fmt == null) {
            return;
        }
        switch (fmt) {
            case "password":
                property.dataType = "PasswordStr";
                property.datatypeWithEnum = "PasswordStr";
                break;
            default:
                break;
        }
    }

    /**
     * Rewrites {@code bytes} to pydantic's {@code Base64Bytes} for properties
     * whose OAS {@code format} is {@code byte} (base64-on-the-wire), handling
     * both scalar fields ({@code data: bytes}) and array items
     * ({@code scans: List[bytes]}). The {@code format: binary} variant is left
     * as plain {@code bytes} — it is a raw octet body, not a JSON base64 string.
     *
     * @param property the model property to inspect and possibly rewrite
     */
    private static void promoteBase64Bytes(CodegenProperty property) {
        final boolean scalarIsByte = "byte".equals(property.dataFormat);
        final boolean itemsAreByte =
                property.items != null && "byte".equals(property.items.dataFormat);
        if (!scalarIsByte && !itemsAreByte) {
            return;
        }
        if (property.dataType != null) {
            // Replaces the `bytes` token in `bytes`, `List[bytes]`,
            // `Optional[bytes]`, `List[Optional[bytes]]`, etc. The word
            // boundary keeps `bytes` from matching inside other identifiers.
            property.dataType =
                    property.dataType.replaceAll("\\bbytes\\b", "Base64Bytes");
        }
        if (property.datatypeWithEnum != null) {
            property.datatypeWithEnum =
                    property.datatypeWithEnum.replaceAll("\\bbytes\\b", "Base64Bytes");
        }
        if (itemsAreByte && property.items.dataType != null) {
            property.items.dataType =
                    property.items.dataType.replaceAll("\\bbytes\\b", "Base64Bytes");
        }
    }

    /**
     * Rewrites a {@code format: date-time} model field's {@code AwareDatetime}
     * token to the {@code <pkg>._types} {@code OffsetDateTime} alias, for both
     * scalar fields ({@code created_at: AwareDatetime}) and array items
     * ({@code samples: List[AwareDatetime]}). {@code OffsetDateTime} keeps
     * AwareDatetime's naive-datetime rejection and adds a JSON-only serializer
     * that pins the {@code .SSS+00:00} wire form on the model_dump_json body
     * path. Applied only to model properties, so parameter and operation
     * return-type strings keep carrying {@code AwareDatetime}.
     *
     * @param property the model property to inspect and possibly rewrite
     */
    private static void promoteOffsetDateTime(CodegenProperty property) {
        final boolean scalarIsDateTime = "date-time".equals(property.dataFormat);
        final boolean itemsAreDateTime =
                property.items != null && "date-time".equals(property.items.dataFormat);
        if (!scalarIsDateTime && !itemsAreDateTime) {
            return;
        }
        if (property.dataType != null) {
            // Replaces the `AwareDatetime` token in `AwareDatetime`,
            // `List[AwareDatetime]`, `Optional[AwareDatetime]`, etc. The word
            // boundary keeps it from matching inside other identifiers.
            property.dataType =
                    property.dataType.replaceAll("\\bAwareDatetime\\b", "OffsetDateTime");
        }
        if (property.datatypeWithEnum != null) {
            property.datatypeWithEnum =
                    property.datatypeWithEnum.replaceAll(
                            "\\bAwareDatetime\\b", "OffsetDateTime");
        }
        if (itemsAreDateTime && property.items.dataType != null) {
            property.items.dataType =
                    property.items.dataType.replaceAll(
                            "\\bAwareDatetime\\b", "OffsetDateTime");
        }
    }


    /**
     * Rewrites a {@code format: time} model field's {@code datetime.time} token
     * to the {@code <pkg>._types} {@code TimeOfDay} alias, whose JSON serializer
     * writes an RFC 3339 partial-time ({@code HH:MM:SS}, plus {@code .SSS} only
     * when the millisecond part is non-zero). Applied only to model properties.
     *
     * @param property the model property to inspect and possibly rewrite
     */
    private static void promoteTimeOfDay(CodegenProperty property) {
        final boolean scalarIsTime = "time".equals(property.dataFormat);
        final boolean itemsAreTime =
                property.items != null && "time".equals(property.items.dataFormat);
        if (!scalarIsTime && !itemsAreTime) {
            return;
        }
        if (property.dataType != null) {
            property.dataType = property.dataType.replaceAll("\\bdatetime\\.time\\b", "TimeOfDay");
        }
        if (property.datatypeWithEnum != null) {
            property.datatypeWithEnum =
                    property.datatypeWithEnum.replaceAll("\\bdatetime\\.time\\b", "TimeOfDay");
        }
        if (itemsAreTime && property.items.dataType != null) {
            property.items.dataType =
                    property.items.dataType.replaceAll("\\bdatetime\\.time\\b", "TimeOfDay");
        }
    }

    /** {@inheritDoc} */
    @Override
    protected boolean sanitizesExampleValues() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void fixEnumDefaultValue(CodegenProperty prop, CodegenModel model) {
        /* A $ref to an enum component that declares its own default (and has
         * none beside the reference) starts at that member as well (D60); the
         * shared pass hands it over as siblingDefault. */
        if (prop.defaultValue == null
                && Boolean.TRUE.equals(prop.vendorExtensions.get("siblingDefaultIsEnum"))
                && prop.vendorExtensions.get("siblingDefaultEnumType") != null) {
            prop.defaultValue = prop.vendorExtensions.get("siblingDefaultEnumType") + "("
                    + quoteEnumValue(escapeEnumStringValue(
                            String.valueOf(prop.vendorExtensions.get("siblingDefault")))) + ")";
        }
        // Only an inline enum's reference ({@code ModeEnum.MEDIUM}) is
        // qualified; a number default such as 1.5 also contains a dot.
        if (prop.defaultValue != null
                && prop.isEnum
                && prop.defaultValue.contains(".")
                && !prop.defaultValue.startsWith("'")
                && !prop.defaultValue.startsWith(model.classname)) {
            prop.defaultValue = model.classname + prop.defaultValue;
        }
        /* A bare type: number field is JsonNumber (a Decimal alias). pydantic
         * does not validate defaults, so a float literal would leave a float in
         * a Decimal field; build the Decimal from the spec text instead. */
        if (prop.defaultValue != null
                && "JsonNumber".equals(prop.dataType)
                && isNumericLiteral(prop.defaultValue)) {
            prop.defaultValue = "Decimal(\"" + prop.defaultValue + "\")";
        }
    }

    /**
     * Whether a default value is a plain number literal.
     *
     * @param value the rendered default
     * @return true when it parses as a decimal number
     */
    private static boolean isNumericLiteral(String value) {
        try {
            new java.math.BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /*
     * Overrides the base class to resolve Python-specific type
     * imports ({@code from datetime import datetime}, etc.).
     * The base class handles this via {@link #buildsFqnModelImports()} and
     * {@link #getPropertyTypeImportMap()}.
     */

    /** {@inheritDoc} */
    @Override
    protected boolean buildsFqnModelImports() {
        return true;
    }

    /**
     * A classic allOf child imports its base at the top of the module, because
     * the class statement needs it; the generic bottom-of-file import list
     * must not repeat it.
     */
    @Override
    protected void decorateClassicDiscriminatorChild(
            final CodegenModel child, final CodegenModel base) {
        child.imports.remove(base.classname);
    }

    /**
     * Enables Gap K so polymorphic subtype Pydantic models auto-emit
     * the discriminator field with a {@code default='...'} on Field().
     * Without this, callers passing only the non-tag fields hit a
     * Pydantic ValidationError on the missing required tag.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected Map<String, String> getPropertyTypeImportMap() {
        // ProtobufDuration's import is package-qualified, so it cannot live in
        // the static TYPE_IMPORTS map (packageName is only known per-run).
        // Augment the static map with it here so every `format: duration` model
        // field gets `from <pkg>._duration import ProtobufDuration` emitted.
        final Map<String, String> map = new HashMap<>(TYPE_IMPORTS);
        map.put(
                "ProtobufDuration",
                "from " + packageName + "._duration import ProtobufDuration");
        // LaxFloat (format: float/double) and UrlStr (format: uri) are
        // package-qualified Annotated aliases in `<pkg>._types`, so their
        // imports likewise depend on the per-run packageName and cannot live in
        // the static TYPE_IMPORTS map.
        map.put("LaxFloat", "from " + packageName + "._types import LaxFloat");
        map.put("UrlStr", "from " + packageName + "._types import UrlStr");
        map.put("Ipv4Address", "from " + packageName + "._types import Ipv4Address");
        map.put("Ipv6Address", "from " + packageName + "._types import Ipv6Address");
        map.put("DecimalStr", "from " + packageName + "._types import DecimalStr");
        // JsonNumber (bare `type: number`) is the branded Decimal alias that
        // serializes UNQUOTED; its import is likewise per-run package-qualified.
        map.put("JsonNumber", "from " + packageName + "._types import JsonNumber");
        // OffsetDateTime (format: date-time) is the `<pkg>._types` Annotated
        // alias that wraps AwareDatetime with a json-only PlainSerializer pinning
        // the `.SSS+00:00` wire form; its import is per-run package-qualified too.
        map.put(
                "OffsetDateTime",
                "from " + packageName + "._types import OffsetDateTime");
        // Base64Bytes (format: byte) is the `<pkg>._types` alias that keeps raw
        // bytes in memory and base64 only on the wire; it replaces pydantic's.
        map.put("Base64Bytes", "from " + packageName + "._types import Base64Bytes");
        map.put("TimeOfDay", "from " + packageName + "._types import TimeOfDay");
        // PasswordStr (format: password) is the `<pkg>._types` SecretStr alias
        // whose JSON serializer writes the real value instead of the mask.
        map.put("PasswordStr", "from " + packageName + "._types import PasswordStr");
        return map;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean emitsBaseAuthenticator() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of(packageName.replace('.', File.separatorChar), "auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return stem + ".py";
    }

    /** {@inheritDoc} */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();
        // Standalone authenticator tests, emitted only when the spec's security
        // schemes produce the corresponding authenticator. Registered here, not
        // in processOpts: the scheme flags are only set once the spec is parsed.
        if (Boolean.TRUE.equals(additionalProperties.get("hasSecuritySchemeEnvVars"))) {
            supportingFiles.add(
                    new SupportingFile("auth/_credentials.mustache", getAuthDir(), "_credentials.py"));
            if (generateTests) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/test_credentials.mustache", "test", "test_credentials.py"));
                supportingFiles.add(
                        new SupportingFile(
                                "test/test_scheme_authenticators.mustache",
                                "test",
                                "test_scheme_authenticators.py"));
            }
        }
        if (generateTests) {
            if (hasBearerAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/test_bearer_authenticator.mustache",
                                "test",
                                "test_bearer_authenticator.py"));
            }
            if (hasApiKeyAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/test_api_key_authenticator.mustache",
                                "test",
                                "test_api_key_authenticator.py"));
            }
        }

        // Python needs an explicit __init__.py for the oauth sub-package.
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/__init__.mustache", getOAuthDir(), "__init__.py"));
        }
    }

    /** {@inheritDoc} */
    @Override
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        return List.of(
                new OAuthTestFileSpec("test/test_basic_authenticator.mustache", "test", "test_basic_authenticator.py", OAuthTestCondition.BASIC),
                new OAuthTestFileSpec("test/test_oauth2_token_manager.mustache", "test", "test_oauth2_token_manager.py", OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec("test/test_oauth2_auth_code_authenticator.mustache", "test", "test_oauth2_authorization_code_authenticator.py", OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec("test/test_oauth2_implicit_authenticator.mustache", "test", "test_oauth2_implicit_authenticator.py", OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec("test/test_oauth2_client_credentials_authenticator.mustache", "test", "test_oauth2_client_credentials_authenticator.py", OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec("test/test_oauth2_password_authenticator.mustache", "test", "test_oauth2_password_authenticator.py", OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec("test/test_openid_connect_authenticator.mustache", "test", "test_openid_connect_authenticator.py", OAuthTestCondition.OIDC));
    }

    /** {@inheritDoc} */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("imports", buildPythonImports(spec));
        final List<Map<String, Object>> constructorParams = buildPythonConstructorParams(spec, ctx);
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", buildPythonSuperArgs(spec));
        final boolean bearer = "BearerAuthenticator".equals(spec.baseClass());
        ctx.put("isBearer", bearer);
        recordSchemeAuthenticator(spec, constructorParams, bearer);
        recordOAuth2MetadataUrl(spec, ctx);
        recordDeviceAuthorizationScheme(spec, String.valueOf(ctx.get("className")));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    /** The per-scheme authenticators of a device authorization flow, for the test template (D191). */
    private final List<Map<String, Object>> deviceAuthorizationSchemes = new ArrayList<>();

    /**
     * Lists a device authorization scheme under {@code deviceAuthorizationSchemes} with its module,
     * spec URLs and scopes (bodies of double-quoted strings), so the generated test checks that
     * the wrapper passes them (D191).
     */
    private void recordDeviceAuthorizationScheme(SchemeAuthSpec spec, String className) {
        if (!"OAuth2DeviceAuthorizationAuthenticator".equals(spec.baseClass())) {
            return;
        }
        final Map<String, Object> entry = new HashMap<>();
        entry.put("className", className);
        entry.put("module", packageName + ".auth.oauth." + NamingConvention.SNAKE_CASE.apply(className));
        entry.put("deviceAuthorizationUrlLiteral",
                pythonStringBody(Objects.toString(spec.authorizationUrl(), "")));
        entry.put("tokenUrlLiteral", pythonStringBody(Objects.toString(spec.tokenUrl(), "")));
        entry.put("scopeLiteral", pythonStringBody(spec.scopes() == null
                ? "" : String.join(" ", spec.scopes().keySet())));
        deviceAuthorizationSchemes.add(entry);
        additionalProperties.put("deviceAuthorizationSchemes", deviceAuthorizationSchemes);
    }

    /** The per-scheme authenticators that declare an oauth2MetadataUrl, for the test template (D190). */
    private final List<Map<String, Object>> oauth2MetadataUrlSchemes = new ArrayList<>();

    /**
     * Adds the escaped {@code oauth2MetadataUrlLiteral} (the body of a double-quoted string) to a
     * scheme context that declares a metadata URL and lists the scheme under
     * {@code oauth2MetadataUrlSchemes} for the generated test (D190).
     */
    private void recordOAuth2MetadataUrl(SchemeAuthSpec spec, Map<String, Object> ctx) {
        if (!Boolean.TRUE.equals(ctx.get("hasOAuth2MetadataUrl"))) {
            return;
        }
        final String literal = String.valueOf(ctx.get("oauth2MetadataUrl"))
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        ctx.put("oauth2MetadataUrlLiteral", literal);
        final String className = String.valueOf(ctx.get("className"));
        final Map<String, Object> entry = new HashMap<>();
        entry.put("className", className);
        entry.put("module", packageName + (spec.isOAuth() ? ".auth.oauth." : ".auth.")
                + NamingConvention.SNAKE_CASE.apply(className));
        entry.put("oauth2MetadataUrlLiteral", literal);
        oauth2MetadataUrlSchemes.add(entry);
        additionalProperties.put("oauth2MetadataUrlSchemes", oauth2MetadataUrlSchemes);
    }

    /**
     * Remembers a generated per-scheme authenticator, with the environment variable of each
     * credential it reads, so that the generated unit test covers every one of them (F862).
     */
    private void recordSchemeAuthenticator(
            SchemeAuthSpec spec, List<Map<String, Object>> constructorParams, boolean bearer) {
        final String className = spec.schemeClass() + spec.oauthSuffix() + "Authenticator";
        final List<Map<String, Object>> envParams = new ArrayList<>();
        for (final Map<String, Object> param : constructorParams) {
            if (Boolean.TRUE.equals(param.get("hasEnv"))) {
                envParams.add(param);
            }
        }
        if (envParams.isEmpty()) {
            return;
        }
        final Map<String, Object> entry = new HashMap<>();
        entry.put("className", className);
        entry.put("module", packageName + (spec.isOAuth() ? ".auth.oauth." : ".auth.")
                + NamingConvention.SNAKE_CASE.apply(className));
        entry.put("envParams", envParams);
        entry.put("firstEnvName", envParams.get(0).get("envName"));
        entry.put("isBearer", bearer);
        entry.put("isBasic", "BasicAuthenticator".equals(spec.baseClass()));
        entry.put("isApiKey", "ApiKeyAuthenticator".equals(spec.baseClass()));
        entry.put("flow", oauthFlow(spec.baseClass()));
        perSchemeAuthEntries.add(entry);
    }

    /** The OAuth flow a per-scheme authenticator wraps, or {@code none} for any other scheme. */
    private static String oauthFlow(String baseClass) {
        if ("OAuth2ClientCredentialsAuthenticator".equals(baseClass)) {
            return "client_credentials";
        }
        if ("OAuth2PasswordAuthenticator".equals(baseClass)) {
            return "password";
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(baseClass)) {
            return "authorization_code";
        }
        if ("OAuth2ImplicitAuthenticator".equals(baseClass)) {
            return "implicit";
        }
        return "none";
    }

    /**
     * Maps base-class names whose snake_case derivation would be wrong (OAuth2* → o_auth2_*,
     * OpenIdConnect* → open_id_connect_*) to the actual module filenames used in the templates.
     */
    private static final Map<String, String> PYTHON_MODULE_LOOKUP = Map.of(
            "OAuth2TokenManager",                     "oauth2_token_manager",
            "OAuth2ClientCredentialsAuthenticator",   "oauth2_client_credentials_authenticator",
            "OAuth2PasswordAuthenticator",            "oauth2_password_authenticator",
            "OAuth2AuthorizationCodeAuthenticator",   "oauth2_authorization_code_authenticator",
            "OAuth2ImplicitAuthenticator",            "oauth2_implicit_authenticator",
            "OAuth2DeviceAuthorizationAuthenticator", "oauth2_device_authorization_authenticator",
            "OpenIdConnectAuthenticator",             "openid_connect_authenticator"
    );

    private static List<Map<String, String>> buildPythonImports(SchemeAuthSpec spec) {
        final String bc = spec.baseClass();
        final String stem = PYTHON_MODULE_LOOKUP.getOrDefault(bc, NamingConvention.SNAKE_CASE.apply(bc));
        final String moduleFile = "." + stem;
        final List<Map<String, String>> imports = new ArrayList<>();
        final Map<String, String> imp = new HashMap<>();
        imp.put("module", moduleFile);
        imp.put("classNames", bc);
        imports.add(imp);
        if ("ApiKeyAuthenticator".equals(bc)) {
            final Map<String, String> loc = new HashMap<>();
            loc.put("module", ".api_key_location");
            loc.put("classNames", "ApiKeyLocation");
            imports.add(loc);
        }
        return imports;
    }

    /**
     * The constructor parameters of a per-scheme authenticator. Every credential field takes its
     * environment variable (from the shared {@code envVars} of the scheme context) so the
     * parameter is optional and a missing one fails at construction (F862); {@code host} is the
     * one parameter that has none.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> buildPythonConstructorParams(
            SchemeAuthSpec spec, Map<String, Object> schemeContext) {
        final List<Map<String, Object>> envVars =
                (List<Map<String, Object>>) schemeContext.getOrDefault("envVars", List.of());
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, Object> param = new HashMap<>();
            final String snake = NamingConvention.SNAKE_CASE.apply(name);
            param.put("name", snake);
            param.put("type", "str");
            param.put("hasEnv", false);
            for (final Map<String, Object> envVar : envVars) {
                if (name.equals(envVar.get("field"))) {
                    param.put("hasEnv", true);
                    param.put("envName", envVar.get("envName"));
                    break;
                }
            }
            params.add(param);
        }
        return params;
    }

    /**
     * Puts the oneOf and anyOf variant names of each model back into the order
     * the spec declares them. The parent keeps them in a sorted set, but the
     * runtime tries the variants in the order of the {@code one_of_schemas} and
     * {@code any_of_schemas} tuples, so a payload that fits several variants
     * must resolve to the first declared one, as in the other SDKs.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            if (model == null || model.getComposedSchemas() == null) {
                continue;
            }
            if (model.oneOf != null) {
                model.oneOf = inDeclaredOrder(model.oneOf, model.getComposedSchemas().getOneOf());
            }
            if (model.anyOf != null) {
                model.anyOf = inDeclaredOrder(model.anyOf, model.getComposedSchemas().getAnyOf());
            }
        }
        return result;
    }

    private static Set<String> inDeclaredOrder(
            Set<String> names, @Nullable List<CodegenProperty> declared) {
        if (declared == null || names.isEmpty()) {
            return names;
        }
        final Set<String> ordered = new java.util.LinkedHashSet<>();
        for (final CodegenProperty variant : declared) {
            if (names.contains(variant.dataType)) {
                ordered.add(variant.dataType);
            }
        }
        ordered.addAll(names);
        return ordered;
    }

    /**
     * Escapes text for the inside of a double-quoted Python string literal, so
     * an apiKey scheme name holding a quote, a backslash or a control
     * character cannot break the generated authenticator wrapper.
     *
     * @param text the raw text
     * @return the text with backslashes, quotes and control characters escaped
     */
    static String pythonStringBody(final String text) {
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
                out.append("\\x");
                if (c < 0x10) {
                    out.append('0');
                }
                out.append(Integer.toHexString(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    @SuppressWarnings("StringConcatenationMissingWhitespace")
    private static List<String> buildPythonSuperArgs(SchemeAuthSpec spec) {
        final String scopes = formatPythonScopes(spec.scopes());
        if ("BasicAuthenticator".equals(spec.baseClass())
                || "DigestAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of(
                    "host",
                    "\"" + pythonStringBody(Objects.toString(spec.keyParamName(), "")) + "\"",
                    "api_key",
                    "ApiKeyLocation." + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of(
                    "host", "client_id", "client_secret", "\"" + spec.tokenUrl() + "\"", scopes);
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg =
                    spec.refreshUrl() != null ? "\"" + spec.refreshUrl() + "\"" : "None";
            return List.of(
                    "host",
                    "client_id",
                    "client_secret",
                    "\"" + spec.tokenUrl() + "\"",
                    "username",
                    "password",
                    scopes,
                    refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg =
                    spec.refreshUrl() != null ? "\"" + spec.refreshUrl() + "\"" : "None";
            return List.of(
                    "host",
                    "client_id",
                    "client_secret",
                    "\"" + spec.authorizationUrl() + "\"",
                    "\"" + spec.tokenUrl() + "\"",
                    "redirect_uri",
                    scopes,
                    refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of(
                    "host", "client_id", "\"" + spec.authorizationUrl() + "\"", scopes);
        }
        if ("OAuth2DeviceAuthorizationAuthenticator".equals(spec.baseClass())) {
            final String refreshArg =
                    spec.refreshUrl() != null ? "\"" + spec.refreshUrl() + "\"" : "None";
            return List.of(
                    "host",
                    "client_id",
                    "\"" + spec.authorizationUrl() + "\"",
                    "\"" + spec.tokenUrl() + "\"",
                    scopes,
                    refreshArg);
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of(
                    "host",
                    "\"" + spec.openIdConnectUrl() + "\"",
                    "client_id",
                    "client_secret",
                    "redirect_uri",
                    "[]");
        }
        return List.of();
    }

    private static String formatPythonScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\", \"", scopes.keySet()) + "\"]";
    }

    /** {@inheritDoc} */
    @Override
    protected List<FileContentFixup> getFileContentFixups() {
        return List.of(new FileContentFixup(
                ".py",
                Pattern.compile("\\{ ('.*?') }"),
                "{$1}"));
    }

    /**
     * Adds a Python-specific import statement for a data
     * type if it requires one. Handles datetime, date, and
     * Decimal types that need explicit Python imports.
     */
    private static void addTypeImport(Set<String> imports, String dataType) {
        final String imp = TYPE_IMPORTS.get(dataType);
        if (imp != null) {
            imports.add(imp);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final TreeSet<String> importSet = new TreeSet<>();
        final Map<String, String> typeImports = getPropertyTypeImportMap();
        importSet.add("from dataclasses import dataclass");
        // The per-call timeout, headers and retry budget are Optional, so every
        // Options class needs the Optional import even when it carries no
        // optional query/header/form/cookie params.
        boolean needsOptional = true;
        final List<String> typingNames = new ArrayList<>();
        /* Every Options class carries the per-call timeout, headers and retry budget (F827). */
        typingNames.add("Mapping");
        for (final CodegenParameter p : optionsParams) {
            if (!p.required) {
                needsOptional = true;
            }
            if (p.dataType != null) {
                if (p.dataType.startsWith("List[") && !typingNames.contains("List")) {
                    typingNames.add("List");
                }
                if (p.dataType.startsWith("Dict[") && !typingNames.contains("Dict")) {
                    typingNames.add("Dict");
                }
                /* The package-qualified aliases (ProtobufDuration, JsonNumber, Ipv4Address) live
                 * in the per-run map, not in the static one. */
                final String imp = typeImports.get(p.dataType);
                if (imp != null) {
                    importSet.add(imp);
                }
            }
        }
        // Add model type imports for non-primitive types referenced by parameters
        for (final CodegenParameter p : optionsParams) {
            if (!p.isArray && !p.isMap && !p.isPrimitiveType
                    && p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)
                    && !TYPE_IMPORTS.containsKey(p.baseType)) {
                importSet.add(
                        "from "
                                + modelPackage
                                + "."
                                + toModelFilename(p.baseType)
                                + " import "
                                + p.baseType);
            }
            // A $ref to a top-level enum carries its type name in dataType
            // (baseType is null); import its generated model so the annotation
            // resolves.
            if (p.isEnumRef
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)
                    && !TYPE_IMPORTS.containsKey(p.dataType)) {
                importSet.add(
                        "from "
                                + modelPackage
                                + "."
                                + toModelFilename(p.dataType)
                                + " import "
                                + p.dataType);
            }
            if (p.items != null
                    && p.items.baseType != null
                    && !p.items.isPrimitiveType
                    && !languageSpecificPrimitives.contains(p.items.baseType)
                    && !TYPE_IMPORTS.containsKey(p.items.baseType)) {
                importSet.add(
                        "from "
                                + modelPackage
                                + "."
                                + toModelFilename(p.items.baseType)
                                + " import "
                                + p.items.baseType);
            }
        }
        /* Every name inside a parameter annotation must resolve: a uniqueItems
         * array is Set[StrictStr], a content-encoded or styled object parameter
         * carries its model name only in dataType (baseType is null), and a
         * container nests strict scalars. Scan each annotation's identifiers. */
        for (final CodegenParameter p : optionsParams) {
            if (p.dataType == null) {
                continue;
            }
            final java.util.regex.Matcher names = TYPE_NAME_TOKEN.matcher(p.dataType);
            while (names.find()) {
                final String name = names.group();
                if (TYPING_NAMES.contains(name)) {
                    if (!typingNames.contains(name)) {
                        typingNames.add(name);
                    }
                } else if (TYPE_IMPORTS.containsKey(name)) {
                    addTypeImport(importSet, name);
                } else if (Character.isUpperCase(name.charAt(0))
                        && name.indexOf('.') < 0
                        && !languageSpecificPrimitives.contains(name)) {
                    importSet.add("from " + modelPackage + "." + toModelFilename(name)
                            + " import " + name);
                }
            }
        }
        if (needsOptional && !typingNames.contains("Optional")) {
            typingNames.add(0, "Optional");
        }
        if (!typingNames.isEmpty()) {
            importSet.add("from typing import " + String.join(", ", typingNames));
        }

        /* The shared pass that attaches the examples to the parameters runs after
         * the options files are written, so attach them here for the doc comments. */
        capturePluralExamplesForParameters(optionsParams);
        final List<Map<String, Object>> requiredParams = new ArrayList<>();
        final List<Map<String, Object>> optionalParams = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("dataType", p.dataType);
            param.put("deprecated", p.isDeprecated);
            param.put("vendorExtensions", p.vendorExtensions);
            if (p.required) {
                requiredParams.add(param);
            } else {
                optionalParams.add(param);
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("operationId", op.operationId);
        context.put("imports", new ArrayList<>(importSet));
        context.put("requiredParams", requiredParams);
        context.put("optionalParams", optionalParams);
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaTemplate() {
        return "errors/schema_exception.mustache";
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaFilePath(String className) {
        return Path.of(
                        outputFolder,
                        packageName.replace('.', '/'),
                        "errors",
                        NamingConvention.SNAKE_CASE.apply(className) + ".py")
                .toString();
    }

    /** {@inheritDoc} */
    @Override
    protected void decorateErrorSchema(Map<String, Object> schema) {
        final String className = Objects.requireNonNull((String) schema.get("className"));
        final String parentClassName = Objects.requireNonNull((String) schema.get("parentClassName"));
        final boolean parentIsApi = "ApiException".equals(parentClassName);
        schema.put("classFileName", NamingConvention.SNAKE_CASE.apply(className));
        schema.put("parentIsApi", parentIsApi);
        schema.put(
                "parentImport",
                parentIsApi
                        ? packageName + ".errors"
                        : packageName + ".errors." + NamingConvention.SNAKE_CASE.apply(parentClassName));
        schema.put("modelFileName", toModelFilename(Objects.requireNonNull((String) schema.get("schemaName"))));
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(
                        outputFolder,
                        packageName.replace('.', '/'),
                        "api",
                        "options",
                        fileName + ".py")
                .toString();
    }

    /** {@inheritDoc} */
    @Override
    public void emitBarrelFiles(List<Map<String, String>> optionsFiles) {
        final List<Map<String, String>> exports = new ArrayList<>();
        final List<Map<String, String>> all = new ArrayList<>(optionsFiles);
        for (final Map<String, String> meta : all) {
            final String className =
                    Objects.requireNonNull(meta.get("optionsClassName"));
            final Map<String, String> export = new HashMap<>();
            export.put("className", className);
            export.put("moduleName", NamingConvention.SNAKE_CASE.apply(className));
            exports.add(export);
        }
        final Map<String, Object> context = new HashMap<>();
        context.put("exports", exports);
        final String content = renderOptionsTemplate("api/options_init.mustache", context);
        final String initPath =
                Path.of(
                                outputFolder,
                                packageName.replace('.', '/'),
                                "api",
                                "options",
                                "__init__.py")
                        .toString();
        writeFile(initPath, content);
    }
}

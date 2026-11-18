package io.github.mridang.codegen.generators.swift;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenComposedSchemas;
import org.openapitools.codegen.CodegenConstants;
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
 * Generates a Swift API client that uses URLSession for transport
 * and Codable for JSON serialization. All type names use PascalCase,
 * variable and method names use camelCase per Swift convention.
 * The formatter pass invokes swift-format inside Docker to enforce
 * consistent style across all generated source files.
 */
@SuppressWarnings("unused")
public class BetterSwiftCodegen extends AbstractBetterCodegen {

    @Override
    protected String errorSuffix() {
        return "Error";
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterSwiftCodegen.class);

    protected String packageName = "OpenApiClient";
    protected String packageVersion = "1.0.0";

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Swift language. Type mappings convert OpenAPI
     * types to their Swift equivalents (e.g. integer to Int,
     * DateTime to Date). Reserved words are loaded from a
     * bundled word-list to avoid generating identifiers that
     * clash with Swift keywords.
     */
    public BetterSwiftCodegen() {
        outputFolder = Path.of("generated-code", "swift").toString();
        embeddedTemplateDir = templateDir = "templates/swift";

        modelTemplateFiles.put("models/model.mustache", ".swift");
        apiTemplateFiles.put("api/api.mustache", ".swift");
        apiTemplateFiles.put("api/api_protocol.mustache", "Protocol.swift");

        modelPackage = "Models";
        apiPackage = "";

        typeMapping.put("string", "String");
        typeMapping.put("boolean", "Bool");
        typeMapping.put("int", "Int");
        typeMapping.put("integer", "Int");
        typeMapping.put("long", "Int64");
        typeMapping.put("short", "Int");
        typeMapping.put("float", "Float");
        typeMapping.put("double", "Double");
        typeMapping.put("number", "Double");
        typeMapping.put("decimal", "Double");
        typeMapping.put("date", "String");
        typeMapping.put("DateTime", "Date");
        // 4.8: format:time and format:duration. Foundation has no civil-time
        // type (Date is an instant, not a wall-clock HH:MM:SS), so `time`
        // stays a String validated at the call site. `duration` maps to
        // TimeInterval (alias for Double seconds) — Swift's stdlib-native
        // duration scalar. The wire format is google.protobuf.Duration's
        // protobuf-JSON form ("<seconds>s", e.g. "3600s", "1.5s"); the
        // generated ISO8601Duration.swift file ships parseProtobufDuration /
        // formatProtobufDuration helpers to convert between TimeInterval and
        // that decimal-seconds string.
        typeMapping.put("time", "String");
        typeMapping.put("duration", "TimeInterval");
        typeMapping.put("array", "Array");
        typeMapping.put("List", "Array");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "Dictionary");
        typeMapping.put("object", "AnyCodable");
        typeMapping.put("AnyType", "AnyCodable");
        typeMapping.put("file", "Data");
        typeMapping.put("binary", "Data");
        typeMapping.put("ByteArray", "Data");
        typeMapping.put("UUID", "UUID");
        typeMapping.put("URI", "String");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "String",
                                "Bool",
                                "Int",
                                "Int32",
                                "Int64",
                                "Float",
                                "Double",
                                "Data",
                                "Date",
                                "TimeInterval",
                                "UUID",
                                "Void",
                                "Any",
                                "AnyObject"));

        reservedWords = loadReservedWords("/reserved-words/swift.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated Swift package (default: 1.0.0).").defaultValue("1.0.0"));
        cliOptions.add(CliOption.newString("repositoryUrl",
                "Source repository URL, listed in the README Package section."));
        cliOptions.add(CliOption.newString("homepageUrl",
                "Project home page, listed in the README Package section."));
        cliOptions.add(CliOption.newString("authors",
                "Package authors, listed in the README Package section."));
        cliOptions.add(CliOption.newString("documentationUrl",
                "Documentation URL, listed in the README Package section."));
        cliOptions.add(CliOption.newString("issueTrackerUrl",
                "Issue tracker URL, listed in the README Package section."));
    }

    /**
     * Adds {@code argName} to every shared {@code clientSchemes} entry (F857): the scheme's
     * camel-case parameter name, wrapped in backticks when it is a Swift reserved word, so the
     * client can take a credential named after any scheme.
     *
     * @param openAPI the parsed document
     */
    @Override
    public void processOpenAPI(final OpenAPI openAPI) {
        super.processOpenAPI(openAPI);
        final Object schemes = additionalProperties.get("clientSchemes");
        if (!(schemes instanceof List)) {
            return;
        }
        for (final Object entry : (List<?>) schemes) {
            @SuppressWarnings("unchecked")
            final Map<String, Object> scheme = (Map<String, Object>) entry;
            scheme.put("argName", clientArgumentName(String.valueOf(scheme.get("paramName"))));
        }
    }

    /**
     * The identifier a client argument is declared and used by: the name itself, or the name in
     * backticks when it is a Swift reserved word.
     *
     * @param name the camel-case scheme name
     * @return the declarable identifier
     */
    String clientArgumentName(final String name) {
        return reservedWords.contains(name) ? "`" + name + "`" : name;
    }

    /**
     * Gives an integer-enum member the same lowerCamel naming as the
     * identifiers Swift uses elsewhere: {@code NUMBER_1} becomes
     * {@code number1} and {@code NUMBER_MINUS_1} becomes {@code numberMinus1}
     * (F308).
     */
    @Override
    public String toEnumVarName(String value, String datatype) {
        final String name = super.toEnumVarName(value, datatype);
        if (!value.isEmpty() && isNumericEnumDatatype(datatype)) {
            return getEnumCasing().apply(name.toLowerCase(Locale.ROOT));
        }
        return name;
    }

    /**
     * Names that the generated operation methods and options structs inject
     * beside the spec's own parameters, so a parameter named like one of them
     * gets the {@code Param} suffix instead of a duplicate declaration (F1055).
     */
    @Override
    protected Set<String> getInjectedArgumentNames() {
        return Set.of(
                "options",
                "server",
                "requestContentType",
                "timeout",
                "headers",
                "maxRetries");
    }

    /**
     * Every operation gets an options struct, even one with no query, header, form or cookie
     * parameter and no security, so each call has a slot for the per-call timeout, headers and
     * retry limit (F827).
     */
    @Override
    protected boolean mintsOptionsForEveryOperation() {
        return true;
    }

    /**
     * Records the security requirement alternatives of the operation as
     * {@code vendorExtensions.swiftSecurity} (F857): one entry per alternative, each a list of
     * the scheme names that must all be configured together. An operation without its own
     * {@code security} inherits the document's.
     */
    @Override
    public CodegenOperation fromOperation(
            String path, String httpMethod, Operation operation, List<Server> servers) {
        final CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);
        if (op.vendorExtensions == null) {
            op.vendorExtensions = new HashMap<>();
        }
        final List<SecurityRequirement> requirements =
                operation.getSecurity() != null
                        ? operation.getSecurity()
                        : openAPI == null ? null : openAPI.getSecurity();
        op.vendorExtensions.put("swiftSecurity", securityAlternatives(requirements));
        return op;
    }

    /**
     * Turns security requirements into the template's alternatives: a requirement that names
     * no scheme (anonymous access) is dropped, the others keep their order.
     *
     * @param requirements the requirements in effect for an operation; may be null
     * @return one map per alternative holding its scheme names as Swift string literals
     */
    static List<Map<String, Object>> securityAlternatives(
            @Nullable List<SecurityRequirement> requirements) {
        final List<Map<String, Object>> alternatives = new ArrayList<>();
        if (requirements == null) {
            return alternatives;
        }
        for (final SecurityRequirement requirement : requirements) {
            if (requirement.isEmpty()) {
                continue;
            }
            final List<Map<String, Object>> schemes = new ArrayList<>();
            for (final String name : requirement.keySet()) {
                final Map<String, Object> scheme = new HashMap<>();
                scheme.put("literal", "\"" + escapeSwiftString(name) + "\"");
                schemes.add(scheme);
            }
            final Map<String, Object> alternative = new HashMap<>();
            alternative.put("schemes", schemes);
            alternatives.add(alternative);
        }
        return alternatives;
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "swift-plus";
    }

    /**
     * Builds the operation decorators, then puts the per-operation server
     * ahead of the options object in every signature-argument list. The order
     * of an operation's arguments is required parameters, server, options, so
     * adding security (which mints an options argument) never moves an
     * existing argument.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void populateOperationDecorators(CodegenOperation op) {
        super.populateOperationDecorators(op);
        if (op == null || op.vendorExtensions == null) {
            return;
        }
        markContentParameters(op.pathParams);
        markContentParameters(op.headerParams);
        markContentParameters(op.cookieParams);
        markRawCookieParameters(op.cookieParams);
        final Object decorator = op.vendorExtensions.get("op");
        if (!(decorator instanceof Map)) {
            return;
        }
        final Map<String, Object> d = (Map<String, Object>) decorator;
        boolean hasOptionsObject = false;
        final Object signature = d.get("signatureArgs");
        if (signature instanceof List) {
            for (final Map<String, Object> arg : (List<Map<String, Object>>) signature) {
                if (Boolean.TRUE.equals(arg.get("isOptions"))) {
                    hasOptionsObject = true;
                }
            }
        }
        d.put("hasOptionsObject", hasOptionsObject);
        for (final String key : List.of(
                "signatureArgs", "signatureArgsNoOptions")) {
            final Object value = d.get(key);
            if (value instanceof List) {
                d.put(key, serverBeforeOptions((List<Map<String, Object>>) value));
            }
        }
    }

    /**
     * Flags every parameter declared with a JSON {@code content} entry (rather
     * than a {@code schema}) as {@code vendorExtensions.swiftContent}, so the
     * template encodes it through the SDK's own object serializer.
     *
     * @param params the parameters of one location; may be null
     */
    private static void markContentParameters(@Nullable List<CodegenParameter> params) {
        if (params == null) {
            return;
        }
        for (final CodegenParameter p : params) {
            final Map<String, ?> content = p.getContent();
            if (content == null) {
                continue;
            }
            for (final String mediaType : content.keySet()) {
                if (mediaType.toLowerCase(Locale.ROOT).contains("json")) {
                    p.vendorExtensions.put("swiftContent", true);
                    break;
                }
            }
        }
    }

    /**
     * Flags every cookie parameter declared with {@code style: cookie} as
     * {@code vendorExtensions.swiftCookieRaw} (F840): its value is sent as it is when every
     * character is a legal RFC 6265 cookie-octet and refused otherwise, because the caller
     * pre-encodes it. A cookie of the default {@code style: form} is percent-encoded.
     *
     * @param params the cookie parameters; may be null
     */
    private static void markRawCookieParameters(@Nullable List<CodegenParameter> params) {
        if (params == null) {
            return;
        }
        for (final CodegenParameter p : params) {
            if ("cookie".equals(p.style)) {
                p.vendorExtensions.put("swiftCookieRaw", true);
            }
        }
    }

    /**
     * Returns a copy of the signature-argument list with the server entry
     * moved directly ahead of the options entry. A list that lacks either one
     * is returned in its original order.
     *
     * @param args the ordered signature arguments of one operation
     * @return the arguments with the server before the options object
     */
    static List<Map<String, Object>> serverBeforeOptions(List<Map<String, Object>> args) {
        final List<Map<String, Object>> out = new ArrayList<>(args);
        int optionsAt = -1;
        int serverAt = -1;
        for (int i = 0; i < out.size(); i++) {
            if (Boolean.TRUE.equals(out.get(i).get("isOptions"))) {
                optionsAt = i;
            }
            if (Boolean.TRUE.equals(out.get(i).get("isServer"))) {
                serverAt = i;
            }
        }
        if (optionsAt >= 0 && serverAt > optionsAt) {
            out.add(optionsAt, out.remove(serverAt));
        }
        return out;
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Swift client with URLSession and Codable.";
    }

    /**
     * Declares Swift as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.SWIFT;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "Tests/Fixtures";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "Tests/Spec";
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
        return NamingConvention.CAMEL_CASE;
    }

    /**
     * Prefixes a model named {@code Error} or {@code Result} with
     * {@code Model}: a public {@code Error} struct shadows
     * {@code Swift.Error} for the whole module, so every error type that
     * conforms to {@code Error} stops compiling. Same rule as java.
     */
    @Override
    public String toModelName(String name) {
        final String modelName = super.toModelName(name);
        if ("Error".equals(modelName) || "Result".equals(modelName)) {
            return "Model" + modelName;
        }
        return modelName;
    }

    /** Applies the {@link #toModelName} prefix to $ref property and parameter types. */
    @Override
    @SuppressWarnings("rawtypes")
    public String getSchemaType(Schema schema) {
        final String type = super.getSchemaType(schema);
        if ("Error".equals(type) || "Result".equals(type)) {
            return "Model" + type;
        }
        return type;
    }

    /**
     * Keeps a property named {@code type} unescaped. The reserved-word list is
     * lowercased on load, so its {@code Type} entry (the metatype member, which
     * only exists with a capital T) would otherwise rename the legal property
     * {@code type} to {@code _type}.
     */
    @Override
    protected boolean shouldEscapeReservedVarName(String name) {
        return !"type".equals(name) && super.shouldEscapeReservedVarName(name);
    }

    /**
     * Flags a property whose type can transitively reach its enclosing model —
     * a direct self-reference (a tree node whose {@code child} is another node)
     * or mutual recursion (Department -> Employee -> Department). A Swift struct
     * is a value type, so such a stored property would have infinite size and
     * fail to compile; opting in to the shared cycle-detection pass marks the
     * recursive members so the model template stores them {@code @Indirect}
     * (see Indirect.swift). An array or dictionary of the type is already
     * heap-indirected and is not flagged.
     */
    @Override
    protected boolean indirectsRecursiveProperties() {
        return true;
    }

    /**
     * Flags a recursive model property so the model template stores it behind an
     * {@code @Indirect} reference. Read in the template as
     * {@code vendorExtensions.isSelfRecursive}. Idempotent.
     */
    @Override
    protected void markRecursiveProperty(org.openapitools.codegen.CodegenProperty property) {
        property.vendorExtensions.put("isSelfRecursive", true);
    }

    /**
     * Surfaces {@code format: duration} to the model template under the
     * {@code isDuration} vendor extension.
     *
     * <p>OpenAPI Generator 7.12 has no {@code isDuration} flag on
     * {@code CodegenProperty}, and {@code duration} maps to {@code TimeInterval}
     * (a plain {@code Double}). Default {@code Codable} would decode the
     * protobuf-JSON string literal (e.g. {@code "3600s"}) into a {@code Double}
     * and throw {@code typeMismatch}, and would encode a bare number rather than
     * the canonical {@code "3600s"} string. The template reads this flag to route
     * the property through {@code ObjectSerializer.decodeDuration} /
     * {@code encodeDuration} (backed by the shipped {@code ISO8601Duration.swift}
     * helpers). Mirrors {@code BetterKotlinCodegen}.
     */
    @Override
    public void postProcessModelProperty(
            org.openapitools.codegen.CodegenModel model,
            org.openapitools.codegen.CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        property.vendorExtensions.put("isDuration", "duration".equals(property.dataFormat));
        /* UUID encodes itself in uppercase; arrays and maps of UUIDs are
         * flagged so the model template writes them lowercase. */
        org.openapitools.codegen.CodegenProperty uuidItems = property.items;
        property.vendorExtensions.put(
                "isUuidCollection",
                (property.isArray || property.isMap) && uuidItems != null && uuidItems.isUuid);
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        // swift:6.2 (matches the build/lint specs). 6.0's swift-format cannot
        // parse the Swift 6.1+ trailing-comma syntax the templates emit, so it
        // errored and — under the old `|| true` — silently left Swift sources
        // unformatted. 6.2 parses and formats them deterministically.
        return "swift:6.2@sha256:4e50a9e711e8682a8c42bacfeed204568adfd6985a63b3789a165f28d296a28a";
    }

    /**
     * {@inheritDoc}
     *
     * <p>swift-format requires a git repository in the working directory to function,
     * so we initialize one before formatting and remove it afterwards to avoid polluting
     * the generated output.
     */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            "apt-get update -qq && apt-get install -qq -y git > /dev/null 2>&1",
            "git init -q .",
            "swift format --in-place --recursive Sources/ Tests/",
            "rm -rf .git"
        };
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("Int", "Int32", "Int64", "Float", "Double");
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "Set<$1>";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayContainerPattern() {
        return "^\\[(.+)\\]$";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayTypeTemplate() {
        return "[%2$s]";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "[%2$s: %3$s]";
    }

    /**
     * Returns {@code String} as the map key type because Swift
     * dictionaries use String keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "String";
    }

    /**
     * Returns {@code AnyCodable} as the default map value type
     * for Swift's dynamic JSON value type.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "AnyCodable";
    }

    /**
     * Resolves user-supplied options and registers all supporting
     * files for the Swift package structure. Sets up the source
     * layout with Models, API, Errors, Auth, and supporting
     * infrastructure in the Sources/ directory.
     */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String pkg = Optional.ofNullable((String) additionalProperties.get("packageName"))
                .orElse(packageName);
        final String srcDir = Path.of("Sources", pkg).toString();
        final String errorsDir = Path.of(srcDir, "Errors").toString();
        return List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("configuration.mustache", srcDir, "Configuration.swift"),
            new SupportingFileSpec("transport_options.mustache", srcDir, "TransportOptions.swift"),
            new SupportingFileSpec("server_configuration.mustache", srcDir, "ServerConfiguration.swift"),
            new SupportingFileSpec("servers.mustache", srcDir, "Servers.swift"),
            new SupportingFileSpec("indirect.mustache", srcDir, "Indirect.swift"),
            new SupportingFileSpec("errors/root_error.mustache", errorsDir, rootErrorName("Error") + ".swift"),
            new SupportingFileSpec("errors/api_error.mustache", errorsDir, "ApiError.swift"),
            new SupportingFileSpec("errors/error_factory.mustache", errorsDir, "ErrorFactory.swift"),
            new SupportingFileSpec("errors/client_error.mustache", errorsDir, "ClientError.swift"),
            new SupportingFileSpec("errors/server_error.mustache", errorsDir, "ServerError.swift"),
            new SupportingFileSpec("errors/bad_request_error.mustache", errorsDir, "BadRequestError.swift"),
            new SupportingFileSpec("errors/unauthorized_error.mustache", errorsDir, "UnauthorizedError.swift"),
            new SupportingFileSpec("errors/forbidden_error.mustache", errorsDir, "ForbiddenError.swift"),
            new SupportingFileSpec("errors/not_found_error.mustache", errorsDir, "NotFoundError.swift"),
            new SupportingFileSpec("errors/conflict_error.mustache", errorsDir, "ConflictError.swift"),
            new SupportingFileSpec("errors/payment_required_error.mustache", errorsDir, "PaymentRequiredError.swift"),
            new SupportingFileSpec("errors/method_not_allowed_error.mustache", errorsDir, "MethodNotAllowedError.swift"),
            new SupportingFileSpec("errors/request_timeout_error.mustache", errorsDir, "RequestTimeoutError.swift"),
            new SupportingFileSpec("errors/gone_error.mustache", errorsDir, "GoneError.swift"),
            new SupportingFileSpec("errors/payload_too_large_error.mustache", errorsDir, "PayloadTooLargeError.swift"),
            new SupportingFileSpec("errors/unsupported_media_type_error.mustache", errorsDir, "UnsupportedMediaTypeError.swift"),
            new SupportingFileSpec("errors/locked_error.mustache", errorsDir, "LockedError.swift"),
            new SupportingFileSpec("errors/not_implemented_error.mustache", errorsDir, "NotImplementedError.swift"),
            new SupportingFileSpec("errors/bad_gateway_error.mustache", errorsDir, "BadGatewayError.swift"),
            new SupportingFileSpec("errors/service_unavailable_error.mustache", errorsDir, "ServiceUnavailableError.swift"),
            new SupportingFileSpec("errors/gateway_timeout_error.mustache", errorsDir, "GatewayTimeoutError.swift"),
            new SupportingFileSpec("errors/unprocessable_entity_error.mustache", errorsDir, "UnprocessableEntityError.swift"),
            new SupportingFileSpec("errors/too_many_requests_error.mustache", errorsDir, "TooManyRequestsError.swift"),
            new SupportingFileSpec("errors/internal_server_error.mustache", errorsDir, "InternalServerError.swift"),
            new SupportingFileSpec("errors/network_error.mustache", errorsDir, "NetworkError.swift"),
            new SupportingFileSpec("errors/network_timeout_error.mustache", errorsDir, "NetworkTimeoutError.swift"),
            new SupportingFileSpec("errors/serialization_error.mustache", errorsDir, "SerializationError.swift"),
            new SupportingFileSpec("errors/oauth2_server_error.mustache", errorsDir, "OAuth2ServerError.swift"),
            new SupportingFileSpec("errors/oauth2_token_error.mustache", errorsDir, "OAuth2TokenError.swift"),
            new SupportingFileSpec("errors/oauth2_authorization_code_error.mustache", errorsDir, "OAuth2AuthorizationCodeError.swift"),
            new SupportingFileSpec("errors/configuration_error.mustache", errorsDir, "ConfigurationError.swift"),
            new SupportingFileSpec("header_selector.mustache", srcDir, "HeaderSelector.swift"),
            new SupportingFileSpec("iso8601_duration.mustache", srcDir, "ISO8601Duration.swift"),
            new SupportingFileSpec("object_serializer.mustache", srcDir, "ObjectSerializer.swift"),
            new SupportingFileSpec("value_serializer.mustache", srcDir, "ValueSerializer.swift"),
            new SupportingFileSpec("trace_context_util.mustache", srcDir, "TraceContextUtil.swift"),
            new SupportingFileSpec("call_hook.mustache", srcDir, "CallHook.swift"),
            new SupportingFileSpec("call_info.mustache", srcDir, "CallInfo.swift"),
            new SupportingFileSpec("call_outcome.mustache", srcDir, "CallOutcome.swift"),
            new SupportingFileSpec("api_response.mustache", srcDir, "ApiHttpResponse.swift"),
            new SupportingFileSpec("api_result.mustache", srcDir, "ApiResult.swift"),
            new SupportingFileSpec("api_client.mustache", srcDir, "ApiClient.swift"),
            new SupportingFileSpec("default_api_client.mustache", srcDir, "DefaultApiClient.swift"),
            new SupportingFileSpec("base_api.mustache", Path.of(srcDir, "Api").toString(), "BaseApi.swift"),
            new SupportingFileSpec("authenticator.mustache", Path.of(srcDir, "Auth").toString(), "Authenticator.swift"),
            new SupportingFileSpec("any_codable.mustache", srcDir, "AnyCodable.swift"),
            new SupportingFileSpec("package_swift.mustache", "", "Package.swift"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore"),
            new SupportingFileSpec(
                "dev_dependencies.mustache", ".openapi-generator", "DEV-DEPENDENCIES"),
            new SupportingFileSpec("swiftlint_yml.mustache", "", ".swiftlint.yml"),
            new SupportingFileSpec("swift_format_json.mustache", "", ".swift-format")
        );
    }

    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put(
                "userAgentDefault", packageName + "/" + packageVersion + " (swift)");

        final String srcDir = Path.of("Sources", packageName).toString();

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        supportingFiles.add(
                new SupportingFile("client.mustache", srcDir, clientClassName + ".swift"));
        supportingFiles.add(
                new SupportingFile(
                        "client_protocol.mustache", srcDir, clientClassName + "Protocol.swift"));

        final String testDir = Path.of("Tests", packageName + "Tests").toString();
        if (emitUnitTests()) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientUnitTests.mustache",
                            testDir,
                            "DefaultApiClientUnitTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TransportOptionsTests.mustache",
                            testDir,
                            "TransportOptionsTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/HeaderSelectorTests.mustache",
                            testDir,
                            "HeaderSelectorTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ValueSerializerTests.mustache",
                            testDir,
                            "ValueSerializerTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TraceContextUtilTests.mustache",
                            testDir,
                            "TraceContextUtilTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/CallHookTests.mustache", testDir, "CallHookTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/CallHookRecipeTests.mustache",
                            testDir,
                            "CallHookRecipeTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ConfigurationTests.mustache",
                            testDir,
                            "ConfigurationTests.swift"));
        }
        if (generateTests) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/TestContainersHelper.mustache",
                            testDir,
                            "TestContainersHelper.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/PetApiTests.mustache", testDir, "PetApiTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/StoreApiTests.mustache", testDir, "StoreApiTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientTests.mustache",
                            testDir,
                            "DefaultApiClientTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ObjectSerializerTests.mustache",
                            testDir,
                            "ObjectSerializerTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/BaseApiTests.mustache", testDir, "BaseApiTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ClientTests.mustache",
                            testDir,
                            "ClientTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiErrorTests.mustache",
                            testDir,
                            "ApiErrorTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/MetadataTests.mustache", testDir, "MetadataTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ComposedSchemaTests.mustache",
                            testDir,
                            "ComposedSchemaTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ServerConfigurationTests.mustache",
                            testDir,
                            "ServerConfigurationTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ServerVariableTests.mustache",
                            testDir,
                            "ServerVariableTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiResultTests.mustache",
                            testDir,
                            "ApiResultTests.swift"));
        }
    }

    /**
     * Returns the output directory for model source files.
     * Swift models go in Sources/{packageName}/Models/.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, "Sources", packageName, "Models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * Swift APIs go in Sources/{packageName}/Api/.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, "Sources", packageName, "Api").toString();
    }

    /**
     * Returns the Swift default value literal for the given schema.
     * Numeric and boolean defaults use their string representation;
     * string defaults are wrapped in double quotes.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (ModelUtils.isIntegerSchema(resolved)
                || ModelUtils.isNumberSchema(resolved)
                || ModelUtils.isBooleanSchema(resolved)) {
            if (resolved.getDefault() != null) {
                return resolved.getDefault().toString();
            }
        } else if (ModelUtils.isStringSchema(resolved)) {
            if (resolved.getDefault() != null) {
                // The shared enum literal escape: the literal evaluates to
                // exactly the spec string (backslash, quote, control chars).
                final String literal =
                        quoteEnumValue(escapeEnumStringValue(String.valueOf(resolved.getDefault())));
                if (schema.get$ref() != null && resolved.getEnum() != null) {
                    // A component enum default is a case of that enum type,
                    // looked up by its wire value like an inline enum default.
                    return toModelName(ModelUtils.getSimpleRef(schema.get$ref()))
                            + "(rawValue: " + literal + ")";
                }
                return literal;
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected String getNullLiteral() {
        return "nil";
    }

    /**
     * Enables Gap K (auto-inject discriminator on serialise) for
     * Swift. The Swift struct {@code init} template renders a
     * default literal directly from {@code defaultValue}, so
     * subtype structs gain {@code init(weightKg: ..., foodType:
     * String = "dry")}.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "Sources";
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of("Sources", packageName, "Auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getOAuthDir() {
        return Path.of(getAuthDir(), "OAuth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return pascalAuthFilename(stem, ".swift");
    }

    /**
     * Registers the Basic authenticator test. It must be added here rather than
     * in processOpts: the security-scheme flags are only set once the spec has
     * been read, so a check in processOpts always saw them false and the test
     * was never generated.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();
        if (!generateTests) {
            return;
        }
        final String testDir = Path.of("Tests", packageName + "Tests").toString();
        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/BasicAuthenticatorTests.mustache",
                            testDir,
                            "BasicAuthenticatorTests.swift"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/BearerAuthenticatorTests.mustache",
                            testDir,
                            "BearerAuthenticatorTests.swift"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiKeyAuthenticatorTests.mustache",
                            testDir,
                            "ApiKeyAuthenticatorTests.swift"));
        }
    }

    /**
     * The OAuth2 and OpenID Connect tests, each gated on the scheme it
     * exercises. Like the Basic, Bearer and API-key tests they are registered
     * once the spec has been read, never from processOpts.
     */
    @Override
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        final String testDir = Path.of("Tests", packageName + "Tests").toString();
        return List.of(
                new OAuthTestFileSpec(
                        "test/OAuth2TokenManagerTests.mustache",
                        testDir,
                        "OAuth2TokenManagerTests.swift",
                        OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec(
                        "test/OAuth2AuthCodeAuthenticatorTests.mustache",
                        testDir,
                        "OAuth2AuthorizationCodeAuthenticatorTests.swift",
                        OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec(
                        "test/OAuth2ImplicitAuthenticatorTests.mustache",
                        testDir,
                        "OAuth2ImplicitAuthenticatorTests.swift",
                        OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec(
                        "test/OAuth2ClientCredentialsAuthenticatorTests.mustache",
                        testDir,
                        "OAuth2ClientCredentialsAuthenticatorTests.swift",
                        OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec(
                        "test/OAuth2PasswordAuthenticatorTests.mustache",
                        testDir,
                        "OAuth2PasswordAuthenticatorTests.swift",
                        OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec(
                        "test/OpenIdConnectAuthenticatorTests.mustache",
                        testDir,
                        "OpenIdConnectAuthenticatorTests.swift",
                        OAuthTestCondition.OIDC));
    }

    /**
     * The per-scheme authenticators, one entry each (class name, base class, the scheme kind and
     * the environment variable of every credential), for the generated tests. It is the live
     * list behind the {@code perSchemeAuthEntries} additional property.
     */
    private final List<Map<String, Object>> perSchemeAuthEntries = new ArrayList<>();

    /**
     * The behaviour token of the empty-variable failure test, one per scheme class: the catalogue
     * ids authscheme003 to authscheme016. A scheme the table does not name keeps an unnumbered
     * test name. Each scheme gets exactly one such test, so no two tests share a name.
     */
    private static final java.util.Map<String, String> EMPTY_VARIABLE_BHV =
            java.util.Map.ofEntries(
                    java.util.Map.entry("PetStoreBasicAuthenticator", "BhvAuthscheme003"),
                    java.util.Map.entry("AdminBasicAuthenticator", "BhvAuthscheme004"),
                    java.util.Map.entry("ApiKeyHeaderAuthenticator", "BhvAuthscheme005"),
                    java.util.Map.entry("ApiKeyQueryAuthenticator", "BhvAuthscheme006"),
                    java.util.Map.entry("SessionCookieAuthenticator", "BhvAuthscheme007"),
                    java.util.Map.entry("SsoAuthAuthenticator", "BhvAuthscheme008"),
                    java.util.Map.entry("PetStoreBearerAuthenticator", "BhvAuthscheme010"),
                    java.util.Map.entry("ServiceTokenAuthenticator", "BhvAuthscheme011"),
                    java.util.Map.entry("InternalApiKeyAuthenticator", "BhvAuthscheme012"),
                    java.util.Map.entry(
                            "MachineAuthClientCredentialsAuthenticator", "BhvAuthscheme013"),
                    java.util.Map.entry("LegacyAuthPasswordAuthenticator", "BhvAuthscheme014"),
                    java.util.Map.entry(
                            "UserAuthAuthorizationCodeAuthenticator", "BhvAuthscheme015"),
                    java.util.Map.entry("BrowserAuthImplicitAuthenticator", "BhvAuthscheme016"));

    /** {@inheritDoc} */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("imports", List.of());
        final boolean hasEnvVars = Boolean.TRUE.equals(ctx.get("hasEnvVars"));
        /* Basic and Bearer take the same argument labels as their base class, so the
           initializer of the scheme class overrides the base one. */
        final boolean needsOverride =
                "BasicAuthenticator".equals(spec.baseClass())
                        || "BearerAuthenticator".equals(spec.baseClass());
        ctx.put("needsOverride", needsOverride);
        ctx.put("constructorSignature", buildSwiftConstructorSignature(spec, hasEnvVars, true));
        ctx.put("environmentSignature", buildSwiftConstructorSignature(spec, hasEnvVars, false));
        ctx.put("forwardArguments", buildSwiftForwardArguments(spec));
        ctx.put("superCall", buildSwiftSuperCall(spec));
        // Basic, Bearer and API-key initializers validate their credentials and
        // throw ConfigurationError; the scheme subclass rethrows.
        final boolean superThrows = java.util.Set.of(
                "BasicAuthenticator", "BearerAuthenticator", "ApiKeyAuthenticator")
                .contains(spec.baseClass());
        ctx.put("fallible", superThrows);
        // A credential that can come from the environment can be missing, which throws.
        ctx.put("throwing", superThrows || hasEnvVars);
        recordSchemeAuthenticator(spec, ctx);
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    /**
     * Remembers a generated per-scheme authenticator so the generated tests can exercise the
     * credentials it reads from the environment.
     */
    private void recordSchemeAuthenticator(SchemeAuthSpec spec, Map<String, Object> ctx) {
        final String className = String.valueOf(ctx.get("className"));
        if (className.equals(spec.baseClass()) || !Boolean.TRUE.equals(ctx.get("hasEnvVars"))) {
            return;
        }
        final Map<String, Object> entry = new HashMap<>();
        entry.put("className", className);
        entry.put("baseClass", spec.baseClass());
        entry.put("isBearerScheme", "BearerAuthenticator".equals(spec.baseClass()));
        /* Only the first bearer-kind scheme carries the bearer catalogue id, so the id stays unique. */
        entry.put("isFirstBearerScheme", "BearerAuthenticator".equals(spec.baseClass())
                && perSchemeAuthEntries.stream().noneMatch(e -> Boolean.TRUE.equals(e.get("isBearerScheme"))));
        entry.put("isBasicScheme", "BasicAuthenticator".equals(spec.baseClass()));
        final boolean apiKey = "ApiKeyAuthenticator".equals(spec.baseClass());
        entry.put("isApiKeyScheme", apiKey);
        /* One catalogue id per behaviour: the id-carrying tests name only the first scheme of
         * their kind, and the empty-variable failure carries the id of the scheme it is named for. */
        entry.put("isFirstApiKeyScheme", apiKey && perSchemeAuthEntries.stream()
                .noneMatch(e -> Boolean.TRUE.equals(e.get("isApiKeyScheme"))));
        entry.put("isFirstBearerScheme", "BearerAuthenticator".equals(spec.baseClass())
                && perSchemeAuthEntries.stream().noneMatch(e -> Boolean.TRUE.equals(e.get("isBearerScheme"))));
        entry.put("emptyVariableBhv", EMPTY_VARIABLE_BHV.getOrDefault(className, ""));
        entry.put("envVars", ctx.get("envVars"));
        perSchemeAuthEntries.add(entry);
        additionalProperties.put("perSchemeAuthEntries", perSchemeAuthEntries);
        additionalProperties.put("hasPerSchemeAuthEntries", true);
    }

    /**
     * The parameter list of a scheme authenticator's initializer. A credential that has an
     * environment variable is optional, so the variable can supply it; the host never is.
     *
     * @param spec the scheme
     * @param optionalCredentials whether every credential is an optional {@code String?}
     * @param withDefaults whether an optional credential defaults to {@code nil}
     * @return the parameter list, such as {@code host: String, token: String? = nil}
     */
    static String buildSwiftConstructorSignature(
            SchemeAuthSpec spec, boolean optionalCredentials, boolean withDefaults) {
        return spec.paramNames().stream()
                .map(
                        name ->
                                optionalCredentials && !"host".equals(name)
                                        ? name + ": String?" + (withDefaults ? " = nil" : "")
                                        : name + ": String")
                .collect(Collectors.joining(", "));
    }

    /**
     * The argument list that forwards a scheme authenticator's own parameters to another
     * initializer of the same class.
     *
     * @param spec the scheme
     * @return the arguments, such as {@code host: host, token: token}
     */
    static String buildSwiftForwardArguments(SchemeAuthSpec spec) {
        return spec.paramNames().stream()
                .map(name -> name + ": " + name)
                .collect(Collectors.joining(", "));
    }

    /**
     * Flags the models that hold a {@code format: password} property, so the
     * model template can keep the value out of reflection, {@code print} and
     * {@code dump}.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            if (model == null || model.isEnum || model.vars == null) {
                continue;
            }
            modelMap.put("hasPasswordVars", model.vars.stream().anyMatch(v -> v.isPassword));
        }
        for (final ModelMap modelMap : result.getModels()) {
            nameUnionVariants(modelMap.getModel());
        }
        return result;
    }

    /**
     * Realises a classic allOf base (a schema with a discriminator that its
     * children compose with allOf) as a union of its subtypes, as go does. A
     * Swift struct cannot be subclassed, so decoding the base must return the
     * subtype its discriminator names; the mapped subtypes become the base's
     * oneOf set and its typed variants, and the union template's discriminator
     * branch does the dispatch and rejects an unknown or missing value (S4,
     * issue #104).
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(final Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();
                if (Boolean.TRUE.equals(model.vendorExtensions.get("x-allof-base"))) {
                    realiseClassicBaseAsUnion(model);
                }
            }
        }
        return result;
    }

    private static void realiseClassicBaseAsUnion(final CodegenModel base) {
        final Set<String> variants = new LinkedHashSet<>();
        final List<CodegenProperty> typed = new ArrayList<>();
        for (final CodegenDiscriminator.MappedModel mapped : base.discriminator.getMappedModels()) {
            if (variants.add(mapped.getModelName())) {
                final CodegenProperty variant = new CodegenProperty();
                variant.dataType = mapped.getModelName();
                typed.add(variant);
            }
        }
        base.oneOf = variants;
        base.setComposedSchemas(new CodegenComposedSchemas(List.of(), typed, List.of(), null));
        nameUnionVariants(base);
    }

    /**
     * Gives every oneOf and anyOf variant a camelCase-ready name for the typed
     * {@code from<Variant>}, {@code is<Variant>} and {@code as<Variant>}
     * members of the union wrapper: the variant type with every non-letter and
     * non-digit dropped and each word capitalised, so {@code Reservation}
     * stays {@code Reservation} and {@code [String]} becomes {@code String}
     * and {@code [Data]} {@code Data}.
     */
    static void nameUnionVariants(@Nullable CodegenModel model) {
        if (model == null || model.getComposedSchemas() == null) {
            return;
        }
        nameVariants(model.getComposedSchemas().getOneOf());
        nameVariants(model.getComposedSchemas().getAnyOf());
    }

    private static void nameVariants(@Nullable List<org.openapitools.codegen.CodegenProperty> variants) {
        if (variants == null) {
            return;
        }
        final Set<String> seen = new HashSet<>();
        for (final org.openapitools.codegen.CodegenProperty variant : variants) {
            final StringBuilder name = new StringBuilder();
            boolean startOfWord = true;
            final String type = String.valueOf(variant.dataType);
            for (int i = 0; i < type.length(); i++) {
                final char c = type.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    name.append(startOfWord ? Character.toUpperCase(c) : c);
                    startOfWord = false;
                } else {
                    startOfWord = true;
                }
            }
            if (type.startsWith("[") && type.contains(":")) {
                name.append("Map");
            } else if (type.startsWith("[")) {
                name.append("List");
            }
            String unique = name.toString();
            for (int n = 2; !seen.add(unique); n++) {
                unique = name.toString() + n;
            }
            if (variant.vendorExtensions == null) {
                variant.vendorExtensions = new HashMap<>();
            }
            variant.vendorExtensions.put("variantName", unique);
        }
    }

    private static String formatSwiftScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "[\""
                + scopes.keySet().stream()
                        .map(BetterSwiftCodegen::escapeSwiftString)
                        .collect(Collectors.joining("\", \""))
                + "\"]";
    }

    /**
     * Registers the {@code swiftString} lambda, which escapes raw text for the
     * body of a Swift string literal (an apiKey header name such as
     * {@code X-"Key"} must not end the literal).
     */
    @Override
    protected com.google.common.collect.ImmutableMap.Builder<
                    String, com.samskivert.mustache.Mustache.Lambda>
            addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put(
                        "swiftString",
                        (fragment, writer) ->
                                writer.write(escapeSwiftString(fragment.execute())));
    }

    /**
     * Escapes raw text for the body of a Swift string literal: backslash and
     * double quote, and every control character (as a Swift unicode escape).
     *
     * @param text raw text, possibly null
     * @return the text safe to place between the quotes of a literal
     */
    static String escapeSwiftString(@Nullable String text) {
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
            } else if (c < 0x20 || c == 0x7F) {
                out.append("\\u{").append(Integer.toHexString(c)).append('}');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "keyIn values are ASCII-only OpenAPI location strings (header/query/cookie)"
                    + " — toLowerCase(Locale.ROOT) is intentional and safe here")
    private static String buildSwiftSuperCall(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return "host: host, username: username, password: password";
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return "host: host, token: token";
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            final String location = "." + NamingConvention.CAMEL_CASE.apply(
                    spec.keyIn() != null ? spec.keyIn().toLowerCase(Locale.ROOT) : "header");
            return "host: host, keyParamName: \"" + escapeSwiftString(spec.keyParamName())
                    + "\", apiKey: apiKey, location: " + location;
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return "host: host, clientID: clientId, clientSecret: clientSecret, tokenURL: \""
                    + spec.tokenUrl() + "\", scopes: "
                    + formatSwiftScopes(spec.scopes());
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? ", refreshURL: \"" + spec.refreshUrl() + "\"" : "";
            return "host: host, clientID: clientId, clientSecret: clientSecret, tokenURL: \""
                    + spec.tokenUrl() + "\", username: username, password: password, scopes: "
                    + formatSwiftScopes(spec.scopes()) + refreshArg;
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? ", refreshURL: \"" + spec.refreshUrl() + "\"" : "";
            return "host: host, clientID: clientId, clientSecret: clientSecret, authorizationURL: \""
                    + spec.authorizationUrl() + "\", tokenURL: \"" + spec.tokenUrl()
                    + "\", redirectURI: redirectUri, scopes: "
                    + formatSwiftScopes(spec.scopes()) + refreshArg;
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return "host: host, clientID: clientId, authorizationURL: \""
                    + spec.authorizationUrl() + "\", scopes: "
                    + formatSwiftScopes(spec.scopes());
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return "host: host, openIDConnectURL: \"" + spec.openIdConnectUrl()
                    + "\", clientID: clientId, clientSecret: clientSecret, redirectURI: redirectUri"
                    + ", scopes: []";
        }
        return "";
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", p.paramName);
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            param.put("requiredNullable", isRequiredNullableQuery(p));
            // Threads the native CodegenParameter deprecation flag into the
            // Options render context so a parameter declared `deprecated: true`
            // in the spec surfaces a Swift availability annotation on the
            // corresponding Options field, mirroring how operations and model
            // properties already emit `@available(*, deprecated, ...)`.
            param.put("deprecated", p.isDeprecated);
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
        /* F857: no per-operation authenticator. An operation uses the credential the client
         * holds for the scheme its spec names, so the Options object has no `auth` field. */
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaTemplate() {
        return "errors/schema_error.mustache";
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaFilePath(String className) {
        return Path.of(getOutputDir(), "Sources", packageName, "Errors", className + ".swift")
                .toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        return Path.of(
                        getOutputDir(),
                        "Sources",
                        packageName,
                        "Api",
                        "Options",
                        optionsClassName + ".swift")
                .toString();
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getReservedEnumMemberNames() {
        return Set.of("description");
    }
}

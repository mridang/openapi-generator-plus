package io.github.mridang.codegen.generators.swift;

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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterSwiftCodegen.class);

    protected String packageName = "PetstoreClient";
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
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "swift-plus";
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
            new SupportingFileSpec("errors/zitadel_error.mustache", errorsDir, "ZitadelError.swift"),
            new SupportingFileSpec("api_error.mustache", srcDir, "ApiError.swift"),
            new SupportingFileSpec("errors/client_error.mustache", errorsDir, "ClientError.swift"),
            new SupportingFileSpec("errors/server_error.mustache", errorsDir, "ServerError.swift"),
            new SupportingFileSpec("errors/bad_request_error.mustache", errorsDir, "BadRequestError.swift"),
            new SupportingFileSpec("errors/unauthorized_error.mustache", errorsDir, "UnauthorizedError.swift"),
            new SupportingFileSpec("errors/forbidden_error.mustache", errorsDir, "ForbiddenError.swift"),
            new SupportingFileSpec("errors/not_found_error.mustache", errorsDir, "NotFoundError.swift"),
            new SupportingFileSpec("errors/conflict_error.mustache", errorsDir, "ConflictError.swift"),
            new SupportingFileSpec("errors/unprocessable_entity_error.mustache", errorsDir, "UnprocessableEntityError.swift"),
            new SupportingFileSpec("errors/internal_server_error.mustache", errorsDir, "InternalServerError.swift"),
            new SupportingFileSpec("header_selector.mustache", srcDir, "HeaderSelector.swift"),
            new SupportingFileSpec("iso8601_duration.mustache", srcDir, "ISO8601Duration.swift"),
            new SupportingFileSpec("object_serializer.mustache", srcDir, "ObjectSerializer.swift"),
            new SupportingFileSpec("value_serializer.mustache", srcDir, "ValueSerializer.swift"),
            new SupportingFileSpec("trace_context_util.mustache", srcDir, "TraceContextUtil.swift"),
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
            if (hasBasicAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/BasicAuthenticatorTests.mustache",
                                testDir,
                                "BasicAuthenticatorTests.swift"));
            }
            supportingFiles.add(
                    new SupportingFile(
                            "test/BearerAuthenticatorTests.mustache",
                            testDir,
                            "BearerAuthenticatorTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiKeyAuthenticatorTests.mustache",
                            testDir,
                            "ApiKeyAuthenticatorTests.swift"));
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
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2TokenManagerTests.mustache",
                            testDir,
                            "OAuth2TokenManagerTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2AuthCodeAuthenticatorTests.mustache",
                            testDir,
                            "OAuth2AuthCodeAuthenticatorTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2ImplicitAuthenticatorTests.mustache",
                            testDir,
                            "OAuth2ImplicitAuthenticatorTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2ClientCredentialsAuthenticatorTests.mustache",
                            testDir,
                            "OAuth2ClientCredentialsAuthenticatorTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OAuth2PasswordAuthenticatorTests.mustache",
                            testDir,
                            "OAuth2PasswordAuthenticatorTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OpenIdConnectAuthenticatorTests.mustache",
                            testDir,
                            "OpenIdConnectAuthenticatorTests.swift"));
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
                return "\"" + escapeText(String.valueOf(resolved.getDefault())) + "\"";
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

    /** {@inheritDoc} */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("imports", List.of());
        final boolean needsOverride =
                "BasicAuthenticator".equals(spec.baseClass())
                        || "BearerAuthenticator".equals(spec.baseClass());
        ctx.put("needsOverride", needsOverride);
        ctx.put("constructorSignature", buildSwiftConstructorSignature(spec));
        ctx.put("superCall", buildSwiftSuperCall(spec));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    private static String buildSwiftConstructorSignature(SchemeAuthSpec spec) {
        return spec.paramNames().stream()
                .map(name -> name + ": String")
                .collect(Collectors.joining(", "));
    }

    private static String formatSwiftScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\", \"", scopes.keySet()) + "\"]";
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
            return "host: host, keyParamName: \"" + spec.keyParamName()
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
        // Folds the optional per-operation `auth` field into the Options object
        // for authed operations. Swift's Authenticator protocol lives in the
        // same module as the generated Options struct, so no import is needed.
        injectAuthFieldContext(op, context);
        return renderOptionsTemplate("api/options.mustache", context);
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
}

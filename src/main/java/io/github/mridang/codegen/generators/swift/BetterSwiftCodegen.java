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
import java.util.Map;
import java.util.Objects;
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
        typeMapping.put("array", "Array");
        typeMapping.put("List", "Array");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "Dictionary");
        typeMapping.put("object", "AnyCodable");
        typeMapping.put("AnyType", "AnyCodable");
        typeMapping.put("file", "Data");
        typeMapping.put("binary", "Data");
        typeMapping.put("ByteArray", "Data");
        typeMapping.put("UUID", "String");
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
                                "Void",
                                "Any",
                                "AnyObject"));

        reservedWords = loadReservedWords("/reserved-words/swift.txt");
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

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "swift:6.0";
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
            "swift format --in-place --recursive Sources/ Tests/ || true",
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
    protected char getQuoteChar() {
        return '"';
    }

    /**
     * Formats an array type declaration using Swift Array syntax.
     * Returns {@code [innerType]}.
     */
    @Override
    protected String formatArrayType(String containerType, String innerType) {
        return "[" + innerType + "]";
    }

    /**
     * Formats a map type declaration using Swift Dictionary syntax.
     * Returns {@code [String: valueType]}.
     */
    @Override
    protected String formatMapType(String containerType, String keyType, String valueType) {
        return "[" + keyType + ": " + valueType + "]";
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
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put(
                "userAgentDefault", packageName + "/" + packageVersion + " (swift)");

        final String srcDir = Path.of("Sources", packageName).toString();

        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", srcDir, "Configuration.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options.mustache", srcDir, "TransportOptions.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache", srcDir, "ServerConfiguration.swift"));
        supportingFiles.add(
                new SupportingFile("servers.mustache", srcDir, "Servers.swift"));
        supportingFiles.add(
                new SupportingFile("api_error.mustache", srcDir, "ApiError.swift"));

        final String errorsDir = Path.of(srcDir, "Errors").toString();
        supportingFiles.add(
                new SupportingFile(
                        "errors/client_error.mustache", errorsDir, "ClientError.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/server_error.mustache", errorsDir, "ServerError.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/bad_request_error.mustache",
                        errorsDir,
                        "BadRequestError.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unauthorized_error.mustache",
                        errorsDir,
                        "UnauthorizedError.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/forbidden_error.mustache", errorsDir, "ForbiddenError.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/not_found_error.mustache", errorsDir, "NotFoundError.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/conflict_error.mustache", errorsDir, "ConflictError.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unprocessable_entity_error.mustache",
                        errorsDir,
                        "UnprocessableEntityError.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/internal_server_error.mustache",
                        errorsDir,
                        "InternalServerError.swift"));

        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", srcDir, "HeaderSelector.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", srcDir, "ObjectSerializer.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "value_serializer.mustache", srcDir, "ValueSerializer.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", srcDir, "TraceContextUtil.swift"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", srcDir, "HTTPApiResponse.swift"));
        supportingFiles.add(
                new SupportingFile("api_result.mustache", srcDir, "ApiResult.swift"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", srcDir, "ApiClient.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", srcDir, "DefaultApiClient.swift"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache", Path.of(srcDir, "Api").toString(), "BaseApi.swift"));
        supportingFiles.add(
                new SupportingFile("authenticator.mustache", Path.of(srcDir, "Auth").toString(), "Authenticator.swift"));
        supportingFiles.add(
                new SupportingFile("any_codable.mustache", srcDir, "AnyCodable.swift"));

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        supportingFiles.add(
                new SupportingFile("client.mustache", srcDir, clientClassName + ".swift"));

        supportingFiles.add(
                new SupportingFile("package_swift.mustache", "", "Package.swift"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            final String testDir = Path.of("Tests", packageName + "Tests").toString();
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
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
                            "test/ObjectSerializerTests.mustache",
                            testDir,
                            "ObjectSerializerTests.swift"));
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
                            "test/BaseApiTests.mustache", testDir, "BaseApiTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ConfigurationTests.mustache",
                            testDir,
                            "ConfigurationTests.swift"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ClientTests.mustache",
                            testDir,
                            "ClientTests.swift"));
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

    /**
     * Fixes enum default values that the base class sets to
     * Java-style enum references (e.g. "StatusEnum.Placed").
     * For Swift, enum fields typed as String should use a
     * Swift string literal default.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            for (final CodegenProperty prop : model.vars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.allVars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.optionalVars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.requiredVars) {
                fixEnumDefaultValue(prop);
            }
        }
        return result;
    }

    private void fixEnumDefaultValue(CodegenProperty prop) {
        if (prop.defaultValue != null && prop.isEnum && prop.defaultValue.contains(".")) {
            final String enumValue = prop.defaultValue.substring(
                    prop.defaultValue.lastIndexOf('.') + 1);
            prop.defaultValue = "\"" + enumValue.toLowerCase(java.util.Locale.ROOT) + "\"";
        }
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
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spec.paramNames().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(spec.paramNames().get(i)).append(": String");
        }
        return sb.toString();
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
                    spec.keyIn() != null ? spec.keyIn().toLowerCase(java.util.Locale.ROOT) : "header");
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

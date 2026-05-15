package io.github.mridang.codegen.generators.csharp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a C# API client that uses HttpClient for transport
 * and System.Text.Json for JSON serialization. Targets .NET 9+
 * and follows PascalCase naming for variables, methods, and enum
 * members. Output is formatted with CSharpier to ensure
 * consistent style across all generated source files.
 */
@SuppressWarnings("unused")
public class BetterCSharpCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterCSharpCodegen.class);

    protected String sourceFolder = "src";
    protected String packageName = "OpenApi";

    /**
     * Initializes all C#-specific type mappings, language
     * primitives, instantiation types, and template file
     * registrations. Uses standard .NET types like DateOnly,
     * DateTimeOffset, and Guid for date, time, and UUID
     * schemas.
     */
    public BetterCSharpCodegen() {
        outputFolder = "generated-code/csharp";
        embeddedTemplateDir = templateDir = "templates/csharp";

        modelTemplateFiles.put("models/model.mustache", ".cs");
        apiTemplateFiles.put("api/api.mustache", ".cs");

        typeMapping.put("integer", "int");
        typeMapping.put("long", "long");
        typeMapping.put("float", "float");
        typeMapping.put("double", "double");
        typeMapping.put("number", "decimal");
        typeMapping.put("decimal", "decimal");
        typeMapping.put("boolean", "bool");
        typeMapping.put("string", "string");
        typeMapping.put("byte", "byte[]");
        typeMapping.put("binary", "System.IO.Stream");
        typeMapping.put("ByteArray", "byte[]");
        typeMapping.put("date", "DateOnly");
        typeMapping.put("DateTime", "DateTimeOffset");
        typeMapping.put("date-time", "DateTimeOffset");
        typeMapping.put("UUID", "Guid");
        typeMapping.put("URI", "string");
        typeMapping.put("object", "Object");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("array", "List");
        typeMapping.put("set", "HashSet");
        typeMapping.put("map", "Dictionary");
        typeMapping.put("File", "System.IO.Stream");
        typeMapping.put("file", "System.IO.Stream");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "long", "float", "double", "decimal", "bool", "string",
                                "byte[]", "void", "Object", "DateOnly", "DateTimeOffset", "Guid"));

        instantiationTypes.put("array", "List");
        instantiationTypes.put("set", "HashSet");
        instantiationTypes.put("map", "Dictionary");

        reservedWords = loadReservedWords("/reserved-words/csharp.txt");
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "csharp-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal C# client with System.Text.Json.";
    }

    /** {@inheritDoc} */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.C_SHARP;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "Test/Resources";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "Spec";
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
    protected String getFormatterDockerImage() {
        return "mcr.microsoft.com/dotnet/sdk:9.0";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {"dotnet tool restore", "dotnet csharpier ."};
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getParamCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "HashSet<";
    }

    /** {@inheritDoc} */
    @Override
    protected String getEmptyEnumVarName() {
        return "Empty";
    }

    /**
     * Processes user-supplied codegen options after they are
     * resolved. Reads the source folder and package name, then
     * registers all supporting files for the client skeleton,
     * exceptions, auth, serialization, and optional test
     * scaffolding.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        sourceFolder = getPropertyOrDefault(CodegenConstants.SOURCE_FOLDER, sourceFolder);
        packageName = getPropertyOrDefault(CodegenConstants.PACKAGE_NAME, packageName);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put("userAgentDefault", packageName + "/1.0.0 (csharp)");

        modelPackage = "Models";
        apiPackage = "Api";

        final String invokerFolder =
                Path.of(sourceFolder, packageName.replace(".", "/")).toString();

        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.cs"));
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.cs"));

        final String exceptionsFolder = Path.of(invokerFolder, "Exceptions").toString();
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ClientException.mustache",
                        exceptionsFolder,
                        "ClientException.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ServerException.mustache",
                        exceptionsFolder,
                        "ServerException.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/BadRequestException.mustache",
                        exceptionsFolder,
                        "BadRequestException.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/UnauthorizedException.mustache",
                        exceptionsFolder,
                        "UnauthorizedException.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ForbiddenException.mustache",
                        exceptionsFolder,
                        "ForbiddenException.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/NotFoundException.mustache",
                        exceptionsFolder,
                        "NotFoundException.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ConflictException.mustache",
                        exceptionsFolder,
                        "ConflictException.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/UnprocessableEntityException.mustache",
                        exceptionsFolder,
                        "UnprocessableEntityException.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/InternalServerErrorException.mustache",
                        exceptionsFolder,
                        "InternalServerErrorException.cs"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.cs"));
        supportingFiles.add(
                new SupportingFile("api_result.mustache", invokerFolder, "ApiResult.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        Path.of(invokerFolder, apiPackage).toString(),
                        "BaseApi.cs"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options.mustache", invokerFolder, "TransportOptions.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache",
                        invokerFolder,
                        "ServerConfiguration.cs"));
        supportingFiles.add(
                new SupportingFile("servers.mustache", invokerFolder, "Servers.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "value_serializer.mustache", invokerFolder, "ValueSerializer.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", invokerFolder, "TraceContextUtil.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "authenticator.mustache",
                        Path.of(invokerFolder, "Auth").toString(),
                        "IAuthenticator.cs"));
        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".cs"));
        supportingFiles.add(
                new SupportingFile("csproj.mustache", invokerFolder, packageName + ".csproj"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));
        supportingFiles.add(
                new SupportingFile("dotnet_tools.mustache", ".config", "dotnet-tools.json"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/tests_csproj.mustache", "", packageName + ".Test.csproj"));
            final String testApiFolder = Path.of("Test", "Api").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/PetApiTest.mustache",
                            testApiFolder,
                            "PetApiTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/StoreApiTest.mustache",
                            testApiFolder,
                            "StoreApiTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientTest.mustache",
                            "Test",
                            "DefaultApiClientTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientUnitTest.mustache",
                            "Test",
                            "DefaultApiClientUnitTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TransportOptionsTest.mustache",
                            "Test",
                            "TransportOptionsTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/HeaderSelectorTest.mustache",
                            "Test",
                            "HeaderSelectorTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ObjectSerializerTest.mustache",
                            "Test",
                            "ObjectSerializerTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ValueSerializerTest.mustache",
                            "Test",
                            "ValueSerializerTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/PrismFixture.mustache", "Test", "PrismFixture.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/WireMockSquidFixture.mustache",
                            "Test",
                            "WireMockSquidFixture.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/BaseApiTest.mustache",
                            "Test",
                            "BaseApiTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/MetadataTest.mustache",
                            "Test",
                            "MetadataTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ComposedSchemaTest.mustache",
                            "Test",
                            "ComposedSchemaTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TraceContextUtilTest.mustache",
                            "Test",
                            "TraceContextUtilTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ConfigurationTest.mustache",
                            "Test",
                            "ConfigurationTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ClientTest.mustache",
                            "Test",
                            "ClientTest.cs"));
        }
    }

    /**
     * Returns the output directory for model source files by
     * combining the output folder, source folder, package name,
     * and model package converted to a directory path.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, sourceFolder, packageName.replace(".", "/"), modelPackage)
                .toString();
    }

    /**
     * Returns the output directory for API source files by
     * combining the output folder, source folder, package name,
     * and API package converted to a directory path.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, sourceFolder, packageName.replace(".", "/"), apiPackage)
                .toString();
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
     * Returns {@code string} as the map key type because C#
     * dictionaries use string keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "string";
    }

    /**
     * Returns null for all schema types because C# uses
     * language-level defaults (null for reference types, zero
     * for value types) and explicit default expressions are
     * not needed in the generated models.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        return null;
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

    /** {@inheritDoc} */
    @Override
    protected String getMapDefaultValueType() {
        return "object";
    }

    /** {@inheritDoc} */
    @Override
    protected String getNullLiteral() {
        return "null";
    }

    /** {@inheritDoc} */
    @Override
    protected String getTrueLiteral() {
        return "true";
    }

    /** {@inheritDoc} */
    @Override
    protected String getFalseLiteral() {
        return "false";
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of(
                "int", "uint", "long", "ulong", "short", "ushort",
                "byte", "sbyte", "float", "double", "decimal");
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '"';
    }

    /** {@inheritDoc} */
    @Override
    protected String escapeEnumStringValue(String value) {
        return value.replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replaceAll("(?<!\\\\)\"", "\\\\\"");
    }

    /**
     * C# enum templates supply the surrounding double-quotes themselves
     * (e.g. {@code [JsonStringEnumMemberName("{{{value}}}")]}).
     * The base class {@code quoteEnumValue} must therefore be skipped;
     * only escape control characters.
     */
    @Override
    public String toEnumValue(String value, String datatype) {
        if (isNumericEnumDatatype(datatype)) {
            return value;
        }
        return escapeEnumStringValue(value);
    }

    /** {@inheritDoc} */
    @Override
    protected String httpAwareAuthenticatorStem() {
        return "i_http_aware_authenticator";
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of(sourceFolder, packageName.replace(".", "/"), "Auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getOAuthDir() {
        return Path.of(getAuthDir(), "OAuth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return pascalAuthFilename(stem, ".cs");
    }

    /** {@inheritDoc} */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();

        if (generateTests) {
            if (hasAnyOAuth2 || hasOpenIdConnect) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2TokenManagerTest.mustache",
                                "Test",
                                "OAuth2TokenManagerTest.cs"));
            }
            if (hasOAuth2AuthorizationCode) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2AuthCodeAuthenticatorTest.mustache",
                                "Test",
                                "OAuth2AuthCodeAuthenticatorTest.cs"));
            }
            if (hasOAuth2Implicit) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2ImplicitAuthenticatorTest.mustache",
                                "Test",
                                "OAuth2ImplicitAuthenticatorTest.cs"));
            }
            if (hasOAuth2ClientCredentials) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2ClientCredentialsAuthenticatorTest.mustache",
                                "Test",
                                "OAuth2ClientCredentialsAuthenticatorTest.cs"));
            }
            if (hasOAuth2Password) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2PasswordAuthenticatorTest.mustache",
                                "Test",
                                "OAuth2PasswordAuthenticatorTest.cs"));
            }
            if (hasOpenIdConnect) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OpenIdConnectAuthenticatorTest.mustache",
                                "Test",
                                "OpenIdConnectAuthenticatorTest.cs"));
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("StringConcatenationMissingWhitespace")
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only scheme values")
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final String scopes = formatCSharpScopes(spec.scopes());
        final String constructorSig;
        final String superCall;
        final String namespaceSuffix;

        if ("BasicAuthenticator".equals(spec.baseClass())) {
            constructorSig = "string host, string username, string password";
            superCall = "host, username, password";
            namespaceSuffix = null;
        } else if ("BearerAuthenticator".equals(spec.baseClass())) {
            constructorSig = "string host, string token";
            superCall = "host, token";
            namespaceSuffix = null;
        } else if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            final String loc = NamingConvention.PASCAL_CASE.apply(
                    spec.keyIn() != null
                            ? spec.keyIn().toLowerCase(Locale.ROOT)
                            : "header");
            constructorSig = "string host, string apiKey";
            superCall =
                    "host, \""
                            + spec.keyParamName()
                            + "\", apiKey, ApiKeyLocation."
                            + loc;
            namespaceSuffix = null;
        } else if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            constructorSig = "string host, string clientId, string clientSecret";
            superCall =
                    "host, clientId, clientSecret, new Uri(\""
                            + spec.tokenUrl()
                            + "\"), "
                            + scopes;
            namespaceSuffix = "OAuth";
        } else if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg =
                    spec.refreshUrl() != null
                            ? "new Uri(\"" + spec.refreshUrl() + "\")"
                            : "null";
            constructorSig =
                    "string host, string clientId, string clientSecret, "
                            + "string username, string password";
            superCall =
                    "host, clientId, clientSecret, new Uri(\""
                            + spec.tokenUrl()
                            + "\"), "
                            + refreshArg
                            + ", username, password, "
                            + scopes;
            namespaceSuffix = "OAuth";
        } else if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg =
                    spec.refreshUrl() != null
                            ? "new Uri(\"" + spec.refreshUrl() + "\")"
                            : "null";
            constructorSig =
                    "string host, string clientId, string clientSecret, Uri redirectUri";
            superCall =
                    "host, clientId, clientSecret, new Uri(\""
                            + spec.authorizationUrl()
                            + "\"), new Uri(\""
                            + spec.tokenUrl()
                            + "\"), "
                            + refreshArg
                            + ", redirectUri, "
                            + scopes;
            namespaceSuffix = "OAuth";
        } else if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            constructorSig = "string host, string clientId";
            superCall =
                    "host, clientId, new Uri(\""
                            + spec.authorizationUrl()
                            + "\"), "
                            + scopes;
            namespaceSuffix = "OAuth";
        } else if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            constructorSig =
                    "string host, string clientId, string clientSecret, Uri redirectUri";
            superCall =
                    "host, new Uri(\""
                            + spec.openIdConnectUrl()
                            + "\"), clientId, clientSecret, redirectUri, []";
            namespaceSuffix = "OAuth";
        } else {
            LOGGER.warn("Unsupported scheme base class: {}", spec.baseClass());
            return "";
        }

        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("imports", List.of());
        ctx.put("constructorSignature", constructorSig);
        ctx.put("superCall", superCall);
        if (namespaceSuffix != null) {
            ctx.put("namespaceSuffix", namespaceSuffix);
        }
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    private static String formatCSharpScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\", \"", scopes.keySet()) + "\"]";
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        boolean hasAnyModelImports = false;
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("pascalParamName", NamingConvention.PASCAL_CASE.apply(p.paramName));
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            params.add(param);
            if (!p.isPrimitiveType
                    && !p.isArray
                    && !p.isMap
                    && p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)) {
                hasAnyModelImports = true;
            }
            if ((p.isArray || p.isMap)
                    && p.items != null
                    && p.items.baseType != null
                    && !p.items.isPrimitiveType
                    && !languageSpecificPrimitives.contains(p.items.baseType)) {
                hasAnyModelImports = true;
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("packageName", packageName);
        context.put("className", className);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("hasModelImports", hasAnyModelImports);
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        return Path.of(
                        outputFolder,
                        sourceFolder,
                        packageName.replace(".", "/"),
                        "Api",
                        "Options",
                        optionsClassName + ".cs")
                .toString();
    }
}

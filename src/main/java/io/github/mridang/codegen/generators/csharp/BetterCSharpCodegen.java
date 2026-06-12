package io.github.mridang.codegen.generators.csharp;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
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
import org.openapitools.codegen.utils.ModelUtils;
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
        // 4.8: format:time → TimeOnly (.NET 6+), format:duration → TimeSpan
        // (rendered/parsed as ISO-8601 by Iso8601DurationConverter in
        // ObjectSerializer). TimeSpan's default JSON form is the .NET
        // "[d.]hh:mm:ss[.fff]" string, which would break interop with
        // every other language SDK that round-trips PT1H30M-style ISO
        // strings — hence the custom converter.
        typeMapping.put("time", "TimeOnly");
        typeMapping.put("duration", "TimeSpan");
        typeMapping.put("UUID", "Guid");
        typeMapping.put("URI", "Uri");
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
                                "byte[]", "void", "Object", "DateOnly", "DateTimeOffset", "Guid",
                                "TimeOnly", "TimeSpan"));

        instantiationTypes.put("array", "List");
        instantiationTypes.put("set", "HashSet");
        instantiationTypes.put("map", "Dictionary");

        reservedWords = loadReservedWords("/reserved-words/csharp.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated NuGet package (default: 1.0.0).").defaultValue("1.0.0"));
        cliOptions.add(CliOption.newString(CodegenConstants.SOURCE_FOLDER,
                CodegenConstants.SOURCE_FOLDER_DESC));
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
        // .NET 10 SDK has the matching dotnet-format tooling for net10.0 targets.
        return "mcr.microsoft.com/dotnet/sdk:10.0@sha256:c0790639332692a0d56cdd81ed581cfd24d040d9839764c138994866df89a3b6";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        // csharpier is the deterministic C# formatter (and the only one the
        // formatting spec verifies). `dotnet format style/analyzers` was only
        // applying info-level Roslyn suggestions, but it aborts with
        // "NotSupportedException: Changing document properties is not supported"
        // when an analyzer such as IDE1006 cannot Fix-All — a dotnet-format bug
        // that was previously masked with `|| true`. Dropping it keeps
        // formatting deterministic; `dotnet build --warnaserror`
        // (CSharpStaticAnalysisSpec) still guards warning-level analysis.
        return new String[] {"dotnet tool restore", "dotnet csharpier format ."};
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
     * Overrides the base class to add an {@code escapeXml} lambda that
     * escapes the XML metacharacters {@code &}, {@code <}, and {@code >}
     * in C# {@code ///} doc-comment prose. Schema descriptions, summaries,
     * and examples are copied verbatim into XML doc comments; an
     * unescaped {@code <} or {@code &} produces a CS1570 "XML comment has
     * badly formed XML" warning (an error under {@code -warnaserror}).
     * The lambda is applied only at prose sites in the model, api, and
     * options templates, never to code-emitting triple-mustache (type
     * names, defaults, paths).
     */
    @Override
    protected ImmutableMap.Builder<String, Mustache.Lambda> addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put(
                        "escapeXml",
                        (fragment, writer) ->
                                writer.write(
                                        fragment
                                                .execute()
                                                .replace("&", "&amp;")
                                                .replace("<", "&lt;")
                                                .replace(">", "&gt;")));
    }

    /**
     * Processes user-supplied codegen options after they are
     * resolved. Reads the source folder and package name, then
     * registers all supporting files for the client skeleton,
     * exceptions, auth, serialization, and optional test
     * scaffolding.
     */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String sf = Optional.ofNullable((String) additionalProperties.get(CodegenConstants.SOURCE_FOLDER))
                .orElse(sourceFolder);
        final String pkg = Optional.ofNullable((String) additionalProperties.get(CodegenConstants.PACKAGE_NAME))
                .orElse(packageName);
        final String invokerFolder = Path.of(sf, pkg.replace(".", "/")).toString();
        final String errorsFolder = Path.of(invokerFolder, "Errors").toString();
        return List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("api_client.mustache", invokerFolder, "ApiClient.cs"),
            new SupportingFileSpec("default_api_client.mustache", invokerFolder, "DefaultApiClient.cs"),
            new SupportingFileSpec("zitadel_exception.mustache", invokerFolder, "ZitadelException.cs"),
            new SupportingFileSpec("api_error.mustache", invokerFolder, "ApiException.cs"),
            new SupportingFileSpec("errors/ClientException.mustache", errorsFolder, "ClientException.cs"),
            new SupportingFileSpec("errors/ServerException.mustache", errorsFolder, "ServerException.cs"),
            new SupportingFileSpec("errors/BadRequestException.mustache", errorsFolder, "BadRequestException.cs"),
            new SupportingFileSpec("errors/UnauthorizedException.mustache", errorsFolder, "UnauthorizedException.cs"),
            new SupportingFileSpec("errors/ForbiddenException.mustache", errorsFolder, "ForbiddenException.cs"),
            new SupportingFileSpec("errors/NotFoundException.mustache", errorsFolder, "NotFoundException.cs"),
            new SupportingFileSpec("errors/ConflictException.mustache", errorsFolder, "ConflictException.cs"),
            new SupportingFileSpec("errors/UnprocessableEntityException.mustache", errorsFolder, "UnprocessableEntityException.cs"),
            new SupportingFileSpec("errors/InternalServerErrorException.mustache", errorsFolder, "InternalServerErrorException.cs"),
            new SupportingFileSpec("api_response.mustache", invokerFolder, "ApiHttpResponse.cs"),
            new SupportingFileSpec("api_result.mustache", invokerFolder, "ApiResult.cs"),
            new SupportingFileSpec("base_api.mustache", Path.of(invokerFolder, "Api").toString(), "BaseApi.cs"),
            new SupportingFileSpec("configuration.mustache", invokerFolder, "Configuration.cs"),
            new SupportingFileSpec("transport_options.mustache", invokerFolder, "TransportOptions.cs"),
            new SupportingFileSpec("server_configuration.mustache", invokerFolder, "ServerConfiguration.cs"),
            new SupportingFileSpec("servers.mustache", invokerFolder, "Servers.cs"),
            new SupportingFileSpec("object_serializer.mustache", invokerFolder, "ObjectSerializer.cs"),
            new SupportingFileSpec("value_serializer.mustache", invokerFolder, "ValueSerializer.cs"),
            new SupportingFileSpec("header_selector.mustache", invokerFolder, "HeaderSelector.cs"),
            new SupportingFileSpec("trace_context_util.mustache", invokerFolder, "TraceContextUtil.cs"),
            new SupportingFileSpec("authenticator.mustache", Path.of(invokerFolder, "Auth").toString(), "IAuthenticator.cs"),
            new SupportingFileSpec("csproj.mustache", invokerFolder, pkg + ".csproj"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore"),
            new SupportingFileSpec("dotnet_tools.mustache", ".config", "dotnet-tools.json"),
            new SupportingFileSpec("docfx.mustache", "", "docfx.json"),
            new SupportingFileSpec("docfx_index.mustache", "", "index.md"),
            new SupportingFileSpec("docfx_toc.mustache", "", "toc.yml"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile")
        );
    }

    @Override
    public void processOpts() {
        super.processOpts();

        sourceFolder = getPropertyOrDefault(CodegenConstants.SOURCE_FOLDER, sourceFolder);
        packageName = getPropertyOrDefault(CodegenConstants.PACKAGE_NAME, packageName);
        additionalProperties.put("packageName", packageName);
        final String packageVersion =
                getPropertyOrDefault(CodegenConstants.PACKAGE_VERSION, "1.0.0");
        additionalProperties.put("packageVersion", packageVersion);
        additionalProperties.put(
                "userAgentDefault", packageName + "/" + packageVersion + " (csharp)");

        modelPackage = "Models";
        apiPackage = "Api";

        final String invokerFolder =
                Path.of(sourceFolder, packageName.replace(".", "/")).toString();

        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".cs"));

        if (generateTests) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/tests_csproj.mustache", "", packageName + ".Test.csproj"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/xunit.runner.mustache", "", "xunit.runner.json"));
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
                            "test/ChasmFixture.mustache", "Test", "ChasmFixture.cs"));
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
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiExceptionTest.mustache",
                            "Test",
                            "ApiExceptionTest.cs"));
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

    /*
     * Returns null for all schema types because C# uses
     * language-level defaults (null for reference types, zero
     * for value types) and explicit default expressions are
     * not needed in the generated models.
     */
    /**
     * Returns a raw string value for string enum schemas that have a
     * declared OAS {@code default}. This value is then matched by
     * {@code updateCodegenPropertyEnum} and converted to the typed
     * enum form (e.g. {@code StatusEnum.Placed}). All other types
     * return null.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (ModelUtils.isStringSchema(resolved)
                && resolved.getDefault() != null
                && resolved.getEnum() != null
                && !resolved.getEnum().isEmpty()) {
            return resolved.getDefault().toString();
        }
        return null;
    }

    /**
     * Keeps the enum-reference default value (e.g.
     * {@code StatusEnum.Placed}) as produced by
     * {@code updateCodegenPropertyEnum}, rather than converting it
     * to a string literal as the base-class implementation would.
     */
    @Override
    protected void fixEnumDefaultValue(CodegenProperty prop, CodegenModel model) {
        // no-op: StatusEnum.Placed is the correct C# form
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
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean demotesDiscriminatorFromRequiredVars() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapDefaultValueType() {
        return "object";
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
    protected List<String[]> getEnumStringEscapes() {
        return List.of(
                new String[]{"\n", "\\\\n"},
                new String[]{"\t", "\\\\t"},
                new String[]{"\r", "\\\\r"},
                new String[]{"(?<!\\\\)\"", "\\\\\""});
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

    /**
     * The C# generic authenticator interface is named {@code IAuthenticator}
     * (the leading {@code I} follows the .NET interface convention). This name
     * is used for the optional per-operation {@code Auth} field folded into each
     * authed operation's Options object.
     */
    @Override
    protected String getAuthenticatorTypeName() {
        return "IAuthenticator";
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

    /* {@inheritDoc} */
    /** {@inheritDoc} */
    @Override
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        return List.of(
                new OAuthTestFileSpec("test/BasicAuthenticatorTest.mustache", "Test", "BasicAuthenticatorTest.cs", OAuthTestCondition.BASIC),
                new OAuthTestFileSpec("test/OAuth2TokenManagerTest.mustache", "Test", "OAuth2TokenManagerTest.cs", OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec("test/OAuth2AuthCodeAuthenticatorTest.mustache", "Test", "OAuth2AuthCodeAuthenticatorTest.cs", OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec("test/OAuth2ImplicitAuthenticatorTest.mustache", "Test", "OAuth2ImplicitAuthenticatorTest.cs", OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec("test/OAuth2ClientCredentialsAuthenticatorTest.mustache", "Test", "OAuth2ClientCredentialsAuthenticatorTest.cs", OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec("test/OAuth2PasswordAuthenticatorTest.mustache", "Test", "OAuth2PasswordAuthenticatorTest.cs", OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec("test/OpenIdConnectAuthenticatorTest.mustache", "Test", "OpenIdConnectAuthenticatorTest.cs", OAuthTestCondition.OIDC));
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
        injectAuthFieldContext(op, context);
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

    /**
     * URI subformat discrimination: only {@code format: uri} maps to
     * {@code System.Uri}. The {@code uri-reference} format may be relative
     * and {@code uri-template} contains RFC 6570 placeholders, so both stay
     * as plain {@code string}. See
     * {@link AbstractBetterCodegen#keepStringForUriSubformats}.
     */
    @Override
    public void postProcessModelProperty(
            org.openapitools.codegen.CodegenModel model,
            org.openapitools.codegen.CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        keepStringForUriSubformats(property, "string");
    }
}

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
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
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

    /**
     * The 501 type is not named {@code NotImplementedException}: that name is
     * {@code System.NotImplementedException}, which every file importing both
     * namespaces would see as ambiguous.
     */
    @Override
    protected String statusClassName(String stem) {
        if ("NotImplemented".equals(stem)) {
            return "NotImplementedStatusException";
        }
        return super.statusClassName(stem);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterCSharpCodegen.class);

    /** One entry per per-scheme authenticator, read by {@code SchemeAuthenticatorTest}. */
    private final List<Map<String, Object>> schemeAuthTests = new ArrayList<>();

    /**
     * The behaviour token of the empty-variable failure test of each named scheme authenticator:
     * authscheme003 to authscheme008 name the scheme, so one test name carries one id.
     */
    private static final Map<String, String> EMPTY_VARIABLE_BHV = Map.of(
            "PetStoreBasicAuthenticator", "BhvAuthscheme003",
            "AdminBasicAuthenticator", "BhvAuthscheme004",
            "ApiKeyHeaderAuthenticator", "BhvAuthscheme005",
            "ApiKeyQueryAuthenticator", "BhvAuthscheme006",
            "SessionCookieAuthenticator", "BhvAuthscheme007",
            "SsoAuthAuthenticator", "BhvAuthscheme008");

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
        apiTemplateFiles.put("api/api_interface.mustache", ".cs");

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
        // (rendered/parsed as protobuf-JSON by ProtobufDurationConverter in
        // ObjectSerializer). TimeSpan's default JSON form is the .NET
        // "[d.]hh:mm:ss[.fff]" string, which protobuf/gRPC-gateway backends
        // such as Zitadel reject with 400; the converter instead emits the
        // google.protobuf.Duration string form ("3600s", "1.5s"), matching
        // the wire format every other language SDK produces.
        typeMapping.put("time", "TimeOnly");
        typeMapping.put("duration", "TimeSpan");
        typeMapping.put("UUID", "Guid");
        typeMapping.put("URI", "Uri");
        typeMapping.put("ipv4", "System.Net.IPAddress");
        typeMapping.put("ipv6", "System.Net.IPAddress");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "object");
        typeMapping.put("array", "List");
        typeMapping.put("set", "HashSet");
        typeMapping.put("map", "Dictionary");
        typeMapping.put("File", "System.IO.Stream");
        typeMapping.put("file", "System.IO.Stream");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "long", "float", "double", "decimal", "bool", "string",
                                "byte[]", "void", "object", "DateOnly", "DateTimeOffset", "Guid",
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
        cliOptions.add(CliOption.newString("repositoryUrl",
                "Source repository URL, written to the package manifest (RepositoryUrl)."));
        cliOptions.add(CliOption.newString("homepageUrl",
                "Project home page, written to the package manifest (PackageProjectUrl)."));
        cliOptions.add(CliOption.newString("authors",
                "Package authors, written to the package manifest (Authors)."));
        cliOptions.add(CliOption.newString("documentationUrl",
                "Documentation URL, linked from the README."));
        cliOptions.add(CliOption.newString("issueTrackerUrl",
                "Issue tracker URL, linked from the README."));
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

    /**
     * Member names are PascalCased, and the PascalCase form of a C#
     * keyword (e.g. {@code Class}, {@code Return}) is itself a valid
     * identifier — C# keywords are lowercase. The inherited
     * {@code isReservedWord} check lowercases the candidate before
     * matching, so it would flag {@code Class}/{@code Return} and
     * {@link #escapeReservedWord} would prepend an underscore, producing
     * {@code _Class}/{@code _Return}, which CA1707 rejects. Returning
     * {@code false} (as Go and Node do) keeps the clean PascalCase name;
     * the original wire name is still preserved via the
     * {@code [JsonPropertyName("...")]} attribute emitted from
     * {@code baseName}.
     */
    @Override
    protected boolean shouldEscapeReservedVarName(String name) {
        return false;
    }

    /** Returns the generated model type names of every component schema. */
    private Set<String> componentModelNames() {
        final Set<String> names = new HashSet<>();
        if (openAPI != null
                && openAPI.getComponents() != null
                && openAPI.getComponents().getSchemas() != null) {
            for (final String key : openAPI.getComponents().getSchemas().keySet()) {
                names.add(toModelName(key));
            }
        }
        return names;
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

    /**
     * The path from the project folder up to the README at the output root, written with forward
     * slashes as MSBuild expects (for {@code src/Pkg} it is {@code ../../README.md}).
     */
    static String readmeRelativePath(final String projectFolder) {
        final StringBuilder path = new StringBuilder();
        for (final String ignored : projectFolder.replace('\\', '/').split("/", -1)) {
            if (!ignored.isEmpty()) {
                path.append("../");
            }
        }
        return path + "README.md";
    }

    /**
     * Gives an integer-enum member the same PascalCase naming as a string
     * enum member: {@code NUMBER_1} becomes {@code Number1} and
     * {@code NUMBER_MINUS_1} becomes {@code NumberMinus1} (F308).
     */
    @Override
    public String toEnumVarName(String value, String datatype) {
        final String name = super.toEnumVarName(value, datatype);
        if (!value.isEmpty() && isNumericEnumDatatype(datatype)) {
            return getEnumCasing().apply(name.toLowerCase(Locale.ROOT));
        }
        return name;
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getInjectedArgumentNames() {
        return Set.of(
                "options",
                "server",
                "cancellationToken",
                "requestContentType",
                "timeout",
                "headers",
                "maxRetries");
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
     * makes C# {@code ///} doc-comment prose well-formed XML without
     * HTML-entity-encoding it. Schema descriptions, summaries, and
     * examples are copied verbatim into XML doc comments; an unescaped
     * {@code <} or {@code &} produces a CS1570 "XML comment has badly
     * formed XML" warning (an error under {@code -warnaserror}).
     *
     * <p>Rather than rewriting {@code <}/{@code >}/{@code &} into their
     * HTML-entity forms — which the house
     * {@code generatedCodeShouldNotContainHtmlEntities} formatting spec
     * forbids — prose that contains any XML metacharacter is wrapped in a
     * {@code <![CDATA[ ... ]]>} section. CDATA is well-formed XML the C#
     * compiler and docfx both accept, and it preserves the literal
     * characters in the output. Prose with no metacharacters is emitted
     * unchanged. A literal {@code ]]>} in the text is split across two
     * CDATA sections (the standard XML technique) so it cannot terminate
     * the section early.
     *
     * <p>The lambda is applied only at prose sites in the model, api, and
     * options templates, never to code-emitting triple-mustache (type
     * names, defaults, paths).
     */
    @Override
    protected ImmutableMap.Builder<String, Mustache.Lambda> addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put(
                        "escapeXml",
                        (fragment, writer) ->
                                writer.write(continueDocCommentLines(wrapDocCommentXml(fragment.execute()))))
                .put(
                        "escapeXmlText",
                        (fragment, writer) -> writer.write(wrapDocCommentXml(fragment.execute())))
                .put(
                        "stringLiteral",
                        (fragment, writer) -> writer.write(escapeStringLiteralBody(fragment.execute())));
    }

    /**
     * Escapes text for the body of a C# regular string literal so the
     * literal evaluates to exactly the spec's characters: backslash and
     * double quote are escaped, and newline, carriage return, tab and the
     * other C0 controls use their escape sequences.
     */
    static String escapeStringLiteralBody(String text) {
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
                out.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Keeps multi-line prose inside the {@code ///} doc comment: every line
     * after the first is prefixed with {@code ///}, so a description with a
     * newline never leaks a bare line of text into the source.
     */
    private static String continueDocCommentLines(String text) {
        if (text == null) {
            return text;
        }
        return text.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\n/// ");
    }

    /**
     * Wraps {@code ///} doc-comment prose in a CDATA section when it
     * contains an XML metacharacter, leaving plain prose untouched. See
     * {@link #addMustacheLambdas()} for the rationale.
     */
    private static String wrapDocCommentXml(String text) {
        if (text == null
                || (text.indexOf('<') < 0 && text.indexOf('>') < 0 && text.indexOf('&') < 0)) {
            return text;
        }
        return "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>";
    }

    /** The adapter is built on the base class library, so csharp always ships it. */
    @Override
    protected boolean hasOpenTelemetryAdapter() {
        return true;
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
        final List<SupportingFileSpec> specs = new ArrayList<>(List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("api_client.mustache", invokerFolder, "ApiClient.cs"),
            new SupportingFileSpec("default_api_client.mustache", invokerFolder, "DefaultApiClient.cs"),
            new SupportingFileSpec("root_exception.mustache", errorsFolder, rootErrorName("Exception") + ".cs"),
            new SupportingFileSpec("api_error.mustache", errorsFolder, "ApiException.cs"),
            new SupportingFileSpec("errors/ErrorFactory.mustache", errorsFolder, "ErrorFactory.cs"),
            new SupportingFileSpec("errors/SerializationException.mustache", errorsFolder, "SerializationException.cs"),
            new SupportingFileSpec("errors/OAuth2ServerException.mustache", errorsFolder, "OAuth2ServerException.cs"),
            new SupportingFileSpec("errors/OAuth2TokenException.mustache", errorsFolder, "OAuth2TokenException.cs"),
            new SupportingFileSpec("errors/ClientException.mustache", errorsFolder, "ClientException.cs"),
            new SupportingFileSpec("errors/ServerException.mustache", errorsFolder, "ServerException.cs"),
            new SupportingFileSpec("errors/BadRequestException.mustache", errorsFolder, "BadRequestException.cs"),
            new SupportingFileSpec("errors/UnauthorizedException.mustache", errorsFolder, "UnauthorizedException.cs"),
            new SupportingFileSpec("errors/ForbiddenException.mustache", errorsFolder, "ForbiddenException.cs"),
            new SupportingFileSpec("errors/NotFoundException.mustache", errorsFolder, "NotFoundException.cs"),
            new SupportingFileSpec("errors/ConflictException.mustache", errorsFolder, "ConflictException.cs"),
            new SupportingFileSpec("errors/PaymentRequiredException.mustache", errorsFolder, "PaymentRequiredException.cs"),
            new SupportingFileSpec("errors/MethodNotAllowedException.mustache", errorsFolder, "MethodNotAllowedException.cs"),
            new SupportingFileSpec("errors/RequestTimeoutException.mustache", errorsFolder, "RequestTimeoutException.cs"),
            new SupportingFileSpec("errors/GoneException.mustache", errorsFolder, "GoneException.cs"),
            new SupportingFileSpec("errors/PayloadTooLargeException.mustache", errorsFolder, "PayloadTooLargeException.cs"),
            new SupportingFileSpec("errors/UnsupportedMediaTypeException.mustache", errorsFolder, "UnsupportedMediaTypeException.cs"),
            new SupportingFileSpec("errors/LockedException.mustache", errorsFolder, "LockedException.cs"),
            new SupportingFileSpec("errors/NotImplementedStatusException.mustache", errorsFolder, "NotImplementedStatusException.cs"),
            new SupportingFileSpec("errors/BadGatewayException.mustache", errorsFolder, "BadGatewayException.cs"),
            new SupportingFileSpec("errors/ServiceUnavailableException.mustache", errorsFolder, "ServiceUnavailableException.cs"),
            new SupportingFileSpec("errors/GatewayTimeoutException.mustache", errorsFolder, "GatewayTimeoutException.cs"),
            new SupportingFileSpec("errors/UnprocessableEntityException.mustache", errorsFolder, "UnprocessableEntityException.cs"),
            new SupportingFileSpec("errors/TooManyRequestsException.mustache", errorsFolder, "TooManyRequestsException.cs"),
            new SupportingFileSpec("errors/InternalServerErrorException.mustache", errorsFolder, "InternalServerErrorException.cs"),
            new SupportingFileSpec("errors/NetworkException.mustache", errorsFolder, "NetworkException.cs"),
            new SupportingFileSpec("errors/NetworkTimeoutException.mustache", errorsFolder, "NetworkTimeoutException.cs"),
            new SupportingFileSpec("errors/StreamException.mustache", errorsFolder, "StreamException.cs"),
            new SupportingFileSpec("api_response.mustache", invokerFolder, "ApiHttpResponse.cs"),
            new SupportingFileSpec("api_stream_response.mustache", invokerFolder, "ApiStreamResponse.cs"),
            new SupportingFileSpec("byte_stream.mustache", invokerFolder, "ByteStream.cs"),
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
            new SupportingFileSpec("call_hook.mustache", invokerFolder, "ICallHook.cs"),
            new SupportingFileSpec("call_observer.mustache", invokerFolder, "ICallObserver.cs"),
            new SupportingFileSpec("call_info.mustache", invokerFolder, "CallInfo.cs"),
            new SupportingFileSpec("call_outcome.mustache", invokerFolder, "CallOutcome.cs"),
            new SupportingFileSpec("chained_call_hook.mustache", invokerFolder, "ChainedCallHook.cs"),
            new SupportingFileSpec(
                "open_telemetry_call_hook.mustache", invokerFolder, "OpenTelemetryCallHook.cs"),
            new SupportingFileSpec("authenticator.mustache", Path.of(invokerFolder, "Auth").toString(), "IAuthenticator.cs"),
            new SupportingFileSpec("illink_descriptors.mustache", invokerFolder, "ILLink.Descriptors.xml"),
            new SupportingFileSpec("csproj.mustache", invokerFolder, pkg + ".csproj"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore"),
            new SupportingFileSpec("dotnet_tools.mustache", ".config", "dotnet-tools.json"),
            new SupportingFileSpec("docfx.mustache", "", "docfx.json"),
            new SupportingFileSpec("docfx_index.mustache", "", "index.md"),
            new SupportingFileSpec("docfx_toc.mustache", "", "toc.yml"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec(
                "dev_dependencies.mustache", ".openapi-generator", "DEV-DEPENDENCIES")
        ));
        if (generateExamples) {
            /* The quickstart is a project of its own next to the test project, which leaves
             * examples/** out of its compile items so the two entry points never meet. */
            final String quickstartFolder = Path.of("examples", "Quickstart").toString();
            specs.add(new SupportingFileSpec(
                "examples/Quickstart.csproj.mustache", quickstartFolder, "Quickstart.csproj"));
            specs.add(new SupportingFileSpec(
                "examples/Program.cs.mustache", quickstartFolder, "Program.cs"));
        }
        return specs;
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
        additionalProperties.put("readmeRelativePath", readmeRelativePath(invokerFolder));
        final Object repositoryUrl = additionalProperties.get("repositoryUrl");
        additionalProperties.put(
                "repositoryIsGitHub",
                repositoryUrl != null
                        && repositoryUrl.toString().toLowerCase(Locale.ROOT).contains("github.com"));

        additionalProperties.put("schemeAuthTests", schemeAuthTests);

        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".cs"));
        supportingFiles.add(
                new SupportingFile(
                        "client_interface.mustache", invokerFolder, "I" + clientClassName + ".cs"));

        if (emitUnitTests()) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/tests_csproj.mustache", "", packageName + ".Test.csproj"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/xunit.runner.mustache", "", "xunit.runner.json"));
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
                            "test/ValueSerializerTest.mustache",
                            "Test",
                            "ValueSerializerTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/SerializerProbeModel.mustache",
                            "Test",
                            "SerializerProbeModel.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TraceContextUtilTest.mustache",
                            "Test",
                            "TraceContextUtilTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/CallHookTest.mustache", "Test", "CallHookTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/OpenTelemetryCallHookTest.mustache",
                            "Test",
                            "OpenTelemetryCallHookTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ConfigurationTest.mustache",
                            "Test",
                            "ConfigurationTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ServerConfigurationTest.mustache",
                            "Test",
                            "ServerConfigurationTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ServerVariableTest.mustache",
                            "Test",
                            "ServerVariableTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ApiResultTest.mustache",
                            "Test",
                            "ApiResultTest.cs"));
        }
        if (generateTests) {
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
                            "test/ObjectSerializerTest.mustache",
                            "Test",
                            "ObjectSerializerTest.cs"));
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
     * Registers the Bearer and API-key authenticator tests once the spec has
     * been parsed, so each is emitted only when the spec declares that scheme.
     * The scheme flags are set in {@code processOpenAPI}, after
     * {@code processOpts}, so they cannot be read there.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();
        if (!generateTests) {
            return;
        }
        if (hasBearerAuth) {
            supportingFiles.add(new SupportingFile(
                    "test/BearerAuthenticatorTest.mustache", "Test", "BearerAuthenticatorTest.cs"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(new SupportingFile(
                    "test/ApiKeyAuthenticatorTest.mustache", "Test", "ApiKeyAuthenticatorTest.cs"));
        }
        if (Boolean.TRUE.equals(additionalProperties.get("hasSecuritySchemeEnvVars"))) {
            supportingFiles.add(new SupportingFile(
                    "test/SchemeAuthenticatorTest.mustache", "Test", "SchemeAuthenticatorTest.cs"));
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
     * Returns the C# default-value literal for a schema with a
     * declared OAS {@code default}.
     *
     * <p>String enum schemas return the raw wire value (e.g.
     * {@code medium}); this is later matched by
     * {@code updateCodegenPropertyEnum} and converted to the typed
     * enum form (e.g. {@code ModeEnum.Medium}). Non-enum integer,
     * number and boolean schemas return their string representation
     * (a valid C# numeric/boolean literal). Non-enum string schemas
     * return the value wrapped in double quotes. All other schemas,
     * and schemas with no default, return null.
     *
     * <p>Emitting the default as a property initializer makes an
     * absent JSON key fall back to the schema default while an
     * explicit JSON null is still preserved: System.Text.Json only
     * overwrites the initializer when the key is present.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (resolved.getDefault() == null) {
            return null;
        }
        final boolean isEnum = resolved.getEnum() != null && !resolved.getEnum().isEmpty();
        if (ModelUtils.isStringSchema(resolved)) {
            if (isEnum) {
                return resolved.getDefault().toString();
            }
            return "\"" + escapeStringLiteralBody(String.valueOf(resolved.getDefault())) + "\"";
        }
        if (ModelUtils.isNumberSchema(resolved) && !isEnum) {
            /*
             * The literal must have the field's exact type: a bare
             * double literal does not convert to decimal (CS0664) or
             * float, so decimal-typed numbers get the m suffix and
             * format: float gets f. Plain text keeps the exact value.
             */
            final Object value = resolved.getDefault();
            final String literal = value instanceof java.math.BigDecimal
                    ? ((java.math.BigDecimal) value).toPlainString()
                    : value.toString();
            final String format = resolved.getFormat();
            if ("float".equals(format)) {
                return literal + "f";
            }
            if ("double".equals(format)) {
                return literal;
            }
            return literal + "m";
        }
        if (ModelUtils.isIntegerSchema(resolved)
                || ModelUtils.isNumberSchema(resolved)
                || ModelUtils.isBooleanSchema(resolved)) {
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

    /**
     * The template supplies the quotes (see {@link #toEnumValue}), so the
     * quote is escaped here, unconditionally and after the shared backslash
     * doubling, instead of by {@code quoteEnumValue}.
     */
    @Override
    protected List<String[]> getEnumStringEscapes() {
        return List.<String[]>of(new String[]{"\"", "\\\\\""});
    }

    /** The quote is already escaped by the table, so only wrap the value. */
    @Override
    protected String toServerEnumLiteral(String value) {
        return "\"" + escapeEnumStringValue(value) + "\"";
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
                new OAuthTestFileSpec("test/OAuth2AuthCodeAuthenticatorTest.mustache", "Test", "OAuth2AuthorizationCodeAuthenticatorTest.cs", OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec("test/OAuth2ImplicitAuthenticatorTest.mustache", "Test", "OAuth2ImplicitAuthenticatorTest.cs", OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec("test/OAuth2ClientCredentialsAuthenticatorTest.mustache", "Test", "OAuth2ClientCredentialsAuthenticatorTest.cs", OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec("test/OAuth2PasswordAuthenticatorTest.mustache", "Test", "OAuth2PasswordAuthenticatorTest.cs", OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec("test/OpenIdConnectAuthenticatorTest.mustache", "Test", "OpenIdConnectAuthenticatorTest.cs", OAuthTestCondition.OIDC));
    }

    /**
     * Renders the per-scheme authenticator. Every credential argument is optional: left null it
     * is read once, in the constructor, from the scheme's environment variable (F862), and a
     * credential that is neither passed nor set fails there. The variable names come from the
     * shared {@code envVars} entries of {@link #baseSchemeContext}, so all twelve languages read
     * the same names.
     */
    @Override
    @SuppressWarnings("StringConcatenationMissingWhitespace")
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only scheme values")
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final String scopes = formatCSharpScopes(spec.scopes());
        final Map<String, Object> ctx = baseSchemeContext(spec);
        final Map<String, String> envNames = new HashMap<>();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> envVars =
                (List<Map<String, Object>>) java.util.Objects.requireNonNull(ctx.get("envVars"));
        for (final Map<String, Object> envVar : envVars) {
            envNames.put(String.valueOf(envVar.get("field")), String.valueOf(envVar.get("envName")));
        }
        final String namespaceSuffix;
        final String superCall;
        final String baseClass = spec.baseClass();
        final String refreshArg =
                spec.refreshUrl() != null ? "new Uri(\"" + spec.refreshUrl() + "\")" : "null";

        if ("BasicAuthenticator".equals(baseClass)) {
            superCall = "host, " + credential(envNames, "username") + ", "
                    + credential(envNames, "password");
            namespaceSuffix = null;
        } else if ("BearerAuthenticator".equals(baseClass)) {
            superCall = "host, " + credential(envNames, "token");
            namespaceSuffix = null;
            ctx.put("isBearer", true);
        } else if ("ApiKeyAuthenticator".equals(baseClass)) {
            final String loc = NamingConvention.PASCAL_CASE.apply(
                    spec.keyIn() != null
                            ? spec.keyIn().toLowerCase(Locale.ROOT)
                            : "header");
            superCall =
                    "host, \""
                            + escapeStringLiteralBody(java.util.Objects.toString(spec.keyParamName(), ""))
                            + "\", " + credential(envNames, "apiKey") + ", ApiKeyLocation."
                            + loc;
            namespaceSuffix = null;
        } else if ("OAuth2ClientCredentialsAuthenticator".equals(baseClass)) {
            superCall =
                    "host, " + credential(envNames, "clientId") + ", "
                            + credential(envNames, "clientSecret") + ", new Uri(\""
                            + spec.tokenUrl()
                            + "\"), "
                            + scopes;
            namespaceSuffix = "OAuth";
        } else if ("OAuth2PasswordAuthenticator".equals(baseClass)) {
            superCall =
                    "host, " + credential(envNames, "clientId") + ", "
                            + credential(envNames, "clientSecret") + ", new Uri(\""
                            + spec.tokenUrl()
                            + "\"), " + credential(envNames, "username") + ", "
                            + credential(envNames, "password") + ", "
                            + scopes
                            + ", "
                            + refreshArg;
            namespaceSuffix = "OAuth";
        } else if ("OAuth2AuthorizationCodeAuthenticator".equals(baseClass)) {
            superCall =
                    "host, " + credential(envNames, "clientId") + ", "
                            + credential(envNames, "clientSecret") + ", new Uri(\""
                            + spec.authorizationUrl()
                            + "\"), new Uri(\""
                            + spec.tokenUrl()
                            + "\"), " + credential(envNames, "redirectUri") + ", "
                            + scopes
                            + ", "
                            + refreshArg;
            namespaceSuffix = "OAuth";
        } else if ("OAuth2ImplicitAuthenticator".equals(baseClass)) {
            superCall =
                    "host, " + credential(envNames, "clientId") + ", new Uri(\""
                            + spec.authorizationUrl()
                            + "\"), "
                            + scopes;
            namespaceSuffix = "OAuth";
        } else if ("OpenIdConnectAuthenticator".equals(baseClass)) {
            superCall =
                    "host, new Uri(\""
                            + spec.openIdConnectUrl()
                            + "\"), " + credential(envNames, "clientId") + ", "
                            + credential(envNames, "clientSecret") + ", "
                            + credential(envNames, "redirectUri") + ", []";
            namespaceSuffix = "OAuth";
        } else {
            LOGGER.warn("Unsupported scheme base class: {}", baseClass);
            return "";
        }

        final List<Map<String, Object>> credentialParams = new ArrayList<>();
        for (final Map<String, Object> envVar : envVars) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", envVar.get("field"));
            param.put("envName", envVar.get("envName"));
            param.put("isUri", isUriField(String.valueOf(envVar.get("field"))));
            credentialParams.add(param);
        }
        ctx.put("imports", List.of());
        ctx.put("credentialParams", credentialParams);
        ctx.put("superCall", superCall);
        if (namespaceSuffix != null) {
            ctx.put("namespaceSuffix", namespaceSuffix);
        }
        recordSchemeAuthTest(spec, namespaceSuffix, envVars);
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    /** Whether a credential field is a {@code Uri} in C#, read from its variable as text. */
    private static boolean isUriField(final String field) {
        return "redirectUri".equals(field);
    }

    /**
     * The expression that resolves one credential field: the argument, else its environment
     * variable, else a failure. A {@code Uri} field is parsed from the variable's text.
     */
    private static String credential(final Map<String, String> envNames, final String field) {
        final String variable = envNames.get(field);
        return (isUriField(field) ? "ResolveUri(" : "ResolveCredential(")
                + field + ", \"" + variable + "\")";
    }

    /**
     * Records what {@code SchemeAuthenticatorTest} needs to exercise one per-scheme authenticator:
     * its class, its base kind and its environment variables.
     */
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "keyIn values are ASCII-only OpenAPI location strings (header/query/cookie)")
    private void recordSchemeAuthTest(
            final SchemeAuthSpec spec,
            @Nullable final String namespaceSuffix,
            final List<Map<String, Object>> envVars) {
        final Map<String, Object> entry = new HashMap<>();
        entry.put("className", spec.schemeClass() + spec.oauthSuffix() + "Authenticator");
        final String emptyVariableBhv = EMPTY_VARIABLE_BHV.getOrDefault((String) entry.get("className"), "");
        entry.put("emptyVariableBhv", emptyVariableBhv);
        entry.put("hasEmptyVariableBhv", !emptyVariableBhv.isEmpty());
        if (namespaceSuffix != null) {
            entry.put("namespaceSuffix", namespaceSuffix);
        }
        entry.put("isBasic", "BasicAuthenticator".equals(spec.baseClass()));
        entry.put("isBearer", "BearerAuthenticator".equals(spec.baseClass()));
        entry.put("isApiKey", "ApiKeyAuthenticator".equals(spec.baseClass()));
        entry.put("isOAuth", spec.isOAuth());
        final String baseClass = spec.baseClass();
        entry.put("isPasswordFlow", "OAuth2PasswordAuthenticator".equals(baseClass));
        entry.put("isClientCredentialsFlow", "OAuth2ClientCredentialsAuthenticator".equals(baseClass));
        entry.put("isAuthorizationCodeFlow", "OAuth2AuthorizationCodeAuthenticator".equals(baseClass));
        entry.put("isImplicitFlow", "OAuth2ImplicitAuthenticator".equals(baseClass));
        entry.put("tokenUrl", java.util.Objects.toString(spec.tokenUrl(), ""));
        entry.put("authorizationUrl", java.util.Objects.toString(spec.authorizationUrl(), ""));
        entry.put("scopesLiteral", formatCSharpScopes(spec.scopes()));
        final String keyIn = java.util.Objects.toString(spec.keyIn(), "header").toLowerCase(Locale.ROOT);
        entry.put("apiKeyName", java.util.Objects.toString(spec.keyParamName(), ""));
        entry.put("isHeaderKey", "header".equals(keyIn));
        entry.put("isQueryKey", "query".equals(keyIn));
        entry.put("isCookieKey", "cookie".equals(keyIn));
        final List<Map<String, Object>> vars = new ArrayList<>();
        for (final Map<String, Object> envVar : envVars) {
            final Map<String, Object> var = new HashMap<>();
            var.put("field", envVar.get("field"));
            var.put("envName", envVar.get("envName"));
            var.put("isUri", isUriField(String.valueOf(envVar.get("field"))));
            vars.add(var);
        }
        entry.put("envVars", vars);
        schemeAuthTests.add(entry);
    }

    /** C# reserved words that cannot name a client argument without an at-sign prefix. */
    private static final Set<String> CSHARP_KEYWORDS = Set.of(
            "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked",
            "class", "const", "continue", "decimal", "default", "delegate", "do", "double", "else",
            "enum", "event", "explicit", "extern", "false", "finally", "fixed", "float", "for",
            "foreach", "goto", "if", "implicit", "in", "int", "interface", "internal", "is", "lock",
            "long", "namespace", "new", "null", "object", "operator", "out", "override", "params",
            "private", "protected", "public", "readonly", "ref", "return", "sbyte", "sealed",
            "short", "sizeof", "stackalloc", "static", "string", "struct", "switch", "this",
            "throw", "true", "try", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort",
            "using", "virtual", "void", "volatile", "while");

    /**
     * Computes the {@code clientSchemes} property (F857): one entry per distinct security scheme
     * with the client argument named after the scheme and the class of its authenticator.
     */
    @Override
    public void processOpenAPI(final io.swagger.v3.oas.models.OpenAPI openAPI) {
        super.processOpenAPI(openAPI);
        final List<Map<String, Object>> clientSchemes = new ArrayList<>();
        if (openAPI.getComponents() != null
                && openAPI.getComponents().getSecuritySchemes() != null) {
            for (final Map.Entry<String, io.swagger.v3.oas.models.security.SecurityScheme> entry :
                    openAPI.getComponents().getSecuritySchemes().entrySet()) {
                final SchemeAuthSpec spec = buildSchemeAuthSpec(entry.getKey(), entry.getValue());
                if (spec == null) {
                    continue;
                }
                final String arg = NamingConvention.CAMEL_CASE.apply(entry.getKey());
                final Map<String, Object> scheme = new HashMap<>();
                scheme.put("schemeName", entry.getKey());
                scheme.put("paramName", arg);
                scheme.put("argName", CSHARP_KEYWORDS.contains(arg) ? "@" + arg : arg);
                scheme.put("className", spec.schemeClass() + spec.oauthSuffix() + "Authenticator");
                if (spec.isOAuth()) {
                    scheme.put("namespaceSuffix", "OAuth");
                }
                clientSchemes.add(scheme);
            }
        }
        additionalProperties.put("clientSchemes", clientSchemes);
        additionalProperties.put("hasClientSchemes", !clientSchemes.isEmpty());
    }

    /** {@inheritDoc} */
    @Override
    protected boolean mintsOptionsForEveryOperation() {
        return true;
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
        /* The shared pass that attaches the named examples runs after the
         * options files are written, so attach them to these parameters first. */
        capturePluralExamplesForParameters(optionsParams);
        final List<Map<String, Object>> params = new ArrayList<>();
        boolean hasAnyModelImports = false;
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("pascalParamName", NamingConvention.PASCAL_CASE.apply(p.paramName));
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            param.put("requiredNullable", isRequiredNullableQuery(p));
            param.put("deprecated", p.isDeprecated);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            final Object pluralExamples =
                    p.vendorExtensions == null ? null : p.vendorExtensions.get("pluralExamples");
            if (pluralExamples != null) {
                param.put("pluralExamples", pluralExamples);
            }
            params.add(param);
            /* A content-based parameter (content: application/json) carries
             * the model type in dataType only; baseType stays null. */
            if (p.dataType != null && componentModelNames().contains(p.dataType)) {
                hasAnyModelImports = true;
            }
            if (!p.isPrimitiveType
                    && !p.isArray
                    && !p.isMap
                    && p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)) {
                hasAnyModelImports = true;
            }
            // A $ref to a top-level enum carries its enum type name in dataType
            // (baseType is null) and the generated enum lives in the Models
            // namespace, so the Options class still needs the Models import.
            if (p.isEnumRef
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)) {
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
    protected String errorSchemaTemplate() {
        return "errors/SchemaException.mustache";
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaFilePath(String className) {
        return Path.of(outputFolder, sourceFolder, packageName.replace(".", "/"), "Errors", className + ".cs")
                .toString();
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

    /**
     * Shapes the per-operation signature for C#: the per-operation server comes before the options
     * object (required parameters, then server, then options, then the trailing cancellation
     * token), so adding security to an operation never moves an existing argument; the trailing
     * {@code CancellationToken} needs a leading comma exactly when an argument precedes it; and
     * header, path and cookie parameters declared with a JSON {@code content} media type carry a
     * {@code contentJson} flag so the template routes them through the SDK serializer.
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        final OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);
        final Map<String, Object> operations = (Map<String, Object>) result.get("operations");
        if (operations == null) {
            return result;
        }
        final List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
        if (ops == null) {
            return result;
        }
        for (final CodegenOperation op : ops) {
            if (op.vendorExtensions == null) {
                op.vendorExtensions = new HashMap<>();
            }
            final Object existing = op.vendorExtensions.get("op");
            final Map<String, Object> deco =
                    existing instanceof Map ? (Map<String, Object>) existing : new HashMap<>();
            final Object sigArgs = deco.get("signatureArgs");
            boolean hasArgs = false;
            if (sigArgs instanceof List) {
                final List<Map<String, Object>> reordered =
                        serverBeforeOptions((List<Map<String, Object>>) sigArgs);
                deco.put("signatureArgs", reordered);
                hasArgs = !reordered.isEmpty();
            }
            final boolean multiConsume = op.consumes != null && op.consumes.size() > 1;
            deco.put("cancellationLeadComma", hasArgs || multiConsume);
            op.vendorExtensions.put("op", deco);
            markBinaryStream(op, deco);
            reorderAuthMethodsToSpecOrder(op, deco);
            flagJsonContent(op.pathParams);
            flagJsonContent(op.headerParams);
            flagJsonContent(op.cookieParams);
        }
        return result;
    }

    /**
     * A binary success response is returned as a {@code ByteStream} by the one operation method
     * (D119), so the return type becomes {@code ByteStream} and the decorator
     * {@code streamsBinary} tells the template to call {@code InvokeApiForStreamAsync}.
     */
    static void markBinaryStream(final CodegenOperation op, final Map<String, Object> deco) {
        final boolean binary = "binary".equals(deco.get("streamKind"));
        deco.put("streamsBinary", binary);
        if (binary) {
            op.returnType = "ByteStream";
            op.returnBaseType = "ByteStream";
        }
    }

    /**
     * Hands the README and SKILLS the first operation that returns a {@code ByteStream}, as
     * {@code binaryStreamOperation} (with {@code binaryStreamGroup}, its operations map, and
     * {@code hasBinaryStreamOperation}), so the Streaming example calls a real operation of the
     * spec under generation. Nothing is rendered when the spec has none.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
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
                                        ((Map<String, Object>) decorators).get("streamsBinary"))) {
                            found = op;
                            group = operations;
                        }
                    }
                }
            }
        }
        objs.put("hasBinaryStreamOperation", found != null);
        if (found != null) {
            objs.put("binaryStreamOperation", found);
            objs.put("binaryStreamGroup", group);
        }
        return super.postProcessSupportingFileData(objs);
    }

    /**
     * Puts the operation's auth methods in the order its own security block lists them (F857).
     * Core lists them in component declaration order; the first scheme an operation names that
     * the client holds supplies its credential, so the list follows the shared
     * {@code securitySchemeOrder}. Schemes not in that order keep their place at the end.
     */
    @SuppressWarnings("unchecked")
    private static void reorderAuthMethodsToSpecOrder(
            final CodegenOperation op, final Map<String, Object> deco) {
        final Object order = deco.get("securitySchemeOrder");
        if (op.authMethods == null || !(order instanceof List)) {
            return;
        }
        final List<String> names = new ArrayList<>();
        for (final Object entry : (List<Object>) order) {
            names.add(String.valueOf(((Map<String, Object>) entry).get("schemeId")));
        }
        final int unlisted = names.size();
        final List<org.openapitools.codegen.CodegenSecurity> sorted =
                new ArrayList<>(op.authMethods);
        sorted.sort(
                java.util.Comparator.comparingInt(
                        am -> names.contains(am.name) ? names.indexOf(am.name) : unlisted));
        op.authMethods = sorted;
    }

    /**
     * Returns the operation signature arguments with the per-operation server placed before the
     * optional-parameters object: required parameters, then server, then options. A required
     * options object stays before the server, because C# optional arguments must come last.
     *
     * @param args the shared signature arguments (path, body, options, server)
     * @return a new list with the server entry directly before the options entry
     */
    static List<Map<String, Object>> serverBeforeOptions(final List<Map<String, Object>> args) {
        for (final Map<String, Object> arg : args) {
            if (Boolean.TRUE.equals(arg.get("isOptions"))
                    && !Boolean.TRUE.equals(arg.get("nullable"))) {
                /* A required options object (it holds a required parameter) is a required
                 * argument, and C# requires every optional argument to come after it. */
                return new ArrayList<>(args);
            }
        }
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
     * Sets {@code vendorExtensions.contentJson} on each parameter that is declared with a JSON
     * {@code content} media type instead of a {@code schema}.
     */
    private static void flagJsonContent(@Nullable final List<CodegenParameter> params) {
        if (params == null) {
            return;
        }
        for (final CodegenParameter param : params) {
            if (isJsonContent(param)) {
                param.vendorExtensions.put("contentJson", true);
            }
        }
    }

    /**
     * Reports whether {@code param} is declared with {@code content} holding a JSON media type
     * ({@code application/json} or any {@code +json} type).
     *
     * @param param the parameter
     * @return true for a JSON content-encoded parameter
     */
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only media type names")
    static boolean isJsonContent(final CodegenParameter param) {
        if (param.getContent() == null) {
            return false;
        }
        for (final String mediaType : param.getContent().keySet()) {
            final String lower = mediaType.toLowerCase(Locale.ROOT);
            final int semicolon = lower.indexOf(';');
            final String bare = (semicolon < 0 ? lower : lower.substring(0, semicolon)).trim();
            if ("application/json".equals(bare) || bare.endsWith("+json")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks each model file that references an {@code [Obsolete]} member so the
     * template can emit a single file-level {@code #pragma warning disable
     * CS0618} on exactly those files.
     *
     * <p>Generated models must still initialize, compare, hash, and serialize
     * their own deprecated properties — and properties whose element type is a
     * deprecated schema — which the C# compiler flags as CS0618. The
     * {@code // <auto-generated/>} header does not silence CS0618 (it is a
     * core compiler diagnostic, not an analyzer), so a file-scoped pragma is
     * the correct file-level directive. It is emitted only where the finding
     * genuinely occurs; clean model files carry no pragma.
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);

        final Set<String> deprecatedModels = new HashSet<>();
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();
                if (model.isDeprecated) {
                    deprecatedModels.add(model.classname);
                }
            }
        }

        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();
                if (referencesDeprecatedMember(model, deprecatedModels)) {
                    modelMap.put("referencesDeprecated", true);
                }
                nameUnionVariants(model);
                markGuardedRequiredProperties(model);
            }
        }
        return result;
    }

    /**
     * Flags the required, non-null reference properties whose null is refused at decode
     * (F316). The model keeps no check in its constructors or init accessors; the flag makes
     * the template emit one {@code OnDeserialized} check per flagged property, so a wrong
     * value fails loudly only when a payload is decoded. The conditions mirror the template's
     * choice of a guarded backing field.
     */
    private static void markGuardedRequiredProperties(final CodegenModel model) {
        boolean any = false;
        for (final CodegenProperty property : model.vars) {
            if (Boolean.TRUE.equals(property.vendorExtensions.get("x-inherited"))) {
                // A classic allOf child reads an inherited property through its base,
                // whose OnDeserialized owns the check (the backing field is private to it).
                continue;
            }
            final boolean hasDefault =
                    property.defaultValue != null && !property.defaultValue.isEmpty();
            final boolean referenceTyped =
                    property.isContainer
                            || !property.isPrimitiveType
                            || property.isString
                            || property.isByteArray
                            || property.isBinary;
            if (property.required
                    && !hasDefault
                    && !property.isNullable
                    && !property.isEnum
                    && !property.isEnumRef
                    && referenceTyped) {
                property.vendorExtensions.put("guardedRequired", true);
                any = true;
            }
        }
        if (any) {
            model.vendorExtensions.put("hasGuardedRequired", true);
        }
    }

    /**
     * Gives every oneOf and anyOf variant a PascalCase name for the typed
     * {@code From<Variant>}, {@code Is<Variant>} and {@code As<Variant>}
     * members of the union wrapper: the variant type with every non-letter
     * and non-digit dropped and each word capitalised, so {@code Reservation}
     * stays {@code Reservation} and {@code List<string>} becomes
     * {@code ListString}.
     */
    private static void nameUnionVariants(CodegenModel model) {
        if (model.getComposedSchemas() == null) {
            return;
        }
        nameVariants(model.getComposedSchemas().getOneOf());
        nameVariants(model.getComposedSchemas().getAnyOf());
    }

    private static void nameVariants(List<CodegenProperty> variants) {
        if (variants == null) {
            return;
        }
        for (final CodegenProperty variant : variants) {
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
            if (variant.vendorExtensions == null) {
                variant.vendorExtensions = new HashMap<>();
            }
            variant.vendorExtensions.put("variantName", name.toString());
        }
    }

    /**
     * Returns {@code true} when generating {@code model} produces a reference
     * to an {@code [Obsolete]} member: the model itself is deprecated, it has a
     * deprecated property, or a property's (element) type is a deprecated
     * schema.
     */
    private static boolean referencesDeprecatedMember(
            CodegenModel model, Set<String> deprecatedModels) {
        if (model.isDeprecated) {
            return true;
        }
        for (final CodegenProperty prop : model.allVars) {
            if (prop.deprecated
                    || deprecatedModels.contains(prop.complexType)
                    || (prop.items != null && deprecatedModels.contains(prop.items.complexType))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Names the group interface file with the C# {@code I} prefix ({@code IPetApi.cs}); the group
     * class file keeps the plain name.
     *
     * @param templateName the API template being rendered
     * @param tag the API group
     * @return the output path
     */
    @Override
    public String apiFilename(final String templateName, final String tag) {
        if ("api/api_interface.mustache".equals(templateName)) {
            return apiFileFolder()
                    + java.io.File.separator
                    + "I"
                    + toApiFilename(tag)
                    + apiTemplateFiles().get(templateName);
        }
        return super.apiFilename(templateName, tag);
    }
}

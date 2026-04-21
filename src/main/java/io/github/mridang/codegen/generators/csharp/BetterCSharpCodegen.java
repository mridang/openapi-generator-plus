package io.github.mridang.codegen.generators.csharp;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
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

    private static final NamingConvention VAR_CASING = NamingConvention.PASCAL_CASE;
    private static final NamingConvention OPERATION_ID_CASING = NamingConvention.PASCAL_CASE;
    private static final NamingConvention ENUM_CASING = NamingConvention.PASCAL_CASE;

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

    /**
     * Returns the unique generator name used by the OpenAPI
     * Generator plugin system to identify this codegen.
     */
    @Override
    public String getName() {
        return "csharp-plus";
    }

    /**
     * Returns a short human-readable description of this
     * codegen shown in the generator list and help output.
     */
    @Override
    public String getHelp() {
        return "Generates a minimal C# client with System.Text.Json.";
    }

    /**
     * Returns the relative path to the directory where test
     * fixture files like certificates and WireMock mappings
     * are placed inside the generated project.
     */
    @Override
    protected String getTestFixturesDir() {
        return "Test/Resources";
    }

    /**
     * Returns the relative path to the directory where
     * user-written spec tests should be placed inside the
     * generated project.
     */
    @Override
    protected String getSpecDir() {
        return "Spec";
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

    /**
     * Returns {@code string} as the map key type because C#
     * dictionaries use string keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "string";
    }

    /**
     * Converts a sanitized parameter name to camelCase for C#
     * method parameters. PascalCase is applied first, then the
     * first character is lowered to produce camelCase which is
     * the standard C# parameter naming convention.
     */
    @Override
    public String toParamName(String name) {
        final String sanitized = sanitizeName(name);
        String result = NamingConvention.PASCAL_CASE.apply(sanitized);
        if (isReservedWord(result) || result.matches("^\\d.*")) {
            result = escapeReservedWord(result);
        }
        if (!result.isEmpty()) {
            result = Character.toLowerCase(result.charAt(0)) + result.substring(1);
        }
        return result;
    }

    /**
     * Returns null for all schema types because C# uses
     * language-level defaults (null for reference types, zero
     * for value types) and explicit default expressions are
     * not needed in the generated models.
     */
    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        return null;
    }

    /**
     * Applies PascalCase casing to a sanitized variable name
     * because C# conventions require all property and variable
     * names to use PascalCase.
     */
    @Override
    protected String applyVarNameCasing(String name) {
        return VAR_CASING.apply(name);
    }

    /**
     * Formats a sanitized operation ID into C#'s PascalCase
     * method naming convention using the configured operation
     * ID casing strategy.
     */
    @Override
    protected String formatOperationId(String sanitizedOperationId) {
        return OPERATION_ID_CASING.apply(sanitizedOperationId);
    }

    /**
     * Derives a PascalCase property name for the client facade
     * from the API class name by stripping the trailing "Api"
     * suffix and applying PascalCase casing.
     */
    @Override
    protected String deriveClientPropertyName(String apiClassName) {
        final String name = apiClassName.replaceAll("Api$", "");
        if (name.isEmpty()) {
            return "Api";
        }
        return VAR_CASING.apply(name);
    }

    /**
     * Returns whether the given datatype represents a numeric
     * C# type. Used to avoid quoting numeric enum values in
     * generated enum classes.
     */
    @Override
    protected boolean isNumericEnumDatatype(String datatype) {
        return datatype.startsWith("int")
                || datatype.startsWith("uint")
                || datatype.startsWith("long")
                || datatype.startsWith("ulong")
                || datatype.startsWith("double")
                || datatype.startsWith("float");
    }

    /**
     * Converts an enum value to its PascalCase constant name.
     * Returns "Empty" for blank values, prefixes numeric values
     * with "NUMBER_", and sanitizes special characters for valid
     * C# identifiers.
     */
    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value.isEmpty()) {
            return "Empty";
        }

        if (isNumericEnumDatatype(datatype)) {
            String varName = "NUMBER_" + value;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return varName;
        }

        final String spaced = value.replaceAll(" ", "_");
        String var = ENUM_CASING.apply(spaced);
        var = var.replaceAll("\\W+", "");

        if (var.matches("\\d.*")) {
            return "_" + var;
        }
        return var;
    }

    /**
     * Returns the enum literal value for C#. Numeric types are
     * returned as bare values while string types have newlines,
     * tabs, carriage returns, and unescaped quotes escaped to
     * produce valid C# string literals.
     */
    @Override
    public String toEnumValue(String value, String datatype) {
        if (datatype.startsWith("int")
                || datatype.startsWith("uint")
                || datatype.startsWith("long")
                || datatype.startsWith("ulong")
                || datatype.startsWith("byte")) {
            return value;
        }
        return value.replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replaceAll("(?<!\\\\)\"", "\\\\\"");
    }

    /**
     * Escapes double-quote characters in generated string
     * literals by replacing them with backslash-escaped quotes
     * to prevent syntax errors in C# source output.
     */
    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("\"", "\\\"");
    }

    /**
     * Handles unique-item arrays by swapping List types for
     * HashSet in property declarations after standard
     * post-processing so that C# models correctly represent
     * OpenAPI uniqueItems constraints.
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (property.isArray && property.getUniqueItems()) {
            property.datatypeWithEnum =
                    property.datatypeWithEnum.replaceFirst("^List<", "HashSet<");
            property.dataType = property.dataType.replaceFirst("^List<", "HashSet<");
        }
    }

    /**
     * Strips primitive parent types from models after standard
     * post-processing. Primitive parents arise from allOf with
     * base types like string or List and would generate invalid
     * inheritance clauses in C#.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            stripPrimitiveParent(model);
        }
        return result;
    }

    /**
     * Registers supporting files for each authentication scheme
     * present in the OpenAPI spec. Files are placed under the
     * Auth subdirectory within the invoker folder. Only the auth
     * classes actually needed by the spec are emitted to keep
     * the generated client minimal.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String invokerFolder =
                Path.of(sourceFolder, packageName.replace(".", "/")).toString();
        final String authFolder = Path.of(invokerFolder, "Auth").toString();
        final String oauthFolder = Path.of(authFolder, "OAuth").toString();

        supportingFiles.add(
                new SupportingFile(
                        "auth/base_authenticator.mustache", authFolder, "BaseAuthenticator.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        authFolder,
                        "IHttpAwareAuthenticator.cs"));
        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic_authenticator.mustache",
                            authFolder,
                            "BasicAuthenticator.cs"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer_authenticator.mustache",
                            authFolder,
                            "BearerAuthenticator.cs"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_authenticator.mustache",
                            authFolder,
                            "ApiKeyAuthenticator.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_location.mustache", authFolder, "ApiKeyLocation.cs"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_token_manager.mustache",
                            oauthFolder,
                            "OAuth2TokenManager.cs"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_client_credentials_authenticator.mustache",
                            oauthFolder,
                            "OAuth2ClientCredentialsAuthenticator.cs"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_password_authenticator.mustache",
                            oauthFolder,
                            "OAuth2PasswordAuthenticator.cs"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_auth_code_authenticator.mustache",
                            oauthFolder,
                            "OAuth2AuthorizationCodeAuthenticator.cs"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_implicit_authenticator.mustache",
                            oauthFolder,
                            "OAuth2ImplicitAuthenticator.cs"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/openid_connect_authenticator.mustache",
                            oauthFolder,
                            "OpenIdConnectAuthenticator.cs"));
        }
    }

    /**
     * No-op for C#: per-scheme authenticators are not generated
     * because the base authenticator classes are sufficient with
     * scheme-specific constructor parameters.
     */
    @Override
    protected void generatePerSchemeAuthenticators(OpenAPI openAPI) {
        // Per-scheme authenticators are not generated for C#
    }

    /**
     * Runs CSharpier inside a Docker container to format all
     * generated C# source files. Uses the .NET 9.0 SDK image
     * and restores dotnet tools before formatting.
     */
    @Override
    public void postProcess() {
        runFormatterInDocker(
                "mcr.microsoft.com/dotnet/sdk:9.0",
                "dotnet tool restore",
                "dotnet csharpier .");
    }
}

package io.github.mridang.codegen.generators.csharp;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
        }
    }

    /**
     * Sets the parent of discriminator subtypes so that
     * System.Text.Json polymorphic deserialization works
     * correctly with {@code [JsonDerivedType]}.
     */
    @Override
    public java.util.Map<String, org.openapitools.codegen.model.ModelsMap> postProcessAllModels(
            java.util.Map<String, org.openapitools.codegen.model.ModelsMap> objs) {
        final java.util.Map<String, org.openapitools.codegen.model.ModelsMap> result =
                super.postProcessAllModels(objs);
        for (final org.openapitools.codegen.model.ModelsMap modelsMap : result.values()) {
            for (final org.openapitools.codegen.model.ModelMap modelMap : modelsMap.getModels()) {
                final org.openapitools.codegen.CodegenModel model = modelMap.getModel();
                if (model.discriminator != null && !model.oneOf.isEmpty()) {
                    for (final org.openapitools.codegen.CodegenDiscriminator.MappedModel mapped :
                            model.discriminator.getMappedModels()) {
                        final org.openapitools.codegen.model.ModelsMap childModels =
                                result.get(mapped.getModelName());
                        if (childModels != null) {
                            for (final org.openapitools.codegen.model.ModelMap cm :
                                    childModels.getModels()) {
                                final org.openapitools.codegen.CodegenModel child = cm.getModel();
                                if (child.parent == null) {
                                    child.parent = model.classname;
                                    child.parentSchema = model.classname;
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
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

    /**
     * Overrides the base class because C# needs special character
     * escaping ({@code \\n}, {@code \\t}, {@code \\r}, unescaped
     * {@code "}) for string enum values. Cannot be standardized
     * because other languages don't need this escaping.
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

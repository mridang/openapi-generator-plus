package io.github.mridang.codegen.generators.csharp;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.HashSet;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates a C# API client using HttpClient and System.Text.Json for serialization. */
@SuppressWarnings("unused")
public class BetterCSharpCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterCSharpCodegen.class);

    protected String sourceFolder = "src";
    protected String packageName = "OpenApi";

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

    @Override
    public String getName() {
        return "csharp-plus";
    }

    @Override
    protected String getTestFixturesDir() {
        return "Test/Resources";
    }

    @Override
    protected String getSpecDir() {
        return "Spec";
    }

    @Override
    public String getHelp() {
        return "Generates a minimal C# client with System.Text.Json.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        sourceFolder = getPropertyOrDefault(CodegenConstants.SOURCE_FOLDER, sourceFolder);
        packageName = getPropertyOrDefault(CodegenConstants.PACKAGE_NAME, packageName);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put("userAgentDefault", packageName + "/1.0.0 (csharp)");

        modelPackage = "Models";
        apiPackage = "Api";

        String invokerFolder =
                sourceFolder + File.separator + packageName.replace(".", File.separator);

        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.cs"));
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.cs"));

        String exceptionsFolder = invokerFolder + File.separator + "Exceptions";
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
                        invokerFolder + File.separator + apiPackage,
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
                        invokerFolder + File.separator + "Auth",
                        "IAuthenticator.cs"));
        String clientClassName = (String) additionalProperties.get("clientClassName");
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
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/PetApiTest.mustache",
                            "Test" + File.separator + "Api",
                            "PetApiTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/StoreApiTest.mustache",
                            "Test" + File.separator + "Api",
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

    @Override
    public String modelFileFolder() {
        return outputFolder
                + File.separator
                + sourceFolder
                + File.separator
                + packageName.replace(".", File.separator)
                + File.separator
                + modelPackage;
    }

    @Override
    public String apiFileFolder() {
        return outputFolder
                + File.separator
                + sourceFolder
                + File.separator
                + packageName.replace(".", File.separator)
                + File.separator
                + apiPackage;
    }

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        ModelsMap result = super.postProcessModels(objs);
        for (ModelMap modelMap : result.getModels()) {
            CodegenModel model = modelMap.getModel();
            if (model.parent != null) {
                String baseParent = model.parent;
                int genericIdx = baseParent.indexOf('<');
                if (genericIdx >= 0) {
                    baseParent = baseParent.substring(0, genericIdx);
                }
                if (languageSpecificPrimitives.contains(baseParent)
                        || typeMapping.containsValue(baseParent)) {
                    model.parent = null;
                    model.parentModel = null;
                }
            }
        }
        return result;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (property.isArray && property.getUniqueItems()) {
            property.datatypeWithEnum =
                    property.datatypeWithEnum.replaceFirst("^List<", "HashSet<");
            property.dataType = property.dataType.replaceFirst("^List<", "HashSet<");
        }
    }

    @Override
    protected String applyVarNameCasing(String name) {
        return StringUtils.camelize(name);
    }

    @Override
    protected String formatOperationId(String sanitizedOperationId) {
        return StringUtils.camelize(sanitizedOperationId);
    }

    @Override
    protected String deriveClientPropertyName(String apiClassName) {
        String name = apiClassName.replaceAll("Api$", "");
        if (name.isEmpty()) {
            return "Api";
        }
        return StringUtils.camelize(name);
    }


    @Override
    protected String getMapKeyType() {
        return "string";
    }

    @Override
    public String toParamName(String name) {
        name = sanitizeName(name);
        name = StringUtils.camelize(name);
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        if (!name.isEmpty()) {
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        return null;
    }

    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value.isEmpty()) {
            return "Empty";
        }

        if (datatype.startsWith("int")
                || datatype.startsWith("uint")
                || datatype.startsWith("long")
                || datatype.startsWith("ulong")
                || datatype.startsWith("double")
                || datatype.startsWith("float")) {
            String varName = "NUMBER_" + value;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return varName;
        }

        String var = value.replaceAll(" ", "_");
        var = StringUtils.camelize(var);
        var = var.replaceAll("\\W+", "");

        if (var.matches("\\d.*")) {
            return "_" + var;
        }
        return var;
    }

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

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input;
    }

    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("\"", "\\\"");
    }

    @Override
    protected void registerAuthSupportingFiles() {
        String invokerFolder =
                sourceFolder + File.separator + packageName.replace(".", File.separator);
        String authFolder = invokerFolder + File.separator + "Auth";
        String oauthFolder = authFolder + File.separator + "OAuth";

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

    @Override
    protected void generatePerSchemeAuthenticators(OpenAPI openAPI) {
        // Per-scheme authenticators are not generated for C#
    }

    @Override
    public void postProcess() {
        runFormatterInDocker(
                "mcr.microsoft.com/dotnet/sdk:9.0",
                "dotnet tool restore",
                "dotnet csharpier .");
    }
}

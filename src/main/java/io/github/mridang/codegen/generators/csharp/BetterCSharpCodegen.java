package io.github.mridang.codegen.generators.csharp;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.SupportingFile;
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
        typeMapping.put("map", "Dictionary");
        typeMapping.put("File", "System.IO.Stream");
        typeMapping.put("file", "System.IO.Stream");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "long", "float", "double", "decimal", "bool", "string",
                                "byte[]", "void", "Object", "DateOnly", "DateTimeOffset", "Guid"));

        instantiationTypes.put("array", "List");
        instantiationTypes.put("map", "Dictionary");

        reservedWords = loadReservedWords("/reserved-words/csharp.txt");
    }

    @Override
    public String getName() {
        return "csharp-plus";
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
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        invokerFolder + File.separator + apiPackage,
                        "BaseApi.cs"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "authenticator.mustache",
                        invokerFolder + File.separator + "Auth",
                        "IAuthenticator.cs"));
        supportingFiles.add(
                new SupportingFile("client.mustache", invokerFolder, "Client.cs"));
        supportingFiles.add(
                new SupportingFile("csproj.mustache", invokerFolder, packageName + ".csproj"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));
        supportingFiles.add(
                new SupportingFile("dotnet_tools.mustache", ".config", "dotnet-tools.json"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));

        if (generateTests) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/tests_csproj.mustache", "", packageName + ".Tests.csproj"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/PetApiTest.mustache",
                            "Tests" + File.separator + "Api",
                            "PetApiTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/StoreApiTest.mustache",
                            "Tests" + File.separator + "Api",
                            "StoreApiTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientTest.mustache",
                            "Tests",
                            "DefaultApiClientTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientUnitTest.mustache",
                            "Tests",
                            "DefaultApiClientUnitTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/HeaderSelectorTest.mustache",
                            "Tests",
                            "HeaderSelectorTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ObjectSerializerTest.mustache",
                            "Tests",
                            "ObjectSerializerTest.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/PrismFixture.mustache", "Tests", "PrismFixture.cs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/WireMockSquidFixture.mustache",
                            "Tests",
                            "WireMockSquidFixture.cs"));
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

    private static final int CSHARPIER_PRINT_WIDTH = 100;

    private static final Pattern LONG_ARRAY_PATTERN =
            Pattern.compile(
                    "^(    private static readonly string\\[\\] \\w+ =) \\[(.+)\\];$",
                    Pattern.MULTILINE);

    private static final Pattern LONG_METHOD_PATTERN =
            Pattern.compile(
                    "^(    public async \\S+ \\w+)\\((.+)\\)$", Pattern.MULTILINE);

    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);
        if (file == null || !file.getName().endsWith(".cs")) {
            return;
        }
        try {
            String content = Files.readString(file.toPath());
            String trimmed = content;
            // Remove blank line after opening brace before field declarations
            trimmed = trimmed.replaceAll("\\{\n\n(    private static)", "{\n$1");
            // Remove blank lines between consecutive field declarations
            trimmed = trimmed.replaceAll("(\\];\n)\n(    private static)", "$1$2");
            // Break long inline array declarations to multi-line
            trimmed = breakLongLines(LONG_ARRAY_PATTERN, trimmed, BetterCSharpCodegen::breakArray);
            // Break long method signatures to multi-line
            trimmed = breakLongLines(LONG_METHOD_PATTERN, trimmed, BetterCSharpCodegen::breakMethod);
            if (!trimmed.equals(content)) {
                Files.write(file.toPath(), trimmed.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to post-process file: {}", file.getAbsolutePath(), e);
        }
    }

    @FunctionalInterface
    private interface LineBreaker {
        String breakLine(String prefix, String inner);
    }

    private static String breakLongLines(Pattern pattern, String content, LineBreaker breaker) {
        Matcher matcher = pattern.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            if (matcher.group(0).length() <= CSHARPIER_PRINT_WIDTH) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(breaker.breakLine(matcher.group(1), matcher.group(2))));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String breakArray(String prefix, String elements) {
        String[] parts = elements.split(", ");
        StringBuilder sb = new StringBuilder(prefix);
        sb.append("\n    [\n");
        for (String part : parts) {
            sb.append("        ").append(part).append(",\n");
        }
        sb.append("    ];");
        return sb.toString();
    }

    private static String breakMethod(String prefix, String params) {
        String[] parts = params.split(", ");
        StringBuilder sb = new StringBuilder(prefix);
        sb.append("(\n");
        for (int i = 0; i < parts.length; i++) {
            sb.append("        ").append(parts[i]);
            if (i < parts.length - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("    )");
        return sb.toString();
    }
}

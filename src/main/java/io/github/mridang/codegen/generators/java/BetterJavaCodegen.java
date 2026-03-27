package io.github.mridang.codegen.generators.java;

import static org.openapitools.codegen.utils.CamelizeOption.LOWERCASE_FIRST_LETTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.ImportOrderer;
import com.google.googlejavaformat.java.JavaFormatterOptions;
import com.google.googlejavaformat.java.RemoveUnusedImports;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates a Java API client using Apache HttpClient and Jackson for serialization. */
@SuppressWarnings("unused")
public class BetterJavaCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterJavaCodegen.class);

    private final Formatter formatter;
    protected String sourceFolder = "src" + File.separator + "main" + File.separator + "java";
    protected String invokerPackage = "org.openapitools";

    public BetterJavaCodegen() {
        outputFolder = "generated-code/java";
        embeddedTemplateDir = templateDir = "templates/java";

        modelTemplateFiles.put("models/model.mustache", ".java");
        apiTemplateFiles.put("api/api.mustache", ".java");

        typeMapping.put("array", "List");
        typeMapping.put("map", "Map");
        typeMapping.put("set", "Set");
        typeMapping.put("boolean", "Boolean");
        typeMapping.put("string", "String");
        typeMapping.put("int", "Integer");
        typeMapping.put("integer", "Integer");
        typeMapping.put("long", "Long");
        typeMapping.put("short", "Short");
        typeMapping.put("float", "Float");
        typeMapping.put("double", "Double");
        typeMapping.put("number", "BigDecimal");
        typeMapping.put("decimal", "BigDecimal");
        typeMapping.put("char", "String");
        typeMapping.put("object", "Object");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("binary", "InputStream");
        typeMapping.put("ByteArray", "byte[]");
        typeMapping.put("byte", "byte[]");
        typeMapping.put("file", "InputStream");
        typeMapping.put("File", "InputStream");
        typeMapping.put("date", "LocalDate");
        typeMapping.put("DateTime", "OffsetDateTime");
        typeMapping.put("date-time", "OffsetDateTime");
        typeMapping.put("UUID", "UUID");
        typeMapping.put("URI", "URI");
        typeMapping.put("BigDecimal", "BigDecimal");

        importMapping.put("List", "java.util.List");
        importMapping.put("Set", "java.util.Set");
        importMapping.put("Map", "java.util.Map");
        importMapping.put("ArrayList", "java.util.ArrayList");
        importMapping.put("Arrays", "java.util.Arrays");
        importMapping.put("LinkedHashSet", "java.util.LinkedHashSet");
        importMapping.put("HashMap", "java.util.HashMap");
        importMapping.put("LocalDate", "java.time.LocalDate");
        importMapping.put("OffsetDateTime", "java.time.OffsetDateTime");
        importMapping.put("BigDecimal", "java.math.BigDecimal");
        importMapping.put("UUID", "java.util.UUID");
        importMapping.put("URI", "java.net.URI");
        importMapping.put("File", "java.io.File");
        importMapping.put("InputStream", "java.io.InputStream");
        importMapping.put("JsonProperty", "com.fasterxml.jackson.annotation.JsonProperty");
        importMapping.put("JsonValue", "com.fasterxml.jackson.annotation.JsonValue");
        importMapping.put("JsonCreator", "com.fasterxml.jackson.annotation.JsonCreator");
        importMapping.put("JsonInclude", "com.fasterxml.jackson.annotation.JsonInclude");
        importMapping.put("JsonTypeName", "com.fasterxml.jackson.annotation.JsonTypeName");
        importMapping.put("JsonTypeInfo", "com.fasterxml.jackson.annotation.JsonTypeInfo");
        importMapping.put("JsonSubTypes", "com.fasterxml.jackson.annotation.JsonSubTypes");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "long", "float", "double", "boolean", "byte", "short",
                                "char", "Integer", "Long", "Float", "Double", "Boolean", "String",
                                "Object", "byte[]", "void"));

        instantiationTypes.put("array", "ArrayList");
        instantiationTypes.put("set", "LinkedHashSet");
        instantiationTypes.put("map", "HashMap");

        reservedWords = loadReservedWords("/reserved-words/java.txt");

        formatter =
                new Formatter(
                        JavaFormatterOptions.builder()
                                .style(JavaFormatterOptions.Style.GOOGLE)
                                .build());
    }

    @Override
    public String getName() {
        return "java-plus";
    }

    @Override
    public String getHelp() {
        return "Generates a minimal Java client with Jackson and Apache HttpClient.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        sourceFolder = getPropertyOrDefault(CodegenConstants.SOURCE_FOLDER, sourceFolder);
        invokerPackage = getPropertyOrDefault(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
        additionalProperties.put("invokerPackage", invokerPackage);
        additionalProperties.put("userAgentDefault", invokerPackage + "/1.0.0 (java)");

        String invokerFolder =
                sourceFolder + File.separator + invokerPackage.replace(".", File.separator);
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.java"));

        String exceptionsFolder = invokerFolder + File.separator + "exceptions";
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ClientException.mustache",
                        exceptionsFolder,
                        "ClientException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ServerException.mustache",
                        exceptionsFolder,
                        "ServerException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/BadRequestException.mustache",
                        exceptionsFolder,
                        "BadRequestException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/UnauthorizedException.mustache",
                        exceptionsFolder,
                        "UnauthorizedException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ForbiddenException.mustache",
                        exceptionsFolder,
                        "ForbiddenException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/NotFoundException.mustache",
                        exceptionsFolder,
                        "NotFoundException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ConflictException.mustache",
                        exceptionsFolder,
                        "ConflictException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/UnprocessableEntityException.mustache",
                        exceptionsFolder,
                        "UnprocessableEntityException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/InternalServerErrorException.mustache",
                        exceptionsFolder,
                        "InternalServerErrorException.java"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.java"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.java"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.java"));
        supportingFiles.add(
                new SupportingFile("api_result.mustache", invokerFolder, "ApiResult.java"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        invokerFolder + File.separator + "api",
                        "BaseApi.java"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.java"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options.mustache", invokerFolder, "TransportOptions.java"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache",
                        invokerFolder,
                        "ServerConfiguration.java"));
        supportingFiles.add(
                new SupportingFile(
                        "server_variable.mustache", invokerFolder, "ServerVariable.java"));
        supportingFiles.add(
                new SupportingFile("servers.mustache", invokerFolder, "Servers.java"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.java"));
        supportingFiles.add(
                new SupportingFile(
                        "value_serializer.mustache", invokerFolder, "ValueSerializer.java"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.java"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", invokerFolder, "TraceContextUtil.java"));
        supportingFiles.add(new SupportingFile("pom.mustache", "", "pom.xml"));
        supportingFiles.add(
                new SupportingFile(
                        "authenticator.mustache",
                        invokerFolder + File.separator + "auth",
                        "Authenticator.java"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        invokerFolder + File.separator + "auth",
                        "HttpAwareAuthenticator.java"));
        supportingFiles.add(
                new SupportingFile("client.mustache", invokerFolder, "Client.java"));
        supportingFiles.add(
                new SupportingFile("spotbugs_exclude.mustache", "", "spotbugs-exclude.xml"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            String testFolder =
                    "src" + File.separator + "test" + File.separator + "java" + File.separator
                            + invokerPackage.replace(".", File.separator);
            String testApiFolder = testFolder + File.separator + "api";
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/PetApiTest.mustache", testApiFolder, "PetApiTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/StoreApiTest.mustache", testApiFolder, "StoreApiTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientTest.mustache",
                            testFolder,
                            "DefaultApiClientTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientUnitTest.mustache",
                            testFolder,
                            "DefaultApiClientUnitTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TransportOptionsTest.mustache",
                            testFolder,
                            "TransportOptionsTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/HeaderSelectorTest.mustache",
                            testFolder,
                            "HeaderSelectorTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ObjectSerializerTest.mustache",
                            testFolder,
                            "ObjectSerializerTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ValueSerializerTest.mustache",
                            testFolder,
                            "ValueSerializerTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TraceContextUtilTest.mustache",
                            testFolder,
                            "TraceContextUtilTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/PrismContainer.mustache", testFolder, "PrismContainer.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/SquidContainer.mustache", testFolder, "SquidContainer.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/WireMockContainer.mustache",
                            testFolder,
                            "WireMockContainer.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/BaseApiTest.mustache",
                            testFolder,
                            "BaseApiTest.java"));
            String testModelsFolder = testFolder + File.separator + "models";
            supportingFiles.add(
                    new SupportingFile(
                            "test/MetadataTest.mustache",
                            testModelsFolder,
                            "MetadataTest.java"));
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
        }
    }

    @Override
    public String modelFileFolder() {
        return outputFolder
                + File.separator
                + sourceFolder
                + File.separator
                + modelPackage().replace('.', File.separatorChar);
    }

    @Override
    public String apiFileFolder() {
        return outputFolder
                + File.separator
                + sourceFolder
                + File.separator
                + apiPackage().replace('.', File.separatorChar);
    }

    @Override
    protected String applyVarNameCasing(String name) {
        if (name.matches("^[A-Z0-9_]*$")) {
            return name;
        }
        return StringUtils.camelize(name, LOWERCASE_FIRST_LETTER);
    }

    @Override
    protected String formatOperationId(String sanitizedOperationId) {
        return StringUtils.camelize(sanitizedOperationId, LOWERCASE_FIRST_LETTER);
    }

    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        schema = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (ModelUtils.isArraySchema(schema)) {
            if (Boolean.TRUE.equals(schema.getUniqueItems())) {
                return "new LinkedHashSet<>()";
            }
            return "new ArrayList<>()";
        } else if (ModelUtils.isMapSchema(schema)) {
            return "new HashMap<>()";
        }
        return null;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (!model.isEnum) {
            model.imports.add("JsonProperty");
            model.imports.add("JsonInclude");
            model.imports.add("JsonTypeName");
            if (property.isEnum) {
                model.imports.add("JsonValue");
                model.imports.add("JsonCreator");
            }
            if (property.isContainer) {
                if (property.isArray) {
                    if (property.getUniqueItems()) {
                        property.datatypeWithEnum =
                                property.datatypeWithEnum.replaceFirst("^List<", "LinkedHashSet<");
                        property.dataType =
                                property.dataType.replaceFirst("^List<", "LinkedHashSet<");
                        property.defaultValue =
                                property.defaultValue != null
                                        ? property.defaultValue.replace(
                                                "new ArrayList<>(",
                                                "new LinkedHashSet<>(")
                                        : property.defaultValue;
                        model.imports.add("LinkedHashSet");
                    } else {
                        model.imports.add("ArrayList");
                        model.imports.add("Arrays");
                    }
                }
                if (property.isMap) {
                    model.imports.add("HashMap");
                }
            }
        }
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
                        || typeMapping.containsValue(baseParent)
                        || instantiationTypes.containsValue(baseParent)) {
                    model.parent = null;
                    model.parentModel = null;
                }
            }
        }
        return result;
    }

    @Override
    public ModelsMap postProcessModelsEnum(ModelsMap objs) {
        objs = super.postProcessModelsEnum(objs);
        for (ModelMap modelMap : objs.getModels()) {
            CodegenModel model = modelMap.getModel();
            if (model.isEnum) {
                model.imports.add("JsonValue");
                model.imports.add("JsonCreator");
            }
            for (CodegenProperty property : model.vars) {
                if (property.isEnum) {
                    model.imports.add("JsonValue");
                    model.imports.add("JsonCreator");
                }
            }
        }
        return objs;
    }

    @Override
    protected void registerAuthSupportingFiles() {
        String invokerFolder =
                sourceFolder + File.separator + invokerPackage.replace(".", File.separator);
        String authFolder = invokerFolder + File.separator + "auth";
        String oauthFolder = authFolder + File.separator + "oauth";

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile("auth/basic_authenticator.mustache", authFolder, "BasicAuthenticator.java"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile("auth/bearer_authenticator.mustache", authFolder, "BearerAuthenticator.java"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile("auth/api_key_authenticator.mustache", authFolder, "ApiKeyAuthenticator.java"));
            supportingFiles.add(
                    new SupportingFile("auth/api_key_location.mustache", authFolder, "ApiKeyLocation.java"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile("auth/oauth/oauth2_token_manager.mustache", oauthFolder, "OAuth2TokenManager.java"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile("auth/oauth/oauth2_client_credentials_authenticator.mustache", oauthFolder, "OAuth2ClientCredentialsAuthenticator.java"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile("auth/oauth/oauth2_password_authenticator.mustache", oauthFolder, "OAuth2PasswordAuthenticator.java"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile("auth/oauth/oauth2_auth_code_authenticator.mustache", oauthFolder, "OAuth2AuthorizationCodeAuthenticator.java"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile("auth/oauth/oauth2_implicit_authenticator.mustache", oauthFolder, "OAuth2ImplicitAuthenticator.java"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile("auth/oauth/openid_connect_authenticator.mustache", oauthFolder, "OpenIdConnectAuthenticator.java"));
        }
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "File paths are constructed from codegen configuration, not user input")
    @Override
    protected void generatePerSchemeAuthenticators(OpenAPI openAPI) {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSecuritySchemes() == null) {
            return;
        }
        String invokerFolder =
                sourceFolder + File.separator + invokerPackage.replace(".", File.separator);
        String authFolder = invokerFolder + File.separator + "auth";
        String oauthFolder = authFolder + File.separator + "oauth";

        for (Map.Entry<String, SecurityScheme> entry :
                openAPI.getComponents().getSecuritySchemes().entrySet()) {
            String schemeName = entry.getKey();
            SecurityScheme scheme = entry.getValue();
            String className = StringUtils.camelize(schemeName);
            String code = generateJavaAuthClass(schemeName, className, scheme);
            if (!code.isEmpty()) {
                boolean isOAuth = scheme.getType() == SecurityScheme.Type.OAUTH2
                        || scheme.getType() == SecurityScheme.Type.OPENIDCONNECT;
                String folder = isOAuth ? oauthFolder : authFolder;
                String fileName = className + (getOAuthSuffix(scheme)) + "Authenticator.java";
                String filePath =
                        outputFolder + File.separator + folder + File.separator + fileName;
                writeFile(filePath, code);
                postProcessFile(new File(filePath), "source");
            }
        }
    }

    private String getOAuthSuffix(SecurityScheme scheme) {
        if (scheme.getType() == SecurityScheme.Type.OAUTH2 && scheme.getFlows() != null) {
            if (scheme.getFlows().getClientCredentials() != null) return "ClientCredentials";
            if (scheme.getFlows().getPassword() != null) return "Password";
            if (scheme.getFlows().getAuthorizationCode() != null) return "AuthorizationCode";
            if (scheme.getFlows().getImplicit() != null) return "Implicit";
        }
        return "";
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Comparing with ASCII-only constants")
    private String generateJavaAuthClass(String schemeName, String className, SecurityScheme scheme) {
        String pkg = invokerPackage;
        if (scheme.getType() == SecurityScheme.Type.HTTP) {
            if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                return "package " + pkg + ".auth;\n\n"
                        + "public final class " + className + "Authenticator extends BasicAuthenticator {\n"
                        + "    public " + className + "Authenticator(String host, String username, String password) {\n"
                        + "        super(host, username, password);\n"
                        + "    }\n"
                        + "}\n";
            }
            if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                return "package " + pkg + ".auth;\n\n"
                        + "public final class " + className + "Authenticator extends BearerAuthenticator {\n"
                        + "    public " + className + "Authenticator(String host, String token) {\n"
                        + "        super(host, token);\n"
                        + "    }\n"
                        + "}\n";
            }
        } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
            String location = scheme.getIn().toString().toUpperCase(java.util.Locale.ROOT);
            String paramName = scheme.getName();
            return "package " + pkg + ".auth;\n\n"
                    + "public final class " + className + "Authenticator extends ApiKeyAuthenticator {\n"
                    + "    public " + className + "Authenticator(String host, String apiKey) {\n"
                    + "        super(host, \"" + paramName + "\", apiKey, ApiKeyLocation." + location + ");\n"
                    + "    }\n"
                    + "}\n";
        } else if (scheme.getType() == SecurityScheme.Type.OAUTH2 && scheme.getFlows() != null) {
            return generateJavaOAuthClass(className, scheme, pkg);
        } else if (scheme.getType() == SecurityScheme.Type.OPENIDCONNECT) {
            String url = scheme.getOpenIdConnectUrl();
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className + "Authenticator extends OpenIdConnectAuthenticator {\n"
                    + "    public " + className + "Authenticator(String host, String clientId,\n"
                    + "            String clientSecret, String redirectUri) {\n"
                    + "        super(host, \"" + url + "\", clientId, clientSecret, redirectUri,\n"
                    + "              List.of());\n"
                    + "    }\n"
                    + "}\n";
        }
        LOGGER.warn("Unsupported security scheme type: {}", scheme.getType());
        return "";
    }

    private String generateJavaOAuthClass(String className, SecurityScheme scheme, String pkg) {
        if (scheme.getFlows().getClientCredentials() != null) {
            var flow = scheme.getFlows().getClientCredentials();
            String tokenUrl = flow.getTokenUrl();
            String scopes = flow.getScopes() != null
                    ? "\"" + String.join("\", \"", flow.getScopes().keySet()) + "\""
                    : "";
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className + "ClientCredentialsAuthenticator extends OAuth2ClientCredentialsAuthenticator {\n"
                    + "    public " + className + "ClientCredentialsAuthenticator(String host, String clientId, String clientSecret) {\n"
                    + "        super(host, clientId, clientSecret, \"" + tokenUrl + "\",\n"
                    + "              List.of(" + scopes + "));\n"
                    + "    }\n"
                    + "}\n";
        }
        if (scheme.getFlows().getPassword() != null) {
            var flow = scheme.getFlows().getPassword();
            String tokenUrl = flow.getTokenUrl();
            String refreshUrl = flow.getRefreshUrl();
            String scopes = flow.getScopes() != null
                    ? "\"" + String.join("\", \"", flow.getScopes().keySet()) + "\""
                    : "";
            String refreshUrlArg = refreshUrl != null ? "\"" + refreshUrl + "\"" : "null";
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className + "PasswordAuthenticator extends OAuth2PasswordAuthenticator {\n"
                    + "    public " + className + "PasswordAuthenticator(String host, String clientId,\n"
                    + "            String clientSecret, String username, String password) {\n"
                    + "        super(host, clientId, clientSecret, \"" + tokenUrl + "\", " + refreshUrlArg + ",\n"
                    + "              username, password, List.of(" + scopes + "));\n"
                    + "    }\n"
                    + "}\n";
        }
        if (scheme.getFlows().getAuthorizationCode() != null) {
            var flow = scheme.getFlows().getAuthorizationCode();
            String authUrl = flow.getAuthorizationUrl();
            String tokenUrl = flow.getTokenUrl();
            String refreshUrl = flow.getRefreshUrl();
            String scopes = flow.getScopes() != null
                    ? "\"" + String.join("\", \"", flow.getScopes().keySet()) + "\""
                    : "";
            String refreshUrlArg = refreshUrl != null ? "\"" + refreshUrl + "\"" : "null";
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className + "AuthorizationCodeAuthenticator extends OAuth2AuthorizationCodeAuthenticator {\n"
                    + "    public " + className + "AuthorizationCodeAuthenticator(String host, String clientId,\n"
                    + "            String clientSecret, String redirectUri) {\n"
                    + "        super(host, clientId, clientSecret,\n"
                    + "              \"" + authUrl + "\",\n"
                    + "              \"" + tokenUrl + "\",\n"
                    + "              " + refreshUrlArg + ",\n"
                    + "              redirectUri, List.of(" + scopes + "));\n"
                    + "    }\n"
                    + "}\n";
        }
        if (scheme.getFlows().getImplicit() != null) {
            var flow = scheme.getFlows().getImplicit();
            String authUrl = flow.getAuthorizationUrl();
            String scopes = flow.getScopes() != null
                    ? "\"" + String.join("\", \"", flow.getScopes().keySet()) + "\""
                    : "";
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className + "ImplicitAuthenticator extends OAuth2ImplicitAuthenticator {\n"
                    + "    public " + className + "ImplicitAuthenticator(String host, String clientId) {\n"
                    + "        super(host, clientId, \"" + authUrl + "\",\n"
                    + "              List.of(" + scopes + "));\n"
                    + "    }\n"
                    + "}\n";
        }
        LOGGER.warn("Unsupported OAuth2 flow for scheme: {}", className);
        return "";
    }

    @SuppressFBWarnings(
            value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification = "filePath always contains a parent directory")
    private void writeFile(String filePath, String content) {
        try {
            java.nio.file.Path path = java.nio.file.Path.of(filePath);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, content, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            LOGGER.warn("Failed to write auth file: {}", filePath, e);
        }
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);
        if (file == null || !file.getName().endsWith(".java")) {
            return;
        }
        try {
            String source = Files.readString(file.toPath());
            source = RemoveUnusedImports.removeUnusedImports(source);
            source =
                    ImportOrderer.reorderImports(source, JavaFormatterOptions.Style.GOOGLE);
            String formatted = formatter.formatSource(source);
            Files.write(file.toPath(), formatted.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("Failed to read/write file for formatting: {}", file.getAbsolutePath(), e);
        } catch (FormatterException e) {
            LOGGER.warn("Failed to format file: {}", file.getAbsolutePath(), e);
        }
    }

    @Override
    public CodegenModel fromModel(String name, Schema schema) {
        CodegenModel model = super.fromModel(name, schema);
        if (model.discriminator != null) {
            model.imports.add("JsonTypeInfo");
            model.imports.add("JsonSubTypes");
        }
        if (!model.oneOf.isEmpty() || !model.anyOf.isEmpty()) {
            model.imports.add("JsonValue");
            model.imports.add("JsonCreator");
        }
        return model;
    }
}

package io.github.mridang.codegen.generators.java;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Java API client that uses Apache HttpClient 5 for
 * transport and Jackson for JSON serialization. Targets Java 17+
 * and follows camelCase naming for variables and methods. Output
 * is formatted with Google Java Format to ensure consistent style
 * across all generated source files.
 */
@SuppressWarnings("unused")
public class BetterJavaCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterJavaCodegen.class);

    private static final Set<String> NUMERIC_DATA_TYPES =
            Set.of("Integer", "Long", "Double", "Float", "Short", "BigDecimal");

    protected String sourceFolder = Path.of("src", "main", "java").toString();
    protected String invokerPackage = "org.openapitools";

    /**
     * Initializes all Java-specific type mappings, import mappings,
     * language primitives, and template file registrations. Uses
     * standard Java types from java.time and java.math for date,
     * time, and numeric schemas.
     */
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
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "java-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Java client with Jackson and Apache HttpClient.";
    }

    /** {@inheritDoc} */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.JAVA;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "src/test/resources";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "src/spec/java";
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
        return NamingConvention.UPPER_SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "eclipse-temurin:17-jdk";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            "curl -sL -o /tmp/gjf.jar https://github.com/google/google-java-format/releases/download/v1.25.2/google-java-format-1.25.2-all-deps.jar",
            "find . -name '*.java' -print0 | xargs -0 java"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"
                    + " --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                    + " -jar /tmp/gjf.jar --replace"
        };
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "LinkedHashSet<";
    }

    /**
     * Processes user-supplied codegen options after they are
     * resolved. Reads the invoker package, Maven coordinates,
     * and source folder, then registers all supporting files
     * for the client skeleton, exceptions, auth, serialization,
     * and optional test scaffolding.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        sourceFolder = getPropertyOrDefault(CodegenConstants.SOURCE_FOLDER, sourceFolder);
        invokerPackage = getPropertyOrDefault(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
        additionalProperties.put("invokerPackage", invokerPackage);
        additionalProperties.put("userAgentDefault", invokerPackage + "/1.0.0 (java)");

        final String groupId = getPropertyOrDefault(CodegenConstants.GROUP_ID, invokerPackage);
        additionalProperties.put("groupId", groupId);
        final String artifactId =
                getPropertyOrDefault(CodegenConstants.ARTIFACT_ID, "openapi-java-client");
        additionalProperties.put("artifactId", artifactId);
        final String artifactVersion =
                getPropertyOrDefault(CodegenConstants.ARTIFACT_VERSION, "1.0.0");
        additionalProperties.put("artifactVersion", artifactVersion);

        final String invokerFolder =
                Path.of(sourceFolder, invokerPackage.replace(".", "/")).toString();
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.java"));

        final String exceptionsFolder = Path.of(invokerFolder, "exceptions").toString();
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
                        Path.of(invokerFolder, "api").toString(),
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
                        Path.of(invokerFolder, "auth").toString(),
                        "Authenticator.java"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        Path.of(invokerFolder, "auth").toString(),
                        "HttpAwareAuthenticator.java"));
        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".java"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            final String testFolder =
                    Path.of("src", "test", "java", invokerPackage.replace(".", "/")).toString();
            final String testApiFolder = Path.of(testFolder, "api").toString();
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
            final String testModelsFolder = Path.of(testFolder, "models").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/MetadataTest.mustache",
                            testModelsFolder,
                            "MetadataTest.java"));
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
        }
    }

    /**
     * Returns the output directory for model source files by
     * combining the output folder, source folder, and model
     * package converted to a directory path.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, sourceFolder, modelPackage().replace('.', '/')).toString();
    }

    /**
     * Returns the output directory for API source files by
     * combining the output folder, source folder, and API
     * package converted to a directory path.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, sourceFolder, apiPackage().replace('.', '/')).toString();
    }

    /**
     * Returns the default value expression for a schema type.
     * Arrays default to empty ArrayList or LinkedHashSet for
     * unique items; maps default to empty HashMap. All other
     * types return null to let the language default apply.
     */
    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema unaliased = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (ModelUtils.isArraySchema(unaliased)) {
            if (Boolean.TRUE.equals(unaliased.getUniqueItems())) {
                return "new LinkedHashSet<>()";
            }
            return "new ArrayList<>()";
        } else if (ModelUtils.isMapSchema(unaliased)) {
            return "new HashMap<>()";
        }
        return null;
    }

    /**
     * Applies camelCase casing to a sanitized variable name.
     * Preserves UPPER_CASE constant names (e.g. "MAX_RETRIES")
     * by returning them unchanged, since they represent
     * intentional constant naming conventions.
     */
    @Override
    protected String applyVarNameCasing(String name) {
        if (name.matches("^[A-Z0-9_]*$")) {
            return name;
        }
        return getVarCasing().apply(name);
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return NUMERIC_DATA_TYPES;
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '"';
    }

    /**
     * Adds Jackson annotation imports and collection imports
     * to a model property after standard post-processing.
     * Unique-item set type swapping is handled by the base
     * class via {@link #getUniqueItemsSetType()}.
     */
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

    /**
     * Adds Jackson serialization annotation imports to enum
     * models and properties after enum-specific post-processing
     * so that enum values can be correctly serialized and
     * deserialized by Jackson.
     */
    @Override
    public ModelsMap postProcessModelsEnum(ModelsMap objs) {
        objs = super.postProcessModelsEnum(objs);
        for (final ModelMap modelMap : objs.getModels()) {
            final CodegenModel model = modelMap.getModel();
            if (model.isEnum) {
                model.imports.add("JsonValue");
                model.imports.add("JsonCreator");
            }
            for (final CodegenProperty property : model.vars) {
                if (property.isEnum) {
                    model.imports.add("JsonValue");
                    model.imports.add("JsonCreator");
                }
            }
        }
        return objs;
    }

    /**
     * Adds Jackson discriminator and polymorphism imports when
     * building a model from an OpenAPI schema. Models with
     * discriminators need JsonTypeInfo and JsonSubTypes; oneOf
     * and anyOf models need JsonValue and JsonCreator.
     */
    @Override
    public CodegenModel fromModel(String name, Schema schema) {
        final CodegenModel model = super.fromModel(name, schema);
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

    /**
     * Registers base auth supporting files based on which
     * security scheme types are present in the OpenAPI spec.
     * Only the auth classes actually needed by the spec are
     * emitted to keep the generated client minimal.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String invokerFolder =
                Path.of(sourceFolder, invokerPackage.replace(".", "/")).toString();
        final String authFolder = Path.of(invokerFolder, "auth").toString();
        final String oauthFolder = Path.of(authFolder, "oauth").toString();

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic_authenticator.mustache",
                            authFolder,
                            "BasicAuthenticator.java"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer_authenticator.mustache",
                            authFolder,
                            "BearerAuthenticator.java"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_authenticator.mustache",
                            authFolder,
                            "ApiKeyAuthenticator.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_location.mustache",
                            authFolder,
                            "ApiKeyLocation.java"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_token_manager.mustache",
                            oauthFolder,
                            "OAuth2TokenManager.java"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_client_credentials_authenticator.mustache",
                            oauthFolder,
                            "OAuth2ClientCredentialsAuthenticator.java"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_password_authenticator.mustache",
                            oauthFolder,
                            "OAuth2PasswordAuthenticator.java"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_auth_code_authenticator.mustache",
                            oauthFolder,
                            "OAuth2AuthorizationCodeAuthenticator.java"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_implicit_authenticator.mustache",
                            oauthFolder,
                            "OAuth2ImplicitAuthenticator.java"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/openid_connect_authenticator.mustache",
                            oauthFolder,
                            "OpenIdConnectAuthenticator.java"));
        }
    }

    /**
     * Generates concrete authenticator Java classes for each
     * security scheme defined in the OpenAPI spec. Each class
     * extends the appropriate base authenticator and is written
     * directly to the output directory.
     */
    @Override
    protected void generatePerSchemeAuthenticators(OpenAPI openAPI) {
        if (openAPI.getComponents() == null
                || openAPI.getComponents().getSecuritySchemes() == null) {
            return;
        }
        final String invokerFolder =
                Path.of(sourceFolder, invokerPackage.replace(".", "/")).toString();
        final String authFolder = Path.of(invokerFolder, "auth").toString();
        final String oauthFolder = Path.of(authFolder, "oauth").toString();

        for (final Map.Entry<String, SecurityScheme> entry :
                openAPI.getComponents().getSecuritySchemes().entrySet()) {
            final String schemeName = entry.getKey();
            final SecurityScheme scheme = entry.getValue();
            final String className = StringUtils.camelize(schemeName);
            final String code = generateJavaAuthClass(schemeName, className, scheme);
            if (!code.isEmpty()) {
                final boolean isOAuth =
                        scheme.getType() == SecurityScheme.Type.OAUTH2
                                || scheme.getType() == SecurityScheme.Type.OPENIDCONNECT;
                final String folder = isOAuth ? oauthFolder : authFolder;
                final String fileName =
                        className + getOAuthSuffix(scheme) + "Authenticator.java";
                final String filePath =
                        Path.of(outputFolder, folder, fileName).toString();
                writeFile(filePath, code);
                postProcessFile(Path.of(filePath).toFile(), "source");
            }
        }
    }

    /**
     * Returns the OAuth2 flow suffix for the authenticator class
     * name. Different OAuth2 flows produce distinct class names
     * so each flow gets its own authenticator.
     */
    private String getOAuthSuffix(SecurityScheme scheme) {
        if (scheme.getType() != SecurityScheme.Type.OAUTH2 || scheme.getFlows() == null) {
            return "";
        }
        return Optional.ofNullable(scheme.getFlows().getClientCredentials())
                .map(f -> "ClientCredentials")
                .or(() -> Optional.ofNullable(scheme.getFlows().getPassword()).map(f -> "Password"))
                .or(() ->
                        Optional.ofNullable(scheme.getFlows().getAuthorizationCode())
                                .map(f -> "AuthorizationCode"))
                .or(() -> Optional.ofNullable(scheme.getFlows().getImplicit()).map(f -> "Implicit"))
                .orElse("");
    }

    /**
     * Generates a concrete authenticator Java source string for
     * a single security scheme. Dispatches on scheme type to
     * produce basic, bearer, API key, OAuth2, or OpenID Connect
     * authenticator classes that extend the appropriate base.
     */
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only constants")
    private String generateJavaAuthClass(
            String schemeName, String className, SecurityScheme scheme) {
        final String pkg = invokerPackage;
        if (scheme.getType() == SecurityScheme.Type.HTTP) {
            if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                return "package " + pkg + ".auth;\n\n"
                        + "public final class " + className
                        + "Authenticator extends BasicAuthenticator {\n"
                        + "    public " + className
                        + "Authenticator(String host, String username, String password) {\n"
                        + "        super(host, username, password);\n"
                        + "    }\n"
                        + "}\n";
            }
            if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                return "package " + pkg + ".auth;\n\n"
                        + "public final class " + className
                        + "Authenticator extends BearerAuthenticator {\n"
                        + "    public " + className
                        + "Authenticator(String host, String token) {\n"
                        + "        super(host, token);\n"
                        + "    }\n"
                        + "}\n";
            }
        } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
            final String location =
                    scheme.getIn().toString().toUpperCase(Locale.ROOT);
            final String paramName = scheme.getName();
            return "package " + pkg + ".auth;\n\n"
                    + "public final class " + className
                    + "Authenticator extends ApiKeyAuthenticator {\n"
                    + "    public " + className
                    + "Authenticator(String host, String apiKey) {\n"
                    + "        super(host, \"" + paramName + "\", apiKey, ApiKeyLocation."
                    + location + ");\n"
                    + "    }\n"
                    + "}\n";
        } else if (scheme.getType() == SecurityScheme.Type.OAUTH2
                && scheme.getFlows() != null) {
            return generateJavaOAuthClass(className, scheme, pkg);
        } else if (scheme.getType() == SecurityScheme.Type.OPENIDCONNECT) {
            final String url = scheme.getOpenIdConnectUrl();
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className
                    + "Authenticator extends OpenIdConnectAuthenticator {\n"
                    + "    public " + className
                    + "Authenticator(String host, String clientId,\n"
                    + "            String clientSecret, String redirectUri) {\n"
                    + "        super(host, \"" + url
                    + "\", clientId, clientSecret, redirectUri,\n"
                    + "              List.of());\n"
                    + "    }\n"
                    + "}\n";
        }
        LOGGER.warn("Unsupported security scheme type: {}", scheme.getType());
        return "";
    }

    /**
     * Generates a concrete OAuth2 authenticator Java source
     * string for the specific OAuth2 flow configured in the
     * scheme. Supports client credentials, password, auth code,
     * and implicit flows.
     */
    private String generateJavaOAuthClass(
            String className, SecurityScheme scheme, String pkg) {
        if (scheme.getFlows().getClientCredentials() != null) {
            final var flow = scheme.getFlows().getClientCredentials();
            final String tokenUrl = flow.getTokenUrl();
            final String scopes =
                    flow.getScopes() != null
                            ? "\"" + String.join("\", \"", flow.getScopes().keySet()) + "\""
                            : "";
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className
                    + "ClientCredentialsAuthenticator"
                    + " extends OAuth2ClientCredentialsAuthenticator {\n"
                    + "    public " + className
                    + "ClientCredentialsAuthenticator(String host,"
                    + " String clientId, String clientSecret) {\n"
                    + "        super(host, clientId, clientSecret, \""
                    + tokenUrl + "\",\n"
                    + "              List.of(" + scopes + "));\n"
                    + "    }\n"
                    + "}\n";
        }
        if (scheme.getFlows().getPassword() != null) {
            final var flow = scheme.getFlows().getPassword();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String scopes =
                    flow.getScopes() != null
                            ? "\"" + String.join("\", \"", flow.getScopes().keySet()) + "\""
                            : "";
            final String refreshUrlArg =
                    refreshUrl != null ? "\"" + refreshUrl + "\"" : "null";
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className
                    + "PasswordAuthenticator"
                    + " extends OAuth2PasswordAuthenticator {\n"
                    + "    public " + className
                    + "PasswordAuthenticator(String host, String clientId,\n"
                    + "            String clientSecret, String username,"
                    + " String password) {\n"
                    + "        super(host, clientId, clientSecret, \""
                    + tokenUrl + "\", " + refreshUrlArg + ",\n"
                    + "              username, password, List.of("
                    + scopes + "));\n"
                    + "    }\n"
                    + "}\n";
        }
        if (scheme.getFlows().getAuthorizationCode() != null) {
            final var flow = scheme.getFlows().getAuthorizationCode();
            final String authUrl = flow.getAuthorizationUrl();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String scopes =
                    flow.getScopes() != null
                            ? "\"" + String.join("\", \"", flow.getScopes().keySet()) + "\""
                            : "";
            final String refreshUrlArg =
                    refreshUrl != null ? "\"" + refreshUrl + "\"" : "null";
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className
                    + "AuthorizationCodeAuthenticator"
                    + " extends OAuth2AuthorizationCodeAuthenticator {\n"
                    + "    public " + className
                    + "AuthorizationCodeAuthenticator(String host,"
                    + " String clientId,\n"
                    + "            String clientSecret, String redirectUri)"
                    + " {\n"
                    + "        super(host, clientId, clientSecret,\n"
                    + "              \"" + authUrl + "\",\n"
                    + "              \"" + tokenUrl + "\",\n"
                    + "              " + refreshUrlArg + ",\n"
                    + "              redirectUri, List.of(" + scopes
                    + "));\n"
                    + "    }\n"
                    + "}\n";
        }
        if (scheme.getFlows().getImplicit() != null) {
            final var flow = scheme.getFlows().getImplicit();
            final String authUrl = flow.getAuthorizationUrl();
            final String scopes =
                    flow.getScopes() != null
                            ? "\"" + String.join("\", \"", flow.getScopes().keySet()) + "\""
                            : "";
            return "package " + pkg + ".auth.oauth;\n\n"
                    + "import " + pkg + ".auth.Authenticator;\n"
                    + "import java.util.List;\n\n"
                    + "public final class " + className
                    + "ImplicitAuthenticator"
                    + " extends OAuth2ImplicitAuthenticator {\n"
                    + "    public " + className
                    + "ImplicitAuthenticator(String host,"
                    + " String clientId) {\n"
                    + "        super(host, clientId, \"" + authUrl
                    + "\",\n"
                    + "              List.of(" + scopes + "));\n"
                    + "    }\n"
                    + "}\n";
        }
        LOGGER.warn("Unsupported OAuth2 flow for scheme: {}", className);
        return "";
    }

    /**
     * Writes generated source content to a file, creating any
     * missing parent directories. Logs a warning on failure
     * instead of throwing so code generation can continue.
     */
    @SuppressFBWarnings(
            value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification =
                    "filePath always contains a parent directory")
    private void writeFile(String filePath, String content) {
        try {
            final Path path = Path.of(filePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to write auth file: {}", filePath, e);
        }
    }

}

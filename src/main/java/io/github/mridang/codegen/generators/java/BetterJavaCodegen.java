package io.github.mridang.codegen.generators.java;

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
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;

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

        cliOptions.add(CliOption.newString(CodegenConstants.SOURCE_FOLDER,
                CodegenConstants.SOURCE_FOLDER_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.INVOKER_PACKAGE,
                CodegenConstants.INVOKER_PACKAGE_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.GROUP_ID,
                CodegenConstants.GROUP_ID_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.ARTIFACT_ID,
                CodegenConstants.ARTIFACT_ID_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.ARTIFACT_VERSION,
                CodegenConstants.ARTIFACT_VERSION_DESC));
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

        final String groupId = getPropertyOrDefault(CodegenConstants.GROUP_ID, invokerPackage);
        additionalProperties.put("groupId", groupId);
        final String artifactId =
                getPropertyOrDefault(CodegenConstants.ARTIFACT_ID, "openapi-java-client");
        additionalProperties.put("artifactId", artifactId);
        final String artifactVersion =
                getPropertyOrDefault(CodegenConstants.ARTIFACT_VERSION, "1.0.0");
        additionalProperties.put("artifactVersion", artifactVersion);
        additionalProperties.put(
                "userAgentDefault", invokerPackage + "/" + artifactVersion + " (java)");

        final String invokerFolder =
                Path.of(sourceFolder, invokerPackage.replace(".", "/")).toString();
        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(
                new SupportingFile("api_error.mustache", invokerFolder, "ApiException.java"));

        final String errorsFolder = Path.of(invokerFolder, "errors").toString();
        supportingFiles.add(
                new SupportingFile(
                        "errors/ClientException.mustache",
                        errorsFolder,
                        "ClientException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/ServerException.mustache",
                        errorsFolder,
                        "ServerException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/BadRequestException.mustache",
                        errorsFolder,
                        "BadRequestException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/UnauthorizedException.mustache",
                        errorsFolder,
                        "UnauthorizedException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/ForbiddenException.mustache",
                        errorsFolder,
                        "ForbiddenException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/NotFoundException.mustache",
                        errorsFolder,
                        "NotFoundException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/ConflictException.mustache",
                        errorsFolder,
                        "ConflictException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/UnprocessableEntityException.mustache",
                        errorsFolder,
                        "UnprocessableEntityException.java"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/InternalServerErrorException.mustache",
                        errorsFolder,
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
        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".java"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));
        supportingFiles.add(new SupportingFile("checkstyle_xml.mustache", "", "checkstyle.xml"));

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
            supportingFiles.add(
                    new SupportingFile(
                            "test/ConfigurationTest.mustache",
                            testFolder,
                            "ConfigurationTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ClientTest.mustache",
                            testFolder,
                            "ClientTest.java"));
            final String testModelsFolder = Path.of(testFolder, "models").toString();
            supportingFiles.add(
                    new SupportingFile(
                            "test/MetadataTest.mustache",
                            testModelsFolder,
                            "MetadataTest.java"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ComposedSchemaTest.mustache",
                            testModelsFolder,
                            "ComposedSchemaTest.java"));

        }
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

    /**
     * Returns the default value expression for a schema type.
     * Arrays default to empty ArrayList or LinkedHashSet for
     * unique items; maps default to empty HashMap. String enum
     * schemas with a default return the raw value so that
     * {@code updateCodegenPropertyEnum} can match it to an enum
     * var and produce {@code StatusEnum.PLACED}. All other types
     * return null.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
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
        if (ModelUtils.isStringSchema(unaliased)
                && unaliased.getDefault() != null
                && unaliased.getEnum() != null
                && !unaliased.getEnum().isEmpty()) {
            return unaliased.getDefault().toString();
        }
        return null;
    }

    /**
     * Keeps the enum-reference default value (e.g.
     * {@code StatusEnum.PLACED}) as produced by
     * {@code updateCodegenPropertyEnum}, rather than converting it
     * to a string literal as the base-class implementation would.
     */
    @Override
    protected void fixEnumDefaultValue(CodegenProperty prop, CodegenModel model) {
        // no-op: StatusEnum.PLACED is the correct Java form
    }

    /**
     * Preserves all-uppercase identifiers (e.g. {@code MAX_RETRIES},
     * {@code HTTP_METHOD}) as-is instead of camelCasing them.
     * Java treats these as intentional constant names that should
     * not be transformed. Cannot be standardized because other
     * languages either always apply casing or lowercase first.
     */
    @Override
    protected UppercaseIdentifierStrategy getUppercaseIdentifierStrategy() {
        return UppercaseIdentifierStrategy.PRESERVE;
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return NUMERIC_DATA_TYPES;
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getUniversalModelPropertyImports() {
        return List.of("JsonProperty", "JsonInclude", "JsonTypeName");
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getEnumPropertyImports() {
        return List.of("JsonValue", "JsonCreator");
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getArrayPropertyImports() {
        return List.of("ArrayList", "Arrays");
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getUniqueArrayPropertyImports() {
        return List.of("LinkedHashSet");
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getMapPropertyImports() {
        return List.of("HashMap");
    }

    /**
     * Replaces the {@code ArrayList} default value with a
     * {@code LinkedHashSet} for unique-item array properties.
     * All import additions are handled by the base-class
     * declaration methods.
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (!model.isEnum && property.isArray && property.getUniqueItems()
                && property.defaultValue != null) {
            property.defaultValue =
                    property.defaultValue.replace("new ArrayList<>(", "new LinkedHashSet<>(");
        }
    }

    /**
     * Adds Jackson enum serialization imports (JsonValue,
     * JsonCreator) to enum models and properties. Cannot be
     * standardized because Jackson is Java-specific.
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
     * Adds Jackson discriminator and polymorphism imports
     * (JsonTypeInfo, JsonSubTypes) for models with discriminators,
     * and JsonValue/JsonCreator for oneOf/anyOf models. Cannot
     * be standardized because Jackson is Java-specific.
     */
    @SuppressWarnings("rawtypes")
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
     * Registers base auth supporting files and, when test generation
     * is enabled, the OAuth test class files.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();

        if (generateTests) {
            final String testFolder =
                    Path.of("src", "test", "java", invokerPackage.replace(".", "/")).toString();
            final String testAuthOauthFolder =
                    Path.of(testFolder, "auth", "oauth").toString();
            if (hasAnyOAuth2 || hasOpenIdConnect) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2TokenManagerTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2TokenManagerTest.java"));
            }
            if (hasOAuth2AuthorizationCode) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2AuthCodeAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2AuthCodeAuthenticatorTest.java"));
            }
            if (hasOAuth2Implicit) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2ImplicitAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2ImplicitAuthenticatorTest.java"));
            }
            if (hasOAuth2ClientCredentials) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2ClientCredentialsAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2ClientCredentialsAuthenticatorTest.java"));
            }
            if (hasOAuth2Password) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OAuth2PasswordAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OAuth2PasswordAuthenticatorTest.java"));
            }
            if (hasOpenIdConnect) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/OpenIdConnectAuthenticatorTest.mustache",
                                testAuthOauthFolder,
                                "OpenIdConnectAuthenticatorTest.java"));
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of(sourceFolder, invokerPackage.replace(".", "/"), "auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return pascalAuthFilename(stem, ".java");
    }

    /**
     * Renders a per-scheme authenticator Java source file using
     * the scheme_authenticator.mustache template.
     */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("package", invokerPackage + (spec.isOAuth() ? ".auth.oauth" : ".auth"));
        final List<String> imports;
        if (spec.isOAuth()) {
            imports = List.of(invokerPackage + ".auth.Authenticator", "java.util.List");
        } else {
            imports = List.of();
        }
        ctx.put("imports", imports);
        final List<Map<String, String>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, String> param = new HashMap<>();
            param.put("type", "String");
            param.put("name", name);
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", buildJavaSuperArgs(spec));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    private static String formatJavaScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "List.of()";
        }
        return "List.of(\"" + String.join("\", \"", scopes.keySet()) + "\")";
    }

    private List<String> buildJavaSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.keyParamName() + "\"", "apiKey",
                    "ApiKeyLocation." + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"", formatJavaScopes(spec.scopes()));
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "null";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"", refreshArg,
                    "username", "password", formatJavaScopes(spec.scopes()));
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "null";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.authorizationUrl() + "\"", "\"" + spec.tokenUrl() + "\"",
                    refreshArg, "redirectUri", formatJavaScopes(spec.scopes()));
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId",
                    "\"" + spec.authorizationUrl() + "\"", formatJavaScopes(spec.scopes()));
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.openIdConnectUrl() + "\"",
                    "clientId", "clientSecret", "redirectUri", "List.of()");
        }
        return List.of();
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        final List<Map<String, Object>> requiredParams = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            params.add(param);
            if (p.required) {
                requiredParams.add(param);
            }
        }

        final Set<String> modelTypes = new LinkedHashSet<>();
        for (final CodegenParameter p : optionsParams) {
            if (!p.isPrimitiveType
                    && !p.isArray
                    && !p.isMap
                    && p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)
                    && !typeMapping.containsValue(p.baseType)) {
                modelTypes.add(p.baseType);
            }
            if ((p.isArray || p.isMap)
                    && p.items != null
                    && p.items.baseType != null
                    && !p.items.isPrimitiveType
                    && !languageSpecificPrimitives.contains(p.items.baseType)
                    && !typeMapping.containsValue(p.items.baseType)) {
                modelTypes.add(p.items.baseType);
            }
        }

        final String apiPkg = apiPackage();
        final Map<String, Object> context = new HashMap<>();
        context.put("package", apiPkg + ".options");
        context.put("modelPackage", modelPackage());
        context.put("className", className);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("requiredParams", requiredParams);
        context.put("modelImports", new ArrayList<>(modelTypes));
        context.put("hasModelImports", !modelTypes.isEmpty());
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        return Path.of(
                        outputFolder,
                        sourceFolder,
                        apiPackage().replace('.', '/'),
                        "options",
                        optionsClassName + ".java")
                .toString();
    }
}

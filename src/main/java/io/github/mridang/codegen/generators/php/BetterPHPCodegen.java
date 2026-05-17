package io.github.mridang.codegen.generators.php;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a PHP API client that uses Symfony HTTP Client
 * for transport and Symfony Serializer for model
 * deserialization. All identifiers follow camelCase naming
 * for variables and methods, and PascalCase for classes.
 * Output is formatted with PHP-CS-Fixer and PHPCBF inside a
 * Docker container to ensure consistent style across all
 * generated source files.
 */
@SuppressWarnings("unused")
public class BetterPHPCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterPHPCodegen.class);
    private static final String SRC_BASE_PATH = "lib";
    private static final String API_DIR_NAME = "Api";
    private static final String MODEL_DIR_NAME = "Models";

    protected String invokerPackage = "OpenAPI\\Client";

    /**
     * Initializes all PHP-specific type mappings, language
     * primitives, instantiation types, and template file
     * registrations. Uses standard PHP types for date, time,
     * and numeric schemas, and maps OpenAPI container types
     * to native PHP arrays.
     */
    public BetterPHPCodegen() {
        outputFolder = "generated-code/php";
        embeddedTemplateDir = templateDir = "templates/php";

        modelTemplateFiles.put("models/model.mustache", ".php");
        apiTemplateFiles.put("api/api.mustache", ".php");

        typeMapping.put("integer", "int");
        typeMapping.put("long", "int");
        typeMapping.put("float", "float");
        typeMapping.put("double", "float");
        typeMapping.put("number", "float");
        typeMapping.put("decimal", "float");
        typeMapping.put("boolean", "bool");
        typeMapping.put("string", "string");
        typeMapping.put("byte", "int");
        typeMapping.put("binary", "string");
        typeMapping.put("ByteArray", "string");
        typeMapping.put("date", "\\DateTime");
        typeMapping.put("Date", "\\DateTime");
        typeMapping.put("DateTime", "\\DateTime");
        typeMapping.put("UUID", "string");
        typeMapping.put("URI", "string");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "mixed");
        typeMapping.put("array", "array");
        typeMapping.put("set", "array");
        typeMapping.put("map", "array");
        typeMapping.put("list", "array");
        typeMapping.put("file", "\\SplFileObject");
        typeMapping.put("File", "\\SplFileObject");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int",
                                "integer",
                                "float",
                                "string",
                                "bool",
                                "boolean",
                                "object",
                                "array",
                                "mixed",
                                "void",
                                "null",
                                "byte",
                                "number",
                                "\\DateTime",
                                "\\SplFileObject"));

        instantiationTypes.put("array", "array");
        instantiationTypes.put("map", "array");

        reservedWords = loadReservedWords("/reserved-words/php.txt");
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "php-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal PHP client with Symfony HTTP Client and Symfony Serializer.";
    }

    /** {@inheritDoc} */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.PHP;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "test/fixtures";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "spec";
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
        return "composer:2";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist",
            "vendor/bin/php-cs-fixer fix --quiet || true",
            "vendor/bin/phpcbf || true",
            "rm -rf vendor"
        };
    }

    /**
     * Processes user-supplied codegen options after they are
     * resolved. Reads the invoker package and derives API and
     * model packages from it, then registers all supporting
     * files for the client skeleton, exceptions, auth,
     * serialization, and optional test scaffolding.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        invokerPackage = getPropertyOrDefault(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
        additionalProperties.put("invokerPackage", invokerPackage);
        final String packageVersion = getPropertyOrDefault("packageVersion", "1.0.0");
        additionalProperties.put("packageVersion", packageVersion);
        additionalProperties.put(
                "userAgentDefault", invokerPackage + "/" + packageVersion + " (php)");

        apiPackage = invokerPackage + "\\" + API_DIR_NAME;
        modelPackage = invokerPackage + "\\" + MODEL_DIR_NAME;

        if (additionalProperties.containsKey(CodegenConstants.MODEL_PACKAGE)) {
            modelPackage =
                    invokerPackage
                            + "\\"
                            + additionalProperties.get(CodegenConstants.MODEL_PACKAGE);
        }
        additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);

        if (additionalProperties.containsKey(CodegenConstants.API_PACKAGE)) {
            apiPackage =
                    invokerPackage
                            + "\\"
                            + additionalProperties.get(CodegenConstants.API_PACKAGE);
        }
        additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);

        additionalProperties.put("escapedInvokerPackage", invokerPackage.replace("\\", "\\\\"));

        final String invokerFolder = toSrcPath(invokerPackage);
        final String apiFolder = toSrcPath(apiPackage);

        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.php"));
        supportingFiles.add(
                new SupportingFile(
                        "configuration_builder.mustache",
                        invokerFolder,
                        "ConfigurationBuilder.php"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.php"));
        supportingFiles.add(
                new SupportingFile(
                        "value_serializer.mustache", invokerFolder, "ValueSerializer.php"));
        supportingFiles.add(
                new SupportingFile("api_error.mustache", invokerFolder, "ApiException.php"));

        final String errorsFolder = Path.of(invokerFolder, "Errors").toString();
        supportingFiles.add(
                new SupportingFile(
                        "errors/ClientException.mustache",
                        errorsFolder,
                        "ClientException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/ServerException.mustache",
                        errorsFolder,
                        "ServerException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/BadRequestException.mustache",
                        errorsFolder,
                        "BadRequestException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/UnauthorizedException.mustache",
                        errorsFolder,
                        "UnauthorizedException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/ForbiddenException.mustache",
                        errorsFolder,
                        "ForbiddenException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/NotFoundException.mustache",
                        errorsFolder,
                        "NotFoundException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/ConflictException.mustache",
                        errorsFolder,
                        "ConflictException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/UnprocessableEntityException.mustache",
                        errorsFolder,
                        "UnprocessableEntityException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/InternalServerErrorException.mustache",
                        errorsFolder,
                        "InternalServerErrorException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.php"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", invokerFolder, "TraceContextUtil.php"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.php"));
        supportingFiles.add(
                new SupportingFile("api_result.mustache", invokerFolder, "ApiResult.php"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.php"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.php"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options.mustache", invokerFolder, "TransportOptions.php"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options_builder.mustache",
                        invokerFolder,
                        "TransportOptionsBuilder.php"));
        supportingFiles.add(
                new SupportingFile(
                        "server_variable.mustache", invokerFolder, "ServerVariable.php"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache",
                        invokerFolder,
                        "ServerConfiguration.php"));
        supportingFiles.add(
                new SupportingFile("servers.mustache", invokerFolder, "Servers.php"));
        supportingFiles.add(
                new SupportingFile("base_api.mustache", apiFolder, "BaseApi.php"));
        supportingFiles.add(
                new SupportingFile(
                        "authenticator.mustache",
                        Path.of(invokerFolder, "Auth").toString(),
                        "Authenticator.php"));
        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".php"));
        supportingFiles.add(new SupportingFile("composer.mustache", "", "composer.json"));
        supportingFiles.add(new SupportingFile("phpstan_neon.mustache", "", "phpstan.neon"));
        supportingFiles.add(new SupportingFile("rector.mustache", "", "rector.php"));
        supportingFiles.add(new SupportingFile("phpcs_xml.mustache", "", "phpcs.xml"));
        supportingFiles.add(
                new SupportingFile(
                        "php_cs_fixer.mustache", "", ".php-cs-fixer.dist.php"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/bootstrap.php", "test", "bootstrap.php"));
            supportingFiles.add(new SupportingFile("test/phpunit.xml", "", "phpunit.xml"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/PetMockApiClient.mustache",
                            Path.of("test", "Api").toString(),
                            "PetMockApiClient.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/StoreMockApiClient.mustache",
                            Path.of("test", "Api").toString(),
                            "StoreMockApiClient.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/PetApiTest.mustache",
                            Path.of("test", "Api").toString(),
                            "PetApiTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/StoreApiTest.mustache",
                            Path.of("test", "Api").toString(),
                            "StoreApiTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientTest.mustache",
                            "test",
                            "DefaultApiClientTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/DefaultApiClientUnitTest.mustache",
                            "test",
                            "DefaultApiClientUnitTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TransportOptionsTest.mustache",
                            "test",
                            "TransportOptionsTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/HeaderSelectorTest.mustache",
                            "test",
                            "HeaderSelectorTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ObjectSerializerTest.mustache",
                            "test",
                            "ObjectSerializerTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ValueSerializerTest.mustache",
                            "test",
                            "ValueSerializerTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/TraceContextUtilTest.mustache",
                            "test",
                            "TraceContextUtilTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/BaseApiTest.mustache",
                            "test",
                            "BaseApiTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/MetadataTest.mustache",
                            "test",
                            "MetadataTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ComposedSchemaTest.mustache",
                            "test",
                            "ComposedSchemaTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ConfigurationTest.mustache",
                            "test",
                            "ConfigurationTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/ClientTest.mustache",
                            "test",
                            "ClientTest.php"));
        }
    }

    /**
     * Converts a PHP namespace to a filesystem path relative
     * to the source base directory. Strips the invoker package
     * prefix and replaces namespace separators with directory
     * separators.
     */
    private String toSrcPath(String packageName) {
        final String relative = packageName.replace(invokerPackage, "");
        String packagePath = relative.replaceAll("[\\\\/.]", "/");
        if (packagePath.startsWith("/")) {
            packagePath = packagePath.substring(1);
        }
        if (!SRC_BASE_PATH.isEmpty()) {
            final String base = SRC_BASE_PATH.replaceAll("[\\\\/]$", "");
            if (packagePath.isEmpty()) {
                return base;
            }
            return Path.of(base, packagePath).toString();
        }
        return packagePath;
    }

    /**
     * Returns the output directory for model source files by
     * combining the output folder and the model package path
     * resolved through the source base directory.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, toSrcPath(modelPackage)).toString();
    }

    /**
     * Returns the output directory for API source files by
     * combining the output folder and the API package path
     * resolved through the source base directory.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, toSrcPath(apiPackage)).toString();
    }

    /**
     * Overrides the base class because PHP needs special
     * handling for {@code anyOf}/{@code oneOf} composed schemas
     * (returns the raw type) and routes non-primitives through
     * {@code toModelName()} for backslash-namespaced FQNs.
     * Cannot be standardized because PHP's namespace resolution
     * rules differ from all other languages.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String getSchemaType(Schema schema) {
        final String type = super.getSchemaType(schema);

        if (type == null) {
            return "UNKNOWN_OPENAPI_TYPE";
        }

        if ((schema.getAnyOf() != null && !schema.getAnyOf().isEmpty())
                || (schema.getOneOf() != null && !schema.getOneOf().isEmpty())) {
            return type;
        }

        if (languageSpecificPrimitives.contains(type)) {
            return type;
        }

        return toModelName(type);
    }

    /**
     * Overrides the base class because PHP uses {@code type[]}
     * suffix for arrays, {@code array<K,V>} for maps, and
     * {@code \\Namespace\\Class} FQN for {@code $ref} schemas.
     * Cannot use base class hooks because the array marker
     * position differs and {@code $ref} qualification has no
     * hook.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            return Optional.ofNullable(ModelUtils.getSchemaItems(p))
                    .map(inner -> getTypeDeclaration(inner) + "[]")
                    .orElse("string[]");
        } else if (ModelUtils.isMapSchema(p)) {
            final Schema inner = ModelUtils.getAdditionalProperties(p);
            if (inner == null) {
                return "array<string,string>";
            }
            return getSchemaType(p) + "<string," + getTypeDeclaration(inner) + ">";
        } else if (isNotBlank(p.get$ref())) {
            final String type = super.getTypeDeclaration(p);
            if (!languageSpecificPrimitives.contains(type)) {
                return "\\" + modelPackage + "\\" + toModelName(type);
            }
            return type;
        }
        return super.getTypeDeclaration(p);
    }

    /**
     * Overrides the base class to prefix non-primitive types
     * with {@code \\} and the model package namespace for PHP
     * FQN type hints. Cannot be standardized because no other
     * language uses backslash-namespaced type references.
     */
    @Override
    public String getTypeDeclaration(String name) {
        if (!languageSpecificPrimitives.contains(name)) {
            return "\\" + modelPackage + "\\" + name;
        }
        return super.getTypeDeclaration(name);
    }

    /** {@inheritDoc} */
    @Override
    protected List<String[]> getModelNameSanitizationRules() {
        return List.of(
                new String[]{"\\]", ""},
                new String[]{"[^\\w\\\\]+", "_"},
                new String[]{"\\$", ""});
    }

    /**
     * Returns the default value expression for a schema type.
     * Boolean, numeric, and integer defaults use their string
     * representation; string defaults are wrapped in single
     * quotes. All other types return null to let the language
     * default apply.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema unaliased = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (ModelUtils.isBooleanSchema(unaliased)
                || ModelUtils.isNumberSchema(unaliased)
                || ModelUtils.isIntegerSchema(unaliased)) {
            return Optional.ofNullable(unaliased.getDefault())
                    .map(Object::toString)
                    .orElse(null);
        }
        if (ModelUtils.isStringSchema(unaliased)) {
            return Optional.ofNullable(unaliased.getDefault())
                    .map(d -> "'" + escapeText(String.valueOf(d)) + "'")
                    .orElse(null);
        }
        return null;
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

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "src";
    }

    /** {@inheritDoc} */
    @Override
    protected String getOperationIdReservedPrefix() {
        return "call_";
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("int", "float");
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '\'';
    }

    /** {@inheritDoc} */
    @Override
    protected boolean shouldEscapeQuotationMark() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public String toEnumVarName(String value, String datatype) {
        if (!value.isEmpty() && value.trim().isEmpty()) {
            return "SPACE_" + value.length();
        }
        return super.toEnumVarName(value, datatype);
    }

    /**
     * Registers base auth supporting files and, when test generation
     * is enabled, the PHP OAuth test class files.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();

        if (generateTests) {
            if (hasAnyOAuth2 || hasOpenIdConnect) {
                supportingFiles.add(new SupportingFile("test/MockTokenApiClient.mustache", "test", "MockTokenApiClient.php"));
                supportingFiles.add(new SupportingFile("test/OAuth2TokenManagerTest.mustache", "test", "OAuth2TokenManagerTest.php"));
            }
            if (hasOAuth2AuthorizationCode) {
                supportingFiles.add(new SupportingFile("test/OAuth2AuthCodeAuthenticatorTest.mustache", "test", "OAuth2AuthCodeAuthenticatorTest.php"));
            }
            if (hasOAuth2Implicit) {
                supportingFiles.add(new SupportingFile("test/OAuth2ImplicitAuthenticatorTest.mustache", "test", "OAuth2ImplicitAuthenticatorTest.php"));
            }
            if (hasOAuth2ClientCredentials) {
                supportingFiles.add(new SupportingFile("test/OAuth2ClientCredentialsAuthenticatorTest.mustache", "test", "OAuth2ClientCredentialsAuthenticatorTest.php"));
            }
            if (hasOAuth2Password) {
                supportingFiles.add(new SupportingFile("test/OAuth2PasswordAuthenticatorTest.mustache", "test", "OAuth2PasswordAuthenticatorTest.php"));
            }
            if (hasOpenIdConnect) {
                supportingFiles.add(new SupportingFile("test/OpenIdConnectAuthenticatorTest.mustache", "test", "OpenIdConnectAuthenticatorTest.php"));
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of(toSrcPath(invokerPackage), "Auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getOAuthDir() {
        return Path.of(getAuthDir(), "OAuth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return pascalAuthFilename(stem, ".php");
    }

    /**
     * Renders a per-scheme authenticator PHP source file using
     * the scheme_authenticator.mustache template.
     */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("package", spec.isOAuth()
                ? invokerPackage + "\\Auth\\OAuth"
                : invokerPackage + "\\Auth");
        ctx.put("imports", List.of());
        final List<Map<String, String>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, String> param = new HashMap<>();
            param.put("name", name);
            param.put("type", "string");
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        final List<String> superArgs = buildPhpSuperArgs(spec);
        ctx.put("superArgs", superArgs);
        ctx.put("hasCustomConstructor", !isPassthroughConstructor(constructorParams, superArgs));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    private static String formatPhpScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "['" + String.join("', '", scopes.keySet()) + "']";
    }

    private List<String> buildPhpSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("$host", "$username", "$password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("$host", "$token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("$host", "'" + spec.keyParamName() + "'", "$apiKey",
                    "ApiKeyLocation::" + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            final String scopes = formatPhpScopes(spec.scopes());
            return List.of("$host", "$clientId", "$clientSecret",
                    "'" + spec.tokenUrl() + "'", scopes);
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "null";
            final String scopes = formatPhpScopes(spec.scopes());
            return List.of("$host", "$clientId", "$clientSecret",
                    "'" + spec.tokenUrl() + "'",
                    "$username", "$password", scopes, refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "null";
            final String scopes = formatPhpScopes(spec.scopes());
            return List.of("$host", "$clientId", "$clientSecret",
                    "'" + spec.authorizationUrl() + "'", "'" + spec.tokenUrl() + "'",
                    "$redirectUri", scopes, refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            final String scopes = formatPhpScopes(spec.scopes());
            return List.of("$host", "$clientId",
                    "'" + spec.authorizationUrl() + "'", scopes);
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("$host", "'" + spec.openIdConnectUrl() + "'",
                    "$clientId", "$clientSecret", "$redirectUri", "[]");
        }
        return List.of();
    }

    private static boolean isPassthroughConstructor(
            List<Map<String, String>> constructorParams, List<String> superArgs) {
        if (constructorParams.size() != superArgs.size()) {
            return false;
        }
        for (int i = 0; i < constructorParams.size(); i++) {
            if (!superArgs.get(i).equals("$" + constructorParams.get(i).get("name"))) {
                return false;
            }
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        boolean hasAnyDocTypes = false;
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            final String phpType = resolvePhpType(p);
            final String phpDocType = resolvePhpDocType(p);
            param.put("phpType", phpType);
            param.put("phpDocType", phpDocType);
            final boolean hasDocType = !phpType.equals(phpDocType);
            param.put("hasDocType", hasDocType);
            if (hasDocType) {
                hasAnyDocTypes = true;
            }
            param.put("required", p.required);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            params.add(param);
        }

        final List<Map<String, Object>> requiredParams = new ArrayList<>();
        final List<Map<String, Object>> optionalParams = new ArrayList<>();
        for (final Map<String, Object> param : params) {
            if (Boolean.TRUE.equals(param.get("required"))) {
                requiredParams.add(param);
            } else {
                optionalParams.add(param);
            }
        }

        // Collect model type imports
        final Set<String> modelTypes = new LinkedHashSet<>();
        for (final CodegenParameter p : optionsParams) {
            if (!p.isArray && !p.isMap && !p.isPrimitiveType && p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)) {
                modelTypes.add(p.baseType);
            }
            if ((p.isArray || p.isMap) && p.items != null && p.items.baseType != null
                    && !p.items.isPrimitiveType
                    && !languageSpecificPrimitives.contains(p.items.baseType)) {
                modelTypes.add(p.items.baseType);
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("namespace", invokerPackage + "\\Api\\Options");
        context.put("invokerPackage", invokerPackage);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("hasAnyDocTypes", hasAnyDocTypes);
        context.put("requiredParams", requiredParams);
        context.put("optionalParams", optionalParams);
        context.put("hasOptionalParams", !optionalParams.isEmpty());
        context.put("hasRequiredParams", !requiredParams.isEmpty());
        context.put("modelImports", new ArrayList<>(modelTypes));
        context.put("hasModelImports", !modelTypes.isEmpty());
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        return Path.of(outputFolder, SRC_BASE_PATH, "Api", "Options", optionsClassName + ".php")
                .toString();
    }

    /**
     * Returns the PHP type string for a parameter, using
     * {@code array} for array and map types as PHP does not
     * support generic array type hints.
     */
    private static String resolvePhpType(CodegenParameter p) {
        return p.isArray || p.isMap ? "array" : p.dataType;
    }

    /**
     * Returns a PHPDoc-specific type string that is more
     * descriptive than the native PHP type hint. For arrays,
     * returns {@code ItemType[]}; for maps, returns
     * {@code array<string, ValueType>}; for scalars, returns
     * the data type unchanged. This ensures {@code @var}
     * annotations add useful type information beyond what
     * PHP's native {@code array} hint conveys.
     */
    private static String resolvePhpDocType(CodegenParameter p) {
        if (p.isArray && p.items != null) {
            return p.items.dataType + "[]";
        }
        if (p.isMap) {
            String valueType = (p.items != null) ? p.items.dataType : "mixed";
            return "array<string, " + valueType + ">";
        }
        return p.dataType;
    }

    /** {@inheritDoc} */
    @Override
    protected void fixEnumDefaultValue(CodegenProperty prop, CodegenModel model) {
        if (prop.defaultValue != null && prop.isEnum && prop.defaultValue.contains(".")) {
            // Fix enum defaults: "StatusEnum.PLACED" → "OrderStatusEnum::PLACED"
            final String[] parts = prop.defaultValue.split("\\s*\\.\\s*", 2);
            if (parts.length == 2) {
                prop.defaultValue = model.classname + parts[0] + "::" + parts[1];
            }
        }
    }
}

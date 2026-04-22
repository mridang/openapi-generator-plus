package io.github.mridang.codegen.generators.php;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.utils.ModelUtils;

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
        additionalProperties.put("userAgentDefault", invokerPackage + "/1.0.0 (php)");

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
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.php"));

        final String exceptionsFolder = Path.of(invokerFolder, "Exceptions").toString();
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ClientException.mustache",
                        exceptionsFolder,
                        "ClientException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ServerException.mustache",
                        exceptionsFolder,
                        "ServerException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/BadRequestException.mustache",
                        exceptionsFolder,
                        "BadRequestException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/UnauthorizedException.mustache",
                        exceptionsFolder,
                        "UnauthorizedException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ForbiddenException.mustache",
                        exceptionsFolder,
                        "ForbiddenException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/NotFoundException.mustache",
                        exceptionsFolder,
                        "NotFoundException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/ConflictException.mustache",
                        exceptionsFolder,
                        "ConflictException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/UnprocessableEntityException.mustache",
                        exceptionsFolder,
                        "UnprocessableEntityException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/InternalServerErrorException.mustache",
                        exceptionsFolder,
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

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/bootstrap.php", "test", "bootstrap.php"));
            supportingFiles.add(new SupportingFile("test/phpunit.xml", "", "phpunit.xml"));
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
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
        if (SRC_BASE_PATH != null && !SRC_BASE_PATH.isEmpty()) {
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
     * Resolves the OpenAPI schema type to a PHP type name.
     * Handles composed schemas (anyOf, oneOf) by returning
     * the raw type, maps language primitives directly, and
     * converts all other types through the model name
     * transformation.
     */
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
     * Returns the PHP type declaration for a schema. Arrays
     * produce bracket-suffixed types, maps produce generic
     * array syntax, and reference schemas are fully qualified
     * with the model namespace prefix.
     */
    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            return Optional.ofNullable(ModelUtils.getSchemaItems(p))
                    .map(inner -> getTypeDeclaration(inner) + "[]")
                    .orElse("string[]");
        } else if (ModelUtils.isMapSchema(p)) {
            final Schema<?> inner = ModelUtils.getAdditionalProperties(p);
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
     * Returns the PHP type declaration for a named type.
     * Non-primitive types are prefixed with a backslash and
     * the model package namespace to produce a fully qualified
     * class reference.
     */
    @Override
    public String getTypeDeclaration(String name) {
        if (!languageSpecificPrimitives.contains(name)) {
            return "\\" + modelPackage + "\\" + name;
        }
        return super.getTypeDeclaration(name);
    }

    /**
     * Converts a schema name to a PascalCase PHP class name,
     * sanitizing invalid characters and prefixing reserved
     * words or digit-leading names with "model_" to produce
     * valid PHP class identifiers.
     */
    @Override
    public String toModelName(String name) {
        name = sanitizeName(name);
        name = name.replaceAll("\\]", "");
        name = name.replaceAll("[^\\w\\\\]+", "_");
        name = name.replace("$", "");

        if (isReservedWord(name)) {
            name = "model_" + name;
        }
        if (name.matches("^\\d.*")) {
            name = "model_" + name;
        }

        return NamingConvention.PASCAL_CASE.apply(name);
    }

    /**
     * Returns the default value expression for a schema type.
     * Boolean, numeric, and integer defaults use their string
     * representation; string defaults are wrapped in single
     * quotes. All other types return null to let the language
     * default apply.
     */
    @Nullable
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
                    .map(d -> "'" + d + "'")
                    .orElse(null);
        }
        return null;
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

    /**
     * Converts an enum value to its UPPER_SNAKE_CASE constant
     * name. Handles PHP-specific edge cases: whitespace-only
     * values become "SPACE_n", symbol characters are resolved
     * via {@link #getSymbolName}, and reserved words are
     * escaped. Standard cases delegate to the base class.
     */
    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value.trim().isEmpty() && !value.isEmpty()) {
            return "SPACE_" + value.length();
        }
        return Optional.ofNullable(getSymbolName(value))
                .map(s -> s.toUpperCase(Locale.ROOT))
                .orElseGet(
                        () -> {
                            final String result = super.toEnumVarName(value, datatype);
                            return isReservedWord(result) ? escapeReservedWord(result) : result;
                        });
    }

    /**
     * Registers supporting files for each authentication
     * scheme type present in the OpenAPI spec. Files are
     * placed under the Auth subdirectory within the invoker
     * package path.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String invokerFolder = toSrcPath(invokerPackage);
        final String authFolder = Path.of(invokerFolder, "Auth").toString();
        final String oauthFolder = Path.of(authFolder, "OAuth").toString();

        supportingFiles.add(new SupportingFile("auth/base_authenticator.mustache", authFolder, "BaseAuthenticator.php"));
        supportingFiles.add(new SupportingFile("auth/http_aware_authenticator.mustache", authFolder, "HttpAwareAuthenticator.php"));
        if (hasBasicAuth) {
            supportingFiles.add(new SupportingFile("auth/basic_authenticator.mustache", authFolder, "BasicAuthenticator.php"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(new SupportingFile("auth/bearer_authenticator.mustache", authFolder, "BearerAuthenticator.php"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(new SupportingFile("auth/api_key_authenticator.mustache", authFolder, "ApiKeyAuthenticator.php"));
            supportingFiles.add(new SupportingFile("auth/api_key_location.mustache", authFolder, "ApiKeyLocation.php"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_token_manager.mustache", oauthFolder, "OAuth2TokenManager.php"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_client_credentials_authenticator.mustache", oauthFolder, "OAuth2ClientCredentialsAuthenticator.php"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_password_authenticator.mustache", oauthFolder, "OAuth2PasswordAuthenticator.php"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_auth_code_authenticator.mustache", oauthFolder, "OAuth2AuthorizationCodeAuthenticator.php"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_implicit_authenticator.mustache", oauthFolder, "OAuth2ImplicitAuthenticator.php"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/openid_connect_authenticator.mustache", oauthFolder, "OpenIdConnectAuthenticator.php"));
        }
    }

}

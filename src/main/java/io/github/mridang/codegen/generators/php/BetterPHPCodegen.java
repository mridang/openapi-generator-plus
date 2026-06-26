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
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationsMap;
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
        typeMapping.put("byte", "string");
        typeMapping.put("binary", "string");
        typeMapping.put("ByteArray", "string");
        typeMapping.put("date", "\\DateTime");
        typeMapping.put("Date", "\\DateTime");
        typeMapping.put("DateTime", "\\DateTime");
        /* 4.8: OAS string formats `time` (RFC 3339 partial-time, e.g.
         * "14:30:00") and `duration` (ISO-8601 duration, e.g. "P1DT2H").
         * PHP has no dedicated time-of-day type, so we use
         * \DateTimeImmutable (callers project the time portion via
         * format('H:i:s')). \DateInterval natively parses ISO-8601
         * duration strings via its constructor. */
        typeMapping.put("time", "\\DateTimeImmutable");
        typeMapping.put("duration", "\\DateInterval");
        typeMapping.put("UUID", "\\Symfony\\Component\\Uid\\Uuid");
        typeMapping.put("URI", "\\Uri\\Rfc3986\\Uri");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "mixed");
        typeMapping.put("array", "\\Ds\\Vector");
        typeMapping.put("set", "\\Ds\\Set");
        typeMapping.put("map", "\\Ds\\Map");
        typeMapping.put("Map", "\\Ds\\Map");
        typeMapping.put("list", "\\Ds\\Vector");
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
                                "\\DateTimeImmutable",
                                "\\DateInterval",
                                "\\SplFileObject",
                                "\\Symfony\\Component\\Uid\\Uuid",
                                "\\Uri\\Rfc3986\\Uri",
                                "\\Ds\\Vector",
                                "\\Ds\\Set",
                                "\\Ds\\Map"));

        instantiationTypes.put("array", "\\Ds\\Vector");
        instantiationTypes.put("set", "\\Ds\\Set");
        instantiationTypes.put("map", "\\Ds\\Map");
        instantiationTypes.put("list", "\\Ds\\Vector");

        reservedWords = loadReservedWords("/reserved-words/php.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.INVOKER_PACKAGE,
                CodegenConstants.INVOKER_PACKAGE_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated package (default: 1.0.0).").defaultValue("1.0.0"));
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

    /**
     * Escapes a reserved word with a <em>trailing</em> underscore
     * ({@code $and_}, {@code $class_}), overriding the base generator's
     * leading-underscore scheme. A leading underscore on a public property
     * name trips phpcs's {@code Squiz.NamingConventions.ValidVariableName}
     * sniff, which reads {@code _} as a (mis-applied) visibility marker. The
     * wire name is preserved separately via the property's
     * {@code #[SerializedName(...)]} attribute, so only the PHP identifier
     * changes; a trailing underscore is a legal PHP identifier and satisfies
     * the sniff.
     */
    @Override
    public String escapeReservedWord(String name) {
        return name + "_";
    }

    /** {@inheritDoc} */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.PHP;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "tests/fixtures";
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
        return "composer:2@sha256:b09bccd91a78fe8a9ab4b33d707b862e8fe54fec17782e32683ad2a69c46867d";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            // Start from a clean install dir so a leftover vendor/ or
            // composer.lock (e.g. from an integration-test run sharing this
            // directory, or a previous formatter attempt) cannot cause a
            // partial install or bin-name conflict.
            "rm -rf vendor composer.lock",
            "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist",
            "vendor/bin/php-cs-fixer fix --quiet",
            // phpcbf exits 1 when it successfully fixes violations and 2/3 on a
            // real error; tolerate only the success-with-fixes case so genuine
            // failures still fail loud (subshell isolates $? from the && chain).
            "( vendor/bin/phpcbf || [ $? -eq 1 ] )",
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
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String invokerFolder = SRC_BASE_PATH;
        final String apiFolder = Path.of(SRC_BASE_PATH, API_DIR_NAME).toString();
        final String errorsFolder = Path.of(SRC_BASE_PATH, "Errors").toString();
        final String serializerFolder = Path.of(SRC_BASE_PATH, "Serializer").toString();
        return List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("configuration.mustache", invokerFolder, "Configuration.php"),
            new SupportingFileSpec("configuration_builder.mustache", invokerFolder, "ConfigurationBuilder.php"),
            new SupportingFileSpec("object_serializer.mustache", invokerFolder, "ObjectSerializer.php"),
            new SupportingFileSpec(
                "serialization_exception.mustache", invokerFolder, "SerializationException.php"),
            new SupportingFileSpec("value_serializer.mustache", invokerFolder, "ValueSerializer.php"),
            new SupportingFileSpec("precise_duration.mustache", invokerFolder, "PreciseDuration.php"),
            new SupportingFileSpec("serializer/uri_normalizer.mustache", serializerFolder, "UriNormalizer.php"),
            new SupportingFileSpec("serializer/duration_normalizer.mustache", serializerFolder, "DurationNormalizer.php"),
            new SupportingFileSpec("serializer/ds_vector_normalizer.mustache", serializerFolder, "DsVectorNormalizer.php"),
            new SupportingFileSpec("serializer/ds_set_normalizer.mustache", serializerFolder, "DsSetNormalizer.php"),
            new SupportingFileSpec("serializer/ds_map_normalizer.mustache", serializerFolder, "DsMapNormalizer.php"),
            new SupportingFileSpec("serializer/ds_aware_object_normalizer.mustache", serializerFolder, "DsAwareObjectNormalizer.php"),
            new SupportingFileSpec("zitadel_exception.mustache", invokerFolder, "ZitadelException.php"),
            new SupportingFileSpec("api_error.mustache", invokerFolder, "ApiException.php"),
            new SupportingFileSpec("errors/ClientException.mustache", errorsFolder, "ClientException.php"),
            new SupportingFileSpec("errors/ServerException.mustache", errorsFolder, "ServerException.php"),
            new SupportingFileSpec("errors/BadRequestException.mustache", errorsFolder, "BadRequestException.php"),
            new SupportingFileSpec("errors/UnauthorizedException.mustache", errorsFolder, "UnauthorizedException.php"),
            new SupportingFileSpec("errors/ForbiddenException.mustache", errorsFolder, "ForbiddenException.php"),
            new SupportingFileSpec("errors/NotFoundException.mustache", errorsFolder, "NotFoundException.php"),
            new SupportingFileSpec("errors/ConflictException.mustache", errorsFolder, "ConflictException.php"),
            new SupportingFileSpec("errors/UnprocessableEntityException.mustache", errorsFolder, "UnprocessableEntityException.php"),
            new SupportingFileSpec("errors/InternalServerErrorException.mustache", errorsFolder, "InternalServerErrorException.php"),
            new SupportingFileSpec("header_selector.mustache", invokerFolder, "HeaderSelector.php"),
            new SupportingFileSpec("trace_context_util.mustache", invokerFolder, "TraceContextUtil.php"),
            new SupportingFileSpec("api_response.mustache", invokerFolder, "ApiHttpResponse.php"),
            new SupportingFileSpec("api_result.mustache", invokerFolder, "ApiResult.php"),
            new SupportingFileSpec("cancellation_token.mustache", invokerFolder, "CancellationToken.php"),
            new SupportingFileSpec("cancellation_exception.mustache", invokerFolder, "CancellationException.php"),
            new SupportingFileSpec("api_client.mustache", invokerFolder, "ApiClient.php"),
            new SupportingFileSpec("raw_http_response.mustache", invokerFolder, "RawHttpResponse.php"),
            new SupportingFileSpec("abstract_api_client.mustache", invokerFolder, "AbstractApiClient.php"),
            new SupportingFileSpec("default_api_client.mustache", invokerFolder, "DefaultApiClient.php"),
            new SupportingFileSpec("psr18_api_client.mustache", invokerFolder, "Psr18ApiClient.php"),
            new SupportingFileSpec("transport_options.mustache", invokerFolder, "TransportOptions.php"),
            new SupportingFileSpec("transport_options_builder.mustache", invokerFolder, "TransportOptionsBuilder.php"),
            new SupportingFileSpec("server_variable.mustache", invokerFolder, "ServerVariable.php"),
            new SupportingFileSpec("server_configuration.mustache", invokerFolder, "ServerConfiguration.php"),
            new SupportingFileSpec("servers.mustache", invokerFolder, "Servers.php"),
            new SupportingFileSpec("base_api.mustache", apiFolder, "BaseApi.php"),
            new SupportingFileSpec("authenticator.mustache", Path.of(SRC_BASE_PATH, "Auth").toString(), "Authenticator.php"),
            new SupportingFileSpec("auth/no_auth.mustache", Path.of(SRC_BASE_PATH, "Auth").toString(), "NoAuth.php"),
            new SupportingFileSpec("composer.mustache", "", "composer.json"),
            new SupportingFileSpec("phpstan_neon.mustache", "", "phpstan.neon"),
            new SupportingFileSpec("rector.mustache", "", "rector.php"),
            new SupportingFileSpec("phpcs_xml.mustache", "", "phpcs.xml"),
            new SupportingFileSpec("php_cs_fixer.mustache", "", ".php-cs-fixer.dist.php"),
            new SupportingFileSpec("phpdoc_dist_xml.mustache", "", "phpdoc.dist.xml"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore")
        );
    }

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
        final String clientClassName = (String) additionalProperties.get("clientClassName");
        supportingFiles.add(
                new SupportingFile(
                        "client.mustache", invokerFolder, clientClassName + ".php"));

        if (emitUnitTests()) {
            // Spec-independent pure-unit tests. These reference only supporting
            // classes (ValueSerializer, HeaderSelector, Configuration,
            // TransportOptions, TraceContextUtil, DefaultApiClient) and no
            // spec-derived models, Api classes, or scheme-gated authenticators,
            // so they are safe to emit into any real client. They run under the
            // client's own phpunit.xml and need no container bootstrap — the
            // generator's bootstrap.php / phpunit.xml (Docker chasm/squid) stay
            // golden-only.
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ValueSerializerTest.mustache",
                            "tests",
                            "ValueSerializerTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/HeaderSelectorTest.mustache",
                            "tests",
                            "HeaderSelectorTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ConfigurationTest.mustache",
                            "tests",
                            "ConfigurationTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/TransportOptionsTest.mustache",
                            "tests",
                            "TransportOptionsTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/TraceContextUtilTest.mustache",
                            "tests",
                            "TraceContextUtilTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/DefaultApiClientUnitTest.mustache",
                            "tests",
                            "DefaultApiClientUnitTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/Psr18ApiClientUnitTest.mustache",
                            "tests",
                            "Psr18ApiClientUnitTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ServerConfigurationTest.mustache",
                            "tests",
                            "ServerConfigurationTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ServerVariableTest.mustache",
                            "tests",
                            "ServerVariableTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ApiResultTest.mustache",
                            "tests",
                            "ApiResultTest.php"));
        }

        if (generateTests) {
            // PHPUnit bootstrap + config wiring the Docker chasm/squid container
            // harness — only the golden's container tests use these; real
            // clients ship their own keep-listed phpunit.xml, so keep them
            // golden-only.
            supportingFiles.add(new SupportingFile("tests/bootstrap.php", "tests", "bootstrap.php"));
            supportingFiles.add(new SupportingFile("tests/phpunit.xml", "", "phpunit.xml"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/Api/PetMockApiClient.mustache",
                            Path.of("tests", "Api").toString(),
                            "PetMockApiClient.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/Api/StoreMockApiClient.mustache",
                            Path.of("tests", "Api").toString(),
                            "StoreMockApiClient.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/Api/PetApiTest.mustache",
                            Path.of("tests", "Api").toString(),
                            "PetApiTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/Api/StoreApiTest.mustache",
                            Path.of("tests", "Api").toString(),
                            "StoreApiTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/DefaultApiClientTest.mustache",
                            "tests",
                            "DefaultApiClientTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/CancellationTokenTest.mustache",
                            "tests",
                            "CancellationTokenTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ObjectSerializerTest.mustache",
                            "tests",
                            "ObjectSerializerTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/BaseApiTest.mustache",
                            "tests",
                            "BaseApiTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/MetadataTest.mustache",
                            "tests",
                            "MetadataTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ComposedSchemaTest.mustache",
                            "tests",
                            "ComposedSchemaTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ClientTest.mustache",
                            "tests",
                            "ClientTest.php"));
            // Standalone authenticator tests for the non-OAuth HTTP schemes.
            // The OAuthTestCondition enum (in AbstractBetterCodegen) only gates
            // BASIC + the OAuth2/OIDC flows, so Bearer/ApiKey have no matching
            // condition; they are registered here in the golden-only generateTests
            // block alongside ClientTest, which likewise references the
            // Bearer/ApiKey authenticators that the golden spec always declares.
            supportingFiles.add(
                    new SupportingFile(
                            "tests/BearerAuthenticatorTest.mustache",
                            "tests",
                            "BearerAuthenticatorTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ApiKeyAuthenticatorTest.mustache",
                            "tests",
                            "ApiKeyAuthenticatorTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/ApiExceptionTest.mustache",
                            "tests",
                            "ApiExceptionTest.php"));
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
            return Boolean.TRUE.equals(p.getUniqueItems()) ? "\\Ds\\Set" : "\\Ds\\Vector";
        } else if (ModelUtils.isMapSchema(p)) {
            return "\\Ds\\Map";
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

    /** {@inheritDoc} */
    @Override
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        return List.of(
                new OAuthTestFileSpec("tests/BasicAuthenticatorTest.mustache", "tests", "BasicAuthenticatorTest.php", OAuthTestCondition.BASIC),
                new OAuthTestFileSpec("tests/MockTokenApiClient.mustache", "tests", "MockTokenApiClient.php", OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec("tests/OAuth2TokenManagerTest.mustache", "tests", "OAuth2TokenManagerTest.php", OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec("tests/OAuth2AuthCodeAuthenticatorTest.mustache", "tests", "OAuth2AuthCodeAuthenticatorTest.php", OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec("tests/OAuth2ImplicitAuthenticatorTest.mustache", "tests", "OAuth2ImplicitAuthenticatorTest.php", OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec("tests/OAuth2ClientCredentialsAuthenticatorTest.mustache", "tests", "OAuth2ClientCredentialsAuthenticatorTest.php", OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec("tests/OAuth2PasswordAuthenticatorTest.mustache", "tests", "OAuth2PasswordAuthenticatorTest.php", OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec("tests/OpenIdConnectAuthenticatorTest.mustache", "tests", "OpenIdConnectAuthenticatorTest.php", OAuthTestCondition.OIDC));
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
            // Thread the native CodegenParameter deprecated flag into the
            // options render context so the corresponding constructor-promoted
            // property can carry an `@deprecated` PHPDoc tag, mirroring the
            // model-property deprecation idiom (model.mustache `@deprecated
            // This property is deprecated.`).
            param.put("deprecated", p.isDeprecated);
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
            // A $ref to a top-level enum carries its enum type name in dataType
            // (baseType is null); the generated enum lives in the models
            // namespace and must be imported with a `use` statement.
            if (p.isEnumRef
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)) {
                modelTypes.add(p.dataType);
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
        injectAuthFieldContext(op, context);
        context.put("authImport", invokerPackage + "\\Auth\\" + getAuthenticatorTypeName());
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
            return withDsGenerics(p.items.dataType) + "[]";
        }
        if (p.isMap) {
            return "array<string, " + resolveMapValueType(p) + ">";
        }
        return withDsGenerics(p.dataType);
    }

    /**
     * Resolves the PHPDoc value type for a map parameter
     * ({@code additionalProperties: {...}}). The schema's value type can live
     * on one of several fields depending on how openapi-generator built the
     * parameter:
     *
     * <ul>
     *   <li>For a plain map query/body param ({@code updateParameterForMap}),
     *       {@code items} is the value schema, so {@code items.dataType} (e.g.
     *       {@code string}) is the value type.</li>
     *   <li>For a {@code style: deepObject} query param,
     *       {@code DefaultCodegen} overwrites {@code items} with the whole map
     *       schema (whose own {@code dataType} is the container placeholder
     *       {@code \Ds\Map}), and the real value schema is nested one level
     *       deeper on {@code items.additionalProperties}. Without unwrapping
     *       this we would emit the container placeholder
     *       {@code \Ds\Map<array-key, mixed>} as the value type instead of the
     *       declared scalar.</li>
     *   <li>{@code additionalProperties} directly on the parameter is honoured
     *       when present.</li>
     * </ul>
     *
     * Falls back to {@code mixed} only when no value schema can be located.
     */
    private static String resolveMapValueType(CodegenParameter p) {
        // Param-level additionalProperties (the map's value schema) wins when set.
        if (p.additionalProperties != null && p.additionalProperties.dataType != null) {
            return withDsGenerics(p.additionalProperties.dataType);
        }
        if (p.items != null) {
            // deepObject case: items is the map schema itself, the value schema
            // is one level deeper on items.additionalProperties. Resolving from
            // items.dataType here would leak the \Ds\Map container placeholder.
            if (p.items.additionalProperties != null
                    && p.items.additionalProperties.dataType != null) {
                return withDsGenerics(p.items.additionalProperties.dataType);
            }
            if (p.items.dataType != null) {
                return withDsGenerics(p.items.dataType);
            }
        }
        return "mixed";
    }

    /**
     * Appends the default generic type arguments to a bare {@code \Ds\*}
     * container type so the PHPDoc satisfies PHPStan's {@code
     * missingType.generics} rule at the default strict level. A bare
     * {@code \Ds\Vector} / {@code \Ds\Set} becomes {@code <mixed>} and a
     * bare {@code \Ds\Map} becomes {@code <array-key, mixed>}; any type
     * that already carries a {@code <...>} argument list, and every
     * non-Ds type, is returned unchanged.
     */
    private static String withDsGenerics(String type) {
        if (type == null) {
            return type;
        }
        // Add default generic arguments to EVERY bare \Ds\* container — one not
        // already followed by '<' — at any nesting depth, so a nested type such
        // as \Ds\Vector<\Ds\Map> (whose inner \Ds\Map carries no generics)
        // still satisfies PHPStan's missingType.generics rule. The lookahead
        // skips a container that already has a <...> argument list.
        type = type.replaceAll("\\\\Ds\\\\Map(?!<)", "\\\\Ds\\\\Map<array-key, mixed>");
        type = type.replaceAll("\\\\Ds\\\\Vector(?!<)", "\\\\Ds\\\\Vector<mixed>");
        type = type.replaceAll("\\\\Ds\\\\Set(?!<)", "\\\\Ds\\\\Set<mixed>");
        return type;
    }

    /**
     * Builds the precise PHPDoc type string for a return property, descending
     * the full item tree so a nested container documents its real inner value
     * type instead of collapsing it to {@code mixed}. The outer container
     * carries no generics in {@code property.dataType} (e.g. {@code \Ds\Vector}),
     * so each level wraps its element/value type in the appropriate generic
     * argument list:
     *
     * <ul>
     *   <li>an array of {@code T} becomes {@code \Ds\Vector<T>};</li>
     *   <li>a map of {@code T} becomes {@code \Ds\Map<string, T>};</li>
     *   <li>a leaf scalar/model is its own {@code dataType}.</li>
     * </ul>
     *
     * Because the recursion uses each level's own {@code items} property, a
     * {@code list<list<int>>} is documented as
     * {@code \Ds\Vector<\Ds\Vector<int>>} rather than the lossy
     * {@code \Ds\Vector<\Ds\Vector>} (which {@link #withDsGenerics} would then
     * have widened to {@code \Ds\Vector<\Ds\Vector<mixed>>}). The final
     * {@link #withDsGenerics} pass still fills in default generics for any bare
     * leaf {@code \Ds\*} container that genuinely has no element schema.
     *
     * <p>The outer container name comes from {@code outerType} ({@code
     * op.returnType}) rather than {@code property.dataType} so any earlier
     * rewrite of the resolved return type — notably the {@code \SplFileObject ->
     * string} binary-response collapse — is preserved at the top level. Inner
     * value types are taken from the property item tree, which is left
     * unmutated by that rewrite.
     */
    @Nullable
    static String phpDocReturnType(@Nullable String outerType, @Nullable CodegenProperty property) {
        if (outerType == null) {
            return null;
        }
        if (property == null) {
            return withDsGenerics(outerType);
        }
        if (property.isArray && property.items != null) {
            return withDsGenerics(outerType + "<" + phpDocItemType(property.items) + ">");
        }
        if (property.isMap && property.items != null) {
            return withDsGenerics(outerType + "<string, " + phpDocItemType(property.items) + ">");
        }
        return withDsGenerics(outerType);
    }

    /**
     * Recursive worker for {@link #phpDocReturnType(String, CodegenProperty)}
     * that produces the un-{@code withDsGenerics}-normalised PHPDoc string for a
     * single property, wrapping array elements and map values in their generic
     * argument lists and recursing into {@code items} for nested containers.
     */
    private static String phpDocItemType(CodegenProperty property) {
        if (property.isArray && property.items != null) {
            return property.dataType + "<" + phpDocItemType(property.items) + ">";
        }
        if (property.isMap && property.items != null) {
            return property.dataType + "<string, " + phpDocItemType(property.items) + ">";
        }
        return property.dataType;
    }

    /**
     * Honour URI subformat discrimination — {@code format: uri-reference}
     * and {@code format: uri-template} stay as plain {@code string} per
     * {@link AbstractBetterCodegen#keepStringForUriSubformats}, only the
     * absolute-URI {@code format: uri} maps to the native PHP 8.5
     * {@code \Uri\Rfc3986\Uri}.
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        keepStringForUriSubformats(property, "string");
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

    /**
     * Enables Gap K so polymorphic subtypes auto-emit their
     * discriminator field on serialization. The PHP model template
     * honours {@code defaultValue} on required constructor
     * parameters via {@code string ${{name}} = 'mapped',}, so the
     * caller can omit the discriminator when constructing a subtype.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /**
     * Returns a single-quoted PHP string literal for the
     * discriminator default value, matching PHP's idiomatic
     * convention for non-interpolated string constants.
     */
    @Override
    protected String formatDiscriminatorDefaultValue(String mappingName) {
        return "'" + mappingName + "'";
    }

    /**
     * Sort vars so properties carrying a default value (including
     * the auto-injected discriminator default from Gap K) come
     * last. PHP forbids non-defaulted parameters after defaulted
     * parameters in a function signature (deprecated since 8.0,
     * error in 9.0).
     */
    @Override
    protected boolean sortVarsByDefaultValue() {
        return true;
    }

    /**
     * Move the discriminator property out of {@code requiredVars}
     * and into {@code optionalVars} after defaulting. PHP's
     * constructor template iterates {@code requiredVars} first
     * then {@code optionalVars}, and a defaulted parameter
     * (the discriminator) cannot precede a non-defaulted required
     * parameter (other required fields) in the same parameter list.
     * Demotion guarantees the discriminator is rendered after all
     * non-defaulted required params, satisfying PHP's signature
     * ordering rule.
     */
    @Override
    protected boolean demotesDiscriminatorFromRequiredVars() {
        return true;
    }

    /**
     * Rewrites binary <em>response</em> return types from
     * {@code \SplFileObject} to {@code string}.
     *
     * <p>openapi-generator special-cases {@code format: binary} response
     * bodies to the PHP file type {@code \SplFileObject}, but the transport
     * delivers the response body as an in-memory string and the deserialize
     * path hands that string back verbatim — so the declared
     * {@code \SplFileObject} return type never matched the actual returned
     * value. Returning the raw bytes as a {@code string} makes the declared
     * type honest and aligns PHP with the other byte-oriented SDKs (Go
     * {@code []byte}, Node {@code Buffer}, Python {@code bytes}, Dart
     * {@code Uint8List}).
     *
     * <p>Only the response type is rewritten; binary <em>request</em> bodies
     * and multipart file uploads continue to use {@code \SplFileObject}, the
     * idiomatic PHP type for streaming a file from disk.
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        // Model types of required enum-ref signature params (path/body). A
        // required param whose type is a $ref to a named enum is resolved away
        // by the parser, so getTypeDeclaration cannot FQN it and the signature
        // would emit the bare name, which php resolves against the API
        // namespace (Api\Swatch) instead of the models one. Importing the type
        // with a `use` lets the short name resolve correctly AND keeps the
        // signature line within the 120-char limit (a FQN can overflow it).
        // Optional enum params live in the Options class, which imports them
        // separately.
        final Set<String> enumParamImports = new LinkedHashSet<>();
        if (operations != null) {
            final List<CodegenOperation> ops =
                    (List<CodegenOperation>) operations.get("operation");
            if (ops != null) {
                for (final CodegenOperation op : ops) {
                    if (op.returnType != null && op.returnType.contains("SplFileObject")) {
                        op.returnType = "string";
                        op.returnBaseType = "string";
                    }
                    collectEnumRefParamImports(op.pathParams, enumParamImports);
                    if (op.bodyParam != null) {
                        collectEnumRefParamImports(List.of(op.bodyParam), enumParamImports);
                    }
                    // Build the PHPDoc return type once by descending the full
                    // return item tree, applying nested \Ds\* generics, so the
                    // @return / @var tags are PHPStan-valid AND precise even when
                    // the array element / map value is itself a container. A
                    // list<list<int>> is documented as
                    // \Ds\Vector<\Ds\Vector<int>>, not the lossy
                    // \Ds\Vector<\Ds\Vector<mixed>> the old single-level build
                    // produced. Kept separate from the runtime deserialize
                    // descriptor, which needs the precise value type, so dataType
                    // is not mutated here.
                    final String phpDoc = phpDocReturnType(op.returnType, op.returnProperty);
                    if (phpDoc != null) {
                        op.vendorExtensions.put("phpDocReturnType", phpDoc);
                    }
                    // H3 request-content-type selector: the api template reads
                    // vendorExtensions.op.hasMultipleConsumes, populated by the shared
                    // operation-decorator pass (AbstractBetterCodegen) alongside
                    // effectiveConsumes — no php-local flag needed.
                }
            }
        }
        final OperationsMap processed = super.postProcessOperationsWithModels(objs, allModels);
        // Lift hasServerTypeDefs to the template root so the api template can
        // emit a file-level phpcs:ignoreFile directive before the namespace
        // declaration. Per-operation server overrides emit co-located
        // server-variant classes (PSR1.MultipleClasses) that are inherent to
        // the generated API group. enrichOperationServers runs inside the
        // super call above, so the flag must be read afterwards.
        // enumParamImports sits on the OperationsMap root, the same level as
        // optionsImports, since the api template renders both as file-level
        // `use` statements outside the {{#operations}} block.
        processed.put("enumParamImports", new ArrayList<>(enumParamImports));
        final Map<String, Object> processedOps =
                (Map<String, Object>) processed.get("operations");
        if (processedOps != null) {
            processed.put("hasServerTypeDefs", processedOps.get("hasServerTypeDefs"));
        }
        return processed;
    }

    /**
     * Collects the model type name of each enum-ref parameter in the given
     * signature-parameter list (path params, body param) so the API class can
     * import it with a {@code use} statement and reference it by short name.
     * Idempotent via the supplied set.
     */
    private void collectEnumRefParamImports(
            List<CodegenParameter> params, Set<String> into) {
        if (params == null) {
            return;
        }
        for (final CodegenParameter p : params) {
            if (p.isEnumRef
                    && p.dataType != null
                    && !p.dataType.startsWith("\\")
                    && !languageSpecificPrimitives.contains(p.dataType)) {
                into.add(p.dataType);
            }
        }
    }
}

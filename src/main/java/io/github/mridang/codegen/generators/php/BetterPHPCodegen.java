package io.github.mridang.codegen.generators.php;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import org.openapitools.codegen.model.ModelsMap;
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

    /** Scheme constructor parameters that carry a secret and must stay out of stack traces. */
    private static final Set<String> SENSITIVE_PARAMS = Set.of("apiKey", "token", "password", "clientSecret");

    /* Type names PHP forbids as a class name, in any letter case. */
    private static final Set<String> FORBIDDEN_CLASS_NAMES =
            Set.of(
                    "int", "float", "bool", "string", "true", "false", "null", "void", "iterable",
                    "object", "mixed", "never", "self", "parent");

    /**
     * The declared type of a {@code format: binary} request body: a file for its name and MIME type,
     * a PSR-7 stream, or the raw bytes as a string, so in-memory content needs no temporary file.
     */
    private static final String BINARY_BODY_TYPE =
            "\\SplFileObject|\\Psr\\Http\\Message\\StreamInterface|string";

    /** The authenticators whose generated scheme class reads a credential from the environment when none is passed. */
    private static final Set<String> CREDENTIAL_FALLBACK_BASES =
            Set.of(
                    "BasicAuthenticator",
                    "BearerAuthenticator",
                    "ApiKeyAuthenticator",
                    "OAuth2ClientCredentialsAuthenticator",
                    "OAuth2PasswordAuthenticator",
                    "OAuth2AuthorizationCodeAuthenticator",
                    "OAuth2ImplicitAuthenticator");

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
        typeMapping.put("URI", "string");
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

    /**
     * Escapes a variable, property or parameter name only when PHP cannot use
     * it. Every PHP keyword is a legal {@code $}-sigiled variable, a legal
     * property name and, since PHP 8, a legal named argument, so
     * {@code $and}, {@code $class} and {@code $return} stay as written. The one
     * name a variable cannot take is {@code $this}. Class, method and constant
     * names still go through the reserved-word list.
     *
     * @param name the cased, sanitized variable name
     * @return whether the name must be escaped
     */
    @Override
    protected boolean shouldEscapeReservedVarName(String name) {
        return "this".equals(name);
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
    protected Set<String> getInjectedArgumentNames() {
        return Set.of(
                "options", "server", "requestContentType", "auth", "timeout", "headers", "maxRetries");
    }

    /**
     * Computes the client's credential parameters (F857): one optional parameter per distinct
     * security scheme of the document, named after the scheme, as {@code schemeCredentials}
     * (each entry has {@code param}, the PHP parameter name, and {@code scheme}, the scheme id
     * that an operation's requirement names).
     */
    @Override
    public void processOpenAPI(OpenAPI openAPI) {
        super.processOpenAPI(openAPI);
        final List<Map<String, Object>> credentials = new ArrayList<>();
        if (openAPI.getComponents() != null && openAPI.getComponents().getSecuritySchemes() != null) {
            final Set<String> taken = new HashSet<>(Set.of("authenticator", "transportOptions", "apiClient"));
            for (final Map.Entry<String, SecurityScheme> entry :
                    openAPI.getComponents().getSecuritySchemes().entrySet()) {
                if (buildSchemeAuthSpec(entry.getKey(), entry.getValue()) == null) {
                    continue;
                }
                String param = NamingConvention.CAMEL_CASE.apply(entry.getKey());
                while (!taken.add(param)) {
                    param = param + "Scheme";
                }
                final Map<String, Object> credential = new HashMap<>();
                credential.put("param", param);
                credential.put("scheme", entry.getKey());
                credential.put("schemeLiteral", toPhpStringLiteral(entry.getKey()));
                credentials.add(credential);
            }
        }
        additionalProperties.put("schemeCredentials", credentials);
        additionalProperties.put("hasSchemeCredentials", !credentials.isEmpty());
    }

    /**
     * Adds the security requirement of the operation as a PHP array literal
     * ({@code phpSecurityRequirements}): the alternatives, each the list of scheme ids that must
     * all be configured. The operation's own requirement wins, else the document's; an empty
     * requirement gives {@code []}.
     */
    @Override
    public CodegenOperation fromOperation(
            String path, String httpMethod, Operation operation, List<Server> servers) {
        final CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);
        final List<SecurityRequirement> requirements =
                operation.getSecurity() != null
                        ? operation.getSecurity()
                        : (openAPI == null ? null : openAPI.getSecurity());
        op.vendorExtensions.put("phpSecurityRequirements", securityRequirementsLiteral(requirements));
        return op;
    }

    /**
     * The PHP array literal of a security requirement list: {@code [['a'], ['b', 'c']]}.
     *
     * @param requirements the requirement alternatives, or {@code null} for none
     * @return the literal, {@code []} when there is no alternative
     */
    static String securityRequirementsLiteral(@Nullable List<SecurityRequirement> requirements) {
        if (requirements == null) {
            return "[]";
        }
        final List<String> alternatives = new ArrayList<>();
        for (final SecurityRequirement requirement : requirements) {
            final List<String> names = new ArrayList<>();
            for (final String name : requirement.keySet()) {
                names.add(toPhpStringLiteral(name));
            }
            if (!names.isEmpty()) {
                alternatives.add("[" + String.join(", ", names) + "]");
            }
        }
        return "[" + String.join(", ", alternatives) + "]";
    }

    /**
     * An operation takes no per-call authenticator (F857): the credential of each scheme is set
     * once on the client, so the options object has no {@code auth} field.
     */
    @Override
    protected void injectAuthFieldContext(CodegenOperation op, Map<String, Object> context) {
        context.put("hasAuthField", false);
        context.put("authFieldType", getAuthenticatorTypeName());
    }

    /**
     * Every operation gets an options object, so each call can carry its own timeout, headers and
     * retry count even when it has no query, header, form or cookie parameter and no security.
     */
    @Override
    protected boolean mintsOptionsForEveryOperation() {
        return true;
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
            // vendor/ holds thousands of small files; extracting them onto the
            // bind-mounted output directory takes minutes on Docker Desktop, so it
            // lives on the container's own disk and is only linked into place.
            "mkdir -p /opt/vendor",
            "ln -s /opt/vendor vendor",
            "export COMPOSER_VENDOR_DIR=/opt/vendor",
            "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist",
            // Rector first, as a client's own `make format` does after
            // generating: it rewrites to the configured PHP level and
            // quality sets, and php-cs-fixer then settles the layout.
            "vendor/bin/rector process --no-progress-bar",
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
            new SupportingFileSpec("value_serializer.mustache", invokerFolder, "ValueSerializer.php"),
            new SupportingFileSpec("precise_duration.mustache", invokerFolder, "PreciseDuration.php"),
            new SupportingFileSpec("serializer/duration_normalizer.mustache", serializerFolder, "DurationNormalizer.php"),
            new SupportingFileSpec("serializer/ds_vector_normalizer.mustache", serializerFolder, "DsVectorNormalizer.php"),
            new SupportingFileSpec("serializer/ds_set_normalizer.mustache", serializerFolder, "DsSetNormalizer.php"),
            new SupportingFileSpec("serializer/ds_map_normalizer.mustache", serializerFolder, "DsMapNormalizer.php"),
            new SupportingFileSpec("serializer/ds_aware_object_normalizer.mustache", serializerFolder, "DsAwareObjectNormalizer.php"),
            new SupportingFileSpec("root_exception.mustache", errorsFolder, rootErrorName("Exception") + ".php"),
            new SupportingFileSpec("api_error.mustache", errorsFolder, "ApiException.php"),
            new SupportingFileSpec("errors/SerializationException.mustache", errorsFolder, "SerializationException.php"),
            new SupportingFileSpec("errors/OAuth2ServerException.mustache", errorsFolder, "OAuth2ServerException.php"),
            new SupportingFileSpec("errors/OAuth2TokenException.mustache", errorsFolder, "OAuth2TokenException.php"),
            new SupportingFileSpec("errors/ClientException.mustache", errorsFolder, "ClientException.php"),
            new SupportingFileSpec("errors/ServerException.mustache", errorsFolder, "ServerException.php"),
            new SupportingFileSpec("errors/BadRequestException.mustache", errorsFolder, "BadRequestException.php"),
            new SupportingFileSpec("errors/UnauthorizedException.mustache", errorsFolder, "UnauthorizedException.php"),
            new SupportingFileSpec("errors/ForbiddenException.mustache", errorsFolder, "ForbiddenException.php"),
            new SupportingFileSpec("errors/NotFoundException.mustache", errorsFolder, "NotFoundException.php"),
            new SupportingFileSpec("errors/ConflictException.mustache", errorsFolder, "ConflictException.php"),
            new SupportingFileSpec("errors/PaymentRequiredException.mustache", errorsFolder, "PaymentRequiredException.php"),
            new SupportingFileSpec("errors/MethodNotAllowedException.mustache", errorsFolder, "MethodNotAllowedException.php"),
            new SupportingFileSpec("errors/RequestTimeoutException.mustache", errorsFolder, "RequestTimeoutException.php"),
            new SupportingFileSpec("errors/GoneException.mustache", errorsFolder, "GoneException.php"),
            new SupportingFileSpec("errors/PayloadTooLargeException.mustache", errorsFolder, "PayloadTooLargeException.php"),
            new SupportingFileSpec("errors/UnsupportedMediaTypeException.mustache", errorsFolder, "UnsupportedMediaTypeException.php"),
            new SupportingFileSpec("errors/LockedException.mustache", errorsFolder, "LockedException.php"),
            new SupportingFileSpec("errors/NotImplementedException.mustache", errorsFolder, "NotImplementedException.php"),
            new SupportingFileSpec("errors/BadGatewayException.mustache", errorsFolder, "BadGatewayException.php"),
            new SupportingFileSpec("errors/ServiceUnavailableException.mustache", errorsFolder, "ServiceUnavailableException.php"),
            new SupportingFileSpec("errors/GatewayTimeoutException.mustache", errorsFolder, "GatewayTimeoutException.php"),
            new SupportingFileSpec("errors/UnprocessableEntityException.mustache", errorsFolder, "UnprocessableEntityException.php"),
            new SupportingFileSpec("errors/TooManyRequestsException.mustache", errorsFolder, "TooManyRequestsException.php"),
            new SupportingFileSpec("errors/InternalServerErrorException.mustache", errorsFolder, "InternalServerErrorException.php"),
            new SupportingFileSpec("errors/NetworkException.mustache", errorsFolder, "NetworkException.php"),
            new SupportingFileSpec("errors/NetworkTimeoutException.mustache", errorsFolder, "NetworkTimeoutException.php"),
            new SupportingFileSpec("header_selector.mustache", invokerFolder, "HeaderSelector.php"),
            new SupportingFileSpec("trace_context_util.mustache", invokerFolder, "TraceContextUtil.php"),
            new SupportingFileSpec("api_response.mustache", invokerFolder, "ApiHttpResponse.php"),
            new SupportingFileSpec("api_result.mustache", invokerFolder, "ApiResult.php"),
            new SupportingFileSpec("api_client.mustache", invokerFolder, "ApiClient.php"),
            new SupportingFileSpec("client_lifecycle.mustache", invokerFolder, "ClientLifecycle.php"),
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
            new SupportingFileSpec("auth/composite_authenticator.mustache", Path.of(SRC_BASE_PATH, "Auth").toString(), "CompositeAuthenticator.php"),
            new SupportingFileSpec("composer.mustache", "", "composer.json"),
            new SupportingFileSpec("phpstan_neon.mustache", "", "phpstan.neon"),
            new SupportingFileSpec("rector.mustache", "", "rector.php"),
            new SupportingFileSpec("phpcs_xml.mustache", "", "phpcs.xml"),
            new SupportingFileSpec("php_cs_fixer.mustache", "", ".php-cs-fixer.dist.php"),
            new SupportingFileSpec("phpdoc_dist_xml.mustache", "", "phpdoc.dist.xml"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore"),
            new SupportingFileSpec(
                "dev_dependencies.mustache", ".openapi-generator", "DEV-DEPENDENCIES")
        );
    }

    /**
     * Registers the {@code phpdocDescription} lambda. Free prose from the
     * document (info, tag and external-docs descriptions) is placed inside a
     * docblock; a literal comment terminator in it would end the docblock early
     * and a newline would leave the following lines without the leading
     * asterisk. The lambda neutralises the terminator the same way
     * {@code escapeUnsafeCharacters} does and continues every line inside the
     * docblock.
     */
    @Override
    protected com.google.common.collect.ImmutableMap.Builder<
                    String, com.samskivert.mustache.Mustache.Lambda>
            addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put(
                        "phpdocDescription",
                        (fragment, writer) ->
                                writer.write(escapeForPhpdoc(fragment.execute())))
                .put(
                        "phpSingleQuoted",
                        (fragment, writer) ->
                                writer.write(escapePhpSingleQuoted(fragment.execute())));
    }

    /**
     * Makes doc-comment prose safe inside a PHP docblock line that starts with
     * {@code " * "}.
     *
     * @param text the prose to escape
     * @return the prose with comment delimiters broken and continuation lines prefixed
     */
    private static String escapeForPhpdoc(@Nullable String text) {
        if (text == null) {
            return "";
        }
        final String safe =
                text.replace("*/", "*_/").replace("\r\n", "\n").strip();
        final StringBuilder sb = new StringBuilder(safe.length());
        final String[] lines = safe.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].stripTrailing();
            if (i > 0) {
                sb.append(line.isEmpty() ? "\n *" : "\n * ");
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Escapes text for the body of a PHP single-quoted string literal, where
     * only the backslash and the single quote are special.
     *
     * @param text the raw text
     * @return the text safe between two single quotes
     */
    static String escapePhpSingleQuoted(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * Renders a raw string as a PHP string literal that evaluates to exactly
     * that string. A single-quoted literal is used unless the text holds a
     * control character, which only a double-quoted literal can spell as an
     * escape sequence.
     *
     * @param text the raw string value
     * @return a PHP string literal, quotes included
     */
    static String toPhpStringLiteral(String text) {
        boolean hasControl = false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < 0x20 || text.charAt(i) == 0x7F) {
                hasControl = true;
                break;
            }
        }
        if (!hasControl) {
            return "'" + escapePhpSingleQuoted(text) + "'";
        }
        final StringBuilder sb = new StringBuilder(text.length() + 8).append('"');
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c == '$') {
                sb.append("\\$");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c < 0x20 || c == 0x7F) {
                sb.append(String.format(java.util.Locale.ROOT, "\\x%02X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Turns an arbitrary enum value into a PHP enum case identifier: every
     * character that cannot appear in an identifier becomes an underscore.
     *
     * @param name the case name computed from the wire value
     * @return a valid PHP identifier
     */
    static String toPhpEnumCaseName(String name) {
        String cleaned = name.replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("_+", "_");
        if (cleaned.length() > 1) {
            cleaned = cleaned.replaceAll("^_|_$", "");
        }
        if (cleaned.isEmpty() || Character.isDigit(cleaned.charAt(0))) {
            cleaned = "V_" + cleaned;
        }
        return cleaned;
    }

    /**
     * The composer name used when no packageName is configured.
     *
     * @param invokerPackage the PHP root namespace
     * @return {@code openapi/<namespace in lower case>}
     */
    private static String defaultComposerName(String invokerPackage) {
        return "openapi/" + invokerPackage.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The package metadata lines of {@code composer.json} that the generator options produce
     * ({@code authors}, {@code homepageUrl}, {@code repositoryUrl}, {@code documentationUrl},
     * {@code issueTrackerUrl}), each one a complete, comma-terminated JSON member line; empty when
     * none is set. {@code authors} is a comma or semicolon separated list of {@code Name} or
     * {@code Name <email>}.
     */
    static String composerExtras(Map<String, Object> options) {
        final StringBuilder extras = new StringBuilder();
        final String homepage = optionText(options, "homepageUrl");
        if (homepage != null) {
            extras.append("    \"homepage\": ").append(jsonString(homepage)).append(",\n");
        }
        final List<String> authorLines = new ArrayList<>();
        for (final String trimmed : authorList(options.get("authors"))) {
            final int open = trimmed.indexOf('<');
            final int close = trimmed.indexOf('>', open + 1);
            if (open > 0 && close > open) {
                authorLines.add(
                        "{\"name\": " + jsonString(trimmed.substring(0, open).trim())
                                + ", \"email\": "
                                + jsonString(trimmed.substring(open + 1, close).trim()) + "}");
            } else {
                authorLines.add("{\"name\": " + jsonString(trimmed) + "}");
            }
        }
        if (!authorLines.isEmpty()) {
            extras.append("    \"authors\": [").append(String.join(", ", authorLines)).append("],\n");
        }
        final List<String> support = new ArrayList<>();
        addSupport(support, "source", optionText(options, "repositoryUrl"));
        addSupport(support, "docs", optionText(options, "documentationUrl"));
        addSupport(support, "issues", optionText(options, "issueTrackerUrl"));
        if (!support.isEmpty()) {
            extras.append("    \"support\": {").append(String.join(", ", support)).append("},\n");
        }
        return extras.toString();
    }

    /**
     * The authors option as a list: a comma or semicolon separated string, or an iterable of
     * strings, each trimmed and none blank.
     */
    static List<String> authorList(@Nullable Object option) {
        final List<String> raw = new ArrayList<>();
        if (option instanceof Iterable<?>) {
            for (final Object author : (Iterable<?>) option) {
                raw.add(String.valueOf(author));
            }
        } else if (option != null) {
            raw.addAll(Arrays.asList(option.toString().split("[,;]")));
        }
        final List<String> authors = new ArrayList<>();
        for (final String author : raw) {
            if (!author.trim().isEmpty()) {
                authors.add(author.trim());
            }
        }
        return authors;
    }

    private static void addSupport(List<String> support, String key, @Nullable String url) {
        if (url != null) {
            support.add("\"" + key + "\": " + jsonString(url));
        }
    }

    @Nullable
    private static String optionText(Map<String, Object> options, String key) {
        final Object value = options.get(key);
        return value != null && isNotBlank(value.toString()) ? value.toString().trim() : null;
    }

    private static String jsonString(String text) {
        final StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"':
                    json.append("\\\"");
                    break;
                case '\\':
                    json.append("\\\\");
                    break;
                case '\n':
                    json.append("\\n");
                    break;
                case '\r':
                    json.append("\\r");
                    break;
                case '\t':
                    json.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
            }
        }
        return json.append('"').toString();
    }

    @Override
    public void processOpts() {
        super.processOpts();

        invokerPackage = getPropertyOrDefault(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
        additionalProperties.put("invokerPackage", invokerPackage);
        final String packageVersion = getPropertyOrDefault("packageVersion", "1.0.0");
        additionalProperties.put("packageVersion", packageVersion);
        final Object packageNameProp = additionalProperties.get("packageName");
        final String composerName =
                packageNameProp != null && isNotBlank(packageNameProp.toString())
                        ? packageNameProp.toString()
                        : defaultComposerName(invokerPackage);
        final String composerPackage = composerName.substring(composerName.indexOf('/') + 1);
        additionalProperties.put(
                "userAgentDefault", composerPackage + "/" + packageVersion + " (php)");

        additionalProperties.put("credentialSchemeEnvVars", new ArrayList<Map<String, Object>>());
        additionalProperties.remove("bearerScheme");
        additionalProperties.remove("basicScheme");
        additionalProperties.remove("apiKeyScheme");
        additionalProperties.put("composerExtras", composerExtras(additionalProperties));
        additionalProperties.put(
                "authorsText", String.join(", ", authorList(additionalProperties.get("authors"))));

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
                            "tests/SerializerProbe.mustache",
                            "tests",
                            "SerializerProbe.php"));
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
                            "tests/CompositeAuthenticatorTest.mustache",
                            "tests",
                            "CompositeAuthenticatorTest.php"));
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
                            "tests/StubbedDefaultApiClient.mustache",
                            "tests",
                            "StubbedDefaultApiClient.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/Psr18ApiClientUnitTest.mustache",
                            "tests",
                            "Psr18ApiClientUnitTest.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/StubPsr18Client.mustache",
                            "tests",
                            "StubPsr18Client.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/MultipartModelPart.mustache",
                            "tests",
                            "MultipartModelPart.php"));
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
                            "tests/CapturingApiClient.mustache",
                            "tests",
                            "CapturingApiClient.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/TestableApi.mustache",
                            "tests",
                            "TestableApi.php"));
            supportingFiles.add(
                    new SupportingFile(
                            "tests/TestAuthenticator.mustache",
                            "tests",
                            "TestAuthenticator.php"));
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

    /*
     * A model named like a type PHP forbids as a class name (String, Object, Int) takes the
     * Model prefix; Error and Result are legal in the models namespace and keep their name.
     */
    @Override
    public String toModelName(String name) {
        final String modelName = super.toModelName(name);
        if (FORBIDDEN_CLASS_NAMES.contains(modelName.toLowerCase(Locale.ROOT))) {
            return "Model" + modelName;
        }
        return modelName;
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
                    .map(d -> toPhpStringLiteral(String.valueOf(d)))
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

    /**
     * A single-quoted PHP literal interprets only {@code \\} and {@code \'},
     * so control characters stay raw in enum literals.
     */
    @Override
    protected boolean escapesEnumControlCharacters() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        return List.of(
                new OAuthTestFileSpec("tests/BasicAuthenticatorTest.mustache", "tests", "BasicAuthenticatorTest.php", OAuthTestCondition.BASIC),
                new OAuthTestFileSpec("tests/MockTokenApiClient.mustache", "tests", "MockTokenApiClient.php", OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec("tests/OAuth2TokenManagerTest.mustache", "tests", "OAuth2TokenManagerTest.php", OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec("tests/OAuth2AuthCodeAuthenticatorTest.mustache", "tests", "OAuth2AuthorizationCodeAuthenticatorTest.php", OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec("tests/OAuth2ImplicitAuthenticatorTest.mustache", "tests", "OAuth2ImplicitAuthenticatorTest.php", OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec("tests/OAuth2ClientCredentialsAuthenticatorTest.mustache", "tests", "OAuth2ClientCredentialsAuthenticatorTest.php", OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec("tests/OAuth2PasswordAuthenticatorTest.mustache", "tests", "OAuth2PasswordAuthenticatorTest.php", OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec("tests/OpenIdConnectAuthenticatorTest.mustache", "tests", "OpenIdConnectAuthenticatorTest.php", OAuthTestCondition.OIDC));
    }

    /**
     * Registers the shared auth files, then the Bearer and API-key
     * authenticator tests. Those tests are emitted only when the spec declares
     * the scheme; the scheme flags are set in {@code processOpenAPI}, after
     * {@code processOpts}, so they cannot be read there.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();
        if (!generateTests) {
            return;
        }
        if (hasBearerAuth) {
            supportingFiles.add(new SupportingFile(
                    "tests/BearerAuthenticatorTest.mustache", "tests", "BearerAuthenticatorTest.php"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(new SupportingFile(
                    "tests/ApiKeyAuthenticatorTest.mustache", "tests", "ApiKeyAuthenticatorTest.php"));
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
            if (SENSITIVE_PARAMS.contains(name)) {
                param.put("sensitive", "true");
            }
            if (readsEnvironment(spec, name)) {
                /* The credential may be left out and read from the environment. */
                param.put("type", "?string");
                param.put("defaultNull", "true");
            }
            constructorParams.add(param);
        }
        final List<Map<String, Object>> credentialEnvVars = schemeCredentialEnvVars(spec, ctx);
        ctx.put("credentialEnvVars", credentialEnvVars);
        ctx.put("hasCredentialEnvVars", !credentialEnvVars.isEmpty());
        recordCredentialScheme(
                spec, spec.schemeClass() + spec.oauthSuffix() + "Authenticator", credentialEnvVars);
        recordOAuthScheme(spec);
        ctx.put("constructorParams", constructorParams);
        final List<String> superArgs = new ArrayList<>();
        for (final String arg : buildPhpSuperArgs(spec)) {
            superArgs.add(resolvedOAuthCredential(spec, arg));
        }
        ctx.put("superArgs", superArgs);
        ctx.put("hasCustomConstructor", !isPassthroughConstructor(constructorParams, superArgs));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    /**
     * Whether the authenticator of this scheme reads the named constructor argument from the
     * environment when it is left out: a credential the scheme takes, in a basic, bearer, API key
     * or OAuth 2 scheme. The host is never read from the environment, and OpenID Connect reads
     * none.
     */
    private static boolean readsEnvironment(SchemeAuthSpec spec, String name) {
        return CREDENTIAL_FALLBACK_BASES.contains(spec.baseClass())
                && !"host".equals(name)
                && spec.paramNames().contains(name);
    }

    /**
     * The argument a scheme class passes to an OAuth 2 base class for a credential it reads from
     * the environment: the argument resolved by {@code credentialOrEnvironment}, so the explicit
     * value wins and the variable is read once, when the authenticator is built. Any other
     * argument is returned unchanged.
     */
    private static String resolvedOAuthCredential(SchemeAuthSpec spec, String arg) {
        if (!spec.isOAuth() || !arg.startsWith("$")) {
            return arg;
        }
        final String name = arg.substring(1);
        if (!readsEnvironment(spec, name)) {
            return arg;
        }
        return "static::credentialOrEnvironment($" + name + ", '" + name + "')";
    }

    /**
     * The environment variables of this scheme that its authenticator reads: the shared
     * {@code envVars} of the scheme, narrowed to the credentials the authenticator takes from the
     * environment. Each entry has {@code field} and {@code envName}.
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> schemeCredentialEnvVars(
            SchemeAuthSpec spec, Map<String, Object> ctx) {
        final List<Map<String, Object>> out = new ArrayList<>();
        final List<Map<String, Object>> shared = (List<Map<String, Object>>) ctx.get("envVars");
        if (shared == null) {
            return out;
        }
        for (final Map<String, Object> entry : shared) {
            if (readsEnvironment(spec, String.valueOf(entry.get("field")))) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * Records the scheme for the README, the skills page and the authenticator tests:
     * {@code credentialSchemeEnvVars} lists every variable (class, field, name) and
     * {@code bearerScheme}, {@code basicScheme} and {@code apiKeyScheme} hold the first scheme of
     * each kind, whose generated class the tests build.
     */
    @SuppressWarnings("unchecked")
    private void recordCredentialScheme(
            SchemeAuthSpec spec, String className, List<Map<String, Object>> envVars) {
        if (envVars.isEmpty()) {
            return;
        }
        final List<Map<String, Object>> all =
                (List<Map<String, Object>>)
                        additionalProperties.computeIfAbsent(
                                "credentialSchemeEnvVars", k -> new ArrayList<Map<String, Object>>());
        final Map<String, Object> first = new HashMap<>();
        first.put("className", className);
        for (final Map<String, Object> entry : envVars) {
            final Map<String, Object> row = new HashMap<>();
            row.put("className", className);
            row.put("field", entry.get("field"));
            row.put("envName", entry.get("envName"));
            all.add(row);
            first.put(entry.get("field") + "Env", entry.get("envName"));
        }
        final String key;
        switch (spec.baseClass()) {
            case "BearerAuthenticator":
                key = "bearerScheme";
                break;
            case "BasicAuthenticator":
                key = "basicScheme";
                break;
            case "ApiKeyAuthenticator":
                key = "apiKeyScheme";
                break;
            default:
                return;
        }
        additionalProperties.putIfAbsent(key, first);
    }

    /**
     * Records the first scheme of each OAuth2 grant the tests can build ({@code
     * oauthClientCredentialsScheme}, {@code oauthPasswordScheme}, {@code oauthAuthCodeScheme},
     * {@code oauthImplicitScheme}):
     * its generated class, the token and authorization URLs of the spec as PHP literals, and the
     * spec scopes, so the scheme wrapper tests can assert what the wrapper sends.
     */
    private void recordOAuthScheme(SchemeAuthSpec spec) {
        final String key;
        switch (spec.baseClass()) {
            case "OAuth2ClientCredentialsAuthenticator":
                key = "oauthClientCredentialsScheme";
                break;
            case "OAuth2PasswordAuthenticator":
                key = "oauthPasswordScheme";
                break;
            case "OAuth2AuthorizationCodeAuthenticator":
                key = "oauthAuthCodeScheme";
                break;
            case "OAuth2ImplicitAuthenticator":
                key = "oauthImplicitScheme";
                break;
            default:
                return;
        }
        final Map<String, Object> scheme = new HashMap<>();
        scheme.put("className", spec.schemeClass() + spec.oauthSuffix() + "Authenticator");
        scheme.put("tokenUrlLiteral", toPhpStringLiteral(java.util.Objects.toString(spec.tokenUrl(), "")));
        scheme.put("refreshUrlLiteral", toPhpStringLiteral(java.util.Objects.toString(
                spec.refreshUrl() != null ? spec.refreshUrl() : spec.tokenUrl(), "")));
        scheme.put("authorizationUrlLiteral",
                toPhpStringLiteral(java.util.Objects.toString(spec.authorizationUrl(), "")));
        scheme.put("scopesLiteral", formatPhpScopes(spec.scopes()));
        scheme.put("hasScopes", spec.scopes() != null && !spec.scopes().isEmpty());
        additionalProperties.putIfAbsent(key, scheme);
    }

    private static String formatPhpScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        final List<String> literals = new ArrayList<>();
        for (final String scope : scopes.keySet()) {
            literals.add(toPhpStringLiteral(scope));
        }
        return "[" + String.join(", ", literals) + "]";
    }

    private List<String> buildPhpSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("$host", "$username", "$password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("$host", "$token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("$host", toPhpStringLiteral(java.util.Objects.toString(spec.keyParamName(), "")), "$apiKey",
                    "ApiKeyLocation::" + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            final String scopes = formatPhpScopes(spec.scopes());
            return List.of("$host", "$clientId", "$clientSecret",
                    toPhpStringLiteral(java.util.Objects.toString(spec.tokenUrl(), "")), scopes);
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? toPhpStringLiteral(java.util.Objects.toString(spec.refreshUrl(), "")) : "null";
            final String scopes = formatPhpScopes(spec.scopes());
            return List.of("$host", "$clientId", "$clientSecret",
                    toPhpStringLiteral(java.util.Objects.toString(spec.tokenUrl(), "")),
                    "$username", "$password", scopes, refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? toPhpStringLiteral(java.util.Objects.toString(spec.refreshUrl(), "")) : "null";
            final String scopes = formatPhpScopes(spec.scopes());
            return List.of("$host", "$clientId", "$clientSecret",
                    toPhpStringLiteral(java.util.Objects.toString(spec.authorizationUrl(), "")), toPhpStringLiteral(java.util.Objects.toString(spec.tokenUrl(), "")),
                    "$redirectUri", scopes, refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            final String scopes = formatPhpScopes(spec.scopes());
            return List.of("$host", "$clientId",
                    toPhpStringLiteral(java.util.Objects.toString(spec.authorizationUrl(), "")), scopes);
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("$host", toPhpStringLiteral(java.util.Objects.toString(spec.openIdConnectUrl(), "")),
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
            final List<String> exampleLines = exampleDocLines(p);
            param.put("exampleLines", exampleLines);
            /* A parameter with named examples is documented even when its docblock
             * type equals its native type, so the examples reach the options class. */
            final boolean hasDocType = !phpType.equals(phpDocType) || !exampleLines.isEmpty();
            param.put("hasDocType", hasDocType);
            if (hasDocType) {
                hasAnyDocTypes = true;
            }
            param.put("required", p.required);
            param.put("requiredNullable", isRequiredNullableQuery(p));
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
            /* A model-typed query or header parameter (style or content
             * serialised) can arrive without a baseType; its dataType is the
             * model class and still needs the import. */
            if (p.isModel
                    && !p.isArray
                    && !p.isMap
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)) {
                modelTypes.add(p.dataType.substring(p.dataType.lastIndexOf('\\') + 1));
            }
            if (p.isEnumRef
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)) {
                /* A component enum's dataType may already be the fully
                 * qualified name; the import takes the short class name. */
                modelTypes.add(p.dataType.substring(p.dataType.lastIndexOf('\\') + 1));
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
    protected String errorSchemaModelName(String dataType) {
        return dataType.substring(dataType.lastIndexOf('\\') + 1);
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaTemplate() {
        return "errors/SchemaException.mustache";
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaFilePath(String className) {
        return Path.of(outputFolder, SRC_BASE_PATH, "Errors", className + ".php").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        return Path.of(outputFolder, SRC_BASE_PATH, "Api", "Options", optionsClassName + ".php")
                .toString();
    }

    /**
     * The named examples of a parameter as docblock lines, each {@code value summary}, with
     * the comment terminator neutralised. Empty when the parameter declares none.
     */
    static List<String> exampleDocLines(CodegenParameter p) {
        final List<String> lines = new ArrayList<>();
        Object pluralExamples =
                p.vendorExtensions == null ? null : p.vendorExtensions.get("pluralExamples");
        if (!(pluralExamples instanceof List) && p.examples != null && !p.examples.isEmpty()) {
            /* The options class is written before the shared pass lifts the OAS examples map into
             * pluralExamples, so read the map itself. */
            pluralExamples = buildPluralExamplesList(p.examples);
        }
        if (!(pluralExamples instanceof List)) {
            return lines;
        }
        final List<?> examples = (List<?>) pluralExamples;
        for (final Object example : examples) {
            if (example instanceof Map) {
                final Map<?, ?> entry = (Map<?, ?>) example;
                final Object value = entry.get("value");
                final Object summary = entry.get("summary");
                lines.add(escapeForPhpdoc(value + " " + summary));
            }
        }
        return lines;
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
     * Derives the native return type declaration from the PHPDoc return type
     * by dropping the generic argument list ({@code \Ds\Vector<Pet>} becomes
     * {@code \Ds\Vector}). Only a class name or a scalar the deserializer is
     * known to return as that exact type is declared; anything else (for
     * example {@code object}, which decodes to an array) stays docblock-only.
     */
    @Nullable
    static String phpNativeReturnType(@Nullable String phpDocType) {
        if (phpDocType == null) {
            return null;
        }
        final int generics = phpDocType.indexOf('<');
        final String base = generics < 0 ? phpDocType : phpDocType.substring(0, generics);
        if (base.matches("string|int|float|bool|\\\\[A-Za-z_][A-Za-z0-9_\\\\]*")) {
            return base;
        }
        return null;
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
     * Every URI format ({@code uri}, {@code uri-reference}, {@code uri-template}) is a plain
     * {@code string} in PHP: the native URI type needs PHP 8.5 and the floor is 8.3. The
     * subformat check stays so a future typed mapping of {@code uri} does not capture the other two.
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        keepStringForUriSubformats(property, "string");
    }

    /**
     * The PHP constant expression for a {@code default} written next to a {@code $ref} (F866), so
     * the constructor starts the property at that value: an enum case ({@code \Ns\Availability::ON_HOLD})
     * for a referenced enum, a literal for a referenced string, integer, number or boolean.
     * Returns {@code null} when the property has no such default or its type has no constant form.
     *
     * @param property the property after the shared pass captured the sibling default
     * @return the constructor default, or {@code null}
     */
    @Nullable
    static String siblingDefaultLiteral(CodegenProperty property) {
        if (property.vendorExtensions == null || property.vendorExtensions.get("siblingDefault") == null) {
            return null;
        }
        final String wire = String.valueOf(property.vendorExtensions.get("siblingDefault"));
        if (Boolean.TRUE.equals(property.vendorExtensions.get("siblingDefaultIsEnum"))) {
            final Object member = property.vendorExtensions.get("siblingDefaultEnumVar");
            if (member == null || property.dataType == null) {
                return null;
            }
            return property.dataType + "::" + member;
        }
        final String type = property.dataType;
        if ("string".equals(type)) {
            return toPhpStringLiteral(wire);
        }
        try {
            if ("int".equals(type)) {
                return Long.toString(Long.parseLong(wire));
            }
            if ("float".equals(type)) {
                final double value = Double.parseDouble(wire);
                return Double.isFinite(value) ? wire : null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if ("bool".equals(type) && ("true".equals(wire) || "false".equals(wire))) {
            return wire;
        }
        return null;
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
                        final String nativeType = phpNativeReturnType(phpDoc);
                        if (nativeType != null) {
                            op.vendorExtensions.put("phpNativeReturnType", nativeType);
                        }
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
            final List<CodegenOperation> orderedOps =
                    (List<CodegenOperation>) processedOps.get("operation");
            if (orderedOps != null) {
                for (final CodegenOperation op : orderedOps) {
                    final boolean binaryBody =
                            op.bodyParam != null && "\\SplFileObject".equals(op.bodyParam.dataType);
                    if (binaryBody) {
                        op.bodyParam.dataType = BINARY_BODY_TYPE;
                    }
                    final Object deco = op.vendorExtensions == null
                            ? null : op.vendorExtensions.get("op");
                    if (deco instanceof Map) {
                        final Map<String, Object> decoMap = (Map<String, Object>) deco;
                        final Object sigArgs = decoMap.get("signatureArgs");
                        if (sigArgs instanceof List) {
                            final List<Map<String, Object>> ordered =
                                    serverBeforeOptions((List<Map<String, Object>>) sigArgs);
                            if (binaryBody) {
                                for (final Map<String, Object> arg : ordered) {
                                    if (Boolean.TRUE.equals(arg.get("isBody"))) {
                                        arg.put("dataType", BINARY_BODY_TYPE);
                                    }
                                }
                            }
                            decoMap.put("signatureArgs", ordered);
                        }
                    }
                }
            }
            processed.put("hasServerTypeDefs", processedOps.get("hasServerTypeDefs"));
            final List<Map<String, Object>> serverTypeDefs =
                    (List<Map<String, Object>>) processedOps.get("serverTypeDefs");
            if (serverTypeDefs != null) {
                writeServerTypeFiles(serverTypeDefs);
            }
        }
        return processed;
    }

    /**
     * Returns the operation signature arguments with the per-operation server placed before the
     * optional-parameters object: required parameters, then server, then options. A required
     * options object stays before the server, because PHP deprecates an optional argument that
     * precedes a required one.
     *
     * @param args the shared signature arguments (path, body, options, server)
     * @return a new list with the server entry directly before the options entry
     */
    static List<Map<String, Object>> serverBeforeOptions(final List<Map<String, Object>> args) {
        for (final Map<String, Object> arg : args) {
            if (Boolean.TRUE.equals(arg.get("isOptions"))
                    && !Boolean.TRUE.equals(arg.get("nullable"))) {
                return new ArrayList<>(args);
            }
        }
        final List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> options = null;
        for (final Map<String, Object> arg : args) {
            if (Boolean.TRUE.equals(arg.get("isOptions"))) {
                options = arg;
            } else if (Boolean.TRUE.equals(arg.get("isServer"))) {
                result.add(arg);
                if (options != null) {
                    result.add(options);
                    options = null;
                }
            } else {
                result.add(arg);
            }
        }
        if (options != null) {
            result.add(options);
        }
        return result;
    }

    /**
     * Writes each per-operation server class, server-variable enum and
     * server variant into its own file named after the class. PSR-4 loads a
     * class only from a file named after it, so a class declared inside the
     * API group's file could not be loaded before the API class itself.
     */
    @SuppressWarnings("unchecked")
    private void writeServerTypeFiles(List<Map<String, Object>> serverTypeDefs) {
        for (final Map<String, Object> typeDef : serverTypeDefs) {
            final String serverTypeName = String.valueOf(typeDef.get("serverTypeName"));
            writePhpClassFile(apiFileFolder(), serverTypeName, "api/server_type.mustache",
                    new HashMap<>(typeDef));
            final List<Map<String, Object>> variants =
                    (List<Map<String, Object>>) typeDef.get("variants");
            if (variants == null) {
                continue;
            }
            for (final Map<String, Object> variant : variants) {
                final List<Map<String, Object>> vars =
                        (List<Map<String, Object>>) variant.get("serverVariables");
                if (vars != null) {
                    for (final Map<String, Object> var : vars) {
                        if (Boolean.TRUE.equals(var.get("hasEnumValues"))) {
                            final Map<String, Object> ctx = new HashMap<>(var);
                            ctx.put("serverTypeName", serverTypeName);
                            final List<Map<String, Object>> phpCases = new ArrayList<>();
                            final List<Map<String, Object>> rawCases =
                                    Optional.ofNullable(
                                                    (List<Map<String, Object>>)
                                                            var.get("enumValues"))
                                            .orElse(List.of());
                            for (final Map<String, Object> ev : rawCases) {
                                final Map<String, Object> phpCase = new HashMap<>(ev);
                                phpCase.put("name",
                                        toPhpEnumCaseName(String.valueOf(ev.get("name"))));
                                phpCases.add(phpCase);
                            }
                            ctx.put("enumValues", phpCases);
                            writePhpClassFile(apiFileFolder(),
                                    serverTypeName + var.get("enumTypeName"),
                                    "api/server_variable_enum.mustache", ctx);
                        }
                    }
                }
                final Map<String, Object> ctx = new HashMap<>(variant);
                ctx.put("serverTypeName", serverTypeName);
                writePhpClassFile(apiFileFolder(),
                        serverTypeName + variant.get("variantName"),
                        "api/server_variant.mustache", ctx);
            }
        }
    }

    /**
     * Renders each inline (property-level) enum of every model into its own
     * file named after the enum. PSR-4 loads a class only from a file named
     * after it, so an enum declared inside its model's file could not be
     * referenced before the model itself had been loaded.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            if (model == null || model.isEnum) {
                continue;
            }
            modelMap.put(
                    "hasByteVariants",
                    model.getComposedSchemas() != null
                            && model.getComposedSchemas().getOneOf() != null
                            && model.getComposedSchemas().getOneOf().stream().anyMatch(
                                    BetterPHPCodegen::holdsBytes));
            modelMap.put("unionVariants", unionVariants(model));
            if (model.vars == null) {
                continue;
            }
            addSiblingDefaultLiterals(model);
            modelMap.put("hasByteVars", model.vars.stream().anyMatch(
                    BetterPHPCodegen::holdsBytes));
            addCopyArguments(model);
            modelMap.put("hasPasswordVars", model.vars.stream().anyMatch(
                    var -> var.isPassword));
            final List<String> requiredNullableNames = new ArrayList<>();
            for (final CodegenProperty var : model.vars) {
                if (var.required && var.isNullable && !var.isAnyType) {
                    requiredNullableNames.add(var.baseName);
                }
            }
            modelMap.put("requiredNullableNames", requiredNullableNames);
            modelMap.put("hasRequiredNullableVars", !requiredNullableNames.isEmpty());
            for (final CodegenProperty var : model.vars) {
                if (!var.isEnum || var.allowableValues == null) {
                    continue;
                }
                final String enumClassName = model.classname + var.enumName;
                final Map<String, Object> ctx = new HashMap<>();
                ctx.put("modelPackage", modelPackage);
                ctx.put("modelClassname", model.classname);
                ctx.put("baseName", var.baseName);
                ctx.put("enumClassName", enumClassName);
                ctx.put("backingType", var.isInteger || var.isLong ? "int" : "string");
                ctx.put("enumVars", var.allowableValues.get("enumVars"));
                writePhpClassFile(modelFileFolder(), enumClassName,
                        "models/inline_enum.mustache", ctx);
            }
        }
        return result;
    }

    /**
     * Writes {@code siblingDefaultLiteral} onto every property that has a constant form of its
     * default beside a {@code $ref}, in each list the model template reads. It is not the
     * property's own default, which would move the property behind the other optional ones and
     * drop its {@code null}, so the constructor reads this one instead. The lists hold separate
     * property objects, so each is visited.
     */
    private static void addSiblingDefaultLiterals(CodegenModel model) {
        final List<List<CodegenProperty>> lists =
                Arrays.asList(model.vars, model.allVars, model.requiredVars, model.optionalVars);
        for (final List<CodegenProperty> list : lists) {
            if (list == null) {
                continue;
            }
            for (final CodegenProperty property : list) {
                final String literal = siblingDefaultLiteral(property);
                if (literal != null) {
                    property.vendorExtensions.put("siblingDefaultLiteral", literal);
                }
            }
        }
    }

    /**
     * Gives each property of an immutable model its copy method: {@code withMethod} is the
     * method name and {@code copyArguments} the named constructor arguments of the copy, every
     * other property carried over and this one replaced by the method parameter.
     */
    private static void addCopyArguments(CodegenModel model) {
        for (int index = 0; index < model.vars.size(); index++) {
            final CodegenProperty property = model.vars.get(index);
            final List<Map<String, String>> arguments = new ArrayList<>();
            for (int other = 0; other < model.vars.size(); other++) {
                final String name = model.vars.get(other).name;
                final Map<String, String> argument = new HashMap<>();
                argument.put("line", name + ": " + (other == index ? "$" : "$this->") + name);
                arguments.add(argument);
            }
            if (model.isAdditionalPropertiesTrue) {
                final Map<String, String> carried = new HashMap<>();
                carried.put("line", "additionalProperties: $this->additionalProperties");
                arguments.add(carried);
            }
            if (property.vendorExtensions == null) {
                property.vendorExtensions = new HashMap<>();
            }
            property.vendorExtensions.put("withMethod", "with" + upperFirst(property.name));
            property.vendorExtensions.put("copyArguments", arguments);
        }
    }

    private static String upperFirst(String name) {
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /**
     * The variants of a oneOf or anyOf wrapper that get a typed factory and accessors
     * ({@code fromX}, {@code isX}, {@code asX}). Each entry carries {@code label} (the method
     * suffix), {@code nativeType}, {@code check} (a test of {@code $value}) and the variant
     * property as {@code prop} for its PHPDoc type. A variant of no checkable type is left out.
     */
    private static List<Map<String, Object>> unionVariants(CodegenModel model) {
        final List<Map<String, Object>> variants = new ArrayList<>();
        if (model.getComposedSchemas() == null) {
            return variants;
        }
        List<CodegenProperty> source = model.getComposedSchemas().getOneOf();
        if (source == null || source.isEmpty()) {
            source = model.getComposedSchemas().getAnyOf();
        }
        if (source == null) {
            return variants;
        }
        final Set<String> labels = new HashSet<>();
        for (final CodegenProperty variant : source) {
            final String type = variant.dataType;
            if (type == null) {
                continue;
            }
            final String label;
            final String check;
            final String nativeType = type;
            if (type.startsWith("\\Ds\\") || type.equals("\\SplFileObject")) {
                continue;
            }
            if (type.startsWith("\\")) {
                final String shortName = type.substring(type.lastIndexOf('\\') + 1);
                label = shortName;
                check = "$value instanceof " + type;
            } else {
                switch (type) {
                    case "string":
                    case "byte":
                        label = "String";
                        check = "is_string($value)";
                        break;
                    case "int":
                    case "integer":
                        label = "Int";
                        check = "is_int($value)";
                        break;
                    case "float":
                    case "number":
                        label = "Float";
                        check = "is_float($value)";
                        break;
                    case "bool":
                    case "boolean":
                        label = "Bool";
                        check = "is_bool($value)";
                        break;
                    default:
                        continue;
                }
            }
            final String phpNativeType;
            switch (type) {
                case "byte":
                    phpNativeType = "string";
                    break;
                case "integer":
                    phpNativeType = "int";
                    break;
                case "number":
                    phpNativeType = "float";
                    break;
                case "boolean":
                    phpNativeType = "bool";
                    break;
                default:
                    phpNativeType = nativeType;
            }
            if (!labels.add(label)) {
                continue;
            }
            final Map<String, Object> entry = new HashMap<>();
            entry.put("label", label);
            entry.put("nativeType", phpNativeType);
            entry.put("check", check);
            entry.put("prop", variant);
            variants.add(entry);
        }
        return variants;
    }

    /**
     * Whether a property is a {@code format: byte} value, or a list or map of
     * them. The model template emits a private constant naming such properties
     * so the serializer can base64-encode and decode them at the wire boundary.
     */
    private static boolean holdsBytes(CodegenProperty prop) {
        if (prop.isByteArray) {
            return true;
        }
        return (prop.isArray || prop.isMap) && prop.items != null && prop.items.isByteArray;
    }

    /** Renders {@code template} with {@code ctx} into {@code folder/className.php}. */
    private void writePhpClassFile(
            String folder, String className, String template, Map<String, Object> ctx) {
        ctx.putIfAbsent("apiPackage", apiPackage);
        ctx.putIfAbsent("modelPackage", modelPackage);
        final String filePath = Path.of(folder, className + ".php").toString();
        writeFile(filePath, renderOptionsTemplate(template, ctx));
        // Files written through this side channel never enter upstream's
        // .openapi-generator/FILES manifest, so a manifest-driven prune in the
        // client's regeneration would delete them as orphans. Record them.
        registerExtraManifestFile(filePath);
        postProcessFile(Path.of(filePath).toFile(), "source");
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

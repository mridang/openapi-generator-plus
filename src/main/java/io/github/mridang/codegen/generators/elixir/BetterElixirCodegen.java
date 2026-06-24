package io.github.mridang.codegen.generators.elixir;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates an Elixir API client that uses Req for HTTP transport
 * and Jason for JSON serialization. Module names use PascalCase,
 * function and variable names use snake_case per Elixir convention.
 * Filenames use snake_case. The formatter pass invokes mix format
 * inside Docker to enforce canonical Elixir formatting.
 */
@SuppressWarnings("unused")
public class BetterElixirCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterElixirCodegen.class);

    protected String packageName = "petstore_client";
    protected String packageVersion = "1.0.0";
    protected String moduleName = "PetstoreClient";

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Elixir language. Type mappings convert OpenAPI
     * types to their Elixir equivalents (e.g. integer to integer,
     * string to String.t()). Reserved words are loaded from a
     * bundled word-list.
     */
    public BetterElixirCodegen() {
        outputFolder = Path.of("generated-code", "elixir").toString();
        embeddedTemplateDir = templateDir = "templates/elixir";

        modelTemplateFiles.put("models/model.mustache", ".ex");
        apiTemplateFiles.put("api/api.mustache", ".ex");

        modelPackage = "models";
        apiPackage = "api";

        typeMapping.put("string", "String.t()");
        typeMapping.put("boolean", "boolean()");
        typeMapping.put("int", "integer()");
        typeMapping.put("integer", "integer()");
        typeMapping.put("long", "integer()");
        typeMapping.put("short", "integer()");
        typeMapping.put("float", "float()");
        typeMapping.put("double", "float()");
        typeMapping.put("number", "float()");
        typeMapping.put("decimal", "float()");
        // Elixir's built-in Date/DateTime types preserve calendar
        // semantics that String.t() loses. ObjectSerializer can
        // pipe ISO-8601 wire strings through Date.from_iso8601!/1
        // and DateTime.from_iso8601/1 on decode.
        typeMapping.put("date", "Date.t()");
        typeMapping.put("DateTime", "DateTime.t()");
        // 4.8: format:time -> Elixir stdlib Time.t() (ISO-8601 HH:MM:SS[.fff]).
        // format:duration -> Elixir 1.17+ stdlib Duration.t(), carried on the
        // wire as google.protobuf.Duration protobuf-JSON ("<seconds>s", e.g.
        // "3600s", "1.5s"). Both are round-tripped through ObjectSerializer's
        // dedicated convert_to_type clauses. mix.exs targets ~> 1.18, so
        // Duration is always available; the older 1.16/Timex.Duration
        // fallback isn't shipped here because the supported floor is 1.18.
        typeMapping.put("time", "Time.t()");
        typeMapping.put("duration", "Duration.t()");
        typeMapping.put("array", "list");
        typeMapping.put("List", "list");
        typeMapping.put("set", "MapSet.t()");
        typeMapping.put("map", "map()");
        typeMapping.put("object", "map()");
        typeMapping.put("AnyType", "any()");
        typeMapping.put("file", "binary()");
        typeMapping.put("binary", "binary()");
        typeMapping.put("ByteArray", "binary()");
        typeMapping.put("UUID", "String.t()");
        typeMapping.put("URI", "String.t()");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "String.t()",
                                "boolean()",
                                "integer()",
                                "float()",
                                "binary()",
                                "map()",
                                "any()",
                                "atom()",
                                "list",
                                "nil",
                                // 4.8: stdlib types -- treat as primitives so
                                // the codegen doesn't try to emit a model for
                                // them or wrap them in Module.t() etc.
                                "Time.t()",
                                "Duration.t()"));

        reservedWords = loadReservedWords("/reserved-words/elixir.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.MODULE_NAME,
                CodegenConstants.MODULE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated Elixir package (default: 1.0.0).").defaultValue("1.0.0"));
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "elixir-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Elixir client with Req and Jason.";
    }

    /**
     * Declares Elixir as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.ELIXIR;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "test/fixtures";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "test/spec";
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getVarCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getOperationIdCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getEnumCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "elixir:1.19@sha256:4c0f76111c8224e6cd2e3f2733a38dbac31c1609a79bce1d1242a1c4acffd4d9";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {"mix format"};
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("integer()", "float()");
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayTypeTemplate() {
        return "[%2$s]";
    }

    /**
     * Elixir uses {@code MapSet.t(Type)} for unique-item collections
     * (the idiomatic Elixir set type).
     */
    @Override
    protected String getUniqueItemsSetType() {
        return "MapSet.t($1)";
    }

    /**
     * Matches Elixir's bracket-wrapped type syntax {@code [Type]},
     * capturing the inner type for replacement by
     * {@link #getUniqueItemsSetType()}.
     */
    @Override
    protected String getArrayContainerPattern() {
        return "^\\[(.+)\\]$";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "%%{%2$s => %3$s}";
    }

    /**
     * Returns {@code String.t()} as the map key type because Elixir
     * maps use string keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "String.t()";
    }

    /**
     * Returns {@code any()} as the default map value type
     * for Elixir's dynamic type.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "any()";
    }

    /**
     * Resolves user-supplied options and registers all supporting
     * files for the Elixir mix project structure. Sets up the
     * source layout with models, api, errors, auth, and supporting
     * infrastructure in the lib/ directory.
     */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String pkg = Optional.ofNullable((String) additionalProperties.get("packageName"))
                .orElse(packageName);
        final String libDir = Path.of("lib", pkg).toString();
        final String apiDir = Path.of(libDir, "api").toString();
        final String modelsDir = Path.of(libDir, "models").toString();
        final String errorsDir = Path.of(libDir, "errors").toString();
        return List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("api_module.mustache", apiDir, "api_module.ex"),
            new SupportingFileSpec("models_module.mustache", modelsDir, "models_module.ex"),
            new SupportingFileSpec("configuration.mustache", libDir, "configuration.ex"),
            new SupportingFileSpec("transport_options.mustache", libDir, "transport_options.ex"),
            new SupportingFileSpec("server_configuration.mustache", libDir, "server_configuration.ex"),
            new SupportingFileSpec("servers.mustache", libDir, "servers.ex"),
            new SupportingFileSpec("api_error.mustache", libDir, "api_error.ex"),
            new SupportingFileSpec("errors/client_error.mustache", errorsDir, "client_error.ex"),
            new SupportingFileSpec("errors/server_error.mustache", errorsDir, "server_error.ex"),
            new SupportingFileSpec("errors/bad_request_error.mustache", errorsDir, "bad_request_error.ex"),
            new SupportingFileSpec("errors/unauthorized_error.mustache", errorsDir, "unauthorized_error.ex"),
            new SupportingFileSpec("errors/forbidden_error.mustache", errorsDir, "forbidden_error.ex"),
            new SupportingFileSpec("errors/not_found_error.mustache", errorsDir, "not_found_error.ex"),
            new SupportingFileSpec("errors/conflict_error.mustache", errorsDir, "conflict_error.ex"),
            new SupportingFileSpec("errors/unprocessable_entity_error.mustache", errorsDir, "unprocessable_entity_error.ex"),
            new SupportingFileSpec("errors/internal_server_error.mustache", errorsDir, "internal_server_error.ex"),
            new SupportingFileSpec("error.mustache", libDir, "error.ex"),
            new SupportingFileSpec("header_selector.mustache", libDir, "header_selector.ex"),
            new SupportingFileSpec("object_serializer.mustache", libDir, "object_serializer.ex"),
            new SupportingFileSpec("value_serializer.mustache", libDir, "value_serializer.ex"),
            new SupportingFileSpec("trace_context_util.mustache", libDir, "trace_context_util.ex"),
            new SupportingFileSpec("api_response.mustache", libDir, "api_response.ex"),
            new SupportingFileSpec("api_result.mustache", libDir, "api_result.ex"),
            new SupportingFileSpec("api_client.mustache", libDir, "api_client.ex"),
            new SupportingFileSpec("default_api_client.mustache", libDir, "default_api_client.ex"),
            new SupportingFileSpec("base_api.mustache", Path.of(libDir, "api").toString(), "base_api.ex"),
            new SupportingFileSpec("authenticator.mustache", Path.of(libDir, "auth").toString(), "authenticator.ex"),
            new SupportingFileSpec("main_module.mustache", "lib", pkg + ".ex"),
            new SupportingFileSpec("mix_exs.mustache", "", "mix.exs"),
            new SupportingFileSpec("coveralls_json.mustache", "", "coveralls.json"),
            new SupportingFileSpec("formatter_exs.mustache", "", ".formatter.exs"),
            new SupportingFileSpec("credo_exs.mustache", "", ".credo.exs"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore")
        );
    }

    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        moduleName = getPropertyOrDefault("moduleName", moduleName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put("moduleName", moduleName);
        additionalProperties.put(
                "userAgentDefault", packageName + "/" + packageVersion + " (elixir)");

        final String libDir = Path.of("lib", packageName).toString();

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", libDir, clientClassFile + ".ex"));

        // Spec-independent unit tests exercise infrastructure modules
        // (value serializer, header selector, configuration, transport
        // options, trace-context util, and the pure-unit default API
        // client) that exist in every generated client regardless of the
        // OpenAPI spec. These ship to real clients when unit tests are
        // requested. Everything else below stays golden-only because it
        // is petstore-coupled or depends on the container test harness.
        if (emitUnitTests()) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "test",
                            "value_serializer_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "test",
                            "header_selector_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration_test.mustache",
                            "test",
                            "configuration_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "test",
                            "transport_options_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "test",
                            "trace_context_util_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "test",
                            "default_api_client_unit_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_configuration_test.mustache",
                            "test",
                            "server_configuration_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_variable_test.mustache",
                            "test",
                            "server_variable_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_result_test.mustache", "test", "api_result_test.exs"));
        }

        if (generateTests) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_helper.mustache", "test", "test_helper.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet_api_test.mustache", "test", "pet_api_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store_api_test.mustache",
                            "test",
                            "store_api_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "test",
                            "default_api_client_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "test",
                            "object_serializer_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache", "test", "base_api_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "test",
                            "client_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_error_test.mustache", "test", "api_error_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache", "test", "metadata_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "test",
                            "composed_schema_test.exs"));
            if (hasBasicAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/basic_authenticator_test.mustache",
                                "test",
                                "basic_authenticator_test.exs"));
            }
            supportingFiles.add(
                    new SupportingFile(
                            "test/bearer_authenticator_test.mustache",
                            "test",
                            "bearer_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_key_authenticator_test.mustache",
                            "test",
                            "api_key_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_token_manager_test.mustache",
                            "test",
                            "oauth2_token_manager_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_auth_code_authenticator_test.mustache",
                            "test",
                            "oauth2_auth_code_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_implicit_authenticator_test.mustache",
                            "test",
                            "oauth2_implicit_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_client_credentials_authenticator_test.mustache",
                            "test",
                            "oauth2_client_credentials_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_password_authenticator_test.mustache",
                            "test",
                            "oauth2_password_authenticator_test.exs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/openid_connect_authenticator_test.mustache",
                            "test",
                            "openid_connect_authenticator_test.exs"));
        }
    }

    /**
     * Returns the output directory for model source files.
     * Elixir models go in lib/{package_name}/models/.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, "lib", packageName, "models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * Elixir APIs go in lib/{package_name}/api/.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, "lib", packageName, "api").toString();
    }

    /**
     * Returns nil (null) for default values because Elixir
     * uses pattern matching and default function arguments
     * rather than inline field defaults.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (resolved.getDefault() != null) {
            // An enum field's wire default ("medium") must become its atom
            // form (:"medium") so an absent property yields the same typed
            // value the deserializer produces for a present one — the struct
            // @type declares enum fields as atoms, and ObjectSerializer
            // atomizes the wire value verbatim via String.to_atom/1. Emitting
            // the raw string here would leave the struct default as a String,
            // diverging from the present-property path.
            if (resolved.getEnum() != null && !resolved.getEnum().isEmpty()) {
                return ":\"" + escapeText(String.valueOf(resolved.getDefault())) + "\"";
            } else if (ModelUtils.isStringSchema(resolved)) {
                return "\"" + escapeText(String.valueOf(resolved.getDefault())) + "\"";
            } else if (ModelUtils.isBooleanSchema(resolved)) {
                return resolved.getDefault().toString();
            } else if (ModelUtils.isIntegerSchema(resolved)
                    || ModelUtils.isNumberSchema(resolved)) {
                return resolved.getDefault().toString();
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean sortVarsByDefaultValue() {
        return true;
    }

    /**
     * Enables Gap K (auto-inject discriminator on serialise) for
     * Elixir. The Elixir {@code defstruct} template emits keyword
     * entries from {@code defaultValue}, so subtype structs gain
     * {@code defstruct [:weight_kg, food_type: "dry"]} and the
     * {@code ObjectSerializer} carries the discriminator value
     * automatically.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    protected String getNullLiteral() {
        return "nil";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "lib";
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", NamingConvention.SNAKE_CASE.apply(p.paramName));
            param.put("dataType", p.dataType);
            param.put("typespec", toElixirTypespec(p));
            param.put("required", p.required);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            params.add(param);
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("moduleName", moduleName);
        context.put("packageName", packageName);
        context.put("operationId", op.operationId);
        context.put("params", params);
        // Per-operation auth folded into the Options struct: authed operations
        // gain an optional `auth` field (guarded by {{#hasAuthField}}) that
        // overrides the API instance's configured authenticator for one call.
        injectAuthFieldContext(op, context);
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(
                        getOutputDir(), "lib", packageName, "api", "options", fileName + ".ex")
                .toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of("lib", packageName, "auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getOAuthDir() {
        return Path.of("lib", packageName, "auth", "oauth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return stem + ".ex";
    }

    /** {@inheritDoc} */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("package", moduleName + (spec.isOAuth() ? ".Auth.OAuth" : ".Auth"));
        ctx.put("imports", List.of());
        final List<Map<String, String>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, String> param = new HashMap<>();
            param.put("name", NamingConvention.SNAKE_CASE.apply(name));
            param.put("type", "String.t()");
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", buildElixirSuperArgs(spec));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "keyIn values are ASCII-only OpenAPI location strings (header/query/cookie)"
                    + " — toLowerCase(Locale.ROOT) is intentional and safe here")
    private List<String> buildElixirSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.keyParamName() + "\"", "api_key",
                    ":" + (spec.keyIn() != null ? spec.keyIn().toLowerCase(Locale.ROOT) : "header"));
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "client_id", "client_secret",
                    "\"" + spec.tokenUrl() + "\"", "[]");
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "nil";
            return List.of("host", "client_id", "client_secret",
                    "\"" + spec.tokenUrl() + "\"", refreshArg,
                    "username", "password", "[]");
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "nil";
            return List.of("host", "client_id", "client_secret",
                    "\"" + spec.authorizationUrl() + "\"", "\"" + spec.tokenUrl() + "\"",
                    "redirect_uri", "[]", refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "client_id", "\"" + spec.authorizationUrl() + "\"", "[]");
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.openIdConnectUrl() + "\"",
                    "client_id", "client_secret", "redirect_uri", "[]");
        }
        return List.of();
    }

    /**
     * Registers the {@code elixirTypespec} Mustache lambda. The shared codegen
     * emits a model/operation data type as a bare module ALIAS in typespec
     * position (e.g. {@code Category}, {@code [Tag]}, {@code Order}). A bare
     * alias is not a valid type — Elixir's compiler and Dialyzer flag it as an
     * undefined/unknown type. This lambda rewrites any bare model alias into the
     * fully-qualified struct type {@code PetstoreClient.Models.<Name>.t()},
     * recursing through list wrappers ({@code [Tag]} becomes
     * {@code [PetstoreClient.Models.Tag.t()]}). Stdlib/primitive descriptors
     * (anything already carrying {@code (}, a {@code %{...}} map, {@code nil},
     * or {@code term()}) pass through unchanged so {@code integer()},
     * {@code String.t()}, {@code DateTime.t()}, {@code MapSet.t(...)},
     * {@code %{String.t() => any()}} and the like are left intact. The lambda is
     * the single source of truth for typespec rendering shared by
     * {@code models/model.mustache} and {@code api/api.mustache}.
     */
    @Override
    protected ImmutableMap.Builder<String, Mustache.Lambda> addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put(
                        "elixirTypespec",
                        (fragment, writer) ->
                                writer.write(toModuleTypespec(fragment.execute(), moduleName)));
    }

    /**
     * Rewrites a single Elixir type descriptor into valid typespec form.
     *
     * <p>A descriptor that is already a stdlib/primitive type (it contains a
     * parenthesis like {@code integer()}/{@code String.t()}/{@code MapSet.t(_)},
     * is a {@code %{...}} map, or is {@code nil}/{@code term()}) is returned
     * verbatim. A list wrapper {@code [Inner]} is rewritten by recursing on its
     * inner descriptor. Anything else is a bare model alias and is qualified to
     * {@code <moduleName>.Models.<Alias>.t()}. An empty fragment yields the empty
     * string so {@code {{^returnType}}nil{{/returnType}}} fallbacks are
     * unaffected.
     */
    static String toModuleTypespec(@Nullable String rawType, String moduleName) {
        if (rawType == null) {
            return "";
        }
        final String t = rawType.trim();
        if (t.isEmpty()) {
            return "";
        }
        // List wrapper: [Inner] -> [<transformed Inner>]. This also covers
        // nested arrays such as [[integer()]].
        if (t.startsWith("[") && t.endsWith("]")) {
            return "[" + toModuleTypespec(t.substring(1, t.length() - 1), moduleName) + "]";
        }
        // Already a valid stdlib/primitive descriptor — leave untouched.
        // A parenthesis covers integer()/String.t()/Date.t()/MapSet.t(...);
        // a brace covers %{...} map descriptors; nil/term() are bare but valid.
        if (t.indexOf('(') >= 0 || t.indexOf('{') >= 0 || "nil".equals(t) || "term()".equals(t)) {
            return t;
        }
        // Bare PascalCase model alias — qualify to a struct typespec.
        return moduleName + ".Models." + t + ".t()";
    }

    /**
     * Maps a CodegenParameter to an Elixir typespec string suitable for
     * {@code @type t :: %__MODULE__{...}}. Optional fields are unioned with
     * {@code nil} so Dialyzer and editor tooling pick up the real shape.
     */
    private static String toElixirTypespec(CodegenParameter p) {
        final String base = elixirBaseType(p);
        return p.required ? base : base + " | nil";
    }

    private static String elixirBaseType(CodegenParameter p) {
        if (p.isMap) {
            return "map()";
        }
        if (p.dataType == null || p.dataType.isEmpty()) {
            return "any()";
        }
        final String dt = p.dataType;
        // Elixir array container is rendered as [Inner] by the codegen.
        if (p.isArray && dt.startsWith("[") && dt.endsWith("]")) {
            return "list(" + elementTypespec(dt.substring(1, dt.length() - 1)) + ")";
        }
        if (p.isArray) {
            return "list()";
        }
        // typeMapping already returns Elixir-shaped strings for primitives
        // (String.t(), integer(), boolean(), float(), binary(), MapSet.t(),
        // map(), any()). Anything else is a model name — render as Model.t().
        if (dt.endsWith(")") || "list".equals(dt)) {
            return "list".equals(dt) ? "list()" : dt;
        }
        return dt + ".t()";
    }

    private static String elementTypespec(String inner) {
        final String t = inner.trim();
        if (t.isEmpty()) {
            return "any()";
        }
        if (t.endsWith(")")) {
            return t;
        }
        return t + ".t()";
    }
}

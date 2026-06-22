package io.github.mridang.codegen.generators.dart;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.github.mridang.codegen.generators.BarrelFileEmitter;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Dart API client that uses the http package for
 * transport and dart:convert for JSON serialization. All type
 * names use PascalCase, variable and method names use camelCase
 * per Dart convention. Filenames use snake_case. The formatter
 * pass invokes dart format inside Docker to enforce canonical
 * Dart formatting.
 */
@SuppressWarnings("unused")
public class BetterDartCodegen extends AbstractBetterCodegen implements BarrelFileEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterDartCodegen.class);

    protected String packageName = "petstore_client";
    protected String packageVersion = "1.0.0";

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Dart language. Type mappings convert OpenAPI
     * types to their Dart equivalents (e.g. integer to int,
     * DateTime to DateTime). Reserved words are loaded from a
     * bundled word-list.
     */
    public BetterDartCodegen() {
        outputFolder = Path.of("generated-code", "dart").toString();
        embeddedTemplateDir = templateDir = "templates/dart";

        modelTemplateFiles.put("models/model.mustache", ".dart");
        apiTemplateFiles.put("api/api.mustache", ".dart");

        modelPackage = "models";
        apiPackage = "api";

        typeMapping.put("string", "String");
        typeMapping.put("boolean", "bool");
        typeMapping.put("int", "int");
        typeMapping.put("integer", "int");
        typeMapping.put("long", "int");
        typeMapping.put("short", "int");
        typeMapping.put("float", "double");
        typeMapping.put("double", "double");
        typeMapping.put("number", "double");
        typeMapping.put("decimal", "double");
        typeMapping.put("date", "String");
        typeMapping.put("DateTime", "DateTime");
        // 4.8: format:time → RFC 3339 partial-time (e.g. "14:30:00").
        // Dart has no civil-time type outside Flutter (TimeOfDay), and
        // pulling in Flutter for a single struct would explode the
        // dependency surface for any non-UI consumer. Keep `time` as
        // `String`; the generated object_serializer.dart ships a
        // regex-validated guard (HH:MM:SS(.fff)?) callers may use.
        // 4.8: format:duration → google.protobuf.Duration protobuf-JSON
        // ("<seconds>s", e.g. "3600s", "1.5s"). Dart's stdlib `Duration`
        // does NOT round-trip through that form (its toString() prints
        // "HH:MM:SS.mmmmmm"), so the generated iso8601_duration.dart helper
        // provides parseProtobufDuration / formatProtobufDuration to bridge
        // the wire format and `Duration`. No 3rd-party dep required.
        typeMapping.put("time", "String");
        typeMapping.put("duration", "Duration");
        typeMapping.put("array", "List");
        typeMapping.put("List", "List");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "Map");
        typeMapping.put("object", "Object");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("file", "Uint8List");
        typeMapping.put("binary", "Uint8List");
        typeMapping.put("ByteArray", "Uint8List");
        typeMapping.put("byte", "Uint8List");
        // UUID values are exposed as `UuidValue` from `package:uuid`. The wrapper
        // gives a typed public API (instead of a stringly-typed `String`) and
        // validates on construction (`UuidValue.fromString` throws on malformed
        // input). Serialization round-trips via `toString()` / `fromString()`.
        typeMapping.put("UUID", "UuidValue");
        typeMapping.put("URI", "String");

        // Binary surface (byte/binary/file/ByteArray) maps to `Uint8List` from
        // `dart:typed_data`. `Uint8List` IS-A `List<int>` so existing consumer
        // code that expects `List<int>` continues to compile, but generated
        // method signatures expose the tighter type. The import must be wired
        // here so the `api.mustache` `{{#imports}}` loop emits the SDK import.
        importMapping.put("Uint8List", "dart:typed_data");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "String",
                                "bool",
                                "int",
                                "double",
                                "num",
                                "dynamic",
                                "Object",
                                "void",
                                "Null",
                                "DateTime",
                                "Duration",
                                "List<int>",
                                "Uint8List",
                                "UuidValue"));

        reservedWords = loadReservedWords("/reserved-words/dart.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated Dart package (default: 1.0.0).").defaultValue("1.0.0"));
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "dart-plus";
    }

    /**
     * Escapes a reserved word by appending an underscore suffix
     * instead of prepending one. In Dart, names starting with
     * underscore are library-private, so we avoid the prefix.
     */
    @Override
    public String escapeReservedWord(String name) {
        return name + "_";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Dart client with http and dart:convert.";
    }

    /**
     * Declares Dart as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.DART;
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
        return NamingConvention.CAMEL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "dart:stable@sha256:6440c7d5fd8713b0706d0b6190eb2be7ad896101e225fc9d7657034b23ab0592";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        // Only 'dart format .' runs here. Lint findings inherent to generated
        // code are handled by file-level '// ignore_for_file:' directives in the
        // templates, not by a codegen-time 'dart fix' pass.
        return new String[] {"dart pub get", "dart format ."};
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("int", "double", "num");
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
    protected String getUniqueItemsSetType() {
        return "Set<";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayTypeTemplate() {
        return "List<%2$s>";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "Map<%2$s, %3$s>";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "lib";
    }

    /**
     * Returns {@code String} as the map key type because Dart
     * maps use String keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "String";
    }

    /**
     * Returns {@code Object} as the default map value type
     * for Dart's universal base type.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "Object";
    }

    /**
     * Resolves user-supplied options and registers all supporting
     * files for the Dart package structure. Sets up the source
     * layout with models, api, errors, auth, and supporting
     * infrastructure in the lib/src/ directory.
     */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String srcDir = Path.of("lib", "src").toString();
        final String errorsDir = Path.of(srcDir, "errors").toString();
        final String pkg = Optional.ofNullable((String) additionalProperties.get("packageName"))
                .orElse(packageName);
        return List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("configuration.mustache", srcDir, "configuration.dart"),
            new SupportingFileSpec("transport_options.mustache", srcDir, "transport_options.dart"),
            new SupportingFileSpec("server_configuration.mustache", srcDir, "server_configuration.dart"),
            new SupportingFileSpec("servers.mustache", srcDir, "servers.dart"),
            new SupportingFileSpec("api_error.mustache", srcDir, "api_error.dart"),
            new SupportingFileSpec("errors/zitadel_exception.mustache", errorsDir, "zitadel_exception.dart"),
            new SupportingFileSpec("errors/api_error.mustache", errorsDir, "api_error.dart"),
            new SupportingFileSpec("errors/client_error.mustache", errorsDir, "client_error.dart"),
            new SupportingFileSpec("errors/server_error.mustache", errorsDir, "server_error.dart"),
            new SupportingFileSpec("errors/bad_request_error.mustache", errorsDir, "bad_request_error.dart"),
            new SupportingFileSpec("errors/unauthorized_error.mustache", errorsDir, "unauthorized_error.dart"),
            new SupportingFileSpec("errors/forbidden_error.mustache", errorsDir, "forbidden_error.dart"),
            new SupportingFileSpec("errors/not_found_error.mustache", errorsDir, "not_found_error.dart"),
            new SupportingFileSpec("errors/conflict_error.mustache", errorsDir, "conflict_error.dart"),
            new SupportingFileSpec("errors/unprocessable_entity_error.mustache", errorsDir, "unprocessable_entity_error.dart"),
            new SupportingFileSpec("errors/internal_server_error.mustache", errorsDir, "internal_server_error.dart"),
            new SupportingFileSpec("header_selector.mustache", srcDir, "header_selector.dart"),
            new SupportingFileSpec("object_serializer.mustache", srcDir, "object_serializer.dart"),
            new SupportingFileSpec("iso8601_duration.mustache", srcDir, "iso8601_duration.dart"),
            new SupportingFileSpec("value_serializer.mustache", srcDir, "value_serializer.dart"),
            new SupportingFileSpec("trace_context_util.mustache", srcDir, "trace_context_util.dart"),
            new SupportingFileSpec("api_response.mustache", srcDir, "api_response.dart"),
            new SupportingFileSpec("api_result.mustache", srcDir, "api_result.dart"),
            new SupportingFileSpec("api_client.mustache", srcDir, "api_client.dart"),
            new SupportingFileSpec("abstract_api_client.mustache", srcDir, "abstract_api_client.dart"),
            new SupportingFileSpec("default_api_client.mustache", srcDir, "default_api_client.dart"),
            new SupportingFileSpec("default_api_client_io.mustache", srcDir, "default_api_client_io.dart"),
            new SupportingFileSpec("default_api_client_web.mustache", srcDir, "default_api_client_web.dart"),
            new SupportingFileSpec("base_api.mustache", Path.of(srcDir, "api").toString(), "base_api.dart"),
            new SupportingFileSpec("authenticator.mustache", Path.of(srcDir, "auth").toString(), "authenticator.dart"),
            new SupportingFileSpec("barrel.mustache", "lib", pkg + ".dart"),
            new SupportingFileSpec("pubspec.mustache", "", "pubspec.yaml"),
            new SupportingFileSpec("analysis_options.mustache", "", "analysis_options.yaml"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore")
        );
    }

    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put(
                "userAgentDefault", packageName + "/" + packageVersion + " (dart)");

        final String srcDir = Path.of("lib", "src").toString();

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", srcDir, clientClassFile + ".dart"));

        if (emitUnitTests()) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "test",
                            "default_api_client_unit_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "test",
                            "transport_options_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "test",
                            "header_selector_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "test",
                            "value_serializer_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "test",
                            "trace_context_util_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration_test.mustache",
                            "test",
                            "configuration_test.dart"));
        }
        if (generateTests) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/testcontainers_helper.mustache",
                            "test",
                            "testcontainers_helper.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet_api_test.mustache", "test", "pet_api_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store_api_test.mustache",
                            "test",
                            "store_api_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "test",
                            "default_api_client_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "test",
                            "object_serializer_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache", "test", "base_api_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "test",
                            "client_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_error_test.mustache",
                            "test",
                            "api_error_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache", "test", "metadata_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "test",
                            "composed_schema_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_configuration_test.mustache",
                            "test",
                            "server_configuration_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_variable_test.mustache",
                            "test",
                            "server_variable_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_result_test.mustache",
                            "test",
                            "api_result_test.dart"));
            if (hasBasicAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/basic_authenticator_test.mustache",
                                "test",
                                "basic_authenticator_test.dart"));
            }
            supportingFiles.add(
                    new SupportingFile(
                            "test/bearer_authenticator_test.mustache",
                            "test",
                            "bearer_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_key_authenticator_test.mustache",
                            "test",
                            "api_key_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_token_manager_test.mustache",
                            "test",
                            "oauth2_token_manager_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_auth_code_authenticator_test.mustache",
                            "test",
                            "oauth2_auth_code_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_implicit_authenticator_test.mustache",
                            "test",
                            "oauth2_implicit_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_client_credentials_authenticator_test.mustache",
                            "test",
                            "oauth2_client_credentials_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_password_authenticator_test.mustache",
                            "test",
                            "oauth2_password_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/openid_connect_authenticator_test.mustache",
                            "test",
                            "openid_connect_authenticator_test.dart"));
        }
    }

    /**
     * Returns the output directory for model source files.
     * Dart models go in lib/src/models/.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, "lib", "src", "models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * Dart APIs go in lib/src/api/.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, "lib", "src", "api").toString();
    }

    /**
     * Returns the Dart default value literal for the given schema.
     * Numeric and boolean defaults use their string representation;
     * string defaults are wrapped in single quotes.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (ModelUtils.isIntegerSchema(resolved)
                || ModelUtils.isNumberSchema(resolved)
                || ModelUtils.isBooleanSchema(resolved)) {
            if (resolved.getDefault() != null) {
                return resolved.getDefault().toString();
            }
        } else if (ModelUtils.isStringSchema(resolved)) {
            if (resolved.getDefault() != null) {
                return "'" + escapeText(String.valueOf(resolved.getDefault())) + "'";
            }
        }
        return null;
    }

    /**
     * Keep {@code isEnum} on string-typed properties so the Dart
     * model template can emit typed enums (idiomatic Dart) instead
     * of erasing them to {@code String}. The template uses
     * {@code {{dataType}}.fromJson} for inline enums which expects
     * the typed enum class to exist; we generate it at the
     * top-of-file as a normal enum.
     */
    @Override
    protected boolean clearsEnumOnPrimitives() {
        return false;
    }

    /**
     * Enables Gap K (auto-inject discriminator on serialise) for
     * Dart. The Dart constructor template branches on
     * {@code defaultValue} per field, so subtype classes gain
     * {@code DryFood({this.foodType = 'dry', required this.weightKg})}.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /** Dart string literals use single quotes by convention. */
    @Override
    protected String formatDiscriminatorDefaultValue(String mappingName) {
        return "'" + mappingName + "'";
    }

    /** {@inheritDoc} */
    @Override
    protected boolean filtersOneOfAnyOfPrimitives() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String getModelImportContextKey() {
        return "dartImports";
    }

    /** {@inheritDoc} */
    @Override
    protected boolean filtersOperationImports() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        objs = super.postProcessOperationsWithModels(objs, allModels);

        final List<Map<String, String>> imports =
                (List<Map<String, String>>) objs.get("imports");
        if (imports != null) {
            // Drop synthetic SDK imports (e.g. `dart:typed_data` for `Uint8List`) — the
            // template emits these statically at the top of the file, not via `{{#imports}}`.
            imports.removeIf(
                    imp -> {
                        final String value = imp.get("import");
                        return value != null && value.startsWith("dart:");
                    });
            for (final Map<String, String> imp : imports) {
                if (!imp.containsKey("className") && imp.containsKey("import")) {
                    String className = imp.get("import");
                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf('.') + 1);
                    }
                    imp.put("className", className);
                    imp.put("filename", toModelFilename(className));
                }
            }
        }
        return objs;
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of("lib", "src", "auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return stem + ".dart";
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("StringConcatenationMissingWhitespace")
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only scheme values")
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final String scopes = formatDartScopes(spec.scopes());
        final List<String> imports;
        final String constructorSig;
        final String superCall;

        if ("BasicAuthenticator".equals(spec.baseClass())) {
            imports = List.of("basic_authenticator.dart");
            constructorSig =
                    "required String host, required String username, required String password";
            superCall = "host: host, username: username, password: password";
        } else if ("BearerAuthenticator".equals(spec.baseClass())) {
            imports = List.of("bearer_authenticator.dart");
            constructorSig = "required String host, required String token";
            superCall = "host: host, token: token";
        } else if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            final String loc = NamingConvention.CAMEL_CASE.apply(
                    spec.keyIn() != null
                            ? spec.keyIn().toLowerCase(Locale.ROOT)
                            : "header");
            imports = List.of("api_key_authenticator.dart", "api_key_location.dart");
            constructorSig = "required String host, required String apiKey";
            superCall =
                    "host: host, keyParamName: '"
                            + spec.keyParamName()
                            + "', apiKey: apiKey, location: ApiKeyLocation."
                            + loc;
        } else if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            imports = List.of("oauth2_client_credentials_authenticator.dart");
            constructorSig =
                    "required String host, required String clientId, required String clientSecret";
            superCall =
                    "host: host, clientId: clientId, clientSecret: clientSecret, tokenUrl: '"
                            + spec.tokenUrl()
                            + "', scopes: "
                            + scopes;
        } else if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg =
                    spec.refreshUrl() != null ? "'" + spec.refreshUrl() + "'" : "null";
            imports = List.of("oauth2_password_authenticator.dart");
            constructorSig =
                    "required String host, required String clientId, "
                            + "required String clientSecret, required String username, "
                            + "required String password";
            superCall =
                    "host: host, clientId: clientId, clientSecret: clientSecret, tokenUrl: '"
                            + spec.tokenUrl()
                            + "', refreshUrl: "
                            + refreshArg
                            + ", username: username, password: password, scopes: "
                            + scopes;
        } else if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg =
                    spec.refreshUrl() != null ? "'" + spec.refreshUrl() + "'" : "null";
            imports = List.of("oauth2_auth_code_authenticator.dart");
            constructorSig =
                    "required String host, required String clientId, "
                            + "required String clientSecret, required String redirectUri";
            superCall =
                    "host: host, clientId: clientId, clientSecret: clientSecret, "
                            + "authorizationUrl: '"
                            + spec.authorizationUrl()
                            + "', tokenUrl: '"
                            + spec.tokenUrl()
                            + "', redirectUri: redirectUri, scopes: "
                            + scopes
                            + ", refreshUrl: "
                            + refreshArg;
        } else if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            imports = List.of("oauth2_implicit_authenticator.dart");
            constructorSig = "required String host, required String clientId";
            superCall =
                    "host: host, clientId: clientId, authorizationUrl: '"
                            + spec.authorizationUrl()
                            + "', scopes: "
                            + scopes;
        } else if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            imports = List.of("openid_connect_authenticator.dart");
            constructorSig =
                    "required String host, required String clientId, "
                            + "required String clientSecret, required String redirectUri";
            superCall =
                    "host: host, openIdConnectUrl: '"
                            + spec.openIdConnectUrl()
                            + "', clientId: clientId, clientSecret: clientSecret, "
                            + "redirectUri: redirectUri, scopes: []";
        } else {
            LOGGER.warn("Unsupported scheme base class: {}", spec.baseClass());
            return "";
        }

        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("imports", imports);
        ctx.put("constructorSignature", constructorSig);
        ctx.put("superCall", superCall);
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    private static String formatDartScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "['" + String.join("', '", scopes.keySet()) + "']";
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", p.paramName);
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            params.add(param);
        }

        final List<Map<String, String>> optionsImports = new ArrayList<>();
        // Dedupe by type name so two params of the same type (e.g. two Swatch
        // enum params) import the model file once, not twice (Dart treats a
        // duplicate import as an analyzer warning/error).
        final Set<String> importedTypes = new HashSet<>();
        for (final CodegenParameter p : optionsParams) {
            if (p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)
                    && !typeMapping.containsValue(p.baseType)
                    && importedTypes.add(p.baseType)) {
                final Map<String, String> imp = new HashMap<>();
                imp.put("classname", p.baseType);
                imp.put("filename", toModelFilename(p.baseType));
                optionsImports.add(imp);
            }
            // A $ref to a top-level enum carries its enum type name in dataType
            // (baseType is null); the generated enum lives under models/ and
            // must be imported.
            if (p.isEnumRef
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)
                    && !typeMapping.containsValue(p.dataType)
                    && importedTypes.add(p.dataType)) {
                final Map<String, String> imp = new HashMap<>();
                imp.put("classname", p.dataType);
                imp.put("filename", toModelFilename(p.dataType));
                optionsImports.add(imp);
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("packageName", packageName);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("dartImports", optionsImports);
        context.put("hasDartImports", !optionsImports.isEmpty());
        injectAuthFieldContext(op, context);
        // Relative import from lib/src/api/options/ up to the generic
        // Authenticator at lib/src/auth/authenticator.dart, mirroring how
        // model imports are formed (../../models/<file>.dart).
        context.put(
                "authImport",
                "../../auth/"
                        + NamingConvention.SNAKE_CASE.apply(getAuthenticatorTypeName())
                        + ".dart");
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(
                        getOutputDir(), "lib", "src", "api", "options", fileName + ".dart")
                .toString();
    }

    /** {@inheritDoc} */
    @Override
    public void emitBarrelFiles(List<Map<String, String>> optionsFiles) {
        if (optionsFiles.isEmpty()) {
            return;
        }
        final List<Map<String, String>> exports = new ArrayList<>();
        for (final Map<String, String> meta : optionsFiles) {
            final String className = Objects.requireNonNull(meta.get("optionsClassName"));
            final Map<String, String> e = new HashMap<>();
            e.put("fileName", NamingConvention.SNAKE_CASE.apply(className));
            exports.add(e);
        }
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put("exports", exports);
        final String content = renderOptionsTemplate("api/options_barrel.mustache", ctx);
        final Path barrelPath = Path.of(getOutputDir(), "lib", packageName + ".dart");
        try {
            Files.writeString(
                    barrelPath,
                    Files.readString(barrelPath, StandardCharsets.UTF_8) + content,
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.warn("Failed to append options exports to barrel: {}", ex.getMessage());
        }
    }
}

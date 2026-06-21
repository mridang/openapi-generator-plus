package io.github.mridang.codegen.generators.rust;

import com.samskivert.mustache.Mustache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.github.mridang.codegen.generators.BarrelFileEmitter;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.io.IOException;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenDiscriminator;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Rust API client that uses reqwest for transport
 * and serde for JSON serialization. All type names use PascalCase,
 * function and variable names use snake_case per Rust convention.
 * Filenames are snake_case. The formatter pass invokes cargo fmt
 * inside Docker to enforce canonical Rust formatting.
 */
@SuppressWarnings("unused")
public class BetterRustCodegen extends AbstractBetterCodegen implements BarrelFileEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRustCodegen.class);

    protected String packageName = "openapi_client";
    protected String packageVersion = "1.0.0";

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Rust language. Type mappings convert OpenAPI
     * types to their Rust equivalents (e.g. integer to i32,
     * string to String). Reserved words are loaded from a
     * bundled word-list to avoid generating identifiers that
     * clash with Rust keywords.
     */
    public BetterRustCodegen() {
        outputFolder = Path.of("generated-code", "rust").toString();
        embeddedTemplateDir = templateDir = "templates/rust";

        modelTemplateFiles.put("models/model.mustache", ".rs");
        apiTemplateFiles.put("api/api.mustache", ".rs");

        modelPackage = "models";
        apiPackage = "api";

        typeMapping.put("string", "String");
        typeMapping.put("boolean", "bool");
        typeMapping.put("int", "i32");
        typeMapping.put("integer", "i32");
        typeMapping.put("long", "i64");
        typeMapping.put("short", "i16");
        typeMapping.put("float", "f32");
        typeMapping.put("double", "f64");
        typeMapping.put("number", "f64");
        typeMapping.put("decimal", "f64");
        // Use chrono for proper date / date-time semantics in
        // generated structs. The previous String mapping forced
        // callers to parse manually and lost serde validation on
        // wire payloads. chrono::DateTime<Utc> serialises as an
        // RFC-3339 string by default (compatible with OAS).
        typeMapping.put("date", "chrono::NaiveDate");
        typeMapping.put("DateTime", "chrono::DateTime<chrono::Utc>");
        // Gap 4.8: `format: time` maps to `chrono::NaiveTime`, which serde-
        // serialises as an RFC-3339 partial-time string ("HH:MM:SS[.fff]")
        // out of the box (via the `serde` feature already enabled on
        // chrono). `format: duration` maps to `chrono::Duration`; chrono's
        // built-in serde impl writes Durations as integer milliseconds —
        // not the protobuf-JSON duration shape — so the generated model
        // template applies `#[serde(with = "crate::proto_duration")]` to
        // fields whose {{isDuration}} is truthy, routing them through the
        // hand-rolled helper in src/proto_duration.rs that emits the
        // google.protobuf.Duration form ("<seconds>s", e.g. "3600s")
        // Zitadel's API requires.
        typeMapping.put("time", "chrono::NaiveTime");
        typeMapping.put("duration", "chrono::Duration");
        typeMapping.put("array", "Vec");
        typeMapping.put("List", "Vec");
        typeMapping.put("set", "std::collections::HashSet");
        typeMapping.put("map", "std::collections::HashMap");
        // Free-form ("any") JSON values map to the SDK-owned `JsonValue`
        // wrapper rather than `serde_json::Value`, so no serde_json type leaks
        // into a public model field or signature.
        typeMapping.put("object", "crate::json_value::JsonValue");
        typeMapping.put("AnyType", "crate::json_value::JsonValue");
        typeMapping.put("file", "Vec<u8>");
        typeMapping.put("binary", "Vec<u8>");
        typeMapping.put("ByteArray", "Vec<u8>");
        // `format: uuid` maps to the `uuid` crate's strongly-typed `Uuid`
        // type rather than a raw `String`. The `serde` feature on the
        // `uuid` crate (declared in cargo_toml.mustache) provides
        // transparent (de)serialization to/from the RFC-4122 string form,
        // so derived `Serialize`/`Deserialize` impls "just work".
        typeMapping.put("UUID", "uuid::Uuid");
        typeMapping.put("URI", "String");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "String",
                                "bool",
                                "i8",
                                "i16",
                                "i32",
                                "i64",
                                "u8",
                                "u16",
                                "u32",
                                "u64",
                                "f32",
                                "f64",
                                "char",
                                "Vec",
                                "HashMap",
                                "uuid::Uuid"));

        reservedWords = loadReservedWords("/reserved-words/rust.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated Rust crate (default: 1.0.0).").defaultValue("1.0.0"));
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "rust-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Rust client with reqwest and serde.";
    }

    /**
     * Declares Rust as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.RUST;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "tests/fixtures";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "tests/spec";
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
        return NamingConvention.PASCAL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "rust:1.96@sha256:fb328f0f58becb23ba1719940a2c94ece8b0b48afa837d05b79ef64bc1e18f6e";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {"rustup component add rustfmt", "cargo fmt"};
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("i32", "i64", "f32", "f64");
    }

    /**
     * Rust value types have a fixed, statically-known size, so a struct that
     * directly contains a field of its own type — e.g. a tree node whose
     * optional {@code child} is another node — would have infinite size and
     * fail to compile (E0072). Box such a recursive member to put it behind a
     * heap pointer. Only DIRECT self-references are boxed: a {@code Vec} or
     * {@code HashMap} of the type is already heap-indirected and needs no Box,
     * and unrelated model fields must not be boxed or the public API would
     * change needlessly. {@code Box<T>} is transparent to serde, so
     * (de)serialization is unaffected.
     */
    @Override
    public void postProcessModelProperty(
            org.openapitools.codegen.CodegenModel model,
            org.openapitools.codegen.CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        boolean directSelfReference =
                !property.isContainer
                        && property.complexType != null
                        && property.complexType.equals(model.classname);
        if (directSelfReference && !property.dataType.startsWith("Box<")) {
            property.dataType = "Box<" + property.dataType + ">";
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "std::collections::HashSet<";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayContainerPattern() {
        return "^Vec<";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayTypeTemplate() {
        return "Vec<%2$s>";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "std::collections::HashMap<%2$s, %3$s>";
    }

    /**
     * Returns {@code String} as the map key type because Rust
     * HashMaps use String keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "String";
    }

    /**
     * Returns the SDK-owned {@code JsonValue} wrapper as the default map value
     * type for free-form JSON, so {@code serde_json} never leaks into a public
     * map-valued model field.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "crate::json_value::JsonValue";
    }

    /**
     * Resolves user-supplied options and registers all
     * supporting files for the Rust crate structure.
     * Sets up the source layout including models, API
     * classes, errors, auth, configuration, and serializers
     * in the src/ directory.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put("userAgentDefault", packageName + "/" + packageVersion + " (rust)");

        additionalProperties.put(
                "rustVariantName",
                (Mustache.Lambda)
                        (fragment, writer) -> {
                            String type = fragment.execute().trim();
                            StringBuilder name = new StringBuilder();
                            boolean capitalize = true;
                            for (int i = 0; i < type.length(); i++) {
                                char c = type.charAt(i);
                                if (Character.isLetterOrDigit(c)) {
                                    name.append(capitalize
                                            ? Character.toUpperCase(c) : c);
                                    capitalize = false;
                                } else {
                                    capitalize = true;
                                }
                            }
                            writer.write(name.toString());
                        });

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", "src", clientClassFile + ".rs"));

        if (emitUnitTests()) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "tests",
                            "default_api_client_unit_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "tests",
                            "transport_options_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "tests",
                            "header_selector_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "tests",
                            "value_serializer_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "tests",
                            "trace_context_util_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration_test.mustache",
                            "tests",
                            "configuration_test.rs"));
        }

        if (generateTests) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/testcontainers_helper.mustache",
                            "tests",
                            "testcontainers_helper.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet_api_test.mustache", "tests", "pet_api_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store_api_test.mustache", "tests", "store_api_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "tests",
                            "default_api_client_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "tests",
                            "object_serializer_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache", "tests", "base_api_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache", "tests", "metadata_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "tests",
                            "composed_schema_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "tests",
                            "client_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_error_test.mustache",
                            "tests",
                            "api_error_test.rs"));
            if (hasBasicAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/basic_authenticator_test.mustache",
                                "tests",
                                "basic_authenticator_test.rs"));
            }
            if (hasBearerAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/bearer_authenticator_test.mustache",
                                "tests",
                                "bearer_authenticator_test.rs"));
            }
            if (hasApiKeyAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/api_key_authenticator_test.mustache",
                                "tests",
                                "api_key_authenticator_test.rs"));
            }
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_configuration_test.mustache",
                            "tests",
                            "server_configuration_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_variable_test.mustache",
                            "tests",
                            "server_variable_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_result_test.mustache",
                            "tests",
                            "api_result_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_token_manager_test.mustache",
                            "tests",
                            "oauth2_token_manager_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_auth_code_authenticator_test.mustache",
                            "tests",
                            "oauth2_auth_code_authenticator_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_implicit_authenticator_test.mustache",
                            "tests",
                            "oauth2_implicit_authenticator_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_client_credentials_authenticator_test.mustache",
                            "tests",
                            "oauth2_client_credentials_authenticator_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_password_authenticator_test.mustache",
                            "tests",
                            "oauth2_password_authenticator_test.rs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/openid_connect_authenticator_test.mustache",
                            "tests",
                            "openid_connect_authenticator_test.rs"));
        }
    }

    /** {@inheritDoc} */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String authDir = Path.of("src", "auth").toString();
        final String nextestDir = Path.of(".config", "nextest").toString();
        return List.of(
                new SupportingFileSpec("readme.mustache", "", "README.md"),
                new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
                new SupportingFileSpec("configuration.mustache", "src", "configuration.rs"),
                new SupportingFileSpec(
                        "transport_options.mustache", "src", "transport_options.rs"),
                new SupportingFileSpec(
                        "server_configuration.mustache", "src", "server_configuration.rs"),
                new SupportingFileSpec("servers.mustache", "src", "servers.rs"),
                new SupportingFileSpec("api_error.mustache", "src", "api_error.rs"),
                new SupportingFileSpec(
                        "errors/zitadel_error.mustache", "src/errors", "zitadel_error.rs"),
                new SupportingFileSpec(
                        "errors/client_error.mustache", "src/errors", "client_error.rs"),
                new SupportingFileSpec(
                        "errors/server_error.mustache", "src/errors", "server_error.rs"),
                new SupportingFileSpec(
                        "errors/bad_request_error.mustache",
                        "src/errors",
                        "bad_request_error.rs"),
                new SupportingFileSpec(
                        "errors/unauthorized_error.mustache",
                        "src/errors",
                        "unauthorized_error.rs"),
                new SupportingFileSpec(
                        "errors/forbidden_error.mustache", "src/errors", "forbidden_error.rs"),
                new SupportingFileSpec(
                        "errors/not_found_error.mustache", "src/errors", "not_found_error.rs"),
                new SupportingFileSpec(
                        "errors/conflict_error.mustache", "src/errors", "conflict_error.rs"),
                new SupportingFileSpec(
                        "errors/unprocessable_entity_error.mustache",
                        "src/errors",
                        "unprocessable_entity_error.rs"),
                new SupportingFileSpec(
                        "errors/internal_server_error.mustache",
                        "src/errors",
                        "internal_server_error.rs"),
                new SupportingFileSpec("errors/mod.mustache", "src/errors", "mod.rs"),
                new SupportingFileSpec(
                        "models/base64_serde.mustache", "src/models", "base64_serde.rs"),
                new SupportingFileSpec(
                        "proto_duration.mustache", "src", "proto_duration.rs"),
                new SupportingFileSpec("json_value.mustache", "src", "json_value.rs"),
                new SupportingFileSpec("header_selector.mustache", "src", "header_selector.rs"),
                new SupportingFileSpec(
                        "object_serializer.mustache", "src", "object_serializer.rs"),
                new SupportingFileSpec("value_serializer.mustache", "src", "value_serializer.rs"),
                new SupportingFileSpec("utils/mod.mustache", "src/utils", "mod.rs"),
                new SupportingFileSpec(
                        "utils/form_url_encode.mustache",
                        "src/utils",
                        "form_url_encode.rs"),
                new SupportingFileSpec(
                        "trace_context_util.mustache", "src", "trace_context_util.rs"),
                new SupportingFileSpec("api_response.mustache", "src", "api_response.rs"),
                new SupportingFileSpec("api_result.mustache", "src", "api_result.rs"),
                new SupportingFileSpec("api_client.mustache", "src", "api_client.rs"),
                new SupportingFileSpec(
                        "default_api_client.mustache", "src", "default_api_client.rs"),
                new SupportingFileSpec("base_api.mustache", "src/api", "base_api.rs"),
                new SupportingFileSpec("authenticator.mustache", authDir, "authenticator.rs"),
                new SupportingFileSpec("lib.mustache", "src", "lib.rs"),
                new SupportingFileSpec("cargo_toml.mustache", "", "Cargo.toml"),
                new SupportingFileSpec("rustfmt_toml.mustache", "", "rustfmt.toml"),
                new SupportingFileSpec("clippy_toml.mustache", "", "clippy.toml"),
                new SupportingFileSpec("deny_toml.mustache", "", "deny.toml"),
                new SupportingFileSpec("nextest_toml.mustache", nextestDir, "default.toml"),
                new SupportingFileSpec("makefile.mustache", "", "Makefile"),
                new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
                new SupportingFileSpec("gitignore.mustache", "", ".gitignore"));
    }

    /**
     * Returns the output directory for model source files.
     * Rust models go in the {@code src/models/} subdirectory.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, "src", "models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * Rust APIs go in the {@code src/api/} subdirectory.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, "src", "api").toString();
    }

    /**
     * Returns null for default values because Rust does not use
     * inline default values in struct field declarations. The
     * Default trait implementation handles defaults instead.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (resolved.getDefault() != null) {
            if (ModelUtils.isStringSchema(resolved)) {
                return "String::from(\"" + escapeText(String.valueOf(resolved.getDefault())) + "\")";
            } else if (ModelUtils.isBooleanSchema(resolved)) {
                return resolved.getDefault().toString();
            } else if (ModelUtils.isIntegerSchema(resolved)
                    || ModelUtils.isNumberSchema(resolved)) {
                return resolved.getDefault().toString();
            }
        }
        return null;
    }

    /**
     * Rewrites an optional enum field's {@code defaultValue} to the
     * fully-qualified DECLARED variant (e.g. {@code DefaultsModeEnum::Medium})
     * rather than the wire string. The schema {@code default} can be any
     * variant — not necessarily the first — so the model template must not
     * fall back to {@code <Enum>::default()} (which always resolves to the
     * {@code #[default]} first variant). Emitting the declared variant here
     * lets both the {@code default_<name>()} serde hook and the {@code new()}
     * constructor seed the correct value.
     */
    @Override
    protected void fixEnumDefaultValue(
            org.openapitools.codegen.CodegenProperty prop,
            org.openapitools.codegen.CodegenModel model) {
        // toDefaultValue wraps a string-enum's wire default as
        // String::from("medium") (a string schema). Unwrap that carrier and
        // re-emit the declared variant (e.g. DefaultsModeEnum::Medium) so the
        // value lands in the enum-typed field/hook rather than producing an
        // Option<String> (E0308). Idempotent: after the rewrite the value no
        // longer matches the String::from(...) form, so the four call sites
        // (vars/allVars/optionalVars/requiredVars) converge.
        final String prefix = "String::from(\"";
        final String suffix = "\")";
        if (prop.defaultValue != null
                && prop.isEnum
                && prop.defaultValue.startsWith(prefix)
                && prop.defaultValue.endsWith(suffix)) {
            final String rawValue = prop.defaultValue.substring(
                    prefix.length(), prop.defaultValue.length() - suffix.length());
            final String variant = toEnumVarName(rawValue.toLowerCase(Locale.ROOT), prop.dataType);
            prop.defaultValue = model.classname + prop.enumName + "::" + variant;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String formatEnumStringLiteral(String value) {
        return "String::from(\"" + value + "\")";
    }

    /** {@inheritDoc} */
    @Override
    protected String getNullLiteral() {
        return "None";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "src";
    }

    /** {@inheritDoc} */
    @Override
    protected boolean emitsBaseAuthenticator() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return "src/auth";
    }

    /** {@inheritDoc} */
    @Override
    protected String getOAuthDir() {
        return "src/auth/oauth";
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return stem + ".rs";
    }

    /** {@inheritDoc} */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();
        // Rust needs explicit mod.rs files for each auth directory.
        supportingFiles.add(new SupportingFile("auth/mod.mustache", getAuthDir(), "mod.rs"));
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile("auth/oauth/mod.mustache", getOAuthDir(), "mod.rs"));
        }
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("StringConcatenationMissingWhitespace")
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only scheme values")
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final List<Map<String, String>> constructorParams;
        final List<String> superArgs;

        if ("BasicAuthenticator".equals(spec.baseClass())) {
            constructorParams = List.of(p("host", "&str"), p("username", "&str"), p("password", "&str"));
            superArgs = List.of("host", "username", "password");
        } else if ("BearerAuthenticator".equals(spec.baseClass())) {
            constructorParams = List.of(p("host", "&str"), p("token", "&str"));
            superArgs = List.of("host", "token");
        } else if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            final String loc = "ApiKeyLocation::" + NamingConvention.PASCAL_CASE.apply(
                    spec.keyIn() != null
                            ? spec.keyIn().toLowerCase(Locale.ROOT)
                            : "header");
            constructorParams = List.of(p("host", "&str"), p("api_key", "&str"));
            superArgs = List.of("host", "\"" + spec.keyParamName() + "\"", "api_key", loc);
        } else if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            constructorParams =
                    List.of(p("host", "&str"), p("client_id", "&str"), p("client_secret", "&str"));
            superArgs =
                    List.of("host", "client_id", "client_secret",
                            "\"" + spec.tokenUrl() + "\"", "vec![]");
        } else if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            // refresh_url is &str — empty string means "fall back to token_url"
            final String refreshArg =
                    spec.refreshUrl() != null ? "\"" + spec.refreshUrl() + "\"" : "\"\"";
            constructorParams =
                    List.of(p("host", "&str"), p("client_id", "&str"), p("client_secret", "&str"),
                            p("username", "&str"), p("password", "&str"));
            // Signature: (host, client_id, client_secret, token_url, username, password, scopes, refresh_url)
            superArgs =
                    List.of("host", "client_id", "client_secret",
                            "\"" + spec.tokenUrl() + "\"",
                            "username", "password", "vec![]", refreshArg);
        } else if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            // refresh_url is &str — empty string means "fall back to token_url"
            final String refreshArg =
                    spec.refreshUrl() != null ? "\"" + spec.refreshUrl() + "\"" : "\"\"";
            constructorParams =
                    List.of(p("host", "&str"), p("client_id", "&str"), p("client_secret", "&str"),
                            p("redirect_uri", "&str"));
            // Signature: (host, client_id, client_secret, authorization_url, token_url, redirect_uri, scopes, refresh_url)
            superArgs =
                    List.of("host", "client_id", "client_secret",
                            "\"" + spec.authorizationUrl() + "\"", "\"" + spec.tokenUrl() + "\"",
                            "redirect_uri", "vec![]", refreshArg);
        } else if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            constructorParams = List.of(p("host", "&str"), p("client_id", "&str"));
            superArgs =
                    List.of("host", "client_id", "\"" + spec.authorizationUrl() + "\"", "vec![]");
        } else if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            constructorParams =
                    List.of(p("host", "&str"), p("client_id", "&str"), p("client_secret", "&str"),
                            p("redirect_uri", "&str"));
            superArgs =
                    List.of("host", "\"" + spec.openIdConnectUrl() + "\"",
                            "client_id", "client_secret", "redirect_uri", "vec![]");
        } else {
            LOGGER.warn("Unsupported scheme base class: {}", spec.baseClass());
            return "";
        }

        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", superArgs);
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    private static Map<String, String> p(String name, String type) {
        final Map<String, String> param = new HashMap<>();
        param.put("name", name);
        param.put("type", type);
        return param;
    }

    /*
     * Keeps the discriminator property on variant structs so
     * direct construction works (`DryFood::new(...)` auto-emits
     * `food_type: "dry"`). The parent enum uses
     * `#[serde(untagged)]` rather than `#[serde(tag = "...")]`,
     * so there is no longer a duplicate-tag-field conflict — the
     * child struct's serde-rename'd field IS the discriminator on
     * the wire. Matches the pattern used by 10 other languages.
     */

    /** {@inheritDoc} */
    @Override
    protected boolean removesDiscriminatorPropertyFromChildren() {
        return false;
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
            param.put("required", p.required);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            params.add(param);
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("packageName", packageName);
        context.put("operationId", op.operationId);
        context.put("params", params);
        // Folds the optional per-operation authenticator into the Options
        // struct (mirrors the Java generator). injectAuthFieldContext sets
        // hasAuthField (= op.hasAuthMethods) and authFieldType. Rust holds the
        // authenticator as a reference-counted trait object so it can be borrowed
        // as `&dyn Authenticator` when threaded into the transport call, matching
        // the constructor's `Option<Arc<dyn Authenticator>>` ownership model.
        injectAuthFieldContext(op, context);
        context.put("authFieldType", "Arc<dyn Authenticator>");
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(getOutputDir(), "src", "api", "options", fileName + ".rs").toString();
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
            e.put("modName", NamingConvention.SNAKE_CASE.apply(className));
            exports.add(e);
        }
        final Map<String, Object> ctx = new HashMap<>();
        ctx.put("exports", exports);
        final String content = renderOptionsTemplate("api/options_mod.mustache", ctx);
        final String modPath =
                Path.of(getOutputDir(), "src", "api", "options", "mod.rs").toString();
        writeFile(modPath, content);
    }

    /**
     * Scans the models and API directories after generation to
     * write Rust module declaration files ({@code mod.rs}), then
     * delegates to the parent for Options barrel files and Docker
     * formatting.
     */
    @Override
    public void postProcess() {
        writeModFile(Path.of(getOutputDir(), "src", "models"));
        writeModFile(Path.of(getOutputDir(), "src", "api"));
        super.postProcess();
    }

    /**
     * Scans a directory for {@code .rs} files (excluding
     * {@code mod.rs}) and writes a {@code mod.rs} that declares
     * and re-exports all discovered modules. For the API
     * directory, also includes the options module.
     */
    private void writeModFile(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            final List<Map<String, String>> modules =
                    entries.filter(p -> p.toString().endsWith(".rs"))
                            .map(Path::getFileName)
                            .filter(Objects::nonNull)
                            .map(Path::toString)
                            .filter(name -> !name.equals("mod.rs"))
                            .map(name -> name.replace(".rs", ""))
                            .sorted()
                            .map(name -> Map.of("name", name))
                            .collect(Collectors.toList());

            final Map<String, Object> ctx = new HashMap<>();
            ctx.put("modules", modules);
            ctx.put("hasOptions",
                    dir.endsWith("api") && Files.isDirectory(dir.resolve("options")));

            writeFile(dir.resolve("mod.rs").toString(),
                    renderOptionsTemplate("module_index.mustache", ctx));
        } catch (IOException e) {
            LOGGER.warn("Failed to write mod.rs in {}: {}", dir, e.getMessage());
        }
    }

    /**
     * Enables Gap K so polymorphic subtypes auto-emit their
     * discriminator field on serialization. The Rust model template
     * only honours {@code defaultValue} on {@code optionalVars}, so
     * the discriminator property is demoted from required to
     * optional with a default; serde then writes the field as
     * {@code Some("foodType")} unconditionally from the constructor.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /**
     * Required because the Rust struct/constructor templates only
     * apply {@code defaultValue} inside the {@code optionalVars}
     * iteration. Without demotion the discriminator would remain a
     * required constructor parameter the caller had to pass.
     */
    @Override
    protected boolean demotesDiscriminatorFromRequiredVars() {
        return true;
    }
}

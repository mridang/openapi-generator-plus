package io.github.mridang.codegen.generators.node;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.github.mridang.codegen.generators.BarrelFileEmitter;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a TypeScript API client that uses the Fetch API for
 * HTTP transport. All identifiers follow camelCase conventions
 * for variables and parameters, PascalCase for model names, and
 * kebab-case for filenames. Output is formatted with Prettier
 * inside Docker to ensure consistent style across all generated
 * source files.
 */
@SuppressWarnings("unused")
public class BetterNodeCodegen extends AbstractBetterCodegen implements BarrelFileEmitter {

    @Override
    protected String errorSuffix() {
        return "Error";
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterNodeCodegen.class);

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaTemplate() {
        return "errors/schema-error.mustache";
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaFilePath(String className) {
        return Path.of(outputFolder, "src", "errors", NamingConvention.KEBAB_CASE.apply(className) + ".ts")
                .toString();
    }

    /**
     * Marks each inherited property of a discriminator child that the base
     * already declares and that has no initializer. TypeScript rejects such a
     * redeclaration (TS2612), and a readonly base field cannot be assigned in
     * the child, so the model template leaves those fields to the base. An
     * inherited property with a default keeps its declaration, because the
     * child's own constructor assigns the default to it.
     */
    @Override
    protected void decorateClassicDiscriminatorChild(final CodegenModel child, final CodegenModel base) {
        final Set<String> baseProps = new HashSet<>();
        for (final CodegenProperty p : base.vars) {
            baseProps.add(p.baseName);
        }
        for (final CodegenProperty p : child.vars) {
            final boolean isDiscriminator = base.discriminator != null
                    && Objects.equals(base.discriminator.getPropertyBaseName(), p.baseName);
            final boolean hasInitializer = p.defaultValue != null || isDiscriminator
                    || Boolean.TRUE.equals(p.vendorExtensions.get("siblingDefaultIsEnum"));
            if (baseProps.contains(p.baseName) && !hasInitializer) {
                p.vendorExtensions.put("x-skip-redeclare", true);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void decorateErrorSchema(Map<String, Object> schema) {
        schema.put(
                "classFileName",
                NamingConvention.KEBAB_CASE.apply(Objects.requireNonNull((String) schema.get("className"))));
        schema.put(
                "parentFileName",
                NamingConvention.KEBAB_CASE.apply(Objects.requireNonNull((String) schema.get("parentClassName"))));
    }

    /**
     * Initializes all TypeScript-specific type mappings, language
     * primitives, template paths, and reserved words. Maps OpenAPI
     * types to their TypeScript equivalents (e.g. integer to
     * number, DateTime to string) and configures the model
     * property naming to preserve original casing.
     */
    public BetterNodeCodegen() {
        outputFolder = "generated-code/typescript";
        embeddedTemplateDir = templateDir = "templates/node";

        modelTemplateFiles.put("models/model.mustache", ".ts");
        apiTemplateFiles.put("api/apis.mustache", ".ts");

        typeMapping.put("integer", "number");
        typeMapping.put("long", "number");
        typeMapping.put("float", "number");
        typeMapping.put("double", "number");
        // `number` (no format) and `decimal` map to decimal.js's `Decimal`
        // at the property level (set in postProcessModelProperty) and at the
        // parameter level (fromParameter).
        // We intentionally leave the typeMapping for `number` as `number`
        // here because the double-lookup in getSchemaType would otherwise
        // turn `long`/`integer` (which map to `number`) into `Decimal`.
        typeMapping.put("number", "number");
        typeMapping.put("short", "number");
        typeMapping.put("boolean", "boolean");
        typeMapping.put("string", "string");
        typeMapping.put("decimal", "Decimal");
        typeMapping.put("Decimal", "Decimal");
        // Map OpenAPI date-time formats to the JS Date type so
        // class-transformer's @Type(() => Date) decorator can
        // hydrate ISO-8601 strings into actual Date instances.
        // A `format: date` is a plain YYYY-MM-DD string: JavaScript has no
        // date-only type, and a Date would carry a time and a zone the wire
        // value never had.
        // round-trip (the previous `string` mapping forced callers
        // to parse manually and produced wire/runtime drift).
        typeMapping.put("date", "string");
        typeMapping.put("DateTime", "Date");
        typeMapping.put("binary", "Buffer");
        typeMapping.put("File", "Buffer");
        typeMapping.put("file", "Buffer");
        // 2.1: `format: byte` (ByteArray) is base64-encoded binary on the
        // wire. Exposing it as `string` shifts the encode/decode burden to
        // the caller and discards type information. Map to `Buffer`; the
        // ObjectSerializer round-trips Buffer <-> base64 string at the
        // serde boundary so the model field stays typed as binary.
        typeMapping.put("ByteArray", "Buffer");
        // 2.2: `format: uuid` is a constrained string. Using a branded
        // alias gives compile-time differentiation from a free-form
        // string without runtime overhead; the model constructor
        // validates the canonical 8-4-4-4-12 hex shape.
        typeMapping.put("UUID", "UUID");
        // 2.3 — `format: uri` (absolute URI per RFC 3986). Use a branded
        // alias for compile-time discrimination from a free-form string,
        // mirroring UUID. The `uri-reference` and `uri-template` subformats
        // are demoted back to plain `string` in `postProcessModelProperty`
        // because neither is guaranteed absolute.
        typeMapping.put("URI", "URI");
        // 2.4 — `format: email` produces a branded `Email = string & {...}`
        // so a callsite can declare intent and prevent free-form strings.
        typeMapping.put("Email", "Email");
        // 4.8: `format: time` (RFC 3339 partial-time) and `format: duration`
        // (ISO 8601 duration). Node has no native time-of-day or duration
        // type, so we adopt `temporal-polyfill` which ships the TC39 Temporal
        // proposal. PlainTime/Duration round-trip via their `.from(string)`
        // factory and `.toString()` — wired up at the ObjectSerializer
        // boundary and via @Transform decorators on the model fields.
        typeMapping.put("time", "Temporal.PlainTime");
        typeMapping.put("duration", "Temporal.Duration");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "unknown");
        typeMapping.put("array", "Array");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "{ [key: string]: unknown }");
        typeMapping.put("Map", "{ [key: string]: unknown }");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "number", "boolean", "string", "object", "any", "unknown",
                                "void", "undefined", "null", "Array", "Set", "Buffer", "UUID",
                                "URI", "Email", "Decimal",
                                // 4.8 — Temporal.* types come from `temporal-polyfill`,
                                // not from generated models, so the model-import filter
                                // must not treat them as model classes.
                                "Temporal.PlainTime", "Temporal.Duration"));

        reservedWords = loadReservedWords("/reserved-words/node.txt");

        additionalProperties.put(CodegenConstants.MODEL_PROPERTY_NAMING, "original");
        additionalProperties.put("importFileExtension", ".js");

        setEnumUnknownDefaultCase(true);

        cliOptions.add(CliOption.newString("repositoryUrl",
                "URL of the source repository, written to the package manifest and the README."));
        cliOptions.add(CliOption.newString("homepageUrl",
                "URL of the project home page, written to the package manifest and the README."));
        cliOptions.add(CliOption.newString("authors",
                "Authors of the package, written to the package manifest and the README."));
        cliOptions.add(CliOption.newString("license",
                "SPDX identifier of the package license (default: MIT). Same as licenseId."));
        cliOptions.add(CliOption.newString("documentationUrl",
                "URL of the documentation, written to the README."));
        cliOptions.add(CliOption.newString("issueTrackerUrl",
                "URL of the issue tracker, written to the package manifest and the README."));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated npm package (default: 1.0.0).").defaultValue("1.0.0"));
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "node-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal TypeScript client using the Fetch API.";
    }

    /** {@inheritDoc} */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.TYPESCRIPT;
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
        return NamingConvention.IDENTITY;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getOperationIdCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getEnumCasing() {
        return NamingConvention.PASCAL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "node:24-slim@sha256:242549cd46785b480c832479a730f4f2a20865d61ea2e404fdb2a5c3d3b73ecf";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            // node_modules holds thousands of small files, which are slow to write onto the
            // bind-mounted output directory; install into the container's own disk and link it.
            "mkdir -p /opt/np && cp package.json /opt/np/",
            "( [ ! -f package-lock.json ] || cp package-lock.json /opt/np/ )",
            "npm install --ignore-scripts --prefix /opt/np",
            "ln -s /opt/np/node_modules node_modules",
            "npx prettier --write .",
            "rm -rf node_modules"
        };
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.KEBAB_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getParamCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /**
     * Derives the per-API-group accessor property name on the client
     * facade. Node uses {@link NamingConvention#IDENTITY} for general
     * variables to preserve spec-defined property names, but instance
     * properties on the client must follow the TypeScript camelCase
     * convention (e.g. {@code PetApi} → {@code pet}), so this overrides
     * the default {@link #getVarCasing()}-based derivation.
     */
    @Override
    protected String deriveClientPropertyName(String apiClassName) {
        final String name = apiClassName.replaceAll("Api$", "");
        return name.isEmpty()
                ? NamingConvention.CAMEL_CASE.apply("api")
                : NamingConvention.CAMEL_CASE.apply(name);
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "Set<";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayContainerPattern() {
        return "^Array<";
    }

    /** {@inheritDoc} */
    @Override
    protected String getEmptyEnumVarName() {
        return "Empty";
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

    /**
     * Resolves user-supplied codegen options and registers all
     * supporting files for the TypeScript package structure.
     * Sets up the output layout including models, API classes,
     * exceptions, auth, serialization, and configuration modules.
     * Also registers test scaffolding files when test generation
     * is enabled.
     */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final List<SupportingFileSpec> specs = new ArrayList<>(List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("api_client.mustache", "src", "api-client.ts"),
            new SupportingFileSpec("abstract_api_client.mustache", "src", "abstract-api-client.ts"),
            new SupportingFileSpec("default_api_client.mustache", "src", "default-api-client.ts"),
            new SupportingFileSpec("web_api_client.mustache", "src", "default-api-client.web.ts"),
            new SupportingFileSpec("api_response.mustache", "src", "api-http-response.ts"),
            new SupportingFileSpec("api_stream_response.mustache", "src", "api-stream-response.ts"),
            new SupportingFileSpec("body_source.mustache", "src", "body-source.ts"),
            new SupportingFileSpec("byte_stream.mustache", "src", "byte-stream.ts"),
            new SupportingFileSpec("paginator.mustache", "src", "paginator.ts"),
            new SupportingFileSpec("link_header.mustache", "src", "link-header.ts"),
            new SupportingFileSpec("errors/pagination-error.mustache", "src/errors", "pagination-error.ts"),
            new SupportingFileSpec("event_stream.mustache", "src", "event-stream.ts"),
            new SupportingFileSpec("server_sent_event.mustache", "src", "server-sent-event.ts"),
            new SupportingFileSpec("stream_options.mustache", "src", "stream-options.ts"),
            new SupportingFileSpec("json_lines_stream.mustache", "src", "json-lines-stream.ts"),
            new SupportingFileSpec("sse_stream.mustache", "src", "sse-stream.ts"),
            new SupportingFileSpec("api_result.mustache", "src", "api-result.ts"),
            new SupportingFileSpec("configuration.mustache", "src", "configuration.ts"),
            new SupportingFileSpec("transport_options.mustache", "src", "transport-options.ts"),
            new SupportingFileSpec("server_configuration.mustache", "src", "server-configuration.ts"),
            new SupportingFileSpec("servers.mustache", "src", "servers.ts"),
            new SupportingFileSpec("errors/api-error.mustache", "src/errors", "api-error.ts"),
            new SupportingFileSpec("base_api.mustache", "src/api", "base-api.ts"),
            new SupportingFileSpec("errors/index.mustache", "src/errors", "index.ts"),
            new SupportingFileSpec("errors/root-error.mustache", "src/errors", errorPrefixKebab() + "-error.ts"),
            new SupportingFileSpec("errors/client-error.mustache", "src/errors", "client-error.ts"),
            new SupportingFileSpec("errors/server-error.mustache", "src/errors", "server-error.ts"),
            new SupportingFileSpec("errors/bad-request-error.mustache", "src/errors", "bad-request-error.ts"),
            new SupportingFileSpec("errors/unauthorized-error.mustache", "src/errors", "unauthorized-error.ts"),
            new SupportingFileSpec("errors/forbidden-error.mustache", "src/errors", "forbidden-error.ts"),
            new SupportingFileSpec("errors/not-found-error.mustache", "src/errors", "not-found-error.ts"),
            new SupportingFileSpec("errors/conflict-error.mustache", "src/errors", "conflict-error.ts"),
            new SupportingFileSpec("errors/payment-required-error.mustache", "src/errors", "payment-required-error.ts"),
            new SupportingFileSpec("errors/method-not-allowed-error.mustache", "src/errors", "method-not-allowed-error.ts"),
            new SupportingFileSpec("errors/request-timeout-error.mustache", "src/errors", "request-timeout-error.ts"),
            new SupportingFileSpec("errors/gone-error.mustache", "src/errors", "gone-error.ts"),
            new SupportingFileSpec("errors/payload-too-large-error.mustache", "src/errors", "payload-too-large-error.ts"),
            new SupportingFileSpec("errors/unsupported-media-type-error.mustache", "src/errors", "unsupported-media-type-error.ts"),
            new SupportingFileSpec("errors/locked-error.mustache", "src/errors", "locked-error.ts"),
            new SupportingFileSpec("errors/not-implemented-error.mustache", "src/errors", "not-implemented-error.ts"),
            new SupportingFileSpec("errors/bad-gateway-error.mustache", "src/errors", "bad-gateway-error.ts"),
            new SupportingFileSpec("errors/service-unavailable-error.mustache", "src/errors", "service-unavailable-error.ts"),
            new SupportingFileSpec("errors/gateway-timeout-error.mustache", "src/errors", "gateway-timeout-error.ts"),
            new SupportingFileSpec("errors/unprocessable-entity-error.mustache", "src/errors", "unprocessable-entity-error.ts"),
            new SupportingFileSpec("errors/too-many-requests-error.mustache", "src/errors", "too-many-requests-error.ts"),
            new SupportingFileSpec("errors/internal-server-error.mustache", "src/errors", "internal-server-error.ts"),
            new SupportingFileSpec("errors/network-error.mustache", "src/errors", "network-error.ts"),
            new SupportingFileSpec("errors/network-timeout-error.mustache", "src/errors", "network-timeout-error.ts"),
            new SupportingFileSpec("errors/stream-error.mustache", "src/errors", "stream-error.ts"),
            new SupportingFileSpec("errors/response-too-large-error.mustache", "src/errors", "response-too-large-error.ts"),
            new SupportingFileSpec("errors/serialization-error.mustache", "src/errors", "serialization-error.ts"),
            new SupportingFileSpec("errors/oauth2-server-error.mustache", "src/errors", "oauth2-server-error.ts"),
            new SupportingFileSpec("errors/oauth2-token-error.mustache", "src/errors", "oauth2-token-error.ts"),
            new SupportingFileSpec("brand.mustache", "src", "brand.ts"),
            new SupportingFileSpec("deep_input.mustache", "src", "deep-input.ts"),
            new SupportingFileSpec("object_serializer.mustache", "src", "object-serializer.ts"),
            new SupportingFileSpec("value_serializer.mustache", "src", "value-serializer.ts"),
            new SupportingFileSpec("header_selector.mustache", "src", "header-selector.ts"),
            new SupportingFileSpec("trace_context_util.mustache", "src", "trace-context-util.ts"),
            new SupportingFileSpec("call_hook.mustache", "src", "call-hook.ts"),
            new SupportingFileSpec("constraint_validator.mustache", "src", "constraint-validator.ts"),
            new SupportingFileSpec("opentelemetry_call_hook.mustache", "src", "opentelemetry-call-hook.ts"),
            new SupportingFileSpec("tsconfig.mustache", "", "tsconfig.json"),
            new SupportingFileSpec("typedoc.mustache", "", "typedoc.json"),
            new SupportingFileSpec("models/index.mustache", "src/models", "index.ts"),
            new SupportingFileSpec("api/index.mustache", "src/api", "index.ts"),
            new SupportingFileSpec("index.mustache", "src", "index.ts"),
            new SupportingFileSpec("package.mustache", "", "package.json"),
            new SupportingFileSpec("prettierrc.mustache", "", ".prettierrc.json"),
            new SupportingFileSpec("prettierignore.mustache", "", ".prettierignore"),
            new SupportingFileSpec("eslint_config.mustache", "", "eslint.config.js"),
            new SupportingFileSpec("authenticator.mustache", "src/auth", "authenticator.ts"),
            new SupportingFileSpec("credentials.mustache", "src/auth", "credentials.ts"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore")
        ));
        if (generateExamples) {
            /* Outside src/: tsconfig.examples.json compiles it against the built package,
             * which it imports by its own name, so it never ships in dist. */
            specs.add(new SupportingFileSpec("examples/quickstart.mustache", "examples", "quickstart.ts"));
            specs.add(new SupportingFileSpec("tsconfig_examples.mustache", "", "tsconfig.examples.json"));
        }
        return specs;
    }

    /** Base classes the barrel already exports, so a per-scheme class of the same name is skipped. */
    private static final Set<String> BASE_AUTHENTICATORS = Set.of(
            "BasicAuthenticator", "BearerAuthenticator", "ApiKeyAuthenticator", "DigestAuthenticator",
            "OAuth2ClientCredentialsAuthenticator", "OAuth2PasswordAuthenticator",
            "OAuth2AuthorizationCodeAuthenticator", "OAuth2ImplicitAuthenticator",
            "OAuth2DeviceAuthorizationAuthenticator", "OpenIdConnectAuthenticator");

    /** Per-scheme authenticators for the barrel: {className, path}. Filled during generation. */
    private final List<Map<String, String>> perSchemeAuthExports = new ArrayList<>();

    /** Per-scheme authenticators for the unit test: {className, baseClass}. */
    private final List<Map<String, String>> perSchemeAuthEntries = new ArrayList<>();

    /** Unique class names the per-scheme unit test imports from the entry point. */
    private final List<Map<String, String>> perSchemeAuthImports = new ArrayList<>();

    @Override
    public void processOpts() {
        // `license` is the SPDX identifier under its package-metadata name; the shared
        // option that fills the manifest is `licenseId`, which wins when both are given.
        if (additionalProperties.containsKey("license") && !additionalProperties.containsKey("licenseId")) {
            additionalProperties.put("licenseId", additionalProperties.get("license"));
        }
        super.processOpts();
        for (final String key : List.of("repositoryUrl", "homepageUrl", "authors", "issueTrackerUrl")) {
            final Object value = additionalProperties.get(key);
            if (value != null && !value.toString().isBlank()) {
                additionalProperties.put(key + "Json", jsonEscape(value.toString().strip()));
            } else {
                additionalProperties.remove(key);
            }
        }
        for (final String key : List.of("documentationUrl")) {
            final Object value = additionalProperties.get(key);
            if (value == null || value.toString().isBlank()) {
                additionalProperties.remove(key);
            }
        }
        additionalProperties.put("perSchemeAuthExports", perSchemeAuthExports);
        additionalProperties.put("perSchemeAuthEntries", perSchemeAuthEntries);
        additionalProperties.put("perSchemeAuthImports", perSchemeAuthImports);
        final String packageVersion = getPropertyOrDefault("packageVersion", "1.0.0");
        additionalProperties.put("packageVersion", packageVersion);
        final String npmName = getPropertyOrDefault("npmName", "openapi-typescript-client");
        additionalProperties.put(
                "userAgentDefault", npmName + "/" + packageVersion + " (node)");

        // Only set apiPackage default when the caller did not override it.
        if (this.apiPackage == null || this.apiPackage.isEmpty() || "openapitools".equals(this.apiPackage)) {
            this.apiPackage = "api";
        }

        final String clientClassName =
                Objects.requireNonNull(
                        (String) additionalProperties.get("clientClassName"));
        final String clientClassFile = getFilenameCasing().apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", "src", clientClassFile + ".ts"));

        if (emitUnitTests()) {
            // The dev-dependency manifest: one line per dependency an emitted test
            // imports. Every client keep-lists its own package manifest, so this
            // generator-owned file is the only thing keeping the two in step.
            supportingFiles.add(
                    new SupportingFile(
                            "dev_dependencies.mustache",
                            ".openapi-generator",
                            "DEV-DEPENDENCIES"));
            // Type-checks the tests together with the sources, as the SDK's own
            // client does before it runs them.
            supportingFiles.add(new SupportingFile("tsconfig_jest.mustache", "", "tsconfig.jest.json"));
        }
        if (emitUnitTests()) {
            // Spec-independent pure-unit tests. These import only supporting
            // modules (ValueSerializer, HeaderSelector, Configuration,
            // TransportOptions, trace-context util, DefaultApiClient) and no
            // spec-derived models, Api classes, or scheme-gated authenticators,
            // so they are safe to emit into any real client. They run under the
            // client's own jest config and need no container harness — the
            // generator's jest.config / global-setup / global-teardown / setup
            // (which spin up the chasm/squid containers) stay golden-only.
            supportingFiles.add(
                    new SupportingFile(
                            "test/value-serializer.test.mustache",
                            "test",
                            "value-serializer.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header-selector.test.mustache",
                            "test",
                            "header-selector.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration.test.mustache",
                            "test",
                            "configuration.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport-options.test.mustache",
                            "test",
                            "transport-options.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace-context-util.test.mustache",
                            "test",
                            "trace-context-util.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/call-hook.test.mustache",
                            "test",
                            "call-hook.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/opentelemetry-call-hook.test.mustache",
                            "test",
                            "opentelemetry-call-hook.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default-api-client-unit.test.mustache",
                            "test",
                            "default-api-client-unit.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/web-api-client.test.mustache",
                            "test",
                            "web-api-client.test.ts"));
            // U1: ServerConfiguration / ServerVariable are spec-independent
            // supporting modules emitted into every client, so their tests ship
            // alongside the other pure-unit tests above.
            supportingFiles.add(
                    new SupportingFile(
                            "test/server-configuration.test.mustache",
                            "test",
                            "server-configuration.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server-variable.test.mustache",
                            "test",
                            "server-variable.test.ts"));
            // U2: ApiResult is a spec-independent supporting type emitted into
            // every client.
            supportingFiles.add(
                    new SupportingFile(
                            "test/api-result.test.mustache",
                            "test",
                            "api-result.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/credentials.test.mustache",
                            "test",
                            "credentials.test.ts"));
            // The parser, the line splitter and the two stream types over
            // canned bytes: the mirror test of event-stream.ts.
            supportingFiles.add(
                    new SupportingFile(
                            "test/event-stream.test.mustache",
                            "test",
                            "event-stream.test.ts"));
        }

        // Spec-coupled tests: they import spec-derived models/APIs, the
        // container transport harness, or scheme-gated authenticators that
        // exist only in the petstore golden, so they are emitted only for the
        // generator's own golden validation, never shipped into real clients.
        if (generateTests) {
            // Jest harness + chasm/squid container bootstrap — only the golden's
            // container transport tests use these; real clients ship their own
            // keep-listed jest config, so keep them golden-only.
            supportingFiles.add(new SupportingFile("test/jest.config.mjs", "", "jest.config.mjs"));
            supportingFiles.add(
                    new SupportingFile("test/global-setup.ts", "test", "global-setup.ts"));
            supportingFiles.add(
                    new SupportingFile("test/global-teardown.ts", "test", "global-teardown.ts"));
            supportingFiles.add(new SupportingFile("test/setup.ts", "test", "setup.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet-api.test.mustache",
                            Path.of("test", "api").toString(),
                            "pet-api.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store-api.test.mustache",
                            Path.of("test", "api").toString(),
                            "store-api.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default-api-client.test.mustache",
                            "test",
                            "default-api-client.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object-serializer.test.mustache",
                            "test",
                            "object-serializer.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base-api.test.mustache",
                            "test",
                            "base-api.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata.test.mustache",
                            "test",
                            "metadata.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed-schema.test.mustache",
                            "test",
                            "composed-schema.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client.test.mustache",
                            "test",
                            "client.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api-error.test.mustache",
                            "test",
                            "api-error.test.ts"));
        }
    }

    /**
     * Returns the output directory for model source files by
     * combining the output folder with the src/models path.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, "src", "models").toString();
    }

    /**
     * Returns the output directory for API source files by
     * combining the output folder with the src/api path.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, "src", "api").toString();
    }

    /**
     * Overrides the base class because TypeScript uses
     * index-signature syntax ({@code \{ [key: string]: T \}})
     * for maps. Cannot use the base class's {@code formatMapType}
     * hook because Node's typeMapping stores the full syntax,
     * not a simple container name.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            final Schema inner = ModelUtils.getSchemaItems(p);
            return getSchemaType(p) + "<" + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isMapSchema(p)) {
            final String valueType =
                    Optional.ofNullable(ModelUtils.getAdditionalProperties(p))
                            .map(this::getTypeDeclaration)
                            .orElse("unknown");
            return "{ [key: string]: " + valueType + " }";
        }
        return super.getTypeDeclaration(p);
    }

    /*
     * Returns null for all schema types because TypeScript
     * variables do not need explicit default value expressions
     * in the generated model constructors.
     */
    /**
     * Returns a quoted string literal for string enum schemas that
     * have a declared OAS {@code default} (e.g. {@code 'placed'}).
     * The quote character is the Node/TypeScript single-quote so
     * the value is used as-is in the model template without further
     * transformation. All other types return null.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (resolved.getDefault() == null) {
            return null;
        }
        // String enum: emit the wire value; fixEnumDefaultValue rewrites it to
        // the typed member (e.g. DefaultsModeEnum.Medium) afterwards.
        if (ModelUtils.isStringSchema(resolved)
                && resolved.getEnum() != null
                && !resolved.getEnum().isEmpty()) {
            return getQuoteChar() + resolved.getDefault().toString() + getQuoteChar();
        }
        // Plain (non-enum) string default → a TypeScript string literal.
        if (ModelUtils.isStringSchema(resolved)) {
            return toStringLiteral(resolved.getDefault().toString());
        }
        // Numeric/boolean defaults render verbatim as TypeScript literals.
        if (ModelUtils.isBooleanSchema(resolved)
                || ModelUtils.isIntegerSchema(resolved)
                || ModelUtils.isNumberSchema(resolved)) {
            return resolved.getDefault().toString();
        }
        return null;
    }

    /**
     * Renders {@code value} as a single-quoted TypeScript string literal
     * that evaluates to exactly that text: backslash, quote and line
     * terminators are escaped, everything else is kept verbatim.
     */
    static String toStringLiteral(String value) {
        final StringBuilder out = new StringBuilder(value.length() + 2).append('\'');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\'') {
                out.append("\\'");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c == ' ') {
                out.append("\\u2028");
            } else if (c == ' ') {
                out.append("\\u2029");
            } else {
                out.append(c);
            }
        }
        return out.append('\'').toString();
    }

    /**
     * Types a {@code format: decimal} parameter, number- or string-shaped, as
     * decimal.js's {@code Decimal}, like the model fields, so the value is
     * exact and written in plain notation.
     */
    @Override
    public CodegenParameter fromParameter(
            io.swagger.v3.oas.models.parameters.Parameter parameter, Set<String> imports) {
        final CodegenParameter p = super.fromParameter(parameter, imports);
        if ("decimal".equals(p.dataFormat)
                && ("number".equals(p.dataType) || "string".equals(p.dataType))) {
            p.dataType = "Decimal";
            p.baseType = "Decimal";
        }
        return p;
    }

    /**
     * Records the branded scalar types ({@code UUID}, {@code URI},
     * {@code Email}, {@code Decimal}) that an API class's positional
     * parameters use, as {@code brandImports}, so the API file imports them
     * from the brand module instead of referencing an undeclared name.
     */
    private static void collectBrandImports(Map<String, Object> operations, OperationsMap objs) {
        final Object opsObj = operations.get("operation");
        final Set<String> brands = new java.util.TreeSet<>();
        if (opsObj instanceof List<?>) {
            for (final Object o : (List<?>) opsObj) {
                final CodegenOperation op = (CodegenOperation) o;
                // A decimal response body (markDecimalReturn) names Decimal in the method.
                if (op.returnType != null && op.returnType.contains("Decimal")) {
                    brands.add("Decimal");
                }
                final List<CodegenParameter> positional = new ArrayList<>(op.allParams);
                if (op.pathParams != null) {
                    positional.addAll(op.pathParams);
                }
                for (final CodegenParameter p : positional) {
                    // A query, header, form or cookie parameter travels on the
                    // Options object, whose own file imports its type.
                    if (p.isQueryParam || p.isHeaderParam || p.isFormParam || p.isCookieParam) {
                        continue;
                    }
                    if (p.dataType != null && BRAND_TYPES.contains(p.dataType)) {
                        brands.add(p.dataType);
                    }
                }
            }
        }
        objs.put("brandImports", new ArrayList<>(brands));
        objs.put("hasBrandImports", !brands.isEmpty());
    }

    private static final Set<String> BRAND_TYPES = Set.of("UUID", "URI", "Email", "Decimal");

    /**
     * Registers the node-only Mustache lambdas: {@code tsString} renders its
     * raw content as an exact single-quoted string literal, and
     * {@code tsComment} neutralises a block-comment terminator so spec prose
     * cannot close a JSDoc comment early, and folds line breaks into spaces
     * so a multi-line description stays inside its comment line.
     */
    @Override
    protected com.google.common.collect.ImmutableMap.Builder<String, com.samskivert.mustache.Mustache.Lambda>
            addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put("tsString", (fragment, writer) -> writer.write(toStringLiteral(fragment.execute())))
                .put(
                        "tsComment",
                        (fragment, writer) ->
                                writer.write(
                                        fragment.execute()
                                                .replace("*/", "*\\/")
                                                .replaceAll("\\s*(\\r\\n|\\r|\\n)\\s*", " ")));
    }

    /** {@inheritDoc} */
    @Override
    protected boolean hasOpenTelemetryAdapter() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean shouldEscapeReservedVarName(String name) {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected String getModelNameCollisionPrefix() {
        return "Model";
    }

    /** Schema names that would shadow a global or collide with another SDK's model name. */
    private static final Set<String> SHADOWING_MODEL_NAMES = Set.of("Error", "Result");

    /**
     * Prefixes a schema named like the global {@code Error} (which it would shadow
     * in every model file that imports it) or like {@code Result} with {@code Model},
     * matching the other SDKs (D45, F173). This is done here and not through the
     * language primitives, because a primitive is never rewritten where a property
     * refers to it.
     */
    @Override
    public String toModelName(String name) {
        final String modelName = super.toModelName(name);
        return SHADOWING_MODEL_NAMES.contains(modelName) ? "Model" + modelName : modelName;
    }

    /**
     * Applies {@link #toModelName} to a reference to a schema named like a
     * shadowing global. The base class returns the raw schema name for a
     * {@code $ref}, so without this a property typed {@code Error} would import
     * and decorate the global {@code Error} while the model file exports
     * {@code ModelError}.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String getSchemaType(Schema schema) {
        final String type = super.getSchemaType(schema);
        return SHADOWING_MODEL_NAMES.contains(type) ? toModelName(type) : type;
    }

    /**
     * Names of the arguments the SDK itself adds to every operation. A parameter
     * named like one would produce two parameters of the same name (TS2300), so
     * the shared {@code toParamName} suffixes it with {@code Param} (F1055).
     */
    @Override
    protected Set<String> getInjectedArgumentNames() {
        return Set.of("options", "requestOptions", "server");
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("number", "boolean");
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '\'';
    }

    /** {@inheritDoc} */
    @Override
    public String toEnumVarName(String value, String datatype) {
        final String mapped = enumNameMapping.get(value);
        if (mapped != null) {
            return mapped;
        }
        final String name = super.toEnumVarName(value, datatype);
        if (!name.isEmpty()) {
            // An integer member takes the member casing of every other enum (Number1, not
            // NUMBER_1), as the string members do.
            return isNumericEnumDatatype(datatype) && !value.isEmpty()
                    ? getEnumCasing().apply(name.toLowerCase(java.util.Locale.ROOT))
                    : name;
        }
        // A value that sanitises to nothing still needs a legal member name:
        // whitespace-only becomes SPACE_<length>, anything else VALUE_<hex
        // code points>, so " " and "@@" stay distinct, valid identifiers.
        if (!value.isEmpty() && value.isBlank()) {
            return "SPACE_" + value.length();
        }
        final StringBuilder hex = new StringBuilder("VALUE");
        value.codePoints()
                .forEach(cp -> hex.append('_').append(String.format(java.util.Locale.ROOT, "%04X", cp)));
        return hex.toString();
    }

    /**
     * For TypeScript, an enum-typed property's default value must be the
     * enum member reference (e.g. {@code OrderStatusEnum.Placed}), not the
     * raw string literal, because a string literal is not assignable to a
     * string enum without an explicit cast.
     *
     * <p>This overrides the base class behaviour, which emits a quoted
     * string literal suitable for most languages but invalid in TypeScript
     * strict mode when the property has been retyped to a generated
     * {@code enum}.
     */
    @Override
    protected void fixEnumDefaultValue(
            org.openapitools.codegen.CodegenProperty prop,
            org.openapitools.codegen.CodegenModel model) {
        if (prop.defaultValue != null && prop.isEnum) {
            String raw = prop.defaultValue;
            // Base class may have already lowered the value to a quoted
            // literal ('placed'); strip the quotes back off.
            if (raw.length() >= 2
                    && raw.charAt(0) == getQuoteChar()
                    && raw.charAt(raw.length() - 1) == getQuoteChar()) {
                raw = raw.substring(1, raw.length() - 1);
            } else if (raw.contains(".")) {
                raw = raw.substring(raw.lastIndexOf('.') + 1);
            }
            final String enumTypeName = model.classname + prop.enumName;
            final String memberName =
                    enumNameMapping.containsKey(raw)
                            ? toEnumVarName(raw, prop.dataType)
                            : enumMemberNameForWire(prop, raw, prop.dataType);
            prop.defaultValue = enumTypeName + "." + memberName;
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String getModelImportContextKey() {
        return "tsImports";
    }

    /** {@inheritDoc} */
    @Override
    protected boolean filtersOptionsOnlyModelImports() {
        return true;
    }

    /**
     * Overrides the base class to add kebab-case filenames,
     * resolve inline enum parameter types, and clean up
     * TypeScript imports. Cannot be standardized because
     * TypeScript's import resolution and enum naming are unique.
     */
    @Override
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        objs = super.postProcessOperationsWithModels(objs, allModels);

        @SuppressWarnings("unchecked")
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            final String classname = (String) operations.get("classname");
            if (classname != null) {
                operations.put("classFilename", getFilenameCasing().apply(classname));
            }
            @SuppressWarnings("unchecked")
            final List<CodegenOperation> decimalOps =
                    (List<CodegenOperation>) operations.get("operation");
            if (decimalOps != null) {
                decimalOps.forEach(BetterNodeCodegen::markDecimalReturn);
            }
            collectBrandImports(operations, objs);

            @SuppressWarnings("unchecked")
            final List<CodegenOperation> ops =
                    (List<CodegenOperation>) operations.get("operation");
            if (ops != null) {
                boolean hasEnums = false;
                boolean hasBinaryStream = false;
                boolean hasBinaryUpload = false;
                boolean hasPagedOperation = false;
                boolean hasSseStream = false;
                boolean hasJsonLinesStream = false;
                for (final CodegenOperation op : ops) {
                    hasBinaryStream |= markBinaryStream(op);
                    hasBinaryUpload |= markBinaryUpload(op);
                    hasPagedOperation |= markPagedOperation(op);
                    final Object marked = op.vendorExtensions.get("op");
                    if (marked instanceof Map) {
                        hasSseStream |=
                                Boolean.TRUE.equals(((Map<?, ?>) marked).get("streamsSse"));
                        hasJsonLinesStream |=
                                Boolean.TRUE.equals(((Map<?, ?>) marked).get("streamsJsonLines"));
                    }
                    for (final CodegenParameter param : op.allParams) {
                        if (param.isEnum) {
                            hasEnums = true;
                            final String opIdCamelCase =
                                    NamingConvention.PASCAL_CASE.apply(
                                            op.operationId);
                            param.datatypeWithEnum =
                                    opIdCamelCase + param.enumName;
                        }
                    }
                    populateRequestWrapper(op);
                }
                boolean hasDeepInputBody = false;
                for (final CodegenOperation op : ops) {
                    if (op.bodyParam != null
                            && !op.bodyParam.dataType.equals(deepInputType(op.bodyParam))) {
                        hasDeepInputBody = true;
                        break;
                    }
                }
                objs.put("hasBinaryStream", hasBinaryStream);
                objs.put("hasBinaryUpload", hasBinaryUpload);
                objs.put("hasPagedOperation", hasPagedOperation);
                objs.put("hasSseStream", hasSseStream);
                objs.put("hasJsonLinesStream", hasJsonLinesStream);
                objs.put("hasStreamOptions", hasSseStream || hasJsonLinesStream);
                objs.put("hasDeepInputBody", hasDeepInputBody);
                objs.put("hasEnums", hasEnums);
            }
        }

        @SuppressWarnings("unchecked")
        final List<Map<String, String>> imports =
                (List<Map<String, String>>) objs.get("imports");
        if (imports != null) {
            imports.removeIf(
                    imp -> {
                        final String importName =
                                Optional.ofNullable(imp.get("classname"))
                                        .orElseGet(() -> imp.get("import"));
                        return importName == null
                                || languageSpecificPrimitives.contains(importName)
                                || typeMapping.containsValue(importName)
                                || importName.contains("_");
                    });
            for (final Map<String, String> imp : imports) {
                if (!imp.containsKey("className") && imp.containsKey("classname")) {
                    imp.put("className", imp.get("classname"));
                }
                if (!imp.containsKey("className") && imp.containsKey("import")) {
                    String className = imp.get("import");
                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf('.') + 1);
                    }
                    imp.put("className", className);
                }
            }
            // The API file names a model only in a method signature (a return
            // type or a parameter type). A model the spec attaches to an error
            // response, or to an Options field only, is never named there, so
            // importing it would be an unused import.
            final Set<String> named = namedInSignatures(objs);
            imports.removeIf(imp -> !named.contains(imp.get("className")));
            addSuccessVariantImports(objs, imports);
            addPagedItemImports(objs, imports);
        }
        objs.put("usesValueSerializer", usesValueSerializer(objs));
        return objs;
    }

    /**
     * A binary success response is returned as a {@code ByteStream} by the one
     * operation method (D119), so the return type becomes {@code ByteStream} and
     * the decorator {@code streamsBinary} tells the templates to call
     * {@code invokeApiForStream}.
     *
     * @return whether the operation streams a binary response
     */
    @SuppressWarnings("unchecked")
    static boolean markBinaryStream(final CodegenOperation op) {
        final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
        if (!(decorators instanceof Map)) {
            return false;
        }
        final Map<String, Object> d = (Map<String, Object>) decorators;
        final Object kind = d.get("streamKind");
        final boolean binary = "binary".equals(kind);
        d.put("streamsBinary", binary);
        if (binary) {
            op.returnType = "ByteStream";
            op.returnBaseType = "ByteStream";
        }
        final boolean streamOnly = Boolean.TRUE.equals(d.get("streamOnly"));
        final boolean sse = "sse".equals(kind) && streamOnly;
        final boolean jsonLines = "jsonlines".equals(kind) && streamOnly;
        d.put("streamsSse", sse);
        d.put("streamsJsonLines", jsonLines);
        if (sse || jsonLines) {
            final boolean text = sse && Boolean.TRUE.equals(d.get("streamEventIsText"));
            final Object declared = d.get("streamItemType");
            final String item = !text && declared != null ? declared.toString() : "string";
            final boolean typed = !"string".equals(item);
            final boolean model =
                    item.matches("[A-Z]\\w*") && !STREAM_ITEM_NON_MODELS.contains(item);
            d.put("streamTyped", typed);
            d.put("streamIsModel", model);
            d.put("streamItemTs", item);
            op.returnType = (sse ? "SseStream<" : "JsonLinesStream<") + item + ">";
            op.returnBaseType = sse ? "SseStream" : "JsonLinesStream";
        }
        return binary;
    }

    /**
     * A raw binary request body is a {@code BodySource}: the operation takes it, in every
     * signature, instead of a {@code Buffer}, so a large upload never has to fit in memory. It
     * runs before the request wrapper is built from the body type. Multipart and every other body
     * kind keep the type the spec maps.
     *
     * @return whether the operation takes a {@code BodySource}
     */
    static boolean markBinaryUpload(final CodegenOperation op) {
        final CodegenParameter body = op.bodyParam;
        if (body == null
                || !body.isBinary
                || op.isMultipart
                || (op.formParams != null && !op.formParams.isEmpty())) {
            return false;
        }
        body.dataType = "BodySource";
        body.datatypeWithEnum = "BodySource";
        if (op.allParams != null) {
            for (final CodegenParameter p : op.allParams) {
                if (p.isBodyParam) {
                    p.dataType = "BodySource";
                    p.datatypeWithEnum = "BodySource";
                }
            }
        }
        return true;
    }

    /**
     * A paged operation gets a {@code <operation>Pages} method that returns a {@code Paginator}; this
     * records the type of that return value as {@code paginatorType} on the pagination decorator, so
     * the class and the interface both say it. A list that follows a next URL, in the {@code Link} header
     * or in the body, has no cursor, so its cursor type is {@code never}.
     *
     * @return whether the operation has a paged method
     */
    @SuppressWarnings("unchecked")
    static boolean markPagedOperation(final CodegenOperation op) {
        final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
        if (!(decorators instanceof Map)) {
            return false;
        }
        final Object pagination = ((Map<String, Object>) decorators).get("pagination");
        if (!(pagination instanceof Map)
                || !Boolean.TRUE.equals(((Map<String, Object>) decorators).get("hasPagesMethod"))) {
            return false;
        }
        final Map<String, Object> p = (Map<String, Object>) pagination;
        p.put(
                "paginatorType",
                "Paginator<"
                        + p.get("itemType")
                        + ", "
                        + (Boolean.TRUE.equals(p.get("isUrlPaged")) ? "never" : p.get("cursorType"))
                        + ">");
        return true;
    }

    /**
     * The item model of a paged operation is named by its {@code Paginator} return type, which no
     * other signature of the operation needs to name, so it is imported here.
     */
    @SuppressWarnings("unchecked")
    private static void addPagedItemImports(
            OperationsMap objs, List<Map<String, String>> imports) {
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        final List<CodegenOperation> ops =
                operations == null ? null : (List<CodegenOperation>) operations.get("operation");
        if (ops == null) {
            return;
        }
        final Set<String> present = new HashSet<>();
        for (final Map<String, String> imp : imports) {
            present.add(imp.get("className"));
        }
        for (final CodegenOperation op : ops) {
            final Object deco = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
            final Object pagination =
                    deco instanceof Map ? ((Map<String, Object>) deco).get("pagination") : null;
            if (!(pagination instanceof Map)) {
                continue;
            }
            final Object model = ((Map<String, Object>) pagination).get("itemModel");
            if (model != null && present.add(model.toString())) {
                final Map<String, String> imp = new java.util.LinkedHashMap<>();
                imp.put("className", model.toString());
                imp.put("classname", model.toString());
                imports.add(imp);
            }
        }
    }

    /** Item types of a stream that are not generated model classes. */
    private static final Set<String> STREAM_ITEM_NON_MODELS =
            Set.of(
                    "Date", "Buffer", "Blob", "UUID", "URI", "Email", "Decimal", "Object", "Array",
                    "Record", "Map", "Set", "Uint8Array");

    /**
     * Hands the README and SKILLS the first operation that returns a
     * {@code ByteStream}, as {@code binaryStreamOperation} (with
     * {@code binaryStreamGroup}, its operations map, and
     * {@code hasBinaryStreamOperation}), so the Streaming example calls a real
     * operation of the spec under generation. Nothing is rendered when the spec has none.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        final String[] flags = {"streamsBinary", "streamsSse", "streamsJsonLines", "hasPagesMethod"};
        final String[] names = {"binaryStream", "sseStream", "jsonLinesStream", "paged"};
        for (int kind = 0; kind < flags.length; kind++) {
            CodegenOperation found = null;
            Object group = null;
            final Object apiInfo = objs.get("apiInfo");
            if (apiInfo instanceof Map) {
                final Object apis = ((Map<String, Object>) apiInfo).get("apis");
                if (apis instanceof List) {
                    for (final Object api : (List<Object>) apis) {
                        final Object operations = ((Map<String, Object>) api).get("operations");
                        if (!(operations instanceof Map)) {
                            continue;
                        }
                        final Object list = ((Map<String, Object>) operations).get("operation");
                        if (!(list instanceof List)) {
                            continue;
                        }
                        for (final Object candidate : (List<Object>) list) {
                            final CodegenOperation op = (CodegenOperation) candidate;
                            final Object decorators =
                                    op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
                            if (found == null
                                    && decorators instanceof Map
                                    && Boolean.TRUE.equals(((Map<String, Object>) decorators).get(flags[kind]))) {
                                found = op;
                                group = operations;
                            }
                        }
                    }
                }
            }
            final String name = names[kind];
            objs.put(
                    "has" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Operation",
                    found != null);
            if (found != null) {
                objs.put(name + "Operation", found);
                objs.put(name + "Group", group);
            }
        }
        return super.postProcessSupportingFileData(objs);
    }

    /**
     * An operation with several 2xx schemas decodes the body into the variant
     * its status code names, so the API file needs each variant class as a
     * value; the signature names only the union alias.
     */
    @SuppressWarnings("unchecked")
    private static void addSuccessVariantImports(
            OperationsMap objs, List<Map<String, String>> imports) {
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        final List<CodegenOperation> ops =
                operations == null ? null : (List<CodegenOperation>) operations.get("operation");
        if (ops == null) {
            return;
        }
        final Set<String> present = new HashSet<>();
        for (final Map<String, String> imp : imports) {
            present.add(imp.get("className"));
        }
        for (final CodegenOperation op : ops) {
            final Object deco = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
            if (!(deco instanceof Map)) {
                continue;
            }
            final Object variants = ((Map<String, Object>) deco).get("successVariants");
            if (!(variants instanceof List)) {
                continue;
            }
            for (final Object variant : (List<Object>) variants) {
                final String type = String.valueOf(((Map<String, Object>) variant).get("returnType"));
                if (present.add(type)) {
                    final Map<String, String> imp = new java.util.LinkedHashMap<>();
                    imp.put("className", type);
                    imp.put("classname", type);
                    imports.add(imp);
                }
            }
        }
    }

    /**
     * Every identifier the API file's method signatures name: the words of
     * each operation's return type and of each request-wrapper field type.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> namedInSignatures(OperationsMap objs) {
        final Set<String> named = new HashSet<>();
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations == null) {
            return named;
        }
        final List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
        if (ops == null) {
            return named;
        }
        final java.util.regex.Pattern word = java.util.regex.Pattern.compile("\\w+");
        for (final CodegenOperation op : ops) {
            final List<String> types = new ArrayList<>();
            if (op.returnType != null) {
                types.add(op.returnType);
            }
            final Object deco = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
            if (deco instanceof Map) {
                final Object fields = ((Map<String, Object>) deco).get("requestWrapperFields");
                if (fields instanceof List) {
                    for (final Object field : (List<Object>) fields) {
                        if (field instanceof Map && ((Map<String, Object>) field).get("type") != null) {
                            types.add(String.valueOf(((Map<String, Object>) field).get("type")));
                        }
                    }
                }
            }
            for (final String type : types) {
                final java.util.regex.Matcher m = word.matcher(type);
                while (m.find()) {
                    named.add(m.group());
                }
            }
        }
        return named;
    }

    /**
     * Whether any operation serializes a parameter through ValueSerializer:
     * every path, header and cookie parameter does, and so does every query
     * parameter except one sent as JSON content.
     */
    @SuppressWarnings("unchecked")
    private static boolean usesValueSerializer(OperationsMap objs) {
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations == null || operations.get("operation") == null) {
            return false;
        }
        for (final CodegenOperation op : (List<CodegenOperation>) operations.get("operation")) {
            if (!op.pathParams.isEmpty() || !op.headerParams.isEmpty() || !op.cookieParams.isEmpty()) {
                return true;
            }
            for (final CodegenParameter p : op.queryParams) {
                if (p.getContent() == null || p.getContent().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Computes the ordered list of an operation's parameters and stamps it onto
     * {@code op.vendorExtensions["op"]} for the api template.
     *
     * <p>Each operation method takes flat positional parameters in the structural
     * order of {@code signatureArgs} (path params, then body, then {@code server},
     * then the synthetic {@code options} object, then the per-call
     * {@code requestOptions}), e.g. {@code getExternalPetInfo(petId, server?)}. The api template renders that
     * parameter list — and the delegating call — from the field list below.
     *
     * <p>Populated key (under the {@code op} decorator namespace):
     * <ul>
     *   <li>{@code requestWrapperFields} — ordered list of maps, each with
     *       {@code name}, {@code type}, {@code optional} (boolean), and the
     *       Mustache-handled {@code -last}, used to render both the parameter
     *       list and the delegating call</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    void populateRequestWrapper(CodegenOperation op) {
        if (op == null) {
            return;
        }
        if (op.vendorExtensions == null) {
            op.vendorExtensions = new HashMap<>();
        }
        final Object opDecoObj = op.vendorExtensions.get("op");
        if (!(opDecoObj instanceof Map)) {
            return;
        }
        final Map<String, Object> d = (Map<String, Object>) opDecoObj;

        final String pascal = NamingConvention.PASCAL_CASE.apply(
                op.operationId == null ? "" : op.operationId);
        final String optionsClassName = pascal + "Options";
        final String serverClassName = pascal + "Server";

        final List<Map<String, Object>> fields = new ArrayList<>();

        if (op.pathParams != null) {
            for (final CodegenParameter p : op.pathParams) {
                fields.add(wrapperField(p.paramName, nullableType(dateInputType(p, p.dataType), p.isNullable), false));
            }
        }
        if (op.bodyParam != null) {
            // Request bodies are routinely supplied as plain object literals; a
            // bare class type would force callers to instantiate every nested
            // model just to satisfy structural typing. DeepInput<T> accepts both
            // a plain (deeply-partial, method-free) literal and a real instance.
            fields.add(
                    wrapperField(
                            op.bodyParam.paramName,
                            nullableType(
                                    deepInputType(op.bodyParam), op.bodyParam.isNullable),
                            !op.bodyParam.required));
        }
        final boolean hasQuery = op.queryParams != null && !op.queryParams.isEmpty();
        final boolean hasHeader = op.headerParams != null && !op.headerParams.isEmpty();
        final boolean hasForm = op.formParams != null && !op.formParams.isEmpty();
        final boolean hasCookie = op.cookieParams != null && !op.cookieParams.isEmpty();
        /* Order: required parameters, then the server, then the optional
         * parameters object, then the per-call request options. Security adds
         * no argument: an operation uses the credential of the scheme its spec
         * names, so there is no per-call authenticator to pass (F857). */
        final boolean optionsParamRequired = Boolean.TRUE.equals(d.get("optionsParamRequired"));
        final boolean hasOptions = hasQuery || hasHeader || hasForm || hasCookie;
        if (op.servers != null && !op.servers.isEmpty()) {
            /* TypeScript forbids a required parameter after an optional one, so
             * when the options object that follows is required the server is
             * spelled out as `Server | undefined` instead of `server?`. */
            if (hasOptions && optionsParamRequired) {
                fields.add(wrapperField("server", serverClassName + " | undefined", false));
            } else {
                fields.add(wrapperField("server", serverClassName, true));
            }
        }
        if (hasOptions) {
            final Map<String, Object> optionsField =
                    wrapperField("options", optionsClassName, !optionsParamRequired);
            optionsField.put("isOptions", true);
            fields.add(optionsField);
        }

        // Optional request content-type selector. An operation that declares
        // MULTIPLE request content-types (e.g. setPetAvatar: image/jpeg,
        // image/png, application/json) otherwise collapses to the first
        // declared type (effectiveConsumes) with no way to reach the others.
        // Expose an OPTIONAL trailing `requestContentType` parameter, typed as a
        // union literal of the declared content-types, so the caller can pick
        // among them. When omitted it defaults to the first declared type, so
        // every existing positional call site keeps compiling unchanged.
        // The parameter only changes the outgoing Content-Type header; for
        // content-types whose body type differs from the operation's body
        // param (e.g. setPetAvatar's application/json envelope) the caller is
        // responsible for supplying a body the server accepts.
        final List<String> declaredConsumes = declaredConsumes(op);
        if (declaredConsumes.size() > 1) {
            final StringBuilder union = new StringBuilder();
            final List<Map<String, Object>> consumeOptions = new ArrayList<>();
            for (int i = 0; i < declaredConsumes.size(); i++) {
                final String mediaType = declaredConsumes.get(i);
                if (i > 0) {
                    union.append(" | ");
                }
                union.append('\'').append(mediaType).append('\'');
                final Map<String, Object> co = new HashMap<>();
                co.put("mediaType", mediaType);
                consumeOptions.add(co);
            }
            fields.add(wrapperField("requestContentType", union.toString(), true));
            d.put("hasContentTypeSelector", true);
            d.put("contentTypeOptions", consumeOptions);
        } else {
            d.put("hasContentTypeSelector", false);
        }

        /* The trailing per-call cancellation handle: always last. A stream
         * operation takes StreamOptions, which includes the per-call options. */
        if (Boolean.TRUE.equals(d.get("streamOnly"))) {
            fields.add(wrapperField("streamOptions", "StreamOptions", true));
        } else {
            fields.add(wrapperField("requestOptions", "RequestOptions", true));
        }

        d.put("requestWrapperFields", fields);
    }

    /**
     * Returns the operation's declared request content-types in declaration
     * order, de-duplicated. Reads {@code op.consumes} (a list of maps keyed by
     * {@code mediaType}), which mirrors the spec's {@code requestBody.content}
     * media-type keys. Returns an empty list when the operation declares no
     * request body content-types.
     */
    private static List<String> declaredConsumes(CodegenOperation op) {
        final List<String> out = new ArrayList<>();
        if (op.consumes == null) {
            return out;
        }
        for (final Map<String, String> consume : op.consumes) {
            if (consume == null) {
                continue;
            }
            final String mediaType = consume.get("mediaType");
            if (mediaType != null && !mediaType.isEmpty() && !out.contains(mediaType)) {
                out.add(mediaType);
            }
        }
        return out;
    }

    /**
     * Returns the input type of a parameter. A binary upload (a file or binary
     * parameter, or an array of them) accepts a {@code Blob} (which includes a
     * {@code File}) as well as a {@code Buffer}, because the transport sends
     * both and fetch-only runtimes hold a {@code Blob}.
     */
    static String uploadType(CodegenParameter p) {
        final boolean upload =
                p.isFile
                        || p.isBinary
                        || (p.items != null && (p.items.isFile || p.items.isBinary));
        if (upload && p.dataType != null) {
            return p.dataType.replaceAll("\\bBuffer\\b", "Buffer | Blob");
        }
        return dateInputType(p, p.dataType);
    }

    /**
     * The type of a parameter value a caller passes: a {@code format: date}
     * parameter (or an array of them) is a {@code YYYY-MM-DD} string and also
     * accepts a {@code Date}, whose UTC date is sent.
     */
    static String dateInputType(CodegenParameter p, String type) {
        if (p.isDate) {
            return type.replaceFirst("^string$", "string | Date");
        }
        if (p.isArray && p.items != null && p.items.isDate) {
            return type.replace("<string>", "<string | Date>");
        }
        return type;
    }

    private static String nullableType(String dataType, boolean isNullable) {
        return isNullable ? dataType + " | null" : dataType;
    }

    /**
     * Returns the request-input type for a body parameter: the declared type
     * wrapped in {@code DeepInput<...>} when it (or its array element) references
     * a generated model, so plain object literals are accepted. Primitive and
     * primitive-array bodies are returned unchanged — there is no class to relax.
     */
    private String deepInputType(CodegenParameter body) {
        if ("BodySource".equals(body.dataType)) {
            return body.dataType;
        }
        final boolean elementIsModel =
                body.items != null
                        && !body.items.isPrimitiveType
                        && body.items.complexType != null
                        && !body.items.isEnum
                        && !isEnumModel(body.items.complexType);
        final boolean isModel =
                !body.isPrimitiveType
                        && !body.isArray
                        && body.baseType != null
                        && !languageSpecificPrimitives.contains(body.baseType)
                        && !body.isEnum
                        && !isEnumModel(body.baseType);
        if (isModel || elementIsModel) {
            return "DeepInput<" + body.dataType + ">";
        }
        return body.dataType;
    }

    private static Map<String, Object> wrapperField(
            String name, String type, boolean optional) {
        final Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("optional", optional);
        return m;
    }

    /**
     * Registers the bearer and API-key authenticator tests, each only when the
     * spec's security schemes produce that authenticator. Registered here, not
     * in processOpts: the scheme flags are only set once the spec is parsed.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();
        if (generateTests && hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/bearer-authenticator.test.mustache",
                            "test",
                            "bearer-authenticator.test.ts"));
        }
        if (generateTests && hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/api-key-authenticator.test.mustache",
                            "test",
                            "api-key-authenticator.test.ts"));
        }
        if (generateTests
                && (hasBasicAuth || hasBearerAuth || hasApiKeyAuth || hasAnyOAuth2 || hasOpenIdConnect)) {
            supportingFiles.add(
                    new SupportingFile(
                            "test/scheme-authenticators.test.mustache",
                            "test",
                            "scheme-authenticators.test.ts"));
        }
    }

    /** {@inheritDoc} */
    @Override
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        return List.of(
                new OAuthTestFileSpec("test/basic-authenticator.test.mustache", "test", "basic-authenticator.test.ts", OAuthTestCondition.BASIC),
                new OAuthTestFileSpec("test/oauth2-token-manager.test.mustache", "test", "oauth2-token-manager.test.ts", OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec("test/oauth2-auth-code-authenticator.test.mustache", "test", "oauth2-authorization-code-authenticator.test.ts", OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec("test/oauth2-implicit-authenticator.test.mustache", "test", "oauth2-implicit-authenticator.test.ts", OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec("test/oauth2-client-credentials-authenticator.test.mustache", "test", "oauth2-client-credentials-authenticator.test.ts", OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec("test/oauth2-password-authenticator.test.mustache", "test", "oauth2-password-authenticator.test.ts", OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec("test/openid-connect-authenticator.test.mustache", "test", "openid-connect-authenticator.test.ts", OAuthTestCondition.OIDC));
    }

    private static Map<String, String> imp(String className, String path) {
        final Map<String, String> importMap = new HashMap<>();
        importMap.put("className", className);
        importMap.put("path", path);
        return importMap;
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
        return stem.replace('_', '-') + ".ts";
    }

    /**
     * Renders a per-scheme authenticator TypeScript source file using
     * the scheme_authenticator.mustache template.
     */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        final Map<String, String> envNames = envNamesByField(ctx);
        recordSchemeAuthenticator(spec, String.valueOf(ctx.get("className")), envNames);
        final List<Map<String, String>> imports = new ArrayList<>(buildNodeImports(spec));
        final boolean credentialed = hasEnvCredentials(spec);
        if (credentialed) {
            imports.add(spec.isOAuth()
                    ? imp("resolveOAuthCredential", "../credentials")
                    : imp("resolveCredential", "./credentials"));
        }
        ctx.put("imports", imports);
        final List<Map<String, String>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, String> param = new HashMap<>();
            param.put("name", name);
            param.put("type", "string");
            // A static credential falls back to the environment, so it may be omitted.
            if (credentialed && !"host".equals(name)) {
                param.put("optional", "true");
                if ("token".equals(name)) {
                    param.put("type", "string | CredentialProvider");
                }
            }
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", buildNodeSuperArgs(spec, envNames));
        recordOAuth2MetadataUrl(ctx);
        recordDeviceAuthorizationScheme(spec, String.valueOf(ctx.get("className")));
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            ctx.put("typeImports", List.of(imp("CredentialProvider", "./credentials")));
        }
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    /** The per-scheme authenticators of a device authorization flow, for the test template (D191). */
    private final List<Map<String, Object>> deviceAuthorizationSchemes = new ArrayList<>();

    /**
     * Lists a device authorization scheme under {@code deviceAuthorizationSchemes} with its spec
     * URLs and scopes as literals, so the generated test checks the wrapper passes them (D191).
     */
    private void recordDeviceAuthorizationScheme(SchemeAuthSpec spec, String className) {
        if (!"OAuth2DeviceAuthorizationAuthenticator".equals(spec.baseClass())) {
            return;
        }
        final Map<String, Object> entry = new HashMap<>();
        entry.put("className", className);
        entry.put("deviceAuthorizationUrlLiteral",
                toStringLiteral(Objects.toString(spec.authorizationUrl(), "")));
        entry.put("tokenUrlLiteral", toStringLiteral(Objects.toString(spec.tokenUrl(), "")));
        entry.put("scopeLiteral", toStringLiteral(spec.scopes() == null
                ? "" : String.join(" ", spec.scopes().keySet())));
        deviceAuthorizationSchemes.add(entry);
        additionalProperties.put("deviceAuthorizationSchemes", deviceAuthorizationSchemes);
    }

    /** The per-scheme authenticators that declare an oauth2MetadataUrl, for the test template (D190). */
    private final List<Map<String, Object>> oauth2MetadataUrlSchemes = new ArrayList<>();

    /**
     * Adds the quoted {@code oauth2MetadataUrlLiteral} to a scheme context that declares a metadata
     * URL and lists the scheme under {@code oauth2MetadataUrlSchemes} for the generated test (D190).
     */
    private void recordOAuth2MetadataUrl(Map<String, Object> ctx) {
        if (!Boolean.TRUE.equals(ctx.get("hasOAuth2MetadataUrl"))) {
            return;
        }
        final String literal = toStringLiteral(String.valueOf(ctx.get("oauth2MetadataUrl")));
        ctx.put("oauth2MetadataUrlLiteral", literal);
        final Map<String, Object> entry = new HashMap<>();
        entry.put("className", ctx.get("className"));
        entry.put("oauth2MetadataUrlLiteral", literal);
        oauth2MetadataUrlSchemes.add(entry);
        additionalProperties.put("oauth2MetadataUrlSchemes", oauth2MetadataUrlSchemes);
    }

    /**
     * Remembers a generated per-scheme authenticator so the package entry
     * point re-exports it and its unit test covers it.
     */
    private void recordSchemeAuthenticator(
            SchemeAuthSpec spec, String className, Map<String, String> envNames) {
        if (BASE_AUTHENTICATORS.contains(className)) {
            return;
        }
        final String stem = NamingConvention.SNAKE_CASE.apply(className).replace('_', '-');
        perSchemeAuthExports.add(Map.of(
                "className", className,
                "path", (spec.isOAuth() ? "auth/oauth/" : "auth/") + stem));
        final Map<String, String> entry = new HashMap<>();
        entry.put("className", className);
        entry.put("baseClass", spec.baseClass());
        final String envVars = schemeEnvVars(spec, envNames);
        if (!envVars.isEmpty()) {
            entry.put("envVars", envVars);
            entry.put("envVarsText", envVars.replace("'", "`"));
        }
        perSchemeAuthEntries.add(entry);
        for (final String name : List.of(className, spec.baseClass())) {
            if (perSchemeAuthImports.stream().noneMatch(m -> name.equals(m.get("name")))) {
                perSchemeAuthImports.add(Map.of("name", name));
            }
        }
    }

    private List<Map<String, String>> buildNodeImports(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("BasicAuthenticator", "./basic-authenticator"));
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("BearerAuthenticator", "./bearer-authenticator"));
        }
        if ("DigestAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("DigestAuthenticator", "./digest-authenticator"));
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of(
                    imp("ApiKeyAuthenticator", "./api-key-authenticator"),
                    imp("ApiKeyLocation", "./api-key-location"));
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("OAuth2ClientCredentialsAuthenticator",
                    "./oauth2-client-credentials-authenticator"));
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("OAuth2PasswordAuthenticator",
                    "./oauth2-password-authenticator"));
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("OAuth2AuthorizationCodeAuthenticator",
                    "./oauth2-authorization-code-authenticator"));
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("OAuth2ImplicitAuthenticator",
                    "./oauth2-implicit-authenticator"));
        }
        if ("OAuth2DeviceAuthorizationAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("OAuth2DeviceAuthorizationAuthenticator",
                    "./oauth2-device-authorization-authenticator"));
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("OpenIdConnectAuthenticator",
                    "./openid-connect-authenticator"));
        }
        return List.of();
    }

    private static String formatNodeScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "['" + String.join("', '", scopes.keySet()) + "']";
    }

    /**
     * Wraps a value in a single-quoted TypeScript string literal, escaping the
     * backslash, the single quote and line terminators so a legal header or
     * query name cannot break the generated source.
     */
    static String quoteSingle(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", "\\n").replace("\r", "\\r") + "'";
    }

    /** The text as a JSON string body: backslash, quote and control characters escaped. */
    static String jsonEscape(String text) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    /**
     * Whether the scheme's authenticator reads its credentials from the environment when none is
     * passed: every scheme with a credential field, the basic, bearer and API key ones and the
     * OAuth2 and OpenID Connect ones alike.
     */
    private static boolean hasEnvCredentials(SchemeAuthSpec spec) {
        return !credentialFields(spec).isEmpty();
    }

    /** The credential fields of a scheme, in constructor order: every parameter but the host. */
    private static List<String> credentialFields(SchemeAuthSpec spec) {
        final List<String> fields = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            if (!"host".equals(name)) {
                fields.add(name);
            }
        }
        return fields;
    }

    /** The label an OAuth2 or OpenID Connect credential is named by in its error message. */
    private static String oauthLabel(String field) {
        final String label = Map.of(
                "clientId", "OAuth2 client ID",
                "clientSecret", "OAuth2 client secret",
                "username", "OAuth2 username",
                "password", "OAuth2 password",
                "redirectUri", "OAuth2 redirect URI").get(field);
        return Objects.requireNonNull(label, "not an OAuth2 credential: " + field);
    }

    /**
     * An OAuth2 or OpenID Connect credential argument of the per-scheme constructor: the caller's
     * value, else the scheme's own environment variable, refused if it has a line break.
     */
    private static String oauthArg(Map<String, String> envNames, String field) {
        return "resolveOAuthCredential(" + field + ", " + quoteSingle(envName(envNames, field))
                + ", " + quoteSingle(oauthLabel(field)) + ")";
    }

    /**
     * The environment variable of each credential field of a scheme, by field name, as the shared
     * generator computed them (F862); the per-scheme authenticator reads exactly this name.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> envNamesByField(Map<String, Object> ctx) {
        final Map<String, String> byField = new HashMap<>();
        final Object envVars = ctx.get("envVars");
        if (envVars instanceof List) {
            for (final Object entry : (List<Object>) envVars) {
                final Map<String, Object> e = (Map<String, Object>) entry;
                byField.put(String.valueOf(e.get("field")), String.valueOf(e.get("envName")));
            }
        }
        return byField;
    }

    /** The environment variable of a credential field; a scheme the shared pass did not name has none. */
    private static String envName(Map<String, String> envNames, String field) {
        return Objects.requireNonNull(envNames.get(field), "no environment variable for " + field);
    }

    /**
     * The environment variables a scheme's authenticator reads, as the quoted list the unit test
     * puts in an array literal ({@code 'PREFIX_TOKEN'}); empty for an authenticator that takes no
     * environment fallback.
     */
    private static String schemeEnvVars(SchemeAuthSpec spec, Map<String, String> envNames) {
        final List<String> quoted = new ArrayList<>();
        for (final String field : credentialFields(spec)) {
            quoted.add(quoteSingle(envName(envNames, field)));
        }
        return String.join(", ", quoted);
    }

    private List<String> buildNodeSuperArgs(SchemeAuthSpec spec, Map<String, String> envNames) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host",
                    "resolveCredential(username, " + quoteSingle(envName(envNames, "username"))
                            + ", 'Basic auth username')",
                    "resolveCredential(password, " + quoteSingle(envName(envNames, "password"))
                            + ", 'Basic auth password')");
        }
        if ("DigestAuthenticator".equals(spec.baseClass())) {
            return List.of("host",
                    "resolveCredential(username, " + quoteSingle(envName(envNames, "username"))
                            + ", 'Digest username')",
                    "resolveCredential(password, " + quoteSingle(envName(envNames, "password"))
                            + ", 'Digest password')");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host",
                    "typeof token === 'function' ? token : resolveCredential(token, "
                            + quoteSingle(envName(envNames, "token")) + ", 'Bearer token')");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            final String keyName = java.util.Objects.toString(spec.keyParamName(), "");
            return List.of("host", quoteSingle(keyName),
                    "resolveCredential(apiKey, " + quoteSingle(envName(envNames, "apiKey")) + ", "
                            + quoteSingle("API key for '" + keyName + "'") + ")",
                    "ApiKeyLocation." + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of("host", oauthArg(envNames, "clientId"), oauthArg(envNames, "clientSecret"),
                    "'" + spec.tokenUrl() + "'", formatNodeScopes(spec.scopes()));
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "null";
            return List.of("host", oauthArg(envNames, "clientId"), oauthArg(envNames, "clientSecret"),
                    "'" + spec.tokenUrl() + "'", oauthArg(envNames, "username"),
                    oauthArg(envNames, "password"), formatNodeScopes(spec.scopes()), refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "null";
            return List.of("host", oauthArg(envNames, "clientId"), oauthArg(envNames, "clientSecret"),
                    "'" + spec.authorizationUrl() + "'", "'" + spec.tokenUrl() + "'",
                    oauthArg(envNames, "redirectUri"), formatNodeScopes(spec.scopes()), refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of("host", oauthArg(envNames, "clientId"),
                    "'" + spec.authorizationUrl() + "'", formatNodeScopes(spec.scopes()));
        }
        if ("OAuth2DeviceAuthorizationAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "null";
            return List.of("host", oauthArg(envNames, "clientId"),
                    "'" + spec.authorizationUrl() + "'", "'" + spec.tokenUrl() + "'",
                    formatNodeScopes(spec.scopes()), refreshArg);
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "'" + spec.openIdConnectUrl() + "'",
                    oauthArg(envNames, "clientId"), oauthArg(envNames, "clientSecret"),
                    oauthArg(envNames, "redirectUri"), "[]");
        }
        return List.of();
    }

    /**
     * F857: there is no per-call authenticator, so an Options object carries no
     * {@code auth} field.
     */
    @Override
    protected void injectAuthFieldContext(
            final CodegenOperation op, final Map<String, Object> context) {
        context.put("hasAuthField", false);
    }

    /**
     * An operation with no query, header, form or cookie parameter has no Options object:
     * its per-call timeout, headers, retries and signal travel on the trailing
     * {@code RequestOptions} argument that every operation takes.
     */
    @Override
    @Nullable
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        if (optionsParams.isEmpty()) {
            return null;
        }
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("dataType", uploadType(p));
            param.put("required", p.required);
            param.put("isNullable", Boolean.TRUE.equals(p.isNullable));
            // Thread the spec parameter's deprecated flag into the Options field so
            // the generated property carries an `@deprecated` JSDoc tag, mirroring
            // the model-property deprecation idiom in models/model.mustache.
            param.put("deprecated", p.isDeprecated);
            // Thread the spec parameter description into the Options field so the
            // generated property carries a JSDoc comment, matching the per-param
            // documentation the other SDKs expose.
            if (p.description != null && !p.description.isBlank()) {
                param.put("description", p.description.strip());
            }
            params.add(param);
        }

        // Collect model type imports — only PascalCase identifiers are real model
        // types; inline TypeScript types like "{ [key: string]: unknown }" must be
        // excluded.
        final Set<String> modelTypes = new LinkedHashSet<>();
        for (final CodegenParameter p : optionsParams) {
            if (!p.isPrimitiveType
                    && !p.isArray
                    && !p.isMap
                    && p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)
                    && p.baseType.matches("^[A-Z]\\w*$")) {
                modelTypes.add(p.baseType);
            }
            // A $ref to a top-level enum carries its enum type name in dataType
            // (baseType is null); its generated type must be imported.
            if (p.isEnumRef
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)
                    && p.dataType.matches("^[A-Z]\\w*$")) {
                modelTypes.add(p.dataType);
            }
            // An array of `format: date` items has the global `Date` as its
            // item type, which is not a generated model and must not be imported.
            if ((p.isArray || p.isMap)
                    && p.items != null
                    && p.items.baseType != null
                    && !"Date".equals(p.items.baseType)
                    && !languageSpecificPrimitives.contains(p.items.baseType)
                    && p.items.baseType.matches("^[A-Z]\\w*$")) {
                modelTypes.add(p.items.baseType);
            }
            // A `content`-encoded parameter has no baseType; its schema's
            // model type is only in dataType.
            if (p.baseType == null
                    && p.dataType != null
                    && !"Date".equals(p.dataType)
                    && !languageSpecificPrimitives.contains(p.dataType)
                    && p.dataType.matches("^[A-Z]\\w*$")) {
                modelTypes.add(p.dataType);
            }
        }
        final boolean usesTemporal =
                optionsParams.stream()
                        .anyMatch(
                                p ->
                                        (p.dataType != null && p.dataType.contains("Temporal."))
                                                || (p.items != null
                                                        && p.items.dataType != null
                                                        && p.items.dataType.contains("Temporal.")));

        // Branded and decimal parameter types come from the brand module.
        final Set<String> brands = new java.util.TreeSet<>();
        for (final CodegenParameter p : optionsParams) {
            if (p.dataType != null && BRAND_TYPES.contains(p.dataType)) {
                brands.add(p.dataType);
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("brandImports", String.join(", ", brands));
        context.put("hasBrandImports", !brands.isEmpty());
        context.put("className", className);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("modelImports", new ArrayList<>(modelTypes));
        context.put("hasModelImports", !modelTypes.isEmpty() || usesTemporal || !brands.isEmpty());
        context.put("hasTemporalImport", usesTemporal);
        // F857: no per-call authenticator. An operation uses the credential of the
        // scheme its spec names, so an Options object never carries an `auth` field.
        injectAuthFieldContext(op, context);
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.KEBAB_CASE.apply(optionsClassName);
        return Path.of(outputFolder, "src", "api", "options", fileName + ".ts").toString();
    }

    /** {@inheritDoc} */
    @Override
    public void emitBarrelFiles(List<Map<String, String>> optionsFiles) {
        final List<Map<String, String>> exports = new ArrayList<>();
        for (final Map<String, String> meta : optionsFiles) {
            final String className =
                    Objects.requireNonNull(meta.get("optionsClassName"));
            final Map<String, String> export = new HashMap<>();
            export.put("fileName", NamingConvention.KEBAB_CASE.apply(className));
            exports.add(export);
        }
        final Map<String, Object> context = new HashMap<>();
        context.put("exports", exports);
        final String content = renderOptionsTemplate("api/options_index.mustache", context);
        final String barrelPath =
                Path.of(outputFolder, "src", "api", "options", "index.ts").toString();
        writeFile(barrelPath, content);
        postProcessFile(Path.of(barrelPath).toFile(), "source");
    }

    /**
     * Sets each model's import flags from exactly what its template emits, once
     * every model is known: {@code hasTypeDecorator} and
     * {@code hasTransformDecorator} when a property carries that decorator, and
     * a brand import when a property's type names the brand as a whole word
     * (a model named {@code *Email} does not use the {@code Email} brand). An
     * import the file never uses fails the SDK's own lint.
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();
                if (model == null || model.vars == null) {
                    continue;
                }
                final boolean nestsModel = model.vars.stream().anyMatch(BetterNodeCodegen::nestsModel);
                modelMap.put("hasTypeDecorator", model.vars.stream().anyMatch(this::needsTypeDecorator));
                modelMap.put(
                        "hasWireRename",
                        model.vars.stream()
                                .anyMatch(v -> Boolean.TRUE.equals(v.vendorExtensions.get("isRenamedWire"))));
                modelMap.put(
                        "hasPasswordField",
                        model.vars.stream()
                                .anyMatch(v -> Boolean.TRUE.equals(v.vendorExtensions.get("isPasswordFormat"))));
                modelMap.put(
                        "hasTransformDecorator",
                        nestsModel || model.vars.stream().anyMatch(BetterNodeCodegen::hasTransform));
                final List<String> serializerImports = new ArrayList<>();
                if (Boolean.TRUE.equals(modelMap.get("hasDurationImport"))) {
                    serializerImports.add("durationFromProtoJson");
                    serializerImports.add("durationToProtoJson");
                }
                if (model.vars.stream()
                        .anyMatch(
                                v ->
                                        Boolean.TRUE.equals(v.vendorExtensions.get("isTimeFormat"))
                                                || Boolean.TRUE.equals(v.vendorExtensions.get("isDurationFormat")))) {
                    serializerImports.add("isTemporalValue");
                }
                if (Boolean.TRUE.equals(modelMap.get("hasBufferImport"))
                        && model.vars.stream()
                                .anyMatch(
                                v ->
                                        v.dataType != null
                                                && v.dataType.contains("Buffer")
                                                && !v.isBinary
                                                && !(v.items != null && v.items.isBinary))) {
                    serializerImports.add("decodeBase64");
                }
                if (nestsModel) {
                    serializerImports.add("reattachAdditionalProperties");
                }
                if (model.vendorExtensions.get("additionalPropertiesValue") != null
                        || model.vars.stream().anyMatch(BetterNodeCodegen::holdsUnion)) {
                    serializerImports.add("ObjectSerializer");
                }
                if (model.vars.stream().anyMatch(BetterNodeCodegen::isPlainNumberDecimal)) {
                    serializerImports.add("decodePlainNumber");
                }
                if (model.vars.stream()
                        .anyMatch(v -> Boolean.TRUE.equals(v.vendorExtensions.get("isPlainNumberDecimalArray")))) {
                    serializerImports.add("decodePlainNumberArray");
                }
                if (model.vars.stream()
                        .anyMatch(v -> Boolean.TRUE.equals(v.vendorExtensions.get("isStringDecimal")))) {
                    serializerImports.add("decodeStringDecimal");
                }
                if (model.vars.stream().anyMatch(BetterNodeCodegen::isPlainDate)) {
                    serializerImports.add("plainDate");
                }
                if (model.vars.stream().anyMatch(BetterNodeCodegen::holdsIpAddress)) {
                    serializerImports.add("isIpv4Address");
                    serializerImports.add("isIpv6Address");
                }
                modelMap.put("serializerImports", String.join(", ", serializerImports));
                modelMap.put("hasSerializerImports", !serializerImports.isEmpty());
                modelMap.put("hasUuidImport", namesType(model, "UUID"));
                modelMap.put("hasUriImport", namesType(model, "URI"));
                modelMap.put("hasEmailImport", namesType(model, "Email"));
                modelMap.put("hasDecimalImport", namesType(model, "Decimal"));
            }
        }
        return result;
    }

    /** Whether the property is a number-shaped decimal carried as a decimal.js Decimal. */
    private static boolean isPlainNumberDecimal(CodegenProperty prop) {
        return Boolean.TRUE.equals(prop.vendorExtensions.get("isPlainNumberDecimal"));
    }

    /**
     * Whether a scalar schema is a number-shaped decimal: a {@code type: number} with no
     * format or with {@code format: decimal}, which this SDK reads as a {@code Decimal}.
     */
    private static boolean isNumberDecimalLeaf(@Nullable CodegenProperty leaf) {
        return leaf != null
                && !leaf.isContainer
                && (leaf.isNumber || "number".equals(leaf.openApiType))
                && (leaf.dataFormat == null || "decimal".equals(leaf.dataFormat))
                && !"string".equals(leaf.openApiType);
    }

    /** Whether a scalar schema is a {@code type: string} with {@code format: decimal}. */
    private static boolean isStringDecimalLeaf(@Nullable CodegenProperty leaf) {
        return leaf != null
                && !leaf.isContainer
                && "decimal".equals(leaf.dataFormat)
                && "string".equals(leaf.openApiType);
    }

    /**
     * Types a decimal response body, bare or the leaf of an array or map, as {@code Decimal}
     * and flags the leaf {@code isDecimalLeaf} (and {@code isStringDecimalLeaf} for the
     * string-shaped form), so the response deserializer reads it through
     * {@code ObjectSerializer.deserializeDecimal} from its exact text rather than casting the
     * double {@code JSON.parse} produced.
     *
     * @param op the operation whose return type to mark
     */
    static void markDecimalReturn(final CodegenOperation op) {
        if (op.returnProperty == null || op.returnType == null) {
            return;
        }
        CodegenProperty leaf = op.returnProperty;
        while (leaf != null && leaf.isContainer) {
            leaf = leaf.items;
        }
        final boolean number = isNumberDecimalLeaf(leaf);
        final boolean string = isStringDecimalLeaf(leaf);
        if (leaf == null || !(number || string)) {
            return;
        }
        leaf.vendorExtensions.put("isDecimalLeaf", true);
        leaf.vendorExtensions.put("isStringDecimalLeaf", string);
        final String scalar = number ? "number" : "string";
        if (!op.returnProperty.isContainer) {
            op.returnType = "Decimal";
            op.returnBaseType = "Decimal";
        } else {
            op.returnType = op.returnType.replace(scalar, "Decimal");
            if (scalar.equals(op.returnBaseType)) {
                op.returnBaseType = "Decimal";
            }
        }
    }

    /** Whether the property is a {@code format: ipv4} or {@code ipv6} string, or an array of them. */
    private static boolean holdsIpAddress(CodegenProperty prop) {
        return Boolean.TRUE.equals(prop.vendorExtensions.get("isIpv4"))
                || Boolean.TRUE.equals(prop.vendorExtensions.get("isIpv6"))
                || Boolean.TRUE.equals(prop.vendorExtensions.get("isIpv4Items"))
                || Boolean.TRUE.equals(prop.vendorExtensions.get("isIpv6Items"));
    }

    /** Whether the property is a {@code format: date}, or an array of them, held as a plain string. */
    private static boolean isPlainDate(CodegenProperty prop) {
        return !prop.isDateTime
                && (prop.isDate || (prop.isArray && prop.items != null && prop.items.isDate));
    }

    /**
     * A {@code format: date} property is a plain string and needs no decorator;
     * every other property is decided by the shared rule.
     */
    @Override
    protected boolean needsTypeDecorator(CodegenProperty prop) {
        if (isPlainDate(prop) || holdsUnion(prop)) {
            return false;
        }
        return super.needsTypeDecorator(prop);
    }

    /**
     * Whether the property's type is a oneOf or anyOf wrapper class, whose wire value
     * may be a scalar or an array: it is decoded through {@code ObjectSerializer}
     * rather than class-transformer's {@code @Type} (#98).
     */
    @SuppressWarnings("unchecked")
    private static boolean holdsUnion(CodegenProperty prop) {
        final Object decorators = prop.vendorExtensions.get("prop");
        return decorators instanceof Map
                && Boolean.TRUE.equals(((Map<String, Object>) decorators).get("complexTypeIsUnion"));
    }

    /** Whether the model template emits an {@code @Transform} for the property. */
    private static boolean hasTransform(CodegenProperty prop) {
        final Map<String, Object> ext = prop.vendorExtensions;
        return holdsUnion(prop)
                || Boolean.TRUE.equals(ext.get("isTimeFormat"))
                || Boolean.TRUE.equals(ext.get("isDurationFormat"))
                || Boolean.TRUE.equals(ext.get("isPlainNumberDecimal"))
                || Boolean.TRUE.equals(ext.get("isPlainNumberDecimalArray"))
                || Boolean.TRUE.equals(ext.get("isStringDecimal"))
                || prop.isByteArray
                || prop.isDateTime
                || (prop.isArray && prop.items != null && prop.items.isByteArray);
    }

    /**
     * Whether the property holds a generated model, directly or as array
     * items. The model template gives such a property a {@code @Transform}
     * that re-attaches the free-form keys of a nested model declaring
     * {@code additionalProperties}, which class-transformer would drop.
     */
    private static boolean nestsModel(CodegenProperty prop) {
        if (holdsUnion(prop)) {
            return false;
        }
        if (prop.isArray) {
            return prop.items != null && prop.items.isModel;
        }
        return prop.isModel && !prop.isMap;
    }

    /** Whether any property's type names {@code type} as a whole word. */
    private static boolean namesType(CodegenModel model, String type) {
        final java.util.regex.Pattern word =
                java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(type) + "\\b");
        return model.vars.stream()
                .anyMatch(p -> p.dataType != null && word.matcher(p.dataType).find());
    }

    /** {@inheritDoc} */
    @Override
    protected boolean shouldApplyTypeDecorators() {
        return true;
    }

    /**
     * 4.8 — Flag any model that has at least one {@code Temporal.PlainTime}
     * or {@code Temporal.Duration} property so the model template emits the
     * {@code import { Temporal } from 'temporal-polyfill'} line. The Gap 14
     * {@code type:} substring matcher matches "Temporal." against the
     * property's {@code dataType}, so a single flag covers both PlainTime
     * and Duration.
     */
    @Override
    protected Map<String, String> getModelContextFlags() {
        return Map.of(
                "type:Temporal.", "hasTemporalImport",
                "type:Temporal.Duration", "hasDurationImport",
                "type:UUID", "hasUuidImport",
                "type:URI", "hasUriImport",
                "type:Email", "hasEmailImport",
                "type:Decimal", "hasDecimalImport",
                "type:Buffer", "hasBufferImport");
    }

    /**
     * 4.8 — Set per-property {@code isTimeFormat} / {@code isDurationFormat}
     * boolean flags on {@code CodegenProperty.vendorExtensions} so the model
     * template can branch on the OAS format without doing string equality on
     * {@code dataType}. Upstream populates {@code CodegenProperty.dataFormat}
     * directly from the schema's {@code format:}, so this is the simplest way
     * to surface it to Mustache (which lacks string equality).
     */
    @Override
    public void postProcessModelProperty(
            org.openapitools.codegen.CodegenModel model,
            org.openapitools.codegen.CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if ("time".equals(property.dataFormat)) {
            property.vendorExtensions.put("isTimeFormat", true);
        }
        if ("duration".equals(property.dataFormat)) {
            property.vendorExtensions.put("isDurationFormat", true);
        }
        if (property.name != null && property.baseName != null && !property.name.equals(property.baseName)) {
            property.vendorExtensions.put("isRenamedWire", true);
        }
        if ("password".equals(property.dataFormat)) {
            property.vendorExtensions.put("isPasswordFormat", true);
        }
        // 2.3 — revert `uri-reference` / `uri-template` back to plain `string`;
        // only `format: uri` retains the branded URI alias.
        keepStringForUriSubformats(property, "string");
        // 2.4 — `format: email` becomes a branded Email alias.
        if ("email".equals(property.dataFormat)) {
            property.dataType = "Email";
            property.datatypeWithEnum = "Email";
        }
        // 2.5 — `type: number` (no numeric format or `format: decimal`) and
        // `type: string, format: decimal` become decimal.js's `Decimal`. The
        // `number` schema type defaults to the `number` JS primitive at
        // the typeMapping layer to avoid collateral damage with `long`
        // and `integer`; we override at the property level here.
        if (property.isArray && isNumberDecimalLeaf(property.items)) {
            // An array of number-shaped decimals is an Array<Decimal>: each element is
            // read from its exact source text (decodePlainNumberArray), never a double.
            property.items.dataType = "Decimal";
            property.items.datatypeWithEnum = "Decimal";
            property.dataType = "Array<Decimal>";
            property.datatypeWithEnum = "Array<Decimal>";
            property.vendorExtensions.put("isPlainNumberDecimalArray", true);
            return;
        }
        final boolean isPlainNumber =
                !property.isContainer
                        && (property.isNumber || "number".equals(property.openApiType))
                        && (property.dataFormat == null || "decimal".equals(property.dataFormat))
                        && !"string".equals(property.openApiType);
        final boolean isStringDecimal =
                !property.isContainer && "decimal".equals(property.dataFormat) && !isPlainNumber;
        if (isPlainNumber || isStringDecimal) {
            property.dataType = "Decimal";
            property.datatypeWithEnum = "Decimal";
            property.vendorExtensions.put("isDecimalFormat", true);
            if (isPlainNumber) {
                // A bare `type: number` is a JSON number on the wire: decode keeps its
                // exact text and rejects a quoted string.
                property.vendorExtensions.put("isPlainNumberDecimal", true);
            } else {
                // A `type: string` decimal is a JSON string on the wire: decode keeps
                // its text and rejects a bare number; encode writes it quoted.
                property.vendorExtensions.put("isStringDecimal", true);
            }
            // A numeric schema default becomes a Decimal built from its exact
            // text, so no digit passes through a double.
            if (property.defaultValue != null && !property.defaultValue.isEmpty()) {
                final String text =
                        property.defaultValue.charAt(0) == getQuoteChar()
                                ? property.defaultValue
                                : toStringLiteral(property.defaultValue);
                property.defaultValue = "new Decimal(" + text + ")";
            }
        }
    }

    /**
     * Enables Gap K so polymorphic subtypes auto-emit their
     * discriminator field on serialization. The TS model template
     * honours {@code defaultValue} on properties so the discriminator
     * renders as e.g. {@code foodType!: string = 'dry';}.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /**
     * The TS constructor block iterates {@code requiredVars} to
     * throw on missing required fields. With the discriminator
     * defaulted in the field initialiser, the runtime check is
     * redundant, so demote it out of {@code requiredVars}.
     */
    @Override
    protected boolean demotesDiscriminatorFromRequiredVars() {
        return true;
    }

    /**
     * Returns a single-quoted TypeScript string literal for the
     * discriminator default value, matching the rest of the model
     * template's string conventions.
     */
    @Override
    protected String formatDiscriminatorDefaultValue(String mappingName) {
        return "'" + mappingName + "'";
    }

}

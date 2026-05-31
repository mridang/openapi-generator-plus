package io.github.mridang.codegen.generators.node;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.FileContentFixup;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterNodeCodegen.class);

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
        typeMapping.put("number", "number");
        typeMapping.put("short", "number");
        typeMapping.put("boolean", "boolean");
        typeMapping.put("string", "string");
        typeMapping.put("decimal", "string");
        // Map OpenAPI date-time formats to the JS Date type so
        // class-transformer's @Type(() => Date) decorator can
        // hydrate ISO-8601 strings into actual Date instances
        // round-trip (the previous `string` mapping forced callers
        // to parse manually and produced wire/runtime drift).
        typeMapping.put("date", "Date");
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
        typeMapping.put("URI", "string");
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
                                // 4.8 — Temporal.* types come from `temporal-polyfill`,
                                // not from generated models, so the model-import filter
                                // must not treat them as model classes.
                                "Temporal.PlainTime", "Temporal.Duration"));

        reservedWords = loadReservedWords("/reserved-words/node.txt");

        additionalProperties.put(CodegenConstants.MODEL_PROPERTY_NAMING, "original");
        additionalProperties.put("importFileExtension", ".js");

        setEnumUnknownDefaultCase(true);

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
            "npm install --ignore-scripts", "npx prettier --write .", "rm -rf node_modules"
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
        return List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("api_client.mustache", "src", "api-client.ts"),
            new SupportingFileSpec("default_api_client.mustache", "src", "default-api-client.ts"),
            new SupportingFileSpec("api_response.mustache", "src", "api-response.ts"),
            new SupportingFileSpec("api_result.mustache", "src", "api-result.ts"),
            new SupportingFileSpec("configuration.mustache", "src", "configuration.ts"),
            new SupportingFileSpec("transport_options.mustache", "src", "transport-options.ts"),
            new SupportingFileSpec("server_configuration.mustache", "src", "server-configuration.ts"),
            new SupportingFileSpec("servers.mustache", "src", "servers.ts"),
            new SupportingFileSpec("api_error.mustache", "src", "api-error.ts"),
            new SupportingFileSpec("base_api.mustache", "src/api", "base-api.ts"),
            new SupportingFileSpec("errors/index.mustache", "src/errors", "index.ts"),
            new SupportingFileSpec("errors/client-error.mustache", "src/errors", "client-error.ts"),
            new SupportingFileSpec("errors/server-error.mustache", "src/errors", "server-error.ts"),
            new SupportingFileSpec("errors/bad-request-error.mustache", "src/errors", "bad-request-error.ts"),
            new SupportingFileSpec("errors/unauthorized-error.mustache", "src/errors", "unauthorized-error.ts"),
            new SupportingFileSpec("errors/forbidden-error.mustache", "src/errors", "forbidden-error.ts"),
            new SupportingFileSpec("errors/not-found-error.mustache", "src/errors", "not-found-error.ts"),
            new SupportingFileSpec("errors/conflict-error.mustache", "src/errors", "conflict-error.ts"),
            new SupportingFileSpec("errors/unprocessable-entity-error.mustache", "src/errors", "unprocessable-entity-error.ts"),
            new SupportingFileSpec("errors/internal-server-error.mustache", "src/errors", "internal-server-error.ts"),
            new SupportingFileSpec("brand.mustache", "src", "brand.ts"),
            new SupportingFileSpec("object_serializer.mustache", "src", "object-serializer.ts"),
            new SupportingFileSpec("value_serializer.mustache", "src", "value-serializer.ts"),
            new SupportingFileSpec("header_selector.mustache", "src", "header-selector.ts"),
            new SupportingFileSpec("trace_context_util.mustache", "src", "trace-context-util.ts"),
            new SupportingFileSpec("tsconfig.mustache", "", "tsconfig.json"),
            new SupportingFileSpec("models/index.mustache", "src/models", "index.ts"),
            new SupportingFileSpec("api/index.mustache", "src/api", "index.ts"),
            new SupportingFileSpec("package.mustache", "", "package.json"),
            new SupportingFileSpec("prettierrc.mustache", "", ".prettierrc.json"),
            new SupportingFileSpec("prettierignore.mustache", "", ".prettierignore"),
            new SupportingFileSpec("eslint_config.mustache", "", "eslint.config.js"),
            new SupportingFileSpec("authenticator.mustache", "src/auth", "authenticator.ts"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore")
        );
    }

    @Override
    public void processOpts() {
        super.processOpts();
        final String packageVersion = getPropertyOrDefault("packageVersion", "1.0.0");
        additionalProperties.put("packageVersion", packageVersion);
        additionalProperties.put(
                "userAgentDefault", "openapi-typescript-client/" + packageVersion + " (node)");

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

        if (generateTests) {
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
                            "test/default-api-client-unit.test.mustache",
                            "test",
                            "default-api-client-unit.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport-options.test.mustache",
                            "test",
                            "transport-options.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object-serializer.test.mustache",
                            "test",
                            "object-serializer.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value-serializer.test.mustache",
                            "test",
                            "value-serializer.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace-context-util.test.mustache",
                            "test",
                            "trace-context-util.test.ts"));
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
        if (ModelUtils.isStringSchema(resolved)
                && resolved.getDefault() != null
                && resolved.getEnum() != null
                && !resolved.getEnum().isEmpty()) {
            final String val = resolved.getDefault().toString();
            return getQuoteChar() + val + getQuoteChar();
        }
        return null;
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
        return super.toEnumVarName(value, datatype);
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
            final String memberName = toEnumVarName(raw, prop.dataType);
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
            final List<CodegenOperation> ops =
                    (List<CodegenOperation>) operations.get("operation");
            if (ops != null) {
                boolean hasEnums = false;
                for (final CodegenOperation op : ops) {
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
                }
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
        }
        return objs;
    }

    /** {@inheritDoc} */
    @Override
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        return List.of(
                new OAuthTestFileSpec("test/basic-authenticator.test.mustache", "test", "basic-authenticator.test.ts", OAuthTestCondition.BASIC),
                new OAuthTestFileSpec("test/oauth2-token-manager.test.mustache", "test", "oauth2-token-manager.test.ts", OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec("test/oauth2-auth-code-authenticator.test.mustache", "test", "oauth2-auth-code-authenticator.test.ts", OAuthTestCondition.AUTH_CODE),
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

    /**
     * Pattern matching block-comment lines of the form
     * {@literal /*} eslint-disable {@literal *}{@literal /} (including the trailing
     * newline) — a Mustache artefact emitted in every generated TS file that
     * conflicts with the project's own eslint configuration.
     */
    private static final Pattern ESLINT_DISABLE_PATTERN =
            Pattern.compile("^/\\*\\s*eslint-disable\\s*\\*/\\s*\\R", Pattern.MULTILINE);

    /**
     * Declares the regex-based fixup for {@code .ts} files: strip eslint-disable
     * block-comment lines. Trailing-blank-line trimming is handled universally
     * by the base class.
     */
    @Override
    protected List<FileContentFixup> getFileContentFixups() {
        return List.of(new FileContentFixup(".ts", ESLINT_DISABLE_PATTERN, ""));
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
        ctx.put("imports", buildNodeImports(spec));
        final List<Map<String, String>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, String> param = new HashMap<>();
            param.put("name", name);
            param.put("type", "string");
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", buildNodeSuperArgs(spec));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    private List<Map<String, String>> buildNodeImports(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("BasicAuthenticator", "./basic-authenticator"));
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("BearerAuthenticator", "./bearer-authenticator"));
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
                    "./oauth2-auth-code-authenticator"));
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of(imp("OAuth2ImplicitAuthenticator",
                    "./oauth2-implicit-authenticator"));
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

    private List<String> buildNodeSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "'" + spec.keyParamName() + "'", "apiKey",
                    "ApiKeyLocation." + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId", "clientSecret",
                    "'" + spec.tokenUrl() + "'", formatNodeScopes(spec.scopes()));
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "null";
            return List.of("host", "clientId", "clientSecret",
                    "'" + spec.tokenUrl() + "'", "username", "password",
                    formatNodeScopes(spec.scopes()), refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "null";
            return List.of("host", "clientId", "clientSecret",
                    "'" + spec.authorizationUrl() + "'", "'" + spec.tokenUrl() + "'",
                    "redirectUri", formatNodeScopes(spec.scopes()), refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId",
                    "'" + spec.authorizationUrl() + "'", formatNodeScopes(spec.scopes()));
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "'" + spec.openIdConnectUrl() + "'",
                    "clientId", "clientSecret", "redirectUri", "[]");
        }
        return List.of();
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            param.put("isNullable", Boolean.TRUE.equals(p.isNullable));
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
            if ((p.isArray || p.isMap)
                    && p.items != null
                    && p.items.baseType != null
                    && !languageSpecificPrimitives.contains(p.items.baseType)
                    && p.items.baseType.matches("^[A-Z]\\w*$")) {
                modelTypes.add(p.items.baseType);
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("modelImports", new ArrayList<>(modelTypes));
        context.put("hasModelImports", !modelTypes.isEmpty());
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
        return Map.of("type:Temporal.", "hasTemporalImport");
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

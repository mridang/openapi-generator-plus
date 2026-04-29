package io.github.mridang.codegen.generators.node;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
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
public class BetterNodeCodegen extends AbstractBetterCodegen {

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
        typeMapping.put("date", "string");
        typeMapping.put("DateTime", "string");
        typeMapping.put("binary", "Buffer");
        typeMapping.put("File", "Buffer");
        typeMapping.put("file", "Buffer");
        typeMapping.put("ByteArray", "string");
        typeMapping.put("UUID", "string");
        typeMapping.put("URI", "string");
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
                                "void", "undefined", "null", "Array", "Set", "Buffer"));

        reservedWords = loadReservedWords("/reserved-words/node.txt");

        additionalProperties.put(CodegenConstants.MODEL_PROPERTY_NAMING, "original");
        additionalProperties.put("importFileExtension", ".js");

        setEnumUnknownDefaultCase(true);
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
        return "node:24-slim";
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

    /**
     * Resolves user-supplied codegen options and registers all
     * supporting files for the TypeScript package structure.
     * Sets up the output layout including models, API classes,
     * exceptions, auth, serialization, and configuration modules.
     * Also registers test scaffolding files when test generation
     * is enabled.
     */
    @Override
    public void processOpts() {
        super.processOpts();
        additionalProperties.put("userAgentDefault", "openapi-typescript-client/1.0.0 (node)");

        this.apiPackage = "api";

        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("api_client.mustache", "src", "api-client.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", "src", "default-api-client.ts"));
        supportingFiles.add(new SupportingFile("api_response.mustache", "src", "api-response.ts"));
        supportingFiles.add(new SupportingFile("api_result.mustache", "src", "api-result.ts"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", "src", "configuration.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options.mustache", "src", "transport-options.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache", "src", "server-configuration.ts"));
        supportingFiles.add(new SupportingFile("servers.mustache", "src", "servers.ts"));
        supportingFiles.add(new SupportingFile("api_error.mustache", "src", "api-error.ts"));
        supportingFiles.add(new SupportingFile("base_api.mustache", "src/api", "base-api.ts"));

        supportingFiles.add(
                new SupportingFile(
                        "exceptions/index.mustache", "src/exceptions", "index.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/client-error.mustache", "src/exceptions", "client-error.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/server-error.mustache", "src/exceptions", "server-error.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/bad-request-error.mustache",
                        "src/exceptions",
                        "bad-request-error.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/unauthorized-error.mustache",
                        "src/exceptions",
                        "unauthorized-error.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/forbidden-error.mustache",
                        "src/exceptions",
                        "forbidden-error.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/not-found-error.mustache",
                        "src/exceptions",
                        "not-found-error.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/conflict-error.mustache",
                        "src/exceptions",
                        "conflict-error.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/unprocessable-entity-error.mustache",
                        "src/exceptions",
                        "unprocessable-entity-error.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/internal-server-error.mustache",
                        "src/exceptions",
                        "internal-server-error.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", "src", "object-serializer.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "value_serializer.mustache", "src", "value-serializer.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", "src", "header-selector.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", "src", "trace-context-util.ts"));
        supportingFiles.add(new SupportingFile("tsconfig.mustache", "", "tsconfig.json"));
        supportingFiles.add(
                new SupportingFile("models/index.mustache", "src/models", "index.ts"));
        supportingFiles.add(
                new SupportingFile("api/index.mustache", "src/api", "index.ts"));
        supportingFiles.add(new SupportingFile("package.mustache", "", "package.json"));
        supportingFiles.add(new SupportingFile("prettierrc.mustache", "", "prettier.config.mjs"));
        supportingFiles.add(new SupportingFile("prettierignore.mustache", "", ".prettierignore"));
        supportingFiles.add(
                new SupportingFile("eslint_config.mustache", "", "eslint.config.mjs"));
        supportingFiles.add(
                new SupportingFile(
                        "authenticator.mustache", "src/auth", "authenticator.ts"));
        final String clientClassName =
                Objects.requireNonNull(
                        (String) additionalProperties.get("clientClassName"));
        final String clientClassFile = getFilenameCasing().apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", "src", clientClassFile + ".ts"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/jest.config.mjs", "", "jest.config.mjs"));
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
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

    /**
     * Returns null for all schema types because TypeScript
     * variables do not need explicit default value expressions
     * in the generated model constructors.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        return null;
    }

    /**
     * Overrides the base class to preserve original JSON property
     * names for serialization fidelity. TypeScript models use
     * the exact property names from the schema. Cannot be
     * standardized because other languages apply casing.
     */
    @Override
    public String toVarName(String name) {
        return sanitizeName(name);
    }

    /**
     * Overrides the base class to additionally check for
     * collisions with TypeScript primitives ({@code number},
     * {@code string}) and prefix with "Model" when a collision
     * occurs.
     */
    @Override
    public String toModelName(String name) {
        final String result = super.toModelName(name);
        if (languageSpecificPrimitives.contains(result)) {
            return "Model" + result;
        }
        return result;
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

    /**
     * Overrides the base class to check {@code enumNameMapping}
     * first, then resolve symbol names with PascalCase. Cannot
     * be standardized because the enumNameMapping lookup and
     * PascalCase casing differ from PHP's handling.
     */
    @Override
    public String toEnumVarName(String value, String datatype) {
        return Optional.ofNullable(enumNameMapping.get(value))
                .orElseGet(
                        () ->
                                Optional.ofNullable(getSymbolName(value))
                                        .map(s -> getEnumCasing().apply(s))
                                        .orElseGet(() -> super.toEnumVarName(value, datatype)));
    }

    /**
     * Overrides the base class to build TypeScript import
     * metadata ({@code tsImports}) and determine type decorator
     * flags for runtime deserialization. Cannot be standardized
     * because TypeScript's import/decorator system is unique.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);

        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();

            final List<Map<String, String>> tsImports = new ArrayList<>();
            for (final String importName : model.imports) {
                if (!languageSpecificPrimitives.contains(importName)
                        && !typeMapping.containsValue(importName)) {
                    final Map<String, String> tsImport = new HashMap<>();
                    tsImport.put("classname", importName);
                    tsImport.put("filename", toModelFilename(importName));
                    tsImports.add(tsImport);
                }
            }
            modelMap.put("tsImports", tsImports);
            modelMap.put("hasImports", !tsImports.isEmpty());

            boolean hasTypeDecorator = false;
            for (final CodegenProperty var : model.vars) {
                if (needsTypeDecorator(var)) {
                    hasTypeDecorator = true;
                    break;
                }
            }
            modelMap.put("hasTypeDecorator", hasTypeDecorator);
        }
        return result;
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

        // Remove model imports that are only used by Options params
        @SuppressWarnings("unchecked")
        final Map<String, Object> operations2 = (Map<String, Object>) objs.get("operations");
        if (operations2 != null) {
            @SuppressWarnings("unchecked")
            final List<CodegenOperation> ops2 =
                    (List<CodegenOperation>) operations2.get("operation");
            if (ops2 != null) {
                final Set<String> optionsOnlyModels = new HashSet<>();
                final Set<String> nonOptionsModels = new HashSet<>();
                for (final CodegenOperation op : ops2) {
                    final List<CodegenParameter> optParams = collectOptionsParams(op);
                    final Set<String> optParamNames = new HashSet<>();
                    for (final CodegenParameter p : optParams) {
                        optParamNames.add(p.paramName);
                        addModelBaseType(optionsOnlyModels, p);
                    }
                    if (op.allParams != null) {
                        for (final CodegenParameter p : op.allParams) {
                            if (!optParamNames.contains(p.paramName)) {
                                addModelBaseType(nonOptionsModels, p);
                            }
                        }
                    }
                    if (op.returnBaseType != null
                            && !languageSpecificPrimitives.contains(op.returnBaseType)) {
                        nonOptionsModels.add(op.returnBaseType);
                    }
                }
                optionsOnlyModels.removeAll(nonOptionsModels);
                if (!optionsOnlyModels.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    final List<Map<String, String>> optImports =
                            (List<Map<String, String>>) objs.get("imports");
                    if (optImports != null) {
                        // The framework inconsistently uses "className" or "classname"
                        // as the import map key, so we check both.
                        optImports.removeIf(
                                imp -> {
                                    final String cn =
                                            imp.getOrDefault(
                                                    "className",
                                                    imp.getOrDefault("classname", ""));
                                    return optionsOnlyModels.contains(cn);
                                });
                    }
                }
            }
        }

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

    /**
     * Registers supporting files for each authentication scheme
     * present in the OpenAPI spec. Files are placed under the
     * src/auth/ subdirectory with kebab-case filenames matching
     * TypeScript naming conventions.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String authFolder = Path.of("src", "auth").toString();
        final String oauthFolder = Path.of(authFolder, "oauth").toString();

        supportingFiles.add(
                new SupportingFile(
                        "auth/base-authenticator.mustache",
                        authFolder,
                        "base-authenticator.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http-aware-authenticator.mustache",
                        authFolder,
                        "http-aware-authenticator.ts"));

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic-authenticator.mustache",
                            authFolder,
                            "basic-authenticator.ts"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer-authenticator.mustache",
                            authFolder,
                            "bearer-authenticator.ts"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api-key-authenticator.mustache",
                            authFolder,
                            "api-key-authenticator.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api-key-location.mustache",
                            authFolder,
                            "api-key-location.ts"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2-token-manager.mustache",
                            oauthFolder,
                            "oauth2-token-manager.ts"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2-client-credentials-authenticator.mustache",
                            oauthFolder,
                            "oauth2-client-credentials-authenticator.ts"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2-password-authenticator.mustache",
                            oauthFolder,
                            "oauth2-password-authenticator.ts"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2-auth-code-authenticator.mustache",
                            oauthFolder,
                            "oauth2-auth-code-authenticator.ts"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2-implicit-authenticator.mustache",
                            oauthFolder,
                            "oauth2-implicit-authenticator.ts"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/openid-connect-authenticator.mustache",
                            oauthFolder,
                            "openid-connect-authenticator.ts"));
        }
    }

    /**
     * Overrides the base class to strip eslint-disable comments
     * and trailing blank lines from generated TypeScript files.
     * Cannot be standardized because this is a Node/TS formatting
     * artifact that doesn't affect other languages.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null || !file.getName().endsWith(".ts")) {
            return;
        }
        try {
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            final List<String> result = new ArrayList<>(lines.size());
            boolean changed = false;

            for (final String line : lines) {
                if (line.equals("/* eslint-disable */")) {
                    changed = true;
                    continue;
                }
                result.add(line);
            }

            while (!result.isEmpty() && result.get(result.size() - 1).trim().isEmpty()) {
                result.remove(result.size() - 1);
                changed = true;
            }

            if (changed) {
                Files.write(file.toPath(), result, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to post-process {}: {}", file.getName(), e.getMessage());
        }
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
    protected void writeOptionsBarrelFiles(List<Map<String, String>> optionsFiles) {
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
     * Returns whether a model property needs a runtime type
     * decorator for correct deserialization. Complex types
     * (non-primitive, non-enum, non-freeform) and arrays of
     * complex types require type metadata so the serializer
     * can instantiate the correct class.
     */
    private static boolean needsTypeDecorator(CodegenProperty prop) {
        if (!prop.isPrimitiveType
                && !prop.isArray
                && prop.complexType != null
                && !prop.isEnum
                && !prop.isFreeFormObject) {
            return true;
        }
        return prop.isArray
                && prop.items != null
                && !prop.items.isPrimitiveType
                && prop.items.complexType != null
                && !prop.items.isEnum
                && !prop.items.isFreeFormObject;
    }

    /**
     * Adds the base type of a parameter (and its items, if it is a
     * collection) to the given set when the type is not a language
     * primitive. Used to track which model imports are referenced
     * by options-only parameters versus non-options parameters.
     */
    private void addModelBaseType(Set<String> types, CodegenParameter p) {
        if (p.baseType != null
                && !languageSpecificPrimitives.contains(p.baseType)
                && p.baseType.matches("^[A-Z]\\w*$")) {
            types.add(p.baseType);
        }
        if (p.items != null
                && p.items.baseType != null
                && !languageSpecificPrimitives.contains(p.items.baseType)
                && p.items.baseType.matches("^[A-Z]\\w*$")) {
            types.add(p.items.baseType);
        }
    }
}

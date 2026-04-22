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
                            "test/Api/pet-api.test.ts",
                            Path.of("test", "Api").toString(),
                            "pet-api.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/store-api.test.ts",
                            Path.of("test", "Api").toString(),
                            "store-api.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default-api-client.test.ts",
                            "test",
                            "default-api-client.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default-api-client-unit.test.ts",
                            "test",
                            "default-api-client-unit.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport-options.test.mustache",
                            "test",
                            "transport-options.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object-serializer.test.ts",
                            "test",
                            "object-serializer.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value-serializer.test.ts",
                            "test",
                            "value-serializer.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace-context-util.test.ts",
                            "test",
                            "trace-context-util.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base-api.test.mustache",
                            "test",
                            "base-api.test.ts"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata.test.ts",
                            "test",
                            "metadata.test.ts"));
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
     * Returns the TypeScript type declaration for a schema.
     * Arrays use angle-bracket generics (e.g. Array<string>),
     * maps use index-signature syntax, and all other types
     * delegate to the default type declaration logic.
     */
    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            final Schema<?> inner = p.getItems();
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
    @Override
    public String toDefaultValue(Schema schema) {
        return null;
    }

    /**
     * Sanitizes the variable name without applying any casing
     * transformation because TypeScript preserves the original
     * JSON property names for serialization fidelity.
     */
    @Override
    public String toVarName(String name) {
        return sanitizeName(name);
    }

    /**
     * Converts a schema name to a PascalCase TypeScript class
     * name. Prefixes the result with "Model" if it collides
     * with a language-specific primitive like "number" or
     * "string".
     */
    @Override
    public String toModelName(String name) {
        final String sanitized = sanitizeName(name);
        final String camelized = NamingConvention.PASCAL_CASE.apply(sanitized);
        if (languageSpecificPrimitives.contains(camelized)) {
            return "Model" + camelized;
        }
        return camelized;
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
     * Converts an enum value into a PascalCase enum member
     * name. Checks the enum name mapping first, resolves
     * known symbol characters, then delegates to the base
     * class for standard handling.
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
     * Builds TypeScript-specific import metadata for each
     * model and determines whether any model property needs
     * a type decorator for runtime deserialization. Primitive
     * parent stripping is handled by the base class.
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
     * Post-processes operations to add kebab-case filenames,
     * resolve inline enum parameter types with PascalCase
     * operation ID prefixes, and clean up import entries that
     * refer to primitives or mapped types. Ensures each import
     * map has a consistent "className" key for templates.
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
                        "auth/http_aware_authenticator.mustache",
                        authFolder,
                        "http-aware-authenticator.ts"));

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic_authenticator.mustache",
                            authFolder,
                            "basic-authenticator.ts"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer_authenticator.mustache",
                            authFolder,
                            "bearer-authenticator.ts"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_authenticator.mustache",
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
     * Removes eslint disable comments that the upstream
     * framework injects into generated TypeScript files, and
     * strips trailing blank lines. This keeps generated output
     * clean since the project uses its own ESLint and Prettier
     * configuration.
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
}

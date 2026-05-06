package io.github.mridang.codegen.generators.go;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
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
import java.util.Set;
import javax.annotation.Nullable;
import com.samskivert.mustache.Mustache;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Go API client that uses net/http for transport
 * and encoding/json for model serialization. All exported
 * identifiers use PascalCase per Go convention, while
 * unexported fields use camelCase. Filenames use snake_case.
 * The formatter pass invokes gofmt inside Docker to enforce
 * canonical Go formatting.
 */
@SuppressWarnings("unused")
public class BetterGoCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterGoCodegen.class);

    protected String packageName = "petstore";
    protected String packageVersion = "1.0.0";

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Go language. Type mappings convert OpenAPI
     * types to their Go equivalents (e.g. integer to int32,
     * DateTime to time.Time). Reserved words are loaded from a
     * bundled word-list to avoid generating identifiers that
     * clash with Go keywords and predeclared identifiers.
     */
    public BetterGoCodegen() {
        outputFolder = Path.of("generated-code", "go").toString();
        embeddedTemplateDir = templateDir = "templates/go";

        modelTemplateFiles.put("models/model.mustache", ".go");
        apiTemplateFiles.put("api/api.mustache", ".go");

        modelPackage = "";
        apiPackage = "";

        typeMapping.put("string", "string");
        typeMapping.put("boolean", "bool");
        typeMapping.put("int", "int32");
        typeMapping.put("integer", "int32");
        typeMapping.put("long", "int64");
        typeMapping.put("short", "int32");
        typeMapping.put("float", "float32");
        typeMapping.put("double", "float64");
        typeMapping.put("number", "float64");
        typeMapping.put("decimal", "float64");
        typeMapping.put("date", "string");
        typeMapping.put("DateTime", "time.Time");
        typeMapping.put("array", "[]");
        typeMapping.put("List", "[]");
        typeMapping.put("map", "map");
        typeMapping.put("object", "interface{}");
        typeMapping.put("AnyType", "interface{}");
        typeMapping.put("file", "*os.File");
        typeMapping.put("binary", "[]byte");
        typeMapping.put("ByteArray", "[]byte");
        typeMapping.put("UUID", "string");
        typeMapping.put("URI", "string");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "string",
                                "bool",
                                "int32",
                                "int64",
                                "float32",
                                "float64",
                                "byte",
                                "interface{}",
                                "error"));

        reservedWords = loadReservedWords("/reserved-words/go.txt");
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "go-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Go client with net/http.";
    }

    /**
     * Declares Go as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.GO;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "testdata";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "spec";
    }

    /**
     * Converts a property name to PascalCase without reserved-word
     * escaping. Go keywords are all lowercase, so PascalCase names
     * never conflict. The inherited case-insensitive
     * {@code isReservedWord} would incorrectly escape names like
     * {@code Type} (from {@code type}) to {@code _Type}, producing
     * unexported fields. This override skips that check entirely.
     */
    @Override
    public String toVarName(String name) {
        name = sanitizeName(name);
        name = applyVarNameCasing(name);
        if (name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        return name;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getVarCasing() {
        return NamingConvention.PASCAL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getOperationIdCasing() {
        return NamingConvention.PASCAL_CASE;
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
    protected NamingConvention getParamCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "golang:1.26";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            "go install golang.org/x/tools/cmd/goimports@v0.30.0",
            "go mod tidy",
            "goimports -w ."
        };
    }

    /**
     * Preserves all-uppercase identifiers like HTTP, URL, ID
     * as Go convention keeps uppercase abbreviations intact.
     */
    @Override
    protected UppercaseIdentifierStrategy getUppercaseIdentifierStrategy() {
        return UppercaseIdentifierStrategy.PRESERVE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getOperationIdReservedPrefix() {
        return "call_";
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("int32", "int64", "float32", "float64");
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '"';
    }

    /**
     * Formats an array type declaration using Go slice syntax.
     * Returns {@code []innerType} instead of the generic
     * angle-bracket form.
     */
    @Override
    protected String formatArrayType(String containerType, String innerType) {
        return "[]" + innerType;
    }

    /**
     * Formats a map type declaration using Go map syntax.
     * Returns {@code map[string]valueType}.
     */
    @Override
    protected String formatMapType(String containerType, String keyType, String valueType) {
        return "map[string]" + valueType;
    }

    /**
     * Returns {@code string} as the map key type because Go
     * maps use string keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "string";
    }

    /**
     * Returns {@code interface{}} as the default map value
     * type for Go's universal empty interface.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "interface{}";
    }

    /**
     * Resolves user-supplied options and registers all
     * supporting files for the Go package structure.
     * Sets up the package layout including models, API
     * classes, errors, auth, configuration, and serializers
     * all in the root package directory.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put("userAgentDefault", packageName + "/" + packageVersion + " (go)");

        additionalProperties.put(
                "pascalcase",
                (Mustache.Lambda)
                        (fragment, writer) ->
                                writer.write(
                                        NamingConvention.PASCAL_CASE.apply(fragment.execute())));

        additionalProperties.put(
                "goVarName",
                (Mustache.Lambda)
                        (fragment, writer) -> {
                            String type = fragment.execute().trim();
                            String clean = type.replace("*", "");
                            int sliceDepth = 0;
                            while (clean.startsWith("[]")) {
                                clean = clean.substring(2);
                                sliceDepth++;
                            }
                            if (!clean.isEmpty()) {
                                clean =
                                        Character.toLowerCase(clean.charAt(0))
                                                + clean.substring(1);
                            }
                            for (int i = 0; i < sliceDepth; i++) {
                                clean += "Slice";
                            }
                            if (clean.isEmpty()) {
                                clean = "val";
                            }
                            writer.write(clean);
                        });

        setModelPackage("");
        setApiPackage("");

        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(new SupportingFile("configuration.mustache", "pkg", "configuration.go"));
        supportingFiles.add(
                new SupportingFile("transport_options.mustache", "pkg", "transport_options.go"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache", "pkg", "server_configuration.go"));
        supportingFiles.add(new SupportingFile("servers.mustache", "pkg", "servers.go"));
        supportingFiles.add(
                new SupportingFile("api_error.mustache", "pkg/errors", "api_error.go"));

        supportingFiles.add(
                new SupportingFile(
                        "errors/client_error.mustache", "pkg/errors", "client_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/server_error.mustache", "pkg/errors", "server_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/bad_request_error.mustache",
                        "pkg/errors",
                        "bad_request_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unauthorized_error.mustache",
                        "pkg/errors",
                        "unauthorized_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/forbidden_error.mustache", "pkg/errors", "forbidden_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/not_found_error.mustache", "pkg/errors", "not_found_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/conflict_error.mustache", "pkg/errors", "conflict_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unprocessable_entity_error.mustache",
                        "pkg/errors",
                        "unprocessable_entity_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/internal_server_error.mustache",
                        "pkg/errors",
                        "internal_server_error.go"));
        supportingFiles.add(
                new SupportingFile("header_selector.mustache", "pkg", "header_selector.go"));
        supportingFiles.add(
                new SupportingFile("object_serializer.mustache", "pkg", "object_serializer.go"));
        supportingFiles.add(
                new SupportingFile("value_serializer.mustache", "pkg", "value_serializer.go"));
        supportingFiles.add(
                new SupportingFile("trace_context_util.mustache", "pkg", "trace_context_util.go"));
        supportingFiles.add(new SupportingFile("api_response.mustache", "pkg", "api_response.go"));
        supportingFiles.add(new SupportingFile("api_result.mustache", "pkg", "api_result.go"));
        supportingFiles.add(new SupportingFile("api_client.mustache", "pkg", "api_client.go"));
        supportingFiles.add(
                new SupportingFile("default_api_client.mustache", "pkg", "default_api_client.go"));
        supportingFiles.add(new SupportingFile("base_api.mustache", "pkg", "base_api.go"));
        supportingFiles.add(new SupportingFile("path_utils.mustache", "pkg", "path_utils.go"));
        supportingFiles.add(
                new SupportingFile("authenticator.mustache", "pkg", "authenticator.go"));
        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", "pkg", clientClassFile + ".go"));
        supportingFiles.add(new SupportingFile("go_mod.mustache", "", "go.mod"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/testcontainers_helper_test.mustache",
                            "test",
                            "testcontainers_helper_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet_api_test.mustache", "test", "pet_api_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store_api_test.mustache", "test", "store_api_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "test",
                            "default_api_client_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "test",
                            "default_api_client_unit_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "test",
                            "transport_options_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "test",
                            "header_selector_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "test",
                            "object_serializer_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "test",
                            "value_serializer_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "test",
                            "trace_context_util_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache",
                            "test",
                            "base_api_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache",
                            "test",
                            "metadata_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "test",
                            "composed_schema_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration_test.mustache",
                            "test",
                            "configuration_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "test",
                            "client_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_token_manager_test.mustache",
                            "test",
                            "oauth2_token_manager_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_auth_code_authenticator_test.mustache",
                            "test",
                            "oauth2_auth_code_authenticator_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_implicit_authenticator_test.mustache",
                            "test",
                            "oauth2_implicit_authenticator_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_client_credentials_authenticator_test.mustache",
                            "test",
                            "oauth2_client_credentials_authenticator_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_password_authenticator_test.mustache",
                            "test",
                            "oauth2_password_authenticator_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/openid_connect_authenticator_test.mustache",
                            "test",
                            "openid_connect_authenticator_test.go"));
        }
    }

    /**
     * Returns the output directory for model source files.
     * Models are placed in the {@code pkg/models/} sub-package.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(getOutputDir(), "pkg", "models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * APIs are placed in the {@code pkg/} directory alongside
     * infrastructure types (Configuration, BaseApi, ApiClient).
     */
    @Override
    public String apiFileFolder() {
        return Path.of(getOutputDir(), "pkg").toString();
    }

    /**
     * Returns the Go default value literal for the given
     * schema. Numeric and boolean defaults use their string
     * representation; string defaults are wrapped in double
     * quotes. All other types return null to omit the default.
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
                return "\"" + escapeText(String.valueOf(resolved.getDefault())) + "\"";
            }
        }
        return null;
    }

    /**
     * Fixes enum default values that the base class sets to
     * Java-style enum references (e.g. "StatusEnum.Placed").
     * For Go, enum fields are typed as string, so the default
     * must be a Go string literal (e.g. "\"placed\"").
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            for (final CodegenProperty prop : model.vars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.allVars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.optionalVars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.requiredVars) {
                fixEnumDefaultValue(prop);
            }

            // Check if any property uses time.Time and set a flag for the template
            boolean needsTimeImport = false;
            for (final CodegenProperty prop : model.vars) {
                if (prop.dataType != null && prop.dataType.contains("time.Time")) {
                    needsTimeImport = true;
                    break;
                }
            }
            if (needsTimeImport) {
                modelMap.put("hasTimeImport", true);
                result.put("hasTimeImport", true);
            }

            // oneOf/anyOf models use fmt.Errorf
            if (!model.oneOf.isEmpty() || !model.anyOf.isEmpty()) {
                result.put("hasFmtImport", true);
            }
        }
        return result;
    }

    /**
     * Detects whether any operation in the tag uses os.File (file
     * parameters or file return types) and sets a hasOsImport flag
     * so the API template can conditionally include the "os" import.
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        objs = super.postProcessOperationsWithModels(objs, allModels);
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            final List<CodegenOperation> ops =
                    (List<CodegenOperation>) operations.get("operation");
            if (ops != null) {
                boolean hasOsImport = false;
                for (final CodegenOperation op : ops) {
                    if (op.returnType != null && op.returnType.contains("os.File")) {
                        hasOsImport = true;
                        break;
                    }
                    for (final CodegenParameter p : op.allParams) {
                        if (p.isFile || (p.dataType != null && p.dataType.contains("os.File"))) {
                            hasOsImport = true;
                            break;
                        }
                    }
                    if (hasOsImport) break;
                }
                if (hasOsImport) {
                    objs.put("hasOsImport", true);
                }
            }
        }
        return objs;
    }

    private void fixEnumDefaultValue(CodegenProperty prop) {
        if (prop.defaultValue != null && prop.isEnum && prop.defaultValue.contains(".")) {
            // Extract the enum value name and convert to a Go string literal
            final String enumValue = prop.defaultValue.substring(
                    prop.defaultValue.lastIndexOf('.') + 1);
            prop.defaultValue = "\"" + enumValue.toLowerCase(java.util.Locale.ROOT) + "\"";
        }
    }

    /**
     * Registers supporting files for each authentication scheme
     * present in the OpenAPI spec. Concrete auth implementations
     * go to {@code auth/} and {@code auth/oauth/} sub-packages.
     * The Authenticator and HttpAwareAuthenticator interfaces
     * remain in the root package to avoid circular imports.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        supportingFiles.add(
                new SupportingFile(
                        "auth/base_authenticator.mustache",
                        "pkg/auth",
                        "base_authenticator.go"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        "pkg/auth",
                        "http_aware_authenticator.go"));

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic_authenticator.mustache",
                            "pkg/auth",
                            "basic_authenticator.go"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer_authenticator.mustache",
                            "pkg/auth",
                            "bearer_authenticator.go"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_location.mustache",
                            "pkg/auth",
                            "api_key_location.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_authenticator.mustache",
                            "pkg/auth",
                            "api_key_authenticator.go"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_token_manager.mustache",
                            "pkg/auth/oauth",
                            "oauth2_token_manager.go"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_client_credentials_authenticator.mustache",
                            "pkg/auth/oauth",
                            "oauth2_client_credentials_authenticator.go"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_password_authenticator.mustache",
                            "pkg/auth/oauth",
                            "oauth2_password_authenticator.go"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_auth_code_authenticator.mustache",
                            "pkg/auth/oauth",
                            "oauth2_auth_code_authenticator.go"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_implicit_authenticator.mustache",
                            "pkg/auth/oauth",
                            "oauth2_implicit_authenticator.go"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/openid_connect_authenticator.mustache",
                            "pkg/auth/oauth",
                            "openid_connect_authenticator.go"));
        }
    }

    /**
     * Collapses runs of two or more consecutive blank lines
     * in generated {@code .go} files into a single blank line,
     * and removes trailing commas from function signatures.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null) {
            return;
        }
        final String name = file.getName();
        if (!name.endsWith(".go")) {
            return;
        }
        cleanupGoFile(file);
    }

    /**
     * Collapses consecutive blank lines and removes trailing
     * commas before closing parentheses in function signatures
     * (an artefact of Mustache template generation).
     */
    private static void cleanupGoFile(File file) {
        try {
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            final List<String> result = new ArrayList<>(lines.size());
            boolean prevBlank = false;
            boolean changed = false;

            for (final String line : lines) {
                String cleaned = line;
                if (cleaned.contains(", )")) {
                    cleaned = cleaned.replace(", )", ")");
                    changed = true;
                }
                final boolean blank = cleaned.trim().isEmpty();
                if (blank && prevBlank) {
                    changed = true;
                    continue;
                }
                result.add(cleaned);
                prevBlank = blank;
            }

            if (changed) {
                Files.write(file.toPath(), result, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.debug(
                    "Failed to clean up Go file {}: {}", file.getName(), e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final Set<String> goPrimitives =
                Set.of(
                        "string", "bool", "int32", "int64", "float32", "float64",
                        "interface{}", "byte", "[]byte");
        boolean hasModelImport = false;
        boolean hasOsImport = false;
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", NamingConvention.PASCAL_CASE.apply(p.paramName));
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            params.add(param);
            if (p.isFile || (p.dataType != null && p.dataType.contains("os.File"))) {
                hasOsImport = true;
            } else if (!p.isPrimitiveType) {
                String baseType = p.dataType.replace("[]", "").replace("*", "");
                if (!goPrimitives.contains(baseType)
                        && !baseType.startsWith("map[")
                        && !baseType.equals("time.Time")) {
                    hasModelImport = true;
                }
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("packageName", "options");
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("moduleName", packageName);
        if (hasModelImport) {
            context.put("hasModelImport", true);
        }
        if (hasOsImport) {
            context.put("hasOsImport", true);
        }
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(getOutputDir(), "pkg", "options", fileName + ".go").toString();
    }
}

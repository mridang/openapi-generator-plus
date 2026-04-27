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
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
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
        return "golang:1.24";
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

        supportingFiles.add(new SupportingFile("configuration.mustache", "", "configuration.go"));
        supportingFiles.add(
                new SupportingFile("transport_options.mustache", "", "transport_options.go"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache", "", "server_configuration.go"));
        supportingFiles.add(new SupportingFile("servers.mustache", "", "servers.go"));
        supportingFiles.add(
                new SupportingFile("api_error.mustache", "errors", "api_error.go"));

        supportingFiles.add(
                new SupportingFile(
                        "errors/client_error.mustache", "errors", "client_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/server_error.mustache", "errors", "server_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/bad_request_error.mustache",
                        "errors",
                        "bad_request_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unauthorized_error.mustache",
                        "errors",
                        "unauthorized_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/forbidden_error.mustache", "errors", "forbidden_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/not_found_error.mustache", "errors", "not_found_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/conflict_error.mustache", "errors", "conflict_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unprocessable_entity_error.mustache",
                        "errors",
                        "unprocessable_entity_error.go"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/internal_server_error.mustache",
                        "errors",
                        "internal_server_error.go"));
        supportingFiles.add(
                new SupportingFile("header_selector.mustache", "", "header_selector.go"));
        supportingFiles.add(
                new SupportingFile("object_serializer.mustache", "", "object_serializer.go"));
        supportingFiles.add(
                new SupportingFile("value_serializer.mustache", "", "value_serializer.go"));
        supportingFiles.add(
                new SupportingFile("trace_context_util.mustache", "", "trace_context_util.go"));
        supportingFiles.add(new SupportingFile("api_response.mustache", "", "api_response.go"));
        supportingFiles.add(new SupportingFile("api_result.mustache", "", "api_result.go"));
        supportingFiles.add(new SupportingFile("api_client.mustache", "", "api_client.go"));
        supportingFiles.add(
                new SupportingFile("default_api_client.mustache", "", "default_api_client.go"));
        supportingFiles.add(new SupportingFile("base_api.mustache", "", "base_api.go"));
        supportingFiles.add(new SupportingFile("path_utils.mustache", "", "path_utils.go"));
        supportingFiles.add(
                new SupportingFile("authenticator.mustache", "", "authenticator.go"));
        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", "", clientClassFile + ".go"));
        supportingFiles.add(new SupportingFile("go_mod.mustache", "", "go.mod"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/pet_api_test.mustache", "test", "pet_api_test.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/store_api_test.mustache", "test", "store_api_test.go"));
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
        }
    }

    /**
     * Returns the output directory for model source files.
     * Models are placed in the {@code models/} sub-package.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(getOutputDir(), "models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * APIs remain in the root package to avoid circular
     * imports with infrastructure types (Configuration,
     * BaseApi, ApiClient).
     */
    @Override
    public String apiFileFolder() {
        return getOutputDir();
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
                        "auth",
                        "base_authenticator.go"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        "auth",
                        "http_aware_authenticator.go"));

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic_authenticator.mustache",
                            "auth",
                            "basic_authenticator.go"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer_authenticator.mustache",
                            "auth",
                            "bearer_authenticator.go"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_location.mustache",
                            "auth",
                            "api_key_location.go"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_authenticator.mustache",
                            "auth",
                            "api_key_authenticator.go"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_token_manager.mustache",
                            "auth/oauth",
                            "oauth2_token_manager.go"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_client_credentials_authenticator.mustache",
                            "auth/oauth",
                            "oauth2_client_credentials_authenticator.go"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_password_authenticator.mustache",
                            "auth/oauth",
                            "oauth2_password_authenticator.go"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_auth_code_authenticator.mustache",
                            "auth/oauth",
                            "oauth2_auth_code_authenticator.go"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_implicit_authenticator.mustache",
                            "auth/oauth",
                            "oauth2_implicit_authenticator.go"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/openid_connect_authenticator.mustache",
                            "auth/oauth",
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
            if (!p.isPrimitiveType && !p.isFile) {
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
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(getOutputDir(), "options", fileName + ".go").toString();
    }
}

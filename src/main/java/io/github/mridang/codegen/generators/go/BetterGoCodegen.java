package io.github.mridang.codegen.generators.go;

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
import java.util.Set;
import java.util.regex.Pattern;
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
        typeMapping.put("UUID", "uuid.UUID");
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
                                "error",
                                "uuid.UUID"));

        reservedWords = loadReservedWords("/reserved-words/go.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated Go module (default: 1.0.0).").defaultValue("1.0.0"));
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

    /** {@inheritDoc} */
    @Override
    protected boolean shouldEscapeReservedVarName(String name) {
        return false;
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
    protected String getNullLiteral() {
        return "nil";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayTypeTemplate() {
        return "[]%2$s";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "map[%2$s]%3$s";
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "Set[$1]";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayContainerPattern() {
        return "^\\[\\](.+)$";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "pkg";
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

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", "pkg", clientClassFile + ".go"));

        if (generateTests) {
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
            if (hasBasicAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/basic_authenticator_test.mustache",
                                "test",
                                "basic_authenticator_test.go"));
            }
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

    /** {@inheritDoc} */
    @Override
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        return List.of(
                new SupportingFileSpec("readme.mustache", "", "README.md"),
                new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
                new SupportingFileSpec("configuration.mustache", "pkg", "configuration.go"),
                new SupportingFileSpec(
                        "transport_options.mustache", "pkg", "transport_options.go"),
                new SupportingFileSpec(
                        "server_configuration.mustache", "pkg", "server_configuration.go"),
                new SupportingFileSpec("servers.mustache", "pkg", "servers.go"),
                new SupportingFileSpec("api_error.mustache", "pkg/errors", "api_error.go"),
                new SupportingFileSpec(
                        "errors/client_error.mustache", "pkg/errors", "client_error.go"),
                new SupportingFileSpec(
                        "errors/server_error.mustache", "pkg/errors", "server_error.go"),
                new SupportingFileSpec(
                        "errors/bad_request_error.mustache",
                        "pkg/errors",
                        "bad_request_error.go"),
                new SupportingFileSpec(
                        "errors/unauthorized_error.mustache",
                        "pkg/errors",
                        "unauthorized_error.go"),
                new SupportingFileSpec(
                        "errors/forbidden_error.mustache", "pkg/errors", "forbidden_error.go"),
                new SupportingFileSpec(
                        "errors/not_found_error.mustache", "pkg/errors", "not_found_error.go"),
                new SupportingFileSpec(
                        "errors/conflict_error.mustache", "pkg/errors", "conflict_error.go"),
                new SupportingFileSpec(
                        "errors/unprocessable_entity_error.mustache",
                        "pkg/errors",
                        "unprocessable_entity_error.go"),
                new SupportingFileSpec(
                        "errors/internal_server_error.mustache",
                        "pkg/errors",
                        "internal_server_error.go"),
                new SupportingFileSpec("header_selector.mustache", "pkg", "header_selector.go"),
                new SupportingFileSpec(
                        "object_serializer.mustache", "pkg", "object_serializer.go"),
                new SupportingFileSpec("value_serializer.mustache", "pkg", "value_serializer.go"),
                new SupportingFileSpec(
                        "trace_context_util.mustache", "pkg", "trace_context_util.go"),
                new SupportingFileSpec("api_response.mustache", "pkg", "api_response.go"),
                new SupportingFileSpec("api_result.mustache", "pkg", "api_result.go"),
                new SupportingFileSpec("api_client.mustache", "pkg", "api_client.go"),
                new SupportingFileSpec(
                        "default_api_client.mustache", "pkg", "default_api_client.go"),
                new SupportingFileSpec("base_api.mustache", "pkg", "base_api.go"),
                new SupportingFileSpec("path_utils.mustache", "pkg", "path_utils.go"),
                new SupportingFileSpec("authenticator.mustache", "pkg", "authenticator.go"),
                new SupportingFileSpec("go_mod.mustache", "", "go.mod"),
                new SupportingFileSpec("makefile.mustache", "", "Makefile"),
                new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
                new SupportingFileSpec("gitignore.mustache", "", ".gitignore"),
                new SupportingFileSpec("golangci.mustache", "", ".golangci.yml"),
                new SupportingFileSpec("models/set.mustache", "pkg/models", "set.go"));
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

    /** {@inheritDoc} */
    @Override
    protected Map<String, String> getModelContextFlags() {
        return Map.of(
                "type:time.Time", "hasTimeImport",
                "type:uuid.UUID", "hasUuidImport",
                "oneOfAnyOf", "hasFmtImport",
                "isEnum", "hasFmtImport",
                "hasInlineEnum", "hasFmtImport",
                "hasRequired", "hasFmtImport");
    }

    /** {@inheritDoc} */
    @Override
    protected Map<String, String> getOperationContextFlags() {
        return Map.of(
                "type:os.File", "hasOsImport",
                "type:uuid.UUID", "hasUuidImport",
                "servers", "hasStringsImport",
                "cookieParams", "hasStringsImport",
                "queryContent", "hasJsonImport");
    }

    /** {@inheritDoc} */
    @Override
    protected List<FileContentFixup> getFileContentFixups() {
        return List.of(new FileContentFixup(
                ".go",
                Pattern.compile(", \\)"),
                ")"));
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
        boolean hasUuidImport = false;
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
            } else if (p.dataType != null && p.dataType.contains("uuid.UUID")) {
                hasUuidImport = true;
            } else if (!p.isPrimitiveType) {
                String baseType = p.dataType.replace("[]", "").replace("*", "");
                if (!goPrimitives.contains(baseType)
                        && !baseType.startsWith("map[")
                        && !baseType.equals("time.Time")
                        && !baseType.equals("uuid.UUID")) {
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
        if (hasUuidImport) {
            context.put("hasUuidImport", true);
        }
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(getOutputDir(), "pkg", "options", fileName + ".go").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return "pkg/auth";
    }

    /** {@inheritDoc} */
    @Override
    protected String getOAuthDir() {
        return "pkg/auth/oauth";
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return stem + ".go";
    }

    /** {@inheritDoc} */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("package", spec.isOAuth() ? "oauth" : "auth");
        ctx.put("imports", List.of());
        final List<Map<String, String>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, String> param = new HashMap<>();
            param.put("name", name);
            param.put("type", "string");
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        ctx.put("superArgs", buildGoSuperArgs(spec));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "keyIn values are ASCII-only OpenAPI location strings (header/query/cookie)"
                    + " — toLowerCase(Locale.ROOT) is intentional and safe here")
    private List<String> buildGoSuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.keyParamName() + "\"", "apiKey",
                    "ApiKeyLocation" + NamingConvention.PASCAL_CASE.apply(spec.keyIn() != null ? spec.keyIn().toLowerCase(Locale.ROOT) : "header"));
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"", "nil");
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "\"\"";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.tokenUrl() + "\"",
                    "username", "password", "nil", refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "\"" + spec.refreshUrl() + "\"" : "\"\"";
            return List.of("host", "clientId", "clientSecret",
                    "\"" + spec.authorizationUrl() + "\"", "\"" + spec.tokenUrl() + "\"",
                    "redirectUri", "nil", refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "clientId",
                    "\"" + spec.authorizationUrl() + "\"", "nil");
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "\"" + spec.openIdConnectUrl() + "\"",
                    "clientId", "clientSecret", "redirectUri", "nil");
        }
        return List.of();
    }

    /**
     * Enables Gap K so polymorphic subtypes auto-emit their
     * discriminator field on serialization. The Go model template
     * honours {@code defaultValue} on optional vars in the New*
     * constructor, so the discriminator initialises to e.g.
     * {@code "dry"} without the caller supplying it.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /**
     * Demotes the discriminator out of {@code requiredVars} so the
     * generated {@code New*} constructor signature drops the
     * discriminator parameter (e.g. {@code NewDryFood(weightKg float64)}
     * instead of {@code NewDryFood(foodType string, weightKg float64)}).
     * The Go struct field stays a non-pointer {@code string} because
     * the property's {@code required} flag remains {@code true}, and
     * the constructor body assigns the default literal via the
     * optionalVars branch of {@code model.mustache}.
     */
    @Override
    protected boolean demotesDiscriminatorFromRequiredVars() {
        return true;
    }
}

package io.github.mridang.codegen.generators.python;


import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Python 3.10+ API client that uses urllib3 for
 * HTTP transport and Pydantic v2 for model serialization.
 * All identifiers follow snake_case conventions enforced by
 * the Ruff formatter. Variable names, operation IDs, and
 * filenames all use {@code SNAKE_CASE} naming.
 */
@SuppressWarnings("unused")
public class BetterPythonCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterPythonCodegen.class);

    private static final Map<String, String> TYPE_IMPORTS =
            Map.of(
                    "datetime", "from datetime import datetime",
                    "date", "from datetime import date",
                    "Decimal", "from decimal import Decimal");

    protected String packageName = "openapi_client";
    protected String packageVersion = "1.0.0";

    /**
     * Initializes the Python codegen with type mappings,
     * language primitives, and reserved words. Clears
     * inherited defaults and configures the template
     * directory for Python-specific Mustache templates.
     */
    public BetterPythonCodegen() {
        outputFolder = "generated-code/python";
        embeddedTemplateDir = templateDir = "templates/python";

        modelTemplateFiles.put("models/model.mustache", ".py");
        apiTemplateFiles.put("api/api.mustache", ".py");

        typeMapping.put("integer", "int");
        typeMapping.put("long", "int");
        typeMapping.put("float", "float");
        typeMapping.put("double", "float");
        typeMapping.put("number", "float");
        typeMapping.put("boolean", "bool");
        typeMapping.put("string", "str");
        typeMapping.put("byte", "bytes");
        typeMapping.put("binary", "bytes");
        typeMapping.put("ByteArray", "bytes");
        typeMapping.put("date", "date");
        typeMapping.put("DateTime", "datetime");
        typeMapping.put("UUID", "str");
        typeMapping.put("URI", "str");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "object");
        typeMapping.put("array", "List");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "Dict");
        typeMapping.put("file", "bytes");
        typeMapping.put("File", "bytes");
        typeMapping.put("decimal", "float");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "float", "bool", "str", "bytes", "object",
                                "date", "datetime", "List", "Dict", "Set",
                                "Tuple", "Optional"));

        reservedWords = loadReservedWords("/reserved-words/python.txt");

        this.setDisallowAdditionalPropertiesIfNotPresent(false);
        this.setLegacyDiscriminatorBehavior(false);
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "python-plus";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Python client with pydantic models.";
    }

    /** {@inheritDoc} */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.PYTHON;
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
        return NamingConvention.UPPER_SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "python:3-slim";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {"pip install --quiet ruff", "ruff format ."};
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getUniqueItemsSetType() {
        return "set[";
    }

    /** {@inheritDoc} */
    @Override
    protected String getArrayContainerPattern() {
        return "^[Ll]ist\\[";
    }

    /**
     * Resolves user-supplied options and registers all
     * supporting files for the Python package structure.
     * Sets up the package layout including models, API
     * classes, exceptions, auth, and configuration modules.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion =
                getPropertyOrDefault(CodegenConstants.PACKAGE_VERSION, packageVersion);
        additionalProperties.put("userAgentDefault", packageName + "/" + packageVersion + " (python)");

        modelPackage = packageName + ".models";
        apiPackage = packageName + ".api";

        final String modelPath = modelPackage.replace('.', File.separatorChar);
        final String apiPath = apiPackage.replace('.', File.separatorChar);
        final String packagePath = packageName.replace('.', File.separatorChar);

        supportingFiles.add(
                new SupportingFile("models/__init__.mustache", modelPath, "__init__.py"));
        supportingFiles.add(
                new SupportingFile("api/__init__.mustache", apiPath, "__init__.py"));
        supportingFiles.add(
                new SupportingFile("__init__.mustache", packagePath, "__init__.py"));
        supportingFiles.add(
                new SupportingFile("py_typed.mustache", packagePath, "py.typed"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", packagePath, "api_client.py"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", packagePath, "default_api_client.py"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", packagePath, "api_response.py"));
        supportingFiles.add(
                new SupportingFile("api_result.mustache", packagePath, "api_result.py"));
        supportingFiles.add(
                new SupportingFile("base_api.mustache", apiPath, "base_api.py"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", packagePath, "configuration.py"));
        final String exceptionsPath = Path.of(packagePath, "exceptions").toString();
        supportingFiles.add(
                new SupportingFile("exceptions.mustache", exceptionsPath, "__init__.py"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/client_exception.mustache",
                        exceptionsPath,
                        "client_exception.py"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/server_exception.mustache",
                        exceptionsPath,
                        "server_exception.py"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/bad_request_exception.mustache",
                        exceptionsPath,
                        "bad_request_exception.py"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/unauthorized_exception.mustache",
                        exceptionsPath,
                        "unauthorized_exception.py"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/forbidden_exception.mustache",
                        exceptionsPath,
                        "forbidden_exception.py"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/not_found_exception.mustache",
                        exceptionsPath,
                        "not_found_exception.py"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/conflict_exception.mustache",
                        exceptionsPath,
                        "conflict_exception.py"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/unprocessable_entity_exception.mustache",
                        exceptionsPath,
                        "unprocessable_entity_exception.py"));
        supportingFiles.add(
                new SupportingFile(
                        "exceptions/internal_server_error_exception.mustache",
                        exceptionsPath,
                        "internal_server_error_exception.py"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", packagePath, "object_serializer.py"));
        supportingFiles.add(
                new SupportingFile(
                        "value_serializer.mustache", packagePath, "value_serializer.py"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", packagePath, "header_selector.py"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", packagePath, "trace_context_util.py"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options.mustache", packagePath, "transport_options.py"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache",
                        packagePath,
                        "server_configuration.py"));
        supportingFiles.add(
                new SupportingFile("servers.mustache", packagePath, "servers.py"));
        final String authPath = Path.of(packagePath, "auth").toString();
        supportingFiles.add(
                new SupportingFile("auth/__init__.mustache", authPath, "__init__.py"));
        supportingFiles.add(
                new SupportingFile("authenticator.mustache", authPath, "authenticator.py"));
        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", packagePath, clientClassFile + ".py"));
        supportingFiles.add(new SupportingFile("pyproject_toml.mustache", "", "pyproject.toml"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/conftest.py", "", "conftest.py"));
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
            supportingFiles.add(
                    new SupportingFile("test/tests_init.py", "test", "__init__.py"));
            final String testApiPath = Path.of("test", "Api").toString();
            supportingFiles.add(
                    new SupportingFile("test/Api_init.py", testApiPath, "__init__.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/test_pet_api.mustache",
                            testApiPath,
                            "test_pet_api.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/test_store_api.mustache",
                            testApiPath,
                            "test_store_api.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_default_api_client.mustache",
                            "test",
                            "test_default_api_client.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_default_api_client_unit.mustache",
                            "test",
                            "test_default_api_client_unit.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_transport_options.mustache",
                            "test",
                            "test_transport_options.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_header_selector.mustache",
                            "test",
                            "test_header_selector.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_object_serializer.mustache",
                            "test",
                            "test_object_serializer.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_value_serializer.mustache",
                            "test",
                            "test_value_serializer.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_trace_context_util.mustache",
                            "test",
                            "test_trace_context_util.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_base_api.mustache",
                            "test",
                            "test_base_api.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_metadata.mustache",
                            "test",
                            "test_metadata.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_composed_schema.mustache",
                            "test",
                            "test_composed_schema.py"));
        }
    }

    /**
     * Returns the output directory for model source files by
     * combining the output folder and the model package
     * converted to a directory path.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, modelPackage.replace('.', '/')).toString();
    }

    /**
     * Returns the output directory for API source files by
     * combining the output folder and the API package
     * converted to a directory path.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, apiPackage.replace('.', '/')).toString();
    }

    /**
     * Overrides the base class because Python uses bracket
     * generics ({@code List[str]}) instead of angle-bracket
     * syntax ({@code List<String>}). Cannot be standardized
     * because no other language uses this bracket form.
     */
    @Override
    protected String formatArrayType(String containerType, String innerType) {
        return containerType + "[" + innerType + "]";
    }

    /**
     * Overrides the base class because Python uses bracket
     * generics ({@code Dict[str, Any]}) instead of angle-bracket
     * syntax ({@code Dict<String, Object>}). Cannot be
     * standardized because no other language uses this form.
     */
    @Override
    protected String formatMapType(String containerType, String keyType, String valueType) {
        return containerType + "[" + keyType + ", " + valueType + "]";
    }

    /**
     * Returns {@code str} as the map key type because Python
     * dictionaries use string keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "str";
    }

    /**
     * Overrides the base class because Python's universal base
     * type is {@code object}, not {@code Object}. Cannot be
     * standardized because the capitalization differs from the
     * Java-style default.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "object";
    }

    /**
     * Overrides the base class because Python needs
     * {@code from X.Y import Z} syntax for model imports.
     * Cannot be standardized because no other language uses
     * this import form.
     */
    @Override
    public String toModelImport(String name) {
        if (name.startsWith("import") || name.startsWith("from")) {
            return name;
        }
        return "from "
                + modelPackage()
                + "."
                + toModelFilename(name)
                + " import "
                + name;
    }

    /**
     * Overrides the base class to skip the PascalCase camelize
     * that DefaultCodegen applies, since Python API class names
     * need snake_case-friendly tags. Cannot be standardized
     * because other languages want PascalCase tag names.
     */
    @Override
    public String sanitizeTag(String tag) {
        return sanitizeName(tag);
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

    /**
     * Overrides the base class because Python uses triple-quote
     * ({@code '''}) docstrings instead of {@code /* *\/} block
     * comments. Breaks triple-quote sequences to prevent
     * accidental docstring closure. Cannot be standardized
     * because other languages use {@code /* *\/} (handled by
     * the base class).
     */
    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("'''", "'_'_'");
    }

    /**
     * Converts a schema default value to valid Python syntax.
     * Transforms boolean defaults to Python's capitalized
     * True/False form and passes other defaults through
     * unchanged.
     */
    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema unaliased = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (unaliased.getDefault() != null) {
            if (ModelUtils.isBooleanSchema(unaliased)) {
                return Boolean.parseBoolean(unaliased.getDefault().toString()) ? "True" : "False";
            }
            return unaliased.getDefault().toString();
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("int", "float");
    }


    /**
     * Overrides the base class to sanitize example values
     * that contain Java-specific artifacts (null literals,
     * byte-array toString output) into valid Python syntax.
     * Cannot be standardized because other languages don't
     * have these example format issues.
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        // Sanitize example values for valid Python syntax
        if (property.example != null) {
            if ("null".equals(property.example) || property.example.startsWith("[B@")) {
                // Java null literal or byte-array toString — not valid Python
                property.example = null;
            } else if (property.isString
                    && !property.example.startsWith("'")
                    && !property.example.startsWith("\"")) {
                property.example = "'" + property.example.replace("'", "\\'") + "'";
            }
        }
    }

    /**
     * Overrides the base class to resolve Python-specific type
     * imports ({@code from datetime import datetime}, etc.).
     * Cannot be standardized because Python is the only language
     * that needs per-type import statements.
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();

                final TreeSet<String> fullImports = new TreeSet<>();

                for (final CodegenProperty prop : model.allVars) {
                    addTypeImport(fullImports, prop.dataType);
                    if (prop.items != null) {
                        addTypeImport(fullImports, prop.items.dataType);
                    }
                }

                for (final String imp : model.imports) {
                    fullImports.add(
                            "from "
                                    + modelPackage
                                    + "."
                                    + toModelFilename(imp)
                                    + " import "
                                    + imp);
                }
                model.imports.clear();
                model.imports.addAll(fullImports);
            }
        }
        return result;
    }

    /**
     * Registers supporting files for authentication classes
     * based on which security scheme types were detected in
     * the OpenAPI spec. Each scheme type gets its own
     * authenticator module in the auth package.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String packagePath = packageName.replace('.', File.separatorChar);
        final String authPath = Path.of(packagePath, "auth").toString();
        final String oauthPath = Path.of(authPath, "oauth").toString();

        supportingFiles.add(new SupportingFile("auth/http_aware_authenticator.mustache", authPath, "http_aware_authenticator.py"));

        if (hasBasicAuth) {
            supportingFiles.add(new SupportingFile("auth/basic_authenticator.mustache", authPath, "basic_authenticator.py"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(new SupportingFile("auth/bearer_authenticator.mustache", authPath, "bearer_authenticator.py"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(new SupportingFile("auth/api_key_authenticator.mustache", authPath, "api_key_authenticator.py"));
            supportingFiles.add(new SupportingFile("auth/api_key_location.mustache", authPath, "api_key_location.py"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/__init__.mustache", oauthPath, "__init__.py"));
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_token_manager.mustache", oauthPath, "oauth2_token_manager.py"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_client_credentials_authenticator.mustache", oauthPath, "oauth2_client_credentials_authenticator.py"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_password_authenticator.mustache", oauthPath, "oauth2_password_authenticator.py"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_auth_code_authenticator.mustache", oauthPath, "oauth2_auth_code_authenticator.py"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_implicit_authenticator.mustache", oauthPath, "oauth2_implicit_authenticator.py"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/openid_connect_authenticator.mustache", oauthPath, "openid_connect_authenticator.py"));
        }
    }

    /**
     * Overrides the base class to fix Mustache whitespace
     * artifacts in Python f-string braces ({@code { 'x' }}
     * becomes {@code {'x'}}). Cannot be standardized because
     * this is a Python template artifact that doesn't affect
     * other languages.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);
        if (file == null || !file.getName().endsWith(".py")) {
            return;
        }
        try {
            final String content = Files.readString(file.toPath());
            // Fix Mustache whitespace in f-string braces: { 'string' } → {'string'}
            final String trimmed = content.replaceAll("\\{ ('.*?') }", "{$1}");
            if (!trimmed.equals(content)) {
                Files.write(file.toPath(), trimmed.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to post-process file: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * Adds a Python-specific import statement for a data
     * type if it requires one. Handles datetime, date, and
     * Decimal types that need explicit Python imports.
     */
    private static void addTypeImport(TreeSet<String> imports, String dataType) {
        final String imp = TYPE_IMPORTS.get(dataType);
        if (imp != null) {
            imports.add(imp);
        }
    }
}

package io.github.mridang.codegen.generators.python;


import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.BarrelFileEmitter;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
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
public class BetterPythonCodegen extends AbstractBetterCodegen implements BarrelFileEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterPythonCodegen.class);

    /** Maps Python built-in datatype names to their required import statement. */
    private static final Map<String, String> TYPE_IMPORTS;

    static {
        final Map<String, String> imports = new HashMap<>();
        imports.put("date", "from datetime import date");
        // These datatypes are emitted module-qualified ("datetime.time",
        // "datetime.timedelta") by typeMapping and the object serializer, so
        // the module itself must be imported. A `from datetime import time`
        // would bind the bare name and leave `datetime.time` unresolved
        // (NameError) on every model that carries a time/duration field.
        imports.put("datetime.time", "import datetime");
        imports.put("datetime.timedelta", "import datetime");
        imports.put("Decimal", "from decimal import Decimal");
        imports.put("uuid.UUID", "import uuid");
        // Pydantic 2 native types
        imports.put("HttpUrl", "from pydantic import HttpUrl");
        imports.put("EmailStr", "from pydantic import EmailStr");
        imports.put("SecretStr", "from pydantic import SecretStr");
        imports.put("AwareDatetime", "from pydantic import AwareDatetime");
        imports.put("StrictInt", "from pydantic import StrictInt");
        imports.put("StrictStr", "from pydantic import StrictStr");
        imports.put("StrictBool", "from pydantic import StrictBool");
        imports.put("StrictFloat", "from pydantic import StrictFloat");
        // stdlib ipaddress for ipv4/ipv6 formats
        imports.put("IPv4Address", "from ipaddress import IPv4Address");
        imports.put("IPv6Address", "from ipaddress import IPv6Address");
        TYPE_IMPORTS = Map.copyOf(imports);
    }

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

        // Item 8: pydantic 2 strict primitives — kills lax JSON coercion
        typeMapping.put("integer", "StrictInt");
        typeMapping.put("long", "StrictInt");
        typeMapping.put("float", "StrictFloat");
        typeMapping.put("double", "StrictFloat");
        // A bare `type: number` (no `format`) is a decimal-precision surface
        // per the typed-everywhere policy: decimal.Decimal in Python (matching
        // BigDecimal in Java/Kotlin and branded Decimal in Node). `format:
        // float`/`format: double` keep StrictFloat above for IEEE-754 fields.
        typeMapping.put("number", "Decimal");
        typeMapping.put("boolean", "StrictBool");
        typeMapping.put("string", "StrictStr");
        typeMapping.put("byte", "bytes");
        typeMapping.put("binary", "bytes");
        typeMapping.put("ByteArray", "bytes");
        typeMapping.put("date", "date");
        // Item 5: format: date-time → AwareDatetime (rejects naive datetimes)
        typeMapping.put("DateTime", "AwareDatetime");
        typeMapping.put("time", "datetime.time");
        // format: duration → a pydantic Annotated alias (datetime.timedelta +
        // Before/PlainSerializer) so model_dump_json emits the protobuf-JSON
        // form "3600s" instead of pydantic's default ISO-8601 "PT1H". The
        // alias and its helpers live in the dependency-free `_duration` module.
        typeMapping.put("duration", "ProtobufDuration");
        typeMapping.put("UUID", "uuid.UUID");
        // Item 1: format: uri → pydantic.HttpUrl (uri-reference/uri-template
        // are downgraded back to StrictStr in postProcessModelProperty).
        typeMapping.put("URI", "HttpUrl");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "object");
        typeMapping.put("array", "List");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "Dict");
        typeMapping.put("file", "bytes");
        typeMapping.put("File", "bytes");
        typeMapping.put("decimal", "Decimal");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "float", "bool", "str", "bytes", "object",
                                "date", "datetime", "datetime.time", "datetime.timedelta",
                                // ProtobufDuration is the Annotated alias for
                                // format: duration; treated as a primitive so the
                                // generic-import machinery doesn't try to emit
                                // `from <pkg>.models.ProtobufDuration import ...`.
                                // Its real import comes from getPropertyTypeImportMap.
                                "ProtobufDuration",
                                "uuid.UUID", "List", "Dict", "Set",
                                "Tuple", "Optional",
                                // Pydantic 2 native types treated as primitives so the
                                // generic-import machinery does not try to emit
                                // `from petstore_client.models.HttpUrl import HttpUrl`.
                                "HttpUrl", "EmailStr", "SecretStr", "AwareDatetime",
                                "StrictInt", "StrictStr", "StrictBool", "StrictFloat",
                                "Decimal", "IPv4Address", "IPv6Address"));

        reservedWords = loadReservedWords("/reserved-words/python.txt");

        // Only carry an extras bag (`additional_properties` + capture
        // validator) on models whose schema *explicitly* declares
        // `additionalProperties`, matching every other SDK in this repo.
        // With this set to `true` (the upstream default), `isAdditionalProp-
        // ertiesTrue` is true only for those models (e.g. Metadata) and false
        // for plain object models (Pet, Order, Tag, Category) and for strict
        // models (StrictTag: unevaluatedProperties:false), which then get
        // pure `extra='forbid'` rejection with no contradictory capture bag.
        this.setDisallowAdditionalPropertiesIfNotPresent(true);
        this.setLegacyDiscriminatorBehavior(false);

        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_NAME,
                CodegenConstants.PACKAGE_NAME_DESC));
        cliOptions.add(CliOption.newString(CodegenConstants.PACKAGE_VERSION,
                "Version of the generated package (default: 1.0.0).").defaultValue("1.0.0"));
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "python-plus";
    }

    /**
     * Escapes a reserved word with a <em>trailing</em> underscore (PEP 8
     * convention: {@code class_}, {@code and_}), overriding the base
     * generator's leading-underscore scheme. A leading underscore makes the
     * attribute private as far as pydantic v2 is concerned — it refuses to
     * register a model field whose name starts with {@code _}, so a property
     * literally named {@code and}/{@code or}/{@code not} would crash at import
     * time. The wire name is preserved separately via the field's
     * {@code alias}, so only the Python attribute name changes.
     */
    @Override
    public String escapeReservedWord(String name) {
        return name + "_";
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
        return "python:3-slim@sha256:c845af9399020c7e562969a13689e929074a10fd057acd1b1fad06a2fb068e97";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {"pip install --quiet \"ruff>=0.15,<0.16\"", "ruff format ."};
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
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String pkg = Optional.ofNullable((String) additionalProperties.get("packageName"))
                .orElse(packageName);
        final String packagePath = pkg.replace('.', File.separatorChar);
        final String apiPath = Path.of(packagePath, "api").toString();
        final String modelPath = Path.of(packagePath, "models").toString();
        final String errorsPath = Path.of(packagePath, "errors").toString();
        final String authPath = Path.of(packagePath, "auth").toString();
        return List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("models/__init__.mustache", modelPath, "__init__.py"),
            new SupportingFileSpec("api/__init__.mustache", apiPath, "__init__.py"),
            new SupportingFileSpec("__init__.mustache", packagePath, "__init__.py"),
            new SupportingFileSpec("py_typed.mustache", packagePath, "py.typed"),
            new SupportingFileSpec("api_client.mustache", packagePath, "api_client.py"),
            new SupportingFileSpec("default_api_client.mustache", packagePath, "default_api_client.py"),
            new SupportingFileSpec("api_response.mustache", packagePath, "api_response.py"),
            new SupportingFileSpec("api_result.mustache", packagePath, "api_result.py"),
            new SupportingFileSpec("base_api.mustache", apiPath, "base_api.py"),
            new SupportingFileSpec("configuration.mustache", packagePath, "configuration.py"),
            new SupportingFileSpec("errors.mustache", errorsPath, "__init__.py"),
            new SupportingFileSpec("errors/client_exception.mustache", errorsPath, "client_exception.py"),
            new SupportingFileSpec("errors/server_exception.mustache", errorsPath, "server_exception.py"),
            new SupportingFileSpec("errors/bad_request_exception.mustache", errorsPath, "bad_request_exception.py"),
            new SupportingFileSpec("errors/unauthorized_exception.mustache", errorsPath, "unauthorized_exception.py"),
            new SupportingFileSpec("errors/forbidden_exception.mustache", errorsPath, "forbidden_exception.py"),
            new SupportingFileSpec("errors/not_found_exception.mustache", errorsPath, "not_found_exception.py"),
            new SupportingFileSpec("errors/conflict_exception.mustache", errorsPath, "conflict_exception.py"),
            new SupportingFileSpec("errors/unprocessable_entity_exception.mustache", errorsPath, "unprocessable_entity_exception.py"),
            new SupportingFileSpec("errors/internal_server_error_exception.mustache", errorsPath, "internal_server_error_exception.py"),
            new SupportingFileSpec("_duration.mustache", packagePath, "_duration.py"),
            new SupportingFileSpec("object_serializer.mustache", packagePath, "object_serializer.py"),
            new SupportingFileSpec("value_serializer.mustache", packagePath, "value_serializer.py"),
            new SupportingFileSpec("header_selector.mustache", packagePath, "header_selector.py"),
            new SupportingFileSpec("trace_context_util.mustache", packagePath, "trace_context_util.py"),
            new SupportingFileSpec("transport_options.mustache", packagePath, "transport_options.py"),
            new SupportingFileSpec("server_configuration.mustache", packagePath, "server_configuration.py"),
            new SupportingFileSpec("servers.mustache", packagePath, "servers.py"),
            new SupportingFileSpec("auth/__init__.mustache", authPath, "__init__.py"),
            new SupportingFileSpec("authenticator.mustache", authPath, "authenticator.py"),
            new SupportingFileSpec("auth/base_authenticator.mustache", authPath, "base_authenticator.py"),
            new SupportingFileSpec("pyproject_toml.mustache", "", "pyproject.toml"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore")
        );
    }

    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion =
                getPropertyOrDefault(CodegenConstants.PACKAGE_VERSION, packageVersion);
        additionalProperties.put("userAgentDefault", packageName + "/" + packageVersion + " (python)");

        modelPackage = packageName + ".models";
        apiPackage = packageName + ".api";

        final String packagePath = packageName.replace('.', File.separatorChar);

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", packagePath, clientClassFile + ".py"));

        // Spec-independent unit tests: they reference only runtime plumbing
        // (serializer, transport, header-selector, configuration, client), never
        // spec-derived models or APIs, so they compile against any generated SDK.
        // Emitted for the full golden suite and for real clients that opt in via
        // generateUnitTests.
        if (emitUnitTests()) {
            supportingFiles.add(new SupportingFile("test/conftest.py", "", "conftest.py"));
            supportingFiles.add(
                    new SupportingFile("test/tests_init.py", "test", "__init__.py"));
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
                            "test/test_configuration.mustache",
                            "test",
                            "test_configuration.py"));
            // ServerConfiguration/ServerVariable/ApiResult are spec-independent
            // runtime plumbing (server_configuration.py, api_result.py), so their
            // tests compile against any generated SDK and ship alongside the other
            // unit tests.
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_server_configuration.mustache",
                            "test",
                            "test_server_configuration.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_server_variable.mustache",
                            "test",
                            "test_server_variable.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_api_result.mustache",
                            "test",
                            "test_api_result.py"));
        }

        // Petstore-coupled tests: they import spec-derived models/APIs
        // (Category, OrderStatusEnum, EdgeCases, PetApi, …) that exist only in
        // the petstore golden, so they are emitted only for the generator's own
        // golden validation, never shipped into real clients.
        if (generateTests) {
            final String testApiPath = Path.of("test", "api").toString();
            supportingFiles.add(
                    new SupportingFile("test/api_init.py", testApiPath, "__init__.py"));
            // Chasm/squid container transport integration test — needs the
            // generator's fixture harness (openapi.yaml, certs, squid.conf)
            // written by writeTestFixtures(), so it is golden-only.
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_default_api_client.mustache",
                            "test",
                            "test_default_api_client.py"));
            // Exercises every generated authenticator (ApiKey/Bearer/OAuth2…)
            // whose set varies by the spec's security schemes — golden-only.
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_client.mustache",
                            "test",
                            "test_client.py"));
            // Standalone authenticator tests, emitted only when the spec's
            // security schemes produce the corresponding authenticator (mirrors
            // the gating of BearerAuthenticatorTest/ApiKeyAuthenticatorTest in
            // BetterJavaCodegen and of test_basic_authenticator above).
            if (hasBearerAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/test_bearer_authenticator.mustache",
                                "test",
                                "test_bearer_authenticator.py"));
            }
            if (hasApiKeyAuth) {
                supportingFiles.add(
                        new SupportingFile(
                                "test/test_api_key_authenticator.mustache",
                                "test",
                                "test_api_key_authenticator.py"));
            }
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/test_pet_api.mustache",
                            testApiPath,
                            "test_pet_api.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/test_store_api.mustache",
                            testApiPath,
                            "test_store_api.py"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_object_serializer.mustache",
                            "test",
                            "test_object_serializer.py"));
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
            supportingFiles.add(
                    new SupportingFile(
                            "test/test_api_error.mustache",
                            "test",
                            "test_api_error.py"));
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

    /** {@inheritDoc} */
    @Override
    protected String getArrayTypeTemplate() {
        return "%1$s[%2$s]";
    }

    /** {@inheritDoc} */
    @Override
    protected String getMapTypeTemplate() {
        return "%1$s[%2$s, %3$s]";
    }

    /** {@inheritDoc} */
    @Override
    protected String getNullLiteral() {
        return "None";
    }

    /** {@inheritDoc} */
    @Override
    protected String getTrueLiteral() {
        return "True";
    }

    /** {@inheritDoc} */
    @Override
    protected String getFalseLiteral() {
        return "False";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "";
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
     * docstrings instead of block comments. Breaks triple-quote
     * sequences to prevent accidental docstring closure. Cannot
     * be standardized because other languages use block comments
     * (handled by the base class).
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
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema unaliased = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (unaliased.getDefault() != null) {
            if (ModelUtils.isBooleanSchema(unaliased)) {
                return Boolean.parseBoolean(unaliased.getDefault().toString()) ? "True" : "False";
            }
            if (ModelUtils.isStringSchema(unaliased)) {
                String val = unaliased.getDefault().toString();
                return "'" + val.replace("'", "\\'") + "'";
            }
            return unaliased.getDefault().toString();
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("int", "float", "StrictInt", "StrictFloat", "Decimal");
    }

    /**
     * Overrides the base class to honour OAS {@code format} hints that the
     * default typeMapping cannot express (one mapping per OAS type only).
     *
     * <ul>
     *   <li>{@code format: uri-reference} / {@code uri-template} downgrade
     *       {@link io.github.mridang.codegen.generators.AbstractBetterCodegen#keepStringForUriSubformats}
     *       the type from {@code HttpUrl} back to {@code str} — these may
     *       be relative or contain RFC 6570 placeholders.</li>
     *   <li>{@code format: email} → {@code EmailStr} (Item 2).</li>
     *   <li>{@code format: password} → {@code SecretStr} (Item 3) — redacts
     *       in {@code __repr__}/{@code __str__}.</li>
     *   <li>{@code format: ipv4} → stdlib {@code ipaddress.IPv4Address}
     *       (Item 4).</li>
     *   <li>{@code format: ipv6} → stdlib {@code ipaddress.IPv6Address}
     *       (Item 4).</li>
     * </ul>
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        // Item 1 — uri subformat guard. The typeMapping rewrites `URI` to
        // `HttpUrl`; revert to `str` for non-absolute subformats. Use the
        // strict primitive so that the lax-coercion guarantee is preserved.
        keepStringForUriSubformats(property, "StrictStr");

        final String fmt = property.dataFormat;
        if (fmt == null) {
            return;
        }
        switch (fmt) {
            case "email":
                property.dataType = "EmailStr";
                property.datatypeWithEnum = "EmailStr";
                break;
            case "password":
                property.dataType = "SecretStr";
                property.datatypeWithEnum = "SecretStr";
                break;
            case "ipv4":
                property.dataType = "IPv4Address";
                property.datatypeWithEnum = "IPv4Address";
                break;
            case "ipv6":
                property.dataType = "IPv6Address";
                property.datatypeWithEnum = "IPv6Address";
                break;
            default:
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    protected boolean sanitizesExampleValues() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void fixEnumDefaultValue(CodegenProperty prop, CodegenModel model) {
        if (prop.defaultValue != null
                && prop.defaultValue.contains(".")
                && !prop.defaultValue.startsWith("'")
                && !prop.defaultValue.startsWith(model.classname)) {
            prop.defaultValue = model.classname + prop.defaultValue;
        }
    }

    /*
     * Overrides the base class to resolve Python-specific type
     * imports ({@code from datetime import datetime}, etc.).
     * The base class handles this via {@link #buildsFqnModelImports()} and
     * {@link #getPropertyTypeImportMap()}.
     */

    /** {@inheritDoc} */
    @Override
    protected boolean buildsFqnModelImports() {
        return true;
    }

    /**
     * Enables Gap K so polymorphic subtype Pydantic models auto-emit
     * the discriminator field with a {@code default='...'} on Field().
     * Without this, callers passing only the non-tag fields hit a
     * Pydantic ValidationError on the missing required tag.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected Map<String, String> getPropertyTypeImportMap() {
        // ProtobufDuration's import is package-qualified, so it cannot live in
        // the static TYPE_IMPORTS map (packageName is only known per-run).
        // Augment the static map with it here so every `format: duration` model
        // field gets `from <pkg>._duration import ProtobufDuration` emitted.
        final Map<String, String> map = new HashMap<>(TYPE_IMPORTS);
        map.put(
                "ProtobufDuration",
                "from " + packageName + "._duration import ProtobufDuration");
        return map;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean emitsBaseAuthenticator() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        return Path.of(packageName.replace('.', File.separatorChar), "auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return stem + ".py";
    }

    /** {@inheritDoc} */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();

        // Python needs an explicit __init__.py for the oauth sub-package.
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/__init__.mustache", getOAuthDir(), "__init__.py"));
        }
    }

    /** {@inheritDoc} */
    @Override
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        return List.of(
                new OAuthTestFileSpec("test/test_basic_authenticator.mustache", "test", "test_basic_authenticator.py", OAuthTestCondition.BASIC),
                new OAuthTestFileSpec("test/test_oauth2_token_manager.mustache", "test", "test_oauth2_token_manager.py", OAuthTestCondition.ANY_OAUTH2_OR_OIDC),
                new OAuthTestFileSpec("test/test_oauth2_auth_code_authenticator.mustache", "test", "test_oauth2_auth_code_authenticator.py", OAuthTestCondition.AUTH_CODE),
                new OAuthTestFileSpec("test/test_oauth2_implicit_authenticator.mustache", "test", "test_oauth2_implicit_authenticator.py", OAuthTestCondition.IMPLICIT),
                new OAuthTestFileSpec("test/test_oauth2_client_credentials_authenticator.mustache", "test", "test_oauth2_client_credentials_authenticator.py", OAuthTestCondition.CLIENT_CREDENTIALS),
                new OAuthTestFileSpec("test/test_oauth2_password_authenticator.mustache", "test", "test_oauth2_password_authenticator.py", OAuthTestCondition.PASSWORD),
                new OAuthTestFileSpec("test/test_openid_connect_authenticator.mustache", "test", "test_openid_connect_authenticator.py", OAuthTestCondition.OIDC));
    }

    /** {@inheritDoc} */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("imports", buildPythonImports(spec));
        ctx.put("constructorParams", buildPythonConstructorParams(spec));
        ctx.put("superArgs", buildPythonSuperArgs(spec));
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    /**
     * Maps base-class names whose snake_case derivation would be wrong (OAuth2* → o_auth2_*,
     * OpenIdConnect* → open_id_connect_*) to the actual module filenames used in the templates.
     */
    private static final Map<String, String> PYTHON_MODULE_LOOKUP = Map.of(
            "OAuth2TokenManager",                     "oauth2_token_manager",
            "OAuth2ClientCredentialsAuthenticator",   "oauth2_client_credentials_authenticator",
            "OAuth2PasswordAuthenticator",            "oauth2_password_authenticator",
            "OAuth2AuthorizationCodeAuthenticator",   "oauth2_auth_code_authenticator",
            "OAuth2ImplicitAuthenticator",            "oauth2_implicit_authenticator",
            "OpenIdConnectAuthenticator",             "openid_connect_authenticator"
    );

    private static List<Map<String, String>> buildPythonImports(SchemeAuthSpec spec) {
        final String bc = spec.baseClass();
        final String stem = PYTHON_MODULE_LOOKUP.getOrDefault(bc, NamingConvention.SNAKE_CASE.apply(bc));
        final String moduleFile = "." + stem;
        final List<Map<String, String>> imports = new ArrayList<>();
        final Map<String, String> imp = new HashMap<>();
        imp.put("module", moduleFile);
        imp.put("classNames", bc);
        imports.add(imp);
        if ("ApiKeyAuthenticator".equals(bc)) {
            final Map<String, String> loc = new HashMap<>();
            loc.put("module", ".api_key_location");
            loc.put("classNames", "ApiKeyLocation");
            imports.add(loc);
        }
        return imports;
    }

    private static List<Map<String, String>> buildPythonConstructorParams(SchemeAuthSpec spec) {
        final List<Map<String, String>> params = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, String> param = new HashMap<>();
            param.put("name", NamingConvention.SNAKE_CASE.apply(name));
            param.put("type", "str");
            params.add(param);
        }
        return params;
    }

    @SuppressWarnings("StringConcatenationMissingWhitespace")
    private static List<String> buildPythonSuperArgs(SchemeAuthSpec spec) {
        final String scopes = formatPythonScopes(spec.scopes());
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of(
                    "host",
                    "\"" + spec.keyParamName() + "\"",
                    "api_key",
                    "ApiKeyLocation." + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            return List.of(
                    "host", "client_id", "client_secret", "\"" + spec.tokenUrl() + "\"", scopes);
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg =
                    spec.refreshUrl() != null ? "\"" + spec.refreshUrl() + "\"" : "None";
            return List.of(
                    "host",
                    "client_id",
                    "client_secret",
                    "\"" + spec.tokenUrl() + "\"",
                    "username",
                    "password",
                    scopes,
                    refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg =
                    spec.refreshUrl() != null ? "\"" + spec.refreshUrl() + "\"" : "None";
            return List.of(
                    "host",
                    "client_id",
                    "client_secret",
                    "\"" + spec.authorizationUrl() + "\"",
                    "\"" + spec.tokenUrl() + "\"",
                    "redirect_uri",
                    scopes,
                    refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            return List.of(
                    "host", "client_id", "\"" + spec.authorizationUrl() + "\"", scopes);
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of(
                    "host",
                    "\"" + spec.openIdConnectUrl() + "\"",
                    "client_id",
                    "client_secret",
                    "redirect_uri",
                    "[]");
        }
        return List.of();
    }

    private static String formatPythonScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\", \"", scopes.keySet()) + "\"]";
    }

    /** {@inheritDoc} */
    @Override
    protected List<FileContentFixup> getFileContentFixups() {
        return List.of(new FileContentFixup(
                ".py",
                Pattern.compile("\\{ ('.*?') }"),
                "{$1}"));
    }

    /**
     * Adds a Python-specific import statement for a data
     * type if it requires one. Handles datetime, date, and
     * Decimal types that need explicit Python imports.
     */
    private static void addTypeImport(Set<String> imports, String dataType) {
        final String imp = TYPE_IMPORTS.get(dataType);
        if (imp != null) {
            imports.add(imp);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final TreeSet<String> importSet = new TreeSet<>();
        importSet.add("from dataclasses import dataclass");
        // The optional per-operation `auth` field is typed Optional[Authenticator],
        // so authed operations always need the Optional import even when they
        // carry no optional query/header/form/cookie params.
        boolean needsOptional = op.hasAuthMethods;
        final List<String> typingNames = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            if (!p.required) {
                needsOptional = true;
            }
            if (p.dataType != null) {
                if (p.dataType.startsWith("List[") && !typingNames.contains("List")) {
                    typingNames.add("List");
                }
                if (p.dataType.startsWith("Dict[") && !typingNames.contains("Dict")) {
                    typingNames.add("Dict");
                }
                addTypeImport(importSet, p.dataType);
            }
        }
        // Add model type imports for non-primitive types referenced by parameters
        for (final CodegenParameter p : optionsParams) {
            if (!p.isArray && !p.isMap && !p.isPrimitiveType
                    && p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)
                    && !TYPE_IMPORTS.containsKey(p.baseType)) {
                importSet.add(
                        "from "
                                + modelPackage
                                + "."
                                + toModelFilename(p.baseType)
                                + " import "
                                + p.baseType);
            }
            // A $ref to a top-level enum carries its type name in dataType
            // (baseType is null); import its generated model so the annotation
            // resolves.
            if (p.isEnumRef
                    && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)
                    && !TYPE_IMPORTS.containsKey(p.dataType)) {
                importSet.add(
                        "from "
                                + modelPackage
                                + "."
                                + toModelFilename(p.dataType)
                                + " import "
                                + p.dataType);
            }
            if (p.items != null
                    && p.items.baseType != null
                    && !p.items.isPrimitiveType
                    && !languageSpecificPrimitives.contains(p.items.baseType)
                    && !TYPE_IMPORTS.containsKey(p.items.baseType)) {
                importSet.add(
                        "from "
                                + modelPackage
                                + "."
                                + toModelFilename(p.items.baseType)
                                + " import "
                                + p.items.baseType);
            }
        }
        if (needsOptional && !typingNames.contains("Optional")) {
            typingNames.add(0, "Optional");
        }
        if (!typingNames.isEmpty()) {
            importSet.add("from typing import " + String.join(", ", typingNames));
        }

        final List<Map<String, Object>> requiredParams = new ArrayList<>();
        final List<Map<String, Object>> optionalParams = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("dataType", p.dataType);
            if (p.required) {
                requiredParams.add(param);
            } else {
                optionalParams.add(param);
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("operationId", op.operationId);
        context.put("imports", new ArrayList<>(importSet));
        context.put("requiredParams", requiredParams);
        context.put("optionalParams", optionalParams);
        injectAuthFieldContext(op, context);
        context.put(
                "authImport",
                "from " + packageName + ".auth.authenticator import "
                        + getAuthenticatorTypeName());
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(
                        outputFolder,
                        packageName.replace('.', '/'),
                        "api",
                        "options",
                        fileName + ".py")
                .toString();
    }

    /** {@inheritDoc} */
    @Override
    public void emitBarrelFiles(List<Map<String, String>> optionsFiles) {
        final List<Map<String, String>> exports = new ArrayList<>();
        for (final Map<String, String> meta : optionsFiles) {
            final String className =
                    Objects.requireNonNull(meta.get("optionsClassName"));
            final Map<String, String> export = new HashMap<>();
            export.put("className", className);
            export.put("moduleName", NamingConvention.SNAKE_CASE.apply(className));
            exports.add(export);
        }
        final Map<String, Object> context = new HashMap<>();
        context.put("exports", exports);
        final String content = renderOptionsTemplate("api/options_init.mustache", context);
        final String initPath =
                Path.of(
                                outputFolder,
                                packageName.replace('.', '/'),
                                "api",
                                "options",
                                "__init__.py")
                        .toString();
        writeFile(initPath, content);
    }
}

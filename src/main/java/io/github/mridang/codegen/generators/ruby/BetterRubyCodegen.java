package io.github.mridang.codegen.generators.ruby;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.underscore;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a Ruby API client that uses Net::HTTP for transport
 * and Dry::Struct for model deserialization. All identifiers use
 * snake_case per Ruby convention, and the output directory layout
 * follows Zeitwerk autoloading rules so that the generated gem
 * can be loaded without explicit requires. RBS type-signature
 * files are emitted alongside source files and then relocated
 * under {@code sig/} during post-processing. The formatter pass
 * invokes RuboCop inside Docker to enforce layout rules.
 */
@SuppressWarnings("unused")
public class BetterRubyCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRubyCodegen.class);

    private static final String GEM_VERSION = "1.0.0";
    private static final String LIB_FOLDER = "lib";

    private static final NamingConvention VAR_CASING = NamingConvention.SNAKE_CASE;
    private static final NamingConvention OPERATION_ID_CASING = NamingConvention.SNAKE_CASE;
    private static final NamingConvention ENUM_CASING = NamingConvention.UPPER_SNAKE_CASE;
    private static final NamingConvention FILENAME_CASING = NamingConvention.SNAKE_CASE;

    @Nullable protected String gemName;
    protected String moduleName = "Opigen::Client";

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Ruby language. Type mappings convert OpenAPI
     * types to their Ruby equivalents (e.g. integer to Integer,
     * DateTime to Time). Reserved words are loaded from a bundled
     * word-list to avoid generating identifiers that clash with
     * Ruby keywords.
     */
    public BetterRubyCodegen() {
        outputFolder = Path.of("generated-code", "ruby").toString();
        embeddedTemplateDir = templateDir = "templates/ruby";

        modelTemplateFiles.put("models/model.mustache", ".rb");
        modelTemplateFiles.put("models/model_rbs.mustache", ".rbs");
        apiTemplateFiles.put("api/api.mustache", ".rb");
        apiTemplateFiles.put("api/api_rbs.mustache", ".rbs");

        modelPackage = "models";
        apiPackage = "api";

        typeMapping.put("string", "String");
        typeMapping.put("boolean", "Boolean");
        typeMapping.put("char", "String");
        typeMapping.put("int", "Integer");
        typeMapping.put("integer", "Integer");
        typeMapping.put("long", "Integer");
        typeMapping.put("short", "Integer");
        typeMapping.put("float", "Float");
        typeMapping.put("double", "Float");
        typeMapping.put("number", "Float");
        typeMapping.put("decimal", "Float");
        typeMapping.put("date", "Date");
        typeMapping.put("DateTime", "Time");
        typeMapping.put("array", "Array");
        typeMapping.put("set", "Array");
        typeMapping.put("List", "Array");
        typeMapping.put("map", "Hash");
        typeMapping.put("object", "Object");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("file", "File");
        typeMapping.put("File", "File");
        typeMapping.put("binary", "String");
        typeMapping.put("ByteArray", "String");
        typeMapping.put("UUID", "String");
        typeMapping.put("URI", "String");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "String", "Boolean", "Integer", "Float", "Date", "Time",
                                "Array", "Hash", "File", "Object"));

        instantiationTypes.put("map", "Hash");
        instantiationTypes.put("array", "Array");

        reservedWords = loadReservedWords("/reserved-words/ruby.txt");
    }

    /**
     * Returns the generator name used to select this codegen on
     * the command line via the {@code -g} flag.
     */
    @Override
    public String getName() {
        return "ruby-plus";
    }

    /**
     * Returns a short description shown in the generator list
     * and help output.
     */
    @Override
    public String getHelp() {
        return "Generates a minimal Ruby client with Faraday.";
    }

    /**
     * Declares Ruby as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.RUBY;
    }

    /**
     * Returns the subdirectory under the test-projects resource
     * tree that holds the Ruby test fixtures.
     */
    @Override
    protected String getTestFixturesDir() {
        return "test/fixtures";
    }

    /**
     * Returns the directory name where user-written spec tests
     * are placed inside the generated project.
     */
    @Override
    protected String getSpecDir() {
        return "spec";
    }

    /**
     * Resolves gem name and module path, then registers all
     * supporting files: gem entry-point, configuration classes,
     * error hierarchy, serializers, authenticators, test harness,
     * RuboCop config, Steepfile, and Makefile. File paths use
     * the Zeitwerk-compatible snake_case layout under lib/.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        moduleName = getPropertyOrDefault(CodegenConstants.MODULE_NAME, moduleName);
        if (additionalProperties.containsKey(CodegenConstants.GEM_NAME)) {
            gemName = (String) additionalProperties.get(CodegenConstants.GEM_NAME);
        }
        if (gemName == null) {
            gemName = underscore(moduleName.replaceAll("[^\\w]+", ""));
        }
        additionalProperties.put(CodegenConstants.GEM_NAME, gemName);
        additionalProperties.put("gemVersion", GEM_VERSION);
        additionalProperties.put("userAgentDefault", gemName + "/" + GEM_VERSION + " (ruby)");

        setModelPackage("models");
        setApiPackage("api");

        final String modulePath = underscore(moduleName.replaceAll("::", "/"));
        final String libPath = Path.of(LIB_FOLDER, modulePath).toString();

        supportingFiles.add(new SupportingFile("gem.mustache", LIB_FOLDER, gemName + ".rb"));
        supportingFiles.add(new SupportingFile("configuration.mustache", libPath, "configuration.rb"));
        supportingFiles.add(new SupportingFile("transport_options.mustache", libPath, "transport_options.rb"));
        supportingFiles.add(new SupportingFile("server_configuration.mustache", libPath, "server_configuration.rb"));
        supportingFiles.add(new SupportingFile("servers.mustache", libPath, "servers.rb"));
        supportingFiles.add(new SupportingFile("api_error.mustache", libPath, "api_error.rb"));

        final String errorsPath = Path.of(libPath, "errors").toString();
        supportingFiles.add(
                new SupportingFile(
                        "errors/client_error.mustache", errorsPath, "client_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/server_error.mustache", errorsPath, "server_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/bad_request_error.mustache",
                        errorsPath,
                        "bad_request_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unauthorized_error.mustache",
                        errorsPath,
                        "unauthorized_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/forbidden_error.mustache", errorsPath, "forbidden_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/not_found_error.mustache", errorsPath, "not_found_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/conflict_error.mustache", errorsPath, "conflict_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unprocessable_entity_error.mustache",
                        errorsPath,
                        "unprocessable_entity_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/internal_server_error.mustache",
                        errorsPath,
                        "internal_server_error.rb"));
        supportingFiles.add(new SupportingFile("version.mustache", libPath, "version.rb"));
        supportingFiles.add(new SupportingFile("header_selector.mustache", libPath, "header_selector.rb"));
        supportingFiles.add(new SupportingFile("object_serializer.mustache", libPath, "object_serializer.rb"));
        supportingFiles.add(new SupportingFile("value_serializer.mustache", libPath, "value_serializer.rb"));
        supportingFiles.add(new SupportingFile("trace_context_util.mustache", libPath, "trace_context_util.rb"));
        supportingFiles.add(new SupportingFile("api_response.mustache", libPath, "api_response.rb"));
        supportingFiles.add(new SupportingFile("api_result.mustache", libPath, "api_result.rb"));
        supportingFiles.add(new SupportingFile("api_client.mustache", libPath, "api_client.rb"));
        supportingFiles.add(new SupportingFile("default_api_client.mustache", libPath, "default_api_client.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache", Path.of(libPath, "api").toString(), "base_api.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "authenticator.mustache",
                        Path.of(libPath, "auth").toString(),
                        "authenticator.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        Path.of(libPath, "auth").toString(),
                        "http_aware_authenticator.rb"));
        final String clientClassName = (String) additionalProperties.get("clientClassName");
        final String clientClassFile = underscore(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", libPath, clientClassFile + ".rb"));
        supportingFiles.add(new SupportingFile("gemfile.mustache", "", "Gemfile"));
        supportingFiles.add(new SupportingFile("rubocop.mustache", "", ".rubocop.yml"));
        supportingFiles.add(new SupportingFile("steepfile.mustache", "", "Steepfile"));
        supportingFiles.add(new SupportingFile("vendor_rbs.mustache", "sig", "vendor.rbs"));
        supportingFiles.add(
                new SupportingFile("infrastructure_rbs.mustache", "sig", "infrastructure.rbs"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("rakefile.mustache", "", "Rakefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
            supportingFiles.add(
                    new SupportingFile("test/test_helper.mustache", "test", "test_helper.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/pet_api_test.mustache",
                            Path.of("test", "Api").toString(),
                            "pet_api_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/store_api_test.mustache",
                            Path.of("test", "Api").toString(),
                            "store_api_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "test",
                            "default_api_client_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "test",
                            "default_api_client_unit_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "test",
                            "transport_options_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "test",
                            "header_selector_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "test",
                            "object_serializer_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "test",
                            "value_serializer_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "test",
                            "trace_context_util_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache",
                            "test",
                            "base_api_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache",
                            "test",
                            "metadata_test.rb"));
        }
    }

    /**
     * Builds the output directory for model source files. The
     * path follows the Zeitwerk layout: {@code lib/<module>/<modelPackage>}.
     */
    @Override
    public String modelFileFolder() {
        final String path = moduleName.replaceAll("::", "/");
        return Path.of(
                        getOutputDir(),
                        LIB_FOLDER,
                        underscore(path),
                        modelPackage().replace(".", File.separator))
                .toString();
    }

    /**
     * Builds the output directory for API source files. The
     * path follows the Zeitwerk layout: {@code lib/<module>/<apiPackage>}.
     */
    @Override
    public String apiFileFolder() {
        final String path = moduleName.replaceAll("::", "/");
        return Path.of(
                        getOutputDir(),
                        LIB_FOLDER,
                        underscore(path),
                        apiPackage().replace(".", File.separator))
                .toString();
    }

    /**
     * Returns the Ruby default value literal for the given
     * schema. Numeric and boolean defaults use their string
     * representation; string defaults are wrapped in single
     * quotes. All other types return null to omit the default.
     */
    @Nullable
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
                return "'" + escapeText(String.valueOf(resolved.getDefault())) + "'";
            }
        }
        return null;
    }

    /**
     * Converts a schema name to a PascalCase Ruby class name,
     * sanitizing invalid characters and prefixing reserved words
     * with "Model" to avoid keyword collisions.
     */
    @Override
    public String toModelName(String name) {
        final String sanitized = sanitizeName(name);
        String result = sanitized;
        if (isReservedWord(result)) {
            result = "Model" + result;
        }
        if (result.matches("^\\d.*")) {
            result = "model_" + result;
        }
        return camelize(result);
    }

    /**
     * Derives the model filename from the model name using
     * Zeitwerk conventions: each uppercase letter boundary
     * becomes an underscore separator.
     */
    @Override
    public String toModelFilename(String name) {
        return toZeitwerkFilename(toModelName(name));
    }

    /**
     * Derives the API filename from the API class name using
     * Zeitwerk conventions for consistent autoloading.
     */
    @Override
    public String toApiFilename(String name) {
        return toZeitwerkFilename(toApiName(name));
    }

    /**
     * Applies snake_case to variable names per Ruby convention.
     * All-uppercase constants (e.g. "HTTP_METHOD") are first
     * lowered to avoid being treated as constants by the
     * underscore helper.
     */
    @Override
    protected String applyVarNameCasing(String name) {
        if (name.matches("^[A-Z_]*$")) {
            final String lowered = name.toLowerCase(Locale.ROOT);
            return VAR_CASING.apply(lowered);
        }
        return VAR_CASING.apply(name);
    }

    /**
     * Formats an operation ID to snake_case. Reserved words are
     * prefixed with "call_" to avoid collisions with Ruby
     * built-in methods like {@code send} or {@code class}.
     */
    @Override
    protected String formatOperationId(String sanitizedOperationId) {
        if (isReservedWord(sanitizedOperationId)) {
            return OPERATION_ID_CASING.apply("call_" + sanitizedOperationId);
        }
        return OPERATION_ID_CASING.apply(sanitizedOperationId);
    }

    /**
     * Derives a snake_case property name for a client accessor
     * from the API class name. The trailing "Api" suffix is
     * stripped so that {@code PetApi} becomes {@code pet}.
     */
    @Override
    protected String deriveClientPropertyName(String apiClassName) {
        final String name = apiClassName.replaceAll("Api$", "");
        if (name.isEmpty()) {
            return "api";
        }
        return VAR_CASING.apply(name);
    }

    /**
     * Checks whether the given datatype represents a numeric
     * Ruby type (Integer or Float) so that enum values can
     * receive a numeric prefix.
     */
    @Override
    protected boolean isNumericEnumDatatype(String datatype) {
        return "Integer".equals(datatype) || "Float".equals(datatype);
    }

    /**
     * Converts a raw enum value into a Ruby UPPER_SNAKE_CASE
     * constant name. Numeric values are prefixed with
     * "NUMBER_" and special characters (minus, plus, dot)
     * are replaced with descriptive tokens. Empty strings
     * map to "EMPTY".
     */
    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value.isEmpty()) {
            return "EMPTY";
        }
        if ("Integer".equals(datatype) || "Float".equals(datatype)) {
            String varName = value;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return "NUMBER_" + varName;
        }
        final String sanitized = sanitizeName(ENUM_CASING.apply(value));
        String enumName = sanitized.replaceFirst("^_", "");
        enumName = enumName.replaceFirst("_$", "");
        if (enumName.matches("\\d.*")) {
            return "NUMBER_" + enumName;
        }
        return enumName;
    }

    /**
     * Strips single-quote characters from template output to
     * prevent broken Ruby string literals.
     */
    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("'", "");
    }

    /**
     * Escapes Ruby heredoc markers ({@code =begin}, {@code =end})
     * and string interpolation sequences ({@code \#\{}) to
     * prevent accidental code injection in generated comments
     * and string literals.
     */
    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("=end", "=_end").replace("=begin", "=_begin").replace("#{", "\\#{");
    }

    /**
     * Strips primitive parent types from models so that Ruby
     * models always extend Dry::Struct rather than inheriting
     * from a mapped primitive like Hash or String.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);

        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            stripPrimitiveParent(model);
        }

        return result;
    }

    /**
     * Registers Mustache lambdas for RBS type conversion and
     * generic stripping. The {@code rbsType} lambda converts
     * Ruby types to their RBS equivalents, {@code rbsApiType}
     * additionally qualifies model types with the Models
     * namespace, and {@code stripGenerics} removes angle-bracket
     * type parameters.
     */
    @Override
    protected ImmutableMap.Builder<String, Mustache.Lambda> addMustacheLambdas() {
        return super.addMustacheLambdas()
                .put(
                        "rbsType",
                        (fragment, writer) -> writer.write(toRbsType(fragment.execute())))
                .put(
                        "rbsApiType",
                        (fragment, writer) -> writer.write(toRbsApiType(fragment.execute())))
                .put(
                        "stripGenerics",
                        (fragment, writer) -> {
                            final String text = fragment.execute();
                            final int idx = text.indexOf('<');
                            writer.write(idx >= 0 ? text.substring(0, idx) : text);
                        })
                .put(
                        "camelize",
                        (fragment, writer) -> writer.write(camelize(fragment.execute())));
    }

    /**
     * Registers supporting files for each authentication scheme
     * present in the OpenAPI spec. Files are placed under the
     * {@code auth/} subdirectory within the module path.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String modulePath =
                org.openapitools.codegen.utils.StringUtils.underscore(
                        moduleName.replaceAll("::", "/"));
        final String libPath = Path.of(LIB_FOLDER, modulePath).toString();
        final String authPath = Path.of(libPath, "auth").toString();
        final String oauthPath = Path.of(authPath, "oauth").toString();

        if (hasBasicAuth) {
            supportingFiles.add(new SupportingFile("auth/basic_authenticator.mustache", authPath, "basic_authenticator.rb"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(new SupportingFile("auth/bearer_authenticator.mustache", authPath, "bearer_authenticator.rb"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(new SupportingFile("auth/api_key_authenticator.mustache", authPath, "api_key_authenticator.rb"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_token_manager.mustache", oauthPath, "oauth2_token_manager.rb"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_client_credentials_authenticator.mustache", oauthPath, "oauth2_client_credentials_authenticator.rb"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_password_authenticator.mustache", oauthPath, "oauth2_password_authenticator.rb"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_auth_code_authenticator.mustache", oauthPath, "oauth2_auth_code_authenticator.rb"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_implicit_authenticator.mustache", oauthPath, "oauth2_implicit_authenticator.rb"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/openid_connect_authenticator.mustache", oauthPath, "openid_connect_authenticator.rb"));
        }
    }

    /**
     * No-op for Ruby: per-scheme authenticators are handled
     * entirely through template logic rather than individual
     * generated classes.
     */
    @Override
    protected void generatePerSchemeAuthenticators(OpenAPI openAPI) {
        // Per-scheme authenticators are not generated for Ruby
    }

    /**
     * Moves generated RBS type-signature files from {@code lib/}
     * to the {@code sig/} directory so that Steep can find them
     * without polluting the runtime load path.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null || !file.getName().endsWith(".rbs")) {
            return;
        }

        final Path filePath = file.toPath();
        final Path outputDir = Path.of(getOutputDir());
        final Path relative = outputDir.relativize(filePath);
        final String relStr = relative.toString();

        if (relStr.startsWith(LIB_FOLDER + File.separator)) {
            final Path sigPath = outputDir.resolve("sig").resolve(relStr.substring(LIB_FOLDER.length() + 1));
            final Path sigParent = sigPath.getParent();
            if (sigParent == null) {
                return;
            }
            try {
                Files.createDirectories(sigParent);
                Files.move(filePath, sigPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Failed to move RBS file {} to {}: {}", filePath, sigPath, e.getMessage());
            }
        }
    }

    /**
     * Runs RuboCop inside a Docker container to auto-correct
     * layout violations in the generated Ruby source. The
     * vendor bundle is cleaned up after formatting to keep the
     * output directory lean.
     */
    @Override
    public void postProcess() {
        runFormatterInDocker(
                "ruby:3.4",
                "bundle config set --local path vendor/bundle",
                "bundle install --quiet",
                "bundle exec rubocop -A --only Layout",
                "rm -rf vendor .bundle");
    }

    /**
     * Converts a Ruby type string to its RBS equivalent for use
     * in API return-type signatures, qualifying non-primitive
     * types with the Models:: namespace and replacing generics
     * syntax.
     */
    private String toRbsApiType(@Nullable String type) {
        if (type == null) {
            return "void";
        }
        return qualifyModelTypes(type)
                .replace("Boolean", "bool")
                .replace("Object", "untyped")
                .replace("<", "[")
                .replace(">", "]");
    }

    /**
     * Walks a type string token-by-token, qualifying each
     * non-primitive type with the {@code Models::} prefix so
     * that RBS signatures resolve correctly within the gem
     * namespace.
     */
    private String qualifyModelTypes(String type) {
        final StringBuilder result = new StringBuilder();
        final StringBuilder token = new StringBuilder();
        for (int i = 0; i < type.length(); i++) {
            final char c = type.charAt(i);
            if (c == '<' || c == '>' || c == ',' || c == ' ') {
                if (token.length() > 0) {
                    result.append(qualifySingleType(token.toString()));
                    token.setLength(0);
                }
                result.append(c);
            } else {
                token.append(c);
            }
        }
        if (token.length() > 0) {
            result.append(qualifySingleType(token.toString()));
        }
        return result.toString();
    }

    /**
     * Prefixes a single type name with {@code Models::} unless
     * it is a language-specific primitive that needs no
     * qualification.
     */
    private String qualifySingleType(String type) {
        if (languageSpecificPrimitives.contains(type)) {
            return type;
        }
        return "Models::" + type;
    }

    /**
     * Converts a Ruby type string to its RBS equivalent by
     * replacing Boolean with bool, Object with untyped, and
     * angle brackets with square brackets.
     */
    private String toRbsType(@Nullable String type) {
        if (type == null) {
            return "void";
        }
        return type.replace("Boolean", "bool")
                .replace("Object", "untyped")
                .replace("<", "[")
                .replace(">", "]");
    }

    /**
     * Converts a PascalCase class name to a Zeitwerk-compatible
     * filename by inserting underscores before each uppercase
     * letter boundary and lowering the result.
     */
    private String toZeitwerkFilename(String name) {
        if (isBlank(name)) {
            return name;
        }
        final String result = name.replaceAll("([A-Z])", "_$1").replaceAll("^_", "");
        return result.toLowerCase(Locale.ROOT);
    }
}

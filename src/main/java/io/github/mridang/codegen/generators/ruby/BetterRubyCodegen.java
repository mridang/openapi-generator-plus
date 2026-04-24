package io.github.mridang.codegen.generators.ruby;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
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
        typeMapping.put("set", "Set");
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
                                "Array", "Set", "Hash", "File", "Object"));

        instantiationTypes.put("map", "Hash");
        instantiationTypes.put("array", "Array");

        reservedWords = loadReservedWords("/reserved-words/ruby.txt");
    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "ruby-plus";
    }

    /** Returns a short description shown in the help output. */
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

    /**
     * Returns {@code "Set<"} so that unique-item arrays are
     * emitted as Ruby {@code Set} instead of {@code Array}.
     * Ruby has {@code Set} in stdlib ({@code require 'set'}).
     */
    @Override
    protected String getUniqueItemsSetType() {
        return "Set<";
    }

    /**
     * Returns the regex matching the {@code Array} container
     * prefix so that it can be replaced with {@code Set} for
     * unique-item properties.
     */
    @Override
    protected String getArrayContainerPattern() {
        return "^Array";
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "ruby:3.4";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            "bundle config set --local path vendor/bundle",
            "bundle install --quiet",
            "bundle exec rubocop -A --only Layout",
            "rm -rf vendor .bundle"
        };
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
        gemName =
                Optional.ofNullable((String) additionalProperties.get(CodegenConstants.GEM_NAME))
                        .orElseGet(() -> NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("[^\\w]+", "")));
        additionalProperties.put(CodegenConstants.GEM_NAME, gemName);
        additionalProperties.put("gemVersion", GEM_VERSION);
        additionalProperties.put("userAgentDefault", gemName + "/" + GEM_VERSION + " (ruby)");

        setModelPackage("models");
        setApiPackage("api");

        final String modulePath = NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("::", "/"));
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
        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
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
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "test",
                            "composed_schema_test.rb"));
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
                        NamingConvention.SNAKE_CASE.apply(path),
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
                        NamingConvention.SNAKE_CASE.apply(path),
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
                return "'" + escapeText(String.valueOf(resolved.getDefault())) + "'";
            }
        }
        return null;
    }

    /**
     * Overrides the base class to use Zeitwerk autoloading
     * conventions for model filenames. Cannot be standardized
     * because Zeitwerk's inflection rules are Ruby-specific.
     */
    @Override
    public String toModelFilename(String name) {
        return toZeitwerkFilename(toModelName(name));
    }

    /**
     * Overrides the base class to use Zeitwerk autoloading
     * conventions for API filenames. Cannot be standardized
     * because Zeitwerk's inflection rules are Ruby-specific.
     */
    @Override
    public String toApiFilename(String name) {
        return toZeitwerkFilename(toApiName(name));
    }

    /**
     * Lowercases all-uppercase identifiers (e.g. {@code HTTP_METHOD})
     * before applying snake_case, to prevent Ruby's underscore
     * helper from treating them as constants and producing
     * unexpected casing. Cannot be standardized because Java
     * preserves these identifiers instead.
     */
    @Override
    protected UppercaseIdentifierStrategy getUppercaseIdentifierStrategy() {
        return UppercaseIdentifierStrategy.LOWERCASE_FIRST;
    }

    /** {@inheritDoc} */
    @Override
    protected String getOperationIdReservedPrefix() {
        return "call_";
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("Integer", "Float");
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
     * Overrides the base class because Ruby uses heredoc markers
     * ({@code =begin}/{@code =end}) and string interpolation
     * instead of block comments. Cannot be standardized because
     * other languages use block comments (handled by the base
     * class).
     */
    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("=end", "=_end").replace("=begin", "=_begin").replace("#{", "\\#{");
    }

    /**
     * Overrides the base class to add RBS type-conversion
     * lambdas ({@code rbsType}, {@code rbsApiType},
     * {@code stripGenerics}, {@code camelize}) for generating
     * Ruby type-signature files. Cannot be standardized because
     * RBS is Ruby-specific.
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
                        (fragment, writer) -> writer.write(NamingConvention.PASCAL_CASE.apply(fragment.execute())));
    }

    /**
     * Registers supporting files for each authentication scheme
     * present in the OpenAPI spec. Files are placed under the
     * {@code auth/} subdirectory within the module path.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String modulePath =
                NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("::", "/"));
        final String libPath = Path.of(LIB_FOLDER, modulePath).toString();
        final String authPath = Path.of(libPath, "auth").toString();
        final String oauthPath = Path.of(authPath, "oauth").toString();

        supportingFiles.add(new SupportingFile("auth/http_aware_authenticator.mustache", authPath, "http_aware_authenticator.rb"));

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
     * Overrides the base class to collapse consecutive blank lines
     * in generated {@code .rb} and {@code .rbs} files (a cosmetic
     * artefact of cascading Mustache section gates), and to move
     * {@code .rbs} type-signature files from {@code lib/} to
     * {@code sig/} as required by Ruby's Steep type-checking
     * tooling.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null) {
            return;
        }
        final String name = file.getName();
        final boolean isRb = name.endsWith(".rb");
        final boolean isRbs = name.endsWith(".rbs");
        if (!isRb && !isRbs) {
            return;
        }

        collapseBlankLines(file);

        if (isRbs) {
            moveRbsToSigDir(file);
        }
    }

    /**
     * Collapses runs of two or more consecutive blank lines into
     * a single blank line. This cleans up whitespace artefacts
     * produced by empty Mustache section iterations for operations
     * that have no options parameters.
     */
    private static void collapseBlankLines(File file) {
        try {
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            final List<String> result = new ArrayList<>(lines.size());
            boolean prevBlank = false;
            boolean changed = false;

            for (final String line : lines) {
                final boolean blank = line.trim().isEmpty();
                if (blank && prevBlank) {
                    changed = true;
                    continue;
                }
                result.add(line);
                prevBlank = blank;
            }

            if (changed) {
                Files.write(file.toPath(), result, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to collapse blank lines in {}: {}", file.getName(), e.getMessage());
        }
    }

    /**
     * Moves an {@code .rbs} file from {@code lib/} to {@code sig/}
     * as required by Ruby's Steep type-checking tooling.
     */
    private void moveRbsToSigDir(File file) {
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
     * Converts a Ruby type string to its RBS equivalent for use
     * in API return-type signatures, qualifying non-primitive
     * types with the Models:: namespace and replacing generics
     * syntax.
     */
    private String toRbsApiType(@Nullable String type) {
        return Optional.ofNullable(type)
                .map(
                        t ->
                                qualifyModelTypes(t)
                                        .replace("Boolean", "bool")
                                        .replace("Object", "untyped")
                                        .replace("<", "[")
                                        .replace(">", "]"))
                .orElse("void");
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
        return NamingConvention.SNAKE_CASE.apply(name);
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            params.add(param);
        }

        final StringBuilder sig = new StringBuilder();
        boolean first = true;
        for (final CodegenParameter p : optionsParams) {
            if (!first) sig.append(", ");
            first = false;
            sig.append(p.paramName).append(": nil");
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("moduleName", moduleName);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("initializeSignature", sig.toString());

        generateOptionsRbsFile(op, optionsParams, className);

        return renderOptionsTemplate("api/options.mustache", context);
    }

    /**
     * Qualifies an RBS type string with the {@code Models::} prefix
     * when the parameter's base type is a non-primitive model type.
     * Uses word-boundary-safe regex replacement to avoid matching
     * substrings of longer type names.
     */
    private String qualifyRbsModelType(String rbsType, CodegenParameter p) {
        if (p.baseType != null && !languageSpecificPrimitives.contains(p.baseType)
                && !rbsType.contains("Models::")) {
            return rbsType.replaceAll("\\b" + java.util.regex.Pattern.quote(p.baseType) + "\\b",
                    "Models::" + p.baseType);
        }
        return rbsType;
    }

    /**
     * Generates the RBS type-signature file for an Options class.
     * The file is written alongside the source file and relocated
     * to {@code sig/} by {@link #postProcessFile}.
     */
    private void generateOptionsRbsFile(
            CodegenOperation op,
            List<CodegenParameter> optionsParams,
            String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("rbsType", qualifyRbsModelType(toRbsType(p.dataType), p));
            param.put("required", p.required);
            params.add(param);
        }

        final StringBuilder sig = new StringBuilder();
        boolean first = true;
        for (final CodegenParameter p : optionsParams) {
            if (!first) sig.append(", ");
            first = false;
            final String rbsType = qualifyRbsModelType(toRbsType(p.dataType), p);
            sig.append('?').append(p.paramName).append(": ").append(rbsType)
                    .append('?');
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("moduleName", moduleName);
        context.put("params", params);
        context.put("initializeSignature", sig.toString());

        final String content = renderOptionsTemplate("api/options_rbs.mustache", context);

        final String modulePath =
                NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("::", "/"));
        final String rbsFileName = NamingConvention.SNAKE_CASE.apply(className);
        final String rbsPath =
                Path.of(
                                getOutputDir(),
                                LIB_FOLDER,
                                modulePath,
                                "api",
                                "options",
                                rbsFileName + ".rbs")
                        .toString();
        writeFile(rbsPath, content);
        postProcessFile(Path.of(rbsPath).toFile(), "source");
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String modulePath =
                NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("::", "/"));
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(getOutputDir(), LIB_FOLDER, modulePath, "api", "options", fileName + ".rb")
                .toString();
    }

    /** {@inheritDoc} */
    @Override
    protected void enrichOptionsMetadata(
            Map<String, String> meta, String operationId, String optionsClassName) {
        final String modulePath =
                NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("::", "/"));
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        meta.put("requirePath", modulePath + "/api/options/" + fileName);
    }
}

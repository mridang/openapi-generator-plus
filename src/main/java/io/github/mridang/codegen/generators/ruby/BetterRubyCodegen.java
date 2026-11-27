package io.github.mridang.codegen.generators.ruby;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.AbstractBetterCodegen.SchemeAuthSpec;
import io.github.mridang.codegen.generators.NamingConvention;
import io.github.mridang.codegen.generators.WithTypeSignatureSupport;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
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
public class BetterRubyCodegen extends AbstractBetterCodegen implements WithTypeSignatureSupport {

    @Override
    protected String errorSuffix() {
        return "Error";
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRubyCodegen.class);

    private static final String DEFAULT_GEM_VERSION = "1.0.0";
    private static final String DEFAULT_API_ERROR_PARENT = "StandardError";
    private static final String LIB_FOLDER = "lib";

    @Nullable protected String gemName;
    protected String moduleName = "Opigen::Client";

    /** The environment variables of every generated scheme authenticator, for its test. */
    private final List<Map<String, Object>> schemeEnvironmentEntries = new ArrayList<>();

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
        typeMapping.put("number", "BigDecimal");
        typeMapping.put("decimal", "BigDecimal");
        typeMapping.put("ipv4", "IPAddr");
        typeMapping.put("ipv6", "IPAddr");
        typeMapping.put("date", "Date");
        typeMapping.put("DateTime", "Time");
        // 4.8: format: time → tod gem (Tod::TimeOfDay) — no native
        // time-of-day type in Ruby stdlib; tod is the canonical small,
        // focused, well-maintained library for this surface.
        typeMapping.put("time", "Tod::TimeOfDay");
        // 4.8: format: duration → iso8601 gem (ISO8601::Duration) — Ruby
        // stdlib has no ISO-8601 duration parser; iso8601 is small and
        // well-maintained, far lighter than ActiveSupport::Duration.
        typeMapping.put("duration", "ISO8601::Duration");
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
                                "String", "Boolean", "Integer", "Float", "BigDecimal", "IPAddr", "Date", "Time",
                                "Tod::TimeOfDay", "ISO8601::Duration",
                                "Array", "Set", "Hash", "File", "Object"));

        instantiationTypes.put("map", "Hash");
        instantiationTypes.put("array", "Array");

        reservedWords = loadReservedWords("/reserved-words/ruby.txt");

        cliOptions.add(CliOption.newString(CodegenConstants.GEM_NAME,
                CodegenConstants.GEM_NAME_DESC));
        cliOptions.add(CliOption.newString("gemVersion",
                "Version of the generated gem (default: 1.0.0).")
                .defaultValue("1.0.0"));
        cliOptions.add(CliOption.newString(CodegenConstants.MODULE_NAME,
                CodegenConstants.MODULE_NAME_DESC));
        cliOptions.add(CliOption.newString("authors",
                "Comma-separated authors written to the gemspec (default: the API title)."));
        cliOptions.add(CliOption.newString("repositoryUrl",
                "Source repository URL, written to the gemspec as source_code_uri."));
        cliOptions.add(CliOption.newString("homepageUrl",
                "Home page URL, written to the gemspec as homepage."));
        cliOptions.add(CliOption.newString("documentationUrl",
                "Documentation URL, written to the gemspec as documentation_uri."));
        cliOptions.add(CliOption.newString("issueTrackerUrl",
                "Issue tracker URL, written to the gemspec as bug_tracker_uri."));
        cliOptions.add(CliOption.newString("apiErrorParent",
                "Fully-qualified superclass for the generated ApiError, and the SDK-wide "
                        + "error base under which all serialization/HTTP errors are rooted. "
                        + "Set this to a hand-written base so a single `rescue <base>` "
                        + "catches every SDK error. Defaults to a generated "
                        + "<moduleName>::<errorPrefix>Error root.")
                .defaultValue(DEFAULT_API_ERROR_PARENT));
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
        return "ruby:3.2@sha256:d3bcbd845d26ae1efafcc987f641aa9ac796267b9b857e0f196a2b05070c8330";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {
            // Start from a clean install dir so a leftover vendor/ or
            // Gemfile.lock (e.g. from an integration-test run sharing this
            // directory, or a previous formatter attempt) cannot poison the
            // bundle install.
            "rm -rf vendor .bundle Gemfile.lock",
            "bundle config set --local path /cache/bundle",
            "bundle install --quiet",
            // rubocop -A exits 1 when it auto-corrects offenses and 2 on a real
            // error; tolerate only the success-with-corrections case so genuine
            // failures still fail loud (subshell isolates $? from the && chain).
            //
            // Beyond Layout, the cops named here are the ones a fixed Mustache
            // template cannot get right on its own: an operation with no
            // parameters renders `def x()` from the same line that renders `def
            // x(a, b)`, a hash whose last entry is conditional renders a
            // trailing comma, and so on. Correcting them at generation time is
            // what lets .rubocop.yml stop excluding them, so the cops stay live
            // and a genuine violation still fails RubyLintingSpec. The list is
            // an allowlist: every cop on it was verified to leave the tree with
            // no offence of its own and no new offence from another cop.
            "( bundle exec rubocop -A --cache false --only Layout"
                    + ",Minitest/AssertMatch"
                    + ",Minitest/AssertPredicate"
                    + ",Minitest/RefuteRespondTo"
                    + ",Style/DefWithParentheses"
                    + ",Style/IfUnlessModifier"
                    + ",Style/Lambda"
                    + ",Style/MethodCallWithoutArgsParentheses"
                    + ",Style/RedundantBegin"
                    + ",Style/RedundantException"
                    + ",Style/RedundantInterpolation"
                    + ",Style/RegexpLiteral"
                    + ",Style/RescueModifier"
                    + ",Style/StringConcatenation"
                    + ",Style/StringLiterals"
                    + ",Style/TrailingCommaInHashLiteral"
                    + " || [ $? -eq 1 ] )",
            // The lock is left out of the tree: it names the formatter image's
            // platform, and the test image (musl) cannot install from it.
            "rm -rf vendor .bundle Gemfile.lock"
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
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        final String resolvedModuleName = Optional.ofNullable(
                (String) additionalProperties.get(CodegenConstants.MODULE_NAME))
                .orElse(moduleName);
        final String resolvedGemName = Optional.ofNullable(
                (String) additionalProperties.get(CodegenConstants.GEM_NAME))
                .orElseGet(() -> NamingConvention.SNAKE_CASE.apply(
                        resolvedModuleName.replaceAll("[^\\w]+", "")));
        final String modulePath = NamingConvention.SNAKE_CASE.apply(
                resolvedModuleName.replaceAll("::", "/"));
        final String libPath = Path.of(LIB_FOLDER, modulePath).toString();
        final String errorsPath = Path.of(libPath, "errors").toString();
        final List<SupportingFileSpec> specs = new ArrayList<>(List.of(
            new SupportingFileSpec("readme.mustache", "", "README.md"),
            new SupportingFileSpec("skills.mustache", "", "SKILLS.md"),
            new SupportingFileSpec("gem.mustache", LIB_FOLDER, resolvedGemName + ".rb"),
            new SupportingFileSpec("configuration.mustache", libPath, "configuration.rb"),
            new SupportingFileSpec("transport_options.mustache", libPath, "transport_options.rb"),
            new SupportingFileSpec("server_configuration.mustache", libPath, "server_configuration.rb"),
            new SupportingFileSpec("server_variable.mustache", libPath, "server_variable.rb"),
            new SupportingFileSpec("servers.mustache", libPath, "servers.rb"),
            new SupportingFileSpec("errors/api_error.mustache", errorsPath, "api_error.rb"),
            new SupportingFileSpec("errors/client_error.mustache", errorsPath, "client_error.rb"),
            new SupportingFileSpec("errors/server_error.mustache", errorsPath, "server_error.rb"),
            new SupportingFileSpec("errors/bad_request_error.mustache", errorsPath, "bad_request_error.rb"),
            new SupportingFileSpec("errors/unauthorized_error.mustache", errorsPath, "unauthorized_error.rb"),
            new SupportingFileSpec("errors/forbidden_error.mustache", errorsPath, "forbidden_error.rb"),
            new SupportingFileSpec("errors/not_found_error.mustache", errorsPath, "not_found_error.rb"),
            new SupportingFileSpec("errors/conflict_error.mustache", errorsPath, "conflict_error.rb"),
            new SupportingFileSpec("errors/payment_required_error.mustache", errorsPath, "payment_required_error.rb"),
            new SupportingFileSpec("errors/method_not_allowed_error.mustache", errorsPath, "method_not_allowed_error.rb"),
            new SupportingFileSpec("errors/request_timeout_error.mustache", errorsPath, "request_timeout_error.rb"),
            new SupportingFileSpec("errors/gone_error.mustache", errorsPath, "gone_error.rb"),
            new SupportingFileSpec("errors/payload_too_large_error.mustache", errorsPath, "payload_too_large_error.rb"),
            new SupportingFileSpec("errors/unsupported_media_type_error.mustache", errorsPath, "unsupported_media_type_error.rb"),
            new SupportingFileSpec("errors/locked_error.mustache", errorsPath, "locked_error.rb"),
            new SupportingFileSpec("errors/not_implemented_error.mustache", errorsPath, "not_implemented_error.rb"),
            new SupportingFileSpec("errors/bad_gateway_error.mustache", errorsPath, "bad_gateway_error.rb"),
            new SupportingFileSpec("errors/service_unavailable_error.mustache", errorsPath, "service_unavailable_error.rb"),
            new SupportingFileSpec("errors/gateway_timeout_error.mustache", errorsPath, "gateway_timeout_error.rb"),
            new SupportingFileSpec("errors/unprocessable_entity_error.mustache", errorsPath, "unprocessable_entity_error.rb"),
            new SupportingFileSpec("errors/too_many_requests_error.mustache", errorsPath, "too_many_requests_error.rb"),
            new SupportingFileSpec("errors/internal_server_error.mustache", errorsPath, "internal_server_error.rb"),
            new SupportingFileSpec("errors/network_error.mustache", errorsPath, "network_error.rb"),
            new SupportingFileSpec("errors/network_timeout_error.mustache", errorsPath, "network_timeout_error.rb"),
            new SupportingFileSpec("errors/stream_error.mustache", errorsPath, "stream_error.rb"),
            new SupportingFileSpec("errors/response_too_large_error.mustache", errorsPath, "response_too_large_error.rb"),
            new SupportingFileSpec("errors/pagination_error.mustache", errorsPath, "pagination_error.rb"),
            new SupportingFileSpec("errors/serialization_error.mustache", errorsPath, "serialization_error.rb"),
            new SupportingFileSpec("errors/oauth2_server_error.mustache", errorsPath, "oauth2_server_error.rb"),
            new SupportingFileSpec("errors/oauth2_token_error.mustache", errorsPath, "oauth2_token_error.rb"),
            new SupportingFileSpec("version.mustache", libPath, "version.rb"),
            new SupportingFileSpec("types.mustache", libPath, "types.rb"),
            new SupportingFileSpec("header_selector.mustache", libPath, "header_selector.rb"),
            new SupportingFileSpec("object_serializer.mustache", libPath, "object_serializer.rb"),
            new SupportingFileSpec("value_serializer.mustache", libPath, "value_serializer.rb"),
            new SupportingFileSpec("constraint_validator.mustache", libPath, "constraint_validator.rb"),
            new SupportingFileSpec("trace_context_util.mustache", libPath, "trace_context_util.rb"),
            new SupportingFileSpec("call_info.mustache", libPath, "call_info.rb"),
            new SupportingFileSpec("call_outcome.mustache", libPath, "call_outcome.rb"),
            new SupportingFileSpec("call_hook.mustache", libPath, "call_hook.rb"),
            new SupportingFileSpec("api_response.mustache", libPath, "api_http_response.rb"),
            new SupportingFileSpec("api_result.mustache", libPath, "api_result.rb"),
            new SupportingFileSpec("api_stream_response.mustache", libPath, "api_stream_response.rb"),
            new SupportingFileSpec("body_source.mustache", libPath, "body_source.rb"),
            new SupportingFileSpec("byte_stream.mustache", libPath, "byte_stream.rb"),
            new SupportingFileSpec("stream_body.mustache", libPath, "stream_body.rb"),
            new SupportingFileSpec("event_stream.mustache", libPath, "event_stream.rb"),
            new SupportingFileSpec("server_sent_event.mustache", libPath, "server_sent_event.rb"),
            new SupportingFileSpec("stream_options.mustache", libPath, "stream_options.rb"),
            new SupportingFileSpec("json_lines_stream.mustache", libPath, "json_lines_stream.rb"),
            new SupportingFileSpec("sse_stream.mustache", libPath, "sse_stream.rb"),
            new SupportingFileSpec("page.mustache", libPath, "page.rb"),
            new SupportingFileSpec("link_header.mustache", libPath, "link_header.rb"),
            new SupportingFileSpec("paginator.mustache", libPath, "paginator.rb"),
            new SupportingFileSpec("api_client.mustache", libPath, "api_client.rb"),
            new SupportingFileSpec("default_api_client.mustache", libPath, "default_api_client.rb"),
            new SupportingFileSpec("base_api.mustache", Path.of(libPath, "api").toString(), "base_api.rb"),
            new SupportingFileSpec("authenticator.mustache", Path.of(libPath, "auth").toString(), "authenticator.rb"),
            new SupportingFileSpec(
                    "auth/all_of_authenticator.mustache",
                    Path.of(libPath, "auth").toString(),
                    "all_of_authenticator.rb"),
            new SupportingFileSpec(
                    "auth/all_of_http_aware_authenticator.mustache",
                    Path.of(libPath, "auth").toString(),
                    "all_of_http_aware_authenticator.rb"),
            new SupportingFileSpec("gemfile.mustache", "", "Gemfile"),
            new SupportingFileSpec("gemspec.mustache", "", resolvedGemName + ".gemspec"),
            new SupportingFileSpec("rubocop.mustache", "", ".rubocop.yml"),
            new SupportingFileSpec("steepfile.mustache", "", "Steepfile"),
            new SupportingFileSpec("vendor_rbs.mustache", "sig", "vendor.rbs"),
            new SupportingFileSpec("infrastructure_rbs.mustache", "sig", "infrastructure.rbs"),
            new SupportingFileSpec("makefile.mustache", "", "Makefile"),
            new SupportingFileSpec("rakefile.mustache", "", "Rakefile"),
            new SupportingFileSpec("editorconfig.mustache", "", ".editorconfig"),
            new SupportingFileSpec("yardopts.mustache", "", ".yardopts"),
            new SupportingFileSpec("gitignore.mustache", "", ".gitignore")
        ));
        if (generateExamples) {
            /* A script outside lib/: the Makefile runs it with -Ilib and the Steepfile's
             * examples target type-checks it against sig/. */
            specs.add(new SupportingFileSpec("examples/quickstart.mustache", "examples", "quickstart.rb"));
        }
        return specs;
    }

    /**
     * Exposes the package metadata options to the gemspec and the README: the
     * authors (comma separated) and the repository, homepage, documentation and
     * issue-tracker URLs, each as a single-quote-safe literal body so the
     * templates can write it with a triple mustache. The licence is the SPDX
     * {@code licenseId} the shared generator already carries.
     */
    private void putManifestMetadata() {
        final String authors = getPropertyOrDefault("authors", "");
        final List<Map<String, String>> authorList = new ArrayList<>();
        for (final String author : Splitter.on(',').split(authors)) {
            if (!author.trim().isEmpty()) {
                authorList.add(Map.of("name", escapeSingleQuoted(author.trim())));
            }
        }
        additionalProperties.put("authorList", authorList);
        additionalProperties.put("hasAuthorList", !authorList.isEmpty());
        for (final String key :
                new String[] {"repositoryUrl", "homepageUrl", "documentationUrl", "issueTrackerUrl"}) {
            final String value = getPropertyOrDefault(key, "").trim();
            if (value.isEmpty()) {
                // An absent option stays absent: an empty string would still open a section.
                additionalProperties.remove(key);
                continue;
            }
            additionalProperties.put(key, value);
            additionalProperties.put(key + "Literal", escapeSingleQuoted(value));
        }
    }

    /**
     * Names the keyword argument of every client credential (F857). The shared pass lists one
     * entry per security scheme with a camel-case {@code paramName}; Ruby spells an argument in
     * snake case, so each entry gains {@code argName}.
     */
    @Override
    public void processOpenAPI(final io.swagger.v3.oas.models.OpenAPI openAPI) {
        super.processOpenAPI(openAPI);
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> schemes =
                (List<Map<String, Object>>) additionalProperties.get("clientSchemes");
        if (schemes != null) {
            nameCredentialArguments(schemes, this::isReservedWord);
        }
    }

    /**
     * Gives each client credential a Ruby keyword argument name: the snake-case form of the scheme
     * name. A name that is a Ruby keyword, one of the client's own arguments or an earlier
     * credential's name gets the suffix {@code _credential}, then a number, so every argument can
     * be read back as a local variable.
     *
     * @param schemes the shared {@code clientSchemes} entries; each gains {@code argName}
     * @param reserved tells whether a name is a Ruby keyword
     */
    static void nameCredentialArguments(
            final List<Map<String, Object>> schemes,
            final java.util.function.Predicate<String> reserved) {
        final Set<String> taken = new HashSet<>(Arrays.asList("authenticator", "transport_options"));
        for (final Map<String, Object> scheme : schemes) {
            final String base = NamingConvention.SNAKE_CASE.apply(String.valueOf(scheme.get("schemeId")));
            String name = base;
            int counter = 1;
            while (reserved.test(name) || taken.contains(name)) {
                name = base + "_credential" + (counter > 1 ? "_" + counter : "");
                counter++;
            }
            taken.add(name);
            scheme.put("argName", name);
        }
    }

    @Override
    public void processOpts() {
        super.processOpts();

        moduleName = getPropertyOrDefault(CodegenConstants.MODULE_NAME, moduleName);
        gemName =
                Optional.ofNullable((String) additionalProperties.get(CodegenConstants.GEM_NAME))
                        .orElseGet(() -> NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("[^\\w]+", "")));
        additionalProperties.put(CodegenConstants.GEM_NAME, gemName);
        final String gemVersion = getPropertyOrDefault("gemVersion", DEFAULT_GEM_VERSION);
        additionalProperties.put("gemVersion", gemVersion);
        additionalProperties.put("userAgentDefault", gemName + "/" + gemVersion + " (ruby)");
        putManifestMetadata();
        // One row per generated scheme authenticator: its constant and the
        // environment variable each of its credentials falls back to. The
        // generated authenticator test reads it; it is filled while the
        // authenticators are rendered, after the options are processed.
        additionalProperties.put("schemeEnvironmentEntries", schemeEnvironmentEntries);

        // SDK-wide error base. ApiError (and through it the whole HTTP/OAuth
        // error tree) and the serializer errors all subclass this, so a single
        // `rescue <apiErrorParent>` catches every SDK error. When unset, the
        // generator emits its own branded root
        // `<moduleName>::<errorPrefix>Error < StandardError` unconditionally and
        // roots the whole error tree (ApiError, SerializationError,
        // SchemaMismatchError, OAuth) under it — so callers can rescue that one
        // class to catch HTTP *and* serialization failures, under the same name
        // every other SDK gives its root. An SDK with a hand-written base points
        // this at that class so the two roots collapse into one.
        String apiErrorParent =
                getPropertyOrDefault("apiErrorParent", DEFAULT_API_ERROR_PARENT);
        final boolean customApiErrorParent =
                !DEFAULT_API_ERROR_PARENT.equals(apiErrorParent);
        if (!customApiErrorParent) {
            // No hand-written base supplied: brand our own root rather than
            // leaving the tree at the bare StandardError. The name is absolute:
            // the error files open the gem's module, so a relative
            // `Zitadel::Client::ZitadelError` resolves its first segment
            // lexically and finds the client class `Zitadel::Client::Zitadel`
            // when clientClassName repeats the top-level module's name.
            apiErrorParent =
                    "::" + moduleName + "::Errors::" + additionalProperties.get("errorPrefix") + "Error";
        }
        additionalProperties.put("apiErrorParent", apiErrorParent);

        // The error base (whether our branded `<moduleName>::<errorPrefix>Error` or a custom
        // hand-written class) lives outside the entrypoint's fixed-order requires,
        // so api_error.rb must load it before evaluating `class ApiError < <parent>`.
        // Make api_error.rb self-sufficient with a `require_relative`. The base
        // lives in the same dir as api_error.rb, so the relative file name is the
        // snake_case of the parent's unqualified class name (Zitadel::Client::
        // ZitadelError -> zitadel_error; PetstoreClient::OpenAPIError ->
        // open_api_error).
        final int sep = apiErrorParent.lastIndexOf("::");
        final String parentSimpleName =
                sep < 0 ? apiErrorParent : apiErrorParent.substring(sep + 2);
        final String apiErrorParentFile = NamingConvention.SNAKE_CASE.apply(parentSimpleName);
        additionalProperties.put("apiErrorParentFile", apiErrorParentFile);
        additionalProperties.put("apiErrorParentSimpleName", parentSimpleName);
        // The branded default root lives inside this gem's module, so we own its
        // RBS declaration too. A custom hand-written base ships its own sig, so
        // suppress ours to avoid a duplicate/conflicting declaration.
        additionalProperties.put("brandedErrorRoot", !customApiErrorParent);

        // Nested-module rendering for files that may be `require`d standalone
        // (e.g. lib/.../version.rb pulled in by the gemspec before Zeitwerk has
        // defined the namespace). A compact `module Zitadel::Client` crashes
        // when `Zitadel` is not yet defined, so emit fully nested modules:
        //   module Zitadel
        //     module Client
        //       VERSION = '...'
        // The parts below let the version template build that nesting generically
        // for any `::`-separated moduleName.
        final java.util.List<String> moduleParts =
            com.google.common.base.Splitter.on("::").splitToList(moduleName);
        final StringBuilder nestedOpen = new StringBuilder();
        final StringBuilder nestedClose = new StringBuilder();
        for (int i = 0; i < moduleParts.size(); i++) {
            nestedOpen.append("  ".repeat(i)).append("module ").append(moduleParts.get(i)).append('\n');
        }
        for (int i = moduleParts.size() - 1; i >= 0; i--) {
            nestedClose.append("  ".repeat(i)).append("end");
            if (i > 0) {
                nestedClose.append('\n');
            }
        }
        // RBS resolves `module Zitadel::Client` only once `Zitadel` itself is
        // declared, so the signatures declare every enclosing module first.
        final StringBuilder parentsRbs = new StringBuilder();
        for (int i = 1; i < moduleParts.size(); i++) {
            parentsRbs.append("module ").append(String.join("::", moduleParts.subList(0, i)))
                    .append("\nend\n\n");
        }
        additionalProperties.put("moduleNameParentsRbs", parentsRbs.toString());
        additionalProperties.put("moduleNameNestedOpen", nestedOpen.toString());
        additionalProperties.put("moduleNameNestedClose", nestedClose.toString());
        additionalProperties.put("moduleNameNestedIndent", "  ".repeat(moduleParts.size()));

        setModelPackage("models");
        setApiPackage("api");

        final String modulePath = NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("::", "/"));
        final String libPath = Path.of(LIB_FOLDER, modulePath).toString();

        // Lib-relative require root, mirroring the module nesting (e.g.
        // moduleName "Zitadel::Client" -> "zitadel/client"). This is the path
        // under lib/ where intra-gem files live and MUST be used for `require`
        // statements; gemName (e.g. "zitadel-client") differs whenever the
        // module name has multiple segments and would produce a LoadError.
        additionalProperties.put("modulePath", modulePath.replace('\\', '/'));

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", libPath, clientClassFile + ".rb"));

        // The generator owns the SDK-wide error base file in both cases: the
        // branded default `<moduleName>::<errorPrefix>Error` and a custom
        // hand-written base (e.g. ZitadelError). Emitting it unconditionally guarantees a single
        // branded root the whole error tree (ApiError, SerializationError,
        // SchemaMismatchError, OAuth) parents under, so one `rescue` catches
        // every SDK error.
        supportingFiles.add(
                new SupportingFile(
                        "errors/error_parent.mustache",
                        Path.of(libPath, "errors").toString(),
                        apiErrorParentFile + ".rb"));

        // The Steepfile type-checks the tests whenever any are emitted.
        additionalProperties.put("emitUnitTests", emitUnitTests());
        if (emitUnitTests()) {
            // The dev-dependency manifest: one line per dependency an emitted test
            // imports. Every client keep-lists its own package manifest, so this
            // generator-owned file is the only thing keeping the two in step.
            supportingFiles.add(
                    new SupportingFile(
                            "dev_dependencies.mustache",
                            ".openapi-generator",
                            "DEV-DEPENDENCIES"));
            supportingFiles.add(
                    new SupportingFile("test/test_helper.mustache", "test", "test_helper.rb"));
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
                            "test/configuration_test.mustache",
                            "test",
                            "configuration_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/call_hook_test.mustache",
                            "test",
                            "call_hook_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/call_hook_recipe_test.mustache",
                            "test",
                            "call_hook_recipe_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/scheme_authenticator_test.mustache",
                            "test",
                            "scheme_authenticator_test.rb"));
        }
        if (generateTests) {
            // The petstore golden has no gem_rbs_collection, so steep cannot see
            // Faraday's types from a vendored gem signature the way real SDKs do.
            // Emit a stub mirroring the gem's surface (and class hierarchy) for
            // the fixture only; sig/vendor.rbs still adds the Connection#proxy=
            // reopen the gem omits, in both this fixture and real clients.
            supportingFiles.add(new SupportingFile("faraday_rbs.mustache", "sig", "faraday.rbs"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet_api_test.mustache",
                            Path.of("test", "api").toString(),
                            "pet_api_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store_api_test.mustache",
                            Path.of("test", "api").toString(),
                            "store_api_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "test",
                            "default_api_client_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "test",
                            "object_serializer_test.rb"));
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
                            "test/model_doc_test.mustache",
                            "test",
                            "model_doc_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "test",
                            "composed_schema_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "test",
                            "client_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_error_test.mustache",
                            "test",
                            "api_error_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/event_stream_test.mustache",
                            "test",
                            "event_stream_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api_result_test.mustache",
                            "test",
                            "api_result_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_configuration_test.mustache",
                            "test",
                            "server_configuration_test.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/server_variable_test.mustache",
                            "test",
                            "server_variable_test.rb"));
            supportingFiles.add(
                    new SupportingFile("test/readme_test.mustache", "test", "readme_test.rb"));
            supportingFiles.add(
                    new SupportingFile("test/skills_test.mustache", "test", "skills_test.rb"));
        }
    }

    /**
     * Records the wire name of the README's {@code format: byte} example
     * property, so the example can build the JSON it deserializes instead
     * of reading an undefined variable.
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        final Object classname = additionalProperties.get("byteExampleClassname");
        final Object propertyName = additionalProperties.get("byteExamplePropertyName");
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();
                if (model == null || model.vars == null || !model.classname.equals(classname)) {
                    continue;
                }
                for (final CodegenProperty prop : model.vars) {
                    if (prop.name.equals(propertyName)) {
                        additionalProperties.put("byteExampleBaseName", prop.baseName);
                    }
                }
            }
        }
        return result;
    }

    /** Sentinel return type for a top-level {@code format: byte} response. */
    private static final String BYTE_RETURN_TYPE = "ByteArray";

    /**
     * Re-types a top-level bare {@code format: byte} response so the transport
     * base64-decodes it instead of handing back the raw base64 string.
     *
     * <p>Such a response (a {@code type: string, format: byte} schema carried as
     * {@code application/json}) arrives on the wire as a JSON string literal
     * ({@code "dGVzdC1pbWFnZQ=="}). Its Ruby surface type is {@code String}, but
     * the deserialize path must JSON-parse <em>and</em> base64-decode it to raw
     * bytes. {@code String} is indistinguishable from a plain string response, so
     * we route the deserialize type through the {@code ByteArray} sentinel (which
     * {@code ObjectSerializer#convert_to_type} base64-decodes) while leaving the
     * public method/RBS return type as {@code String}. Nested {@code format: byte}
     * model fields already decode correctly via {@code OPENAPI_FORMATS}; only the
     * top-level bare response needs this. Mirrors the python {@code bytes} /
     * java {@code byte[]} return types.
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        final Map<String, Object> early = (Map<String, Object>) objs.get("operations");
        if (early != null && early.get("operation") != null) {
            for (final CodegenOperation op : (List<CodegenOperation>) early.get("operation")) {
                markBinaryUpload(op);
            }
        }
        objs = super.postProcessOperationsWithModels(objs, allModels);
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations == null) {
            return objs;
        }
        final List<CodegenOperation> ops =
                (List<CodegenOperation>) operations.get("operation");
        if (ops == null) {
            return objs;
        }
        for (final CodegenOperation op : ops) {
            markBinaryStream(op);
            // A top-level byte response: the return schema itself is
            // `format: byte` (not a byte *field* of a model, which decodes via
            // OPENAPI_FORMATS, and not `binary`, which streams as a File).
            if (op.returnProperty != null
                    && "byte".equals(op.returnProperty.dataFormat)
                    && !op.returnProperty.isArray
                    && !op.returnProperty.isMap) {
                if (op.vendorExtensions == null) {
                    op.vendorExtensions = new HashMap<>();
                }
                op.vendorExtensions.put("deserializeReturnType", BYTE_RETURN_TYPE);
            }

            // When an operation declares MORE THAN ONE request content-type
            // (e.g. setPetAvatar's image/jpeg, image/png, application/json) the
            // wire Content-Type collapses to the first declared type
            // (effectiveConsumes) and the remaining types are unreachable. This
            // flag lets the api template emit an OPTIONAL `content_type:` keyword
            // selector so the caller can pick among the declared types; when
            // unset it defaults to the first (current behaviour). Mustache cannot
            // count list elements, so the "two-or-more" decision is computed here
            // in the language-scoped codegen rather than in the template.
            if (op.consumes != null && op.consumes.size() > 1) {
                if (op.vendorExtensions == null) {
                    op.vendorExtensions = new HashMap<>();
                }
                op.vendorExtensions.put("hasMultipleConsumes", true);
            }
        }
        return objs;
    }

    /**
     * A raw binary request body is a {@code BodySource}: the operation takes it, in every
     * signature, instead of a {@code File}. It runs before the shared pass, which copies the body
     * type into the signature arguments. Multipart and every other body kind keep the type the
     * spec maps.
     */
    static void markBinaryUpload(final CodegenOperation op) {
        final CodegenParameter body = op.bodyParam;
        if (body == null
                || !body.isBinary
                || op.isMultipart
                || (op.formParams != null && !op.formParams.isEmpty())) {
            return;
        }
        body.dataType = "BodySource";
        body.baseType = "BodySource";
        body.datatypeWithEnum = "BodySource";
        if (op.allParams != null) {
            for (final CodegenParameter p : op.allParams) {
                if (p.isBodyParam) {
                    p.dataType = "BodySource";
                    p.baseType = "BodySource";
                    p.datatypeWithEnum = "BodySource";
                }
            }
        }
    }

    /**
     * A binary success response is returned as a {@code ByteStream} by the one operation method
     * (D119): the return type becomes {@code ByteStream}, and the decorator {@code streamsBinary}
     * tells the API template to call {@code invoke_api_for_stream}.
     */
    @SuppressWarnings("unchecked")
    static void markBinaryStream(final CodegenOperation op) {
        final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
        if (!(decorators instanceof Map)) {
            return;
        }
        final Map<String, Object> d = (Map<String, Object>) decorators;
        final boolean binary = "binary".equals(d.get("streamKind"));
        d.put("streamsBinary", binary);
        if (binary) {
            op.returnType = "ByteStream";
            op.returnBaseType = "ByteStream";
            op.isResponseFile = false;
        }
        final boolean sse = "sse".equals(kindOf(d)) && Boolean.TRUE.equals(d.get("streamOnly"));
        final boolean jsonLines = "jsonlines".equals(kindOf(d)) && Boolean.TRUE.equals(d.get("streamOnly"));
        d.put("streamsSse", sse);
        d.put("streamsJsonLines", jsonLines);
        if (sse || jsonLines) {
            final boolean text = sse && Boolean.TRUE.equals(d.get("streamEventIsText"));
            final Object declared = d.get("streamItemType");
            final String item = !text && declared != null ? declared.toString() : "String";
            d.put("streamTyped", !"String".equals(item));
            d.put("streamItemRuby", item);
            op.returnType = sse ? "SseStream" : "JsonLinesStream";
            op.returnBaseType = op.returnType;
            op.isResponseFile = false;
        }
    }

    @Nullable
    private static Object kindOf(final Map<String, Object> decorators) {
        return decorators.get("streamKind");
    }

    /**
     * Hands the README and SKILLS the first operation that returns a {@code ByteStream}, as
     * {@code binaryStreamOperation} (with {@code binaryStreamGroup}, its operations map, and
     * {@code hasBinaryStreamOperation}), so the Streaming example calls a real operation of the
     * spec under generation. The first paged operation is handed over the same way, as
     * {@code pagedOperation}. Nothing is rendered when the spec has none.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> postProcessSupportingFileData(final Map<String, Object> objs) {
        final String[] flags = {"streamsBinary", "streamsSse", "streamsJsonLines", "hasPagesMethod"};
        final String[] names = {"binaryStream", "sseStream", "jsonLinesStream", "paged"};
        for (int kind = 0; kind < flags.length; kind++) {
            CodegenOperation found = null;
            Object group = null;
            final Object apiInfo = objs.get("apiInfo");
            if (apiInfo instanceof Map) {
                final Object apis = ((Map<String, Object>) apiInfo).get("apis");
                if (apis instanceof List) {
                    for (final Object api : (List<Object>) apis) {
                        final Object operations = ((Map<String, Object>) api).get("operations");
                        if (!(operations instanceof Map)) {
                            continue;
                        }
                        final Object list = ((Map<String, Object>) operations).get("operation");
                        if (!(list instanceof List)) {
                            continue;
                        }
                        for (final Object candidate : (List<Object>) list) {
                            final CodegenOperation op = (CodegenOperation) candidate;
                            final Object decorators =
                                    op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
                            if (found == null
                                    && decorators instanceof Map
                                    && Boolean.TRUE.equals(((Map<String, Object>) decorators).get(flags[kind]))) {
                                found = op;
                                group = operations;
                            }
                        }
                    }
                }
            }
            final String name = names[kind];
            objs.put("has" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Operation", found != null);
            if (found != null) {
                objs.put(name + "Operation", found);
                objs.put(name + "Group", group);
            }
        }
        return super.postProcessSupportingFileData(objs);
    }

    /**
     * Registers the authenticator tests, each only when the spec's security
     * schemes produce the authenticator it exercises. Registered here, not in
     * processOpts: the scheme flags are only set once the spec is parsed.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        super.registerAuthSupportingFiles();
        if (hasOAuth2DeviceAuthorization) {
            /* D191: one constant per file, so the device authorization value has a file of its own. */
            supportingFiles.add(new SupportingFile(
                    "auth/oauth/device_authorization.mustache", getOAuthDir(), "device_authorization.rb"));
        }
        if (!generateTests) {
            return;
        }
        addTestIf(hasBearerAuth, "bearer_authenticator_test");
        addTestIf(hasApiKeyAuth, "api_key_authenticator_test");
        addTestIf(hasBasicAuth, "basic_authenticator_test");
        addTestIf(hasAnyOAuth2 || hasOpenIdConnect, "oauth2_token_manager_test");
        addTestIf(hasOAuth2AuthorizationCode, "oauth2_auth_code_authenticator_test",
                "oauth2_authorization_code_authenticator_test");
        addTestIf(hasOAuth2Implicit, "oauth2_implicit_authenticator_test");
        addTestIf(hasOAuth2ClientCredentials, "oauth2_client_credentials_authenticator_test");
        addTestIf(hasOAuth2Password, "oauth2_password_authenticator_test");
        addTestIf(hasOpenIdConnect, "openid_connect_authenticator_test");
    }

    private void addTestIf(boolean condition, String template) {
        addTestIf(condition, template, template);
    }

    private void addTestIf(boolean condition, String template, String file) {
        if (condition) {
            supportingFiles.add(
                    new SupportingFile("test/" + template + ".mustache", "test", file + ".rb"));
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
                final String literal = resolved.getDefault().toString();
                // Ruby style groups five or more integer digits with '_'
                // (9_000_000_000); the value is unchanged.
                return literal.matches("-?\\d{5,}")
                        ? literal.replaceAll("(\\d)(?=(\\d{3})+$)", "$1_")
                        : literal;
            }
        } else if (ModelUtils.isStringSchema(resolved)) {
            if (resolved.getDefault() != null) {
                return toRubyStringLiteral(String.valueOf(resolved.getDefault()));
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
        return Set.of("Integer", "Float", "BigDecimal");
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
     * Overrides the base class because Ruby's block comment is the
     * {@code =begin}/{@code =end} pair. An interpolation sequence (a hash
     * followed by an opening brace) is left as written: the templates put
     * spec text only in line comments and single-quoted literals, where it
     * does not interpolate. Cannot be standardized because other languages
     * use C-style block comments (handled by the base class).
     */
    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("=end", "=_end").replace("=begin", "=_begin");
    }

    /**
     * Keywords that stay plain as a model property name: {@code obj.and},
     * {@code obj.return} and a {@code attribute :and} reader are legal Ruby
     * and RBS, so only names that clash ({@code class} is Object#class) or
     * are literals keep the underscore prefix. Parameters still escape every
     * keyword, see {@link #toParamName}. Cannot be standardized because the
     * reserved set is Ruby-specific.
     */
    private static final Set<String> PLAIN_PROPERTY_KEYWORDS = Set.of("and", "or", "not", "return");

    /** {@inheritDoc} */
    @Override
    protected boolean shouldEscapeReservedVarName(String name) {
        return !PLAIN_PROPERTY_KEYWORDS.contains(name) && super.shouldEscapeReservedVarName(name);
    }

    /**
     * The names the generated operation methods bind themselves: the injected
     * {@code options} object, the {@code server} and {@code request_content_type}
     * keywords, and the locals the method body builds. A path or body parameter
     * with one of these names would shadow the binding, so the shared
     * {@code toParamName} suffixes it (F1055). The wire name is unaffected: it
     * travels in {@code baseName}.
     */
    @Override
    protected Set<String> getInjectedArgumentNames() {
        return Set.of(
                "options", "server", "request_content_type", "path", "server_url",
                "query_params", "header_params", "cookie_parts", "cookie_value",
                "request_body", "declared_consumes", "resolved_content_type", "result",
                "timeout", "headers", "max_retries");
    }

    /** {@inheritDoc} */
    @Override
    protected String getInjectedArgumentSuffix() {
        return "_param";
    }

    /**
     * A parameter becomes a keyword argument and a local variable, where a
     * keyword such as {@code and} cannot be read back, so every reserved
     * word is escaped here, including the ones a property keeps plain.
     */
    @Override
    public String toParamName(String name) {
        final String paramName = super.toParamName(name);
        return isReservedWord(paramName) ? escapeReservedWord(paramName) : paramName;
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
                        (fragment, writer) -> writer.write(NamingConvention.PASCAL_CASE.apply(fragment.execute())))
                .put(
                        "noSpaces",
                        (fragment, writer) -> writer.write(fragment.execute().replaceAll("\\s+", "")))
                .put(
                        "rubySnake",
                        (fragment, writer) -> writer.write(NamingConvention.SNAKE_CASE.apply(fragment.execute())))
                .put(
                        "rubyConst",
                        (fragment, writer) -> writer.write(toRubyConstantName(fragment.execute())))
                .put(
                        "rubyQuoted",
                        (fragment, writer) -> writer.write(escapeSingleQuoted(fragment.execute())))
                .put(
                        "rubyComment",
                        (fragment, writer) ->
                                writer.write(
                                        escapeYardLinkBraces(
                                                fragment
                                                        .execute()
                                                        .replaceAll("\\s*[\\r\\n]+\\s*", " ")
                                                        .trim())))
                .put(
                        "rubyDocComment",
                        (fragment, writer) ->
                                writer.write(
                                        escapeYardLinkBraces(
                                                unescapeSingleQuoted(fragment.execute()))));
    }

    /**
     * Sets every word of a doc comment that contains a brace in code font. YARD reads {@code {name}}
     * as a link and fails {@code --fail-on-warning} when it cannot resolve the name, which happens for
     * a placeholder such as {@code #{petId}} in a description. Character references do not help:
     * YARD decodes them before it looks for links. Code font keeps the braces as written.
     */
    static String escapeYardLinkBraces(String input) {
        return BRACED_WORD.matcher(input)
                .replaceAll(match -> Matcher.quoteReplacement("<tt>" + match.group() + "</tt>"));
    }

    private static final Pattern BRACED_WORD = Pattern.compile("\\S*[{}]\\S*");

    /**
     * Reverses {@link #escapeSingleQuoted} for text that {@link #escapeText}
     * prepared for a literal but that lands in a line comment, where an
     * escaped quote would show its backslash. Cannot be standardized because the
     * other languages quote differently.
     */
    static String unescapeSingleQuoted(String input) {
        final StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            final char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                final char next = input.charAt(i + 1);
                if (next == '\'' || next == '\\') {
                    sb.append(next);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Escapes text for a Ruby single-quoted string literal, where only a
     * backslash and the single quote itself are escape sequences. Cannot be
     * standardized because the other languages quote differently.
     */
    static String escapeSingleQuoted(String input) {
        return input.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * Renders a raw string as a Ruby string literal that evaluates to exactly
     * that string. A single-quoted literal is used unless the text holds a
     * control character, which only a double-quoted literal can spell as an
     * escape sequence. Cannot be standardized because the other languages
     * quote and escape differently.
     */
    static String toRubyStringLiteral(String text) {
        boolean hasControl = false;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < 0x20 || text.charAt(i) == 0x7F) {
                hasControl = true;
                break;
            }
        }
        if (!hasControl) {
            return "'" + escapeSingleQuoted(text) + "'";
        }
        final StringBuilder sb = new StringBuilder(text.length() + 8).append('"');
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c < 0x20 || c == 0x7F) {
                sb.append(String.format(java.util.Locale.ROOT, "\\u{%X}", (int) c));
            } else if (c == '#' && i + 1 < text.length() && "{$@".indexOf(text.charAt(i + 1)) >= 0) {
                sb.append("\\#");
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Turns an arbitrary spec value into a legal Ruby constant name: upper
     * case, every non-identifier character replaced by an underscore, and a
     * leading letter guaranteed. Cannot be standardized because constant
     * naming rules are Ruby-specific.
     */
    static String toRubyConstantName(String input) {
        final String cleaned = input.toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (cleaned.isEmpty()) {
            return "EMPTY";
        }
        return Character.isDigit(cleaned.charAt(0)) ? "N" + cleaned : cleaned;
    }

    /**
     * Escapes spec text for the single-quoted Ruby literals the templates
     * emit: a backslash and a single quote are escaped, a double quote and
     * {@code #{...}} are left as written because single-quoted strings do
     * not interpolate. Cannot be standardized because the other languages
     * quote differently.
     */
    @Nullable
    @Override
    public String escapeText(@Nullable String input) {
        if (input == null) {
            return null;
        }
        final String flattened = input.replaceAll("[\\t\\n\\r]", " ");
        return escapeSingleQuoted(flattened).replace("=end", "=_end").replace("=begin", "=_begin");
    }

    /**
     * Rewrites a default that references a component enum constant to Ruby's
     * {@code ::} constant lookup; everything else goes to the base class.
     * Cannot be standardized because the other languages use {@code .}.
     */
    @Override
    protected void fixEnumDefaultValue(CodegenProperty prop, CodegenModel model) {
        fixEnumRefDefault(prop, model);
    }

    /**
     * A single-quoted Ruby literal interprets only {@code \\} and {@code \'},
     * so control characters stay raw in enum literals ({@code \n} would be a
     * literal backslash-n).
     */
    @Override
    protected boolean escapesEnumControlCharacters() {
        return false;
    }

    private void fixEnumRefDefault(CodegenProperty prop, CodegenModel model) {
        if (prop.defaultValue != null
                && !prop.isEnum
                && prop.defaultValue.matches("[A-Z][A-Za-z0-9]*\\.[A-Z_][A-Z0-9_]*")) {
            // A default that references a component enum ({@code Swatch.GREEN})
            // is a constant lookup in Ruby, not a method call.
            prop.defaultValue = prop.defaultValue.replace(".", "::");
            return;
        }
        super.fixEnumDefaultValue(prop, model);
    }

    /**
     * Ruby enum constants must be non-empty identifiers. A value made only
     * of symbols (a space, {@code @@}) sanitises to nothing, so it takes the
     * empty-value name and the collision suffixing that follows. Cannot be
     * standardized because the other languages accept other fallbacks.
     */
    @Override
    public String toEnumVarName(String value, String datatype) {
        final String name = super.toEnumVarName(value, datatype);
        return name.isEmpty() ? getEmptyEnumVarName() : name;
    }

    /*
     * Overrides the base class to move {@code .rbs} type-signature
     * files from {@code lib/} to {@code sig/} as required by Ruby's
     * Steep type-checking tooling. The base class handles this via
     * {@link WithTypeSignatureSupport} dispatch.
     */

    /** {@inheritDoc} */
    @Override
    protected String getAuthDir() {
        final String modulePath =
                NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("::", "/"));
        return Path.of(LIB_FOLDER, modulePath, "auth").toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String toAuthFilename(String stem) {
        return stem + ".rb";
    }

    /**
     * Renders a per-scheme authenticator Ruby source file using
     * the scheme_authenticator.mustache template.
     *
     * <p>Every scheme reads its own credentials from the environment (F862): a
     * value given to the constructor wins, then the scheme's own variable
     * ({@code envVars}, one per credential), read once when the authenticator is
     * built, an empty variable counting as unset. The wrapper hands the variable
     * names to its base for a bearer, basic or API-key scheme, and resolves them
     * itself for an OAuth one, which fails with {@code ArgumentError} when a
     * credential is missing.
     */
    @Override
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("isOAuth", spec.isOAuth());
        ctx.put("imports", List.of());
        final Map<String, String> envByParam = environmentByParameter(ctx);
        final SchemeConstructor ctor = schemeConstructor(spec, envByParam);
        ctx.put("constructorSignature", ctor.signature);
        ctx.put("superArgs", ctor.superArgs);
        ctx.put("resolutions", ctor.resolutions);
        ctx.put("hasResolutions", !ctor.resolutions.isEmpty());
        ctx.put("rbsSignature", ctor.rbsSignature);
        final Object metadataUrl = ctx.get("oauth2MetadataUrl");
        if (metadataUrl != null) {
            ctx.put("metadataUrlLiteral", toRubyStringLiteral(String.valueOf(metadataUrl)));
        }
        final Map<String, Object> entry = new HashMap<>();
        /* D190: authscheme700 reads the metadata URL constant of the first scheme that declares one. */
        entry.put(
                "metadataUrlTest",
                metadataUrl != null
                        && schemeEnvironmentEntries.stream().noneMatch(e -> Boolean.TRUE.equals(e.get("metadataUrlTest"))));
        entry.put("metadataUrlLiteral", ctx.get("metadataUrlLiteral"));
        entry.put("className", ctx.get("className"));
        entry.put("namespace", spec.isOAuth() ? "Auth::OAuth" : "Auth");
        entry.put("isOAuth", spec.isOAuth());
        entry.put("baseClass", spec.baseClass());
        final List<Map<String, String>> credentials = new ArrayList<>();
        for (final Map.Entry<String, String> e : envByParam.entrySet()) {
            credentials.add(Map.of("name", e.getKey(), "envName", e.getValue()));
        }
        entry.put("credentials", credentials);
        /* D191: authoauthtwodevice706 builds the first device authorization wrapper. */
        final boolean device = "OAuth2DeviceAuthorizationAuthenticator".equals(spec.baseClass());
        entry.put(
                "deviceTest",
                device && schemeEnvironmentEntries.stream().noneMatch(e -> Boolean.TRUE.equals(e.get("deviceTest"))));
        if (device) {
            entry.put("deviceFile", NamingConvention.SNAKE_CASE.apply(String.valueOf(ctx.get("className"))));
            entry.put("deviceAuthorizationUrl", spec.authorizationUrl());
            entry.put("deviceTokenUrl", spec.tokenUrl());
            entry.put("deviceScopes", formatRubyScopes(spec.scopes()));
        }
        entry.put("firstEnvName", credentials.isEmpty() ? "" : credentials.get(0).get("envName"));
        schemeEnvironmentEntries.add(entry);
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", ctx);
    }

    /**
     * The environment variable of each credential of one scheme, keyed by the
     * constructor parameter's snake_case name, from the {@code envVars} the
     * shared generator puts in the scheme's context.
     */
    private static Map<String, String> environmentByParameter(Map<String, Object> ctx) {
        final Map<String, String> byParam = new LinkedHashMap<>();
        final Object envVars = ctx.get("envVars");
        if (!(envVars instanceof List<?>)) {
            return byParam;
        }
        for (final Object entry : (List<?>) envVars) {
            final Map<?, ?> row = (Map<?, ?>) entry;
            byParam.put(
                    NamingConvention.SNAKE_CASE.apply(String.valueOf(row.get("field"))),
                    String.valueOf(row.get("envName")));
        }
        return byParam;
    }

    /** The constructor of one scheme authenticator, as the templates print it. */
    private static final class SchemeConstructor {
        private final String signature;
        private final String rbsSignature;
        private final List<String> superArgs;
        private final List<String> resolutions;

        private SchemeConstructor(
                String signature,
                String rbsSignature,
                List<String> superArgs,
                List<String> resolutions) {
            this.signature = signature;
            this.rbsSignature = rbsSignature;
            this.superArgs = superArgs;
            this.resolutions = resolutions;
        }
    }

    private SchemeConstructor schemeConstructor(
            SchemeAuthSpec spec, Map<String, String> envByParam) {
        final List<String> superArgs = new ArrayList<>(buildRubySuperArgs(spec));
        final List<String> resolutions = new ArrayList<>();
        final StringBuilder signature = new StringBuilder("host");
        final StringBuilder rbs = new StringBuilder("String host");
        final String base = spec.baseClass();
        if ("BearerAuthenticator".equals(base)) {
            signature.append(", token = nil, token_provider: nil, env: ENV");
            rbs.append(", ?String? token, ?token_provider: (^() -> String)?, ?env: untyped");
            superArgs.add("token_provider: token_provider");
            superArgs.add("env: env");
            superArgs.add("token_env: " + rubyLiteral(envByParam.get("token")));
        } else if ("BasicAuthenticator".equals(base)) {
            signature.append(", username = nil, password = nil, env: ENV");
            rbs.append(", ?String? username, ?String? password, ?env: untyped");
            superArgs.add("env: env");
            superArgs.add("username_env: " + rubyLiteral(envByParam.get("username")));
            superArgs.add("password_env: " + rubyLiteral(envByParam.get("password")));
        } else if ("ApiKeyAuthenticator".equals(base)) {
            signature.append(", api_key = nil, env: ENV");
            rbs.append(", ?String? api_key, ?env: untyped");
            superArgs.add("env: env");
            superArgs.add("api_key_env: " + rubyLiteral(envByParam.get("api_key")));
        } else {
            for (final Map.Entry<String, String> param : envByParam.entrySet()) {
                signature.append(", ").append(param.getKey()).append(" = nil");
                rbs.append(", ?String? ").append(param.getKey());
                resolutions.add(
                        param.getKey()
                                + " = require_credential("
                                + param.getKey()
                                + ", "
                                + rubyLiteral(param.getValue())
                                + ", "
                                + rubyLiteral(param.getKey())
                                + ", env)");
            }
            signature.append(", env: ENV");
            rbs.append(", ?env: untyped");
        }
        return new SchemeConstructor(signature.toString(), rbs.toString(), superArgs, resolutions);
    }

    private static String rubyLiteral(@Nullable String text) {
        return "'" + Objects.toString(text, "") + "'";
    }

    /**
     * Writes the RBS signature of a scheme authenticator, whose constructor
     * takes the optional credentials and the {@code env} seam.
     */
    @Override
    protected void postWriteSchemeAuthenticator(SchemeAuthSpec spec, String writtenPath) {
        final Map<String, Object> ctx = baseSchemeContext(spec);
        ctx.put("isOAuth", spec.isOAuth());
        final Map<String, String> envByParam = environmentByParameter(ctx);
        ctx.put("rbsSignature", schemeConstructor(spec, envByParam).rbsSignature);
        final int lastDot = writtenPath.lastIndexOf('.');
        final String companionPath =
                (lastDot >= 0 ? writtenPath.substring(0, lastDot) : writtenPath)
                        + getSignatureFileExtension();
        writeFile(companionPath, renderOptionsTemplate(getAuthenticatorSignatureTemplate(), ctx));
        postProcessFile(Path.of(companionPath).toFile(), "source");
    }

    private static String formatRubyScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "['" + String.join("', '", scopes.keySet()) + "']";
    }

    private List<String> buildRubySuperArgs(SchemeAuthSpec spec) {
        if ("BasicAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "username", "password");
        }
        if ("BearerAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "token");
        }
        if ("ApiKeyAuthenticator".equals(spec.baseClass())) {
            return List.of("host", toRubyStringLiteral(java.util.Objects.toString(spec.keyParamName(), "")), "api_key",
                    "ApiKeyLocation::" + spec.keyIn());
        }
        if ("OAuth2ClientCredentialsAuthenticator".equals(spec.baseClass())) {
            final String scopes = formatRubyScopes(spec.scopes());
            return List.of("host", "client_id", "client_secret",
                    "'" + spec.tokenUrl() + "'", scopes);
        }
        if ("OAuth2PasswordAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "nil";
            final String scopes = formatRubyScopes(spec.scopes());
            return List.of("host", "client_id", "client_secret",
                    "'" + spec.tokenUrl() + "'",
                    "username", "password", scopes,
                    "refresh_url: " + refreshArg);
        }
        if ("OAuth2AuthorizationCodeAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "nil";
            final String scopes = formatRubyScopes(spec.scopes());
            return List.of("host", "client_id", "client_secret",
                    "'" + spec.authorizationUrl() + "'", "'" + spec.tokenUrl() + "'",
                    "redirect_uri", scopes,
                    "refresh_url: " + refreshArg);
        }
        if ("OAuth2ImplicitAuthenticator".equals(spec.baseClass())) {
            final String scopes = formatRubyScopes(spec.scopes());
            return List.of("host", "client_id",
                    "'" + spec.authorizationUrl() + "'", scopes);
        }
        if ("OAuth2DeviceAuthorizationAuthenticator".equals(spec.baseClass())) {
            final String refreshArg = spec.refreshUrl() != null
                    ? "'" + spec.refreshUrl() + "'" : "nil";
            return List.of("host", "client_id",
                    "'" + spec.authorizationUrl() + "'", "'" + spec.tokenUrl() + "'",
                    formatRubyScopes(spec.scopes()),
                    "refresh_url: " + refreshArg);
        }
        if ("OpenIdConnectAuthenticator".equals(spec.baseClass())) {
            return List.of("host", "'" + spec.openIdConnectUrl() + "'",
                    "client_id", "client_secret", "redirect_uri", "[]");
        }
        return List.of();
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
        if ("BodySource".equals(type)) {
            return "::" + Optional.ofNullable((String) additionalProperties.get(CodegenConstants.MODULE_NAME))
                    .orElse(moduleName) + "::BodySource";
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

    /**
     * Whether the operation gets a {@code _pages} method. A paged operation's Options object can
     * copy itself with other values, so every page is sent with its own cursor.
     */
    private static boolean hasPagesMethod(final CodegenOperation op) {
        final Object decorators = op.vendorExtensions == null ? null : op.vendorExtensions.get("op");
        return decorators instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) decorators).get("hasPagesMethod"));
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        capturePluralExamplesForParameters(optionsParams);
        final List<Map<String, Object>> params = new ArrayList<>();
        boolean hasAnyRequired = false;
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("paramName", p.paramName);
            param.put("required", p.required);
            // Per-field YARD documentation: mirror the `# @param` lines
            // api.mustache emits for direct operation parameters, so option
            // fields are documented with the same type and description as the
            // parameters they wrap. dataType is the unqualified surface type
            // (matching api.mustache); description/isDeprecated may be absent.
            param.put("dataType", p.dataType);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            param.put("isDeprecated", p.isDeprecated);
            // The named examples of the parameter, documented like the ones
            // api.mustache writes for a path or body parameter.
            final Object examples =
                    p.vendorExtensions == null ? null : p.vendorExtensions.get("pluralExamples");
            if (examples instanceof List<?> && !((List<?>) examples).isEmpty()) {
                param.put("pluralExamples", examples);
            }
            params.add(param);
            if (p.required) {
                hasAnyRequired = true;
            }
        }

        // Collect model type imports for require_relative
        final List<String> modelRequires = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            if (!p.isArray && !p.isMap && !p.isPrimitiveType && p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)) {
                final String modelFile = NamingConvention.SNAKE_CASE.apply(p.baseType);
                modelRequires.add("require_relative '../../models/" + modelFile + "'");
            }
            if ((p.isArray || p.isMap) && p.items != null && p.items.baseType != null
                    && !p.items.isPrimitiveType
                    && !languageSpecificPrimitives.contains(p.items.baseType)) {
                final String modelFile = NamingConvention.SNAKE_CASE.apply(p.items.baseType);
                final String req = "require_relative '../../models/" + modelFile + "'";
                if (!modelRequires.contains(req)) {
                    modelRequires.add(req);
                }
            }
            // A $ref to a top-level enum carries its type name in dataType
            // (baseType is null), so the two branches above skip it. The RBS
            // sig already types the reader as Models::<Enum>, so the source
            // file must require it too — mirror node/java/kotlin/php/csharp.
            if (p.isEnumRef && p.dataType != null
                    && !languageSpecificPrimitives.contains(p.dataType)) {
                final String modelFile = NamingConvention.SNAKE_CASE.apply(p.dataType);
                final String req = "require_relative '../../models/" + modelFile + "'";
                if (!modelRequires.contains(req)) {
                    modelRequires.add(req);
                }
            }
        }

        final StringBuilder sig = new StringBuilder();
        boolean first = true;
        for (final CodegenParameter p : optionsParams) {
            if (!p.required) continue;
            if (!first) sig.append(", ");
            first = false;
            sig.append(p.paramName).append(":");
        }
        for (final CodegenParameter p : optionsParams) {
            if (p.required) continue;
            if (!first) sig.append(", ");
            first = false;
            sig.append(p.paramName).append(": nil");
        }
        // The per-call request options, on every Options object: a timeout in
        // milliseconds, extra headers and a retry count that apply to this
        // call alone (nil keeps the client's value). There is no per-call
        // authenticator (F857): an operation uses the credential of the scheme
        // its spec names, held by the client.
        if (!first) sig.append(", ");
        sig.append("timeout: nil, headers: nil, max_retries: nil");

        // Single grouped attr_accessor line (Style/AccessorGrouping): all
        // option params plus the per-call request options.
        final StringBuilder accessors = new StringBuilder();
        boolean firstAcc = true;
        for (final CodegenParameter p : optionsParams) {
            if (!firstAcc) accessors.append(", ");
            firstAcc = false;
            accessors.append(':').append(p.paramName);
        }
        if (!firstAcc) accessors.append(", ");
        accessors.append(":timeout, :headers, :max_retries");

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("moduleName", moduleName);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("hasAnyRequired", hasAnyRequired);
        context.put("initializeSignature", sig.toString());
        context.put("accessorList", accessors.toString());
        context.put("modelRequires", modelRequires);
        context.put("hasModelRequires", !modelRequires.isEmpty());
        context.put("hasCopy", hasPagesMethod(op));

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
            return rbsType.replaceAll(
                    "\\b" + Pattern.quote(p.baseType) + "\\b", "Models::" + p.baseType);
        }
        // A $ref to a top-level enum carries its type name in dataType (baseType
        // is null); its RBS type lives under Models:: too.
        if (p.isEnumRef && p.dataType != null
                && !languageSpecificPrimitives.contains(p.dataType)
                && !rbsType.contains("Models::")) {
            return rbsType.replaceAll(
                    "\\b" + Pattern.quote(p.dataType) + "\\b", "Models::" + p.dataType);
        }
        // A content-encoded parameter (OAS `content`, not `schema`) carries its
        // model name in dataType with no baseType; qualify it the same way.
        if (p.baseType == null && p.dataType != null
                && rbsType.equals(p.dataType)
                && p.dataType.matches("[A-Z][A-Za-z0-9]*")) {
            return qualifySingleType(p.dataType);
        }
        return rbsType;
    }

    /**
     * The RBS type of an Options field. An uploaded file is read, never
     * reopened by path, so any readable IO is accepted, an in-memory StringIO
     * included, not just a File.
     */
    private String optionRbsType(CodegenParameter p) {
        final String rbsType = qualifyRbsModelType(toRbsType(p.dataType), p);
        final boolean upload = p.isFile || p.isBinary
                || (p.items != null && (p.items.isFile || p.items.isBinary));
        final String type =
                upload ? rbsType.replaceAll("\\bFile\\b", "(::IO | ::StringIO)") : rbsType;
        // A required nullable query parameter keeps its keyword required but
        // accepts nil: the RBS type is the nullable form of the type.
        return isRequiredNullableQuery(p) ? type + "?" : type;
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
            param.put("rbsType", optionRbsType(p));
            param.put("required", p.required);
            params.add(param);
        }

        final StringBuilder sig = new StringBuilder();
        boolean first = true;
        for (final CodegenParameter p : optionsParams) {
            if (!p.required) continue;
            if (!first) sig.append(", ");
            first = false;
            final String rbsType = optionRbsType(p);
            sig.append(p.paramName).append(": ").append(rbsType);
        }
        for (final CodegenParameter p : optionsParams) {
            if (p.required) continue;
            if (!first) sig.append(", ");
            first = false;
            final String rbsType = optionRbsType(p);
            sig.append('?').append(p.paramName).append(": ").append(rbsType).append('?');
        }
        if (!first) sig.append(", ");
        sig.append("?timeout: Numeric?, ?headers: Hash[String, String]?, ?max_retries: Integer?");

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("moduleName", moduleName);
        context.put("params", params);
        context.put("initializeSignature", sig.toString());
        context.put("hasCopy", hasPagesMethod(op));

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

    /**
     * Every operation gets an Options object (F827), so each call has a slot for
     * the optional per-call {@code timeout}, {@code headers} and
     * {@code max_retries} even when it has no parameter of its own.
     */
    @Override
    protected boolean mintsOptionsForEveryOperation() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaTemplate() {
        return "errors/schema_error.mustache";
    }

    /** {@inheritDoc} */
    @Override
    protected String errorSchemaFilePath(String className) {
        final String modulePath =
                NamingConvention.SNAKE_CASE.apply(moduleName.replaceAll("::", "/"));
        return Path.of(
                        getOutputDir(),
                        LIB_FOLDER,
                        modulePath,
                        "errors",
                        NamingConvention.SNAKE_CASE.apply(className) + ".rb")
                .toString();
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
    protected String getNullLiteral() {
        return "nil";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSourceFolder() {
        return "lib";
    }

    /**
     * Enables Gap K so polymorphic subtypes auto-emit their
     * discriminator field on serialization. The Ruby model template
     * (Dry::Struct) honours {@code defaultValue} on required
     * attributes via {@code Types::Required.default(value)} so the
     * discriminator renders as a required attribute with a default,
     * eliminating the need for the caller to set it.
     */
    @Override
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return true;
    }

    /**
     * Returns a single-quoted Ruby string literal for the
     * discriminator default value, matching the rest of the Ruby
     * codegen's string conventions (cf. {@link #fixEnumDefaultValue}).
     */
    @Override
    protected String formatDiscriminatorDefaultValue(String mappingName) {
        return "'" + mappingName + "'";
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getReservedEnumMemberNames() {
        return Set.of("VALUES");
    }
}

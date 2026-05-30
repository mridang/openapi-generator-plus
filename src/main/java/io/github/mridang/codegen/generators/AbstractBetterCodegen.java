package io.github.mridang.codegen.generators;

import com.samskivert.mustache.Mustache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.tags.Tag;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.EnumSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenDiscriminator;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenSecurity;
import org.openapitools.codegen.CodegenServer;
import org.openapitools.codegen.CodegenServerVariable;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.meta.features.ClientModificationFeature;
import org.openapitools.codegen.meta.features.DataTypeFeature;
import org.openapitools.codegen.meta.features.DocumentationFeature;
import org.openapitools.codegen.meta.features.GlobalFeature;
import org.openapitools.codegen.meta.features.ParameterFeature;
import org.openapitools.codegen.meta.features.SchemaSupportFeature;
import org.openapitools.codegen.meta.features.SecurityFeature;
import org.openapitools.codegen.meta.features.WireFormatFeature;
import org.openapitools.codegen.utils.ModelUtils;

import io.github.mridang.codegen.rules.DropInternalOperationsRule;
import io.github.mridang.codegen.rules.NormalizePrefixItemsRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base for all twelve language code generators (Ruby,
 * Python, PHP, Java, Node/TypeScript, C#, Swift, Go, Kotlin,
 * Dart, Elixir, and Rust). Centralizes shared concerns that
 * every generated client needs: security scheme detection
 * across all OpenAPI auth types, global and per-operation
 * server configuration extraction, reserved-word loading from
 * classpath resources, and post-processing hooks for
 * operations, models, and enum values.
 *
 * <p>Subclasses declare their conventions by implementing the
 * abstract accessor methods ({@link #getVarCasing},
 * {@link #getOperationIdCasing}, {@link #getEnumCasing},
 * {@link #getFormatterDockerImage}, {@link #getFormatterCommands})
 * and optionally overriding hook methods with defaults
 * ({@link #getFilenameCasing}, {@link #getParamCasing},
 * {@link #getUniqueItemsSetType}, {@link #getArrayContainerPattern},
 * {@link #getEmptyEnumVarName}). Subclasses may also override
 * {@link #formatArrayType}, {@link #formatMapType},
 * {@link #getMapKeyType}, {@link #getMapDefaultValueType},
 * {@link #isNumericEnumDatatype}, and {@link #quoteEnumValue}.
 */
public abstract class AbstractBetterCodegen extends DefaultCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBetterCodegen.class);

    private final Set<String> globalAuthOperationIds = new HashSet<>();

    /** Accumulates Options file metadata across per-tag postProcessOperationsWithModels calls. */
    private final List<Map<String, String>> accumulatedOptionsFiles = new ArrayList<>();

    /**
     * Set of schema names declared with {@code unevaluatedProperties: false} (OAS 3.1 /
     * JSON Schema 2020-12). Populated by {@link #fromModel(String, Schema)} and consumed
     * by {@link #postProcessModels(ModelsMap)} to set the
     * {@code isUnevaluatedPropertiesFalse} flag on each {@link ModelMap} so per-language
     * templates can emit strict-mode deserializers that reject unknown JSON keys. Gap AX.1.
     */
    private final Set<String> unevaluatedPropertiesFalseSchemas = new HashSet<>();

    protected boolean hasBasicAuth;
    protected boolean hasBearerAuth;
    protected boolean hasApiKeyAuth;
    protected boolean hasOAuth2ClientCredentials;
    protected boolean hasOAuth2Password;
    protected boolean hasOAuth2AuthorizationCode;
    protected boolean hasOAuth2Implicit;
    protected boolean hasOpenIdConnect;
    protected boolean hasAnyOAuth2;
    protected boolean generateTests;

    /**
     * Initializes shared codegen defaults by clearing the
     * inherited type and import mappings so that each language
     * subclass starts from a clean slate, and hides the
     * generation timestamp to keep output deterministic.
     */
    protected AbstractBetterCodegen() {
        super();
        typeMapping.clear();
        importMapping.clear();
        hideGenerationTimestamp = true;

        cliOptions.add(CliOption.newString("clientClassName",
                "Name of the generated API client class (default: Client).")
                .defaultValue("Client"));
        cliOptions.add(CliOption.newBoolean("generateTests",
                "Whether to generate test files alongside source files (default: true).")
                .defaultValue("true"));

        modifyFeatureSet(features -> features

                // --- SecurityFeature ---
                .securityFeatures(EnumSet.of(
                        SecurityFeature.BasicAuth,
                        SecurityFeature.BearerToken,
                        SecurityFeature.ApiKey,
                        SecurityFeature.OAuth2_Implicit,
                        SecurityFeature.OAuth2_Password,
                        SecurityFeature.OAuth2_ClientCredentials,
                        SecurityFeature.OAuth2_AuthorizationCode,
                        SecurityFeature.OpenIDConnect
                        // SecurityFeature.SignatureAuth,        // not implemented
                        // SecurityFeature.AWSV4Signature        // not implemented
                        ))

                // --- GlobalFeature ---
                .globalFeatures(EnumSet.of(
                        GlobalFeature.Host,
                        GlobalFeature.BasePath,
                        GlobalFeature.Info,
                        GlobalFeature.ExternalDocumentation,
                        GlobalFeature.MultiServer,
                        GlobalFeature.ParameterizedServer,
                        GlobalFeature.ParameterStyling
                        // GlobalFeature.Schemes,               // OAS 2.0 only
                        // GlobalFeature.PartialSchemes,         // OAS 2.0 only
                        // GlobalFeature.Consumes,               // OAS 2.0 only
                        // GlobalFeature.Produces,               // OAS 2.0 only
                        // GlobalFeature.Examples,               // not implemented
                        // GlobalFeature.XMLStructureDefinitions, // not implemented
                        // GlobalFeature.Callbacks,              // not implemented
                        // GlobalFeature.LinkObjects             // not implemented
                        ))

                // --- ParameterFeature (all supported) ---
                .parameterFeatures(EnumSet.of(
                        ParameterFeature.Path,
                        ParameterFeature.Query,
                        ParameterFeature.Header,
                        ParameterFeature.Body,
                        ParameterFeature.FormUnencoded,
                        ParameterFeature.FormMultipart,
                        ParameterFeature.Cookie))

                // --- WireFormatFeature ---
                .wireFormatFeatures(EnumSet.of(
                        WireFormatFeature.JSON
                        // WireFormatFeature.XML,               // not implemented
                        // WireFormatFeature.PROTOBUF,           // not implemented
                        // WireFormatFeature.Custom              // not implemented
                        ))

                // --- DocumentationFeature ---
                // .documentationFeatures(EnumSet.of(
                //     DocumentationFeature.Readme,             // not generated
                //     DocumentationFeature.Model,              // not generated
                //     DocumentationFeature.Api                 // not generated
                // ))

                // --- DataTypeFeature ---
                .dataTypeFeatures(EnumSet.of(
                        DataTypeFeature.Int32,
                        DataTypeFeature.Int64,
                        DataTypeFeature.Float,
                        DataTypeFeature.Double,
                        DataTypeFeature.Decimal,
                        DataTypeFeature.String,
                        DataTypeFeature.Byte,
                        DataTypeFeature.Binary,
                        DataTypeFeature.Boolean,
                        DataTypeFeature.Date,
                        DataTypeFeature.DateTime,
                        DataTypeFeature.Password,
                        DataTypeFeature.File,
                        DataTypeFeature.Uuid,
                        DataTypeFeature.Array,
                        DataTypeFeature.Object,
                        DataTypeFeature.Maps,
                        DataTypeFeature.Enum,
                        DataTypeFeature.ArrayOfEnum,
                        DataTypeFeature.ArrayOfModel,
                        DataTypeFeature.ArrayOfCollectionOfPrimitives,
                        DataTypeFeature.ArrayOfCollectionOfModel,
                        DataTypeFeature.ArrayOfCollectionOfEnum,
                        DataTypeFeature.MapOfEnum,
                        DataTypeFeature.MapOfModel,
                        DataTypeFeature.MapOfCollectionOfPrimitives,
                        DataTypeFeature.MapOfCollectionOfModel,
                        DataTypeFeature.MapOfCollectionOfEnum,
                        DataTypeFeature.Null,
                        DataTypeFeature.AnyType
                        // DataTypeFeature.Custom,              // not implemented
                        // DataTypeFeature.CollectionFormat,     // not implemented
                        // DataTypeFeature.CollectionFormatMulti // not implemented
                        ))

                // --- SchemaSupportFeature ---
                .schemaSupportFeatures(EnumSet.of(
                        SchemaSupportFeature.Simple,
                        SchemaSupportFeature.Composite,
                        SchemaSupportFeature.Polymorphism,
                        SchemaSupportFeature.Union,
                        SchemaSupportFeature.oneOf,
                        SchemaSupportFeature.anyOf,
                        SchemaSupportFeature.allOf
                        // SchemaSupportFeature.not             // not implemented
                        ))

                // --- ClientModificationFeature ---
                .clientModificationFeatures(EnumSet.of(
                        ClientModificationFeature.BasePath,
                        ClientModificationFeature.UserAgent,
                        ClientModificationFeature.Authorizations
                        // ClientModificationFeature.MockServer // not implemented
                        )));
    }

    /**
     * Strategy for handling all-uppercase identifiers (e.g.
     * {@code HTTP_METHOD}, {@code MAX_RETRIES}) when applying
     * variable name casing. Different languages treat these
     * differently: Java preserves them as constants, Ruby
     * lowercases first to avoid constant treatment, and most
     * other languages simply apply casing directly.
     */
    protected enum UppercaseIdentifierStrategy {
        /** Always apply {@link #getVarCasing()} directly (default for most languages). */
        APPLY_CASING {
            @Override
            String apply(String name, NamingConvention casing) {
                return casing.apply(name);
            }
        },
        /**
         * If the input matches {@code ^[A-Z0-9_]*$}, return
         * it as-is to preserve intentional constant naming
         * (used by Java).
         */
        PRESERVE {
            @Override
            String apply(String name, NamingConvention casing) {
                if (name.matches("^[A-Z0-9_]*$")) {
                    return name;
                }
                return casing.apply(name);
            }
        },
        /**
         * If the input matches {@code ^[A-Z_]*$}, lowercase it
         * first to avoid Ruby's underscore helper treating it
         * as a constant (used by Ruby).
         */
        LOWERCASE_FIRST {
            @Override
            String apply(String name, NamingConvention casing) {
                if (name.matches("^[A-Z_]*$")) {
                    return casing.apply(name.toLowerCase(Locale.ROOT));
                }
                return casing.apply(name);
            }
        };

        abstract String apply(String name, NamingConvention casing);
    }

    /**
     * Returns the naming convention for variable and property
     * names in this language (e.g. CAMEL_CASE for Java,
     * SNAKE_CASE for Python).
     */
    protected abstract NamingConvention getVarCasing();

    /**
     * Returns the naming convention for operation ID method
     * names in this language (e.g. CAMEL_CASE for Java,
     * SNAKE_CASE for Python).
     */
    protected abstract NamingConvention getOperationIdCasing();

    /**
     * Returns the naming convention for enum constant names
     * in this language (e.g. UPPER_SNAKE_CASE for Java,
     * PASCAL_CASE for C#).
     */
    protected abstract NamingConvention getEnumCasing();

    /**
     * Returns the Docker image used to run the code formatter
     * for this language (e.g. "eclipse-temurin:17-jdk" for
     * Java, "python:3-slim" for Python).
     */
    protected abstract String getFormatterDockerImage();

    /**
     * Returns the shell commands to run inside the formatter
     * Docker container. Commands are joined with {@code &&}
     * and executed via {@code sh -c}.
     */
    protected abstract String[] getFormatterCommands();

    /**
     * Returns the naming convention for source file names, or
     * null if filenames match class names. Override in languages
     * like Python (SNAKE_CASE) or TypeScript (KEBAB_CASE).
     */
    @Nullable protected NamingConvention getFilenameCasing() {
        return null;
    }

    /**
     * Returns the naming convention for method parameter names,
     * or null if parameters use the same convention as variables.
     * Override in languages like TypeScript or C# where param
     * casing differs from property casing.
     */
    @Nullable protected NamingConvention getParamCasing() {
        return null;
    }

    /**
     * Returns the set container type prefix for unique-item
     * arrays (e.g. {@code "LinkedHashSet<"} for Java, "set[" for
     * Python), or null if the language does not support set
     * types.
     */
    @Nullable protected String getUniqueItemsSetType() {
        return null;
    }

    /**
     * Returns the regex pattern matching the array container
     * prefix in type declarations. Used to replace array types
     * with set types for unique-item properties.
     */
    protected String getArrayContainerPattern() {
        return "^List<";
    }

    /**
     * Returns the enum constant name used for empty string
     * values. Most languages use "EMPTY"; override for
     * PascalCase languages like TypeScript ("Empty").
     */
    protected String getEmptyEnumVarName() {
        return "EMPTY";
    }

    /**
     * Returns the quote character used in generated string
     * literals for this language (either single or double
     * quote). Defaults to double-quote; languages that use
     * single-quote (Python, Dart, PHP, Node, Ruby) override.
     */
    protected char getQuoteChar() {
        return '"';
    }

    /**
     * Returns whether the quote character should be escaped
     * with a backslash or stripped from input. Defaults to
     * {@code true} (escape). Override to return {@code false}
     * for languages that strip quotation marks.
     */
    protected boolean shouldEscapeQuotationMark() {
        return true;
    }

    /**
     * Processes user-supplied additional properties after the
     * codegen options are resolved. Enables post-process file
     * hooks, clears default supporting files so subclasses
     * control which files are emitted, and reads the
     * {@code generateTests} and {@code clientClassName} options.
     */
    @Override
    public void processOpts() {
        super.processOpts();
        setEnablePostProcessFile(true);
        supportingFiles.clear();

        if (additionalProperties.containsKey("generateTests")) {
            generateTests =
                    Boolean.parseBoolean(additionalProperties.get("generateTests").toString());
        }
        additionalProperties.put("generateTests", generateTests);

        getPropertyOrDefault("clientClassName", "Client");

        for (SupportingFileSpec spec : getSupportingFileSpecs()) {
            supportingFiles.add(
                    new SupportingFile(spec.template(), spec.folder(), spec.outputName()));
        }
    }

    /**
     * Declarative list of supporting files to register during
     * {@link #processOpts()}. Subclasses override this hook to
     * eliminate boilerplate {@code supportingFiles.add(new
     * SupportingFile(...))} calls. The default is an empty list.
     *
     * <p>This hook is invoked at the end of the base
     * {@code processOpts()} after subclass-specific property
     * resolution has completed in {@code super.processOpts()};
     * however, because the subclass override of
     * {@code processOpts} calls {@code super.processOpts()}
     * first, any fields the subclass computes in its own
     * {@code processOpts} body must be resolved <em>before</em>
     * the {@code super} call if they are referenced from this
     * method. Conditional or imperative supporting files (e.g.
     * those gated by {@code generateTests} or computed from
     * resolved options) should remain in the subclass
     * {@code processOpts} body.
     *
     * @return list of supporting file specifications to
     *     register; empty by default
     */
    protected List<SupportingFileSpec> getSupportingFileSpecs() {
        return List.of();
    }

    /**
     * Specification for a supporting file to register. Used by
     * {@link #getSupportingFileSpecs()} as a lightweight
     * data-carrier alternative to constructing
     * {@link SupportingFile} instances directly. Implemented as
     * a plain final class (rather than a record) because the
     * project targets Java 11.
     */
    protected static final class SupportingFileSpec {
        private final String template;
        private final String folder;
        private final String outputName;

        /**
         * Creates a new specification for a supporting file.
         *
         * @param template   the Mustache template name
         * @param folder     the destination sub-folder relative to the output dir
         * @param outputName the generated file name
         */
        public SupportingFileSpec(String template, String folder, String outputName) {
            this.template = template;
            this.folder = folder;
            this.outputName = outputName;
        }

        /** Returns the Mustache template name. */
        public String template() {
            return template;
        }

        /** Returns the destination sub-folder relative to the output dir. */
        public String folder() {
            return folder;
        }

        /** Returns the generated file name. */
        public String outputName() {
            return outputName;
        }
    }

    /**
     * Processes the parsed OpenAPI document after it is loaded.
     * Detects all security scheme types, registers supporting
     * files for auth, generates per-scheme authenticator
     * classes, extracts server configuration, and optionally
     * writes test fixtures when test generation is enabled.
     */
    @Override
    public void processOpenAPI(OpenAPI openAPI) {
        super.processOpenAPI(openAPI);
        // Gap AZ — degrade OAS 3.1 `prefixItems` tuple arrays to plain
        // array-of-Object before the rest of the pipeline inspects schemas.
        // See NormalizePrefixItemsRule for the chosen cross-language strategy.
        new NormalizePrefixItemsRule().apply(openAPI, java.util.Collections.emptyMap(), LOGGER);
        // Drop {{x-internal: true}} operations from the SDK generator's
        // view. Chasm still sees the full spec (it loads the file directly);
        // only the codegen-side document is pruned so transport-test
        // endpoints baked into the petstore spec don't generate junk SDK
        // methods. No-op when the spec contains no x-internal operations.
        new DropInternalOperationsRule().apply(openAPI, java.util.Collections.emptyMap(), LOGGER);
        detectSecuritySchemes(openAPI);
        additionalProperties.put("hasBasicAuth", hasBasicAuth);
        additionalProperties.put("hasBearerAuth", hasBearerAuth);
        additionalProperties.put("hasApiKeyAuth", hasApiKeyAuth);
        additionalProperties.put("hasOAuth2ClientCredentials", hasOAuth2ClientCredentials);
        additionalProperties.put("hasOAuth2Password", hasOAuth2Password);
        additionalProperties.put("hasOAuth2AuthorizationCode", hasOAuth2AuthorizationCode);
        additionalProperties.put("hasOAuth2Implicit", hasOAuth2Implicit);
        additionalProperties.put("hasOpenIdConnect", hasOpenIdConnect);
        additionalProperties.put("hasAnyOAuth2", hasAnyOAuth2);
        registerAuthSupportingFiles();
        generatePerSchemeAuthenticators(openAPI);
        processServers(openAPI);
        if (generateTests) {
            writeTestFixtures();
        }
    }

    /**
     * Extracts global server definitions from the OpenAPI spec
     * and populates template properties for server selection
     * code generation. Sets {@code hasServers} and
     * {@code serverConfigs} in additional properties so
     * templates can render server URL constants and variable
     * substitution logic.
     */
    private void processServers(OpenAPI openAPI) {
        final List<Server> servers = openAPI.getServers();
        if (servers == null || servers.isEmpty()) {
            additionalProperties.put("hasServers", false);
            return;
        }
        additionalProperties.put("hasServers", true);

        final List<Map<String, Object>> serverList = new ArrayList<>();
        for (int i = 0; i < servers.size(); i++) {
            final Server server = servers.get(i);
            final Map<String, Object> serverMap = new HashMap<>();
            serverMap.put("serverIndex", String.valueOf(i));
            serverMap.put("serverUrl", Optional.ofNullable(server.getUrl()).orElse(""));
            serverMap.put("serverDescription", server.getDescription());

            final boolean hasVariables =
                    Optional.ofNullable(server.getVariables())
                            .filter(v -> !v.isEmpty())
                            .isPresent();
            serverMap.put("hasVariables", hasVariables);
            if (hasVariables) {
                final List<Map<String, Object>> varList = new ArrayList<>();
                for (Map.Entry<String, ServerVariable> varEntry :
                        server.getVariables().entrySet()) {
                    final ServerVariable sv = varEntry.getValue();
                    final Map<String, Object> varMap = new HashMap<>();
                    varMap.put("varName", varEntry.getKey());
                    varMap.put("varDefault", sv.getDefault());
                    varMap.put("varDescription", sv.getDescription());
                    Optional.ofNullable(sv.getEnum())
                            .filter(e -> !e.isEmpty())
                            .ifPresentOrElse(
                                    enumValues -> {
                                        varMap.put("hasEnumValues", true);
                                        varMap.put("varEnumValues", enumValues);
                                    },
                                    () -> varMap.put("hasEnumValues", false));
                    varList.add(varMap);
                }
                serverMap.put("serverVariables", varList);
            }
            serverList.add(serverMap);
        }
        final boolean anyServerHasVariables =
                serverList.stream().anyMatch(s -> Boolean.TRUE.equals(s.get("hasVariables")));
        additionalProperties.put("hasAnyServerVariables", anyServerHasVariables);
        additionalProperties.put("serverConfigs", serverList);
    }

    /**
     * Scans the OpenAPI components for security scheme
     * definitions and sets the corresponding boolean flags
     * (basic, bearer, API key, each OAuth2 flow, OpenID
     * Connect). These flags drive conditional template
     * rendering and supporting-file registration downstream.
     */
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only constants")
    private void detectSecuritySchemes(OpenAPI openAPI) {
        if (openAPI.getComponents() == null
                || openAPI.getComponents().getSecuritySchemes() == null) {
            return;
        }
        for (Map.Entry<String, SecurityScheme> entry :
                openAPI.getComponents().getSecuritySchemes().entrySet()) {
            final SecurityScheme scheme = entry.getValue();
            if (scheme.getType() == SecurityScheme.Type.HTTP) {
                if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                    hasBasicAuth = true;
                } else if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                    hasBearerAuth = true;
                }
            } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
                hasApiKeyAuth = true;
            } else if (scheme.getType() == SecurityScheme.Type.OAUTH2
                    && scheme.getFlows() != null) {
                if (scheme.getFlows().getClientCredentials() != null) {
                    hasOAuth2ClientCredentials = true;
                }
                if (scheme.getFlows().getPassword() != null) {
                    hasOAuth2Password = true;
                }
                if (scheme.getFlows().getAuthorizationCode() != null) {
                    hasOAuth2AuthorizationCode = true;
                }
                if (scheme.getFlows().getImplicit() != null) {
                    hasOAuth2Implicit = true;
                }
            } else if (scheme.getType() == SecurityScheme.Type.OPENIDCONNECT) {
                hasOpenIdConnect = true;
            }
        }
        hasAnyOAuth2 =
                hasOAuth2ClientCredentials
                        || hasOAuth2Password
                        || hasOAuth2AuthorizationCode
                        || hasOAuth2Implicit;
    }

    // =========================================================================
    // Auth directory / filename contracts — implemented by each language
    // =========================================================================

    /**
     * Returns the relative path (from the output root) of the auth source
     * directory for this language; e.g. {@code "src/main/java/com/example/auth"}.
     */
    protected abstract String getAuthDir();

    /**
     * Returns the relative path (from the output root) of the OAuth2/OIDC
     * sub-directory. The default implementation appends {@code "oauth"} to
     * {@link #getAuthDir()}; languages with a different OAuth directory
     * (e.g. Swift's {@code "OAuth"}) override this method.
     */
    protected String getOAuthDir() {
        return Path.of(getAuthDir(), "oauth").toString();
    }

    /**
     * Converts a well-known snake_case file stem (e.g. {@code "basic_authenticator"})
     * to the full language-specific filename including extension
     * (e.g. {@code "BasicAuthenticator.java"}, {@code "basic_authenticator.py"}).
     *
     * <p>PascalCase languages should use {@link #pascalAuthFilename} to
     * handle the OAuth2 / OpenId naming quirks correctly.
     */
    protected abstract String toAuthFilename(String stem);

    private static final Map<String, String> PASCAL_STEM_LOOKUP = buildPascalStemLookup();

    @SuppressFBWarnings(value = "HARD_CODE_PASSWORD",
            justification = "Not a credential: map values are generated source-class names that"
                    + " happen to contain 'Password'; they are not hardcoded secrets")
    private static Map<String, String> buildPascalStemLookup() {
        final Map<String, String> m = new HashMap<>();
        m.put("oauth2_token_manager", "OAuth2TokenManager");
        m.put("oauth2_client_credentials_authenticator", "OAuth2ClientCredentialsAuthenticator");
        m.put("oauth2_password_authenticator", "OAuth2PasswordAuthenticator");
        m.put("oauth2_auth_code_authenticator", "OAuth2AuthorizationCodeAuthenticator");
        m.put("oauth2_implicit_authenticator", "OAuth2ImplicitAuthenticator");
        m.put("openid_connect_authenticator", "OpenIdConnectAuthenticator");
        m.put("client_auth_method", "ClientAuthMethod");
        return Collections.unmodifiableMap(m);
    }

    /**
     * Converts a snake_case stem to a PascalCase filename with the given
     * extension, using a lookup table for names that
     * {@link NamingConvention#PASCAL_CASE} does not capitalise correctly
     * (specifically OAuth2* and OpenIdConnect*).
     * Call this from {@link #toAuthFilename} in PascalCase languages.
     */
    protected final String pascalAuthFilename(String stem, String extension) {
        return PASCAL_STEM_LOOKUP.getOrDefault(stem, NamingConvention.PASCAL_CASE.apply(stem))
                + extension;
    }

    /**
     * Returns {@code true} if this language should emit a
     * {@code base_authenticator} supporting file. Python registers it in
     * {@code processOpts} instead; Rust uses a {@code mod.rs} approach.
     * The default is {@code true}.
     */
    protected boolean emitsBaseAuthenticator() {
        return true;
    }

    /**
     * Returns the snake_case stem used to derive the
     * HttpAwareAuthenticator filename. Override in C# to return
     * {@code "i_http_aware_authenticator"} (which becomes
     * {@code "IHttpAwareAuthenticator.cs"} after {@link #toAuthFilename}).
     */
    protected String httpAwareAuthenticatorStem() {
        return "http_aware_authenticator";
    }

    // =========================================================================
    // registerAuthSupportingFiles — base implementation (non-abstract)
    // =========================================================================

    /*
     * Registers the language-specific auth supporting files (base class,
     * HTTP-aware interface, and the concrete authenticator classes enabled by
     * the security-scheme flags set in #detectSecuritySchemes).
     *
     * Subclasses that need additional files (e.g. test OAuth helpers, Rust
     * mod.rs) should call super.registerAuthSupportingFiles() and then
     * add their extras. Subclasses that have no additional files (Ruby, Go,
     * Kotlin, Swift, Dart, Elixir) can skip the override entirely.
     */
    /**
     * Condition controlling when an OAuth test file is added.
     * Used by {@link #getOAuthTestFileSpecs()} + the base-class loop
     * in {@link #registerAuthSupportingFiles()}.
     */
    protected enum OAuthTestCondition {
        /** Token manager + any authenticator test (hasAnyOAuth2 || hasOpenIdConnect). */
        ANY_OAUTH2_OR_OIDC,
        /** Auth-code authenticator test. */
        AUTH_CODE,
        /** Implicit authenticator test. */
        IMPLICIT,
        /** Client-credentials authenticator test. */
        CLIENT_CREDENTIALS,
        /** Password authenticator test. */
        PASSWORD,
        /** OpenID Connect authenticator test. */
        OIDC,
        /** HTTP Basic authenticator test. */
        BASIC
    }

    /** Descriptor for a single OAuth test supporting file. */
    public static final class OAuthTestFileSpec {
        private final String templatePath;
        private final String outputDir;
        private final String outputFile;
        private final OAuthTestCondition condition;

        public OAuthTestFileSpec(
                String templatePath,
                String outputDir,
                String outputFile,
                OAuthTestCondition condition) {
            this.templatePath = templatePath;
            this.outputDir = outputDir;
            this.outputFile = outputFile;
            this.condition = condition;
        }

        /** Returns the template path relative to the language template root. */
        public String templatePath() { return templatePath; }

        /** Returns the output directory for the generated file. */
        public String outputDir() { return outputDir; }

        /** Returns the output filename for the generated file. */
        public String outputFile() { return outputFile; }

        /** Returns the condition controlling when this file is emitted. */
        public OAuthTestCondition condition() { return condition; }
    }

    /**
     * Returns the list of OAuth test files to register. Override in each
     * language subclass; the base class loops over these and applies the
     * {@link OAuthTestCondition} filter inside
     * {@link #registerAuthSupportingFiles()}. Default: empty (no test files).
     */
    protected List<OAuthTestFileSpec> getOAuthTestFileSpecs() {
        return List.of();
    }

    protected void registerAuthSupportingFiles() {
        if (emitsBaseAuthenticator()) {
            supportingFiles.add(new SupportingFile(
                    "auth/base_authenticator.mustache",
                    getAuthDir(),
                    toAuthFilename("base_authenticator")));
        }
        supportingFiles.add(new SupportingFile(
                "auth/http_aware_authenticator.mustache",
                getAuthDir(),
                toAuthFilename(httpAwareAuthenticatorStem())));
        if (hasBasicAuth) {
            supportingFiles.add(new SupportingFile(
                    "auth/basic_authenticator.mustache",
                    getAuthDir(),
                    toAuthFilename("basic_authenticator")));
        }
        if (hasBearerAuth) {
            supportingFiles.add(new SupportingFile(
                    "auth/bearer_authenticator.mustache",
                    getAuthDir(),
                    toAuthFilename("bearer_authenticator")));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(new SupportingFile(
                    "auth/api_key_location.mustache",
                    getAuthDir(),
                    toAuthFilename("api_key_location")));
            supportingFiles.add(new SupportingFile(
                    "auth/api_key_authenticator.mustache",
                    getAuthDir(),
                    toAuthFilename("api_key_authenticator")));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile(
                    "auth/oauth/oauth2_token_manager.mustache",
                    getOAuthDir(),
                    toAuthFilename("oauth2_token_manager")));
        }
        if (hasOAuth2ClientCredentials || hasOAuth2Password) {
            supportingFiles.add(new SupportingFile(
                    "auth/oauth/client_auth_method.mustache",
                    getOAuthDir(),
                    toAuthFilename("client_auth_method")));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(new SupportingFile(
                    "auth/oauth/oauth2_client_credentials_authenticator.mustache",
                    getOAuthDir(),
                    toAuthFilename("oauth2_client_credentials_authenticator")));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(new SupportingFile(
                    "auth/oauth/oauth2_password_authenticator.mustache",
                    getOAuthDir(),
                    toAuthFilename("oauth2_password_authenticator")));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(new SupportingFile(
                    "auth/oauth/oauth2_auth_code_authenticator.mustache",
                    getOAuthDir(),
                    toAuthFilename("oauth2_auth_code_authenticator")));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(new SupportingFile(
                    "auth/oauth/oauth2_implicit_authenticator.mustache",
                    getOAuthDir(),
                    toAuthFilename("oauth2_implicit_authenticator")));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile(
                    "auth/oauth/openid_connect_authenticator.mustache",
                    getOAuthDir(),
                    toAuthFilename("openid_connect_authenticator")));
        }

        // B3: Process per-language OAuth test file declarations
        if (generateTests) {
            for (final OAuthTestFileSpec spec : getOAuthTestFileSpecs()) {
                final boolean shouldAdd;
                if (spec.condition() == OAuthTestCondition.ANY_OAUTH2_OR_OIDC) {
                    shouldAdd = hasAnyOAuth2 || hasOpenIdConnect;
                } else if (spec.condition() == OAuthTestCondition.AUTH_CODE) {
                    shouldAdd = hasOAuth2AuthorizationCode;
                } else if (spec.condition() == OAuthTestCondition.IMPLICIT) {
                    shouldAdd = hasOAuth2Implicit;
                } else if (spec.condition() == OAuthTestCondition.CLIENT_CREDENTIALS) {
                    shouldAdd = hasOAuth2ClientCredentials;
                } else if (spec.condition() == OAuthTestCondition.PASSWORD) {
                    shouldAdd = hasOAuth2Password;
                } else if (spec.condition() == OAuthTestCondition.OIDC) {
                    shouldAdd = hasOpenIdConnect;
                } else if (spec.condition() == OAuthTestCondition.BASIC) {
                    shouldAdd = hasBasicAuth;
                } else {
                    shouldAdd = false;
                }
                if (shouldAdd) {
                    supportingFiles.add(
                            new SupportingFile(spec.templatePath(), spec.outputDir(), spec.outputFile()));
                }
            }
        }
    }

    // =========================================================================
    // SchemeAuthSpec — language-agnostic descriptor for a single security scheme
    // =========================================================================

    /**
     * Language-agnostic descriptor for a single OpenAPI security scheme.
     * Built once by {@link #buildSchemeAuthSpec} and passed to the
     * language-specific {@link #renderSchemeAuthenticator} to produce source.
     */
    public static final class SchemeAuthSpec {
        private final String schemeName;
        private final String schemeClass;
        private final String oauthSuffix;
        private final String baseClass;
        private final boolean isOAuth;
        private final List<String> paramNames;
        @Nullable private final String keyParamName;
        @Nullable private final String keyIn;
        @Nullable private final String tokenUrl;
        @Nullable private final String authorizationUrl;
        @Nullable private final String refreshUrl;
        @Nullable private final String openIdConnectUrl;
        @Nullable private final Map<String, String> scopes;

        public SchemeAuthSpec(String schemeName, String schemeClass, String oauthSuffix,
                String baseClass, boolean isOAuth, List<String> paramNames,
                @Nullable String keyParamName, @Nullable String keyIn,
                @Nullable String tokenUrl, @Nullable String authorizationUrl,
                @Nullable String refreshUrl, @Nullable String openIdConnectUrl,
                @Nullable Map<String, String> scopes) {
            this.schemeName = schemeName;
            this.schemeClass = schemeClass;
            this.oauthSuffix = oauthSuffix;
            this.baseClass = baseClass;
            this.isOAuth = isOAuth;
            this.paramNames = List.copyOf(paramNames);
            this.keyParamName = keyParamName;
            this.keyIn = keyIn;
            this.tokenUrl = tokenUrl;
            this.authorizationUrl = authorizationUrl;
            this.refreshUrl = refreshUrl;
            this.openIdConnectUrl = openIdConnectUrl;
            this.scopes = scopes != null ? Collections.unmodifiableMap(new HashMap<>(scopes)) : null;
        }

        public String schemeName() { return schemeName; }
        public String schemeClass() { return schemeClass; }
        public String oauthSuffix() { return oauthSuffix; }
        public String baseClass() { return baseClass; }
        public boolean isOAuth() { return isOAuth; }
        public List<String> paramNames() { return paramNames; }
        @Nullable public String keyParamName() { return keyParamName; }
        @Nullable public String keyIn() { return keyIn; }
        @Nullable public String tokenUrl() { return tokenUrl; }
        @Nullable public String authorizationUrl() { return authorizationUrl; }
        @Nullable public String refreshUrl() { return refreshUrl; }
        @Nullable public String openIdConnectUrl() { return openIdConnectUrl; }
        @Nullable public Map<String, String> scopes() { return scopes; }
    }

    /**
     * Builds a {@link SchemeAuthSpec} from a raw OpenAPI security scheme entry.
     * Returns {@code null} for unsupported / unrecognised scheme types.
     */
    @Nullable
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only HTTP scheme constants (basic/bearer)")
    protected SchemeAuthSpec buildSchemeAuthSpec(String schemeName, SecurityScheme scheme) {
        final String cls = NamingConvention.PASCAL_CASE.apply(schemeName);
        if (scheme.getType() == SecurityScheme.Type.HTTP) {
            if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                return new SchemeAuthSpec(schemeName, cls, "", "BasicAuthenticator", false,
                        List.of("host", "username", "password"),
                        null, null, null, null, null, null, null);
            }
            if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                return new SchemeAuthSpec(schemeName, cls, "", "BearerAuthenticator", false,
                        List.of("host", "token"),
                        null, null, null, null, null, null, null);
            }
        } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
            final String location =
                    NamingConvention.UPPER_SNAKE_CASE.apply(scheme.getIn().toString());
            return new SchemeAuthSpec(schemeName, cls, "", "ApiKeyAuthenticator", false,
                    List.of("host", "apiKey"),
                    scheme.getName(), location, null, null, null, null, null);
        } else if (scheme.getType() == SecurityScheme.Type.OAUTH2
                && scheme.getFlows() != null) {
            return buildOAuthSpec(schemeName, cls, scheme);
        } else if (scheme.getType() == SecurityScheme.Type.OPENIDCONNECT) {
            return new SchemeAuthSpec(schemeName, cls, "", "OpenIdConnectAuthenticator", true,
                    List.of("host", "clientId", "clientSecret", "redirectUri"),
                    null, null, null, null, null, scheme.getOpenIdConnectUrl(), null);
        }
        LOGGER.warn("Unsupported security scheme type: {}", scheme.getType());
        return null;
    }

    @Nullable
    private SchemeAuthSpec buildOAuthSpec(
            String schemeName, String cls, SecurityScheme scheme) {
        if (scheme.getFlows().getClientCredentials() != null) {
            final var flow = scheme.getFlows().getClientCredentials();
            return new SchemeAuthSpec(schemeName, cls, "ClientCredentials",
                    "OAuth2ClientCredentialsAuthenticator", true,
                    List.of("host", "clientId", "clientSecret"),
                    null, null, flow.getTokenUrl(), null, null, null, flow.getScopes());
        }
        if (scheme.getFlows().getPassword() != null) {
            final var flow = scheme.getFlows().getPassword();
            return new SchemeAuthSpec(schemeName, cls, "Password",
                    "OAuth2PasswordAuthenticator", true,
                    List.of("host", "clientId", "clientSecret", "username", "password"),
                    null, null, flow.getTokenUrl(), null, flow.getRefreshUrl(), null,
                    flow.getScopes());
        }
        if (scheme.getFlows().getAuthorizationCode() != null) {
            final var flow = scheme.getFlows().getAuthorizationCode();
            return new SchemeAuthSpec(schemeName, cls, "AuthorizationCode",
                    "OAuth2AuthorizationCodeAuthenticator", true,
                    List.of("host", "clientId", "clientSecret", "redirectUri"),
                    null, null, flow.getTokenUrl(), flow.getAuthorizationUrl(),
                    flow.getRefreshUrl(), null, flow.getScopes());
        }
        if (scheme.getFlows().getImplicit() != null) {
            final var flow = scheme.getFlows().getImplicit();
            return new SchemeAuthSpec(schemeName, cls, "Implicit",
                    "OAuth2ImplicitAuthenticator", true,
                    List.of("host", "clientId"),
                    null, null, null, flow.getAuthorizationUrl(), null, null,
                    flow.getScopes());
        }
        LOGGER.warn("Unsupported OAuth2 flow for scheme: {}", schemeName);
        return null;
    }

    // =========================================================================
    // generatePerSchemeAuthenticators — lifted to base
    // =========================================================================

    /**
     * Generates a concrete per-scheme authenticator source file for every
     * security scheme defined in the OpenAPI spec. Subclasses provide the
     * language-specific rendering via {@link #renderSchemeAuthenticator}.
     */
    protected void generatePerSchemeAuthenticators(OpenAPI openAPI) {
        if (openAPI.getComponents() == null
                || openAPI.getComponents().getSecuritySchemes() == null) {
            return;
        }
        for (final Map.Entry<String, SecurityScheme> entry :
                openAPI.getComponents().getSecuritySchemes().entrySet()) {
            final SchemeAuthSpec spec = buildSchemeAuthSpec(entry.getKey(), entry.getValue());
            if (spec == null) continue;
            final String code = renderSchemeAuthenticator(spec);
            if (code == null || code.isBlank()) continue;
            final String dir = spec.isOAuth() ? getOAuthDir() : getAuthDir();
            final String stem = NamingConvention.SNAKE_CASE.apply(
                    spec.schemeClass() + spec.oauthSuffix() + "Authenticator");
            final String filename = toAuthFilename(stem);
            final String filePath = Path.of(outputFolder, dir, filename).toString();
            writeFile(filePath, code);
            postProcessFile(Path.of(filePath).toFile(), "source");
            postWriteSchemeAuthenticator(spec, filePath);
        }
    }

    /**
     * Renders language-specific source for a single security scheme.
     * The default no-op means no per-scheme file is generated; each
     * language subclass overrides to produce its own content.
     */
    protected String renderSchemeAuthenticator(SchemeAuthSpec spec) {
        return "";
    }

    /**
     * Called after the main per-scheme authenticator file has been written.
     * When this generator implements {@link WithTypeSignatureSupport}, renders
     * a companion type-signature file (e.g. {@code .rbs}) using the template
     * declared by {@link WithTypeSignatureSupport#getAuthenticatorSignatureTemplate()}.
     * The companion context is built from {@link #baseSchemeContext(SchemeAuthSpec)}
     * plus a {@code constructorParams} list whose {@code name} entries are
     * normalised via {@link #toVarName(String)}.
     * Languages that do not implement {@link WithTypeSignatureSupport} receive
     * the inherited no-op.
     */
    protected void postWriteSchemeAuthenticator(SchemeAuthSpec spec, String writtenPath) {
        if (!(this instanceof WithTypeSignatureSupport)) {
            return;
        }
        final WithTypeSignatureSupport ts = (WithTypeSignatureSupport) this;
        final String template = ts.getAuthenticatorSignatureTemplate();
        if (template.isEmpty()) {
            return;
        }
        final Map<String, Object> ctx = baseSchemeContext(spec);
        final List<Map<String, String>> constructorParams = new ArrayList<>();
        for (final String name : spec.paramNames()) {
            final Map<String, String> param = new HashMap<>();
            param.put("name", toVarName(name));
            param.put("type", "String");
            constructorParams.add(param);
        }
        ctx.put("constructorParams", constructorParams);
        final int lastDot = writtenPath.lastIndexOf('.');
        final String companionPath = lastDot >= 0
                ? writtenPath.substring(0, lastDot) + ts.getSignatureFileExtension()
                : writtenPath + ts.getSignatureFileExtension();
        writeFile(companionPath, renderOptionsTemplate(template, ctx));
        postProcessFile(Path.of(companionPath).toFile(), "source");
    }

    /**
     * Returns a base context map seeded with all additional properties and
     * the common scheme-descriptor fields. Language overrides of
     * {@link #renderSchemeAuthenticator} typically start from this and add
     * language-specific entries.
     */
    protected Map<String, Object> baseSchemeContext(SchemeAuthSpec spec) {
        final Map<String, Object> ctx = new HashMap<>(additionalProperties);
        ctx.put("schemeName", spec.schemeName());
        ctx.put("className", spec.schemeClass() + spec.oauthSuffix() + "Authenticator");
        ctx.put("baseClass", spec.baseClass());
        ctx.put("isOAuth", spec.isOAuth());
        return ctx;
    }

    /**
     * Computes the authenticator class name for a given
     * security scheme. Appends a flow-specific suffix for
     * OAuth2 schemes (e.g. "ClientCredentials", "Password")
     * and a generic "Authenticator" suffix for all other
     * scheme types.
     */
    protected String toAuthClassName(CodegenSecurity auth) {
        final String base = NamingConvention.PASCAL_CASE.apply(auth.name);
        if (Boolean.TRUE.equals(auth.isBasicBasic)) {
            return base + "Authenticator";
        }
        if (Boolean.TRUE.equals(auth.isBasicBearer)) {
            return base + "Authenticator";
        }
        if (Boolean.TRUE.equals(auth.isApiKey)) {
            return base + "Authenticator";
        }
        if (auth.isCode != null && auth.isCode) {
            return base + "AuthorizationCodeAuthenticator";
        }
        if (auth.isPassword != null && auth.isPassword) {
            return base + "PasswordAuthenticator";
        }
        if (auth.flow != null && "application".equals(auth.flow)) {
            return base + "ClientCredentialsAuthenticator";
        }
        if (auth.flow != null && "implicit".equals(auth.flow)) {
            return base + "ImplicitAuthenticator";
        }
        return base + "Authenticator";
    }

    /**
     * Returns the value of an additional property if it exists,
     * otherwise sets it to the given default and returns that.
     * Ensures template properties always have a defined value
     * without requiring null checks in templates.
     */
    protected String getPropertyOrDefault(String key, String defaultValue) {
        return Optional.ofNullable((String) additionalProperties.get(key))
                .orElseGet(
                        () -> {
                            additionalProperties.put(key, defaultValue);
                            return defaultValue;
                        });
    }

    /**
     * Loads a set of reserved words from a classpath text
     * resource. Each non-blank, non-comment line becomes a
     * reserved word. This keeps reserved-word lists in plain
     * text files alongside the templates rather than
     * hard-coding them in Java.
     */
    protected static Set<String> loadReservedWords(String resourcePath) {
        try (InputStream is = AbstractBetterCodegen.class.getResourceAsStream(resourcePath);
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        Objects.requireNonNull(
                                                is,
                                                "Reserved words resource not found: "
                                                        + resourcePath),
                                        StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load reserved words from " + resourcePath, e);
        }
    }

    /**
     * Returns {@link CodegenType#CLIENT} because all subclasses
     * produce client SDK libraries, not server stubs or
     * documentation.
     */
    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    /**
     * Escapes a reserved word by prepending an underscore.
     * This avoids collisions with language keywords while
     * keeping the generated name recognizable.
     */
    @Override
    public String escapeReservedWord(String name) {
        return "_" + name;
    }

    /**
     * Resolves an OpenAPI schema to its language-specific type
     * name by first checking the type mapping table, then
     * falling back to the raw schema type. This ensures that
     * mapped types like "integer" to "int" are applied
     * consistently.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String getSchemaType(Schema schema) {
        final String type = super.getSchemaType(schema);
        if (typeMapping.containsKey(type)) {
            return typeMapping.get(type);
        }
        return type;
    }

    /**
     * Returns the output directory for model source files,
     * constructed from the output root, {@link #getSourceFolder()},
     * and the model package (dots replaced by path separators).
     * Languages with a significantly different directory structure
     * (Go, Dart, Elixir, Rust, Swift, PHP, C#) override this
     * method directly and still declare {@link #getSourceFolder()}
     * for completeness.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, getSourceFolder(),
                modelPackage.replace('.', '/')).toString();
    }

    /**
     * Returns the output directory for API source files,
     * constructed from the output root, {@link #getSourceFolder()},
     * and the API package (dots replaced by path separators).
     * Languages that need a different structure override this method.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, getSourceFolder(),
                apiPackage.replace('.', '/')).toString();
    }

    /**
     * Returns the default value expression for a schema, handling
     * the boolean case using {@link #getTrueLiteral()} and
     * {@link #getFalseLiteral()}. Language subclasses override
     * this method to add handling for strings, arrays, maps, and
     * enums, calling {@code super} for the boolean/null cases.
     *
     * @param schema the OpenAPI schema
     * @return the default value expression, or {@code null} if
     *         there is no default
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        if (schema == null) {
            return null;
        }
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (ModelUtils.isBooleanSchema(resolved) && resolved.getDefault() != null) {
            return Boolean.parseBoolean(resolved.getDefault().toString())
                    ? getTrueLiteral() : getFalseLiteral();
        }
        return null;
    }

    /**
     * Produces the full type declaration for a schema,
     * including generic type parameters for arrays and maps.
     * Delegates to {@link #formatArrayType} and
     * {@link #formatMapType} so subclasses can customize the
     * generic syntax per language.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public String getTypeDeclaration(Schema schema) {
        if (ModelUtils.isArraySchema(schema)) {
            final Schema inner = ModelUtils.getSchemaItems(schema);
            return formatArrayType(getSchemaType(schema), getTypeDeclaration(inner));
        } else if (ModelUtils.isMapSchema(schema)) {
            final String valueType =
                    Optional.ofNullable(ModelUtils.getAdditionalProperties(schema))
                            .map(this::getTypeDeclaration)
                            .orElseGet(this::getMapDefaultValueType);
            return formatMapType(getSchemaType(schema), getMapKeyType(), valueType);
        }
        return super.getTypeDeclaration(schema);
    }

    /**
     * Formats an array type declaration with the given container
     * and inner type using the template returned by
     * {@link #getArrayTypeTemplate()}. The template uses
     * {@code %1$s} for the container and {@code %2$s} for the
     * inner type. This method is {@code final}; override
     * {@link #getArrayTypeTemplate()} instead.
     */
    @SuppressFBWarnings(
            value = "FORMAT_STRING_MANIPULATION",
            justification = "Format string is a hardcoded subclass declaration, not user input")
    protected final String formatArrayType(String containerType, String innerType) {
        return String.format(getArrayTypeTemplate(), containerType, innerType);
    }

    /**
     * Formats a map type declaration with the given container,
     * key, and value types using the template returned by
     * {@link #getMapTypeTemplate()}. The template uses
     * {@code %1$s} for the container, {@code %2$s} for the key
     * type, and {@code %3$s} for the value type. This method is
     * {@code final}; override {@link #getMapTypeTemplate()} instead.
     */
    @SuppressFBWarnings(
            value = "FORMAT_STRING_MANIPULATION",
            justification = "Format string is a hardcoded subclass declaration, not user input")
    protected final String formatMapType(String containerType, String keyType, String valueType) {
        return String.format(getMapTypeTemplate(), containerType, keyType, valueType);
    }

    /**
     * Returns the default key type for map schemas. Most
     * languages use {@code String} but subclasses can override
     * for language-specific types like {@code string}.
     */
    protected String getMapKeyType() {
        return "String";
    }

    /**
     * Returns the default value type for map schemas when no
     * {@code additionalProperties} schema is specified. Most
     * languages use {@code Object} but subclasses can override.
     */
    protected String getMapDefaultValueType() {
        return "Object";
    }

    /**
     * Records whether the source schema for this model declares
     * {@code unevaluatedProperties: false} (OAS 3.1 / JSON Schema
     * 2020-12 strict-mode). The flag is consumed by
     * {@link #postProcessModels(ModelsMap)} which copies it onto the
     * per-model {@link ModelMap} so templates can emit deserializers
     * that reject unknown JSON keys instead of silently capturing them.
     *
     * <p>Subclasses that override this method <strong>must</strong>
     * call {@code super.fromModel(name, schema)} to preserve the
     * detection. Gap AX.1.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public CodegenModel fromModel(String name, Schema schema) {
        if (schema != null && schema.getUnevaluatedProperties() != null) {
            final Schema unevaluated = schema.getUnevaluatedProperties();
            if (Boolean.FALSE.equals(unevaluated.getBooleanSchemaValue())) {
                unevaluatedPropertiesFalseSchemas.add(name);
            }
        }
        return super.fromModel(name, schema);
    }

    /**
     * Post-processes generated models to strip primitive parents,
     * apply enum naming conventions, sanitize byte-array example
     * values, and run all declarative post-processing hooks
     * (sorting, enum-on-primitive clearing, oneOf/anyOf filtering,
     * import-context building, type-decorator detection, and
     * type-substring context flag setting).
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = postProcessModelsEnum(super.postProcessModels(objs));
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            // Gap AX.1: propagate unevaluatedProperties:false flag onto the
            // modelMap so {{#isUnevaluatedPropertiesFalse}} in templates resolves
            // via Mustache context-chain lookup. Detection happens in fromModel
            // because CodegenModel does not preserve the source schema's
            // unevaluatedProperties field.
            if (unevaluatedPropertiesFalseSchemas.contains(model.name)
                    || unevaluatedPropertiesFalseSchemas.contains(model.schemaName)) {
                modelMap.put("isUnevaluatedPropertiesFalse", true);
            }
            stripPrimitiveParent(model);
            // Filter the OpenAPI Generator sentinel ghost-enum value
            // ("UnknownDefaultOpenApi" = "11184809") that upstream injects
            // into every string enum. It is not part of the spec and
            // pollutes generated client enums in every language.
            stripGhostEnumValues(model);
            for (final CodegenProperty prop : model.vars) {
                sanitizeByteArrayExample(prop);
                fixEnumDefaultValue(prop, model);
            }
            for (final CodegenProperty prop : model.allVars) {
                sanitizeByteArrayExample(prop);
                fixEnumDefaultValue(prop, model);
            }
            for (final CodegenProperty prop : model.optionalVars) {
                fixEnumDefaultValue(prop, model);
                applyUniqueItemsSetType(prop);
            }
            for (final CodegenProperty prop : model.requiredVars) {
                fixEnumDefaultValue(prop, model);
                applyUniqueItemsSetType(prop);
            }

            // Gap 10: Sort vars by default value (Elixir defstruct ordering)
            if (sortVarsByDefaultValue()) {
                model.vars.sort(Comparator.comparing(p -> p.defaultValue != null ? 1 : 0));
            }

            // Gap 11: Clear enum flags on primitive-typed properties (Dart)
            if (clearsEnumOnPrimitives()) {
                for (final CodegenProperty prop : model.vars) {
                    clearEnumOnPrimitiveProp(prop);
                }
                for (final CodegenProperty prop : model.allVars) {
                    clearEnumOnPrimitiveProp(prop);
                }
                for (final CodegenProperty prop : model.optionalVars) {
                    clearEnumOnPrimitiveProp(prop);
                }
                for (final CodegenProperty prop : model.requiredVars) {
                    clearEnumOnPrimitiveProp(prop);
                }
            }

            // Gap 11: Filter primitive type names from oneOf/anyOf (Dart)
            if (filtersOneOfAnyOfPrimitives()) {
                model.oneOf = filterNonPrimitiveTypeNames(model.oneOf);
                model.anyOf = filterNonPrimitiveTypeNames(model.anyOf);
            }

            // Gap 12: Build model import context list (Node → tsImports, Dart → dartImports)
            final String importContextKey = getModelImportContextKey();
            if (importContextKey != null) {
                final List<Map<String, String>> importList = new ArrayList<>();
                for (final String importName : model.imports) {
                    if (!languageSpecificPrimitives.contains(importName)
                            && !typeMapping.containsValue(importName)) {
                        final Map<String, String> entry = new HashMap<>();
                        entry.put("classname", importName);
                        entry.put("filename", toModelFilename(importName));
                        importList.add(entry);
                    }
                }
                modelMap.put(importContextKey, importList);
                final String hasKey = "has"
                        + Character.toUpperCase(importContextKey.charAt(0))
                        + importContextKey.substring(1);
                modelMap.put(hasKey, !importList.isEmpty());
            }

            // Gap 13: Type decorator flag (Node/TypeScript @Type() decorators)
            modelMap.put("hasTypeDecorator",
                    model.vars.stream().anyMatch(this::needsTypeDecorator));

            // Gap 14: Unified model context flags (Go → hasTimeImport, hasFmtImport)
            for (final Map.Entry<String, String> entry : getModelContextFlags().entrySet()) {
                final String key = entry.getKey();
                final String flagName = entry.getValue();
                if (key.startsWith("type:")) {
                    final String typeSubstring = key.substring(5);
                    boolean found = false;
                    for (final CodegenProperty prop : model.vars) {
                        if (prop.dataType != null
                                && prop.dataType.contains(typeSubstring)) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        modelMap.put(flagName, true);
                        result.put(flagName, true);
                    }
                } else if ("oneOfAnyOf".equals(key)) {
                    if (!model.oneOf.isEmpty() || !model.anyOf.isEmpty()) {
                        result.put(flagName, true);
                    }
                } else if ("isEnum".equals(key)) {
                    if (model.isEnum) {
                        modelMap.put(flagName, true);
                        result.put(flagName, true);
                    }
                } else if ("hasInlineEnum".equals(key)) {
                    final boolean hasInlineEnum =
                            model.vars.stream().anyMatch(p -> p.isEnum);
                    if (hasInlineEnum) {
                        modelMap.put(flagName, true);
                        result.put(flagName, true);
                    }
                } else if ("hasRequired".equals(key)) {
                    if (model.hasRequired) {
                        modelMap.put(flagName, true);
                        result.put(flagName, true);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Removes the OpenAPI Generator sentinel ghost-enum value
     * ("UnknownDefaultOpenApi" = "11184809") from all enum-typed
     * properties on a model and from the model itself when it is an
     * enum. Upstream injects this sentinel into every string enum to
     * represent "unknown future values," but it leaks into client SDKs
     * as a meaningless extra variant.
     */
    private static void stripGhostEnumValues(CodegenModel model) {
        if (model.isEnum) {
            stripGhostEnumFromAllowableValues(model.allowableValues);
        }
        for (final CodegenProperty prop : model.vars) {
            if (prop.isEnum) {
                stripGhostEnumFromAllowableValues(prop.allowableValues);
            }
        }
        for (final CodegenProperty prop : model.allVars) {
            if (prop.isEnum) {
                stripGhostEnumFromAllowableValues(prop.allowableValues);
            }
        }
        for (final CodegenProperty prop : model.optionalVars) {
            if (prop.isEnum) {
                stripGhostEnumFromAllowableValues(prop.allowableValues);
            }
        }
        for (final CodegenProperty prop : model.requiredVars) {
            if (prop.isEnum) {
                stripGhostEnumFromAllowableValues(prop.allowableValues);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void stripGhostEnumFromAllowableValues(
            @Nullable Map<String, Object> allowableValues) {
        if (allowableValues == null) {
            return;
        }
        final Object values = allowableValues.get("values");
        if (values instanceof List) {
            ((List<Object>) values).removeIf(v -> "11184809".equals(String.valueOf(v)));
        }
        final Object enumVars = allowableValues.get("enumVars");
        if (enumVars instanceof List) {
            final List<Map<String, Object>> entries = (List<Map<String, Object>>) enumVars;
            entries.removeIf(entry -> {
                final Object value = entry.get("value");
                final Object name = entry.get("name");
                return ("'11184809'".equals(String.valueOf(value))
                        || "\"11184809\"".equals(String.valueOf(value))
                        || "11184809".equals(String.valueOf(value))
                        || "UnknownDefaultOpenApi".equals(String.valueOf(name)));
            });
            // Precomputed lowercase form so templates can use
            // {{nameLowercase}} instead of inline
            // {{#lambda.lowercase}}{{name}}{{/lambda.lowercase}}.
            for (final Map<String, Object> entry : entries) {
                final Object name = entry.get("name");
                if (name != null) {
                    entry.put(
                            "nameLowercase",
                            String.valueOf(name).toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
    }

    /**
     * Fixes a property's default value when the base class has set it to a
     * Java-style enum reference (e.g. {@code "StatusEnum.Placed"}). The default
     * implementation strips the prefix, lowercases the remaining value, and
     * wraps it with the language's quote character via
     * {@link #formatEnumStringLiteral(String)}. Subclasses that need a more
     * exotic literal (e.g. Rust's {@code String::from("...")}) override
     * {@link #formatEnumStringLiteral(String)}; subclasses with entirely
     * different semantics (e.g. Python's class-prefixed form) override this
     * method directly. The {@code model} parameter gives access to
     * {@code model.classname}.
     *
     * @param prop  the property whose {@code defaultValue} may need rewriting
     * @param model the enclosing model (provides {@code classname})
     */
    protected void fixEnumDefaultValue(CodegenProperty prop, CodegenModel model) {
        if (prop.defaultValue != null && prop.isEnum && prop.defaultValue.contains(".")) {
            final String enumValue = prop.defaultValue.substring(
                    prop.defaultValue.lastIndexOf('.') + 1);
            prop.defaultValue = formatEnumStringLiteral(enumValue.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Wraps the (already-lowercased) enum value as a language-appropriate
     * string literal used by {@link #fixEnumDefaultValue(CodegenProperty,
     * CodegenModel)}. The default form is {@code <quote>value<quote>} using
     * {@link #getQuoteChar()}; languages with bespoke literal syntax (e.g.
     * Rust's {@code String::from("...")}) override this hook.
     *
     * @param value the lowercased enum value
     * @return the language-appropriate string literal
     */
    protected String formatEnumStringLiteral(String value) {
        final char q = getQuoteChar();
        return q + value + q;
    }

    /*
     * Post-processes a model property to handle unique-item
     * arrays, add declarative import lists (Gap 15), and
     * optionally sanitize example values (Gap 16). Subclasses
     * that need additional processing should call super.
     */
    /**
     * Applies the unique-items set type replacement to a single property.
     * Safe to call multiple times — idempotent because once the container
     * prefix no longer matches the pattern, subsequent calls are no-ops.
     * Called from both {@link #postProcessModelProperty} (for {@code vars}
     * and {@code allVars}) and {@link #postProcessModels} (for
     * {@code requiredVars} and {@code optionalVars}, which are separate
     * object instances in the OpenAPI Generator model).
     */
    private void applyUniqueItemsSetType(CodegenProperty property) {
        final String setType = getUniqueItemsSetType();
        if (setType != null && property.isArray && property.getUniqueItems()) {
            final String pattern = getArrayContainerPattern();
            property.datatypeWithEnum =
                    property.datatypeWithEnum.replaceFirst(pattern, setType);
            property.dataType = property.dataType.replaceFirst(pattern, setType);
        }
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);

        // Unique-item array → set type replacement
        applyUniqueItemsSetType(property);

        // Gap 15: Declarative model property import lists (Java Jackson annotations)
        if (!model.isEnum) {
            model.imports.addAll(getUniversalModelPropertyImports());
            if (property.isEnum) {
                model.imports.addAll(getEnumPropertyImports());
            }
            if (property.isContainer) {
                if (property.isArray) {
                    if (property.getUniqueItems()) {
                        model.imports.addAll(getUniqueArrayPropertyImports());
                    } else {
                        model.imports.addAll(getArrayPropertyImports());
                    }
                }
                if (property.isMap) {
                    model.imports.addAll(getMapPropertyImports());
                }
            }
        }

        // Gap 16: Example value sanitization (Python)
        if (sanitizesExampleValues()) {
            sanitizePropertyExampleValue(property);
        }
    }

    /**
     * Strips garbage {@code [B@hex} toString output from byte
     * array examples. OpenAPI Generator converts
     * {@code format: byte} example strings into Java byte
     * arrays whose toString produces meaningless memory
     * addresses that would pollute generated documentation.
     */
    private static void sanitizeByteArrayExample(CodegenProperty prop) {
        Optional.ofNullable(prop.example)
                .filter(ex -> ex.matches("\\[B@[0-9a-fA-F]+"))
                .ifPresent(ignored -> prop.example = null);
    }

    /**
     * Converts a tag name to an API class name by camelizing
     * the tag and appending "Api". Returns "DefaultApi" when
     * the tag name is empty, ensuring every operation group
     * has a valid class name.
     */
    @Override
    public String toApiName(String name) {
        if (name.isEmpty()) {
            return "DefaultApi";
        }
        return NamingConvention.PASCAL_CASE.apply(name) + "Api";
    }

    /**
     * Converts a schema name to a PascalCase model class name.
     * First applies rules from {@link #getModelNameSanitizationRules} to allow
     * language-specific pre-cleaning (e.g. PHP strips illegal
     * characters), then sanitizes, checks for reserved-word
     * collisions and digit-leading names, applies PascalCase,
     * and finally checks {@link #getModelNameCollisionPrefix} (e.g.
     * Node/TypeScript adds "Model" prefix for primitives).
     */
    @Override
    public String toModelName(String name) {
        for (final String[] rule : getModelNameSanitizationRules()) {
            name = name.replaceAll(rule[0], rule[1]);
        }
        name = sanitizeName(name);
        if (isReservedWord(name)) {
            name = "model_" + name;
        }
        if (name.matches("^\\d.*")) {
            name = "model_" + name;
        }
        final String cased = NamingConvention.PASCAL_CASE.apply(name);
        final String collisionPrefix = getModelNameCollisionPrefix();
        if (!collisionPrefix.isEmpty() && languageSpecificPrimitives.contains(cased)) {
            return collisionPrefix + cased;
        }
        return cased;
    }

    /**
     * Converts a property name to a language-appropriate
     * variable name by sanitizing it, applying subclass
     * casing rules, and escaping if it collides with a
     * reserved word or starts with a digit. Whether a cased
     * name triggers reserved-word escaping is controlled by
     * {@link #shouldEscapeReservedVarName} (default:
     * {@link #isReservedWord}; Go and Node return {@code false}).
     */
    @Override
    public String toVarName(String name) {
        name = sanitizeName(name);
        name = applyVarNameCasing(name);
        if (shouldEscapeReservedVarName(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        return name;
    }

    /**
     * Returns the strategy for handling all-uppercase
     * identifiers when applying variable name casing.
     * Defaults to {@link UppercaseIdentifierStrategy#APPLY_CASING}.
     * Override in Java ({@code PRESERVE}) or Ruby
     * ({@code LOWERCASE_FIRST}).
     */
    protected UppercaseIdentifierStrategy getUppercaseIdentifierStrategy() {
        return UppercaseIdentifierStrategy.APPLY_CASING;
    }

    /**
     * Applies the language-specific casing convention to a
     * sanitized variable name using {@link #getVarCasing()}
     * and {@link #getUppercaseIdentifierStrategy()}.
     */
    protected final String applyVarNameCasing(String sanitizedName) {
        return getUppercaseIdentifierStrategy().apply(sanitizedName, getVarCasing());
    }

    /**
     * Converts a parameter name to its language-appropriate
     * form. If {@link #getParamCasing()} returns a convention,
     * applies it with reserved-word escaping; otherwise
     * delegates to {@link #toVarName}.
     */
    @Override
    public String toParamName(String name) {
        final NamingConvention paramCasing = getParamCasing();
        if (paramCasing != null) {
            final String sanitized = sanitizeName(name);
            final String cased = paramCasing.apply(sanitized);
            if (isReservedWord(cased) || cased.matches("^\\d.*")) {
                return escapeReservedWord(cased);
            }
            return cased;
        }
        return toVarName(name);
    }

    /**
     * Converts a model name to its filename. If
     * {@link #getFilenameCasing()} returns a convention, applies
     * it to the model name; otherwise returns the model class
     * name unchanged. Subclasses with complex filename logic
     * (e.g. Ruby's Zeitwerk) can override this method.
     */
    @Override
    public String toModelFilename(String name) {
        final NamingConvention fc = getFilenameCasing();
        return fc != null ? fc.apply(toModelName(name)) : toModelName(name);
    }

    /**
     * Converts an API tag name to its filename. If
     * {@link #getFilenameCasing()} returns a convention, applies
     * it to the API class name; otherwise returns the API class
     * name unchanged. Subclasses with complex filename logic
     * can override this method.
     */
    @Override
    public String toApiFilename(String name) {
        final NamingConvention fc = getFilenameCasing();
        return fc != null ? fc.apply(toApiName(name)) : toApiName(name);
    }

    /**
     * Converts a raw operation ID to a language-appropriate
     * method name. Rejects null or empty operation IDs early
     * because missing IDs cause cryptic downstream failures
     * in template rendering.
     */
    @Override
    public final String toOperationId(String operationId) {
        if (operationId == null || operationId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty method/operation name (operationId) not allowed");
        }
        return formatOperationId(sanitizeName(operationId));
    }

    /**
     * Returns the prefix to prepend to operation IDs that
     * collide with reserved words or start with a digit.
     * Returns {@code null} by default, meaning no prefix is
     * applied. Ruby and PHP override this to return
     * {@code "call_"}.
     */
    @Nullable
    protected String getOperationIdReservedPrefix() {
        return null;
    }

    /**
     * Formats a sanitized operation ID into the language's
     * method naming convention using {@link #getOperationIdCasing()}.
     * When {@link #getOperationIdReservedPrefix()} returns a
     * non-null value, reserved words and digit-leading IDs are
     * prefixed to produce valid method names.
     */
    protected final String formatOperationId(String sanitizedOperationId) {
        final String prefix = getOperationIdReservedPrefix();
        if (prefix != null) {
            if (isReservedWord(sanitizedOperationId)
                    || sanitizedOperationId.matches("^\\d.*")) {
                return getOperationIdCasing().apply(prefix + sanitizedOperationId);
            }
        }
        return getOperationIdCasing().apply(sanitizedOperationId);
    }

    /**
     * Converts a raw enum value to its language representation.
     * Numeric enums are returned as-is to preserve their type;
     * string enums are first passed through
     * {@link #escapeEnumStringValue} (for control-character
     * escaping, e.g. in C#) and then wrapped via
     * {@link #quoteEnumValue}.
     */
    @Override
    public String toEnumValue(String value, String datatype) {
        if (isNumericEnumDatatype(datatype)) {
            return value;
        }
        return quoteEnumValue(escapeEnumStringValue(value));
    }

    /**
     * Converts a raw enum value to a language-appropriate
     * constant name using {@link #getEnumCasing()}. Returns
     * {@link #getEmptyEnumVarName()} for blank values, prefixes
     * numeric values with "NUMBER_", checks
     * {@link #getSymbolName} for symbol-to-word mappings (e.g.
     * {@code "+"} → {@code "PLUS"}), and applies reserved-word
     * escaping after casing. Subclasses that need further
     * customization (e.g. Node's {@code enumNameMapping}) should
     * override and call super for the fallback.
     */
    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value.isEmpty()) {
            return getEmptyEnumVarName();
        }
        if (isNumericEnumDatatype(datatype)) {
            return "NUMBER_"
                    + value.replaceAll("-", "MINUS_")
                            .replaceAll("\\+", "PLUS_")
                            .replaceAll("\\.", "_DOT_");
        }
        // Symbol lookup: maps "+" → "plus", etc.
        final String symbol = getSymbolName(value);
        if (symbol != null) {
            return getEnumCasing().apply(symbol);
        }
        final String sanitized = sanitizeName(value);
        final String cased = getEnumCasing().apply(sanitized);
        final String cleaned =
                cased.replaceFirst("^_", "").replaceFirst("_$", "");
        if (cleaned.matches("\\d.*")) {
            return "_" + cleaned;
        }
        // Reserved-word escape after casing
        return isReservedWord(cleaned) ? escapeReservedWord(cleaned) : cleaned;
    }

    /**
     * Returns the set of language-specific numeric type names.
     * Used by {@link #isNumericEnumDatatype} to decide whether
     * enum values should be emitted as bare literals or quoted
     * strings.
     */
    protected abstract Set<String> getNumericDataTypes();

    // =========================================================================
    // Gap 2 — Array / map type template declarations
    // =========================================================================

    /**
     * Returns a {@link String#format} template for array types.
     * {@code %1$s} is the container type, {@code %2$s} is the
     * inner element type. Examples:
     * <ul>
     *   <li>Java / C# / Kotlin: {@code "%1$s<%2$s>"}</li>
     *   <li>Go: {@code "[]%2$s"}</li>
     *   <li>Swift / Elixir: {@code "[%2$s]"}</li>
     *   <li>Rust: {@code "Vec<%2$s>"}</li>
     *   <li>Dart: {@code "List<%2$s>"}</li>
     * </ul>
     */
    protected abstract String getArrayTypeTemplate();

    /**
     * Returns a {@link String#format} template for map types.
     * {@code %1$s} is the container, {@code %2$s} the key type,
     * {@code %3$s} the value type. Examples:
     * <ul>
     *   <li>Java / C# / Kotlin: {@code "%1$s<%2$s, %3$s>"}</li>
     *   <li>Go: {@code "map[%2$s]%3$s"}</li>
     *   <li>Swift: {@code "[%2$s: %3$s]"}</li>
     *   <li>Elixir: {@code "%%{%2$s => %3$s}"}</li>
     *   <li>Rust: {@code "std::collections::HashMap<%2$s, %3$s>"}</li>
     * </ul>
     */
    protected abstract String getMapTypeTemplate();

    // =========================================================================
    // Gap 7 — Null / boolean literal declarations
    // =========================================================================

    /**
     * Returns the language-specific null literal, used in
     * {@link #toDefaultValue} for schemas with no default.
     * Defaults to {@code "null"}; languages with a different
     * null literal (Go/Elixir/Swift/Ruby → {@code "nil"},
     * Python/Rust → {@code "None"}) override.
     */
    protected String getNullLiteral() {
        return "null";
    }

    /**
     * Returns the language-specific true literal.
     * Defaults to {@code "true"}; Python overrides to {@code "True"}.
     */
    protected String getTrueLiteral() {
        return "true";
    }

    /**
     * Returns the language-specific false literal.
     * Defaults to {@code "false"}; Python overrides to {@code "False"}.
     */
    protected String getFalseLiteral() {
        return "false";
    }

    // =========================================================================
    // Gap 8 — Source folder declaration
    // =========================================================================

    /**
     * Returns the source folder path component between the output
     * root and the package path. Used by the default
     * {@link #modelFileFolder()} and {@link #apiFileFolder()}
     * implementations. Examples: {@code "src/main/java"} (Java),
     * {@code "src/main/kotlin"} (Kotlin), {@code "src"} (C# configurable).
     * Languages with non-standard folder structures (Go, Dart, Elixir,
     * Rust, Swift, PHP) override {@code modelFileFolder()} and
     * {@code apiFileFolder()} directly and still declare this for
     * completeness.
     */
    protected abstract String getSourceFolder();

    // =========================================================================
    // Gap 3 — toModelName pipeline declarations
    // =========================================================================

    /**
     * Returns a list of regex-replacement pairs applied to the raw schema name
     * at the start of {@link #toModelName}, before {@link #sanitizeName} runs.
     * Each entry is a two-element array: {@code [pattern, replacement]} used
     * with {@link String#replaceAll}. Default is an empty list (no-op).
     *
     * <p>PHP declares three rules to strip characters that are illegal in PHP
     * identifiers ({@code ]}, {@code $}) and replace other non-word,
     * non-backslash characters with underscores.
     *
     * @return ordered list of {@code [pattern, replacement]} pairs
     */
    protected List<String[]> getModelNameSanitizationRules() {
        return List.of();
    }

    /**
     * Returns the prefix to prepend when a PascalCase model name collides with
     * a value in {@link #languageSpecificPrimitives}. Default is {@code ""} (no
     * prefix). Node/TypeScript declares {@code "Model"} so that a schema named
     * {@code string} becomes {@code ModelString}.
     *
     * @return collision prefix, or empty string for no collision handling
     */
    protected String getModelNameCollisionPrefix() {
        return "";
    }

    // =========================================================================
    // Gap 4 — toVarName reserved-word hook
    // =========================================================================

    /**
     * Returns {@code true} when the given (cased, sanitized)
     * variable name should be escaped with {@link #escapeReservedWord}.
     * The default delegates to {@link #isReservedWord}. Override
     * in Go and Node to return {@code false} so PascalCase fields
     * and identity-cased names are never escaped.
     *
     * @param name the cased, sanitized variable name
     * @return whether to apply reserved-word escaping
     */
    protected boolean shouldEscapeReservedVarName(String name) {
        return isReservedWord(name);
    }

    // =========================================================================
    // Gap 6 — toEnumValue escaping declarations
    // =========================================================================

    /**
     * Returns an ordered list of regex-replacement pairs used to escape
     * special characters in string enum values before quoting. Each entry is a
     * two-element array: {@code [pattern, replacement]} used with
     * {@link String#replaceAll}. Default is an empty list (no-op).
     *
     * <p>C# declares four rules: escape {@code \n}, {@code \t}, {@code \r},
     * and unescaped {@code "} so they survive inside a C# string literal.
     *
     * @return ordered list of {@code [pattern, replacement]} pairs
     */
    protected List<String[]> getEnumStringEscapes() {
        return List.of();
    }

    /**
     * Escapes control characters and special sequences in a string enum value
     * before quoting. Applies each pair from {@link #getEnumStringEscapes()} in
     * order via {@link String#replaceAll}. Default is a no-op when no escapes
     * are declared.
     *
     * @param value the raw enum value string
     * @return the escaped value
     */
    protected String escapeEnumStringValue(String value) {
        for (final String[] escape : getEnumStringEscapes()) {
            value = value.replaceAll(escape[0], escape[1]);
        }
        return value;
    }

    // =========================================================================
    // Gap 10 — postProcessModels sort flag
    // =========================================================================

    /**
     * Returns {@code true} if model vars should be sorted so that
     * properties without a default come first. Required by
     * Elixir's {@code defstruct} positional-argument ordering.
     * Default is {@code false}.
     */
    protected boolean sortVarsByDefaultValue() {
        return false;
    }

    // =========================================================================
    // Gap 11 — postProcessModels primitive-enum / oneOf-anyOf flags
    // =========================================================================

    /**
     * Returns {@code true} if enum flags should be cleared for
     * primitive-typed properties after model processing. Dart cannot
     * represent inline enums on primitives. Default is {@code false}.
     */
    protected boolean clearsEnumOnPrimitives() {
        return false;
    }

    /**
     * Clears the {@code isEnum} flag on a property whose data type
     * is a language primitive or mapped type. Called when
     * {@link #clearsEnumOnPrimitives()} returns {@code true}.
     */
    private void clearEnumOnPrimitiveProp(CodegenProperty prop) {
        if (prop.isEnum
                && (languageSpecificPrimitives.contains(prop.dataType)
                        || typeMapping.containsValue(prop.dataType))) {
            prop.isEnum = false;
        }
    }

    /**
     * Returns {@code true} if primitive type names should be
     * removed from {@code model.oneOf} and {@code model.anyOf}
     * after model processing. Required by Dart because primitive
     * types cannot appear as oneOf/anyOf variants in generated
     * code. Default is {@code false}.
     */
    protected boolean filtersOneOfAnyOfPrimitives() {
        return false;
    }

    /**
     * Filters a set of type names, keeping only those that are
     * not language primitives, not mapped types, and not
     * collection wrappers ({@code List<}, {@code Map<}, {@code Set<}).
     */
    private Set<String> filterNonPrimitiveTypeNames(Set<String> typeNames) {
        final Set<String> result = new LinkedHashSet<>();
        for (final String typeName : typeNames) {
            if (!languageSpecificPrimitives.contains(typeName)
                    && !typeMapping.containsValue(typeName)
                    && !typeName.startsWith("List<")
                    && !typeName.startsWith("Map<")
                    && !typeName.startsWith("Set<")) {
                result.add(typeName);
            }
        }
        return result;
    }

    // =========================================================================
    // Gap 12 — Model import context key
    // =========================================================================

    /**
     * Returns the template context key under which to store the
     * filtered, enriched model import list. Return {@code null}
     * (the default) to skip building the list. Node returns
     * {@code "tsImports"}; Dart returns {@code "dartImports"}.
     * The base class also populates {@code "has<Key>"} (e.g.
     * {@code "hasTsImports"}).
     */
    @Nullable
    protected String getModelImportContextKey() {
        return null;
    }

    // =========================================================================
    // Gap 13 — Type decorator flag
    // =========================================================================

    /**
     * Returns {@code true} if this generator should evaluate type-decorator
     * requirements on model properties. Default is {@code false}. Node/TypeScript
     * returns {@code true} because it emits {@code @Type()} decorators on complex
     * properties to guide the runtime deserializer.
     *
     * @return whether type-decorator analysis is active for this language
     */
    protected boolean shouldApplyTypeDecorators() {
        return false;
    }

    /**
     * Returns {@code true} if the given property requires a runtime type
     * decorator for correct deserialization. Only called when
     * {@link #shouldApplyTypeDecorators()} returns {@code true}. The base
     * implementation covers the TypeScript/Node case: non-primitive, non-array
     * complex object types, or arrays whose item type is such a complex type.
     *
     * @param prop the property to test
     * @return whether the property needs a type decorator
     */
    protected boolean needsTypeDecorator(CodegenProperty prop) {
        if (!shouldApplyTypeDecorators()) {
            return false;
        }
        if (prop.isDate || prop.isDateTime) {
            return true;
        }
        if (!prop.isPrimitiveType
                && !prop.isArray
                && prop.complexType != null
                && !prop.isEnum
                && !prop.isFreeFormObject) {
            return true;
        }
        if (prop.isArray
                && prop.items != null
                && (prop.items.isDate || prop.items.isDateTime)) {
            return true;
        }
        return prop.isArray
                && prop.items != null
                && !prop.items.isPrimitiveType
                && prop.items.complexType != null
                && !prop.items.isEnum
                && !prop.items.isFreeFormObject;
    }

    // =========================================================================
    // Gap 14 — Unified model context flags
    // =========================================================================

    /**
     * Returns a map whose keys describe a condition and whose values are the
     * template context flag to set when that condition is true. Two key forms
     * are supported:
     *
     * <ul>
     *   <li>{@code "type:<substr>"} — set the flag if any model property's
     *       {@code dataType} contains {@code <substr>}. Example: Go uses
     *       {@code "type:time.Time"} → {@code "hasTimeImport"}.</li>
     *   <li>{@code "oneOfAnyOf"} — set the flag if the model's {@code oneOf}
     *       or {@code anyOf} list is non-empty. Example: Go uses
     *       {@code "oneOfAnyOf"} → {@code "hasFmtImport"}.</li>
     * </ul>
     *
     * Default is an empty map (no flags set).
     */
    protected Map<String, String> getModelContextFlags() {
        return Map.of();
    }

    // =========================================================================
    // Gap 15 — Model property import declarations
    // =========================================================================

    /**
     * Returns a list of imports added to every non-enum model for
     * every property. Used by Java for universal Jackson
     * annotations ({@code JsonProperty}, {@code JsonInclude},
     * {@code JsonTypeName}). Default is empty.
     */
    protected List<String> getUniversalModelPropertyImports() {
        return List.of();
    }

    /**
     * Returns a list of imports added when a property is an enum.
     * Used by Java for {@code JsonValue} and {@code JsonCreator}.
     * Default is empty.
     */
    protected List<String> getEnumPropertyImports() {
        return List.of();
    }

    /**
     * Returns a list of imports added when a property is a
     * non-unique array. Used by Java for {@code ArrayList} and
     * {@code Arrays}. Default is empty.
     */
    protected List<String> getArrayPropertyImports() {
        return List.of();
    }

    /**
     * Returns a list of imports added when a property is a
     * unique-item array (set). Used by Java for
     * {@code LinkedHashSet}. Default is empty.
     */
    protected List<String> getUniqueArrayPropertyImports() {
        return List.of();
    }

    /**
     * Returns a list of imports added when a property is a map.
     * Used by Java for {@code HashMap}. Default is empty.
     */
    protected List<String> getMapPropertyImports() {
        return List.of();
    }

    // =========================================================================
    // Gap 16 — Example value sanitization flag
    // =========================================================================

    /**
     * Returns {@code true} if property example values should be
     * sanitized to valid language syntax. Python needs this to
     * strip Java null literals and byte-array strings, and to
     * quote bare strings. Default is {@code false}.
     */
    protected boolean sanitizesExampleValues() {
        return false;
    }

    /**
     * Sanitizes a property's example value for languages that
     * cannot render Java-style example strings. Strips Java null
     * literals and byte-array toString artefacts; wraps bare
     * strings in single quotes with proper escaping.
     */
    private static void sanitizePropertyExampleValue(CodegenProperty property) {
        if (property.example == null) {
            return;
        }
        if ("null".equals(property.example) || property.example.startsWith("[B@")) {
            property.example = null;
        } else if (property.isString
                && !property.example.startsWith("'")
                && !property.example.startsWith("\"")) {
            property.example = "'" + property.example.replace("'", "\\'") + "'";
        }
    }

    // =========================================================================
    // Gap BD — Plural examples surfaced into property/parameter docstrings
    // =========================================================================

    /**
     * Builds a Mustache-friendly list of {@code {summary, description, value}}
     * maps from an OAS named-examples source. Accepts either a
     * {@code Map<String, io.swagger.v3.oas.models.examples.Example>} (the
     * native form used by parameters and media types) or a raw
     * {@code Map<String, Object>} parsed from an {@code x-examples} extension.
     */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> buildPluralExamplesList(Object raw) {
        if (!(raw instanceof Map)) {
            return new ArrayList<>();
        }
        final Map<String, Object> rawMap = (Map<String, Object>) raw;
        if (rawMap.isEmpty()) {
            return new ArrayList<>();
        }
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final Map.Entry<String, Object> entry : rawMap.entrySet()) {
            final Object exObj = entry.getValue();
            String summary = entry.getKey();
            String description = null;
            Object value = null;
            if (exObj instanceof io.swagger.v3.oas.models.examples.Example) {
                final io.swagger.v3.oas.models.examples.Example ex =
                        (io.swagger.v3.oas.models.examples.Example) exObj;
                if (ex.getSummary() != null) {
                    summary = ex.getSummary();
                }
                description = ex.getDescription();
                value = ex.getValue();
            } else if (exObj instanceof Map) {
                final Map<String, Object> ex = (Map<String, Object>) exObj;
                if (ex.get("summary") != null) {
                    summary = String.valueOf(ex.get("summary"));
                }
                if (ex.get("description") != null) {
                    description = String.valueOf(ex.get("description"));
                }
                value = ex.get("value");
            } else {
                value = exObj;
            }
            final Map<String, Object> mustacheEntry = new HashMap<>();
            mustacheEntry.put("summary", summary == null ? "" : summary);
            mustacheEntry.put("description", description == null ? "" : description);
            mustacheEntry.put("value", renderExampleValue(value));
            result.add(mustacheEntry);
        }
        return result;
    }

    /**
     * Renders an OAS example value as a single-line string. Primitives are
     * stringified directly; complex values are serialized as compact JSON so
     * they remain readable inside a docstring.
     */
    private static String renderExampleValue(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean) {
            return value.toString();
        }
        try {
            return io.swagger.v3.core.util.Json.mapper().writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    /**
     * Captures any OAS plural {@code examples} declared on a property's
     * schema (OAS 3.1 schema-level {@code examples} list, or the
     * {@code x-examples} extension that callers may use on OAS 3.0 schemas)
     * and stores a Mustache-friendly list under
     * {@code property.vendorExtensions["pluralExamples"]}. Templates read
     * the list via {@code {{#vendorExtensions.pluralExamples}}}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected static void capturePropertyPluralExamples(
            CodegenProperty property, Schema schema) {
        if (property == null || schema == null) {
            return;
        }
        List<Map<String, Object>> examples = Collections.emptyList();
        if (schema.getExtensions() != null
                && schema.getExtensions().containsKey("x-examples")) {
            examples = buildPluralExamplesList(schema.getExtensions().get("x-examples"));
        }
        if (examples.isEmpty()
                && schema.getExamples() != null
                && !schema.getExamples().isEmpty()) {
            final List<Map<String, Object>> list = new ArrayList<>();
            int idx = 1;
            for (final Object ex : (List<Object>) schema.getExamples()) {
                final Map<String, Object> entry = new HashMap<>();
                entry.put("summary", "Example " + idx++);
                entry.put("description", "");
                entry.put("value", renderExampleValue(ex));
                list.add(entry);
            }
            examples = list;
        }
        if (!examples.isEmpty()) {
            if (property.vendorExtensions == null) {
                property.vendorExtensions = new HashMap<>();
            }
            property.vendorExtensions.put("pluralExamples", examples);
        }
    }

    /**
     * Converts the OAS named {@code examples} map carried on each
     * {@link CodegenParameter} into a Mustache-friendly list under
     * {@code parameter.vendorExtensions["pluralExamples"]}. Templates read
     * the list via {@code {{#vendorExtensions.pluralExamples}}}.
     */
    protected static void capturePluralExamplesForParameters(
            List<CodegenParameter> params) {
        if (params == null) {
            return;
        }
        for (final CodegenParameter param : params) {
            if (param == null || param.examples == null || param.examples.isEmpty()) {
                continue;
            }
            final List<Map<String, Object>> list = buildPluralExamplesList(param.examples);
            if (list.isEmpty()) {
                continue;
            }
            if (param.vendorExtensions == null) {
                param.vendorExtensions = new HashMap<>();
            }
            param.vendorExtensions.put("pluralExamples", list);
        }
    }

    // =========================================================================
    // Decorator pass — operation / parameter / property properties
    //
    // Lifts repetitive Mustache conditionals into Java by attaching
    // first-class derived properties to vendorExtensions. Templates read them
    // via {{vendorExtensions.op.foo}}, {{vendorExtensions.param.foo}}, and
    // {{vendorExtensions.prop.foo}} — namespaced sub-maps avoid collisions
    // with spec-supplied x-extensions. Populated unconditionally so the
    // decorator pass alone is a no-op on output (templates choose whether
    // to consume the derived fields).
    // =========================================================================

    /** Namespace key under {@code op.vendorExtensions} for operation-level decorators. */
    private static final String OP_DECORATOR_NS = "op";

    /** Namespace key under {@code param.vendorExtensions} for parameter-level decorators. */
    private static final String PARAM_DECORATOR_NS = "param";

    /** Namespace key under {@code prop.vendorExtensions} for property-level decorators. */
    private static final String PROP_DECORATOR_NS = "prop";

    /**
     * Returns the decorator sub-map under {@code vendorExtensions[ns]},
     * creating both the outer {@code vendorExtensions} map and the inner
     * decorator sub-map on demand. Used by the three populate* methods to
     * keep decorator properties out of the spec-supplied vendor-extension
     * namespace.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> decoratorMap(
            Map<String, Object> vendorExtensions, String ns) {
        Object existing = vendorExtensions.get(ns);
        if (existing instanceof Map) {
            return (Map<String, Object>) existing;
        }
        final Map<String, Object> created = new HashMap<>();
        vendorExtensions.put(ns, created);
        return created;
    }

    /**
     * Populates operation-level decorator properties on
     * {@code op.vendorExtensions["op"]}. Idempotent — safe to invoke
     * multiple times per operation. Templates consume via
     * {@code {{vendorExtensions.op.<name>}}}.
     *
     * <p>Properties populated:
     * <ul>
     *   <li>{@code effectiveConsumes} — request Content-Type (first of
     *       {@code consumes} or {@code "application/json"} as a default)</li>
     *   <li>{@code effectiveProduces} — response Accept (first of
     *       {@code produces} or {@code "application/json"} as a default)</li>
     *   <li>{@code optionsClassName} — {@code Pascalcase(operationId) + "Options"}</li>
     *   <li>{@code serverClassName} — {@code Pascalcase(operationId) + "Server"}</li>
     *   <li>{@code apiClassName} — {@code Pascalcase(operationId) + "Api"}</li>
     *   <li>{@code optionsParamRequired} — true iff any options-eligible
     *       parameter is {@code required} (replaces legacy
     *       {@code vendorExtensions.hasRequiredOptions})</li>
     *   <li>{@code hasOptionsParam} — true iff the operation has any
     *       options-eligible parameter (query/header/form/cookie)</li>
     *   <li>{@code hasPerOperationServer} — true iff {@code op.servers} is
     *       non-empty</li>
     *   <li>{@code requestBodyKind} — one of {@code "none" | "json" |
     *       "form" | "multipart" | "binary" | "text" | "other"}</li>
     *   <li>{@code returnKind} — one of {@code "void" | "primitive" |
     *       "model" | "container"}</li>
     *   <li>{@code hasReturnType} — true iff {@code returnType != null}</li>
     *   <li>{@code hasQueryParams|hasHeaderParams|hasFormParams|
     *        hasCookieParams|hasPathParams|hasBodyParam} — convenience
     *        booleans equivalent to the {@code op.<list>.isEmpty()}
     *        complement</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    protected void populateOperationDecorators(CodegenOperation op) {
        if (op == null) {
            return;
        }
        if (op.vendorExtensions == null) {
            op.vendorExtensions = new HashMap<>();
        }
        final Map<String, Object> d = decoratorMap(op.vendorExtensions, OP_DECORATOR_NS);

        // O1 / O2 — Effective Content-Type and Accept
        final String effectiveConsumes = effectiveMediaType(op.consumes);
        final String effectiveProduces = effectiveMediaType(op.produces);
        d.put("effectiveConsumes", effectiveConsumes);
        d.put("effectiveProduces", effectiveProduces);

        // O3 / O4 / O5 — Derived class names from operationId
        final String opId = op.operationId == null ? "" : op.operationId;
        final String pascal = NamingConvention.PASCAL_CASE.apply(opId);
        final String optionsClassName = pascal + "Options";
        final String serverClassName = pascal + "Server";
        d.put("optionsClassName", optionsClassName);
        d.put("serverClassName", serverClassName);
        d.put("apiClassName", pascal + "Api");

        // O6 — Options-param required flag (promoted from vendorExtensions.hasRequiredOptions)
        final List<CodegenParameter> optionsParams = collectOptionsParams(op);
        boolean anyRequired = false;
        for (final CodegenParameter p : optionsParams) {
            if (p.required) {
                anyRequired = true;
                break;
            }
        }
        d.put("optionsParamRequired", anyRequired);
        d.put("hasOptionsParam", !optionsParams.isEmpty());

        // O7 — Per-operation server presence
        d.put("hasPerOperationServer", op.servers != null && !op.servers.isEmpty());

        // O8 — Request-body kind (none/json/form/multipart/binary/text/other)
        d.put("requestBodyKind", deriveRequestBodyKind(op));

        // Phase 1.14 — Required-but-empty body indicator. Promoted from the
        // upstream spec-level "x-is-empty-body" extension that
        // CleanEmptyRequestBodiesRule attaches to the RequestBody in Tag
        // mode. Surfacing it on the operation decorator removes the last
        // vendorExtensions.x-* reference from any template.
        d.put("requestBodyIsRequiredButEmpty", isRequestBodyRequiredButEmpty(op));

        // O10 — Return kind classification
        d.put("returnKind", deriveReturnKind(op));
        d.put("hasReturnType", op.returnType != null);

        // Convenience booleans (mirrors the {{#list}}{{#-first}}…{{/-first}}{{/list}} pattern)
        d.put("hasQueryParams", op.queryParams != null && !op.queryParams.isEmpty());
        d.put("hasHeaderParams", op.headerParams != null && !op.headerParams.isEmpty());
        d.put("hasFormParams", op.formParams != null && !op.formParams.isEmpty());
        d.put("hasCookieParams", op.cookieParams != null && !op.cookieParams.isEmpty());
        d.put("hasPathParams", op.pathParams != null && !op.pathParams.isEmpty());
        d.put("hasBodyParam", op.bodyParam != null);

        // Phase 1.7 — signatureArgs: ordered list of method-signature parameters
        // (path / body / options / server) so templates can iterate declaratively
        // instead of the nested {{#queryParams}}{{#-first}}…{{^queryParams}}
        // {{#headerParams}}… separator chain that previously appeared in every
        // language's api template. The auth slot intentionally stays per-template
        // since its type varies per auth method (resolved from the outer
        // {{#authMethods}} scope at render time).
        d.put("signatureArgs", computeSignatureArgs(op, true, optionsClassName, serverClassName));
        d.put(
                "signatureArgsNoServer",
                computeSignatureArgs(op, false, optionsClassName, serverClassName));

        // Phase 1.7+ — Stamp the resolved authenticator class name on each
        // auth method so templates can reference {{vendorExtensions.
        // authenticatorClassName}} inside {{#authMethods}} instead of
        // composing it inline from {{#lambda.pascalcase}}{{name}}…{{#isCode}}
        // AuthorizationCode{{/isCode}}…Authenticator.
        if (op.authMethods != null) {
            for (final CodegenSecurity am : op.authMethods) {
                if (am == null) {
                    continue;
                }
                if (am.vendorExtensions == null) {
                    am.vendorExtensions = new HashMap<>();
                }
                am.vendorExtensions.put("authenticatorClassName", toAuthClassName(am));
                if (am.name != null) {
                    am.vendorExtensions.put(
                            "namePascal", NamingConvention.PASCAL_CASE.apply(am.name));
                }
            }
        }

        // Propagate the op decorator reference onto every parameter so templates
        // can read {{vendorExtensions.op.optionsClassName}} from inside a
        // parameter sub-scope (e.g. {{#queryParams}}{{#-first}}…{{/-first}}
        // {{/queryParams}}). JMustache resolves vendorExtensions against the
        // innermost scope only, so without this mirror the op-level decorator
        // is invisible to parameter-scoped template blocks.
        propagateOpDecoratorToParams(op, d);
    }

    /**
     * Copies the operation-level decorator sub-map reference onto every
     * parameter's {@code vendorExtensions["op"]} key so templates inside
     * parameter scopes can read it.
     */
    private static void propagateOpDecoratorToParams(
            CodegenOperation op, Map<String, Object> opDeco) {
        propagateOpDecoratorToList(op.allParams, opDeco);
        propagateOpDecoratorToList(op.queryParams, opDeco);
        propagateOpDecoratorToList(op.headerParams, opDeco);
        propagateOpDecoratorToList(op.formParams, opDeco);
        propagateOpDecoratorToList(op.cookieParams, opDeco);
        propagateOpDecoratorToList(op.pathParams, opDeco);
        propagateOpDecoratorToList(op.bodyParams, opDeco);
        if (op.bodyParam != null) {
            if (op.bodyParam.vendorExtensions == null) {
                op.bodyParam.vendorExtensions = new HashMap<>();
            }
            op.bodyParam.vendorExtensions.put(OP_DECORATOR_NS, opDeco);
        }
        // CodegenServer ships with a non-null empty vendorExtensions map by
        // default, which means jmustache stops walking the context chain at
        // the server scope when resolving {{vendorExtensions.op.foo}}. Mirror
        // the op decorator onto each server so templates inside
        // {{#servers}}…{{/servers}} can still reach it.
        if (op.servers != null) {
            for (final CodegenServer s : op.servers) {
                if (s == null) {
                    continue;
                }
                if (s.vendorExtensions == null) {
                    s.vendorExtensions = new HashMap<>();
                }
                s.vendorExtensions.put(OP_DECORATOR_NS, opDeco);
            }
        }
        // CodegenSecurity (authMethods) likewise has a non-null
        // vendorExtensions; mirror so templates inside {{#authMethods}}…
        // {{/authMethods}} can read the op decorator.
        if (op.authMethods != null) {
            for (final CodegenSecurity am : op.authMethods) {
                if (am == null) {
                    continue;
                }
                if (am.vendorExtensions == null) {
                    am.vendorExtensions = new HashMap<>();
                }
                am.vendorExtensions.put(OP_DECORATOR_NS, opDeco);
            }
        }
    }

    /** Helper for {@link #propagateOpDecoratorToParams}. */
    private static void propagateOpDecoratorToList(
            List<CodegenParameter> params, Map<String, Object> opDeco) {
        if (params == null) {
            return;
        }
        for (final CodegenParameter p : params) {
            if (p == null) {
                continue;
            }
            if (p.vendorExtensions == null) {
                p.vendorExtensions = new HashMap<>();
            }
            p.vendorExtensions.put(OP_DECORATOR_NS, opDeco);
            // Precompute the PascalCase form of paramName so templates can
            // reference {{vendorExtensions.pascalName}} instead of invoking
            // {{#lambda.pascalcase}}{{paramName}}{{/lambda.pascalcase}}
            // inline at render time. Universal — any language template that
            // needs PascalCase access (e.g. C# options.PascalProperty) can
            // consume it; cheap to compute even if unused.
            if (p.paramName != null) {
                p.vendorExtensions.put(
                        "pascalName", NamingConvention.PASCAL_CASE.apply(p.paramName));
            }
            // Universal precomputed strings for the most repeated inline
            // ternaries across api templates. Eliminates per-param
            // {{#required}} (required){{/required}}{{^required}} (optional)
            // {{/required}} and {{#isExplode}}true{{/isExplode}}…false… repeats.
            p.vendorExtensions.put(
                    "requiredLabel", p.required ? " (required)" : " (optional)");
            final String requiredLabelWithDefault;
            if (p.required) {
                requiredLabelWithDefault = " (required)";
            } else if (!p.isContainer && p.defaultValue != null && !p.defaultValue.isEmpty()) {
                requiredLabelWithDefault =
                        " (optional, default to " + p.defaultValue + ")";
            } else {
                requiredLabelWithDefault = " (optional)";
            }
            p.vendorExtensions.put("requiredLabelWithDefault", requiredLabelWithDefault);
            p.vendorExtensions.put("isExplodeStr", p.isExplode ? "true" : "false");
            p.vendorExtensions.put("isExplodeStrCap", p.isExplode ? "True" : "False");
        }
    }

    /** Runs {@link #populateParameterDecorators} over every entry of a list. */
    private void populateParameterDecoratorsForList(List<CodegenParameter> params) {
        if (params == null) {
            return;
        }
        for (final CodegenParameter p : params) {
            populateParameterDecorators(p);
        }
    }

    /**
     * Populates parameter-level decorator properties on
     * {@code param.vendorExtensions["param"]}. Templates consume via
     * {@code {{vendorExtensions.param.<name>}}}.
     *
     * <p>Properties populated:
     * <ul>
     *   <li>{@code pluralExamples} — Mustache-friendly list of named
     *       examples (replaces legacy
     *       {@code vendorExtensions.examples})</li>
     *   <li>{@code hasPluralExamples} — true iff the list is non-empty</li>
     *   <li>{@code serializationMode} — one of {@code "form" | "deepObject" |
     *       "spaceDelimited" | "pipeDelimited" | "default"}</li>
     *   <li>{@code pathSerialisationKind} — one of {@code "scalar" | "array" |
     *       "object"} for path parameters; {@code "none"} otherwise</li>
     *   <li>{@code requiresPathEncoding} — true iff path-param value should
     *       be percent-encoded (always true today; reserved for future
     *       passthrough modes)</li>
     *   <li>{@code isScalar} — true iff parameter is a primitive scalar
     *       (not container, not model)</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    protected void populateParameterDecorators(CodegenParameter param) {
        if (param == null) {
            return;
        }
        if (param.vendorExtensions == null) {
            param.vendorExtensions = new HashMap<>();
        }
        final Map<String, Object> d = decoratorMap(param.vendorExtensions, PARAM_DECORATOR_NS);

        // P6 — Plural examples (mirror of vendorExtensions.pluralExamples)
        List<Map<String, Object>> examples = Collections.emptyList();
        if (param.examples != null && !param.examples.isEmpty()) {
            examples = buildPluralExamplesList(param.examples);
        }
        // Mirror the flat-key write done by capturePluralExamplesForParameters
        // so this method is order-independent: if the flat write already ran,
        // pick up its value rather than re-deriving.
        if (examples.isEmpty()) {
            final Object flat = param.vendorExtensions.get("pluralExamples");
            if (flat instanceof List) {
                examples = (List<Map<String, Object>>) flat;
            }
        }
        d.put("pluralExamples", examples);
        d.put("hasPluralExamples", !examples.isEmpty());

        // P1 — Serialization mode (style/explode/deepObject 4-way nest)
        d.put("serializationMode", deriveSerializationMode(param));

        // Phase 1.5 — Query-parameter serialization strategy. Captures the
        // four mutually-exclusive query-param emission branches that every
        // language's api template currently expresses as a nested
        // {{#isDeepObject}}/{{#content}}/{{#isAllowEmptyValue}} cascade:
        //   "deepObject"        — explode an object into bracketed keys
        //   "content"           — serialize via the content media-type codec
        //   "styledAllowEmpty"  — RFC 6570 style serialization, emit "" when
        //                         the value is absent (allowEmptyValue:true)
        //   "styled"            — RFC 6570 style serialization, omit when absent
        // The single string lets a template select one block instead of
        // nesting; the boolean mirrors below let jmustache (which has no
        // switch) pick a section directly.
        final String querySerializationKind = deriveQuerySerializationKind(param);
        d.put("querySerializationKind", querySerializationKind);
        d.put("queryDeepObject", "deepObject".equals(querySerializationKind));
        d.put("queryContent", "content".equals(querySerializationKind));
        d.put(
                "queryStyledAllowEmpty",
                "styledAllowEmpty".equals(querySerializationKind));
        d.put("queryStyled", "styled".equals(querySerializationKind));

        // P2 / P3 — Path serialisation kind + encoding requirement
        d.put("pathSerialisationKind", derivePathSerialisationKind(param));
        d.put("requiresPathEncoding", param.isPathParam);

        // Scalar/container/model classification (P4 family)
        d.put(
                "isScalar",
                !param.isContainer
                        && !param.isModel
                        && !param.isFile
                        && !param.isBinary);
    }

    /**
     * Populates property-level decorator properties on
     * {@code prop.vendorExtensions["prop"]}. Invoked from
     * {@link #postProcessAllModels} for every property of every model so
     * the decorator pass runs strictly before the operation pass.
     * Templates consume via {@code {{vendorExtensions.prop.<name>}}}.
     *
     * <p>Properties populated:
     * <ul>
     *   <li>{@code pluralExamples} — Mustache-friendly list of named
     *       examples (mirrors {@code param.vendorExtensions.examples}
     *       so model templates can share the same render block)</li>
     *   <li>{@code hasPluralExamples} — true iff the list is non-empty</li>
     *   <li>{@code isScalar} — true iff property is a primitive scalar</li>
     *   <li>{@code isContainerOfModel} — true iff property is a container
     *       whose items reference a model</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    protected void populatePropertyDecorators(
            CodegenProperty property, List<Object> allModels) {
        if (property == null) {
            return;
        }
        if (property.vendorExtensions == null) {
            property.vendorExtensions = new HashMap<>();
        }
        final Map<String, Object> d = decoratorMap(property.vendorExtensions, PROP_DECORATOR_NS);

        // M5 — Plural examples (mirror of vendorExtensions.pluralExamples)
        List<Map<String, Object>> examples = Collections.emptyList();
        final Object flat = property.vendorExtensions.get("pluralExamples");
        if (flat instanceof List) {
            examples = (List<Map<String, Object>>) flat;
        }
        d.put("pluralExamples", examples);
        d.put("hasPluralExamples", !examples.isEmpty());

        d.put(
                "isScalar",
                !property.isContainer
                        && !property.isModel
                        && !property.isFile
                        && !property.isBinary);
        final boolean isContainerOfModel =
                property.isContainer
                        && property.items != null
                        && property.items.isModel;
        d.put("isContainerOfModel", isContainerOfModel);
    }

    /**
     * Returns the first media-type from {@code consumesOrProduces} or
     * {@code "application/json"} if the list is null/empty. Mirrors the
     * Mustache idiom
     * {@code {{#list.0}}{{mediaType}}{{/list.0}}{{^list}}application/json{{/list}}}.
     */
    private static String effectiveMediaType(List<Map<String, String>> list) {
        if (list != null && !list.isEmpty()) {
            final Map<String, String> first = list.get(0);
            final String mt = first == null ? null : first.get("mediaType");
            if (mt != null && !mt.isEmpty()) {
                return mt;
            }
        }
        return "application/json";
    }

    /**
     * Builds the language-agnostic structural list of method-signature
     * parameters for an operation: path params (in order), then bodyParam (if
     * present), then a single synthetic {@code options} entry (present iff
     * any of query/header/form/cookieParams exist), then optionally a
     * {@code server} entry. The auth slot is intentionally NOT included
     * because its type is per-auth-method and must be emitted by the
     * template from its outer {@code {{#authMethods}}} scope.
     *
     * <p>Each entry is a Mustache-friendly map with: {@code kind},
     * {@code isPath} / {@code isBody} / {@code isOptions} / {@code isServer},
     * {@code paramName}, {@code dataType} (null for options/server — the
     * template substitutes {@code vendorExtensions.op.optionsClassName} /
     * {@code serverClassName}), {@code nullable} (true when the parameter
     * should carry the per-language nullable annotation).
     */
    private static List<Map<String, Object>> computeSignatureArgs(
            CodegenOperation op,
            boolean includeServer,
            String optionsClassName,
            String serverClassName) {
        final List<Map<String, Object>> args = new ArrayList<>();
        if (op.pathParams != null) {
            for (final CodegenParameter p : op.pathParams) {
                args.add(signatureArg("path", p.paramName, p.dataType, false, p.isNullable));
            }
        }
        if (op.bodyParam != null) {
            args.add(
                    signatureArg(
                            "body",
                            op.bodyParam.paramName,
                            op.bodyParam.dataType,
                            !op.bodyParam.required,
                            op.bodyParam.isNullable));
        }
        final boolean hasQuery = op.queryParams != null && !op.queryParams.isEmpty();
        final boolean hasHeader = op.headerParams != null && !op.headerParams.isEmpty();
        final boolean hasForm = op.formParams != null && !op.formParams.isEmpty();
        final boolean hasCookie = op.cookieParams != null && !op.cookieParams.isEmpty();
        if (hasQuery || hasHeader || hasForm || hasCookie) {
            // Matches the existing Java rule: the options param is @Nullable
            // only when ONLY cookie params populate it; otherwise it's
            // non-null (because at least one required-eligible kind exists).
            final boolean nullable = !hasQuery && !hasHeader && !hasForm && hasCookie;
            args.add(signatureArg("options", "options", optionsClassName, nullable, false));
        }
        if (includeServer && op.servers != null && !op.servers.isEmpty()) {
            args.add(signatureArg("server", "server", serverClassName, true, false));
        }
        return args;
    }

    private static Map<String, Object> signatureArg(
            String kind, String paramName, String dataType, boolean nullable, boolean isNullable) {
        final Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("isPath", "path".equals(kind));
        m.put("isBody", "body".equals(kind));
        m.put("isOptions", "options".equals(kind));
        m.put("isServer", "server".equals(kind));
        m.put("paramName", paramName);
        m.put("dataType", dataType);
        m.put("nullable", nullable);
        m.put("isNullable", isNullable);
        return m;
    }

    /**
     * Classifies an operation's request body into one of
     * {@code "none" | "json" | "form" | "multipart" | "binary" |
     * "text" | "other"} based on declared {@code consumes} media-types
     * and the presence of {@code formParams}.
     */
    private static String deriveRequestBodyKind(CodegenOperation op) {
        if (op.bodyParam == null && (op.formParams == null || op.formParams.isEmpty())) {
            return "none";
        }
        if (op.isMultipart) {
            return "multipart";
        }
        if (op.formParams != null && !op.formParams.isEmpty()) {
            return "form";
        }
        if (op.bodyParam != null && op.bodyParam.isBinary) {
            return "binary";
        }
        final String mt = effectiveMediaType(op.consumes).toLowerCase(Locale.ROOT);
        if (mt.contains("json")) {
            return "json";
        }
        if (mt.startsWith("text/")) {
            return "text";
        }
        if (mt.contains("form-urlencoded")) {
            return "form";
        }
        if (mt.contains("multipart")) {
            return "multipart";
        }
        return "other";
    }

    /**
     * Returns {@code true} when {@link CleanEmptyRequestBodiesRule} has
     * tagged the operation's request body as required-but-empty. The rule
     * sets {@code x-is-empty-body} on the spec-level
     * {@code RequestBody.extensions}; openapi-generator propagates that
     * onto the CodegenParameter's vendorExtensions during processing.
     */
    private static boolean isRequestBodyRequiredButEmpty(CodegenOperation op) {
        if (op.bodyParam == null || op.bodyParam.vendorExtensions == null) {
            return false;
        }
        final Object v = op.bodyParam.vendorExtensions.get("x-is-empty-body");
        return Boolean.TRUE.equals(v);
    }

    /**
     * Classifies an operation's return type into one of
     * {@code "void" | "primitive" | "model" | "container"}.
     */
    private static String deriveReturnKind(CodegenOperation op) {
        if (op.returnType == null) {
            return "void";
        }
        if (op.isArray || op.isMap || op.returnContainer != null) {
            return "container";
        }
        if (op.returnBaseType != null && !op.returnBaseType.isEmpty()
                && Character.isUpperCase(op.returnBaseType.charAt(0))) {
            return "model";
        }
        return "primitive";
    }

    /**
     * Classifies a parameter's serialization style + explode combo into
     * one of {@code "form" | "deepObject" | "spaceDelimited" |
     * "pipeDelimited" | "default"}. Used by query-param templates that
     * currently nest 3-4 levels of style/explode conditionals.
     */
    private static String deriveSerializationMode(CodegenParameter param) {
        if (param.isDeepObject) {
            return "deepObject";
        }
        final String style = param.style;
        if (style == null) {
            return "default";
        }
        switch (style) {
            case "form":
                return "form";
            case "spaceDelimited":
                return "spaceDelimited";
            case "pipeDelimited":
                return "pipeDelimited";
            default:
                return "default";
        }
    }

    /**
     * Classifies a query parameter's emission strategy into one of
     * {@code "deepObject" | "content" | "styledAllowEmpty" | "styled"}.
     * Mirrors the precedence of the nested template cascade exactly:
     * deepObject wins over content, content wins over the styled branches,
     * and {@code allowEmptyValue} only distinguishes the two styled cases.
     */
    private static String deriveQuerySerializationKind(CodegenParameter param) {
        if (param.isDeepObject) {
            return "deepObject";
        }
        if (param.getContent() != null && !param.getContent().isEmpty()) {
            return "content";
        }
        if (param.isAllowEmptyValue) {
            return "styledAllowEmpty";
        }
        return "styled";
    }

    /**
     * Classifies a path parameter's value shape into one of
     * {@code "scalar" | "array" | "object" | "none"}.
     */
    private static String derivePathSerialisationKind(CodegenParameter param) {
        if (!param.isPathParam) {
            return "none";
        }
        if (param.isArray) {
            return "array";
        }
        if (param.isMap || param.isModel) {
            return "object";
        }
        return "scalar";
    }

    // =========================================================================
    // Gap 17 — File content fixup declarations
    // =========================================================================

    /**
     * Describes a regex-based post-processing fixup applied to
     * generated files that match a given extension.
     */
    public static final class FileContentFixup {
        private final String extension;
        private final Pattern pattern;
        private final String replacement;

        /**
         * @param extension   the file extension to match (e.g. {@code ".go"}, {@code ".py"})
         * @param pattern     the compiled regex {@link Pattern} to find
         * @param replacement the replacement string (supports backreferences)
         */
        public FileContentFixup(String extension, Pattern pattern, String replacement) {
            this.extension = extension;
            this.pattern = pattern;
            this.replacement = replacement;
        }

        /** Returns the file extension this fixup applies to. */
        public String extension() { return extension; }

        /** Returns the regex pattern to search for. */
        public Pattern pattern() { return pattern; }

        /** Returns the replacement string. */
        public String replacement() { return replacement; }
    }

    /**
     * Returns the list of {@link FileContentFixup}s to apply
     * during {@link #postProcessFile}. Default is empty. Go
     * declares a fixup to remove trailing commas from
     * {@code .go} files; Python declares one to fix f-string
     * brace whitespace in {@code .py} files.
     */
    protected List<FileContentFixup> getFileContentFixups() {
        return List.of();
    }

    // =========================================================================
    // Gap 18 — Operation import filter flag
    // =========================================================================

    /**
     * Returns {@code true} if primitive and mapped-type imports
     * should be removed from the operation import list during
     * {@link #postProcessOperationsWithModels}. Used by Dart.
     * Default is {@code false}.
     */
    protected boolean filtersOperationImports() {
        return false;
    }

    // =========================================================================
    // Gap 19 — Unified operation context flags
    // =========================================================================

    // =========================================================================
    // Gap 26 — Options-only model import filter (Node / TypeScript)
    // =========================================================================

    /**
     * Returns {@code true} if model imports that are <em>only</em> referenced
     * by options-style parameters (query, header, cookie) — and never by
     * path/body parameters or return types — should be removed from the
     * operation import list during
     * {@link #postProcessOperationsWithModels}.
     *
     * <p>TypeScript generates an Options class for every operation that has
     * optional parameters. Models that appear exclusively in that Options class
     * would produce an unused-import warning in the main API file. This flag
     * lets the base class remove those imports automatically.
     *
     * <p>Only Node currently returns {@code true}. Default is {@code false}.
     */
    protected boolean filtersOptionsOnlyModelImports() {
        return false;
    }

    /**
     * Adds the base type of a parameter (and its item base type if it is a
     * collection) to {@code types} when the type is not a language primitive
     * and looks like a model name (starts with an uppercase letter).
     *
     * <p>Used by {@link #postProcessOperationsWithModels} when
     * {@link #filtersOptionsOnlyModelImports()} is {@code true}.
     */
    private void addModelBaseTypeToSet(Set<String> types, CodegenParameter p) {
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

    /**
     * Returns a map whose keys describe a condition and whose values are the
     * template context flag to set when that condition is true for any
     * operation in the tag. Four key forms are supported:
     *
     * <ul>
     *   <li>{@code "type:<substr>"} — set the flag if any operation's
     *       {@code returnType} or any parameter's {@code dataType} contains
     *       {@code <substr>}. Example: Go uses {@code "type:os.File"} →
     *       {@code "hasOsImport"}.</li>
     *   <li>{@code "servers"} — set the flag if any operation has a non-empty
     *       per-operation servers list. Example: Go uses {@code "servers"} →
     *       {@code "hasStringsImport"}.</li>
     *   <li>{@code "cookieParams"} — set the flag if any operation has a cookie
     *       parameter. Example: Go uses {@code "cookieParams"} →
     *       {@code "hasStringsImport"}.</li>
     *   <li>{@code "queryContent"} — set the flag if any non-deep-object query
     *       parameter uses content-type negotiation (non-empty {@code content}
     *       map). Example: Go uses {@code "queryContent"} →
     *       {@code "hasJsonImport"}.</li>
     * </ul>
     *
     * Default is an empty map (no flags set).
     */
    protected Map<String, String> getOperationContextFlags() {
        return Map.of();
    }

    /**
     * Returns whether the given datatype is numeric based on
     * the set from {@link #getNumericDataTypes()}.
     */
    protected final boolean isNumericEnumDatatype(String datatype) {
        return getNumericDataTypes().contains(datatype);
    }

    /**
     * Wraps an enum string value in the language's quote
     * character with proper escaping or stripping based on
     * {@link #getQuoteChar()} and
     * {@link #shouldEscapeQuotationMark()}.
     */
    protected final String quoteEnumValue(String value) {
        final char q = getQuoteChar();
        final String qs = String.valueOf(q);
        final String cleaned =
                shouldEscapeQuotationMark()
                        ? value.replace(qs, "\\" + q)
                        : value.replace(qs, "");
        return q + cleaned + q;
    }

    /**
     * Escapes or strips the language's quote character from
     * generated string literals based on
     * {@link #getQuoteChar()} and
     * {@link #shouldEscapeQuotationMark()}.
     */
    @Override
    public final String escapeQuotationMark(String input) {
        final char q = getQuoteChar();
        final String qs = String.valueOf(q);
        if (shouldEscapeQuotationMark()) {
            return input.replace(qs, "\\" + q);
        }
        return input.replace(qs, "");
    }

    /**
     * Escapes comment-closing sequences that could prematurely
     * terminate block comments in generated code. Inserts an
     * underscore to break the sequence while keeping the text
     * readable.
     */
    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*");
    }

    /**
     * Overrides {@link DefaultCodegen#fromProperty} so that named plural
     * examples declared on a schema (OAS 3.1 {@code examples} list or the
     * {@code x-examples} extension on OAS 3.0 schemas) are carried through
     * into a Mustache-iterable list stored at
     * {@code property.vendorExtensions["examples"]}. Templates iterate that
     * list to render per-example docstring blocks (Gap BD).
     */
    @Override
    @SuppressWarnings("rawtypes")
    public CodegenProperty fromProperty(
            String name, Schema p, boolean required, boolean schemaIsFromAdditionalProperties) {
        final CodegenProperty property =
                super.fromProperty(name, p, required, schemaIsFromAdditionalProperties);
        capturePropertyPluralExamples(property, p);
        applyConstAsSingleValueEnum(property, p);
        return property;
    }

    /**
     * Translates OpenAPI 3.1 {@code const} schemas into the existing
     * single-value enum codepath. Upstream {@link DefaultCodegen}
     * acknowledges {@code const} is unsupported (see its own warning:
     * "Maybe it's a const (not yet supported) in openapi v3.1 spec.");
     * this hook fills the gap by treating a property with a non-null
     * {@code const} value as a one-element enum. The default value is
     * set to the const so language templates render an immutable field
     * initialized to the only allowed value (Gap AY).
     */
    @SuppressWarnings("rawtypes")
    private static void applyConstAsSingleValueEnum(CodegenProperty property, Schema p) {
        if (property == null || p == null) {
            return;
        }
        final Object constValue = p.getConst();
        if (constValue == null) {
            return;
        }
        if (property.isEnum || (property._enum != null && !property._enum.isEmpty())) {
            return;
        }
        final String stringValue = String.valueOf(constValue);
        final List<String> enumValues = new ArrayList<>();
        enumValues.add(stringValue);
        property._enum = enumValues;
        property.isEnum = true;
        property.isInnerEnum = true;
        final Map<String, Object> allowableValues = new HashMap<>();
        final List<Object> values = new ArrayList<>();
        values.add(constValue);
        allowableValues.put("values", values);
        property.allowableValues = allowableValues;
        if (property.defaultValue == null) {
            property.defaultValue = stringValue;
        }
    }

    /**
     * Post-processes operations after all models are resolved.
     * Strips global-level auth from individual operations to
     * avoid redundant auth injection, injects tag metadata for
     * API-level documentation, and builds structured server
     * type definitions for per-operation server overrides.
     */
    @Override
    @SuppressWarnings("unchecked")
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        objs = super.postProcessOperationsWithModels(objs, allModels);
        final Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            final String classname = (String) operations.get("classname");
            if (classname != null) {
                operations.put("clientPropertyName", deriveClientPropertyName(classname));
            }
            injectTagMetadata(operations);
            final List<CodegenOperation> ops =
                    (List<CodegenOperation>) operations.get("operation");
            boolean anyOpHasAuth = false;
            if (ops != null) {
                for (final CodegenOperation op : ops) {
                    if (globalAuthOperationIds.contains(op.operationId)) {
                        op.authMethods = null;
                        op.hasAuthMethods = false;
                    }
                    if (op.hasAuthMethods) {
                        anyOpHasAuth = true;
                    }
                }
            }
            objs.put("hasAnyAuthMethods", anyOpHasAuth);
            if (ops != null) {
                enrichOperationServers(ops, operations);
                generateOptionsFilesForOps(ops, objs);
                // Gap BD: surface plural examples on parameters for docstrings
                for (final CodegenOperation op : ops) {
                    capturePluralExamplesForParameters(op.allParams);
                    capturePluralExamplesForParameters(op.pathParams);
                    capturePluralExamplesForParameters(op.queryParams);
                    capturePluralExamplesForParameters(op.headerParams);
                    capturePluralExamplesForParameters(op.cookieParams);
                    capturePluralExamplesForParameters(op.formParams);
                    if (op.bodyParam != null) {
                        capturePluralExamplesForParameters(
                                Collections.singletonList(op.bodyParam));
                    }
                }
                // Decorator pass — attaches derived properties to vendorExtensions.
                // Runs AFTER generateOptionsFilesForOps so optionsParamRequired
                // observes the same options-eligible parameter set.
                for (final CodegenOperation op : ops) {
                    populateOperationDecorators(op);
                    // Populate parameter decorators on every parameter list.
                    // OpenAPI-generator stores distinct CodegenParameter copies
                    // in allParams vs queryParams/headerParams/etc.; templates
                    // iterate the location-specific lists, so the decorator must
                    // run on those copies too — not just allParams.
                    populateParameterDecoratorsForList(op.allParams);
                    populateParameterDecoratorsForList(op.queryParams);
                    populateParameterDecoratorsForList(op.headerParams);
                    populateParameterDecoratorsForList(op.pathParams);
                    populateParameterDecoratorsForList(op.formParams);
                    populateParameterDecoratorsForList(op.cookieParams);
                    populateParameterDecoratorsForList(op.bodyParams);
                    if (op.bodyParam != null) {
                        populateParameterDecorators(op.bodyParam);
                    }
                }
            }
        }
        cleanupBadImports(objs);

        // Gap 18: Filter primitive/mapped-type imports (Dart)
        if (filtersOperationImports()) {
            @SuppressWarnings("unchecked")
            final List<Map<String, String>> filteredImports =
                    (List<Map<String, String>>) objs.get("imports");
            if (filteredImports != null) {
                filteredImports.removeIf(imp -> {
                    final String importName = imp.getOrDefault("import", "");
                    final String className =
                            importName.contains(".")
                                    ? importName.substring(importName.lastIndexOf('.') + 1)
                                    : importName;
                    return languageSpecificPrimitives.contains(className)
                            || typeMapping.containsValue(className)
                            || className.startsWith("List<")
                            || className.equals("List")
                            || className.startsWith("Map<")
                            || className.equals("Map")
                            || className.startsWith("Set<")
                            || className.equals("Set");
                });
            }
        }

        // Gap 26: Remove model imports that appear only in options params (Node)
        if (filtersOptionsOnlyModelImports()) {
            @SuppressWarnings("unchecked")
            final Map<String, Object> opsMapForFilter =
                    (Map<String, Object>) objs.get("operations");
            if (opsMapForFilter != null) {
                @SuppressWarnings("unchecked")
                final List<CodegenOperation> opsForFilter =
                        (List<CodegenOperation>) opsMapForFilter.get("operation");
                if (opsForFilter != null) {
                    final Set<String> optionsOnlyModels = new HashSet<>();
                    final Set<String> nonOptionsModels = new HashSet<>();
                    for (final CodegenOperation op : opsForFilter) {
                        final List<CodegenParameter> optParams = collectOptionsParams(op);
                        final Set<String> optParamNames = new HashSet<>();
                        for (final CodegenParameter p : optParams) {
                            optParamNames.add(p.paramName);
                            addModelBaseTypeToSet(optionsOnlyModels, p);
                        }
                        if (op.allParams != null) {
                            for (final CodegenParameter p : op.allParams) {
                                if (!optParamNames.contains(p.paramName)) {
                                    addModelBaseTypeToSet(nonOptionsModels, p);
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
                            optImports.removeIf(imp -> {
                                final String cn = imp.getOrDefault(
                                        "className", imp.getOrDefault("classname", ""));
                                return optionsOnlyModels.contains(cn);
                            });
                        }
                    }
                }
            }
        }

        // Gap 19: Operation type-substring / server / cookie / query-content flags (Go)
        applyOperationContextFlags(objs);

        return objs;
    }

    /**
     * Applies the declarative operation context flags declared by
     * {@link #getOperationContextFlags()} to the operations map.
     * Supports four key forms: {@code "type:<substr>"}, {@code "servers"},
     * {@code "cookieParams"}, and {@code "queryContent"}.
     */
    @SuppressWarnings("unchecked")
    private void applyOperationContextFlags(OperationsMap objs) {
        final Map<String, String> flags = getOperationContextFlags();
        if (flags.isEmpty()) {
            return;
        }

        final Map<String, Object> opsMap = (Map<String, Object>) objs.get("operations");
        if (opsMap == null) {
            return;
        }
        final List<CodegenOperation> ops =
                (List<CodegenOperation>) opsMap.get("operation");
        if (ops == null) {
            return;
        }

        // Track which flags have already been set (avoid redundant writes)
        final Map<String, Boolean> flagState = new HashMap<>();
        for (final String flagName : flags.values()) {
            flagState.put(flagName, false);
        }

        for (final CodegenOperation op : ops) {
            for (final Map.Entry<String, String> entry : flags.entrySet()) {
                final String key = entry.getKey();
                final String flagName = entry.getValue();
                if (flagState.getOrDefault(flagName, false)) {
                    continue; // already set
                }
                if (key.startsWith("type:")) {
                    final String substr = key.substring(5);
                    if (op.returnType != null && op.returnType.contains(substr)) {
                        flagState.put(flagName, true);
                        continue;
                    }
                } else if ("servers".equals(key)) {
                    if (op.servers != null && !op.servers.isEmpty()) {
                        flagState.put(flagName, true);
                        continue;
                    }
                }
                // Per-parameter checks
                if (op.allParams != null) {
                    for (final CodegenParameter p : op.allParams) {
                        if (flagState.getOrDefault(flagName, false)) {
                            break;
                        }
                        if (key.startsWith("type:")) {
                            final String substr = key.substring(5);
                            if (p.dataType != null && p.dataType.contains(substr)) {
                                flagState.put(flagName, true);
                            }
                        } else if ("cookieParams".equals(key)) {
                            if (p.isCookieParam) {
                                flagState.put(flagName, true);
                            }
                        } else if ("queryContent".equals(key)) {
                            if (p.isQueryParam && !p.isDeepObject
                                    && p.getContent() != null
                                    && !p.getContent().isEmpty()) {
                                flagState.put(flagName, true);
                            }
                        }
                    }
                }
            }
        }

        // Write set flags to objs
        for (final Map.Entry<String, Boolean> entry : flagState.entrySet()) {
            if (entry.getValue()) {
                objs.put(entry.getKey(), true);
            }
        }
    }

    /**
     * Iterates operations in a tag, generates per-operation Options
     * files for those with query/header/form/cookie params, and
     * injects import metadata into the operations context so the
     * API template can emit the correct import statements.
     */
    @SuppressWarnings("unchecked")
    private void generateOptionsFilesForOps(
            List<CodegenOperation> ops, Map<String, Object> objs) {
        final List<Map<String, String>> optionsImports = new ArrayList<>();
        for (final CodegenOperation op : ops) {
            final List<CodegenParameter> optionsParams = collectOptionsParams(op);
            if (optionsParams.isEmpty()) {
                continue;
            }
            // Whether any Options-class field is required is exposed to
            // templates via the operation decorator's
            // {@code vendorExtensions.op.optionsParamRequired} flag (populated
            // by {@link #populateOperationDecorators}). Templates use it to
            // drop the trailing {@code ?} / {@code = nil} sigil and force the
            // caller to pass an Options instance (compile-time check).
            final String className =
                    NamingConvention.PASCAL_CASE.apply(op.operationId) + "Options";
            final String content = generateOptionsFileContent(op, optionsParams, className);
            if (content == null) {
                continue;
            }
            final String filePath = getOptionsFilePath(op.operationId, className);
            writeFile(filePath, content);
            postProcessFile(Path.of(filePath).toFile(), "source");

            final Map<String, String> meta = new HashMap<>();
            meta.put("optionsClassName", className);
            meta.put("optionsFilePath", filePath);
            accumulatedOptionsFiles.add(meta);

            final Map<String, String> importMeta = new HashMap<>();
            importMeta.put("optionsClassName", className);
            final NamingConvention fc = getFilenameCasing();
            importMeta.put("optionsFileName", fc != null ? fc.apply(className) : className);
            optionsImports.add(importMeta);

            enrichOptionsMetadata(meta, op.operationId, className);

            // Expose to supporting-file templates (rendered after all API tags).
            additionalProperties.put("hasAnyOptionsClasses", true);
            @SuppressWarnings("unchecked")
            List<String> reqPaths = (List<String>) additionalProperties
                    .computeIfAbsent("optionsRequires", k -> new ArrayList<String>());
            final String rp = meta.get("requirePath");
            if (rp != null) {
                reqPaths.add(rp);
            }
        }
        if (!optionsImports.isEmpty()) {
            objs.put("optionsImports", optionsImports);
        }
    }

    /**
     * Removes imports that reference invalid class names such
     * as camelCase inline schema names generated for oneOf
     * variants. Identifies the class name by extracting the
     * segment after the last space (Python-style) or last dot
     * (Java-style), then removes entries whose class name
     * starts with a lowercase letter.
     */
    @SuppressWarnings("unchecked")
    private static void cleanupBadImports(OperationsMap objs) {
        final List<Map<String, String>> imports =
                (List<Map<String, String>>) objs.get("imports");
        if (imports == null) {
            return;
        }
        imports.removeIf(
                imp -> {
                    final String importLine = imp.get("import");
                    if (importLine == null) {
                        return false;
                    }
                    final int lastSpace = importLine.lastIndexOf(' ');
                    final String className;
                    if (lastSpace >= 0) {
                        className = importLine.substring(lastSpace + 1);
                    } else {
                        final int lastDot = importLine.lastIndexOf('.');
                        className =
                                lastDot >= 0
                                        ? importLine.substring(lastDot + 1)
                                        : importLine;
                    }
                    return !className.isEmpty() && Character.isLowerCase(className.charAt(0));
                });
    }

    /**
     * Builds structured server type definitions for operations
     * that declare per-operation servers. Populates
     * {@code serverTypeDefs} and {@code hasServerTypeDefs}
     * in the operations map so templates can generate typesafe
     * server selection types with variant names and variables.
     */
    @SuppressWarnings("unchecked")
    private static void enrichOperationServers(
            List<CodegenOperation> ops, Map<String, Object> operations) {
        final List<Map<String, Object>> serverTypeDefs = new ArrayList<>();
        for (final CodegenOperation op : ops) {
            if (op.servers == null || op.servers.isEmpty()) {
                continue;
            }
            final Map<String, Object> typeDef = new HashMap<>();
            typeDef.put("operationId", op.operationId);
            typeDef.put("serverTypeName", NamingConvention.PASCAL_CASE.apply(op.operationId) + "Server");

            final List<Map<String, Object>> variants = new ArrayList<>();
            for (int i = 0; i < op.servers.size(); i++) {
                final CodegenServer server = op.servers.get(i);
                final Map<String, Object> variant = new HashMap<>();

                final int serverIndex = i;
                final String variantName =
                        Optional.ofNullable(server.description)
                                .filter(d -> !d.isBlank())
                                .map(d -> NamingConvention.PASCAL_CASE.apply(d.replaceAll("[^a-zA-Z0-9]+", "_").trim()))
                                .orElse("Server" + serverIndex);
                variant.put("variantName", variantName);
                variant.put("variantNameLower", NamingConvention.CAMEL_CASE.apply(variantName));
                variant.put("url", server.url);
                variant.put("description", server.description);
                variant.put(
                        "hasDescription",
                        Optional.ofNullable(server.description)
                                .filter(d -> !d.isBlank())
                                .isPresent());
                final boolean hasVars =
                        Optional.ofNullable(server.variables)
                                .filter(v -> !v.isEmpty())
                                .isPresent();
                variant.put("hasVariables", hasVars);

                if (hasVars) {
                    final List<Map<String, Object>> vars = new ArrayList<>();
                    for (final CodegenServerVariable v : server.variables) {
                        final Map<String, Object> varMap = new HashMap<>();
                        varMap.put("name", v.name);
                        varMap.put("camelName", NamingConvention.CAMEL_CASE.apply(v.name));
                        varMap.put("pascalName", NamingConvention.PASCAL_CASE.apply(v.name));
                        varMap.put("snakeName", NamingConvention.SNAKE_CASE.apply(v.name));
                        varMap.put("defaultValue", v.defaultValue);
                        final boolean hasEnum =
                                Optional.ofNullable(v.enumValues)
                                        .filter(e -> !e.isEmpty())
                                        .isPresent();
                        varMap.put("hasEnumValues", hasEnum);
                        if (hasEnum) {
                            final List<Map<String, String>> enumVals = new ArrayList<>();
                            for (final String e : v.enumValues) {
                                final Map<String, String> ev = new HashMap<>();
                                final String evName =
                                        NamingConvention.UPPER_SNAKE_CASE.apply(
                                                e.replace(".", "_"));
                                ev.put("name", evName);
                                ev.put("value", e);
                                // Precomputed lowercase form so templates can use
                                // {{nameLowercase}} instead of invoking
                                // {{#lambda.lowercase}}{{name}}{{/lambda.lowercase}}.
                                ev.put("nameLowercase", evName.toLowerCase(java.util.Locale.ROOT));
                                enumVals.add(ev);
                            }
                            varMap.put("enumValues", enumVals);
                        }
                        vars.add(varMap);
                    }
                    variant.put("serverVariables", vars);
                }
                variants.add(variant);
            }
            typeDef.put("variants", variants);
            typeDef.put("multipleVariants", variants.size() > 1);
            serverTypeDefs.add(typeDef);
        }

        operations.put("serverTypeDefs", serverTypeDefs);
        operations.put("hasServerTypeDefs", !serverTypeDefs.isEmpty());
    }

    /**
     * Derives a property name for the API client instance from
     * the API class name by stripping the "Api" suffix and
     * applying {@link #getVarCasing()}. Used in client facades
     * that expose each API group as a named property.
     */
    protected String deriveClientPropertyName(String apiClassName) {
        final String name = apiClassName.replaceAll("Api$", "");
        return name.isEmpty() ? getVarCasing().apply("api") : getVarCasing().apply(name);
    }

    /**
     * Injects tag description and external documentation into
     * the operations template context so API class-level
     * Javadoc or docstrings can render the tag metadata from
     * the OpenAPI spec. All operations in a group share the
     * same tag, so the first operation's tag is used to look
     * up the description and external docs.
     */
    @SuppressWarnings("unchecked")
    private void injectTagMetadata(Map<String, Object> operations) {
        if (openAPI == null || openAPI.getTags() == null) {
            return;
        }
        final List<CodegenOperation> ops =
                (List<CodegenOperation>) operations.get("operation");
        if (ops == null || ops.isEmpty()) {
            return;
        }
        final String tagName =
                Optional.ofNullable(ops.get(0).tags)
                        .filter(t -> !t.isEmpty())
                        .map(t -> t.get(0).getName())
                        .orElse(null);
        if (tagName == null) {
            return;
        }
        for (final Tag tag : openAPI.getTags()) {
            if (tagName.equals(tag.getName())) {
                Optional.ofNullable(tag.getDescription())
                        .ifPresent(desc -> operations.put("tagDescription", desc));
                Optional.ofNullable(tag.getExternalDocs())
                        .ifPresent(
                                extDocs -> {
                                    final Map<String, Object> externalDocsMap = new HashMap<>();
                                    externalDocsMap.put("url", extDocs.getUrl());
                                    externalDocsMap.put("description", extDocs.getDescription());
                                    operations.put("tagExternalDocs", externalDocsMap);
                                });
                break;
            }
        }
    }

    /**
     * Creates a {@link CodegenOperation} from an OpenAPI path
     * operation and tracks whether the operation inherits
     * global security. Operations without explicit security
     * blocks inherit global auth and are recorded so their
     * auth methods can be stripped during post-processing.
     */
    @Override
    public CodegenOperation fromOperation(
            String path, String httpMethod, Operation operation, List<Server> servers) {
        final CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);
        if (operation.getSecurity() == null) {
            globalAuthOperationIds.add(op.operationId);
        }
        return op;
    }

    /**
     * Returns the language-specific base directory (relative
     * to the output root) where test fixtures such as TLS
     * certificates and the Squid proxy config should be placed.
     */
    protected abstract String getTestFixturesDir();

    /**
     * Returns the language-specific directory (relative to the
     * output root) where user-written spec tests should be
     * placed. An empty directory with a .gitkeep file is
     * created here so the directory is tracked by version
     * control.
     */
    protected abstract String getSpecDir();

    /**
     * Copies bundled test fixtures and the input OpenAPI spec
     * into the output directory so the generated SDK's test
     * suite is fully self-contained and can run without
     * external file dependencies.
     */
    private void writeTestFixtures() {
        final Path outputDir = Path.of(getOutputDir());

        final String inputSpec = getInputSpec();
        if (inputSpec != null) {
            final Path specTarget = outputDir.resolve(getTestFixturesDir() + "/openapi.yaml");
            try {
                final Path parent = specTarget.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(Path.of(inputSpec), specTarget, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Failed to copy {} to {}: {}", inputSpec, specTarget, e.getMessage());
            }
        }

        final String[] fixtures = {
            "certs/ca.pem",
            "certs/ca-key.pem",
            "certs/server.pem",
            "certs/server-key.pem",
            "proxy/squid.conf"
        };
        final Path fixturesBase = outputDir.resolve(getTestFixturesDir());
        for (final String fixture : fixtures) {
            final Path target = fixturesBase.resolve(fixture);
            try (InputStream is =
                    getClass().getClassLoader().getResourceAsStream("fixtures/" + fixture)) {
                if (is == null) {
                    LOGGER.warn("Test fixture not found on classpath: fixtures/{}", fixture);
                    continue;
                }
                final Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Failed to copy test fixture fixtures/{}: {}", fixture, e.getMessage());
            }
        }

        try {
            final Path specDir = outputDir.resolve(getSpecDir());
            Files.createDirectories(specDir);
            final Path gitkeep = specDir.resolve(".gitkeep");
            if (!Files.exists(gitkeep)) {
                Files.writeString(gitkeep, "");
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to create spec directory: {}", e.getMessage());
        }
    }

    /**
     * Collects all options-eligible parameters (query, header, form, cookie)
     * from the operation into a single ordered list with required params first.
     */
    protected static List<CodegenParameter> collectOptionsParams(CodegenOperation op) {
        final List<CodegenParameter> required = new ArrayList<>();
        final List<CodegenParameter> optional = new ArrayList<>();
        final List<List<CodegenParameter>> groups = new ArrayList<>();
        if (op.queryParams != null) groups.add(op.queryParams);
        if (op.headerParams != null) groups.add(op.headerParams);
        if (op.formParams != null) groups.add(op.formParams);
        if (op.cookieParams != null) groups.add(op.cookieParams);
        for (final List<CodegenParameter> group : groups) {
            for (final CodegenParameter p : group) {
                if (p.required) {
                    required.add(p);
                } else {
                    optional.add(p);
                }
            }
        }
        required.addAll(optional);
        return required;
    }

    /**
     * Writes generated source content to a file, creating any
     * missing parent directories. Logs a warning on failure
     * instead of throwing so code generation can continue.
     */
    @SuppressFBWarnings(
            value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification = "filePath always contains a parent directory")
    protected void writeFile(String filePath, String content) {
        try {
            final Path path = Path.of(filePath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to write file: {}", filePath, e);
        }
    }

    /**
     * Renders a Mustache template from the embedded template directory
     * with the given context map. Used for programmatic per-operation
     * file generation (e.g. Options classes) where the standard
     * apiTemplateFiles mechanism cannot produce per-operation files.
     */
    protected String renderOptionsTemplate(String templateName, Map<String, Object> context) {
        for (Map.Entry<String, Object> entry : additionalProperties.entrySet()) {
            context.putIfAbsent(entry.getKey(), entry.getValue());
        }
        final String templatePath = embeddedTemplateDir + "/" + templateName;
        try (InputStream is =
                        getClass().getClassLoader().getResourceAsStream(templatePath);
                InputStreamReader reader =
                        new InputStreamReader(
                                Objects.requireNonNull(
                                        is, "Template not found: " + templatePath),
                                StandardCharsets.UTF_8)) {
            return Mustache.compiler()
                    .escapeHTML(false)
                    .withLoader(
                            name -> {
                                String partialPath =
                                        embeddedTemplateDir + "/" + name + ".mustache";
                                InputStream partialIs =
                                        getClass()
                                                .getClassLoader()
                                                .getResourceAsStream(partialPath);
                                if (partialIs == null) {
                                    throw new FileNotFoundException(
                                            "Partial not found: " + partialPath);
                                }
                                return new InputStreamReader(
                                        partialIs, StandardCharsets.UTF_8);
                            })
                    .compile(reader)
                    .execute(context);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render template: " + templatePath, e);
        }
    }

    /**
     * Generates an Options file for the given operation.
     * Subclasses override to produce language-idiomatic content.
     * Returns null if no Options file should be generated.
     */
    @Nullable
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        return null;
    }

    /**
     * Returns the full file path for an Options file for the
     * given operation. Subclasses must override when
     * {@link #generateOptionsFileContent} returns non-null.
     */
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        throw new UnsupportedOperationException(
                "Override getOptionsFilePath when generating Options files");
    }

    /**
     * Hook for subclasses to add language-specific metadata to
     * the accumulated Options file map. Called once per generated
     * Options file. Default is a no-op.
     */
    protected void enrichOptionsMetadata(
            Map<String, String> meta, String operationId, String optionsClassName) {
        // no-op by default
    }

    /**
     * Writes barrel/index files for the accumulated Options classes.
     * Delegates to {@link BarrelFileEmitter#emitBarrelFiles} when this
     * codegen implements that interface; otherwise a no-op.
     */
    protected void writeOptionsBarrelFiles(List<Map<String, String>> optionsFiles) {
        if (this instanceof BarrelFileEmitter) {
            ((BarrelFileEmitter) this).emitBarrelFiles(optionsFiles);
        }
    }

    /**
     * Returns the accumulated Options file metadata, for use
     * by subclass barrel generation.
     */
    protected List<Map<String, String>> getAccumulatedOptionsFiles() {
        return Collections.unmodifiableList(accumulatedOptionsFiles);
    }

    /**
     * Runs the language-specific code formatter inside Docker
     * using the image from {@link #getFormatterDockerImage()}
     * and commands from {@link #getFormatterCommands()}.
     */
    @Override
    public void postProcess() {
        if (!accumulatedOptionsFiles.isEmpty()) {
            writeOptionsBarrelFiles(accumulatedOptionsFiles);
        }
        runFormatterInDocker(getFormatterDockerImage(), getFormatterCommands());
    }

    /**
     * Post-processes a single generated file. First collapses runs of
     * two or more consecutive blank lines into one (a cosmetic artefact
     * of cascading Mustache section guards). Then applies any
     * {@link FileContentFixup}s declared by {@link #getFileContentFixups()}
     * to matching file extensions (Gap 17).
     *
     * <p>Subclasses that override this method <em>must</em> call
     * {@code super.postProcessFile(file, fileType)} first so that the
     * blank-line normalisation and fixups run before any additional
     * language-specific formatting.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);
        if (file == null || !file.exists()) {
            return;
        }

        // Pass 1: Blank-line collapse + trailing-blank trim (always applied)
        try {
            final List<String> lines =
                    Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            final List<String> result = new ArrayList<>(lines.size());
            boolean prevBlank = false;
            boolean changed = false;
            for (final String line : lines) {
                final boolean blank = line.isBlank();
                if (blank && prevBlank) {
                    changed = true;
                    continue;
                }
                result.add(line);
                prevBlank = blank;
            }
            while (!result.isEmpty() && result.get(result.size() - 1).isBlank()) {
                result.remove(result.size() - 1);
                changed = true;
            }
            if (changed) {
                Files.write(file.toPath(), result, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to collapse blank lines in {}", file, e);
        }

        // Pass 2: FileContentFixup regex replacements (Gap 17)
        final String name = file.getName();
        for (final FileContentFixup fixup : getFileContentFixups()) {
            if (!name.endsWith(fixup.extension())) {
                continue;
            }
            try {
                final String content =
                        Files.readString(file.toPath(), StandardCharsets.UTF_8);
                final String fixed =
                        fixup.pattern().matcher(content).replaceAll(fixup.replacement());
                if (!fixed.equals(content)) {
                    Files.write(file.toPath(), fixed.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to apply content fixup to {}: {}", file, e.getMessage());
            }
        }

        // Gap 27: Move type-signature files to their target directory
        if (this instanceof WithTypeSignatureSupport) {
            final WithTypeSignatureSupport ts = (WithTypeSignatureSupport) this;
            if (file.getName().endsWith(ts.getSignatureFileExtension())) {
                moveToSignatureDir(file, ts);
            }
        }
    }

    /**
     * Moves a generated type-signature file from the source directory to the
     * signature collection directory declared by
     * {@link WithTypeSignatureSupport#getSignatureDir()}.
     */
    private void moveToSignatureDir(File file, WithTypeSignatureSupport ts) {
        final Path filePath = file.toPath();
        final Path outputDir = Path.of(getOutputDir());
        final Path relative = outputDir.relativize(filePath);
        final String relStr = relative.toString();
        final String sourcePrefix = ts.getSignatureSourceDir() + File.separator;
        if (!relStr.startsWith(sourcePrefix)) {
            return;
        }
        final Path sigPath = outputDir.resolve(ts.getSignatureDir())
                .resolve(relStr.substring(sourcePrefix.length()));
        final Path sigParent = sigPath.getParent();
        if (sigParent == null) {
            return;
        }
        try {
            Files.createDirectories(sigParent);
            Files.move(filePath, sigPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Failed to move signature file {} to {}: {}",
                    filePath, sigPath, e.getMessage());
        }
    }

    // =========================================================================
    // Gap 20 + 24 + 25 — postProcessAllModels declarative flags
    // =========================================================================

    /**
     * Opt-in flag for the discriminator-parent wiring logic in
     * {@link #postProcessAllModels}. Languages that rely on
     * discriminator-based inheritance (Java, C#) override this to
     * return {@code true}. Languages whose templates are not
     * designed for discriminator inheritance (Python, Ruby, etc.)
     * leave this at the default {@code false} to avoid generating
     * invalid subclass declarations without corresponding imports.
     */
    protected boolean setsDiscriminatorParent() {
        return false;
    }

    /**
     * Opt-in flag for building fully-qualified model import strings
     * in {@link #postProcessAllModels}. When {@code true}, the base
     * class replaces each bare model name in {@code model.imports}
     * with a language-idiomatic FQN import string built from
     * {@code modelPackage}, {@link #toModelFilename(String)}, and
     * the class name. Python overrides this to {@code true}.
     * Default is {@code false}.
     */
    protected boolean buildsFqnModelImports() {
        return false;
    }

    /**
     * Returns a map from datatype name to the import statement
     * needed to use that type in model files. Used by
     * {@link #postProcessAllModels} when
     * {@link #buildsFqnModelImports()} is {@code true} to inject
     * stdlib or third-party imports for built-in types (e.g. Python's
     * {@code "datetime"} → {@code "from datetime import datetime"}).
     * Default is an empty map.
     */
    protected Map<String, String> getPropertyTypeImportMap() {
        return Map.of();
    }

    /**
     * Opt-in flag to remove the discriminator property from each
     * child model's field lists in {@link #postProcessAllModels}.
     * When a polymorphic parent uses a discriminator field, the
     * generated enum wrapper already manages that tag; the child
     * struct must not declare it as a plain field. Rust overrides
     * this to {@code true}. Default is {@code false}.
     */
    protected boolean removesDiscriminatorPropertyFromChildren() {
        return false;
    }

    /**
     * Opt-in flag (Gap K) to set {@code defaultValue} on each child
     * model's discriminator property to the mapping name declared on
     * the parent, and demote the property out of {@code requiredVars}
     * so generated constructors do not require it as a parameter.
     * This lets generated subtype models auto-emit the discriminator
     * field on serialization without the caller having to set it.
     * Languages whose model templates honour {@code defaultValue} on
     * properties (Node, Go) override this to {@code true}. Default
     * is {@code false}.
     */
    protected boolean setsDiscriminatorDefaultOnChildren() {
        return false;
    }

    /**
     * Returns the target-language literal expression for a
     * discriminator default string value. Called by
     * {@link #postProcessAllModels} when
     * {@link #setsDiscriminatorDefaultOnChildren()} is {@code true}.
     * Override per language to switch quoting style. Default wraps
     * the value in double quotes, which works for Go, Java, C# and
     * similar languages.
     */
    protected String formatDiscriminatorDefaultValue(String mappingName) {
        return "\"" + mappingName + "\"";
    }

    /**
     * Opt-in flag that also demotes the discriminator property
     * from {@code requiredVars} into {@code optionalVars} after
     * the default value is set. Useful for languages whose
     * constructor templates split required and optional parameter
     * lists (Java, C#). Languages whose templates iterate
     * {@code vars} directly and branch on {@code defaultValue}
     * (Swift, Dart, Elixir) leave this at {@code false} so the
     * property's type stays non-nullable in the constructor
     * signature.
     */
    protected boolean demotesDiscriminatorFromRequiredVars() {
        return false;
    }

    /**
     * Wires discriminator-to-subtype parent links (Gap 20), builds
     * fully-qualified model imports (Gap 24), removes the
     * discriminator property from child struct field lists (Gap 25),
     * and auto-injects the discriminator default on subtype models
     * (Gap K). Each step is gated by its own opt-in flag.
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        final Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        final Map<String, String> typeImportMap = getPropertyTypeImportMap();
        // Decorator pass over model properties — runs BEFORE the
        // operation-level decorator pass (postProcessAllModels precedes
        // postProcessOperationsWithModels in the codegen pipeline).
        final List<Object> allModelsForDecorator = new ArrayList<>();
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                allModelsForDecorator.add(modelMap.getModel());
            }
        }
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();
                if (model.vars != null) {
                    for (final CodegenProperty prop : model.vars) {
                        populatePropertyDecorators(prop, allModelsForDecorator);
                    }
                }
                if (model.allVars != null) {
                    for (final CodegenProperty prop : model.allVars) {
                        populatePropertyDecorators(prop, allModelsForDecorator);
                    }
                }
            }
        }
        for (final ModelsMap modelsMap : result.values()) {
            for (final ModelMap modelMap : modelsMap.getModels()) {
                final CodegenModel model = modelMap.getModel();

                // Gap 20: Wire discriminator-based parent links
                if (setsDiscriminatorParent()
                        && model.discriminator != null && !model.oneOf.isEmpty()) {
                    for (final CodegenDiscriminator.MappedModel mapped :
                            model.discriminator.getMappedModels()) {
                        setParentOnChild(result, mapped.getModelName(), model.classname);
                    }
                }

                // Gap 24: Build fully-qualified model imports (Python)
                if (buildsFqnModelImports()) {
                    final TreeSet<String> fullImports = new TreeSet<>();
                    if (!typeImportMap.isEmpty()) {
                        for (final CodegenProperty prop : model.allVars) {
                            final String imp = typeImportMap.get(prop.dataType);
                            if (imp != null) {
                                fullImports.add(imp);
                            }
                            if (prop.items != null) {
                                final String itemImp = typeImportMap.get(prop.items.dataType);
                                if (itemImp != null) {
                                    fullImports.add(itemImp);
                                }
                            }
                        }
                    }
                    for (final String imp : model.imports) {
                        fullImports.add("from " + modelPackage + "."
                                + toModelFilename(imp) + " import " + imp);
                    }
                    model.imports.clear();
                    model.imports.addAll(fullImports);
                }

                // Gap 25: Remove discriminator property from child struct fields (Rust)
                if (removesDiscriminatorPropertyFromChildren()
                        && model.discriminator != null && !model.oneOf.isEmpty()) {
                    final String discPropName = model.discriminator.getPropertyBaseName();
                    for (final CodegenDiscriminator.MappedModel mapped :
                            model.discriminator.getMappedModels()) {
                        removeDiscriminatorFromChild(
                                result, mapped.getModelName(), discPropName);
                    }
                }

                // Gap K: Auto-inject discriminator value on serialization by
                // defaulting the child model's discriminator property.
                if (setsDiscriminatorDefaultOnChildren()
                        && model.discriminator != null && !model.oneOf.isEmpty()) {
                    final String discPropName = model.discriminator.getPropertyBaseName();
                    final boolean demote = demotesDiscriminatorFromRequiredVars();
                    final boolean resort = sortVarsByDefaultValue();
                    for (final CodegenDiscriminator.MappedModel mapped :
                            model.discriminator.getMappedModels()) {
                        setDiscriminatorDefaultOnChild(
                                result,
                                mapped.getModelName(),
                                discPropName,
                                formatDiscriminatorDefaultValue(mapped.getMappingName()),
                                demote,
                                resort);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Sets {@code parent} and {@code parentSchema} on a child
     * model (identified by {@code childName}) to {@code parentName},
     * but only if the child does not already have a parent.
     */
    private static void setParentOnChild(
            Map<String, ModelsMap> allModels, String childName, String parentName) {
        final ModelsMap childModels = allModels.get(childName);
        if (childModels == null) {
            return;
        }
        for (final ModelMap modelMap : childModels.getModels()) {
            final CodegenModel child = modelMap.getModel();
            if (child.parent == null) {
                child.parent = parentName;
                child.parentSchema = parentName;
            }
        }
    }

    /**
     * Removes the named discriminator property from all field lists
     * ({@code vars}, {@code requiredVars}, {@code optionalVars},
     * {@code allVars}, {@code readWriteVars}) of the child model
     * identified by {@code childName}.
     */
    private static void removeDiscriminatorFromChild(
            Map<String, ModelsMap> allModels, String childName, String discPropName) {
        final ModelsMap childModels = allModels.get(childName);
        if (childModels == null) {
            return;
        }
        for (final ModelMap modelMap : childModels.getModels()) {
            final CodegenModel child = modelMap.getModel();
            child.vars.removeIf(p -> discPropName.equals(p.baseName));
            child.requiredVars.removeIf(p -> discPropName.equals(p.baseName));
            child.optionalVars.removeIf(p -> discPropName.equals(p.baseName));
            child.allVars.removeIf(p -> discPropName.equals(p.baseName));
            child.readWriteVars.removeIf(p -> discPropName.equals(p.baseName));
        }
    }

    /**
     * Sets {@code defaultValue} on the discriminator property of the
     * child model identified by {@code childName} (Gap K). The
     * property keeps its required JSON shape in {@code vars} /
     * {@code allVars} (so generated field declarations remain
     * non-nullable). When {@code demote} is {@code true} it is also
     * moved from {@code requiredVars} to {@code optionalVars} for
     * languages whose constructor templates split required and
     * optional parameter lists. When {@code resort} is {@code true},
     * {@code vars} and {@code allVars} are re-sorted by presence of
     * {@code defaultValue} (Elixir defstruct ordering).
     */
    private static void setDiscriminatorDefaultOnChild(
            Map<String, ModelsMap> allModels,
            String childName,
            String discPropName,
            String defaultLiteral,
            boolean demote,
            boolean resort) {
        final ModelsMap childModels = allModels.get(childName);
        if (childModels == null) {
            return;
        }
        for (final ModelMap modelMap : childModels.getModels()) {
            final CodegenModel child = modelMap.getModel();
            CodegenProperty target = applyDiscriminatorDefault(
                    child.vars, discPropName, defaultLiteral);
            final CodegenProperty fromAllVars = applyDiscriminatorDefault(
                    child.allVars, discPropName, defaultLiteral);
            if (target == null) {
                target = fromAllVars;
            }
            applyDiscriminatorDefault(child.readWriteVars, discPropName, defaultLiteral);
            applyDiscriminatorDefault(child.requiredVars, discPropName, defaultLiteral);
            applyDiscriminatorDefault(child.optionalVars, discPropName, defaultLiteral);

            if (demote) {
                child.requiredVars.removeIf(p -> discPropName.equals(p.baseName));
                if (target != null) {
                    final boolean alreadyOptional = child.optionalVars.stream()
                            .anyMatch(p -> discPropName.equals(p.baseName));
                    if (!alreadyOptional) {
                        child.optionalVars.add(target);
                    }
                }
                child.hasRequired = !child.requiredVars.isEmpty();
            }

            if (resort) {
                child.vars.sort(
                        Comparator.comparing(p -> p.defaultValue != null ? 1 : 0));
                child.allVars.sort(
                        Comparator.comparing(p -> p.defaultValue != null ? 1 : 0));
            }
        }
    }

    @Nullable
    private static CodegenProperty applyDiscriminatorDefault(
            @Nullable List<CodegenProperty> props,
            String discPropName,
            String defaultLiteral) {
        if (props == null) {
            return null;
        }
        CodegenProperty found = null;
        for (final CodegenProperty prop : props) {
            if (discPropName.equals(prop.baseName)) {
                prop.defaultValue = defaultLiteral;
                prop.isDiscriminator = true;
                if (found == null) {
                    found = prop;
                }
            }
        }
        return found;
    }

    /**
     * Strips the parent type from a model when the parent is
     * a language primitive, a mapped type, or an instantiation
     * type. These pseudo-parents arise from allOf with
     * primitive base types and would generate invalid
     * inheritance in the output language.
     */
    protected void stripPrimitiveParent(CodegenModel model) {
        Optional.ofNullable(model.parent)
                .map(
                        parent -> {
                            final int idx = parent.indexOf('<');
                            return idx >= 0 ? parent.substring(0, idx) : parent;
                        })
                .filter(
                        base ->
                                languageSpecificPrimitives.contains(base)
                                        || typeMapping.containsValue(base)
                                        || instantiationTypes.containsValue(base))
                .ifPresent(
                        ignored -> {
                            model.parent = null;
                            model.parentModel = null;
                        });
    }

    /**
     * Runs formatter commands inside a Docker container with
     * the output directory bind-mounted at {@code /app}.
     * Commands are joined with {@code &&} and executed via
     * {@code sh -c}. Logs output at DEBUG level and warns on
     * failure without throwing, so code generation succeeds
     * even if Docker is not available.
     */
    @SuppressFBWarnings(
            value = {"COMMAND_INJECTION", "PATH_TRAVERSAL_IN"},
            justification = "Commands and paths are hardcoded by subclasses, not user input")
    protected void runFormatterInDocker(String dockerImage, String... commands) {
        final String workDir = getOutputDir();
        final String script = String.join(" && ", commands);
        final List<String> dockerCmd =
                List.of(
                        "docker",
                        "run",
                        "--rm",
                        "-v",
                        workDir + ":/app",
                        "-w",
                        "/app",
                        dockerImage,
                        "sh",
                        "-c",
                        script);
        try {
            LOGGER.debug("Running formatter in Docker: {}", dockerCmd);
            final ProcessBuilder pb =
                    new ProcessBuilder(dockerCmd)
                            .directory(new File(workDir))
                            .redirectErrorStream(true);
            final Process process = pb.start();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> LOGGER.debug("[formatter] {}", line));
            }
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                LOGGER.debug(
                        "Docker formatter {} exited with code {} in {}",
                        dockerImage,
                        exitCode,
                        workDir);
            }
        } catch (IOException e) {
            LOGGER.warn(
                    "Docker formatter {} failed in {}: {}", dockerImage, workDir, e.getMessage());
        } catch (InterruptedException e) {
            LOGGER.warn("Docker formatter {} interrupted in {}", dockerImage, workDir);
            Thread.currentThread().interrupt();
        }
    }
}

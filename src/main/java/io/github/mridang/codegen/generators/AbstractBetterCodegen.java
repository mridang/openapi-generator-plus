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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.EnumSet;
import java.util.stream.Collectors;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base for all six language code generators (Ruby,
 * Python, PHP, Java, Node/TypeScript, and C#). Centralizes
 * shared concerns that every generated client needs: security
 * scheme detection across all OpenAPI auth types, global and
 * per-operation server configuration extraction, reserved-word
 * loading from classpath resources, and post-processing hooks
 * for operations, models, and enum values.
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
     * quote).
     */
    protected abstract char getQuoteChar();

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

    /**
     * Conditionally registers base auth class supporting files
     * based on which security scheme types are present.
     * Subclasses use the boolean flags set by
     * {@link #detectSecuritySchemes} to decide which auth
     * template files to emit.
     */
    protected abstract void registerAuthSupportingFiles();

    /**
     * Generates per-scheme concrete authenticator classes
     * programmatically. The default implementation is a no-op;
     * only Java overrides this with real logic.
     */
    protected void generatePerSchemeAuthenticators(OpenAPI openAPI) {
        // no-op by default
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
     * Formats an array type declaration with the given
     * container and inner type. Override in subclasses for
     * language-specific array syntax (e.g. {@code List[str]}
     * in Python vs {@code Array<String>} in TypeScript).
     */
    protected String formatArrayType(String containerType, String innerType) {
        return containerType + "<" + innerType + ">";
    }

    /**
     * Formats a map type declaration with the given container,
     * key, and value types. Override in subclasses for
     * language-specific map syntax (e.g. {@code Dict[str, Any]}
     * in Python vs {@code Hash} in Ruby).
     */
    protected String formatMapType(String containerType, String keyType, String valueType) {
        return containerType + "<" + keyType + ", " + valueType + ">";
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
     * Post-processes generated models to strip primitive parents,
     * apply enum naming conventions, and sanitize byte-array
     * example values that would otherwise render as Java memory
     * addresses in generated documentation.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = postProcessModelsEnum(super.postProcessModels(objs));
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            stripPrimitiveParent(model);
            for (final var prop : model.vars) {
                sanitizeByteArrayExample(prop);
            }
            for (final var prop : model.allVars) {
                sanitizeByteArrayExample(prop);
            }
        }
        return result;
    }

    /**
     * Post-processes a model property to handle unique-item
     * arrays by replacing the array container type with the
     * set type returned by {@link #getUniqueItemsSetType()}.
     * Subclasses that need additional property processing
     * (e.g. Jackson imports in Java) should call super.
     */
    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        final String setType = getUniqueItemsSetType();
        if (setType != null && property.isArray && property.getUniqueItems()) {
            final String pattern = getArrayContainerPattern();
            property.datatypeWithEnum =
                    property.datatypeWithEnum.replaceFirst(pattern, setType);
            property.dataType = property.dataType.replaceFirst(pattern, setType);
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
     * Sanitizes the input first to remove characters that are
     * invalid in identifiers, then checks for reserved-word
     * collisions and digit-leading names.
     */
    @Override
    public String toModelName(String name) {
        name = sanitizeName(name);
        if (isReservedWord(name)) {
            name = "model_" + name;
        }
        if (name.matches("^\\d.*")) {
            name = "model_" + name;
        }
        return NamingConvention.PASCAL_CASE.apply(name);
    }

    /**
     * Converts a property name to a language-appropriate
     * variable name by sanitizing it, applying subclass
     * casing rules, and escaping if it collides with a
     * reserved word or starts with a digit.
     */
    @Override
    public String toVarName(String name) {
        name = sanitizeName(name);
        name = applyVarNameCasing(name);
        if (isReservedWord(name) || name.matches("^\\d.*")) {
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
     * string enums are quoted via {@link #quoteEnumValue}.
     */
    @Override
    public String toEnumValue(String value, String datatype) {
        if (isNumericEnumDatatype(datatype)) {
            return value;
        }
        return quoteEnumValue(value);
    }

    /**
     * Converts a raw enum value to a language-appropriate
     * constant name using {@link #getEnumCasing()}. Returns
     * {@link #getEmptyEnumVarName()} for blank values, prefixes
     * numeric values with "NUMBER_", and sanitizes special
     * characters. Subclasses with different enum naming (e.g.
     * Python's quoted values) should override this method.
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
        final String sanitized = sanitizeName(value);
        final String cased = getEnumCasing().apply(sanitized);
        final String cleaned =
                cased.replaceFirst("^_", "").replaceFirst("_$", "");
        if (cleaned.matches("\\d.*")) {
            return "_" + cleaned;
        }
        return cleaned;
    }

    /**
     * Returns the set of language-specific numeric type names.
     * Used by {@link #isNumericEnumDatatype} to decide whether
     * enum values should be emitted as bare literals or quoted
     * strings.
     */
    protected abstract Set<String> getNumericDataTypes();

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
            }
        }
        cleanupBadImports(objs);
        return objs;
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
                                ev.put(
                                        "name",
                                        NamingConvention.UPPER_SNAKE_CASE.apply(
                                                e.replace(".", "_")));
                                ev.put("value", e);
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
     * to the output root) where test fixtures such as
     * certificates, proxy config, and WireMock mappings should
     * be placed.
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
            "certs/server-keystore.p12",
            "wiremock/mappings/test.json",
            "wiremock/mappings/redirect.json",
            "wiremock/mappings/slow.json",
            "wiremock/mappings/echo-headers.json",
            "wiremock/mappings/echo-body.json",
            "wiremock/mappings/text-plain.json",
            "wiremock/mappings/error-400.json",
            "wiremock/mappings/error-401.json",
            "wiremock/mappings/error-403.json",
            "wiremock/mappings/error-404.json",
            "wiremock/mappings/error-409.json",
            "wiremock/mappings/error-418.json",
            "wiremock/mappings/error-422.json",
            "wiremock/mappings/error-500.json",
            "wiremock/mappings/error-502.json",
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
     * Writes barrel/index files for the accumulated Options
     * classes. Subclasses override to produce language-idiomatic
     * barrel exports. Default is a no-op.
     */
    protected void writeOptionsBarrelFiles(List<Map<String, String>> optionsFiles) {
        // no-op by default
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

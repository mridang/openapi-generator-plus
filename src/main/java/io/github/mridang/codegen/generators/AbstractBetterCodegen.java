package io.github.mridang.codegen.generators;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.tags.Tag;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenSecurity;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.StringUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for all language-specific code generators. Provides type resolution, enum
 * post-processing, operation validation, and template methods for language-specific naming
 * conventions.
 *
 * <p>Subclasses must implement {@link #formatOperationId} and {@link #applyVarNameCasing}.
 * Subclasses may override {@link #formatArrayType}, {@link #formatMapType}, {@link
 * #getMapKeyType}, {@link #getMapDefaultValueType}, {@link #isNumericEnumDatatype}, and {@link
 * #quoteEnumValue}.
 */
public abstract class AbstractBetterCodegen extends DefaultCodegen
        implements UnsupportedFeaturesValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBetterCodegen.class);

    private final Set<String> globalAuthOperationIds = new HashSet<>();

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

    protected AbstractBetterCodegen() {
        super();
        typeMapping.clear();
        importMapping.clear();
        hideGenerationTimestamp = true;
    }

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
    }

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
    }

    /**
     * Extract global server definitions from the OpenAPI spec and populate
     * template properties for server selection code generation.
     *
     * <p>Sets {@code hasServers} and {@code servers} in additional properties.
     * Each server entry contains {@code serverIndex}, {@code serverUrl},
     * {@code serverDescription}, {@code hasVariables}, and {@code serverVariables}.
     */
    private void processServers(OpenAPI openAPI) {
        List<Server> servers = openAPI.getServers();
        if (servers == null || servers.isEmpty()) {
            additionalProperties.put("hasServers", false);
            return;
        }
        additionalProperties.put("hasServers", true);

        List<Map<String, Object>> serverList = new ArrayList<>();
        for (int i = 0; i < servers.size(); i++) {
            Server server = servers.get(i);
            Map<String, Object> serverMap = new HashMap<>();
            serverMap.put("serverIndex", String.valueOf(i));
            serverMap.put("serverUrl", server.getUrl() != null ? server.getUrl() : "");
            serverMap.put("serverDescription", server.getDescription());

            if (server.getVariables() != null && !server.getVariables().isEmpty()) {
                serverMap.put("hasVariables", true);
                List<Map<String, Object>> varList = new ArrayList<>();
                for (Map.Entry<String, ServerVariable> varEntry :
                        server.getVariables().entrySet()) {
                    ServerVariable sv = varEntry.getValue();
                    Map<String, Object> varMap = new HashMap<>();
                    varMap.put("varName", varEntry.getKey());
                    varMap.put("varDefault", sv.getDefault());
                    varMap.put("varDescription", sv.getDescription());
                    if (sv.getEnum() != null && !sv.getEnum().isEmpty()) {
                        varMap.put("hasEnumValues", true);
                        varMap.put("varEnumValues", sv.getEnum());
                    } else {
                        varMap.put("hasEnumValues", false);
                    }
                    varList.add(varMap);
                }
                serverMap.put("serverVariables", varList);
            } else {
                serverMap.put("hasVariables", false);
            }
            serverList.add(serverMap);
        }
        boolean anyServerHasVariables =
                serverList.stream().anyMatch(s -> Boolean.TRUE.equals(s.get("hasVariables")));
        additionalProperties.put("hasAnyServerVariables", anyServerHasVariables);
        additionalProperties.put("serverConfigs", serverList);
    }

    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Comparing with ASCII-only constants")
    private void detectSecuritySchemes(OpenAPI openAPI) {
        if (openAPI.getComponents() == null
                || openAPI.getComponents().getSecuritySchemes() == null) {
            return;
        }
        for (Map.Entry<String, SecurityScheme> entry :
                openAPI.getComponents().getSecuritySchemes().entrySet()) {
            SecurityScheme scheme = entry.getValue();
            if (scheme.getType() == SecurityScheme.Type.HTTP) {
                if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                    hasBasicAuth = true;
                } else if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                    hasBearerAuth = true;
                }
            } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
                hasApiKeyAuth = true;
            } else if (scheme.getType() == SecurityScheme.Type.OAUTH2 && scheme.getFlows() != null) {
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
     * Conditionally register base auth class supporting files based on which security scheme types
     * are present.
     */
    protected abstract void registerAuthSupportingFiles();

    /**
     * Generate per-scheme concrete authenticator classes programmatically.
     */
    protected abstract void generatePerSchemeAuthenticators(OpenAPI openAPI);

    /**
     * Compute the authenticator class name for a security scheme.
     */
    protected String toAuthClassName(CodegenSecurity auth) {
        String base = StringUtils.camelize(auth.name);
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

    protected String getPropertyOrDefault(String key, String defaultValue) {
        if (additionalProperties.containsKey(key)) {
            return (String) additionalProperties.get(key);
        }
        additionalProperties.put(key, defaultValue);
        return defaultValue;
    }

    protected static Set<String> loadReservedWords(String resourcePath) {
        try (InputStream is = AbstractBetterCodegen.class.getResourceAsStream(resourcePath);
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        java.util.Objects.requireNonNull(
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

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String escapeReservedWord(String name) {
        return "_" + name;
    }

    @Override
    public String getSchemaType(Schema schema) {
        String type = super.getSchemaType(schema);
        if (typeMapping.containsKey(type)) {
            return typeMapping.get(type);
        }
        return type;
    }

    @Override
    public String getTypeDeclaration(Schema schema) {
        if (ModelUtils.isArraySchema(schema)) {
            Schema<?> inner = ModelUtils.getSchemaItems(schema);
            return formatArrayType(getSchemaType(schema), getTypeDeclaration(inner));
        } else if (ModelUtils.isMapSchema(schema)) {
            Schema<?> inner = ModelUtils.getAdditionalProperties(schema);
            String valueType =
                    (inner == null) ? getMapDefaultValueType() : getTypeDeclaration(inner);
            return formatMapType(getSchemaType(schema), getMapKeyType(), valueType);
        }
        return super.getTypeDeclaration(schema);
    }

    protected String formatArrayType(String containerType, String innerType) {
        return containerType + "<" + innerType + ">";
    }

    protected String formatMapType(String containerType, String keyType, String valueType) {
        return containerType + "<" + keyType + ", " + valueType + ">";
    }

    protected String getMapKeyType() {
        return "String";
    }

    protected String getMapDefaultValueType() {
        return "Object";
    }

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        return postProcessModelsEnum(super.postProcessModels(objs));
    }

    @Override
    public String toApiName(String name) {
        if (name.isEmpty()) {
            return "DefaultApi";
        }
        return StringUtils.camelize(name) + "Api";
    }

    @Override
    public String toModelName(String name) {
        name = sanitizeName(name);
        return StringUtils.camelize(name);
    }

    @Override
    public String toVarName(String name) {
        name = sanitizeName(name);
        name = applyVarNameCasing(name);
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        return name;
    }

    protected abstract String applyVarNameCasing(String sanitizedName);

    @Override
    public String toParamName(String name) {
        return toVarName(name);
    }

    @Override
    public String toModelFilename(String name) {
        return toModelName(name);
    }

    @Override
    public String toApiFilename(String name) {
        return toApiName(name);
    }

    @Override
    public final String toOperationId(String operationId) {
        if (operationId == null || operationId.isEmpty()) {
            throw new IllegalArgumentException("Empty method/operation name (operationId) not allowed");
        }
        return formatOperationId(sanitizeName(operationId));
    }

    protected abstract String formatOperationId(String sanitizedOperationId);

    @Override
    public String toEnumValue(String value, String datatype) {
        if (isNumericEnumDatatype(datatype)) {
            return value;
        }
        return quoteEnumValue(value);
    }

    protected boolean isNumericEnumDatatype(String datatype) {
        return false;
    }

    protected String quoteEnumValue(String value) {
        return "\"" + escapeText(value) + "\"";
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*");
    }

    @Override
    @SuppressWarnings("unchecked")
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        objs = super.postProcessOperationsWithModels(objs, allModels);
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            String classname = (String) operations.get("classname");
            if (classname != null) {
                operations.put("clientPropertyName", deriveClientPropertyName(classname));
            }
            injectTagMetadata(operations);
            List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
            boolean anyOpHasAuth = false;
            if (ops != null) {
                for (CodegenOperation op : ops) {
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
        }
        cleanupBadImports(objs);
        return objs;
    }

    /**
     * Remove imports that reference invalid class names (e.g. camelCase inline
     * schema names generated for oneOf variants). These produce import errors
     * because the imported name does not match any actual generated class.
     */
    @SuppressWarnings("unchecked")
    private static void cleanupBadImports(OperationsMap objs) {
        List<Map<String, String>> imports = (List<Map<String, String>>) objs.get("imports");
        if (imports == null) {
            return;
        }
        imports.removeIf(
                imp -> {
                    String importLine = imp.get("import");
                    if (importLine == null) {
                        return false;
                    }
                    // Inline oneOf variant imports have lowercase class names
                    // (e.g. "import setPetAvatar_request") — filter them out.
                    int lastSpace = importLine.lastIndexOf(' ');
                    if (lastSpace < 0) {
                        return false;
                    }
                    String className = importLine.substring(lastSpace + 1);
                    return !className.isEmpty() && Character.isLowerCase(className.charAt(0));
                });
    }

    protected String deriveClientPropertyName(String apiClassName) {
        String name = apiClassName.replaceAll("Api$", "");
        if (name.isEmpty()) {
            return "api";
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Inject tag description and externalDocs into the operations template context so that API
     * class-level documentation can render tag metadata.
     */
    @SuppressWarnings("unchecked")
    private void injectTagMetadata(Map<String, Object> operations) {
        if (openAPI == null || openAPI.getTags() == null) {
            return;
        }
        List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
        if (ops == null || ops.isEmpty()) {
            return;
        }
        // All operations in this group share the same tag — use the first operation's tag
        List<Tag> opTags = ops.get(0).tags;
        if (opTags == null || opTags.isEmpty()) {
            return;
        }
        String tagName = opTags.get(0).getName();
        for (Tag tag : openAPI.getTags()) {
            if (tagName.equals(tag.getName())) {
                if (tag.getDescription() != null) {
                    operations.put("tagDescription", tag.getDescription());
                }
                ExternalDocumentation extDocs = tag.getExternalDocs();
                if (extDocs != null) {
                    Map<String, Object> externalDocsMap = new java.util.HashMap<>();
                    externalDocsMap.put("url", extDocs.getUrl());
                    externalDocsMap.put("description", extDocs.getDescription());
                    operations.put("tagExternalDocs", externalDocsMap);
                }
                break;
            }
        }
    }

    @Override
    public CodegenOperation fromOperation(
            String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        CodegenOperation op = super.fromOperation(path, httpMethod, operation, servers);
        if (operation.getSecurity() == null) {
            globalAuthOperationIds.add(op.operationId);
        }
        return op;
    }

    /**
     * Run formatter commands inside a Docker container. The output directory is bind-mounted into
     * the container at {@code /app}. Commands are joined with {@code &&} and executed via {@code
     * sh -c}. Logs output at DEBUG level and warns on failure without throwing, so code generation
     * succeeds even if Docker is not available.
     *
     * @param dockerImage the Docker image to use (e.g. "node:24-slim")
     * @param commands shell commands to run sequentially inside the container
     */
    @SuppressFBWarnings(
            value = {"COMMAND_INJECTION", "PATH_TRAVERSAL_IN"},
            justification = "Commands and paths are hardcoded by subclasses, not user input")
    protected void runFormatterInDocker(String dockerImage, String... commands) {
        String workDir = getOutputDir();
        String script = String.join(" && ", commands);
        List<String> dockerCmd =
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
            ProcessBuilder pb =
                    new ProcessBuilder(dockerCmd).directory(new File(workDir)).redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> LOGGER.debug("[formatter] {}", line));
            }
            int exitCode = process.waitFor();
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

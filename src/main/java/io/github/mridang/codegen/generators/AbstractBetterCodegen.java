package io.github.mridang.codegen.generators;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.StringUtils;

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

    protected AbstractBetterCodegen() {
        super();
        typeMapping.clear();
        importMapping.clear();
        hideGenerationTimestamp = Boolean.TRUE;
    }

    @Override
    public void processOpts() {
        super.processOpts();
        setEnablePostProcessFile(true);
        supportingFiles.clear();
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
            throw new RuntimeException("Empty method/operation name (operationId) not allowed");
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
        }
        return objs;
    }

    protected String deriveClientPropertyName(String apiClassName) {
        String name = apiClassName.replaceAll("Api$", "");
        if (name.isEmpty()) {
            return "api";
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public CodegenOperation fromOperation(
            String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}

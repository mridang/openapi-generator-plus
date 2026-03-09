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
import java.util.Set;
import java.util.stream.Collectors;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.StringUtils;

/**
 * Abstract base class for all custom code generators. Provides shared behavior
 * including client type tagging, reserved word escaping, type resolution through
 * typeMapping, enum post-processing, API naming, and operation validation.
 */
public abstract class AbstractBetterCodegen extends DefaultCodegen
        implements UnsupportedFeaturesValidator {

    protected AbstractBetterCodegen() {
        super();
        typeMapping.clear();
        importMapping.clear();
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
    public String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*");
    }

    @Override
    public CodegenOperation fromOperation(
            String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }
}

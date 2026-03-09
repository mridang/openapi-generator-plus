package io.github.mridang.codegen.generators.java;

import static org.openapitools.codegen.utils.CamelizeOption.LOWERCASE_FIRST_LETTER;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.ImportOrderer;
import com.google.googlejavaformat.java.JavaFormatterOptions;
import com.google.googlejavaformat.java.RemoveUnusedImports;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom Java code generator providing a minimal, modern Java client
 * with Jackson and Apache HttpClient.
 */
@SuppressWarnings("unused")
public class BetterJavaCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterJavaCodegen.class);

    private final Formatter formatter;
    protected String sourceFolder = "src" + File.separator + "main" + File.separator + "java";
    protected String invokerPackage = "org.openapitools";

    public BetterJavaCodegen() {
        outputFolder = "generated-code/java";
        embeddedTemplateDir = templateDir = "templates/java";

        modelTemplateFiles.put("models/model.mustache", ".java");
        apiTemplateFiles.put("api/api.mustache", ".java");

        typeMapping.put("array", "List");
        typeMapping.put("map", "Map");
        typeMapping.put("set", "Set");
        typeMapping.put("boolean", "Boolean");
        typeMapping.put("string", "String");
        typeMapping.put("int", "Integer");
        typeMapping.put("integer", "Integer");
        typeMapping.put("long", "Long");
        typeMapping.put("short", "Short");
        typeMapping.put("float", "Float");
        typeMapping.put("double", "Double");
        typeMapping.put("number", "BigDecimal");
        typeMapping.put("decimal", "BigDecimal");
        typeMapping.put("char", "String");
        typeMapping.put("object", "Object");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("binary", "byte[]");
        typeMapping.put("ByteArray", "byte[]");
        typeMapping.put("byte", "byte[]");
        typeMapping.put("file", "File");
        typeMapping.put("File", "File");
        typeMapping.put("date", "LocalDate");
        typeMapping.put("DateTime", "OffsetDateTime");
        typeMapping.put("date-time", "OffsetDateTime");
        typeMapping.put("UUID", "UUID");
        typeMapping.put("URI", "URI");
        typeMapping.put("BigDecimal", "BigDecimal");

        importMapping.put("List", "java.util.List");
        importMapping.put("Set", "java.util.Set");
        importMapping.put("Map", "java.util.Map");
        importMapping.put("ArrayList", "java.util.ArrayList");
        importMapping.put("Arrays", "java.util.Arrays");
        importMapping.put("LinkedHashSet", "java.util.LinkedHashSet");
        importMapping.put("HashMap", "java.util.HashMap");
        importMapping.put("LocalDate", "java.time.LocalDate");
        importMapping.put("OffsetDateTime", "java.time.OffsetDateTime");
        importMapping.put("BigDecimal", "java.math.BigDecimal");
        importMapping.put("UUID", "java.util.UUID");
        importMapping.put("URI", "java.net.URI");
        importMapping.put("File", "java.io.File");
        importMapping.put("JsonProperty", "com.fasterxml.jackson.annotation.JsonProperty");
        importMapping.put("JsonValue", "com.fasterxml.jackson.annotation.JsonValue");
        importMapping.put("JsonCreator", "com.fasterxml.jackson.annotation.JsonCreator");
        importMapping.put("JsonInclude", "com.fasterxml.jackson.annotation.JsonInclude");
        importMapping.put("JsonTypeName", "com.fasterxml.jackson.annotation.JsonTypeName");
        importMapping.put("JsonTypeInfo", "com.fasterxml.jackson.annotation.JsonTypeInfo");
        importMapping.put("JsonSubTypes", "com.fasterxml.jackson.annotation.JsonSubTypes");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "long", "float", "double", "boolean", "byte", "short",
                                "char", "Integer", "Long", "Float", "Double", "Boolean", "String",
                                "Object", "byte[]", "void"));

        instantiationTypes.put("array", "ArrayList");
        instantiationTypes.put("set", "LinkedHashSet");
        instantiationTypes.put("map", "HashMap");

        reservedWords = loadReservedWords("/reserved-words/java.txt");

        setEnablePostProcessFile(true);
        formatter =
                new Formatter(
                        JavaFormatterOptions.builder()
                                .style(JavaFormatterOptions.Style.GOOGLE)
                                .build());
    }

    @Override
    public String getName() {
        return "java-plus";
    }

    @Override
    public String getHelp() {
        return "Generates a minimal Java client with Jackson and Apache HttpClient.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.SOURCE_FOLDER)) {
            sourceFolder = (String) additionalProperties.get(CodegenConstants.SOURCE_FOLDER);
        }
        if (additionalProperties.containsKey(CodegenConstants.INVOKER_PACKAGE)) {
            invokerPackage = (String) additionalProperties.get(CodegenConstants.INVOKER_PACKAGE);
        }
        additionalProperties.put(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
        additionalProperties.put("invokerPackage", invokerPackage);

        supportingFiles.clear();
        setEnablePostProcessFile(true);

        String invokerFolder =
                sourceFolder + File.separator + invokerPackage.replace(".", File.separator);
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.java"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.java"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.java"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.java"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        invokerFolder + File.separator + "api",
                        "BaseApi.java"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.java"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.java"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.java"));
    }

    @Override
    public String modelFileFolder() {
        return outputFolder
                + File.separator
                + sourceFolder
                + File.separator
                + modelPackage().replace('.', File.separatorChar);
    }

    @Override
    public String apiFileFolder() {
        return outputFolder
                + File.separator
                + sourceFolder
                + File.separator
                + apiPackage().replace('.', File.separatorChar);
    }

    @Override
    public String toVarName(String name) {
        name = sanitizeName(name);
        if (name.matches("^[A-Z0-9_]*$")) {
            return name;
        }
        name = StringUtils.camelize(name, LOWERCASE_FIRST_LETTER);
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        return name;
    }

    @Override
    public String toOperationId(String operationId) {
        if (operationId == null || operationId.isEmpty()) {
            throw new RuntimeException("Empty method/operation name (operationId) not allowed");
        }
        return StringUtils.camelize(sanitizeName(operationId), LOWERCASE_FIRST_LETTER);
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            Schema<?> inner = p.getItems();
            return getSchemaType(p) + "<" + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema<?> inner = ModelUtils.getAdditionalProperties(p);
            if (inner == null) {
                return "Map<String, Object>";
            }
            return getSchemaType(p) + "<String, " + getTypeDeclaration(inner) + ">";
        }
        return super.getTypeDeclaration(p);
    }

    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        schema = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (ModelUtils.isArraySchema(schema)) {
            if (Boolean.TRUE.equals(schema.getUniqueItems())) {
                return "new LinkedHashSet<>()";
            }
            return "new ArrayList<>()";
        } else if (ModelUtils.isMapSchema(schema)) {
            return "new HashMap<>()";
        }
        return null;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (!model.isEnum) {
            model.imports.add("JsonProperty");
            model.imports.add("JsonInclude");
            model.imports.add("JsonTypeName");
            if (property.isEnum) {
                model.imports.add("JsonValue");
                model.imports.add("JsonCreator");
            }
            if (property.isContainer) {
                if (property.isArray) {
                    model.imports.add("ArrayList");
                    model.imports.add("Arrays");
                }
                if (property.isMap) {
                    model.imports.add("HashMap");
                }
            }
        }
    }

    @Override
    public ModelsMap postProcessModelsEnum(ModelsMap objs) {
        objs = super.postProcessModelsEnum(objs);
        for (ModelMap modelMap : objs.getModels()) {
            CodegenModel model = modelMap.getModel();
            if (model.isEnum) {
                model.imports.add("JsonValue");
                model.imports.add("JsonCreator");
            }
            for (CodegenProperty property : model.vars) {
                if (property.isEnum) {
                    model.imports.add("JsonValue");
                    model.imports.add("JsonCreator");
                }
            }
        }
        return objs;
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);
        if (file == null || !file.getName().endsWith(".java")) {
            return;
        }
        try {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            source = RemoveUnusedImports.removeUnusedImports(source);
            source =
                    ImportOrderer.reorderImports(source, JavaFormatterOptions.Style.GOOGLE);
            String formatted = formatter.formatSource(source);
            Files.write(file.toPath(), formatted.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.warn("Failed to read/write file for formatting: {}", file.getAbsolutePath(), e);
        } catch (FormatterException e) {
            LOGGER.warn("Failed to format file: {}", file.getAbsolutePath(), e);
        }
    }

    @Override
    public CodegenModel fromModel(String name, Schema schema) {
        CodegenModel model = super.fromModel(name, schema);
        if (model.discriminator != null) {
            model.imports.add("JsonTypeInfo");
            model.imports.add("JsonSubTypes");
        }
        if (!model.oneOf.isEmpty() || !model.anyOf.isEmpty()) {
            model.imports.add("JsonValue");
            model.imports.add("JsonCreator");
        }
        return model;
    }
}

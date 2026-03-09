package io.github.mridang.codegen.generators.node;

import static org.openapitools.codegen.utils.CamelizeOption.LOWERCASE_FIRST_LETTER;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.media.Schema;
import org.openapitools.codegen.utils.StringUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates a TypeScript API client using the Fetch API. */
@SuppressWarnings("unused")
public class BetterNodeCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterNodeCodegen.class);

    public BetterNodeCodegen() {
        outputFolder = "generated-code/typescript";
        embeddedTemplateDir = templateDir = "templates/node";

        modelTemplateFiles.put("models/model.mustache", ".ts");
        apiTemplateFiles.put("api/apis.mustache", ".ts");

        typeMapping.put("integer", "number");
        typeMapping.put("long", "number");
        typeMapping.put("float", "number");
        typeMapping.put("double", "number");
        typeMapping.put("number", "number");
        typeMapping.put("short", "number");
        typeMapping.put("boolean", "boolean");
        typeMapping.put("string", "string");
        typeMapping.put("decimal", "string");
        typeMapping.put("date", "string");
        typeMapping.put("DateTime", "string");
        typeMapping.put("binary", "Blob");
        typeMapping.put("File", "Blob");
        typeMapping.put("file", "Blob");
        typeMapping.put("ByteArray", "string");
        typeMapping.put("UUID", "string");
        typeMapping.put("URI", "string");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "unknown");
        typeMapping.put("array", "Array");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "{ [key: string]: unknown }");
        typeMapping.put("Map", "{ [key: string]: unknown }");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "number", "boolean", "string", "object", "any", "unknown",
                                "void", "undefined", "null", "Array", "Set", "Blob"));

        reservedWords = loadReservedWords("/reserved-words/node.txt");

        additionalProperties.put(CodegenConstants.MODEL_PROPERTY_NAMING, "original");
        additionalProperties.put("importFileExtension", ".js");

        setEnumUnknownDefaultCase(true);

    }

    @Override
    public String getName() {
        return "node-plus";
    }

    @Override
    public String getHelp() {
        return "Generates a minimal TypeScript client using the Fetch API.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        this.apiPackage = "api";

        supportingFiles.add(new SupportingFile("api_client.mustache", "", "api-client.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", "", "default-api-client.ts"));
        supportingFiles.add(new SupportingFile("api_response.mustache", "", "api-response.ts"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", "", "configuration.ts"));
        supportingFiles.add(new SupportingFile("base_api.mustache", "api", "base-api.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", "", "object-serializer.ts"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", "", "header-selector.ts"));
        supportingFiles.add(new SupportingFile("tsconfig.mustache", "", "tsconfig.json"));
        supportingFiles.add(
                new SupportingFile("models/index.mustache", "models", "index.ts"));
        supportingFiles.add(
                new SupportingFile("api/index.mustache", "api", "index.ts"));
        supportingFiles.add(new SupportingFile("package.mustache", "", "package.json"));
        supportingFiles.add(new SupportingFile("prettierrc.mustache", "", ".prettierrc"));
        supportingFiles.add(
                new SupportingFile("eslint_config.mustache", "", "eslint.config.mjs"));
        supportingFiles.add(new SupportingFile("authenticator.mustache", "auth", "authenticator.ts"));
        supportingFiles.add(new SupportingFile("client.mustache", "", "client.ts"));
    }

    @Override
    public String modelFileFolder() {
        return outputFolder + File.separator + "models";
    }

    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + "api";
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            Schema<?> inner = p.getItems();
            return getSchemaType(p) + "<" + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema<?> inner = ModelUtils.getAdditionalProperties(p);
            String valueType = inner == null ? "unknown" : getTypeDeclaration(inner);
            return "{ [key: string]: " + valueType + " }";
        }
        return super.getTypeDeclaration(p);
    }

    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        return null;
    }

    @Override
    public String toVarName(String name) {
        return sanitizeName(name);
    }

    @Override
    public String toParamName(String name) {
        name = sanitizeName(name);
        name = StringUtils.camelize(name, LOWERCASE_FIRST_LETTER);
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        return name;
    }

    @Override
    public String toModelName(String name) {
        name = sanitizeName(name);
        String camelized = StringUtils.camelize(name);
        if (languageSpecificPrimitives.contains(camelized)) {
            return "Model" + camelized;
        }
        return camelized;
    }

    @Override
    protected String applyVarNameCasing(String name) {
        return name;
    }

    @Override
    protected String formatOperationId(String sanitizedOperationId) {
        return StringUtils.camelize(sanitizedOperationId, LOWERCASE_FIRST_LETTER);
    }

    @Override
    protected boolean isNumericEnumDatatype(String datatype) {
        return "number".equals(datatype) || "boolean".equals(datatype);
    }

    @Override
    protected String quoteEnumValue(String value) {
        return "'" + escapeText(value) + "'";
    }

    @Override
    public String toModelFilename(String name) {
        return toKebabCase(toModelName(name));
    }

    @Override
    public String toApiFilename(String name) {
        return toKebabCase(toApiName(name));
    }

    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("'", "\\'");
    }

    private String toKebabCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }

    @Override
    public String toEnumVarName(String value, String datatype) {
        if (enumNameMapping.containsKey(value)) {
            return enumNameMapping.get(value);
        }
        if (value.isEmpty()) {
            return "Empty";
        }
        String symbolName = getSymbolName(value);
        if (symbolName != null) {
            return toPascalCase(symbolName);
        }
        if ("number".equals(datatype) || "boolean".equals(datatype)) {
            String varName = "number".equals(datatype) ? "NUMBER_" + value : value;
            varName =
                    varName.replaceAll("-", "MINUS_")
                            .replaceAll("\\+", "PLUS_")
                            .replaceAll("\\.", "_DOT_");
            return varName;
        }
        return toPascalCase(value);
    }

    private String toPascalCase(String value) {
        if (value.matches("[a-zA-Z0-9]+")) {
            return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        for (String word : value.split("[_\\- ]+")) {
            if (!word.isEmpty()) {
                sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
                if (word.length() > 1) {
                    sb.append(word.substring(1));
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : value;
    }

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        ModelsMap result = super.postProcessModels(objs);

        for (ModelMap modelMap : result.getModels()) {
            CodegenModel model = modelMap.getModel();
            List<Map<String, String>> tsImports = new ArrayList<>();
            for (String importName : model.imports) {
                if (!languageSpecificPrimitives.contains(importName)
                        && !typeMapping.containsValue(importName)) {
                    Map<String, String> tsImport = new HashMap<>();
                    tsImport.put("classname", importName);
                    tsImport.put("filename", toModelFilename(importName));
                    tsImports.add(tsImport);
                }
            }
            modelMap.put("tsImports", tsImports);
            modelMap.put("hasImports", !tsImports.isEmpty());

            boolean hasTypeDecorator = false;
            for (CodegenProperty var : model.vars) {
                if (needsTypeDecorator(var)) {
                    hasTypeDecorator = true;
                    break;
                }
            }
            modelMap.put("hasTypeDecorator", hasTypeDecorator);
        }
        return result;
    }

    private static boolean needsTypeDecorator(CodegenProperty prop) {
        if (!prop.isPrimitiveType
                && !prop.isArray
                && prop.complexType != null
                && !prop.isEnum
                && !prop.isFreeFormObject) {
            return true;
        }
        return prop.isArray
            && prop.items != null
            && !prop.items.isPrimitiveType
            && prop.items.complexType != null
            && !prop.items.isEnum
            && !prop.items.isFreeFormObject;
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        objs = super.postProcessOperationsWithModels(objs, allModels);

        @SuppressWarnings("unchecked")
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        if (operations != null) {
            String classname = (String) operations.get("classname");
            if (classname != null) {
                operations.put("classFilename", toKebabCase(classname));
            }

            @SuppressWarnings("unchecked")
            List<CodegenOperation> ops =
                    (List<CodegenOperation>) operations.get("operation");
            if (ops != null) {
                boolean hasEnums = false;
                for (CodegenOperation op : ops) {
                    for (CodegenParameter param : op.allParams) {
                        if (param.isEnum) {
                            hasEnums = true;
                            String opIdCamelCase =
                                    StringUtils.camelize(
                                            op.operationId);
                            param.datatypeWithEnum =
                                    opIdCamelCase + param.enumName;
                        }
                    }
                }
                objs.put("hasEnums", hasEnums);
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> imports = (List<Map<String, String>>) objs.get("imports");
        if (imports != null) {
            imports.removeIf(
                    imp -> {
                        String importName = imp.get("classname");
                        if (importName == null) {
                            importName = imp.get("import");
                        }
                        return importName == null
                                || languageSpecificPrimitives.contains(importName)
                                || typeMapping.containsValue(importName);
                    });
            for (Map<String, String> imp : imports) {
                if (!imp.containsKey("className") && imp.containsKey("classname")) {
                    imp.put("className", imp.get("classname"));
                }
                if (!imp.containsKey("className") && imp.containsKey("import")) {
                    String className = imp.get("import");
                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf('.') + 1);
                    }
                    imp.put("className", className);
                }
            }
        }
        return objs;
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null || !file.getName().endsWith(".ts")) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> result = new ArrayList<>(lines.size());
            boolean changed = false;

            for (String line : lines) {
                if (line.equals("/* tslint:disable */") || line.equals("/* eslint-disable */")) {
                    changed = true;
                    continue;
                }
                result.add(line);
            }

            while (!result.isEmpty() && result.get(result.size() - 1).trim().isEmpty()) {
                result.remove(result.size() - 1);
                changed = true;
            }

            if (changed) {
                Files.write(file.toPath(), result, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to post-process {}: {}", file.getName(), e.getMessage());
        }
    }

}

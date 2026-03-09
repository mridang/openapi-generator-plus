package io.github.mridang.codegen.generators.python;

import static org.openapitools.codegen.utils.StringUtils.underscore;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;
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
 * A custom Python code generator providing a minimal, modern Python client
 * with pydantic models.
 */
@SuppressWarnings("unused")
public class BetterPythonCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterPythonCodegen.class);

    private static final Map<String, String> TYPE_IMPORTS =
            Map.of(
                    "datetime", "from datetime import datetime",
                    "date", "from datetime import date",
                    "Decimal", "from decimal import Decimal");

    protected String packageName = "openapi_client";
    protected String packageVersion = "1.0.0";

    public BetterPythonCodegen() {
        outputFolder = "generated-code/python";
        embeddedTemplateDir = templateDir = "templates/python";

        modelTemplateFiles.put("models/model.mustache", ".py");
        apiTemplateFiles.put("api/api.mustache", ".py");

        typeMapping.put("integer", "int");
        typeMapping.put("long", "int");
        typeMapping.put("float", "float");
        typeMapping.put("double", "float");
        typeMapping.put("number", "float");
        typeMapping.put("boolean", "bool");
        typeMapping.put("string", "str");
        typeMapping.put("byte", "bytes");
        typeMapping.put("binary", "bytes");
        typeMapping.put("ByteArray", "bytes");
        typeMapping.put("date", "date");
        typeMapping.put("DateTime", "datetime");
        typeMapping.put("UUID", "str");
        typeMapping.put("URI", "str");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "object");
        typeMapping.put("array", "List");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "Dict");
        typeMapping.put("File", "file");
        typeMapping.put("decimal", "float");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "float", "bool", "str", "bytes", "object",
                                "date", "datetime", "file", "List", "Dict", "Set",
                                "Tuple", "Optional"));

        reservedWords = loadReservedWords("/reserved-words/python.txt");

        this.setDisallowAdditionalPropertiesIfNotPresent(false);
        this.setLegacyDiscriminatorBehavior(false);

        setEnablePostProcessFile(true);
    }

    @Override
    public String getName() {
        return "python-plus";
    }

    @Override
    public String getHelp() {
        return "Generates a minimal Python client with pydantic models.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey("packageName")) {
            packageName = (String) additionalProperties.get("packageName");
        }
        additionalProperties.put("packageName", packageName);

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_VERSION)) {
            packageVersion = (String) additionalProperties.get(CodegenConstants.PACKAGE_VERSION);
        }
        additionalProperties.put(CodegenConstants.PACKAGE_VERSION, packageVersion);

        modelPackage = packageName + ".models";
        apiPackage = packageName + ".api";

        supportingFiles.clear();
        setEnablePostProcessFile(true);

        String modelPath = modelPackage.replace('.', File.separatorChar);
        String apiPath = apiPackage.replace('.', File.separatorChar);
        String packagePath = packageName.replace('.', File.separatorChar);

        supportingFiles.add(
                new SupportingFile("models/__init__.mustache", modelPath, "__init__.py"));
        supportingFiles.add(
                new SupportingFile("api/__init__.mustache", apiPath, "__init__.py"));
        supportingFiles.add(
                new SupportingFile("__init__.mustache", packagePath, "__init__.py"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", packagePath, "api_client.py"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", packagePath, "default_api_client.py"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", packagePath, "api_response.py"));
        supportingFiles.add(
                new SupportingFile("base_api.mustache", apiPath, "base_api.py"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", packagePath, "configuration.py"));
        supportingFiles.add(
                new SupportingFile("exceptions.mustache", packagePath, "exceptions.py"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", packagePath, "object_serializer.py"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", packagePath, "header_selector.py"));
        supportingFiles.add(new SupportingFile("requirements.mustache", "", "requirements.txt"));
    }

    @Override
    public String modelFileFolder() {
        return outputFolder
                + File.separator
                + modelPackage().replace('.', File.separatorChar);
    }

    @Override
    public String apiFileFolder() {
        return outputFolder
                + File.separator
                + apiPackage().replace('.', File.separatorChar);
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            Schema<?> inner = p.getItems();
            return getSchemaType(p) + "[" + getTypeDeclaration(inner) + "]";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema<?> inner = ModelUtils.getAdditionalProperties(p);
            if (inner == null) {
                return "Dict[str, object]";
            }
            return getSchemaType(p) + "[str, " + getTypeDeclaration(inner) + "]";
        }
        return super.getTypeDeclaration(p);
    }

    @Override
    public String toModelImport(String name) {
        if (name.startsWith("import") || name.startsWith("from")) {
            return name;
        }
        return "from "
                + modelPackage()
                + "."
                + toModelFilename(name)
                + " import "
                + name;
    }

    @Override
    public String toVarName(String name) {
        name = sanitizeName(name);
        name = underscore(name);
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        return name;
    }

    @Override
    public String toModelFilename(String name) {
        return underscore(toModelName(name));
    }

    @Override
    public String toApiFilename(String name) {
        return underscore(toApiName(name));
    }

    @Override
    public String toOperationId(String operationId) {
        if (operationId == null || operationId.isEmpty()) {
            throw new RuntimeException("Empty method/operation name (operationId) not allowed");
        }
        return underscore(sanitizeName(operationId));
    }

    @Override
    public String sanitizeTag(String tag) {
        return sanitizeName(tag);
    }

    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("'", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("'''", "'_'_'");
    }

    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        if (schema.getDefault() != null) {
            if (ModelUtils.isBooleanSchema(schema)) {
                return Boolean.valueOf(schema.getDefault().toString()) ? "True" : "False";
            }
            return schema.getDefault().toString();
        }
        return null;
    }

    @Override
    public String toEnumValue(String value, String datatype) {
        if ("int".equals(datatype) || "float".equals(datatype)) {
            return value;
        }
        return "'" + value.replace("'", "") + "'";
    }

    @Override
    public String toEnumVarName(String name, String datatype) {
        if ("int".equals(datatype) || "float".equals(datatype)) {
            return name;
        }
        return "'" + name + "'";
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (ModelsMap modelsMap : result.values()) {
            for (ModelMap modelMap : modelsMap.getModels()) {
                CodegenModel model = modelMap.getModel();

                TreeSet<String> fullImports = new TreeSet<>();

                for (CodegenProperty prop : model.allVars) {
                    addTypeImport(fullImports, prop.dataType);
                    if (prop.items != null) {
                        addTypeImport(fullImports, prop.items.dataType);
                    }
                }

                for (String imp : model.imports) {
                    fullImports.add(
                            "from "
                                    + modelPackage
                                    + "."
                                    + toModelFilename(imp)
                                    + " import "
                                    + imp);
                }
                model.imports.clear();
                model.imports.addAll(fullImports);
            }
        }
        return result;
    }

    private static void addTypeImport(TreeSet<String> imports, String dataType) {
        String imp = TYPE_IMPORTS.get(dataType);
        if (imp != null) {
            imports.add(imp);
        }
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);
        if (file == null || !file.getName().endsWith(".py")) {
            return;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String trimmed = content.replaceAll("\\{ ('.*?') }", "{$1}");
            trimmed = trimmed.replaceAll("\\n{4,}", "\n\n\n");
            trimmed = trimmed.replaceAll("\\n+$", "\n");
            if (!trimmed.equals(content)) {
                Files.write(file.toPath(), trimmed.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to post-process file: {}", file.getAbsolutePath(), e);
        }
    }
}

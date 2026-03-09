package io.github.mridang.codegen.generators.php;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.openapitools.codegen.utils.CamelizeOption.LOWERCASE_FIRST_LETTER;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.StringUtils;

/**
 * A custom PHP code generator providing a minimal, modern PHP client
 * with Guzzle and Symfony Serializer.
 */
@SuppressWarnings("unused")
public class BetterPHPCodegen extends AbstractBetterCodegen {

    protected String invokerPackage = "OpenAPI\\Client";
    protected String srcBasePath = "lib";
    protected String apiDirName = "Api";
    protected String modelDirName = "Models";

    public BetterPHPCodegen() {
        outputFolder = "generated-code/php";
        embeddedTemplateDir = templateDir = "templates/php";

        modelTemplateFiles.put("models/model.mustache", ".php");
        apiTemplateFiles.put("api/api.mustache", ".php");

        typeMapping.put("integer", "int");
        typeMapping.put("long", "int");
        typeMapping.put("float", "float");
        typeMapping.put("double", "float");
        typeMapping.put("number", "float");
        typeMapping.put("decimal", "float");
        typeMapping.put("boolean", "bool");
        typeMapping.put("string", "string");
        typeMapping.put("byte", "int");
        typeMapping.put("binary", "string");
        typeMapping.put("ByteArray", "string");
        typeMapping.put("date", "\\DateTime");
        typeMapping.put("Date", "\\DateTime");
        typeMapping.put("DateTime", "\\DateTime");
        typeMapping.put("UUID", "string");
        typeMapping.put("URI", "string");
        typeMapping.put("object", "object");
        typeMapping.put("AnyType", "mixed");
        typeMapping.put("array", "array");
        typeMapping.put("map", "array");
        typeMapping.put("list", "array");
        typeMapping.put("file", "\\SplFileObject");
        typeMapping.put("File", "\\SplFileObject");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int",
                                "integer",
                                "float",
                                "string",
                                "bool",
                                "boolean",
                                "object",
                                "array",
                                "mixed",
                                "void",
                                "null",
                                "byte",
                                "number",
                                "\\DateTime",
                                "\\SplFileObject"));

        instantiationTypes.put("array", "array");
        instantiationTypes.put("map", "array");

        reservedWords = loadReservedWords("/reserved-words/php.txt");
    }

    @Override
    public String getName() {
        return "php-plus";
    }

    @Override
    public String getHelp() {
        return "Generates a minimal PHP client with Guzzle and Symfony Serializer.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.INVOKER_PACKAGE)) {
            invokerPackage = (String) additionalProperties.get(CodegenConstants.INVOKER_PACKAGE);
        }
        additionalProperties.put(CodegenConstants.INVOKER_PACKAGE, invokerPackage);
        additionalProperties.put("invokerPackage", invokerPackage);

        apiPackage = invokerPackage + "\\" + apiDirName;
        modelPackage = invokerPackage + "\\" + modelDirName;

        if (additionalProperties.containsKey(CodegenConstants.MODEL_PACKAGE)) {
            modelPackage =
                    invokerPackage
                            + "\\"
                            + (String) additionalProperties.get(CodegenConstants.MODEL_PACKAGE);
        }
        additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);

        if (additionalProperties.containsKey(CodegenConstants.API_PACKAGE)) {
            apiPackage =
                    invokerPackage
                            + "\\"
                            + (String) additionalProperties.get(CodegenConstants.API_PACKAGE);
        }
        additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);

        additionalProperties.put("escapedInvokerPackage", invokerPackage.replace("\\", "\\\\"));

        supportingFiles.clear();

        String invokerFolder = toSrcPath(invokerPackage);
        String apiFolder = toSrcPath(apiPackage);

        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.php"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.php"));
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.php"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.php"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.php"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.php"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.php"));
        supportingFiles.add(
                new SupportingFile("base_api.mustache", apiFolder, "BaseApi.php"));
        supportingFiles.add(new SupportingFile("composer.mustache", "", "composer.json"));
    }

    private String toSrcPath(String packageName) {
        String relative = packageName.replace(invokerPackage, "");
        String packagePath = relative.replaceAll("[\\\\/.]", "/");
        if (packagePath.startsWith("/")) {
            packagePath = packagePath.substring(1);
        }
        if (srcBasePath != null && !srcBasePath.isEmpty()) {
            String base = srcBasePath.replaceAll("[\\\\/]$", "");
            if (packagePath.isEmpty()) {
                return base;
            }
            return base + File.separator + packagePath;
        }
        return packagePath;
    }

    @Override
    public String modelFileFolder() {
        return outputFolder + File.separator + toSrcPath(modelPackage);
    }

    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + toSrcPath(apiPackage);
    }

    @Override
    public String getSchemaType(Schema schema) {
        String type = super.getSchemaType(schema);

        if (type == null) {
            return "UNKNOWN_OPENAPI_TYPE";
        }

        if ((schema.getAnyOf() != null && !schema.getAnyOf().isEmpty())
                || (schema.getOneOf() != null && !schema.getOneOf().isEmpty())) {
            return type;
        }

        if (languageSpecificPrimitives.contains(type)) {
            return type;
        }

        return toModelName(type);
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            Schema<?> inner = ModelUtils.getSchemaItems(p);
            if (inner == null) {
                return "string[]";
            }
            return getTypeDeclaration(inner) + "[]";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema<?> inner = ModelUtils.getAdditionalProperties(p);
            if (inner == null) {
                return "array<string,string>";
            }
            return getSchemaType(p) + "<string," + getTypeDeclaration(inner) + ">";
        } else if (isNotBlank(p.get$ref())) {
            String type = super.getTypeDeclaration(p);
            if (!languageSpecificPrimitives.contains(type)) {
                return "\\" + modelPackage + "\\" + toModelName(type);
            }
            return type;
        }
        return super.getTypeDeclaration(p);
    }

    @Override
    public String getTypeDeclaration(String name) {
        if (!languageSpecificPrimitives.contains(name)) {
            return "\\" + modelPackage + "\\" + name;
        }
        return super.getTypeDeclaration(name);
    }

    @Override
    public String toVarName(String name) {
        name = sanitizeName(name);
        name =
                StringUtils.camelize(name, LOWERCASE_FIRST_LETTER);
        if (name.matches("^\\d.*")) {
            name = "_" + name;
        }
        if (isReservedWord(name)) {
            name = escapeReservedWord(name);
        }
        return name;
    }

    @Override
    public String toModelName(String name) {
        name = sanitizeName(name);
        name = name.replaceAll("\\]", "");
        name = name.replaceAll("[^\\w\\\\]+", "_");
        name = name.replace("$", "");

        if (isReservedWord(name)) {
            name = "model_" + name;
        }
        if (name.matches("^\\d.*")) {
            name = "model_" + name;
        }

        return StringUtils.camelize(name);
    }

    @Override
    public String toOperationId(String operationId) {
        if (operationId == null || operationId.isEmpty()) {
            throw new RuntimeException("Empty method/operation name (operationId) not allowed");
        }
        if (isReservedWord(operationId)) {
            operationId = "call_" + operationId;
        }
        if (operationId.matches("^\\d.*")) {
            operationId = "call_" + operationId;
        }
        return StringUtils.camelize(sanitizeName(operationId), LOWERCASE_FIRST_LETTER);
    }

    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        schema = ModelUtils.unaliasSchema(this.openAPI, schema);
        if (ModelUtils.isBooleanSchema(schema)) {
            if (schema.getDefault() != null) {
                return schema.getDefault().toString();
            }
        } else if (ModelUtils.isNumberSchema(schema)) {
            if (schema.getDefault() != null) {
                return schema.getDefault().toString();
            }
        } else if (ModelUtils.isIntegerSchema(schema)) {
            if (schema.getDefault() != null) {
                return schema.getDefault().toString();
            }
        } else if (ModelUtils.isStringSchema(schema)) {
            if (schema.getDefault() != null) {
                return "'" + schema.getDefault() + "'";
            }
        }
        return null;
    }

    @Override
    public String toEnumValue(String value, String datatype) {
        if ("int".equals(datatype) || "float".equals(datatype)) {
            return value;
        }
        return "'" + escapeTextInSingleQuotes(value) + "'";
    }

    @Override
    public String toEnumVarName(String name, String datatype) {
        if (name.isEmpty()) {
            return "EMPTY";
        }
        if (name.trim().isEmpty()) {
            return "SPACE_" + name.length();
        }
        if (getSymbolName(name) != null) {
            return getSymbolName(name).toUpperCase(Locale.ROOT);
        }
        if ("int".equals(datatype) || "float".equals(datatype)) {
            if (name.matches("\\d.*")) {
                name = "NUMBER_" + name;
            }
            name = name.replaceAll("-", "MINUS_");
            name = name.replaceAll("\\+", "PLUS_");
            name = name.replaceAll("\\.", "_DOT_");
        }

        String enumName =
                sanitizeName(
                                StringUtils.underscore(name)
                                        .toUpperCase(Locale.ROOT))
                        .replaceFirst("^_", "")
                        .replaceFirst("_$", "");

        if (isReservedWord(enumName) || enumName.matches("\\d.*")) {
            return escapeReservedWord(enumName);
        }
        return enumName;
    }

    @Override
    public String toEnumName(CodegenProperty property) {
        String name = property.name;
        name = name.replaceAll("\\]", "");
        name = name.replaceAll("[^\\w\\\\]+", "_");
        name = name.replace("$", "");

        String enumName =
                StringUtils.underscore(name)
                        .toUpperCase(Locale.ROOT);

        enumName = enumName.replace("[]", "");

        if (enumName.matches("\\d.*")) {
            return "_" + enumName;
        }
        return enumName;
    }

    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("'", "");
    }

    @Override
    public String escapeText(String input) {
        if (input == null) {
            return input;
        }
        if (input.trim().isEmpty()) {
            return input;
        }
        return super.escapeText(input).trim();
    }
}

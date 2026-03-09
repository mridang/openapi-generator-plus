package io.github.mridang.codegen.generators.csharp;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.StringUtils;

/**
 * A custom C# code generator providing a minimal, modern C# client
 * with System.Text.Json.
 */
@SuppressWarnings("unused")
public class BetterCSharpCodegen extends AbstractBetterCodegen {

    protected String sourceFolder = "src";
    protected String packageName = "OpenApi";

    public BetterCSharpCodegen() {
        outputFolder = "generated-code/csharp";
        embeddedTemplateDir = templateDir = "templates/csharp";

        modelTemplateFiles.put("models/model.mustache", ".cs");
        apiTemplateFiles.put("api/api.mustache", ".cs");

        typeMapping.put("integer", "int");
        typeMapping.put("long", "long");
        typeMapping.put("float", "float");
        typeMapping.put("double", "double");
        typeMapping.put("number", "decimal");
        typeMapping.put("decimal", "decimal");
        typeMapping.put("boolean", "bool");
        typeMapping.put("string", "string");
        typeMapping.put("byte", "byte[]");
        typeMapping.put("binary", "byte[]");
        typeMapping.put("ByteArray", "byte[]");
        typeMapping.put("date", "DateOnly");
        typeMapping.put("DateTime", "DateTimeOffset");
        typeMapping.put("date-time", "DateTimeOffset");
        typeMapping.put("UUID", "Guid");
        typeMapping.put("URI", "string");
        typeMapping.put("object", "Object");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("array", "List");
        typeMapping.put("map", "Dictionary");
        typeMapping.put("File", "System.IO.Stream");
        typeMapping.put("file", "System.IO.Stream");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "long", "float", "double", "decimal", "bool", "string",
                                "byte[]", "void", "Object", "DateOnly", "DateTimeOffset", "Guid"));

        instantiationTypes.put("array", "List");
        instantiationTypes.put("map", "Dictionary");

        reservedWords = loadReservedWords("/reserved-words/csharp.txt");
    }

    @Override
    public String getName() {
        return "csharp-plus";
    }

    @Override
    public String getHelp() {
        return "Generates a minimal C# client with System.Text.Json.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.SOURCE_FOLDER)) {
            sourceFolder = (String) additionalProperties.get(CodegenConstants.SOURCE_FOLDER);
        }
        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_NAME)) {
            packageName = (String) additionalProperties.get(CodegenConstants.PACKAGE_NAME);
        }
        additionalProperties.put(CodegenConstants.PACKAGE_NAME, packageName);
        additionalProperties.put("packageName", packageName);

        modelPackage = "Models";
        apiPackage = "Api";

        supportingFiles.clear();

        String invokerFolder =
                sourceFolder + File.separator + packageName.replace(".", File.separator);

        supportingFiles.add(
                new SupportingFile("api_client.mustache", invokerFolder, "ApiClient.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", invokerFolder, "DefaultApiClient.cs"));
        supportingFiles.add(
                new SupportingFile("api_exception.mustache", invokerFolder, "ApiException.cs"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", invokerFolder, "ApiResponse.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        invokerFolder + File.separator + apiPackage,
                        "BaseApi.cs"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", invokerFolder, "Configuration.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", invokerFolder, "ObjectSerializer.cs"));
        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", invokerFolder, "HeaderSelector.cs"));
        supportingFiles.add(
                new SupportingFile("csproj.mustache", invokerFolder, packageName + ".csproj"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));
    }

    @Override
    public String modelFileFolder() {
        return outputFolder
                + File.separator
                + sourceFolder
                + File.separator
                + packageName.replace(".", File.separator)
                + File.separator
                + modelPackage;
    }

    @Override
    public String apiFileFolder() {
        return outputFolder
                + File.separator
                + sourceFolder
                + File.separator
                + packageName.replace(".", File.separator)
                + File.separator
                + apiPackage;
    }

    @Override
    public String toVarName(String name) {
        name = sanitizeName(name);
        name = StringUtils.camelize(name);
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        return name;
    }

    @Override
    public String toParamName(String name) {
        name = sanitizeName(name);
        name = StringUtils.camelize(name);
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }
        if (!name.isEmpty()) {
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    @Override
    public String toOperationId(String operationId) {
        if (operationId == null || operationId.isEmpty()) {
            throw new RuntimeException("Empty method/operation name (operationId) not allowed");
        }
        return StringUtils.camelize(sanitizeName(operationId));
    }

    @Override
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            Schema<?> inner = p.getItems();
            return getSchemaType(p) + "<" + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema<?> inner = ModelUtils.getAdditionalProperties(p);
            if (inner == null) {
                return "Dictionary<string, Object>";
            }
            return getSchemaType(p) + "<string, " + getTypeDeclaration(inner) + ">";
        }
        return super.getTypeDeclaration(p);
    }

    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        return null;
    }

    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value.isEmpty()) {
            return "Empty";
        }

        if (datatype.startsWith("int")
                || datatype.startsWith("uint")
                || datatype.startsWith("long")
                || datatype.startsWith("ulong")
                || datatype.startsWith("double")
                || datatype.startsWith("float")) {
            String varName = "NUMBER_" + value;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return varName;
        }

        String var = value.replaceAll(" ", "_");
        var = StringUtils.camelize(var);
        var = var.replaceAll("\\W+", "");

        if (var.matches("\\d.*")) {
            return "_" + var;
        }
        return var;
    }

    @Override
    public String toEnumValue(String value, String datatype) {
        if (datatype.startsWith("int")
                || datatype.startsWith("uint")
                || datatype.startsWith("long")
                || datatype.startsWith("ulong")
                || datatype.startsWith("byte")) {
            return value;
        }
        return value.replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replaceAll("(?<!\\\\)\"", "\\\\\"");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input;
    }

    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("\"", "\\\"");
    }
}

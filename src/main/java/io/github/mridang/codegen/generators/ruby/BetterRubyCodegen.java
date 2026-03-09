package io.github.mridang.codegen.generators.ruby;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.underscore;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.media.Schema;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import javax.annotation.Nullable;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.utils.ModelUtils;

/**
 * A custom Ruby code generator providing a minimal, modern Ruby client
 * using the Typhoeus HTTP library.
 */
@SuppressWarnings("unused")
public class BetterRubyCodegen extends AbstractBetterCodegen {

    @Nullable protected String gemName;
    protected String moduleName = "Opigen::Client";
    protected String gemVersion = "1.0.0";
    protected String libFolder = "lib";

    public BetterRubyCodegen() {
        outputFolder = "generated-code" + File.separator + "ruby";
        embeddedTemplateDir = templateDir = "templates/ruby";

        modelTemplateFiles.put("models/model.mustache", ".rb");
        apiTemplateFiles.put("api/api.mustache", ".rb");

        modelPackage = "models";
        apiPackage = "api";

        typeMapping.put("string", "String");
        typeMapping.put("boolean", "Boolean");
        typeMapping.put("char", "String");
        typeMapping.put("int", "Integer");
        typeMapping.put("integer", "Integer");
        typeMapping.put("long", "Integer");
        typeMapping.put("short", "Integer");
        typeMapping.put("float", "Float");
        typeMapping.put("double", "Float");
        typeMapping.put("number", "Float");
        typeMapping.put("decimal", "Float");
        typeMapping.put("date", "Date");
        typeMapping.put("DateTime", "Time");
        typeMapping.put("array", "Array");
        typeMapping.put("set", "Array");
        typeMapping.put("List", "Array");
        typeMapping.put("map", "Hash");
        typeMapping.put("object", "Object");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("file", "File");
        typeMapping.put("File", "File");
        typeMapping.put("binary", "String");
        typeMapping.put("ByteArray", "String");
        typeMapping.put("UUID", "String");
        typeMapping.put("URI", "String");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "String", "Boolean", "Integer", "Float", "Date", "Time",
                                "Array", "Hash", "File", "Object"));

        instantiationTypes.put("map", "Hash");
        instantiationTypes.put("array", "Array");
        instantiationTypes.put("set", "Set");

        reservedWords = loadReservedWords("/reserved-words/ruby.txt");

        hideGenerationTimestamp = Boolean.TRUE;
    }

    @Override
    public String getName() {
        return "ruby-plus";
    }

    @Override
    public String getHelp() {
        return "Generates a minimal Ruby client with Typhoeus.";
    }

    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.RUBY;
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.MODULE_NAME)) {
            moduleName = (String) additionalProperties.get(CodegenConstants.MODULE_NAME);
        }
        if (additionalProperties.containsKey(CodegenConstants.GEM_NAME)) {
            gemName = (String) additionalProperties.get(CodegenConstants.GEM_NAME);
        }

        if (gemName == null) {
            gemName = underscore(moduleName.replaceAll("[^\\w]+", ""));
        }

        additionalProperties.put(CodegenConstants.GEM_NAME, gemName);
        additionalProperties.put(CodegenConstants.MODULE_NAME, moduleName);
        additionalProperties.put("gemVersion", gemVersion);

        setModelPackage("models");
        setApiPackage("api");

        supportingFiles.clear();

        String modulePath = underscore(moduleName.replaceAll("::", "/"));
        String libPath = libFolder + File.separator + modulePath;

        supportingFiles.add(new SupportingFile("gem.mustache", libFolder, gemName + ".rb"));
        supportingFiles.add(new SupportingFile("configuration.mustache", libPath, "configuration.rb"));
        supportingFiles.add(new SupportingFile("api_error.mustache", libPath, "api_error.rb"));
        supportingFiles.add(new SupportingFile("version.mustache", libPath, "version.rb"));
        supportingFiles.add(new SupportingFile("header_selector.mustache", libPath, "header_selector.rb"));
        supportingFiles.add(new SupportingFile("object_serializer.mustache", libPath, "object_serializer.rb"));
        supportingFiles.add(new SupportingFile("api_response.mustache", libPath, "api_response.rb"));
        supportingFiles.add(new SupportingFile("api_client.mustache", libPath, "api_client.rb"));
        supportingFiles.add(new SupportingFile("default_api_client.mustache", libPath, "default_api_client.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache", libPath + File.separator + "api", "base_api.rb"));
    }

    @Override
    public String getTypeDeclaration(Schema schema) {
        if (ModelUtils.isArraySchema(schema)) {
            Schema<?> inner = ModelUtils.getSchemaItems(schema);
            return getSchemaType(schema) + "<" + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isMapSchema(schema)) {
            Schema<?> inner = ModelUtils.getAdditionalProperties(schema);
            if (inner == null) {
                return "Hash<String, Object>";
            }
            return getSchemaType(schema) + "<String, " + getTypeDeclaration(inner) + ">";
        }
        return super.getTypeDeclaration(schema);
    }

    @Nullable
    @Override
    public String toDefaultValue(Schema schema) {
        schema = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (ModelUtils.isIntegerSchema(schema)
                || ModelUtils.isNumberSchema(schema)
                || ModelUtils.isBooleanSchema(schema)) {
            if (schema.getDefault() != null) {
                return schema.getDefault().toString();
            }
        } else if (ModelUtils.isStringSchema(schema)) {
            if (schema.getDefault() != null) {
                return "'" + escapeText(String.valueOf(schema.getDefault())) + "'";
            }
        }
        return null;
    }

    @Override
    public String toVarName(String name) {
        String varName = sanitizeName(name);
        if (name.matches("^[A-Z_]*$")) {
            varName = varName.toLowerCase(Locale.ROOT);
        }
        varName = underscore(varName);
        if (isReservedWord(varName) || varName.matches("^\\d.*")) {
            varName = escapeReservedWord(varName);
        }
        return varName;
    }

    @Override
    public String toModelName(String name) {
        name = sanitizeName(name);
        if (isReservedWord(name)) {
            name = "Model" + name;
        }
        if (name.matches("^\\d.*")) {
            name = "model_" + name;
        }
        return camelize(name);
    }

    @Override
    public String toModelFilename(String name) {
        return toZeitwerkFilename(toModelName(name));
    }

    @Override
    public String toApiFilename(String name) {
        return toZeitwerkFilename(toApiName(name));
    }

    @Override
    public String toOperationId(String operationId) {
        if (isEmpty(operationId)) {
            throw new RuntimeException("Empty method/operation name (operationId) not allowed");
        }
        if (isReservedWord(operationId)) {
            return underscore("call_" + operationId);
        }
        return underscore(sanitizeName(operationId));
    }

    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("'", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("=end", "=_end").replace("=begin", "=_begin").replace("#{", "\\#{");
    }

    @Override
    public String toEnumValue(String value, String datatype) {
        if ("Integer".equals(datatype) || "Float".equals(datatype)) {
            return value;
        }
        return "\"" + escapeText(value) + "\"";
    }

    @Override
    public String toEnumVarName(String name, String datatype) {
        if (name.isEmpty()) {
            return "EMPTY";
        }
        if ("Integer".equals(datatype) || "Float".equals(datatype)) {
            String varName = name;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return "N" + varName;
        }
        String enumName = sanitizeName(underscore(name).toUpperCase(Locale.ROOT));
        enumName = enumName.replaceFirst("^_", "");
        enumName = enumName.replaceFirst("_$", "");
        if (enumName.matches("\\d.*")) {
            return "N" + enumName;
        }
        return enumName;
    }

    @Override
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public String modelFileFolder() {
        String path = moduleName.replaceAll("::", "/");
        return Paths.get(
                        getOutputDir(),
                        libFolder,
                        underscore(path),
                        modelPackage().replace(".", File.separator))
                .toString();
    }

    @Override
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public String apiFileFolder() {
        String path = moduleName.replaceAll("::", "/");
        return Paths.get(
                        getOutputDir(),
                        libFolder,
                        underscore(path),
                        apiPackage().replace(".", File.separator))
                .toString();
    }

    private String toZeitwerkFilename(String name) {
        if (isBlank(name)) {
            return name;
        }
        String result = name.replaceAll("([A-Z])", "_$1").replaceAll("^_", "");
        return result.toLowerCase(Locale.ROOT);
    }
}

package io.github.mridang.codegen.generators.ruby;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.openapitools.codegen.utils.StringUtils.camelize;
import static org.openapitools.codegen.utils.StringUtils.underscore;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
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
import org.openapitools.codegen.GeneratorLanguage;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates a Ruby API client using Faraday for HTTP and Dry::Struct for models. */
@SuppressWarnings("unused")
public class BetterRubyCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterRubyCodegen.class);

    private static final String GEM_VERSION = "1.0.0";
    private static final String LIB_FOLDER = "lib";

    @Nullable protected String gemName;
    protected String moduleName = "Opigen::Client";

    public BetterRubyCodegen() {
        outputFolder = "generated-code" + File.separator + "ruby";
        embeddedTemplateDir = templateDir = "templates/ruby";

        modelTemplateFiles.put("models/model.mustache", ".rb");
        modelTemplateFiles.put("models/model_rbs.mustache", ".rbs");
        apiTemplateFiles.put("api/api.mustache", ".rb");
        apiTemplateFiles.put("api/api_rbs.mustache", ".rbs");

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
    }

    @Override
    public String getName() {
        return "ruby-plus";
    }

    @Override
    public String getHelp() {
        return "Generates a minimal Ruby client with Faraday.";
    }

    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.RUBY;
    }

    @Override
    public void processOpts() {
        super.processOpts();

        moduleName = getPropertyOrDefault(CodegenConstants.MODULE_NAME, moduleName);
        if (additionalProperties.containsKey(CodegenConstants.GEM_NAME)) {
            gemName = (String) additionalProperties.get(CodegenConstants.GEM_NAME);
        }
        if (gemName == null) {
            gemName = underscore(moduleName.replaceAll("[^\\w]+", ""));
        }
        additionalProperties.put(CodegenConstants.GEM_NAME, gemName);
        additionalProperties.put("gemVersion", GEM_VERSION);

        setModelPackage("models");
        setApiPackage("api");

        String modulePath = underscore(moduleName.replaceAll("::", "/"));
        String libPath = LIB_FOLDER + File.separator + modulePath;

        supportingFiles.add(new SupportingFile("gem.mustache", LIB_FOLDER, gemName + ".rb"));
        supportingFiles.add(new SupportingFile("configuration.mustache", libPath, "configuration.rb"));
        supportingFiles.add(new SupportingFile("transport_options.mustache", libPath, "transport_options.rb"));
        supportingFiles.add(new SupportingFile("server_configuration.mustache", libPath, "server_configuration.rb"));
        supportingFiles.add(new SupportingFile("servers.mustache", libPath, "servers.rb"));
        supportingFiles.add(new SupportingFile("api_error.mustache", libPath, "api_error.rb"));

        String errorsPath = libPath + File.separator + "errors";
        supportingFiles.add(
                new SupportingFile(
                        "errors/client_error.mustache", errorsPath, "client_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/server_error.mustache", errorsPath, "server_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/bad_request_error.mustache",
                        errorsPath,
                        "bad_request_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unauthorized_error.mustache",
                        errorsPath,
                        "unauthorized_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/forbidden_error.mustache", errorsPath, "forbidden_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/not_found_error.mustache", errorsPath, "not_found_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/conflict_error.mustache", errorsPath, "conflict_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unprocessable_entity_error.mustache",
                        errorsPath,
                        "unprocessable_entity_error.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/internal_server_error.mustache",
                        errorsPath,
                        "internal_server_error.rb"));
        supportingFiles.add(new SupportingFile("version.mustache", libPath, "version.rb"));
        supportingFiles.add(new SupportingFile("header_selector.mustache", libPath, "header_selector.rb"));
        supportingFiles.add(new SupportingFile("object_serializer.mustache", libPath, "object_serializer.rb"));
        supportingFiles.add(new SupportingFile("value_serializer.mustache", libPath, "value_serializer.rb"));
        supportingFiles.add(new SupportingFile("trace_context_util.mustache", libPath, "trace_context_util.rb"));
        supportingFiles.add(new SupportingFile("api_response.mustache", libPath, "api_response.rb"));
        supportingFiles.add(new SupportingFile("api_client.mustache", libPath, "api_client.rb"));
        supportingFiles.add(new SupportingFile("default_api_client.mustache", libPath, "default_api_client.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache", libPath + File.separator + "api", "base_api.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "authenticator.mustache",
                        libPath + File.separator + "auth",
                        "authenticator.rb"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        libPath + File.separator + "auth",
                        "http_aware_authenticator.rb"));
        supportingFiles.add(new SupportingFile("client.mustache", libPath, "client.rb"));
        supportingFiles.add(new SupportingFile("gemfile.mustache", "", "Gemfile"));
        supportingFiles.add(new SupportingFile("rubocop.mustache", "", ".rubocop.yml"));
        supportingFiles.add(new SupportingFile("steepfile.mustache", "", "Steepfile"));
        supportingFiles.add(new SupportingFile("vendor_rbs.mustache", "sig", "vendor.rbs"));
        supportingFiles.add(
                new SupportingFile("infrastructure_rbs.mustache", "sig", "infrastructure.rbs"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("rakefile.mustache", "", "Rakefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
            supportingFiles.add(
                    new SupportingFile("test/spec_helper.mustache", "spec", "spec_helper.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/pet_api_spec.mustache",
                            "spec" + File.separator + "Api",
                            "pet_api_spec.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/Api/store_api_spec.mustache",
                            "spec" + File.separator + "Api",
                            "store_api_spec.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_spec.mustache",
                            "spec",
                            "default_api_client_spec.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_spec.mustache",
                            "spec",
                            "default_api_client_unit_spec.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_spec.mustache",
                            "spec",
                            "transport_options_spec.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_spec.mustache",
                            "spec",
                            "header_selector_spec.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_spec.mustache",
                            "spec",
                            "object_serializer_spec.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_spec.mustache",
                            "spec",
                            "value_serializer_spec.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_spec.mustache",
                            "spec",
                            "trace_context_util_spec.rb"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_spec.mustache",
                            "spec",
                            "base_api_spec.rb"));
        }
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
    protected String applyVarNameCasing(String name) {
        if (name.matches("^[A-Z_]*$")) {
            name = name.toLowerCase(Locale.ROOT);
        }
        return underscore(name);
    }

    @Override
    protected String formatOperationId(String sanitizedOperationId) {
        if (isReservedWord(sanitizedOperationId)) {
            return underscore("call_" + sanitizedOperationId);
        }
        return underscore(sanitizedOperationId);
    }

    @Override
    protected boolean isNumericEnumDatatype(String datatype) {
        return "Integer".equals(datatype) || "Float".equals(datatype);
    }

    @Override
    protected String deriveClientPropertyName(String apiClassName) {
        String name = apiClassName.replaceAll("Api$", "");
        if (name.isEmpty()) {
            return "api";
        }
        return underscore(name);
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
    public String escapeQuotationMark(String input) {
        return input.replace("'", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("=end", "=_end").replace("=begin", "=_begin").replace("#{", "\\#{");
    }

    @Override
    @SuppressFBWarnings("IMPROPER_UNICODE")
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
                        LIB_FOLDER,
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
                        LIB_FOLDER,
                        underscore(path),
                        apiPackage().replace(".", File.separator))
                .toString();
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (ModelsMap models : result.values()) {
            for (ModelMap modelMap : models.getModels()) {
                CodegenModel model = modelMap.getModel();
                for (CodegenProperty var : model.vars) {
                    String rbsType = toRbsType(var.dataType);
                    var.vendorExtensions.put("x-rbs-type", var.required ? rbsType : rbsType + "?");
                }
            }
        }
        return result;
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);
        for (CodegenOperation op : result.getOperations().getOperation()) {
            addRbsTypeToParams(op.allParams);
            addRbsTypeToParams(op.requiredParams);
            addRbsTypeToParams(op.optionalParams);
        }
        return result;
    }

    private void addRbsTypeToParams(List<CodegenParameter> params) {
        for (CodegenParameter param : params) {
            param.vendorExtensions.put("x-rbs-type", toRbsApiType(param.dataType));
        }
    }

    private String toRbsApiType(@Nullable String type) {
        if (type == null) {
            return "void";
        }
        return qualifyModelTypes(type)
                .replace("Boolean", "bool")
                .replace("Object", "untyped")
                .replace("<", "[")
                .replace(">", "]");
    }

    private String qualifyModelTypes(String type) {
        StringBuilder result = new StringBuilder();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < type.length(); i++) {
            char c = type.charAt(i);
            if (c == '<' || c == '>' || c == ',' || c == ' ') {
                if (token.length() > 0) {
                    result.append(qualifySingleType(token.toString()));
                    token.setLength(0);
                }
                result.append(c);
            } else {
                token.append(c);
            }
        }
        if (token.length() > 0) {
            result.append(qualifySingleType(token.toString()));
        }
        return result.toString();
    }

    private String qualifySingleType(String type) {
        if (languageSpecificPrimitives.contains(type)) {
            return type;
        }
        return "Models::" + type;
    }

    private String toRbsType(@Nullable String type) {
        if (type == null) {
            return "void";
        }
        return type.replace("Boolean", "bool")
                .replace("Object", "untyped")
                .replace("<", "[")
                .replace(">", "]");
    }

    private String toZeitwerkFilename(String name) {
        if (isBlank(name)) {
            return name;
        }
        String result = name.replaceAll("([A-Z])", "_$1").replaceAll("^_", "");
        return result.toLowerCase(Locale.ROOT);
    }

    @Override
    protected void registerAuthSupportingFiles() {
        String modulePath = org.openapitools.codegen.utils.StringUtils.underscore(moduleName.replaceAll("::", "/"));
        String libPath = LIB_FOLDER + File.separator + modulePath;
        String authPath = libPath + File.separator + "auth";
        String oauthPath = authPath + File.separator + "oauth";

        if (hasBasicAuth) {
            supportingFiles.add(new SupportingFile("auth/basic_authenticator.mustache", authPath, "basic_authenticator.rb"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(new SupportingFile("auth/bearer_authenticator.mustache", authPath, "bearer_authenticator.rb"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(new SupportingFile("auth/api_key_authenticator.mustache", authPath, "api_key_authenticator.rb"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_token_manager.mustache", oauthPath, "oauth2_token_manager.rb"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_client_credentials_authenticator.mustache", oauthPath, "oauth2_client_credentials_authenticator.rb"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_password_authenticator.mustache", oauthPath, "oauth2_password_authenticator.rb"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_auth_code_authenticator.mustache", oauthPath, "oauth2_auth_code_authenticator.rb"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_implicit_authenticator.mustache", oauthPath, "oauth2_implicit_authenticator.rb"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/openid_connect_authenticator.mustache", oauthPath, "openid_connect_authenticator.rb"));
        }
    }

    @Override
    protected void generatePerSchemeAuthenticators(OpenAPI openAPI) {
        // Per-scheme authenticators are not generated for Ruby
    }

    @Override
    @SuppressFBWarnings("PATH_TRAVERSAL_IN")
    public void postProcessFile(File file, String fileType) {
        if (file == null || !file.getName().endsWith(".rbs")) {
            return;
        }

        Path filePath = file.toPath();
        Path outputDir = Paths.get(getOutputDir());
        Path relative = outputDir.relativize(filePath);
        String relStr = relative.toString();

        if (relStr.startsWith(LIB_FOLDER + File.separator)) {
            Path sigPath = outputDir.resolve("sig").resolve(relStr.substring(LIB_FOLDER.length() + 1));
            Path sigParent = sigPath.getParent();
            if (sigParent == null) {
                return;
            }
            try {
                Files.createDirectories(sigParent);
                Files.move(filePath, sigPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("Failed to move RBS file {} to {}: {}", filePath, sigPath, e.getMessage());
            }
        }
    }
}

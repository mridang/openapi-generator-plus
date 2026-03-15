package io.github.mridang.codegen.generators.python;

import static org.openapitools.codegen.utils.StringUtils.underscore;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates a Python API client using urllib3 for HTTP and Pydantic for models. */
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
        typeMapping.put("file", "bytes");
        typeMapping.put("File", "bytes");
        typeMapping.put("decimal", "float");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "int", "float", "bool", "str", "bytes", "object",
                                "date", "datetime", "List", "Dict", "Set",
                                "Tuple", "Optional"));

        reservedWords = loadReservedWords("/reserved-words/python.txt");

        this.setDisallowAdditionalPropertiesIfNotPresent(false);
        this.setLegacyDiscriminatorBehavior(false);
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

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion =
                getPropertyOrDefault(CodegenConstants.PACKAGE_VERSION, packageVersion);

        modelPackage = packageName + ".models";
        apiPackage = packageName + ".api";

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
                new SupportingFile("py_typed.mustache", packagePath, "py.typed"));
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
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", packagePath, "trace_context_util.py"));
        String authPath = packagePath + File.separator + "auth";
        supportingFiles.add(
                new SupportingFile("auth/__init__.mustache", authPath, "__init__.py"));
        supportingFiles.add(
                new SupportingFile("authenticator.mustache", authPath, "authenticator.py"));
        supportingFiles.add(
                new SupportingFile("client.mustache", packagePath, "client.py"));
        supportingFiles.add(new SupportingFile("requirements.mustache", "", "requirements.txt"));
        supportingFiles.add(new SupportingFile("pyproject_toml.mustache", "", "pyproject.toml"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));
    }

    @Override
    protected String formatArrayType(String containerType, String innerType) {
        return containerType + "[" + innerType + "]";
    }

    @Override
    protected String formatMapType(String containerType, String keyType, String valueType) {
        return containerType + "[" + keyType + ", " + valueType + "]";
    }

    @Override
    protected String getMapKeyType() {
        return "str";
    }

    @Override
    protected String getMapDefaultValueType() {
        return "object";
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
    protected String applyVarNameCasing(String name) {
        return underscore(name);
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
    protected String formatOperationId(String sanitizedOperationId) {
        return underscore(sanitizedOperationId);
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
                return Boolean.parseBoolean(schema.getDefault().toString()) ? "True" : "False";
            }
            return schema.getDefault().toString();
        }
        return null;
    }

    @Override
    protected boolean isNumericEnumDatatype(String datatype) {
        return "int".equals(datatype) || "float".equals(datatype);
    }

    @Override
    protected String quoteEnumValue(String value) {
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
    protected String deriveClientPropertyName(String apiClassName) {
        String name = apiClassName.replaceAll("Api$", "");
        if (name.isEmpty()) {
            return "api";
        }
        return org.openapitools.codegen.utils.StringUtils.underscore(name);
    }

    @Override
    protected void registerAuthSupportingFiles() {
        String packagePath = packageName.replace('.', File.separatorChar);
        String authPath = packagePath + File.separator + "auth";
        String oauthPath = authPath + File.separator + "oauth";

        if (hasBasicAuth) {
            supportingFiles.add(new SupportingFile("auth/basic_authenticator.mustache", authPath, "basic_authenticator.py"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(new SupportingFile("auth/bearer_authenticator.mustache", authPath, "bearer_authenticator.py"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(new SupportingFile("auth/api_key_authenticator.mustache", authPath, "api_key_authenticator.py"));
            supportingFiles.add(new SupportingFile("auth/api_key_location.mustache", authPath, "api_key_location.py"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/__init__.mustache", oauthPath, "__init__.py"));
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_token_manager.mustache", oauthPath, "oauth2_token_manager.py"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_client_credentials_authenticator.mustache", oauthPath, "oauth2_client_credentials_authenticator.py"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_password_authenticator.mustache", oauthPath, "oauth2_password_authenticator.py"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_auth_code_authenticator.mustache", oauthPath, "oauth2_auth_code_authenticator.py"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(new SupportingFile("auth/oauth/oauth2_implicit_authenticator.mustache", oauthPath, "oauth2_implicit_authenticator.py"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(new SupportingFile("auth/oauth/openid_connect_authenticator.mustache", oauthPath, "openid_connect_authenticator.py"));
        }
    }

    @Override
    protected void generatePerSchemeAuthenticators(OpenAPI openAPI) {
        // Per-scheme authenticators are not generated for Python
        // The base classes are sufficient with the scheme-specific parameters
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);
        if (file == null || !file.getName().endsWith(".py")) {
            return;
        }
        try {
            String content = Files.readString(file.toPath());
            String trimmed = content.replaceAll("\\{ ('.*?') }", "{$1}");
            trimmed = trimmed.replaceAll("(?m)[ \\t]+$", "");
            trimmed = trimmed.replaceAll("\\n{4,}", "\n\n\n");
            trimmed = breakLongRaises(trimmed);
            trimmed = trimmed.replaceAll("\\n+$", "\n");
            if (!trimmed.equals(content)) {
                Files.write(file.toPath(), trimmed.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to post-process file: {}", file.getAbsolutePath(), e);
        }
    }

    private static String breakLongRaises(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            if (line.length() > 120
                    && line.stripLeading().startsWith("raise ValueError(\"")
                    && line.stripTrailing().endsWith("\")")) {
                String indent = line.substring(0, line.indexOf('r'));
                int msgStart = line.indexOf("(\"") + 1;
                int msgEnd = line.lastIndexOf("\")") + 1;
                String msg = line.substring(msgStart, msgEnd);
                sb.append(indent).append("raise ValueError(\n");
                sb.append(indent).append("    ").append(msg).append("\n");
                sb.append(indent).append(")");
            } else {
                sb.append(line);
            }
            sb.append("\n");
        }
        if (sb.length() > 0 && content.charAt(content.length() - 1) != '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }
}

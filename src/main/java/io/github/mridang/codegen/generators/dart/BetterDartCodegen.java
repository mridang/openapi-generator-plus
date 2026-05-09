package io.github.mridang.codegen.generators.dart;

import io.github.mridang.codegen.generators.AbstractBetterCodegen;
import io.github.mridang.codegen.generators.NamingConvention;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
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

/**
 * Generates a Dart API client that uses the http package for
 * transport and dart:convert for JSON serialization. All type
 * names use PascalCase, variable and method names use camelCase
 * per Dart convention. Filenames use snake_case. The formatter
 * pass invokes dart format inside Docker to enforce canonical
 * Dart formatting.
 */
@SuppressWarnings("unused")
public class BetterDartCodegen extends AbstractBetterCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterDartCodegen.class);

    protected String packageName = "petstore_client";
    protected String packageVersion = "1.0.0";

    /**
     * Initializes type mappings, template paths, and reserved
     * words for the Dart language. Type mappings convert OpenAPI
     * types to their Dart equivalents (e.g. integer to int,
     * DateTime to DateTime). Reserved words are loaded from a
     * bundled word-list.
     */
    public BetterDartCodegen() {
        outputFolder = Path.of("generated-code", "dart").toString();
        embeddedTemplateDir = templateDir = "templates/dart";

        modelTemplateFiles.put("models/model.mustache", ".dart");
        apiTemplateFiles.put("api/api.mustache", ".dart");

        modelPackage = "models";
        apiPackage = "api";

        typeMapping.put("string", "String");
        typeMapping.put("boolean", "bool");
        typeMapping.put("int", "int");
        typeMapping.put("integer", "int");
        typeMapping.put("long", "int");
        typeMapping.put("short", "int");
        typeMapping.put("float", "double");
        typeMapping.put("double", "double");
        typeMapping.put("number", "double");
        typeMapping.put("decimal", "double");
        typeMapping.put("date", "String");
        typeMapping.put("DateTime", "DateTime");
        typeMapping.put("array", "List");
        typeMapping.put("List", "List");
        typeMapping.put("set", "Set");
        typeMapping.put("map", "Map");
        typeMapping.put("object", "Object");
        typeMapping.put("AnyType", "Object");
        typeMapping.put("file", "List<int>");
        typeMapping.put("binary", "List<int>");
        typeMapping.put("ByteArray", "List<int>");
        typeMapping.put("UUID", "String");
        typeMapping.put("URI", "String");

        languageSpecificPrimitives =
                new HashSet<>(
                        Arrays.asList(
                                "String",
                                "bool",
                                "int",
                                "double",
                                "num",
                                "dynamic",
                                "Object",
                                "void",
                                "Null",
                                "DateTime",
                                "List<int>"));

        reservedWords = loadReservedWords("/reserved-words/dart.txt");

    }

    /** Returns the generator name used to select this codegen via the {@code -g} flag. */
    @Override
    public String getName() {
        return "dart-plus";
    }

    /**
     * Escapes a reserved word by appending an underscore suffix
     * instead of prepending one. In Dart, names starting with
     * underscore are library-private, so we avoid the prefix.
     */
    @Override
    public String escapeReservedWord(String name) {
        return name + "_";
    }

    /** Returns a short description shown in the help output. */
    @Override
    public String getHelp() {
        return "Generates a minimal Dart client with http and dart:convert.";
    }

    /**
     * Declares Dart as the target language so that the framework
     * can apply language-specific post-processing steps.
     */
    @Override
    public GeneratorLanguage generatorLanguage() {
        return GeneratorLanguage.DART;
    }

    /** {@inheritDoc} */
    @Override
    protected String getTestFixturesDir() {
        return "test/fixtures";
    }

    /** {@inheritDoc} */
    @Override
    protected String getSpecDir() {
        return "test/spec";
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getVarCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getOperationIdCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getEnumCasing() {
        return NamingConvention.CAMEL_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected NamingConvention getFilenameCasing() {
        return NamingConvention.SNAKE_CASE;
    }

    /** {@inheritDoc} */
    @Override
    protected String getFormatterDockerImage() {
        return "dart:3.6";
    }

    /** {@inheritDoc} */
    @Override
    protected String[] getFormatterCommands() {
        return new String[] {"dart pub get", "dart fix --apply", "dart format ."};
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getNumericDataTypes() {
        return Set.of("int", "double", "num");
    }

    /** {@inheritDoc} */
    @Override
    protected char getQuoteChar() {
        return '\'';
    }

    /** {@inheritDoc} */
    @Override
    protected boolean shouldEscapeQuotationMark() {
        return false;
    }

    /**
     * Formats an array type declaration using Dart List syntax.
     * Returns {@code List<innerType>}.
     */
    @Override
    protected String formatArrayType(String containerType, String innerType) {
        return "List<" + innerType + ">";
    }

    /**
     * Formats a map type declaration using Dart Map syntax.
     * Returns {@code Map<String, valueType>}.
     */
    @Override
    protected String formatMapType(String containerType, String keyType, String valueType) {
        return "Map<" + keyType + ", " + valueType + ">";
    }

    /**
     * Returns {@code String} as the map key type because Dart
     * maps use String keys for JSON-derived schemas.
     */
    @Override
    protected String getMapKeyType() {
        return "String";
    }

    /**
     * Returns {@code Object} as the default map value type
     * for Dart's universal base type.
     */
    @Override
    protected String getMapDefaultValueType() {
        return "Object";
    }

    /**
     * Resolves user-supplied options and registers all supporting
     * files for the Dart package structure. Sets up the source
     * layout with models, api, errors, auth, and supporting
     * infrastructure in the lib/src/ directory.
     */
    @Override
    public void processOpts() {
        super.processOpts();

        packageName = getPropertyOrDefault("packageName", packageName);
        packageVersion = getPropertyOrDefault("packageVersion", packageVersion);
        additionalProperties.put("packageName", packageName);
        additionalProperties.put(
                "userAgentDefault", packageName + "/" + packageVersion + " (dart)");

        final String srcDir = Path.of("lib", "src").toString();

        supportingFiles.add(new SupportingFile("readme.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("skills.mustache", "", "SKILLS.md"));
        supportingFiles.add(
                new SupportingFile("configuration.mustache", srcDir, "configuration.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "transport_options.mustache", srcDir, "transport_options.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "server_configuration.mustache", srcDir, "server_configuration.dart"));
        supportingFiles.add(
                new SupportingFile("servers.mustache", srcDir, "servers.dart"));
        supportingFiles.add(
                new SupportingFile("api_error.mustache", srcDir, "api_error.dart"));

        final String errorsDir = Path.of(srcDir, "errors").toString();
        supportingFiles.add(
                new SupportingFile(
                        "errors/api_error.mustache", errorsDir, "api_error.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/client_error.mustache", errorsDir, "client_error.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/server_error.mustache", errorsDir, "server_error.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/bad_request_error.mustache",
                        errorsDir,
                        "bad_request_error.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unauthorized_error.mustache",
                        errorsDir,
                        "unauthorized_error.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/forbidden_error.mustache",
                        errorsDir,
                        "forbidden_error.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/not_found_error.mustache",
                        errorsDir,
                        "not_found_error.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/conflict_error.mustache",
                        errorsDir,
                        "conflict_error.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/unprocessable_entity_error.mustache",
                        errorsDir,
                        "unprocessable_entity_error.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "errors/internal_server_error.mustache",
                        errorsDir,
                        "internal_server_error.dart"));

        supportingFiles.add(
                new SupportingFile(
                        "header_selector.mustache", srcDir, "header_selector.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "object_serializer.mustache", srcDir, "object_serializer.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "value_serializer.mustache", srcDir, "value_serializer.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "trace_context_util.mustache", srcDir, "trace_context_util.dart"));
        supportingFiles.add(
                new SupportingFile("api_response.mustache", srcDir, "api_response.dart"));
        supportingFiles.add(
                new SupportingFile("api_result.mustache", srcDir, "api_result.dart"));
        supportingFiles.add(
                new SupportingFile("api_client.mustache", srcDir, "api_client.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "default_api_client.mustache", srcDir, "default_api_client.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "base_api.mustache",
                        Path.of(srcDir, "api").toString(),
                        "base_api.dart"));
        supportingFiles.add(
                new SupportingFile("authenticator.mustache", Path.of(srcDir, "auth").toString(), "authenticator.dart"));

        final String clientClassName =
                Objects.requireNonNull((String) additionalProperties.get("clientClassName"));
        final String clientClassFile = NamingConvention.SNAKE_CASE.apply(clientClassName);
        additionalProperties.put("clientClassFile", clientClassFile);
        supportingFiles.add(
                new SupportingFile("client.mustache", srcDir, clientClassFile + ".dart"));

        supportingFiles.add(
                new SupportingFile("barrel.mustache", "lib", packageName + ".dart"));
        supportingFiles.add(
                new SupportingFile("pubspec.mustache", "", "pubspec.yaml"));
        supportingFiles.add(
                new SupportingFile("analysis_options.mustache", "", "analysis_options.yaml"));
        supportingFiles.add(new SupportingFile("makefile.mustache", "", "Makefile"));
        supportingFiles.add(new SupportingFile("editorconfig.mustache", "", ".editorconfig"));

        if (generateTests) {
            supportingFiles.add(new SupportingFile("test/gitignore", "", ".gitignore"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/testcontainers_helper.mustache",
                            "test",
                            "testcontainers_helper.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/pet_api_test.mustache", "test", "pet_api_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/api/store_api_test.mustache",
                            "test",
                            "store_api_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_test.mustache",
                            "test",
                            "default_api_client_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/default_api_client_unit_test.mustache",
                            "test",
                            "default_api_client_unit_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/transport_options_test.mustache",
                            "test",
                            "transport_options_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/header_selector_test.mustache",
                            "test",
                            "header_selector_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/object_serializer_test.mustache",
                            "test",
                            "object_serializer_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/value_serializer_test.mustache",
                            "test",
                            "value_serializer_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/trace_context_util_test.mustache",
                            "test",
                            "trace_context_util_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/base_api_test.mustache", "test", "base_api_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/configuration_test.mustache",
                            "test",
                            "configuration_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/client_test.mustache",
                            "test",
                            "client_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/metadata_test.mustache", "test", "metadata_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/composed_schema_test.mustache",
                            "test",
                            "composed_schema_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_token_manager_test.mustache",
                            "test",
                            "oauth2_token_manager_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_auth_code_authenticator_test.mustache",
                            "test",
                            "oauth2_auth_code_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_implicit_authenticator_test.mustache",
                            "test",
                            "oauth2_implicit_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_client_credentials_authenticator_test.mustache",
                            "test",
                            "oauth2_client_credentials_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/oauth2_password_authenticator_test.mustache",
                            "test",
                            "oauth2_password_authenticator_test.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "test/openid_connect_authenticator_test.mustache",
                            "test",
                            "openid_connect_authenticator_test.dart"));
        }
    }

    /**
     * Returns the output directory for model source files.
     * Dart models go in lib/src/models/.
     */
    @Override
    public String modelFileFolder() {
        return Path.of(outputFolder, "lib", "src", "models").toString();
    }

    /**
     * Returns the output directory for API source files.
     * Dart APIs go in lib/src/api/.
     */
    @Override
    public String apiFileFolder() {
        return Path.of(outputFolder, "lib", "src", "api").toString();
    }

    /**
     * Returns the Dart default value literal for the given schema.
     * Numeric and boolean defaults use their string representation;
     * string defaults are wrapped in single quotes.
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    @Override
    public String toDefaultValue(Schema schema) {
        final Schema resolved = ModelUtils.getReferencedSchema(this.openAPI, schema);
        if (ModelUtils.isIntegerSchema(resolved)
                || ModelUtils.isNumberSchema(resolved)
                || ModelUtils.isBooleanSchema(resolved)) {
            if (resolved.getDefault() != null) {
                return resolved.getDefault().toString();
            }
        } else if (ModelUtils.isStringSchema(resolved)) {
            if (resolved.getDefault() != null) {
                return "'" + escapeText(String.valueOf(resolved.getDefault())) + "'";
            }
        }
        return null;
    }

    /**
     * Fixes enum default values that the base class sets to
     * Java-style enum references (e.g. "StatusEnum.Placed").
     * For Dart, enum fields typed as String should use a
     * Dart string literal default.
     */
    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        final ModelsMap result = super.postProcessModels(objs);
        for (final ModelMap modelMap : result.getModels()) {
            final CodegenModel model = modelMap.getModel();
            for (final CodegenProperty prop : model.vars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.allVars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.optionalVars) {
                fixEnumDefaultValue(prop);
            }
            for (final CodegenProperty prop : model.requiredVars) {
                fixEnumDefaultValue(prop);
            }

            for (final CodegenProperty prop : model.vars) {
                clearEnumOnPrimitives(prop);
            }
            for (final CodegenProperty prop : model.allVars) {
                clearEnumOnPrimitives(prop);
            }
            for (final CodegenProperty prop : model.optionalVars) {
                clearEnumOnPrimitives(prop);
            }
            for (final CodegenProperty prop : model.requiredVars) {
                clearEnumOnPrimitives(prop);
            }

            final List<Map<String, String>> dartImports = new ArrayList<>();
            for (final String importName : model.imports) {
                if (!languageSpecificPrimitives.contains(importName)
                        && !typeMapping.containsValue(importName)) {
                    final Map<String, String> dartImport = new HashMap<>();
                    dartImport.put("classname", importName);
                    dartImport.put("filename", toModelFilename(importName));
                    dartImports.add(dartImport);
                }
            }
            modelMap.put("dartImports", dartImports);
            modelMap.put("hasDartImports", !dartImports.isEmpty());

            final Set<String> filteredOneOf = new java.util.LinkedHashSet<>();
            for (final String typeName : model.oneOf) {
                if (!languageSpecificPrimitives.contains(typeName)
                        && !typeName.startsWith("List<")
                        && !typeName.startsWith("Map<")
                        && !typeName.startsWith("Set<")) {
                    filteredOneOf.add(typeName);
                }
            }
            model.oneOf = filteredOneOf;

            final Set<String> filteredAnyOf = new java.util.LinkedHashSet<>();
            for (final String typeName : model.anyOf) {
                if (!languageSpecificPrimitives.contains(typeName)
                        && !typeName.startsWith("List<")
                        && !typeName.startsWith("Map<")
                        && !typeName.startsWith("Set<")) {
                    filteredAnyOf.add(typeName);
                }
            }
            model.anyOf = filteredAnyOf;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public OperationsMap postProcessOperationsWithModels(
            OperationsMap objs, List<ModelMap> allModels) {
        objs = super.postProcessOperationsWithModels(objs, allModels);

        final List<Map<String, String>> imports =
                (List<Map<String, String>>) objs.get("imports");
        if (imports != null) {
            imports.removeIf(imp -> {
                final String importName = imp.getOrDefault("import", "");
                final String className =
                        importName.contains(".")
                                ? importName.substring(importName.lastIndexOf('.') + 1)
                                : importName;
                return languageSpecificPrimitives.contains(className)
                        || typeMapping.containsValue(className)
                        || className.startsWith("List<")
                        || className.equals("List")
                        || className.startsWith("Map<")
                        || className.equals("Map")
                        || className.startsWith("Set<")
                        || className.equals("Set");
            });
            for (final Map<String, String> imp : imports) {
                if (!imp.containsKey("className") && imp.containsKey("import")) {
                    String className = imp.get("import");
                    if (className.contains(".")) {
                        className = className.substring(className.lastIndexOf('.') + 1);
                    }
                    imp.put("className", className);
                    imp.put("filename", toModelFilename(className));
                }
            }
        }
        return objs;
    }

    private void clearEnumOnPrimitives(CodegenProperty prop) {
        if (prop.isEnum
                && (languageSpecificPrimitives.contains(prop.dataType)
                        || typeMapping.containsValue(prop.dataType))) {
            prop.isEnum = false;
        }
    }

    private void fixEnumDefaultValue(CodegenProperty prop) {
        if (prop.defaultValue != null && prop.isEnum && prop.defaultValue.contains(".")) {
            final String enumValue = prop.defaultValue.substring(
                    prop.defaultValue.lastIndexOf('.') + 1);
            prop.defaultValue = "'" + enumValue.toLowerCase(java.util.Locale.ROOT) + "'";
        }
    }

    /**
     * Registers supporting files for each authentication scheme
     * present in the OpenAPI spec. Auth files go in
     * lib/src/auth/.
     */
    @Override
    protected void registerAuthSupportingFiles() {
        final String srcDir = Path.of("lib", "src").toString();
        final String authDir = Path.of(srcDir, "auth").toString();
        final String oauthDir = Path.of(authDir, "oauth").toString();

        supportingFiles.add(
                new SupportingFile(
                        "auth/base_authenticator.mustache",
                        authDir,
                        "base_authenticator.dart"));
        supportingFiles.add(
                new SupportingFile(
                        "auth/http_aware_authenticator.mustache",
                        authDir,
                        "http_aware_authenticator.dart"));

        if (hasBasicAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/basic_authenticator.mustache",
                            authDir,
                            "basic_authenticator.dart"));
        }
        if (hasBearerAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/bearer_authenticator.mustache",
                            authDir,
                            "bearer_authenticator.dart"));
        }
        if (hasApiKeyAuth) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_location.mustache",
                            authDir,
                            "api_key_location.dart"));
            supportingFiles.add(
                    new SupportingFile(
                            "auth/api_key_authenticator.mustache",
                            authDir,
                            "api_key_authenticator.dart"));
        }
        if (hasAnyOAuth2 || hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_token_manager.mustache",
                            oauthDir,
                            "oauth2_token_manager.dart"));
        }
        if (hasOAuth2ClientCredentials) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_client_credentials_authenticator.mustache",
                            oauthDir,
                            "oauth2_client_credentials_authenticator.dart"));
        }
        if (hasOAuth2Password) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_password_authenticator.mustache",
                            oauthDir,
                            "oauth2_password_authenticator.dart"));
        }
        if (hasOAuth2AuthorizationCode) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_auth_code_authenticator.mustache",
                            oauthDir,
                            "oauth2_auth_code_authenticator.dart"));
        }
        if (hasOAuth2Implicit) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/oauth2_implicit_authenticator.mustache",
                            oauthDir,
                            "oauth2_implicit_authenticator.dart"));
        }
        if (hasOpenIdConnect) {
            supportingFiles.add(
                    new SupportingFile(
                            "auth/oauth/openid_connect_authenticator.mustache",
                            oauthDir,
                            "openid_connect_authenticator.dart"));
        }

        if (openAPI.getComponents() == null
                || openAPI.getComponents().getSecuritySchemes() == null) {
            return;
        }

        for (final Map.Entry<String, SecurityScheme> entry :
                openAPI.getComponents().getSecuritySchemes().entrySet()) {
            final String schemeName = entry.getKey();
            final SecurityScheme scheme = entry.getValue();
            final String className = NamingConvention.PASCAL_CASE.apply(schemeName);
            final String code = generateDartAuthClass(schemeName, className, scheme);
            if (!code.isEmpty()) {
                final boolean isOAuth =
                        scheme.getType() == SecurityScheme.Type.OAUTH2
                                || scheme.getType() == SecurityScheme.Type.OPENIDCONNECT;
                final String folder = isOAuth ? oauthDir : authDir;
                final String suffix = getDartOAuthSuffix(scheme);
                final String fileName =
                        NamingConvention.SNAKE_CASE.apply(className + suffix + "Authenticator")
                                + ".dart";
                final String filePath =
                        Path.of(outputFolder, folder, fileName).toString();
                writeFile(filePath, code);
                postProcessFile(Path.of(filePath).toFile(), "source");
            }
        }
    }

    private String getDartOAuthSuffix(SecurityScheme scheme) {
        if (scheme.getType() != SecurityScheme.Type.OAUTH2 || scheme.getFlows() == null) {
            return "";
        }
        return Optional.ofNullable(scheme.getFlows().getClientCredentials())
                .map(f -> "ClientCredentials")
                .or(() -> Optional.ofNullable(scheme.getFlows().getPassword()).map(f -> "Password"))
                .or(() ->
                        Optional.ofNullable(scheme.getFlows().getAuthorizationCode())
                                .map(f -> "AuthorizationCode"))
                .or(() -> Optional.ofNullable(scheme.getFlows().getImplicit()).map(f -> "Implicit"))
                .orElse("");
    }

    @SuppressWarnings("StringConcatenationMissingWhitespace")
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "Comparing with ASCII-only constants")
    private String generateDartAuthClass(
            String schemeName, String className, SecurityScheme scheme) {
        if (scheme.getType() == SecurityScheme.Type.HTTP) {
            if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                return renderDartSchemeAuth(className + "Authenticator",
                        "BasicAuthenticator",
                        List.of("basic_authenticator.dart"),
                        "required String host, required String username, required String password",
                        "host: host, username: username, password: password");
            }
            if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                return renderDartSchemeAuth(className + "Authenticator",
                        "BearerAuthenticator",
                        List.of("bearer_authenticator.dart"),
                        "required String host, required String token",
                        "host: host, token: token");
            }
        } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
            final String location =
                    NamingConvention.CAMEL_CASE.apply(scheme.getIn().toString());
            final String paramName = scheme.getName();
            return renderDartSchemeAuth(className + "Authenticator",
                    "ApiKeyAuthenticator",
                    List.of("api_key_authenticator.dart", "api_key_location.dart"),
                    "required String host, required String apiKey",
                    "host: host, keyParamName: '" + paramName + "', apiKey: apiKey, "
                            + "location: ApiKeyLocation." + location);
        } else if (scheme.getType() == SecurityScheme.Type.OAUTH2
                && scheme.getFlows() != null) {
            return generateDartOAuthClass(className, scheme);
        } else if (scheme.getType() == SecurityScheme.Type.OPENIDCONNECT) {
            final String url = scheme.getOpenIdConnectUrl();
            return renderDartSchemeAuth(className + "Authenticator",
                    "OpenIdConnectAuthenticator",
                    List.of("openid_connect_authenticator.dart"),
                    "required String host, required String clientId, "
                            + "required String clientSecret, required String redirectUri",
                    "host: host, openIdConnectUrl: '" + url + "', clientId: clientId, "
                            + "clientSecret: clientSecret, redirectUri: redirectUri, "
                            + "scopes: []");
        }
        LOGGER.warn("Unsupported security scheme type: {}", scheme.getType());
        return "";
    }

    private String generateDartOAuthClass(
            String className, SecurityScheme scheme) {
        if (scheme.getFlows().getClientCredentials() != null) {
            final var flow = scheme.getFlows().getClientCredentials();
            final String tokenUrl = flow.getTokenUrl();
            final String scopes = formatDartScopes(flow.getScopes());
            return renderDartSchemeAuth(
                    className + "ClientCredentialsAuthenticator",
                    "OAuth2ClientCredentialsAuthenticator",
                    List.of("oauth2_client_credentials_authenticator.dart"),
                    "required String host, required String clientId, "
                            + "required String clientSecret",
                    "host: host, clientId: clientId, clientSecret: clientSecret, "
                            + "tokenUrl: '" + tokenUrl + "', scopes: " + scopes);
        }
        if (scheme.getFlows().getPassword() != null) {
            final var flow = scheme.getFlows().getPassword();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String refreshUrlArg = refreshUrl != null ? "'" + refreshUrl + "'" : "null";
            final String scopes = formatDartScopes(flow.getScopes());
            return renderDartSchemeAuth(
                    className + "PasswordAuthenticator",
                    "OAuth2PasswordAuthenticator",
                    List.of("oauth2_password_authenticator.dart"),
                    "required String host, required String clientId, "
                            + "required String clientSecret, required String username, "
                            + "required String password",
                    "host: host, clientId: clientId, clientSecret: clientSecret, "
                            + "tokenUrl: '" + tokenUrl + "', refreshUrl: " + refreshUrlArg + ", "
                            + "username: username, password: password, scopes: " + scopes);
        }
        if (scheme.getFlows().getAuthorizationCode() != null) {
            final var flow = scheme.getFlows().getAuthorizationCode();
            final String authUrl = flow.getAuthorizationUrl();
            final String tokenUrl = flow.getTokenUrl();
            final String refreshUrl = flow.getRefreshUrl();
            final String refreshUrlArg = refreshUrl != null ? "'" + refreshUrl + "'" : "null";
            final String scopes = formatDartScopes(flow.getScopes());
            return renderDartSchemeAuth(
                    className + "AuthorizationCodeAuthenticator",
                    "OAuth2AuthorizationCodeAuthenticator",
                    List.of("oauth2_auth_code_authenticator.dart"),
                    "required String host, required String clientId, "
                            + "required String clientSecret, required String redirectUri",
                    "host: host, clientId: clientId, clientSecret: clientSecret, "
                            + "authorizationUrl: '" + authUrl + "', tokenUrl: '" + tokenUrl + "', "
                            + "redirectUri: redirectUri, scopes: " + scopes + ", "
                            + "refreshUrl: " + refreshUrlArg);
        }
        if (scheme.getFlows().getImplicit() != null) {
            final var flow = scheme.getFlows().getImplicit();
            final String authUrl = flow.getAuthorizationUrl();
            final String scopes = formatDartScopes(flow.getScopes());
            return renderDartSchemeAuth(
                    className + "ImplicitAuthenticator",
                    "OAuth2ImplicitAuthenticator",
                    List.of("oauth2_implicit_authenticator.dart"),
                    "required String host, required String clientId",
                    "host: host, clientId: clientId, authorizationUrl: '"
                            + authUrl + "', scopes: " + scopes);
        }
        LOGGER.warn("Unsupported OAuth2 flow for scheme: {}", className);
        return "";
    }

    @SuppressWarnings("SameParameterValue")
    private static String formatDartScopes(@Nullable Map<String, String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "[]";
        }
        return "['" + String.join("', '", scopes.keySet()) + "']";
    }

    private String renderDartSchemeAuth(String className, String baseClass,
            List<String> imports, String constructorSignature, String superCall) {
        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("baseClass", baseClass);
        context.put("imports", imports);
        context.put("constructorSignature", constructorSignature);
        context.put("superCall", superCall);
        return renderOptionsTemplate("auth/scheme_authenticator.mustache", context);
    }

    /**
     * Collapses runs of two or more consecutive blank lines
     * in generated {@code .dart} files into a single blank line.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        if (file == null) {
            return;
        }
        if (!file.getName().endsWith(".dart")) {
            return;
        }
        cleanupDartFile(file);
    }

    /**
     * Collapses consecutive blank lines in a Dart source file.
     */
    private static void cleanupDartFile(File file) {
        try {
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            final List<String> result = new ArrayList<>(lines.size());
            boolean prevBlank = false;
            boolean changed = false;

            for (final String line : lines) {
                final boolean blank = line.trim().isEmpty();
                if (blank && prevBlank) {
                    changed = true;
                    continue;
                }
                result.add(line);
                prevBlank = blank;
            }

            if (changed) {
                Files.write(file.toPath(), result, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.debug(
                    "Failed to clean up Dart file {}: {}", file.getName(), e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    protected String generateOptionsFileContent(
            CodegenOperation op, List<CodegenParameter> optionsParams, String className) {
        final List<Map<String, Object>> params = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            final Map<String, Object> param = new HashMap<>();
            param.put("name", p.paramName);
            param.put("dataType", p.dataType);
            param.put("required", p.required);
            if (p.description != null && !p.description.isEmpty()) {
                param.put("description", p.description);
            }
            params.add(param);
        }

        final List<Map<String, String>> optionsImports = new ArrayList<>();
        for (final CodegenParameter p : optionsParams) {
            if (p.baseType != null
                    && !languageSpecificPrimitives.contains(p.baseType)
                    && !typeMapping.containsValue(p.baseType)) {
                final Map<String, String> imp = new HashMap<>();
                imp.put("classname", p.baseType);
                imp.put("filename", toModelFilename(p.baseType));
                optionsImports.add(imp);
            }
        }

        final Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("packageName", packageName);
        context.put("operationId", op.operationId);
        context.put("params", params);
        context.put("dartImports", optionsImports);
        context.put("hasDartImports", !optionsImports.isEmpty());
        return renderOptionsTemplate("api/options.mustache", context);
    }

    /** {@inheritDoc} */
    @Override
    protected String getOptionsFilePath(String operationId, String optionsClassName) {
        final String fileName = NamingConvention.SNAKE_CASE.apply(optionsClassName);
        return Path.of(
                        getOutputDir(), "lib", "src", "api", "options", fileName + ".dart")
                .toString();
    }

    /** Appends options class exports to the barrel file. */
    @Override
    protected void writeOptionsBarrelFiles(List<Map<String, String>> optionsFiles) {
        if (optionsFiles.isEmpty()) {
            return;
        }
        final Path barrelPath =
                Path.of(getOutputDir(), "lib", packageName + ".dart");
        try {
            final StringBuilder sb = new StringBuilder();
            for (final Map<String, String> meta : optionsFiles) {
                final String className = meta.get("optionsClassName");
                if (className == null) {
                    continue;
                }
                final String fileName = NamingConvention.SNAKE_CASE.apply(className);
                sb.append("export 'src/api/options/")
                        .append(fileName)
                        .append(".dart';\n");
            }
            Files.writeString(
                    barrelPath,
                    Files.readString(barrelPath) + sb,
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to append options exports to barrel: {}", e.getMessage());
        }
    }
}

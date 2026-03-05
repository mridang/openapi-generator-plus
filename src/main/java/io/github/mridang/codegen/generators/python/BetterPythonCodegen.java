package io.github.mridang.codegen.generators.python;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.PythonClientCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A custom Python code generator that provides sane defaults for generating a
 * minimal, modern Python client.
 * <p>
 * This generator is configured to:
 * <ul>
 * <li>Use the 'urllib3' library for HTTP requests.</li>
 * <li>Set specific project, package, and version information.</li>
 * <li>Allow additional properties in models for forward compatibility.</li>
 * <li>Enable oneOf discriminator lookups.</li>
 * <li>Generate only model and API files, excluding tests, docs, and
 * other supporting project files.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class BetterPythonCodegen extends PythonClientCodegen implements UnsupportedFeaturesValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterPythonCodegen.class);

    /**
     * Initializes a new instance of the {@code BetterPythonCodegen} class,
     * setting up the hardcoded default configurations for a minimal client.
     */
    public BetterPythonCodegen() {
        super();

        this.setLibrary(DEFAULT_LIBRARY);
        this.setDisallowAdditionalPropertiesIfNotPresent(false);
        this.setUseOneOfDiscriminatorLookup(true);

        setTemplateDir("templates/python");

        apiDocTemplateFiles.clear();
        modelDocTemplateFiles.clear();
        apiTestTemplateFiles.clear();
        modelTestTemplateFiles.clear();
    }

    @Override
    public String getLibrary() {
        return DEFAULT_LIBRARY;
    }

    /**
     * Gets the unique name of this generator. This name is used to select the
     * generator from the command line or other tools.
     *
     * @return The unique generator name, "python-plus".
     */
    @Override
    public String getName() {
        return "python-plus";
    }

    /**
     * Processes generator options and then customizes the output by removing
     * non-essential supporting files while keeping the core infrastructure
     * needed for the API classes to function.
     */
    @Override
    public void processOpts() {
        super.processOpts();
        setEnablePostProcessFile(true);
        this.supportingFiles.clear();

        String modelPath = modelPackage.replace('.', File.separatorChar);
        String apiPath = apiPackage.replace('.', File.separatorChar);
        String packagePath = packageName.replace('.', File.separatorChar);

        // Package __init__ files
        supportingFiles.add(new SupportingFile("__init__model.mustache", modelPath, "__init__.py"));
        supportingFiles.add(new SupportingFile("__init__api.mustache", apiPath, "__init__.py"));
        supportingFiles.add(new SupportingFile("__init__package.mustache", packagePath, "__init__.py"));

        // Essential supporting files for API functionality
        supportingFiles.add(new SupportingFile("api_client.mustache", packagePath, "api_client.py"));
        supportingFiles.add(new SupportingFile("default_api_client.mustache", packagePath, "default_api_client.py"));
        supportingFiles.add(new SupportingFile("api_response.mustache", packagePath, "api_response.py"));
        supportingFiles.add(new SupportingFile("base_api.mustache", apiPath, "base_api.py"));
        supportingFiles.add(new SupportingFile("configuration.mustache", packagePath, "configuration.py"));
        supportingFiles.add(new SupportingFile("exceptions.mustache", packagePath, "exceptions.py"));
        supportingFiles.add(new SupportingFile("object_serializer.mustache", packagePath, "object_serializer.py"));
        supportingFiles.add(new SupportingFile("header_selector.mustache", packagePath, "header_selector.py"));
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
    }

    /**
     * Pattern to match double-quoted string values inside Field() or other
     * annotations in Python type expressions. Matches {@code "..."} but not
     * already single-quoted strings.
     */
    private static final Pattern DOUBLE_QUOTED_IN_FIELD =
        Pattern.compile("(?<=[(,=\\s])\"([^\"]*)\"(?=[),\\s])");

    /**
     * Replaces double-quoted string literals with single-quoted ones inside
     * Python type annotation expressions (e.g. in {@code Field(description="...")}).
     * This ensures the generated code conforms to ruff's single-quote style.
     */
    private static String singleQuoteTyping(String typing) {
        Matcher m = DOUBLE_QUOTED_IN_FIELD.matcher(typing);
        return m.replaceAll("'$1'");
    }

    /**
     * Wraps an {@code Annotated[...]} type expression across multiple lines if
     * the resulting parameter line would exceed 120 characters. The method
     * signature indentation is 8 spaces, so the content inside the Annotated
     * bracket is indented with 12 spaces.
     */
    private static String wrapAnnotatedIfNeeded(String paramName, String typing, boolean isOptional) {
        if (!typing.startsWith("Annotated[")) {
            return typing;
        }
        // Calculate total line length: "        {paramName}: {typing} = None," or "        {paramName}: {typing},"
        int lineLength = 8 + paramName.length() + 2 + typing.length() + (isOptional ? 8 : 1);
        if (lineLength <= 120) {
            return typing;
        }
        // Extract inner content: Annotated[INNER]
        String inner = typing.substring("Annotated[".length(), typing.length() - 1);
        return "Annotated[\n            " + inner + "\n        ]";
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);
        OperationMap ops = result.getOperations();
        if (ops != null) {
            for (CodegenOperation op : ops.getOperation()) {
                for (CodegenParameter param : op.allParams) {
                    Object typing = param.vendorExtensions.get("x-py-typing");
                    if (typing instanceof String) {
                        String fixed = singleQuoteTyping((String) typing);
                        fixed = wrapAnnotatedIfNeeded(param.paramName, fixed, !param.required);
                        param.vendorExtensions.put("x-py-typing", fixed);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (ModelsMap modelsMap : result.values()) {
            for (ModelMap modelMap : modelsMap.getModels()) {
                CodegenModel model = modelMap.getModel();
                for (CodegenProperty prop : model.vars) {
                    Object typing = prop.vendorExtensions.get("x-py-typing");
                    if (typing instanceof String) {
                        prop.vendorExtensions.put("x-py-typing", singleQuoteTyping((String) typing));
                    }
                }
                for (CodegenProperty prop : model.allVars) {
                    Object typing = prop.vendorExtensions.get("x-py-typing");
                    if (typing instanceof String) {
                        prop.vendorExtensions.put("x-py-typing", singleQuoteTyping((String) typing));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Post-processes each generated Python file to strip trailing blank lines,
     * ensuring the output conforms to ruff's formatting expectations.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);
        if (file == null || !file.getName().endsWith(".py")) {
            return;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String trimmed = content.replaceAll("\\n+$", "\n");
            if (!trimmed.equals(content)) {
                Files.write(file.toPath(), trimmed.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to post-process file: {}", file.getAbsolutePath(), e);
        }
    }
}

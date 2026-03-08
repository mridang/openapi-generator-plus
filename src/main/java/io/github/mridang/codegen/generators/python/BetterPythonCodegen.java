package io.github.mridang.codegen.generators.python;

import io.github.mridang.codegen.generators.UnsupportedFeaturesValidator;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.servers.Server;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.PythonClientCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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

        // Use plain Python types instead of pydantic Strict* types
        typeMapping.put("integer", "int");
        typeMapping.put("long", "int");
        typeMapping.put("float", "float");
        typeMapping.put("double", "float");
        typeMapping.put("string", "str");
        typeMapping.put("boolean", "bool");
        typeMapping.put("binary", "bytes");
        typeMapping.put("ByteArray", "bytes");

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
        this.modelTemplateFiles.clear();
        this.modelTemplateFiles.put("models/model.mustache", ".py");
        this.apiTemplateFiles.clear();
        this.apiTemplateFiles.put("api/api.mustache", ".py");
        this.supportingFiles.clear();

        String modelPath = modelPackage.replace('.', File.separatorChar);
        String apiPath = apiPackage.replace('.', File.separatorChar);
        String packagePath = packageName.replace('.', File.separatorChar);

        // Package __init__ files
        supportingFiles.add(new SupportingFile("models/__init__.mustache", modelPath, "__init__.py"));
        supportingFiles.add(new SupportingFile("api/__init__.mustache", apiPath, "__init__.py"));
        supportingFiles.add(new SupportingFile("__init__.mustache", packagePath, "__init__.py"));

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

    /**
     * Converts each model's imports from short type names (e.g. "Category") to
     * full Python import statements (e.g. "from package.models.category import Category").
     * This allows the template to use {@code {{{.}}}} to output imports directly.
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> result = super.postProcessAllModels(objs);
        for (ModelsMap modelsMap : result.values()) {
            for (ModelMap modelMap : modelsMap.getModels()) {
                CodegenModel model = modelMap.getModel();

                // Build full import statements from short type names
                TreeSet<String> fullImports = new TreeSet<>();

                // Add imports for language-specific primitives (datetime, date, etc.)
                // that the parent codegen handles via vendor extensions
                for (CodegenProperty prop : model.allVars) {
                    addTypeImport(fullImports, prop.dataType);
                    if (prop.items != null) {
                        addTypeImport(fullImports, prop.items.dataType);
                    }
                }

                // Convert short model type names to full import statements
                for (String imp : model.imports) {
                    fullImports.add(
                        "from " + modelPackage + "." + toModelFilename(imp) + " import " + imp);
                }
                model.imports.clear();
                model.imports.addAll(fullImports);
            }
        }
        return result;
    }

    /**
     * Maps Python type names that are language-specific primitives to their
     * required import statements. The parent codegen handles these via
     * vendor extensions, but we generate them directly.
     */
    private static final Map<String, String> TYPE_IMPORTS = Map.of(
        "datetime", "from datetime import datetime",
        "date", "from datetime import date",
        "Decimal", "from decimal import Decimal"
    );

    private static void addTypeImport(TreeSet<String> imports, String dataType) {
        String imp = TYPE_IMPORTS.get(dataType);
        if (imp != null) {
            imports.add(imp);
        }
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, List<Server> servers) {
        validateOperation(operation);
        return super.fromOperation(path, httpMethod, operation, servers);
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
            // Fix set literal formatting: { 'A', 'B' } → {'A', 'B'}
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

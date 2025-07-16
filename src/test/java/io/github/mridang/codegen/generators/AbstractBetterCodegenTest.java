package io.github.mridang.codegen.generators;

import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public abstract class AbstractBetterCodegenTest {

    @SuppressWarnings("unused")
    protected static Path newTempFolder() {
        final Path tempDir;
        try {
            tempDir = Files.createTempDirectory("test");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        tempDir.toFile().deleteOnExit();

        return tempDir;
    }

    protected void doGenerate(String generatorName, Path outputDir) {
        final CodegenConfigurator configurator = new CodegenConfigurator()
            .setGeneratorName(generatorName)
            .setAdditionalProperties(Map.of(
                CodegenConstants.MODEL_PACKAGE, "xyz.abcdef.models",
                CodegenConstants.API_PACKAGE, "xyz.abcdef.api"
            ))
            .setInputSpec("src/test/resources/spec.yaml")
            .setOutputDir(outputDir.toString().replace("\\", "/"));

        DefaultGenerator generator = new DefaultGenerator();
        generator.setGenerateMetadata(false);
        generator.opts(configurator.toClientOptInput()).generate();
    }
}

package io.github.mridang.codegen.generators;

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

    protected void doGenerate(String generatorName, Path outputDir, Map<String, Object> genOpts) {
        final CodegenConfigurator configurator = new CodegenConfigurator()
            .setGeneratorName(generatorName)
            .setAdditionalProperties(genOpts)
            .setInputSpec("src/test/resources/spec.yaml")
            .setOutputDir(outputDir.toString().replace("\\", "/"));

        DefaultGenerator generator = new DefaultGenerator();
        generator.setGenerateMetadata(false);
        generator.opts(configurator.toClientOptInput()).generate();
    }
}

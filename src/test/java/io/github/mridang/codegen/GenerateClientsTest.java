package io.github.mridang.codegen;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

public class GenerateClientsTest {

    private static final Path TESTPROJECTS_BASE =
            Paths.get("src/spec/resources/testprojects");

    @Test
    void generateAllClients() {
        String spec = "src/spec/resources/specs/petstore/openapi.yaml";

        generate(
                "python-plus",
                spec,
                TESTPROJECTS_BASE.resolve("pytest").toString(),
                Map.of("packageName", "petstore_client", "generateTests", "true"));

        generate(
                "java-plus",
                spec,
                TESTPROJECTS_BASE.resolve("javatest").toString(),
                Map.of(
                        "modelPackage", "com.example.petstore.models",
                        "apiPackage", "com.example.petstore.api",
                        "invokerPackage", "com.example.petstore",
                        "generateTests", "true"));

        generate(
                "php-plus",
                spec,
                TESTPROJECTS_BASE.resolve("phptest").toString(),
                Map.of("invokerPackage", "PetstoreClient", "generateTests", "true"));

        generate(
                "ruby-plus",
                spec,
                TESTPROJECTS_BASE.resolve("rubytest").toString(),
                Map.of(
                        "gemName", "petstore_client",
                        "moduleName", "PetstoreClient",
                        "generateTests", "true"));

        generate(
                "node-plus",
                spec,
                TESTPROJECTS_BASE.resolve("nodetest").toString(),
                Map.of("generateTests", "true"));

        generate(
                "csharp-plus",
                spec,
                TESTPROJECTS_BASE.resolve("csharptest").toString(),
                Map.of(
                        "packageName", "PetstoreClient",
                        "sourceFolder", "src",
                        "generateTests", "true"));

        System.out.println("\n=== Generated all clients into testprojects ===");
    }

    void generate(String generator, String spec, String output, Map<String, Object> props) {
        System.out.println("Generating " + generator + " to " + output);
        CodegenConfigurator configurator =
                new CodegenConfigurator()
                        .setGeneratorName(generator)
                        .setInputSpec(spec)
                        .setOutputDir(output)
                        .setAdditionalProperties(props);

        DefaultGenerator gen = new DefaultGenerator();
        gen.setGenerateMetadata(false);
        gen.opts(configurator.toClientOptInput()).generate();
    }
}

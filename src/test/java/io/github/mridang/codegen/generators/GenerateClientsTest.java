package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

/**
 * Generates all 12 client SDKs into src/spec/resources/generated/{lang} so that the generated
 * snapshot files stay in sync with template changes. Run with: mvn test -Dtest=GenerateClientsTest
 */
class GenerateClientsTest {

    private static final String SPEC_RESOURCE = "specs/petstore/openapi.yaml";

    private void generateClient(
            String generatorName, Map<String, Object> properties, Path outputDir) throws IOException {
        // Clean output directory except .out/
        if (Files.exists(outputDir)) {
            try (var entries = Files.list(outputDir)) {
                for (Path entry : entries.collect(Collectors.toList())) {
                    if (entry.getFileName().toString().equals(".out")) {
                        continue;
                    }
                    if (Files.isDirectory(entry)) {
                        deleteRecursively(entry);
                    } else {
                        Files.delete(entry);
                    }
                }
            }
        }
        Files.createDirectories(outputDir);

        URL specUrl = getClass().getClassLoader().getResource(SPEC_RESOURCE);
        if (specUrl == null) {
            throw new IllegalStateException("Could not find spec resource: " + SPEC_RESOURCE);
        }

        Map<String, Object> props = new HashMap<>(properties);
        props.put("generateTests", "true");

        CodegenConfigurator configurator =
                new CodegenConfigurator()
                        .setGeneratorName(generatorName)
                        .setInputSpec(specUrl.getPath())
                        .setOutputDir(outputDir.toString().replace("\\", "/"))
                        .setValidateSpec(false)
                        .setAdditionalProperties(props);

        DefaultGenerator generator = new DefaultGenerator();
        generator.setGenerateMetadata(false);
        generator.opts(configurator.toClientOptInput()).generate();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        Files.walkFileTree(
                dir,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path d, IOException exc)
                            throws IOException {
                        Files.delete(d);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    @Test
    void generateJavaClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/java").toAbsolutePath();
        generateClient(
                "java-plus",
                Map.of(
                        "modelPackage", "com.example.petstore.models",
                        "apiPackage", "com.example.petstore.api",
                        "invokerPackage", "com.example.petstore"),
                outputDir);

    }

    @Test
    void generatePythonClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/python").toAbsolutePath();
        generateClient(
                "python-plus",
                Map.of("packageName", "petstore_client", "projectName", "petstore-client"),
                outputDir);

    }

    @Test
    void generateRubyClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/ruby").toAbsolutePath();
        generateClient(
                "ruby-plus",
                Map.of("gemName", "petstore_client", "moduleName", "PetstoreClient"),
                outputDir);

    }

    @Test
    void generatePhpClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/php").toAbsolutePath();
        generateClient("php-plus", Map.of("invokerPackage", "PetstoreClient"), outputDir);

    }

    @Test
    void generateNodeClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/node").toAbsolutePath();
        generateClient("node-plus", Map.of(), outputDir);

    }

    @Test
    void generateGoClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/go").toAbsolutePath();
        generateClient("go-plus", Map.of("packageName", "petstore"), outputDir);

    }

    @Test
    void generateCSharpClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/csharp").toAbsolutePath();
        generateClient(
                "csharp-plus",
                Map.of("packageName", "PetstoreClient", "sourceFolder", "src"),
                outputDir);

    }

    @Test
    void generateKotlinClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/kotlin").toAbsolutePath();
        generateClient(
                "kotlin-plus",
                Map.of(
                        "modelPackage", "com.example.petstore.models",
                        "apiPackage", "com.example.petstore.api",
                        "invokerPackage", "com.example.petstore"),
                outputDir);

    }

    @Test
    void generateRustClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/rust").toAbsolutePath();
        generateClient("rust-plus", Map.of("packageName", "petstore"), outputDir);

    }

    @Test
    void generateSwiftClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/swift").toAbsolutePath();
        generateClient("swift-plus", Map.of("packageName", "PetstoreClient"), outputDir);

    }

    @Test
    void generateDartClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/dart").toAbsolutePath();
        generateClient("dart-plus", Map.of("packageName", "petstore_client"), outputDir);

    }

    @Test
    void generateElixirClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/elixir").toAbsolutePath();
        generateClient(
                "elixir-plus",
                Map.of("packageName", "petstore_client", "moduleName", "PetstoreClient"),
                outputDir);

    }
}

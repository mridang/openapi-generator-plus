package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

/**
 * Generates all 6 client SDKs into src/spec/resources/generated/{lang} so that the generated
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

    private void copyClasspathResource(String resourcePath, Path targetPath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Could not find classpath resource: " + resourcePath);
            }
            Files.createDirectories(targetPath.getParent());
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copySharedResources(Path outputDir) throws IOException {
        copyClasspathResource(
                "specs/petstore/openapi.yaml", outputDir.resolve("specs/openapi.yaml"));
        copyClasspathResource("certs/ca.pem", outputDir.resolve("certs/ca.pem"));
        copyClasspathResource("certs/ca-key.pem", outputDir.resolve("certs/ca-key.pem"));
        copyClasspathResource("certs/server.pem", outputDir.resolve("certs/server.pem"));
        copyClasspathResource("certs/server-key.pem", outputDir.resolve("certs/server-key.pem"));
        copyClasspathResource(
                "certs/server-keystore.p12", outputDir.resolve("certs/server-keystore.p12"));
        copyClasspathResource(
                "wiremock/mappings/test.json", outputDir.resolve("wiremock/mappings/test.json"));
        copyClasspathResource(
                "wiremock/mappings/redirect.json",
                outputDir.resolve("wiremock/mappings/redirect.json"));
        copyClasspathResource(
                "wiremock/mappings/slow.json", outputDir.resolve("wiremock/mappings/slow.json"));
        copyClasspathResource(
                "wiremock/mappings/echo-headers.json",
                outputDir.resolve("wiremock/mappings/echo-headers.json"));
        copyClasspathResource(
                "wiremock/mappings/error-400.json",
                outputDir.resolve("wiremock/mappings/error-400.json"));
        copyClasspathResource(
                "wiremock/mappings/error-401.json",
                outputDir.resolve("wiremock/mappings/error-401.json"));
        copyClasspathResource(
                "wiremock/mappings/error-403.json",
                outputDir.resolve("wiremock/mappings/error-403.json"));
        copyClasspathResource(
                "wiremock/mappings/error-404.json",
                outputDir.resolve("wiremock/mappings/error-404.json"));
        copyClasspathResource(
                "wiremock/mappings/error-409.json",
                outputDir.resolve("wiremock/mappings/error-409.json"));
        copyClasspathResource(
                "wiremock/mappings/error-418.json",
                outputDir.resolve("wiremock/mappings/error-418.json"));
        copyClasspathResource(
                "wiremock/mappings/error-422.json",
                outputDir.resolve("wiremock/mappings/error-422.json"));
        copyClasspathResource(
                "wiremock/mappings/error-500.json",
                outputDir.resolve("wiremock/mappings/error-500.json"));
        copyClasspathResource(
                "wiremock/mappings/error-502.json",
                outputDir.resolve("wiremock/mappings/error-502.json"));
        copyClasspathResource(
                "wiremock/mappings/text-plain.json",
                outputDir.resolve("wiremock/mappings/text-plain.json"));
        copyClasspathResource(
                "wiremock/mappings/echo-body.json",
                outputDir.resolve("wiremock/mappings/echo-body.json"));
        copyClasspathResource("proxy/squid.conf", outputDir.resolve("proxy/squid.conf"));
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
        copySharedResources(outputDir);
    }

    @Test
    void generatePythonClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/python").toAbsolutePath();
        generateClient(
                "python-plus",
                Map.of("packageName", "petstore_client", "projectName", "petstore-client"),
                outputDir);
        copySharedResources(outputDir);
    }

    @Test
    void generateRubyClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/ruby").toAbsolutePath();
        generateClient(
                "ruby-plus",
                Map.of("gemName", "petstore_client", "moduleName", "PetstoreClient"),
                outputDir);
        copySharedResources(outputDir);
    }

    @Test
    void generatePhpClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/php").toAbsolutePath();
        generateClient("php-plus", Map.of("invokerPackage", "PetstoreClient"), outputDir);
        copySharedResources(outputDir);
    }

    @Test
    void generateNodeClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/node").toAbsolutePath();
        generateClient("node-plus", Map.of(), outputDir);
        copySharedResources(outputDir);
    }

    @Test
    void generateCSharpClient() throws IOException {
        Path outputDir = Path.of("src/spec/resources/generated/csharp").toAbsolutePath();
        generateClient(
                "csharp-plus",
                Map.of("packageName", "PetstoreClient", "sourceFolder", "src"),
                outputDir);
        copySharedResources(outputDir);
    }
}

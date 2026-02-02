package io.github.mridang.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

public class GenerateClientsTest {

    @Test
    void generateAllClients(@TempDir Path tempDir) throws IOException {
        String spec = "src/spec/resources/specs/petstore/openapi.yaml";

        // Python
        generate("python-plus", spec, tempDir.resolve("python").toString(),
            Map.of("packageName", "petstore_client"));

        // Java
        generate("java-plus", spec, tempDir.resolve("java").toString(),
            Map.of("modelPackage", "com.example.petstore.model",
                   "apiPackage", "com.example.petstore.api",
                   "invokerPackage", "com.example.petstore"));

        // PHP
        generate("php-plus", spec, tempDir.resolve("php").toString(),
            Map.of("invokerPackage", "PetstoreClient"));

        // Ruby
        generate("ruby-plus", spec, tempDir.resolve("ruby").toString(),
            Map.of("gemName", "petstore_client", "moduleName", "PetstoreClient"));

        // Copy to /tmp for viewing
        Path dest = Paths.get("/tmp/generated-clients");
        if (Files.exists(dest)) {
            deleteRecursively(dest);
        }
        copyRecursively(tempDir, dest);

        System.out.println("\n=== Generated files at /tmp/generated-clients ===");
    }

    void generate(String generator, String spec, String output, Map<String, Object> props) {
        System.out.println("Generating " + generator + " to " + output);
        CodegenConfigurator configurator = new CodegenConfigurator()
            .setGeneratorName(generator)
            .setInputSpec(spec)
            .setOutputDir(output)
            .setAdditionalProperties(props);

        DefaultGenerator gen = new DefaultGenerator();
        gen.setGenerateMetadata(false);
        gen.opts(configurator.toClientOptInput()).generate();
    }

    void copyRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

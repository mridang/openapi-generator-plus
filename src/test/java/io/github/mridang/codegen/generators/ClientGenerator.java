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
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

/**
 * Helper used by the per-language {@code GenerateXxxClientTest} classes. Each test class
 * is a single-method JUnit class with a {@code ResourceLock("generated-xxx")} so that the
 * language's downstream {@code XxxClientSpec} / {@code XxxBuildSpec} / etc. start as soon
 * as that language's codegen finishes, instead of waiting for every other language's
 * codegen to complete.
 *
 * <p>Splitting the previous single {@code GenerateClientsTest} into 12 thin classes lets
 * the per-language codegen + spec pipelines overlap: while Rust is still being generated,
 * Java's specs can already be running.
 */
public final class ClientGenerator {

  private static final String SPEC_RESOURCE = "specs/petstore/openapi.yaml";

  private ClientGenerator() {}

  /**
   * Regenerates the SDK for {@code generatorName} into {@code outputDir}. The directory
   * is cleaned first (preserving {@code .out/}), then {@link DefaultGenerator} runs the
   * configured codegen.
   */
  public static void generateClient(
      String generatorName, Map<String, Object> properties, Path outputDir) throws IOException {
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

    URL specUrl = ClientGenerator.class.getClassLoader().getResource(SPEC_RESOURCE);
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
          public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
            Files.delete(d);
            return FileVisitResult.CONTINUE;
          }
        });
  }
}

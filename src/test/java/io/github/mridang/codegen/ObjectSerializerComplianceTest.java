package io.github.mridang.codegen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

/**
 * Compliance test that verifies all generated clients route serialization/deserialization through
 * ObjectSerializer rather than doing inline serde.
 */
public class ObjectSerializerComplianceTest {

  private static final Path OUTPUT_DIR = Paths.get("/tmp/compliance-test-clients");

  @BeforeAll
  static void generateAllClients() throws IOException {
    if (Files.exists(OUTPUT_DIR)) {
      deleteRecursively(OUTPUT_DIR);
    }
    Files.createDirectories(OUTPUT_DIR);

    String spec = "src/spec/resources/specs/petstore/openapi.yaml";

    generate(
        "python-plus",
        spec,
        OUTPUT_DIR.resolve("python").toString(),
        Map.of("packageName", "petstore_client"));

    generate(
        "java-plus",
        spec,
        OUTPUT_DIR.resolve("java").toString(),
        Map.of(
            "modelPackage", "com.example.petstore.models",
            "apiPackage", "com.example.petstore.api",
            "invokerPackage", "com.example.petstore"));

    generate(
        "php-plus",
        spec,
        OUTPUT_DIR.resolve("php").toString(),
        Map.of("invokerPackage", "PetstoreClient"));

    generate(
        "ruby-plus",
        spec,
        OUTPUT_DIR.resolve("ruby").toString(),
        Map.of("gemName", "petstore_client", "moduleName", "PetstoreClient"));

    generate(
        "node-plus",
        spec,
        OUTPUT_DIR.resolve("node").toString(),
        Map.of());
  }

  static void generate(String generator, String spec, String output, Map<String, Object> props) {
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

  static void deleteRecursively(Path path) throws IOException {
    Files.walkFileTree(
        path,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
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

  // --- 1. ObjectSerializer file exists in each language ---

  @Test
  void pythonObjectSerializerExists() {
    assertTrue(
        Files.exists(OUTPUT_DIR.resolve("python/petstore_client/object_serializer.py")),
        "Python ObjectSerializer must exist");
  }

  @Test
  void javaObjectSerializerExists() {
    assertTrue(
        Files.exists(
            OUTPUT_DIR.resolve(
                "java/src/main/java/com/example/petstore/ObjectSerializer.java")),
        "Java ObjectSerializer must exist");
  }

  @Test
  void rubyObjectSerializerExists() {
    assertTrue(
        Files.exists(OUTPUT_DIR.resolve("ruby/lib/petstore_client/object_serializer.rb")),
        "Ruby ObjectSerializer must exist");
  }

  @Test
  void nodeObjectSerializerExists() {
    assertTrue(
        Files.exists(OUTPUT_DIR.resolve("node/ObjectSerializer.ts")),
        "Node ObjectSerializer must exist");
  }

  @Test
  void phpObjectSerializerExists() {
    assertTrue(
        Files.exists(OUTPUT_DIR.resolve("php/lib/ObjectSerializer.php")),
        "PHP ObjectSerializer must exist");
  }

  // --- 2. Serde entry points reference ObjectSerializer ---

  @Test
  void pythonBaseApiDelegatesToObjectSerializer() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("python/petstore_client/api/base_api.py"));
    assertTrue(
        content.contains("from ..object_serializer import ObjectSerializer"),
        "Python base_api.py must import ObjectSerializer");
    assertTrue(
        content.contains("_object_serializer"),
        "Python base_api.py must use _object_serializer");
  }

  @Test
  void javaBaseApiDelegatesToObjectSerializer() throws IOException {
    String content =
        Files.readString(
            OUTPUT_DIR.resolve(
                "java/src/main/java/com/example/petstore/api/BaseApi.java"));
    assertTrue(
        content.contains("objectSerializer.serialize"),
        "Java BaseApi must use objectSerializer.serialize()");
    assertTrue(
        content.contains("objectSerializer.deserialize"),
        "Java BaseApi must use objectSerializer.deserialize()");
  }

  @Test
  void rubyBaseApiReferencesObjectSerializer() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("ruby/lib/petstore_client/api/base_api.rb"));
    assertTrue(
        content.contains("ObjectSerializer."),
        "Ruby base_api.rb must reference ObjectSerializer");
  }

  @Test
  void nodeApiFilesReferenceObjectSerializer() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("node");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("Api.ts"))
          .filter(p -> !p.getFileName().toString().equals("BaseApi.ts"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("ObjectSerializer."),
                      "Node " + p.getFileName() + " must reference ObjectSerializer");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void phpApiFilesReferenceObjectSerializer() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("php/lib/Api");
    if (Files.exists(apiDir)) {
      try (Stream<Path> files = Files.walk(apiDir)) {
        files
            .filter(p -> p.getFileName().toString().endsWith("Api.php"))
            .filter(p -> !p.getFileName().toString().equals("BaseApi.php"))
            .forEach(
                p -> {
                  try {
                    String content = Files.readString(p);
                    assertTrue(
                        content.contains("ObjectSerializer::"),
                        "PHP " + p.getFileName() + " must reference ObjectSerializer");
                  } catch (IOException e) {
                    fail("Could not read " + p);
                  }
                });
      }
    }
  }

  // --- 3. API files don't do inline serde ---

  @Test
  void javaApiFilesNoInlineSerde() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("java/src/main/java/com/example/petstore/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("Api.java"))
          .filter(p -> !p.getFileName().toString().equals("BaseApi.java"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertFalse(
                      content.contains("objectMapper."),
                      "Java " + p.getFileName() + " must NOT use objectMapper directly");
                  assertFalse(
                      content.contains("new ObjectMapper"),
                      "Java " + p.getFileName() + " must NOT instantiate ObjectMapper");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void pythonApiFilesNoInlineSerde() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("python/petstore_client/api");
    if (Files.exists(apiDir)) {
      try (Stream<Path> files = Files.walk(apiDir)) {
        files
            .filter(p -> p.getFileName().toString().endsWith("_api.py"))
            .filter(p -> !p.getFileName().toString().equals("base_api.py"))
            .forEach(
                p -> {
                  try {
                    String content = Files.readString(p);
                    assertFalse(
                        content.contains("json.loads("),
                        "Python " + p.getFileName() + " must NOT use json.loads() directly");
                    assertFalse(
                        content.contains("json.dumps("),
                        "Python " + p.getFileName() + " must NOT use json.dumps() directly");
                  } catch (IOException e) {
                    fail("Could not read " + p);
                  }
                });
      }
    }
  }

  @Test
  void rubyApiFilesNoInlineSerde() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("ruby/lib/petstore_client/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("_api.rb"))
          .filter(p -> !p.getFileName().toString().equals("base_api.rb"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertFalse(
                      content.contains("JSON.parse("),
                      "Ruby " + p.getFileName() + " must NOT use JSON.parse() directly");
                  assertFalse(
                      content.contains("JSON.generate("),
                      "Ruby " + p.getFileName() + " must NOT use JSON.generate() directly");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void nodeApiFilesNoInlineSerde() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("node/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("-api.ts"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertFalse(
                      content.contains("JSON.parse("),
                      "Node " + p.getFileName() + " must NOT use JSON.parse() directly");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }
}

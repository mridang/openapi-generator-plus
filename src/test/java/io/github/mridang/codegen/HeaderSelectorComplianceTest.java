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
 * Compliance test that verifies all generated clients have a standalone HeaderSelector class with
 * RFC 9110 compliant content negotiation, and that API entry points delegate to it rather than
 * implementing inline header selection logic.
 */
public class HeaderSelectorComplianceTest {

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

  // --- 1. HeaderSelector file exists in each language ---

  @Test
  void pythonHeaderSelectorExists() {
    assertTrue(
        Files.exists(OUTPUT_DIR.resolve("python/petstore_client/header_selector.py")),
        "Python HeaderSelector must exist");
  }

  @Test
  void javaHeaderSelectorExists() {
    assertTrue(
        Files.exists(
            OUTPUT_DIR.resolve(
                "java/src/main/java/com/example/petstore/HeaderSelector.java")),
        "Java HeaderSelector must exist");
  }

  @Test
  void phpHeaderSelectorExists() {
    assertTrue(
        Files.exists(OUTPUT_DIR.resolve("php/lib/HeaderSelector.php")),
        "PHP HeaderSelector must exist");
  }

  @Test
  void rubyHeaderSelectorExists() {
    assertTrue(
        Files.exists(OUTPUT_DIR.resolve("ruby/lib/petstore_client/header_selector.rb")),
        "Ruby HeaderSelector must exist");
  }

  @Test
  void nodeHeaderSelectorExists() {
    assertTrue(
        Files.exists(OUTPUT_DIR.resolve("node/HeaderSelector.ts")),
        "Node HeaderSelector must exist");
  }

  // --- 2. API entry points reference HeaderSelector ---

  @Test
  void pythonBaseApiDelegatesToHeaderSelector() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("python/petstore_client/api/base_api.py"));
    assertTrue(
        content.contains("from ..header_selector import HeaderSelector"),
        "Python base_api.py must import HeaderSelector");
    assertTrue(
        content.contains("_header_selector"),
        "Python base_api.py must use _header_selector");
  }

  @Test
  void javaBaseApiDelegatesToHeaderSelector() throws IOException {
    String content =
        Files.readString(
            OUTPUT_DIR.resolve(
                "java/src/main/java/com/example/petstore/api/BaseApi.java"));
    assertTrue(
        content.contains("HeaderSelector"),
        "Java BaseApi must reference HeaderSelector");
    assertTrue(
        content.contains("headerSelector"),
        "Java BaseApi must use headerSelector field");
  }

  @Test
  void phpBaseApiDelegatesToHeaderSelector() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("php/lib/Api/BaseApi.php"));
    assertTrue(
        content.contains("HeaderSelector"),
        "PHP BaseApi.php must reference HeaderSelector");
    assertTrue(
        content.contains("headerSelector"),
        "PHP BaseApi.php must use headerSelector field");
  }

  @Test
  void rubyBaseApiDelegatesToHeaderSelector() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("ruby/lib/petstore_client/api/base_api.rb"));
    assertTrue(
        content.contains("HeaderSelector"),
        "Ruby base_api.rb must reference HeaderSelector");
  }

  @Test
  void nodeBaseApiDelegatesToHeaderSelector() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("node/api/BaseApi.ts"));
    assertTrue(
        content.contains("HeaderSelector"),
        "Node BaseApi.ts must reference HeaderSelector");
    assertTrue(
        content.contains("headerSelector"),
        "Node BaseApi.ts must use headerSelector field");
  }

  // --- 3. API files don't have inline header selection logic ---

  @Test
  void phpApiFilesNoInlineHeaderSelection() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("php/lib/Api");
    if (Files.exists(apiDir)) {
      try (Stream<Path> files = Files.walk(apiDir)) {
        files
            .filter(p -> p.getFileName().toString().endsWith("Api.php"))
            .forEach(
                p -> {
                  try {
                    String content = Files.readString(p);
                    assertFalse(
                        content.contains("function selectAcceptHeader"),
                        "PHP " + p.getFileName() + " must NOT have inline selectAcceptHeader");
                    assertFalse(
                        content.contains("function isJsonMime"),
                        "PHP " + p.getFileName() + " must NOT have inline isJsonMime");
                  } catch (IOException e) {
                    fail("Could not read " + p);
                  }
                });
      }
    }
  }

  // --- 4. Structural checks on generated HeaderSelector files ---

  @Test
  void pythonHeaderSelectorHasRequiredMethods() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("python/petstore_client/header_selector.py"));
    assertTrue(content.contains("def select_headers("), "Python must have select_headers");
    assertTrue(content.contains("def is_json_mime("), "Python must have is_json_mime");
    assertTrue(content.contains("def get_next_weight("), "Python must have get_next_weight");
    assertTrue(content.contains("_select_accept_header"), "Python must have _select_accept_header");
    assertTrue(content.contains("_get_accept_header_with_adjusted_weight"), "Python must have _get_accept_header_with_adjusted_weight");
    assertTrue(content.contains("_adjust_weight"), "Python must have _adjust_weight");
    assertTrue(content.contains("_build_accept_header"), "Python must have _build_accept_header");
    assertTrue(content.contains("HeaderData"), "Python must have HeaderData");
  }

  @Test
  void javaHeaderSelectorHasRequiredMethods() throws IOException {
    String content =
        Files.readString(
            OUTPUT_DIR.resolve(
                "java/src/main/java/com/example/petstore/HeaderSelector.java"));
    assertTrue(content.contains("selectHeaders("), "Java must have selectHeaders");
    assertTrue(content.contains("isJsonMime("), "Java must have isJsonMime");
    assertTrue(content.contains("getNextWeight("), "Java must have getNextWeight");
    assertTrue(content.contains("selectAcceptHeader("), "Java must have selectAcceptHeader");
    assertTrue(content.contains("getAcceptHeaderWithAdjustedWeight("), "Java must have getAcceptHeaderWithAdjustedWeight");
    assertTrue(content.contains("adjustWeight("), "Java must have adjustWeight");
    assertTrue(content.contains("buildAcceptHeader("), "Java must have buildAcceptHeader");
    assertTrue(content.contains("class HeaderData"), "Java must have HeaderData");
  }

  @Test
  void phpHeaderSelectorHasRequiredMethods() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("php/lib/HeaderSelector.php"));
    assertTrue(content.contains("function selectHeaders("), "PHP must have selectHeaders");
    assertTrue(content.contains("function isJsonMime("), "PHP must have isJsonMime");
    assertTrue(content.contains("function getNextWeight("), "PHP must have getNextWeight");
    assertTrue(content.contains("function selectAcceptHeader("), "PHP must have selectAcceptHeader");
    assertTrue(content.contains("function getAcceptHeaderWithAdjustedWeight("), "PHP must have getAcceptHeaderWithAdjustedWeight");
    assertTrue(content.contains("function adjustWeight("), "PHP must have adjustWeight");
    assertTrue(content.contains("function buildAcceptHeader("), "PHP must have buildAcceptHeader");
  }

  @Test
  void rubyHeaderSelectorHasRequiredMethods() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("ruby/lib/petstore_client/header_selector.rb"));
    assertTrue(content.contains("def select_headers("), "Ruby must have select_headers");
    assertTrue(content.contains("def json_mime?("), "Ruby must have json_mime?");
    assertTrue(content.contains("def get_next_weight("), "Ruby must have get_next_weight");
    assertTrue(content.contains("def select_accept_header("), "Ruby must have select_accept_header");
    assertTrue(content.contains("def get_accept_header_with_adjusted_weight("), "Ruby must have get_accept_header_with_adjusted_weight");
    assertTrue(content.contains("def adjust_weight("), "Ruby must have adjust_weight");
    assertTrue(content.contains("def build_accept_header("), "Ruby must have build_accept_header");
    assertTrue(content.contains("HeaderData"), "Ruby must have HeaderData");
  }

  @Test
  void nodeHeaderSelectorHasRequiredMethods() throws IOException {
    String content =
        Files.readString(OUTPUT_DIR.resolve("node/HeaderSelector.ts"));
    assertTrue(content.contains("selectHeaders("), "Node must have selectHeaders");
    assertTrue(content.contains("isJsonMime("), "Node must have isJsonMime");
    assertTrue(content.contains("getNextWeight("), "Node must have getNextWeight");
    assertTrue(content.contains("selectAcceptHeader("), "Node must have selectAcceptHeader");
    assertTrue(content.contains("getAcceptHeaderWithAdjustedWeight("), "Node must have getAcceptHeaderWithAdjustedWeight");
    assertTrue(content.contains("adjustWeight("), "Node must have adjustWeight");
    assertTrue(content.contains("buildAcceptHeader("), "Node must have buildAcceptHeader");
    assertTrue(content.contains("HeaderData"), "Node must have HeaderData");
  }
}

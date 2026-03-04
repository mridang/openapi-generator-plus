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
 * Compliance test that verifies all generated clients follow the unified BaseApi + ApiClient
 * architecture: a pluggable ApiClient interface, a DefaultApiClient HTTP transport implementation,
 * a BaseApi with invokeApi, and thin API classes that extend BaseApi.
 */
public class BaseApiComplianceTest {

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
            "modelPackage", "com.example.petstore.model",
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

  // --- 1. File existence: BaseApi, ApiClient, DefaultApiClient, ApiResponse ---

  @Test
  void javaFilesExist() {
    String base = "java/src/main/java/com/example/petstore/";
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "BaseApi.java")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "ApiClient.java")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "DefaultApiClient.java")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "ApiResponse.java")));
  }

  @Test
  void pythonFilesExist() {
    String base = "python/petstore_client/";
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "base_api.py")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "api_client.py")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "default_api_client.py")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "api_response.py")));
  }

  @Test
  void phpFilesExist() {
    String base = "php/lib/";
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "BaseApi.php")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "ApiClient.php")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "DefaultApiClient.php")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "ApiResponse.php")));
  }

  @Test
  void rubyFilesExist() {
    String base = "ruby/lib/petstore_client/";
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "base_api.rb")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "api_client.rb")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "default_api_client.rb")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve(base + "api_response.rb")));
  }

  @Test
  void nodeFilesExist() {
    assertTrue(Files.exists(OUTPUT_DIR.resolve("node/BaseApi.ts")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve("node/ApiClient.ts")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve("node/DefaultApiClient.ts")));
    assertTrue(Files.exists(OUTPUT_DIR.resolve("node/ApiResponse.ts")));
  }

  // --- 2. API classes extend BaseApi ---

  @Test
  void javaApiClassesExtendBaseApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("java/src/main/java/com/example/petstore/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("Api.java"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("extends BaseApi"),
                      p.getFileName() + " must extend BaseApi");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void pythonApiClassesExtendBaseApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("python/petstore_client/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("_api.py"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("(BaseApi)"),
                      p.getFileName() + " must extend BaseApi");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void phpApiClassesExtendBaseApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("php/lib/Api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("Api.php"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("extends BaseApi"),
                      p.getFileName() + " must extend BaseApi");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void rubyApiClassesExtendBaseApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("ruby/lib/petstore_client/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("_api.rb"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("BaseApi"),
                      p.getFileName() + " must extend BaseApi");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void nodeApiClassesExtendBaseApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("node/apis");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("-api.ts"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("extends BaseApi"),
                      p.getFileName() + " must extend BaseApi");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  // --- 3. API classes use invokeApi ---

  @Test
  void javaApiClassesUseInvokeApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("java/src/main/java/com/example/petstore/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("Api.java"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("invokeApi("),
                      p.getFileName() + " must call invokeApi");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void pythonApiClassesUseInvokeApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("python/petstore_client/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("_api.py"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("invoke_api("),
                      p.getFileName() + " must call invoke_api");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void phpApiClassesUseInvokeApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("php/lib/Api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("Api.php"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("invokeApi("),
                      p.getFileName() + " must call invokeApi");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void rubyApiClassesUseInvokeApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("ruby/lib/petstore_client/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("_api.rb"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("invoke_api("),
                      p.getFileName() + " must call invoke_api");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void nodeApiClassesUseInvokeApi() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("node/apis");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("-api.ts"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertTrue(
                      content.contains("invokeApi("),
                      p.getFileName() + " must call invokeApi");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  // --- 4. No inline HTTP logic in API classes ---

  @Test
  void javaApiClassesNoInlineHttp() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("java/src/main/java/com/example/petstore/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("Api.java"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertFalse(
                      content.contains("sendRequest("),
                      p.getFileName() + " must NOT contain sendRequest");
                  assertFalse(
                      content.contains("httpClient"),
                      p.getFileName() + " must NOT reference httpClient");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void pythonApiClassesNoInlineHttp() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("python/petstore_client/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("_api.py"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertFalse(
                      content.contains("send_request("),
                      p.getFileName() + " must NOT contain send_request");
                  assertFalse(
                      content.contains("rest_client"),
                      p.getFileName() + " must NOT reference rest_client");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void phpApiClassesNoInlineHttp() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("php/lib/Api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("Api.php"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertFalse(
                      content.contains("sendRequest("),
                      p.getFileName() + " must NOT contain sendRequest");
                  assertFalse(
                      content.contains("executeRequest("),
                      p.getFileName() + " must NOT contain executeRequest");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void rubyApiClassesNoInlineHttp() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("ruby/lib/petstore_client/api");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("_api.rb"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertFalse(
                      content.contains("send_request("),
                      p.getFileName() + " must NOT contain send_request");
                  assertFalse(
                      content.contains("call_api("),
                      p.getFileName() + " must NOT contain call_api");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  @Test
  void nodeApiClassesNoInlineHttp() throws IOException {
    Path apiDir = OUTPUT_DIR.resolve("node/apis");
    try (Stream<Path> files = Files.walk(apiDir)) {
      files
          .filter(p -> p.getFileName().toString().endsWith("-api.ts"))
          .forEach(
              p -> {
                try {
                  String content = Files.readString(p);
                  assertFalse(
                      content.contains("sendRequest("),
                      p.getFileName() + " must NOT contain sendRequest");
                  assertFalse(
                      content.contains("this.request("),
                      p.getFileName() + " must NOT contain this.request()");
                } catch (IOException e) {
                  fail("Could not read " + p);
                }
              });
    }
  }

  // --- 5. Structural checks: BaseApi has invokeApi, ApiClient has sendRequest, DefaultApiClient has sendRequest ---

  @Test
  void javaStructuralChecks() throws IOException {
    String base = "java/src/main/java/com/example/petstore/";
    String baseApi = Files.readString(OUTPUT_DIR.resolve(base + "BaseApi.java"));
    assertTrue(baseApi.contains("invokeApi("), "BaseApi must have invokeApi method");

    String apiClient = Files.readString(OUTPUT_DIR.resolve(base + "ApiClient.java"));
    assertTrue(apiClient.contains("sendRequest("), "ApiClient must have sendRequest method");

    String defaultApiClient = Files.readString(OUTPUT_DIR.resolve(base + "DefaultApiClient.java"));
    assertTrue(defaultApiClient.contains("sendRequest("), "DefaultApiClient must have sendRequest method");
  }

  @Test
  void pythonStructuralChecks() throws IOException {
    String base = "python/petstore_client/";
    String baseApi = Files.readString(OUTPUT_DIR.resolve(base + "base_api.py"));
    assertTrue(baseApi.contains("invoke_api("), "base_api must have invoke_api method");

    String apiClient = Files.readString(OUTPUT_DIR.resolve(base + "api_client.py"));
    assertTrue(apiClient.contains("send_request("), "api_client must have send_request method");

    String defaultApiClient = Files.readString(OUTPUT_DIR.resolve(base + "default_api_client.py"));
    assertTrue(defaultApiClient.contains("send_request("), "default_api_client must have send_request method");
  }

  @Test
  void phpStructuralChecks() throws IOException {
    String base = "php/lib/";
    String baseApi = Files.readString(OUTPUT_DIR.resolve(base + "BaseApi.php"));
    assertTrue(baseApi.contains("invokeApi("), "BaseApi must have invokeApi method");

    String apiClient = Files.readString(OUTPUT_DIR.resolve(base + "ApiClient.php"));
    assertTrue(apiClient.contains("sendRequest("), "ApiClient must have sendRequest method");

    String defaultApiClient = Files.readString(OUTPUT_DIR.resolve(base + "DefaultApiClient.php"));
    assertTrue(defaultApiClient.contains("sendRequest("), "DefaultApiClient must have sendRequest method");
  }

  @Test
  void rubyStructuralChecks() throws IOException {
    String base = "ruby/lib/petstore_client/";
    String baseApi = Files.readString(OUTPUT_DIR.resolve(base + "base_api.rb"));
    assertTrue(baseApi.contains("invoke_api("), "base_api must have invoke_api method");

    String apiClient = Files.readString(OUTPUT_DIR.resolve(base + "api_client.rb"));
    assertTrue(apiClient.contains("send_request("), "api_client must have send_request method");

    String defaultApiClient = Files.readString(OUTPUT_DIR.resolve(base + "default_api_client.rb"));
    assertTrue(defaultApiClient.contains("send_request("), "default_api_client must have send_request method");
  }

  @Test
  void nodeStructuralChecks() throws IOException {
    String baseApi = Files.readString(OUTPUT_DIR.resolve("node/BaseApi.ts"));
    assertTrue(baseApi.contains("invokeApi"), "BaseApi must have invokeApi method");

    String apiClient = Files.readString(OUTPUT_DIR.resolve("node/ApiClient.ts"));
    assertTrue(apiClient.contains("sendRequest("), "ApiClient must have sendRequest method");

    String defaultApiClient = Files.readString(OUTPUT_DIR.resolve("node/DefaultApiClient.ts"));
    assertTrue(defaultApiClient.contains("sendRequest("), "DefaultApiClient must have sendRequest method");
  }
}

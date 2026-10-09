package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateJavaClientTest {

  /**
   * Holds the {@code generated-java} resource lock in READ_WRITE mode while writing the
   * SDK source. The {@code Java*Spec} classes acquire the same lock; the JUnit Platform
   * blocks them until this test releases its write lock — so they pick up exactly the
   * source this test produced and not a half-generated tree.
   */
  @Test
  @ResourceLock(value = "generated-java", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/java").toAbsolutePath();
    ClientGenerator.generateClient(
        "java-plus",
        Map.of(
            "modelPackage", "com.example.petstore.models",
            "apiPackage", "com.example.petstore.api",
            "invokerPackage", "com.example.petstore"),
        outputDir);
  }
}

package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateKotlinClientTest {

  @Test
  @ResourceLock(value = "generated-kotlin", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/kotlin").toAbsolutePath();
    ClientGenerator.generateClient(
        "kotlin-plus",
        Map.of(
            "modelPackage", "com.example.petstore.models",
            "apiPackage", "com.example.petstore.api",
            "invokerPackage", "com.example.petstore"),
        outputDir);
  }
}

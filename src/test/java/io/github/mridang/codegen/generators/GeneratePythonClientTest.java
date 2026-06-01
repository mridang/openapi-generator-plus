package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GeneratePythonClientTest {

  @Test
  @ResourceLock(value = "generated-python", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/python").toAbsolutePath();
    ClientGenerator.generateClient(
        "python-plus",
        Map.of("packageName", "petstore_client", "projectName", "petstore-client"),
        outputDir);
  }
}

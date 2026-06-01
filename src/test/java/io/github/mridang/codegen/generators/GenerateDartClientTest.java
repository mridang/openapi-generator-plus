package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateDartClientTest {

  @Test
  @ResourceLock(value = "generated-dart", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/dart").toAbsolutePath();
    ClientGenerator.generateClient("dart-plus", Map.of("packageName", "petstore_client"), outputDir);
  }
}

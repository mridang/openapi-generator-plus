package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateNodeClientTest {

  @Test
  @ResourceLock(value = "generated-node", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/node").toAbsolutePath();
    ClientGenerator.generateClient("node-plus", Map.of("npmName", "petstore-client"), outputDir);
  }
}

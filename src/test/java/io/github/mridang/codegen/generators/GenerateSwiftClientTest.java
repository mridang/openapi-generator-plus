package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateSwiftClientTest {

  @Test
  @ResourceLock(value = "generated-swift", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/swift").toAbsolutePath();
    ClientGenerator.generateClient("swift-plus", Map.of("packageName", "PetstoreClient"), outputDir);
  }
}

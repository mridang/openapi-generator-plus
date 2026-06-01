package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateCSharpClientTest {

  @Test
  @ResourceLock(value = "generated-csharp", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/csharp").toAbsolutePath();
    ClientGenerator.generateClient(
        "csharp-plus", Map.of("packageName", "PetstoreClient", "sourceFolder", "src"), outputDir);
  }
}

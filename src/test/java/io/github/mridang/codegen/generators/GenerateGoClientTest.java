package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateGoClientTest {

  @Test
  @ResourceLock(value = "generated-go", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/go").toAbsolutePath();
    ClientGenerator.generateClient("go-plus", Map.of("packageName", "petstore"), outputDir);
  }
}

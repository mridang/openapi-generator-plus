package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GeneratePhpClientTest {

  @Test
  @ResourceLock(value = "generated-php", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/php").toAbsolutePath();
    ClientGenerator.generateClient("php-plus", Map.of("invokerPackage", "PetstoreClient"), outputDir);
  }
}

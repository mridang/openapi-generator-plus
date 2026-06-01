package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class GenerateRubyClientTest {

  @Test
  @ResourceLock(value = "generated-ruby", mode = ResourceAccessMode.READ_WRITE)
  void generate() throws IOException {
    Path outputDir = Path.of("src/spec/resources/generated/ruby").toAbsolutePath();
    ClientGenerator.generateClient(
        "ruby-plus",
        Map.of("gemName", "petstore_client", "moduleName", "PetstoreClient"),
        outputDir);
  }
}

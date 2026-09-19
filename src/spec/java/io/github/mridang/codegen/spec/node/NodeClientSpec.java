package io.github.mridang.codegen.spec.node;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-node")
public class NodeClientSpec extends AbstractClientSpec implements NodeSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "npx jest --verbose",
      "mv .out/cobertura-coverage.xml .out/coverage.xml"
    };
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("src/api")).exists();
    assertThat(outputDir.resolve("src/models")).exists();
  }
}

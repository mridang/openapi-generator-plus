package io.github.mridang.codegen.spec.node;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class NodeClientSpec extends AbstractClientSpec implements NodeSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "npm install",
      "API_BASE_URL=http://prism:4010 npx jest --verbose"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/nodetest");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of();
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("api")).exists();
    assertThat(outputDir.resolve("models")).exists();
  }
}

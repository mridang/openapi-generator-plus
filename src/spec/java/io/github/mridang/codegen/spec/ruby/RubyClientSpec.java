package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class RubyClientSpec extends AbstractClientSpec implements RubySpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "bundle install --quiet",
      "API_BASE_URL=http://prism:4010 bundle exec rspec --format documentation"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/rspec");
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.GEM_NAME, "opigen_client",
        CodegenConstants.MODULE_NAME, "OpigenClient");
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("lib")).exists();
  }
}

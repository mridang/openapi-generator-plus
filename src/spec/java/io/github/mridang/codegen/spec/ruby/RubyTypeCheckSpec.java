package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated Ruby code passes Steep type checking with RBS signatures. If this test
 * fails, the Ruby templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class RubyTypeCheckSpec extends AbstractIntegrationSpec {

  @Override
  protected String getGeneratorName() {
    return "ruby-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("ruby:3.4-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "apt-get update && apt-get install -y build-essential --no-install-recommends 2>/dev/null",
      "bundle install --quiet",
      "mkdir -p sig"
          + " && find lib/opigen_client/models lib/opigen_client/api"
          + " -name '*.rb' ! -name 'base_api.rb'"
          + " -exec rbs prototype rb {} + > sig/generated.rbs",
      "bundle exec steep check"
    };
  }

  @Test
  void generatedCodeShouldPassTypeChecking() {
    generateClientToDirectory(
        Map.of("gemName", "opigen_client", "moduleName", "OpigenClient"), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Ruby code has type checking errors:\n%s", result.output())
        .isTrue();
  }
}

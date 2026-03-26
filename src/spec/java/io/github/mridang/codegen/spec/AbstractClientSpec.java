package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Base class for client integration specs. Validates that the pre-generated SDK
 * (committed to git) has the expected structure, then runs language-native tests
 * inside a Docker container. No code generation happens here — that is done once
 * via {@code GenerateClientsTest}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractClientSpec extends AbstractIntegrationSpec {

  protected abstract void assertGeneratedStructure(Path outputDir);

  @Test
  @Order(1)
  void shouldGenerateClient() {
    assertGeneratedStructure(tempOutputDir);
  }

  @Test
  @Order(2)
  void shouldRunClientTests() throws IOException {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Client tests failed:\n%s", result.output())
        .isTrue();
  }
}

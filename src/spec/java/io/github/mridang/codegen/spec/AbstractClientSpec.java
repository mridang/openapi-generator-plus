package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Base class for client integration specs. Provides the shared test structure:
 * copy test project, generate client, assert structure, then run tests against Prism.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractClientSpec extends AbstractIntegrationSpec {

  protected abstract Path getTestProjectPath();

  protected abstract Map<String, Object> getCodegenProperties();

  protected abstract void assertGeneratedStructure(Path outputDir);

  @BeforeEach
  void copyTestProject() throws IOException {
    copyDirectory(getTestProjectPath(), tempOutputDir);
  }

  @Test
  @Order(1)
  void shouldGenerateClient() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);
    assertGeneratedStructure(tempOutputDir);
  }

  @Test
  @Order(2)
  void shouldRunClientTests() {
    startPrismServer();
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Client tests failed:\n%s", result.output())
        .isTrue();
  }
}

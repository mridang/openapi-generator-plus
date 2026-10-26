package io.github.mridang.codegen.spec;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Base class for integration specs. Each generated SDK lives in
 * {@code src/spec/resources/generated/{lang}/} and is already fully self-contained
 * (source, tests, certs, specs). Code generation is done once
 * via {@code GenerateClientsTest}; this class only runs tests against the committed code.
 */
@SuppressWarnings("NullAway.Init")
public abstract class AbstractIntegrationSpec implements LanguageSpec {

  protected static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationSpec.class);

  protected Path tempOutputDir;

  protected abstract String[] getBuildCommands();

  @BeforeEach
  void resolveOutputDir() {
    String lang = getGeneratorName().replace("-plus", "");
    tempOutputDir = Path.of("src/spec/resources/generated/" + lang).toAbsolutePath();
  }

  @BeforeEach
  void logTestContext(TestInfo testInfo) {
    logger.info("========================================");
    logger.info("Test: {}", testInfo.getDisplayName());
    logger.info("Generator: {}", getGeneratorName());
    logger.info("Output directory: {}", tempOutputDir.toAbsolutePath());
    logger.info("========================================");
  }

  protected ExecResult executeInRuntimeContainer(String[] commands) {
    GenericContainer<?> container = SharedRuntimeContainer.getOrCreate(this, tempOutputDir);
    return SharedRuntimeContainer.execInContainer(container, tempOutputDir, commands);
  }

  public static class ExecResult {
    private final int exitCode;
    private final String output;

    public ExecResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }

    public String output() {
      return output;
    }

    public boolean isSuccess() {
      return exitCode == 0;
    }
  }
}

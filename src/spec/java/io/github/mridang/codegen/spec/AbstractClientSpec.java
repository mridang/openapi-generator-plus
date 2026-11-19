package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.parity.ParityReportParser;
import io.github.mridang.codegen.spec.parity.ParityStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    /* Clear the previous run's reports first, so the summary below can only
     * count reports this run wrote. */
    Path reportsDir = tempOutputDir.resolve(".out/reports");
    if (Files.isDirectory(reportsDir)) {
      try (Stream<Path> reports = Files.list(reportsDir)) {
        for (Path report : reports.filter(p -> p.toString().endsWith(".xml")).collect(Collectors.toList())) {
          Files.delete(report);
        }
      }
    }

    String language = getGeneratorName().replace("-plus", "");
    ParityStore parityStore = ParityStore.defaultStore();
    parityStore.markRunning(language);
    ExecResult result;
    try {
      result = executeInRuntimeContainer(getBuildCommands());

      JUnitReportPrinter.printReport(reportsDir, getGeneratorName(), logger);
    } finally {
      /* Persist what the language ran, whatever happened, so the comparison spec
       * that runs after every client spec can compare the suites without running
       * them again, and sees a run that wrote no report as zero tests. */
      parityStore.write(ParityReportParser.parse(language, reportsDir, System.currentTimeMillis()));
      keepReports(reportsDir, language);
    }

    assertThat(result.isSuccess())
        .withFailMessage("Client tests failed:\n%s", result.output())
        .isTrue();
  }

  /**
   * Copies the language's JUnit XML reports to {@code target/test-reports/<language>} so a failed
   * run can be read with {@code etc/test-failures.py} after the temporary output folder is gone.
   */
  private static void keepReports(Path reportsDir, String language) throws IOException {
    if (!Files.isDirectory(reportsDir)) {
      return;
    }
    Path kept = Path.of("target/test-reports", language).toAbsolutePath();
    Files.createDirectories(kept);
    try (Stream<Path> reports = Files.list(reportsDir)) {
      for (Path report : reports.filter(p -> p.toString().endsWith(".xml")).collect(Collectors.toList())) {
        Files.copy(report, kept.resolve(report.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }
}

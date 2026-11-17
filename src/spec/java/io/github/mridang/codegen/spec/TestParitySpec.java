package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.mridang.codegen.spec.parity.ParityAllowList;
import io.github.mridang.codegen.spec.parity.ParityComparison;
import io.github.mridang.codegen.spec.parity.ParitySummary;
import io.github.mridang.codegen.spec.parity.ParityStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares the test suites of the generated SDKs. Each client spec persists the canonical keys of
 * the tests its language ran under {@code target/test-parity}; this spec reads them after every
 * client spec of the run has finished (it is ordered last and isolated, and waits for any
 * language still marked as running), and fails when the languages disagree beyond what
 * {@code test-parity-allow.json} lists. A run of fewer than all twelve languages compares only
 * the languages present, and is skipped when fewer than two are.
 */
@Isolated
public class TestParitySpec {

  private static final Logger logger = LoggerFactory.getLogger(TestParitySpec.class);
  private static final Path ALLOW_FILE = Path.of("src/spec/resources/test-parity-allow.json");
  private static final long WAIT_MILLIS = 30L * 60 * 1000;

  @Test
  void shouldHaveTheSameTestsInEveryLanguage() throws IOException, InterruptedException {
    ParityStore store = ParityStore.defaultStore();
    waitForRunningSpecs(store);

    Map<String, ParitySummary> present = store.readSince(ParityStore.jvmStartTime());
    assumeTrue(
        present.size() >= 2,
        "Test parity needs the reports of at least two languages in this run, found "
            + present.keySet()
            + "; run the client specs of two or more languages to compare them");
    logger.info("Comparing the test suites of {}", present.keySet());

    ParityAllowList allow = ParityAllowList.load(ALLOW_FILE.toAbsolutePath());
    ParityComparison.Result result = ParityComparison.compare(present, allow);
    ParityComparison.describe(result.gaps()).forEach(line -> logger.info("{}", line));

    assertThat(result.problems())
        .withFailMessage(
            "The test suites of %s disagree. Add the missing tests, or list the gap with a reason"
                + " in %s:%n%s%n%nFor reference:%n%s",
            present.keySet(),
            ALLOW_FILE,
            String.join("\n", result.problems()),
            String.join("\n", ParityComparison.describe(result.gaps())))
        .isEmpty();
  }

  private static void waitForRunningSpecs(ParityStore store)
      throws IOException, InterruptedException {
    long deadline = System.currentTimeMillis() + WAIT_MILLIS;
    while (!store.running().isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(2000);
    }
    assertThat(store.running())
        .withFailMessage("Client specs were still running after %d minutes", WAIT_MILLIS / 60000)
        .isEmpty();
    assertThat(Files.exists(ALLOW_FILE)).withFailMessage("Missing %s", ALLOW_FILE).isTrue();
  }
}

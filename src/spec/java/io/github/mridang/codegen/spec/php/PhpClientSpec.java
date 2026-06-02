package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
public class PhpClientSpec extends AbstractClientSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    /* `--testdox --parallel` together leak PHP-serialize()'d
     * TestResultCollection blobs onto stdout. Verified by repro in
     * docker against the actual locked versions (pest 4.7.2 +
     * paratest 7.20.0 + phpunit 12.5.28):
     *
     *   --testdox --parallel  →  ~100 KB output incl. raw
     *                            `a:1:{s:32:"...";O:44:"PHPUnit\
     *                            Logging\TestDox\TestResultCollection
     *                            ":...}` blob
     *   --testdox alone       →  clean prose
     *   --parallel alone      →  clean dots
     *
     * Cause: paratest's WrapperWorker writes the testdox collection
     * to its per-worker testdoxFile via `serialize($collection)`
     * (ApplicationForWrapperWorker.php:262). The contract is for the
     * parent runner to read those files back, `unserialize`, merge,
     * then render via PHPUnit's TestDoxResultPrinter. Pest's
     * Plugins/Parallel/Paratest/ResultPrinter.php:167 just
     * concatenates the raw bytes onto stdout — no unserialize, no
     * render. Bug ships in every Pest 4.x release, all of which pin
     * paratest ^7.20.0; no fixed version exists upstream.
     *
     * Keeping `--parallel` for the speedup; dropping `--testdox`
     * gives back the regular dot-progress format which is what the
     * other 11 lang test outputs use anyway. */
    return new String[] {"mkdir -p .out && vendor/bin/pest --parallel"};
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("lib/Api")).exists();
    assertThat(outputDir.resolve("lib/Models")).exists();
  }
}

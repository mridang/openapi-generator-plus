package io.github.mridang.codegen.spec.php;

import io.github.mridang.codegen.spec.AbstractDocsSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated PHP SDK's {@code make docs} target builds the phpDocumentor site
 * without a single warning or error, and that it writes the documentation. The pinned phpDocumentor
 * phar is copied from the cache volume that the PHP setup fills, so the target never downloads at
 * test time. If this test fails, the PHP templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
public class PhpDocsSpec extends AbstractDocsSpec implements PhpSpec {

  @Override
  protected String[] getDocsCommands() {
    return new String[] {
      "mkdir -p .phpdoc && cp /root/.cache/php/phpdoc/phpDocumentor.phar .phpdoc/phpDocumentor.phar",
      "make docs"
    };
  }

  @Override
  protected String getDocsOutputDirectory() {
    return ".out/docs";
  }
}

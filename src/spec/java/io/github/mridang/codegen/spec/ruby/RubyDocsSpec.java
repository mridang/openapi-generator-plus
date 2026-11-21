package io.github.mridang.codegen.spec.ruby;

import io.github.mridang.codegen.spec.AbstractDocsSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated Ruby SDK's {@code make docs} target builds the YARD documentation
 * with warnings treated as errors, and that it writes the documentation. If this test fails, the
 * Ruby templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-ruby")
public class RubyDocsSpec extends AbstractDocsSpec implements RubySpec {

  @Override
  protected String[] getDocsCommands() {
    return new String[] {"make docs"};
  }

  @Override
  protected String getDocsOutputDirectory() {
    return ".out/docs";
  }
}

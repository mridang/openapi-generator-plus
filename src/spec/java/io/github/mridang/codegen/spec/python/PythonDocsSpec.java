package io.github.mridang.codegen.spec.python;

import io.github.mridang.codegen.spec.AbstractDocsSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated Python SDK's {@code make docs} target builds the Sphinx
 * documentation with warnings treated as errors, and that it writes the documentation. If this
 * test fails, the Python templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-python")
public class PythonDocsSpec extends AbstractDocsSpec implements PythonSpec {

  @Override
  protected String[] getDocsCommands() {
    return new String[] {"make docs"};
  }

  @Override
  protected String getDocsOutputDirectory() {
    return ".out/docs";
  }
}

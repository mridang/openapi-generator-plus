package io.github.mridang.codegen.spec.dart;

import io.github.mridang.codegen.spec.AbstractDocsSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated Dart SDK's {@code make docs} target builds the API documentation with
 * {@code dart doc}, validates its links, fails on any warning other than a deprecation notice, and
 * writes the documentation. If this test fails, the Dart templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-dart")
public class DartDocsSpec extends AbstractDocsSpec implements DartSpec {

  @Override
  protected String[] getDocsCommands() {
    return new String[] {"make docs"};
  }

  @Override
  protected String getDocsOutputDirectory() {
    return ".out/docs";
  }
}

package io.github.mridang.codegen.spec.csharp;

import io.github.mridang.codegen.spec.AbstractDocsSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated C# SDK's {@code make docs} target builds the docfx site with
 * warnings treated as errors, and that it writes the documentation. If this test fails, the C#
 * templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-csharp")
public class CSharpDocsSpec extends AbstractDocsSpec implements CSharpSpec {

  @Override
  protected String[] getDocsCommands() {
    return new String[] {"make docs"};
  }

  @Override
  protected String getDocsOutputDirectory() {
    return ".out/docs";
  }
}

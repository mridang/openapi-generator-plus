package io.github.mridang.codegen.spec.elixir;

import io.github.mridang.codegen.spec.AbstractDocsSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated Elixir SDK's {@code make docs} target builds the ExDoc documentation
 * with warnings treated as errors, and that it writes the documentation. If this test fails, the
 * Elixir templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-elixir")
public class ElixirDocsSpec extends AbstractDocsSpec implements ElixirSpec {

  @Override
  protected String[] getDocsCommands() {
    return new String[] {"make docs"};
  }

  @Override
  protected String getDocsOutputDirectory() {
    return ".out/docs";
  }
}

package io.github.mridang.codegen.spec.go;

import io.github.mridang.codegen.spec.AbstractWasmSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the generated Go SDK compiles for the WASI target. Go's
 * {@code net/http} and the SDK's dependencies are wasm-clean, so {@code
 * go build} for {@code GOOS=wasip1} succeeds with no source changes.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-go")
public class GoWasmSpec extends AbstractWasmSpec implements GoSpec {

  @Override
  protected String[] getWasmBuildCommands() {
    return new String[] {"GOOS=wasip1 GOARCH=wasm go build ./..."};
  }
}

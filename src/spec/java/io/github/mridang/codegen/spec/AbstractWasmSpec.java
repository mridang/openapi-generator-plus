package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Shared WebAssembly / WASI compile check — the "wasm mixin".
 *
 * <p>A language opts in by subclassing this and supplying {@link
 * #getWasmBuildCommands()}: the commands that compile the generated SDK for a
 * wasm/WASI target inside that language's runtime container. The toolchains that
 * can target wasm today (Go's {@code GOOS=wasip1 GOARCH=wasm go build}, Dart's
 * {@code dart compile wasm}, ...) gain a permanent regression check that the
 * generated SDK stays wasm-portable; languages whose toolchain or generated
 * transport is not wasm-capable simply do not subclass it.
 *
 * <p>The check is intentionally a <em>compile</em> (not a run): a clean wasm
 * build proves the generated code pulls in no platform API that is unavailable
 * on wasm — which is precisely the regression we want to guard against.
 */
@Tag("wasm")
public abstract class AbstractWasmSpec extends AbstractIntegrationSpec {

  /**
   * The command(s) that build the generated SDK for a wasm/WASI target, run in
   * the language's runtime container after its setup commands. Each entry is
   * executed through a shell, so environment-variable prefixes (e.g. {@code
   * GOOS=wasip1}) and {@code &&} chains are allowed.
   */
  protected abstract String[] getWasmBuildCommands();

  /** {@link AbstractIntegrationSpec} requires a build command; reuse the wasm one. */
  @Override
  protected String[] getBuildCommands() {
    return getWasmBuildCommands();
  }

  @Test
  void generatedCodeShouldBuildForWasm() {
    ExecResult result = executeInRuntimeContainer(getWasmBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated code failed to build for wasm/WASI:%n%s", result.output())
        .isTrue();
  }
}

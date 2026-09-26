package io.github.mridang.codegen.spec.dart;

import io.github.mridang.codegen.spec.AbstractWasmSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the generated Dart SDK compiles to WebAssembly via {@code dart
 * compile wasm} (the Flutter Web / dart2wasm target). {@code dart compile wasm}
 * needs an entry point, so the check writes a tiny probe that references {@code
 * DefaultApiClient} — forcing the conditional export to resolve to the
 * {@code BrowserClient} web transport — and compiles that. A clean wasm build
 * proves the web variant pulls in no {@code dart:io}.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-dart")
public class DartWasmSpec extends AbstractWasmSpec implements DartSpec {

  @Override
  protected String[] getWasmBuildCommands() {
    return new String[] {
      "printf 'import \"package:petstore_client/petstore_client.dart\"; "
          + "void main() { print(DefaultApiClient().runtimeType); }\\n' > wasm_probe.dart "
          + "&& dart compile wasm wasm_probe.dart -o /tmp/petstore.wasm"
    };
  }
}

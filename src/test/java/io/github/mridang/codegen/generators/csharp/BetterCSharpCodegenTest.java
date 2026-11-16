package io.github.mridang.codegen.generators.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BetterCSharpCodegenTest {

  @Test
  void escapesQuoteAndBackslashInAnApiKeyName() {
    assertThat(BetterCSharpCodegen.escapeStringLiteralBody("X-Api\"Key\\x"))
        .isEqualTo("X-Api\\\"Key\\\\x");
  }

  @Test
  void leavesAPlainApiKeyNameUnchanged() {
    assertThat(BetterCSharpCodegen.escapeStringLiteralBody("X-API-Key")).isEqualTo("X-API-Key");
  }
}

package io.github.mridang.codegen.generators.dart;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BetterDartCodegenTest {

  @Test
  void escapesQuoteDollarAndBackslashInAnApiKeyName() {
    assertThat(BetterDartCodegen.escapeDartString("X-Api'Key$ident\\x"))
        .isEqualTo("X-Api\\'Key\\$ident\\\\x");
  }

  @Test
  void leavesAPlainApiKeyNameUnchanged() {
    assertThat(BetterDartCodegen.escapeDartString("X-API-Key")).isEqualTo("X-API-Key");
  }
}

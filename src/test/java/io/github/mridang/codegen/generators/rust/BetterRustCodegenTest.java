package io.github.mridang.codegen.generators.rust;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BetterRustCodegenTest {

  private final BetterRustCodegen codegen = new BetterRustCodegen();

  @Test
  void quotesAndEscapesAnApiKeyNameSoItCannotBreakTheLiteral() {
    assertThat(codegen.toRustStringLiteral("X-Api-Key")).isEqualTo("\"X-Api-Key\"");
    assertThat(codegen.toRustStringLiteral("X-Api\"Key\\")).isEqualTo("\"X-Api\\\"Key\\\\\"");
    assertThat(codegen.toRustStringLiteral("a\nb")).isEqualTo("\"a\\nb\"");
  }

  @Test
  void leavesAUrlUntouchedAndTurnsAMissingValueIntoAnEmptyLiteral() {
    assertThat(codegen.toRustStringLiteral("https://auth.example.com/oauth/token?a=b&c=d"))
        .isEqualTo("\"https://auth.example.com/oauth/token?a=b&c=d\"");
    assertThat(codegen.toRustStringLiteral(null)).isEqualTo("\"\"");
  }
}

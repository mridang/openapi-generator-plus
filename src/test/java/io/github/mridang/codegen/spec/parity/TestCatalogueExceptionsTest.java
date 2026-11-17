package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TestCatalogueExceptionsTest {

  @Test
  void shouldReadExceptionsPerLanguage() {
    TestCatalogueExceptions exceptions =
        TestCatalogueExceptions.parse(
            "# c\ngo | transport014 | no retry hook\nrust | *unnumbered | harness test\n");

    assertThat(exceptions.allows("go", "transport014")).isTrue();
    assertThat(exceptions.allows("rust", "transport014")).isFalse();
    assertThat(exceptions.allows("rust", "*unnumbered")).isTrue();
    assertThat(exceptions.reason("go", "transport014")).isEqualTo("no retry hook");
    assertThat(exceptions.idsOf("go")).containsExactly("transport014");
  }

  @Test
  void shouldRejectBadLines() {
    assertThatThrownBy(() -> TestCatalogueExceptions.parse("go | transport014"))
        .hasMessageContaining("with a reason");
    assertThatThrownBy(() -> TestCatalogueExceptions.parse("cobol | transport014 | x"))
        .hasMessageContaining("unknown language");
    assertThatThrownBy(() -> TestCatalogueExceptions.parse("go | transport14 | x"))
        .hasMessageContaining("not an id");
    assertThatThrownBy(() -> TestCatalogueExceptions.parse("go | a001 | x\ngo | a001 | y"))
        .hasMessageContaining("twice");
  }
}

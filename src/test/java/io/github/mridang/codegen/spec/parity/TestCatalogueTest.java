package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TestCatalogueTest {

  @Test
  void shouldReadBehavioursSkippingBlankLinesAndComments() {
    TestCatalogue catalogue =
        TestCatalogue.parse(
            "# header\n\ntransport014 | Retries on 503\nretries007 | Backs off | multi\n");

    assertThat(catalogue.isEmpty()).isFalse();
    assertThat(catalogue.get("transport014").promise()).isEqualTo("Retries on 503");
    assertThat(catalogue.get("transport014").multi()).isFalse();
    assertThat(catalogue.get("retries007").promise()).isEqualTo("Backs off");
    assertThat(catalogue.get("retries007").multi()).isTrue();
  }

  @Test
  void shouldBeEmptyWhenOnlyCommentsAreThere() {
    assertThat(TestCatalogue.parse("# nothing yet\n\n").isEmpty()).isTrue();
  }

  @Test
  void shouldKeepAPipeInsideThePromise() {
    assertThat(TestCatalogue.parse("a001 | x | y").get("a001").promise()).isEqualTo("x | y");
  }

  @Test
  void shouldRejectAMalformedId() {
    assertThatThrownBy(() -> TestCatalogue.parse("transport14 | x"))
        .hasMessageContaining("line 1")
        .hasMessageContaining("transport14");
    assertThatThrownBy(() -> TestCatalogue.parse("oauth2014 | x"))
        .hasMessageContaining("not an id");
  }

  @Test
  void shouldRejectAMissingPromiseAndADuplicateId() {
    assertThatThrownBy(() -> TestCatalogue.parse("a001 |")).hasMessageContaining("no promise");
    assertThatThrownBy(() -> TestCatalogue.parse("a001 | x\na001 | y"))
        .hasMessageContaining("line 2")
        .hasMessageContaining("twice");
  }
}

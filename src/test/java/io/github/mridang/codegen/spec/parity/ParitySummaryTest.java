package io.github.mridang.codegen.spec.parity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class ParitySummaryTest {

  @Test
  void shouldRoundTripThroughJson() throws IOException {
    ParitySummary summary =
        new ParitySummary(
            "go",
            42L,
            3,
            new TreeSet<>(List.of("b::two", "a::one")),
            List.of("cannot read junit.xml: boom"),
            List.of("TestBhvTransport014_A", "TestBhvTransport014_A"));

    ParitySummary read = ParitySummary.fromJson(summary.toJson());

    assertThat(read.language()).isEqualTo("go");
    assertThat(read.writtenAt()).isEqualTo(42L);
    assertThat(read.total()).isEqualTo(3);
    assertThat(read.keys()).containsExactly("a::one", "b::two");
    assertThat(read.errors()).containsExactly("cannot read junit.xml: boom");
    assertThat(read.names()).containsExactly("TestBhvTransport014_A", "TestBhvTransport014_A");
  }

  @Test
  void shouldDeriveTheGroupsFromTheKeys() {
    ParitySummary summary =
        new ParitySummary(
            "rust", 1L, 3, new TreeSet<>(List.of("a::one", "a::two", "b::")), List.of());

    assertThat(summary.groups()).containsExactly("a", "b");
  }
}

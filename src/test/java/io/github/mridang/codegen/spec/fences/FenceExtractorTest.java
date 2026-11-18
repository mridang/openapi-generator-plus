package io.github.mridang.codegen.spec.fences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FenceExtractorTest {

  @Test
  void shouldReturnOnlyFencesOfTheSdkLanguageWithTheirKind() {
    String md =
        "# Title\n\n## Usage\n\n```go file\npackage main\n```\n\n```bash\nmake\n```\n\n"
            + "```go statements\nx := 1\n```\n";

    List<Fence> fences = FenceExtractor.extract("README.md", md, "go");

    assertThat(fences).hasSize(2);
    assertThat(fences.get(0).kind()).isEqualTo("file");
    assertThat(fences.get(0).body()).isEqualTo("package main\n");
    assertThat(fences.get(1).kind()).isEqualTo("statements");
    assertThat(fences.get(1).number()).isEqualTo(2);
  }

  @Test
  void shouldIgnoreGoodAndBadFences() {
    String md = "```good\ntext\n```\n```bad\ntext\n```\n```go file\nx\n```\n";

    assertThat(FenceExtractor.extract("SKILLS.md", md, "go")).hasSize(1);
  }

  @Test
  void shouldReportTheHeadingAboveAFence() {
    String md = "## Errors\n\ntext\n\n```rust statements\nlet a = 1;\n```\n";

    Fence fence = FenceExtractor.extract("README.md", md, "rust").get(0);

    assertThat(fence.heading()).isEqualTo("Errors");
    assertThat(fence.describe()).contains("README.md:5").contains("Errors");
  }

  @Test
  void shouldReturnAnEmptyKindWhenTheInfoStringHasNone() {
    assertThat(FenceExtractor.extract("README.md", "```go\nx\n```\n", "go").get(0).kind())
        .isEmpty();
  }

  @Test
  void shouldFailOnAFenceThatIsNeverClosed() {
    assertThatThrownBy(() -> FenceExtractor.extract("README.md", "x\n```go file\nx\n", "go"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("README.md:2")
        .hasMessageContaining("never closed");
  }
}

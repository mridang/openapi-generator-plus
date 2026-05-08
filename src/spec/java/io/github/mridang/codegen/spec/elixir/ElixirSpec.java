package io.github.mridang.codegen.spec.elixir;

import io.github.mridang.codegen.spec.LanguageSpec;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("elixir")
interface ElixirSpec extends LanguageSpec {

  @Override
  default String getGeneratorName() {
    return "elixir-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("elixir:1.18");
  }
}

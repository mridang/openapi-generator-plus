package io.github.mridang.codegen.spec.elixir;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("elixir")
interface ElixirSpec extends LanguageSpec, DockerImageSpec {

  @Override
  default String getGeneratorName() {
    return "elixir-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("elixir:1.18");
  }

  @Override
  default String getDockerImage() {
    return "elixir:1.18";
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of("mix local.hex --force && mix local.rebar --force", "mix deps.get");
  }
}

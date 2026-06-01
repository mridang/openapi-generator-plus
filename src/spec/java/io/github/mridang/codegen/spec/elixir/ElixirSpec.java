package io.github.mridang.codegen.spec.elixir;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import java.util.Map;
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

  @Override
  default Map<String, String> getCacheEnv() {
    /* MIX_HOME holds runtime archives and the hex/rebar plugin state.
     * HEX_HOME holds the registry cache. MIX_DEPS_PATH and MIX_BUILD_PATH
     * relocate the project's deps/ and _build/ directories — those normally
     * live under /work and get destroyed between specs, but routing them to
     * /root/.cache/elixir keeps the compiled BEAM .beam files around so
     * ElixirLintingSpec / ElixirClientSpec / ElixirBuildSpec each find their
     * predecessors' compile work intact. */
    return Map.of(
        "MIX_HOME", "/root/.cache/elixir/mix",
        "HEX_HOME", "/root/.cache/elixir/hex",
        "MIX_DEPS_PATH", "/root/.cache/elixir/deps",
        "MIX_BUILD_PATH", "/root/.cache/elixir/_build");
  }
}

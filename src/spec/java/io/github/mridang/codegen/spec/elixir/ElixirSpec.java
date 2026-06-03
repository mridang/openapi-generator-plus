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
    return DockerImageName.parse(
        "elixir:1.19-alpine@sha256:08324c4b0c40ff9ee053e04525567d21275beed217ace8e7d1540d07b8a69d0b");
  }

  @Override
  default String getDockerImage() {
    return "elixir:1.19-alpine@sha256:08324c4b0c40ff9ee053e04525567d21275beed217ace8e7d1540d07b8a69d0b";
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of("mix local.hex --force && mix local.rebar --force", "mix deps.get");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* MIX_HOME (runtime archives / hex+rebar plugin state), HEX_HOME
     * (registry cache), MIX_DEPS_PATH (downloaded dep sources) — all safe
     * to relocate outside /work. MIX_BUILD_PATH was tried in the previous
     * wave but broke the generated discriminator deserialization test
     * (ComposedSchemaTest oneOf → WetFood): Mix's runtime BEAM module
     * discovery resolves Model modules relative to the on-disk _build/
     * tree, and moving _build/ outside the project root made
     * Code.ensure_loaded?/1 miss the generated structs at the moment
     * the discriminator routes the JSON map into a typed struct,
     * leaving the result as a raw map. Keeping _build/ project-local
     * trades the per-spec compile cache for correctness — the wins on
     * Hex / deps still survive. */
    return Map.of(
        "MIX_HOME", "/root/.cache/elixir/mix",
        "HEX_HOME", "/root/.cache/elixir/hex",
        "MIX_DEPS_PATH", "/root/.cache/elixir/deps");
  }
}

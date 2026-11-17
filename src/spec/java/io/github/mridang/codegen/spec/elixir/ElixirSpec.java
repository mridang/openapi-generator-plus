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
    return DockerImageName.parse("elixir:1.19-alpine");
  }

  @Override
  default String getDockerImage() {
    return "elixir:1.19-alpine";
  }

  @Override
  default List<String> getSetupCommands() {
    /* The snapshot every spec restores is taken from the host's golden
     * directory, which still holds the previous run's JUnit report; drop it so
     * no later spec copies it back. */
    return List.of(
        /* The Elixir image is Alpine and ships without make, which the SDK's own
         * Makefile targets (docs, test, lint) need. Installed once with the
         * shared container, unpinned like the other Alpine languages: a pinned
         * revision (make=4.4.1-r4) is not on the mirror and breaks apk's solver.
         * Retries because the package mirror is flaky on CI runners. */
        "for i in 1 2 3 4 5; do apk add --no-cache make && break;"
            + " echo \"apk attempt $i failed; retrying\" >&2; sleep 3; done;"
            + " command -v make > /dev/null || { echo \"make missing after retries\" >&2; exit 1; }",
        "find .out/reports -mindepth 1 -delete 2>/dev/null; true",
        "mix local.hex --force && mix local.rebar --force",
        "mix deps.get",
        /* Compile the dependencies once here so every spec starts from the
         * compiled snapshot instead of rebuilding them: the lint spec uses the
         * dev environment, the client spec the test environment. */
        "MIX_ENV=dev mix deps.compile",
        "MIX_ENV=test mix deps.compile");
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

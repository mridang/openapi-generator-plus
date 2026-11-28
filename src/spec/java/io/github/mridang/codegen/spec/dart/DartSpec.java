package io.github.mridang.codegen.spec.dart;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("dart")
interface DartSpec extends LanguageSpec, DockerImageSpec {

  @Override
  default String getGeneratorName() {
    return "dart-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse(
        "dart:stable@sha256:6440c7d5fd8713b0706d0b6190eb2be7ad896101e225fc9d7657034b23ab0592");
  }

  @Override
  default String getDockerImage() {
    return "dart:stable@sha256:6440c7d5fd8713b0706d0b6190eb2be7ad896101e225fc9d7657034b23ab0592";
  }

  @Override
  default List<String> getSetupCommands() {
    /* Setup only resolves dependencies. It must not rewrite sources: every
     * spec restores the tree this setup leaves behind, so a `dart format` or
     * `dart fix --apply` here would make DartFormattingSpec and
     * DartLintingSpec check the rewritten tree instead of the committed
     * golden. The generator already runs its fixes and `dart format` at
     * generation time. */
    return List.of(
        /* The dart image is Debian and ships without make, which the SDK's own
         * docs target needs. Installed once with the shared container, with
         * retries because the package mirror is flaky on CI runners. */
        "for i in 1 2 3 4 5; do (apt-get update -qq && apt-get install -y -qq make)"
            + " > /dev/null 2>&1 && break;"
            + " echo \"apt attempt $i failed; retrying\" >&2; sleep 3; done;"
            + " command -v make > /dev/null || { echo \"make missing after retries\" >&2; exit 1; }",
        "dart pub get");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* PUB_CACHE owns the resolved package sources. Project .dart_tool/
     * stays local (no env var for it). */
    return Map.of("PUB_CACHE", "/root/.cache/dart/pub");
  }
}

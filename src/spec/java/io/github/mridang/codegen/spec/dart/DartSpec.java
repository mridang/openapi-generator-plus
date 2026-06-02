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
    return DockerImageName.parse("dart:stable");
  }

  @Override
  default String getDockerImage() {
    return "dart:stable";
  }

  @Override
  default List<String> getSetupCommands() {
    /* `dart fix --apply` runs the analyzer's auto-fix bridge on the
     * generated tree: rewrites relative imports inside lib/ to the
     * `package:` form, sorts import directives, fills in the
     * documentation-for-ignore comments, and applies every other lint
     * rule that has a registered fix producer. This silences a large
     * class of `dart analyze` info-level diagnostics that the codegen
     * emits and that the formatter (cosmetic-only) won't touch. Runs
     * AFTER `dart pub get` because the fix engine needs the resolved
     * package map to know what `package:<name>/...` URI to substitute
     * the relative imports with. The result is captured in the
     * container's /work-snapshot, so DartLintingSpec / DartFormattingSpec
     * / DartClientSpec all see the already-cleaned-up code. No other
     * language ships an equally-capable autofix, so this is a
     * Dart-only setup step — not parity with the other 11 specs. */
    return List.of("dart pub get", "dart fix --apply");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* PUB_CACHE owns the resolved package sources. Project .dart_tool/
     * stays local (no env var for it). */
    return Map.of("PUB_CACHE", "/root/.cache/dart/pub");
  }
}

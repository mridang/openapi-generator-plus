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
    /* Run `dart fix --apply` ONLY for a small allowlist of lint codes
     * that have well-behaved fix producers. The unrestricted
     * `dart fix --apply` form runs every registered fix producer in
     * the SDK, several of which cascade — they rewrite code in a way
     * that exposes (or directly introduces) other lints or compile
     * errors. Empirically the broad form produces:
     *   - `prefer_final_locals` rewriting `var x` to illegal `final var x`
     *   - `unnecessary_null_checks` removing needed `!` so the receiver
     *     fails `unchecked_use_of_nullable_value`
     *   - `cast_nullable_to_non_nullable` inserting casts the analyzer
     *     then flags as `unnecessary_cast`
     *   - 200+ additional cascade_invocations / sort_constructors_first
     *     style findings on the rewritten tree
     * The 3 codes below were verified locally on a pristine generated
     * tree to apply 195 fixes across 84 files without introducing any
     * warning- or error-level findings:
     *   - `always_use_package_imports`: rewrites `'../foo.dart'` →
     *     `'package:<name>/.../foo.dart'` (150 sites)
     *   - `directives_ordering`: sorts import groups alphabetically
     *     within each section (34 sites)
     *   - `document_ignores`: adds the explanatory comment line above
     *     `// ignore:` directives (1 site)
     *
     * `dart format .` runs AFTER the fix because `dart fix` (even
     * narrowed) leaves whitespace the formatter rewrites; without this
     * pass `DartFormattingSpec`'s `--set-exit-if-changed` would fail.
     *
     * After this chain on the pristine tree: `dart analyze` exits 0
     * (770 info-level findings remain, all from rules without safe
     * fix producers — they don't fail the spec) and
     * `dart format --set-exit-if-changed .` exits 0. */
    return List.of(
        "dart pub get",
        "dart fix --apply"
            + " --code=always_use_package_imports"
            + " --code=directives_ordering"
            + " --code=document_ignores",
        "dart format .");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* PUB_CACHE owns the resolved package sources. Project .dart_tool/
     * stays local (no env var for it). */
    return Map.of("PUB_CACHE", "/root/.cache/dart/pub");
  }
}

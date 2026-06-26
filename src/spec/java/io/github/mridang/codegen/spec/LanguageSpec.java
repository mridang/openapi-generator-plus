package io.github.mridang.codegen.spec;

import java.util.List;
import java.util.Map;
import org.testcontainers.utility.DockerImageName;

public interface LanguageSpec {

  String getGeneratorName();

  DockerImageName getRuntimeImage();

  default Map<String, Object> getCodegenProperties() {
    return Map.of();
  }

  default List<String> getSetupCommands() {
    return List.of();
  }

  /**
   * Per-language env vars that point the toolchain's cache (and where the
   * toolchain supports it, the build artifact path) at a stable directory
   * outside {@code /work}. SharedRuntimeContainer wipes and restores
   * {@code /work} between every spec method, so anything one spec writes
   * under {@code /work} is gone before the next runs; routing the cache to a
   * path under {@code /root} (preserved across exec calls in the same shared
   * container) lets deps + index downloads (and Rust/Elixir's compile
   * artifacts) survive across BuildSpec → ClientSpec → LintingSpec.
   *
   * <p>Each per-language Spec interface overrides this to return its
   * docker-verified env vars (env-name → absolute path inside the container).
   * SharedRuntimeContainer applies them via {@code withEnv(...)} when the
   * container is created, before setup commands run, so even the initial
   * dependency install lands in the right place.
   */
  default Map<String, String> getCacheEnv() {
    return Map.of();
  }
}

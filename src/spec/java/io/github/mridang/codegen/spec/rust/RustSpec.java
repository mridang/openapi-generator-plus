package io.github.mridang.codegen.spec.rust;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("rust")
interface RustSpec extends LanguageSpec, DockerImageSpec {

    @Override
    default String getGeneratorName() {
        return "rust-plus";
    }

    @Override
    default DockerImageName getRuntimeImage() {
        return DockerImageName.parse("rust:1.88");
    }

    @Override
    default String getDockerImage() {
        return "rust:1.88";
    }

    @Override
    default Map<String, Object> getCodegenProperties() {
        return Map.of("packageName", "petstore");
    }

    @Override
    default List<String> getSetupCommands() {
        return List.of("rustup component add rustfmt clippy");
    }

    @Override
    default Map<String, String> getCacheEnv() {
        /* CARGO_HOME holds the registry index + downloaded crate sources;
         * CARGO_TARGET_DIR holds compiled artifacts (debug/release/test/clippy
         * each get their own profile-keyed subdir, so cargo test → cargo
         * clippy doesn't invalidate cargo build). Together they let
         * RustBuildSpec → RustClientSpec → RustLintingSpec share every byte
         * of compile output across the inter-spec /work wipe. */
        return Map.of(
                "CARGO_HOME", "/root/.cache/rust/cargo",
                "CARGO_TARGET_DIR", "/root/.cache/rust/target");
    }
}

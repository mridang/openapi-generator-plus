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
        return DockerImageName.parse("rust:1.96-slim");
    }

    @Override
    default String getDockerImage() {
        return "rust:1.96-slim";
    }

    @Override
    default Map<String, Object> getCodegenProperties() {
        return Map.of("packageName", "petstore");
    }

    @Override
    default List<String> getSetupCommands() {
        /* `cargo nextest` is the runner that emits JUnit XML (cargo test
         * itself has no XML reporter, only stdout). Used by RustClientSpec
         * via `cargo nextest run --profile=ci`. Install via the upstream
         * prebuilt binary (a few seconds) rather than `cargo install
         * cargo-nextest` (a few minutes to compile). The .config dir +
         * nextest.toml below tells nextest where to write the XML. */
        return List.of(
                /* The snapshot every spec restores is taken from the host's
                 * golden directory, which still holds the previous run's JUnit
                 * report; drop it so no later spec copies it back. */
                "find .out/reports -mindepth 1 -delete 2>/dev/null; true",
                /* rust:slim omits curl + ca-certificates that the full image
                 * ships via buildpack-deps; the nextest download below needs
                 * both (curl for the fetch, CA certs for its TLS verify). */
                "apt-get update -qq && apt-get install -y -qq --no-install-recommends"
                        + " curl ca-certificates",
                /* The rust image is Debian and ships without make, which the SDK's own
                 * docs target needs. Installed once with the shared container, with
                 * retries because the package mirror is flaky on CI runners. */
                "for i in 1 2 3 4 5; do (apt-get update -qq && apt-get install -y -qq"
                        + " --no-install-recommends make)"
                        + " > /dev/null 2>&1 && break;"
                        + " echo \"apt attempt $i failed; retrying\" >&2; sleep 3; done;"
                        + " command -v make > /dev/null"
                        + " || { echo \"make missing after retries\" >&2; exit 1; }",
                "rustup component add rustfmt clippy",
                "mkdir -p $CARGO_HOME/bin && curl -fLsS --retry 5 --retry-delay 2"
                        + " --retry-all-errors"
                        + " \"https://get.nexte.st/0.9.137/$(case \"$(uname -m)\" in"
                        + " aarch64|arm64) echo linux-arm ;; *) echo linux ;; esac)\""
                        + " | tar zxf - -C $CARGO_HOME/bin",
                "mkdir -p .config && printf '%s\\n%s\\n' '[profile.ci.junit]'"
                        + " 'path = \"junit.xml\"' > .config/nextest.toml");
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

package io.github.mridang.codegen.spec.rust;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Rust SDK (D180): rustdoc writes the crate's JSON
 * description (an unstable format, enabled on the stable toolchain with {@code
 * RUSTC_BOOTSTRAP=1}), and a one-file reader built against the SDK's own {@code serde_json} walks
 * it from the crate root and prints one signature per reachable public item. If this test fails
 * on the snapshot, the public API changed; if it fails on a leak, a public signature names a
 * {@code reqwest}, {@code serde_json}, OpenTelemetry or other third-party type.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-rust")
public class RustApiSurfaceSpec extends AbstractApiSurfaceSpec implements RustSpec {

    @Override
    protected List<String> getExtractorScripts() {
        return List.of("rust-surface.rs");
    }

    @Override
    protected String[] getSurfaceCommands() {
        /* The JSON is written with private and hidden items so the reader can tell a type that
         * is declared pub from one that is reachable, and with every feature so the
         * feature-gated API is part of the snapshot. Its own target directory keeps the
         * RUSTC_BOOTSTRAP build out of the shared compile cache. */
        return new String[] {
            "RUSTC_BOOTSTRAP=1 RUSTDOCFLAGS='-Z unstable-options --output-format json"
                    + " --document-private-items --document-hidden-items'"
                    + " cargo doc -q --no-deps --all-features --target-dir /tmp/api-surface/doc",
            "mkdir -p /tmp/api-surface/reader/src"
                    + " && cp /tmp/api-surface/rust-surface.rs /tmp/api-surface/reader/src/main.rs"
                    + " && printf '[package]\\nname = \"rust-surface\"\\nversion = \"0.0.0\"\\n"
                    + "edition = \"2021\"\\n\\n[dependencies]\\nserde_json = \"1\"\\n'"
                    + " > /tmp/api-surface/reader/Cargo.toml",
            "cargo run -q --offline --manifest-path /tmp/api-surface/reader/Cargo.toml"
                    + " --target-dir /tmp/api-surface/reader/target --"
                    + " /tmp/api-surface/doc/doc/*.json"
        };
    }

    @Override
    protected List<Pattern> getThirdPartyPatterns() {
        /* A trait implementation ("impl serde::Serialize for ...") names no third-party type in
         * a signature a caller passes or receives, so those lines are not checked. */
        return List.of(
                Pattern.compile(
                        "^(?!impl[<\\s]).*\\b(reqwest|serde|serde_json|serde_repr|opentelemetry"
                                + "|tokio|bytes|futures_core|futures_util|flate2|brotli|zstd|regex"
                                + "|base64|sha2|encoding_rs|urlencoding|chrono|uuid|rust_decimal)::"));
    }
}

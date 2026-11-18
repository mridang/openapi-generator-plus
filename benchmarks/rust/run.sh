#!/usr/bin/env bash
# Runs inside rust:1-slim. Mounts: /golden (generated rust SDK, read-only), /fixtures, /bench (this folder);
# cache volumes for /usr/local/cargo/registry and /target keep rebuilds short.
# Builds a reduced crate from the golden's object_serializer, models and their helper modules only: the HTTP
# stack is not part of the codec and the full crate would add minutes of compile time per run.
set -euo pipefail
mkdir -p /work/src/errors /work/examples && cd /work
for f in object_serializer value_serializer json_value date_time proto_duration; do cp "/golden/src/$f.rs" "src/$f.rs"; done
cp -r /golden/src/models src/models
cp /golden/src/errors/serialization_error.rs src/errors/
printf 'pub mod serialization_error;\npub use serialization_error::SerializationError;\n' > src/errors/mod.rs
cat > src/lib.rs <<'RS'
pub mod date_time;
pub mod errors;
pub mod json_value;
pub mod models;
pub mod object_serializer;
pub mod proto_duration;
pub mod value_serializer;
RS
grep -E '^(serde|serde_repr|serde_json|uuid|chrono|regex|base64|sha2|urlencoding)( |=)' /golden/Cargo.toml > /tmp/deps.toml
{ printf '[package]\nname = "petstore"\nversion = "1.0.0"\nedition = "2021"\n\n[dependencies]\n'; cat /tmp/deps.toml
  printf '\n[dev-dependencies]\nsimd-json = "0.15"\nsonic-rs = "0.5"\n\n[profile.release]\ndebug = false\n'; } > Cargo.toml
cp /bench/bench.rs examples/bench.rs
CARGO_TARGET_DIR=/target cargo build --release --quiet --example bench >&2
/target/release/examples/bench

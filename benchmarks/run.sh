#!/usr/bin/env bash
# Runs the benchmark for each language named on the command line (default: go java node python rust php csharp) in Docker,
# against the committed golden SDKs in src/spec/resources/generated/<lang>, and prints the markdown report.
# Raw rows go to benchmarks/.out/<lang>.txt (gitignored scratch, delete it when done).
# Environment: ROUNDS (default 5) and the per-measurement budget BENCHTIME/TARGET_MS.
# One language at a time: parallel runs share the machine and corrupt each other's timings.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"
out="$here/.out"
mkdir -p "$out"
langs=("$@")
[ ${#langs[@]} -gt 0 ] || langs=(go java node python rust php csharp)
common=(-v "$here/fixtures:/fixtures:ro" -e "ROUNDS=${ROUNDS:-5}" -e "TARGET_MS=${TARGET_MS:-700}" -e "BENCHTIME=${BENCHTIME:-1s}")
for lang in "${langs[@]}"; do
  golden="$root/src/spec/resources/generated/$lang:/golden:ro"
  mine="$here/$lang:/bench:ro"
  case "$lang" in
    go) docker run --rm "${common[@]}" -v "$golden" -v "$mine" -v bench-gomod:/go/pkg/mod golang:1.26-alpine \
          sh -c 'apk add -q --no-cache bash git >/dev/null; bash /bench/run.sh' ;;
    java) docker run --rm "${common[@]}" -v "$golden" -v "$mine" -v bench-m2:/root/.m2 maven:3.9-eclipse-temurin-21 bash /bench/run.sh ;;
    node) docker run --rm "${common[@]}" -v "$golden" -v "$mine" node:24-alpine \
          sh -c 'apk add -q --no-cache bash >/dev/null; bash /bench/run.sh' ;;
    python) docker run --rm "${common[@]}" -v "$golden" -v "$mine" python:3.14-alpine \
          sh -c 'apk add -q --no-cache bash >/dev/null; bash /bench/run.sh' ;;
    php) docker run --rm "${common[@]}" -v "$golden" -v "$mine" --entrypoint bash composer:2 /bench/run.sh ;;
    csharp) docker run --rm "${common[@]}" -v "$golden" -v "$mine" -v bench-nuget:/root/.nuget mcr.microsoft.com/dotnet/sdk:10.0-alpine \
          sh -c 'apk add -q --no-cache bash >/dev/null; bash /bench/run.sh' ;;
    rust) docker run --rm "${common[@]}" -v "$golden" -v "$mine" -v bench-cargo:/usr/local/cargo/registry -v bench-target:/target rust:1-slim bash /bench/run.sh ;;
    *) echo "no runner for $lang" >&2; exit 2 ;;
  esac > "$out/$lang.txt" 2> "$out/$lang.err"
done
cat "$out"/*.txt | node "$here/report.mjs"

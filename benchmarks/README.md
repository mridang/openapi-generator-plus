# JSON codec benchmarks

Measures the generated `ObjectSerializer` of each SDK (serialize and deserialize a model) against the best reflection-free alternative of the language. The analysis and the recommendation are in [docs/design/json-performance.md](../docs/design/json-performance.md); the last full run is in [RESULTS.md](RESULTS.md).

```
benchmarks/run.sh              # go java node python rust php csharp, one after the other
benchmarks/run.sh go php       # only these
ROUNDS=10 TARGET_MS=1500 benchmarks/run.sh java
```

Everything runs in Docker against the committed golden SDKs in `src/spec/resources/generated/<lang>` (mounted read-only and copied inside the container), so a run needs no local toolchain and leaves nothing in the repository except `benchmarks/.out/` (raw rows and logs, gitignored; delete it when done). Run one language at a time on an otherwise idle machine: parallel runs, or a gate running beside it, change the timings by more than the differences being measured.

| Folder | Runner image | What it compares with the generated serializer |
|---|---|---|
| `go/` | golang:1.26-alpine | `encoding/json`, `encoding/json/v2`, easyjson on mirror structs |
| `java/` | maven:3.9-eclipse-temurin-21 | Jackson Blackbird, Afterburner, a hand-written streaming codec (one JVM each) |
| `node/` | node:24-alpine | native JSON plus a hand-written mapper |
| `python/` | python:3.14-alpine | pydantic called directly, `json` plus a mapper, orjson plus a mapper |
| `rust/` | rust:1-slim | `serde_json`, simd-json, sonic-rs (builds a reduced crate: the object serializer, models and their helpers) |
| `php/` | composer:2 (PHP 8.5, opcache and JIT on) | `json_decode` plus a hand-written mapper |
| `csharp/` | mcr.microsoft.com/dotnet/sdk:10.0-alpine | `System.Text.Json` source generation |

`fixtures/` holds the three payloads (`small.json`, `medium.json`, `large.json`); `node fixtures/generate.mjs` rewrites them. They use values every language's validators accept, so one payload serves all runners. Each runner prints one row per round, `lang impl model op ns_per_op bytes_per_op allocs_per_op`, with `-` where the runtime cannot count; `report.mjs` turns the rows into the tables (median, range, spread, factor against the generated serializer). Each runner first round-trips the medium payload through the generated serializer and prints `MISMATCH` to stderr (kept in `.out/<lang>.err`) if the output differs from the fixture.

The harnesses are the only code here and are not part of any generated SDK. kotlin, ruby, dart, swift and elixir have no runner yet.

# PetstoreClient SDK

Auto-generated C# SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **.NET 10** (Standard Term Support) runtime / SDK
- **C# 13** language version (`LangVersion=13`)
- **dotnet CLI 10.x** — install from <https://dotnet.microsoft.com/download/dotnet/10.0>

Tooling baked into the project:

- **Formatter:** `dotnet format` (built into the SDK) and `dotnet csharpier`
- **Linter / style:** [`StyleCop.Analyzers`](https://github.com/DotNetAnalyzers/StyleCopAnalyzers) (NuGet, included as a `PrivateAssets=all` analyzer reference)
- **Static analysis:** [`Microsoft.CodeAnalysis.NetAnalyzers`](https://learn.microsoft.com/dotnet/fundamentals/code-analysis/overview) (Roslyn analyzers), with `EnableNETAnalyzers=true` and `AnalysisLevel=latest-all`

## Build

```bash
dotnet build
```

## Test

```bash
dotnet test
```

## Format / Lint / Analyze

```bash
# Apply formatting (CSharpier + dotnet format)
make format
# or directly:
dotnet csharpier .
dotnet format --severity warn

# Verify formatting without writing changes
make lint
# or directly:
dotnet csharpier --check .
dotnet format --verify-no-changes --severity warn

# Run Roslyn / StyleCop analyzers with warnings as errors
make analyze
# or directly:
dotnet build --no-restore /warnaserror
```

## Package

- Name: `PetstoreClient`
- Version: `1.0.0`

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### Numeric / string constraint validation

OpenAPI keywords like `minLength`, `maxLength`, `minimum`, `maximum`,
`pattern`, `minItems`, `maxItems`, `uniqueItems`, `multipleOf` are
**not enforced** by this SDK. The server is the authoritative
validator; client-side enforcement is a DX nicety, not a correctness
requirement. If you want fast-fail validation before the network
round trip, plug in a JSON Schema validator library for your language.

### SOCKS proxies

`TransportOptions.proxy()` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme throws (or
panics) at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

# Go

The `go-plus` generator (`BetterGoCodegen`) produces a Go module with one file per API tag, one struct per model, an `ApiClient` interface with a `DefaultApiClient`, authenticators, typed errors and a `_test.go` suite. It uses the standard library `net/http`. For the rules shared by all twelve languages see [how it works](../how-it-works.md), for the type table across languages see [types](../types.md), and for settled cross-language choices see [decisions](../decisions.md).

## Versions and support policy

The generated `go.mod` declares `go 1.26.0`, and the generated README says Go 1.26 or newer. The floor follows one rule: it is never newer than the oldest Go release that has not reached end of life. When that release reaches end of life the floor moves up. The generator itself builds and checks the golden module in a Go 1.26 toolchain image.

The module also builds for `GOOS=wasip1 GOARCH=wasm`.

## Type mapping

| OpenAPI type and format | Go type | Notes |
|---|---|---|
| `string` | `string` | Also used for `email`, `hostname`, `ipv4`, `ipv6`, `password` and unknown formats. |
| `string` / `date` | `string` | The standard library has no calendar-date type. The value is not validated. |
| `string` / `date-time` | `models.DateTime` | A `time.Time` wrapper. The wire form has a numeric offset (never `Z`) and a fixed three-digit millisecond fraction. |
| `string` / `time` | `string` | No standard library type. `iso8601.go` ships `FormatTimeOfDay` and `ParseTimeOfDay` for callers who want a `time.Time`. |
| `string` / `duration` | `models.Duration` | A `time.Duration` wrapper. The wire form is the protobuf-JSON string such as `"3600s"`, not ISO 8601. |
| `string` / `uuid` | `uuid.UUID` | From `github.com/google/uuid`. Written in lower case. |
| `string` / `byte` | `[]byte` | `encoding/json` encodes it as padded standard base64. |
| `string` / `binary`, file bodies | `[]byte` for downloads, `io.Reader` for uploads | Responses are read fully into memory. |
| `string` / `uri` | `string` | |
| `integer` / `int32` | `int32` | |
| `integer` / `int64` | `int64` | |
| `integer` with no format | `int32` | |
| `number` / `float` | `float32` | |
| `number` / `double`, `number` with no format | `float64` | |
| `number` / `decimal` | `float64` | Lossy for exact decimal values such as money. This is documented in the generated README. |
| `boolean` | `bool` | |
| `array` | `[]T` | |
| `array` with `uniqueItems` | `models.Set[T]` | Slice-backed, `T` is `comparable`. Scalar members are written sorted so the bytes are stable. |
| `object` with `additionalProperties` | `map[string]T` | |
| free-form `object`, any type | `any` | |
| nullable | `*T` | A required nullable property keeps its key in the output. |
| `enum` | named string type with constants | Marshalling and unmarshalling reject values outside the enum. |
| `oneOf`, `anyOf` | struct holding an `any` value | Has `MarshalJSON` and `UnmarshalJSON`. Variants are tried in declaration order, or chosen by the discriminator when there is one. |

## Dependencies

Runtime modules, from the generated `go.mod`:

| Module | Version | Used for |
|---|---|---|
| `github.com/andybalholm/brotli` | v1.2.1 | Decoding `br` responses |
| `github.com/klauspost/compress` | v1.18.6 | Decoding `zstd` responses |
| `github.com/google/uuid` | v1.6.0 | The `uuid` type |
| `go.opentelemetry.io/otel` and `otel/trace` | v1.47.0 | Trace-context propagation |

HTTP is the standard library. Other requirements in the manifest are for the generated tests and tooling (`testcontainers-go`, the OpenTelemetry SDK, and a `tool` block with `gotestsum`, `staticcheck`, `gofumpt` and `gocover-cobertura`). Dependencies that need cgo are not accepted.

## Options

| Option | Default | What it does |
|---|---|---|
| `packageName` | none | Name of the generated module. |
| `packageVersion` | `1.0.0` | Version written to the README. |
| `repositoryUrl` | none | Source repository URL, written to the README. |
| `homepageUrl` | none | Project homepage, written to the README. |
| `authors` | none | Comma-separated author names, written to the README. |
| `documentationUrl` | none | Documentation URL, written to the README. |
| `issueTrackerUrl` | none | Issue tracker URL, written to the README. |

## Idiom notes

- Every operation takes `ctx context.Context` as its first parameter. Cancelling it, or calling `Close()` on the client, cancels the call.
- Errors are values. Caller mistakes are exported sentinels you match with `errors.Is` (for example `ErrMissingRequiredParameter`, `ErrInvalidTimeout`, `ErrTransportConflict`, `ErrClientClosed`). SDK errors implement `Unwrap` and `As`. Error strings start in lower case, as `staticcheck` requires.
- Optional operation parameters live in an `opts.<Operation>Options` struct. Per-call settings (`WithTimeout`, `WithHeaders`, `WithMaxRetries`, `WithRequestContentType`) are trailing variadic `RequestOption` values.
- Configuration is built with `NewConfigurationBuilder()` and `NewTransportOptionsBuilder()`.
- You can supply your own HTTP stack with `TransportOptionsBuilder.HttpClient` (anything with `Do(*http.Request)`) or `RoundTripper`. Combining that with the SDK's own proxy, TLS or pool settings fails `Build()` with `ErrTransportConflict`, and the SDK never closes a client you supplied.
- Initialisms are upper case (`URL`, `ID`, `HTTP`).
- A model struct can be created without its required properties. Decoding enforces them.

## Known limits

- `format: date` and `format: time` are plain strings, so they are not validated.
- A schema integer with no format decodes into `int32`, so a larger value fails to decode. Declare `format: int64` where needed.
- `format: decimal` and `number` are `float64` and lose exactness.
- Schema constraints (`pattern`, `minLength`, `maximum` and so on) are not enforced by the client. The server owns validation.
- `allOf` with a discriminator is not implemented yet. Only `oneOf` with a discriminator is.
- Responses are buffered in full. Streaming a download is not supported.
- SOCKS proxies are not supported.
- Callbacks and webhooks, XML bodies, Link Objects and mutual TLS are out of scope in every language.

## Under review

These are ideas from a type review in November 2026. None of them is decided, and none changes the generated SDK today.

- Add SDK-owned `models.Date` and `models.TimeOfDay` types so `date` and `time` are validated. This needs agreement with the other SDKs because it adds public API.
- Map an unformatted `integer` to `int64`, because OpenAPI treats it as unbounded. This changes a public type and would be decided together with the other languages.
- `net/netip.Addr` for `ipv4` and `ipv6`, and `url.URL` for `uri`. Both are in the standard library and need no floor change, but they change public types.
- Replace `google/uuid` with the `uuid` package the review says Go 1.27 adds. The review could not confirm its API, and using it would raise the floor to 1.27, so it is not before Go 1.26 reaches end of life.
- Run the full golden suite on Go 1.27 before moving the floor or the toolchain. The review notes that `encoding/json` is backed by json/v2 in that release, which is stricter and may change error text the SDK's tests assert on.
- Whether the duration wire form should stay protobuf-JSON or move to ISO 8601, across all languages.

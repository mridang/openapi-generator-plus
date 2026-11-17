# Elixir

The `elixir-plus` generator (`BetterElixirCodegen`) produces a Mix project with one module per API group, model, error and authenticator. It uses Req for HTTP and Jason for JSON. For the rules shared by all twelve languages see [how it works](../how-it-works.md), for the type table across languages see [types](../types.md), and for settled cross-language choices see [decisions](../decisions.md).

## Versions and support policy

The generated `mix.exs` requires `elixir: "~> 1.17"`, and the generated README asks for Elixir 1.17 or newer on Erlang/OTP 27 or newer. The floor is the oldest Elixir release that still gets security fixes, and it moves up when that release reaches end of life. The generator builds and checks the golden project on Elixir 1.19.

## Type mapping

Types are the typespecs that appear in the generated modules.

| OpenAPI type and format | Elixir type | Notes |
|---|---|---|
| `string` | `String.t()` | Also used for `email`, `hostname`, `ipv4`, `ipv6`, `password` and unknown formats. |
| `string` / `date` | `Date.t()` | |
| `string` / `date-time` | `DateTime.t()` | Decoded from ISO 8601. Elixir returns the instant in UTC, so check that the offset you need survives if you rely on it. |
| `string` / `time` | `Time.t()` | |
| `string` / `duration` | `Duration.t()` | The wire form is the protobuf-JSON string such as `"3600s"`, not ISO 8601. |
| `string` / `uuid` | `String.t()` | Neither Elixir nor Erlang has a UUID type. Written in lower case and validated when sent as a parameter. |
| `string` / `byte` | `binary()` | Base64 with `Base`. |
| `string` / `binary`, file bodies | `binary()` | Raw bytes in memory. |
| `string` / `uri` | `String.t()` | |
| `integer` (any format) | `integer()` | BEAM integers are unbounded. `int32` and `int64` are not range checked. |
| `number` / `float`, `double`, and no format | `float()` | |
| `number` / `decimal` | `float()` | Lossy for exact decimal values such as money. Parse the body with the `Decimal` package if you need exactness. |
| `boolean` | `boolean()` | |
| `array` | `[T]` | |
| `array` with `uniqueItems` | `MapSet.t(T)` | Sent in the `MapSet`'s own order. |
| `object` with `additionalProperties` | `map()` | String keys. |
| free-form `object`, any type | `map()` for objects, `any()` for any type | |
| nullable | `T \| nil` | |
| `enum` | atoms | Each enum module has `all_values/0`. Atoms come from a fixed set, so server input cannot grow the atom table. |
| `oneOf`, `anyOf` | the matched variant | `build/1` returns it. An `anyOf` can return an `AnyOfComposite` struct. |

## Dependencies

Runtime dependencies, from the generated `mix.exs`:

| Package | Version | Used for |
|---|---|---|
| `req` | ~> 0.5 | HTTP client (on Finch and Mint) |
| `jason` | ~> 1.4 | JSON |
| `mime` | ~> 2.0 | Content types |
| `brotli` | ~> 0.3 | Optional, `prod` only: decoding `br` responses |

The extra applications are `:logger` and `:crypto`. If a `br` response arrives and `brotli` is not installed, the call raises. The OpenTelemetry packages are used by the generated tests only. If `:opentelemetry_api` is not installed, trace-context injection does nothing. Other dependencies (`excoveralls`, `credo`, `dialyxir`, `ex_doc`, `testcontainers` and so on) are for development and tests.

## Options

| Option | Default | What it does |
|---|---|---|
| `packageName` | none | Name of the Mix application. |
| `moduleName` | none | Name of the top-level module. |
| `packageVersion` | `1.0.0` | Version written to `mix.exs`. |
| `repositoryUrl` | none | Source repository, written to the Hex links and the docs source URL. |
| `homepageUrl` | none | Home page, written to the Hex links and the docs home page. |
| `authors` | none | Comma-separated names, written to the Hex maintainers. |
| `documentationUrl` | none | Documentation location, written to the Hex links. |
| `issueTrackerUrl` | none | Issue tracker, written to the Hex links. |

## Idiom notes

- Configuration is data. `Configuration.new/1` and `TransportOptions.new/1` take a keyword list, and there are no builders.
- The API struct is the first argument of every operation. Optional trailing arguments (a server override, a content-type choice, a refresh URL) go into one `opts` keyword list.
- A missing required option raises `ArgumentError` at call time.
- There is no exception inheritance. `api_error?/1` tells you whether an exception is an API error, and `exceptions/0` lists the hierarchy for `rescue` clauses.
- Authenticators are a behaviour. The OAuth2 token manager is an `Agent` started by `start_link/1` and stopped by `Client.close/1`.
- The client is synchronous, with one `get_auth_headers` and no async variant.
- Structs are plain maps with no private fields, so authenticators hide their secrets through a custom `Inspect` implementation.
- Deprecated operations carry `@deprecated`. A deprecated model field gets a note in the module's `@moduledoc`, because `@deprecated` applies to functions only.
- Streaming is not supported. Req reads each response whole. Inject a transport if you need streaming.

## Known limits

- `format: date-time` decoding returns UTC. See the table note above.
- `format: decimal` and `number` are `float()` and lose exactness.
- Struct fields are not validated on construction beyond inline enums, so wrong-typed scalars pass.
- Fractional and quoted numbers are rejected when decoding. There is no 32-bit or 64-bit range check, because the typespec is the width-less `integer()`.
- A body with a charset other than UTF-8 (such as `shift_jis` or the `iso-8859-x` family) is not decoded. Erlang's `:unicode` module does not cover it.
- An optional nullable property cannot tell an absent key from an explicit `nil`.
- `allOf` with a discriminator is not implemented yet.
- Schema constraints (`pattern`, `minLength`, `maximum` and so on) are not enforced by the client.
- Callbacks and webhooks, XML bodies, Link Objects and mutual TLS are out of scope in every language.

## Under review

These are ideas from a type review in November 2026. None of them is decided, and none changes the generated SDK today.

- Check that `DateTime` and `Time` decoding does not silently convert a value such as `+05:30` to UTC where the generated README claims a round trip, and add a test for it.
- Tighten typespecs: `number()` instead of `float()` for a bare `number`, because Jason decodes `1` as an integer, and `%{optional(String.t()) => T}` for `additionalProperties`.
- A documentation-only `@type uuid :: String.t()` alias in the models module. The review advises against adding a UUID dependency.
- `URI.t()` for `uri`, and `:inet` address tuples for `ipv4` and `ipv6`. Both are in the standard library and need no floor change, but both are optional and lower value than the date-time check.
- `Decimal.t()` for `decimal` would need the `decimal` package as a dependency, so the README guidance stays unless that is accepted.
- No floor change is required by any of these ideas.

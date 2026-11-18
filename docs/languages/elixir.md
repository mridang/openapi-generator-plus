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
| `oneOf`, `anyOf` | the matched variant | `build/1` returns it. An `anyOf` can return an `AnyOfComposite` struct. The module of the union has `from_<variant>/1`, `<variant>?/1` and `as_<variant>/1` for each model variant. |

## Dependencies

Runtime dependencies, from the generated `mix.exs`:

| Package | Version | Used for |
|---|---|---|
| `req` | ~> 0.5 | HTTP client (on Finch and Mint) |
| `jason` | ~> 1.4 | JSON |
| `mime` | ~> 2.0 | Content types |
| `brotli` | ~> 0.3 | Optional, `prod` only: decoding `br` responses |

The extra applications are `:logger` and `:crypto`. If a `br` response arrives and `brotli` is not installed, the call raises. The OpenTelemetry packages are used by the generated tests only (`opentelemetry_api`, `opentelemetry`, and `opentelemetry_api_experimental` and `opentelemetry_experimental` for the metrics of the call hook recipe); the SDK depends on none of them. If `:opentelemetry_api` is not installed, trace-context injection does nothing. Other dependencies (`excoveralls`, `credo`, `dialyxir`, `ex_doc`, `testcontainers` and so on) are for development and tests.

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
| `envVarPrefix` | the API title without a trailing SDK, upper snake case | Prefix of the environment variables the authenticators read credentials from. |

## Idiom notes

- Configuration is data. `Configuration.new/1` and `TransportOptions.new/1` take a keyword list, and there are no builders.
- The API struct is the first argument of every operation. Optional trailing arguments (a server override, a content-type choice, a refresh URL) go into one `opts` keyword list.
- A missing required option raises `ArgumentError` at call time.
- Every operation takes an optional options struct, even one with no query, header, form or cookie parameter. It carries the per-call `timeout` (milliseconds), `headers` and `max_retries`. The `opts` keyword list takes the same three keys and wins over the struct.
- A credential that is not passed (`nil`) is read, once, when the authenticator is built, from an environment variable. Each security scheme reads its own variables, named `<PREFIX>_<FIELD>` (`PETSTORE_TOKEN`, `PETSTORE_API_KEY`, `PETSTORE_CLIENT_ID`), or `<PREFIX>_<SCHEME>_<FIELD>` for every scheme that shares a field name with another. An argument you pass wins, an empty variable counts as unset, and a credential found in neither place raises `ArgumentError` when the client is built. A bearer token or an API key can also be a zero-argument function that is called for every request, which takes the place of a token-provider callback. The generated README lists the variable of every credential.
- A union module has a typed function for each model variant: `from_<variant>/1` checks the value and returns it (it raises `ArgumentError` for another value), `<variant>?/1` tells whether a built value is that variant, and `as_<variant>/1` returns the value or `nil`. The predicate is spelled `<variant>?` because Elixir reserves the `is_` prefix for guards.
- A property that is a `$ref` with a `default` written beside it (an OpenAPI 3.1 sibling keyword) starts at that default: a referenced enum becomes the matching atom, so `%StockItem{priority: :number_1}.availability` is `:on_hold`, and a payload without the property decodes to it too.
- There is no exception inheritance. `api_error?/1` tells you whether an exception is an API error, and `exceptions/0` lists the hierarchy for `rescue` clauses.
- The client takes one optional credential per security scheme, named after the scheme, in a keyword list: `Client.new(authenticator, transport_options, pet_store_basic: credential, admin_basic: other)`. The authenticator is optional (`nil`) when at least one credential is given. An operation uses the credential of the first scheme in its own security order that the client holds, and falls back to the authenticator when it holds none of them. An operation declared `security: []` sends no credential. There is no per-call credential: the options struct has no `auth` field and `:auth` in `opts` is rejected as an unknown key. A key that names no scheme of the API, or no credential at all, raises `ArgumentError`.
- `Client.close/1` is graceful and idempotent. It marks the client closed. A call already in flight finishes, a call that starts afterwards raises `RuntimeError` ("ApiClient has been closed and can no longer send requests"), and the processes the SDK started (OAuth2 token managers, the OpenID Connect discovery cache) are stopped when the last call in flight is over, or at once when none is. Req and Finch own the HTTP pool, so there is no transport to release, and a transport passed through `req_options` is never touched.
- A cookie parameter is sent raw when every character is a legal RFC 6265 cookie-octet. Any other value raises `ArgumentError` before the request is sent; the caller percent-encodes it first.
- Authenticators are a behaviour. The OAuth2 token manager is an `Agent` started by `start_link/1` and stopped by `Client.close/1`.
- The client is synchronous, with one `get_auth_headers` and no async variant.
- Structs are plain maps with no private fields, so authenticators hide their secrets through a custom `Inspect` implementation.
- Deprecated operations carry `@deprecated`. A deprecated model field gets a note in the module's `@moduledoc`, because `@deprecated` applies to functions only.
- The call hook is a module that implements the `CallHook` behaviour (`start/2` and `finish/2`), or `{module, opts}`, which is called as `start/3`. Data is immutable, so `start` returns the headers to send and an opaque state that `finish` receives, and a `nil` state means there is no observer. `CallInfo` and `CallOutcome` are structs, `CallHook.chain/1` returns a hook that starts them in order and finishes their observers in reverse, and the hook goes in through the `call_hook:` option of `Configuration.new/1`, `Client.new/4`, `with_token/4` and `with_authenticator/3` (there is no builder). Elixir has no cancellation handle, so a call that ends with an exit or a throw is reported to `finish` as `{:exit, reason}` or `{:throw, value}` and then exits or throws as before; the recipe classifies it as `cancelled`. The hook's header changes are kept as a delta and put back on every retry and on the replay after a 401, where the fresh credentials still win over a hook header of the same name.
- The SDK ships no OpenTelemetry adapter. The traces API is stable, but the metrics API lives in `opentelemetry_api_experimental` and is not, so the identical span, count and histogram promise is met by a recipe, a `TelemetryCallHook` module the README shows. The README prints it from the partial `call_hook_recipe.mustache`, and `test/call_hook_recipe_test.exs` includes the same partial, so the text a user copies is the text under test. `TraceContextUtil` still injects the propagated trace context before `start`; the recipe injects it again for the client span, and its value replaces the first.
- Streaming is not supported. Req reads each response whole. Inject a transport if you need streaming.

## Known limits

- `format: date-time` decoding returns UTC. See the table note above.
- `format: decimal` and `number` are `float()` and lose exactness.
- Building a struct never validates anything: a wrong-typed scalar or an atom that is no enum member builds fine. The checks run when a response is decoded (a wrong type, a missing required property and an unknown enum string raise `SerializationError`) and when a body is encoded (an inline enum atom outside its members raises).
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

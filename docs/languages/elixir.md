# Elixir

The `elixir-plus` generator (`BetterElixirCodegen`) produces a Mix project with one module per API group, model, error and authenticator. It uses Req for HTTP, Jason for JSON and the `decimal` package for exact decimals. For the rules shared by all twelve languages see [how it works](../how-it-works.md), for the type table across languages see [types](../types.md), and for settled cross-language choices see [decisions](../decisions.md).

## Versions and support policy

The generated `mix.exs` requires `elixir: "~> 1.17"`, and the generated README asks for Elixir 1.17 or newer on Erlang/OTP 27 or newer. The floor is the oldest Elixir release that still gets security fixes, and it moves up when that release reaches end of life. The generator builds and checks the golden project on Elixir 1.19.

## Type mapping

Types are the typespecs that appear in the generated modules.

| OpenAPI type and format | Elixir type | Notes |
|---|---|---|
| `string` | `String.t()` | Also used for `email`, `hostname`, `password` and unknown formats. |
| `string` / `ipv4`, `ipv6` | `:inet.ip4_address()`, `:inet.ip6_address()` | The `:inet` tuples. A leading zero, a prefix length, a zone id, brackets, whitespace and the wrong family fail the decode with `SerializationError`; an address is written in canonical text. |
| `string` / `date` | `Date.t()` | |
| `string` / `date-time` | `DateTime.t()` | Decoded from ISO 8601. Elixir returns the instant in UTC, so check that the offset you need survives if you rely on it. |
| `string` / `time` | `Time.t()` | |
| `string` / `duration` | `Duration.t()` | The wire form is the protobuf-JSON string such as `"3600s"`, not ISO 8601. |
| `string` / `uuid` | `String.t()` | Neither Elixir nor Erlang has a UUID type. Written in lower case and validated when sent as a parameter. |
| `string` / `byte` | `binary()` | Base64 with `Base`. |
| `string` / `binary`, file bodies | `BodySource` for a raw binary request body (a multipart file part stays `binary()`); `ByteStream` (an `Enumerable` of binaries) for a response | The response body is read as you consume it, and a raw upload is sent as it is read, see Streaming. |
| `string` / `uri` | `String.t()` | Checked as an absolute URI on decode. Not `URI.t()`, because `URI.to_string/1` lowercases the scheme and drops a default port, and `URI.new/1` refuses a square bracket in a path, so the text would not round-trip (D146). |
| `integer` (any format) | `integer()` | BEAM integers are unbounded. `int32` and `int64` are not range checked. |
| `number` / `float`, `double`, and no format | `float()` | |
| `number` / `decimal` | `Decimal.t()` | Exact (D135). The digits are read from the JSON text, never through a float, and written back as a bare JSON number in plain notation with every digit. A number with more than 34 significant digits after a decimal point, which the `decimal` package refuses to parse, fails with `SerializationError` instead of being rounded; an integer of any length is exact. |
| `string` / `decimal` | `Decimal.t()` | The same `Decimal`, read only from a JSON string (a bare JSON number fails with `SerializationError`) and written back as a JSON string that keeps its decimal places (`"12.50"`). |
| `boolean` | `boolean()` | |
| `array` | `[T]` | |
| `array` with `uniqueItems` | `MapSet.t(T)` | Sent in the `MapSet`'s own order. |
| `object` with `additionalProperties` | `map()` | String keys.. `T` is the declared value type; a free-form bucket keeps the any-type |
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
| `decimal` | ~> 3.2 | `format: decimal` values |
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
- `Configuration.new/1` takes `validate_constraints:` (off by default, D189). When it is `true`, every request body is checked against the `minLength`, `maxLength`, `pattern`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minItems`, `maxItems`, `minProperties` and `maxProperties` keywords of its schema and of the models it holds before anything is sent, and a body that breaks one raises `ArgumentError` (`Invalid request body: label must be at least 2 characters long`). Lengths count code points, a pattern is searched with `Regex` (unicode) and one it cannot compile is not enforced, and responses are never checked.
- `TransportOptions` takes `max_response_bytes` (off by default): a buffered response whose body, raw or decompressed, passes it fails with `ResponseTooLargeError` (D138); zero, a negative value or a non-integer raises `ArgumentError` in `TransportOptions.new/1`. The body is read through a Req `into:` function that counts the bytes as they arrive and halts (dropping the connection) once they, or the declared `Content-Length`, pass the limit, and gzip and deflate are inflated a piece at a time with `:zlib.safeInflate/2`, stopping as soon as the output passes the limit. A zstd body is decoded the same way, a few input bytes at a time, and a br body through the `:brotli` decoder's streaming calls, its output counted each time the decoder hands it over (at most one brotli window), so every codec stops as soon as the output passes the limit and the client asks for the same codecs with or without a limit.
- The API struct is the first argument of every operation. Optional trailing arguments (a server override, a content-type choice, a refresh URL) go into one `opts` keyword list.
- A missing required option raises `ArgumentError` at call time.
- Every operation takes an optional options struct, even one with no query, header, form or cookie parameter. It carries the per-call `timeout` (milliseconds), `headers` and `max_retries`. The `opts` keyword list takes the same three keys and wins over the struct. The per-call timeout, like the transport one (default 60 seconds), is one end-to-end deadline that covers connecting, every redirect hop and the whole body read of a buffered call (D137).
- A credential that is not passed (`nil`) is read, once, when the authenticator is built, from an environment variable. Each security scheme reads its own variables, named `<PREFIX>_<FIELD>` (`PETSTORE_TOKEN`, `PETSTORE_API_KEY`, `PETSTORE_CLIENT_ID`), or `<PREFIX>_<SCHEME>_<FIELD>` for every scheme that shares a field name with another. An argument you pass wins, an empty variable counts as unset, and a credential found in neither place raises `ArgumentError` when the client is built. A bearer token or an API key can also be a zero-argument function that is called for every request, which takes the place of a token-provider callback. The generated README lists the variable of every credential.
- A security scheme the API marks `deprecated: true` keeps its authenticator, and the authenticator's `@moduledoc` says so (no `@deprecated` attribute, D190); a scheme that declares `oauth2MetadataUrl` exposes it through `metadata_url/0` on its authenticator module, which the SDK never fetches.
- The OAuth2 device authorization flow (D191) is `OAuth2DeviceAuthorizationAuthenticator.new(host, client_id, device_authorization_url, token_url, scopes, opts)`: `request_device_authorization/1` returns a `%DeviceAuthorization{}` struct (defined in the same file) and `poll_for_token/2` waits through the `:sleep` and `:clock` options of `new/6`, which only the tests pass.
- HTTP Digest (D192) is `DigestAuthenticator.new(host, username, password)`, reading `<PREFIX>_USERNAME` and `<PREFIX>_PASSWORD` like Basic; it sends nothing until the server's 401 challenge, which the SDK answers with `:crypto` (MD5 or SHA-256) before replaying the request once. The remembered challenge and the nonce count live in an `Agent` that every copy of the struct shares.
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
- Streaming. An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns `{:ok, byte_stream}` from the one operation function (there is no second function), and `..._with_http_info` returns an `ApiResult` whose `data` is the stream and whose `raw_body` is empty. `ByteStream` is a struct that implements `Enumerable` of binaries and carries `status_code` and the lower-cased `headers`. `ByteStream.close/1` is idempotent and aborts the connection instead of draining it, `ByteStream.read_all/1` (and `read_all!/1`) reads the rest and leaves the stream closed, and `inspect/1` shows only the status and the bytes delivered. Enumerating a stream to its end, or stopping early, closes it. The transport behaviour gets the optional callback `send_streaming/6`, which returns an `ApiStreamResponse` (status, headers, an `Enumerable` body and a `close` function); a transport without it keeps working for buffered calls and fails a stream call with a `RuntimeError` that names it. `DefaultApiClient` makes the request in a process of its own (Req's `into: :self`) that relays the body one read at a time, so a stream can be read from any process, a read stops at `TransportOptions` `idle_timeout` (default 60 seconds, `0` or `nil` disables it) and `close/1` and `Client.close/1` abort the connection from outside. The owner process is linked to the process that made the call, so ending that process releases the connection. `timeout` ends at the response headers, a connection reset or a body shorter than its `Content-Length` raises `StreamError` (a `NetworkError`, status 0), a stalled body raises `NetworkTimeoutError`, and nothing is retried after the first body byte. A stream asks for gzip and deflate only and decodes them as they arrive; a stream that arrives encoded with br or zstd raises `ApiError`. Req sends the chunks of a body as messages without flow control, so a consumer that is slower than the server holds the unread chunks in the owner process's mailbox. Uploads are unchanged.
- Uploads. An operation whose request body is raw binary takes a `BodySource` (a struct in the SDK package) in every signature (the operation, its bang form, `_with_http_info` and the behaviour): `BodySource.of/1` (bytes), `BodySource.of_file/1` and `BodySource.from_factory/2` (a function that returns a fresh `Enumerable` of binaries, optionally with a length) are replayable, `BodySource.once/2` (an `Enumerable` of binaries or an open IO device, optionally with a length) is single use. The body is sent as it is read, never buffered: a known length goes out as `Content-Length` and an unknown length is sent chunked. A replayable body is opened again for a retry after a 5xx, a 429 or a network error, for a 401 replay and for a 307 or 308 redirect. A single use body is never retried or replayed, so the first result is returned unchanged, a 307 or 308 raises `ApiError` with `Refusing to replay a streamed request body`, and a stream request with a single use body is never reconnected. The SDK never closes an IO device passed to `once/2` and closes what it opens from a file or a factory. A raw binary body must be a `BodySource`: a bare binary raises `ArgumentError`. A multipart body stays buffered. A custom transport receives the `BodySource` as the `body` and reads it with `BodySource.open/1`.
- Pagination. An operation that is paged (see `docs/design/pagination.md`) has `<operation>_pages(api, ..., options, opts)` beside its plain function, with the same parameters, and returns a `Paginator`, a struct that implements `Enumerable` of the items. `Paginator.items/1` is the same sequence as a `Stream`, and `Paginator.pages/1` is a `Stream` of `Page` structs (`items`, `next_cursor` and `result`, the `ApiResult` of the request). Both are lazy: nothing is sent before the first read, one request goes out per page and enumerating again starts from the starting position. A failed page raises its SDK error from the read that needed it, never an error tuple, after the earlier items were delivered. A cursor equal to the one just sent raises `PaginationError`, a member of the root error family that is neither a network error nor an API error. A `Link` header list is followed only within the origin of the client. The generator also pages four other shapes: an id cursor (a `starting_after`-style parameter with a `has_more` flag, ending when `has_more` is false), an offset and limit list (the offset advances by the items received, and the list ends on an empty page or when `has_more` is false), a page-number list (the page number advances by one, and the list ends on an empty page or when `has_more` is false), and a next URL list (the URL is read from the body of each page and followed only within the origin of the client, like a `Link` list).
- JSON Lines and server-sent events. An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or `text/event-stream` has no buffered function and no `_with_http_info` or bang variant of one: it has `<operation>_stream(api, ..., options, opts)` and `<operation>_stream!`, taking the operation's parameters, its options struct and an `opts` keyword list, and returning `{:ok, stream}`. The stream is a `JsonLinesStream` of the decoded items or an `SseStream` of `ServerSentEvent` structs (`id`, `event`, `data`, `retry`, `raw`); both implement `Enumerable`, carry `status_code` and `headers`, have an idempotent `close/1` and an `inspect/1` that shows only counts, and close themselves when enumeration ends or stops early. The stream settings are keys of `opts` (`:idle_timeout`, `:max_event_bytes`, and for server-sent events `:max_reconnects`, `:reconnect_delay` and `:last_event_id`), and the first request is sent by the call, so its failure is the `{:error, exception}`; a failed read raises. A reconnect runs the call pipeline again (hook, headers, authentication) through the same `<operation>_open` function. The parser and the line splitter are `EventStream`; `reconnect_wait/5` is pure, so the wait order (server `retry`, `Retry-After`, `:reconnect_delay` doubling to 30 seconds with up to 25% jitter) is tested without sleeping. A line, event or data value that does not decode raises `SerializationError`, an `error` event raises `ApiError`, and the generator option `sseTerminator` ends the stream. JSON Lines decodes gzip and deflate incrementally; a stream asks for no other encoding.
- Testing your code: `<Group>ApiBehaviour` in the group file, with a `@callback` for the full arity of each operation, its bang form and its `_with_http_info` form; `PetApi` declares `@behaviour` and `@impl true`. A fake is a module (Elixir calls a module, not a value), so code under test takes the group module. There is no client behaviour: `Client` is a struct of group structs. The tests use a hand-written module, not Mox.
- With `generateExamples` on, the package ships `examples/quickstart.ex`, a module `Quickstart` whose `main/0` reads `{PREFIX}_BASE_URL` and `{PREFIX}_TOKEN` (`System.fetch_env!/1` raises when one is missing), builds a client with `with_token/2`, calls the example operation and closes the client in an `after` clause. `examples/` is outside the package's compile paths, so it is never compiled with the package or published: `make examples` compiles it with `elixirc --warnings-as-errors` against the built package, and `make examples-run` runs it with `mix run`.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `PetstoreClient.CallHook` is the name to look for in this SDK.

```elixir
defmodule TenantHook do
  @behaviour PetstoreClient.CallHook

  @impl true
  def start(_info, headers), do: {Map.put(headers, "X-Tenant", "a"), nil}

  @impl true
  def finish(_state, _outcome), do: :ok
end

client = PetstoreClient.Client.with_token("/api/v3", "token", nil, call_hook: TenantHook)
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```elixir
eu = PetstoreClient.Client.with_token("https://eu.api.example.com", "eu-token")
us = PetstoreClient.Client.with_token("https://us.api.example.com", "us-token")
```

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `models/animal.ex`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `..._with_http_info` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `PetstoreClient.Errors.ApiError` that holds the status code, the response headers and the response body. A live response object is not exposed, because that would need streaming (see [decisions](../decisions.md)); to see or change what goes over the wire, use the call hook above or the transport you supply.

```elixir
{:ok, result} = PetstoreClient.Api.PetApi.add_pet_with_http_info(client.pet, pet)
result.status_code
result.headers["etag"]
result.raw_body

{:error, %PetstoreClient.Errors.ApiError{} = error} = PetstoreClient.Api.PetApi.add_pet(client.pet, pet)
{error.status_code, error.response_headers, error.response_body}
```

## Known limits

- `format: date-time` decoding returns UTC. See the table note above.
- A bare `number` with no format, `float` and `double` are `float()` and lose exactness; `format: decimal` is the exact `Decimal.t()`. An array of `type: string` decimals is read as numbers, like an array of `type: number` decimals.
- Building a struct never validates anything: a wrong-typed scalar or an atom that is no enum member builds fine. The checks run when a response is decoded (a wrong type and a missing required property raise `SerializationError`, and an unknown enum string decodes to `:unknown_default_open_api`, D117) and when a body is encoded (an inline enum atom outside its members, the catch-all included, raises).
- Fractional and quoted numbers are rejected when decoding. There is no 32-bit or 64-bit range check, because the typespec is the width-less `integer()`.
- A body with a charset other than UTF-8 (such as `shift_jis` or the `iso-8859-x` family) is not decoded. Erlang's `:unicode` module does not cover it.
- An optional nullable property cannot tell an absent key from an explicit `nil`.
- `allOf` with a discriminator is not implemented yet.
- Schema constraints (`pattern`, `minLength`, `maximum` and so on) are not enforced by the client unless `validate_constraints: true` is set on the configuration, which checks request bodies only.
- Callbacks and webhooks, XML bodies, Link Objects and mutual TLS are out of scope in every language.
- Operations are blocking only, as a plain function call that blocks the calling process, by decision D111. There is no asynchronous variant of an operation, because each SDK follows the style of its own language. Run the call on your own thread or task when you need concurrency.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.
- A security requirement that names several schemes together (for example a bearer token and an API key) sends the credentials of all of them, by decision D116. The client must hold every credential of the requirement; an alternative the client holds only in part is used only when no other alternative is complete. A requirement that names one scheme works as before.
- The OpenID Connect authenticator sends a nonce and checks the claims of the `id_token` (nonce, issuer, audience, expiry), but it does not verify the token's signature, by decision D140. Signature checking can be added later without changing the public API, because the discovery document already names the provider's keys (`jwks_uri`).

## Under review

These are ideas from a type review in November 2026. None of them is decided, and none changes the generated SDK today.

- Check that `DateTime` and `Time` decoding does not silently convert a value such as `+05:30` to UTC where the generated README claims a round trip, and add a test for it.
- Tighten typespecs: `number()` instead of `float()` for a bare `number`, because Jason decodes `1` as an integer, and `%{optional(String.t()) => T}` for `additionalProperties`.
- A documentation-only `@type uuid :: String.t()` alias in the models module. The review advises against adding a UUID dependency.
- No floor change is required by any of these ideas.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `:otel_propagator_text_map.inject` reads the process's context; the headers are built in the calling process (`Tracer.with_span`).

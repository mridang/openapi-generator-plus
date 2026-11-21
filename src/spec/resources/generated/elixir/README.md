# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Elixir SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **Elixir** 1.17+
- **Erlang/OTP** 27+
- **mix** (ships with Elixir)

## Build

```bash
mix deps.get && mix compile
```

## Test

```bash
mix test
```

## Format

```bash
mix format
```

## Lint

```bash
mix credo --strict
```

## Static analysis

```bash
mix dialyzer
```

## Dependency audit

```bash
mix hex.audit
```

## Package

- Name: `petstore_client`
- Version: `1.0.0`
- License: MIT
- Authors: Ada Lovelace, Grace Hopper
- Repository: <https://example.com/acme/petstore-client>
- Homepage: <https://example.com/petstore>
- Documentation: <https://example.com/petstore/docs>
- Issue tracker: <https://example.com/acme/petstore-client/issues>

## Usage

Add `{:petstore_client, "~> 1.0.0"}` to the `deps` in `mix.exs`, then:

```elixir statements
host = System.fetch_env!("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL")
token = System.fetch_env!("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN")
client = PetstoreClient.Client.with_token(host, token)

try do
  {:ok, result} =
    PetstoreClient.Api.StoreApi.get_defaults(client.store)

  IO.inspect(result)
after
  :ok = PetstoreClient.Client.close(client)
end
```

[SKILLS.md](SKILLS.md) covers authentication, error handling, transport options and models.

### Streaming

An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns a `ByteStream` instead of the whole body in memory, so a large download never has to fit in memory. The call returns as soon as the response headers arrive, and the body is read as you consume it. A `ByteStream` has the status code, the lower-cased headers and an idempotent `close/1` that aborts the connection instead of draining it, so close it when you stop early (in an `after` block). `read_all/1` reads the rest into memory and leaves the stream closed.

```elixir file
defmodule Download do
  def save_to(client, pet_id, document_id, options, path) do
    {:ok, stream} = PetstoreClient.Api.PetApi.download_pet_document(client.pet, pet_id, document_id, options)

    try do
      IO.puts(stream.status_code)
      Enum.into(stream, File.stream!(path))
    after
      PetstoreClient.ByteStream.close(stream)
    end
  end
end
```

The call `timeout` covers the wait for the response headers and stops there. `idle_timeout` (in `TransportOptions`, default 60 seconds, `0` or `nil` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutError`. Retries, redirects, token refresh and the error contract apply up to the headers exactly as for a buffered call; a request is never retried after the first body byte, and a non-2xx status raises the usual typed error with its body read up to 64 KiB and never returns a stream. A connection reset, or a body that ends before its declared `Content-Length`, raises `StreamError`, a `NetworkError` with status 0. `Client.close/1` cancels the streams that are still open, and their next read raises the closed-client error. A custom transport that does not implement `send_streaming` fails a stream call with `RuntimeError` naming it, and its buffered calls keep working.

An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or server-sent events (`text/event-stream`) has no buffered function. It has a `<operation>_stream` function that takes the operation's parameters, its options struct and the trailing `opts` keyword list that carries the stream settings, and returns `{:ok, stream}`, a `JsonLinesStream` of the schema type or an `SseStream` of `ServerSentEvent` (`id`, `event`, `data`, `retry` and the raw `data` text; the `data` is the schema of the `data`, or a string). Enumerate it, and close it when you stop early. A JSON Lines line that does not decode, and a line or event longer than `max_event_bytes` (1 MiB), raise `SerializationError` and end the stream.

```elixir file
defmodule ExportLines do
  def run(client, options) do
    {:ok, stream} = PetstoreClient.Api.StoreApi.export_orders_stream(client.store, options)

    try do
      Enum.each(stream, fn item -> IO.inspect(item) end)
    after
      PetstoreClient.JsonLinesStream.close(stream)
    end
  end
end
```

```elixir file
defmodule FollowEvents do
  def run(client, options) do
    {:ok, stream} = PetstoreClient.Api.StoreApi.watch_notices_stream(client.store, options, max_reconnects: 3)

    try do
      Enum.each(stream, fn item -> IO.inspect(item) end)
    after
      PetstoreClient.SseStream.close(stream)
    end
  end
end
```

An event stream reconnects when the connection ends, fails or stays silent for the idle timeout: it asks again with fresh headers and `Last-Event-ID`, waits the server's `retry` value or `reconnect_delay` (1 second, doubling to 30 seconds with jitter), and raises the last failure after `max_reconnects` failures in a row without an event (5; 0 for a request with a body; `-1` is unlimited). It never reconnects after a 204, a status other than 408, 429 and 5xx, a response that is not `text/event-stream`, a cancellation or `Client.close/1`. An event named `error` raises `ApiError` with the response status and the data as its body, and a comment line such as a heartbeat counts as activity for the idle timeout. The generator option `sseTerminator` names a data value (such as `[DONE]`) that ends the stream and is not delivered. `opts` also takes `idle_timeout` and `last_event_id`.

### Custom HTTP client

Pass Req options such as an `:adapter` or a `:plug` through `TransportOptions.new(req_options: [...])`. The SDK's own settings win and the SDK never closes a transport you supply.

### Testing your code

Every API group has an interface (`PetstoreClient.Api.PetApiBehaviour`) and the client is a struct of the group structs, so a test can replace a group with a hand-written fake module: no mocking library, no network.

```elixir file
defmodule FakeGroup do
  @behaviour PetstoreClient.Api.PetApiBehaviour

  # Every callback gets a stub; replace the ones your code calls with the answers your test needs.
  for {name, arity} <- PetstoreClient.Api.PetApiBehaviour.behaviour_info(:callbacks) do
    arguments = for index <- 1..arity//1, do: Macro.var(:"_argument#{index}", __MODULE__)

    @impl true
    def unquote(name)(unquote_splicing(arguments)), do: {:error, :not_faked}
  end
end

defmodule Subject do
  # Code under test takes the group module, never the generated one.
  def fetch(group, api), do: group.url(api)
end
```

A new operation in the API adds a callback to the behaviour, and the loop above gives it a stub the moment you regenerate, so a fake module that implements the whole behaviour never warns about a missing callback; keep one fake module in your test support code so only the operation you fake is written.

### Retries

A failed call is retried up to `max_retries` times (default `2`; `0` turns retries off): after a network error that happened before any response, and after a response with status 408, 409, 429 or 500 to 599 other than 501. The wait starts at 0.5 s and doubles up to 8 s, less up to 25% jitter, and a `Retry-After` header (seconds or an HTTP date, capped at 60 s) replaces it. Every retry is a fresh request with its authentication headers rebuilt, and no retry runs past the end-to-end `timeout`. A 401 on a call authenticated by OAuth2 or OpenID Connect drops the cached access token (the refresh token is kept) and replays the request once, rebuilt; a second 401 is the usual `UnauthorizedError`.

```elixir file
defmodule Retries do
  def transport, do: PetstoreClient.TransportOptions.new(max_retries: 0)
end
```

### Per-call options

Every operation takes an optional options struct, even one with no query, header, form or cookie parameter. It carries the per-call settings `timeout` (milliseconds), `headers` (a map) and `max_retries`; there is no per-call credential (see Credentials). The same three settings can also be passed as `:timeout`, `:headers` and `:max_retries` in the trailing `opts` keyword list. A setting replaces the client's own for that call only; the `opts` key wins over the options struct. `headers` are merged after the default headers and before the authentication headers, and replace a header of the same name in any casing.

```elixir file
defmodule PerCall do
  def call(api) do
    options = %PetstoreClient.Api.Options.GetDefaultsOptions{timeout: 5_000, headers: %{"X-Trace" => "1"}, max_retries: 0}
    PetstoreClient.Api.StoreApi.get_defaults(api, options)
    PetstoreClient.Api.StoreApi.get_defaults(api, nil, timeout: 5_000)
  end
end
```

### Credentials

`PetstoreClient.Client.new/3` takes one optional credential for each security scheme of the API, named after the scheme in a keyword list: `pet_store_basic`, `admin_basic`, `pet_store_bearer`, `service_token`, `api_key_header`, `internal_api_key`, `api_key_query`, `session_cookie`, `machine_auth`, `legacy_auth`, `user_auth`, `browser_auth`, `sso_auth`. Each is an authenticator. An operation uses the credential of the first scheme its own security requirement lists that the client holds, and there is no per-call override. The optional first argument, the authenticator, serves an operation that none of the scheme credentials matches. An operation whose spec says `security: []` sends no credential. The base URL comes from the first credential given, the authenticator first. The client is refused (`ArgumentError`) when it gets no credential at all, or a key that names no scheme of this API.

```elixir file
defmodule SchemeCredential do
  def client(credential) do
    PetstoreClient.Client.new(nil, nil, pet_store_basic: credential)
  end
end
```

An authenticator takes its credentials as arguments. A credential you do not pass (a `nil`) is read, once, when the authenticator is built, from an environment variable; an argument you pass always wins, and an empty variable counts as unset. A required credential found in neither place raises `ArgumentError` when the authenticator, and so the client, is built. A bearer token or an API key may also be a zero-argument function, called for every request, so a token that rotates is read fresh each time.

Each security scheme reads its own variables, named `SWAGGER_PETSTORE_OPEN_API_3_0_<FIELD>`, or `SWAGGER_PETSTORE_OPEN_API_3_0_<SCHEME>_<FIELD>` for every scheme that shares a field name with another:

| Security scheme | Credential | Environment variable |
|---|---|---|
| `adminBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` |
| `adminBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` |
| `apiKeyHeader` | `api_key` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` |
| `apiKeyQuery` | `api_key` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` |
| `browserAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` |
| `internalApiKey` | `api_key` | `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` |
| `legacyAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` |
| `legacyAuth` | `client_secret` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` |
| `legacyAuth` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` |
| `legacyAuth` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` |
| `machineAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` |
| `machineAuth` | `client_secret` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` |
| `petStoreBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` |
| `petStoreBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` |
| `petStoreBearer` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` |
| `serviceToken` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` |
| `sessionCookie` | `api_key` | `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` |
| `ssoAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` |
| `ssoAuth` | `client_secret` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` |
| `ssoAuth` | `redirect_uri` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` |
| `userAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` |
| `userAuth` | `client_secret` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` |
| `userAuth` | `redirect_uri` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` |

An authenticator you build without a scheme (`BearerAuthenticator`, `BasicAuthenticator` and `ApiKeyAuthenticator` on their own) reads `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN`, `SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD` or `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY`. Set `envVarPrefix` when generating to change the `SWAGGER_PETSTORE_OPEN_API_3_0` prefix.

### Cancellation

There is no cancellation argument. A caller stops a call by terminating the process that made it (a process exit signal, the idiom here), and `timeout` on `TransportOptions` ends a call with `NetworkTimeoutError`.

### Closing the client

`PetstoreClient.Client.close/1` closes the client and is idempotent. A call already in flight finishes; a call that starts after it raises `RuntimeError` with the message "ApiClient has been closed and can no longer send requests". Streams that are still open are cancelled. The processes the SDK started for the client (OAuth2 token managers, OpenID Connect discovery) are stopped when the last call in flight is over, or at once when none is. Req and Finch own the HTTP pool, and a transport you supply through `req_options` is never closed by the SDK.

### Cookie parameters

A cookie parameter goes into the `Cookie` header exactly as given when every character is a legal RFC 6265 cookie-octet: printable ASCII except space, double quote, comma, semicolon and backslash. A value with any other character fails with `ArgumentError` before the request is sent, so percent-encode such a value yourself first. The SDK never encodes it for you, because a cookie parser does not decode it.

### Raw responses, request id and logging

Every operation has a `_with_http_info` variant that returns the status, headers and body as an `ApiResult`. With `inject_request_id: true` on `TransportOptions`, the SDK sends an `X-Request-ID` it generates, unless the caller already set one. It does not log: wrap a call, or give `TransportOptions` a Req `:adapter` or step through `req_options`, to log requests.

An error carries the request it answers: `request_method` and `request_path` (the path without the query string), and its message has a `Request: GET /path` line after the status line.

### Hooks and telemetry

Every operation calls one small, optional hook around the call. The hook can edit the request headers before the request is sent and observe the status or the error afterwards, and without a hook set the call does nothing extra.

A hook is a module that implements the `PetstoreClient.CallHook` behaviour, or `{module, opts}`, which is called as `module.start(info, headers, opts)`. `info` is a `CallInfo` with `operation_id` (exactly as the spec writes it), `method`, `url_template` (the spec path, for example `/items/{id}`), `url` (without query string and fragment) and `server_address` (the host, lower-case, without port or IPv6 brackets). `headers` is the final set of request headers: defaults, call headers, authentication, cookies and trace context are already in it. `start` returns the headers to send and a state, or `nil` for no change and nothing to observe. When the state is not `nil`, `finish(state, outcome)` is called once the call has ended. `outcome` is a `CallOutcome` with `status_code` (0 when no response arrived), `headers` (lower-cased names, `nil` when no response arrived) and `error` (the very error the caller receives, or `nil`). Set the hook with the `call_hook:` option of `Configuration.new/1` or of the client, and use `CallHook.chain([first, second])` to run several: they start in the order given and finish in the reverse order.

`start` runs once per operation call, however many times the transport sends the request, and `finish` runs exactly once on every way the call can end. A header the hook sets is on every send of the call, and the fresh credentials of the one replay after a 401 still replace a header of the same name. An error raised by `start` reaches the caller unchanged and nothing is sent. An error raised by `finish` is swallowed. The hook runs above the transport, so it also runs when you supply your own, and it runs inline in the calling process, so it must not block. Headers are a plain map: reuse the spelling of an existing name to replace it.

```elixir file
defmodule TenantHook do
  @behaviour PetstoreClient.CallHook

  @impl true
  def start(_info, headers), do: {Map.put(headers, "X-Tenant", "a"), nil}

  @impl true
  def finish(_state, _outcome), do: :ok
end

defmodule TenantClient do
  def build do
    PetstoreClient.Client.with_token("https://api.example.com", "token", nil, call_hook: TenantHook)
  end
end
```

#### OpenTelemetry

This SDK ships no OpenTelemetry adapter and the SDK itself depends on no OpenTelemetry package: the Elixir OpenTelemetry metrics API is not stable yet, so the same hook is shown as a recipe instead. It records one span per call, named with the operation id, of kind client, with `http.request.method`, `url.template`, `server.address`, `http.response.status_code` (only when a response arrived) and `error.type` (the status code as text for a status of 400 or more, otherwise `timeout`, `network_error`, `serialization_error`, `cancelled` or `_OTHER`). It also records the counter `http.client.request.count` (unit `{request}`) and the histogram `http.client.request.duration` (unit `s`) with the same attributes, under an instrumentation scope named after the package and carrying its version. It injects the `traceparent` header itself, so the span sent to the server is the client span. Add `opentelemetry_api` and `opentelemetry_api_experimental` to your own `mix.exs`; without an OpenTelemetry SDK it does nothing. The module at the end of the recipe passes it as the `call_hook`.

```elixir file
defmodule TelemetryCallHook do
  @moduledoc """
  A call hook that records one span, one request count and one duration per
  call. It needs `opentelemetry_api` and `opentelemetry_api_experimental` in
  your own `mix.exs`, and records nothing until an OpenTelemetry SDK runs.

  Pass it as `{TelemetryCallHook, tracer: tracer, meter: meter}` to use a
  tracer and a meter other than the global ones.
  """

  @behaviour PetstoreClient.CallHook

  @scope_name :petstore_client
  @scope_version "1.0.0"

  @impl true
  def start(info, headers), do: start(info, headers, [])

  @impl true
  def start(info, headers, opts) do
    tracer =
      Keyword.get_lazy(opts, :tracer, fn ->
        :opentelemetry.get_tracer(@scope_name, @scope_version, :undefined)
      end)

    meter =
      Keyword.get_lazy(opts, :meter, fn ->
        :opentelemetry_experimental.get_meter(
          :opentelemetry.instrumentation_scope(@scope_name, @scope_version, :undefined)
        )
      end)

    attributes = %{
      "http.request.method" => info.method,
      "url.template" => info.url_template,
      "server.address" => info.server_address
    }

    span =
      :otel_tracer.start_span(tracer, info.operation_id, %{
        kind: :client,
        attributes: attributes
      })

    # The hook writes the traceparent itself, so the request names the client span.
    headers =
      :otel_propagator_text_map.inject_from(
        :otel_tracer.set_current_span(:otel_ctx.get_current(), span),
        :opentelemetry.get_text_map_injector(),
        headers,
        fn key, value, carrier -> Map.put(carrier, key, value) end
      )

    {headers,
     %{span: span, meter: meter, attributes: attributes, started: System.monotonic_time()}}
  end

  @impl true
  def finish(state, outcome) do
    failure = error_type(outcome)

    attributes =
      state.attributes
      |> then(fn attributes ->
        if outcome.status_code > 0,
          do: Map.put(attributes, "http.response.status_code", outcome.status_code),
          else: attributes
      end)
      |> then(fn attributes ->
        if failure, do: Map.put(attributes, "error.type", failure), else: attributes
      end)

    :otel_span.set_attributes(
      state.span,
      Map.take(attributes, ["http.response.status_code", "error.type"])
    )

    if failure, do: :otel_span.set_status(state.span, :opentelemetry.status(:error, failure))
    :otel_span.end_span(state.span)

    seconds =
      System.convert_time_unit(System.monotonic_time() - state.started, :native, :nanosecond) /
        1_000_000_000

    context = :otel_ctx.get_current()
    :otel_counter.create(state.meter, :"http.client.request.count", %{unit: :"{request}"})
    :otel_counter.add(context, state.meter, :"http.client.request.count", 1, attributes)
    :otel_histogram.create(state.meter, :"http.client.request.duration", %{unit: :s})

    :otel_histogram.record(
      context,
      state.meter,
      :"http.client.request.duration",
      seconds,
      attributes
    )

    :ok
  end

  defp error_type(%{status_code: status}) when status >= 400, do: Integer.to_string(status)
  defp error_type(%{error: nil}), do: nil
  defp error_type(%{error: %PetstoreClient.Errors.NetworkTimeoutError{}}), do: "timeout"
  defp error_type(%{error: %PetstoreClient.Errors.NetworkError{}}), do: "network_error"
  defp error_type(%{error: %PetstoreClient.Errors.StreamError{}}), do: "network_error"

  defp error_type(%{error: %PetstoreClient.Errors.SerializationError{}}),
    do: "serialization_error"

  defp error_type(%{error: {:exit, _reason}}), do: "cancelled"
  defp error_type(_outcome), do: "_OTHER"
end

defmodule TelemetryClient do
  def build do
    PetstoreClient.Client.with_token("https://api.example.com", "token", nil, call_hook: TelemetryCallHook)
  end
end
```

### Response compression

The SDK sends `Accept-Encoding` for the codecs it can decode (`gzip` and `deflate`, `br` when the optional `:brotli` dependency is present, `zstd` on OTP 28) and decodes the body before it returns; the returned headers no longer carry `content-encoding` or `content-length`. A caller-supplied `Accept-Encoding` header, in any casing, replaces the SDK's.

### Versioning

The package follows semantic versioning: a new operation or field is a minor release, a removed or renamed one is a major release, and a deprecated operation is marked `@deprecated` before it goes. The Elixir floor is the oldest release that still gets security fixes (1.17 today) and moves up when that release reaches end of life.

### Models are immutable

A model is a struct, and a struct never changes in place. Make a changed copy with the update syntax, `%{model | name: "new"}`, which leaves the original as it was.

### Behaviours that differ from what you may expect

- An optional property that is absent and a property that is `null` both decode to `nil`; the model cannot tell them apart. A required property that is also nullable must be present, and an explicit `null` is kept. A required nullable query parameter is kept the same way: when its value is `nil` the key is still sent, with an empty value (`?key=`).
- An enum value the SDK does not know decodes to the catch-all member `:unknown_default_open_api`, which has no wire value, so writing it raises a `SerializationError`; a discriminator value the SDK does not know still raises a `SerializationError`.
- A response header that repeats arrives as one entry in `headers`, its values joined with `, ` and its name in lower case.
- A request body and a JSON, text or form response body are held in memory. A binary, JSON Lines or server-sent events response is a stream (see the Streaming section).
- `Configuration` (the base URL and the headers of API calls) and `TransportOptions` (proxy, TLS, timeouts, retries) stay separate structs on purpose: the first describes the API, the second the connection.

## Caveats

### Decimal / `format: number` precision

`format: decimal` / `format: number` values are decoded into Elixir
`float` (IEEE-754 double), so they lose exact decimal representation.
`0.1 + 0.2` in Elixir is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed fields.
If you need exact decimal arithmetic, parse the raw response body
yourself (e.g. with `Jason.decode/2`) and use the `Decimal` library.

`format: int64` is unaffected — Elixir integers are arbitrary-precision
and represent the full 64-bit range without loss.

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

### Schema constraints

Schema constraints (`pattern`, `minLength`, `maxLength`, `minimum`,
`maximum`, `multipleOf`, `minItems`, `maxItems`) are validated by the server
and by the generated models only where the language's model layer does it
natively. Elixir structs and typespecs carry no constraint checking, so this
SDK enforces none of them: the model layer only checks, when a response is decoded,
that required properties are present and that values have the declared type.
Building a struct never validates: a wrong-typed value or an unknown enum
atom builds fine, and an unknown enum string is checked only when it is decoded. The server is
the authoritative validator. If you want fast-fail validation before the
network round trip, plug in a JSON Schema validator library.

`uniqueItems` arrays are held as a `MapSet` and sent in the `MapSet`'s own
enumeration order, not sorted.

### SOCKS proxies

The `proxy:` option of `TransportOptions.new/1` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### XML bodies, Link Objects and mutual TLS

XML request and response bodies (including `xml` `nodeType` annotations),
OpenAPI Link Objects and `mutualTLS` security schemes are not generated.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.

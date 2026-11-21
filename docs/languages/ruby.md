# Ruby

The `ruby-plus` generator produces a gem built on Faraday 2 for HTTP and `dry-struct` for models. This page covers what the generated gem needs, how it maps OpenAPI types, the options the generator accepts, and where Ruby differs from the other eleven languages. For the type table across all languages see [../types.md](../types.md); for the pipeline see [../how-it-works.md](../how-it-works.md).

## Versions and support policy

The generated gemspec sets `required_ruby_version = '>= 3.2'`, the `Gemfile` declares `ruby '>= 3.2'`, RuboCop targets 3.2, and the generator's tests run in the `ruby:3.2-alpine` image. The policy is the oldest Ruby release that still receives security fixes.

A review of the type mapping (see "Under review") found that Ruby 3.2 reached end of life on 2026-04-01, so the floor is now behind that policy. Whether to raise it is not settled.

## Type mapping

| OpenAPI | Ruby type | Notes |
|---|---|---|
| `integer`, `int32`, `int64` | `Integer` | Ruby integers have arbitrary precision, so 64-bit values are exact. |
| `number` with `format: float` or `double` | `Float` | |
| `number` with no format, `format: decimal` | `BigDecimal` | Read from the JSON text with `decimal_class: BigDecimal` and written as a bare JSON number in plain notation. A `BigDecimal` keeps no trailing zero, so `12.50` is written `12.5`. A `type: string` decimal is read from a JSON string only and written as a JSON string. An untyped or free-form number is still a `Float`. |
| `string` with `format: ipv4` or `ipv6` | `IPAddr` | Read from the plain address text only (a prefix length, a zone id, brackets, a leading zero and the wrong family fail with `SerializationError`) and written in canonical text, RFC 5952 for IPv6. |
| `boolean` | `true` or `false` | |
| `string` | `String` | |
| `string` with `format: date` | `Date` | |
| `string` with `format: date-time` | `Time` | Decoded with `Time.iso8601`. |
| `string` with `format: time` | `Tod::TimeOfDay` | From the `tod` gem. |
| `string` with `format: duration` | `ISO8601::Duration` | From the `iso8601` gem. The wire form is the protobuf-JSON form such as `3600s`. |
| `string` with `format: uuid` | `String` | Validated against the canonical 8-4-4-4-12 form and written in lowercase. Ruby has no UUID class. |
| `string` with `format: uri` | `String` | Validated, not parsed into a `URI` object. |
| `string` with `format: email`, `hostname`, `ipv4`, `ipv6` | `String` | |
| `string` with `format: byte` | `String` in binary encoding | Strictly base64-decoded on read. |
| `string` with `format: binary` | `String` for a request body; `ByteStream` for a response | The response body is read as you consume it, see Streaming. |
| file upload and download | `File` or `StringIO` upload, `ByteStream` download | Same as `binary`. |
| `array` | `Array` | |
| `array` with `uniqueItems` | `Set` | |
| `additionalProperties` map | `Hash` | `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form object | `Object` | |
| nullable or optional | `nil` | |
| `enum` | a constant class with a `VALUES` array and `validate!` | Ruby has no enum type. |
| `oneOf`, `anyOf` | module functions `actual_instance` and `matched_instances`, plus a `build` that returns the variant | `anyOf` also has a `*Composite` struct. There is no wrapper object. |

The model classes declare their attributes as `Types::Any.optional`, and the real type of each attribute lives in an `OPENAPI_TYPES` table that the serializer uses. So `dry-struct` does no type checking when you construct a model; a wrong type is caught when the model is serialized, or by Steep through the generated `.rbs` signatures. See "Known limits".

## Dependencies

The gemspec declares these runtime dependencies: `base64` (~> 0.3), `bigdecimal` (~> 3.1), `dry-struct` (~> 1.6), `dry-types` (~> 1.7), `faraday` (~> 2.0), `iso8601` (~> 0.13), `json` (~> 2.6), `set` (~> 1.1) and `tod` (~> 3.1). `base64`, `json` and `set` are declared explicitly because Ruby is moving them out of the default install.

Response decompression for `br` and `zstd` is optional. The `Gemfile` puts `brotli` (~> 0.5) and `zstd-ruby` (~> 1.5) in an `optional` group. The client requires them inside `begin`/`rescue` and offers a codec in `Accept-Encoding` only when it loaded. Both are C extensions with no precompiled musl build, so install them on a glibc platform if you want them.

The generated `Gemfile` also pins `bigdecimal` to 3.1.3, so Bundler uses the precompiled copy that ships with the Ruby base image instead of compiling a newer one on Alpine. That pin applies to the SDK's own tests, not to the gemspec.

Development tools (minitest, RuboCop, Steep, YARD, SimpleCov) are in the `development` and `test` groups only. The tests of the OpenTelemetry recipe add `opentelemetry-sdk` (~> 1.5) and `opentelemetry-metrics-sdk` (~> 0.12) to the same groups. The SDK itself depends on no OpenTelemetry gem.

## Options

Pass options with `--additional-properties` or a config file. Options that apply to every language, such as `clientClassName`, `errorPrefix`, `generateTests`, `generateUnitTests`, `licenseId` and `licenseUrl`, are shared by all twelve generators and are not repeated here. The Ruby generator adds these:

| Option | Meaning |
|---|---|
| `gemName` | The gem name. Defaults to the snake_case form of `moduleName`. It also sets the product token in the default `User-Agent`. |
| `moduleName` | The top-level module that namespaces the generated code, for example `Petstore::Client`. |
| `gemVersion` | The version written to the gemspec. Defaults to `1.0.0`. |
| `authors` | Comma-separated authors for the gemspec. Defaults to the API title. |
| `repositoryUrl`, `homepageUrl`, `documentationUrl`, `issueTrackerUrl` | Written to the gemspec as `source_code_uri`, `homepage`, `documentation_uri` and `bug_tracker_uri`, only when given. |
| `apiErrorParent` | A fully qualified class to use as the root of the SDK's error tree, so one `rescue` of your own base class catches every SDK error. Defaults to a generated `<moduleName>::<errorPrefix>Error`. |

## Idiom notes

- The entry class is `Client` inside the module, for example `Petstore::Client::Client`.
- Operations, variables and files use snake_case; enum constants are upper case. Every file opens with the compact form `module A::B`, and each file declares exactly the constant its path names.
- `Configuration` and `TransportOptions` are built with builders whose setters take a parameter named `val`.
- `Exception#cause` is Ruby's built-in chaining, so `ApiError`, `NetworkError` and `NetworkTimeoutError` take no `cause` argument.
- The server list is the constant `Servers::ALL`.
- There is no per-call cancellation handle. A timeout is the only bound on a call.
- The gem ships RBS signatures in `sig/` and a `Steepfile`, and the type check must pass over `lib`.
- A credential that is not passed comes from an environment variable. Each security scheme reads its own variables, named `<PREFIX>_<FIELD>` (`PETSTORE_TOKEN`, `PETSTORE_API_KEY`, `PETSTORE_CLIENT_ID`), or `<PREFIX>_<SCHEME>_<FIELD>` for every scheme that shares a field name with another, so no scheme reads another's secret. The prefix is the shared `envVarPrefix` option, else the API title without a trailing `SDK`. The order is the value you pass, then (for a Bearer token) the `token_provider:` callback, then the variable. The variable is read once, when the authenticator is built, and an empty variable counts as unset. A credential that is still missing raises `ArgumentError` before the client is built. An authenticator built without a scheme (`Client.with_token`, or `BearerAuthenticator`, `BasicAuthenticator` and `ApiKeyAuthenticator` on their own) reads `<PREFIX>_TOKEN`, `<PREFIX>_USERNAME`, `<PREFIX>_PASSWORD` or `<PREFIX>_API_KEY`. Every authenticator takes an `env:` keyword (default `ENV`) naming where the variables are read from, so a test passes a hash instead of changing the process environment.
- The client takes one optional credential per distinct security scheme, as a keyword argument named after the scheme in snake case (`pet_store_bearer:`, `api_key_header:`), beside the optional `authenticator` and `transport_options` positional arguments: `Client.new(authenticator = nil, transport_options = nil, pet_store_bearer: nil, ...)`. An operation uses the credential of the first scheme in its own security requirement (in the order the spec lists them) that the client holds, and falls back to `authenticator` when none matches. An operation that declares `security: []` sends no credential. There is no per-call scheme override. The base URL comes from the first credential given, `authenticator` first, and a client given no credential raises `ArgumentError`. A scheme whose name is a Ruby keyword, or one of the client's own arguments, gets the suffix `_credential`.
- Every operation takes an optional Options object, even one with no query, header, form or cookie parameter and no security. It carries the per-call `timeout` (milliseconds), `headers` and `max_retries`. A member left `nil` keeps the client's value. There is no `auth` member: the credentials belong to the client.
- `close` is graceful and idempotent. It marks the client closed at once, a request already in flight finishes, and the connection the SDK built is released when the last in-flight request completes (at once when none is running). A request started after it raises a `RuntimeError` ("ApiClient has been closed and can no longer send requests"), a second `close` does nothing, and a connection supplied through `TransportOptions` is never closed.
- Streaming. An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns `ByteStream` from the one operation method (there is no second method), and `..._with_http_info` returns an `ApiResult` whose `data` is the `ByteStream` and whose `raw_body` is empty. A `ByteStream` has `status_code`, the lower-cased `headers`, `bytes_delivered`, `read(length = nil, outbuf = nil)` with the semantics of `IO#read`, `each(chunk_size)`, an idempotent `close` that aborts the connection instead of draining it, `closed?`, a `read_all` that reads the rest and leaves the stream closed, and an `inspect` (also `to_s`) that shows only the status and the bytes delivered. A read of a closed stream raises `IOError`. The transport gets `ApiClient#send_streaming(method, url, headers, body, timeout:, max_retries:, idle_timeout:)`, which returns an `ApiStreamResponse` (status, headers, open body); its default implementation raises `NotImplementedError` naming the transport, and `BaseApi` raises the same for a transport object that has no `send_streaming` at all, so an injected `ApiClient` that only implements `send_request` keeps working for buffered calls. `DefaultApiClient` runs the buffered call's header phase (retries, redirects, the end-to-end `timeout`, the header rules, a 401 replay) and stops there: the `timeout` ends at the response headers, `TransportOptions.builder.idle_timeout(milliseconds)` (default 60 seconds, `0` or `nil` disables) bounds the gap between two reads of the body and raises `NetworkTimeoutError`, and nothing is retried after the first body byte. The request runs on a producer thread that hands the head and then the body chunks to the reader through a bounded queue (the thread is stopped, which closes the socket, when the stream is closed); a gzip or deflate body is decoded as it arrives, and a stream call advertises only those two encodings, since `br` and `zstd` cannot be decoded as the body arrives (a stream response in either is refused with an `ApiError` carrying its status). A non-2xx status raises the usual typed error with its body read up to 64 KiB. A reset, or a body shorter than its `Content-Length`, raises `StreamError` (a `NetworkError`, status 0). `client.close` cancels the streams still open; their next read raises the `RuntimeError` of a closed client. A connection supplied through `TransportOptions` still streams, but reports the headers with the first body chunk. Uploads are unchanged and JSON Lines and server-sent events have no method yet.
- A cookie parameter is sent raw when every character of its value is a legal RFC 6265 cookie-octet (printable ASCII except space, double quote, comma, semicolon and backslash). Any other value raises `ArgumentError` before the request is sent, so the caller pre-encodes it.
- The call hook is any object that responds to `start(info, headers)` and returns an observer that responds to `finish(outcome)`, or `nil`; there is no module to include. `CallInfo` and `CallOutcome` are `Data` values (`Data.define` needs the Ruby 3.2 floor), each in its own file, and `CallHook.chain(*hooks)` returns a hook that starts them in order and finishes their observers in reverse. `CallInfo` names the method `http_method`, because a `method` member would shadow `Object#method`. The hook is set with `Configuration::Builder#call_hook(hook)`, which refuses an object that does not respond to `start` with an `ArgumentError`, or with the `call_hook:` keyword of `Client.new`, `Client.with_token` and `Client.with_authenticator` (the client has no builder). `finish` runs from an `ensure`, so an interruption raised into the thread (`Interrupt`) is observed too and re-raised as it is; Ruby has no cancellation handle, so that is what "cancelled" means here. The hook's header changes are kept as a delta and put back on every transport retry and on the replay after a 401, where the fresh credentials still win over a hook header of the same name.
- The SDK ships no OpenTelemetry adapter. The traces API is stable, but the metrics API is a separate gem (`opentelemetry-metrics-api`) that is not, so the identical span, count and histogram promise is met by a recipe, a `TelemetryCallHook` class the README shows. The README prints it from the partial `call_hook_recipe.mustache`, and `test/call_hook_recipe_test.rb` includes the same partial, so the text a user copies is the text under test. It sets `error.type` to `cancelled` for a `SignalException` (`Interrupt` included). `TraceContextUtil` still injects the propagated trace context before `start`; the recipe injects it again for the client span, and its value replaces the first.
- A property that is a `$ref` with a `default` written beside it (an OpenAPI 3.1 sibling keyword) starts at that default: a referenced enum becomes the matching constant, so `StockItem.new(priority: 1).availability` is `Availability::ON_HOLD`, and a decoded body that leaves the property out gets it too.
- Testing your code: `interface _<Group>ApiInterface` in each group's `.rbs` and `interface _<Client>Interface` in `sig/infrastructure.rbs`; no runtime module (duck typing needs none, and Steep's `:test` target has core diagnostics off). The tests read the signature files and compare the interface members with `public_instance_methods(false)`.
- With `generateExamples` on, the gem ships `examples/quickstart.rb`, a script that requires the gem, builds a client with `Client.with_token` from `{PREFIX}_BASE_URL` and `{PREFIX}_TOKEN` (`ENV.fetch`, so a missing variable raises `KeyError`), calls the example operation and closes the client in an `ensure` block. `make examples` checks its syntax with `ruby -wc` and type-checks it with the Steepfile's `examples` target (default diagnostics), and `make examples-run` runs it with `-Ilib`. The README and SKILLS install line is `bundle add`.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `call_hook:` is the name to look for in this SDK.

```ruby
class TenantHook
  def start(_info, headers)
    headers['X-Tenant'] = 'a'
    nil
  end
end

client = Petstore::Client::Petstore.with_token('/api/v3', 'token', call_hook: TenantHook.new)
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```ruby
eu = Petstore::Client::Petstore.with_token('https://eu.api.example.com', 'eu-token')
us = Petstore::Client::Petstore.with_token('https://us.api.example.com', 'us-token')
```

Call `close` on each when you are done.

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `models/animal.rb`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `..._with_http_info` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiError` that holds the status code, the response headers and the response body. A live response object is not exposed for a buffered operation (a binary operation returns a `ByteStream`, see Streaming and [decisions](../decisions.md)); to see or change what goes over the wire, use the call hook above or the transport you supply.

```ruby
result = client.pet.add_pet_with_http_info(pet)
status = result.status_code
etag = result.headers['etag']
raw = result.raw_body

begin
  client.pet.add_pet(pet)
rescue Petstore::Client::Errors::ApiError => e
  puts [e.status_code, e.response_headers, e.response_body].inspect
end
```

## Known limits

- A model never validates when it is constructed: a value of the wrong type, or an inline enum string the spec does not list, is held as given (a required property still has to be present and not `nil`). Decoding is where the check happens: a wrong type fails the decode with `SerializationError`, and an unknown enum value decodes to the catch-all member `UNKNOWN_DEFAULT_OPEN_API` (D117). See the type mapping above.
- A `BigDecimal` keeps no trailing zero, so a decimal read as `12.50` is written back as `12.5`.
- An overflowing double literal such as `1e400` decodes to Infinity.
- Faraday's default `net_http` adapter opens a connection per request. Connection reuse would need `net_http_persistent`, which is an open decision.
- SOCKS proxies are not supported; `proxy` accepts `http://` and `https://` only.
- Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope. OpenAPI 3.1 and 3.2 support is tracked in the epics #96 and #49.
- The YARD documentation build (`yard --fail-on-warning`) is in the generated Makefile but no test runs it. No `LICENSE` file is generated.
- Operations are blocking only, as a plain method call, by decision D111. There is no asynchronous variant of an operation, because each SDK follows the style of its own language. Run the call on your own thread or task when you need concurrency.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.
- A security requirement that names several schemes together (for example a bearer token and an API key) sends the credentials of all of them, by decision D116. The client must hold every credential of the requirement; an alternative the client holds only in part is used only when no other alternative is complete. A requirement that names one scheme works as before.

## Under review

These are ideas from a review of the type mapping against current Ruby. None is decided, and none changes what the SDK does today.

- Raise the floor to Ruby 3.3, because 3.2 is past end of life. This would also mean updating the CI images and the RBS and Steep target.
- Tighten model attributes from `Types::Any` to the real `dry-types`, but only if lenient decoding is not wanted. Otherwise the plan is to document `OPENAPI_TYPES` as the contract. This needs an owner decision.
- Keep `String` for `uuid`, `uri`, `email` and `hostname`. Ruby has no UUID class, and parsing a URI risks changing its text.
- Ruby 4.0 has no Temporal-like type, so `tod` and `iso8601` remain the libraries for time of day and duration.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `OpenTelemetry.propagation.inject` reads `OpenTelemetry::Context.current` on the caller's thread (`Context.with_current`); no thread or fiber is created on the header path.

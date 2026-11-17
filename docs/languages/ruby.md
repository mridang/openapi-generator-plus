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
| `number` with no format, `format: decimal` | `Float` | Money loses exact decimal representation: `0.1 + 0.2` is `0.30000000000000004`. The generated README says so and points to `JSON.parse(body, decimal_class: BigDecimal)` for callers who need exact arithmetic. |
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
| `string` with `format: binary` | `String` | |
| file upload and download | `File` or `StringIO` | Binary responses are returned as a stream object. |
| `array` | `Array` | |
| `array` with `uniqueItems` | `Set` | |
| `additionalProperties` map | `Hash` | |
| free-form object | `Object` | |
| nullable or optional | `nil` | |
| `enum` | a constant class with a `VALUES` array and `validate!` | Ruby has no enum type. |
| `oneOf`, `anyOf` | module functions `actual_instance` and `matched_instances`, plus a `build` that returns the variant | `anyOf` also has a `*Composite` struct. There is no wrapper object. |

The model classes declare their attributes as `Types::Any.optional`, and the real type of each attribute lives in an `OPENAPI_TYPES` table that the serializer uses. So `dry-struct` does no type checking when you construct a model; a wrong type is caught when the model is serialized, or by Steep through the generated `.rbs` signatures. See "Known limits".

## Dependencies

The gemspec declares these runtime dependencies: `base64` (~> 0.3), `dry-struct` (~> 1.6), `dry-types` (~> 1.7), `faraday` (~> 2.0), `iso8601` (~> 0.13), `json` (~> 2.6), `set` (~> 1.1) and `tod` (~> 3.1). `base64`, `json` and `set` are declared explicitly because Ruby is moving them out of the default install.

Response decompression for `br` and `zstd` is optional. The `Gemfile` puts `brotli` (~> 0.5) and `zstd-ruby` (~> 1.5) in an `optional` group. The client requires them inside `begin`/`rescue` and offers a codec in `Accept-Encoding` only when it loaded. Both are C extensions with no precompiled musl build, so install them on a glibc platform if you want them.

The generated `Gemfile` also pins `bigdecimal` to 3.1.3, so Bundler uses the precompiled copy that ships with the Ruby base image instead of compiling a newer one on Alpine. That pin applies to the SDK's own tests, not to the gemspec.

Development tools (minitest, RuboCop, Steep, YARD, SimpleCov) are in the `development` and `test` groups only.

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
- A credential that is not passed comes from an environment variable. Each security scheme reads its own variables, named `<PREFIX>_<FIELD>` (`PETSTORE_TOKEN`, `PETSTORE_API_KEY`, `PETSTORE_CLIENT_ID`), or `<PREFIX>_<SCHEME>_<FIELD>` for every scheme that shares a field name with another, so no scheme reads another's secret. The prefix is the shared `envVarPrefix` option, else the API title without a trailing `SDK`. The order is the value you pass, then the variable, then (for a Bearer token) the `token_provider:` callback. The variable is read once, when the authenticator is built, and an empty variable counts as unset. A credential that is still missing raises `ArgumentError` before the client is built. An authenticator built without a scheme (`Client.with_token`, or `BearerAuthenticator`, `BasicAuthenticator` and `ApiKeyAuthenticator` on their own) reads `<PREFIX>_TOKEN`, `<PREFIX>_USERNAME`, `<PREFIX>_PASSWORD` or `<PREFIX>_API_KEY`. Every authenticator takes an `env:` keyword (default `ENV`) naming where the variables are read from, so a test passes a hash instead of changing the process environment.
- Every operation takes an optional Options object, even one with no query, header, form or cookie parameter and no security. It carries the per-call `timeout` (milliseconds), `headers` and `max_retries`, and a `auth` member only when the operation has security. A member left `nil` keeps the client's value.
- A property that is a `$ref` with a `default` written beside it (an OpenAPI 3.1 sibling keyword) starts at that default: a referenced enum becomes the matching constant, so `StockItem.new(priority: 1).availability` is `Availability::ON_HOLD`, and a decoded body that leaves the property out gets it too.

## Known limits

- Models do no type or enum validation when constructed, beyond what `dry-types` does for untyped fields. See the type mapping above.
- `number` is a `Float`, so exact decimal arithmetic needs the caller to decode with `BigDecimal` as the README describes.
- An overflowing double literal such as `1e400` decodes to Infinity.
- Faraday's default `net_http` adapter opens a connection per request. Connection reuse would need `net_http_persistent`, which is an open decision.
- SOCKS proxies are not supported; `proxy` accepts `http://` and `https://` only.
- Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope. OpenAPI 3.1 and 3.2 support is tracked in the epics #96 and #49.
- The YARD documentation build (`yard --fail-on-warning`) is in the generated Makefile but no test runs it. No `LICENSE` file is generated.

## Under review

These are ideas from a review of the type mapping against current Ruby. None is decided, and none changes what the SDK does today.

- Raise the floor to Ruby 3.3, because 3.2 is past end of life. This would also mean updating the CI images and the RBS and Steep target.
- Decode `number` as `BigDecimal` through `JSON.parse` with `decimal_class`, and write it back as a bare JSON number. This needs `bigdecimal` declared in the gemspec, because it is a bundled gem from Ruby 3.4, and the musl pin in the `Gemfile` resolved first. It needs no floor change.
- Tighten model attributes from `Types::Any` to the real `dry-types`, but only if lenient decoding is not wanted. Otherwise the plan is to document `OPENAPI_TYPES` as the contract. This needs an owner decision.
- Use `IPAddr` for `ipv4` and `ipv6` only if canonical-text round trips are acceptable, because `IPAddr#to_s` rewrites addresses such as compressed IPv6.
- Keep `String` for `uuid`, `uri`, `email` and `hostname`. Ruby has no UUID class, and parsing a URI risks changing its text.
- Ruby 4.0 has no Temporal-like type, so `tod` and `iso8601` remain the libraries for time of day and duration.

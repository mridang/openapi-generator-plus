# Decisions

This page lists the choices that are settled for all twelve SDKs, written for people who use the generated clients. Each one says what the SDK does and where to read the details for your language. The language pages are in [docs/languages](languages), and the type table is in [types.md](types.md). Work that is still open is listed at the end, with a link to its issue.

## Retries are on by default

Every client retries a failed call up to two times unless you change it. A call is retried after a network error that happened before any response arrived, and after a response with status 408, 409, 429 or any 5xx except 501. The end-to-end timeout covers all attempts together, so a wait that no longer fits in it ends the retrying. The wait starts at half a second and doubles up to eight seconds, with up to a quarter taken off at random, and a `Retry-After` header from the server is used when it is present. Set the retry count to `0` on the transport options to turn retries off, or change it for a single call with a per-call option (below).

A client that you build around your own HTTP client keeps the retry rules of that client in some languages: the Go page says that such a client never retries, for example. See the Retries or Automatic retries section of your language page: [Java](languages/java.md), [Kotlin](languages/kotlin.md), [C#](languages/csharp.md), [Node.js](languages/node.md), [Python](languages/python.md), [Ruby](languages/ruby.md), [PHP](languages/php.md), [Go](languages/go.md), [Rust](languages/rust.md), [Swift](languages/swift.md), [Dart](languages/dart.md), [Elixir](languages/elixir.md).

## Models are immutable

A model is never changed after it is built. To change one, you make a copy with the new value and leave the original as it was. The way to copy follows the language: a builder in Java (`toBuilder()`), a `with` expression on a record in C#, `copyWith` in Dart, `copy(...)` in Swift, `with` in Node.js and Ruby, `With<Property>` methods in Go and PHP (`withX()`), `model_copy` in Python, `with_<field>` methods in Rust, and the struct update syntax in Elixir. Kotlin models are data classes with read-only properties. Copies are shallow: a list or map inside the copy is the same one the original holds, so clone it before you change it in place.

## Unknown enum values fail loudly

When a response carries an enum value, or a discriminator value, that the SDK does not know, decoding fails with the SDK's serialization error and the message names the value. The SDK never maps an unknown value to a default and never passes it through silently. The reason is that a specification that is out of date should fail where you can see it, not produce a wrong value later. Read the section on unknown values in your language page to see the exact error type.

## Repeated response headers are joined

A response header that the server sends on several lines, such as `Link` or `Set-Cookie`, arrives as one value in which the lines are joined with `, ` in the order received. Header names are lower-cased. The headers are available from the `...WithHttpInfo` variant of each operation, which returns the status, the headers and the decoded body together.

## Streaming is not supported

The SDKs read a response body in full before decoding it, and send a request body whole. There is no API for server-sent events, chunked downloads or streaming uploads. When you need to stream, every client accepts your own HTTP client or transport and sends its requests through it: `java.net.http.HttpClient` in Java, a `urllib3.PoolManager` in Python, a `Faraday::Connection` in Ruby, an `ApiClient` or `http.RoundTripper` in Go, and the standard client object of each other language. The SDK keeps its defaults on top of the client you supply and never closes it. Streaming support is under review (issue #114, below).

## Options for a single call

Every operation accepts three settings that apply to that one call and replace the client-wide value: a timeout in milliseconds, extra headers (which win over a default header of the same name) and a retry count. Where a language has a native way to cancel a call, it is used instead of a shared wrapper: a `context.Context` in Go, a `CancellationToken` in C#, an `AbortSignal` in the Node.js per-call options, coroutine cancellation in Kotlin, thread interruption in Java, and so on. Cancelling is never reported as a timeout.

## Several success responses decode by status

When an operation declares more than one 2xx response with different schemas, it returns a union of those schemas, and the body is decoded as the schema of the status that actually arrived, never by guessing from the shape of the body. Each language page shows how to ask the union which variant it holds.

## Unions are built with typed constructors where the language allows it

A `oneOf` or `anyOf` model is built from a variant, not from an untyped value, in most languages: `ofVariant` in Java, `FromVariant` in C#, `fromVariant` in Swift, `fromX` in PHP, `from_<variant>` in Ruby, a named constructor per variant in Dart, a sum type in Rust, a union type in TypeScript and module functions in Elixir. In Go, Kotlin and Python the union is a wrapper that holds the decoded value (`actualInstance` or `actual_instance`) and has no constructor per variant yet; making these consistent is covered by issue #104 and by the review of union types in the language pages.

## Credentials come from an argument, then the environment

An authenticator takes its credential as an argument. When you pass none, most SDKs read an environment variable once, when the client or authenticator is built, and an empty value counts as unset. An argument always wins over the environment. A credential that is found in neither place fails at construction time, not on the first request. A bearer token or an API key that rotates can instead come from a provider function that is called for every request. The variable names differ by language and by generated package, so they are listed on each language page. The Java SDK takes its credentials as constructor arguments only today and does not read the environment or accept a provider.

## Language version floors

The minimum version of each language is the oldest release that its maintainers still support, and it is written in the build manifest of the generated SDK (`pom.xml`, `build.gradle.kts`, the `.csproj`, `package.json`, `pyproject.toml`, the gemspec, `composer.json`, `go.mod`, `Cargo.toml`, `Package.swift`, `pubspec.yaml` and `mix.exs`). A generated README states the same floor as its manifest, and the language page for each SDK is the single place where the floor is explained. The generated READMEs describe how a floor moves: the Node.js, C# and Swift READMEs say that dropping a runtime is a major release, and the Ruby README says that dropping an end-of-life Ruby is a minor release. The current floors are in the table in the [README](../README.md#languages).

## Out of scope

These OpenAPI features are not supported: callbacks and webhooks, Link Objects, XML bodies and the `xml` object, and mutual TLS. Callbacks and links are ignored when the specification contains them.

## Under review

Nothing below is decided. Each item is an open issue, and the list can change.

* [#103](https://github.com/mridang/openapi-generator-plus/issues/103): type the values of `additionalProperties` instead of exposing an untyped map.
* [#104](https://github.com/mridang/openapi-generator-plus/issues/104): support classic `allOf` inheritance with a discriminator in every SDK.
* [#105](https://github.com/mridang/openapi-generator-plus/issues/105): run a vulnerability and unused-dependency audit on every generated SDK.
* [#106](https://github.com/mridang/openapi-generator-plus/issues/106): honour a default on a property that references an enum.
* [#107](https://github.com/mridang/openapi-generator-plus/issues/107): decode Go models in a single pass.
* [#108](https://github.com/mridang/openapi-generator-plus/issues/108): add optional tracer and meter providers to the Go client.
* [#109](https://github.com/mridang/openapi-generator-plus/issues/109): serialize object-valued query and header parameters per the OpenAPI style rules.
* [#110](https://github.com/mridang/openapi-generator-plus/issues/110): generate a nonce and validate `id_token` claims in the OIDC authenticator.
* [#111](https://github.com/mridang/openapi-generator-plus/issues/111): compile the README and `SKILLS.md` code fences and test the minimum supported runtimes.
* [#112](https://github.com/mridang/openapi-generator-plus/issues/112): add a maximum response size transport option.
* [#113](https://github.com/mridang/openapi-generator-plus/issues/113): return a typed error from Rust operations instead of `Box<dyn Error>`.
* [#114](https://github.com/mridang/openapi-generator-plus/issues/114): support streaming responses and uploads in the generated clients.
* [#115](https://github.com/mridang/openapi-generator-plus/issues/115): cover the Dart browser client and derive the keyword lists from the toolchains.
* [#116](https://github.com/mridang/openapi-generator-plus/issues/116): add a per-call timeout and make the end-to-end timeout hold while the body is read.
* [#117](https://github.com/mridang/openapi-generator-plus/issues/117): decode error bodies against the operation's declared error schema.

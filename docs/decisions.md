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

These OpenAPI features are not supported: callbacks and webhooks, Link Objects, XML bodies and the `xml` object, and mutual TLS. Callbacks and links are ignored when the specification contains them. The reasons are in [What is not supported and why](#what-is-not-supported-and-why).

## What is not supported and why

Each item below was weighed and left out on purpose, or deferred to an open issue. The reason is given so that the question does not need to be asked again. Items with an issue number are listed under review in the next section; the others are settled.

### Left out on purpose

* **Callbacks, webhooks, Link Objects, XML bodies and mutual TLS.** They need a design of their own and no caller has asked for them, so the generators ignore callbacks and links and the READMEs say so. The tracking issues are #53, #70, #78 and #90.
* **SOCKS proxies.** Only `http://` and `https://` proxy URLs are accepted, and a `socks` URL fails when the client is built. The HTTP libraries underneath do not agree on SOCKS support, and a client that quietly ignored the setting would send traffic outside the proxy.
* **Proxy settings on Swift for Linux.** `URLSession` in swift-corelibs-foundation does not implement the proxy dictionary, so the Swift client raises a `ConfigurationError` instead of ignoring the setting. Supporting it would mean replacing the transport.
* **Ambient proxy settings.** The SDKs use only the proxy you pass in the transport options and ignore proxy environment variables, with one exception: the Kotlin client runs on the Ktor CIO engine, which falls back to the JVM proxy selector (`http.proxyHost` and the system proxy) and cannot be told not to. This is the one settled difference in the proxy behaviour.
* **A `LICENSE`, a `CHANGELOG.md`, CI workflows, pre-commit hooks and a devcontainer in a generated SDK.** The generator does not know which licence text you want or what changed since the last run, and the CI setup belongs to the project that publishes the SDK. Add them in your release pipeline.
* **Client-side `dependentRequired` and `dependentSchemas`.** No mainstream client generator checks them and the server is the authority. Use a JSON Schema validator if you need it.
* **HTTP/2 and chunked transfer encoding.** The libraries underneath choose the protocol version and reassemble chunks themselves, and the caller sees the same bytes either way, so there is nothing for the SDK to set.
* **Replaying a multipart body on a 307 or 308 redirect in Kotlin, C# and PHP.** The libraries underneath drop or rewrite the body, and a fix means following redirects by hand as the Rust client does. The tests are skipped in those three languages and say why in the test file.

### Deferred to an issue

* **Streaming responses and uploads (#114).** The transport contract reads a whole response and sends a whole body in all twelve SDKs, so server-sent events, JSON Lines and very large files cannot be expressed. The shape has to be chosen once for all twelve, so nothing was built in one language first. Until then you can pass your own HTTP client.
* **Typed values for `additionalProperties` (#103).** The extra-properties bucket is an untyped map in every language and its values are not checked on decode. Typing it changes the public model API, so it has to land in all twelve together.
* **Validating the `id_token` and sending a nonce in the OIDC authenticator (#110).** PKCE is in place for the authorization-code flow, but replay of an `id_token` is not detected by the client.
* **A maximum response size (#112).** No transport limits how much it reads or decompresses, so a hostile server can make a client read a very large body. The option and its default must be the same in all twelve.
* **An end-to-end timeout that holds while the body is read, and a per-call timeout (#116).** The default is a 10 second budget for the whole call. Python and Ruby still apply the timeout per socket read, which needs a streaming reader, and the issue records the per-call option still to be added in every SDK.
* **A typed error from Rust operations (#113).** Every Rust operation returns `Box<dyn Error>`. A concrete error type is a public API break for that SDK alone.
* **Error bodies decoded against the declared error schema (#117).** `errorBody` holds the parsed JSON, not a model. Typing it changes the `ApiError` generics in every SDK and needs a shared model in the generator.
* **Object-valued query and header parameters (#109), enum defaults on a `$ref` property (#106), classic `allOf` with a discriminator (#104), single-pass Go models (#107), Go tracer and meter providers (#108), a dependency audit (#105), compiled README examples (#111) and the Dart browser client (#115).** Each changes generated output in all or many languages and is waiting for its own change.

### Settled differences between languages

* **Cookie parameters (F840).** A value is sent as given when every character is allowed in a cookie, and an illegal character fails before the request is sent. Nothing is percent-encoded for you, because the shared parameter model cannot tell an explicit `style: form` from the default.
* **One credential per security scheme (F857).** Where a client takes one credential per scheme, an operation uses the credential of the first scheme it lists that you set. A per-call override of the scheme is deferred, and Rust has no such override.
* **Test TLS keys (F1085).** The generated tests use the key files copied from the shared fixtures, because the shared test server mounts a fixed server key. Generating the keys when the tests start is a change to that shared harness, and it is not done yet. The Dart and Swift standard libraries cannot create X.509 certificates, so once the harness generates the material the SDKs only read it.

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

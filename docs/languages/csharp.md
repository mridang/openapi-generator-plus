# C#

The C# generator (`csharp-plus`, class `BetterCSharpCodegen`) produces a NuGet package. It sends requests with `System.Net.Http.HttpClient` and serializes with `System.Text.Json`, and its generated project has no third-party runtime package dependencies. This page lists the supported versions, the type mapping, the dependencies, the generator options and the idioms. The shared pipeline is in [how-it-works.md](../how-it-works.md) and the cross-language type table is in [types.md](../types.md).

## Supported versions

| What | Version | Where it comes from |
|---|---|---|
| Target framework | `net10.0` | `<TargetFramework>` in the generated `.csproj` |
| C# language version | 14 | `<LangVersion>14</LangVersion>` |
| .NET SDK for building | 10.0 or newer | the generated README |

The floor is written once, in the project file above. The support policy is that the floor is the oldest .NET runtime still in security support, and a runtime that reaches end of support is dropped in a major release. .NET 10 is a long-term-support release, supported until November 2028; .NET 8 left support on 2026-11-10. The enum converters stay hand-written so wire values are explicit and unknown values throw.

## Type mapping

| OpenAPI | C# type | Notes |
|---|---|---|
| `string`, `format: date` | `DateOnly` | |
| `string`, `format: date-time` | `DateTimeOffset` | keeps the offset |
| `string`, `format: time` | `TimeOnly` | |
| `string`, `format: duration` | `TimeSpan` | sent and read as a protobuf-JSON duration string such as `3600s` through a custom converter; ISO-8601 calendar durations such as `P1Y2M` are not supported |
| `string`, `format: uuid` | `Guid` | |
| `string`, `format: byte` | `byte[]` | base64 on the wire |
| `string`, `format: binary` | `System.IO.Stream` | the body is still read whole into memory |
| `string`, `format: uri` | `Uri` | |
| `string`, `format: email`, `hostname`, `ipv4`, `ipv6`, and plain `string` | `string` | no dedicated type |
| `integer`, `format: int32` | `int` | |
| `integer`, `format: int64` | `long` | |
| `integer`, no format | `int` | 32 bits; see Under review |
| `number`, `format: float` | `float` | |
| `number`, `format: double` | `double` | binary floating point, so not for money |
| `number`, no format, or `format: decimal` | `decimal` | exact base-10 arithmetic |
| `boolean` | `bool` | |
| `array` | `List<T>` | |
| `array` with `uniqueItems` | `HashSet<T>` | sent in the set's enumeration order, not sorted |
| `object` with `additionalProperties` | `Dictionary<string, T>` | |
| free-form `object` | `object?` | `System.Text.Json` decodes it as a `JsonElement` |
| optional or nullable property | `T?` | an absent key and an explicit `null` read the same and neither is written back; a required and nullable property keeps its key and is written as `null` |
| `enum` | C# `enum` | each enum has its own JSON converter; an unknown value fails with `SerializationException`; `ToString()` gives the member name, not the wire value |
| `oneOf`, `anyOf` | a wrapper class | see Idioms |
| file upload and download | `System.IO.Stream` | same as `binary` |

Models are `record`s with `init`-only properties and `required` members.

## Dependencies

The packed library has no runtime package references. Everything it uses ships in the .NET base class library: `HttpClient`, `System.Text.Json`, `System.Net`.

| Reference | Why |
|---|---|
| `Microsoft.CodeAnalysis.NetAnalyzers` 10.0.100 (private, analyzer only) | static analysis at build time with warnings as errors; it does not flow to consumers |
| `ILLink.Descriptors.xml` (embedded resource) | tells the trimmer to keep the model members that reflection-based JSON needs |

The project also builds with documentation file and symbol package (`.snupkg`) output, and its tests run on xunit; those are not consumer dependencies.

## Generator options

| Option | Default | Effect |
|---|---|---|
| `packageName` | `OpenApi` | NuGet package id, root namespace and source folder name |
| `packageVersion` | `1.0.0` | NuGet package version |
| `sourceFolder` | `src` | directory that holds the project |
| `repositoryUrl` | none | written to the package manifest as `RepositoryUrl` |
| `homepageUrl` | none | written as `PackageProjectUrl` |
| `authors` | none | written as `Authors` |
| `documentationUrl` | none | linked from the README |
| `issueTrackerUrl` | none | linked from the README |
| `envVarPrefix` | the API title in upper snake case without a trailing `SDK`, else `API` | prefix of the credential environment variables (`{PREFIX}_HOST`, `{PREFIX}_TOKEN` and one `{PREFIX}_{FIELD}` per credential field of each security scheme); the same option and default in every language |
| `clientClassName` | `Client` | name of the client facade |
| `errorPrefix` | `OpenAPI` | prefix of the root error type; C# appends `Exception`, giving `OpenAPIException` |
| `generateTests` | `true` | emit the full test suite |
| `generateUnitTests` | `false` | emit only the spec-independent unit tests |

The model namespace is always `Models` and the API namespace is always `Api`; they are not options.

## Idioms

- **Errors.** Failures derive from `OpenAPIException`. `ApiException` carries an HTTP response or a transport failure with `StatusCode`, `ResponseBody`, `ResponseHeaders`, `RequestMethod` and `RequestPath` (without the query string), and its message reads like `API returned status code 404 for GET /items/7`. `NetworkException` and `NetworkTimeoutException` mean no response arrived; `TooManyRequestsException` is HTTP 429.
- **Retries.** Two retries by default (`TransportOptionsBuilder.MaxRetries(n)`, `0` turns them off), after a network error before any response and after 408, 409, 429 and 5xx except 501. The wait starts at 0.5 seconds, doubles up to 8 seconds, loses up to 25% to jitter and is replaced by `Retry-After` (at most 60 seconds). Retries share the end-to-end timeout, which defaults to 10 000 ms, headers are rebuilt on every retry, and a stream or multipart body is never retried.
- **Cancellation.** Every operation ends with `CancellationToken cancellationToken = default`. Cancelling raises `OperationCanceledException`, distinct from `NetworkTimeoutException`. `TransportOptionsBuilder.CancellationToken(...)` sets a client-level token that is linked with each call's token.
- **Authentication.** Basic, bearer and API key authenticators, plus OAuth 2 and OpenID Connect. After a 401 from an OAuth 2 or OpenID Connect call the cached access token is dropped and the request is sent once more. Each per-scheme authenticator (for example `PetStoreBasicAuthenticator`) takes its credentials as optional arguments. A value you pass wins; otherwise the constructor reads the scheme's environment variable once, and an empty variable counts as unset. A credential that is neither passed nor set fails the constructor with `InvalidOperationException` that names the variable. The variable is `{PREFIX}_{FIELD}` in upper snake case (`ACME_TOKEN`, `ACME_CLIENT_SECRET`); when two schemes have a field of the same name, each is qualified with its scheme id (`ACME_SERVICE_TOKEN_TOKEN`). The host is never read from a per-scheme variable. A bearer authenticator also accepts a `tokenProvider` callback in place of a token, called before every request, and it wins over the environment. **Credentials per scheme.** `new Client(host, <scheme>: ..., transportOptions)` takes one optional argument per distinct security scheme, named after the scheme in camel case (`petStoreBasic`, `apiKeyHeader`), typed as that scheme's authenticator. Each operation uses the credential of the scheme its spec requires; when it lists several schemes the first one you passed wins, and an operation whose schemes you passed none for is sent without a credential. There is no per-call scheme override, so the `<Operation>Options` records carry no authenticator. `Client(IAuthenticator)` remains for one credential that applies to every operation. `Client.FromEnvironment()` reads the host and bearer token from `{PREFIX}_HOST` and `{PREFIX}_TOKEN` once when the client is built; arguments win over the environment.
- **Models are never validated when built.** Constructors and `init` accessors accept any value, including `null` for a required property, and the union factories accept `null`; nothing throws when you build a model in code. A payload is checked only when it is decoded: a required property that arrives as `null` and an enum string outside the declared set fail with `SerializationException` (wrapping the `JsonException` that names the property or the value).
- **Cookie parameters.** A cookie parameter is sent exactly as given when every character is a legal RFC 6265 cookie-octet (that includes `%`, so a value you percent-encoded yourself goes out unchanged). A value with a space, quote, comma, semicolon, backslash, control character or non-ASCII character fails with `ArgumentException` before any request, and you percent-encode it first. The SDK does not encode cookie values for you.
- **Immutability.** Models are records with `init`-only properties; copy one with a `with` expression. The copy is shallow, so a list or dictionary property still refers to the same collection.
- **Unions.** A `oneOf` or `anyOf` model is an immutable wrapper built with a typed factory (`Wrapper.FromVariant(value)`) and read with `IsVariant()` and `AsVariant()`; it also exposes `ActualInstance` and `MatchedInstances`, and it has a custom JSON converter. An operation with several `2xx` responses returns a union decoded by the status that arrived.
- **Optional arguments.** Optional parameters are C# optional arguments, and per-call `Timeout`, `MaxRetries` and `Headers` go in the generated `<Operation>Options` record. Every operation has one, including an operation with no parameters and no security requirement, whose record holds only those three settings and has no `Auth` property.
- **Lifecycle.** The client is `IDisposable`, and `Dispose()` is graceful and idempotent. The first call marks the client closed and returns; requests that already started finish, and the last one releases the transport. A request issued after that fails with one error, `InvalidOperationException` with the message `ApiClient has been closed and can no longer send requests`. Only a transport the SDK created is released: an `HttpClient` or `HttpMessageHandler` you passed in through `TransportOptions` is never disposed. Cancellation stays per request, through the `CancellationToken` argument.
- **Raw responses.** Every operation has a `...WithHttpInfoAsync` variant returning `ApiResult<T>`.
- **Custom transport.** `TransportOptionsBuilder.HttpClient(...)` and `HttpMessageHandler(...)` carry requests on your own client or handler; combining them with `Proxy`, `CaCertPath` or `VerifySsl(false)` fails in `Build()` with `ArgumentException`. You can also implement `IApiClient` and inject it.
- **Trimming.** The SDK works with `PublishTrimmed`; expect the `IL2026` and `IL2075` trim warnings that any reflection-based serializer produces.

## Known limits

- Native AOT is not supported, because the serialization layer relies on reflection and custom converters.
- Request and response bodies are read and sent whole; there is no streaming of a response body.
- Schema constraints (`pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`) are not checked by the SDK. `System.Text.Json` enforces required members, integer ranges and wire types, and nothing else.
- `dependentRequired` and `dependentSchemas` are not enforced.
- Webhooks and callbacks, XML bodies, Link Objects and `mutualTLS` security schemes are not generated. For mutual TLS, configure the certificate on your own `HttpClientHandler`.
- Only `http://` and `https://` proxies are accepted; SOCKS is rejected with `ArgumentException`.
- A header repeated by the server is joined into one value with `, `.
- The SDK writes no logs; wrap the transport with a `DelegatingHandler` to log.
- No `LICENSE` file is generated.

## Under review

These are proposals from a review of the type mapping against current .NET. None of them is decided, and none changes what the generator produces today.

- Map `type: integer` with no format to `long` (or `BigInteger`) instead of `int`, as one decision shared with Java and Kotlin.
- Map free-form objects to `System.Text.Json.Nodes.JsonNode` or `JsonElement` instead of `object`.
- Map `ipv4` and `ipv6` to `System.Net.IPAddress` with a custom converter, or keep `string` for parity with the other SDKs.
- Do not adopt the union types of C# 15 now: they need .NET 11, a short-support release, and would push the floor well above the oldest supported runtime.
- Keep `TimeSpan` with the protobuf-JSON converter for durations, and document that ISO-8601 calendar durations are unsupported.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. Reads `Activity.Current` through `DistributedContextPropagator.Current`; the header path has no thread hand-off.

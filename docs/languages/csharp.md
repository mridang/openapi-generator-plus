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
| `string`, `format: email`, `hostname`, and plain `string` | `string` | no dedicated type |
| `string`, `format: ipv4`, `ipv6` | `System.Net.IPAddress` | read from the plain address text only (a leading zero, a prefix length, a zone id, brackets, whitespace, shorthand such as `1` and the wrong family fail with `SerializationException`) and written in canonical text; a `decimal` holds about 28 digits, and a larger number fails instead of being rounded |
| `integer`, `format: int32` | `int` | |
| `integer`, `format: int64` | `long` | |
| `integer`, no format | `int` | 32 bits; see Known limits |
| `number`, `format: float` | `float` | |
| `number`, `format: double` | `double` | binary floating point, so not for money |
| `number`, no format, or `format: decimal` | `decimal` | exact base-10 arithmetic |
| `boolean` | `bool` | |
| `array` | `List<T>` | |
| `array` with `uniqueItems` | `HashSet<T>` | sent in the set's enumeration order, not sorted |
| `object` with `additionalProperties` | `Dictionary<string, T>` | `T` is the declared value type; a free-form bucket keeps the any-type |
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
- **Hooks and telemetry.** `ICallHook.Start(info, headers)` runs once per operation call, after the headers are final, and may edit them; the `ICallObserver` it returns is finished exactly once with a `CallOutcome` (`StatusCode`, lower-cased `Headers`, `Error`). Set it with `ConfigurationBuilder.CallHook(hook)` or the trailing `callHook` argument of the `Client` constructors and `WithToken`, `WithAuthenticator` and `FromEnvironment`; `ICallHook.Chain(...)` combines several. It runs above the transport, so it also fires with a transport you supplied. `OpenTelemetryCallHook` records one span, `http.client.request.count` and `http.client.request.duration` per call with the base class library `ActivitySource` and `Meter` (named after the package), so it needs no package: add the source and the meter to your OpenTelemetry setup. An `IMeterFactory` may be passed to its constructor.
- **Trimming.** The SDK works with `PublishTrimmed`; expect the `IL2026` and `IL2075` trim warnings that any reflection-based serializer produces.
- Testing your code: `I<Group>Api` (file `I<Group>Api.cs`, named by an `apiFilename` override in `BetterCSharpCodegen`) and `IClient : IDisposable`. `Client` implements `IClient` and each group property through an explicit implementation, so `client.Pet` keeps its concrete type. The interface repeats the default parameter values of the class.
- With `generateExamples` on, the project ships `examples/Quickstart/` (`Quickstart.csproj` and a top-level `Program.cs`) that builds a client with `Client.WithToken` from `{PREFIX}_HOST` and `{PREFIX}_TOKEN`, awaits the example operation and disposes the client with `using var`. It is a project of its own with the same analyzer settings as the SDK, the test project leaves `examples/**` out of its compile items, `make examples` builds it and `make examples-run` runs it.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `ICallHook` is the name to look for in this SDK.

```csharp
using var client = Client.WithToken("https://api.example.com", "your-token", callHook: new TimingHook());

internal sealed class TimingObserver(CallInfo info, long started) : ICallObserver
{
    public void Finish(CallOutcome outcome) =>
        Console.WriteLine(
            $"{info.OperationId} {outcome.StatusCode} {Stopwatch.GetElapsedTime(started).TotalMilliseconds} ms");
}

internal sealed class TimingHook : ICallHook
{
    public ICallObserver? Start(CallInfo info, IDictionary<string, string> headers)
    {
        headers["X-Caller"] = "billing";
        return new TimingObserver(info, Stopwatch.GetTimestamp());
    }
}
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```csharp
using var eu = Client.WithToken("https://eu.api.example.com", "eu-token");
using var us = Client.WithToken("https://us.api.example.com", "us-token");
```

Disposing one client does not affect the other.

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `Models/Animal.cs`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `...WithHttpInfoAsync` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiException` that holds the status code, the response headers and the response body. A live response object is not exposed, because that would need streaming (see [decisions](../decisions.md)); to see or change what goes over the wire, use the call hook above or the transport you supply.

```csharp
ApiResult<Pet> result = await client.Pet.AddPetWithHttpInfoAsync(pet);
int status = result.StatusCode;
string? etag = result.Headers.GetValueOrDefault("etag");
string raw = result.RawBody;

try
{
    await client.Pet.AddPetAsync(pet);
}
catch (ApiException e)
{
    Console.WriteLine($"{e.StatusCode} {e.ResponseHeaders} {e.ResponseBody}");
}
```

## Known limits

- An `integer` with no format is 32 bits, the same as `int32`, by decision D95 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value outside the range fails the decode with a `SerializationError` and is never wrapped or truncated. Declare `format: int64` where a field needs more.
- Native AOT is not supported, because the serialization layer relies on reflection and custom converters.
- Request and response bodies are read and sent whole; there is no streaming of a response body.
- Schema constraints (`pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`) are not checked by the SDK. `System.Text.Json` enforces required members, integer ranges and wire types, and nothing else.
- `dependentRequired` and `dependentSchemas` are not enforced.
- Webhooks and callbacks, XML bodies, Link Objects and `mutualTLS` security schemes are not generated. For mutual TLS, configure the certificate on your own `HttpClientHandler`.
- Only `http://` and `https://` proxies are accepted; SOCKS is rejected with `ArgumentException`.
- A header repeated by the server is joined into one value with `, `.
- The SDK writes no logs; wrap the transport with a `DelegatingHandler` to log.
- No `LICENSE` file is generated.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- Operations are asynchronous only, as `Task`-returning `...Async` methods, by decision D111. There is no blocking variant of an operation, because each SDK follows the style of its own language. Call it from your language's async machinery.

## Under review

These are proposals from a review of the type mapping against current .NET. None of them is decided, and none changes what the generator produces today.

- Map free-form objects to `System.Text.Json.Nodes.JsonNode` or `JsonElement` instead of `object`.
- Do not adopt the union types of C# 15 now: they need .NET 11, a short-support release, and would push the floor well above the oldest supported runtime.
- Keep `TimeSpan` with the protobuf-JSON converter for durations, and document that ISO-8601 calendar durations are unsupported.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. Reads `Activity.Current` through `DistributedContextPropagator.Current`; the header path has no thread hand-off.

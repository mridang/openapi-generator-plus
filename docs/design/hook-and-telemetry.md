# Call hook and OpenTelemetry adapter (issues #158 and #108)

Status: built in all twelve SDKs and settled as D141 in `docs/decisions.md`. The text below is the design as proposed and is kept for its reasoning; where it differs from the generated code, the code and the language pages win.

## 1. Goal

Every operation of all twelve SDKs calls one small, optional hook around the call: it can edit the request headers before the send and observe the status or the error afterwards, and it does nothing when no hook is set. The same hook is the only seam for tracing: an optional OpenTelemetry adapter, built on the OpenTelemetry API package and never on its SDK, implements the hook and records one span, one request count and one duration histogram per call with the same names and attributes in every language. The SDK core takes no OpenTelemetry dependency: where an OpenTelemetry API package is mature and can be an optional dependency the language gets the adapter, and in the other languages it gets the hook and a documented recipe that is tested like code.

## 2. Owner decisions that bind this work

From the issues and the task:

- One hook for editing, observing and tracing (#158). Separate hooks were rejected. The default is a no-op, so nothing changes for a user who does nothing.
- The core takes no OpenTelemetry dependency. The adapter is optional, built on the API package and not the SDK, and only where such a package exists; elsewhere a hook plus a recipe (#108).
- All twelve languages. Identical span names and attributes: the operation id as the span name, `http.request.method`, `url.template`, `server.address`, `http.response.status_code`, `error.type`, plus a request count and a duration histogram.
- The #140 recipe (wrapping the injected transport) stays as the fallback and as the answer for per-attempt access.

From `AGENTS.md` and the shared generator rules (`src/main/java/io/github/mridang/codegen/generators/AGENTS.md`):

- Clients only, no servers, no client-side validation, typed errors are thrown and never returned as values (Go returns, Rust returns `Result`, as already documented there).
- A difference between two languages is a defect until a named language feature forces it. New public API is added to all twelve in the same change (shared file, "API surface"), and the public members are compared by hand because the guard (#122) does not exist yet.
- Templates are Mustache files, never read a spec-sourced `x-*` extension, and a value the generator computes is handed over under a name without the `x-` prefix. `.github` workflows are never edited (S13).
- S9: the injected transport keeps working. The hook therefore lives above the transport, in `BaseApi`, so it fires with a caller-supplied HTTP client too.
- S7 and F1078: cancellation is never reported as the timeout error; `close()` is graceful. S15 and F830: nothing a log could leak. The request path reaches errors without its query string, and the hook follows the same rule.
- S10: a small pure library or a version bump is acceptable, a dependency with a native binary is not. G1: `.openapi-generator/DEV-DEPENDENCIES` lists only dependencies of tests that are emitted to a client.
- F1087: runtime floors are fixed by the manifests; the adapter may not raise one.
- Generated prose is character-aligned across the twelve and every example compiles as written (root `AGENTS.md`).
- The shared test catalogue (`src/spec/resources/test-catalogue.txt`): one behaviour id per behaviour, every language has a test named from the id or a line in `test-catalogue-exceptions.txt`. Test layout: unit tests mirror the source file, no catch-all files, every fix backed by a test.
- Existing state this work must respect: all twelve already have a `TraceContextUtil` that injects `traceparent` and `tracestate` when OpenTelemetry is present (FD-19, catalogue area `tracing`, ids tracing001 to tracing012). It is propagation only and it is optional or registered by hook in every language. It stays as it is (see risk 1).

## 3. The shared behaviour

### 3.1 Vocabulary (the same four types and one function in every language, cased by idiom)

- `CallHook`: one method, `start(info, headers)`, returning a `CallObserver` or nothing. Go takes the call context first (`Start(ctx, info, headers)`), as `ctx` is already the first parameter of every Go operation (S7).
- `CallObserver`: one method, `finish(outcome)`.
- `CallInfo`: `operationId`, `method`, `urlTemplate`, `url`, `serverAddress`.
- `CallOutcome`: `statusCode`, `headers`, `error`.
- `chain(hooks...)`: a hook that starts the hooks in the order given and finishes their observers in the reverse order.
- Configuration: `callHook` on the `Configuration` builder, a read accessor, and the same setter on the client builder. It is on `Configuration` and not on `TransportOptions` (F828: Configuration says what the API is and how a call is shaped, TransportOptions says how bytes travel) because it runs above the transport.

Field rules:

- `operationId` is the operation id exactly as written in the spec (`operationIdOriginal`), never the language-cased name, so a span is named the same in all twelve.
- `method` is the upper-case HTTP method.
- `urlTemplate` is the spec path template of the operation without the server base path, for example `/items/{id}`.
- `url` is the request URL without query string and fragment.
- `serverAddress` is the host of the request URL, lower-case, without port and without the brackets of an IPv6 literal.
- `statusCode` is 0 when no response arrived. `headers` is the lower-cased read-only response header map and is null when no response arrived (FD-5), never an empty map. `error` is the very error object the caller receives, or null on success.

When it runs:

1. Once per operation call, however many sends happen inside it (transport retries, the one replay after a 401) and for both variants of an operation (`op` and `opWithHttpInfo`, one call).
2. `start` runs at the point where the code now calls `injectTraceContext`, immediately after it: the header set is then final (defaults, operation headers, per-call headers, authentication, cookies, the propagated trace context), so a hook can read and replace any of them.
3. `finish` runs exactly once on every exit path after `start` returned: success, `ApiError`, network error, timeout, cancellation, a body that cannot be encoded, a response that cannot be decoded. The call then ends after decoding, so a decode failure is observed.
4. A header the hook sets is on every send of the call. Where a language rebuilds the headers for each attempt (Go, Python, see risk 3) the hook's edits are kept as a delta and reapplied; the fresh authentication headers of a 401 replay still replace a header of the same name, as they do today.
5. An error thrown by `start` propagates unchanged, nothing is sent and `finish` is not called. An error thrown by `finish` is swallowed, so the call still returns its result or its own error.
6. The hook never changes the result or the error of the call.

### 3.2 Catalogue lines to add

Two new areas, `hooks` (all twelve) and `telemetry` (all twelve: eight against the shipped adapter, four against the recipe class that the README shows). They are written in the file's own form. `multi` marks a line that several tests cover.

```
hooks001 | Without a hook set a call sends the same headers and returns the same result as before
hooks002 | start is called once per operation call with the spec operation id, the upper-case method, the url template and the url without its query string
hooks003 | serverAddress is the host without port, and without the brackets of an IPv6 literal | multi
hooks004 | A header the hook sets reaches the server
hooks005 | start sees the final header set, authentication and default headers included, and can replace one
hooks006 | A header the hook sets is on every send of the call, retries and the replay after a 401 included
hooks007 | The fresh authentication headers of a replay still replace a hook header of the same name
hooks008 | A call that is retried by the transport calls start once and finish once
hooks009 | The plain and the WithHttpInfo variant of an operation call the hook once each
hooks010 | A hook set on the configuration still fires when the caller supplied the transport
hooks011 | finish gets the status, the lower-cased response headers and no error on a 2xx
hooks012 | finish gets the status, the headers and the same typed ApiError the caller receives on a non-2xx
hooks013 | finish gets status 0, null headers and the network error when no response arrived
hooks014 | finish gets the timeout error when the end-to-end budget is exhausted, never the cancellation error
hooks015 | finish gets the cancellation and the call rethrows it, never as the timeout error
hooks016 | finish gets the SerializationError, with the status that arrived, when the response cannot be decoded
hooks017 | finish is called when the request body cannot be encoded and nothing is sent
hooks018 | An error thrown by start reaches the caller unchanged, nothing is sent and finish is not called
hooks019 | An error thrown by finish is swallowed and the caller still gets its result or its own error
hooks020 | chain starts the hooks in order and finishes their observers in reverse order, and a hook that returns nothing is skipped
hooks021 | The url given to the hook never carries the query string or the fragment
telemetry001 | The span is named with the spec operation id and has kind client
telemetry002 | A 2xx sets http.request.method, url.template, server.address and http.response.status_code and leaves the span status unset
telemetry003 | A 4xx or 5xx sets error.type to the status code as text and marks the span as an error
telemetry004 | error.type is timeout for the timeout error, network_error for the network error, serialization_error for the decode error, cancelled for a cancelled call and _OTHER for anything else
telemetry005 | A call with no response sets no http.response.status_code
telemetry006 | The span is a child of the caller's active span and the traceparent sent names the client span as its parent | multi
telemetry007 | A call retried by the transport records one span and one count
telemetry008 | The request count is incremented once per call with the same attributes as the span
telemetry009 | The duration histogram records one value in seconds per call with the same attributes as the span
telemetry010 | The instrumentation scope is named after the published package and carries the SDK version
telemetry011 | A provider handed to the adapter is used instead of the global one | multi
telemetry012 | With no provider configured the adapter does nothing and the call is unchanged
telemetry013 | The span ends exactly once, also when the call throws
telemetry014 | No attribute carries a query string, a header value or a credential
```

A language that cannot satisfy a line says why in `test-catalogue-exceptions.txt`. Candidates, none verified: telemetry006 and telemetry011 in the recipe languages if the recipe shows only the default provider; hooks015 in PHP, which runs one request at a time (precedent: `php | lifecycle019`); hooks003 IPv6 in a language whose URL parser rejects it (not verified).

### 3.3 Telemetry mapping (identical in every adapter and recipe)

| Item | Value |
|---|---|
| Span name | the operation id (`operationId`) |
| Span kind | client |
| Attributes | `http.request.method`, `url.template`, `server.address`, `http.response.status_code` (only when a response arrived), `error.type` (only on failure) |
| Span status | error for a status of 400 or more and for any thrown error, unset otherwise |
| `error.type` | the status code as text for a status of 400 or more; otherwise `timeout`, `network_error`, `serialization_error`, `cancelled`; otherwise `_OTHER` |
| Counter | `http.client.request.count`, unit `{request}`, attributes as the span |
| Histogram | `http.client.request.duration`, unit `s`, one value per call, attributes as the span |
| Instrumentation scope | the published package name (D22) and the SDK version |
| Window | from `start` to `finish`, measured with the language's monotonic clock |
| Not recorded | the query string, `url.full`, any header or body value, `server.port`, resend counts |

The histogram name and unit follow the OpenTelemetry HTTP client conventions as the maintainer knows them; they were not re-read in this session and must be checked against the current semantic-conventions page before the ids are frozen. `http.client.request.count` is not a semantic-convention name; the owner asked for a count, and the histogram's own count carries the same number (risk 7).

The adapter injects `traceparent` itself with the propagator of the provider it was given, so it needs no ambient "current span" and works across threads and coroutines. Because the existing `TraceContextUtil` runs before `start` and the adapter writes the same lower-case keys after it, the adapter's value wins and the header holds the client span as parent (risk 2).

## 4. The shared generator changes

Everything shared is small, because both inputs the hook needs are native fields of `CodegenOperation`.

| File | Change |
|---|---|
| `AbstractBetterCodegen.java` | No change to operation handling: `operationIdOriginal` (set from the spec id in `DefaultCodegen.fromOperation`, read in the 7.14.0 sources jar; null only for callback requests, which are out of scope, S11) and `path` already reach the templates. Add one protected method `hasOpenTelemetryAdapter()` (default false) and put its value into `additionalProperties` as `hasOpenTelemetryAdapter`, so one README partial can branch on it without a vendor extension. Spec-sourced strings go through `{{#lambda.stringLiteral}}` (D40). |
| `BetterJavaCodegen`, `BetterKotlinCodegen`, `BetterCSharpCodegen`, `BetterPythonCodegen`, `BetterNodeCodegen`, `BetterGoCodegen`, `BetterRustCodegen`, `BetterPhpCodegen` | Override `hasOpenTelemetryAdapter()` to true and register the adapter files in `getSupportingFileSpecs()` (the mechanism of `AbstractBetterCodegen.java:590`). |
| all twelve `Better<Lang>Codegen` | Register the hook files and the new test templates (`SupportingFileSpec` or the per-language `supportingFiles.add` blocks, for example `BetterGoCodegen.java:476-592`). |
| Mustache context | No new vendor extension. Templates read `{{{operationIdOriginal}}}` and `{{{path}}}`. |
| `src/main/resources/templates/<lang>/` | New hook files, edited API templates, `base_api`, `configuration`, `client`, `readme`, `skills`, `dev_dependencies`, manifests (section 5). |
| `src/main/resources/templates/<lang>/readme.mustache` | One section "Hooks and telemetry", the same heading and first paragraph in all twelve; its OpenTelemetry sub-block shows the adapter where `hasOpenTelemetryAdapter` and the recipe elsewhere. The recipe text lives in one partial that the README and the recipe test both include, so a recipe cannot drift from its test (risk 9). |
| `src/test/java/.../generators/` | A guard in the manner of `NoCommittedPrivateKeysTest`: no template other than the adapter files and the existing `trace_context_util*` files names OpenTelemetry (risk 1). A case in `AbstractBetterCodegenTest` that pins `operationIdOriginal` for an operation with and without a spec id. |
| `src/spec/resources/test-catalogue.txt`, `test-catalogue-exceptions.txt` | The lines of 3.2 and the exceptions. |
| Goldens `src/spec/resources/generated/<lang>/` | All twelve regenerated and committed in the same commit as the templates (shared file, "Before you push"). |
| Docs | `docs/decisions.md` (new section "A hook around every call"; the line for #108 in "Under review" says "tracer and meter providers to the Go client" and is wrong for the decided scope), `docs/languages/<lang>.md` (twelve: hook names, the adapter or recipe, the dependency row), shared `AGENTS.md` (two decision rows, an idiom-matrix row, the new areas), the twelve `generators/<lang>/AGENTS.md`. `docs/faq.md` does not exist. |

The one change that is not a template edit is passing the two new values into `BaseApi` in every language. They are not passed today in at least three: Go's `invokeApiParams` has no operation id (`go/pkg/base_api.go:120`), Python passes the snake-cased `operation_name="add_pet"` (`python/petstore_client/api/pet_api.py:268`), and Elixir passes none (`elixir/lib/petstore_client/api/pet_api.ex:389`). Java passes the camel-cased `"getPetById"`, which equals the spec id only by luck of the casing.

## 5. Per language

### 5.1 Which languages get the adapter

Rule: a language gets the adapter when an OpenTelemetry API package covers both traces and metrics at a stable level, can be an optional dependency, and an adapter file can live next to the core without making the dependency mandatory. Otherwise it gets the hook and a recipe.

| Language | OpenTelemetry API package and maturity (read 2026-11-18) | Result |
|---|---|---|
| java | `opentelemetry-api`; traces and metrics stable (opentelemetry.io java page). Already an optional dependency, `java/pom.xml:353-356` | adapter |
| kotlin | the same JVM API, `compileOnly` (`kotlin/build.gradle.kts:109`) | adapter, JVM source set only |
| csharp | the .NET API is the base class library (`ActivitySource`, `Meter`); status stable (opentelemetry.io dotnet page). That the BCL types are the API is not stated on that page; the existing `csharp/src/PetstoreClient/TraceContextUtil.cs` already uses `Activity` | adapter with no package at all |
| python | `opentelemetry-api`; traces and metrics stable (python page) | adapter |
| node | `@opentelemetry/api`; traces and metrics stable (js page). Already an optional peer, `node/package.json:37-43` | adapter |
| go | `go.opentelemetry.io/otel`, `otel/trace`, `otel/metric`; traces and metrics stable (go page). Already in `go/go.mod:11-13` | adapter |
| rust | `opentelemetry` crate; traces beta, metrics stable (rust page); 0.x versions. Already an optional feature, `rust/Cargo.toml:63-68` | adapter behind the existing feature, version pinned |
| php | `open-telemetry/api` 1.11.0, requires PHP ^8.2 (packagist); all signals stable (php page). The SDK floor is `^8.3` (`php/composer.json:7`) | adapter, `suggest` entry |
| ruby | `opentelemetry-api` traces stable; metrics are a separate gem `opentelemetry-metrics-api` 0.10.0 and the page lists metrics as Development (ruby page, rubygems) | hook plus recipe |
| elixir | `opentelemetry_api` stable for traces; metrics are in `opentelemetry_api_experimental`, which stays 0.x by its own versioning policy (hexdocs versioning page) | hook plus recipe |
| dart | `opentelemetry` 0.18.11 (Workiva), traces beta, metrics alpha, API and SDK in one package; the pub.dev page marks it deprecated and re-exporting `opentelemetry_wk`, which shows the same status | hook plus recipe |
| swift | `OpenTelemetryApi` product, no-op API, traces and metrics described as stable in the README at 2.6.0 (tools 6.0). The README of the main branch moves API and SDK into one package with tools 6.2 and warns against mixing it with the core package. SwiftPM has no optional dependency, and whether a nested package could be consumed by URL is not verified | hook plus recipe |

Doubt resolved: Dart and Swift are recipe, as the owner expected. Ruby and Elixir are recipe too, because the identical promise includes the metrics and their metrics APIs are not stable. PHP gets the adapter. Count: eight adapters, four recipes.

### 5.2 The table

Common to all twelve and not repeated in each row: `api` templates pass `{{{operationIdOriginal}}}` and `{{{path}}}` to the base class; `base_api` calls `start` after the trace-context injection and `finish` in every exit path; `configuration` and `client` carry the hook; `readme` and `skills` get the section; `docs/languages/<lang>.md` and `<lang>/AGENTS.md` are updated; the golden of every file below is regenerated. Golden folders are the ones read above, for example `src/spec/resources/generated/java/src/main/java/com/example/petstore/` (`BaseApi.java` is in `api/`).

| Language | Public names and signatures | Template files touched (under `templates/<lang>/`) | Golden files touched | Idiom and real limit |
|---|---|---|---|---|
| java | `interface CallHook { @Nullable CallObserver start(CallInfo info, Map<String,String> headers); static CallHook chain(CallHook... hooks); }`, `interface CallObserver { void finish(CallOutcome outcome); }`, records `CallInfo` and `CallOutcome`; `Configuration.Builder.callHook(CallHook)`, `Configuration.getCallHook()`, `Client.Builder.callHook(CallHook)`; adapter `OpenTelemetryCallHook.create()` and `create(OpenTelemetry)` | new `call_hook`, `call_info`, `call_observer`, `call_outcome`, `open_telemetry_call_hook`; edited `base_api` (the call at line 391), `api/api.mustache` (line 367 and the four `invokeApiForResult` overloads), `configuration`, `client`, `pom` (no change: the optional API dependency is already there), `test/BaseApiTest`, `test/ConfigurationTest`, `test/ClientTest`, new `test/CallHookTest`, `test/OpenTelemetryCallHookTest`, `dev_dependencies` (metrics test reader) | `CallHook.java`, `CallInfo.java`, `CallObserver.java`, `CallOutcome.java`, `OpenTelemetryCallHook.java`, `api/BaseApi.java`, `api/*Api.java`, `Configuration.java`, `Client.java`, tests, `README.md`, `SKILLS.md`, `.openapi-generator/DEV-DEPENDENCIES` | One public top-level type per file (`java/AGENTS.md`, `ServerVariable` precedent), hence four files; sync client so `start` and `finish` run on the calling thread; interrupt is the cancellation (S7). Retries are inside `DefaultApiClient` (`sendWithBackoff`, `default_api_client.mustache:833`), the 401 replay in `BaseApi` (`sendReplayingOnUnauthorized`), so one call is one `start`. The headers map is a plain `HashMap` and the replay removes same-name auth headers (`sendReplayingOnUnauthorized` in `base_api.mustache`). Verified in `java/base_api.mustache`, `java/pom` golden, `java/AGENTS.md`. |
| kotlin | `interface CallHook { fun start(info: CallInfo, headers: MutableMap<String,String>): CallObserver? }`, `fun interface CallObserver`, data classes `CallInfo`, `CallOutcome`; `Configuration.Builder.callHook`, `Client` builder `callHook`; adapter `OpenTelemetryCallHook` in the JVM source set | new common `call_hook`; new `jvmMain` `open_telemetry_call_hook`; edited `base_api` (line 253 of the golden), `api` template, `configuration`, `client`, `build_gradle` (nothing: `compileOnly` exists), tests as java | `src/main/kotlin/com/example/petstore/CallHook.kt` etc., `src/jvmMain/kotlin/.../OpenTelemetryCallHook.kt`, `BaseApi.kt`, `Configuration.kt`, `Client.kt`, tests, docs | Operations are `suspend`; `start` and `finish` are plain functions called inline, so they must not block (documented). Because the adapter injects the header itself, the thread-local OpenTelemetry context never has to survive a suspension. Multiplatform build with one JVM target (`build.gradle.kts:51`); `TraceContextUtil` is `expect`/`actual`, which the hook does not need. Verified in `kotlin/build.gradle.kts`, `kotlin/.../TraceContextUtil.kt`. Whether an iOS or JS target is planned: not verified. |
| csharp | `interface ICallHook { ICallObserver? Start(CallInfo info, IDictionary<string,string> headers); static ICallHook Chain(params ICallHook[] hooks); }`, `interface ICallObserver { void Finish(CallOutcome outcome); }`, `record CallInfo`, `record CallOutcome`; `ConfigurationBuilder.CallHook(ICallHook)`, `Configuration.CallHook`, client builder `CallHook`; adapter `OpenTelemetryCallHook` (no argument; uses `ActivitySource` and `Meter` named after the package) | new `call_hook`, `call_observer`, `call_info`, `call_outcome`, `open_telemetry_call_hook`; edited `base_api` (golden line 298), `api`, `configuration`, `client`, tests (`BaseApiTest`, `ConfigurationTest`, `ClientTest`, new `CallHookTest`, `OpenTelemetryCallHookTest`) | `src/PetstoreClient/ICallHook.cs` etc., `Api/BaseApi.cs`, `Configuration.cs`, `Client.cs`, `Test/*`, docs | `I` prefix is the language's interface rule; no OpenTelemetry package, because `ActivitySource` and `Meter` are in the base class library on the net10.0 target (`PetstoreClient.csproj:7`); the adapter file can therefore ship in the core assembly without a dependency. Cancellation token is a trailing parameter (S7) and is reported as `cancelled`. Whether the analyzers force one type per file: not verified. Verified in `csharp/.../TraceContextUtil.cs`, `PetstoreClient.csproj`. |
| php | `interface CallHook { public function start(CallInfo $info, array &$headers): ?CallObserver; }`, `interface CallObserver { public function finish(CallOutcome $outcome): void; }`, `final readonly class CallInfo`, `CallOutcome`; `ConfigurationBuilder::callHook()`, `Configuration::getCallHook()`; adapter `OpenTelemetryCallHook::create(?...)` | new `call_hook`, `call_observer`, `call_info`, `call_outcome`, `open_telemetry_call_hook` (PSR-4, one class per file, FD-4 and FD-9); edited `base_api` (golden line 299), `api`, `configuration`, `composer` (add `suggest` for `open-telemetry/api`), `dev_dependencies`, tests (`tests/BaseApiTest`, `ConfigurationTest`, `ClientTest`, new `CallHookTest`, `OpenTelemetryCallHookTest`) | `lib/CallHook.php` etc., `lib/Api/BaseApi.php`, `lib/Configuration.php`, `composer.json`, `tests/*`, docs | The array is passed by reference, as `TraceContextUtil::injectTraceContext(array &$headers)` already does. Requires no `//` comments (build-breaking rule). PHP runs one request at a time, so metrics only reach a backend if the application flushes its provider; the adapter cannot do it (documented). PHP floor is ^8.3, the API needs ^8.2: no floor change. Verified in `php/lib/TraceContextUtil.php`, `php/composer.json`. |
| python | `class CallHook(Protocol): def start(self, info: CallInfo, headers: Dict[str,str]) -> Optional[CallObserver]`, `class CallObserver(Protocol): def finish(self, outcome: CallOutcome) -> None`, frozen dataclasses `CallInfo`, `CallOutcome`; `chain(*hooks)`; `ConfigurationBuilder.call_hook(hook)`, `Configuration.call_hook`; adapter `petstore_client.opentelemetry_call_hook.OpenTelemetryCallHook(tracer_provider=None, meter_provider=None)` | new `call_hook`, `opentelemetry_call_hook`; edited `base_api` (golden line 415, and `build_headers` which runs for every attempt), `api/api.mustache` (replace `operation_name` use for the original id), `configuration`, `client`, `pyproject_toml` (an extra `opentelemetry = ["opentelemetry-api"]`), tests (`test_base_api`, `test_configuration`, new `test_call_hook`, `test_opentelemetry_call_hook`) | `petstore_client/call_hook.py`, `petstore_client/opentelemetry_call_hook.py`, `api/base_api.py`, `api/*_api.py`, `configuration.py`, `pyproject.toml`, tests | The client is `async`; the hook is a plain function called inline. The package `__init__` must not import the adapter, so the core imports no OpenTelemetry. Headers are rebuilt per attempt (`build_headers`, "Every send ... is a fresh request"), so the hook's delta is reapplied; frozen dataclass means the hook goes in the builder. Verified in `python/.../api/base_api.py`, `configuration.py`, `pyproject.toml`. |
| ruby | duck type: any object with `start(info, headers)` returning an observer with `finish(outcome)`, or nil; `Petstore::Client::CallInfo` and `CallOutcome` as `Data.define`; `CallHook.chain`; `Configuration::Builder#call_hook(hook)`, `Configuration#call_hook`; no adapter | new `call_info`, `call_outcome`, `call_hook` (each its own file, Zeitwerk, FD-9), `infrastructure_rbs` (signatures for Steep); edited `base_api` (golden line 262), `api`, `configuration`, `client`, `readme` with the recipe partial, tests (`base_api_test`, `configuration_test`, new `call_hook_test`, `call_hook_recipe_test`) | `lib/petstore/client/call_info.rb`, `call_outcome.rb`, `call_hook.rb`, `sig/*.rbs`, `api/base_api.rb`, `configuration.rb`, `README.md`, tests | `Data.define` needs Ruby 3.2, the floor is `>= 3.2` (`ruby.gemspec`). The soft `require 'opentelemetry-api'` already used by `TraceContextUtil` shows the detection style; a recipe needs `opentelemetry-metrics-api` for the instruments, which is 0.x. Steep runs `:lib` at full strength (KT-3), so the new files need signatures. Verified in `ruby/lib/petstore/client/trace_context_util.rb`, gemspec. |
| node | `interface CallHook { start(info: CallInfo, headers: Record<string,string>): CallObserver \| void }`, `interface CallObserver { finish(outcome: CallOutcome): void }`, types `CallInfo`, `CallOutcome`, `chain(...hooks)`; `ConfigurationBuilder.callHook(hook)`, `Configuration.callHook`; adapter exported from the subpath `./opentelemetry`: `createOpenTelemetryCallHook({ tracerProvider?, meterProvider? })` | new `call_hook` (ts), `opentelemetry_call_hook`, `package` (add the `exports` entry `./opentelemetry`; the map now has only `.` and `./package.json`, `package.json:30-36`), `index` (exports the four types), edited `base-api` (golden line 284), `api/apis.mustache` (lines 248 and 252), `configuration`, `client`, tests (`base-api.test`, `configuration.test`, new `call-hook.test`, `opentelemetry-call-hook.test`), `dev_dependencies` (add the metrics SDK) | `src/call-hook.ts`, `src/opentelemetry-call-hook.ts`, `src/api/base-api.ts`, `src/api/*-api.ts`, `src/configuration.ts`, `src/index.ts`, `package.json`, `test/*` | `@opentelemetry/api` is already an optional peer. The core `trace-context-util.ts` loads it with a guarded `require`; the adapter imports it statically because it is its own entry point. The web build (`default-api-client.web.ts`) must keep working: the hook is above the transport. Verified in `node/src/trace-context-util.ts`, `package.json`, `api/base-api.ts`. |
| dart | `abstract interface class CallHook { CallObserver? start(CallInfo info, Map<String,String> headers); }`, `abstract interface class CallObserver { void finish(CallOutcome outcome); }`, classes `CallInfo`, `CallOutcome`, `chainCallHooks(List<CallHook>)`; `ConfigurationBuilder.callHook(hook)`, `Configuration.callHook`; no adapter | new `call_hook`, `call_info`, `call_outcome`; edited `base_api` (golden line 258), `api`, `configuration`, `client`, `readme` recipe partial, `dev_dependencies` (nothing), tests (`base_api_test`, `configuration_test`, `client_test`, new `call_hook_test`, `call_hook_recipe_test`) | `lib/src/call_hook.dart` etc., `lib/src/api/base_api.dart`, `lib/src/configuration.dart`, `lib/<package>.dart` exports, `README.md`, tests | Dart cannot declare an optional dependency (pubspec has only `dependencies` and `dev_dependencies`; `TraceContextUtil.propagator` is already a registration hook for that reason, `docs/languages/dart.md:74`). The recipe test uses the existing dev dependency `opentelemetry ^0.18.11` (`dart/pubspec.yaml:24`), which pub.dev marks deprecated; the recipe must name the maintained package once that is confirmed. SDK floor 3.11 (`pubspec.yaml:11`). Verified in `dart/pubspec.yaml`, `docs/languages/dart.md`. |
| go | `type CallHook interface { Start(ctx context.Context, info CallInfo, headers map[string]string) CallObserver }`, `type CallObserver interface { Finish(CallOutcome) }`, structs `CallInfo`, `CallOutcome`, `CallHookFunc`, `ChainCallHooks(...CallHook) CallHook`; `ConfigurationBuilder.CallHook(CallHook)`, `Configuration.CallHook()`; adapter in the subpackage `pkg/otelhook`: `otelhook.New(opts ...Option)` with `WithTracerProvider`, `WithMeterProvider`, `WithPropagator` | new `call_hook` (pkg), `otelhook/otel_call_hook` (new subpackage, like `pkg/auth`, `pkg/errors`), edited `base_api` (golden line 299; `invokeApiParams` gains `operationId` and `urlTemplate`), `api/api.mustache` (line 272), `configuration`, `client`, `go_mod` (move `otel/metric` to a direct requirement), tests (`base_api_test`, `base_api_unit_test`, `configuration_test`, `client_test`, new `call_hook_test`, `otelhook_test`), `dev_dependencies` | `pkg/call_hook.go`, `pkg/otelhook/otelhook.go`, `pkg/base_api.go`, `pkg/*_api.go`, `pkg/configuration.go`, `go.mod`, `go.sum`, tests | The retry loop is in `baseApi` (`base_api.go:359-430`) and rebuilds the headers for every attempt (`buildAttempt`), so the hook's edits are stored as a delta; `Start` receives the call context because the OpenTelemetry parent comes from it. The SDK already passes the call context to `InjectTraceContext(ctx, headers)` (`base_api.go:299`). A custom `ApiClient` is never retried (`retryPolicyProvider`). The adapter lives in the same module so no second `go.mod`; the module already requires otel (`go.mod:11-13`), the core package imports none of it (risk 5). Verified in `go/pkg/base_api.go`, `trace_context_util.go`, `go.mod`. |
| rust | `trait CallHook: Send + Sync { fn start(&self, info: &CallInfo, headers: &mut HashMap<String,String>) -> Option<Box<dyn CallObserver>>; }`, `trait CallObserver: Send { fn finish(self: Box<Self>, outcome: &CallOutcome); }`, structs `CallInfo`, `CallOutcome<'a>`, `chain(Vec<Arc<dyn CallHook>>)`; `ConfigurationBuilder::call_hook(Arc<dyn CallHook>)`, `Configuration::call_hook()`; adapter `OpenTelemetryCallHook::new()` and `with_providers(...)` behind the existing `opentelemetry` feature | new `call_hook`, `open_telemetry_call_hook`; edited `base_api` (golden lines 334 and 755: the header build and `refresh_request_headers`), `api`, `configuration`, `client`, `cargo` (add the metrics API to the existing feature), tests (`base_api_test`, `configuration_test`, `client_test`; the adapter's tests are in-crate like `trace_context_util.rs`'s) | `src/call_hook.rs`, `src/open_telemetry_call_hook.rs`, `src/api/base_api.rs`, `src/api/*_api.rs`, `src/configuration.rs`, `src/lib.rs`, `Cargo.toml`, `tests/*` | Operations return `Box<dyn Error>` (#113), so `CallOutcome` borrows `&(dyn Error + 'static)` and the adapter classifies by downcast to the SDK errors. The trait objects must be `Send + Sync` for `async` use. `Configuration` may derive `Clone` and `Debug`, which an `Arc<dyn CallHook>` complicates: not verified. The crate is 0.x and its traces API is beta, so the version is pinned (`0.32` in `Cargo.toml:65-67`) and a bump is a manual task. Compiles only on CI (amd64, shared file, "Known limitations"). Verified in `rust/src/trace_context_util.rs`, `Cargo.toml`, `api/base_api.rs`. |
| swift | `public protocol CallHook: Sendable { func start(info: CallInfo, headers: inout [String:String]) -> (any CallObserver)? }`, `public protocol CallObserver: Sendable { func finish(_ outcome: CallOutcome) }`, structs `CallInfo`, `CallOutcome`, `CallHooks.chain(_:)`; `Configuration.Builder.callHook(_:)`, `Configuration.callHook`; no adapter | new `call_hook`; edited `base_api` (golden line 288, where `injectTraceContext(headers: &headers)` takes an `inout`), `api`, `configuration`, `client`, `readme` recipe partial, tests (`BaseApiTests`, `ConfigurationTests`, `ClientTests`, new `CallHookTests`, `CallHookRecipeTests`) | `Sources/PetstoreClient/CallHook.swift`, `Api/BaseApi.swift`, `Configuration.swift`, `Client.swift`, `Tests/*`, docs | `swift-tools-version: 6.0` (`Package.swift:1`) puts the target in Swift 6 language mode, which makes `Sendable` a requirement; that the generated target sets the mode explicitly: not verified. Operations are `async throws`. `TraceContextUtil.propagator` is a registration hook already (`TraceContextUtil.swift`). The recipe test uses the existing test-only `opentelemetry-swift` dependency (`Package.swift:47-64`). Linux behaviour of the OpenTelemetry API: not verified. |
| elixir | `@behaviour PetstoreClient.CallHook` with `@callback start(info, headers) :: {headers, state}` and `@callback finish(state, outcome) :: any`; structs `CallInfo`, `CallOutcome`; `CallHook.chain/1`; `Configuration.new(call_hook: module_or_{module, opts})`; no adapter | new `call_hook`; edited `base_api` (golden line 490 inside `build_request` at 398; `build_for_replay` at 615), `api` (passes the original id and the template), `configuration`, `client`, `readme` recipe partial, tests (`base_api_test`, `configuration_test`, `client_test`, new `call_hook_test`, `call_hook_recipe_test`) | `lib/petstore_client/call_hook.ex`, `api/base_api.ex`, `configuration.ex`, `client.ex`, `README.md`, tests | Immutable data: `start` returns the new headers and an opaque state that `finish` receives, like `inject_trace_context/1` returning the map. `end` is a keyword, so the callbacks are `start` and `finish`. `Configuration` has no builder (FD-8), so the hook is one more keyword. No `//`-style problem; `mix compile --warnings-as-errors` and dialyzer gate (`elixir ~> 1.17`, `mix.exs:8`). The recipe test uses the test-only `opentelemetry_api` already in `mix.exs:96-97`. Verified in `elixir/lib/.../trace_context_util.ex`, `mix.exs`. |

## 6. The tests

Placement follows the repository: the behaviour ids live in the test templates that exist (`BaseApiTest`, `ConfigurationTest`, `ClientTest` and their per-language names), the way every catalogue id does, and a mirror test template is added only for a new source file (`CallHookTest` for `chain`, `OpenTelemetryCallHookTest` for the adapter), as `TraceContextUtilTest` mirrors `TraceContextUtil`. The shared file says "do not add test files"; a new source file with no test template is the one case, flagged as risk 10. The four recipe languages carry the same `telemetry` ids in a test that includes the README recipe partial.

| Ids | What a test asserts | Where |
|---|---|---|
| hooks001 | Default configuration: headers sent and result equal to the unhooked call; no `CallInfo` is built | `BaseApiTest` |
| hooks002, hooks003, hooks021 | A fake hook records the `CallInfo` of a golden operation: spec id (not the cased name), `GET`, `/items/{id}`, URL without `?a=1#x`, server address for a normal host and a `[::1]` base URL | `BaseApiTest` |
| hooks004, hooks005 | The server (Chasm, as in the existing tests) sees the added header; the hook reads the auth header and replaces it | `BaseApiTest` |
| hooks006, hooks007, hooks008, hooks009, hooks010 | Operations that fail with 503 then succeed, and with 401 then 200 under an OAuth authenticator (the fixtures the `retries` ids already use; which operations: not verified): one `start`, one `finish`, the header on every request the server logged, the replay's fresh auth header winning; plain and `WithHttpInfo` variants; a supplied transport (S9) | `BaseApiTest`, `ClientTest` |
| hooks011 to hooks017 | One test per outcome: 200, 404 with a typed error, refused connection, exhausted budget, cancellation, undecodable body, unencodable body; the outcome's status, headers (null versus empty, FD-5) and error identity | `BaseApiTest` |
| hooks018, hooks019 | A throwing `start` and a throwing `finish` | `BaseApiTest` |
| hooks020 | Order of `start` and `finish` through `chain`, and a hook returning nothing | `CallHookTest` |
| telemetry001 to telemetry005, telemetry013, telemetry014 | In-memory span exporter: name, kind, attribute set, status, `error.type` tokens, one end, no query or header value in any attribute | `OpenTelemetryCallHookTest` (adapters), recipe test (recipes) |
| telemetry006 | A parent span active: the span's parent is it and the `traceparent` the server logged carries the client span id, once | same |
| telemetry007, telemetry008, telemetry009, telemetry010 | In-memory metric reader: one count and one duration value (unit seconds) per call with the span's attributes, scope name and version, also when the transport retried | same |
| telemetry011, telemetry012 | A provider passed in versus the global one; no provider: nothing recorded and the call unchanged | same |

Also: a generator guard (section 4) that the core templates name no OpenTelemetry; wiring tests in `ConfigurationTest` (the setter, the accessor, `toString` does not print the hook, as S15 redaction does for secrets) and `ClientTest` (the client builder hands the hook to every API object). Every test must fail without its fix: revert once to prove it. Tests that wait use an observable condition, never a sleep (shared file, "Gate and push"). New test dependencies (the metrics SDK or in-memory reader) go into each language's `DEV-DEPENDENCIES` and manifest (G1) and into the keep-listed manifest of each real client (root `AGENTS.md`). Existing OpenTelemetry SDK test dependencies are already present in ten languages (java, kotlin, python, node, go, rust, ruby, elixir, dart, swift by the files listed in section 5); csharp needs none.

## 7. Risks and open questions

Each item is real, with the recommended answer.

1. The existing `TraceContextUtil` contradicts "no OpenTelemetry dependency" if read literally. Java has an optional Maven dependency and references the classes (`java/pom.xml:353-356`, `TraceContextUtil.java`), kotlin `compileOnly`, rust an optional feature, node an optional peer, go requires otel in `go.mod`, the others detect it at run time. Answer: it is propagation only, accepted as FD-19, and stays; the new hook types and everything in the core are free of OpenTelemetry, and the generator guard lists the grandfathered files. Do not remove the utility: it is what a user without the adapter relies on for `traceparent`.
2. Header order. `injectTraceContext` runs before `start`, so with the adapter the header would hold the caller's span as parent unless the adapter writes it afterwards. Answer: keep the order, the adapter overwrites the same lower-case keys, and telemetry006 asserts one `traceparent` naming the client span. Header names differing only in case are the hook author's job; the hook gets a plain map and the docs say to reuse the existing spelling (a case-insensitive map in all twelve was rejected as larger than the problem).
3. Header rebuild differs. Java and Node build the headers once and reuse them on the replay; Go rebuilds per attempt (`buildAttempt`), Python per send (`build_headers`), Rust and Elixir rebuild through `refresh_request_headers` and `build_for_replay`; C#, Kotlin, PHP, Ruby, Dart and Swift: not verified. The promise "a hook header is on every send" needs a stored delta in the rebuilding languages. Answer: implement the delta, and test hooks006 in all twelve so a language that forgot it fails.
4. Per-attempt access is not offered. Retries are in the transport in Java and in `BaseApi` in Go and Python, and a caller-supplied transport retries on its own terms (S9). Answer: once per call, written down the same way in all twelve as the issue asks; per-attempt needs the #140 recipe. Resend counts are not recorded for the same reason.
5. Go adapter location. A nested module would need a parent version tag and a `replace` for the golden; a package in the same module needs nothing and the module graph already lists otel (`go.mod:11-13`). Answer: subpackage `pkg/otelhook`; the core package imports nothing.
6. Operation id and template reach `BaseApi` differently today (section 4). Using a language-cased name would give `add_pet` in Python and `addPet` in Java and break "identical span names". Answer: always `operationIdOriginal`, pinned by a generator unit test; fall back to `operationId` only if null.
7. Metric names. `http.client.request.count` is not a semantic-convention name; the histogram already counts. Bucket boundaries can only be suggested through each API (support per language not verified). Answer: keep the count the owner asked for and say in the README that the histogram's count equals it; promise names, unit and attributes and not buckets.
8. Ruby and Elixir have a stable traces API and an unstable metrics API. A spans-only adapter would break the identical promise. Answer: recipe for both, revisit when the metrics API stabilises; Dart and Swift recipe as expected.
9. Recipe drift. #111 (compiled README examples) is open, so nothing yet compiles a README. Answer: the recipe is one Mustache partial included by both the README and the recipe test, so the text a user copies is the text under test.
10. Test file placement. The repository says extend existing test templates; the user's rule says a new source file gets its mirror test. Answer: mirror files only for `call_hook` and the adapter, everything else in existing templates; confirm with the owner.
11. `chain` is surface beyond the issue ("one small hook"). Without it a user cannot have a signing hook and the adapter. Answer: keep it, one function.
12. Hook error policy: `start` errors propagate, `finish` errors are swallowed (hooks018, hooks019). Alternative: swallow both. Answer: as written, because an editing hook must be able to refuse a call and an observer must never replace the real outcome.
13. Metrics need a dependency in the adapter tests only where the SDK test packages lack a reader; G1 and the keep-listed client manifests break if a dev dependency is missed. Answer: add to `DEV-DEPENDENCIES` and run the six real clients' own CI after the change (shared file, "Client guardrails").
14. `url.template` without the base path (`/items/{id}`), no `server.port`. Answer: as written; both can be added later without a break because attributes are additive.
15. Swift 6 `Sendable` and Rust `Send + Sync` make the hook types stricter than the others, and a closure-based hook is not enough (it needs state between `start` and `finish`). Answer: accept; the observer value is the state.

## 8. Order of work and size

Size is per language and covers hook, adapter or recipe, tests, docs page and the golden.

| Order | Step | Languages and size |
|---|---|---|
| 0 | Owner answers risks 1, 7, 10, 11, 12 and the open points in 3.3; write decisions D95 (hook) and D96 (telemetry) into the shared `AGENTS.md`, add the catalogue lines and exceptions, extend `AbstractBetterCodegen` with `hasOpenTelemetryAdapter` and the generator guard tests | generator S |
| 1 | Reference implementation in one language, reviewed before copying: java (hook, adapter, all ids) | java M |
| 2 | Languages whose retry and header model is closest to java, one worktree each | kotlin M, csharp M, php M |
| 3 | Languages that rebuild headers per attempt or are async-first: delta logic and the adapter with its own options | python M, node M, go L, rust L |
| 4 | Hook plus recipe, with the recipe partial and its test | ruby S, dart S, swift M, elixir M |
| 5 | Merge the branches, regenerate all twelve goldens in one commit, update `docs/decisions.md`, the twelve language pages, the twelve `AGENTS.md`, run `etc/ci-local.sh check` for each language, one independent review with the `idiom-<lang>` agents, then the Zitadel clients' own CI, then push once and let CI decide | all, S |

Real code needed: all twelve languages need hook code (the new types, the `BaseApi` calls, the configuration and client setters); eight (java, kotlin, csharp, php, python, node, go, rust) also need adapter code; four (ruby, dart, swift, elixir) need only the hook plus a recipe, its test and docs. No language is tests-or-docs only.

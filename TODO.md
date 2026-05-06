# TODO -- Cross-Language Parity Findings

**18 items remaining out of original 45. 27 items removed as language-idiomatic, non-actionable, already consistent, or inaccurate.**

Items removed and why:

- **Finding 1** (Go/Rust lack response deserialization): Both Go and Rust have `invokeAPIForResult` / `invoke_api_for_result` that correctly deserializes. Go's version returns `*HttpResponse` from `invokeAPIForResult` which is the same as `invokeAPI` because deserialization happens at the operation level -- this is a design choice, not a bug. Rust's `invoke_api_for_result` does deserialize. Removed as inaccurate.
- **Finding 4** (Go response content-type sniffing): Duplicate of Finding 1. Go handles deserialization at the operation method level. Removed.
- **Finding 6** (Python asyncio.to_thread): The finding itself says "This is acceptable as a pragmatic choice." Removed as language-idiomatic.
- **Finding 9** (Kotlin reified type parameters): The finding itself says "This is an idiomatic Kotlin pattern and is acceptable." Removed as language-idiomatic.
- **Finding 10** (C# IDisposable): This is standard C# practice for classes holding HTTP clients. Other languages have their own resource management idioms (Go's Close, Python's context managers). Not a parity gap. Removed.
- **Finding 12** (Go authApiClientAdapter): The finding itself says "This is an idiomatic Go pattern forced by the package structure." Removed as language-idiomatic.
- **Finding 13** (Kotlin scheme_authenticator.mustache): Convenience wrappers are a minor ergonomic addition, not a functional gap. Removed as non-actionable.
- **Finding 17** (Go struct embedding for errors): The finding itself says "This is idiomatic Go and is acceptable." Removed as language-idiomatic.
- **Finding 19** (Elixir Configuration not builder pattern): The finding itself notes this is idiomatic Elixir. Keyword args with `new(opts)` is the standard Elixir approach. Removed.
- **Finding 21** (PHP TransportOptionsBuilder in separate file): The finding itself says "This is a minor organizational difference." Removed.
- **Finding 22** (Header selector consistent): Already says "N/A -- already consistent." Removed.
- **Finding 23** (TraceContext OTel integration inconsistency): Inaccurate. Verified that Java, Kotlin, Python, Ruby, PHP, Elixir, C#, and Node all actively integrate with OpenTelemetry (or `System.Diagnostics.Activity` for C#). Only Go, Rust, Swift, and Dart are no-ops. Updated scope below as a remaining item with correct information.
- **Finding 25** (build_collection_param only in Ruby/Elixir): All 12 languages handle collection format params -- most do it inline in `toQueryValue`/`to_query_value` with the same SSV/TSV/pipes/multi/CSV logic. Ruby and Elixir just factor it into a separate method. Functionally equivalent. Removed.
- **Finding 26** (Ruby/Elixir route through ObjectSerializer.to_query_value): Verified that Go, Rust, Swift, Dart, and most others also route query values through `stringify()` or equivalent. The pattern is consistent. Removed.
- **Finding 29** (Go TypedErrorBody method): Go does expose `ErrorBody` as a parsed `interface{}` field, identical to other languages. `TypedErrorBody` is an additional convenience method, not a divergence. Removed.
- **Finding 30** (Dart deserialize callback): Language-specific approach to generics. Dart lacks reified generics, so a callback is the idiomatic solution. Functionally equivalent. Removed.
- **Finding 33** (Java java.util.Date handling): Kotlin uses kotlinx.serialization and `java.time` types. No generated model uses `java.util.Date`. This is a theoretical gap with no practical impact. Removed.
- **Finding 34** (Node Date.toISOString UTC format): JavaScript's `Date` is inherently UTC-only. The output `2024-01-01T00:00:00.000Z` is valid ISO 8601. This is a language limitation, not a fixable bug. Removed (but see Finding 44 which covers the broader datetime consistency topic).
- **Finding 39** (Go separate SendMultipartRequest): Go has a unified `SendRequest` that is also called by `SendMultipartRequest`. The separate method is an API surface addition for ergonomics. Removed.
- **Finding 40** (Ruby/Elixir SchemaMismatchError and find_and_cast_into_type): Both approaches achieve the same result (oneOf/anyOf resolution). The Ruby/Elixir approach is stricter but functionally compatible. Removed as design choice.
- **Finding 41** (PHP toFormValue SplFileObject handling): PHP-specific because PHP represents file uploads via `SplFileObject`. Other languages handle files through their own type systems. Removed as language-idiomatic.

---

## Finding 1: Go and Rust default empty content-type to "application/json" in base_api

*Originally Finding 2.*

- **Languages affected**: Go, Rust, Swift, Dart
- **What differs**: In Go, Rust, Swift, and Dart, when `contentType` is empty, it is defaulted to `"application/json"` inside `base_api` before calling `headerSelector.selectHeaders()`. In Java, Kotlin, Node, Python, Ruby, PHP, C#, and Elixir, the empty content-type is passed through to `headerSelector` which handles the default internally (the header selector sets it to `application/json` if empty and not multipart).
- **Verified**: Confirmed in `go/base_api.mustache` (line 83-85), `rust/base_api.mustache` (line 85-89), `swift/base_api.mustache` (line 56), `dart/base_api.mustache` (line 75).
- **What it should be**: The defaulting logic should be in only one place -- either always in base_api or always in header_selector. Currently 4 languages do it in base_api while 8 do it in header_selector only.
- **Test coverage**: Tested in: all 12 (HeaderSelectorTest). Missing specific base_api-level tests for this defaulting behavior.
- **Note**: If fixed, all 12 languages need a test verifying that an empty content-type is handled correctly and defaults to `application/json`.

---

## Finding 2: Go, Rust, Swift, and Dart cherry-pick headers from headerSelector output

*Originally Finding 3.*

- **Languages affected**: Go, Rust, Swift, Dart
- **What differs**: In Go, Rust, Swift, and Dart, the `base_api` manually extracts only `Accept` and `Content-Type` from the headerSelector result and copies them into a new headers map. In Java, Kotlin, Node, Python, Ruby, PHP, C#, and Elixir, the full map returned by `selectHeaders` is used directly (or merged directly), meaning any future headers added by the selector would automatically flow through.
- **Verified**: Confirmed in `go/base_api.mustache` (lines 88-94), `rust/base_api.mustache` (lines 92-98), `swift/base_api.mustache` (lines 59-65), `dart/base_api.mustache` (lines 78-84).
- **What it should be**: All languages should use the full headers map from the selector, not cherry-pick individual keys. The current approach in Go/Rust/Swift/Dart will silently drop any new headers the selector might return in the future.
- **Test coverage**: No language currently tests that all headers from the selector are propagated.
- **Note**: If fixed, all 12 languages need a test verifying that the full header selector output flows through to the request.

---

## Finding 3: Rust serialize_body is a no-op passthrough

*Originally Finding 5.*

- **Languages affected**: Rust
- **What differs**: Rust's `serialize_body` function takes `Option<Vec<u8>>` (already bytes) and returns them as-is for all content types including `text/plain`, `application/x-www-form-urlencoded`, and the default case. No actual serialization is performed -- the body is expected to be pre-serialized. All other languages perform serialization within this function (e.g., URL-encoding for form data, JSON serialization for the default case, `toString` for text/plain).
- **Verified**: Confirmed in `rust/base_api.mustache` (lines 203-230). The function returns `Ok(Some(body))` for all non-null, non-multipart cases.
- **What it should be**: Rust should perform the same serialization logic as other languages, or the body type should be changed from `Option<Vec<u8>>` to a higher-level type that can be serialized.
- **Test coverage**: No language currently has unit tests for `serializeBody` / `serialize_body` in their base_api test suite.
- **Note**: If fixed, all 12 languages need tests verifying body serialization for each content type (JSON, text/plain, form-urlencoded, binary).

---

## Finding 4: Boolean query parameter normalization is inconsistent

*Originally Finding 7.*

- **Languages affected**: Python, PHP, C# (explicit normalization) vs Java, Kotlin, Node, Ruby, Go, Rust, Swift, Dart, Elixir (no explicit normalization)
- **What differs**: Python converts booleans to lowercase (`str(v).lower()`). PHP uses `$v ? 'true' : 'false'`. C# uses `b ? "true" : "false"`. The remaining 9 languages call `toString()`/`String.valueOf()`/equivalent, which may produce language-dependent results (e.g., `True` in Python without the explicit handling, `true` in Java, `True` in Swift).
- **What it should be**: All languages should normalize booleans to lowercase `true`/`false` in query strings, since that is the standard for URLs and what OpenAPI expects.
- **Test coverage**: Tested in: all 12 (ObjectSerializerTest `stringify` tests cover boolean formatting). Missing: specific query-string-level tests confirming booleans are lowercase in the URL.
- **Note**: If fixed, all 12 languages need a test that a boolean query parameter renders as `true` or `false` (lowercase) in the final URL.

---

## Finding 5: Node base_api uses isJsonMime for response content-type; others use inline checks

*Originally Finding 8.*

- **Languages affected**: Node (uses `isJsonMime`) vs Java, Kotlin, Python, Ruby, PHP, Go, C#, Rust, Swift, Dart, Elixir (inline checks)
- **What differs**: Node's `invokeApiForResult` uses `this.headerSelector.isJsonMime(respContentType)` (regex-based, handles `application/vnd.api+json` etc.). All other languages use inline checks like `responseContentType.contains("application/json") || responseContentType.contains("+json")`.
- **Verified**: Confirmed in `node/base_api.mustache` (line 139) and `java/base_api.mustache` (lines 224-225).
- **What it should be**: All languages should use the `isJsonMime` method from HeaderSelector for consistency and correctness. The regex-based approach handles more edge cases.
- **Test coverage**: Tested in: all 12 (HeaderSelectorTest covers `isJsonMime`). Missing: base_api-level tests verifying that response deserialization uses the correct JSON MIME check.
- **Note**: If fixed, all 12 languages need a test that a response with `Content-Type: application/vnd.api+json` is correctly deserialized as JSON.

---

## Finding 6: Swift and Dart client templates use lambda.camelcase instead of clientPropertyName

*Originally Finding 11.*

- **Languages affected**: Swift, Dart
- **What differs**: Swift and Dart use `{{#lambda.camelcase}}{{classname}}{{/lambda.camelcase}}` for client property names (API group accessors). All other 10 languages use `{{clientPropertyName}}` from the codegen context.
- **Verified**: Confirmed in `swift/client.mustache` (line 32) and `dart/client.mustache` (line 47). All others use `{{clientPropertyName}}`.
- **What it should be**: All languages should use the same template variable `{{clientPropertyName}}` for consistency. If `clientPropertyName` already produces the correct casing, Swift and Dart should use it too.
- **Test coverage**: Tested in: all 12 (ClientTest verifies API group properties exist). No test checks the exact property name.
- **Note**: If fixed, verify that `clientPropertyName` produces correct camelCase for Swift and Dart conventions.

---

## Finding 7: Rust BaseAuthenticator is a concrete struct, not abstract

*Originally Finding 14.*

- **Languages affected**: Rust
- **What differs**: Rust's `BaseAuthenticator` is a concrete struct that stores a `host` field and returns empty auth headers. In all other languages, `BaseAuthenticator` is abstract (or a trait macro in Elixir) and requires subclasses to implement `getHost()` and `getAuthHeaders()`.
- **Verified**: Confirmed in `rust/auth/base_authenticator.mustache` (lines 12-22). The `Authenticator` trait does have default empty implementations for `query_params` and `cookie_params` (lines 17-23 of `authenticator.mustache`), which is correct. But `BaseAuthenticator` itself is concrete with empty `auth_headers()`.
- **What it should be**: Rust's `BaseAuthenticator` should not be directly usable as a no-op authenticator. It should either be removed (since Rust uses traits, not inheritance) or `auth_headers()` should not have a default implementation that returns empty headers.
- **Test coverage**: Tested in: all 12 (authenticator tests verify that concrete authenticators provide correct headers). No test checks that base authenticator cannot be used standalone.
- **Note**: If fixed, all 12 languages need a test verifying that the base authenticator pattern is consistent.

---

## Finding 8: Rust error hierarchy does not parse error body as JSON

*Originally Finding 16.*

- **Languages affected**: Rust
- **What differs**: Rust's `throw_api_error` function creates `ApiError` with the raw body string but does not attempt to parse it as JSON to populate an `error_body` field. All other 11 languages attempt `JSON.parse`/`json.loads`/`json_decode`/etc. to make structured error data available.
- **Verified**: Confirmed in `rust/base_api.mustache` (lines 232-265). The `ApiError::new` receives only `code`, `msg`, `body` (string), and `headers`. No JSON parsing of the body occurs. Compare with Go (`go/base_api.mustache` lines 223-225) which does `json.Unmarshal`.
- **What it should be**: Rust should attempt to parse the error response body as JSON and store the result in an `error_body` field, consistent with all other languages.
- **Test coverage**: Tested in: java, kotlin, node, python, ruby, php, go, csharp, swift, dart, elixir (base_api tests verify error body parsing). Missing: rust.
- **Note**: If fixed, all 12 languages need a test verifying that the error body is parsed as JSON when the response contains JSON.

---

## Finding 9: Dart and Swift error constructors use "code" parameter name

*Originally Finding 18.*

- **Languages affected**: Dart, Swift
- **What differs**: Dart uses `code:` as the named parameter for status code in error constructors. Swift uses `statusCode:`. Java/Kotlin/Node/Python/Ruby/PHP/C#/Go/Rust/Elixir use `statusCode` or `status_code` consistently.
- **Verified**: Confirmed in `dart/base_api.mustache` (line 243: `code: code`). Swift actually uses `statusCode:` which is consistent with the majority. Only Dart uses `code:`.
- **What it should be**: Dart should use `statusCode:` instead of `code:` to match all other languages. `statusCode` is clearer since `code` is ambiguous.
- **Test coverage**: Tested in: all 12 (error hierarchy tests). The parameter name itself is not explicitly tested.
- **Note**: If fixed, Dart error classes need updating. All 12 languages need consistent naming in tests.

---

## Finding 10: Java and PHP base_api expose getConfig() public method

*Originally Finding 20.*

- **Languages affected**: Java, PHP
- **What differs**: Java and PHP `BaseApi` classes have a public `getConfig()` / `getConfig(): Configuration` method. No other language's BaseApi exposes the configuration.
- **Verified**: Confirmed in `java/base_api.mustache` (line 105) and `php/base_api.mustache` (line 60).
- **What it should be**: Either all languages should expose configuration access from the BaseApi or none should. Since 10 languages do not expose it, removing it from Java and PHP is the simpler fix.
- **Test coverage**: No language tests for `getConfig()` specifically.
- **Note**: If a decision is made, apply uniformly across all 12 languages.

---

## Finding 11: TraceContext: Go, Rust, Swift, and Dart are no-ops while 8 others integrate with OpenTelemetry

*Originally Finding 23 (corrected).*

- **Languages affected**: Go, Rust, Swift, Dart (no-ops)
- **What differs**: Java, Kotlin, Python, Ruby, PHP, and Elixir actively try to use OpenTelemetry when available. C# uses `System.Diagnostics.Activity` (the .NET native tracing API). Node dynamically imports `@opentelemetry/api`. Go, Rust, Swift, and Dart are pure no-ops with comments telling users to inject context themselves.
- **Verified**: Confirmed by reading all 12 `trace_context_util.mustache` templates.
- **What it should be**: All languages should attempt to use the available tracing API. Go can check for `go.opentelemetry.io/otel` at compile time. Rust can use `cfg` feature flags for `opentelemetry`. Swift and Dart have limited tracing ecosystem support but could at least attempt dynamic lookup.
- **Test coverage**: Tested in: all 12 (TraceContextUtilTest). Tests verify the no-op case (no crash when OTel is not available). No tests verify actual trace context injection.
- **Note**: If fixed, all 12 languages need tests for both the no-op case and the OTel-present case.

---

## Finding 12: Node trace context injection is async

*Originally Finding 24.*

- **Languages affected**: Node
- **What differs**: Node's `injectTraceContext` is `async` and uses `await import(...)`. All other languages call it synchronously. OpenTelemetry context propagation is always a synchronous, in-process operation.
- **Verified**: Confirmed in `node/trace_context_util.mustache` (line 17: `export async function injectTraceContext`).
- **What it should be**: Trace context injection should be synchronous. The dynamic `import()` is the cause of the async requirement. Node should use a synchronous `require()` with try/catch instead.
- **Test coverage**: Tested in: all 12 (TraceContextUtilTest). No test specifically validates sync vs async behavior.
- **Note**: If fixed, all 12 languages should verify that trace context injection is synchronous.

---

## Finding 13: Swift and Dart DefaultApiClient do not honor followRedirects or maxRedirects from TransportOptions

*Originally Finding 35.*

- **Languages affected**: Swift, Dart
- **What differs**: Swift's `DefaultApiClient` uses `URLSession` which follows redirects by default, but does not read `TransportOptions.followRedirects` or `TransportOptions.maxRedirects`. Dart's `DefaultApiClient` similarly ignores these fields. Both languages define these fields in `TransportOptions` but the `DefaultApiClient` never applies them.
- **Verified**: Confirmed by reading `swift/default_api_client.mustache` and `dart/default_api_client.mustache` -- no references to `followRedirects` or `maxRedirects`.
- **What it should be**: Both should honor `TransportOptions.followRedirects` and `TransportOptions.maxRedirects`, consistent with Java, Kotlin, Python, Ruby, PHP, Go, C#, Rust, Node, and Elixir.
- **Test coverage**: Tested in: go (DefaultApiClientTest verifies redirect behavior). Missing tests in: java, kotlin, node, python, ruby, php, csharp, rust, swift, dart, elixir.
- **Note**: If fixed, all 12 languages need a DefaultApiClient test verifying that redirect settings from TransportOptions are applied.

---

## Finding 14: Java and Kotlin DefaultApiClient do not honor maxRedirects

*Originally Finding 36.*

- **Languages affected**: Java, Kotlin
- **What differs**: Java's `HttpClient.Builder.followRedirects()` only supports `NEVER`, `NORMAL`, and `ALWAYS` -- there is no way to set a maximum redirect count natively. Kotlin's OkHttp `followRedirects()` is also a boolean. Both read `TransportOptions.maxRedirects` but the `DefaultApiClient` never uses the value.
- **Verified**: Confirmed in `java/default_api_client.mustache` (lines 125-128) and `kotlin/default_api_client.mustache` (line 107). Neither reads `maxRedirects`.
- **What it should be**: Java can implement max redirects via a custom redirect policy. Kotlin/OkHttp can use an Interceptor that counts redirects.
- **Test coverage**: No language tests `maxRedirects` enforcement specifically.
- **Note**: If fixed, all 12 languages need a test verifying that `maxRedirects` is enforced when set.

---

## Finding 15: Swift, Dart, and Rust DefaultApiClient have no multipart/form-data support

*Originally Finding 37.*

- **Languages affected**: Swift, Dart, Rust
- **What differs**: The `DefaultApiClient` in Swift, Dart, and Rust has no multipart form data building logic. There is no `buildMultipartBody` or equivalent. All other languages (Java, Kotlin, Node, Python, Ruby, PHP, Go, C#, Elixir) include multipart body assembly.
- **Verified**: Confirmed by reading `swift/default_api_client.mustache`, `dart/default_api_client.mustache`, and `rust/default_api_client.mustache` -- no multipart handling code.
- **What it should be**: All three should implement multipart/form-data encoding for file upload operations: boundary generation, MIME part assembly with Content-Disposition headers, and proper binary/file handling.
- **Test coverage**: No language has unit tests for multipart body building in DefaultApiClient tests.
- **Note**: If fixed, all 12 languages need a DefaultApiClient test verifying multipart body construction.

---

## Finding 16: Dart DefaultApiClient does not implement proxy, TLS verification, or CA cert handling

*Originally Finding 38.*

- **Languages affected**: Dart
- **What differs**: Dart's `DefaultApiClient` ignores `TransportOptions.verifySSL`, `TransportOptions.caCertPath`, and `TransportOptions.proxy`. These fields exist in `TransportOptions` but the client never reads them.
- **Verified**: Confirmed in `dart/default_api_client.mustache` -- no references to `verifySSL`, `caCertPath`, or `proxy`.
- **What it should be**: At minimum, Dart should apply these settings when running on `dart:io` platforms (via `IOClient`), or provide a factory method that creates an appropriately configured client.
- **Test coverage**: No language has unit tests for proxy/TLS/CA cert configuration in DefaultApiClient.
- **Note**: If fixed, all 12 languages need tests verifying that proxy, TLS, and CA cert settings from TransportOptions are applied.

---

## Finding 17: PHP ObjectSerializer.stringify() checks null after other types

*Originally Finding 42.*

- **Languages affected**: PHP
- **What differs**: In PHP's `stringify()`, the null check (`$value === null`) comes after the DateTime, bool, string, and int/float checks. In all other 11 languages, null is checked first, returning an empty string immediately.
- **Verified**: Confirmed in `php/object_serializer.mustache` (lines 106-130). The null check is at line 124, after DateTime (108), bool (112), string (116), and int/float (120).
- **What it should be**: The null check should come first for consistency. While PHP's type system prevents null from matching `is_bool` or `is_string`, the ordering diverges from the pattern used everywhere else and makes the code harder to compare across languages.
- **Test coverage**: Tested in: all 12 (ObjectSerializerTest `stringify` tests cover null input).
- **Note**: If fixed, verify all 12 languages have `stringify(null)` returning empty string, tested consistently.

---

## Finding 18: Kotlin ObjectSerializer encodeDefaults = false is over-aggressive

*Originally Finding 45.*

- **Languages affected**: Kotlin
- **What differs**: Kotlin's JSON config has `encodeDefaults = false` (skip properties at default values). Java's Jackson config uses `JsonInclude.Include.NON_NULL` (skip null values only). These are subtly different: Java will serialize a field explicitly set to its default (e.g., `0` for int, `false` for boolean) but skip `null`. Kotlin will skip both defaults AND nulls, silently dropping explicitly-set default values.
- **Verified**: Confirmed in `kotlin/object_serializer.mustache` (line 158: `encodeDefaults = false`).
- **What it should be**: Kotlin should use `encodeDefaults = true` to match Java's `NON_NULL` behavior. A field explicitly set to its default value (e.g., `count = 0`) should be serialized. The current setting can silently lose data.
- **Test coverage**: Tested in: java, kotlin (ObjectSerializerTest). Missing: specific test that serializing a model with a field set to its default value includes that field in the JSON output.
- **Note**: If fixed, all 12 languages need a test verifying that a field explicitly set to its type's default value is included in serialized output.

---

## Summary of removed findings

| Finding | Reason removed |
|---------|---------------|
| 1 | Inaccurate -- both Go and Rust do have deserialization in their result methods |
| 4 | Duplicate of Finding 1 |
| 6 | Acknowledged as acceptable pragmatic choice |
| 9 | Idiomatic Kotlin (reified generics) |
| 10 | Idiomatic C# (IDisposable) |
| 12 | Idiomatic Go (adapter for circular imports) |
| 13 | Minor ergonomic addition, not functional |
| 17 | Idiomatic Go (struct embedding) |
| 19 | Idiomatic Elixir (keyword args) |
| 21 | Minor file organization difference |
| 22 | Already consistent |
| 25 | Functionally equivalent -- all languages handle collection formats |
| 26 | Functionally equivalent -- all languages route through stringify |
| 27 | Idiomatic per language (return-then-throw vs direct throw) |
| 28 | PHP trim() is marginally more robust, acceptable |
| 29 | Go does expose ErrorBody; TypedErrorBody is an addition |
| 30 | Idiomatic Dart (callback for generic deserialization) |
| 31 | Kotlin instance methods are functionally equivalent to static |
| 32 | Rust delegates to serde which handles dates via chrono |
| 33 | Theoretical gap with no practical impact (no models use java.util.Date) |
| 34 | Language limitation (JS Date is UTC-only) |
| 39 | Go SendMultipartRequest is an addition, not a divergence |
| 40 | Design choice; both approaches work for oneOf/anyOf |
| 41 | Language-specific file handling (SplFileObject) |
| 43 | Design choice on error vs null for no-match; all approaches are valid |
| 44 | Language limitations drive datetime format differences; all produce valid ISO 8601 |
| 15 | Rust Authenticator trait already has correct defaults for query_params and cookie_params |

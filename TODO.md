# TODO — Cross-Language Parity Fixes

All 18 items approved for fixing. Each fix must include tests across all 12 languages where noted.

---

## 1. Content-type defaulting location (Go, Rust, Swift, Dart)

**Action**: Remove content-type defaulting from base_api in Go, Rust, Swift, Dart. Let headerSelector handle it, matching the other 8 languages.
**Tests**: All 12 languages — verify empty content-type defaults to `application/json`.

---

## 2. Header selector cherry-picking (Go, Rust, Swift, Dart)

**Action**: Use the full headers map from headerSelector instead of cherry-picking only Accept and Content-Type. Match Java/Kotlin/Node/Python/Ruby/PHP/C#/Elixir.
**Tests**: All 12 languages — verify all headers from selector flow through to the request.

---

## 3. Rust serialize_body no-op

**Action**: Implement actual body serialization in Rust (JSON, form-urlencoded, text/plain). Currently a passthrough that expects pre-serialized bytes.
**Tests**: All 12 languages — verify body serialization for each content type (JSON, text/plain, form-urlencoded, binary).

---

## 4. Boolean query param normalization

**Action**: All languages must normalize booleans to lowercase `true`/`false` in query strings. Currently Python, PHP, C# do it explicitly; others rely on language-default toString.
**Tests**: All 12 languages — verify boolean query parameter renders as lowercase `true`/`false`.

---

## 5. Use isJsonMime() for response content-type checking

**Action**: All 11 non-Node languages should use `isJsonMime()` from HeaderSelector (regex-based) instead of inline `contains("application/json")` checks. Handles edge cases like `application/vnd.api+json`.
**Tests**: All 12 languages — verify `application/vnd.api+json` response is deserialized as JSON.

---

## 6. Swift/Dart client templates use lambda.camelcase instead of clientPropertyName

**Action**: Change Swift and Dart client.mustache to use `{{clientPropertyName}}` instead of `{{#lambda.camelcase}}{{classname}}{{/lambda.camelcase}}`.
**Tests**: No additional tests needed (ClientTest already covers).

---

## 7. Rust BaseAuthenticator is concrete, not abstract

**Action**: Make Rust's BaseAuthenticator not directly usable as a no-op authenticator. Either remove it or remove the default empty `auth_headers()` implementation.
**Tests**: No additional tests needed.

---

## 8. Rust error body not parsed as JSON

**Action**: Add JSON parsing of error response body in Rust's `throw_api_error`, storing result in `error_body` field. Match all other 11 languages.
**Tests**: All 12 languages — verify error body is parsed as JSON when response contains JSON.

---

## 9. Dart error constructor uses "code" instead of "statusCode"

**Action**: Rename Dart error constructor parameter from `code` to `statusCode` to match all other languages.
**Tests**: No additional tests needed (error hierarchy tests already cover).

---

## 10. Remove getConfig() from Java and PHP base_api

**Action**: Remove public `getConfig()` from Java and PHP BaseApi. 10 other languages don't expose it.
**Tests**: No additional tests needed.

---

## 11. Add OpenTelemetry trace context to Go, Rust, Swift, Dart

**Action**: Add optional OTel integration to Go (`go.opentelemetry.io/otel`), Rust (feature flag), Swift, and Dart. Check if OTel exists at runtime, matching the pattern used by the other 8 languages.
**Tests**: All 12 languages — test both no-op case and OTel-present case.

---

## 12. Node trace context injection: async → sync

**Action**: Change Node's `injectTraceContext` from `async` with `await import()` to synchronous with `try { require() } catch {}`. Trace context injection is in-process, no I/O involved.
**Tests**: No additional tests needed.

---

## 13. Swift/Dart DefaultApiClient redirect handling

**Action**: Implement `followRedirects` and `maxRedirects` from TransportOptions in Swift and Dart DefaultApiClient.
**Tests**: All 12 languages — verify redirect settings from TransportOptions are applied.

---

## 14. Java/Kotlin maxRedirects enforcement

**Action**: Implement maxRedirects via custom redirect policy (Java HttpClient) and OkHttp Interceptor (Kotlin).
**Tests**: All 12 languages — verify maxRedirects is enforced.

---

## 15. Multipart/form-data support for Swift, Dart, Rust

**Action**: Implement multipart body assembly in Swift, Dart, and Rust DefaultApiClient: boundary generation, MIME part assembly, Content-Disposition headers, binary/file handling.
**Tests**: All 12 languages — verify multipart body construction.

---

## 16. Dart proxy/TLS/CA cert handling

**Action**: Implement proxy, TLS verification, and CA cert support in Dart DefaultApiClient using `dart:io` IOClient. Skip if it requires Testcontainers for testing.
**Tests**: Skip if needs Docker/Testcontainers.

---

## 17. PHP stringify null check ordering

**Action**: Move null check to the top of PHP's `stringify()` method, before DateTime/bool/string/number checks. Match all other 11 languages.
**Tests**: No additional tests needed (ObjectSerializerTest already covers null).

---

## 18. Kotlin encodeDefaults = false → true

**Action**: Change Kotlin's `encodeDefaults = false` to `encodeDefaults = true` to match Java's `NON_NULL` behavior. Fields explicitly set to default values (e.g., `count = 0`) must be serialized.
**Tests**: All 12 languages — verify field set to default value is included in serialized output.

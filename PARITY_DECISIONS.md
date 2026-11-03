# Parity decisions (cross-cutting findings)

One entry per root cause. `canonical` is the behaviour all 12 SDKs must end up with; `per-language` lists what each SDK has to change. Source: the ledger in `PARITY_FINDINGS.json`. A decision is a proposal until its ledger ids are marked fixed; override any of them by editing this file or telling the maintainer.

Themes: oauth-oidc 12, errors-messages-api-shape 17, content-negotiation-and-bodies 10, codegen-escaping-and-names 6, query-form-path-encoding 8, models-validation-enums-defaults 16, redirect-proxy-tls-timeout 10, serialization-wire-edge-cases 15.

| # | Theme | Ledger ids | Still true | Effort | Wire-visible | Needs user | Title |
|---|---|---|---|---|---|---|---|
| D01 | oauth-oidc | F016 F230 | yes | L | yes |  | OIDC rediscovery refreshes the discovery document only; the exchanged session survives it, and discovery is si |
| D02 | oauth-oidc | F017 | yes | M | yes |  | Refresh grant carries client authentication (RFC 6749 sec 6 / 3.2.1) and the fallback re-runs the real grant |
| D03 | oauth-oidc | F119 | yes | M | yes |  | exchangeCode always POSTs the authorization_code grant and replaces the session; it never goes through the tok |
| D04 | oauth-oidc | F018 F288 | partly | S | yes |  | Per-scheme OAuth wrappers pass the spec's declared scopes, in spec declaration order |
| D05 | oauth-oidc | F113 | yes | S | yes |  | OpenID Connect authenticators always request the openid scope |
| D06 | oauth-oidc | F112 | yes | S | yes |  | Implicit flow treats an empty access token as unset |
| D07 | oauth-oidc | F115 F266 F289 | yes | M | no |  | Token-response and OIDC-discovery error messages: one wording set, no parser detail or cause on token errors |
| D08 | oauth-oidc | F116 F294 | partly | M | no |  | expires_in and discovery max-age: one decimal grammar for strings and a one-year ceiling applied before any da |
| D09 | oauth-oidc | F286 | yes | S | no |  | Auth-code precondition messages: one sentence per condition |
| D10 | oauth-oidc | F292 | yes | M | yes |  | Token-request body and authorize-URL parameters are emitted in one fixed order |
| D11 | oauth-oidc | F293 | yes | S | yes |  | An empty `state` is treated as absent when building authorize URLs |
| D12 | oauth-oidc | F231 F290 | partly | S | no |  | Synchronous auth-header accessor on token-based OAuth flows: fail loudly, never an empty map |
| D13 | errors-messages-api-shape | F099 | yes | M | no |  | An ApiError for a response that arrived but was unusable (decompress failure, redirect refusals) carries the r |
| D14 | errors-messages-api-shape | F100 | yes | M | no |  | Error-contract test matrix: lock OAuth2ServerError message, deep-body cap, exact rendering, BOM and scalar bod |
| D15 | errors-messages-api-shape | F245 | yes | M | no |  | Serializer error messages: one serialize message, one deserialize message, one depth-cap message |
| D16 | errors-messages-api-shape | F260 | yes | M | no |  | Redirect-refusal messages: one wording for scheme, https->http replay and too-many |
| D17 | errors-messages-api-shape | F267 | yes | M | no |  | Rendered 'Response headers:' line: one syntax (compact JSON, keys sorted) |
| D18 | errors-messages-api-shape | F268 | yes | S | no |  | Strip a leading BOM inside the shared JSON parse helper so the error-body path matches the success path |
| D19 | errors-messages-api-shape | F274 F275 | yes | M | no |  | Credential emptiness guard: explicit ASCII whitespace set, and strip the 'Bearer ' prefix before the emptiness |
| D20 | errors-messages-api-shape | F298 F340 | yes | S | no |  | List rendering inside messages: `[a, b, c]` for server-variable allowed values and declared content types |
| D21 | errors-messages-api-shape | F299 | yes | M | yes |  | User-Agent and X-Request-ID injection checks are case-insensitive; the caller's header always wins |
| D22 | errors-messages-api-shape | F300 | yes | S | yes |  | Default User-Agent product token is the SDK's published package name |
| D23 | errors-messages-api-shape | F321 | yes | M | no |  | Every model has a field-dumping string form that renders containers element-wise and includes additionalProper |
| D24 | errors-messages-api-shape | F322 | yes | L | no |  | oneOf/anyOf union wrappers get the same value equality as plain models |
| D25 | errors-messages-api-shape | F323 | no | S | no |  | Null required Options object reports the canonical missing-parameter message |
| D26 | errors-messages-api-shape | F324 | partly | S | no |  | An optional request body can be omitted: add the missing java no-argument overloads |
| D27 | errors-messages-api-shape | F325 | yes | S | no |  | ApiResult.headers is a defensive, read-only copy in every language that can express it |
| D28 | errors-messages-api-shape | F392 | yes | S | no |  | Required Options parameters are named by the caller-facing identifier in the missing-parameter message |
| D29 | errors-messages-api-shape | F409 | yes | S | no |  | Use-after-close: one message, built-in state error, and doc comments that match |
| D30 | content-negotiation-and-bodies | F033 F085 F150 | yes | L | no |  | Binary (format: binary) response: always raw bytes, never JSON-parsed, one rule for JSON / absent / text / bin |
| D31 | content-negotiation-and-bodies | F150 F151 | yes | M | no |  | format: byte response (getPetAvatarThumbnail): JSON by default, one strict decoder, every failure is a Seriali |
| D32 | content-negotiation-and-bodies | F149 | yes | M | no |  | Non-JSON success Content-Type on a non-binary return: String gets the text, everything else is a Serialization |
| D33 | content-negotiation-and-bodies | F155 | partly | S | no |  | rust and go text/plain on a String-returning operation |
| D34 | content-negotiation-and-bodies | F153 | no | S | no |  | java BaseApi mirror treated an empty Content-Type as base64 while the transport treats it as text |
| D35 | content-negotiation-and-bodies | F035 F084 | yes | M | yes |  | Binary request body with a non-binary Content-Type selected (setPetAvatar + application/json): send the caller |
| D36 | content-negotiation-and-bodies | F214 | yes | M | yes |  | Multipart scalar parts: every primitive is a plain text part with the canonical stringify form; only objects/m |
| D37 | content-negotiation-and-bodies | F254 | no | S | no |  | Multipart scalar stringification: python bool, php bool, swift Int64/Float |
| D38 | content-negotiation-and-bodies | F385 | partly | S | yes |  | Multipart JSON part headers: csharp/dart charset and header order, kotlin per-part Content-Length |
| D39 | content-negotiation-and-bodies | F216 | partly | S | yes |  | x-www-form-urlencoded values: scalars use the canonical stringify form; nested objects/maps are rejected inste |
| D40 | codegen-escaping-and-names | F045 F180 F362 F176 F048 | yes | L | yes |  | One value-preserving string-literal escaper per language for every spec string written into a code literal (de |
| D41 | codegen-escaping-and-names | F178 F181 F177 | yes | M | no |  | Doc prose and package-manifest descriptions: one normalisation for comments, one escaper per manifest format |
| D42 | codegen-escaping-and-names | F122 F145 | yes | M | no |  | Per-operation server codegen: one type per variable per operation, unique variant names |
| D43 | codegen-escaping-and-names | F172 F363 | yes | M | no |  | Identifiers: a digit-leading or empty sanitised name gets a deterministic legal fallback, never an escape pref |
| D44 | codegen-escaping-and-names | F309 F364 | partly | S | no |  | Reserved-word escaping applies exactly to names the language cannot use (add java literals, narrow php/ruby) |
| D45 | codegen-escaping-and-names | F173 | yes | M | no |  | Model names that collide with language or SDK types get the Model prefix in all 12, from a dedicated reserved- |
| D46 | query-form-path-encoding | F044 F047 | yes | M | yes |  | String enum literals: escape the quote char (never strip it), escape backslash and interpolation sigils, in al |
| D47 | query-form-path-encoding | F063 | yes | L | yes |  | Object-valued (model or free-form map) query/header parameters: convert to a wire-keyed map via the SDK Object |
| D48 | query-form-path-encoding | F064 | yes | M | yes |  | Reject a path parameter value of exactly '.' or '..' before dispatch, in all 12 |
| D49 | query-form-path-encoding | F066 F387 | yes | M | yes |  | allowReserved: leave literal only the RFC 3986 query-legal reserved set; percent-encode '#', '[' and ']' in al |
| D50 | query-form-path-encoding | F068 | yes | M | yes |  | Content-based query parameters (content: application/json) go through the SDK ObjectSerializer.serialize in al |
| D51 | query-form-path-encoding | F069 | yes | M | yes |  | format: byte parameters (query, header, path, form) are base64-encoded, in all 12 |
| D52 | query-form-path-encoding | F086 F197 F199 F239 F341 | partly | S | yes |  | Query and form-body percent-encoding keeps exactly A-Za-z0-9-._~ literal (%-encodes * ! ' ( )); only dart stil |
| D53 | query-form-path-encoding | F087 | yes | L | yes |  | Query, form-body and multipart part order is the spec declaration order in all 12 (ordered containers everywhe |
| D54 | models-validation-enums-defaults | F006 F076 F205 F213 F310 | yes | L | yes |  | Integer decode: reject fractional, quoted and out-of-range values for int32/int64 everywhere, including contai |
| D55 | models-validation-enums-defaults | F056 F137 | yes | S | no |  | Schema validation keywords (pattern, min/max length, min/max, multipleOf, min/maxItems) are enforced by NO SDK |
| D56 | models-validation-enums-defaults | F025 F055 | yes | S | yes |  | readOnly/writeOnly have no wire effect in any SDK; java's inverted Jackson access modes are removed |
| D57 | models-validation-enums-defaults | F080 | yes | M | yes |  | date-time decode accepts only RFC 3339 (offset required) and never consults the host clock or time zone |
| D58 | models-validation-enums-defaults | F081 | yes | M | yes |  | format: byte base64 decode is strict RFC 4648 standard alphabet with required padding and no whitespace in all |
| D59 | models-validation-enums-defaults | F129 F170 F320 | yes | M | no |  | A required property that carries a default (synthetic discriminator default) is filled with that default when  |
| D60 | models-validation-enums-defaults | F131 F171 | yes | M | yes |  | A default on a $ref'd enum (property-level sibling or on the enum component) becomes the typed enum member in  |
| D61 | models-validation-enums-defaults | F174 | partly | M | yes |  | Numeric default literals render as an exact, compiling literal of the target type in every language |
| D62 | models-validation-enums-defaults | F132 | yes | M | yes |  | format: uri keeps each language's type surface but has one wire rule: absolute RFC 3986 URI, any scheme, byte- |
| D63 | models-validation-enums-defaults | F138 | yes | M | yes |  | additionalProperties key that collides with a declared property: the declared value wins and the key is writte |
| D64 | models-validation-enums-defaults | F139 | yes | M | no |  | Non-discriminated oneOf/anyOf variants are tried in declaration order in all 12 |
| D65 | models-validation-enums-defaults | F206 | yes | M | yes |  | A null element in an array or map of models is rejected unless the element schema is nullable |
| D66 | models-validation-enums-defaults | F215 | yes | L | yes |  | Required + nullable: the key must be present, null is a legal value, and null is written as null |
| D67 | models-validation-enums-defaults | F217 | yes | M | yes |  | format: uuid is written lowercase hyphenated and validated in both directions in all 12 |
| D68 | models-validation-enums-defaults | F227 | yes | L | no | YES | A model missing required properties cannot be built or sent: java, node and rust must reject it before dispatc |
| D69 | models-validation-enums-defaults | F319 | yes | M | yes |  | uniqueItems arrays of scalars go on the wire sorted, so every SDK emits identical bytes on every run |
| D70 | redirect-proxy-tls-timeout | F088 F093 | no | S | no |  | 3xx without Location, and the HTTPS->HTTP body-replay guard, already uniform |
| D71 | redirect-proxy-tls-timeout | F089 | yes | M | yes |  | An unusable (unparseable) Location behaves exactly like a missing Location: return the 3xx |
| D72 | redirect-proxy-tls-timeout | F020 | partly | S | yes |  | caCertPath is a pin: it replaces the system trust store (and hostname checks still apply) |
| D73 | redirect-proxy-tls-timeout | F295 | yes | S | no |  | Validate caCertPath whenever it is set, even with verifySsl=false |
| D74 | redirect-proxy-tls-timeout | F090 | yes | M | yes |  | Ambient proxy settings (env / JVM properties / system settings) are ignored: no proxy unless TransportOptions. |
| D75 | redirect-proxy-tls-timeout | F091 | yes | M | yes |  | The SDK's own Proxy-Authorization survives redirect hops (strip removes only caller credentials) |
| D76 | redirect-proxy-tls-timeout | F094 F407 | yes | L | yes |  | timeout is one end-to-end budget for the whole call: all hops plus the body read |
| D77 | redirect-proxy-tls-timeout | F121 | yes | M | no |  | timeout must be a positive number of milliseconds or null; reject <= 0 at configuration |
| D78 | redirect-proxy-tls-timeout | F066 | partly | S | yes |  | allowReserved: encoder output is the contract; transport re-encoding of [ ] and ' is a documented FORCED excep |
| D79 | redirect-proxy-tls-timeout | F297 | yes | M | no |  | One message grammar for proxy and CA configuration errors, credentials redacted |
| D80 | serialization-wire-edge-cases | F054 | yes | L | yes |  | Content-encoded parameters (content: instead of schema:) are serialized by the SDK ObjectSerializer in every l |
| D81 | serialization-wire-edge-cases | F071 | yes | M | yes |  | format: time is RFC 3339 partial-time HH:mm:ss, plus .SSS only when the millisecond part is non-zero, in param |
| D82 | serialization-wire-edge-cases | F072 | yes | M | yes |  | uniqueItems array (Set) parameters expand exactly like array parameters |
| D83 | serialization-wire-edge-cases | F075 | yes | L | yes |  | Bare type: number precision: exact where the language can, and AGENTS/README state precisely where it is not |
| D84 | serialization-wire-edge-cases | F082 F198 F243 | partly | M | yes |  | JSON strings in request bodies are written literally (/, <, >, &, non-ASCII, U+2028/2029) in all 12, with docu |
| D85 | serialization-wire-edge-cases | F244 | partly | M | yes |  | Request-body object keys follow declared property order; swift is a documented forced sorted order |
| D86 | serialization-wire-edge-cases | F246 | yes | S | yes |  | format: double body text is not canonicalised: JSON number equality, not byte equality, is the contract |
| D87 | serialization-wire-edge-cases | F078 F242 | partly | S | no |  | swift stays a documented FORCED exception for the 512-level decoder cap and first-wins duplicate keys (php and |
| D88 | serialization-wire-edge-cases | F249 | partly | S | no |  | Protobuf durations beyond ~292 years: rust fixed, go a documented FORCED exception |
| D89 | serialization-wire-edge-cases | F252 | partly | M | yes |  | Multipart parts carry only Content-Disposition (+ Content-Type where meaningful): no Content-Transfer-Encoding |
| D90 | serialization-wire-edge-cases | F253 | yes | M | yes |  | Multipart filename directive: one rule (go/php/rust form) in all 12 |
| D91 | serialization-wire-edge-cases | F258 | partly | S | no |  | Declared-charset decoding: documented minimum set plus lenient UTF-8 fallback that always yields a valid strin |
| D92 | serialization-wire-edge-cases | F261 | yes | L | no |  | Response headers returned to the caller drop Content-Encoding and Content-Length whenever the body was decoded |
| D93 | serialization-wire-edge-cases | F241 | yes | S | no |  | dart findPetsBornOn comment says date is a String, but it is a DateTime passed through stringifyDate |
| D94 | serialization-wire-edge-cases | F073 F074 F077 F079 F083 F248 F250 F251 F255 F256 F257 F259 | no | S | no |  | Verified fixed in the current goldens (no action; locks exist) |

## D01 OIDC rediscovery refreshes the discovery document only; the exchanged session survives it, and discovery is single-flight

- Ledger ids: F016 F230 | Theme: oauth-oidc | Still true: **yes** | Effort: L | Wire-visible: True | Needs user: False

**Current state.** 11 of 12 still lose the session. java OpenIdConnectAuthenticator.java:116/126 builds a new OAuth2AuthorizationCodeAuthenticator (new OAuth2TokenManager, tokenExchanged=false) once discoveryExpiry passes; getDelegate is synchronized (single-flight). kotlin OpenIdConnectAuthenticator.kt:~120 same, no lock. csharp OpenIdConnectAuthenticator.cs:108 same, no lock. php OpenIdConnectAuthenticator.php:163 same, no lock. python openid_connect_authenticator.py:154 same, no lock. ruby openid_connect_authenticator.rb:131 same, no lock. node openid-connect-authenticator.ts:150 same, no in-flight promise. dart openid_connect_authenticator.dart:158 same, no in-flight future. go openid_connect_authenticator.go:210 same, mutex held across the fetch. rust openid_connect_authenticator.rs:242 swaps in a fresh Arc delegate (fresh manager, token_exchanged=false); only guards a concurrent install. swift OpenIdConnectAuthenticator.swift:180 same, lock only around the state write, not around the fetch. elixir openid_connect_authenticator.ex:265 keep_token_manager/2 re-uses the previous delegate's token manager (tokens + mark_exchanged flag) across rediscovery, i.e. already correct for state (test openid_connect_authenticator_test.exs:343), fetch not serialised. Effect in the 11: after max-age (86400 s default; max-age=0 means the very first call after exchangeCode) getAuthHeaders throws 'Must call exchangeCode() before making API requests' and the user must re-authorise. No language tests max-age/rediscovery except elixir.

**Canonical behaviour.** The session (access token, refresh token, 'exchanged' flag) is owned by the OAuth2TokenManager, which outlives discovery. (1) Move the 'exchanged' flag into OAuth2TokenManager (markExchanged/isExchanged, exactly as elixir already has it); OAuth2AuthorizationCodeAuthenticator.getAuthHeaders reads it from the manager. (2) Give OAuth2AuthorizationCodeAuthenticator an internal constructor/overload that takes an existing token manager; on rediscovery the OIDC authenticator builds the replacement delegate around the previous delegate's manager (new authorization/token endpoints, same session). (3) If rediscovery fails (non-2xx, transport, malformed document) the old delegate and its expiry are left untouched, the error propagates and the next call retries (no stale serving, no silent swallow). (4) Discovery fetch is single-flight per authenticator using the language's idiom (lock / in-flight future), re-checking expiry inside it. PHP is the FORCED exception for (4): no in-process concurrency, and the token manager already documents cross-process locking as out of scope. The max-age value is clamped per the expires_in/max-age decision. Standing AGENTS.md rules are not touched; the 'RFC 8414 default 86400' behaviour stays.

**Why.** Criteria (c) and (b): the session must never be silently discarded by a cache refresh, and elixir (plus java/go for locking) already show the shape is feasible without a new dependency. RFC 8414/OIDC Discovery only describe caching of metadata, never invalidating client authorization state. Rust/kotlin/csharp/swift/node/dart/python/ruby need only stdlib primitives (Mutex, SemaphoreSlim, Task/Future, threading.Lock).

**Rejected.** (a) Mutate endpoints in place on the existing delegate: needs mutable endpoint fields in go/rust/swift/elixir (immutable or Arc'd types), wider surface than sharing the manager. (b) Ignore max-age once a code was exchanged: endpoints could rotate, and it silently changes caching semantics. (c) Serve the stale delegate when rediscovery fails: hides OP outages and security-relevant endpoint changes. (d) Status quo (drop session, force re-login): rejected, it is the bug. (e) Leave single-flight out because state-preserving rebuild makes duplicates harmless: rejected, it still fires duplicate discovery GETs and last-writer races on the first discovery; it is cheap with each language's own primitive.

**Per language.**

- java: Add exchanged flag to OAuth2TokenManager (markExchanged/isExchanged); add package-private constructor taking an OAuth2TokenManager to OAuth2AuthorizationCodeAuthenticator; in getDelegate() reuse delegate.tokenManager when rebuilding. Single-flight already present (synchronized). (src/main/resources/templates/java/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- kotlin: Same as java (internal constructor taking the manager; flag in manager). Wrap resolveDelegate in a kotlinx.coroutines.sync.Mutex (already imported by the token manager) with an expiry re-check inside the lock. (src/main/resources/templates/kotlin/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- csharp: Same; internal ctor overload; flag in OAuth2TokenManager; SemaphoreSlim(1,1) around GetDelegateAsync with re-check inside (await WaitAsync/Release in finally). (src/main/resources/templates/csharp/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- php: Same state carry-over (second constructor argument ?OAuth2TokenManager, flag in manager). No lock: FORCED, single-threaded request model. (src/main/resources/templates/php/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- python: Same; optional token_manager kwarg on OAuth2AuthorizationCodeAuthenticator.__init__; flag in manager; threading.Lock around _get_delegate with expiry re-check inside. (src/main/resources/templates/python/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- ruby: Same; token_manager: keyword on the constructor; flag in manager; Mutex around resolve_delegate with re-check inside. (src/main/resources/templates/ruby/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- node: Same; optional tokenManager constructor arg; flag in manager; keep a private in-flight Promise so concurrent callers await one discovery (clear it in finally). (src/main/resources/templates/node/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- dart: Same; optional tokenManager named arg; flag in manager; keep an in-flight Future/Completer so concurrent callers share one discovery. (src/main/resources/templates/dart/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- go: Add an unexported constructor in the same package that takes the previous delegate's *OAuth2TokenManager; move tokenExchanged (atomic.Bool) into OAuth2TokenManager; resolveDelegate (mutex already held across fetch) builds the new delegate around a.delegate.tokenManager. (src/main/resources/templates/go/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- rust: Make token_manager an Arc<OAuth2TokenManager> and add pub(crate) with_token_manager(...); move token_exchanged into the manager; in resolve_delegate build the replacement from the existing delegate's manager Arc. Replace the 'keep concurrent delegate' workaround by a tokio::sync::Mutex discovery lock held across the fetch (expiry re-check inside). (src/main/resources/templates/rust/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- swift: Add an internal init taking the existing OAuth2TokenManager; move storedTokenExchanged into the manager (lock-protected); serialize discovery with a lock-guarded in-flight Task<OAuth2AuthorizationCodeAuthenticator, Error> (an NSLock cannot be held across await). (src/main/resources/templates/swift/auth/oauth/{openid_connect_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- elixir: already canonical

**Tests.** Per-language OpenIdConnectAuthenticator test (generated: java OpenIdConnectAuthenticatorTest.java, kotlin ...Test.kt, csharp Test/OpenIdConnectAuthenticatorTest.cs, php tests/OpenIdConnectAuthenticatorTest.php, python test/test_openid_connect_authenticator.py, ruby test/openid_connect_authenticator_test.rb, node test/openid-connect-authenticator.test.ts, dart test/openid_connect_authenticator_test.dart, go test/openid_connect_authenticator_test.go, rust tests/openid_connect_authenticator_test.rs, swift OpenIdConnectAuthenticatorTests.swift, elixir test/openid_connect_authenticator_test.exs; templates under src/main/resources/templates/<lang>/test/). Add (1) 'rediscovery after max-age expiry keeps the exchanged session': discovery fake answering Cache-Control: max-age=0, exchangeCode, then getAuthHeaders returns 'Bearer <token>' with no token POST for the already valid token, discovery GET count is 2, and a second discovery document with a different authorization_endpoint is used by buildAuthorizationUrl (elixir :343 is the model; rust already has enqueue_cached headers helper at openid_connect_authenticator_test.rs:80). (2) 'failed rediscovery keeps the old delegate': second discovery 500 -> typed ApiError, third call with a good document succeeds with the same token. (3) concurrency row in every language except php: N concurrent getAuthHeaders on a cold authenticator issue exactly one discovery GET (gate the fake). No petstore spec change needed.

**Risk.** Touches the auth-code authenticator, its token manager and the OIDC wrapper in 11 languages at once; the exchanged flag moving into the manager changes constructor/tests of every auth-code test. Rust Arc/Mutex change compiles only on CI (amd64).

## D02 Refresh grant carries client authentication (RFC 6749 sec 6 / 3.2.1) and the fallback re-runs the real grant

- Ledger ids: F017 | Theme: oauth-oidc | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Uniform in all 12. Password authenticator (java OAuth2PasswordAuthenticator.java:159-170, kotlin .kt:83-92, csharp .cs:98-109, php .php:138-143, python .py:110-117, ruby .rb:98-105, node .ts:131-136, dart .dart:96-102, go .go:103-108, rust .rs:157-161, swift .swift:90-96, elixir .ex:136-141) builds {grant_type=refresh_token, refresh_token} with NO client_id/client_secret once a refresh token exists, with a code comment that misreads RFC 6749 sec 6 ('carries only grant_type and refresh_token'). In BASIC mode the Authorization header still rides along via extraHeaders, in BODY mode the refresh is unauthenticated. The auth-code authenticator (java :168-170, rust :213-218, elixir, ...) always sends the bare refresh params (it has no Basic option). The token managers (java OAuth2TokenManager.java:101-106, go :108-117, rust :150-158) copy client_id/client_secret only from the params they are handed, so nothing is added, and on failure 'fall back to the original grant' re-POSTs the identical bare refresh request (two identical failing requests; the password flow can never re-run the password grant). Manager sends the refresh to the same tokenUrl it was given (the authenticator pre-selects refreshUrl).

**Canonical behaviour.** RFC 6749 sec 6: a client that authenticated at the token endpoint authenticates the refresh the same way (sec 3.2.1). (1) Token managers gain an optional refreshUrl argument on getAccessToken (default = tokenUrl). (2) Password authenticator no longer branches on refresh-token presence: it always passes the password-grant params (client_id/client_secret only in BODY mode, Basic header via extraHeaders otherwise) with tokenUrl and refreshUrl. The manager, when it holds a refresh token and params.grant_type != refresh_token, POSTs {grant_type=refresh_token, refresh_token, client_id, client_secret (copied from params, i.e. BODY mode only)} plus the same extraHeaders to refreshUrl; on any non-cancellation failure it falls back to POSTing the original params to tokenUrl (re-runs the password grant, as the client-credentials flow already does). (3) Auth-code authenticator passes {grant_type=refresh_token, refresh_token, client_id, client_secret} with the refresh endpoint as the url; when params.grant_type is already refresh_token the manager performs exactly one POST (no internal refresh attempt, no duplicate fallback), so a rejected refresh surfaces as OAuth2ServerError once. No scope on refresh (optional per sec 6).

**Why.** (a) RFC 6749 sec 6 literally says a confidential client or a client issued credentials MUST authenticate per sec 3.2.1; (c) the current behaviour makes every flow with a refresh_token break permanently at first expiry against such OPs with no recovery path (invalidateAccessToken never clears the refresh token); (d) only parameter plumbing, no new dependency.

**Rejected.** (a) Only add client_id/secret to the refresh params and keep the duplicate fallback: fixes the first half of the finding but leaves password flows unable to re-login and sends a duplicate failing request. (b) Add a ClientAuthMethod to auth-code: scope creep, the spec/generator has no such option for that flow. (c) Leave as is because 'matches the other SDKs': it is uniformly wrong against Keycloak/Google/Auth0 confidential clients, and uniform bugs are not a parity argument.

**Per language.**

- java: OAuth2TokenManager.getAccessToken(tokenUrl, params, extraHeaders, @Nullable refreshUrl) overload; skip internal refresh when params grant_type is refresh_token; OAuth2PasswordAuthenticator always passes password params + tokenUrl + refreshUrl, deletes the refresh branch and misleading comment; OAuth2AuthorizationCodeAuthenticator.getAuthHeaders adds client_id/client_secret. (src/main/resources/templates/java/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- kotlin: Same (getAccessToken(tokenUrl, params, extraHeaders, refreshUrl = null)); password always passes the password grant; auth-code adds client creds; remove the 'RFC 6749 sec 6 ... only grant_type and refresh_token' comment. (src/main/resources/templates/kotlin/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- csharp: Same, optional refreshUrl parameter on GetAccessTokenAsync; password/auth-code as above. (src/main/resources/templates/csharp/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- php: Same, optional ?string $refreshUrl on getAccessToken; password at OAuth2PasswordAuthenticator.php:138 collapses to one call. (src/main/resources/templates/php/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- python: Same, refresh_url=None kwarg on get_access_token; password at oauth2_password_authenticator.py:110 collapses; auth-code adds creds. (src/main/resources/templates/python/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- ruby: Same; token_request in oauth2_password_authenticator.rb:98 returns the password grant always; manager gets refresh_url: kwarg. (src/main/resources/templates/ruby/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- node: Same, optional refreshUrl parameter on getAccessToken. (src/main/resources/templates/node/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- dart: Same, optional named/positional refreshUrl on getAccessToken; oauth2_password_authenticator.dart:96 collapses; auth-code adds creds. (src/main/resources/templates/dart/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- go: Add GetAccessTokenWithHeadersAndRefresh (or extra refreshURL arg on GetAccessTokenWithHeaders); AuthHeadersOrError of the password authenticator always passes the password grant; auth-code adds creds. (src/main/resources/templates/go/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- rust: get_access_token_with_headers gets an Option<&str> refresh_url; password/auth-code as above (oauth2_authorization_code_authenticator.rs:213 adds client_id/client_secret). (src/main/resources/templates/rust/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- swift: Same, refreshURL: String? parameter on getAccessToken; password/auth-code as above. (src/main/resources/templates/swift/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- elixir: get_access_token/5 with an optional refresh_url in opts; password at oauth2_password_authenticator.ex:136 collapses; auth-code adds creds. (src/main/resources/templates/elixir/auth/oauth/{oauth2_token_manager,oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)

**Tests.** Existing generated tests: OAuth2PasswordAuthenticatorTest / OAuth2AuthorizationCodeAuthenticatorTest / OAuth2TokenManagerTest in every language (java usesRefreshTokenOnSubsequentCalls only asserts grant_type/refresh_token). Add per language: (1) password BODY mode: after the first token response carrying refresh_token and expires_in=1, the next call POSTs to refreshUrl with grant_type=refresh_token AND client_id AND client_secret; (2) password BASIC mode: refresh has the Basic Authorization header and no client_id in the body; (3) refresh answered 400 invalid_grant -> a third request to tokenUrl with grant_type=password succeeds and its token is returned; (4) auth-code: refresh body carries client_id/client_secret, and a 400 on refresh surfaces OAuth2ServerError after exactly ONE refresh POST; (5) token manager unit row: refreshUrl routing and 'params already refresh_token => single POST'. The petstore spec already has legacyAuth (password + refreshUrl) and userAuth (authorizationCode + refreshUrl); the authenticator-level tests above do not need a spec change.

**Risk.** Changes the refresh request bytes for all password/auth-code users: extra client_id/client_secret fields (RFC-conformant, but an OP that rejects extra params on refresh is theoretically possible). Token manager signature change touches every caller and test.

## D03 exchangeCode always POSTs the authorization_code grant and replaces the session; it never goes through the token cache or the refresh path

- Ledger ids: F119 | Theme: oauth-oidc | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Uniform in all 12. exchangeCode builds {grant_type=authorization_code, code, client_id, client_secret, redirect_uri} and calls the caching tokenManager.getAccessToken (java OAuth2AuthorizationCodeAuthenticator.java:153, go oauth2_authorization_code_authenticator.go:145, rust .rs:144, elixir .ex:179, python/ruby/node/dart/php/kotlin/csharp/swift equivalents). That method first returns a still-valid cached token and otherwise tries the stored refresh_token grant (java OAuth2TokenManager.java:92-117), so a second exchangeCode (re-login, account switch, scope upgrade) never sends the new code and still sets the exchanged flag; an old refresh_token also survives a response that has none.

**Canonical behaviour.** Add an explicit exchange operation to the token manager (java exchange(tokenUrl, params, extraHeaders)) that: takes the manager lock, saves the previous access/refresh/expiry, clears them, POSTs the given params verbatim to the given URL (no cache hit, no refresh attempt), on success keeps exactly what the response carried (a response without refresh_token leaves NO refresh token; without expires_in leaves no expiry) and marks the session exchanged (flag lives in the manager, see the rediscovery decision); on any failure restores the previous state and rethrows, so a mistyped code does not log the user out. exchangeCode in the auth-code authenticator (and therefore the OIDC delegate) calls only this operation. getAuthHeaders is unchanged. Client-credentials/password flows keep the caching getAccessToken.

**Why.** (a) RFC 6749 sec 4.1.3: the token request for an authorization_code grant must carry the code; a client method named exchangeCode that skips the request violates its contract. (c) caller-safe: failure restores state, success fully replaces it. (d) pure plumbing in the manager of each language.

**Rejected.** (a) Call invalidateAccessToken + clear refresh token before getAccessToken: leaves the refresh-first branch live and wipes the old session even when the new exchange fails. (b) Make exchangeCode throw if already exchanged: breaks legitimate re-login on a long-lived authenticator. (c) Leave as is: silently keeps the previous identity, a correctness and security-relevant bug.

**Per language.**

- java: New synchronized OAuth2TokenManager.exchange(...) with state restore on exception; OAuth2AuthorizationCodeAuthenticator.exchangeCode calls it instead of getAccessToken and drops its own tokenExchanged field. (src/main/resources/templates/java/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- kotlin: Same inside refreshMutex.withLock. (src/main/resources/templates/kotlin/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- csharp: Same under _stateLock (note fetch is async; use the existing single-flight pattern). (src/main/resources/templates/csharp/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- php: Same; restore state in a catch/rethrow. (src/main/resources/templates/php/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- python: Same under self._lock; restore in except/raise. (src/main/resources/templates/python/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- ruby: Same under @mutex with ensure-style restore. (src/main/resources/templates/ruby/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- node: Same (async, serialised with the manager's existing in-flight promise). (src/main/resources/templates/node/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- dart: Same. (src/main/resources/templates/dart/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- go: Add Exchange(tokenURL, params) under m.mu; ExchangeCode (oauth2_authorization_code_authenticator.go:145) calls it. (src/main/resources/templates/go/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- rust: Add exchange(...) on OAuth2TokenManager using fetch_token_locked after saving/clearing inner; exchange_code (rs:144) calls it. (src/main/resources/templates/rust/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- swift: Add exchange(...) in OAuth2TokenManager; exchangeCode calls it. (src/main/resources/templates/swift/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)
- elixir: Add OAuth2TokenManager.exchange/3 (Agent state replaced only on success); exchange_code (ex:179) calls it then mark_exchanged. (src/main/resources/templates/elixir/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator}.mustache)

**Tests.** OAuth2AuthorizationCodeAuthenticatorTest in each language (generated names listed in the first decision): add 'second exchangeCode sends the new code': exchangeCode('a') -> token1 (with refresh_token); exchangeCode('b') -> a second POST whose body has code=b and grant_type=authorization_code, getAuthHeaders then returns token2; 'second exchange without refresh_token drops the old refresh token'; 'failed exchange keeps the previous session' (400 invalid_grant on 'bad', getAuthHeaders still returns token1). OAuth2TokenManagerTest: unit rows for exchange (cache bypass, refresh bypass, failure restore). No spec change.

**Risk.** Shares the token-manager and auth-code files with the rediscovery and refresh decisions; do these three together to avoid triple churn on the same templates.

## D04 Per-scheme OAuth wrappers pass the spec's declared scopes, in spec declaration order

- Ledger ids: F018 F288 | Theme: oauth-oidc | Still true: **partly** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** F018 is FIXED: go (machine_auth_client_credentials_authenticator.go:~25 []string{"pets:write","pets:read"}), rust (machine_auth_client_credentials_authenticator.rs:22-24) and elixir (...ex:23) now pass the scopes, and tests pin it (go oauth2_client_credentials_authenticator_test.go:89-105 'scope=pets%3Awrite+pets%3Aread', rust ...test.rs:291-306, elixir ...test.exs:50-58). F288 is STILL TRUE in all 12: AbstractBetterCodegen.java:1137 copies flow.getScopes() (swagger-parser Scopes extends LinkedHashMap, spec order pets:read, pets:write at petstore openapi.yaml:1470-1472 and :1488-1490) into new HashMap<>, so every wrapper emits ["pets:write","pets:read"] and the scope= string on the wire is in hash order. Every per-language formatXScopes (e.g. BetterJavaCodegen.java:955 scopes.keySet()) then preserves that wrong order.

**Canonical behaviour.** Wrappers carry the scheme's scopes in the order the spec declares them. SchemeAuthSpec stores scopes in a LinkedHashMap (Collections.unmodifiableMap(new LinkedHashMap<>(scopes))). Single generator fix, no per-language template change; all 12 follow automatically. Scope = all scopes the scheme declares (as today); per-operation `security` requirement scopes remain unused (unchanged, not part of this decision). Goldens: pets:read before pets:write.

**Why.** (a) OpenAPI scopes is an ordered map in the parsed model and the scope parameter is a space-delimited list where authors expect declaration order (RFC 6749 sec 3.3 treats it as unordered but byte-identical output across runs/SDKs is the repo rule); (c) no semantic change for the OP; (d) one-line generator change.

**Rejected.** Sort scopes alphabetically: deterministic but discards the spec author's order and still changes the wire string vs. what the spec shows. Keep HashMap and document: nondeterministic across JDKs for large maps, and unrelated to the spec.

**Per language.**

- java: Generator fix only (affects wrappers via formatJavaScopes). (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137)
- kotlin: Generator fix only. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137)
- csharp: Generator fix only. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137)
- php: Generator fix only. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137)
- python: Generator fix only. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137)
- ruby: Generator fix only. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137)
- node: Generator fix only. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137)
- dart: Generator fix only. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137)
- go: Generator fix only; update go test assertion at oauth2_client_credentials_authenticator_test.go:102 to scope=pets%3Aread+pets%3Awrite. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137; src/main/resources/templates/go/test/oauth2_client_credentials_authenticator_test.mustache)
- rust: Generator fix only; update rust test assertion at oauth2_client_credentials_authenticator_test.rs:306 to scope=pets%3Aread+pets%3Awrite. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137; src/main/resources/templates/rust/test/oauth2_client_credentials_authenticator_test.mustache)
- swift: Generator fix only. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137)
- elixir: Generator fix only; the elixir wrapper test sorts scopes (test.exs:58) so it cannot see order: assert the unsorted list ["pets:read","pets:write"]. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java:1137; src/main/resources/templates/elixir/test/oauth2_client_credentials_authenticator_test.mustache)

**Tests.** Unit (mirror the source file, new): src/test/java/io/github/mridang/codegen/generators/AbstractBetterCodegenTest.java asserting SchemeAuthSpec.scopes().keySet() for machineAuth and userAuth equals [pets:read, pets:write] (built from the petstore spec). Generated: the Machine wrapper scope test that go/rust/elixir already have in OAuth2ClientCredentialsAuthenticatorTest should be added to the other 9 languages (wrapper constructed with host/client id/secret, token POST body contains scope=pets%3Aread+pets%3Awrite, form-encoded space as +) and a userAuth wrapper authorize-URL row (scope=pets%3Aread%20pets%3Awrite) in OAuth2AuthorizationCodeAuthenticatorTest. The petstore spec already declares the multi-scope flows in the needed order; no YAML change.

**Risk.** Changes the scope= bytes in every generated multi-scope wrapper (goldens in all 12 regenerate).

## D05 OpenID Connect authenticators always request the openid scope

- Ledger ids: F113 | Theme: oauth-oidc | Still true: **yes** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** Uniform in all 12. Every SsoAuth wrapper passes an empty scope list (java SsoAuthAuthenticator.java:30 List.of(), kotlin ...kt:21 listOf(), csharp ...cs:23 [], php ...php:33 [], python ...py:23 [], ruby ...rb:22 [], node ...ts:26 [], dart ...dart:21 [], go sso_auth_authenticator.go:~28 nil, rust ...rs:24 vec![], swift ...swift:14 scopes: [], elixir ...ex:24 []) because AbstractBetterCodegen.java:~1186 passes null scopes for openIdConnect schemes, and the runtime OpenIdConnectAuthenticator adds nothing; the delegate's authorize URL therefore has no scope parameter, so it is a plain OAuth2 request (no id_token) and OPs such as Google reject it. No `openid` string exists anywhere under the goldens.

**Canonical behaviour.** OIDC Core sec 3.1.2.1: scope MUST contain `openid`. In the runtime OpenIdConnectAuthenticator constructor (all 12) compute effectiveScopes = ["openid"] + the caller's scopes with any case-sensitive duplicate of "openid" removed, and pass effectiveScopes to the delegate. The configured scopes stay as given in toString/repr and constructor fields. Wrappers keep passing the scheme's declared scopes (none for openIdConnect, as today); security-requirement scopes remain unused. Doing it at runtime (not in the generator) also covers callers who instantiate OpenIdConnectAuthenticator directly.

**Why.** (a) normative MUST in OIDC Core; (c) adds no privilege beyond the identity token the flow is named for; (d) a list prepend in each language.

**Rejected.** Generator-only (pass ["openid"] from the wrapper): misses direct construction, leaves the runtime class able to produce non-OIDC requests. Make it opt-in: defeats the purpose, an OIDC authenticator without openid is not OIDC. Also add profile/email by default: unrequested data access, rejected.

**Per language.**

- java: Constructor: this.scopes = withOpenid(scopes) helper (List.copyOf); keep toString using the configured list or document that it shows effective scopes. (src/main/resources/templates/java/auth/oauth/openid_connect_authenticator.mustache)
- kotlin: Same helper, passed to the delegate in resolveDelegate. (src/main/resources/templates/kotlin/auth/oauth/openid_connect_authenticator.mustache)
- csharp: Same in the constructor ([.. scopes] copy). (src/main/resources/templates/csharp/auth/oauth/openid_connect_authenticator.mustache)
- php: Same in __construct. (src/main/resources/templates/php/auth/oauth/openid_connect_authenticator.mustache)
- python: Same in __init__. (src/main/resources/templates/python/auth/oauth/openid_connect_authenticator.mustache)
- ruby: Same in initialize. (src/main/resources/templates/ruby/auth/oauth/openid_connect_authenticator.mustache)
- node: Same in the constructor. (src/main/resources/templates/node/auth/oauth/openid_connect_authenticator.mustache)
- dart: Same in the constructor initializer. (src/main/resources/templates/dart/auth/oauth/openid_connect_authenticator.mustache)
- go: Same in NewOpenIdConnectAuthenticator (handles nil input). (src/main/resources/templates/go/auth/oauth/openid_connect_authenticator.mustache)
- rust: Same in OpenIdConnectAuthenticator::new. (src/main/resources/templates/rust/auth/oauth/openid_connect_authenticator.mustache)
- swift: Same in init. (src/main/resources/templates/swift/auth/oauth/openid_connect_authenticator.mustache)
- elixir: Same in new/6. (src/main/resources/templates/elixir/auth/oauth/openid_connect_authenticator.mustache)

**Tests.** OpenIdConnectAuthenticator test in every language (the createAuthenticator helper already passes [openid, profile], e.g. java OpenIdConnectAuthenticatorTest.java:36-45): add rows (1) constructed with empty scopes, buildAuthorizationUrl(null) contains scope=openid; (2) constructed with [profile], URL contains scope=openid%20profile in that order; (3) constructed with [openid, profile] the URL contains exactly one openid (scope=openid%20profile). The SsoAuth wrapper adds nothing testable beyond (1). No spec change.

**Risk.** Adds a scope= parameter to previously scope-less authorize URLs of every OIDC client; existing OIDC tests that assert the full URL need updating.

## D06 Implicit flow treats an empty access token as unset

- Ledger ids: F112 | Theme: oauth-oidc | Still true: **yes** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** 7 of 12 send 'Authorization: Bearer ' after setAccessToken(""): java OAuth2ImplicitAuthenticator.java:115 (== null), kotlin OAuth2ImplicitAuthenticator.kt:~100 (?: throw on null only), csharp .cs:130-135 (is null), php .php:155 (=== null), python oauth2_implicit_authenticator.py:115 (is None), ruby .rb:115 (nil?), elixir .ex:128 (access_token: nil). 5 raise 'Must set the access token before making API requests': node .ts:132 (!this.accessToken), go .go:137 (== ""), rust .rs:165 (is_empty), swift .swift:114 (isEmpty), dart .dart:98 (isEmpty). BearerAuthenticator rejects empty tokens ('Bearer token must not be empty').

**Canonical behaviour.** An empty string is 'unset': getAuthHeaders raises the existing precondition error 'Must set the access token before making API requests' (the SDK's usual state error type) when the token is null/absent OR exactly empty. setAccessToken("") stays legal (it is how a caller clears the token) and keeps the printable-ASCII check. Whitespace-only tokens are not specially treated here (the whitespace policy belongs to the simple-auth findings, e.g. F274).

**Why.** (b) 5 of 12 already do it; (a)/(c) never put a credential-less 'Bearer ' header on the wire, consistent with BearerAuthenticator's guarantee; (d) trivial everywhere.

**Rejected.** Reject "" at set time: stops callers clearing a token and diverges from the 5 SDKs that already behave as 'unset'. Also reject whitespace-only: different whitespace sets per runtime would reopen F274.

**Per language.**

- java: if (token == null || token.isEmpty()) throw IllegalStateException(...) (src/main/resources/templates/java/auth/oauth/oauth2_implicit_authenticator.mustache)
- kotlin: accessToken?.takeIf { it.isNotEmpty() } ?: throw IllegalStateException(...) (src/main/resources/templates/kotlin/auth/oauth/oauth2_implicit_authenticator.mustache)
- csharp: string.IsNullOrEmpty(token) -> InvalidOperationException (src/main/resources/templates/csharp/auth/oauth/oauth2_implicit_authenticator.mustache)
- php: $this->accessToken === null || $this->accessToken === '' -> LogicException (src/main/resources/templates/php/auth/oauth/oauth2_implicit_authenticator.mustache)
- python: if not self._access_token: raise RuntimeError(...) (src/main/resources/templates/python/auth/oauth/oauth2_implicit_authenticator.mustache)
- ruby: raise ... if @access_token.nil? || @access_token.empty? (src/main/resources/templates/ruby/auth/oauth/oauth2_implicit_authenticator.mustache)
- elixir: add a clause for access_token: "" alongside the nil clause (src/main/resources/templates/elixir/auth/oauth/oauth2_implicit_authenticator.mustache)
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical

**Tests.** OAuth2ImplicitAuthenticatorTest in all 12 (java/kotlin src/test/.../auth/oauth/OAuth2ImplicitAuthenticatorTest, csharp Test/OAuth2ImplicitAuthenticatorTest.cs, php tests/OAuth2ImplicitAuthenticatorTest.php, python test/test_oauth2_implicit_authenticator.py, ruby test/oauth2_implicit_authenticator_test.rb, node test/oauth2-implicit-authenticator.test.ts, dart test/oauth2_implicit_authenticator_test.dart, go test/oauth2_implicit_authenticator_test.go, rust tests/oauth2_implicit_authenticator_test.rs, swift OAuth2ImplicitAuthenticatorTests.swift, elixir test/oauth2_implicit_authenticator_test.exs): one row 'setAccessToken("") then getAuthHeaders raises Must set the access token before making API requests'. No spec change.

**Risk.** None beyond a new error where 7 SDKs previously sent an unauthenticated 'Bearer ' header.

## D07 Token-response and OIDC-discovery error messages: one wording set, no parser detail or cause on token errors

- Ledger ids: F115 F266 F289 | Theme: oauth-oidc | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Token response (2xx but unusable): canonical triple is 'Token response is not valid JSON' / 'Token response is not a JSON object' / 'Token response missing or empty access_token field'. java (OAuth2TokenManager.java:196/203/235), ruby (.rb:173/177/203) and node (.ts:195/198/204) are exactly this, no detail, no cause (F289 fix landed there; kotlin .kt:225 also dropped detail but collapsed invalid-JSON into 'is not a JSON object'). Still divergent: python (.py:189) 'not valid JSON: {e}' with `from e`; csharp (.cs:315) 'not valid JSON: {ex.Message}' with inner exception; dart (.dart:188) 'not valid JSON: ${e.message}'; go (.go:215) 'not valid JSON: %v' and a JSON array/null body is reported as 'not valid JSON: json: cannot unmarshal array' / 'missing access_token'; rust (.rs:235/246) 'failed to parse token response: {e}' and lowercase 'token response missing or empty access_token field', array body -> missing-token message, no non-object check; php (.php:216) reports invalid JSON as 'is not a JSON object', a JSON list passes is_array so `[]` -> missing token; swift (.swift:275) and elixir (.ex:250) use 'Token response is not a valid token JSON object' for both invalid and non-object. Parser detail is a redaction risk only where it quotes input (jackson/V8/kotlinx/Ruby did; .NET and Go quote one character, python/dart none). OIDC discovery document: missing-endpoint wording 'OIDC discovery document is missing \'<field>\'' is common (go lowercase 'oidc ...'), but invalid JSON appends parser detail in java/python/csharp/dart/node/ruby, is detail-free in rust/go/swift, and is reported as 'not a JSON object' in kotlin/php/elixir; java alone has 'field is not a string: <f>'; python (`if not authorization_endpoint`, .py:145) and ruby (.to_s.strip.empty?, .rb:128) accept a non-string truthy endpoint such as 123; swift decodes via a typed struct so a non-string endpoint reports 'not a JSON object'; go's typed struct reports a non-string endpoint as 'not valid JSON'; whitespace-only endpoints are rejected by rust/go/swift/ruby/elixir only.

**Canonical behaviour.** Token response errors (OAuth2TokenError, no cause, no parser detail, message is byte-identical in all 12): `Token response is not valid JSON` (body does not parse), `Token response is not a JSON object` (parses but is not an object, including null and arrays), `Token response missing or empty access_token field`. Rationale for dropping detail and cause: the repo already redacts secrets in reprs and F289 fixed the leak in java/kotlin/ruby/node by exactly this rule. OIDC discovery errors (SerializationError) keep the same three-way split with fixed text and NO parser detail in the message: `OIDC discovery document is not valid JSON` (the parser exception stays attached as the cause where the language's error type has one; a discovery document holds no secrets), `OIDC discovery document is not a JSON object`, and `OIDC discovery document is missing '<field>'` for an endpoint that is absent, null, not a string, empty or whitespace-only (so java's 'field is not a string' goes away). Go uses the capital 'OIDC' (these are typed errors built with New*Error, ST1005 does not apply, and go's token errors already start with a capital). csharp keeps its extra absolute-URL guard (documented in AGENTS.md). Do not contradict AGENTS.md R7-13: go and swift keep their typed field decoders; they classify invalid-JSON vs non-object with a first dynamic parse.

**Why.** (a) the repo's redaction rule (F289, AGENTS secrets-masked) and R8-7 'one canonical string per message'; (b) java/ruby/node already match the token triple and the discovery 'missing' wording is already the majority; (c) never echo the body into a log-bound message; (d) trivial string/branch changes; go/swift needing a pre-parse is a stdlib feature.

**Rejected.** Keep ': <detail>' (majority of discovery messages, java/python/csharp/dart/node/ruby): detail text is parser-specific so messages can never be byte-identical, and for token responses it is the leak vector. Keep the cause chained for token errors: java/ruby/kotlin deliberately dropped it because the cause message quotes the body. Merge invalid-JSON and non-object into one message (swift/elixir/kotlin/php today): loses a useful distinction that 9 SDKs already have.

**Per language.**

- java: Token: already canonical. Discovery: OpenIdConnectAuthenticator.java:109 message -> 'OIDC discovery document is not valid JSON' (keep cause e); drop the 'field is not a string' branch (:144-146) so a non-string endpoint reports 'missing'; add blank/whitespace check. (src/main/resources/templates/java/auth/oauth/openid_connect_authenticator.mustache)
- kotlin: Token (.kt:225): split SerializationException/IllegalArgumentException from the jsonObject cast: parse failure -> 'not valid JSON', non-JsonObject -> 'not a JSON object', both without cause. Discovery (.kt:107): same split, keep cause for invalid JSON; blank endpoint -> missing. (src/main/resources/templates/kotlin/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- csharp: Token ParseTokenResponse (.cs:315): drop {ex.Message} and inner exception. Discovery (.cs:136): drop {ex.Message}, keep inner exception; add whitespace-only check to RequireEndpoint. (src/main/resources/templates/csharp/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- php: Token (.php:214-217) and discovery (.php:144-147): decode with JSON_THROW_ON_ERROR (catch JsonException -> 'not valid JSON'), then require a stdClass/assoc object (a list or scalar -> 'not a JSON object', e.g. decode non-assoc and check instanceof \\stdClass, then get_object_vars); discovery endpoints: is_string && trim !== ''. (src/main/resources/templates/php/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- python: Token (.py:189): 'Token response is not valid JSON' and raise ... from None. Discovery (.py:137): drop ': {e}', keep cause; endpoint check -> isinstance(str) and strip() non-empty. (src/main/resources/templates/python/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- ruby: Token: already canonical. Discovery (.rb:150): drop ': #{e.message}' (keep cause); endpoint must be a String and non-blank (currently .to_s accepts 123). (src/main/resources/templates/ruby/auth/oauth/openid_connect_authenticator.mustache)
- node: Token: already canonical. Discovery (.ts:123-126): drop the `: ${message}` suffix (keep cause); reject whitespace-only endpoints (trim().length === 0). (src/main/resources/templates/node/auth/oauth/openid_connect_authenticator.mustache)
- dart: Token (.dart:188): drop ': ${e.message}'. Discovery (.dart:130): drop detail, keep cause; whitespace-only endpoint -> missing. (src/main/resources/templates/dart/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- go: Token (.go:213-220): first json.Unmarshal into `any`: error -> 'Token response is not valid JSON' (no %v), non-map (array, null, scalar) -> 'Token response is not a JSON object', then the existing typed Unmarshal for fields. Discovery (.go:196-207): same classification, capital 'OIDC ...', decode endpoints from map[string]any so a non-string endpoint is 'missing'; keep strings.TrimSpace check. (src/main/resources/templates/go/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- rust: Token (.rs:235-250): map serde error -> 'Token response is not valid JSON' (no `{}`), add `if !parsed.is_object()` -> 'Token response is not a JSON object', capitalise 'Token response missing or empty access_token field'. Discovery (.rs:196-212): add an is_object check -> 'OIDC discovery document is not a JSON object' (a JSON array currently reports missing endpoint). (src/main/resources/templates/rust/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- swift: Token (.swift:275): pre-parse with JSONSerialization.jsonObject (already used later at :283): throw -> invalidResponse('Token response is not valid JSON'), non-dictionary -> invalidResponse('Token response is not a JSON object'); keep the typed Decodable afterwards. Discovery (.swift:146-152): same pre-parse so 'not a JSON object' is only for non-objects and a non-string endpoint reports 'missing' (read endpoints from the dictionary as? String). (src/main/resources/templates/swift/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- elixir: Token (.ex:244-251): split {:error,_} -> 'Token response is not valid JSON' from {:ok, non-map} -> 'Token response is not a JSON object'. Discovery (.ex:229-235): same split with the 'OIDC discovery document ...' texts. (src/main/resources/templates/elixir/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)

**Tests.** OAuth2TokenManagerTest and OpenIdConnectAuthenticatorTest in all 12 (file names as in the first decision): table rows on the full message (equality, not substring): token bodies 'access_token=gho_SECRET&token_type=bearer' -> 'Token response is not valid JSON' and the message and cause (if any) do not contain 'gho_SECRET'; '[]' , 'null', '"x"' -> 'Token response is not a JSON object'; '{}' and '{"access_token":""}' -> 'Token response missing or empty access_token field'. Discovery bodies '<html>' -> 'OIDC discovery document is not valid JSON'; '[]' -> '... is not a JSON object'; {"authorization_endpoint":123,"token_endpoint":"https://x"}, "  " and null -> "OIDC discovery document is missing 'authorization_endpoint'". Use it.each / table style per AGENTS test rules. No spec change.

**Risk.** Changes error text only; users string-matching old messages break (python/csharp/dart/go/rust/swift/elixir/kotlin/php). Swift/go pre-parse adds a second parse of the token body (negligible).

## D08 expires_in and discovery max-age: one decimal grammar for strings and a one-year ceiling applied before any date arithmetic

- Ledger ids: F116 F294 | Theme: oauth-oidc | Still true: **partly** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** F116 (partly fixed): only rust clamps (oauth2_token_manager.rs:31 MAX_EXPIRES_IN_SECONDS=31_536_000 applied at :268; openid_connect_authenticator.rs:52 parse_max_age clamps to one year; test at openid_connect_authenticator_test.rs:450-455 max-age=18446744073709551615). Still unclamped: swift (OAuth2TokenManager.swift:356/361 Int(d.rounded(.down)) TRAPS for >= 9.22e18, e.g. expires_in 1e20), java (OAuth2TokenManager.java:302-316 returns Long.MAX_VALUE then Instant.plusSeconds throws ArithmeticException/DateTimeException at :123-ish; OIDC parseMaxAge likewise), csharp (.cs:294 AddSeconds throws ArgumentOutOfRangeException; ParseMaxAge long.TryParse overflow falls to default), kotlin (.kt:256 (expiresIn-bufferSecs)*1000L overflows -> expiry in the past -> refetch every call; OIDC plusSeconds throws), go (.go:232 time.Duration(n)*time.Second overflows above ~9.2e9 s; int64(math.Floor(1e20)) is implementation-defined; parseDiscoveryMaxAge Atoi), dart (.dart:216 Duration(seconds:) microsecond overflow; int.tryParse fallback to default), ruby (digits-only grammar fixed the Float('1e400') crash but huge Integer still unbounded), php/python/node/elixir (no crash, but php (int)floor(1e20) is undefined and python float+int can OverflowError for 400-digit max-age). F294 (string grammar) is fixed only in java (text.matches \\d+(\\.\\d+)?), ruby (regex), node (regex), swift (regex, but allows a trailing dot '5.'): kotlin toDoubleOrNull (hex/p-exponent, 'd'/'f' suffix), csharp double.TryParse(NumberStyles.Float) (exponent, sign), php is_numeric (exponent, leading whitespace), python float() (exponent, underscores '3_600'), dart double.tryParse (hex '0x10', exponent), go strconv.ParseFloat (exponent, 'Inf'), rust str::parse::<f64> (exponent, 'inf'), elixir Integer.parse/Float.parse (exponent) still use their native grammar.

**Canonical behaviour.** (1) String expires_in: trim ASCII whitespace, then require ^[0-9]+(\\.[0-9]+)?$ (floor the fraction); anything else (hex, exponent, sign, suffix, underscore, NaN/Inf, trailing dot) is unusable -> 0 -> token stale (refetch next call), as java/ruby/node do. JSON numbers keep floor() and non-finite -> stale. (2) A shared ceiling constant of 31_536_000 seconds (one year, rust's existing value) applied to the parsed expires_in and to the parsed discovery max-age BEFORE subtracting the 30 s buffer, multiplying to ms or adding to a clock; digits that overflow the language integer saturate to the ceiling (not to the 86400 default). A huge-but-finite lifetime therefore caches for at most a year (no throw, no wrap, no trap); a non-finite or non-positive one stays 'stale'. Applies in all 12 even where nothing crashes today (php, python, node, elixir) so behaviour is identical.

**Why.** (a) RFC 6749 sec 5.1 defines expires_in as a lifetime in seconds (a number); server input must not crash the client (error-contract); (b) java/ruby/node/swift already use the strict grammar and rust already clamps; (c) no process trap/silent wrap; (d) all stdlib.

**Rejected.** Treat huge values as stale/unusable: avoids caching a token for a year but makes a server that legitimately sends 'never expires' style 2^53 values refetch on every call. Different ceilings per language: no, rust's already tested constant is reusable everywhere. Keep each language's native numeric grammar and only clamp: leaves the F294 split ('0x10' = 16 s in dart, 0 elsewhere).

**Per language.**

- java: Grammar already canonical. Add ceiling: min(expiresIn, 31_536_000L) after parseExpiresIn and in parseMaxAge (NumberFormatException on a long digit run -> ceiling); guard double->long cast. (src/main/resources/templates/java/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- kotlin: Replace toDoubleOrNull with the ^[0-9]+(\.[0-9]+)?$ regex then toDouble; JsonPrimitive number path: finite check; ceiling in parseExpiresIn and parseMaxAge (toLongOrNull null on overflow -> ceiling). (src/main/resources/templates/kotlin/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- csharp: Replace double.TryParse with a Regex ^[0-9]+(\.[0-9]+)?$ (source-generated, as MaxAgePattern is) then parse InvariantCulture; clamp seconds and max-age (long.TryParse failure with all-digit input -> ceiling). (src/main/resources/templates/csharp/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- php: Replace is_numeric with preg_match('/^[0-9]+(\.[0-9]+)?$/'); clamp the float before the (int) cast; clamp parseMaxAge. (src/main/resources/templates/php/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- python: Replace float(trimmed) with re.fullmatch(r'[0-9]+(\.[0-9]+)?') (ASCII, re.ASCII flag); clamp expires_in and max-age (int() of the digit run is safe, min with ceiling). (src/main/resources/templates/python/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- ruby: Grammar canonical. Add the ceiling in parse_expires_in result and parse_max_age. (src/main/resources/templates/ruby/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- node: Grammar canonical. Add Math.min(.., 31_536_000) to expires_in and parseMaxAge (and Number.isFinite on the max-age digits). (src/main/resources/templates/node/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- dart: Replace double.tryParse with RegExp(r'^[0-9]+(\.[0-9]+)?$') then double.parse; clamp expiresIn (before Duration) and _parseMaxAge (tryParse null on overflow -> ceiling). (src/main/resources/templates/dart/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- go: Replace strconv.ParseFloat on strings with a regexp ^[0-9]+(\.[0-9]+)?$ then ParseFloat; clamp the float64 to the ceiling BEFORE int64() conversion and before time.Duration math; same in parseDiscoveryMaxAge (Atoi range error -> ceiling). (src/main/resources/templates/go/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- rust: Ceiling already present and tested. Only the string grammar: replace trimmed.parse::<f64>() (.rs:363) with the digits(+fraction) check; and let parse_max_age saturate (u64 overflow on a long digit run -> MAX_DISCOVERY_MAX_AGE_SECONDS, now falls back to 86400). (src/main/resources/templates/rust/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- swift: Clamp the Double (min(d, 31_536_000)) BEFORE Int(...) in all three branches (Double, NSNumber, String; Int64/Int path min as well) so Int() never traps; tighten the regex to ^[0-9]+(\.[0-9]+)?$ (no trailing dot); parseMaxAge: Int(String) nil on overflow -> ceiling. (src/main/resources/templates/swift/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)
- elixir: Replace Integer.parse/Float.parse with a Regex ~r/^[0-9]+(\.[0-9]+)?$/ then parse; min(..., 31_536_000) in parse_expires_in and parse_max_age. (src/main/resources/templates/elixir/auth/oauth/{oauth2_token_manager,openid_connect_authenticator}.mustache)

**Tests.** OAuth2TokenManagerTest in all 12: table rows for expires_in as JSON number 1e20, 99999999999999999999 (int where the parser allows it), the string "100000000000000000000" -> no exception, token still served from cache on the second call with no second POST; "Infinity"/1e999 (where the JSON parser yields it) -> stale (second call refetches); string rows "0x10", "3600d", "1e3", "+3600", "3_600", "5." -> stale (refetch); "3600", " 3600 ", "3600.9" and "3600\n" -> cached. OpenIdConnectAuthenticatorTest: Cache-Control max-age=99999999999999999999 -> two buildAuthorizationUrl calls cause one discovery GET (rust has this at openid_connect_authenticator_test.rs:450; copy to the other 11). No spec change.

**Risk.** A year-long cache for absurd lifetimes; strings like '1e3' that 8 SDKs used to accept now count as stale (an OP sending that is non-conformant). Regex is ASCII-only by design.

## D09 Auth-code precondition messages: one sentence per condition

- Ledger ids: F286 | Theme: oauth-oidc | Still true: **yes** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Canonical in java (OAuth2AuthorizationCodeAuthenticator.java:145/165), csharp (.cs:132/174, 'ExchangeCodeAsync()'), php (.php:157/192), python (.py:112/137, 'exchange_code()'), node (.ts:123/168), dart for the empty message (.dart:105). Divergent: kotlin .kt:98 'Authorization code must not be empty or blank'; ruby .rb:98 lowercase 'authorization code must not be empty'; ruby .rb:113 / elixir .ex:196 / dart .dart:121,134 omit the parentheses ('Must call exchange_code before making API requests'); rust errors/oauth2_authorization_code_error.rs:28-31 'oauth2 authorization code: must call exchange_code before requesting auth headers' / 'oauth2 authorization code: code must not be empty'; go .go:27/128 sentinels 'oauth2 authorization code: must call ExchangeCode before requesting auth headers' and 'authorization code must not be empty'; swift OAuth2AuthorizationCodeError (.swift:20/22) enum cases without a description, so the message is Foundation's generic text.

**Canonical behaviour.** Two messages, identical in all 12 except the idiomatic method name: `Authorization code must not be empty` and `Must call <method>() before making API requests` where <method> is the language's exchange method (exchangeCode / ExchangeCodeAsync / exchange_code / ExchangeCode). Parentheses always present. Go keeps the sentinel errors but lowercases the first letter (ST1005 FORCED, already documented for go) and drops the 'oauth2 authorization code:' prefix: `authorization code must not be empty`, `must call ExchangeCode() before making API requests`. Rust Display and swift LocalizedError/CustomStringConvertible return the canonical text (rust can capitalise: R8-7 already moved rust to capitalised messages). The same rule applies to the sibling implicit-flow sentinel (go ErrAccessTokenNotSet 'oauth2 implicit: must set ...' -> 'must set the access token before making API requests').

**Why.** (a) AGENTS R8-7 'one canonical string per message'; (b) 8-9 SDKs already match; (d) string edits; go lowercase is the only forced difference.

**Rejected.** Keep per-flow prefixes in rust/go: duplicates the error type name in the message and breaks byte-identical text. Say 'must not be empty or blank' everywhere: describes only kotlin's isNotBlank and diverges from the 9 that already say 'empty' (whitespace-only is empty here by design).

**Per language.**

- java: already canonical
- kotlin: 'Authorization code must not be empty' (drop 'or blank'). (src/main/resources/templates/kotlin/auth/oauth/oauth2_auth_code_authenticator.mustache)
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: Capitalise to 'Authorization code must not be empty'; 'Must call exchange_code() before making API requests'. (src/main/resources/templates/ruby/auth/oauth/oauth2_auth_code_authenticator.mustache)
- node: already canonical
- dart: Add () to both 'Must call exchangeCode() before making API requests' strings (.dart:121,134). (src/main/resources/templates/dart/auth/oauth/oauth2_auth_code_authenticator.mustache)
- go: ErrAuthCodeNotExchanged = errors.New("must call ExchangeCode() before making API requests"); ErrAuthCodeEmpty stays 'authorization code must not be empty'; same prefix removal for ErrAccessTokenNotSet in the implicit template. (src/main/resources/templates/go/auth/oauth/{oauth2_auth_code_authenticator,oauth2_implicit_authenticator}.mustache)
- rust: Display: EmptyCode -> 'Authorization code must not be empty', CodeNotExchanged -> 'Must call exchange_code() before making API requests'. (src/main/resources/templates/rust/errors/oauth2_authorization_code_error.mustache)
- swift: Make OAuth2AuthorizationCodeError conform to LocalizedError (errorDescription) and CustomStringConvertible with the canonical texts ('Must call exchangeCode() before making API requests'). (src/main/resources/templates/swift/errors/oauth2_authorization_code_error.mustache)
- elixir: 'Must call exchange_code() before making API requests' (.ex:196). (src/main/resources/templates/elixir/auth/oauth/oauth2_auth_code_authenticator.mustache)

**Tests.** OAuth2AuthorizationCodeAuthenticatorTest in all 12: assert the full message text (not just the type) for exchangeCode(""), exchangeCode("  ") and getAuthHeaders before exchange; go via errors.Is plus err.Error() equality, swift via error.localizedDescription. No spec change.

**Risk.** Error text only.

## D10 Token-request body and authorize-URL parameters are emitted in one fixed order

- Ledger ids: F292 | Theme: oauth-oidc | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Insertion-ordered and mutually identical: kotlin (mutableMapOf), csharp (Dictionary, insertion order while nothing is removed), php, python, ruby, node, dart. Not deterministic or not the same: java builds params with HashMap (OAuth2ClientCredentialsAuthenticator.java:88, OAuth2PasswordAuthenticator.java:~150, OAuth2AuthorizationCodeAuthenticator.java:146, manager refreshParams :96) so the body is grant_type, scope, client_secret, client_id (hash order); go oauth2_token_manager.go:170 url.Values.Encode() sorts keys, and go's authorize URLs (oauth2_authorization_code_authenticator.go:123, oauth2_implicit_authenticator.go) use params.Encode() sorted (client_id, redirect_uri, response_type, scope, state); elixir oauth2_token_manager.ex:184 URI.encode_query(map) sorted, authorize URL ex:144 sorted; rust oauth2_token_manager.rs:192 iterates a HashMap with RandomState (different order per process); swift OAuth2TokenManager.swift:208 params.map over a Dictionary (per-process seeded). rust and swift authorize URLs are built as ordered lists and java builds the authorize URL in code order, so those already match.

**Canonical behaviour.** The wire order is part of the byte-identical contract. Token body: grant_type, code, refresh_token, username, password, client_id, client_secret, redirect_uri, scope (then any other key, sorted). That is exactly the insertion order the seven ordered SDKs already produce for client_credentials (grant_type, client_id, client_secret, scope), password (grant_type, username, password, client_id, client_secret, scope), authorization_code (grant_type, code, client_id, client_secret, redirect_uri) and refresh (grant_type, refresh_token, client_id, client_secret). Authorize URL: response_type, client_id, redirect_uri, scope, state (implicit: no redirect_uri). Implementation: java uses LinkedHashMap; go/rust/swift/elixir, whose maps are unordered by contract, serialise through one helper that applies this key priority list (go and elixir build the authorize URL from an ordered list instead of url.Values / URI.encode_query(map)).

**Why.** (b) majority insertion order; (d) a key-priority helper needs no new dependency and keeps the manager API stable; semantically equivalent for form decoders but required for byte-level assertions and deterministic logs/cassettes.

**Rejected.** Change the callers to ordered containers everywhere (ordered slice of pairs in go, Vec in rust, KeyValuePairs in swift, keyword lists in elixir): cleaner but changes every token-manager signature in 4 languages. Sort alphabetically everywhere: would change the 7 SDKs that are already insertion-ordered and have tests asserting contains() only.

**Per language.**

- java: HashMap -> LinkedHashMap for all token params (client credentials, password, auth-code, manager refreshParams). (src/main/resources/templates/java/auth/oauth/{oauth2_client_credentials_authenticator,oauth2_password_authenticator,oauth2_auth_code_authenticator,oauth2_token_manager}.mustache)
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: fetchToken: build the body from a key list in priority order using the same url.QueryEscape-then-'+' encoding (not url.Values.Encode); BuildAuthorizationURL (auth-code and implicit): assemble an ordered []kv and join, keep the %20 replacement. (src/main/resources/templates/go/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator,oauth2_implicit_authenticator}.mustache)
- rust: fetch_token_locked: sort params.iter() by the priority list before joining (helper ordered_params); authorize URL already ordered. (src/main/resources/templates/rust/auth/oauth/oauth2_token_manager.mustache)
- swift: fetchToken: replace params.map by the priority-ordered iteration; authorize URL (URLComponents with ordered items) already ordered. (src/main/resources/templates/swift/auth/oauth/oauth2_token_manager.mustache)
- elixir: Manager: encode a keyword list built in priority order (URI.encode_query accepts a list); auth-code and implicit build_authorization_url: keyword list instead of a map. (src/main/resources/templates/elixir/auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator,oauth2_implicit_authenticator}.mustache)

**Tests.** Client-credentials, password, auth-code and implicit authenticator tests plus OAuth2TokenManagerTest in all 12: replace the contains() assertions with whole-string equality on the captured form body, e.g. 'grant_type=client_credentials&client_id=my-client-id&client_secret=my-client-secret&scope=read+write', 'grant_type=authorization_code&code=C&client_id=...&client_secret=...&redirect_uri=...', refresh 'grant_type=refresh_token&refresh_token=R&client_id=...&client_secret=...', and for authorize URLs 'response_type=code&client_id=...&redirect_uri=...&scope=a%20b&state=s'. No spec change.

**Risk.** Byte order of every OAuth token request changes for java/go/rust/swift/elixir; any user-side cassette or mock matching on the old order breaks.

## D11 An empty `state` is treated as absent when building authorize URLs

- Ledger ids: F293 | Theme: oauth-oidc | Still true: **yes** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** Emit '&state=' for state="": java (OAuth2AuthorizationCodeAuthenticator.java:128, OAuth2ImplicitAuthenticator.java:79 state != null), kotlin (.kt:82), csharp (.cs:98 is not null), php (.php:127 !== null), ruby (.rb:76 `if state`, "" is truthy), elixir (ex:135 `if state`). Omit it: python (.py:90 `if state`), node (.ts:94 `if (state)`), go (`state != ""`), rust (`!state.is_empty()`), swift (`!state.isEmpty`), dart (`state.isNotEmpty`). The OIDC authenticators forward state unchanged.

**Canonical behaviour.** state is emitted only when it is non-empty; "" means 'no state', identically for the auth-code builder, the implicit builder and the OIDC delegate. A null/nil/None state is likewise omitted (unchanged).

**Why.** (b) 6 of 12 already omit; (d) the other 6 can only do 'empty means absent' without an API break; RFC 6749 sec 4.1.1 state is RECOMMENDED and an empty value protects nothing.

**Rejected.** Emit state= for "" where the language can express null (the 6 today): keeps caller input byte-for-byte but cannot be done by go/rust/swift/dart (no null for a plain string), so it would need an API change in four SDKs for a parameter that carries no CSRF value when empty.

**Per language.**

- java: state != null && !state.isEmpty() in auth-code and implicit builders. (src/main/resources/templates/java/auth/oauth/{oauth2_auth_code_authenticator,oauth2_implicit_authenticator}.mustache)
- kotlin: !state.isNullOrEmpty() (src/main/resources/templates/kotlin/auth/oauth/{oauth2_auth_code_authenticator,oauth2_implicit_authenticator}.mustache)
- csharp: !string.IsNullOrEmpty(state) (src/main/resources/templates/csharp/auth/oauth/{oauth2_auth_code_authenticator,oauth2_implicit_authenticator}.mustache)
- php: $state !== null && $state !== '' (src/main/resources/templates/php/auth/oauth/{oauth2_auth_code_authenticator,oauth2_implicit_authenticator}.mustache)
- python: already canonical
- ruby: params['state'] = state unless state.nil? || state.empty? (src/main/resources/templates/ruby/auth/oauth/{oauth2_auth_code_authenticator,oauth2_implicit_authenticator}.mustache)
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: add `state not in [nil, ""]` condition (ex:135 and the implicit builder). (src/main/resources/templates/elixir/auth/oauth/{oauth2_auth_code_authenticator,oauth2_implicit_authenticator}.mustache)

**Tests.** OAuth2AuthorizationCodeAuthenticatorTest, OAuth2ImplicitAuthenticatorTest and OpenIdConnectAuthenticatorTest in all 12: row 'empty state is omitted' asserting the URL does not contain 'state' and equals the URL built with a null/absent state; keep the existing 'state=csrf' row. No spec change.

**Risk.** None expected; only affects callers passing an empty string.

## D12 Synchronous auth-header accessor on token-based OAuth flows: fail loudly, never an empty map

- Ledger ids: F231 F290 | Theme: oauth-oidc | Still true: **partly** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** F231 is FIXED: node getAuthHeaders() on client-credentials/password/auth-code/OIDC now throws 'OAuth2 authenticators require async token exchange; use getAuthHeadersAsync() instead.' (oauth2-client-credentials-authenticator.ts:83, oauth2-password-authenticator.ts:92, oauth2-authorization-code-authenticator.ts:152, openid-connect-authenticator.ts:227), dart authHeaders() throws StateError with the same text (client-credentials .dart:57-64, password :68-73, auth-code :121-127 after the 'Must call exchangeCode() ...' precondition, OIDC :82-88), csharp GetAuthHeaders() throws InvalidOperationException (CC/password/auth-code with the same text). Remaining F290 residue: csharp OpenIdConnectAuthenticator.cs:231 uses a different sentence ('OpenIdConnectAuthenticator requires async discovery and token exchange; use GetAuthHeadersAsync() instead.'); go and rust infallible accessors return an empty map on error: go AuthHeaders() (oauth2_client_credentials_authenticator.go:125, oauth2_authorization_code_authenticator.go:~186, oauth2_password_authenticator.go:~143, openid_connect_authenticator.go:147) and rust auth_headers() (openid_connect_authenticator.rs, doc 'or an empty map when discovery or the token request fails'), while the SDK's own transport uses go AuthHeadersOrError / rust try_auth_headers which report the real error; go's password and auth-code variants additionally call log.Printf (oauth2_password_authenticator.go:148, oauth2_authorization_code_authenticator.go:192) whereas client-credentials and OIDC stay silent. java/kotlin/php/python/ruby/swift/elixir have one accessor that does the real work.

**Canonical behaviour.** In languages where the accessor can raise, the sync accessor on token-fetching flows raises one fixed text: 'OAuth2 authenticators require async token exchange; use <asyncMethod>() instead.' (csharp OIDC adopts this text too; node/dart/csharp already do). go AuthHeaders() and rust auth_headers() are FORCED infallible by the Authenticator interface/trait (no exceptions; the typed-error variants AuthHeadersOrError / try_auth_headers are what the SDK uses): they return an empty map on failure, documented as such, and must not have side effects, so go drops the log.Printf in the password and auth-code variants (a library must not write to the process logger). Add one sentence for go/rust to the AGENTS.md 'Auth-header method shape' paragraph (rust's try_auth_headers is mentioned there, go's AuthHeadersOrError is not).

**Why.** (c) a sync call must never silently send an unauthenticated request where the language allows an exception; (b) node/dart/csharp already raise; (a) go/rust cannot raise by interface design, so it is documented as forced instead of forced into a panic.

**Rejected.** Panic in go/rust infallible accessors: non-idiomatic and would turn a missing token into a process crash. Make go log everywhere: a global-logger side effect from a library, and error text may carry OP response data. Make node/dart sync accessors return a cached token when one exists: works only after a first async call, an order-dependent behaviour.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: OpenIdConnectAuthenticator.GetAuthHeaders(): use 'OAuth2 authenticators require async token exchange; use GetAuthHeadersAsync() instead.' (.cs:229-233). (src/main/resources/templates/csharp/auth/oauth/openid_connect_authenticator.mustache)
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: Infallible AuthHeaders() stays (FORCED); remove log.Printf from the password and auth-code variants so all four are silent and documented identically ('empty map when the token cannot be obtained; use AuthHeadersOrError'). (src/main/resources/templates/go/auth/oauth/{oauth2_password_authenticator,oauth2_auth_code_authenticator}.mustache)
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Existing: node/dart/csharp already assert the throw in their CC/password/auth-code/OIDC tests (verify OIDC rows exist, csharp OIDC row must assert the new text). Add to the go and rust OAuth2 authenticator tests: 'infallible accessor returns an empty map when the token request fails while the Or-Error variant returns the OAuth2ServerError' for all four flows. No spec change.

**Risk.** Removing go's log lines drops a (rarely read) diagnostic; AuthHeadersOrError still reports the error.

## D13 An ApiError for a response that arrived but was unusable (decompress failure, redirect refusals) carries the real response headers

- Ledger ids: F099 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Probed decompression failure plus the three redirect refusals (non-http scheme, https->http body replay, too many).
java: null headers on all four (ApiException.java:76 hard-codes null; DefaultApiClient.java:499/518/581 pass null,null; :620 uses (int,String,Throwable)).
kotlin: same, null on all four (DefaultApiClient.kt:213/239/313/354).
csharp: no headers, so ResponseHeaders stays null (DefaultApiClient.cs:466/504/614/665/703; ApiException.cs:38 ctor has no headers).
node: null on all (abstract-api-client.ts:345/402/449/502/598 pass null,null,null).
swift: nil on all (DefaultApiClient.swift:432 and the delegate at 1139/1156/1207).
elixir: nil on all (default_api_client.ex:389-473 and 863/883/922 pass only message+status_code).
php: decompress passes real $responseHeaders (AbstractApiClient.php:312); the three redirect throws (:202/231/283) omit them so they become [] from the ctor default at ApiException.php:64. [] says 'response arrived with no headers', which is false.
python: real headers on all (default_api_client.py:497/624/651/724).
ruby: real headers on all via unusable_response (default_api_client.rb:424-429).
dart: decompress passes streamedResponse.headers (abstract_api_client.dart:443); the redirect throws (:266/315/372) pass none, so null.
go: real lower-cased headers on all (default_api_client.go:179-185 and 206-212).
rust: decompress passes Some(resp_headers) (default_api_client.rs:475); redirects pass None (:307/333/413).
Also node's leaf/ClientError ctors default responseHeaders to {} (bad-request-error.ts:17 etc.), which erases the null signal for hand-built errors.

**Canonical behaviour.** Whenever an HTTP response arrived and the SDK then cannot use it, the thrown ApiError/ApiException carries (a) the real status, (b) the real response headers: the same lower-cased map the success path puts in ApiHttpResponse.headers, never null and never a substitute empty map, (c) responseBody/errorBody null (body is unusable by definition), and (d) the underlying failure as cause where one exists. This covers decompression failure and all three redirect refusals in all 12. Null/nil/None headers stay reachable only through the transport-failure constructor, exactly as the AGENTS.md 'responseHeaders null vs empty is a signal' rule says. Constructors must never default headers to an empty map: php ApiException::__construct default becomes null, and node ClientError and every leaf ctor default becomes null.

**Why.** Criterion (a) AGENTS.md:194 (null means no response arrived). (b) python, ruby, go already do this on every path; php, dart, rust do it on decompress. (c) callers that branch on headers == null to decide 'retry transport' vs 'inspect the response' get the correct answer. (d) every language has the response headers in scope at each throw site (verified at each cited line) so no new dependency.

**Rejected.** (1) Null headers when the body is unusable: rejected, contradicts AGENTS.md:194-203 and hides x-request-id and retry-after from the caller. (2) Pass an empty map when headers are not at hand: rejected, it is the exact 'default to empty map for consistency' that AGENTS.md forbids. (3) Make decompress failure a transport/Network error: rejected, a response did arrive and the AGENTS rule is that it is an ApiError carrying its status.

**Per language.**

- java: Replace ApiException(int,String,Throwable) (ApiException.java:62-78) by (int,String,@Nullable Map<String,String> headers,Throwable cause); at DefaultApiClient.java:620 pass the lower-cased response header map (build it before the decompress call); at :499/:518/:581 pass headers instead of null,null. (templates/java/api_error.mustache, templates/java/default_api_client.mustache)
- kotlin: Same as java: add a headers parameter to the (status,message,cause) constructor and pass responseHeaders at DefaultApiClient.kt:213/239/313/354. (templates/kotlin/api_error.mustache, templates/kotlin/default_api_client.mustache)
- csharp: ApiException(int,string,Exception) gets a leading IReadOnlyDictionary<string,string> headers parameter (copy into Dictionary); pass the lower-cased headers at DefaultApiClient.cs:466/504/614/665/703 (the two decompress sites included). Add headers to the 2-arg (status,message) throws, which today have no way to carry them. (templates/csharp/api_error.mustache, templates/csharp/default_api_client.mustache)
- php: Pass $responseHeaders to the three redirect throws (AbstractApiClient.php:202/231/283); change ApiException ctor default at ApiException.php:64 from [] to null (and the leaf exception ctors that forward it). (templates/php/abstract_api_client.mustache, templates/php/api_error.mustache)
- python: already canonical
- ruby: already canonical
- node: Pass the lower-cased header record at abstract-api-client.ts:345/402/449/502/598 instead of null; change the default of responseHeaders from {} to null in client-error.ts and every leaf ctor (bad-request-error.ts:17, conflict-error.ts:17, internal-server-error.ts:17, ...). (templates/node/abstract_api_client.mustache, templates/node/errors/*.mustache)
- dart: Build the lower-cased responseHeaders map before the redirect branch and pass responseHeaders: to the throws at abstract_api_client.dart:266/315/372; in the decompress rethrow (:443) pass that same lower-cased map instead of raw streamedResponse.headers. (templates/dart/abstract_api_client.mustache)
- go: already canonical
- rust: Pass Some(headers) in the three redirect ApiError::new calls (default_api_client.rs:307/333/413); keep Some(resp_headers) on decompress. (templates/rust/default_api_client.mustache)
- swift: Pass responseHeaders: (lower-cased from HTTPURLResponse.allHeaderFields) to the ApiError built at DefaultApiClient.swift:432 and the three redirectErrors.set(ApiError(...)) sites (1139/1156/1207). (templates/swift/default_api_client.mustache)
- elixir: Thread the response headers map into gunzip_body/inflate/brotli/zstd helpers and the three redirect raises (default_api_client.ex:389-473, 863, 883, 922) and set response_headers: on every ApiError raise. (templates/elixir/default_api_client.mustache)

**Tests.** Extend the existing decompress-failure and redirect-refusal tests in each language's DefaultApiClientUnitTest/default_api_client_unit_test (java DefaultApiClientUnitTest.java:1032/1046/1148 area; same files for kotlin, csharp, php, python, ruby, node, dart, go (pkg/default_api_client_unit_test.go), rust, swift, elixir). The local test server already sends a response; add a custom header (x-request-id: abc) to it and assert responseHeaders is non-null and contains it, plus responseBody == null, for all four paths. Add a row to each ApiExceptionTest/api_error test: an error built with the transport-failure constructor has null headers, one built from a response with no headers has an empty map (php/node default regression). No petstore YAML needed.

**Risk.** Callers that treated null headers on these errors as 'no response' now see a map (that is the intended fix). php/node default change to null: any caller doing count($e->getResponseHeaders()) on a hand-built error would now hit null; guard with ?? [] in generated code that reads it.

## D14 Error-contract test matrix: lock OAuth2ServerError message, deep-body cap, exact rendering, BOM and scalar bodies in every language

- Ledger ids: F100 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** OAuth2ServerError 'Token request failed with status N: ...' three-form message is asserted only in go (go/test/oauth2_token_manager_test.go:724-781); the other 11 produce it (java OAuth2ServerException.java, python oauth2_server_exception.py:37-41, rust, php, csharp, kotlin, swift, node, ruby, dart, elixir) with no assertion. The 'refuses a deeply nested error body' test exists in python (test_api_error.py:195), ruby, node, dart, go, rust and php (ApiExceptionTest.php:202); it is absent in java, kotlin, csharp, swift and elixir. Exact rendered string (including 'Response headers:') is asserted in java, kotlin, csharp, php, node and ruby only (grep count of 'Response headers' = 1 in each); python, dart, go, rust, swift and elixir assert only non-empty. No language tests a BOM-prefixed error body or a scalar JSON body except swift (scalar, 2 hits) and rust (BOM in object_serializer unit tests only). Nothing asserts the TYPE of a getTypedErrorBody failure.

**Canonical behaviour.** No new behaviour: every decision in this theme lands with its test in the owner file named below, and these four additional rows are added to each language's existing ApiError test file as table rows (ApiExceptionTest.java/.kt/.cs/.php, test_api_error.py, api_error_test.rb/.dart/.go/.rs/.exs, api-error.test.ts, ApiErrorTests.swift): (1) OAuth2ServerError message, three rows (raw body, code, code+description) asserted by exact string in the existing oauth2 token-manager or OAuth2ServerError test of each language, using go's table at oauth2_token_manager_test.go:747-781 as the model; (2) 'refuses a body nested deeper than the cap' for java, kotlin, csharp, swift, elixir; (3) exact rendered-string row with two headers (unsorted input) and an empty-headers/empty-body row that asserts the Response-headers line is omitted, replacing every 'non-empty' assertion; (4) BOM-prefixed body populates errorBody (F268), scalar JSON body ("oops" and 42) leaves errorBody per the shared rule, and getTypedErrorBody failure type.

**Why.** Criterion (a): standing test-placement rule (mirror file, table rows, every fix backed). The gaps are why F099/F245/F267/F268 survived; closing them here is cheaper than new suites.

**Rejected.** One cross-language 'contract' spec file: rejected, the global test-placement rule is unit rows in the mirror file per language and no contract suites. Extending petstore /test/status/* with a scalar-error route: rejected, the unit rows reach the same code (fromResponse) with no chasm round-trip.

**Per language.**

- java: Add depth-cap row and exact-render empty-headers row to ApiExceptionTest.java; add OAuth2ServerException message rows (auth/oauth test dir).
- kotlin: Same rows in ApiExceptionTest.kt and the oauth2 test.
- csharp: Add depth-cap row (ApiExceptionTest.cs currently has none; line 225 'IsNested' is unrelated) and OAuth2ServerException message rows.
- php: Depth cap exists (ApiExceptionTest.php:202); add OAuth2ServerException message rows and BOM/scalar rows.
- python: Tighten non-empty assertions to exact strings; add OAuth2ServerException rows to the oauth2 test.
- ruby: Add empty-headers/empty-body exact rows (api_error_test.rb:42 area) and OAuth2ServerError rows.
- node: Add the empty-string-body row and OAuth2ServerError rows (api-error.test.ts).
- dart: Replace non-empty assertions by exact strings; add OAuth2ServerException rows.
- go: OAuth2 message already asserted; tighten api_error_test.go:247 area to assert the raw-error type of GetTypedErrorBody failure; add exact-render rows.
- rust: Replace is_empty assertion at api_error_test.rs:65 by exact-string rows; add OAuth2ServerError rows.
- swift: Add depth-cap row and exact-render rows in ApiErrorTests.swift; add OAuth2ServerError rows.
- elixir: Add depth-cap row, exact-render rows and OAuth2ServerError rows in api_error_test.exs.

**Tests.** This finding is the test list itself. Files per language: ApiError test file listed above plus the existing OAuth2TokenManager/OAuth2ServerError test file. No petstore YAML.

## D15 Serializer error messages: one serialize message, one deserialize message, one depth-cap message

- Ledger ids: F245 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Serialize failure: java 'Failed to serialize object to JSON' no detail (ObjectSerializer.java:107); csharp same (ObjectSerializer.cs:81-94); swift same (ObjectSerializer.swift:155/177); python/ruby/php/dart/node 'Failed to serialize object to JSON: <detail>'; go and rust the same text lower-cased with detail (object_serializer.go:85, object_serializer.rs:358); kotlin 'Failed to encode request body: <detail>' (ObjectSerializer.kt:278/305); elixir 'Failed to serialize: <inspect>' (object_serializer.ex:157).
Deserialize failure: java/csharp/python/ruby/php 'Failed to deserialize JSON to <type>[: detail]' (java: type only, no detail); swift 'Failed to deserialize JSON' (:255); dart 'Failed to deserialize JSON: <e>' plus 'JSON list'/'JSON map' variants (object_serializer.dart:170/199/260/284/298); go and rust lower-case 'failed to deserialize JSON: <e>'; node 'Failed to deserialize object: <e>' (object-serializer.ts:720); kotlin 'Failed to decode response body: <e>' (ObjectSerializer.kt:435); elixir has no wrapped message.
Depth cap: python, ruby, node, dart, go, rust, swift, kotlin, elixir use the byte scan and 'JSON nesting depth N exceeds limit 1000'; java and csharp let the parser enforce 1000 and the library text only appears inside the cause; php relies on json_decode depth ('Maximum stack depth exceeded' via JsonException at php/lib/ObjectSerializer.php:385).

**Canonical behaviour.** Serialize failure: `Failed to serialize object to JSON: <detail>`. Deserialize failure: `Failed to deserialize JSON: <detail>` (no `to <type>`, no list/map/object variants). Depth cap: `JSON nesting depth <N> exceeds limit 1000`, produced by the SDK's own pre-scan (not by the parser) in all 12, thrown as the SDK SerializationException/Error. <detail> is the underlying failure's message (java getMessage, kotlin e.message, csharp ex.Message, swift localizedDescription, elixir Exception.message(reason) not inspect/1). The SDK exception type stays the same as today and the original exception stays attached as cause. Only go lower-cases the first letter (ST1005, forced); rust is capitalised like every other message in the crate (rust has no lowercase rule, the current lowercase is drift).

**Why.** (a) R8-7 states these messages are harmonised, the goldens disagree. (b) 'Failed to serialize object to JSON: <detail>' is already the text of 8 SDKs, the 'deserialize JSON' prefix is shared by 7 once type is dropped. (c) detail in message avoids silent loss of the reason in logs. (d) pre-scan helper exists; php can reuse JSON_DECODE_DEPTH constant.

**Rejected.** Keep 'to <type>': rejected, only 5 of 12 carry it, a type name is not language-neutral (java Type.toString vs python repr) and the cause already holds the type. Let java/csharp/php keep parser-enforced depth with library text: rejected, a byte-scan helper already exists in nine SDKs (kotlin ObjectSerializer.kt:423, swift :239) and needs no dependency, so it is not forced. Put the detail only in the cause chain: rejected, java/csharp/swift logs print the top message only and lose the reason.

**Per language.**

- java: ObjectSerializer.java:107 add ': ' + e.getMessage(); :140 -> 'Failed to deserialize JSON: ' + e.getMessage(); add a jsonMaxDepth pre-scan (as kotlin) before readValue and throw the canonical depth message. (templates/java/object_serializer.mustache)
- kotlin: ObjectSerializer.kt:278/305 -> 'Failed to serialize object to JSON: ${e.message}'; :435 and parseToJsonElement 'Failed to parse JSON' -> 'Failed to deserialize JSON: ${e.message}'. Depth already canonical (:391/:423). (templates/kotlin/object_serializer.mustache)
- csharp: ObjectSerializer.cs:81/85/94 append $': {e.Message}'; :127/131/137 -> $'Failed to deserialize JSON: {e.Message}'; add depth pre-scan before JsonSerializer.Deserialize (reuse MaxJsonDepth) throwing the canonical text. (templates/csharp/object_serializer.mustache)
- php: ObjectSerializer.php:385 drop ' to ' . $class; add a depth pre-scan before json_decode and throw SerializationException with the canonical depth text instead of relying on JsonException. (templates/php/object_serializer.mustache)
- python: object_serializer.py:286 drop the ' to {target_type}'. Serialize (:211) and depth (:237) already canonical. (templates/python/object_serializer.mustache)
- ruby: object_serializer.rb:170 drop ' to #{target_type}'. Serialize (:104) and depth (:52) already canonical. (templates/ruby/object_serializer.mustache)
- node: object-serializer.ts:720 'Failed to deserialize object:' -> 'Failed to deserialize JSON:'. Serialize (:480) and depth (:344) already canonical. (templates/node/object_serializer.mustache)
- dart: Collapse 'JSON list'/'JSON map' variants (object_serializer.dart:199/260/284) to 'Failed to deserialize JSON: $e'. (templates/dart/object_serializer.mustache)
- go: Already canonical modulo the forced lower-case first letter; document that as the single FORCED exception. No change. (templates/go/object_serializer.mustache)
- rust: Capitalise: object_serializer.rs:350/358/417/424/439 'failed to ...' -> 'Failed to ...'; depth already canonical (:396). (templates/rust/object_serializer.mustache)
- swift: ObjectSerializer.swift:155/177 add ': \(error.localizedDescription)'; :255 -> 'Failed to deserialize JSON: ...'. Depth already canonical (:239). (templates/swift/object_serializer.mustache)
- elixir: object_serializer.ex:157 -> 'Failed to serialize object to JSON: #{Exception.message(reason)}'; make deserialize failures raise the same prefix 'Failed to deserialize JSON: ...' (depth already canonical :126). (templates/elixir/object_serializer.mustache)

**Tests.** ObjectSerializerTest/test_object_serializer/object_serializer_test in every language (templates/<lang>/test/ObjectSerializerTest.mustache etc.): add three rows asserting the full message: un-serializable value (NaN/Infinity or a self-referencing/unsupported object where the language allows) -> startsWith 'Failed to serialize object to JSON: '; malformed JSON -> startsWith 'Failed to deserialize JSON: '; a 1001-deep payload -> equals 'JSON nesting depth 1001 exceeds limit 1000' (go: lower-case first letter for the first two). No petstore YAML.

## D16 Redirect-refusal messages: one wording for scheme, https->http replay and too-many

- Ledger ids: F260 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Non-http scheme: java/kotlin/python/node 'Refusing to follow redirect to non-HTTP(S) URL: <url>'; csharp/ruby/dart/rust '...non-http(s) URL: <url>'; swift '...non-HTTP(S) Location: <url>'; php "Redirect to unsupported scheme '<s>' in Location: <url>" (AbstractApiClient.php:204); elixir 'refusing ... non-http(s) Location (<st> redirect from a to b)' (default_api_client.ex:865); go 'redirect refused: refusing to follow redirect to non-HTTP scheme "x"' (default_api_client.go:354 wrapped at :183).
Too many: java 'Too many redirects (exceeded maxRedirects) following <url>' (:583); kotlin/php/node '(exceeded maxRedirects=N)'; python '(exceeded max_redirects=N)'; csharp/ruby/dart/swift 'Exceeded maximum number of redirects (N)'; rust/elixir 'too many redirects (exceeded N)'; go 'redirect refused: stopped after N redirects' (:351).
Downgrade: ten distinct strings, e.g. java ':...HTTPS->HTTP redirect: <next>' (:520), kotlin/rust '<cur> -> <next>' (kt:239, rs:333), python 'HTTPS -> HTTP ...', node '(from a to b)', php 'HTTPS->HTTP downgrade redirect to <next>', csharp '...downgrade redirect to: <next>', swift 'an HTTPS -> HTTP redirect (TLS downgrade) to', ruby 'Refusing to replay body across HTTPS -> HTTP', elixir lower-case, go 'refusing to replay ... HTTPS→HTTP downgrade on %d redirect to %s' (:386).

**Canonical behaviour.** Three strings, identical in all 12 except go's lower-case first letter (ST1005, forced):
1. `Refusing to follow redirect to non-HTTP(S) URL: <next-url>`
2. `Refusing to replay request body across HTTPS->HTTP redirect: <current-url> -> <next-url>`
3. `Too many redirects (exceeded <N>)` where N is the integer redirect limit, with no option name and no URL.
Go drops the 'redirect refused: ' wrapper and the net/http 'Get "url":' prefix (unwrap *url.Error via errors.As, or return typed sentinels from CheckRedirect) so its text is exactly the lower-cased canonical.

**Why.** (a) AGENTS R8-7 claims these are harmonised. (b) 'Refusing to follow redirect to non-HTTP(S) URL' is used by 4, the '<cur> -> <next>' form by kotlin and rust and is the only one that names both hops, which matters for the current-hop anchoring rule already in AGENTS.md. (d) trivial per language; go needs the *url.Error unwrap.

**Rejected.** 'Exceeded maximum number of redirects (N)' (4 SDKs): rejected, the codebase comments (csharp DefaultApiClient.cs:608, node :442, rust :409) already call 'too many redirects' the canonical, and it matches the ledger R-D1 wording. Including the option name (maxRedirects=N): rejected, the identifier is language-specific (maxRedirects vs max_redirects) so the string cannot be shared. Including the request URL in too-many (java only): rejected, URL may carry credentials and the other 11 omit it.

**Per language.**

- java: DefaultApiClient.java:520 add current URL ('... redirect: ' + currentUri + ' -> ' + redirectUri); :583 -> 'Too many redirects (exceeded ' + maxRedirects + ')'. (templates/java/default_api_client.mustache)
- kotlin: DefaultApiClient.kt:313 -> 'Too many redirects (exceeded N)'; others already canonical. (templates/kotlin/default_api_client.mustache)
- csharp: DefaultApiClient.cs:468 'non-http(s)' -> 'non-HTTP(S)'; :506 -> 'across HTTPS->HTTP redirect: {cur} -> {next}'; :616 -> 'Too many redirects (exceeded {maxRedirects})'. (templates/csharp/default_api_client.mustache)
- php: AbstractApiClient.php:204 -> 'Refusing to follow redirect to non-HTTP(S) URL: $nextUrl'; :233 -> '... redirect: $currentUrl -> $nextUrl'; :285 -> 'Too many redirects (exceeded $maxRedirects)'. (templates/php/abstract_api_client.mustache)
- python: default_api_client.py:648-650 -> canonical downgrade text; :723 -> 'Too many redirects (exceeded {max_redirects})'. (templates/python/default_api_client.mustache)
- ruby: default_api_client.rb:185 'non-http(s)' -> 'non-HTTP(S)'; :217 canonical downgrade text; :254 -> 'Too many redirects (exceeded #{max_redirects})'. (templates/ruby/default_api_client.mustache)
- node: abstract-api-client.ts:404-405 -> 'Refusing to replay request body across HTTPS->HTTP redirect: ${currentUrl} -> ${nextUrl}'; :451 -> `Too many redirects (exceeded ${maxRedirects})`. (templates/node/abstract_api_client.mustache)
- dart: abstract_api_client.dart:269 'non-HTTP(S)'; :318 canonical downgrade; :374 -> 'Too many redirects (exceeded $maxRedirects)'. (templates/dart/abstract_api_client.mustache)
- go: CheckRedirect (default_api_client.go:351/354/386) return the lower-cased canonical strings; at :183 stop prefixing 'redirect refused: ' and unwrap *url.Error so the ApiError message is exactly the lower-cased canonical. Lower-case first letter is the FORCED exception (ST1005). (templates/go/default_api_client.mustache)
- rust: default_api_client.rs:307 'non-HTTP(S)'; :413 -> 'Too many redirects (exceeded {})'; :333 already '<cur> -> <next>'. (templates/rust/default_api_client.mustache)
- swift: DefaultApiClient.swift:1141 -> 'Too many redirects (exceeded N)'; :1158 'URL:' instead of 'Location:'; :1209 canonical downgrade text with current and next URL. (templates/swift/default_api_client.mustache)
- elixir: default_api_client.ex:865/885/923 -> capitalised canonical strings (drop the '(status redirect from a to b)' detail; downgrade uses '<cur> -> <next>'). (templates/elixir/default_api_client.mustache)

**Tests.** Same redirect tests as F099 (DefaultApiClientUnitTest per language, java :1032/:1046, plus the downgrade test): replace the contains('non-http')/loose assertions by exact message equality (go: lower-case). Combine with the F099 header assertion in the same test row. Petstore has no YAML dependency; redirect routes are provided by the chasm mock.

## D17 Rendered 'Response headers:' line: one syntax (compact JSON, keys sorted)

- Ledger ids: F267 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Rendering of the headers map: java and kotlin and csharp '{k=v}' (java ApiException.java:255 uses Map.toString of a Map.copyOf, whose order is salted per JVM run; csharp ApiException.cs:171 builds '{k=v, ...}'); php and node JSON compact (ApiException.php:217, api-error.ts:174); python repr '{'k': 'v'}' (errors/__init__.py:181); ruby '{"k" => "v"}' (api_error.rb:122); dart '{k: v}' (api_exception.dart:203); go '%v' -> 'map[k:v]' (api_error.go:182); rust '{:?}' (api_error.rs:281); swift '["k": "v"]' (ApiError.swift:61); elixir inspect '%{...}' (api_error.ex:97). The line is omitted when headers are null or empty in all 12 (verified in each cited line).

**Canonical behaviour.** `Response headers: <json>` where <json> is a compact JSON object (no spaces after ':' or ','), keys sorted ascending by code point (they are already lower-cased), string values escaped per RFC 8259 but with '/' not escaped, non-ASCII emitted verbatim, and without HTML escaping. Example: `Response headers: {"content-type":"application/json","x-request-id":"abc"}`. Omit the whole line when headers are null or empty (unchanged). Sorting is applied at render time so java's per-run order disappears.

**Why.** (b) node and php already emit compact JSON. (c) deterministic and unambiguous for log scrapers. (d) every language has a JSON encoder already linked into the SDK (Jackson, kotlinx, System.Text.Json, json, JSON, dart:convert, encoding/json, serde_json, Foundation JSONSerialization, Jason).

**Rejected.** `k: v` pairs joined by ', ': rejected, ambiguous because header values legitimately contain commas. One header per line: rejected, breaks single-line log scraping of the existing 'Response headers:' label. Insertion order JSON: rejected, java Map.copyOf, php arrays and go maps do not all preserve it, so it is not achievable uniformly.

**Per language.**

- java: ApiException.java:255 render via the SDK's Jackson ObjectMapper with ORDER_MAP_ENTRIES_BY_KEYS (or a TreeMap) instead of Map.toString. (templates/java/api_error.mustache)
- kotlin: ApiException.kt:188 render sorted JsonObject via kotlinx (buildJsonObject over headers.toSortedMap()). (templates/kotlin/api_error.mustache)
- csharp: ApiException.cs:168-172 use JsonSerializer.Serialize(new SortedDictionary<string,string>(headers), options with JavaScriptEncoder.UnsafeRelaxedJsonEscaping). (templates/csharp/api_error.mustache)
- php: ApiException.php:217 ksort a copy and pass JSON_UNESCAPED_SLASHES|JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR. (templates/php/api_error.mustache)
- python: errors/__init__.py:181 json.dumps(headers, sort_keys=True, separators=(',', ':'), ensure_ascii=False). (templates/python/errors/*.mustache)
- ruby: api_error.rb:122 JSON.generate(response_headers.sort.to_h) (compact, ruby does not escape '/'). (templates/ruby/errors/api_error.mustache)
- node: api-error.ts:174 sort keys before JSON.stringify (JSON.stringify(Object.fromEntries(Object.entries(h).sort()))). (templates/node/errors/api-error.mustache)
- dart: api_exception.dart:203 jsonEncode(SplayTreeMap.of(headers)). (templates/dart/errors/*.mustache)
- go: api_error.go:182 json.Encoder with SetEscapeHTML(false) over the map (encoding/json sorts map keys), trim trailing newline. (templates/go/api_error.mustache)
- rust: api_error.rs:281 serde_json::to_string(&BTreeMap::from_iter(headers)) (HashMap order is random). (templates/rust/errors/api_error.mustache)
- swift: ApiError.swift:61 JSONSerialization.data(withJSONObject:options:[.sortedKeys,.withoutEscapingSlashes]). (templates/swift/errors/api_error.mustache)
- elixir: api_error.ex:97 Jason.encode!(Jason.OrderedObject.new(Enum.sort(headers))) (plain maps >32 keys are unordered). (templates/elixir/errors/api_error.mustache)

**Tests.** The exact-render rows from F100 in each ApiError test file: build an error with headers {'x-request-id':'abc','content-type':'application/json'} supplied in unsorted order and a value containing a comma and a slash, assert the exact `Response headers: {...}` suffix; second row empty map -> no Response headers line. Files: ApiExceptionTest.java/.kt/.cs/.php, test_api_error.py, api_error_test.rb/.dart/.go/.rs/.exs, api-error.test.ts, ApiErrorTests.swift. No petstore YAML.

**Risk.** Anyone who parsed the old per-language format breaks; the log line is not a contract anywhere in AGENTS.md. Header values such as set-cookie are already printed today; redaction is a separate decision and is not changed here.

## D18 Strip a leading BOM inside the shared JSON parse helper so the error-body path matches the success path

- Ledger ids: F268 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Error-body path with a BOM-prefixed body: java goes through ObjectSerializer.deserialize which strips (ObjectSerializer.java:128), swift strips (ObjectSerializer.swift:229), go strips (api_error.go:31 TrimPrefix), rust strips (object_serializer.rs:373 via deserialize) = populated errorBody. kotlin: ApiException.kt:131 calls parseToJsonElement (ObjectSerializer.kt:380-398) which does not strip, only deserialize (:416) does; csharp: ApiException.cs:90 JsonDocument.Parse raw; php: ApiException.php:103 json_decode raw; python/ruby/dart/elixir: parse_json raw (the strip lives in deserialize only: python object_serializer.py:271, ruby :147, dart _stripBom at :152-292 but not in parseJson :130, elixir :188-193 but not parse_json :120); node: no BOM strip anywhere (object-serializer.ts:274 JSON.parse), so even the success path rejects a BOM payload. All eight yield errorBody == null.

**Canonical behaviour.** Every JSON parse entry point strips exactly one leading U+FEFF (UTF-8 bytes EF BB BF in byte-based languages) before parsing, and the strip lives in the shared parse helper (parse_json / parseJson / parseToJsonElement / the csharp and php equivalents) that both the success path and fromResponse call, so the two paths cannot drift again. Node gains the strip on both paths.

**Why.** (a) RFC 8259 s8.1 permits a parser to ignore a BOM; (b) 11 success-path deserializers already strip and each carries the Windows rationale comment; (c) rejecting a body silently turns errorBody into null, i.e. silent data loss for the caller; (d) one-line change per helper.

**Rejected.** Strip only in fromResponse: rejected, node's success path would still reject a BOM payload and the next caller of the helper regresses again. Reject BOM everywhere per RFC 8259 s8.1 (which allows parsers to ignore it): rejected, 11 success paths already tolerate it, Windows-generated payloads are the stated reason.

**Per language.**

- java: already canonical
- kotlin: Move the strip into parseToJsonElement (ObjectSerializer.kt:380) and have deserialize call it, so ApiException.kt:131 benefits. (templates/kotlin/object_serializer.mustache)
- csharp: Route ApiException.cs:90 through an ObjectSerializer helper that strips (as ObjectSerializer.cs:113 does) instead of calling JsonDocument.Parse directly. (templates/csharp/api_error.mustache, templates/csharp/object_serializer.mustache)
- php: ApiException.php:103 call a shared ObjectSerializer::decodeJson that strips (ObjectSerializer.php:417 logic) rather than json_decode directly. (templates/php/api_error.mustache, templates/php/object_serializer.mustache)
- python: Move the startswith('﻿') strip from deserialize (object_serializer.py:271) into parse_json. (templates/python/object_serializer.mustache)
- ruby: Move the strip from deserialize (object_serializer.rb:147) into parse_json (:46-52). (templates/ruby/object_serializer.mustache)
- node: Add the strip to ObjectSerializer.parseJson (object-serializer.ts:274), which both deserialize and api-error.ts:99/152 call. (templates/node/object_serializer.mustache)
- dart: Call _stripBom inside parseJson (object_serializer.dart:130). (templates/dart/object_serializer.mustache)
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: Move the <<0xEF,0xBB,0xBF,rest>> match (object_serializer.ex:188-193) into parse_json (:120). (templates/elixir/object_serializer.mustache)

**Tests.** One row in each ApiError test file (ApiExceptionTest.java/.kt/.cs/.php, test_api_error.py, api_error_test.rb/.dart/.go/.rs/.exs, api-error.test.ts, ApiErrorTests.swift): fromResponse(404, '﻿{"code":1}') yields a populated errorBody (go/rust/java/swift included so the 4 already-correct SDKs stay correct). Node also gets a success-path row in object-serializer.test.ts. No petstore YAML.

## D19 Credential emptiness guard: explicit ASCII whitespace set, and strip the 'Bearer ' prefix before the emptiness check

- Ledger ids: F274 F275 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** F274: each SDK uses its stdlib trim in the api-key and bearer constructors: java Character.isWhitespace/isBlank (BearerAuthenticator.java:23), kotlin isBlank (BearerAuthenticator.kt:24), csharp IsNullOrWhiteSpace, php trim (ApiKeyAuthenticator.php:32; strips NUL, not FF), python str.strip (api_key_authenticator.py:30; bearer_authenticator.py:28), ruby strip (api_key_authenticator.rb:64), node trim, go strings.TrimSpace (bearer_authenticator.go:53), dart trim (api_key_authenticator.dart:40), rust trim (bearer_authenticator.rs:41), swift .whitespacesAndNewlines (ApiKeyAuthenticator.swift:31, BearerAuthenticator.swift:27), elixir String.trim (api_key_authenticator.ex:53, bearer_authenticator.ex:45). Results differ for \f, \x1f, U+00A0, U+0085, NUL (per the ledger probe).
F275: in all 12 the 'Bearer ' prefix de-duplication runs in getAuthHeaders (java BearerAuthenticator.java:55-64, python :43-49, go :89, rust :83-87, elixir :70-82, swift :55-60), after the empty guard, so token 'Bearer ' or 'bearer    ' passes construction and the header becomes 'Authorization: Bearer ' / 'Bearer    ' (credential-less).

**Canonical behaviour.** Constructor order for the bearer authenticator: (1) if the token starts with `bearer ` (case-insensitive ASCII, 7 chars) strip that prefix once and store the stripped token; getAuthHeaders then emits `Bearer ` + stored token with no further stripping; (2) reject as `Bearer token must not be empty` when the stored token is empty or consists only of the ASCII whitespace set {U+0020, U+0009, U+000A, U+000B, U+000C, U+000D}; (3) then the existing printable-ASCII check. For api keys: reject as `API key value for '<name>' must not be empty` when empty or only that set, then the existing CR/LF/NUL check. The set is implemented as an explicit private helper per language, never the native trim/strip/isBlank. Unicode whitespace (U+00A0, U+0085, U+2028...) is NOT in the set: a key made only of such characters is non-empty for query/cookie locations and is rejected by the existing character checks for header and bearer. NUL-only input is reported as the forbidden-character error in all 12 (php and ruby no longer say 'must not be empty'); '\x1f' is accepted by the emptiness check everywhere (java, kotlin, python no longer reject it as empty) and left to the downstream character checks.

**Why.** (a) RFC 9110 s5.5/s5.6.3 treats SP/HTAB (OWS) as the only insignificant whitespace in field values; our set is OWS plus the line/page controls the ledger probe showed disagreement on. (c) closes the F275 hole where the guard exists to prevent 'Authorization: Bearer ' and fails in all 12; the likely real-world trigger is an env var templated as `Bearer ${TOKEN}` with TOKEN unset. (d) explicit loops need no dependency.

**Rejected.** Unicode White_Space property: rejected, it needs a 25-code-point table per language, runtimes disagree on membership (U+180E, U+0085), and header/cookie values are ASCII-only on the wire so Unicode whitespace is not the credential-less-header risk. Broaden the forbidden set to all C0 controls: a reasonable follow-up (RFC 9110 field-value forbids CTLs except HTAB) but a separate behaviour change, not needed to unify the emptiness guard. Keep native trims and only document: rejected, results provably differ across 12.

**Per language.**

- java: BearerAuthenticator.java:22-37: strip prefix in ctor, use isAsciiBlank helper instead of isBlank; ApiKey*Authenticator likewise; getAuthHeaders just concatenates. (templates/java/auth/bearer_authenticator.mustache, templates/java/auth/api_key_authenticator.mustache)
- kotlin: BearerAuthenticator.kt:24 and ApiKey*Authenticator.kt:32: same. (templates/kotlin/auth/*.mustache)
- csharp: Replace string.IsNullOrWhiteSpace with an explicit char-set check in BearerAuthenticator.cs and ApiKey*Authenticator.cs; prefix strip in ctor. (templates/csharp/auth/*.mustache)
- php: ApiKeyAuthenticator.php:32 and BearerAuthenticator.php: replace trim($x) (which strips NUL, not FF) by trim($x, " \t\n\v\f\r"); prefix strip in ctor. (templates/php/auth/*.mustache)
- python: api_key_authenticator.py:30 and bearer_authenticator.py:28: replace .strip() by .strip(' \t\n\x0b\x0c\r'); prefix strip in __post_init__. (templates/python/auth/*.mustache)
- ruby: api_key_authenticator.rb:64 and bearer: replace strip (which strips NUL) by a regex /\A[ \t\n\v\f\r]*\z/ test; prefix strip in initialize. (templates/ruby/auth/*.mustache)
- node: Replace trim() by a /^[ \t\n\v\f\r]*$/ test (JS trim also strips U+00A0 and U+FEFF); prefix strip in constructor. (templates/node/auth/*.mustache)
- dart: api_key_authenticator.dart:40 and bearer: replace trim() with explicit RegExp(r'^[ \t\n\v\f\r]*$'). (templates/dart/auth/*.mustache)
- go: bearer_authenticator.go:53 and api key: strings.Trim(s, " \t\n\v\f\r") == "" instead of TrimSpace; prefix strip in constructor. (templates/go/auth/*.mustache)
- rust: bearer_authenticator.rs:41 and api key: trim_matches([' ', '\t', '\n', '\u{0B}', '\u{0C}', '\r']) instead of trim(); prefix strip in new(). (templates/rust/auth/*.mustache)
- swift: ApiKeyAuthenticator.swift:31 and BearerAuthenticator.swift:27: replace .whitespacesAndNewlines by CharacterSet(charactersIn: " \t\n\u{0B}\u{0C}\r"); prefix strip in init. (templates/swift/auth/*.mustache)
- elixir: api_key_authenticator.ex:53 and bearer_authenticator.ex:45: String.trim(s, ...) with the explicit set (String.trim/1 strips Unicode); prefix strip in new/2 and drop the strip from auth_headers (:70-82). (templates/elixir/auth/*.mustache)

**Tests.** BearerAuthenticatorTest and ApiKeyAuthenticatorTest in every language (java auth/BearerAuthenticatorTest.java, kotlin auth/, csharp Test/BearerAuthenticatorTest.cs, php tests/BearerAuthenticatorTest.php, python test_bearer_authenticator.py, ruby bearer_authenticator_test.rb, node bearer-authenticator.test.ts, dart bearer_authenticator_test.dart, go test/bearer_authenticator_test.go, rust tests/bearer_authenticator_test.rs, swift BearerAuthenticatorTests.swift, elixir bearer_authenticator_test.exs, and the api_key equivalents). One shared table: ['', ' ', '\t\n\v\f\r', '\f', 'Bearer ', 'bearer    ', 'BEARER \t'] -> 'Bearer token must not be empty'; ['Bearer abc'] -> header 'Bearer abc' (single prefix); '\0' -> forbidden-character message; U+00A0 -> accepted for query/cookie api key, rejected for bearer by the printable-ASCII message. No petstore YAML.

**Risk.** A caller that supplied '\x1f'-only or NBSP-only values and relied on the rejection will now reach the character checks instead (still rejected for header/bearer). Tokens passed as 'Bearer ' alone now throw at construction where they previously sent an empty credential: intended.

## D20 List rendering inside messages: `[a, b, c]` for server-variable allowed values and declared content types

- Ledger ids: F298 F340 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Server-variable message 'Invalid value 'x' for server variable 'v'. Allowed values: <list>': java ServerConfiguration.java:107 and kotlin :50 and dart :87 List.toString '[a, b]'; csharp :87 explicit '[a, b]'; php ServerConfiguration.php:66 and node :107 'a, b' without brackets; python :97 repr "['a', 'b']"; ruby :59 and swift ConfigurationError.swift:48 and elixir :93 '["a", "b"]'; rust :143 '{:?}' '["a", "b"]' and the Display wrapper adds 'invalid server variable: ' on top (configuration_error.rs:48); go :172 '%v' '[a b]'. The same message is repeated per operation in api files (elixir api/pet_api.ex:68/131/140, ruby api/pet_api.rb:55/106/110, java PetApi.java region/environment/version enums).
Multi-consumes rejection 'Unsupported request content-type '<ct>' for <op>; declared types are <list>': java/kotlin/csharp/node/dart/swift '[a, b]' (swift passes the array, so '["a", "b"]'); python repr (pet_api.py:1607); ruby, rust ({:?} pet_api.rs:2241), elixir inspect '["a", "b"]'; php implode without brackets (PetApi.php:1637); go %q for the content type and %v for the list (pet_api.go:2050).

**Canonical behaviour.** Both lists render as `[` + elements joined by `, ` (comma, one space) + `]`, elements unquoted and unescaped. Full messages: `Invalid value '<v>' for server variable '<name>'. Allowed values: [a, b, c]` and `Unsupported request content-type '<ct>' for <op>; declared types are [a, b, c]`. The rejected value is always wrapped in single quotes (go uses '%s', not %q). Go lower-cases the first letter (ST1005, forced) and keeps its %w sentinel prefix; nothing else differs. Rust's ConfigurationError::InvalidServerVariable / InvalidArgument / InvalidState Display prints the stored reason verbatim (no 'invalid server variable: ' prefix) so the message is not double-prefixed. The `<op>` in the content-type message keeps each language's identifier style (setPetAvatar, set_pet_avatar, SetPetAvatar), as the required-parameter message already does.

**Why.** (a) AGENTS.md:275 and PARITY_TODO R8-7 claim one canonical string. (b) The bracket + ', ' form is what java/kotlin/csharp/dart already print by default and what node/csharp print for content types. (d) a one-line join per language, no dependency.

**Rejected.** JSON-style quoted list: rejected, most callers read it as prose and java, kotlin, csharp, dart, node (content type) already use the bracket-unquoted form (5 of 12). Unbracketed 'a, b' (php, node server var): rejected, loses the visual list boundary. Keep rust's Display prefix as a deliberate category label: rejected, it makes rust's message the only one that cannot be compared to the canonical string, and AGENTS.md:275 says the message is uniform in all 12.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: ServerConfiguration.php:66 and PetApi.php:1637/2068 and the per-operation server-variable checks: '[' . implode(', ', $x) . ']'. (templates/php/server_configuration.mustache, templates/php/api/api.mustache)
- python: server_configuration.py:97 and pet_api.py:1607/2013: '[' + ', '.join(x) + ']' instead of f'{x}'. (templates/python/server_configuration.mustache, templates/python/api/api.mustache)
- ruby: server_configuration.rb:59, pet_api.rb:55/106/110/1121/1420: "[#{x.join(', ')}]". (templates/ruby/server_configuration.mustache, templates/ruby/api/api.mustache)
- node: server-configuration.ts:107 and per-operation checks: `[${x.join(", ")}]` (content-type message already canonical). (templates/node/server_configuration.mustache, templates/node/api/apis.mustache)
- dart: already canonical
- go: server_configuration.go:172: '[' + strings.Join(enumValues, ", ") + ']'; pet_api.go:2050/2553: use '%s' for the content type and the same join for the list. (templates/go/server_configuration.mustache, templates/go/api/api.mustache)
- rust: server_configuration.rs:143 and pet_api.rs:2241/2794: format!("[{}]", v.join(", ")) instead of {:?}; configuration_error.rs:48-50 Display prints the reason verbatim for InvalidServerVariable/InvalidArgument/InvalidState. (templates/rust/server_configuration.mustache, templates/rust/errors/configuration_error.mustache, templates/rust/api/api.mustache)
- swift: ConfigurationError.swift:48 and PetApi.swift:1159/1471: "[\(x.joined(separator: ", "))]". (templates/swift/errors/configuration_error.mustache, templates/swift/api/api.mustache)
- elixir: server_configuration.ex:93, pet_api.ex:68/131/140/2667/3363: "[#{Enum.join(x, ", ")}]" instead of inspect/1. (templates/elixir/server_configuration.mustache, templates/elixir/api/api.mustache)

**Tests.** ServerConfigurationTest/test_server_configuration/server_configuration_test per language (java ServerConfigurationTest.java and ServerVariableTest.java, kotlin same, csharp Test/ServerConfigurationTest.cs, php tests/ServerConfigurationTest.php, python test_server_configuration.py, ruby server_configuration_test.rb, node server-configuration.test.ts, dart server_configuration_test.dart, go test/server_configuration_test.go, rust tests/server_configuration_test.rs, swift ServerConfigurationTests.swift, elixir server_configuration_test.exs): assert the FULL message string for a variable with enum [api, staging, sandbox]. Content-type message: assert the full string in PetApiTest per language for setPetAvatar (petstore already has the multi-consume operation). The petstore already has the enum server variables (region/environment/version); no YAML needed.

## D21 User-Agent and X-Request-ID injection checks are case-insensitive; the caller's header always wins

- Ledger ids: F299 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** All 12 test for the exact strings with a case-sensitive lookup before injecting: java DefaultApiClient.java:392/395 containsKey, kotlin .kt:146/149 `in`, csharp .cs:315/320 ContainsKey on a default-comparer Dictionary, php AbstractApiClient.php:147/154 isset, python default_api_client.py:378/384 `not in`, ruby default_api_client.rb:123/124 `||=`, node abstract-api-client.ts:245/251 property lookup (the X-Request-ID check is `!` truthy, so an empty string also re-injects), dart abstract_api_client.dart:142/146 containsKey, go default_api_client.go:121/124 map lookup, rust default_api_client.rs:170/185 contains_key, swift DefaultApiClient.swift:263/266 dictionary subscript, elixir default_api_client.ex:187/194 Map.has_key?. A caller header `user-agent`/`x-request-id` therefore gets a second injected header; wire effect depends on the HTTP library (two lines in java/python, comma-joined in node fetch Headers, last-writer-wins and map-order dependent in go/rust/swift).

**Canonical behaviour.** Before injecting `User-Agent` or `X-Request-ID` the SDK searches the merged header map for a name equal under ASCII case-folding (RFC 9110 s5.1: field names are case-insensitive). If any case variant is present, even with an empty value, the SDK injects nothing and leaves the caller's key and value exactly as written (it does not rename or lower-case it). Implemented by one private `hasHeader(map, name)` helper per client; no other header logic changes.

**Why.** (a) RFC 9110 s5.1. (c) removes non-deterministic wire output in go/rust/swift. (b) all 12 already merge into a map, only the lookup changes. (d) no dependency.

**Rejected.** Normalise the whole merged map to lower case: rejected, it changes the casing callers see on the wire and in tests for every header, a much larger behaviour change. Case-insensitive map type (TreeMap CASE_INSENSITIVE_ORDER etc.): rejected for go/rust/swift/elixir where the merged structure is a plain map; a helper is uniform. Treat an empty caller value as 'absent' (node's current truthy check): rejected, an explicit empty User-Agent is a legitimate way to suppress the header, same as every other SDK.

**Per language.**

- java: DefaultApiClient.java:392/395 -> mergedHeaders.keySet().stream().noneMatch(k -> k.equalsIgnoreCase(name)). (templates/java/default_api_client.mustache)
- kotlin: DefaultApiClient.kt:146/149 -> mergedHeaders.keys.none { it.equals(name, ignoreCase = true) }. (templates/kotlin/default_api_client.mustache)
- csharp: DefaultApiClient.cs:315/320 -> mergedHeaders.Keys.Any(k => string.Equals(k, name, StringComparison.OrdinalIgnoreCase)). (templates/csharp/default_api_client.mustache)
- php: AbstractApiClient.php:147/154 -> array_change_key_case($mergedHeaders) isset check on strtolower(name). (templates/php/abstract_api_client.mustache)
- python: default_api_client.py:378/384 -> any(k.lower() == name.lower() for k in merged_headers). (templates/python/default_api_client.mustache)
- ruby: default_api_client.rb:123/124 -> unless merged.keys.any? { |k| k.casecmp?('User-Agent') } (replace `||=`). (templates/ruby/default_api_client.mustache)
- node: abstract-api-client.ts:245/251 -> Object.keys(mergedHeaders).some(k => k.toLowerCase() === name.toLowerCase()); an empty value counts as present. (templates/node/abstract_api_client.mustache)
- dart: abstract_api_client.dart:142/146 -> merged.keys.any((k) => k.toLowerCase() == name.toLowerCase()). (templates/dart/abstract_api_client.mustache)
- go: default_api_client.go:121/124 -> loop with strings.EqualFold. (templates/go/default_api_client.mustache)
- rust: default_api_client.rs:170/185 -> merged.keys().any(|k| k.eq_ignore_ascii_case(name)). (templates/rust/default_api_client.mustache)
- swift: DefaultApiClient.swift:263/266 -> merged.keys.contains { $0.caseInsensitiveCompare(name) == .orderedSame }. (templates/swift/default_api_client.mustache)
- elixir: default_api_client.ex:187/194 -> Enum.any?(Map.keys(merged), &(String.downcase(&1) == String.downcase(name))). (templates/elixir/default_api_client.mustache)

**Tests.** DefaultApiClientTest (integration, uses the existing petstore /test/echo route at specs/petstore/openapi.yaml:1237 which echoes request headers) in each language: TransportOptions default header `user-agent: MyApp` and `x-request-id: abc` -> echoed request carries exactly one User-Agent value `MyApp` and one X-Request-ID `abc`, and the SDK default token appears nowhere. Files: DefaultApiClientTest.java/.kt/.cs, DefaultApiClientTest.php, test_default_api_client.py, default_api_client_test.rb, default-api-client.test.ts, default_api_client_test.dart, test/default_api_client_test.go, tests/default_api_client_test.rs, DefaultApiClientTests.swift, default_api_client_test.exs. No petstore YAML.

## D22 Default User-Agent product token is the SDK's published package name

- Ledger ids: F300 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** Generated default UA (petstore config): java and kotlin 'com.example.petstore/1.0.0 (java|kotlin)' from invokerPackage (TransportOptions.java:207, BetterJavaCodegen.java:456, BetterKotlinCodegen.java:344) although the published Maven artifactId is petstore-client (pom.xml); php 'PetstoreClient/1.0.0 (php)' from the namespace (BetterPHPCodegen.java:319; composer name is openapi/petstoreclient); python 'petstore_client/1.0.0' from the import package (transport_options.py:98; the PyPI distribution is petstore-client); node 'petstore-client' (npmName, canonical); ruby 'petstore_client' (gemName, canonical); dart 'petstore_client' (pubspec name, canonical); elixir 'petstore_client' (app name); csharp and swift 'PetstoreClient' (package name); go 'petstore' (module name) and rust 'petstore' (crate name).

**Canonical behaviour.** The token before '/' is the name the SDK is published under in its own ecosystem's registry (the identifier a user types into their package manager), exactly as written there: Maven artifactId (java, kotlin), PyPI project name (python, pyproject name), npm name, gem name, pub name, hex app name, NuGet package id, SwiftPM package name, Go module last path segment, crates.io name, and the package segment of the composer name (php, without the 'openapi/' vendor). Format unchanged: `<name>/<version> (<lang>)`. The generator computes it in the language codegen from the same variable that drives the manifest, never from an internal namespace.

**Why.** (a) RFC 9110 s10.1.5 says the product token identifies the software; the published name is the only identifier that is unique and discoverable. (b) 7 of 12 already use the published name. (d) all values exist in the codegen (artifactId, projectName, composer name) today.

**Rejected.** One identical token across all 12 (e.g. kebab-case 'petstore-client'): rejected, each ecosystem already normalises names differently (PyPI treats - and _ as equal, crates/go use the bare name) so a shared string would not match any registry entry and needs a new shared option; analytics can still join on the (<lang>) suffix. Keep the namespace-derived tokens: rejected, 'com.example.petstore' and 'PetstoreClient' identify a code namespace, not the product.

**Per language.**

- java: BetterJavaCodegen.java:456 use the derived artifactId (line 449) instead of invokerPackage. (src/main/java/.../generators/java/BetterJavaCodegen.java)
- kotlin: BetterKotlinCodegen.java:344 use artifactId instead of invokerPackage. (src/main/java/.../generators/kotlin/BetterKotlinCodegen.java)
- csharp: already canonical
- php: BetterPHPCodegen.java:319 use the composer package segment (composer name minus vendor) instead of invokerPackage. (src/main/java/.../generators/php/BetterPHPCodegen.java)
- python: BetterPythonCodegen.java:480 use projectName (the pyproject name) instead of packageName. (src/main/java/.../generators/python/BetterPythonCodegen.java)
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** TransportOptionsTest per language (java TransportOptionsTest.java, kotlin TransportOptionsTest.kt, csharp Test/TransportOptionsTest.cs, php tests/, python test_transport_options.py, ruby, node, dart transport_options_test.dart, go, rust tests/transport_options_test.rs, swift TransportOptionsTests.swift, elixir): assert the full default string, e.g. java 'petstore-client/1.0.0 (java)', php 'petstoreclient/1.0.0 (php)', python 'petstore-client/1.0.0 (python)'. The per-language Spec classes already pin the names (src/spec/java/.../PythonSpec.java:30 sets projectName petstore-client). No petstore YAML.

## D23 Every model has a field-dumping string form that renders containers element-wise and includes additionalProperties

- Ledger ids: F321 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** dart: model classes have no toString (only enums at model.mustache:48-223), so print(pet) shows "Instance of 'Pet'" (metadata.dart has hashCode at :64 but no toString). java: model.mustache:815 only special-cases a bare byte[]; BinaryVault.java:69-70 appends List<byte[]> and Map<String,byte[]> raw (prints [[B@hash]). csharp: Metadata.cs:54-57 StringBuilder.Append(List/Dictionary) prints the type name. kotlin: data-class toString covers only primary-constructor properties, so Metadata's @Transient additionalProperties is omitted and ByteArray/List<ByteArray> print [B@...; node has inspect, python repr, rust Debug, swift, ruby, elixir get a dump from the language; go structs use plain %v (pointer fields print as addresses, not verified at runtime); php has no __toString by an AGENTS.md decision.

**Canonical behaviour.** Each language keeps its idiomatic dump style but must: (1) exist on every generated model and union (dart is the gap), (2) include every property plus the additionalProperties bucket, (3) render collections and maps element-wise (`[a, b]`, `{k=v}` or the language's own container syntax) and byte data by content (decimal list as java Arrays.toString / kotlin contentToString, per AGENTS.md:277), never a type name or identity hash. Formats: java/csharp `ClassName{prop=value, ...}` (unchanged), kotlin `ClassName(prop=value, ..., additionalProperties={...})` (data-class shape, extended), dart `ClassName(prop: value, ...)`. php stays without __toString (existing AGENTS decision, unchanged). AGENTS.md:277 must be corrected: dart did NOT already have toString.

**Why.** (a) AGENTS.md:277 documents the intent that java/csharp dump all properties with bytes rendered by content; the goldens contradict it. (c) identity hashes and type names in logs are worse than no output because they look like data. (d) java has a helper-per-container option, kotlin/csharp/dart need small overrides.

**Rejected.** Base64 for bytes: rejected, AGENTS.md:277 already allows the decimal list and java/kotlin use it. Dropping toString on java/csharp to rely on a library: rejected, neither has a built-in reflective dump. Adding php __toString now: deferred, as AGENTS.md:277 states (cannot be verified safely).

**Per language.**

- java: model.mustache:815: add a private static render(Object) helper that handles byte[] (Arrays.toString), Collection/Map recursively; use it for every field so shards/labels/scans print content. (templates/java/models/model.mustache)
- kotlin: For classes that already get hand-written equals (ByteArray fields or additionalProperties) emit toString that uses contentToString for ByteArray, element-wise for List<ByteArray>/Map<String,ByteArray>, and appends additionalProperties. (templates/kotlin/models/model.mustache)
- csharp: model.mustache:562: render IEnumerable/IDictionary fields via string.Join (and byte[] via the existing base64/array path); include AdditionalProperties as `{k=v}`. (templates/csharp/models/model.mustache)
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: Generate `@override String toString()` on model classes (and union classes) dumping all fields and additionalProperties, next to the existing ==/hashCode. (templates/dart/models/model.mustache)
- go: Out of the finding's scope, flag only: plain %v on structs with pointer fields prints addresses; consider generating String() if the maintainers want parity (not verified, no change decided here). (templates/go/models/model.mustache)
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Per-language model tests (java models/MetadataTest.java and ObjectSerializerTest.java which already build BinaryVault; kotlin models/, csharp Test/MetadataTest.cs, dart test/metadata_test.dart, others where a toString test exists): toString of Metadata (additionalProperties {x:1}) contains 'additionalProperties'/'AdditionalProperties' and the key; toString of BinaryVault contains '[1, 2' style content and no '@'/'[B@'; toString of Pet with tags list contains the tag fields and not 'System.Collections.Generic' (csharp) . Existing fixtures Metadata, BinaryVault, PetPassport, Pet already cover list, map, byte list and additionalProperties; no YAML needed.

## D24 oneOf/anyOf union wrappers get the same value equality as plain models

- Ledger ids: F322 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: L | Wire-visible: False | Needs user: False

**Current state.** Plain models have value equality in all 12 (node equals(), go Equal() e.g. models/pet.go:101, swift Equatable/Hashable pet.swift:19). Union wrappers: java PetTreatment/SetPetAvatarThumbnailRequest/PetFood no equals/hashCode (PetTreatment.java:16); kotlin plain `class PetFood` and PetTreatment/SetPetAvatarThumbnailRequest with none; csharp PetTreatment/PetFood/SetPetAvatarThumbnailRequest none; node pet-food.ts/pet-treatment.ts no equals() (pet.ts has 8); dart pet_food.dart/pet_treatment.dart none (set_pet_avatar_thumbnail_request.dart:73 has ==); swift PetFood/PetTreatment structs holding `_value: Any` are not Equatable (PetFood.swift:10); go PetTreatment is a struct of pointers with no Equal method (go ==) compares pointers. rust (derived PartialEq), python (pydantic), php, ruby, elixir already compare by value.

**Canonical behaviour.** Two union wrappers built from identical JSON (or identical variants) are equal and have the same hash; wrappers holding different variants or different variant values are unequal. Equality delegates to the held variant(s) using the language's existing model equality (byte[] by content, anyOf compares the retained instance list in order). API per language matches the plain models of that language: java equals/hashCode, kotlin equals/hashCode, csharp Equals/GetHashCode (+ IEquatable<T>), node `equals(other)`, dart ==/hashCode, swift Equatable and Hashable, go `Equal(other T) bool`.

**Why.** (a) AGENTS.md models rule: every plain model has value equality; the union types are the same contract. (b) 5 of 12 already comply. (c) identity equality on a decoded value is a silent surprise in sets/maps and tests. (d) all variants already have equality, so delegation is local generation.

**Rejected.** Leave unions identity-equal and document: rejected, the ledger rule is that a divergence is a defect unless the language forces it, and nothing forces it here (rust/python/php/ruby/elixir already do it). Reflective/JSON-based equality in swift (encode both and compare Data): acceptable fallback only if switching on the variants is impractical, since it hides number-format differences.

**Per language.**

- java: Add equals/hashCode on the oneOf/anyOf branch of model.mustache delegating to Objects.deepEquals/Arrays.deepHashCode over the instance(s) (byte[] variant safe). (templates/java/models/model.mustache)
- kotlin: Add equals/hashCode to the union classes (PetFood.kt:39, PetTreatment.kt, SetPetAvatarThumbnailRequest.kt) with contentEquals for ByteArray variants. (templates/kotlin/models/model.mustache)
- csharp: Add Equals/GetHashCode/IEquatable and ==/!= to union classes, SequenceEqual for byte[] variants. (templates/csharp/models/model.mustache)
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: Add equals(other) to the generated union classes (PetFood, PetTreatment) matching the plain models' equals(). (templates/node/models/model.mustache)
- dart: Add ==/hashCode to PetFood/PetTreatment (copy the DeepCollectionEquality pattern of set_pet_avatar_thumbnail_request.dart:73). (templates/dart/models/model.mustache)
- go: Generate `func (o PetTreatment) Equal(other PetTreatment) bool` comparing each non-nil variant with its own Equal / bytes.Equal, and fix the model doc comment that warns about ==. (templates/go/models/model.mustache)
- rust: already canonical
- swift: Make union structs Equatable & Hashable: implement ==/hash(into:) by switching over the generated variants (replace the `Any` storage comparison with typed casts). (templates/swift/models/model.mustache)
- elixir: already canonical

**Tests.** ComposedSchemaTest in every language (java models/ComposedSchemaTest.java, kotlin same, csharp Test/ComposedSchemaTest.cs, node composed-schema.test.ts, dart composed_schema_test.dart, go test/composed_schema_test.go, swift ComposedSchemaTests.swift, and rows in php/python/ruby/rust/elixir to lock the existing behaviour): decode the same JSON twice into PetTreatment (anyOf), PetFood (discriminated oneOf) and SetPetAvatarThumbnailRequest (contains a byte variant) -> equal and equal hash; decode a different payload -> unequal. Petstore already has all three schemas; no YAML needed.

## D25 Null required Options object reports the canonical missing-parameter message

- Ledger ids: F323 | Theme: errors-messages-api-shape | Still true: **no** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Fixed in the current goldens. java: PetApi.java:407/411 `Objects.isNull(options) || options.files() == null` throws IllegalArgumentException 'Missing the required parameter 'files' when calling PetApi.addPetPhotos'. csharp: PetApi.cs:362-377 null options reports the first required field's canonical message before ThrowIfNull. rust: pet_api.rs:368-374 still names the missing field but, for a None Options, uses the literal 'files' (first required field) in the canonical text; python/ruby/node/elixir report the first required field; kotlin/swift/dart/php reject null by type; go keeps 'options are required when calling <Class>.<op>' as the documented forced exception. Residual: rust wraps the message in ConfigurationError::InvalidArgument whose Display prefixes 'invalid argument: '; that is handled by the F298/F340 decision (verbatim Display).

**Canonical behaviour.** Already satisfied: a null required Options object reads as its first missing required field with the canonical message; go's 'options are required' message is the existing FORCED exception.

**Why.** Re-verified against PetApi.java, PetApi.cs and pet_api.rs; no divergence left except the rust Display prefix, covered elsewhere.

**Rejected.** None needed.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Existing: rust/tests/pet_api_test.rs:215 asserts the 'files' message; add a null-options row to PetApiTest.java and Test/Api/PetApiTest.cs if absent (addPetPhotos(1L, null) -> IllegalArgumentException with the canonical text).

## D26 An optional request body can be omitted: add the missing java no-argument overloads

- Ledger ids: F324 | Theme: errors-messages-api-shape | Still true: **partly** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** csharp is fixed (StoreApi.cs:928/962 `Order? order = null`) and swift is fixed (StoreApi.swift:572/586 `order: Order? = nil`). Still diverging: java StoreApi.java:644/655 only `placeOrder(@Nullable Order order)` and `placeOrderWithHttpInfo(@Nullable Order order)`, no `placeOrder()` overload, although optional Options already get one (getStockItem() at StoreApi.java:450-464 delegates with null). kotlin `order: Order? = null`, python, ruby, node, php, elixir default the body. dart `placeOrder(Order? order)` (documented positional shape R7-14), go `PlaceOrder(order *Order)` and rust `Option` have no default arguments.

**Canonical behaviour.** Where the language has default arguments or overloads, an optional body (and each trailing optional argument) can be omitted at the call site: `placeOrder()` works in java. java generates overloads that drop trailing optional arguments from the right (body, then options). Dart (documented positional shape), go and rust (no default arguments) are FORCED exceptions and keep passing the nullable explicitly.

**Why.** (b) 8 of 12 already allow omission, (c) no behaviour change for existing callers (overloads only add), (d) java already has overload generation for options (api.mustache:153-202).

**Rejected.** Remove the defaults elsewhere so all 12 are explicit: rejected, it would break the published call shape of kotlin/python/ruby/node/php/elixir/csharp/swift.

**Per language.**

- java: In api.mustache extend the convenience-overload block (lines ~153-202) to also emit `op(...required)` and `opWithHttpInfo(...required)` when the body is optional, delegating with null. (templates/java/api/api.mustache)
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** StoreApiTest in java (api/StoreApiTest.java) plus csharp (Test/Api/StoreApiTest.cs) and swift (StoreApiTests.swift) to lock the fixes: a test that calls placeOrder() with no argument and asserts a request with no body is sent (chasm echo) and a non-null Order is returned. kotlin/python/ruby/node/php/elixir get the same no-argument call as a regression row. Uses the existing petstore placeOrder operation; no YAML.

## D27 ApiResult.headers is a defensive, read-only copy in every language that can express it

- Ledger ids: F325 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** java: Collections.unmodifiableMap(new HashMap<>(headers)) (ApiResult.java:33); csharp: ReadOnlyDictionary over a copy (ApiResult.cs:35-36); rust: private field exposed by shared reference; node: `Readonly<Record>` type only, no runtime copy or freeze (api-result.ts:18); php readonly array and swift struct dictionary and elixir map are value types. Mutable/uncopied: python frozen dataclass holding the caller's dict (api_result.py:25), dart final Map with the caller's map (api_result.dart:21), go struct field referencing the caller's map (api_result.go:27/36), kotlin stores the caller's Map without a copy (ApiResult.kt:24); ruby freezes the caller's own hash in place: `@headers = headers.freeze` (api_result.rb:34) which freezes the transport's response.headers as a side effect.

**Canonical behaviour.** At construction ApiResult takes a copy of the headers and exposes it read-only: java/csharp/rust/php/swift/elixir unchanged; kotlin `headers.toMap()` stored (read-only Map type); python `types.MappingProxyType(dict(headers))` with the field typed Mapping[str, str]; dart `Map.unmodifiable(headers)`; node `Object.freeze({ ...headers })`; ruby `headers.dup.freeze`; go copies with maps.Clone at NewApiResult and documents that the map must not be mutated. Go is the only FORCED exception to read-only (the language has no immutable map type), but it still copies so ApiResult never aliases the ApiHttpResponse map.

**Why.** (c) callers cannot corrupt the ApiHttpResponse or other results sharing the map, and the ruby side effect disappears. (b) java, csharp, rust, node-by-type already do it. (d) every language has the needed stdlib type except go.

**Rejected.** Leave go/python/dart/kotlin mutable and document: rejected, 3 languages can enforce it with their stdlib and the rust doc already promises 'unmodifiable'. Freeze without copying: rejected (ruby today), it mutates the transport's own object.

**Per language.**

- java: already canonical
- kotlin: ApiResult.kt:24 keep a constructor parameter and store `headers.toMap()` in the property. (templates/kotlin/api_result.mustache)
- csharp: already canonical
- php: already canonical
- python: api_result.py:25 type headers as Mapping[str, str] and in __post_init__ object.__setattr__(self, 'headers', MappingProxyType(dict(self.headers))). (templates/python/api_result.mustache)
- ruby: api_result.rb:34 `@headers = headers.dup.freeze`. (templates/ruby/api_result.mustache)
- node: api-result.ts constructor: `this.headers = Object.freeze({ ...headers })`. (templates/node/api_result.mustache)
- dart: api_result.dart:21/28 initialize with `headers = Map.unmodifiable(headers)`. (templates/dart/api_result.mustache)
- go: NewApiResult (api_result.go:31-36) store maps.Clone(headers); document 'do not mutate' on the field. Read-only is FORCED-impossible (no immutable map). (templates/go/api_result.mustache)
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** ApiResultTest per language (ApiResultTest.java/.kt/.cs, ApiResultTest.php, test_api_result.py, api_result_test.rb, api-result.test.ts, api_result_test.dart, test/api_result_test.go, tests/api_result_test.rs, ApiResultTests.swift, api_result_test.exs): row 1 mutate the source map after construction and assert result.headers unchanged; row 2 assert mutating result.headers fails (java UnsupportedOperationException, python TypeError, dart UnsupportedError, ruby FrozenError, node TypeError in strict mode, kotlin by type so skip); ruby extra row: the source hash is not frozen afterwards. No YAML.

## D28 Required Options parameters are named by the caller-facing identifier in the missing-parameter message

- Ledger ids: F392 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Message 'Missing the required parameter '<x>' when calling <Class>.<op>' for required query/header/form/cookie Options params: java templates/java/api/api.mustache:281/289/297/305 and php api.mustache:175/184/193/471/480/489 and rust api.mustache (10 sites) interpolate {{baseName}} (the wire name); kotlin (11 sites), csharp, python, node (apis.mustache), ruby, dart, swift, elixir interpolate {{paramName}}; go has its own sentinel wording. Path params use {{paramName}} everywhere (php api.mustache:166/462, java 2 sites). Petstore masks it because category/nickname/file are single lowercase words; the one visible difference is kotlin's escaped identifier `'_file'` (PetApi.kt:1373) vs java `'file'` (PetApi.java:1850). AGENTS.md:291 says php uses baseName 'matching java/kotlin', which is wrong for kotlin.

**Canonical behaviour.** The quoted name is the identifier the caller types in that language's API (the Options field / argument name, i.e. {{paramName}}), identical to what path parameters already use. A header `X-Correlation-Id` is reported as `xCorrelationId` (java, kotlin, csharp, node, dart, swift), `x_correlation_id` (python, ruby, elixir, rust, go), `XCorrelationId` where the language exposes it that way. Kotlin's `_file` is therefore correct because that is the parameter the caller must set. The wire name is never used. AGENTS.md:291 is corrected accordingly.

**Why.** (b) majority 9/12 and all path params; (c) actionable, the quoted token compiles in the caller's code; (a) matches the openapi-generator upstream convention. The cost of the opposite choice is only message text, so no product decision is needed.

**Rejected.** Wire name everywhere (the ledger's suggestion): rejected, the message exists to tell the caller what to fill in, and the caller addresses `options.xCorrelationId`, not 'X-Correlation-Id'; 9 of 12 and all path parameters already use the identifier; choosing the wire name would change the most SDKs and make the message disagree with the Options type.

**Per language.**

- java: api.mustache:281/289/297/305 {{baseName}} -> {{paramName}}. (templates/java/api/api.mustache)
- kotlin: already canonical
- csharp: already canonical
- php: api.mustache:175/184/193/471/480/489 {{baseName}} -> {{paramName}}; fix AGENTS.md:291 sentence. (templates/php/api/api.mustache)
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: api.mustache: the 10 {{baseName}} interpolations (lines ~311,373,393,411,483,535...) -> {{paramName}}. (templates/rust/api/api.mustache)
- swift: already canonical
- elixir: already canonical

**Tests.** PetApiTest per language (java api/PetApiTest.java, kotlin api/PetApiTest.kt, csharp Test/Api/PetApiTest.cs, php tests/Api/PetApiTest.php:1021-1029, python test/api/test_pet_api.py:575, ruby api/pet_api_test.rb:578, node, dart pet_api_test.dart:983, go base_api_test.go, rust pet_api_test.rs:814/838, swift PetApiTests.swift, elixir pet_api_test.exs:1091/1110): add one row calling getPetByName without the new required header and assert the FULL message with the language's identifier, e.g. java "Missing the required parameter 'xCorrelationId' when calling PetApi.getPetByName", python 'x_correlation_id'. Existing getPetByName tests must also set the new header. YAML to add (specs/petstore/openapi.yaml:827-838, operationId getPetByName, after the `category` query parameter):
        - name: X-Correlation-Id
          in: header
          required: true
          schema:
            type: string
Do not use X-Request-Id: it is the header the transport injects (F299).

**Petstore spec addition.**

```yaml
        - name: X-Correlation-Id
          in: header
          required: true
          schema:
            type: string
```

## D29 Use-after-close: one message, built-in state error, and doc comments that match

- Ledger ids: F409 | Theme: errors-messages-api-shape | Still true: **yes** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Types are already correct per AGENTS.md:70-77 (built-in state errors, go sentinel, rust typed Err, swift ConfigurationError). Messages: java DefaultApiClient.java:387 and csharp .cs:301 'ApiClient has been closed'; kotlin .kt:140, php AbstractApiClient.php:140, ruby default_api_client.rb:120, swift DefaultApiClient.swift:247 'ApiClient has been closed and can no longer send requests'; python .py:370, node abstract-api-client.ts:232, elixir default_api_client.ex:169 '... can no longer be used'; dart abstract_api_client.dart:134 'Cannot send request: the API client has been closed'; go default_api_client.go:42 'API client has been closed and can no longer send requests'; rust default_api_client.rs:162 'ApiClient is closed' (Display adds 'invalid state: '). Wrong doc comments: php DefaultApiClient.php:315 and Psr18ApiClient.php:137 promise an SDK-typed ApiException (code throws \LogicException); node abstract-api-client.ts:921 promises an ApiError (code throws Error); dart abstract_api_client.dart:662 promises a typed ApiException (code throws StateError); rust default_api_client.rs:59 promises an ApiError (code returns ConfigurationError::InvalidState); elixir default_api_client.ex:42 says 'raises an SDK error' (code raises RuntimeError).

**Canonical behaviour.** Message `ApiClient has been closed and can no longer send requests` in all 12 (the 4 existing majority wording). Exception type unchanged per AGENTS.md (java/kotlin IllegalStateException, csharp InvalidOperationException, php \LogicException, python/ruby/elixir RuntimeError, node Error, dart StateError, rust ConfigurationError::InvalidState displayed verbatim, swift ConfigurationError, go ErrClientClosed). Go's wording is `API client has been closed and can no longer send requests`: 'API client' (acronym) instead of 'ApiClient' is the FORCED difference because staticcheck ST1005 rejects an error string starting with the capitalised word ApiClient. All doc comments that describe use-after-close say 'raises the language's built-in state error (X), never an SDK ApiError' naming the actual type.

**Why.** (a) AGENTS.md:70-77 for the types; (b) majority wording for the text; the comments contradict the code and the documented rule, which misleads callers into catching the wrong type.

**Rejected.** '... can no longer be used' (3 SDKs): rejected, 'send requests' is the more specific and is used by 4+go. Throw an SDK ApiError: rejected, contradicts AGENTS.md:70-77 (a caller mistake is not an SDK error).

**Per language.**

- java: DefaultApiClient.java:387 -> canonical text. (templates/java/default_api_client.mustache)
- kotlin: already canonical
- csharp: DefaultApiClient.cs:301 -> canonical text. (templates/csharp/default_api_client.mustache)
- php: Keep message (AbstractApiClient.php:140); fix comments at DefaultApiClient.php:315-317 and Psr18ApiClient.php:137-138 to say \LogicException. (templates/php/default_api_client.mustache, templates/php/psr18_api_client.mustache)
- python: default_api_client.py:370 -> canonical text. (templates/python/default_api_client.mustache)
- ruby: already canonical
- node: abstract-api-client.ts:232 -> canonical text; fix the close() doc at :921 to say it throws a plain Error. (templates/node/abstract_api_client.mustache)
- dart: abstract_api_client.dart:134 -> canonical text; fix doc at :662 to say StateError. (templates/dart/abstract_api_client.mustache)
- go: already canonical
- rust: default_api_client.rs:162 -> canonical text; fix struct doc at :59 to say ConfigurationError::InvalidState; Display verbatim (see F298/F340). (templates/rust/default_api_client.mustache)
- swift: already canonical
- elixir: default_api_client.ex:169 -> canonical text; fix comment at :42 to say RuntimeError. (templates/elixir/default_api_client.mustache)

**Tests.** DefaultApiClientUnitTest per language: after close(), sendRequest throws the built-in type (assert the class) with message equal to the canonical string (go: errors.Is(err, ErrClientClosed) and err.Error() equal; rust: matches InvalidState and to_string() equal). Files: java DefaultApiClientUnitTest.java, kotlin .kt, csharp Test/DefaultApiClientUnitTest.cs, php DefaultApiClientUnitTest.php (and Psr18ApiClientUnitTest.php), python test_default_api_client_unit.py, ruby default_api_client_unit_test.rb, node default-api-client-unit.test.ts, dart default_api_client_unit_test.dart, go pkg/default_api_client_unit_test.go, rust tests/default_api_client_unit_test.rs, swift DefaultApiClientUnitTests.swift, elixir default_api_client_unit_test.exs. No YAML.

## D30 Binary (format: binary) response: always raw bytes, never JSON-parsed, one rule for JSON / absent / text / binary Content-Type

- Ledger ids: F033 F085 F150 | Theme: content-negotiation-and-bodies | Still true: **yes** | Effort: L | Wire-visible: False | Needs user: False

**Current state.** java: InputStream path is canonical (BaseApi.java:298-345; isTextResponseContentType('') now true, :481-488) but byte[] shares the branch (see byte decision). kotlin: ByteArray covers binary and byte, so any JSON Content-Type goes deserialize<String>()+decode and throws SerializationException on {"id":..} (BaseApi.kt:306-319); absent CT returns UTF-8 bytes. csharp: Stream is canonical, JSON/absent => UTF-8 bytes, non-text => base64 with a silent FormatException fallback to UTF-8 (BaseApi.cs:325-345, DecodeBinaryBody :437-450). php: binary and String share returnType 'string'; JSON CT => json_decode+settype => 'Array' + E_WARNING (ObjectSerializer.php:521-522); absent CT => json_decode(raw)=null => '' with no error; non-text non-JSON => lenient base64_decode with raw fallback (BaseApi.php:195-226). python: JSON CT returns the parsed dict as 'bytes' (object_serializer.py:491-499 'return data'); absent CT raw text => SerializationException; non-JSON => b64decode without validate (base_api.py:359-373). ruby: canonical, File token, text-class/absent => raw StringIO, else Base64.strict_decode64 (raw ArgumentError on malformed) (BaseApi.rb:151-165). node: JSON/absent => parseJson then identity closure cast to Buffer (pet-api.ts:536/888/1263, base-api.ts:244), raw text => parse error; non-text => lenient Buffer.from(base64) (base-api.ts:229). dart: returnsBytes shared with byte; non-string JSON => UTF-8 bytes, everything else => base64Decode then UTF-8 fallback (base_api.dart:258-298). go: JSON/absent => json.Unmarshal into []byte => SerializationError for an object or raw text; non-JSON incl. text/csv => base64 decode (pet_api.go:567-600, base_api.go:558). rust: JSON object, absent CT or parse failure => Ok(empty Vec) via '_ => Vec::new()'; bad base64 => unwrap_or_default => empty (base_api.rs:327-359). swift: JSON/absent => JSONDecoder Data => throws for object/raw text; non-JSON => Data(base64Encoded:) ?? utf8 fallback (BaseApi.swift:253-275). elixir: JSON/absent => convert_to_type(map,'binary()') => SerializationError; non-JSON => Base.decode64 with raw-body fallback (base_api.ex:322-340).

**Canonical behaviour.** A response whose declared return is format:binary (java InputStream, csharp Stream, ruby File/StringIO, kotlin/python/dart/go/rust/swift/elixir/node bytes, php string) is NEVER passed to the JSON parser, whatever the Content-Type. The transport contract is unchanged (text-class media types arrive as decoded text, everything else as base64): (1) Content-Type absent/empty, text/*, application/json, +json, xml or js => the UTF-8 bytes of the body string verbatim (so getPetPhoto answered with application/json {Photo} returns the raw JSON bytes; the caller sees the type via the WithHttpInfo headers/rawBody). (2) Any other Content-Type => base64 body decoded with the SDK's single strict decoder; malformed input raises the SDK serialization error (SerializationException / SerializationError). No 'try base64, else treat as raw text' fallbacks and no silent empty result. To make this possible in the 8 languages whose native type collapses binary and byte, the op template passes the response format to the base layer (e.g. a binaryReturn flag emitted from {{#returnProperty}}{{#isBinary}}, already used by node/apis.mustache:348 and php/api/api.mustache:378), as F150 proposes.

**Why.** (a) OpenAPI says the response schema is chosen per media type, so a JSON reply to getPetPhoto is legal and must not be reinterpreted as an image or rejected; format:binary means 'opaque octets', so the faithful value is the octets that arrived. (b) java, csharp, ruby already return the raw bytes for JSON, kotlin/csharp/ruby/java for absent Content-Type, and java's mirror bug (F153) is already fixed. (c) Of the other behaviours, rust returns Ok(empty Vec) (silent data loss), python/node return a parsed object typed as bytes, php returns the string 'Array'; raw bytes is the only choice that neither throws on a legal reply nor lies about the type. (d) Needs no dependency; only a flag from a field the templates already read.

**Rejected.** Rejected: raise a typed SerializationError when a binary operation receives application/json. It makes a spec-declared success response (getPetPhoto declares application/json) un-receivable, hides the body from the caller and would change java/csharp/ruby/dart which already return the bytes; raw bytes lose nothing because ApiResult keeps headers and rawBody. Rejected: generate a oneOf return type per content type (new codegen model, L effort, not expressible in several of the 12 type systems). Rejected: keep per-language 'base64 first, fall back to text' heuristics (dart, swift, php, csharp, elixir) because valid-base64-looking text is decoded to garbage and a contract violation by a custom ApiClient is hidden.

**Per language.**

- java: already canonical
- kotlin: Add a defaulted binaryReturn: Boolean parameter to the inline invokeApiForResult; api.mustache emits true from returnProperty.isBinary. When set: skip the JSON branch; isTextResponseContentType(ct) => response.body.toByteArray(UTF_8), else strict Base64 decode wrapped into SerializationException. (templates/kotlin/base_api.mustache; templates/kotlin/api/api.mustache)
- csharp: Keep the Stream branch, delete the catch(FormatException) UTF-8 fallback in DecodeBinaryBody and wrap FromBase64String failures in SerializationException. (templates/csharp/base_api.mustache)
- php: Emit a distinct 'binary' return token (php/api/api.mustache:378 already emits 'byte' for isByteArray) so BaseApi can tell binary from String. For 'binary': text-class CT or absent => $response->body unchanged; otherwise strict base64_decode and throw SerializationException on false (no raw fallback). Never route 'binary' to ObjectSerializer::deserialize. (templates/php/base_api.mustache; templates/php/api/api.mustache)
- python: Pass a binary marker (new keyword/return token distinct from the 'bytes' used for format:byte) from api.mustache; in base_api.py for binary: text-class/absent CT => response.body.encode('utf-8'), else base64.b64decode(validate=True) wrapped in SerializationException. Remove reliance on the dict passthrough in object_serializer 'klass is bytes'. (templates/python/base_api.mustache; templates/python/api/api.mustache)
- ruby: already canonical
- node: Pass a binaryReturn flag next to returnType (apis.mustache:348 already branches on isBinary); in base-api.ts, when set, bypass parseJson: text-class/JSON/absent => Buffer.from(body,'utf8'), else the strict decodeBase64 => Buffer. Add application/json and +json to the text-class check used there. (templates/node/base_api.mustache; templates/node/api/apis.mustache)
- dart: Add binaryReturn (from isBinary) to the invokeApi call; binary: text-class or empty CT => Uint8List.fromList(utf8.encode(body)), else base64Decode with FormatException wrapped in SerializationException; delete the 'try base64 then utf8' catch. returnsBytes without binaryReturn is the byte path. (templates/dart/base_api.mustache; templates/dart/api/api.mustache)
- go: In api.mustache emit a {{#returnProperty}}{{#isBinary}} branch before the isJSON test: text-class or empty CT => []byte(response.Body), else decodeBinaryResponse (wrapped in SerializationError). Keep json.Unmarshal into []byte only for the non-binary byte path. (templates/go/api/api.mustache; templates/go/base_api.mustache)
- rust: Add binary_return: bool to InvokeApiParams (set from isBinary) instead of params.return_type == "Vec<u8>"; binary: text-class/absent => body.as_bytes().to_vec(), else strict STANDARD.decode mapped to Err(SerializationError). Remove '_ => Vec::new()' and every unwrap_or_default() in this block. (templates/rust/base_api.mustache; templates/rust/api/api.mustache)
- swift: Add binaryReturn to InvokeAPIParams; binary: text-class/empty CT => Data(response.body.utf8), else Data(base64Encoded:) or throw the serialization error (drop the ?? utf8 fallback). Bypass ObjectSerializer.deserialize for it. (templates/swift/base_api.mustache; templates/swift/api/api.mustache)
- elixir: Pass binary_return: true in the op's invoke options (distinct from return_type 'binary()' used for format:byte); decode_success_body: text-class/nil CT => response.body, else Base.decode64 with SerializationError on :error (remove the raw-body fallback). (templates/elixir/base_api.mustache; templates/elixir/api/api.mustache)

**Tests.** Existing stub-ApiClient suites already drive the generated binary ops, no spec change needed: java templates/java/test/BaseApiTest.mustache (TestableApi.call with InputStream.class), kotlin BaseApiTest.kt, csharp Test/BaseApiTest.cs, php tests/BaseApiTest.php, python test/test_base_api.py, ruby test/base_api_test.rb, node test/base-api.test.ts, dart test/base_api_test.dart, go test/base_api_test.go (binaryResponseApiClient), rust tests/base_api_test.rs (BinaryResponseApiClient), swift Tests/PetstoreClientTests/BaseApiTests.swift (MockApiClient), elixir test/base_api_test.exs. Add to each, through getPetPhoto/getPetAvatar/downloadPetDocument: (1) Content-Type application/json, body {"id":1,"url":"u"} => returned bytes equal the UTF-8 body; (2) no Content-Type, body 'hello' => bytes 'hello'; (3) image/png with base64 of 89 50 4E 47 => those 4 bytes; (4) image/png body '!!!' => SerializationError. Replace the weak rust tests (base_api_test.rs:1467 'let _ =' and :1500) with these assertions.

**Risk.** Removing the 'not valid base64 => return raw text' fallbacks (csharp, dart, swift, php, elixir) will fail unit tests/stubs that hand an image Content-Type with a raw body; those stubs must send base64 as the real transport does. The new flag changes the shared invoke signature in kotlin/dart/swift/rust/elixir/node/python/php (additive, defaulted).

## D31 format: byte response (getPetAvatarThumbnail): JSON by default, one strict decoder, every failure is a SerializationError

- Ledger ids: F150 F151 | Theme: content-negotiation-and-bodies | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** java: JSON => parse literal then Base64.getDecoder().decode(inner), raw IllegalArgumentException leaks (BaseApi.java:309-320); absent CT => isTextResponseContentType('') true => UTF-8 bytes of the quoted literal. kotlin: JSON => decode(inner) raw IAE (BaseApi.kt:306-313); absent => UTF-8 of quoted literal. csharp: JSON => Convert.FromBase64String raw FormatException (BaseApi.cs:312-323); absent CT (null) skips the JSON branch and returns UTF-8 of the quoted literal (:325-345). php: 'byte' token => decodeJson + strict base64_decode => SerializationException, absent => JSON (ObjectSerializer.php:~505-525): canonical. python: b64decode(validate=True) wrapped; absent => JSON: canonical. ruby: 'ByteArray' => deserialize; canonical. node: closure Buffer.from(json,'base64') silently drops non-alphabet characters (pet-api.ts:953); the strict decodeBase64 is not called on this path. dart: JSON => base64Decode(inner) raw FormatException; absent CT => returnsBytes && !isJson => base64Decode of the quoted literal fails => UTF-8 bytes of the quoted literal (base_api.dart:263-296), contradicting AGENTS. go: json.Unmarshal into []byte, wrapped in SerializationError, absent => JSON: canonical. rust: Value::String => STANDARD.decode(..).unwrap_or_default() => Ok(empty Vec) (base_api.rs:335-342). swift: JSONDecoder Data, wrapped, absent => JSON: canonical. elixir: convert_to_type('binary()') raises SerializationError, absent => JSON: canonical.

**Canonical behaviour.** A top-level format:byte return (java/kotlin/csharp byte[]/ByteArray, ruby ByteArray, php 'byte', node Buffer closure, python/dart/go/rust/swift/elixir bytes) follows the model-return path: Content-Type absent/empty or JSON mime => JSON-parse the body, require a JSON string, decode it with the SAME strict base64 decoder the SDK already uses for model format:byte fields (padding/alphabet strictness itself is owned by F081, not decided here), return bytes. Any failure (invalid JSON, non-string JSON, malformed base64) raises the SDK's serialization error; none leaks a platform exception (IllegalArgumentException, FormatException) and none returns empty or garbage bytes. A non-JSON Content-Type on a byte return is handled by the non-JSON decision (SerializationError). This is the AGENTS rule 'dart defaults an absent/empty response Content-Type to JSON ... matching swift/node' generalised to all 12, and the AGENTS 'python and node reject malformed base64 on decode' rule extended from model fields to the operation-return path.

**Why.** (a) The declared schema is application/json carrying a base64 string; decoding it is the only reading that matches OAS format:byte, and the AGENTS standing rules (absent CT = JSON; reject malformed base64; error contract 'every decode failure is a SerializationError'). (b) python, ruby, php, go, swift, elixir already do exactly this. (c) node silently decodes '!!not*base64??' to 6 garbage bytes and rust returns an empty Vec with Ok: silent data change; java/kotlin/csharp/dart give wrong bytes for absent CT. (d) Every SDK already has a strict base64 decoder in its ObjectSerializer.

**Rejected.** Rejected: treat an absent Content-Type on a byte return as text and return the UTF-8 bytes (what java/kotlin/csharp do today): the body is a JSON string literal, so the caller receives the quoted base64 text as 'bytes'. Rejected: separate lenient decoder for operation returns; two decoders per SDK would immediately re-diverge.

**Per language.**

- java: In base_api.mustache make the byte[] branch fire for (ct empty || isJsonMime) and decode via the ObjectSerializer strict byte decoder, converting IllegalArgumentException/JsonProcessingException to SerializationException. Stop sending byte[] to the text->UTF-8 branch. (templates/java/base_api.mustache)
- kotlin: With the binaryReturn flag from the binary decision, the non-binary ByteArray path runs when ct is empty or JSON; replace java.util.Base64.getDecoder().decode(inner) with the ObjectSerializer strict decoder wrapped in SerializationException. (templates/kotlin/base_api.mustache)
- csharp: Condition becomes (string.IsNullOrEmpty(ct) || IsJsonMime(ct)); wrap Convert.FromBase64String FormatException/JsonException in SerializationException. (templates/csharp/base_api.mustache)
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: apis.mustache:348: replace Buffer.from(json as string,'base64') with the ObjectSerializer decodeBase64 (throws on invalid alphabet/length), requiring typeof json === 'string' else SerializationError. (templates/node/api/apis.mustache)
- dart: For the non-binary bytes path treat empty CT like JSON; parseJson => require String => base64Decode in try/catch FormatException => SerializationException; non-string JSON is an error, not the UTF-8 fallback (base_api.dart:270-283). (templates/dart/base_api.mustache)
- go: already canonical
- rust: Replace .unwrap_or_default() with a mapped Err(SerializationError); non-string/array JSON for a byte return is Err, not Vec::new(). (templates/rust/base_api.mustache)
- swift: already canonical
- elixir: already canonical

**Tests.** Same BaseApi suites as the binary decision, through getPetAvatarThumbnail with a stub transport: (1) no Content-Type, body '"dGVzdC1pbWFnZQ=="' => bytes 'test-image'; (2) application/json same body => same bytes; (3) application/json body '"!!not*base64??"' => SerializationError; (4) application/json body '123' => SerializationError; (5) text/plain body 'dGVzdA==' => SerializationError (non-JSON decision). Existing partial coverage: rust tests/base_api_test.rs test_byte_over_json_response_decodes_to_bytes, java PetApiTest.java:223. Java BaseApiTest.binaryReturnTypeWithoutContentTypeReadsTextBody currently uses byte[].class and must move to InputStream.class. No spec change needed.

**Risk.** Callers of java/kotlin/csharp/dart who today (wrongly) receive quoted-literal bytes for a no-Content-Type reply will start receiving the decoded bytes. Intended, but it is a behaviour change for those servers.

## D32 Non-JSON success Content-Type on a non-binary return: String gets the text, everything else is a SerializationError naming the Content-Type

- Ledger ids: F149 | Theme: content-negotiation-and-bodies | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** java: unchecked (T) response.body() stored in the T slot, ClassCastException at the caller's assignment (BaseApi.java:336-341). kotlin: response.body as T => ClassCastException inside the SDK (BaseApi.kt:321-324). csharp: (T)(object)response.Body => InvalidCastException (BaseApi.cs:343-346). python: returns the raw string as the model (base_api.py:369-371). ruby: raw string (BaseApi.rb:167-168). node: text-class => raw string, otherwise Buffer.from(base64) typed as the model (base-api.ts:222-230). php: raw string (BaseApi.php:205-211). elixir: 'true -> response.body' (base_api.ex:337). dart: data = null => empty-body ApiError (base_api.dart:301-316). rust: from_value(String).ok() => None => empty-body ApiError; rust also base64-decodes non-text-class bodies for String returns (base_api.rs:361-379). swift: response.body as? T => nil => empty-body ApiError (BaseApi.swift:274-276; locked by BaseApiTests.swift:277 'Data should be nil'). go: skips deserialization and returns a zero-valued model with a nil error (pet_api.go:1504-1520), locked by test/base_api_test.go:531 and :1213.

**Canonical behaviour.** On a 2xx response with a non-empty body and a return type that is not format:binary: (1) Content-Type absent/empty or JSON mime (incl. +json, vendor) => JSON path (unchanged, and dart/swift/node/go keep 'absent means JSON'). (2) Otherwise, if the declared return type is String and the Content-Type is text-class (text/*, application/xml, application/javascript, +xml) => the body string verbatim. (3) Otherwise => raise the SDK serialization error with a fixed message shape: "Unexpected response Content-Type '<media-type>' for <Class>.<operation>: expected a JSON body" (go: lowercase). Never a cast, never a silently returned raw string typed as a model, never a zero-valued model with a nil error, and never reuse the 'Expected a response body ... but received none' ApiError (that message stays for genuinely empty bodies). Void operations still ignore the body; binary returns follow the binary decision.

**Why.** (a) Precedent and spec: the OAS client convention (openapi-generator's own ApiClient.deserialize) is to throw 'Content type ... is not supported' unless the type is String; AGENTS error contract says a body that does not match the declared schema is a SerializationError (elixir already documents this in decode_success_body). (b) No current behaviour is a majority (5 different outcomes across 12), but 'raise' is what java/kotlin/csharp do accidentally (ClassCastException) and what the error contract asks for. (c) Safest for callers: the failure is loud, typed, names the offending header and keeps the response reachable. (d) Needs only a String type test that every language has (T::class == String::class, typeof(T)==typeof(string), return_type == 'str', 'String', 'String.t()', *string, etc.).

**Rejected.** Rejected: return the raw text typed as the model (python, ruby, node, php, elixir today): type confusion. Rejected: leave data null so the convenience method raises the empty-body ApiError (dart, rust, swift today): the message says 'received none' when a body was received. Rejected: sniff the body and parse JSON when it is mislabelled text/plain: a tolerant reader would silently accept protocol violations. Considered: return a zero value with no error (go today, locked by a go test): silent data loss.

**Per language.**

- java: Replace the unchecked cast with: returnType == String.class && isTextResponseContentType(ct) ? body : throw new SerializationException(...). isTextResponseContentType('') is true, so apply this only in the non-empty, non-JSON branch. (templates/java/base_api.mustache)
- kotlin: if (T::class == String::class && isTextResponseContentType(ct)) response.body as T else throw SerializationException(...) (templates/kotlin/base_api.mustache)
- csharp: typeof(T) == typeof(string) && IsTextResponseContentType(ct) ? (T)(object)Body : throw new SerializationException(...) (templates/csharp/base_api.mustache)
- php: After the new 'binary' token exists, the isNonJson branch returns the body only when returnType === 'string' and isTextMediaType(ct); otherwise throw SerializationException. Delete the non-text lenient base64 branch for non-binary types. (templates/php/base_api.mustache)
- python: base_api.py:366-371: if return_type == 'str' and text-class: body; else raise SerializationException(f"Unexpected response Content-Type '{ct}' ...") (binary handled by its own branch). (templates/python/base_api.mustache)
- ruby: Final else branch: return_type == 'String' && text_response_content_type?(ct) ? response.body : raise Errors::SerializationError. (templates/ruby/base_api.mustache)
- node: Needs a stringReturn marker beside returnType (from returnType == 'string' && !returnProperty.isByteArray in apis.mustache:348); non-JSON text-class + stringReturn => raw string; everything else non-JSON throws SerializationError; remove the Buffer.from(base64) fallthrough for non-binary. (templates/node/base_api.mustache; templates/node/api/apis.mustache)
- dart: base_api.dart:301-316: keep returnType == 'String' && text-class => body; replace both 'data = null' branches with throw SerializationException. (templates/dart/base_api.mustache)
- go: In api.mustache's non-JSON branch keep the *string case (text-class only) and add a final else returning newSerializationError("unexpected response Content-Type ..."); invert the two go tests. (templates/go/api/api.mustache; templates/go/test/base_api_test.mustache)
- rust: Non-JSON non-binary: text-class + T deserializable from Value::String => Ok; else Err(SerializationError). Replace .ok() with map_err, and stop base64-decoding for String returns (base_api.rs:368-379). (templates/rust/base_api.mustache; templates/rust/test/base_api_test.mustache)
- swift: BaseApi.swift:274-276: if let s = response.body as? T and text-class => data = s, else throw the serialization error; invert BaseApiTests.swift:277. (templates/swift/base_api.mustache; templates/swift/test/BaseApiTests.mustache)
- elixir: Replace 'true -> response.body' with a clause for return_type 'String.t()' + text-class, and a final clause raising PetstoreClient.Errors.SerializationError. (templates/elixir/base_api.mustache)

**Tests.** Existing generated stub suites already have the non-JSON case on getPetById (go test/base_api_test.go:531/:1213, rust tests/base_api_test.rs:357, swift BaseApiTests.swift:277, java BaseApiTest.skipsDeserializationForTextPlain with STRING_TYPE, kotlin/csharp/python/ruby/php/node/dart/elixir BaseApi suites). Add/convert: getPetById answered with text/plain 'hello' => SerializationError whose message names text/plain (all 12); getPetById answered with application/problem+json valid body => model (unchanged); and the String-return case below. The String-return case needs the spec addition in spec_yaml (no public operation returns a String today; testTextPlain is x-internal and never generated).

**Petstore spec addition.**

```yaml
# add under paths: (next to /pet/{petId}/documents)
  /pet/{petId}/summary:
    get:
      tags:
        - pet
      summary: Get a plain-text summary of the pet
      operationId: getPetSummary
      parameters:
        - name: petId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: Successful operation
          content:
            text/plain:
              schema:
                type: string
        '404':
          description: Pet not found
# tests: stub 200 text/plain 'hello' => getPetSummary returns 'hello' in all 12; stub 200 text/plain on getPetById => SerializationError.
```

**Risk.** Behaviour change for servers that mislabel JSON as text/plain on model operations: today ruby/python/php/node/elixir hand back a string and go/dart/rust/swift an empty result; after the change all 12 raise. Existing tests lock the old behaviour and must be inverted (go base_api_test.go:531 and :1213, swift BaseApiTests.swift:277, rust base_api_test.rs:357 which currently accepts any outcome).

## D33 rust and go text/plain on a String-returning operation

- Ledger ids: F155 | Theme: content-negotiation-and-bodies | Still true: **partly** | Effort: S | Wire-visible: None | Needs user: None

**Current state.** java/kotlin/csharp/python/ruby/node/php/dart/swift/elixir: text returned verbatim. go: *string branch present (pet_api.go:1518-1522). rust: text/* verbatim (base_api.rs:367-372); non-text-class is base64-decoded then lossy UTF-8 (:373-377).

**Canonical behaviour.** Covered by the non-JSON decision: String return + text-class Content-Type returns the body verbatim in all 12. The divergence itself is already fixed in the goldens; what remains is that no generated public operation returns a String, so it is not locked.

**Why.** rust base_api.rs:361-379 now returns text/* verbatim (is_text_media_type), and go now has a *string branch in every per-operation response block (e.g. go/pkg/store_api.go:177, pet_api.go:1518-1522). java/kotlin/csharp/python and the rest already returned the text. Remaining gap is purely coverage: the only text/plain operation (testTextPlain) is x-internal and never generated, so none of the 12 exercises a String return end to end; rust additionally still base64-decodes non-text-class bodies for String returns, which the non-JSON decision removes.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Lock with the getPetSummary operation from the non-JSON decision: stub 200 text/plain 'hello' => 'hello' in each language's BaseApi suite (java BaseApiTest.returnsRawStringForNonJson, kotlin/csharp/python already have an equivalent on a hand-built call; go/rust/swift/dart/elixir/php/ruby/node need the generated-op version).

## D34 java BaseApi mirror treated an empty Content-Type as base64 while the transport treats it as text

- Ledger ids: F153 | Theme: content-negotiation-and-bodies | Still true: **no** | Effort: S | Wire-visible: None | Needs user: None

**Current state.** java: isTextResponseContentType('') returns true (BaseApi.java:481-488). All other 11 mirror their transport.

**Canonical behaviour.** Already fixed: an absent/empty Content-Type is text-class in both the java transport and the BaseApi mirror.

**Why.** java/src/main/java/com/example/petstore/api/BaseApi.java:481-488 now returns true for null/empty and the comment says the String is read as UTF-8; DefaultApiClient.java:756-767 agrees. kotlin (BaseApi.kt:424), csharp, ruby, python, go already matched. The remaining absent-CT divergences in the other languages are covered by the binary decision.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** java BaseApiTest.binaryReturnTypeWithoutContentTypeReadsTextBody already exists and passes.

## D35 Binary request body with a non-binary Content-Type selected (setPetAvatar + application/json): send the caller's bytes verbatim, never re-encode

- Ledger ids: F035 F084 | Theme: content-negotiation-and-bodies | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** java: InputStream falls to objectSerializer.serialize with FAIL_ON_EMPTY_BEANS disabled => '{}' (BaseApi.java:236-273; byte[] is passed raw by 'body instanceof byte[]'). kotlin: canonical, ByteArray passthrough (BaseApi.kt:210). csharp: Stream => Serializer.Serialize(Stream) reflects the object (BaseApi.cs:281-285). php: SplFileObject => ObjectSerializer::serialize (BaseApi.php:456). python: bytes => ObjectSerializer.serialize => base64 JSON string (base_api.py:347). ruby: File/IO => ObjectSerializer.serialize of the handle (BaseApi.rb:280). node: Buffer => ObjectSerializer.serialize => base64 string via replacer (base-api.ts:415). dart: Uint8List => jsonEncode => JSON int array (base_api.dart:443). go: *os.File => serialize => '{}' (base_api.go:417). rust: canonical, body is already bytes and the default arm returns it (base_api.rs:516). swift: Data => JSONEncoder => base64 JSON string (BaseApi.swift:428). elixir: binary => ObjectSerializer.serialize => Jason string (fails on non-UTF-8) (base_api.ex:487).

**Canonical behaviour.** When the operation's request body parameter is format:binary (the generator knows this from the body param's isBinary), the SDK sends the caller-supplied bytes/stream/file UNCHANGED under whatever declared Content-Type the caller selected, including application/json. It never routes a binary body through the JSON serializer (no '{}', no base64 string, no int array, no reflected stream object). The selector therefore only changes the Content-Type header, as java/kotlin/rust/go comments already document; a caller who wants the {data,mimeType} envelope pre-builds the JSON and passes its bytes. The unrecognised-value rejection (AGENTS 'multi-consumes selector REJECTS an unrecognised value in all 12') is unchanged; application/json is a declared value and is accepted. Mechanism: the op template (which already reads bodyParam.isBinary) passes a rawBody flag to the shared invoke call; BaseApi's serialize ladder tests it first (after extractBinaryPart for the multipart Map case) so the clause cannot catch a legitimate format:byte JSON body in languages where binary and byte share a native type.

**Why.** (a) The OAS media-type map pairs one schema per type; the SDK only models the first, so the honest behaviour is 'selector changes the header, body is the caller's'. (b) kotlin (body is ByteArray passthrough) and rust (body already bytes) already do exactly this; go/java/swift/python/node comments claim it. (c) The current outcomes are silent data loss ('{}' in java and go) or silent re-encoding (base64 string in python/swift/node, int array in dart); pass-through is loud at the server if the payload is wrong. (d) No dependency; one flag and one clause.

**Rejected.** Rejected: reject application/json when the body is binary. It makes a spec-declared request type unusable and still needs the same op-level flag. Rejected: generate an envelope body type or one parameter per content type (what F035 suggests): needs oneOf request bodies in all 12 codegens (L, and the JSON-vs-binary choice then cannot be a plain selector). Rejected: type-only pass-through in BaseApi (e.g. 'body is bytes'): python/node/dart/swift/elixir would then send format:byte JSON bodies raw instead of base64.

**Per language.**

- java: Add rawBody to invokeApiForResult/invokeApi; first clause: if (rawBody) requestBody = body (after extractBinaryPart when body is a Map). api.mustache passes true from bodyParam.isBinary. (Narrow alternative: add 'body instanceof InputStream || body instanceof java.io.File' next to 'body instanceof byte[]' at :236.) (templates/java/base_api.mustache; templates/java/api/api.mustache)
- kotlin: already canonical
- csharp: rawBody (or body is Stream || byte[]) before the JSON fallback at BaseApi.cs:281-285; DefaultApiClient already applies the declared Content-Type to byte[]/Stream bodies (:373-386). (templates/csharp/base_api.mustache; templates/csharp/api/api.mustache)
- php: Extend the SplFileObject/resource drain at BaseApi.php:407-440 to run whenever rawBody is set, not only for image/* and octet-stream. (templates/php/base_api.mustache; templates/php/api/api.mustache)
- python: raw_body flag: serialized_body = body (read() for file-like) before the content_type ladder. (templates/python/base_api.mustache; templates/python/api/api.mustache)
- ruby: raw_body: true => body.respond_to?(:read) ? body.read : body (same line as the image/* branch, BaseApi.rb:253-264) regardless of content_type. (templates/ruby/base_api.mustache; templates/ruby/api/api.mustache)
- node: rawBody parameter on invokeApi; serializeBody returns Buffer/Blob/Uint8Array unchanged when set (the image/* branch at base-api.ts:333-359 becomes 'rawBody || image/* || octet-stream'). (templates/node/base_api.mustache; templates/node/api/apis.mustache)
- dart: _serializeBody: if rawBody and body is Uint8List/List<int> return it before the JSON fallback at :443; thread rawBody through invokeApi. (templates/dart/base_api.mustache; templates/dart/api/api.mustache)
- go: Move the []byte/string/*os.File/io.Reader block in serializeBody out from under the image/octet-stream prefix check when rawBody (new invokeApiParams field) is set. (templates/go/base_api.mustache; templates/go/api/api.mustache)
- rust: already canonical
- swift: serializeBody: when rawBody and body is Data (or String) return it before ObjectSerializer.serialize at :428; add rawBody to InvokeAPIParams. (templates/swift/base_api.mustache; templates/swift/api/api.mustache)
- elixir: New serialize_body clause matching the raw_body option (is_binary(body)) before the catch-all at base_api.ex:487. (templates/elixir/base_api.mustache; templates/elixir/api/api.mustache)

**Tests.** No spec change: setPetAvatar already declares image/jpeg, image/png and application/json. In each language's PetApi test next to the existing selector test (java PetApiTest.java:743 setPetAvatarHonoursTheSelectedRequestContentType, kotlin PetApiTest.kt, csharp Test/Api/PetApiTest.cs, php tests/Api/PetApiTest.php, python test/api/test_pet_api.py, ruby test/api/pet_api_test.rb, node test/Api/pet-api.test.ts, dart test/pet_api_test.dart, go test/pet_api_test.go, rust tests/pet_api_test.rs, swift PetApiTests.swift, elixir test/pet_api_test.exs) call setPetAvatar with the JSON selector and the 4 bytes FF D8 FF E0 and assert the capturing client's body equals those exact bytes and Content-Type equals application/json (the same capturing client these tests already use). Additionally assert that a format:byte JSON body (setPetAvatarThumbnail) is still base64 so the flag cannot leak.

**Risk.** Callers who today select application/json with a raw image get server-side 400s instead of a (wrong) JSON string; nobody can have been succeeding with the old output because it never equals the declared envelope. The extra rawBody argument is additive on 11 invoke signatures.

## D36 Multipart scalar parts: every primitive is a plain text part with the canonical stringify form; only objects/models are application/json parts

- Ledger ids: F214 | Theme: content-negotiation-and-bodies | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** java: String/Number/Boolean text, everything else (OffsetDateTime, LocalDate, UUID, enum) a JSON part with Content-Type application/json and quoted body (DefaultApiClient.java:1088-1099). kotlin: same (DefaultApiClient.kt:476-499). csharp: string text; int/long/float/double/bool/decimal via value.ToString() so bool is 'True' and floats are culture-dependent (a gap the F254 finding missed); every other type a JSON part (DefaultApiClient.cs:869-905). php: is_scalar => ObjectSerializer::stringify (fixed, 'true'), but is_object catches DateTimeInterface and BackedEnum into JSON parts (DefaultApiClient.php:191 and :245, Psr18ApiClient.php:170 and :199). python: canonical, else-branch ObjectSerializer.stringify, models via ObjectSerializer.serialize (default_api_client.py:944-971). ruby: interpolates #{value} (Time => '2024-01-15 10:30:00 UTC') and to_hash => JSON (default_api_client.rb:572-588). node: typeof 'object' (Date, Temporal) => JSON part, primitives String(value) (abstract-api-client.ts:679-701). dart: String/num/bool toString, any other value jsonEncode (a DateTime throws JsonUnsupportedObjectError) (abstract_api_client.dart:639-657). go: string text, struct/map (time.Time, DateTime) JSON part, other fmt.Sprintf('%v') (base_api.go:462-512). rust: canonical, MultipartValue::Text(object_serializer::stringify(val)) vs Json chosen in the op layer (pet_api.rs:2849-2855, :387). swift: String/Bool/BinaryInteger/BinaryFloatingPoint interpolated text (fixed), Date/enum => application/json part (BaseApi.swift:476-499, DefaultApiClient.swift:497). elixir: binaries text, any map including DateTime/Date structs => JSON part, others to_string (default_api_client.ex:1090-1107).

**Canonical behaviour.** In multipart/form-data, a value that is a primitive (string, number, boolean, date, date-time, duration, uuid, enum) is written as a text part: Content-Disposition: form-data; name="x" and NO part Content-Type, its body being the SDK's ObjectSerializer.stringify form (the one already used for form-urlencoded, query and path values): true/false, 2024-01-15T10:30:00.000+00:00, 2024-01-15, red, 3. Only an object/model/map value is written as a JSON part with exactly Content-Type: application/json (no charset, no per-part Content-Length, header order Content-Disposition then Content-Type), serialized by the ObjectSerializer. Files keep the existing filename/MIME rules (the AGENTS closed 'multipart filename' decision is unaffected). An array of primitives repeats the text part per element.

**Why.** (a) OAS 3.x Encoding Object: for a primitive property the default part Content-Type is text/plain, for an object application/json; RFC 7578 section 4.2 says a text field value is the plain string. (b) python and rust (op layer picks Text(stringify) vs Json) are already exactly this; php does it for is_scalar. (c) The JSON-part route silently adds quotes; interpolation silently changes format per language (csharp sends 'True'). (d) Every SDK already has stringify; no new dependency.

**Rejected.** Rejected: JSON-encode non-string scalars as application/json parts (java, kotlin, csharp, php objects, node Date, go time, swift Date/enum, elixir DateTime today): the part body becomes a quoted string that no form parser accepts as a date. Rejected: language toString/interpolation (python was, ruby, go %v, csharp bool.ToString() = 'True', culture-sensitive floats). Considered: honour an explicit encoding.contentType on a primitive property as a JSON part (OAS allows it); not needed now and no spec in the repo declares it, so deferred.

**Per language.**

- java: addMultipartField: replace the String/Number/Boolean test with 'model/Map/Collection of models => JSON part, otherwise text part of ObjectSerializer.toFormValue(value)'. (templates/java/default_api_client.mustache)
- kotlin: appendFormField: after ByteArray/null, branch to objectSerializer.serialize only for non-scalar objects; scalars => append(fieldName, objectSerializer.stringify(value)). (templates/kotlin/default_api_client.mustache)
- csharp: Replace value.ToString() (bool => 'True') with ObjectSerializer.Stringify(value) and widen the scalar test to DateTimeOffset/DateOnly/Guid/Enum/TimeSpan; only classes/records go to the JSON part. (templates/csharp/default_api_client.mustache)
- php: In both DefaultApiClient and Psr18ApiClient treat DateTimeInterface, BackedEnum and uuid objects as scalar: stringify instead of the is_object JSON branch. (templates/php/default_api_client.mustache; templates/php/psr18_api_client.mustache)
- python: already canonical
- ruby: multipart_part final else: ObjectSerializer.stringify(value) instead of #{value}; keep to_hash/Struct => JSON part. (templates/ruby/default_api_client.mustache)
- node: multipartPart: Date, Temporal and other non-plain objects => ObjectSerializer.stringify text part; only plain objects/arrays-of-objects => JSON part; replace String(value) with ObjectSerializer.stringify. (templates/node/abstract_api_client.mustache)
- dart: _addMultipartField: DateTime/Duration/Uuid/enum => stringify text; JSON only for Map/model. (Repeated text parts stay MultipartFile.fromString: see the F385 forced exception.) (templates/dart/abstract_api_client.mustache)
- go: writeMultipartField: time.Time, models.DateTime, fmt.Stringer and named string/int kinds => WriteField(name, stringify(v)); isJSONObjectValue only for structs that are not DateTime/time.Time. (templates/go/base_api.mustache)
- rust: already canonical
- swift: appendMultipartField in BaseApi and DefaultApiClient (the known dual builder): Date, RawRepresentable enums, UUID => ObjectSerializer.stringify text part; models only => JSON part. (templates/swift/base_api.mustache; templates/swift/default_api_client.mustache)
- elixir: multipart_part: add DateTime/Date/NaiveDateTime/Time struct clauses ahead of the is_map JSON clause using ObjectSerializer.stringify; enums/atoms via stringify. (templates/elixir/default_api_client.mustache)

**Tests.** Unit level (no server): each language's DefaultApiClientUnitTest / default_api_client_unit_test (java/kotlin/csharp/php Psr18ApiClientUnitTest+DefaultApiClientUnitTest, python test_default_api_client_unit.py, ruby default_api_client_unit_test.rb, node default-api-client-unit.test.ts, dart default_api_client_unit_test.dart, go pkg/default_api_client_unit_test.go, rust tests/default_api_client_unit_test.rs, swift DefaultApiClientUnitTests.swift, elixir default_api_client_unit_test.exs) builds a multipart body from a typed Map {isPrimary: true, pageCount: 3, takenAt: 2024-01-15T10:30Z, swatch: red, metadata: <model>} and asserts byte-for-byte: isPrimary part body 'true', pageCount '3', takenAt '2024-01-15T10:30:00.000+00:00', swatch 'red', none of these four parts has a Content-Type header, and the metadata part has exactly 'Content-Type: application/json'. The spec addition below makes the API layer hand typed values to the encoder (rust op template, php/ruby/swift option fields) and extends the PetApiTest assertions.

**Petstore spec addition.**

```yaml
# paths./pet/{petId}/documents.post.requestBody.content.multipart/form-data.schema.properties (after notes)
                isPrimary:
                  type: boolean
                pageCount:
                  type: integer
                  format: int64
                takenAt:
                  type: string
                  format: date-time
                swatch:
                  $ref: '#/components/schemas/Swatch'
# expected parts: isPrimary=true, pageCount=3, takenAt=2024-01-15T10:30:00.000+00:00, swatch=red; no part Content-Type on any of them. (A format: uuid companion follows the takenAt path and can be added later.)
```

**Risk.** A server that today receives a quoted JSON date-time part from java/kotlin/csharp/swift/go/node will receive the bare ISO string; this is the documented RFC 7578/OAS form and what python/rust already send.

## D37 Multipart scalar stringification: python bool, php bool, swift Int64/Float

- Ledger ids: F254 | Theme: content-negotiation-and-bodies | Still true: **no** | Effort: S | Wire-visible: None | Needs user: None

**Current state.** python: stringify (true). php: stringify (true) for scalars. swift: every integer/float width is a text part. csharp: 'True' (tracked under F214). Others: 'true'.

**Canonical behaviour.** Fixed. python, php and swift now emit the canonical text part; the remaining non-string scalar divergences (csharp bool 'True', java/kotlin/go/ruby/node/dart/swift/elixir date-time and enum parts) share a root cause and are decided in the multipart scalar decision (F214).

**Why.** python default_api_client.py:964-971 uses ObjectSerializer.stringify; php DefaultApiClient.php:245-246 and Psr18ApiClient.php:199-200 use ObjectSerializer::stringify (no strval); swift BaseApi.swift:476-477 and DefaultApiClient.swift:497 test BinaryInteger/BinaryFloatingPoint. Newly observed while verifying: csharp (not listed in this finding) still sends bool via bool.ToString() = 'True' (DefaultApiClient.cs:869-872); folded into the F214 decision.

**Per language.**

- python: already canonical
- php: already canonical
- swift: already canonical
- csharp: bool.ToString() => 'True'; fix under F214 (Stringify). (templates/csharp/default_api_client.mustache)
- java: already canonical
- kotlin: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- elixir: already canonical

**Tests.** Locked by the F214 unit tests and spec additions (isPrimary boolean, pageCount int64).

## D38 Multipart JSON part headers: csharp/dart charset and header order, kotlin per-part Content-Length

- Ledger ids: F385 | Theme: content-negotiation-and-bodies | Still true: **partly** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** java/python/ruby/php/node/go/rust/swift/elixir: Content-Disposition then Content-Type: application/json. csharp: fixed via CreateStringContent. dart: fixed for the JSON part (abstract_api_client.dart:651-657); repeated text parts still carry text/plain; charset=utf-8 (:642). kotlin: binary parts carry Content-Length (DefaultApiClient.kt:456-463 via Ktor).

**Canonical behaviour.** A JSON part carries exactly Content-Disposition then Content-Type: application/json (no charset parameter, no per-part Content-Length). csharp and dart are fixed. Two residual library-forced differences are documented exceptions, not defects: (1) kotlin: Ktor's formData adds a Content-Length header to every binary part (RFC 7578 permits extra part headers and receivers must ignore them). (2) dart: a REPEATED text part (array of strings in multipart) must be added as an http.MultipartFile because MultipartRequest.fields is a Map<String,String> that cannot repeat a name, and package:http always emits a content-type (text/plain; charset=utf-8) for MultipartFile parts. Single text parts use fields and carry no content-type.

**Why.** csharp now builds the JSON part through CreateStringContent (DefaultApiClient.cs:369, :591 and the model branch near :895) which removes the charset/ordering difference; dart builds the JSON part with MultipartFile.fromBytes + MediaType('application','json') (abstract_api_client.dart:647-657) so no charset is added. What remains follows library behaviour (criterion d): Ktor's FormBuilder.append(ByteArray) adds Content-Length; package:http cannot repeat a field name without a file part.

**Rejected.** Rejected: hand-build dart/kotlin multipart bodies to match byte-for-byte (L effort, replaces the platform multipart writers and their streaming/redirect-replay behaviour) for a header no server keys on.

**Per language.**

- java: already canonical
- kotlin: FORCED (library): Ktor adds Content-Length to binary parts; document in AGENTS.md 'Forced divergences'. Optional: an InputProvider with unknown size avoids it. (AGENTS.md)
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: JSON part is fixed. Repeated scalar text parts: FORCED (package:http fields map cannot repeat); document in AGENTS.md. (AGENTS.md)
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Existing: each DefaultApiClientUnitTest asserts the addPetPhotos metadata part; extend the csharp and dart cases to assert the exact header block 'Content-Disposition: form-data; name="metadata"\r\nContent-Type: application/json' (no charset). No spec change.

## D39 x-www-form-urlencoded values: scalars use the canonical stringify form; nested objects/maps are rejected instead of emitting each language's repr

- Ledger ids: F216 | Theme: content-negotiation-and-bodies | Still true: **partly** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** ruby: scalars fixed, serialize_body uses ObjectSerializer.stringify; a nested object falls to its to_s (BaseApi.rb:267-278). java/kotlin: toFormValue/stringify fallback toString (ObjectSerializer.java:359, ObjectSerializer.kt:538). csharp: ObjectSerializer.Stringify fallback ToString (BaseApi.cs:241-277). python: stringify else str(value) (pydantic repr) (object_serializer.py:~561). node: String(value) => '[object Object]' (object_serializer, base-api.ts:373-409). go: fmt.Sprintf('%v') (object_serializer.go:44, base_api.go:377-405). php, dart, swift, elixir: stringify fallback to the language's string conversion. rust: any residual composite becomes compact JSON text (base_api.rs:523-531).

**Canonical behaviour.** Every scalar field value in an application/x-www-form-urlencoded body is encoded as ObjectSerializer.stringify(value) (canonical date, date-time, duration, uuid, enum, boolean forms), arrays of scalars repeat the key, null fields and null elements are omitted (all already true in 11 of 12 after the ruby fix). A value that is a model, map or array-of-objects has no defined encoding in the generator today (it does not emit the OAS encoding style/explode), so the SDK raises its serialization error ('form-urlencoded field <name> must be a scalar or array of scalars') instead of emitting java toString(), python str(), node '[object Object]', go %v, ruby to_s, rust compact JSON. Supporting OAS form/deepObject styles (reusing ValueSerializer.serializeStyled) is deferred until the generator surfaces the encoding block.

**Why.** (a) OAS only defines object encoding in form bodies through style/explode; an unqualified guess is not spec-conformant. (b) The scalar half is already majority-canonical and ruby now routes through ObjectSerializer.stringify (BaseApi.rb:267-278, with '*'/'~' normalised). (c) Failing loudly is the only choice that cannot put garbage on the wire. (d) A type guard needs no dependency.

**Rejected.** Rejected: implement deepObject/form-explode now (the finding's proposal): requires the codegen to carry encoding.style/explode into every op template and a body-specific styled serializer in 12 languages (L) with no spec in the repo needing it. Rejected: keep the per-language repr: 12 different byte strings, none valid for a server.

**Per language.**

- java: In the Map form loop (BaseApi.java:258-270) throw SerializationException when the element is not a String/Number/Boolean/Enum/temporal/UUID. (templates/java/base_api.mustache)
- kotlin: Same guard in the parts loop (BaseApi.kt:217-232). (templates/kotlin/base_api.mustache)
- csharp: Guard before ObjectSerializer.Stringify in the form loop (BaseApi.cs:241-277). (templates/csharp/base_api.mustache)
- php: Guard in the application/x-www-form-urlencoded branch of serializeBody (BaseApi.php:450+). (templates/php/base_api.mustache)
- python: Guard before stringify in the form_pairs loop (base_api.py:320-345): BaseModel/dict value => SerializationException. (templates/python/base_api.mustache)
- ruby: Guard in the pairs builder (BaseApi.rb:267-278): Hash/Struct/model item => Errors::SerializationError. (templates/ruby/base_api.mustache)
- node: Guard before ObjectSerializer.stringify (base-api.ts:373-398): plain object => SerializationError. (templates/node/base_api.mustache)
- dart: Guard in the Map form loop (base_api.dart:420-440). (templates/dart/base_api.mustache)
- go: Guard where toStringSlice/stringify are called (base_api.go:386-405): map/struct that is not a time or DateTime => SerializationError. (templates/go/base_api.mustache)
- rust: form_scalar_to_string returns Result; Object / nested Array => Err(SerializationError) instead of other.to_string() (base_api.rs:523-531). (templates/rust/base_api.mustache)
- swift: Guard before ObjectSerializer.stringify in the params loop (BaseApi.swift:415-433). (templates/swift/base_api.mustache)
- elixir: Guard in serialize_body for form maps (base_api.ex:480-486): map/struct value (except DateTime/Date) => raise SerializationError. (templates/elixir/base_api.mustache)

**Tests.** Scalar canonical forms: extend the existing form test on setPetPreferences (java PetApiTest.java:~700 pairs assertion and the equivalent in every PetApiTest/pet_api_test) with the new remindAt field: wire contains remindAt=2024-01-15T10%3A30%3A00.000%2B00%3A00 and still no '%20'. Rejection: add one test per BaseApi suite (java BaseApiTest.serializesFormUrlencodedBody neighbour, and the kotlin/csharp/php/python/ruby/node/dart/go/rust/swift/elixir equivalents) that posts application/x-www-form-urlencoded with a Map body {location: {lat: 1.5}} and asserts the serialization error; this needs no spec change.

**Petstore spec addition.**

```yaml
# paths./pet/{petId}/preferences (setPetPreferences) requestBody.content.application/x-www-form-urlencoded.schema.properties, after renewalDate
                remindAt:
                  type: string
                  format: date-time
# expected body fragment: remindAt=2024-01-15T10%3A30%3A00.000%2B00%3A00
```

**Risk.** A spec that already has a nested object in a form body (none in this repo) will start failing at call time instead of sending a meaningless value. Deferred follow-up L: OAS encoding style support.

## D40 One value-preserving string-literal escaper per language for every spec string written into a code literal (defaults, server urls/variables, OAuth urls/scopes)

- Ledger ids: F045 F180 F362 F176 F048 | Theme: codegen-escaping-and-names | Still true: **yes** | Effort: L | Wire-visible: True | Needs user: False

**Current state.** All still true; only line numbers and a few partial fixes changed since the ledger (go/rust/elixir escape scope names, csharp XML-escapes server doc copies). Default literals are built in each BetterXCodegen.toDefaultValue (line numbers move, re-grep 'escapeText(String.valueOf'):
- java: "\""+escapeText(default)+"\"" (BetterJavaCodegen ~632). Quote and backslash right; tab/newline/CR become a space, `*/` becomes `*_/`. Global servers.mustache:34-40 writes `"{{{serverDescription}}}"` raw (a `"` breaks it). Operation servers api.mustache:60,94 write `"{{value}}"` / `"{{url}}"` HTML-escaped (`=` -> `&#x3D;`). formatJavaScopes (~955) joins scope names raw.
- kotlin: same as java (~552) plus `$name` in a default, in servers.mustache and in tokenUrl/scopes (BetterKotlinCodegen ~611-631) is a string template. api.mustache:45,60,71 HTML-escaped.
- csharp: same as java (~561); no interpolation. Server doc copies use lambda.escapeXml (fixed) but the literals are raw (servers.mustache:30-38). formatCSharpScopes raw. api.mustache:37,64,77 HTML-escaped.
- go: same as java (~604). formatGoScopes escapes backslash and quote (ok). servers.mustache:25-31 raw. api.mustache:44,63 HTML-escaped.
- rust: `String::from("..escapeText..")` (~592); formatRustScopes escapes; servers.mustache:41-51 raw; api.mustache:42,62,71 HTML-escaped. The default is echoed in a `///` doc line (models/model.mustache:103).
- swift: same as java (~517); formatSwiftScopes raw; servers.mustache:27-33 raw; api.mustache:18,39,46 HTML-escaped.
- elixir: `".."` / `:".."` with escapeText (~482-484) and NO `#{` guard, so `#{x}` interpolates; formatElixirScopes does guard `#{`; servers.mustache:28-34 raw; api.mustache:16,48,57,63 HTML-escaped.
- dart: `'`+escapeText+`'` (~596): an apostrophe is a syntax error, `$x` interpolates; formatDartScopes raw; servers.mustache:29-35 raw.
- php: `'`+escapeText+`'` (~644): `'` breaks, and `\"` stays two literal characters so the runtime default becomes say \"hi\"; formatPhpScopes raw; server_variant.mustache:31,42 and server_variable_enum.mustache:11 HTML-escaped.
- ruby: `'`+escapeText+`'` (~759): same breakage as php, plus escapeUnsafeCharacters turns `#{` into a literal `\#{`; servers.mustache:25-31 raw; api.mustache:25,58,69 HTML-escaped.
- node: getQuoteChar()+escapeText+getQuoteChar() (~566): `'` breaks; formatNodeScopes raw; servers.mustache:32-37 raw; apis.mustache:35,66,70 HTML-escaped.
- python: own `val.replace("'", "\\'")` (~768) with no escapeText: backslash and newline untouched (a newline is a syntax error, `\n` becomes a real newline); formatPythonScopes raw; servers.mustache:25-31 raw; api.mustache:44,64 HTML-escaped.
Goldens never show it because every fixture string is plain (java models/Defaults.java:74 `"untitled"`, php Models/Defaults.php:27 `'untitled'`, java api/PetApi.java:119 `"https://external-api.example.com/v1"`, php Servers.php:59).

**Canonical behaviour.** Contract: a string literal in generated source must evaluate to EXACTLY the spec's bytes. Add one overridable `escapeStringLiteral(String raw)` to AbstractBetterCodegen (body only, quotes stay in the template/caller) and expose it as `{{#lambda.stringLiteral}}{{{x}}}{{/lambda.stringLiteral}}`. Use it in every toDefaultValue string branch (replace escapeText), in every server/operation-server template (global: serverUrl, serverDescription, varDefault, varDescription, varEnumValues; operation: url, value), and in the OAuth builders (tokenUrl, refreshUrl, authorizationUrl, openIdConnectUrl, keyParamName, scope names; all 12 build*SuperArgs/format*Scopes). Rules: never escapeText, never escapeUnsafeCharacters (`*/` is harmless in a literal), never collapse newline/tab to a space, never HTML-escape (triple braces only; JMustache turns `=`, `&`, `'` into entities and changes wire bytes), never htmlUnescape (defaults, URLs, enum values are data; the existing preprocessOpenAPI comment already says so). Always escape backslash and the language's own quote character. Newline, CR, tab and other C0 controls use the language's escape. Neutralise the sigil only where that quote style interpolates: kotlin and dart `$` -> `\$`, elixir `#{` -> `\#{`. Swift `\(` is already safe because the backslash is doubled. ruby and php keep single-quoted literals where the ONLY escapes are `\\` and `\'`; a raw newline or tab stays literal inside the quotes (valid, exact). The wire value of a default or server URL is then identical in all 12.

**Why.** Criteria (a) and (c): a literal must evaluate to the spec bytes, and today ruby/php send `say \"hi\"` on the wire, newlines become spaces, and HTML entities replace `=`/`&` in operation-server URLs, all silent data changes. (b) java/kotlin/csharp/go/rust/swift/elixir already get quote and backslash right, so the fix is a shared escaper with per-language tables. (d) Needs no dependency. Root cause is shared by F045/F180 (defaults), F048/F176 (server literals, HTML escaping) and the unescaped scope/URL builders of F362. The StringBuilder-in-Java hygiene part of F362 is not parity and is excluded.

**Rejected.** (1) Fix escapeText per language: rejected, it is a comment escaper and mutates by design (newline->space, `*/`, `\"` that single-quoted ruby/php keep literally). (2) Upstream escapeTextInSingleQuotes: rejected, it still collapses newlines and applies escapeUnsafeCharacters. (3) Warn or refuse hostile defaults: rejected, silently dropping a spec default is a silent data change. (4) Raw/verbatim strings (C# @"", Rust r#"", Python r''): rejected, not available or not safe in all 12. (5) Switch ruby/php to double-quoted to get \n: rejected, adds `$`/`#{`/`{$` interpolation hazards for no gain, since a literal newline inside single quotes is already exact. Does not contradict AGENTS.md (R7-6 unescapeDocComment only concerns prose in comments, covered by the next decision).

**Per language.**

- java: BetterJavaCodegen.toDefaultValue (~632 and the enum branch ~655): replace escapeText with escapeStringLiteral (escapes \\, ", \n \r \t, other C0 as \uXXXX). formatJavaScopes/buildJavaSuperArgs (~955-993): escape each scope/url. templates/java/servers.mustache:34-40 and api/api.mustache:60,94 (and :59 doc) wrap values in lambda.stringLiteral with triple braces. (generators/java/BetterJavaCodegen.java; templates/java/servers.mustache; templates/java/api/api.mustache)
- kotlin: BetterKotlinCodegen.toDefaultValue (~552), formatKotlinScopes (~611) and tokenUrl/authorizationUrl args (~631): escapeStringLiteral with `$` -> `\$`. templates/kotlin/servers.mustache:31-37, api/api.mustache:45,60,71. (generators/kotlin/BetterKotlinCodegen.java; templates/kotlin/servers.mustache; templates/kotlin/api/api.mustache)
- csharp: BetterCSharpCodegen.toDefaultValue (~561) and formatCSharpScopes (~797). templates/csharp/servers.mustache:30-38 and api/api.mustache:37,64,77 use lambda.stringLiteral (the escapeXml on the doc copies stays). (generators/csharp/BetterCSharpCodegen.java; templates/csharp/servers.mustache; templates/csharp/api/api.mustache)
- go: BetterGoCodegen.toDefaultValue (~604); formatGoScopes already escapes \ and " but not control chars: route through escapeStringLiteral. templates/go/servers.mustache:25-31, api/api.mustache:44,63. (generators/go/BetterGoCodegen.java; templates/go/servers.mustache; templates/go/api/api.mustache)
- rust: BetterRustCodegen.toDefaultValue (~592) and formatRustScopes (~1059) via escapeStringLiteral (control chars as \u{..}); templates/rust/servers.mustache:41-51, api/api.mustache:42,62,71. (generators/rust/BetterRustCodegen.java; templates/rust/servers.mustache; templates/rust/api/api.mustache)
- swift: BetterSwiftCodegen.toDefaultValue (~517), formatSwiftScopes (~667) and the super-call builder (~693-700); control chars as \u{..}. templates/swift/servers.mustache:27-33, api/api.mustache:18,39,46. (generators/swift/BetterSwiftCodegen.java; templates/swift/servers.mustache; templates/swift/api/api.mustache)
- elixir: BetterElixirCodegen.toDefaultValue (~482-484, both the :".." atom and the string form), formatElixirScopes (~683) and the tokenUrl arg (~711): escapeStringLiteral with `#{` -> `\#{`. templates/elixir/servers.mustache:28-34, api/api.mustache:16,48,57,63. (generators/elixir/BetterElixirCodegen.java; templates/elixir/servers.mustache; templates/elixir/api/api.mustache)
- dart: BetterDartCodegen.toDefaultValue (~596), formatDartScopes (~977): single-quoted, escape \\ \' \n \r \t and `$` -> `\$`. templates/dart/servers.mustache:29-35, api/api.mustache:33,55. (generators/dart/BetterDartCodegen.java; templates/dart/servers.mustache; templates/dart/api/api.mustache)
- php: BetterPHPCodegen.toDefaultValue (~644) and formatPhpScopes (~783): single-quoted with only \\ and \' escapes (no \" ). templates/php/servers.mustache:33-39, api/server_variant.mustache:31,42, api/server_variable_enum.mustache:11. (generators/php/BetterPHPCodegen.java; templates/php/servers.mustache; templates/php/api/server_variant.mustache; templates/php/api/server_variable_enum.mustache)
- ruby: BetterRubyCodegen.toDefaultValue (~759): single-quoted with only \\ and \' escapes. Remove the `#{` -> `\#{` rewrite from escapeUnsafeCharacters (see next decision). templates/ruby/servers.mustache:25-31 (enum_values uses %w[..], which breaks on a value with a space: emit a quoted array instead), api/api.mustache:25,58,69. (generators/ruby/BetterRubyCodegen.java; templates/ruby/servers.mustache; templates/ruby/api/api.mustache)
- node: BetterNodeCodegen.toDefaultValue (~566, the non-enum string branch; the enum branch ~562 also needs it) and formatNodeScopes (~1092): single-quoted, \\ \' \n \r \t \uXXXX. templates/node/servers.mustache:32-37, api/apis.mustache:35,66,70. (generators/node/BetterNodeCodegen.java; templates/node/servers.mustache; templates/node/api/apis.mustache)
- python: BetterPythonCodegen.toDefaultValue (~768): replace the quote-only replace with escapeStringLiteral (\\ \' \n \r \t \xHH); formatPythonScopes (~1186). templates/python/servers.mustache:25-31, api/api.mustache:44,64. (generators/python/BetterPythonCodegen.java; templates/python/servers.mustache; templates/python/api/api.mustache)

**Tests.** Lock in the existing generated tests; add no new test files (AGENTS.md Tests). (1) Defaults: ObjectSerializerTest in every language decodes `{}` into Defaults and asserts the new `greeting` equals the exact string: java/kotlin ObjectSerializerTest.java(~897)/.kt, csharp Test/ObjectSerializerTest.cs, php tests/ObjectSerializerTest.php, python test/test_object_serializer.py, ruby test/object_serializer_test.rb, node test/object-serializer.test.ts, dart test/object_serializer_test.dart, go pkg/object_serializer_test.go, rust tests/composed_schema_test.rs (already asserts `untitled`), swift Tests/PetstoreClientTests/ObjectSerializerTests.swift, elixir test/object_serializer_test.exs. Golden compilation is the other half of the lock (a bad escape fails the build). (2) Global server: ServerConfigurationTest/ServerVariableTest in every language assert the new third server's url, description, variable default and enum values round-trip unchanged. (3) Operation-server literal and the HTML-entity bug (F176): covered by the server-types YAML (channel enum values `a&b` and `it's`), asserted through each language's PetApiTest getMultiServerPetInfo case on the variant's getUrl(). (4) Per-language escaper table cases go in an existing generator unit test (no new files), otherwise rely on the golden.

**Petstore spec addition.**

```yaml
# petstore openapi.yaml
# (a) components.schemas.Defaults.properties: add
        greeting:
          type: string
          default: "it's \"quoted\" \\ back\tslash\nnext $USD #{x} a*/b"
# (b) top-level servers: append a THIRD entry (keeps Server0/Server1 indexes stable)
  - url: https://{tenant}.example.com/api
    description: "Bob's \"sandbox\" & co: $5 #{x} a*/b"
    variables:
      tenant:
        default: "o'neil"
        description: "Tenant's \"id\""
        enum: ["o'neil", "a&b"]
# (c) operation-level URL/enum literals: see the getMultiServerPetInfo YAML in the server-types decision.
```

**Risk.** Every golden string default and server literal is re-rendered (plain strings unchanged, so diffs should be nil for the current petstore). Post-format Docker formatters (black, prettier, dart format) may re-quote but never change the value. Doc comments that echo a default (rust model.mustache:103) keep the escaped single-line form. postProcessFile whole-file regexes (go `, \)`->`)` BetterGoCodegen ~631, python `\{ ('.*?') }` ~1191) can rewrite text inside a literal containing `, )` or `{ 'x' }`; scope them to code lines or cover with the same fixture. The StringBuilder-in-Java parts of F362 (ruby/python import and RBS builders) are code hygiene, not parity, and are NOT part of this decision: only the unescaped spec strings in those builders are.

## D41 Doc prose and package-manifest descriptions: one normalisation for comments, one escaper per manifest format

- Ledger ids: F178 F181 F177 | Theme: codegen-escaping-and-names | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** All three still true (templates unchanged). Prose from the spec reaches comments and manifests by different routes.
Tag and tag-externalDocs descriptions (AbstractBetterCodegen.injectTagMetadata ~4663) are stored raw; the other prose (operation notes, model description) goes through escapeText. Rendering of the raw tag text: java api.mustache:25, kotlin :25, php :26, go :74, ruby :78, elixir :75 use `{{{.}}}` (a multi-line value leaves bare code lines in go/ruby `//`/`#` comments; `*/` closes the java/kotlin/php block comment; `#{` interpolates in elixir `@moduledoc`); dart api.mustache:67, node apis.mustache:79, rust api.mustache:80 and swift api.mustache:55 use `{{.}}` which HTML-escapes already-unescaped text (`don't` -> `don&#39;t` in a comment); csharp :89 uses lambda.escapeXml (correct for XML doc, but newlines are not collapsed); python :75 is inside a docstring (only the closing triple quote can break it).
escapeUnsafeCharacters overrides: python (BetterPythonCodegen ~749) rewrites `'''` but no python template contains `'''` (grep finds none) so it only mangles prose; ruby (~829) rewrites `=begin`/`=end` (cannot occur at line start: escapeText collapses newlines) and `#{` -> `\#{`, which then LEAKS a backslash into `#` comments and single-quoted literals where `#{` is inert; elixir has no override and inherits only `*/`, so `Fetches /users/#{id}` in an operation description interpolates in `@doc` heredocs / `@moduledoc ".."` (elixir/api/api.mustache:23,99).
Manifests (`appDescription` is the escapeText'd info.description): node package.json (package.mustache:5), php composer.mustache:3, python pyproject_toml.mustache:4 and rust cargo_toml.mustache:7 use HTML-escaping `{{appDescription}}` (`Pets & owners'` is published as `Pets &amp; owners&#39;`); java pom.mustache:8 and csharp csproj.mustache:5 write it raw into XML (`&`/`<` makes the POM/csproj ill-formed); ruby gemspec.mustache:15 (`'..'`), kotlin build_gradle.mustache:19 (`".."`, `$` interpolates) and elixir mix_exs.mustache:44 (`".."`, `#{` interpolates) write it raw into a literal; dart pubspec.mustache:3-8 writes an unquoted YAML scalar (`: `, leading `&`/`*`/`#` break it). go/swift have no manifest description. Goldens show only the plain sentence (java pom.xml:8, node package.json:4, dart pubspec.yaml:3).
F048 doc copies: global server descriptions use `{{serverDescription}}` (HTML-escaped) in 10 comment sites; csharp now uses escapeXml (fixed); python servers.mustache:35-37 is raw inside a docstring.

**Canonical behaviour.** Three contexts, three rules, no overlap with the literal escaper. (1) Doc-comment prose (tag, tag/op/model externalDocs, global server and server-variable descriptions, info): htmlUnescape once in preprocessOpenAPI (extend it to openAPI.getServers() and their variables, and to every ExternalDocumentation description; today it skips servers), then the same pipeline as operation notes: escapeText-style newline collapse, then escapeUnsafeCharacters, rendered with TRIPLE braces and with the same unescapeDocComment wrapper the language already uses for operation/model docs (go, dart, ruby). Never `{{ }}` for prose (HTML entities appear in comments). (2) escapeUnsafeCharacters is redefined as exactly the terminators/interpolators of the delimiters the templates really use for prose: java, kotlin, php, node, go (Servers `/* */`): `*/` -> `*_/` (kotlin also `/*` -> `/_*` because its block comments nest); csharp: lambda.escapeXml only; dart, rust, swift, ruby (`#`/`///`): identity; python: identity (escapeText already turned `"` into `\"`, so a triple quote cannot form); elixir: `#{` -> `\#{`. Delete the python and ruby overrides. (3) Manifest descriptions: expose a new plain `appDescriptionPlain` (htmlUnescaped info.description, whitespace runs collapsed to one space, NOT escapeText'd) and escape it for the manifest's syntax: package.json, composer.json, pyproject.toml, Cargo.toml and pubspec.yaml (as a double-quoted scalar) use one `lambda.jsonString` (JSON, TOML basic strings and YAML double-quoted share \\ \" \n \uXXXX); pom.xml and csproj use lambda.escapeXml (exists); gemspec, build.gradle.kts and mix.exs use the previous decision's lambda.stringLiteral. The published description equals the spec text byte for byte.

**Why.** Criteria (a) and (c): prose must read as the spec says without HTML entities or broken comments, and each escaper must target the delimiter actually used. The current escapeUnsafeCharacters overrides target hazards that do not exist (python `'''`, ruby `=end`) and miss the real one (elixir `#{`). Manifests need the escaping of their own format (JSON/TOML/YAML-double-quoted share one rule, XML has lambda.escapeXml, three languages reuse the literal escaper). Consistent with AGENTS.md R7-6 (unescapeDocComment for doc prose). Feasible in all 12 with existing mechanisms.

**Rejected.** (1) Keep HTML-escaping manifests (`{{ }}`): rejected, it publishes entities to npm/PyPI/crates.io/Packagist and is only right for XML by accident. (2) Keep the cross-language `*/` guard on prose in languages that never use `/* */`: rejected, it alters glob examples like `src/**/*.ts` for no benefit. (3) Preserve multi-line prose in comments: rejected for now, the standing behaviour for all other prose is newline->space. (4) One generic Java escape function for all manifests: rejected, JSON/TOML/XML/Ruby/Kotlin/Elixir need different rules and lambdas keep the quotes in the template.

**Per language.**

- java: api.mustache:25,28 keep `{{{.}}}` but feed collapsed, `*/`-guarded text; pom.mustache:8 uses lambda.escapeXml over appDescriptionPlain; global-server Javadoc copies servers.mustache:29-31 stay `*/`-guarded. (templates/java/api/api.mustache; templates/java/pom.mustache; AbstractBetterCodegen.injectTagMetadata)
- kotlin: Same as java for api.mustache:25,28; build_gradle.mustache:19 -> lambda.stringLiteral over appDescriptionPlain (fixes `$`); escapeUnsafeCharacters also guards `/*` (nested comments). (templates/kotlin/api/api.mustache; templates/kotlin/build_gradle.mustache; BetterKotlinCodegen)
- csharp: api.mustache:85-91 already escapeXml; make tag text pass the newline-collapse pipeline; csproj.mustache:5 `{{{appDescription}}}` -> `{{#lambda.escapeXml}}{{{appDescriptionPlain}}}{{/lambda.escapeXml}}`. (templates/csharp/api/api.mustache; templates/csharp/csproj.mustache)
- go: api.mustache:74,77 get the collapsed/unescaped text (wrap with the existing unescapeDocComment like the rest of go's docs); no manifest description. The Servers `/* */` comment (servers.mustache:22-23) keeps the `*/` guard. (templates/go/api/api.mustache; templates/go/servers.mustache)
- rust: api.mustache:80,83 and servers.mustache:35-38 `{{.}}`/`{{serverDescription}}` -> triple braces; cargo_toml.mustache:7 -> lambda.jsonString over appDescriptionPlain. (templates/rust/api/api.mustache; templates/rust/servers.mustache; templates/rust/cargo_toml.mustache)
- swift: api.mustache:55,58 and servers.mustache:23-25 `{{ }}` -> triple braces; no manifest description. (templates/swift/api/api.mustache; templates/swift/servers.mustache)
- elixir: Add BetterElixirCodegen.escapeUnsafeCharacters override (`#{` -> `\#{`, no `*/` rewrite); api.mustache:23,75,99 unchanged; mix_exs.mustache:44 -> lambda.stringLiteral over appDescriptionPlain. (generators/elixir/BetterElixirCodegen.java; templates/elixir/mix_exs.mustache)
- dart: api.mustache:67,70 and servers.mustache:25-27 `{{ }}` -> triple braces (+ existing unescapeDocComment); pubspec.mustache:3-8 writes `description: "{{#lambda.jsonString}}{{{appDescriptionPlain}}}{{/lambda.jsonString}}"`. (templates/dart/api/api.mustache; templates/dart/servers.mustache; templates/dart/pubspec.mustache)
- php: api.mustache:26,29 keep triple braces with `*/` guard; composer.mustache:3 -> lambda.jsonString over appDescriptionPlain (also removes the entity output). (templates/php/api/api.mustache; templates/php/composer.mustache)
- ruby: Delete BetterRubyCodegen.escapeUnsafeCharacters override (~829); gemspec.mustache:15 -> `{{#lambda.stringLiteral}}{{{appDescriptionPlain}}}{{/lambda.stringLiteral}}` (single-quoted); api.mustache:78 gets the collapsed text. (generators/ruby/BetterRubyCodegen.java; templates/ruby/gemspec.mustache; templates/ruby/api/api.mustache)
- node: apis.mustache:79,82 and servers.mustache:27-29 `{{.}}`/`{{ }}` -> triple braces with `*/` guard; package.mustache:5 -> lambda.jsonString over appDescriptionPlain. (templates/node/api/apis.mustache; templates/node/servers.mustache; templates/node/package.mustache)
- python: Delete BetterPythonCodegen.escapeUnsafeCharacters override (~749, dead `'''` rule); pyproject_toml.mustache:4 -> lambda.jsonString over appDescriptionPlain; servers.mustache:35-37 uses the collapsed text. (generators/python/BetterPythonCodegen.java; templates/python/pyproject_toml.mustache; templates/python/servers.mustache)

**Tests.** The proof is that the golden still compiles/lints (a bad comment or manifest fails the build) plus the metadata tests. Extend the existing MetadataTest/metadata_test in each language (java/test/MetadataTest.mustache, kotlin, csharp, php tests/MetadataTest.php, python test/test_metadata.py, ruby test/metadata_test.rb, node test/metadata.test.ts, dart test/metadata_test.dart, rust tests/metadata_test.rs, elixir) so that instead of 'description is non-empty' they parse the manifest (JSON/TOML/XML/YAML, ruby Gem::Specification.load, mix project) and assert the description equals the spec's info.description verbatim. Elixir compilation with --warnings-as-errors locks the `#{petId}` case; go/rust/swift/dart/csharp/ruby compilation locks the multi-line tag case; java/kotlin/php/node compilation locks the `*/` case.

**Petstore spec addition.**

```yaml
# petstore openapi.yaml
info:
  description: "A simplified Pet Store API for integration testing. Covers pets & owners, <admin> notes, \"quoted\" text, it's $USD and #{x}."
tags:
  - name: pet
    description: |
      Everything about your Pets
      (globs like /pets/* and /pets/**/ and an id of #{petId})
    externalDocs:
      description: "Find out more about pets (it's */ here)\non two lines"
      url: https://example.com/docs/pets
```

**Risk.** Entity-decoded server and tag text now appears in comments, so goldens with `&`-style prose change (none today). Removing the python/ruby overrides changes any description containing `'''`, `=end` or `#{` from mangled to verbatim. Changing the info.description fixture re-renders every README/API-info/manifest; review the diff once.

## D42 Per-operation server codegen: one type per variable per operation, unique variant names

- Ledger ids: F122 F145 | Theme: codegen-escaping-and-names | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Both findings are the same defect and still true. AbstractBetterCodegen.enrichOperationServers (~4540-4620) builds serverVariables per variant and never de-duplicates; variantName = PascalCase(description with non-alphanumerics -> '_') else `Server<i>`, with no uniqueness or leading-digit check. Every template emits the variable enum inside `{{#variants}}{{#serverVariables}}{{#hasEnumValues}}` under a name that excludes the variant:
- java (api.mustache:52-71): nested `enum {{pascalName}}` twice -> duplicate member compile error (current golden shows the single case PetApi.java:134 `enum Region`).
- kotlin api.mustache, csharp api.mustache:36-37 (CS0101), dart api.mustache:33, rust api.mustache:42, swift api.mustache:18: duplicate type declarations, compile error.
- go api.mustache:36-44: `type {{serverTypeName}}{{pascalName}}` duplicate (golden pet_api.go:43-49 `GetMultiServerPetInfoServerRegion`); a description-only collision also duplicates the `...Server<Variant>` structs (pet_api.go:63-72).
- node apis.mustache:35: duplicate `export enum`.
- python api.mustache:39-44 and ruby api.mustache:25: silent re-definition, last one wins (wrong when enum sets differ).
- php server_variable_enum.mustache:11: same file written twice, last wins.
- elixir api.mustache:16: module redefinition, fatal under --warnings-as-errors.
Petstore getMultiServerPetInfo (openapi.yaml ~911-925) has only one variant with a variable, so nothing trips. Variant names from descriptions such as `1st region` or non-ASCII text become an invalid or empty identifier, and a variant named like a variable enum (`Region` vs `region`) collides with it in java/kotlin/csharp/swift.

**Canonical behaviour.** Compute naming in enrichOperationServers and make every template emit enums from a per-operation list instead of per variant. (a) Variant names: PascalCase(description) as today, but fall back to `Server<index>` when the result is empty, does not start with a letter, or equals an earlier variant's name or any enum type name of the operation; if `Server<index>` would still collide, append the index. (b) Enum types: a variable name that appears in exactly ONE variant of the operation keeps the current name (`Region`, `Environment`; zero golden churn). If the same variable name appears in two or more variants of one operation, each variant's enum is named `<VariantName><Var>` (e.g. `RegionalRegion`, `MirrorRegion`), because their value sets may differ. Each enum is emitted exactly once, from a new `typeDef.serverEnums` list (typeName, varName, values, owning variant), and variants reference it by `enumTypeName`. Non-enum variables stay plain strings. Identical in all 12; php/python/ruby/elixir module and file names follow the same typeName.

**Why.** Criteria (b)/(c): all 12 emit the enum inside the variants loop, so any two variants sharing a variable break compilation in 8 languages and silently last-wins in 4 (python, ruby, php, and elixir under warnings). A variant must never accept a value its own server does not list, so merging value sets is rejected; keeping the existing name when the variable is unique avoids any golden churn. F122 and F145 are the same defect. Feasible in all 12 by computing names once in Java and iterating a per-operation list in the template.

**Rejected.** (1) Merge same-named variables into one enum with the union of values: rejected, a variant would silently accept values its server never declared (weakened validation). (2) Always prefix every enum with the variant name: rejected, renames every existing public type (Region -> RegionalRegion in all goldens) for no safety gain. (3) Emit only the first enum and ignore later ones: rejected, silent first/last-wins data change (what python/php/ruby do today). (4) Reject specs with duplicate variables: rejected, `https://{region}.a.com` + `https://{region}.b.com` is a normal multi-region pattern.

**Per language.**

- java: api.mustache:52-71 iterate `{{#serverEnums}}` (emit once, nested enum named {{typeName}}); record components at :86 use `{{enumTypeName}}`. (templates/java/api/api.mustache; AbstractBetterCodegen.enrichOperationServers)
- kotlin: Emit enums from serverEnums once; variants reference enumTypeName. (templates/kotlin/api/api.mustache)
- csharp: api.mustache:36-37 and the variant constructors: emit from serverEnums; type name `{{serverTypeName}}{{typeName}}`. (templates/csharp/api/api.mustache)
- go: api.mustache:36-48: `{{#serverEnums}}` produces `type {{serverTypeName}}{{typeName}} string` and its consts once; variant structs use it (golden today pet_api.go:43-72). (templates/go/api/api.mustache)
- rust: api.mustache ~35-55: emit each enum once from serverEnums. (templates/rust/api/api.mustache)
- swift: api.mustache ~14-30: emit each enum once from serverEnums. (templates/swift/api/api.mustache)
- elixir: api.mustache ~10-60: one module per serverEnums entry (also the `allowed_*` list at :48). (templates/elixir/api/api.mustache)
- dart: api.mustache ~28-50: emit enum once; fields use enumTypeName. (templates/dart/api/api.mustache)
- php: server_variable_enum.mustache is rendered once per serverEnums entry (file name = typeName) instead of once per variant variable; server_variant.mustache references enumTypeName. (templates/php/api/server_variable_enum.mustache; templates/php/api/server_variant.mustache; BetterPHPCodegen supporting-file registration)
- ruby: api.mustache ~20-30: emit module/constants once per serverEnums entry; add the matching rbs declaration. (templates/ruby/api/api.mustache; sig templates)
- node: apis.mustache ~30-40: single `export enum {{typeName}}` per entry. (templates/node/api/apis.mustache)
- python: api.mustache:39-48: one Enum class per serverEnums entry. (templates/python/api/api.mustache)

**Tests.** Existing per-language PetApiTest already exercises getMultiServerPetInfo (java src/test/java/.../api/PetApiTest.java, kotlin PetApiTest.kt, csharp Test/Api/PetApiTest.cs, php tests/Api/PetApiTest.php, python test/api/test_pet_api.py, ruby test/api/pet_api_test.rb, node, dart, go, rust, swift, elixir test/pet_api_test.exs). Extend that case to build the Mirror variant with a `MirrorRegion` value and assert getUrl() equals the expected string, including `a&b` and `it's` channel values (this also locks the HTML-entity bug F176). Golden compilation (duplicate types in 8 languages, elixir warnings-as-errors) is the main lock. Variant-name uniqueness/leading-digit rules are Java-side logic: add table cases to the nearest existing generator unit test (src/test/java/.../generators/OperationDecoratorTest.java) rather than a new file.

**Petstore spec addition.**

```yaml
# petstore openapi.yaml, path /pet/{petId}/multi: replace the servers list (Primary and Regional unchanged, Mirror added)
      servers:
        - url: https://primary.example.com/v1
          description: Primary
        - url: https://{region}.example.com/v1
          description: Regional
          variables:
            region:
              default: us
              description: Geographic region
              enum: [us, eu, ap]
        - url: https://{region}.mirror.example.com/{channel}/v1
          description: Mirror
          variables:
            region:
              default: us
              description: Mirror region
              enum: [us, eu]
            channel:
              default: "a&b"
              description: Release channel
              enum: ["a&b", "it's"]
```

**Risk.** Public type names change only for operations that already could not compile (java/kotlin/csharp/go/dart/rust/swift/node) or silently misbehaved (python/ruby/php/elixir). The YAML below renames the existing `Region` enum of getMultiServerPetInfo to `RegionalRegion` because the new Mirror variant shares `region`; the per-language PetApiTest templates that reference it must be updated.

## D43 Identifiers: a digit-leading or empty sanitised name gets a deterministic legal fallback, never an escape prefix

- Ledger ids: F172 F363 | Theme: codegen-escaping-and-names | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Both still true (code unchanged: AbstractBetterCodegen.toVarName ~2236, toParamName ~2274, toEnumVarName ~2381-2405). A property or query param `2fa`/`1stPlace` is routed to escapeReservedWord, so the result is whatever each language's keyword escape does: java, kotlin, node, swift, ruby, elixir `_2fa` (legal); python `2fa_` (SyntaxError), dart `2fa_` (SyntaxError), php `$2fa_` (parse error), rust `r#2fa` (not a legal raw identifier), go `_2fa` (unexported struct field; the alias MarshalJSON/UnmarshalJSON, e.g. go/pkg/models/pet.go:114-125, skips it so the property is silently dropped both ways), csharp `_2fa` (CA1707 under the analyzers; BetterCSharpCodegen ~160 documents the rule). Dart also hits the same trap for digit-leading enum members: toEnumVarName returns `_` + name and a leading underscore is library-private in Dart.
Enum values: toEnumVarName strips the leading and trailing underscore after casing, so a value that is only spaces or symbols (`" "`, `@@`, non-ASCII without allowUnicodeIdentifiers) cleans to "" and the generated constant has an empty name -> compile error in 11 languages. Only BetterPHPCodegen.toEnumVarName (~694) maps whitespace-only to `SPACE_<n>`; nothing handles a collision after casing (`a-b` vs `a_b`). Not exercised: the petstore has no such property or enum value.

**Canonical behaviour.** (1) Digit-leading variable/parameter name: prefix the LETTER `n` BEFORE applying the language casing (`2fa` -> `n2fa`; PascalCase csharp/go give `N2fa`; snake gives `n1st_place`). It must not go through escapeReservedWord. The wire name is already preserved by the serializer alias/tag. (2) Enum constant whose cleaned name is empty: whitespace-only -> `SPACE_<length>` (php's rule moved into the base), any other empty result -> `VALUE_<upper-hex codepoints joined by _>` (e.g. `@@` -> `VALUE_0040_0040`), then getEnumCasing; digit-leading enum names get the same letter prefix instead of `_`. (3) After the names of one enum are computed, a second constant with an identical name gets a `_2`, `_3` suffix in declaration order; wire values are untouched. All are pure functions of the value, so output is stable and identical across the 12.

**Why.** Criteria (a)-(c): the result must be a legal, public identifier in all 12 and must not drop data (go currently drops the property in both directions). No single punctuation prefix or suffix works everywhere (underscore is private in dart, rejected by pydantic, unexported in go, CA1707 in csharp), but a letter prefix does. Both findings are the same class: the sanitiser output is not a valid identifier. php already shows the right approach for whitespace-only enum values.

**Rejected.** (1) Keep escapeReservedWord for digits and fix each override: rejected, no single punctuation prefix/suffix is legal in all 12. (2) Prefix `_` and special-case dart/python/go/csharp: rejected, more per-language exceptions and still unexported/private in go/dart. (3) Index-based enum fallback `VALUE_<i>`: rejected, toEnumVarName has no index and names would shift when a value is inserted. (4) Fail generation on unrepresentable values: rejected, dropping or aborting on a legal spec is worse than a deterministic name.

**Per language.**

- java: Base toVarName/toParamName digit branch; base toEnumVarName fallback and dedupe pass. (AbstractBetterCodegen.java)
- kotlin: Inherits the base change. (AbstractBetterCodegen.java)
- csharp: Inherits the base change; the digit branch must not reach escapeReservedWord (BetterCSharpCodegen.shouldEscapeReservedVarName already returns false, but the `|| name.matches("^\\d.*")` test at base ~2236 still triggers). (AbstractBetterCodegen.java)
- go: Same base fix; fixes the silently dropped unexported-field case. (AbstractBetterCodegen.java)
- rust: Base fix; the r# escape in BetterRustCodegen.escapeReservedWord (~637) stays for real keywords only. (AbstractBetterCodegen.java; BetterRustCodegen.java)
- swift: Inherits the base change. (AbstractBetterCodegen.java)
- elixir: Inherits the base change. (AbstractBetterCodegen.java)
- dart: Base fix; digit-leading enum members must not become `_1` (private); BetterDartCodegen.escapeReservedWord (~165) stays for keywords only. (AbstractBetterCodegen.java; BetterDartCodegen.java)
- php: Base fix; delete the SPACE_<n> special case in BetterPHPCodegen.toEnumVarName (~694) because the base now provides it. (AbstractBetterCodegen.java; BetterPHPCodegen.java)
- ruby: Inherits the base change. (AbstractBetterCodegen.java)
- node: Base fix; BetterNodeCodegen.toEnumVarName enumNameMapping override (~603) keeps priority. (AbstractBetterCodegen.java)
- python: Base fix; BetterPythonCodegen.escapeReservedWord (~230) stays for keywords only. (AbstractBetterCodegen.java; BetterPythonCodegen.java)

**Tests.** Golden compile/lint/type-check of the new fixtures is the lock, plus the round-trip in the existing ObjectSerializerTest of each language (EdgeCases with `2fa` set, encode, assert the JSON key is `2fa`, decode back): java/kotlin ObjectSerializerTest, csharp Test/ObjectSerializerTest.cs, php tests/ObjectSerializerTest.php, python test/test_object_serializer.py, ruby test/object_serializer_test.rb, node test/object-serializer.test.ts, dart test/object_serializer_test.dart, go pkg/object_serializer_test.go, rust tests/composed_schema_test.rs, swift ObjectSerializerTests.swift, elixir test/object_serializer_test.exs. For the query parameter, the existing findPetsByStatus case in each PetApiTest asserts `2ndPage=2` reaches the wire. For enums, extend the existing enum round-trip (Color/Availability) with Separator: assert each of the four wire values decodes to a distinct constant and re-encodes to the original string.

**Petstore spec addition.**

```yaml
# petstore openapi.yaml
# components.schemas.EdgeCases.properties: add
        '2fa':
          type: boolean
        separator:
          $ref: '#/components/schemas/Separator'
# components.schemas: add
    Separator:
      type: string
      description: String enum whose values sanitise to an empty or colliding identifier
      enum: [" ", "@@", "a-b", "a_b"]
# /pet/findByStatus get: add an optional query parameter
        - name: 2ndPage
          in: query
          required: false
          schema:
            type: integer
            format: int32
```

**Risk.** Renames the public field/param for digit-leading names in java/kotlin/node/swift/ruby/elixir from `_2fa` to `n2fa` (rare). The enum collision suffix needs a hook after all enum var names exist (postProcessModelsEnum / property enum build): verify upstream DefaultCodegen does not already de-duplicate before writing it. A generated `n2fa` could collide with a real `n2fa` property; reuse the `_2` suffix logic if wanted.

## D44 Reserved-word escaping applies exactly to names the language cannot use (add java literals, narrow php/ruby)

- Ledger ids: F309 F364 | Theme: codegen-escaping-and-names | Still true: **partly** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** F309 is partly fixed: swift keeps `type` (swift Models/ApiResponse.swift:13 `public var type`), dart keeps `type`, `and`, `or`, `not` (BetterDartCodegen.shouldEscapeReservedVarName ~178; dart edge_cases.dart:21-29 shows `and`, `class_`, `return_`). Still over-broad: php escapes `and`/`or` to `$and_`/`$or_` (php Models/EdgeCases.php:36,39) but not `not` (`$not`, :42), and `class_`/`return_` (:45,48), although a keyword is a legal `$property`/parameter name and PHP 8 named arguments accept keywords; ruby `_and`, `_or`, `_not`, `_class`, `_return` (ruby models/edge_cases.rb:79-83), where only `class` really clashes (Object#class); elixir `_and`/`_or`/`_not` (elixir models/edge_cases.ex:47-49) while `class`/`return` are kept (inconsistent; unverified whether keyword atoms are usable). Correctly escaped real keywords: java/kotlin/swift `_class`/`_return`, python `class_`/`return_`, rust `r#return`. go/csharp/node never escape. kotlin escapes soft keywords by design (AGENTS.md: `file` -> `_file`, enforced by KotlinReservedWordsSpec).
F364 still true: reserved-words/java.txt contains no `true`, `false`, `null` (and no `_`, a keyword since Java 9), so a property `null` gives `public Boolean null;` (java models/EdgeCases.java:39-49 shows `and`/`or`/`not` plain, which is right). src/test/resources/scripts/JavaKeywords.java:20 explicitly excludes the literals and JavaReservedWordsSpec only checks the resource is a superset, so adding them is safe. csharp, dart, elixir, go, kotlin, node, ruby, rust, swift and python all list the literals; php does not (and does not need to for variables).

**Canonical behaviour.** Escape an identifier only when the language cannot use it as a plain field/property/parameter name there. Concretely: (1) java.txt gains `true`, `false`, `null`, `_`, and JavaKeywords.java stops excluding the literals so JavaReservedWordsSpec enforces it. (2) php: `shouldEscapeReservedVarName` returns false for variable/property/parameter names (keep escaping `this`, and keep the list for class/method/const contexts), so `$and`, `$or`, `$class`, `$return` stay as written. (3) ruby: narrow the variable-escape set to identifiers that clash with Object/Kernel/Dry::Struct instance methods or cannot be a bare name (`class`, `self`, `nil`, `true`, `false`, `__FILE__`, ...) and drop `and`/`or`/`not`/`return` ONLY IF Steep and the RBS signature for `attribute :and` accept it in the ruby golden; otherwise document ruby `_and/_or/_not` as a FORCED exception in AGENTS.md. (4) elixir: same experiment for `and`/`or`/`not`; keep `_and` as a documented FORCED exception if the struct key or field access does not compile. (5) kotlin's soft-keyword escaping is a standing decision and stays.

**Why.** Criterion (a) and (b): escape only what the language forbids, as swift, dart, go, csharp and node already do; java must also escape its literals, which are illegal identifiers. php keywords are legal `$property`/parameter names. kotlin soft-keyword escaping stays because AGENTS.md records it as a deliberate policy. Ruby and elixir narrowing is conditional on the golden's Steep/mix compile because RBS/atom handling of keyword names was not verified by reading.

**Rejected.** (1) Keep escaping every reserved word everywhere for predictability: rejected, it renames legal public fields (`$and_`) with no language feature forcing it, and swift/dart already moved away from it. (2) Remove the over-broad entries from the shared php.txt list: rejected, the same list guards class/method/constant names where keywords ARE illegal; use the var-name hook instead. (3) Escape java literals only for fields: rejected, they are illegal as any identifier.

**Per language.**

- java: Add true/false/null/_ to src/main/resources/reserved-words/java.txt; update src/test/resources/scripts/JavaKeywords.java:20 so the spec derives them too. (reserved-words/java.txt; src/test/resources/scripts/JavaKeywords.java)
- kotlin: already canonical
- csharp: already canonical
- php: Override shouldEscapeReservedVarName to return false except for `this`; EdgeCases.php:36-48 become `$and`, `$or`, `$not`, `$class`, `$return`; update the PHPCS comment on escapeReservedWord (~150). (generators/php/BetterPHPCodegen.java)
- python: already canonical
- ruby: Restrict the var-escape predicate as in the canonical text, conditional on Steep/RBS passing; else record FORCED in AGENTS.md. (generators/ruby/BetterRubyCodegen.java; models/model.mustache RBS)
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: Verify `and`/`or`/`not` as struct keys and field access under --warnings-as-errors; drop the escape if they compile, else document as FORCED. (generators/elixir/BetterElixirCodegen.java; reserved-words/elixir.txt)

**Tests.** EdgeCases already carries and/or/not/class/return (petstore openapi.yaml ~1877): the goldens (php Models/EdgeCases.php, ruby edge_cases.rb/.rbs, elixir edge_cases.ex, java EdgeCases.java) and their compile/lint (phpcs, Steep, mix --warnings-as-errors, javac) are the lock; add the null/true/false properties below so javac verifies F364. The existing ObjectSerializerTest in each language should round-trip the EdgeCases wire keys `and`, `class`, `null`, `true`. Keep AbstractReservedWordsSpec/JavaReservedWordsSpec (src/spec/java/io/github/mridang/codegen/spec/<lang>/) as the superset guard; with JavaKeywords.java no longer excluding literals it asserts the new java.txt entries.

**Petstore spec addition.**

```yaml
# petstore openapi.yaml, components.schemas.EdgeCases.properties: add (keys must be quoted: YAML 1.1 reads bare null/true/false as non-strings)
        'null':
          type: string
        'true':
          type: boolean
        'false':
          type: boolean
```

**Risk.** php and (if verified) ruby/elixir rename existing public fields/accessors (`$and_` -> `$and`): breaking for anyone already using the generated php names. Ruby/elixir behaviour is not verified by reading; the decision is explicitly conditional on the golden's Steep/mix compile.

## D45 Model names that collide with language or SDK types get the Model prefix in all 12, from a dedicated reserved-model-name set

- Ledger ids: F173 | Theme: codegen-escaping-and-names | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Still true: AbstractBetterCodegen.toModelName (~2216) applies getModelNameCollisionPrefix() only when it is non-empty and the PascalCase name is in languageSpecificPrimitives; only BetterNodeCodegen (~585) returns "Model". Per language today a schema named `Error`: swift shadows Swift.Error module-wide while the SDK uses it unqualified (swift DefaultApiClient.swift:723 `private struct UndecodableBody: Error`, SerializationError.swift:19 `cause: Error?`), so the SDK stops compiling; java/kotlin (`String`, `Object`, `Error`, `Exception` in the models package) shadow java.lang/kotlin types inside the package; php rejects `String`/`Null`/`True` etc. as class names; rust model files `use super::*` (rust src/models/pet_food.rs:8) so `Result`/`String`/`Option`/`Vec` shadow the prelude; dart `Error`/`List`/`Map`/`Type`; csharp `Task`/`Type`/`Enum`/`Exception`; python `Optional`/`List`/`Any` shadow typing imports; ruby `Types` (used by every model, `Types::Any`) / `Hash` / `Array`; go and elixir namespace models in their own package/module so only identifiers declared in the same package matter. java's languageSpecificPrimitives is only int/long/.../String/Object/byte[]/void (BetterJavaCodegen ~120), swift's is String/Bool/Int/Data/Date/UUID/Void/Any (~100), and these sets are also used for import decisions (BetterJavaCodegen ~1050-1067 etc.), so they cannot simply be extended.

**Canonical behaviour.** Introduce a separate per-language `getReservedModelNames()` (prelude and builtin type names the generated source references unqualified, plus the SDK's own top-level type names in the same namespace) and make `getModelNameCollisionPrefix()` return "Model" in all 12 (node's existing convention, matching upstream openapi-generator's ModelError). toModelName checks the new set, not languageSpecificPrimitives, so import logic is untouched. Seed sets: swift {Error, Result, Data, Date, URL, UUID, String, Int, Bool, Double, Float, Any, AnyObject, Void, Never, Optional, Array, Dictionary, Set, Codable, Encodable, Decodable, Hashable, Equatable, Sendable, Type}; java {Object, String, Integer, Long, Double, Float, Boolean, Byte, Short, Character, Number, Void, Class, Record, Enum, Error, Exception, Throwable, List, Map, Set, Optional, Objects, UUID}; kotlin {Any, String, Int, Long, Double, Float, Boolean, Byte, Short, Char, Unit, Nothing, Array, List, Map, Set, Pair, Result}; rust {Result, Option, Vec, String, Box, Some, None, Ok, Err, Self}; php {String, Int, Float, Bool, Array, Object, Null, True, False, Mixed, Void, Iterable, Callable, Never, Self, Static, Parent}; dart {String, int, double, num, bool, List, Map, Set, Object, Null, Future, Stream, Type, Function, Duration, DateTime, Uri, Error, Exception, Iterable}; csharp {Object, String, Task, Type, Enum, Exception, Attribute, Action, Func, List, Dictionary}; python {Optional, List, Dict, Any, Field, BaseModel, Union}; ruby {Types, Hash, Array, String, Integer, Float, Object, Class, Module, Struct, Time, Date, Set}; go/elixir: only the SDK's own names declared in the same package/module. Extend each set from the SDK's actual unqualified references (grep the golden).

**Why.** Criteria (b)/(c): node already renames colliding models with `Model`, the other 11 either fail to compile (swift `Error`, rust `use super::*` with `Result`) or silently shadow a type the SDK uses. `Error`, `Date`, `Result` are very common schema names. A separate set is needed because languageSpecificPrimitives also drives import and primitive-vs-model decisions.

**Rejected.** (1) Fully qualify every `Error` reference inside the SDK (Swift.Error): rejected as the only fix, it covers swift only and leaves java/rust/php/python/ruby shadowing. (2) Extend languageSpecificPrimitives: rejected, those sets drive imports and primitive-vs-model decisions, so a renamed entry would suppress imports of real models. (3) A different prefix per language: rejected, node already ships `Model` and one prefix keeps names predictable. (4) Fail generation: rejected, `Error`, `Date`, `Result` are legitimate schema names.

**Per language.**

- java: Override getModelNameCollisionPrefix -> "Model" and getReservedModelNames with the java set. (generators/java/BetterJavaCodegen.java; AbstractBetterCodegen.toModelName)
- kotlin: Same with the kotlin set. (generators/kotlin/BetterKotlinCodegen.java)
- csharp: Same with the csharp set. (generators/csharp/BetterCSharpCodegen.java)
- go: Prefix enabled; set limited to names declared in the models package by the SDK (likely empty). (generators/go/BetterGoCodegen.java)
- rust: Rust prelude set; `use super::*` in models/*.rs makes this mandatory. (generators/rust/BetterRustCodegen.java)
- swift: Swift set; required to keep DefaultApiClient.swift:723 and SerializationError.swift:19 compiling when a schema is named Error. (generators/swift/BetterSwiftCodegen.java)
- elixir: Prefix enabled; set limited to SDK module names in the same namespace. (generators/elixir/BetterElixirCodegen.java)
- dart: Dart set. (generators/dart/BetterDartCodegen.java)
- php: PHP reserved class-name set (also covers the getModelNameSanitizationRules path). (generators/php/BetterPHPCodegen.java)
- ruby: Ruby set incl. `Types`. (generators/ruby/BetterRubyCodegen.java)
- node: Already prefixes via languageSpecificPrimitives; move it onto getReservedModelNames and add Error/Result/Date so node behaves like the others. (generators/node/BetterNodeCodegen.java (~585))
- python: Python set (typing/pydantic names the models import). (generators/python/BetterPythonCodegen.java)

**Tests.** The golden for the new EdgeCases properties must compile in all 12 (swift is the one that fails today); additionally the existing ObjectSerializerTest in each language round-trips EdgeCases.lastError and EdgeCases.outcome with the renamed types: java/kotlin ObjectSerializerTest, csharp Test/ObjectSerializerTest.cs, php tests/ObjectSerializerTest.php, python test/test_object_serializer.py, ruby test/object_serializer_test.rb, node test/object-serializer.test.ts, dart test/object_serializer_test.dart, go pkg/object_serializer_test.go, rust tests/composed_schema_test.rs, swift ObjectSerializerTests.swift, elixir test/object_serializer_test.exs. The expected type names are ModelError/ModelResult in all 12 (file names follow each language's casing, e.g. model_error.py, ModelError.swift).

**Petstore spec addition.**

```yaml
# petstore openapi.yaml
# components.schemas.EdgeCases.properties: add
        lastError:
          $ref: '#/components/schemas/Error'
        outcome:
          $ref: '#/components/schemas/Result'
# components.schemas: add
    Error:
      type: object
      properties:
        code:
          type: integer
          format: int32
        message:
          type: string
    Result:
      type: object
      properties:
        ok:
          type: boolean
```

**Risk.** A schema currently named e.g. `Result` in rust/swift becomes `ModelResult` (public rename), but those SDKs either did not compile or silently shadowed the prelude. No current petstore schema is in any seed set, so goldens should not change except for the new fixture. The sets need maintenance when templates start using a new unqualified type.

## D46 String enum literals: escape the quote char (never strip it), escape backslash and interpolation sigils, in all 12

- Ledger ids: F044 F047 | Theme: query-form-path-encoding | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Root: AbstractBetterCodegen.toEnumValue (AbstractBetterCodegen.java:2362) = quoteEnumValue(escapeEnumStringValue(v)); quoteEnumValue (:3894) deletes the quote char when shouldEscapeQuotationMark() is false. Nothing escapes a backslash, `$` or `#{`. Petstore enums only contain [a-z-] plus 'Available/Sold/on-hold', so nothing tests it.
java: "..." quote escaped as \", backslash NOT escaped (`a\d` fails to compile, `\u` hazards), value also lands in `{@code ...}` javadoc (model.mustache:23).
kotlin: same, plus `$eq` interpolates (model.mustache:97 @SerialName($eq) fails to compile).
csharp: own table getEnumStringEscapes (BetterCSharpCodegen.java:622): \n \t \r and a lookbehind rule for `"`; backslash not escaped.
python: single-quoted, shouldEscapeQuotationMark=false (BetterPythonCodegen.java:737) so `o'clock` becomes 'oclock' (wire value silently changed); backslash unescaped (`a\b` = backspace).
ruby: same strip (BetterRubyCodegen.java:817).
php: same strip (BetterPHPCodegen.java:688).
dart: same strip (BetterDartCodegen.java:298); `$eq` interpolates (dart/models/model.mustache:29).
node: single-quoted, DOES escape `'` as \' (default true) but not backslash.
go: double-quoted, `"` escaped, backslash not.
rust: same as go; value is in #[serde(rename = ...)] (rust/models/model.mustache:31).
swift: same as go; `\(` in a value would start an interpolation.
elixir: same as go; `#{x}` interpolates (elixir/models/model.mustache:33,49).

**Canonical behaviour.** The enum literal emitted into source must evaluate, in the target language, to EXACTLY the spec string. Implemented once in AbstractBetterCodegen.escapeEnumStringValue (default body, run before quoteEnumValue), in this order: (1) `\` -> `\\`; (2) the language quote char -> `\<q>` (delete every shouldEscapeQuotationMark()=false override so quoteEnumValue escapes instead of strips); (3) CR/LF/TAB -> `\r` `\n` `\t` in every language EXCEPT ruby and php, whose single-quoted literals interpret only `\\` and `\'` (a raw newline is legal there and `\n` would be a literal backslash-n); (4) interpolation neutralisation via getEnumStringEscapes: kotlin `$`->`\$`, dart `$`->`\$`, elixir `#{`->`\#{`. swift `\(` is covered by (1). Nothing else is rewritten. Where the raw value is echoed in a doc comment (java `{@code ...}` model.mustache:23/67/110, go `/* ... */` model.mustache:24, dart/rust `///`), render a comment-safe variant (apply escapeUnsafeCharacters so `*/` cannot close the comment) rather than the code literal.

**Why.** Criterion (a) is each language's own lexical rules plus the AGENTS governing rule that the wire value is identical in all 12; stripping a quote silently changes the value the client sends and then fails to match the same value in responses (criterion c). (b) 8 of 12 already escape the quote char (java, kotlin, csharp, go, rust, swift, elixir via default; node via default) so the 4 strippers are the minority. (d) Every language has a valid escape (\' in python/dart/php/ruby/node, \$ in kotlin/dart, \#{ in elixir), so nothing is forced. Note csharp's `(?<!\\)"` lookbehind must become an unconditional `"` -> `\"` rule applied AFTER backslash doubling, otherwise `a\"` is mis-escaped.

**Rejected.** Rejected: keep stripping and document it (silent wire change, 4 languages diverge from the other 8). Rejected: reject hostile enum values at generation time (a spec with `$eq` / `o'clock` is legal and realistic, e.g. Mongo operators). Rejected: per-language ad-hoc fixes with no shared ordering (the csharp lookbehind shows the interaction bugs).

**Per language.**

- java: Gains backslash escape from the shared default; quote already escaped. Make javadoc `{@code {{{value}}}}` sinks use a comment-safe value. (src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java (escapeEnumStringValue default); src/main/resources/templates/java/models/model.mustache:23,67,110,531,575,618,682)
- kotlin: Add getEnumStringEscapes rule `$` -> `\$` (after backslash doubling); backslash from shared default. (src/main/java/io/github/mridang/codegen/generators/kotlin/BetterKotlinCodegen.java; templates/kotlin/models/model.mustache:30,64,97,98,687,719,750,751)
- csharp: Prepend backslash rule; replace the lookbehind quote rule with unconditional `"` -> `\"`; keep \n \t \r. toEnumValue override stays (template supplies the quotes). (src/main/java/io/github/mridang/codegen/generators/csharp/BetterCSharpCodegen.java:622-641)
- python: Delete shouldEscapeQuotationMark()=false override (BetterPythonCodegen.java:737) so `'` becomes `\'`; backslash and control chars from the shared default. (src/main/java/io/github/mridang/codegen/generators/python/BetterPythonCodegen.java:731-739; templates/python/models/model.mustache:19,333)
- ruby: Delete the override (BetterRubyCodegen.java:817) so `'` -> `\'`; override getEnumStringEscapes to ONLY `\\` and `\'` (skip rule 3: control chars stay raw). (src/main/java/io/github/mridang/codegen/generators/ruby/BetterRubyCodegen.java:811-819; templates/ruby/models/model.mustache:20,456)
- node: Quote already escaped; gains backslash escape from the shared default. (AbstractBetterCodegen.java (shared default); templates/node/models/model.mustache)
- php: Delete the override (BetterPHPCodegen.java:688); only `\\` and `\'` are escapes in single-quoted PHP (skip rule 3). (src/main/java/io/github/mridang/codegen/generators/php/BetterPHPCodegen.java:682-690; templates/php/models/model.mustache:24, php/models/inline_enum.mustache:11)
- dart: Delete the override (BetterDartCodegen.java:298) so `'` -> `\'`; add rule `$` -> `\$`; make the `/// Represents the value {{{value}}}` doc lines comment-safe. (src/main/java/io/github/mridang/codegen/generators/dart/BetterDartCodegen.java:292-300; templates/dart/models/model.mustache:28-29,62-63,95-96,144-145,174-175,203-204)
- go: Gains backslash escape and \n \t \r from shared default (raw newlines are illegal in a Go interpreted string); comment sink `/* ... {{{value}}}. */` (model.mustache:24) must be comment-safe. (AbstractBetterCodegen.java (shared default); templates/go/models/model.mustache:24-25,106,169)
- rust: Gains backslash escape from shared default; value sits inside #[serde(rename = ...)] so the same escapes apply. (AbstractBetterCodegen.java (shared default); templates/rust/models/model.mustache:29-31,73-75)
- swift: Gains backslash escape from shared default (also neutralises `\(`). (AbstractBetterCodegen.java (shared default); templates/swift/models/*)
- elixir: Add rule `#{` -> `\#{` (after backslash doubling); backslash from shared default. (src/main/java/io/github/mridang/codegen/generators/elixir/BetterElixirCodegen.java; templates/elixir/models/model.mustache:30,33,49)

**Tests.** Unit (mirror the source file): new src/test/java/io/github/mridang/codegen/generators/AbstractBetterCodegenTest.java, a table over all 12 codegens calling toEnumValue for o'clock, a\b, $eq, #{x}, say "hi", and a value with a newline, asserting the exact literal per language (ruby/php: `'o\'clock'`, `'a\\b'`, raw newline; kotlin/dart: `"$eq"`->`\$`). SDK-level lock via the existing Availability fixture (YAML below): the per-language ObjectSerializer tests already round-trip Availability (python/test/test_object_serializer.py:1032-1048, java ObjectSerializerTest.java, kotlin ObjectSerializerTest.kt, csharp Test/ObjectSerializerTest.cs, node test/object-serializer.test.ts, ruby test/object_serializer_test.rb, php tests/ObjectSerializerTest.php, dart test/object_serializer_test.dart, go pkg/object_serializer_test.go, rust tests/composed_schema_test.rs, swift ObjectSerializerTests.swift, elixir test/object_serializer_test.exs): add a row per new value asserting serialize(member) == the JSON-quoted spec string and deserialize round-trips. These tests are authored as templates/<lang>/test/*.mustache and surface in the goldens.

**Petstore spec addition.**

```yaml
    Availability:
      type: string
      description: Stock availability, non-lowercase string enum
      enum:
        - Available
        - Sold
        - on-hold
        - "o'clock"
        - 'a\b'
        - '$eq'
        - '#{x}'
```

**Risk.** Low. Only changes output for enum values containing quote, backslash, `$`, `#{` or control characters; petstore values are unaffected. escapeQuotationMark() (the doc/default-value sibling, AbstractBetterCodegen.java:3911) also strips in the same four languages; flip it with the same switch but check default-value rendering first, it is a separate sink. Python/ruby/php/dart formatters (ruff, rubocop, php-cs-fixer, dart format) may re-quote the literal; the escape must survive that, so assert on generated output after formatting.

## D47 Object-valued (model or free-form map) query/header parameters: convert to a wire-keyed map via the SDK ObjectSerializer, then expand per the OAS style/explode rows, in all 12

- Ledger ids: F063 | Theme: query-form-path-encoding | Still true: **yes** | Effort: L | Wire-visible: True | Needs user: False

**Current state.** deriveQuerySerializationKind (AbstractBetterCodegen.java:3719-3725) sends every non-deepObject, non-content query param to the 'styled' path and no template has a model/object branch (grep isModel in api templates: none for params). deepObject only works for a free-form map.
java: ValueSerializer.serializeDeepObject(String, Map<?,?>) (ValueSerializer.java:317); a $ref model fails to COMPILE; form object -> String.valueOf -> `{a=1}`.
kotlin: serializeDeepObject(Map<*,*>) (ValueSerializer.kt:295), compile error for a model; form -> toString.
csharp: SerializeDeepObject(IDictionary<string,object?>) (ValueSerializer.cs:299), compile error; form -> type name.
python: serialize_deep_object(Dict) (value_serializer.py:259) calls .items() -> AttributeError on a pydantic model; form -> str(dict).
ruby: each_with_object on a model -> NoMethodError (value_serializer.rb:224); form -> Hash#to_s.
node: Object.entries on a class instance (value-serializer.ts:306) sends TS property names not wire names; form -> `[object Object]`.
php: serializeDeepObject(?array) (ValueSerializer.php:329) -> TypeError on an object; form -> json_encode text.
dart: `as Map<String, Object?>?` cast (dart/api/api.mustache:291) throws at runtime; form -> `{a: 1}`.
go: type switch matches only map[string]any (value_serializer.go:174), anything else returns an EMPTY map, param silently dropped; form -> `map[a:1]`.
rust: serialize_deep_object(&HashMap<String,String>) (value_serializer.rs:143) compile error for a model, and iterates a HashMap (random order); form -> serde to_string.
swift: `as? [String: Any]` (swift/api/api.mustache:226) fails and the param is silently skipped.
elixir: Map.new over the struct (value_serializer.ex:434) emits `filter[__struct__]=Elixir...`; form raises.

**Canonical behaviour.** An object-valued parameter is first converted to an ORDERED generic map keyed by WIRE names by the SDK's own ObjectSerializer (new ObjectSerializer.toWireMap(value): serialize with the canonical serializer, then parse generically; a free-form map passes straight through; null members dropped; numbers kept as text where the parser allows it). Templates call it at the call site, so ValueSerializer signatures keep taking a map. Then expand with the OAS rows, scalar leaves via the canonical stringify: query form+explode(default) `k1=v1&k2=v2` (keys NOT prefixed by the param name); query form+explode=false `name=k1,v1,k2,v2` (one param, same comma-then-percent-encode path the array form/no-explode branch already uses); deepObject `name[k]=v` (extend serializeDeepObject to accept the converted map); header/path simple `k1,v1,k2,v2` and simple+explode `k1=v1,k2=v2`. A NESTED object or array leaf has no OAS serialization and raises the SDK SerializationError (never stringified, never dropped). spaceDelimited/pipeDelimited on an object also raise SerializationError. Member order = model declaration order (free-form maps: insertion order, sorted by key in go/rust/swift/elixir whose maps are unordered).

**Why.** (a) OAS 3.x defines these rows explicitly. (c) Today three of the failure modes are compile errors (java, kotlin, csharp, rust) or silent drops (go, swift), and node/elixir send wrong keys; a compile error breaks the whole SDK for any spec with a model-typed deepObject, which is common in real specs. (d) Every language has a JSON parse-to-generic in its stdlib (Jackson convertValue, kotlinx JsonObject, System.Text.Json JsonElement, model_dump(by_alias), JSON.parse, json_decode, jsonDecode, json.Unmarshal into map with UseNumber, serde_json::Value, JSONSerialization, Jason.decode), so routing through the existing serializer is dependency-free and automatically inherits wire names, date/enum canonical forms and the depth cap.

**Rejected.** Rejected: reject object params at generation/runtime with a typed error everywhere (safe but leaves a valid OAS feature unusable and still needs per-language work; acceptable only as a stop-gap for phase 1, see risk). Rejected: per-language reflection over fields (node/go/swift would then disagree on wire names and omit-null rules). Rejected: JSON-encode the object into one param (that is what `content: application/json` is for, F068).

**Per language.**

- java: Add ObjectSerializer.toWireMap (readValue of serialize() into LinkedHashMap); widen serializeDeepObject to Map<?,?> from toWireMap; add form-explode/no-explode and simple object expanders in serializeStyled; new 'object' kind in deriveQuerySerializationKind routes model/map params there. (templates/java/object_serializer.mustache, templates/java/value_serializer.mustache, templates/java/api/api.mustache (queryDeepObject ~:325, queryStyled ~:342, header block), AbstractBetterCodegen.java:3719)
- kotlin: toWireMap via Json.encodeToJsonElement -> JsonObject -> LinkedHashMap; same expanders. (templates/kotlin/object_serializer.mustache, value_serializer.mustache, api/api.mustache (queryDeepObject ~:263))
- csharp: ToWireMap via JsonSerializer.SerializeToElement with the SDK options -> ordered Dictionary<string,object?>; widen SerializeDeepObject; same expanders. (templates/csharp/object_serializer.mustache, value_serializer.mustache, api/api.mustache:379-390)
- python: to_wire_map via json.loads(self.serialize(model)) (or model_dump(by_alias=True, mode='json')); accept pydantic models in serialize_deep_object; same expanders. (templates/python/object_serializer.mustache, value_serializer.mustache, api/api.mustache:255)
- ruby: to_wire_map via JSON.parse(ObjectSerializer.serialize(v)); same expanders. (templates/ruby/object_serializer.mustache, value_serializer.mustache, api/api.mustache)
- node: toWireMap via JSON.parse(ObjectSerializer.serialize(v)) so @Expose wire names are used; same expanders. (templates/node/object_serializer.mustache, value_serializer.mustache, api/apis.mustache)
- php: toWireMap via json_decode(ObjectSerializer::serialize($v), true); widen serializeDeepObject beyond ?array. (templates/php/object_serializer.mustache, value_serializer.mustache, api/api.mustache:249-253,545-549)
- dart: toWireMap via jsonDecode(serialize(v)); remove the `as Map<String,Object?>?` cast. (templates/dart/object_serializer.mustache, value_serializer.mustache, api/api.mustache:291,305,321)
- go: toWireMap via serialize() then json.Unmarshal into an ordered pair list (UseNumber); stop returning an empty map for unknown types (return the SerializationError). (templates/go/object_serializer.mustache, value_serializer.mustache (~:174), api/api.mustache (deepObject block ~:250-267))
- rust: to_wire_map via serde_json::to_value (preserve_order feature or Vec<(String,String)>); change serialize_deep_object to accept it and emit declaration order; return SerializationError for nested. (templates/rust/object_serializer.mustache, value_serializer.mustache:143, api/api.mustache (deep object block ~:400))
- swift: toWireMap via JSONSerialization over ObjectSerializer.serialize; replace the `as? [String: Any]` skip with a thrown SerializationError; ordered result. (templates/swift/object_serializer.mustache, value_serializer.mustache, api/api.mustache:226,288-293,319)
- elixir: to_wire_map via ObjectSerializer.serialize |> Jason.decode!; never Map.new over a struct; ordered list of {k,v}. (templates/elixir/object_serializer.mustache, value_serializer.mustache (~:434), api/api.mustache:330-338)

**Tests.** The current petstore spec cannot exercise it (its only deepObject is additionalProperties:string). Add the YAML below to getPetTag. Lock in each language's PetApi test (java api/PetApiTest.java near :286, kotlin api/PetApiTest.kt :338/:798, csharp Test/Api/PetApiTest.cs, python test/api/test_pet_api.py :417, ruby test/api/pet_api_test.rb, node test/Api/pet-api.test.ts, php tests/Api/PetApiTest.php, dart test/pet_api_test.dart, go test/pet_api_test.go, rust tests/pet_api_test.rs, swift PetApiTests.swift, elixir test/pet_api_test.exs) with PetCriteria{breed:'lab', min_age:3}: the captured query must contain, in this order, `breed=lab&min_age=3` (criteria), `criteriaCsv=breed%2Clab%2Cmin_age%2C3`, `criteriaDeep%5Bbreed%5D=lab&criteriaDeep%5Bmin_age%5D=3`, and the header `X-Criteria: breed,lab,min_age,3`. `min_age` is deliberately snake_case so wire names (not property names) are asserted, and breed<min_age keeps declaration == alphabetical order so swift's sortedKeys cannot mask an ordering bug. Add unit rows to each value serializer test for nested-object leaf -> SerializationError. Also assert the existing filter[color]=blue free-form map output is unchanged.

**Petstore spec addition.**

```yaml
# 1) append to /pet/{petId}/tag/{tagName} get.parameters, after `revision`:
        - name: criteria
          in: query
          description: Object-valued query parameter, form style exploded (the OAS default for query)
          schema:
            $ref: '#/components/schemas/PetCriteria'
        - name: criteriaCsv
          in: query
          style: form
          explode: false
          schema:
            $ref: '#/components/schemas/PetCriteria'
        - name: criteriaDeep
          in: query
          style: deepObject
          explode: true
          schema:
            $ref: '#/components/schemas/PetCriteria'
        - name: X-Criteria
          in: header
          style: simple
          explode: false
          schema:
            $ref: '#/components/schemas/PetCriteria'
# 2) add under components.schemas:
    PetCriteria:
      type: object
      properties:
        breed:
          type: string
        min_age:
          type: integer
          format: int32
```

**Risk.** Largest item in this theme (effort L). Suggested sequencing: phase 1, make every language fail fast with SerializationError for a non-map object so java/kotlin/csharp/rust at least compile and go/swift stop dropping silently; phase 2, implement the rows. Existing free-form-map deepObject output must not change (petstore filter[color]=blue). Header-object rows touch the header builders as well.

## D48 Reject a path parameter value of exactly '.' or '..' before dispatch, in all 12

- Ledger ids: F064 | Theme: query-form-path-encoding | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** No SDK rejects or encodes dot segments (grep of the 12 goldens finds none; only php AbstractApiClient.php:555 mentions dot segments, for redirect resolution). All 12 encodePathSegment keep `.` literal (java ValueSerializer.java:117, python value_serializer.py:102, node value-serializer.ts:210, go value_serializer.go:79, rust value_serializer.rs:26, kotlin ValueSerializer.kt:100, csharp ValueSerializer.cs:89, php ValueSerializer.php:133, swift ValueSerializer.swift:62, ruby value_serializer.rb:87, elixir value_serializer.ex:106, dart value_serializer.dart). getPetByName("..") builds /pet/byName/..?category=x. What happens next is transport dependent. Verified locally: node WHATWG URL -> http://h/pet/?x=1 (even %2e%2E is normalised), python urllib3 2.6.3 parse_url -> /pet/?x=1, swift URL(string:) keeps `..` literally (only .standardized collapses it). From the finding, not run here: dart Uri.parse collapses it (reroutes), rust url crate / csharp System.Uri / php Symfony resolve most likely collapse it; java.net.URI and ruby send it literally; go net/http and elixir most likely send it literally.
So java/swift/ruby/go/elixir send /pet/byName/.. while node/python/dart(/rust/csharp/php) silently hit /pet/.

**Canonical behaviour.** A path parameter whose final simple-style value is exactly `.` or `..` is rejected client-side, before any request, with the SDK's existing argument error, message `Path parameter '<name>' must not be '.' or '..'` (go lowercases `path`). It sits next to, and is raised from the same place as, the existing empty-path rejection: the serializer's path check in the 10 languages that have one; the operation-layer path guard in rust and swift (whose serializers have no path guard, AGENTS R6-5/R8-6). Percent-encoded forms need no rule: a literal `%2E%2E` input is encoded to `%252E%252E`. Matrix/label styles prefix their value (`;n=..`, `...`) so they can never be a dot segment. Substrings such as `a..b` or `.hidden` are untouched.

**Why.** (c) A user-controlled name can silently re-route the request to a different endpoint (/pet instead of /pet/byName/..) in at least 3 SDKs; encoding cannot fix it because WHATWG parsers (node, rust) resolve even %2E%2E, so only rejection is uniform. (a) RFC 3986 section 5.2.4 treats `.`/`..` segments as path navigation, so they are not addressable resource identifiers. (d) trivial in every language (same shape as the existing empty check). Not forced anywhere.

**Rejected.** Rejected: always encode `.` as %2E (node and rust still normalise it; 10 languages change bytes for nothing). Rejected: fix only the three transports that normalise (leaves java/swift/ruby/go/elixir sending a dot-segment that a proxy or server will resolve).

**Per language.**

- java: Extend the path check in serializeStyled (value instanceof String s && (".".equals(s)||"..".equals(s))) -> IllegalArgumentException. (templates/java/value_serializer.mustache (golden ValueSerializer.java:235))
- kotlin: Same, IllegalArgumentException/require. (templates/kotlin/value_serializer.mustache (golden ValueSerializer.kt:228))
- csharp: Same, ArgumentException. (templates/csharp/value_serializer.mustache (golden ValueSerializer.cs:211))
- python: Same, ValueError. (templates/python/value_serializer.mustache (golden value_serializer.py:178))
- ruby: Same, ArgumentError. (templates/ruby/value_serializer.mustache (golden value_serializer.rb:144))
- node: Same, TypeError. (templates/node/value_serializer.mustache (golden value-serializer.ts:92))
- php: Same, the exception type already used at ValueSerializer.php:209. (templates/php/value_serializer.mustache (golden ValueSerializer.php:209))
- dart: Same, ArgumentError. (templates/dart/value_serializer.mustache (golden value_serializer.dart:290))
- go: Extend requirePathParam (value_serializer.go:212-221): string-kinded value == "." or ".." returns a new sentinel ErrInvalidPathParameter (not ErrMissingRequiredParameter), lowercase message. (templates/go/value_serializer.mustache)
- rust: Extend the operation-layer guard (`if name.is_empty()` at pet_api.rs:1557) with `|| name == "." || name == ".."` -> ConfigurationError::InvalidArgument with the canonical message. (templates/rust/api/api.mustache)
- swift: Extend requirePathParam (ValueSerializer.swift:101) to throw ConfigurationError.invalidArgument for "."/"..". (templates/swift/value_serializer.mustache)
- elixir: Same, ArgumentError. (templates/elixir/value_serializer.mustache (golden value_serializer.ex:190))

**Tests.** No new YAML: getPetByName (/pet/byName/{name}, required query category) already exists. In each PetApi test file (java api/PetApiTest.java, kotlin api/PetApiTest.kt, csharp Test/Api/PetApiTest.cs, python test/api/test_pet_api.py, ruby test/api/pet_api_test.rb, node test/Api/pet-api.test.ts, php tests/Api/PetApiTest.php, dart test/pet_api_test.dart, go test/pet_api_test.go, rust tests/pet_api_test.rs, swift PetApiTests.swift, elixir test/pet_api_test.exs) add getPetByName('.') and ('..') -> the canonical error AND the mock/capturing transport saw zero requests; one control row getPetByName('a..b') succeeds and sends /pet/byName/a..b. Add the same rows as unit tests in each value serializer test file (serializeStyled path, simple style).

**Risk.** Low. A resource whose id is literally `.` or `..` could never be reached through a URL path anyway. Over-strictness only if a template is `/x/{a}.{b}` with a='.'; the check is on the standalone value so this is not hit.

## D49 allowReserved: leave literal only the RFC 3986 query-legal reserved set; percent-encode '#', '[' and ']' in all 12; document the WHATWG/URI-class '''' rewrite as FORCED in node, rust, ruby

- Ledger ids: F066 F387 | Theme: query-form-path-encoding | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** All 12 encode_query_allowing_reserved keep `: / ? # [ ] @ ! $ & ' ( ) * + , ; =` literal (e.g. node value-serializer.ts:252-270, java ValueSerializer.java:177-199, python value_serializer.py:142 safe="/?#[]@!$&'()*+,;=:"). The transport then rewrites URLs. Wire result of a value with `[ ] ' #`:
java: java.net.URI keeps all literal, a literal `#` becomes the URI fragment and is NOT sent (query truncated).
kotlin: Ktor keeps literal; `#` truncates (same).
csharp: System.Uri keeps `[ ] '`; `#` truncates.
python: urllib3 2.6.3 parse_url (default_api_client.py:446) re-encodes `[`/`]` to %5B/%5D (verified locally), keeps `'`; `#` -> fragment.
ruby: after the QueryParamsEncoder fix (default_api_client.rb:67-81) reserved chars survive Faraday's Hash round trip, but Faraday Request#url slices at `#` and URI::Generic#query= escapes `'` to %27 and `#` to %23 (verified on ruby 2.6.10); `[ ]` stay literal.
node: WHATWG URL (fetch) rewrites `'` to %27 (verified locally), keeps `[ ]`; `#` -> hash, truncates.
php: nyholm/psr7 filterQueryAndFragment (composer.json:33) encodes `[ ]` (likely, not run), keeps `'`.
dart: Uri.parse (abstract_api_client.dart:167) encodes `[ ]` to %5B/%5D (finding; not run here).
go: net/url keeps RawQuery as given.
rust: reqwest::Url::parse (default_api_client.rs:226) WHATWG special-query set rewrites `'` to %27.
swift: URL(string:) (DefaultApiClient.swift:207) encodes `[ ]` to %5B/%5D (verified locally on macOS).
elixir: keeps literal.
Net: `[ ]` literal in java/kotlin/csharp/go/elixir/node/ruby but %5B%5D in python/dart/swift/php; `'` literal in 9 and %27 in node/rust/ruby; a literal `#` silently truncates the query in every SDK. petstore only tests v1.0/beta:rc1, and only at encoder level.

**Canonical behaviour.** allowReserved leaves literal exactly the characters RFC 3986 section 3.4 permits in a query component: `: / ? @ ! $ & ' ( ) * + , ; =` (plus unreserved `A-Za-z0-9-._~`). It always percent-encodes `#` (%23), `[` (%5B) and `]` (%5D), and everything else (space -> %20, controls, non-ASCII) as before. The encoder output is the contract: it is what the SDK hands the transport. FORCED exception, documented in AGENTS.md: node (WHATWG URL), rust (url crate, WHATWG) and ruby (URI::Generic#query=) turn a literal `'` into %27 after the SDK encodes; the other 9 send `'` literally. A server decodes both identically; do not try to defeat the URL parsers.

**Why.** (c) A literal `#` can never be on the wire inside a query: every URL parser treats it as the fragment delimiter, so allowReserved values containing `#` silently lose the rest of the query today in all 12; that is silent data loss. (a) RFC 3986 section 3.4 does not allow `[ ]` in a query; OAS lists them but they cannot be honoured in dart (Uri) and swift (URL) without abandoning the platform URL types, and python/php already normalise them, so encoding them is the only value reachable in all 12 without a new dependency (criterion d). Making the encoder match what 4 transports already produce moves 7 languages to the same bytes and changes nothing for the 4. For `'` the criteria say document, not force (instruction d): 9 of 12 keep it literal and the OAS text allows it.

**Rejected.** Rejected: keep `[ ] #` literal and fix the transports (python needs a raw urllib3 connection-pool call, dart/swift cannot hold a non-RFC query in Uri/URL at all, and `#` is unfixable anywhere). Rejected: also encode `'` everywhere for byte equality (contradicts the OAS reserved list for the 9 that can honour it; only buys byte equality that servers do not observe). Rejected: leave as is and only add tests (pins a known truncation bug).

**Per language.**

- java: Drop the .replace("%23","#"), ("%5B","["), ("%5D","]") restorations in encodeQueryAllowingReserved. (templates/java/value_serializer.mustache (golden ValueSerializer.java:186-189))
- kotlin: Same restoration set minus # [ ]. (templates/kotlin/value_serializer.mustache)
- csharp: Same. (templates/csharp/value_serializer.mustache (EncodeQueryAllowingReserved))
- python: safe="/?@!$&'()*+,;=:" (drop #[]). (templates/python/value_serializer.mustache (golden value_serializer.py:142))
- ruby: Same restoration set minus # [ ]; leave QueryParamsEncoder as is. (templates/ruby/value_serializer.mustache)
- node: Remove the %23, %5B, %5D restorations (value-serializer.ts:256-259). `'` rewrite by fetch is the documented FORCED exception. (templates/node/value_serializer.mustache)
- php: Same minus # [ ]; PSR-7 already encodes them. (templates/php/value_serializer.mustache)
- dart: Same minus # [ ]; Uri.parse then no longer rewrites anything. (templates/dart/value_serializer.mustache (encodeQueryAllowingReserved))
- go: Same minus # [ ]. (templates/go/value_serializer.mustache (encodeQueryAllowingReserved))
- rust: Same minus # [ ]; `'` -> %27 by the url crate is the documented FORCED exception. (templates/rust/value_serializer.mustache)
- swift: Remove `#[]` from queryComponentAllowedReserved (BaseApi.swift:318-323 string ":/?#[]@!$&'()*+,;="). (templates/swift/base_api.mustache)
- elixir: Same minus # [ ]. (templates/elixir/value_serializer.mustache)

**Tests.** No new YAML: getPetTag.revision (allowReserved) already exists. (1) Encoder unit test in every value serializer test file (java api/ValueSerializerTest.java:607, kotlin ValueSerializerTest.kt:702, csharp ValueSerializerTest.cs:896, python test_value_serializer.py:405, ruby value_serializer_test.rb:477, node value-serializer.test.ts:843, php ValueSerializerTest.php:511, dart value_serializer_test.dart:826, go pkg/value_serializer_test.go:1004, rust value_serializer_test.rs, swift ValueSerializerTests.swift, elixir value_serializer_test.exs:823): encode_query_allowing_reserved("a:b/c?d[e]f@g!h$i&j'k(l)m*n+o,p;q=r~s#t u") == "a:b/c?d%5Be%5Df@g!h$i&j'k(l)m*n+o,p;q=r~s%23t%20u". (2) The missing TRANSPORT-level lock (this is what both findings ask for): in each DefaultApiClientUnitTest (or DefaultApiClientTest against chasm if its /test/echo envelope exposes the raw request URI) send GET <local listener>/echo?revision=v1.0/beta:rc1[x]'y and assert the raw request line the server sees: `revision=v1.0/beta:rc1%5Bx%5D'y` in java, kotlin, csharp, python, php, dart, go, swift, elixir, and `revision=v1.0/beta:rc1%5Bx%5D%27y` in node, rust, ruby (the documented FORCED difference, asserted so it cannot drift). (3) PetApi-level: getPetTag(revision='v1.0/beta:rc1#frag') must produce `revision=v1.0/beta:rc1%23frag` in the captured URL in all 12.

**Risk.** Changes bytes only for allowReserved values containing `#`, `[`, `]` (java, kotlin, csharp, go, elixir, node, ruby previously sent `[ ]` literal; all previously mangled `#`). A server that matched on a raw `[`/`]` would have been depending on non-RFC input. Also update each encoder's doc comment which lists the reserved set. Add the rule to AGENTS.md next to the existing %20 rule (consistent with it; contradicts no standing decision).

## D50 Content-based query parameters (content: application/json) go through the SDK ObjectSerializer.serialize in all 12

- Ledger ids: F068 | Theme: query-form-path-encoding | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** java and kotlin call the SDK serializer (templates/java/api/api.mustache:334 objectSerializer.serialize; kotlin api.mustache:273). dart calls its top-level serialize() (api.mustache:296). swift calls ObjectSerializer.serialize but wrapped in `try?` (api.mustache:275, 300) so a serialization failure silently DROPS the parameter. The other eight bypass it:
go: json.Marshal with the error discarded (go/api/api.mustache:270), HTML escaping ON so `<` -> `<`, contradicting AGENTS go rule (SetEscapeHTML(false), go/object_serializer.mustache:74).
csharp: System.Text.Json.JsonSerializer.Serialize with default options (csharp/api/api.mustache:374): `<` -> `<`, `e-acute` -> `é`, SDK converters skipped.
python: json.dumps (api.mustache:260): spaces after separators, ensure_ascii, TypeError on a pydantic model.
node: JSON.stringify (apis.mustache:256): Date ends in Z not +00:00.
ruby: JSON.generate (api.mustache:273).
php: json_encode with no flags (api.mustache:246, 542): `/` -> `\/`, non-ASCII escaped.
rust: serde_json::to_string(..).unwrap_or_default() (api.mustache:412): failure sends an empty string.
elixir: Jason.encode! (api.mustache:342): raises Jason.EncodeError instead of SerializationError.
For {"breed":"a/b<e-acute"} at least five different byte strings reach the wire.

**Canonical behaviour.** Every content: application/json query parameter value is produced by the same function that serializes request bodies (java/kotlin objectSerializer.serialize, csharp Serializer.Serialize, python self._object_serializer.serialize, ruby/node/php ObjectSerializer.serialize, dart serialize, go serialize(), rust object_serializer::serialize, swift ObjectSerializer.serialize, elixir ObjectSerializer.serialize), and any failure propagates as the SDK SerializationError (swift `try` not `try?`, go/rust return the error instead of discarding it). The resulting compact JSON text (no spaces, `/` and non-ASCII unescaped, `<` unescaped, canonical date-time +00:00, wire property names) is then percent-encoded by the normal query encoder.

**Why.** (a) AGENTS error contract: SerializationError covers every encode/decode failure; AGENTS go rule requires HTML escaping off. (b) 4 of 12 already do it. (c) Today a failure either sends an empty string (rust), drops the param (swift) or raises a foreign type (elixir); bytes differ for `<`, `/`, non-ASCII, dates. (d) every SDK already has the function, nothing new is needed; none of the 8 bypasses is forced.

**Rejected.** Rejected: leave stdlib JSON encoders (5 different byte strings, discarded errors). Rejected: sorting keys or other normalisation beyond what the shared serializer does (would diverge from body serialization).

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: Replace System.Text.Json.JsonSerializer.Serialize(...) with the BaseApi ObjectSerializer instance (expose a protected SerializeJson helper); errors become SerializationException. (templates/csharp/api/api.mustache:371-378, templates/csharp/base_api.mustache)
- python: Replace json.dumps with self._object_serializer.serialize(options.<p>); drop the inline `import json`. (templates/python/api/api.mustache:257-261)
- ruby: Replace JSON.generate with ::<Module>::ObjectSerializer.serialize. (templates/ruby/api/api.mustache:272-274)
- node: Replace JSON.stringify with ObjectSerializer.serialize (import it in the api template if absent). (templates/node/api/apis.mustache:254-258)
- php: Replace json_encode with ObjectSerializer::serialize in both blocks. (templates/php/api/api.mustache:244-248,540-544)
- dart: already canonical
- go: Replace json.Marshal(...) with serialize(...) ([]byte, error) and return the error from the operation. (templates/go/api/api.mustache:268-273)
- rust: Replace serde_json::to_string(..).unwrap_or_default() with crate::object_serializer::serialize(&v)? mapped to the operation's boxed SerializationError, in the required and optional arms. (templates/rust/api/api.mustache:406-421)
- swift: Replace `if let json = try? ObjectSerializer.serialize(val)` with `try`, in both optional and required arms. (templates/swift/api/api.mustache:270-278,295-303,321-)
- elixir: Replace Jason.encode! with PetstoreClient.ObjectSerializer.serialize (SerializationError on failure). (templates/elixir/api/api.mustache:339-346)

**Tests.** Needs the YAML below (petstore has no content query param). Lock in each PetApi test file with meta = PetCriteria{breed:'a/b<e-acute' (U+00E9), min_age:3}: the captured query value must equal the percent-encoding of the compact JSON {"breed":"a/b<é","min_age":3}, i.e. `meta=%7B%22breed%22%3A%22a%2Fb%3C%C3%A9%22%2C%22min_age%22%3A3%7D`. A second row with an unserializable value (where the language can construct one) asserts SerializationError and zero requests (also pins swift `try?` and rust unwrap_or_default). Files: java api/PetApiTest.java, kotlin api/PetApiTest.kt, csharp Test/Api/PetApiTest.cs, python test/api/test_pet_api.py, ruby test/api/pet_api_test.rb, node test/Api/pet-api.test.ts, php tests/Api/PetApiTest.php, dart test/pet_api_test.dart, go test/pet_api_test.go, rust tests/pet_api_test.rs, swift PetApiTests.swift, elixir test/pet_api_test.exs.

**Petstore spec addition.**

```yaml
# append to /pet/{petId}/tag/{tagName} get.parameters (PetCriteria is defined in the F063 YAML):
        - name: meta
          in: query
          description: JSON-encoded query parameter (OAS `content`, not `schema`)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PetCriteria'
```

**Risk.** Low; only content-typed query params are affected and petstore has none. csharp: BaseApi.Serializer is private (BaseApi.cs:28), make it protected/internal or add a BaseApi helper. Only application/json is in scope; other content media types stay unsupported.

## D51 format: byte parameters (query, header, path, form) are base64-encoded, in all 12

- Ledger ids: F069 | Theme: query-form-path-encoding | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** No SDK has a byte branch on the parameter path (grep isByteArray in api templates: none; only dart/elixir model templates use it), although model bodies already round-trip base64 in all 12 (AGENTS R8-1). For bytes "hi":
java: String.valueOf(byte[]) -> `[B@6d06d69c` (ObjectSerializer.java:195).
kotlin: ByteArray.toString() -> `[B@...` (ObjectSerializer.kt stringify else-branch :540).
csharp: byte[] is an IList so ValueSerializer.cs:50 treats it as an array -> `104,105` (or p=104&p=105 exploded); Stringify falls to ToString (ObjectSerializer.cs:148).
python: str(b'hi') -> `b'hi'` (object_serializer.py:591).
node: Buffer -> String(value) (object-serializer.ts:789) raw bytes as text.
ruby: String#to_s raw bytes (param type is String with format byte, binary_vault.rb:54).
php: ?string raw bytes (php stringify :255-300).
dart: Uint8List is a List -> `104,105` (value_serializer.dart:453).
go: toStringSlice reflects over []byte -> `104,105` (value_serializer.go:406).
rust: Vec<u8> through serde -> `[104,105]` (object_serializer.rs:430).
swift: Data.description -> `2 bytes` (ObjectSerializer.swift:301).
elixir: binary sent raw (object_serializer.ex stringify).

**Canonical behaviour.** A parameter declared type: string, format: byte (and each element of an array/map of them) serializes to standard-alphabet RFC 4648 base64 WITH padding (`aGk=` for "hi") before the normal query/header/path/form encoding (so the query wire is `aGk%3D`, header `aGk=`). Two mechanisms, one result: (1) the nine languages whose native byte type is distinct (java byte[], kotlin ByteArray, csharp byte[], python bytes, node Buffer/Uint8Array, dart Uint8List, go []byte, rust Vec<u8>, swift Data) get a byte branch at the TOP of the central stringify/serialize-styled dispatch, before any IList/List/slice/array check; (2) php, ruby and elixir model byte as a plain string/binary, so the type cannot tell bytes from text: the API templates wrap the value with the language's base64 encoder when the parameter has isByteArray (and items.isByteArray), using one helper per language (ObjectSerializer.encodeBase64Param). A null stays omitted.

**Why.** (a) OAS `format: byte` means base64 text, and the SDKs already apply that to bodies; (c) the current outputs are identity hashes, comma lists or raw binary, i.e. silently wrong data; (d) every language has a stdlib base64 encoder. Type-driven stringify for the nine is one function per language instead of four template sites; the three string-typed languages cannot be type-driven without corrupting genuine text params, hence the codegen flag.

**Rejected.** Rejected: param-driven wrap in all 12 (4 call-site families x 12 templates, easy to miss header/path/form; the typed nine are fixed centrally by one branch). Rejected: reject byte params (valid OAS, simple to support). Rejected: URL-safe base64 (OAS format byte is the standard alphabet).

**Per language.**

- java: Add `if (value instanceof byte[] b) return Base64.getEncoder().encodeToString(b);` at the top of ObjectSerializer.stringify (also List<byte[]> elements). (templates/java/object_serializer.mustache (golden ObjectSerializer.java:~140-195))
- kotlin: Add `is ByteArray -> Base64.getEncoder().encodeToString(value)` to stringify's when. (templates/kotlin/object_serializer.mustache (golden ObjectSerializer.kt:516))
- csharp: Add `byte[] bytes => Convert.ToBase64String(bytes)` to Stringify AND a `value is byte[]` guard before `value is IList` in ValueSerializer (ValueSerializer.cs:50). (templates/csharp/object_serializer.mustache, templates/csharp/value_serializer.mustache)
- python: In stringify add `if isinstance(value, (bytes, bytearray)): return base64.b64encode(value).decode('ascii')` before str(value). (templates/python/object_serializer.mustache (golden object_serializer.py:596))
- ruby: Template wrap: `Base64.strict_encode64(x)` when the param isByteArray (ruby String type cannot signal bytes). (templates/ruby/api/api.mustache (query/header/path/form blocks), templates/ruby/object_serializer.mustache (helper))
- node: Add `if (Buffer.isBuffer(value) || value instanceof Uint8Array) return Buffer.from(value).toString('base64')` in stringify. (templates/node/object_serializer.mustache (golden object-serializer.ts:789))
- php: Template wrap with base64_encode() when isByteArray / items.isByteArray, via a helper in ObjectSerializer. (templates/php/api/api.mustache, templates/php/object_serializer.mustache)
- dart: Add `if (value is Uint8List) return base64Encode(value);` in stringify AND before `value is List` in value_serializer.dart:453. (templates/dart/object_serializer.mustache:311, templates/dart/value_serializer.mustache)
- go: In stringify and in toStringSlice treat []byte as a scalar and return base64.StdEncoding.EncodeToString (check before the reflect.Slice test at value_serializer.go:406). (templates/go/object_serializer.mustache:198, templates/go/value_serializer.mustache)
- rust: stringify/to_query_value/to_form_value take bytes via a dedicated path: for a Vec<u8> param emit base64 (STANDARD engine) instead of serde's number array; may need a codegen flag so the call site picks serialize_bytes_param. (templates/rust/object_serializer.mustache:459,493,547; templates/rust/api/api.mustache)
- swift: Add `case let data as Data: return data.base64EncodedString()` before the CustomStringConvertible case (ObjectSerializer.swift:301), and make toStringList skip Data. (templates/swift/object_serializer.mustache)
- elixir: Template wrap with Base.encode64/1 when isByteArray (binary type cannot signal bytes). (templates/elixir/api/api.mustache, templates/elixir/object_serializer.mustache)

**Tests.** Needs the YAML below. Per-language PetApi test (same files as F068): getPetTag with fingerprint = bytes "hi" (0x68 0x69) must send `fingerprint=aGk%3D` and header `X-Fingerprint: aGk=`; a second row with bytes {0xFB,0xFF} asserts `%2B%2F%2F`-free standard alphabet (`-_` URL-safe forms must NOT appear): base64 `+/8=` -> `fingerprint=%2B%2F8%3D`. Also add a byte row to each ObjectSerializer test's stringify table (java ObjectSerializerTest.java, kotlin, csharp ObjectSerializerTest.cs, python test_object_serializer.py, node object-serializer.test.ts, dart, go object_serializer_test.go, rust object_serializer_test.rs, swift ObjectSerializerTests.swift) asserting stringify(bytes)=='aGk='.

**Petstore spec addition.**

```yaml
# append to /pet/{petId}/tag/{tagName} get.parameters:
        - name: fingerprint
          in: query
          description: Opaque bytes, base64 on the wire (OAS format byte)
          schema:
            type: string
            format: byte
        - name: X-Fingerprint
          in: header
          schema:
            type: string
            format: byte
```

**Risk.** php/ruby/elixir callers who today pass an already-base64 string would be double-encoded: the Options field doc must say the value is the raw bytes. Latent today (petstore has no byte param), and the current output was never usable. Kotlin/csharp/go/dart ordering matters: the byte branch must precede the collection branch.

## D52 Query and form-body percent-encoding keeps exactly A-Za-z0-9-._~ literal (%-encodes * ! ' ( )); only dart still diverges and 9 languages lack a lock test

- Ledger ids: F086 F197 F199 F239 F341 | Theme: query-form-path-encoding | Still true: **partly** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** Re-checked against the current goldens. The ledger's java/node/ruby/kotlin divergences are FIXED, but the ledger wrongly lists dart as already canonical. Query = space as %20; form body = space as +.
java: FIXED. encode() = URLEncoder + `+`->%20, `*`->%2A, %7E->~ (BaseApi.java:563-566); formEncode() same without the space rule (:579). Locked: BaseApiTest.java:454 (query) and :1059 (form).
kotlin: canonical. Ktor encodeURLParameter keeps only -._~ (verified by running ktor-http 3.5.0: `a~b*c!d'e(f)g h+i` -> `a~b%2Ac%21d%27e%28f%29g%20h%2Bi`); BaseApi.kt:489 (form, spaceToPlus) and :501 (query). NO BaseApi-level lock.
csharp: canonical (Uri.EscapeDataString, BaseApi.cs:463 form with %20->+, :519/:545 query). No lock.
python: canonical (urlencode quote_via=quote for query, base_api.py:217; urlencode default quote_plus for form, :345). No lock.
ruby: FIXED. query CGI.escape.gsub('+','%20') (base_api.rb:239-246), form URI.encode_www_form + gsub('%7E','~').gsub('*','%2A') (:268-271). Locked: base_api_test.rb:485 (form); no query assertion found.
node: FIXED. encodeQueryComponent escapes ! ' ( ) * (value-serializer.ts:234-239), form `.replace(/\*/g,'%2A').replace(/%7E/g,'~')` (base-api.ts:413). Locked: base-api.test.ts:497,705.
php: canonical (rawurlencode, BaseApi.php:323-324). No lock.
dart: NOT canonical. Uri.encodeQueryComponent keeps `! ' ( ) *` literal (the repo's own OAuth helper proves it: oauth2_token_manager.dart:289-296 post-processes exactly these), and base_api.dart:345 (_encodeFormComponent), :365 (query key) and :391 (query value) apply NO post-processing, so `a~b*c!d'e(f)g` goes out as `a~b*c!d'e(f)g`.
go: canonical (url.QueryEscape, base_api.go:308,336; url.Values.Encode form). No lock.
rust: canonical (urlencoding::encode base_api.rs:434-436; form_url_encode.rs:26 keeps -._~). No lock.
swift: canonical (explicit -._~ set, BaseApi.swift:309-314; formSafe :400). No lock.
elixir: canonical (URI.encode_www_form + `+`->%20, base_api.ex:463; form :490-499). No lock.

**Canonical behaviour.** Query keys/values and application/x-www-form-urlencoded keys/values keep exactly `A-Za-z0-9-._~` literal and percent-encode everything else as uppercase %XX (UTF-8), so `a~b*c!d'e(f)g h` is `a~b%2Ac%21d%27e%28f%29g%20h` in a query value and `a~b%2Ac%21d%27e%28f%29g+h` in a form value. Space is %20 in queries and `+` in form bodies; a literal `+` is %2B in both. This is the same set AGENTS R8-10 fixed for OAuth, applied to every operation encoder. allowReserved values are a separate encoder (F066).

**Why.** (a) RFC 3986 unreserved set and the AGENTS %20-vs-+ rule; (b) 11 of 12 already emit it (the F086 text mis-stated dart); (c) a spec-compliant server decodes both forms identically but the bytes differ for signing, caching and recording; (d) dart has `Uri.encodeQueryComponent` plus the already-written `_rfc3986` post-processor, so no dependency. Nothing is forced.

**Rejected.** Rejected: the WHATWG form set (`*` literal, `~`->%7E) as canonical: only java/ruby/node used it and it contradicts R8-10 and the rust helper's own comment. Rejected: encodeURIComponent-style set (keeps `! ' ( ) *`), which is what dart does today and WHATWG then rewrites `'` anyway.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- php: already canonical
- dart: Add a public `encodeRfc3986QueryComponent(String, {bool spaceToPlus})` to value_serializer: Uri.encodeQueryComponent then `!`->%21 `'`->%27 `(`->%28 `)`->%29 `*`->%2A (move the logic from oauth2_token_manager.dart:289-296), then `+`->%20 for queries. Call it from _encodeFormComponent (base_api.dart:345), the key (:365) and the value (:391). Delete the 5 private copies in auth/oauth/*. (templates/dart/value_serializer.mustache, templates/dart/base_api.mustache, templates/dart/auth/oauth/*.mustache)
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** No new YAML: getPetByName (required query `category`) and setPetPreferences (form fields nickname/note) exist. In every BaseApi test (java BaseApiTest.java, kotlin BaseApiTest.kt, csharp BaseApiTest.cs, python test_base_api.py, ruby base_api_test.rb, node base-api.test.ts, php BaseApiTest.php, dart base_api_test.dart, go test/base_api_test.go, rust tests/base_api_test.rs, swift BaseApiTests.swift, elixir base_api_test.exs) assert, from one shared value `a~b*c!d'e(f)g h`: query `?category=a~b%2Ac%21d%27e%28f%29g%20h` and form body `nickname=a~b%2Ac%21d%27e%28f%29g+h` (java :454/:1059 and node :497/:705 are the existing models; copy them), plus a key row (`k~*`-> `k~%2A`). Tests are authored in templates/<lang>/test/*.mustache. Add one line to AGENTS.md Resolved: the operation query/form encoders use the R8-10 set.

**Risk.** Dart only: query/form values containing `! ' ( ) *` change from literal to percent-encoded; servers decode identically. Dart has the encoder duplicated 5 times (oauth2_token_manager.dart plus 4 authenticators); consolidate into one public helper so base_api and OAuth cannot drift again.

## D53 Query, form-body and multipart part order is the spec declaration order in all 12 (ordered containers everywhere)

- Ledger ids: F087 | Theme: query-form-path-encoding | Still true: **yes** | Effort: L | Wire-visible: True | Needs user: False

**Current state.** Declaration order today in kotlin (mutableMapOf = LinkedHashMap, PetApi.kt:166), csharp (Dictionary insertion order in practice, PetApi.cs:296; undocumented but stable without removals), php ([] arrays), ruby, node, dart (maps keep insertion order), rust query/form (Vec<(String,String,bool)>, pet_api.rs:254). Diverging:
java: HashMap for queryParams and formBody (PetApi.java:1405, :1744; templates/java/api/api.mustache:324,382), so getPetTag sends filter&sizes&colors&revision-style hash order and setPetPreferences sends note,nickname,renewalDate,tags; multipart parts the same.
go: query `for k, v := range queryParams` over map[string]any (base_api.go:294) = random per call; multipart `range formFields` (:419) random per call; form body goes through url.Values.Encode (:404) which SORTS keys (deterministic but nickname&note&renewalDate&tags, not declaration).
swift: [String: Any?] dictionaries for query (BaseApi.swift:335), form (:410) and multipart (:444); Swift seeds hashing per process so order varies between runs.
elixir: query_params = %{} and request_body maps (pet_api.ex:2207; base_api.ex:480): maps up to 32 keys iterate sorted, so bornAfter&filter[..]&status and multipart documentType,file,notes.
rust: multipart is HashMap<String, MultipartValue> (base_api.rs:103; serialize_multipart_body iterates it, default_api_client.rs:661) = random per run; serialize_deep_object also iterates a HashMap (value_serializer.rs:143).
python: allowReserved params are emitted after all normal ones (base_api.py:211-240 normalized then reserved groups).
No test pins the full string: every multi-field assertion uses contains/split; the kotlin and go exact `nickname=a+b&tags=x&tags=y` assertions pass only because those keys happen to be in sorted order.

**Canonical behaviour.** Wire order = declaration order: query params in the operation's parameter order, form-urlencoded pairs and multipart parts in the request-body schema's property order, with array members expanded in place in array order; authenticator-contributed query params (apiKey in query) are appended after the operation's own params. Every SDK builds these in an insertion-ordered container: java LinkedHashMap, go a slice of {key, value} pairs, swift an array of (String, Any?) tuples, elixir a list of {k, v} tuples, rust Vec<(String, MultipartValue)>, python one in-order pass that encodes each key normal-or-reserved as it goes. deepObject/map expansion keeps the map's own order where it is ordered and sorts keys lexicographically where the language map is unordered (go, rust, swift, elixir).

**Why.** (b) 7 of 12 already do exactly this; (c) go/swift/rust multipart order changes between RUNS, which breaks byte-level request recording, non-canonicalising signature schemes, cache keys and the AGENTS rule of identical bytes on the wire; (d) every language has an ordered container with no new dependency. Multipart part order is also semantically meaningful to some servers (e.g. a metadata part before the file).

**Rejected.** Rejected: sort alphabetically everywhere (what go form and elixir small maps do by accident): nothing in the spec says sorted, it would break the 7 languages that are right, and it hides the declared order. Rejected: leave unspecified and make tests order-insensitive (the current state; it permits run-to-run nondeterminism).

**Per language.**

- java: HashMap -> LinkedHashMap for queryParams, formBody (and multipart form map) in the generated operations. (templates/java/api/api.mustache:324,382 (golden PetApi.java:1405,1744))
- kotlin: already canonical
- csharp: already canonical
- python: Replace the normalized/reserved two-group build with a single loop over filtered.items(): quote (safe='') normally, ValueSerializer.encode_query_allowing_reserved for AllowReservedValue, appending to one parts list. (templates/python/base_api.mustache (golden base_api.py:196-240))
- ruby: already canonical
- node: already canonical
- php: already canonical
- dart: already canonical
- go: Introduce `type queryPair struct{ key string; value any }`; invokeApiParams.queryParams, buildQueryString (base_api.go:288-294), form body (replace url.Values.Encode at :404 by an in-order join using the existing QueryEscape rules) and buildMultipartBody (:419) take ordered []pair slices; operations append in declaration order. (templates/go/base_api.mustache:286, templates/go/api/api.mustache, templates/go/default_api_client.mustache (multipart))
- rust: Multipart HashMap -> Vec<(String, MultipartValue)> in InvokeApiParams.multipart (base_api.rs:103), serialize_multipart_body(&[(String, MultipartValue)], ..) (default_api_client.rs:661) and the operation builder (pet_api.rs:363-376 uses multipart.insert -> push); serialize_deep_object iterates sorted keys. (templates/rust/base_api.mustache, default_api_client.mustache, value_serializer.mustache:143, api/api.mustache)
- swift: queryParams: [(String, Any?)], form/multipart field collections: [(String, Any)] (or KeyValuePairs); buildQueryString/form/buildMultipartBody iterate the arrays (BaseApi.swift:335,410,444); operations append in declaration order. (templates/swift/base_api.mustache, api/api.mustache, default_api_client.mustache)
- elixir: query_params = [] and form/multipart request_body as lists of {key, value} tuples (append with ++); serialize_body clause for form (base_api.ex:480) and the multipart builder accept the list; JSON bodies stay maps. (templates/elixir/api/api.mustache:312, templates/elixir/base_api.mustache)

**Tests.** No new YAML: getPetTag (declaration order colors, sizes, filter, revision), setPetPreferences (nickname, tags, note, renewalDate), findPetsByStatus (status, filter[..], bornAfter) and uploadPetDocument multipart (file, documentType, notes) exist. In each PetApi test (java api/PetApiTest.java:286/:684, kotlin api/PetApiTest.kt:338/:1055, csharp Test/Api/PetApiTest.cs, python test/api/test_pet_api.py:417-457, ruby, node, php, dart, go test/pet_api_test.go, rust tests/pet_api_test.rs, swift PetApiTests.swift, elixir test/pet_api_test.exs) assert the EXACT full string, not contains/split: getPetTag with all four set -> query `colors=a%7Cb&sizes=x%20y&filter=f&revision=v1.0/beta:rc1`; setPetPreferences -> body `nickname=n&tags=x&tags=y&note=m&renewalDate=2025-01-02`; uploadPetDocument -> the three `Content-Disposition: form-data; name=` markers appear in order file, documentType, notes. For go/swift/rust multipart, repeat the call 20 times and assert identical bytes (catches per-run randomness). Replace the coincidental-order assertions kotlin BaseApiTest and go base_api_test.go:614 with an unsorted-key fixture (z then a) so they would fail on sorting.

**Risk.** Largest blast radius is go (invokeApiParams.queryParams and formFields change type, internal-only) and swift/elixir (api and base api templates). Internal types only, no public API change. csharp relies on Dictionary insertion order; a later removal path would break it, the lock test guards that.

## D54 Integer decode: reject fractional, quoted and out-of-range values for int32/int64 everywhere, including container leaves

- Ledger ids: F006 F076 F205 F213 F310 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: L | Wire-visible: True | Needs user: False

**Current state.** java: canonical, ObjectSerializer.java:392-398 (ALLOW_COERCION_OF_SCALARS false, ACCEPT_FLOAT_AS_INT disabled); the 1.9->1 truncation in F006/F213 is already fixed.
kotlin: model fields canonical (StrictIntSerializer/StrictLongSerializer, ObjectSerializer.kt:103-130, toIntOrNull); container leaves NOT: getInventory/getMatrix decode Map<String,Int>/List<List<Int>> with the stock Int serializer (StoreApi.kt:270), which accepts quoted numbers ("5").
csharp: canonical (int/long, STJ strict numbers; Order.cs:87).
php: raw and container primitives are strict now (ObjectSerializer.php ~560-600, 'abc'->0 and 1.9->1 are gone; int64 overflow raises OverflowException) but int32 range is not checked (?int, Order.php:30).
python: StrictInt unbounded (order.py:34); container leaves use strict aliases (object_serializer.py:77 _STRICT_LEAF_TYPES) but also unbounded.
ruby: Integer only (object_serializer.rb:374 is_a?(Integer)); no range; bignum accepted.
node: deserializeLeaf 'integer' = Number.isInteger (object-serializer.ts:883-903) and model constructor typeof+isInteger (order.ts:86-96); no range; int64 above 2^53 rounded.
dart: models 'as int?' (order.dart fromJson) with no range; containers use lazy .cast<String,int>() (store_api.dart:363) so a bad element throws a raw TypeError at element access, outside SerializationException.
go: canonical (int32/int64, order.go:65).
rust: canonical (i32/i64, order.rs:43).
swift: int32/format-less map to 64-bit Int (BetterSwiftCodegen.java:66-67, Order.swift:24); int64 is Int64; also accepts 1.0.
elixir: is_integer only (order.ex 'integer()'); no range; bignum accepted.

**Canonical behaviour.** Anywhere an integer is decoded (model field, container element, top-level response) a JSON number for format int32 and for format-less integer must be an integer token inside [-2^31, 2^31-1]; for format int64 inside [-2^63, 2^63-1]. A fractional number (1.5), a quoted string ("5"), a boolean or an out-of-range number raises the SDK SerializationError. Nothing is truncated, coerced, wrapped or rounded. Dynamic languages (python, ruby, php, dart, node, elixir) apply the same range check when a model is constructed or serialised. FORCED exceptions to document in AGENTS.md: (1) node cannot detect int64 values above 2^53 because JSON.parse rounds before the SDK sees them (README already documents it); (2) the literal spelling 1.0 is NOT pinned: node and swift (JSONDecoder) cannot tell it from 1, while the others reject it, so tests must not assert either way. Format-less integer follows int32, as java/kotlin/csharp/go/rust already do through the generator's 'integer' type token.

**Why.** Majority already correct (java, kotlin models, csharp, go, rust, php container leaves). Criteria (c) silent truncation, coercion and bignum acceptance are silent data changes; (d) every language can range-check without a dependency (pydantic Field(ge,le), Int32, guards). The only truly forced pieces are node int64>2^53 and the 1.0 spelling.

**Rejected.** (1) Leave range unchecked in the six dynamic languages and document it: rejected, 5 compiled SDKs already reject and the dynamic six then re-send a value the schema forbids (criterion c, and the repo's same-input-same-outcome rule). (2) Treat format-less integer as int64: rejected, would widen java Integer, kotlin Int, csharp int, go int32, rust i32 to 64 bit, a public type break in five shipped SDKs. (3) Make node reject int64 above 2^53 via isSafeInteger: rejected, turns a lossy-but-working call into a hard failure for snowflake-style ids; keep the documented rounding as a forced exception.

**Per language.**

- java: already canonical
- kotlin: Container leaves: in ObjectSerializer.deserialize, before decodeFromJsonElement, walk serializer<T>().descriptor against the parsed JsonElement and require PrimitiveKind INT/SHORT/BYTE/LONG leaves to be unquoted integral JsonPrimitives in range, BOOLEAN unquoted true/false, FLOAT/DOUBLE unquoted numbers, STRING quoted; throw SerializationException so the SDK wraps it. (Annotating type arguments with @Serializable(with=...) is the alternative but is fragile with reified serializer<T>().) (templates/kotlin/object_serializer.mustache (deserialize, near the Strict* serializers); golden kotlin/src/main/kotlin/com/example/petstore/ObjectSerializer.kt)
- csharp: already canonical
- php: Add int32 range: in the 'int'/'integer' primitive branch of deserializeInternal and in the existing post-denormalize hook next to assertRequiredPresent, check a generated per-model INT32_PROPERTIES list (int64 needs nothing, PHP int is 64 bit and overflow is already rejected). Throw SerializationException. Apply the same check on normalise for caller-built models. (templates/php/object_serializer.mustache (~506-600); templates/php/models/model.mustache)
- python: typeMapping integer->Int32 and long->Int64 in BetterPythonCodegen.java:101-102; define in _types.py.mustache Int32 = Annotated[StrictInt, Field(ge=-2**31, le=2**31-1)] and Int64 likewise; replace StrictInt in object_serializer _NATIVE_TYPES_MAPPING (line 155) and _STRICT_LEAF_TYPES (line 77) with the two aliases so containers and models share them. (src/main/java/.../python/BetterPythonCodegen.java:101; templates/python/_types.mustache; templates/python/object_serializer.mustache)
- ruby: Split the 'Integer' branch of convert_to_type into 32/64-bit checks (new type tokens or a bits argument from the model's OPENAPI_TYPES) and raise Errors::SerializationError when out of range; also constrain the Dry attribute (Types::Strict::Integer.constrained(gteq:, lteq:)) for construction. (templates/ruby/object_serializer.mustache:371; templates/ruby/models/model.mustache)
- node: Add int32 bounds beside the Number.isInteger check for {{#isInteger}} properties in model.mustache (~556) and give deserializeLeaf separate 'int32'/'int64' kinds; int64 stays integer-only (forced exception, document in AGENTS.md). (templates/node/models/model.mustache:556; templates/node/object_serializer.mustache (deserializeLeaf))
- dart: Replace 'as int?' in models with ObjectSerializer.readInt32/readInt64 helpers that throw SerializationException on non-int or out-of-range; replace .cast<String,int>() / .cast<List<int>>() in the api template with eager element-validating helpers so a bad leaf fails inside deserialize. (templates/dart/models/model.mustache; templates/dart/object_serializer.mustache; templates/dart/api/api.mustache (returnType deserialize lambda))
- go: already canonical
- rust: already canonical
- swift: typeMapping int and integer -> Int32 (keep long -> Int64) in BetterSwiftCodegen.java:66-67; regenerate. Public model, option and container types become Int32 (API break, accepted: it is the only way to get Codable to reject 3000000000). JSONDecoder keeps accepting 1.0 (forced, not pinned). (src/main/java/.../swift/BetterSwiftCodegen.java:66-67)
- elixir: Add range guards to the integer clause of convert_to_type and to sanitize_for_serialization for int32/int64 typed fields (distinguish them by the type descriptor string, e.g. emit 'int32'/'int64' tokens next to 'integer()'). (templates/elixir/object_serializer.mustache; templates/elixir/models/model.mustache (type descriptors))

**Tests.** Existing petstore fixtures are enough, no YAML: Order.quantity (int32), Order.id (int64), ApiResponse.code, WetFood.volumeMl, getInventory (map<string,int32>) and getMatrix (array<array<int32>>). Add one table to the ObjectSerializer test in every language: {"quantity":3000000000}, {"quantity":1.5}, {"quantity":"5"}, {"quantity":true}, {"id":9223372036854775808} (skip for node) all raise the language's SerializationException/Error (assert exact type); {"quantity":2147483647} and {"id":9223372036854775807} decode. Container rows: getInventory body {"a":"5"}, {"a":3000000000}, {"a":1.5} and getMatrix [[1.5]] fail inside deserialize (dart: must be SerializationException, not TypeError). Do not assert on the spelling 1.0. Files: java/src/test/java/com/example/petstore/ObjectSerializerTest.java, kotlin/src/test/kotlin/com/example/petstore/ObjectSerializerTest.kt, csharp/Test/ObjectSerializerTest.cs, php/tests/ObjectSerializerTest.php, python/test/test_object_serializer.py, ruby/test/object_serializer_test.rb, node/test/object-serializer.test.ts, dart/test/object_serializer_test.dart, go/pkg/object_serializer_test.go, rust/tests/object_serializer_test.rs, swift/Tests/PetstoreClientTests/ObjectSerializerTests.swift, elixir/test/object_serializer_test.exs (templates under templates/<lang>/test/).

**Risk.** Swift Int->Int32 and python StrictInt->Int32 change public types/annotations; swift and python have shipped consumers. A server sending a format-less integer above 2^31 is already rejected by 5 SDKs, now by 12. Regenerate the six Zitadel clients and run their suites.

## D55 Schema validation keywords (pattern, min/max length, min/max, multipleOf, min/maxItems) are enforced by NO SDK

- Ledger ids: F056 F137 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** python: model.mustache:373 and :381 turn every keyword into Field(min_length=, max_length=, pattern=r'...', ge/gt/le/lt, multiple_of); README.md:128 says they ARE enforced; a raw pattern with a quote is a SyntaxError and a look-around pattern (pydantic-core regex) makes the models module fail to import.
java, kotlin, csharp, php, ruby, node, dart, go, rust, swift, elixir: no template references these keywords; node README.md:102 says 'not enforced'; all accept any value.

**Canonical behaviour.** None of the 12 enforces minLength, maxLength, pattern, minimum, maximum, exclusiveMinimum/Maximum, multipleOf, minItems or maxItems client-side, on decode, construction or encode. The server is the authority, exactly as eleven READMEs already say. The keywords may appear in doc comments and (python) as non-enforcing JSON-schema metadata. Python stops emitting them as pydantic Field constraints and its README section is rewritten to match the other eleven.

**Why.** Criteria (b) majority and documented README rule, (d) feasibility; the python-only behaviour also carries two real defects (SyntaxError on a quote in a pattern, import failure on look-around).

**Rejected.** Enforce everywhere: rejected. Regex dialects differ (ECMA look-around works in java/js/php/ruby/csharp/python-re but not in Go RE2, Rust regex, pydantic-core), so uniform pattern enforcement needs a per-language translator or a new dependency (criterion d); response-path enforcement turns small server drift into client failures; 11 of 12 READMEs document non-enforcement (criterion b). Enforce only the language-neutral numeric/length/items keywords: rejected, adds 11 implementations for a convenience the server already provides.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: Delete the {{#minLength}}..{{#maxItems}} blocks from both Field(...) lines (required and optional, enum and non-enum) in model.mustache:373-381; keep readOnly/writeOnly json_schema_extra. Rewrite the 'Numeric / string constraint validation' README section to the 'not enforced' wording used by node/readme. (templates/python/models/model.mustache:373,381; templates/python/readme.mustache)
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Needs the YAML below (petstore has no constrained property). In every language's ObjectSerializer test: decode Order {"quantity":500} (maximum 100) and Pet with nickname "ZZZZZZZZZZ" (breaks minLength/maxLength/pattern) succeeds and round-trips; python additionally imports the models package (guards the look-around import failure). Files as in the integer decision.

**Petstore spec addition.**

```yaml
# components.schemas.Order.properties.quantity: add two keys
        quantity:
          type: integer
          format: int32
          example: 7
          minimum: 1
          maximum: 100
# components.schemas.Pet.properties: add
        nickname:
          type: string
          minLength: 2
          maxLength: 8
          pattern: "^(?!admin)[a-z']+$"
```

**Risk.** Python callers lose client-side validation they may rely on (pydantic ValidationError becomes a server 4xx). Note in release notes; the Zitadel python client has no cross-language contract on it.

## D56 readOnly/writeOnly have no wire effect in any SDK; java's inverted Jackson access modes are removed

- Ledger ids: F025 F055 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** java: model.mustache:692-697 emits @JsonProperty(access=READ_ONLY/WRITE_ONLY), which Jackson defines from the document's point of view: a readOnly id is dropped on decode (null) and a writeOnly secret is never serialised. Inverted and wire-visible.
csharp (model.mustache:423-428), php (:461-466), ruby (:448-453), node (:290-295): doc remark only.
python: json_schema_extra metadata only (model.mustache:373).
kotlin, dart, go, rust, swift, elixir: keywords ignored, no doc remark.
No petstore property uses either keyword, so nothing is exercised.

**Canonical behaviour.** readOnly and writeOnly properties are sent and received like any other property in all 12; the keywords only produce documentation (a doc remark, plus python json_schema_extra metadata). A decoded response always populates a readOnly field (server id); a writeOnly field (password) is always written on requests. OAS only says a client SHOULD NOT send readOnly, and omitting it per language needs per-property serializer metadata that go, rust, kotlin and elixir cannot express without custom serializers, so omission is not made canonical.

**Why.** (b) eleven SDKs are already neutral; (c) no silent drops; (d) wire-neutral is trivially feasible everywhere, the omit variant is not.

**Rejected.** Omit readOnly on encode and ignore writeOnly on decode in all 12 (the ledger's 'most likely'): rejected for now. It is the stricter OAS reading, but rust (skip_serializing + custom deserialize), go, kotlin and elixir need hand-written per-property serializers, and silently dropping a caller-set value is a silent data change (criterion c). Jackson request/response views: rejected, java-only machinery with no analogue elsewhere. If strict servers that reject readOnly in requests become a real client problem, revisit as a separate decision.

**Per language.**

- java: Remove the two access= branches; emit a plain @JsonProperty and keep the javadoc remark. (templates/java/models/model.mustache:692-697)
- kotlin: Add the 'This property is read-only/write-only' KDoc remark for parity (no behaviour change). (templates/kotlin/models/model.mustache)
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: Add the doc remark only. (templates/dart/models/model.mustache)
- go: Add the doc remark only. (templates/go/models/model.mustache)
- rust: Add the doc remark only. (templates/rust/models/model.mustache)
- swift: Add the doc remark only. (templates/swift/models/model.mustache)
- elixir: Add the field note to the @moduledoc, as is done for deprecated fields. (templates/elixir/models/model.mustache)

**Tests.** Needs the YAML below. In each ObjectSerializer test: decode {"id":7,"username":"u"} as Account and assert id == 7 (this is the case java drops today); serialise Account(username:'u', password:'p') and assert the body contains password; decode {"id":7,"username":"u","password":"p"} keeps password. API-level: createAccount echo test in the store API test of each language.

**Petstore spec addition.**

```yaml
# components.schemas
    Account:
      type: object
      required: [username]
      properties:
        id: {type: integer, format: int64, readOnly: true}
        username: {type: string}
        password: {type: string, writeOnly: true}
# paths
  /store/account:
    post:
      tags: [store]
      operationId: createAccount
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema: {$ref: '#/components/schemas/Account'}
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: {$ref: '#/components/schemas/Account'}
```

**Risk.** None for the 11 neutral SDKs. Java users who relied on the (inverted) behaviour see ids populated and passwords sent, which is what the spec means.

## D57 date-time decode accepts only RFC 3339 (offset required) and never consults the host clock or time zone

- Ledger ids: F080 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** java, kotlin, go, rust, elixir: strict RFC 3339 parsers that never use the host zone (elixir DateTime.from_iso8601 needs an offset).
swift: ISO8601DateFormatter + UTC-pinned DateFormatter + UTC date-only fallback (ObjectSerializer.swift:68-82), host independent; forced date-only acceptance.
csharp: DateTimeOffset.TryParse(text, InvariantCulture, DateTimeStyles.None) (ObjectSerializer.cs:415-422): offset-less maps to the host offset, accepts "01/02/2024 10:00", "Jan 1 2024", "10:00".
ruby: Time.parse(data.to_s) (object_serializer.rb:388): host-local for offset-less, today's date for "10:00".
node: class-transformer Transform builds new Date(raw) (order.ts:39-50) and deserializeLeaf uses new Date(value) (object-serializer.ts:896): offset-less is host-local.
dart: DateTime.parse(json['shipDate'] as String) (order.dart:85): offset-less becomes a local DateTime.
php: DateTimeNormalizer with RFC3339_EXTENDED (ObjectSerializer.php:95) falls back to new \DateTime($data) (:618/:641), so "tomorrow", "@1700000000" are accepted, offset-less uses php.ini date.timezone, and plain "...:00Z" (no 3-digit fraction) only parses through that lenient fallback.
python: model fields use pydantic AwareDatetime (rejects naive, but lax mode also takes epoch numbers and a space separator); the container/top-level path uses dateutil parse() (object_serializer.py:476, 509, 519), which accepts free text and only rejects the naive result afterwards.

**Canonical behaviour.** A format: date-time value is decoded only if it matches RFC 3339 date-time: YYYY-MM-DDTHH:MM:SS, optional fraction of any length, then Z or +-HH:MM. Offset-less strings, date-only strings, free text ("tomorrow", "Jan 1 2024", "10:00"), US-style dates and epoch numbers raise SerializationError. No path may consult the host zone or the current date. Encode is unchanged (fixed .SSS+00:00). FORCED exception, already documented in AGENTS 'date-time input strictness': swift also accepts a bare yyyy-MM-dd as UTC midnight (one Foundation.Date type for date and date-time); it is host independent.

**Why.** (a) RFC 3339 §5.6 and JSON Schema date-time require the offset; (b) six SDKs already strict; (c) no host-dependent instants; (d) a shared pre-gate regex plus the platform parser works in every language with no new dependency.

**Rejected.** Keep platform parsers and document host dependence: rejected, the same wire string yields different instants per machine (criterion c). Also accept offset-less as UTC: rejected, silently invents a zone; RFC 3339 requires the offset and python/elixir/java/go/rust/kotlin already reject.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: Gate with the RFC 3339 regex, then DateTimeOffset.TryParse with DateTimeStyles.RoundtripKind (or TryParseExact with the Z and zzz formats); reject otherwise with JsonException. (templates/csharp/object_serializer.mustache:389)
- php: Replace the DateTimeNormalizer FORMAT_KEY path and both new \DateTime($data) fallbacks with one parseRfc3339(string) helper: regex gate (any fraction length, Z or offset), then new \DateTime($data) which can no longer consult the zone; use a small custom denormalizer for model fields. (templates/php/object_serializer.mustache (~95, ~618, ~641))
- python: Replace dateutil parse() on the datetime path with the regex gate plus dateutil isoparse (or datetime.fromisoformat after the gate); for the OffsetDateTime model alias add strict=True so epoch numbers and space separators are rejected too. (templates/python/object_serializer.mustache:476-519; templates/python/_types.mustache)
- ruby: In the 'Time' branch gate with the regex, then Time.iso8601; keep Date.parse out of the date-time path. (templates/ruby/object_serializer.mustache:388)
- node: Add ObjectSerializer.parseDateTime (regex gate + Date.parse) and use it from the model @Transform, from deserializeLeaf 'date' and from the constructor Invalid Date check. (templates/node/models/model.mustache (Transform for date-time); templates/node/object_serializer.mustache)
- dart: Add ObjectSerializer.parseDateTime (regex gate then DateTime.parse) and call it in model fromJson instead of DateTime.parse. (templates/dart/models/model.mustache; templates/dart/object_serializer.mustache)
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** No YAML: Order.shipDate and Metadata.createdAt exist. Table in each ObjectSerializer test: reject "2024-01-01T10:00:00", "01/02/2024 10:00", "tomorrow", "10:00", "1700000000" (swift: only the first four, date-only is its forced exception); accept "2024-01-01T10:00:00Z", "2024-01-01T10:00:00.123456789+05:30". csharp/ruby/php run the offset-less row under two TZ values to prove host independence (or assert rejection, which makes it moot).

**Risk.** Servers that emit offset-less timestamps start failing in csharp, ruby, node, dart, php, python; they were already failing in 6 others and are non-conformant.

## D58 format: byte base64 decode is strict RFC 4648 standard alphabet with required padding and no whitespace in all 12

- Ledger ids: F081 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** kotlin: Base64.getDecoder().decode (ObjectSerializer.kt:60) accepts unpadded "dGVzdA".
java: Jackson MIME_NO_LINEFEEDS default accepts embedded space; rejects unpadded.
csharp: STJ byte[] (.NET 9) accepts embedded space.
php: model byte fields are plain ?string (PetPassport.php:28) never validated on deserialize ("!!!!" accepted until getThumbnailAsBytes()); the top-level 'byte' branch uses base64_decode strict (ObjectSerializer.php:534) which accepts unpadded and spaces.
node: decodeBase64 strips \s+ before validating (object-serializer.ts:122), so it accepts spaces and newlines; its comment claiming parity with php/go/python is false.
go: encoding/json []byte skips \r and \n.
dart: base64Decode accepts the URL-safe alphabet.
python: b64decode(validate=True) (_types.py:109) rejects all four; ruby Base64.strict_decode64 (object_serializer.rb:565), rust STANDARD.decode (base64_serde.rs:19), swift, elixir Base.decode64 (object_serializer.ex:565) reject all four.

**Canonical behaviour.** A format: byte string must be base64 of the standard alphabet (RFC 4648 section 4), length a multiple of 4 with = padding, and contain no whitespace or newline and no URL-safe characters (- _). Anything else raises SerializationError at decode time, for model fields as well as containers. Non-zero trailing pad bits are NOT pinned (RFC 4648 section 3.5 leaves rejection optional and java/go/swift have no strict switch), so tests must not assert on them.

**Why.** (a) RFC 4648 section 3.3 says implementations must reject non-alphabet characters unless the spec says otherwise; (b) 6 SDKs already reject all four; (d) a length%4 plus alphabet regex pre-check works everywhere.

**Rejected.** Be lenient everywhere (MIME style): rejected, hides corrupted payloads and contradicts the rule already stated in AGENTS ('python and node reject malformed base64 on decode'). Pin trailing bits: rejected, not enforceable in java/go/swift without hand-rolled decoders.

**Per language.**

- java: Register a byte[] deserializer in the mapper module that applies the shared regex ^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$ then java.util.Base64.getDecoder(). (templates/java/object_serializer.mustache (mapper module))
- kotlin: In Base64ByteArraySerializer.deserialize run the same regex before getDecoder(). (templates/kotlin/object_serializer.mustache:60)
- csharp: Add a byte[] JsonConverter (regex gate, then Convert.FromBase64String) to the serializer options; encode stays Convert.ToBase64String. (templates/csharp/object_serializer.mustache)
- php: Validate every format: byte model field in the post-denormalize hook (regex gate + base64_decode strict) and give the top-level 'byte' branch the regex gate; keep the string-typed property. (templates/php/object_serializer.mustache (~534, assertRequiredPresent hook); templates/php/models/model.mustache)
- python: already canonical
- ruby: already canonical
- node: Stop stripping whitespace in decodeBase64; require length%4===0 and BASE64_PATTERN on the raw string; fix the comment. (templates/node/object_serializer.mustache (decodeBase64))
- dart: Add ObjectSerializer.decodeBase64 (regex gate then base64Decode) and use it in models and containers instead of bare base64Decode. (templates/dart/models/model.mustache; templates/dart/object_serializer.mustache)
- go: For models with format:byte fields, UnmarshalJSON should read the field as json.RawMessage/string, apply the regex gate, then base64.StdEncoding.DecodeString (the gate is what rejects the \n/\r that DecodeString skips). (templates/go/models/model.mustache; templates/go/object_serializer.mustache)
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** No YAML: PetPassport.thumbnail/scans and BinaryVault.labels exist. One shared vector table in each ObjectSerializer test applied to PetPassport.thumbnail: reject "dGVzdA", "dGVz dA==", "dGVz\ndA==", "-_8=", "!!!!"; accept "dGVzdA==". The existing R7-T3 malformed-base64 tests (missing in java/kotlin/csharp/dart/swift per PARITY_TODO) become the home for it.

**Risk.** Servers that wrap base64 with newlines (MIME) will start failing in java, csharp, php, node, go; they already fail in 7 others.

## D59 A required property that carries a default (synthetic discriminator default) is filled with that default when absent, in all 12

- Ledger ids: F129 F170 F320 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Decoding {"weightKg":1} as DryFood on its own:
java: accepted, 'dry' filled (public field initializer, only weightKg is a creator param; DryFood.java:17,43).
kotlin: accepted ('val foodType: String = "dry"', DryFood.kt:24).
csharp: accepted (no [JsonRequired]).
php: accepted (assertRequiredPresent skips defaulted ctor params).
python: accepted (default="dry", dry_food.py:19).
ruby: accepted (Types::Required.default('dry')).
node: accepted (constructor fills default).
elixir: accepted (required-with-default excluded from required_fields; doc claims it matches 'Go / PHP / Kotlin' but go rejects).
go: REJECTED, dry_food.go:68; the comment at :78 ('populated with that default') is dead code.
dart: REJECTED, 'Missing required field' (dry_food.dart:25).
swift: REJECTED, container.decode(String.self, forKey: .foodType) (DryFood.swift:38).
rust: REJECTED, plain String without serde default (dry_food.rs:16).
The F320 claim that rust DryFood::default().food_type is "" is already fixed: a hand-written Default now seeds "dry" (dry_food.rs:38-47).

**Canonical behaviour.** When a required property has a default (the synthetic mapping value generated for a oneOf subtype discriminator, e.g. DryFood.foodType = "dry", or a real schema default), decoding a payload that omits the key succeeds and fills the default; an explicit null is still rejected for a non-nullable property. The discriminator default is lossless because the mapping determines the only valid value. Construction without the discriminator also fills it (already true everywhere). This is a change for go, dart, swift and rust and a documented decision, not a quiet default: it keeps the stance stated in the elixir template ('a default satisfies the requirement') and the 8 SDKs shipped clients already use.

**Why.** (b) 8 of 12 already accept and the template comments state that as intended; (c) rejecting would change behaviour of six shipped clients while accepting only loosens four unshipped ones; the inference is lossless. Contradicts the OAS reading of required literally, which is why it is spelled out above.

**Rejected.** Reject absence in all 12 (OAS: required means present, the F170 'schema marks it required' argument): rejected for now. It is stricter than the spec needs, it would break shipped java/php/python/ruby/node/csharp clients that talk to servers which omit the redundant discriminator on a concretely-typed endpoint (criterion c, safe for callers), and the value is fully determined by the type. Needs the user only if you prefer strict spec reading; the opposite implementation is the mirror image (java creator param required=true, kotlin @Required, csharp [JsonRequired], python wire_decode before-validator, etc.).

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- elixir: already canonical
- go: In the generated UnmarshalJSON, skip the 'is missing' error for a required var that has a defaultValue and assign the default when the key is absent (keep the null rejection); this makes the existing dead comment true. (templates/go/models/model.mustache:302,350,389)
- dart: For required vars with defaultValue, read json['x'] as T? ?? default instead of the 'Missing required field' guard (keep rejecting an explicit null via containsKey). (templates/dart/models/model.mustache:359)
- swift: For required vars with defaultValue use decodeIfPresent(...) ?? default in init(from:). (templates/swift/models/model.mustache (init(from:), ~128-154))
- rust: Add #[serde(default = "default_<field>")] to required fields that have a defaultValue (the discriminator included); keep skip/serialise as is. (templates/rust/models/model.mustache)

**Tests.** No YAML: PetFood/DryFood/WetFood exist. In each ObjectSerializer test (or ComposedSchemaTest where the PetFood cases live) add rows: decode {"weightKg":2.5} as DryFood gives foodType "dry"; decode {"foodType":null,"weightKg":2.5} fails; decode {"foodType":"dry","weightKg":2.5} works. Python example today lives at python/test/test_object_serializer.py:933-963; go in go/test/composed_schema_test.go.

**Risk.** Loosens go, dart, swift and rust. If the owner prefers the strict reading, flip to 'reject' in the 8 accepting SDKs instead, which is the riskier change for shipped clients.

## D60 A default on a $ref'd enum (property-level sibling or on the enum component) becomes the typed enum member in all 12

- Ledger ids: F131 F171 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Nothing in the goldens exercises it: the petstore deliberately dropped the default (openapi.yaml:2016-2025). Reading the generators:
dart, elixir: emit the typed member reference (dart enum member, elixir atom).
java, kotlin, php, python: unaliasSchema keeps the $ref to an enum unchanged, getDefault() is null, the default is silently dropped (BetterJavaCodegen.java:614, BetterKotlinCodegen.java:539-543).
csharp, node, rust, swift, go, ruby: resolve through getReferencedSchema and pick the default up but emit it as a plain string literal; for an enum-typed field that is a type error in node ('medium' to an enum, TS2322), rust (String::from, E0308), swift ("medium"), csharp (bare token under the global:: prefix, model.mustache:491); go emits an untyped string constant (compiles), ruby/elixir come out right per F171 while the petstore comment says go/ruby/swift mis-render. Treat the per-language results as unverified until the YAML below renders.

**Canonical behaviour.** If an enum-typed property is a $ref to an enum component and a default exists, either as a sibling default on the property (OAS 3.1) or as default on the enum component, the property's default is that enum member in the language's idiom (Availability.ON_HOLD, DefaultsModeEnum.Medium style), applied when the key is absent, also in a default-constructed model; an explicit null stays null for nullable and is rejected otherwise. A property-level default wins over the component default. One shared resolution step in AbstractBetterCodegen (resolve via ModelUtils.getReferencedSchema, then map the wire value to the enum's enumVars) feeds every language, instead of eight private toDefaultValue implementations.

**Why.** (a) OAS: default describes what a consumer assumes when the value is absent; Defaults.mode already proves the inline form is supported in all 12, so the ref form should produce the identical result.

**Rejected.** Ignore defaults on $ref'd enums and document it: rejected, java/kotlin/php/python then disagree with dart/elixir about absent-key decoding and a default-constructed wire body (criterion c, no silent data change). Only support the component-level default: rejected, OAS 3.1 allows the sibling default and the petstore note names that form.

**Per language.**

- java: Resolve the default through getReferencedSchema and run the existing fixEnumDefaultValue member mapping for isEnumRef properties (field initializer Availability.ON_HOLD). (src/main/java/.../AbstractBetterCodegen.java:1702-1713,1980; java/BetterJavaCodegen.java:614,673)
- kotlin: Same shared step; toDefaultValue (BetterKotlinCodegen.java:539-543) handles only inline string enums today. Emit '= Availability.ON_HOLD'. (kotlin/BetterKotlinCodegen.java:529-573)
- csharp: Typed member instead of the bare token (model.mustache:491). (csharp/BetterCSharpCodegen.java:551-578; templates/csharp/models/model.mustache:491)
- php: Shared step; emit 'Availability::ON_HOLD' ctor default and property default (keep the typed-property rule from AGENTS). (php/BetterPHPCodegen.java:633,1128)
- python: Shared step; also fix fixEnumDefaultValue (BetterPythonCodegen.java:944) to guard on prop.isEnum so it does not prefix class names onto every default containing a dot. (python/BetterPythonCodegen.java:762,944)
- ruby: Verify the rendered default is the enum's wire value inside Types::String.enum(...).default(...); fix if it renders a constant name. (ruby/BetterRubyCodegen.java:749,1107,1190)
- node: Emit Availability.OnHold (typed member) rather than the quoted literal. (node/BetterNodeCodegen.java:552,623)
- dart: already canonical
- go: Emit the typed constant (AvailabilityOnHold) instead of an untyped string. (go/BetterGoCodegen.java:594)
- rust: Emit Some(Availability::OnHold) and keep the hand-written Default consistent. (rust/BetterRustCodegen.java:588,655)
- swift: Emit Availability(rawValue: "on-hold") like the inline enum case (Defaults.swift:31) or the member. (swift/BetterSwiftCodegen.java:507)
- elixir: already canonical

**Tests.** Needs the YAML below. In every ObjectSerializer test next to the existing Defaults 'absent vs explicit null' case (e.g. java ObjectSerializerTest.java:891, python test_object_serializer.py:628, node object-serializer.test.ts:605, go object_serializer_test.go:176): decode StockItem {"priority":1} and assert availability == the on-hold member; decode Defaults {} and assert swatch == green; default-construct both and assert the same. The generated model compiling is itself the check for node/rust/swift/csharp type errors.

**Petstore spec addition.**

```yaml
# components.schemas.StockItem.properties: replace the $ref-only entry
        availability:
          $ref: '#/components/schemas/Availability'
          default: on-hold
# components.schemas.Swatch: add a component-level default
    Swatch:
      type: string
      enum: [red, green, blue]
      default: green
# components.schemas.Defaults.properties: add
        swatch:
          $ref: '#/components/schemas/Swatch'
# also delete the stale 'honoring an enum-typed property default is a generator-wide gap' comment above Availability (openapi.yaml ~2016-2025)
```

**Risk.** Default-constructed models of such specs start serialising the default (same as inline enums today). Real clients with $ref enum defaults will see new fields on the wire; regenerate and diff the six clients.

## D61 Numeric default literals render as an exact, compiling literal of the target type in every language

- Ledger ids: F174 | Theme: models-validation-enums-defaults | Still true: **partly** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** java: handles L, F, BigDecimal("...") (BetterJavaCodegen.java:614-660): canonical.
kotlin: L suffix added for int64 but number maps to BigDecimal and float to Float, so 'BigDecimal? = 1.5' and 'Float? = 1.5' do not compile, and 'Double? = 2' does not either (BetterKotlinCodegen.java:529-562).
csharp: raw literal (BetterCSharpCodegen.java:551): 'decimal? = 1.5' is CS0664, float needs f.
rust: raw literal inside Some(...) (BetterRustCodegen.java:588): integral default on f64 gives Some(3), E0308.
node: raw literal (BetterNodeCodegen.java:552-571) for the branded Decimal string type: TS error.
python: raw literal, and fixEnumDefaultValue (BetterPythonCodegen.java:944) prefixes the class name onto any default containing '.', so default 1.5 becomes 'Defaults1.5' (no isEnum guard).
go, swift, dart, php, ruby, elixir: raw literal compiles for the cases the ledger lists (int literal widens to Double/float64/float), elixir float() with an integer default is a typespec mismatch only.
Only Defaults.retries (int32 = 3) is present in the petstore, so none of this renders.

**Canonical behaviour.** The generated default for integer, int64, float, double and format-less number properties is a literal of the field's exact type that equals the schema value: decimal-typed fields use an exact-text constructor from the string (BigDecimal("1.5"), 1.5m, Decimal('1.5'), a branded Decimal cast), float32 targets get the f/F suffix, floating-point targets always carry a decimal point (2 becomes 2.0 where the language will not widen an int literal), int64 gets L where required. A schema default never produces non-compiling or silently altered code.

**Why.** Criterion (a) schema default semantics; (b) java already does it; (d) per-language literal formatting only.

**Rejected.** Fall back to null (no default) for unsupported numeric types: rejected, loses the schema default that java honours and breaks the 'absent -> default' rule in AGENTS Round 5.

**Per language.**

- java: already canonical
- kotlin: float -> '1.5f'; number/decimal -> java.math.BigDecimal("1.5"); double -> ensure decimal point. (kotlin/BetterKotlinCodegen.java:529-562)
- csharp: decimal -> '1.5m'; float -> '1.5f'; double/long unchanged. (csharp/BetterCSharpCodegen.java:551)
- php: already canonical
- python: Guard fixEnumDefaultValue on prop.isEnum (and only prefix when the default is an enum member); emit Decimal('1.5') for Decimal-typed numbers. (python/BetterPythonCodegen.java:762,944)
- ruby: already canonical
- node: For the branded Decimal type emit a cast string literal ('1.5' as Decimal), other numbers verbatim. (node/BetterNodeCodegen.java:552-571)
- dart: already canonical
- go: already canonical
- rust: Floating-point targets always render with a decimal point (3 -> 3.0). (rust/BetterRustCodegen.java:588-600)
- swift: already canonical
- elixir: Render floating defaults with a decimal point (low priority, typespec only). (elixir/BetterElixirCodegen.java:471)

**Tests.** Needs the YAML below. The model compiling in every golden is the main gate (the build specs already compile all 12). In each ObjectSerializer test's Defaults case assert the default-constructed values: ratio 1.5, scale 0.25, weight 2.0, ceiling 9000000000.

**Petstore spec addition.**

```yaml
# components.schemas.Defaults.properties: add
        ratio:
          type: number
          default: 1.5
        scale:
          type: number
          format: float
          default: 0.25
        weight:
          type: number
          format: double
          default: 2
        ceiling:
          type: integer
          format: int64
          default: 9000000000
```

**Risk.** New Defaults members change every language's default-constructed wire body in tests; update the existing Defaults assertions.

## D62 format: uri keeps each language's type surface but has one wire rule: absolute RFC 3986 URI, any scheme, byte-verbatim

- Ledger ids: F132 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** python: UrlStr = TypeAdapter(HttpUrl) (_types.py:25,62): rejects urn:, mailto:, ftp:, relative; pydantic also normalises.
java: java.net.URI: rejects spaces, accepts relative references.
kotlin: io.ktor.http.Url(String): accepts almost anything, normalises output (ObjectSerializer.kt:803).
csharp: System.Uri (STJ): accepts relative, writes OriginalString.
php: \Uri\Rfc3986\Uri: parses relative references.
node: branded URI with only a typeof check (pet.ts:149).
dart, go, rust, swift, ruby, elixir: plain String, no validation (swift: Pet.swift:33).

**Canonical behaviour.** The per-language type surface for format: uri is intentional (petstore comment 'Type-surface exercise fields') and stays: URI/Url/Uri/branded string. Wire rule for all 12: decode accepts any absolute RFC 3986 URI (a scheme followed by ':' then no whitespace or control characters; http, ftp, mailto, urn all valid) and rejects relative references, empty or whitespace-containing strings with SerializationError; encode re-emits exactly the decoded text. FORCED exception: kotlin io.ktor.http.Url normalises some inputs (trailing '/' after an authority-only URL, documented at ObjectSerializer.kt:790-794) because Url keeps no original text. uri-reference and uri-template stay plain strings.

**Why.** (a) JSON Schema format uri = RFC 3986 URI with scheme; (c) python's http-only check wrongly rejects valid values, ktor normalisation is the only silent change and is forced.

**Rejected.** Make all 12 plain strings: rejected, public API break in java, kotlin, csharp, php, node, python and contradicts the fixture comment. Make all typed: rejected, six languages would gain a stdlib parse that normalises. Validate nothing: rejected, python would still be the odd one out and 'not a uri' would be accepted by half.

**Per language.**

- java: Add the scheme/whitespace check in a URI deserializer (URI.create alone accepts relative). (templates/java/object_serializer.mustache)
- kotlin: Add the check in KtorUrlSerializer.deserialize before Url(...); record the normalisation as a forced exception. (templates/kotlin/object_serializer.mustache:803)
- csharp: Add the check to the Uri converter (UriKind.Absolute). (templates/csharp/object_serializer.mustache)
- php: Reject relative references after Uri parse; emit toRawString() so output is verbatim. (templates/php/object_serializer.mustache)
- python: Replace the HttpUrl adapter with an AfterValidator on str: urlsplit, scheme present, no whitespace; keep the value as the original str. Update BetterPythonCodegen.java:144 mapping comment. (templates/python/_types.mustache:25-62; python/BetterPythonCodegen.java:144)
- ruby: Add the same check in the apply_format 'uri' hook (value stays a String). (templates/ruby/object_serializer.mustache)
- node: Replace the typeof check on the branded URI with the regex check in the constructor. (templates/node/models/model.mustache:~149)
- dart: Add the check in the model fromJson for uri-format fields. (templates/dart/models/model.mustache)
- go: Add the check in the model UnmarshalJSON (string field stays string). (templates/go/models/model.mustache)
- rust: Add a small serde deserialize_with check (no new crate). (templates/rust/models/model.mustache)
- swift: Add the check in the model init(from:). (templates/swift/models/model.mustache)
- elixir: Add a uri clause beside validate_uuid in convert_to_type. (templates/elixir/object_serializer.mustache)

**Tests.** No YAML: Pet.homepageUrl (format: uri), thumbnailRef (uri-reference) exist. Each ObjectSerializer test: accept "urn:isbn:0451450523", "mailto:a@b.com", "ftp://x.org/f", "https://example.com/pets/fido"; reject "/relative", "not a uri", ""; thumbnailRef still accepts "/assets/thumb-fido.png". Round-trip equality except kotlin authority-only inputs.

**Risk.** Low priority and low severity; ship after the integer, date-time and base64 decisions. Servers returning relative URIs in a format: uri field start failing in 12 SDKs.

## D63 additionalProperties key that collides with a declared property: the declared value wins and the key is written once

- Ledger ids: F138 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Declared wins: kotlin (ObjectSerializer.kt:336 if !merged.containsKey), python ('Declared fields win'), elixir (Map.merge(sanitized_extras, declared), object_serializer.ex:434).
Extra wins: php (ObjectSerializer.php:203), ruby (hash[json_key] = after declared loop), dart (json.addAll(additionalProperties), metadata.dart:40), swift (second keyed container overwrites).
Duplicate keys: java (@JsonAnyGetter after fields), csharp ([JsonExtensionData]), go (byte-splice of both objects, metadata.go:55-60), rust (#[serde(flatten)], metadata.rs:13-15).
node: extras are own properties, cannot collide.

**Canonical behaviour.** When a caller-built model has an additionalProperties entry whose key equals a declared property's wire name, serialisation writes the declared property's value and drops the colliding extra entry. No SDK ever emits duplicate JSON keys. (Decode never produces a collision.)

**Why.** (c) a typed field is authoritative; (d) filtering at serialisation time is possible in all 12; duplicate keys are undefined JSON behaviour.

**Rejected.** Reject at insertion time with an exception: rejected, rust's public additional_properties HashMap, go's map and kotlin's map are plain mutable collections the SDK cannot intercept. Extra wins: rejected, lets an untyped bucket override a typed, validated field.

**Per language.**

- java: Make the @JsonAnyGetter accessor return a copy without keys that equal declared JSON property names (generated static Set). (templates/java/models/model.mustache (JsonAnyGetter))
- kotlin: already canonical
- csharp: Filter colliding keys before write (IJsonOnSerializing swap to a filtered snapshot, restored in IJsonOnSerialized, or a model converter); this is the awkward one because STJ writes extension data after properties without a dedupe hook. (templates/csharp/models/model.mustache)
- php: Skip extras whose key is already in $normalized. (templates/php/object_serializer.mustache:203)
- python: already canonical
- ruby: Only assign extras for keys not already set by the declared loop. (templates/ruby/models/model.mustache; templates/ruby/object_serializer.mustache)
- node: already canonical
- dart: Replace json.addAll(additionalProperties) with a loop that skips keys already in json. (templates/dart/models/model.mustache)
- go: In MarshalJSON delete declared wire names from a copy of AdditionalProperties before marshalling extra. (templates/go/models/model.mustache (MarshalJSON))
- rust: Replace #[serde(flatten)] serialisation by serialize_with that filters declared names (flatten + serialize_with is supported), or a manual Serialize impl. (templates/rust/models/model.mustache)
- swift: In encode(to:) skip extra keys equal to a declared CodingKey raw value. (templates/swift/models/model.mustache)
- elixir: already canonical

**Tests.** No YAML: Metadata (createdAt + additionalProperties) exists. Add to every MetadataTest (java/models/MetadataTest.java, kotlin/.../models/MetadataTest.kt, csharp/Test/MetadataTest.cs, php/tests/MetadataTest.php, python/test/test_metadata.py, ruby/test/metadata_test.rb, node/test/metadata.test.ts, dart/test/metadata_test.dart, go/test/metadata_test.go, rust/tests/metadata_test.rs, swift/Tests/PetstoreClientTests/MetadataTests.swift, elixir/test/metadata_test.exs): set createdAt and additionalProperties["createdAt"]="x", serialise, assert exactly one createdAt key holding the declared value.

**Risk.** A caller who deliberately overrode a declared field through the bucket (accidentally working in php/ruby/dart/swift) loses that. Intentional.

## D64 Non-discriminated oneOf/anyOf variants are tried in declaration order in all 12

- Ledger ids: F139 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: False

**Current state.** Alphabetical TreeSet order (wrong): java (model.mustache:219,325; SetPetAvatarThumbnailRequest tries List<byte[]> before byte[], SetPetAvatarThumbnailRequest.java:25), kotlin, csharp (SetPetAvatarThumbnailRequest.cs:33), node (model.mustache:68,145), go (set_pet_avatar_thumbnail_request.go:24 [][]byte first), swift (model.mustache:398), elixir (set_pet_avatar_thumbnail_request.ex:18 [binary()] first).
Declaration order (right): python, php, ruby, rust (model.mustache:358), dart (iterate composedSchemas).
PetTreatment (Medication, Surgery) and the thumbnail pair are disjoint or coincidentally alphabetical so no test notices. The python template comment 'the other 11 SDKs all resolve it to the first declared variant' (python model.mustache:140) is false today and becomes true.

**Canonical behaviour.** For a oneOf/anyOf without discriminator, the trial order is the order the variants are declared in the schema (composedSchemas.oneOf/anyOf). oneOf: the first variant that decodes wins. anyOf: every matching variant is retained in declaration order and on a key collision during merge the later declared variant wins (as the existing 'Surgery is the later member' test already assumes). Templates must stop iterating {{#oneOf}}/{{#anyOf}} (a TreeSet, alphabetical) for trial lists.

**Why.** (a) JSON Schema oneOf/anyOf have no ordering, so the only stable and reviewable order is the author's; (b) five already do it; (d) composedSchemas is available to every template.

**Rejected.** Alphabetical everywhere: rejected, order would depend on class names rather than the spec and python/php/ruby/rust/dart would regress. Strict oneOf (error when more than one variant matches): rejected, changes semantics for 12 SDKs and the repo documents first-match behaviour.

**Per language.**

- java: Iterate composedSchemas.oneOf/anyOf (dataType) in the ONE_OF_SCHEMAS list and the anyOf trial list. (templates/java/models/model.mustache:219,325)
- kotlin: Same in the per-model build path. (templates/kotlin/models/model.mustache)
- csharp: Same in the deserialize trial list. (templates/csharp/models/model.mustache)
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: ONE_OF_SCHEMAS/ANY_OF_SCHEMAS from composedSchemas. (templates/node/models/model.mustache:68,145)
- dart: already canonical
- go: Trial loop over composedSchemas. (templates/go/models/model.mustache)
- rust: already canonical
- swift: Trial list from composedSchemas. (templates/swift/models/model.mustache:398)
- elixir: openapi_one_of/any_of list from composedSchemas. (templates/elixir/models/model.mustache)

**Tests.** Needs the YAML below. In every ComposedSchemaTest (java/.../models/ComposedSchemaTest.java, kotlin .../ComposedSchemaTest.kt, csharp/Test/ComposedSchemaTest.cs, php/tests/ComposedSchemaTest.php, python/test/test_composed_schema.py, ruby/test/composed_schema_test.rb, node/test/composed-schema.test.ts, dart/test/composed_schema_test.dart, go/test/composed_schema_test.go, rust/tests/composed_schema_test.rs, swift/.../ComposedSchemaTests.swift, elixir/test/composed_schema_test.exs): decode {"drugName":"a","procedureName":"b"} as TreatmentRecord and assert the Surgery variant is the first retained; merge a Surgery and Medication that both set notes and assert the later declared (Medication) wins. Existing PetTreatment tests keep their meaning (declared order happens to be alphabetical).

**Petstore spec addition.**

```yaml
# components.schemas: add (same members as PetTreatment, opposite, non-alphabetical order)
    TreatmentRecord:
      description: Same members as PetTreatment declared in non-alphabetical order
      anyOf:
        - $ref: '#/components/schemas/Surgery'
        - $ref: '#/components/schemas/Medication'
```

**Risk.** Ambiguous payloads in specs whose declaration order differs from alphabetical start resolving to a different variant in java, kotlin, csharp, node, go, swift, elixir; they now match python/php/ruby/rust/dart.

## D65 A null element in an array or map of models is rejected unless the element schema is nullable

- Ledger ids: F206 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Keep the null: java ([null, P], Jackson default), csharp List<Pet>, ruby (convert_to_type(nil) returns nil, object_serializer.rb:364), elixir, php (deserializeInternal(null) returns null).
Reject: kotlin List<Pet>, swift [Pet], rust Vec<Pet>, python TypeAdapter(list[Pet]), dart ('null as Map<String,dynamic>', object_serializer.dart:191), go (Pet.UnmarshalJSON on null reports the missing name).
Drop silently: node deserializeArray filters nulls for class-constructor element types (object-serializer.ts:752-754), shifting indices.

**Canonical behaviour.** Decoding [null, {...}] into an array (or map) of models raises SerializationError when the items schema is not nullable. No SDK keeps the null, none drops it, none leaves it to an incidental NPE later. Where the generated type can express a nullable element (kotlin List<Pet?>, swift [Pet?], rust Vec<Option<Pet>>, go []*Pet) a nullable items schema keeps null; dynamic languages (java, csharp, php, ruby, elixir, node, dart, python) cannot see element nullability and reject null model elements always (documented limitation, nullable-model-items is not in the petstore).

**Why.** (a) JSON Schema: null is not an object; (b) six already reject; (c) silent drop is the worst case.

**Rejected.** Keep nulls: rejected, violates the non-nullable items schema and hides server bugs. Drop nulls: rejected, silent data change and index shift. Full nullable-element modelling in dynamic languages: rejected as scope, no fixture needs it.

**Per language.**

- java: After decoding a container type, fail on null elements (post-pass over List/Set/Map values) or set Nulls.FAIL content-null config overrides on the mapper. (templates/java/object_serializer.mustache)
- kotlin: already canonical
- csharp: Post-pass rejecting null elements of List<T>/Dictionary<string,T> where T is a model. (templates/csharp/object_serializer.mustache)
- php: In the Ds\Vector/array/map element loops (ObjectSerializer.php ~455-480) throw when an element is null for a model-typed inner class. (templates/php/object_serializer.mustache)
- python: already canonical
- ruby: In the Array<...>/Hash branches of convert_to_type raise SerializationError when an element is nil and the inner type is a model. (templates/ruby/object_serializer.mustache:362-364)
- node: Replace the mapped.filter((x) => x !== null) with a throw of SerializationError for null model elements. (templates/node/object_serializer.mustache:586)
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: Raise on nil elements in the list/map clauses for model inner types. (templates/elixir/object_serializer.mustache)

**Tests.** No YAML: findPetsByStatus returns array<Pet>. In each ObjectSerializer test add: deserialize "[null,{\"name\":\"a\",\"photoUrls\":[]}]" as List<Pet> raises the SerializationError type (assert exact class); same for a map<string,Pet>.

**Risk.** Low. Servers returning null in model arrays start failing in java, csharp, php, ruby, elixir, node.

## D66 Required + nullable: the key must be present, null is a legal value, and null is written as null

- Ledger ids: F215 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: L | Wire-visible: True | Needs user: False

**Current state.** node: constructor accepts null and rejects only undefined (model.mustache:538); null then dropped on serialise.
elixir: nullable requireds are excluded from required_fields (model.mustache:399-401), so absent is accepted too.
kotlin: every required var is non-null (model.mustache:676 '{{^required}}?{{/required}}'): explicit null rejected.
rust (T not Option<T>), swift (non-optional let), python (name: dataType not Optional), dart ('as String'): explicit null rejected.
go: required vars are non-pointer with no omitempty (model.mustache:153): null silently becomes ""/0 and is re-sent as ""/0.
java: accepts null; serialisation drops it (JsonInclude NON_NULL, object_serializer.mustache:380).
csharp, php, ruby: not characterised by the ledger, verify (csharp WhenWritingNull, php and ruby skip null).
Every SDK's null-omission policy (NON_NULL, WhenWritingNull, exclude_none, encodeIfPresent, skip_serializing_if) drops a required key whose value is null. The only nullable property in the petstore (Defaults.label) is optional, so nothing is exercised.

**Canonical behaviour.** For a property that is both required and nullable: decode accepts an explicit null (value null), rejects an absent key with the canonical missing-required SerializationError; serialise writes the key with null when the value is null (never drops it); the type is nullable with no default so the caller must pass it. All other nullable (optional) properties keep omitting null. go keeps its zero-value limitation for non-pointer fields except that required+nullable becomes a pointer with no omitempty.

**Why.** (a) OAS 3.1: required constrains key presence, nullability constrains the value; they are independent.

**Rejected.** Keep omitting null for required+nullable and document it: rejected, sends an object that violates 'required' and strict servers answer 4xx. Reject null for required+nullable: rejected, contradicts nullable.

**Per language.**

- java: @JsonInclude(ALWAYS) on required+nullable fields, creator param required=true without requireNonNull. (templates/java/models/model.mustache)
- kotlin: Type T? without default, @Required, and an always-encode-null mechanism for that property (explicitNulls is Json-wide; use a per-property serializer or @EncodeDefault-style workaround, verify against the kotlinx version in use). (templates/kotlin/models/model.mustache:676)
- csharp: [JsonRequired] plus [JsonIgnore(Condition = JsonIgnoreCondition.Never)] on required+nullable properties; verify current output first. (templates/csharp/models/model.mustache; templates/csharp/required_guarded_property.mustache)
- php: Verify; ensure assertRequiredPresent treats key presence as satisfied by null and the normalizer keeps a null-valued required nullable key (skip_null_values off for these). (templates/php/object_serializer.mustache; templates/php/models/model.mustache)
- python: Optional[X] = Field(alias=...) with no default (required), and exclude_none must not drop it (model_serializer or per-field). (templates/python/models/model.mustache:370-381)
- ruby: Verify; required+nullable attribute is Types::X.optional (key required), serialise keeps nil. (templates/ruby/models/model.mustache)
- node: Constructor/deserialize: reject undefined, accept null (already true at :538); serialise: keep the key for null (instanceToPlain exposeUnsetFields / explicit null). (templates/node/models/model.mustache:538)
- dart: 'T? note' with 'required this.note'; fromJson: containsKey check then 'as T?'; toJson: json['note'] = note unconditionally. (templates/dart/models/model.mustache)
- go: Pointer type, json tag without omitempty, UnmarshalJSON presence check (key absent -> missing error, null -> nil). (templates/go/models/model.mustache:153)
- rust: Option<T> without skip_serializing_if and with a presence check (custom deserialize_with that distinguishes absent from null: use Option<Option<T>> helper or a required-key check). (templates/rust/models/model.mustache)
- swift: 'let note: String?'; decode(String?.self, forKey:) (throws keyNotFound when absent, nil on null); encode(note, forKey:) instead of encodeIfPresent. (templates/swift/models/model.mustache)
- elixir: Keep nullable requireds in required_fields as key-presence only (check Map.has_key?) and keep nil in the sanitised map. (templates/elixir/models/model.mustache:399-401)

**Tests.** Needs the YAML below. Each ObjectSerializer test: decode {"note":null} gives note null; decode {} fails with the missing-required error; serialise a model with note=null and assert exact bytes {"note":null}.

**Petstore spec addition.**

```yaml
# components.schemas: add
    RequiredNullable:
      type: object
      required: [note]
      properties:
        note:
          type: [string, "null"]
```

**Risk.** Touches the model type of every required+nullable property in real specs (public API in kotlin, rust, swift, python, dart, go). Check the Zitadel specs for such properties before regenerating clients.

## D67 format: uuid is written lowercase hyphenated and validated in both directions in all 12

- Ledger ids: F217 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** swift: Foundation.UUID with no custom handling; JSONEncoder and path/query stringification use uuidString, UPPERCASE.
java UUID, kotlin Uuid, csharp Guid, python uuid.UUID, go uuid.UUID, rust uuid::Uuid, php Symfony Uid (toRfc4122), dart UuidValue (toLowerCase, object_serializer.mustache:48): lowercase.
ruby, elixir: String; validate_uuid only on decode and returns the value unchanged (ruby object_serializer.rb:570, elixir:583); nothing validates or normalises on encode.
node: branded UUID string, caller's casing passes through.
Neither spec declares format: uuid.

**Canonical behaviour.** Encode (body, path, query, header, form) always emits the canonical lowercase 8-4-4-4-12 form (RFC 9562 section 4). Decode accepts any letter case and a malformed value raises SerializationError; string-typed languages normalise to lowercase on decode as well. A malformed caller value (string-typed languages) is rejected before dispatch.

**Why.** (a) RFC 9562 output lowercase, input case-insensitive; (b) 8 of 12 already lowercase; (d) trivial in swift via uuidString.lowercased().

**Rejected.** Verbatim caller string in the string-typed languages: rejected, produces different wire bytes for the same logical UUID. Uppercase: rejected, RFC 9562 requires lowercase output.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: Run validate_uuid + downcase in the serialise hook as well as decode. (templates/ruby/object_serializer.mustache:564,570)
- node: Validate and lowercase the branded UUID in the model constructor/serialize replacer and in value-serializer. (templates/node/models/model.mustache; templates/node/object_serializer.mustache; templates/node/value_serializer.mustache)
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: Encode UUID properties (and arrays/maps of them) via uuidString.lowercased() in the generated encode(to:), and use the same in the value serializer for path/query/header/form. (templates/swift/models/model.mustache (encode(to:)); templates/swift/value_serializer.mustache)
- elixir: Add a uuid clause to sanitize_for_serialization (validate, String.downcase) and downcase on decode. (templates/elixir/object_serializer.mustache:583)

**Tests.** Needs the YAML below. Each ObjectSerializer test: serialise Order with trackingId "E621E1F8-C36C-495A-93FC-0C247A3E6E5F" and assert the body carries the lowercase form; decode the uppercase form works and (string-typed) comes back lowercase; "not-a-uuid" is rejected on decode and before dispatch. Store API test: getOrderByTrackingId puts the lowercase value in the path.

**Petstore spec addition.**

```yaml
# components.schemas.Order.properties: add
        trackingId:
          type: string
          format: uuid
# paths: add
  /store/order/by-tracking/{trackingId}:
    get:
      tags: [store]
      operationId: getOrderByTrackingId
      security: []
      parameters:
        - name: trackingId
          in: path
          required: true
          schema: {type: string, format: uuid}
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: {$ref: '#/components/schemas/Order'}
```

**Risk.** Swift UUID bytes change case on the wire (servers should be case-insensitive).

## D68 A model missing required properties cannot be built or sent: java, node and rust must reject it before dispatch

- Ledger ids: F227 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: L | Wire-visible: False | Needs user: True

**Current state.** java: public Pet() {} (Pet.java:173) leaves name null, fields are public and non-final; NON_NULL drops the key, no client error. PetApiTest builds models with new Pet().
node: constructor(data?: Partial<Pet>) checks required fields only if (data !== undefined) (pet.ts:87), so new Pet() serialises to {}.
rust: #[derive(Default)] on Pet (pet.rs:27) gives name "" and an empty set, sent as present values; DryFood has a hand-written Default as well.
kotlin (non-default vals), csharp (required members, Pet.cs:111), php (required ctor args), python (ValidationError), ruby (Dry::Struct raises), dart (required named params), swift (memberwise init), elixir (@enforce_keys, pet.ex:32): cannot build it.
go: zero value, forced.

**Canonical behaviour.** Constructing a model without its required properties is impossible or raises before any request is dispatched, in every language except go, whose zero-value struct is the forced exception. java: no public no-arg constructor for models with required properties. node: a call to serialise/validate (and the constructor when data is supplied) rejects missing required properties; the no-arg constructor stays only because class-transformer plainToInstance needs it, so enforcement moves to a validate step in ObjectSerializer.serialize (FORCED, name class-transformer). rust: models with required fields do not derive or implement Default; construction goes through new(required...).

**Why.** (a) required; (b) eight languages enforce construction; (c) fails early rather than sending a partial body; (d) removable constructors and a validate hook need no dependency.

**Rejected.** Leave as is (status deferred in the ledger): rejected as the canonical because three SDKs send input the other eight refuse before dispatch. A looser variant that only adds the serialise-time check to java and rust: acceptable fallback if removing the constructors is too disruptive.

**Per language.**

- java: Do not emit the public no-arg constructor when the model has required vars (the @JsonCreator constructor already serves deserialisation); update PetApiTest and other golden tests to the creator constructor; optionally add a serialise-time requireNonNull pass for fields assigned null later. (templates/java/models/model.mustache:764; templates/java/test/*)
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: Add a generated validateRequired() and call it from ObjectSerializer.serialize and the API layer before dispatch; keep the constructor guard for supplied data. (templates/node/models/model.mustache:531; templates/node/object_serializer.mustache)
- dart: already canonical
- go: already canonical
- rust: Do not derive Default (or emit the hand-written Default impl) for models with required vars lacking defaults; keep new(...). (templates/rust/models/model.mustache:126)
- swift: already canonical
- elixir: already canonical

**Tests.** No YAML: Pet (required name, photoUrls). Each model-level test: attempt to build a Pet without name (java/rust: does not compile, so use a compile-fail note or assert the removed API; node: serialise(new Pet()) throws TypeError/SerializationError), plus the existing eight languages keep their construction-failure tests. Locate in the ObjectSerializer tests of node, and in model_test.go (go forced exception documented).

**Risk.** Public API breaks: java loses new Pet(), rust loses Pet::default(). This is the one decision here that is a product call (ergonomics of partial builders against failing fast); the ledger already deferred it. Recommendation: apply it.

## D69 uniqueItems arrays of scalars go on the wire sorted, so every SDK emits identical bytes on every run

- Ledger ids: F319 | Theme: models-validation-enums-defaults | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Unordered, nondeterministic or hash order: rust HashSet<String> with RandomState (pet.rs:38, differs between runs), swift Set<String> (Pet.swift:25, per-process seed), python Set[StrictStr] (pet.py:38, hash randomisation), elixir MapSet (enumerates sorted for small sets).
Insertion order: java LinkedHashSet, kotlin mutableSetOf, php Ds\Set, ruby Set, node Set, dart LinkedHashSet, go slice-backed Set[T]; csharp HashSet keeps insertion order only as an implementation detail.
So addPet bodies differ between languages and, in rust/swift/python, between runs (breaks signing, caching, byte-exact tests).

**Canonical behaviour.** A uniqueItems array whose elements are scalars (string, number, boolean, enum) is serialised in ascending natural order: numbers numerically, strings and enum wire values by Unicode code point. Elements are deduplicated by the set type. Arrays of models/objects keep the set's own order (documented as unspecified). The in-memory set types are unchanged (no public type change, no new dependency).

**Why.** (c) deterministic bytes; (d) sorting needs only the standard library; JSON arrays under uniqueItems are semantically sets, so reordering is not a data change.

**Rejected.** Insertion-ordered sets everywhere (rust IndexSet, swift OrderedSet, python list+dedupe): rejected, needs a new dependency in rust and swift (violates d) or changes the public type in four SDKs. Document nondeterminism as accepted: rejected, rust/swift/python are nondeterministic across runs.

**Per language.**

- java: Sort scalar-element sets in the set serializer registered for Set (custom JsonSerializer on Set/uniqueItems properties). (templates/java/object_serializer.mustache; templates/java/models/model.mustache)
- kotlin: Sort scalar sets in the Set serializer / before encode. (templates/kotlin/object_serializer.mustache)
- csharp: Sort on write via a HashSet<T> converter for scalar T (OrderBy ordinal). (templates/csharp/object_serializer.mustache)
- php: In sanitizeForSerialization sort a Ds\Set of scalars before converting to a list. (templates/php/object_serializer.mustache:~130-200)
- python: Add a field_serializer / PlainSerializer on Set[...] aliases returning sorted(list) for scalar members. (templates/python/models/model.mustache; templates/python/_types.mustache)
- ruby: sanitize_for_serialization: Set -> sort for scalar members. (templates/ruby/object_serializer.mustache)
- node: In the serialize replacer, Set -> sorted array for scalar members (code-point compare, not default UTF-16 sort for astral characters). (templates/node/object_serializer.mustache)
- dart: toJson: sorted list for Set of scalars. (templates/dart/models/model.mustache)
- go: Set[T].MarshalJSON sorts scalar members (cmp.Ordered). (templates/go/models/set.mustache)
- rust: serialize_with on HashSet fields that sorts scalar members into a Vec first (BTreeSet collect is fine for String/ints). (templates/rust/models/model.mustache)
- swift: encode(to:) writes set.sorted() for Comparable members. (templates/swift/models/model.mustache)
- elixir: sanitize_for_serialization: MapSet -> Enum.sort for scalar members (explicit, do not rely on small-map ordering). (templates/elixir/object_serializer.mustache)

**Tests.** No YAML: Pet.photoUrls (uniqueItems) exists. Each ObjectSerializer test: build a Pet with photoUrls inserted as ["c","a","b"], serialise, assert the body contains "photoUrls":["a","b","c"] (repeat in-process twice for rust/swift/python to guard run-to-run order).

**Risk.** The eight insertion-ordered SDKs now reorder on the wire; semantically a no-op for sets but visible in recorded fixtures.

## D70 3xx without Location, and the HTTPS->HTTP body-replay guard, already uniform

- Ledger ids: F088 F093 | Theme: redirect-proxy-tls-timeout | Still true: **no** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** java: post-loop 'Too many redirects' throw is now gated on redirectsRemaining <= 0 (DefaultApiClient.java:580), so a Location-less 3xx is returned.
kotlin/csharp/php/python/node/dart/rust/elixir: break on missing Location and gate budget exhaustion on hops/remaining (unchanged, canonical).
ruby: budget raise gated on 'hops >= max_redirects' (default_api_client.rb:254); body-replay guard now 'replays_body = [307,308].include?(status) && !next_body.to_s.empty?' (rb:215).
go: CheckRedirect uses 'hasBody := req.Body != nil && req.Body != http.NoBody' (default_api_client.go:381), so a bodyless GET or empty POST 307 https->http is followed.
swift: refusesBodyReplay ignores the explicit empty Data() (DefaultApiClient.swift:1260).
elixir: refuse_body_replay? = status in [307,308] and body not in [nil,""] and downgrade? (default_api_client.ex:1020).

**Canonical behaviour.** A 3xx with no usable Location is a legitimate terminal response and is returned unchanged; 'too many redirects' is raised only when the hop budget is exhausted while a Location is still present. The https->http body-replay refusal fires only for 307/308 with a non-empty body. No change needed.

**Why.** Both findings are fixed in the current goldens; all 12 now agree. Do not re-open.

**Rejected.** None.

**Per language.**

- all 12: already canonical

**Tests.** The no-Location case is locked by the new /test/redirect/no-location row listed under the malformed-Location decision (F089). The https->http empty-body cases need a TLS-to-plain-HTTP hop that chasm cannot serve, so they stay in each language's existing default_api_client unit test (java DefaultApiClientUnitTest, go default_api_client_unit_test.go, ruby default_api_client_unit_test.rb, swift DefaultApiClientUnitTests, elixir default_api_client_unit_test.exs).

## D71 An unusable (unparseable) Location behaves exactly like a missing Location: return the 3xx

- Ledger ids: F089 | Theme: redirect-proxy-tls-timeout | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: None

**Current state.** java: IllegalArgumentException from URI.resolve escapes the redirect loop (DefaultApiClient.java:490 resolveLocation -> base.resolve; verified 'Illegal character in authority').
kotlin: same IAE from resolveRedirectLocation (DefaultApiClient.kt:198), and the 3xx body was already consumed by bodyAsBytes() at :192.
csharp: Headers.Location is null for an invalid value so the loop breaks (canonical), but new Uri(currentUrl, rel) at DefaultApiClient.cs:455 can still throw UriFormatException.
php: resolveUrl() returns null on parse failure and the loop breaks (AbstractApiClient.php:192).
python: urlsplit('http://[bad') raises ValueError('Invalid IPv6 URL') inside _resolve_url (default_api_client.py ~720), uncaught; lenient values are forwarded raw.
ruby: resolve_url rescues to nil and breaks, returning the 3xx (rb:501).
node: new URL(location, currentUrl) throws TypeError (abstract-api-client.ts:335) which the catch maps to NetworkError status 0, and discardBody() already ran.
dart: Uri.resolve FormatException lands in the generic catch -> ApiException(statusCode: 0) (abstract_api_client.dart:271); the 3xx body is drained before the Location check (:256) so even the missing-Location break reads a consumed stream.
go: net/http returns *url.Error 'failed to parse Location header' with resp == nil -> NetworkError status 0.
rust: Url::join(..).ok() -> None -> break, returns the 3xx (default_api_client.rs:295).
swift: URLSession owns Location parsing; Apple platforms complete with the 3xx and never call the redirect delegate (not verified on corelibs).
elixir: resolve_url rescues to nil and returns the response (default_api_client.ex:977).

**Canonical behaviour.** Location is 'usable' only if it is non-empty and the platform's RFC 3986 reference resolver accepts it against the current URL. If not, stop the loop and return the 3xx untouched (status, headers, body), exactly like a missing Location; BaseApi then maps it to the ApiError for that status. Order matters: resolve the Location BEFORE draining/cancelling the 3xx body, so the returned response still has its body. A resolvable-but-forbidden scheme (file:, javascript:) stays a typed ApiError carrying the 3xx status, as today in all 12.

**Why.** By the error contract a response arrived, so the outcome must be an ApiError/3xx carrying that status, never NetworkError(0) (node, go, dart) nor a leaked library exception (java, kotlin, python). Returning the 3xx matches the 4 SDKs that already do it (php, rust, elixir, ruby) and the missing-Location rule fixed in F088, needs no new error type, and is the safest: nothing is followed, nothing is guessed. Empty Location is folded in because java/kotlin/php/csharp/rust currently resolve '' to the same URL and burn the whole hop budget ('Too many redirects'), whereas node/ruby/dart/python/go already treat it as missing.

**Rejected.** Raise ApiError(3xx) for a malformed Location. Rejected: it loses the body/headers, needs a new message in 12 languages, and the 4 SDKs that already return the 3xx would have to change. Leave go as NetworkError and document it as forced. Rejected as a default because a thin RoundTripper can reproduce the behaviour; kept only as the fallback if the maintainers refuse the wrapper.

**Per language.**

- java: Wrap resolveLocation(...) at :490 in try/catch IllegalArgumentException and break; treat an empty string like null at :485. (src/main/resources/templates/java/default_api_client.mustache)
- kotlin: Call resolveRedirectLocation inside try/catch (IllegalArgumentException, URISyntaxException) BEFORE response.bodyAsBytes(); break on failure or empty location. (src/main/resources/templates/kotlin/default_api_client.mustache)
- csharp: Replace new Uri(currentUrl, Location) with Uri.TryCreate(currentUrl, location, out next); break when false or when the header is empty. (src/main/resources/templates/csharp/default_api_client.mustache)
- php: already canonical
- python: Make _resolve_url total: try resolve, then validate with urllib3.util.parse_url(next_url); on ValueError/LocationParseError break out of the loop instead of raising. (src/main/resources/templates/python/default_api_client.mustache)
- ruby: already canonical
- node: Resolve new URL(location, currentUrl) in try/catch before AbstractApiClient.discardBody(response); break on failure. (src/main/resources/templates/node/abstract_api_client.mustache)
- dart: Resolve the Location in try/on FormatException before the drain; only call _drainWithin once a redirect will actually be followed, so a break leaves the body readable by the _readWithin at :383. Verify the existing missing-Location path has the same stream-consumed problem. (src/main/resources/templates/dart/abstract_api_client.mustache)
- go: Wrap the *http.Transport in a small RoundTripper that, for 301/302/303/307/308, drops a Location header that req.URL.Parse rejects so net/http returns the 3xx instead of a *url.Error; keep a reference to the inner *http.Transport so Close() still calls CloseIdleConnections. (src/main/resources/templates/go/default_api_client.mustache)
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Per language, in the existing redirect block of the client spec: java DefaultApiClientTest 'redirect handling' (:354), kotlin DefaultApiClientTest.kt, csharp Test/DefaultApiClientTest.cs, php tests/DefaultApiClientTest.php, python test/test_default_api_client.py, ruby test/default_api_client_test.rb, node test/default-api-client.test.ts, dart test/default_api_client_test.dart, go test/default_api_client_test.go, rust tests/default_api_client_test.rs, swift DefaultApiClientTests.swift, elixir test/default_api_client_test.exs. Two rows with followRedirects=true: GET chasm+/test/redirect/malformed-location and /test/redirect/no-location must each return status 302 (no exception) with the marker body. This is the 'one it.each row per client spec' the finding asks for.

**Petstore spec addition.**

```yaml
  /test/redirect/no-location:
    get: { operationId: testRedirectNoLocation, x-internal: true, tags: [test], security: [], responses: { '302': { description: Found without a Location header, content: { application/json: { schema: { type: object }, example: { redirect: no-location } } } } } }
  /test/redirect/malformed-location:
    get: { operationId: testRedirectMalformedLocation, x-internal: true, tags: [test], security: [], responses: { '302': { description: Found with an unparseable Location, headers: { Location: { schema: { type: string }, example: 'http://[bad' } }, content: { application/json: { schema: { type: object }, example: { redirect: malformed } } } } } }
```

**Risk.** go changes the returned 3xx (Location header removed) and adds a RoundTripper layer; dart's stream-consumption fix touches the common redirect path. Treating '' as missing changes java/kotlin/csharp/php/rust, which today follow it back to the same URL until 'Too many redirects'. Chasm must emit a header value of 'http://[bad' verbatim; if it rejects it, drive that row from the existing in-process stub servers instead.

## D72 caCertPath is a pin: it replaces the system trust store (and hostname checks still apply)

- Ledger ids: F020 | Theme: redirect-proxy-tls-timeout | Still true: **partly** | Effort: S | Wire-visible: True | Needs user: None

**Current state.** java: fresh KeyStore holding only the CA bundle (buildPinnedSslContext, DefaultApiClient.java:~262-310). Replaces.
kotlin: loadCaCertTrustManager builds a store of only the CA certs (HttpClientFactory.kt:~127-155). Replaces.
csharp: X509ChainTrustMode.CustomRootTrust (DefaultApiClient.cs:197) so the chain is pinned, BUT the callback '(_, cert, _, _) => customChain.Build(cert)' discards SslPolicyErrors, so the host name is never checked: any certificate issued by the pinned CA is accepted for any host. Real defect.
php: curl cafile only (DefaultApiClient.php:103); libcurl may still add its compiled-in CApath on some distros (verify).
python: ca_certs + CERT_REQUIRED, urllib3 then does not load defaults (default_api_client.py:279). Replaces.
ruby: FIXED, cert_store = OpenSSL::X509::Store.new so ca_file is the only anchor (default_api_client.rb:~470).
node: undici connect 'ca' (default-api-client.ts:~101). Replaces.
dart: FIXED, SecurityContext(withTrustedRoots: !pinned) (default_api_client_io.dart:63-65).
go: fresh x509.NewCertPool() as RootCAs (transport_options.go, buildHTTPClient). Replaces.
rust: FIXED, tls_built_in_root_certs(false) before add_root_certificate (default_api_client.rs:~566).
swift: SecTrustSetAnchorCertificatesOnly (DefaultApiClient.swift:~1040) on Apple; Linux refuses with tlsOptionsUnsupported.
elixir: transport_opts cacertfile with verify_peer (default_api_client.ex:~710). Replaces.
The doc comment still says 'in addition to (or instead of) the system trust store' in java TransportOptions.java:89, kotlin TransportOptions.kt:31, csharp TransportOptions.cs:43, python transport_options.py:64, go transport_options.go:97, swift TransportOptions.swift:38, elixir transport_options.ex:33, ruby transport_options.rb:33; only dart (transport_options.dart:35) and rust (transport_options.rs:41) were reworded.

**Canonical behaviour.** When caCertPath is set (and verifySsl is true) the client trusts only certificates that chain to the certificates in that file; the system/native roots are not consulted; host name verification still applies. Every TransportOptions doc says exactly that ('replaces the system trust store; to trust both, put the system bundle in the file'). Swift on Linux stays a documented FORCED refusal (corelibs surfaces no trust challenge).

**Why.** The code divergence in dart/rust/ruby is already gone, so the remaining harm is the contradictory doc and one real hole. Pin semantics are what 12/12 now implement, what java's own Javadoc and dart's comment call 'TLS pinning', and the only safe reading: additive trust would let any publicly-trusted certificate (a MITM) through when the caller configured a private CA. The csharp hostname gap violates 'curl -k only when verifySsl=false' and is a silent security weakening.

**Rejected.** Additive trust (system roots + CA). Rejected: weaker, would need a new dependency or platform-specific code in java/kotlin/go/swift/csharp, and the 9 SDKs that pin would all change. A separate option for additive mode: out of scope, no demand.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: Use the callback's 4th argument: return false when (sslPolicyErrors & SslPolicyErrors.RemoteCertificateNameMismatch) != 0 before/after customChain.Build(cert); reword the XML doc at TransportOptions.cs:43. (src/main/resources/templates/csharp/default_api_client.mustache, src/main/resources/templates/csharp/transport_options.mustache)
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Add one hostname row to each existing custom-CA integration test (csharp DefaultApiClientTest.cs:46 MakesHttpsRequestWithCustomCaCert and the equivalents in the other 11 default_api_client tests): caCertPath = the fixture CA, URL = https://127.0.0.1:<mapped 8443> (the chasm cert has no IP SAN, the same trick the existing 'verifySsl=true rejects hostname mismatch' test at java DefaultApiClientTest.java:~66 already uses) must raise NetworkError. That row fails today in csharp. 'Not additive' cannot be shown offline, so assert the trust set directly where it is inspectable: go default_api_client_unit_test.go (RootCAs holds only the file's certs), java DefaultApiClientUnitTest (buildPinnedTrustStore size), ruby default_api_client_unit_test.rb (connection.ssl.cert_store empty and ca_file set), rust default_api_client_unit_test.rs (builder config).

**Risk.** The csharp fix starts rejecting a CA-signed cert for the wrong host; that is the intended behaviour but could surface in a client that pointed at a mismatched host.

## D73 Validate caCertPath whenever it is set, even with verifySsl=false

- Ledger ids: F295 | Theme: redirect-proxy-tls-timeout | Still true: **yes** | Effort: S | Wire-visible: False | Needs user: None

**Current state.** java: CA read only in 'else if (getCaCertPath() != null)' after !verifySsl (DefaultApiClient.java:232), so a bad path is ignored.
kotlin: same shape in HttpClientFactory.kt:~97-102.
csharp: 'else if (transportOptions.CaCertPath != null)' (DefaultApiClient.cs:~163), ignored.
php: 'elseif (caCertPath !== null)' (DefaultApiClient.php:81), ignored.
python: 'elif transport_options.ca_cert_path' (default_api_client.py:265), ignored.
ruby: validate_ca_cert_path in initialize regardless of verify_ssl (rb:97). node: eager read + X509Certificate in buildDispatcher (default-api-client.ts:~70-95). elixir: validate_ca_cert_path(opts.ca_cert_path) (ex:59). swift: validateCaCertPath in init (DefaultApiClient.swift:~92). dart: setTrustedCertificates before the verifySsl branch (default_api_client_io.dart:~66-80). go and rust: file read in TransportOptions Build.
So 7 reject, 5 accept.

**Canonical behaviour.** A caCertPath that is set must be readable and contain at least one certificate, otherwise the caller-mistake error of the language is raised at construction, irrespective of verifySsl. With verifySsl=false the CA is validated but not installed.

**Why.** Majority (7/12) and AGENTS 'missing CA file is a caller mistake'. A config that sets both a CA and verifySsl=false is contradictory, and failing early is safer than silently ignoring a typo that would later take effect when someone flips verifySsl back on. Cost to callers is a stricter constructor for an already-odd configuration.

**Rejected.** Ignore the CA whenever verification is off (curl -k style). Rejected: needs 7 SDKs to stop validating, hides typos, and contradicts the fail-fast comments already in every template.

**Per language.**

- java: Hoist buildPinnedSslContext(path) out of the else-if: if (caCertPath != null) { ctx = build...; } then install ctx only when verifySsl. (src/main/resources/templates/java/default_api_client.mustache)
- kotlin: Call loadCaCertTrustManager before the verifySsl branch and use the result only when verifySsl is true. (src/main/resources/templates/kotlin/http_client_factory_jvm.mustache)
- csharp: Move the ImportFromPemFile + empty-collection check above the VerifySsl if/else. (src/main/resources/templates/csharp/default_api_client.mustache)
- php: Move the file_get_contents/openssl_x509_read block above the verifySsl branch; set cafile only when verifySsl. (src/main/resources/templates/php/default_api_client.mustache)
- python: Hoist the ssl.create_default_context().load_verify_locations validation out of the elif. (src/main/resources/templates/python/default_api_client.mustache)
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Next to the existing 'missing CA file' test in each language's unit/transport test (java DefaultApiClientUnitTest, kotlin DefaultApiClientUnitTest, csharp DefaultApiClientUnitTest.cs, php DefaultApiClientUnitTest.php, python test_default_api_client_unit.py, ruby default_api_client_unit_test.rb, node default-api-client-unit.test.ts, dart default_api_client_unit_test.dart, go transport_options_test.go, rust transport_options_test.rs, swift DefaultApiClientUnitTests, elixir default_api_client_unit_test.exs) add the verifySsl=false twin of each existing row (missing path and garbage file) expecting the same configuration error. No petstore YAML needed.

**Risk.** Callers that currently pass a stale caCertPath together with verifySsl=false will start failing at construction.

## D74 Ambient proxy settings (env / JVM properties / system settings) are ignored: no proxy unless TransportOptions.proxy is set

- Ledger ids: F090 | Theme: redirect-proxy-tls-timeout | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: None

**Current state.** java: HttpClient with no ProxySelector uses the JDK default selector, which reads the JVM properties http(s).proxyHost/proxyPort/nonProxyHosts (not env); the finding missed this (DefaultApiClient.java:~213-219 only sets one when proxy != null).
kotlin: Ktor CIO, no proxy unless configured (HttpClientFactory.kt:~95). Direct.
csharp: HttpClientHandler default UseProxy=true with HttpClient.DefaultProxy (env on Linux, system on Windows); only the explicit-proxy branch touches it (DefaultApiClient.cs:224). Honours ambient proxy.
php: Symfony HttpClient reads http_proxy/https_proxy/no_proxy when 'proxy' is unset (DefaultApiClient.php:106-108). Honours env.
python: urllib3 PoolManager, no env lookup. Direct.
ruby: Faraday reads env (f.proxy only assigned when set, rb:466) and Net::HTTP.new defaults to proxy_from_env (verified in ruby: proxy? is true with http_proxy set). Honours env.
node: undici fetch, no env lookup unless the host process opts in via NODE_USE_ENV_PROXY / a global dispatcher. Direct by default.
dart: no findProxy is set when proxy == null (default_api_client_io.dart:~93); dart:io may fall back to http_proxy/https_proxy env (verify). Treat as unsafe until pinned.
go: bare http.Transport with no Proxy field (default_api_client.go:~273); the comment there already says 'no environment proxy'. Direct.
rust: reqwest ClientBuilder::new() keeps system/env proxies on (default_api_client.rs:547). Honours env.
swift: URLSession.default uses system proxy settings on Apple, libcurl env on Linux (proxy unsupported there). Honours ambient proxy.
elixir: Req/Finch/Mint have no env proxy lookup; confirm against the pinned Req version.

**Canonical behaviour.** TransportOptions.proxy is the only proxy source. When it is null the SDK connects directly and ignores HTTP(S)_PROXY, ALL_PROXY, NO_PROXY, JVM proxy properties and OS proxy settings. This is the documented contract ('null for direct connections'). Swift on Linux is the one FORCED exception (swift-corelibs gives no switch for libcurl's env proxy); document it next to proxyUnsupported.

**Why.** Documented rule (TransportOptions.java:103 'null for direct connections') and the majority of the unambiguous SDKs (kotlin, python, node, go, elixir). Silent rerouting through an ambient proxy is the riskier failure: traffic (and bearer tokens) go to a middlebox the caller never configured, and behaviour differs per host. The explicit option already exists, so callers who need a proxy lose nothing. Every language can disable it without a dependency.

**Rejected.** Honour the environment everywhere (undici EnvHttpProxyAgent, Ktor/okhttp env, urllib3 ProxyManager from env, go ProxyFromEnvironment). Rejected: five SDKs would need new code and tests, NO_PROXY semantics differ per library, and it contradicts the documented 'null = direct'. It is defensible (curl/requests do it) so it is the one place this decision could be revisited if users in proxied networks complain; hence the README must say 'pass proxy explicitly'.

**Per language.**

- java: When proxy == null call builder.proxy(ProxySelector.of(null)) (disables proxying; verify on the CI JDK). (src/main/resources/templates/java/default_api_client.mustache)
- kotlin: already canonical
- csharp: Set handler.UseProxy = false when transportOptions.Proxy is null (the explicit branch already sets handler.Proxy and UseProxy = true). (src/main/resources/templates/csharp/default_api_client.mustache)
- php: Add $options['no_proxy'] = '*' when transportOptions->proxy is null (Symfony treats '*' as bypass; verify with the dead-proxy test). (src/main/resources/templates/php/default_api_client.mustache)
- python: already canonical
- ruby: Assign f.proxy unconditionally (nil when unset, which sets Faraday's manual-proxy flag) and in the adapter block set http.proxy_from_env = false (the writer exists in Net::HTTP). (src/main/resources/templates/ruby/default_api_client.mustache)
- node: already canonical
- dart: When proxy == null set ioClient.findProxy = (_) => 'DIRECT' (harmless if dart:io is already direct). (src/main/resources/templates/dart/default_api_client_io.mustache)
- go: already canonical
- rust: Call builder = builder.no_proxy() right after ClientBuilder::new(), before the explicit .proxy(..), so only the option proxy remains. (src/main/resources/templates/rust/default_api_client.mustache)
- swift: On Apple set config.connectionProxyDictionary = [:] when opts.proxy is nil (disables system proxies; verify). On Linux document the FORCED exception. (src/main/resources/templates/swift/default_api_client.mustache)
- elixir: already canonical

**Tests.** One row in each existing client integration test (the file that holds the squid proxy tests, e.g. java BaseApiTest ProxyAuthenticationTests / DefaultApiClientTest, csharp DefaultApiClientTest.cs:98-114, dart default_api_client_test.dart:80-99): set the ambient proxy to a dead address (HTTP_PROXY/HTTPS_PROXY/http_proxy/https_proxy=http://127.0.0.1:1; for java System.setProperty http.proxyHost=127.0.0.1, http.proxyPort=1), build a client with default TransportOptions, GET chasm /test/echo and expect 200. Env is cached at first use in csharp/swift/php-cli, so set it in the test-runner environment for those three (runsettings / scheme / phpunit.xml <server>). No YAML.

**Risk.** Behaviour change for rust, csharp, ruby, php, swift, java and possibly dart users who rely on ambient proxies; README/Migration note required. The dead-proxy test is the only thing that proves the per-library switches (php no_proxy '*', java ProxySelector.of(null), swift [:]) actually work.

## D75 The SDK's own Proxy-Authorization survives redirect hops (strip removes only caller credentials)

- Ledger ids: F091 | Theme: redirect-proxy-tls-timeout | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: None

**Current state.** java: proxyAuthHeader put into mergedHeaders (DefaultApiClient.java:459-460), then redirect headers are a copy of those and a cross-origin hop removes 'proxy-authorization' via SENSITIVE_HEADER_NAMES (:~530-535), so the redirected request reaches the proxy with no credentials -> 407.
kotlin: same (DefaultApiClient.kt:155-156 injected, 'proxy-authorization' in the strip set at :50).
swift: header injected at DefaultApiClient.swift:279-280, stripped in the willPerformHTTPRedirection delegate (:~1170, sensitiveRedirectHeaders :1283). 407 after cross-origin hop.
dart: injected in abstract_api_client.dart:155-157, 'proxy-authorization' in the cross-origin strip set (:243); dart-io can recover through addProxyCredentials.
python: FIXED, proxy_headers on the ProxyManager are never touched (default_api_client.py:~590 docstring item 3).
csharp (WebProxy.Credentials), ruby (Faraday proxy user), php (Symfony proxy), go (Transport.Proxy userinfo), rust (reqwest Proxy), node (ProxyAgent), elixir (Mint proxy_headers, default_api_client.ex:~675): credentials live in the proxy layer and apply to every hop.

**Canonical behaviour.** Proxy credentials authenticate to the proxy, which is the same for every hop, so they belong to the transport, not to the caller's header set. The cross-origin strip removes only caller-supplied credential headers. In the four SDKs that inject Proxy-Authorization as a request header (java, kotlin, swift, dart) the SDK re-applies its own proxyAuthHeader to every redirected request after the strip, with one guard: on a cross-origin hop whose target is https it is NOT re-added (there the header would travel to an unrelated origin inside the TLS tunnel).

**Why.** Eight SDKs already authenticate every hop; 407-after-redirect is a functional bug and the finding's fix is local. The https cross-origin guard keeps the fix from handing proxy credentials to an attacker-chosen host on a malicious 302, which would be a silent security weakening.

**Rejected.** Move credentials to the proxy layer in java/kotlin/swift (java.net.http Authenticator of type PROXY, URLSession proxy challenge). Rejected for now: java needs the JVM-wide jdk.http.auth.tunneling.disabledSchemes property for Basic over CONNECT and Ktor CIO has no proxy-credential API; revisit if header injection proves insufficient (it already cannot authenticate the CONNECT of an https origin).

**Per language.**

- java: After the removeIf at ~:530, if (proxyAuthHeader != null && (sameOrigin || "http".equalsIgnoreCase(redirectUri.getScheme()))) redirectHeaders.put("Proxy-Authorization", proxyAuthHeader). (src/main/resources/templates/java/default_api_client.mustache)
- kotlin: Same re-add after the redirectHeaders strip (before the request is built at ~:290). (src/main/resources/templates/kotlin/default_api_client.mustache)
- swift: In the redirect delegate after the strip loop, redirectRequest.setValue(proxyAuthHeader, forHTTPHeaderField: "Proxy-Authorization") under the same guard; the delegate needs the header value passed into SessionDelegate.init. (src/main/resources/templates/swift/default_api_client.mustache)
- dart: After building redirectHeaders (abstract_api_client.dart:~320-333) put _proxyAuthHeader back under the same guard. (src/main/resources/templates/dart/abstract_api_client.mustache)
- python: already canonical
- csharp: already canonical
- ruby: already canonical
- php: already canonical
- node: already canonical
- go: already canonical
- rust: already canonical
- elixir: already canonical

**Tests.** Use the in-process stub servers already present in the unit tests (java DefaultApiClientUnitTest, kotlin DefaultApiClientUnitTest, swift DefaultApiClientUnitTests, dart default_api_client_unit_test.dart, python test_default_api_client_unit.py, node default-api-client-unit.test.ts, go default_api_client_unit_test.go), acting as the forward proxy: client proxy = stub with user:pass, request http://a.test/x, stub answers 302 Location: http://b.test/y when it sees Proxy-Authorization, and asserts the second request (absolute-URI target b.test) also carries the same Proxy-Authorization and does NOT carry a caller-supplied Authorization. No second host is needed because the stub is the proxy. Add the same row for the 8 already-canonical SDKs so the property is locked everywhere. No petstore YAML (squid cannot be told to redirect cross-origin).

**Risk.** Header-injected proxy auth on https origins is a pre-existing weakness (the CONNECT is unauthenticated and the header may be forwarded in-tunnel); this decision does not widen it but does not fix it either.

## D76 timeout is one end-to-end budget for the whole call: all hops plus the body read

- Ledger ids: F094 F407 | Theme: redirect-proxy-tls-timeout | Still true: **yes** | Effort: L | Wire-visible: True | Needs user: None

**Current state.** go: http.Client.Timeout covers every hop and the body (default_api_client.go:315). Whole chain.
node: one AbortSignal.timeout spread into every hop and the body read (abstract-api-client.ts:296, redirectOptions :425). Whole chain.
swift: timeoutIntervalForResource is task-wide (DefaultApiClient.swift:900). Whole chain.
dart: FIXED, a Stopwatch clock with _remaining() is threaded through _sendWithin/_drainWithin/_readWithin (abstract_api_client.dart:229, 484-559). Whole chain (the ledger entry is stale for dart).
java: HttpRequest.timeout re-armed per hop (DefaultApiClient.java:454 and redirectBuilder :562). Per hop.
kotlin: Ktor HttpTimeout installed per request (HttpClientFactory.kt:111-115). Per hop.
csharp: HttpClient.Timeout per SendAsync (DefaultApiClient.cs:244). Per hop.
php: max_duration per request (DefaultApiClient.php:122). Per hop.
python: urllib3.Timeout(total) rebuilt once and reused per pool request (default_api_client.py:414). Per hop.
ruby: Faraday open/read/write timeout per run_request (rb:476-486). Per hop.
rust: reqwest ClientBuilder::timeout per send (default_api_client.rs:590). Per hop.
elixir: fresh Task.yield(task, total) per recursive hop (default_api_client.ex:797-799). Per hop.

**Canonical behaviour.** timeout is an end-to-end budget measured from the start of sendRequest: connect, TLS, request, every redirect hop, and the response body read all draw from it. Each hop is sent with the remaining budget; if nothing is left before a hop starts, raise the language's NetworkTimeout error (status 0) without sending it. This is the existing documented meaning ('End-to-end request timeout ... entire request lifecycle').

**Why.** (a) The TransportOptions doc in all 12 already says end-to-end; (b) four SDKs already behave so; (c) the per-hop reading allows N*timeout (20 redirects x 10 s = 200 s by default), a silent loosening of the caller's bound; (d) go and swift cannot be per-hop without abandoning Client.Timeout / URLSession's resource timer, while the manual-loop SDKs can all carry a deadline without a dependency.

**Rejected.** Per-hop timeout everywhere. Rejected: contradicts the docs, go/swift/node would need manual redirect loops or per-hop cancellation, and it leaves an unbounded worst case.

**Per language.**

- java: Compute a System.nanoTime() deadline before the first send; before each hop set redirectBuilder.timeout(Duration.ofNanos(remaining)) and throw the same NetworkTimeoutException used for HttpTimeoutException when remaining <= 0. (src/main/resources/templates/java/default_api_client.mustache)
- kotlin: Keep a monotonic deadline in the redirect loop and pass a per-request timeout { requestTimeoutMillis = connectTimeoutMillis = socketTimeoutMillis = remaining } to httpClient.request; NetworkTimeoutException when remaining <= 0. (src/main/resources/templates/kotlin/default_api_client.mustache)
- csharp: One CancellationTokenSource(timeout) per SendRequestAsync (linked with the caller token) used for every hop and the body read; set HttpClient.Timeout = Timeout.InfiniteTimeSpan; map 'our CTS fired and the caller token did not' to NetworkTimeoutException (the current mapping at :626/:671 keys on InnerException is TimeoutException). (src/main/resources/templates/csharp/default_api_client.mustache)
- php: hrtime() deadline in the redirect loop; per request pass ['timeout' => remaining, 'max_duration' => remaining]; NetworkTimeoutException when <= 0. (src/main/resources/templates/php/abstract_api_client.mustache, src/main/resources/templates/php/default_api_client.mustache)
- python: time.monotonic() deadline captured in send_request and passed into the redirect loop; rebuild request_kwargs['timeout'] = urllib3.Timeout(total=remaining) per hop; NetworkTimeoutException when <= 0. urllib3 read timeouts stay per socket op (library limit; document). (src/main/resources/templates/python/default_api_client.mustache)
- ruby: Monotonic deadline in the redirect loop; pass per-request options in the run_request block (timeout/open_timeout/write_timeout = remaining); Errors::NetworkTimeoutError when <= 0. (src/main/resources/templates/ruby/default_api_client.mustache)
- rust: std::time::Instant deadline at loop start; request_builder.timeout(remaining) per hop (overrides the client-level timeout at :590); build the NetworkTimeoutError path when remaining is zero. (src/main/resources/templates/rust/default_api_client.mustache)
- elixir: Compute the deadline once in send_request and thread it through do_request_with_redirects; Task.yield(task, max(deadline - now, 0)) per hop. (src/main/resources/templates/elixir/default_api_client.mustache)
- go: already canonical
- node: already canonical
- swift: already canonical
- dart: already canonical

**Tests.** Next to the existing /test/slow timeout test in each client spec (java DefaultApiClientTest.java:~236, go test/default_api_client_test.go:~150, and the equivalents): timeout(1200), GET chasm /test/redirect/chain/1 (three hops of 500 ms, then a fast echo). Expect NetworkTimeout (status 0) in all 12; per-hop code would return 200 because each hop is 500 ms < 1200 ms. Positive control in the same block: timeout(5000) on the same chain returns 200. The margins (300 ms below, 700 ms above) tolerate container jitter.

**Petstore spec addition.**

```yaml
  /test/redirect/chain/1:
    get: { operationId: testRedirectChain1, x-internal: true, tags: [test], security: [], x-chasm-delay-ms: 500, responses: { '302': { description: Hop 1, headers: { Location: { schema: { type: string }, example: /test/redirect/chain/2 } } } } }
  /test/redirect/chain/2:
    get: { operationId: testRedirectChain2, x-internal: true, tags: [test], security: [], x-chasm-delay-ms: 500, responses: { '302': { description: Hop 2, headers: { Location: { schema: { type: string }, example: /test/redirect/chain/3 } } } } }
  /test/redirect/chain/3:
    get: { operationId: testRedirectChain3, x-internal: true, tags: [test], security: [], x-chasm-delay-ms: 500, responses: { '302': { description: Hop 3, headers: { Location: { schema: { type: string }, example: /test/echo } } } } }
```

**Risk.** python and ruby only expose per-socket-operation read timeouts, so a slow-trickle body is still bounded per read, not in total (as elixir needed the Task for). The hop-boundary check makes the chain bounded; the body remains best effort there. Chasm must honour x-chasm-delay-ms on 3xx responses (it does on the 200 in /test/slow).

## D77 timeout must be a positive number of milliseconds or null; reject <= 0 at configuration

- Ledger ids: F121 | Theme: redirect-proxy-tls-timeout | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: None

**Current state.** java: Builder.timeout stores the raw Integer (TransportOptions.java:269); Duration.ofMillis(<=0) later throws a raw IllegalArgumentException from HttpRequest.timeout at request time.
kotlin: raw Long (TransportOptions.kt:157); Ktor HttpTimeout rejects <= 0 with a raw IAE.
csharp: raw int? (TransportOptions.cs:216); HttpClient.Timeout throws for 0, and -1 silently means InfiniteTimeSpan.
php: raw ?int (TransportOptionsBuilder.php:103); max_duration 0 = unlimited.
python: raw Optional[int] (transport_options.py:214); urllib3.Timeout(total<=0) raises ValueError on every request.
ruby: raw (transport_options.rb:165); Faraday/Net::HTTP behaviour.
node: raw (transport-options.ts:206); AbortSignal.timeout(-1) RangeError per request, 0 aborts immediately.
dart: raw (transport_options.dart:185); Future.timeout negative fires immediately.
go: raw *int (transport_options.go:215); client.Timeout 0 or negative = no deadline (default_api_client.go:315), while the Timeout() doc says 0 means a 0 ms timeout.
rust: only SDK that validates: Build rejects ms < 0 with InvalidTimeout (transport_options.rs:308-311), but 0 is accepted as an immediate deadline.
swift: raw Int? from a non-throwing setter (TransportOptions.swift:187).
elixir: typespec non_neg_integer() only, not enforced (transport_options.ex:64).

**Canonical behaviour.** timeout is either null (no timeout) or an integer >= 1 millisecond. Anything else (0, negative) is a caller mistake rejected in the same place and with the same error family the SDK already uses for an invalid proxy URL. Message: 'Invalid timeout: must be a positive number of milliseconds (got N)'; go lower-cases the first letter (ST1005).

**Why.** AGENTS: a caller mistake raises the language's built-in argument error. 0 has no consistent meaning across libraries (no timeout in go and php, immediate failure in node/dart/rust, exception in java/kotlin/csharp/python), and 'silently no timeout' is a security-weakening surprise; null is the one spelled way to disable. Rejecting 0 as well as negatives is the only rule that all 12 can enforce identically.

**Rejected.** Reject only negatives and make 0 mean 'no timeout' (go/php today). Rejected: a typo or an unset env var parsed as 0 would silently disable the bound. Reject only negatives and leave 0 as 'immediate timeout' (rust today). Rejected: useless, and java/kotlin/python already throw for it.

**Per language.**

- java: In Builder.timeout throw IllegalArgumentException when timeout != null && timeout <= 0. (src/main/resources/templates/java/transport_options.mustache)
- kotlin: require(timeout == null || timeout > 0) in Builder.timeout, with the canonical message. (src/main/resources/templates/kotlin/transport_options.mustache)
- csharp: Throw ArgumentOutOfRangeException(nameof(timeout), ...) in TransportOptionsBuilder.Timeout. (src/main/resources/templates/csharp/transport_options.mustache)
- php: Throw \InvalidArgumentException in TransportOptionsBuilder::timeout() and in the TransportOptions constructor guard that already validates the proxy. (src/main/resources/templates/php/transport_options_builder.mustache, src/main/resources/templates/php/transport_options.mustache)
- python: Raise ValueError next to the proxy validation (__post_init__) so direct construction is covered too. (src/main/resources/templates/python/transport_options.mustache)
- ruby: raise ArgumentError in Builder#timeout. (src/main/resources/templates/ruby/transport_options.mustache)
- node: Throw TypeError (the type the proxy and CA checks use) in the builder and in the constructor validation at transport-options.ts:104. (src/main/resources/templates/node/transport_options.mustache)
- dart: ArgumentError in TransportOptionsBuilder.timeout, no value echoed beyond the number. (src/main/resources/templates/dart/transport_options.mustache)
- go: Add ErrInvalidTimeout; Build() returns it for *timeout <= 0; fix the Timeout() doc line 'a zero value means a 0ms timeout'. (src/main/resources/templates/go/transport_options.mustache)
- rust: Tighten Build to ms <= 0 and change the Display text in configuration_error.rs:46 from 'must not be negative' to 'must be positive'. (src/main/resources/templates/rust/transport_options.mustache, src/main/resources/templates/rust/errors/configuration_error.mustache)
- swift: Make timeout(_:) throws like proxy(_:) and throw a new ConfigurationError.invalidTimeout(Int); source-breaking for callers (add 'try'). (src/main/resources/templates/swift/transport_options.mustache, src/main/resources/templates/swift/errors/configuration_error.mustache)
- elixir: In TransportOptions.new/1 raise ArgumentError unless timeout is nil or a positive integer. (src/main/resources/templates/elixir/transport_options.mustache)

**Tests.** Per language in the existing TransportOptions test file (java TransportOptionsTest.java, kotlin TransportOptionsTest.kt, csharp Test/TransportOptionsTest.cs, php tests/TransportOptionsTest.php, python test/test_transport_options.py, ruby test/transport_options_test.rb, node test/transport-options.test.ts, dart test/transport_options_test.dart, go test/transport_options_test.go, rust tests/transport_options_test.rs:281, swift TransportOptionsTests.swift, elixir test/transport_options_test.exs): a table of timeout = -1, 0 expects the language's argument error, and 1 and null build. No petstore YAML (config-time).

**Risk.** Rust callers passing 0 and swift callers (new 'try') break at compile or build time. Both are pre-1.0 caller mistakes.

## D78 allowReserved: encoder output is the contract; transport re-encoding of [ ] and ' is a documented FORCED exception

- Ledger ids: F066 | Theme: redirect-proxy-tls-timeout | Still true: **partly** | Effort: S | Wire-visible: True | Needs user: None

**Current state.** java (java.net.URI), kotlin (Ktor), csharp, go, elixir, php: reserved characters stay literal on the wire.
ruby: FIXED, QueryParamsEncoder carries the query as one opaque string (default_api_client.rb:67-82), so the BaseApi bytes are the bytes sent.
python: urllib3 2.6 re-encodes '[' ']' to %5B %5D (verified; both parse_url and the pool's _encode_target do it, so the pool_manager.request path at default_api_client.py:~446 cannot avoid it).
swift: Foundation URL/URLComponents rewrite '[' ']' (verified on swift 6.2, macOS 15: URL(string:) -> %5B%5D, and URLComponents.percentEncodedQuery traps on them), DefaultApiClient.swift:207.
dart: Uri.parse normalises '[' ']' (abstract_api_client.dart:167; not runnable here).
node: WHATWG URL rewrites ' to %27 (verified, abstract-api-client.ts url handling before fetch).
rust: reqwest::Url::parse (default_api_client.rs:226) uses the same WHATWG query set, so ' becomes %27.

**Canonical behaviour.** BaseApi's encoder keeps the RFC 3986 reserved set literal (OAS allowReserved) and that is the contract. Five SDKs cannot keep every character literal because their URL type owns normalisation: '[' and ']' in python, swift and dart; the apostrophe in node and rust. These are documented FORCED exceptions: the value is preserved (the server percent-decodes %5B/%5D/%27 to the same text) but the bytes differ. Nothing is re-encoded by the SDK itself.

**Why.** All three 'avoidable' options in the ledger fail on inspection: pre-encoded urls still go through urllib3's _encode_target, Foundation will not hold '[' in a query, and Dart's Uri has no raw-query setter. The alternative of percent-encoding [ ] ' in all 12 would give identical bytes but break the OAS text (which lists them as literal) in the 7 SDKs that are correct. Value-preserving wire differences are lower risk than changing the encoder in 12 languages.

**Rejected.** Percent-encode '[', ']' and ''' in the shared encoder for every language so the wire is identical. Rejected: violates OAS allowReserved for the characters that ARE kept literal today, with no benefit to servers. Bypass the URL types (raw http.client / HttpClient.request with a pre-built target). Rejected: large, platform-specific, risks proxy/redirect handling.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- ruby: already canonical
- go: already canonical
- elixir: already canonical
- python: Document FORCED: '[' ']' rewritten by urllib3; add the note to the value-serializer allowReserved doc and AGENTS.md. (src/main/resources/templates/python/value_serializer.mustache)
- swift: Document FORCED: '[' ']' rewritten by Foundation. (src/main/resources/templates/swift/value_serializer.mustache)
- dart: Document FORCED: '[' ']' rewritten by dart:core Uri. (src/main/resources/templates/dart/value_serializer.mustache)
- node: Document FORCED: ''' becomes %27 (WHATWG special-query set). (src/main/resources/templates/node/value_serializer.mustache)
- rust: Document FORCED: ''' becomes %27 (WHATWG special-query set). (src/main/resources/templates/rust/value_serializer.mustache)

**Tests.** The shared petstore allowReserved parameter is revision on getPetTag (petstore openapi.yaml:805), but that operation cannot show the wire. In each default_api_client test (the file that already hits chasm /test/echo) send GET chasm/test/echo?revision=<full reserved set built by the SDK's own allowReserved encoder> and assert on what the server received: (1) the percent-decoded value equals the input in all 12, (2) the always-literal characters (: / ? @ ! $ ( ) * + , ; ~) appear unencoded, (3) '[' ']' and ''' are asserted literal only in the 7 canonical SDKs and as %5B/%5D (python, swift, dart) or %27 (node, rust) in the forced ones. This needs the chasm echo envelope to expose the request target; if it does not, assert against the in-process stub server in default_api_client_unit_test instead. Existing encoder-only tests (swift BaseApiTests.swift:689, ruby value_serializer_test.rb:472) stay.

**Risk.** None for callers; the change is documentation plus a test that records the known differences. If the echo envelope lacks the query, the integration half of the test cannot be written without extending the chasm fixture.

## D79 One message grammar for proxy and CA configuration errors, credentials redacted

- Ledger ids: F297 | Theme: redirect-proxy-tls-timeout | Still true: **yes** | Effort: M | Wire-visible: False | Needs user: None

**Current state.** java/php/csharp/python/elixir: 'Invalid proxy URL: X' (unparseable), '(must use http or https scheme): X', '(missing host): X' (canonical, value echoed with any user:pass).
kotlin: adds '(missing scheme)' (TransportOptions.kt:123).
node: unparseable is 'Invalid proxy URL (unparseable): X' (transport-options.ts:24).
ruby: unparseable embeds the URI library message 'Invalid proxy URL (bad URI(is not URI?): ...): X' (transport_options.rb:108).
dart: three messages without the value at all, deliberately (transport_options.dart:167-175).
swift: all three causes collapse to ConfigurationError.invalidProxyURL(val) -> 'Invalid proxy URL: X' (TransportOptions.swift:~170-180, ConfigurationError.swift:45).
rust: lower-case, Debug-quoted, different wording 'invalid proxy URL: "X": must use http or https scheme' (configuration_error.rs:43).
go: '%w %q: must use http or https scheme' (transport_options.go:201-205); lower-case is ST1005-forced.
CA file messages: 'failed to read or parse CA certificate from P: e' (java/kotlin), 'failed to read|parse CA certificate from "P"[: ...]' (node/php/elixir), 'failed to load CA certificate from' (python/csharp), 'failed to read or parse CA certificate: e' (dart), 'Invalid CA certificate: failed to read "P"' (swift), '{:?}' quoted (rust, go).

**Canonical behaviour.** Proxy: exactly 'Invalid proxy URL: <url>' (does not parse), 'Invalid proxy URL (must use http or https scheme): <url>' (includes a scheme-less value) and 'Invalid proxy URL (missing host): <url>'. CA: exactly 'failed to read CA certificate from "<path>": <cause>' and 'failed to parse CA certificate from "<path>": <cause or no PEM blocks found>'. The URL in the message has any userinfo replaced by '***@' (proxy URLs carry passwords). Go keeps the same text with a lower-case first letter and its sentinel wrapped (ST1005 is the only forced difference). Rust, which has no lint forcing lower-case, uses the capitalised text.

**Why.** Eight SDKs already produce the three proxy forms, so aligning the other four is the smaller change, and a single grep-able grammar makes the failure-path tests table-driven. Redaction resolves the conflict between the majority (echoes the URL, leaking credentials into logs) and dart (echoes nothing): callers keep the host/port context, secrets stay out. The CA grammar picks the most common wording and splits read from parse so the cause is actionable.

**Rejected.** Echo nothing (dart's rule). Rejected: loses the context needed to find the bad value. Keep full URLs. Rejected: leaks passwords. Keep per-library detail in the message (ruby/rust). Rejected: unstable across library versions and untestable.

**Per language.**

- java: Redact userinfo in the three messages (TransportOptions.java:248-256); CA message to the read/parse split (DefaultApiClient.java buildPinnedSslContext). (src/main/resources/templates/java/transport_options.mustache, src/main/resources/templates/java/default_api_client.mustache)
- kotlin: Fold '(missing scheme)' into the scheme message; redact; CA message split. (src/main/resources/templates/kotlin/transport_options.mustache, src/main/resources/templates/kotlin/http_client_factory_jvm.mustache)
- csharp: Redact; 'failed to load' -> 'failed to read/parse' (DefaultApiClient.cs:~166-185). (src/main/resources/templates/csharp/transport_options.mustache, src/main/resources/templates/csharp/default_api_client.mustache)
- php: Redact; CA wording already read/parse, keep. (src/main/resources/templates/php/transport_options.mustache)
- python: Redact; 'failed to load CA certificate' -> read/parse split. (src/main/resources/templates/python/transport_options.mustache, src/main/resources/templates/python/default_api_client.mustache)
- ruby: Drop the '(#{e.message})' interpolation at transport_options.rb:108 to 'Invalid proxy URL: <url>'; redact. (src/main/resources/templates/ruby/transport_options.mustache)
- node: Drop '(unparseable)' at transport-options.ts:24; redact. (src/main/resources/templates/node/transport_options.mustache)
- dart: Append the redacted URL to all three messages (still no value-carrying ArgumentError constructor); CA message to read/parse split. (src/main/resources/templates/dart/transport_options.mustache, src/main/resources/templates/dart/default_api_client_io.mustache)
- go: Change '%w %q: reason' to '%w (reason): %s' with the redacted URL; CA errors to the split wording. (src/main/resources/templates/go/transport_options.mustache)
- rust: Store the full canonical message in InvalidProxy/InvalidCaCertificate and Display it verbatim (capitalised proxy text); drop Debug quoting. (src/main/resources/templates/rust/transport_options.mustache, src/main/resources/templates/rust/errors/configuration_error.mustache)
- swift: Give invalidProxyURL a reason (or a message string) so the three causes render distinctly; errorDescription for invalidCACertificate returns the reason without the 'Invalid CA certificate: ' prefix. (src/main/resources/templates/swift/transport_options.mustache, src/main/resources/templates/swift/errors/configuration_error.mustache)
- elixir: Redact; add the unparseable form; CA messages already near canonical. (src/main/resources/templates/elixir/transport_options.mustache, src/main/resources/templates/elixir/default_api_client.mustache)

**Tests.** Table-drive the existing proxy/CA rows in each TransportOptions test file (java TransportOptionsTest.java, kotlin TransportOptionsTest.kt, csharp TransportOptionsTest.cs, php TransportOptionsTest.php, python test_transport_options.py, ruby transport_options_test.rb, node transport-options.test.ts, dart transport_options_test.dart, go transport_options_test.go, rust transport_options_test.rs, swift TransportOptionsTests.swift, elixir transport_options_test.exs): inputs 'http://[::1' (unparseable), 'ftp://h:1', 'proxy:3128' (scheme-less), 'http://u:p@' (no host), each asserting the exact string (go and rust per their case rule) and that 'p' never appears; CA rows for a missing path and a non-PEM file. No petstore YAML.

**Risk.** Anything that matches on the old text (a client test or log scraper) breaks. Redaction needs a small shared helper per language, which is the bulk of the effort.

## D80 Content-encoded parameters (content: instead of schema:) are serialized by the SDK ObjectSerializer in every location

- Ledger ids: F054 | Theme: serialization-wire-edge-cases | Still true: **yes** | Effort: L | Wire-visible: True | Needs user: False

**Current state.** Re-checked in the templates (no petstore op has a content param, so the goldens do not show it). Only query params have a content branch (AbstractBetterCodegen.deriveQuerySerializationKind:3715, templates test vendorExtensions.param.queryContent). header/path/cookie content has NO branch in any language and falls into serializeStyled with an empty style. Query branch per language: java objectSerializer.serialize (api/api.mustache:332-336) OK; kotlin objectSerializer.serialize (api.mustache:271-275) OK; dart serialize() (api.mustache:295-311) OK; swift ObjectSerializer.serialize but wrapped in `try?` so an error silently drops the param (api.mustache:270-303); csharp bare System.Text.Json.JsonSerializer.Serialize (api.mustache:371-378: nulls kept, SDK converters bypassed); python json.dumps (api.mustache:257-261: TypeError on pydantic models/datetimes); ruby JSON.generate (api.mustache:272-274: Dry::Struct falls back to to_s); node JSON.stringify (apis.mustache:254-258: ignores @Expose wire names, Dates end in Z); php json_encode (api.mustache:244-248 and 540-544: php property names, DateTime becomes an object); go json.Marshal with the error discarded (`jsonBytes, _ :=`, api.mustache:268-273) and HTML escaping ON; rust serde_json::to_string(..).unwrap_or_default() (api.mustache:406-421: silently sends `q=`); elixir Jason.encode! (api.mustache:339-346: Protocol.UndefinedError instead of SerializationError).

**Canonical behaviour.** Any parameter declared with `content: application/json` (query, header, path, cookie) is serialized by the SDK's own ObjectSerializer.serialize: wire property names, canonical .SSS+00:00 date-times, null fields omitted, NaN/Infinity rejected, <, > and & literal. Any failure raises the SDK's SerializationError (never swallowed, never an empty value, never a dropped param). The resulting JSON text is then encoded once by the location's normal rule (query component-encoded as for any query value, path segment-encoded, header verbatim after the CR/LF check, cookie encoded). Only JSON media types are in scope; other content media types stay unchanged.

**Why.** OpenAPI 3.x says content-encoded parameters carry the media-type serialization of the schema value, so the model's wire form is the SDK's JSON form (criterion a). java, kotlin, dart and swift already route the query case through ObjectSerializer (b). Silent drop (swift), silent empty value (rust) and a raised foreign exception (elixir, python, ruby, node) violate the AGENTS error contract and are silent data changes (c). Every language has an ObjectSerializer.serialize entry point, so no dependency is needed (d).

**Rejected.** Keep each language's stdlib encoder: rejected, 8 of 12 diverge on wire names, dates, null handling or escaping and two swallow errors. Reject content params at generation time: rejected, the 4 correct languages already support them and OpenAPI allows the feature. Fix query only and leave header/path/cookie: rejected, the silent empty-style fallback is a worse failure than the query divergence.

**Per language.**

- java: Query branch already canonical. Add the same objectSerializer.serialize call for header, path (then segment-encode) and cookie content params. (templates/java/api/api.mustache:332-336)
- kotlin: Query branch already canonical. Add header/path/cookie content branches using objectSerializer.serialize. (templates/kotlin/api/api.mustache:271-275)
- csharp: Replace System.Text.Json.JsonSerializer.Serialize with the ObjectSerializer instance's Serialize (expose it from the base api); add header/path/cookie branches. (templates/csharp/api/api.mustache:371-378)
- php: Replace json_encode with ObjectSerializer::serialize in both occurrences; add header/path/cookie branches. (templates/php/api/api.mustache:244-248,540-544)
- python: Replace json.dumps with self._object_serializer.serialize(...); add header/path/cookie branches. (templates/python/api/api.mustache:257-261)
- ruby: Replace JSON.generate with ::{{moduleName}}::ObjectSerializer.serialize; add header/path/cookie branches. (templates/ruby/api/api.mustache:272-274)
- node: Replace JSON.stringify with ObjectSerializer.serialize; add header/path/cookie branches. (templates/node/api/apis.mustache:254-258)
- dart: Query branch already canonical (serialize()). Add header/path/cookie branches. (templates/dart/api/api.mustache:295-311)
- go: Replace json.Marshal with the package serialize() (Encoder, HTML escaping off) and return its error from the operation instead of discarding it; add header/path/cookie branches. (templates/go/api/api.mustache:268-273)
- rust: Replace serde_json::to_string(..).unwrap_or_default() with crate::object_serializer::serialize(val)? in both the required and optional arms (operation already returns Result). (templates/rust/api/api.mustache:406-421)
- swift: Replace `if let json = try? ObjectSerializer.serialize(val)` with `let json = try ObjectSerializer.serialize(val)` (operation is async throws) in both arms; add header/path/cookie branches. (templates/swift/api/api.mustache:270-303)
- elixir: Replace Jason.encode! with PetstoreClient.ObjectSerializer.serialize/1 (raises SerializationError); add header/path/cookie branches. (templates/elixir/api/api.mustache:339-346)

**Tests.** Add one new petstore operation (YAML below) so every language's generated StoreApi test can drive it against the mock server: java api/StoreApiTest.java, kotlin api/StoreApiTest.kt, csharp Test/Api/StoreApiTest.cs, php tests/Api/StoreApiTest.php, python test/api/test_store_api.py, ruby test/api/store_api_test.rb, node test/Api/store-api.test.ts, dart test/store_api_test.dart, go test/store_api_test.go, rust tests/store_api_test.rs, swift StoreApiTests.swift, elixir test/store_api_test.exs. Assert (1) the exact `q=` bytes (percent-decoded) for PhotoMetadata{caption:'a<b>&c', takenAt:2024-01-02T03:04:05Z, location:{lat:1.5}} equal {"caption":"a<b>&c","takenAt":"2024-01-02T03:04:05.000+00:00","location":{"lat":1.5}} with no null keys; (2) X-Filter header carries {"k":"v"}; (3) failure path: lat=NaN raises the SDK SerializationError and no request is sent (table row / it.each across languages).

**Petstore spec addition.**

```yaml
# paths: (single block shared with the time and Set decisions, apply once)
  /store/search:
    get:
      tags:
        - store
      summary: Searches the store; exercises content-encoded, Set and time-of-day parameters.
      operationId: searchStore
      security: []
      parameters:
        - name: q
          in: query
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PhotoMetadata'
        - name: X-Filter
          in: header
          content:
            application/json:
              schema:
                type: object
                additionalProperties:
                  type: string
        - name: labels
          in: query
          schema:
            type: array
            uniqueItems: true
            items:
              type: string
        - name: at
          in: query
          schema:
            type: string
            format: time
      responses:
        '200':
          description: Successful operation
# components/schemas/EdgeCases/properties: (body-path coverage for format: time)
        cutoff:
          type: string
          format: time
```

**Risk.** Generator surface: needs flags (or the native CodegenParameter.content, per CLAUDE.md no vendor extensions) for header/path/cookie in AbstractBetterCodegen and all 12 api templates. csharp/python api layers must be given access to the ObjectSerializer instance held by the base api (python base_api.py:347 already has self._object_serializer). Path values must be percent-encoded after serialization exactly once.

## D81 format: time is RFC 3339 partial-time HH:mm:ss, plus .SSS only when the millisecond part is non-zero, in params and bodies

- Ledger ids: F071 | Theme: serialization-wire-edge-cases | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Re-checked, no format:time anywhere in petstore, so goldens only show the serializers. java: ISO_LOCAL_TIME keeps up to 9 fraction digits (ObjectSerializer.java:167-169). kotlin: NO LocalTime branch, LocalTime matches `is TemporalAccessor -> DATE_TIME_FORMATTER` and throws UnsupportedTemporalTypeException (ObjectSerializer.kt:520-527), and no LocalTime KSerializer is registered in the module (~:990), so a time model field cannot serialize either. csharp: TimeOnly -> HH:mm:ss, fraction dropped (ObjectSerializer.cs:158). php: time maps to \DateTimeImmutable and stringify formats it as a full RFC 3339 date-time (ObjectSerializer.php:262, DATE_TIME_FORMAT). python: isoformat(timespec=seconds), dropped (object_serializer.py:586). ruby: Tod::TimeOfDay strftime('%H:%M:%S'), dropped (object_serializer.rb:190, 287). node: Temporal.PlainTime.toString(), minimal digits kept (object-serializer.ts:439, 931). rust: chrono::NaiveTime Display, fraction kept. elixir: Time.truncate(:second), dropped (object_serializer.ex:265, 386). go, dart, swift: time maps to String, caller supplies text.

**Canonical behaviour.** A `format: time` value is written as `HH:mm:ss` (no date, no offset); when the millisecond part is non-zero it is written as `HH:mm:ss.SSS` (exactly three digits, sub-millisecond digits truncated, never rounded). Same form for path/query/header/cookie params and JSON bodies. Parsing accepts HH:mm:ss with an optional fraction. go/dart/swift pass the caller's string through (their type is String).

**Why.** RFC 3339 partial-time; the whole-second form matches every spec example and is what 4 SDKs already emit, so strict-pattern servers keep working (criterion c). Keeping milliseconds avoids the silent fraction loss of csharp/python/ruby/elixir and matches java/node/rust (b), capped at ms like the date-time canon. Kotlin throwing a raw java.time exception violates the AGENTS error contract (a). php can special-case via the schemaType tag it already passes (`\DateTimeImmutable` identifies time, `|date` identifies date).

**Rejected.** Seconds only everywhere: rejected, silently truncates fractions that java/node/rust preserve. Always .SSS like date-time: rejected, changes the common whole-second form that strict servers expect and that spec examples show. Verbatim minimal digits (java/node today): rejected, not byte-identical across languages. Caveat: ruby's tod gem must carry sub-second values; if Tod::TimeOfDay cannot, ruby becomes a documented FORCED seconds-only exception.

**Per language.**

- java: Replace ISO_LOCAL_TIME with a helper: truncate to millis, pattern HH:mm:ss or HH:mm:ss.SSS; register the same LocalTime serializer in dateTimeModule() for bodies. (templates/java/object_serializer.mustache)
- kotlin: Add `is LocalTime ->` branch before TemporalAccessor in stringify and a LocalTimeSerializer registered next to the other contextual serializers. (templates/kotlin/object_serializer.mustache)
- csharp: Format TimeOnly as HH:mm:ss or HH:mm:ss.fff after truncating to ms; add a TimeOnly JsonConverter in CreateDefaultOptions for bodies. (templates/csharp/object_serializer.mustache)
- php: In ValueSerializer treat schemaType `\DateTimeImmutable` like the existing `|date` special case, format H:i:s[.v]; give the body normalizer the same per-property format. (templates/php/value_serializer.mustache, templates/php/object_serializer.mustache)
- python: Truncate microsecond to ms; isoformat(timespec='milliseconds') when ms != 0 else 'seconds'; same field serializer for models. (templates/python/object_serializer.mustache, templates/python/_types.mustache)
- ruby: strftime('%H:%M:%S') plus '.%L' when ms != 0 (verify Tod carries sub-seconds; else document forced). (templates/ruby/object_serializer.mustache)
- node: Replace PlainTime.toString() with ms==0 ? toString({fractionalSecondDigits:0}) : toString({fractionalSecondDigits:3}) after truncating to ms (auto would emit .5 not .500). (templates/node/object_serializer.mustache)
- dart: already canonical
- go: already canonical
- rust: Format NaiveTime via a helper: truncate to ms, `%H:%M:%S` or `%H:%M:%S%.3f`, used by to_path_value and the serde adapter. (templates/rust/object_serializer.mustache, templates/rust/value_serializer.mustache)
- swift: already canonical
- elixir: Time.truncate(:millisecond); emit Time.truncate(:second) form when microsecond is {0,_}, else Time.to_iso8601 (prints exactly .SSS). (templates/elixir/object_serializer.mustache)

**Tests.** Unit: each language's ObjectSerializer test (java ObjectSerializerTest.java, kotlin ObjectSerializerTest.kt, csharp Test/ObjectSerializerTest.cs, php tests/ObjectSerializerTest.php, python test/test_object_serializer.py, ruby test/object_serializer_test.rb, node test/object-serializer.test.ts, rust tests/object_serializer_test.rs, elixir test/object_serializer_test.exs): 10:30:00 -> `10:30:00`, 10:30:00.123456789 -> `10:30:00.123`, 10:30:00.9996 -> `10:30:00.999` (truncation). Operation level: the `at` query param on searchStore (YAML in the F054 decision) asserts `at=10%3A30%3A00.123`; the EdgeCases.cutoff body property asserts the same JSON string and a decode round trip (this is what covers kotlin's missing serializer).

**Petstore spec addition.**

```yaml
# Part of the single YAML block given in the F054 decision: query param `at` (type string, format time) on /store/search and property EdgeCases.cutoff (type string, format time). Nothing else is needed.
```

**Risk.** Public wire change for csharp/python/ruby/elixir (adds a fraction when present) and java/node/rust (ms cap). php body path needs a DateTimeNormalizer format per time property.

## D82 uniqueItems array (Set) parameters expand exactly like array parameters

- Ledger ids: F072 | Theme: serialization-wire-edge-cases | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Re-checked: petstore has uniqueItems only on a model property (Pet.photoUrls, openapi.yaml:1585), never on a parameter, and the serializers still recognise only their list type. csharp ValueSerializer.cs:227 `value is IList` (HashSet falls through to scalar stringify -> `System.Collections.Generic.HashSet`1[...]`); python value_serializer.py:205 `isinstance(value, list)` -> `{'a', 'b'}`; node value-serializer.ts:146 `Array.isArray` -> `[object Set]`; ruby `is_a?(Array)` -> `#<Set: ...>`; php ValueSerializer.php:71/113/239 `is_array` (\Ds\Set is JsonSerializable -> JSON text); dart value_serializer.dart:452 `_toStringList` accepts List only -> `{a, b}`; swift ValueSerializer.swift:274-283 `as? [String]/[Any]` only; elixir value_serializer.ex:50-395 only `is_list` clauses, so a MapSet reaches stringify -> to_string and raises Protocol.UndefinedError. java and kotlin accept Collection, go uses a slice, rust iterates: correct.

**Canonical behaviour.** A `type: array, uniqueItems: true` parameter is serialized exactly like an array parameter for every style and explode value: `labels=red,blue` (non-exploded) or `labels=red&labels=blue` (exploded), elements stringified individually, in the Set's iteration order (a Set has no declared order, so callers must not rely on it).

**Why.** OpenAPI array parameter serialization does not depend on uniqueItems (a); java, kotlin, go, rust already do it (b); the current output is debug text on the wire or a foreign exception (c); every language can test for its own Set/Enumerable type without a dependency (d).

**Rejected.** Sort the elements for byte-identical output: rejected, it silently reorders caller data and Set order is not part of any contract. Map Sets to lists in the generator typeMapping: rejected, it loses the Set type on models for no gain.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: `value is IList` -> `value is IEnumerable and not string and not IDictionary` (ValueSerializer.cs:227). (templates/csharp/value_serializer.mustache)
- php: At entry convert `\Ds\Set`/`\Ds\Collection` via toArray() before the is_array checks. (templates/php/value_serializer.mustache)
- python: At entry `if isinstance(value, (set, frozenset, tuple)): value = list(value)`. (templates/python/value_serializer.mustache)
- ruby: At entry `value = value.to_a if value.is_a?(Set)`. (templates/ruby/value_serializer.mustache)
- node: At entry `if (value instanceof Set) value = [...value]`. (templates/node/value_serializer.mustache)
- dart: Normalise `Set` (Iterable that is not List/String/Map) to a list at entry and in _toStringList. (templates/dart/value_serializer.mustache)
- go: already canonical
- rust: already canonical
- swift: Add `else if let s = value as? Set<AnyHashable> { items = Array(s) }` next to the [Any] cases (ValueSerializer.swift:274-283). (templates/swift/value_serializer.mustache)
- elixir: Add a leading clause converting %MapSet{} to MapSet.to_list/1 in serialize_styled so the is_list clauses apply. (templates/elixir/value_serializer.mustache)

**Tests.** Existing ValueSerializer tests (java api/ValueSerializerTest.java, kotlin ValueSerializerTest.kt, csharp Test/ValueSerializerTest.cs, php tests/ValueSerializerTest.php, ruby test/value_serializer_test.rb, node test/value-serializer.test.ts, dart test/value_serializer_test.dart, go pkg/value_serializer_test.go, rust tests/value_serializer_test.rs, swift ValueSerializerTests.swift, elixir test/value_serializer_test.exs, python test/ via test_base_api) get a Set-input row for form/explode true+false. Operation level: the `labels` query param on searchStore (YAML in the F054 decision) with two elements; assert the query pairs as an unordered multiset (`labels=red`, `labels=blue`).

**Petstore spec addition.**

```yaml
# Part of the single YAML block given in the F054 decision: query param `labels` (array, uniqueItems: true, items string) on /store/search.
```

**Risk.** Low. Normalise at the entry of serializeStyled/serializeDeepObject so every style branch sees a list.

## D83 Bare type: number precision: exact where the language can, and AGENTS/README state precisely where it is not

- Ledger ids: F075 | Theme: serialization-wire-edge-cases | Still true: **yes** | Effort: L | Wire-visible: True | Needs user: False

**Current state.** Re-checked, AGENTS.md:246 still claims python and node 'preserve full textual precision'. python: json.loads without parse_float (object_serializer.py:~220), so 12345678901234567890.123 becomes Decimal('1.2345678901234567E+19') before pydantic; encode widens through float (_types.py:87-93, _decimal_to_json_number) and the field accepts a quoted "12.5"; README.md:82-100 now admits the encode loss but still says Decimal 'preserves exact decimal representation'. node: JSON.parse then String(number) in the Pet constructor (models/pet.ts:176-183, object-serializer.ts:348), so digits are lost on decode and 1e-7 is re-sent as 1e-7; ANY string is accepted for weightKg ('abc' is re-sent quoted); encode uses JSON.rawJSON and is exact for valid text. java: BigDecimal but Jackson writes with toString, so 1E+3/1E-7 stay in exponent form (no WRITE_BIGDECIMAL_AS_PLAIN in ObjectSerializer.java:~375-430). kotlin: BigDecimal with toPlainString (ObjectSerializer.kt:736). csharp: decimal, plain digits. ruby/swift/go/rust/php/dart/elixir: native float, documented lossy.

**Canonical behaviour.** The four languages that model bare `type: number` as a decimal (java, kotlin, csharp, node) are exact in both directions: decode keeps the numeric text, encode writes an unquoted JSON number in PLAIN notation (no exponent), and a non-numeric string is rejected. python is exact on decode (Decimal parsed from the text) and a FORCED float-widening on encode (pydantic/stdlib json cannot emit a raw Decimal without a dependency), documented as such. The seven native-float languages keep their documented lossy behaviour. AGENTS.md and the python README are corrected to say exactly this.

**Why.** A documented promise of exact decimals (criterion a: README/AGENTS) that is silently broken on decode is a silent data change on money-like values (c). Node and java can be fixed without a dependency (Node >= 21 reviver context.source is the same feature level as the JSON.rawJSON the template already needs); python decode can use parse_float=Decimal (d). Python encode genuinely cannot (named feature: pydantic-core's JSON serializer has no raw-number type) so it is documented FORCED rather than forced into a hack.

**Rejected.** Only correct the docs and move python/node to the lossy list: defensible and zero risk, rejected because java/kotlin/csharp prove exactness is achievable and node's rawJSON encode path already exists. Add a decimal dependency to python: rejected (criterion d).

**Per language.**

- java: Enable JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN on the mapper so 1E+3 writes 1000 and 1E-7 writes 0.0000001. (templates/java/object_serializer.mustache)
- kotlin: already canonical
- csharp: already canonical
- python: parse_json uses json.loads(..., parse_float=Decimal); LaxFloat gains a Decimal->float BeforeValidator; JsonNumber becomes Strict (reject quoted strings); default= hook maps stray Decimal to float; README Decimal section and AGENTS state the encode widening as FORCED. (templates/python/object_serializer.mustache, templates/python/_types.mustache, templates/python/readme.mustache)
- node: Decode with JSON.parse(text, reviver) using context.source for keys in __decimalFields so the Decimal holds the original text; constructor rejects a string that does not match the numeric-text regex already used by rawDecimal. (templates/node/object_serializer.mustache, templates/node/models/model.mustache)
- ruby: already canonical
- swift: already canonical
- go: already canonical
- rust: already canonical
- php: already canonical
- dart: already canonical
- elixir: already canonical

**Tests.** Pet.weightKg already exists in petstore (openapi.yaml:1633), no YAML needed. java ObjectSerializerTest.java: bodies {"weightKg":1E+3} and 0.0000001 re-serialize as 1000 and 0.0000001. node test/object-serializer.test.ts: 12345678901234567890.123 and 12.30 round-trip byte-exact, "abc" throws TypeError. python test/test_object_serializer.py: deserialize keeps Decimal('12345678901234567890.123') and 12.30; quoted "12.5" is rejected; serialize of Decimal('1e400') raises. kotlin/csharp existing tests stay as the reference rows.

**Risk.** python parse_float=Decimal turns every JSON float into Decimal, so LaxFloat (format float/double) needs a Decimal->float widening BeforeValidator and free-form/additionalProperties numbers become Decimal in memory; the json.dumps default=str fallback would then quote them, so add a Decimal->float default. Node reviver only applies to fields in the existing __decimalFields name set. If that proves too invasive, fall back to the doc-only alternative for python and keep java/node fixes.

## D84 JSON strings in request bodies are written literally (/, <, >, &, non-ASCII, U+2028/2029) in all 12, with documented stdlib residuals

- Ledger ids: F082 F198 F243 | Theme: serialization-wire-edge-cases | Still true: **partly** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Re-checked. Fixed: php serialize uses JSON_UNESCAPED_SLASHES|UNICODE|LINE_TERMINATORS (ObjectSerializer.php:~290); swift uses .withoutEscapingSlashes (+ .sortedKeys) in both JSONEncoder and JSONSerialization (ObjectSerializer.swift:101, 166); go body path uses Encoder+SetEscapeHTML(false) in serialize() (object_serializer.go:71-90) and the model helper marshalJSON (models/json_marshal.go:27-35, used by every MarshalJSON and Set). Still open: (1) go multipart JSON parts still call json.Marshal, which HTML-escapes even a Marshaler's output (base_api.go:536 writeJSONMultipartPart, default_api_client.go:739 and :757), so a PhotoMetadata part with caption 'a<b>&c' goes out as a<b>&c; (2) go always escapes U+2028/U+2029 (encoding/json hard-codes it, also with SetEscapeHTML(false)); (3) csharp uses JavaScriptEncoder.UnsafeRelaxedJsonEscaping (ObjectSerializer.cs:358), which still escapes supplementary-plane characters (emoji as surrogate pairs), U+2028/2029 and DEL (known .NET behaviour, not run here). java, kotlin, python, ruby, node, dart, rust, elixir write all of these literally.

**Canonical behaviour.** Request bodies, multipart JSON parts and JSON query/header values write `/`, `<`, `>`, `&`, all non-ASCII characters (including supplementary plane, U+2028, U+2029, DEL) literally; only `"`, `\` and control characters below U+0020 are escaped. FORCED exception: go always escapes U+2028 and U+2029 because encoding/json does so unconditionally; both forms are valid JSON that decodes to the same string.

**Why.** AGENTS already records that go's HTML escaping was removed so bytes go out 'like every other SDK' (a); 8 SDKs plus php and swift now do it (b); literal output never changes the decoded value (c). csharp can be fixed with a small custom JavaScriptEncoder, no dependency (d). go's U+2028/2029 would need a post-pass over encoder output that must distinguish an escaped backslash from an escape, which is not worth the risk, so it is a named FORCED exception.

**Rejected.** Leave the multipart paths and csharp as accepted escapes: rejected for go multipart, which is an actual leftover of the F082 fix (same json.Marshal root cause). Accept csharp's escapes as forced: rejected, a custom encoder (override WillEncode to false for everything but quote, backslash and C0 controls) is feasible.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: Replace UnsafeRelaxedJsonEscaping with a LiteralJsonEncoder : JavaScriptEncoder that encodes only '"', '\\' and < U+0020; keep it in CreateDefaultOptions and any multipart options. (templates/csharp/object_serializer.mustache (CreateDefaultOptions))
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: Route writeJSONMultipartPart and the two multipart json.Marshal calls in the default client through serialize() (Encoder, SetEscapeHTML(false)); document U+2028/2029 as FORCED in AGENTS. (templates/go/base_api.mustache (writeJSONMultipartPart), templates/go/default_api_client.mustache (~:739,:757))
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** No YAML: Pet.name, Pet.photoUrls and the existing addPetPhotos multipart op (metadata: PhotoMetadata, openapi.yaml:420-456) are enough. Add a table row (it.each style) to each ObjectSerializer test with the string `a<b>&c/é😀 ` asserting the literal bytes (go: everything literal except  ; csharp: ObjectSerializerTest.cs), and to go test/default_api_client_test.go / pkg/default_api_client_unit_test.go an addPetPhotos call whose metadata.caption is `a<b>&c` asserting the multipart part body is literal. php tests/ObjectSerializerTest.php and swift ObjectSerializerTests.swift keep the slash assertion (swift test at :985-989 must now expect an unescaped slash).

**Risk.** Low. csharp custom encoder must be used for every serializer-options path including multipart JSON parts.

## D85 Request-body object keys follow declared property order; swift is a documented forced sorted order

- Ledger ids: F244 | Theme: serialization-wire-edge-cases | Still true: **partly** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Re-checked. swift now sets .sortedKeys on both encoders (ObjectSerializer.swift:101, 166): deterministic, alphabetical (no longer random per process). elixir still builds each model as a plain map (object_serializer.ex:404-421 Enum.reduce + Map.put) so Jason emits alphabetical order and hash order above 32 keys: Order -> complete,id,petId,quantity,shipDate,status. java, kotlin, csharp, python, ruby, php, node, go, rust, dart emit declared (schema) order.

**Canonical behaviour.** Object keys are written in schema declaration order, with additionalProperties entries after the declared ones. FORCED exception: swift writes keys sorted alphabetically, because JSONEncoder only offers hash order or sortedKeys and Codable gives no control over declaration order; the order is deterministic across runs.

**Why.** 10 SDKs already do declared order (b); JSON objects are unordered per RFC 8259 but request signing and caching need stable bytes (c). elixir can do it with Jason.OrderedObject (Jason ~> 1.4 is already the dependency, mix.exs:64) fed from module.__info__(:struct) field order (Elixir 1.17 floor), no new dependency (d). swift cannot (named feature: JSONEncoder key ordering).

**Rejected.** Declare order irrelevant and keep elixir alphabetical: rejected, cheap to fix and byte-identical bodies matter for signing. Switch swift to a hand-written encoder: rejected, disproportionate for a forced, deterministic difference.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: FORCED sorted keys; record it in AGENTS.md forced divergences and comment the .sortedKeys line. (AGENTS.md, templates/swift/object_serializer.mustache)
- elixir: Build the model payload as a Jason.OrderedObject from module.__info__(:struct) order (declared fields first, additionalProperties after); keep plain maps for free-form values. (templates/elixir/object_serializer.mustache (~:404-421))

**Tests.** No YAML: Order (6 keys) and Pet exist. Add to each ObjectSerializer test one assertion that serializing a fully populated Order yields exactly {"id":..,"petId":..,"quantity":..,"shipDate":..,"status":..,"complete":..} in declared order (swift: the sorted string, and encoding 20 times yields one distinct string; elixir test/object_serializer_test.exs: declared order, not alphabetical).

**Risk.** Low. elixir additionalProperties merge must append after declared keys; oneOf/anyOf merge path (AnyOfComposite) must keep first-seen order.

## D86 format: double body text is not canonicalised: JSON number equality, not byte equality, is the contract

- Ledger ids: F246 | Theme: serialization-wire-edge-cases | Still true: **yes** | Effort: S | Wire-visible: True | Needs user: False

**Current state.** Re-checked, no body-level change since the audit. 3.0 -> `3.0` in java, kotlin, python, ruby, rust, dart, elixir but `3` in csharp, php (no JSON_PRESERVE_ZERO_FRACTION, ObjectSerializer.php:~290), node, go, swift. Exponent spelling differs in all 12 (1e21: 1.0E21 java/kotlin, 1E+21 csharp, 1.0e+21 ruby/php, 1e+21 node/go/dart/swift, 1e21 python, 1.0e21 elixir). node writes -0.0 as `0`. The parameter path IS canonical (csharp FormatRealNumber ObjectSerializer.cs:192, go formatFloat object_serializer.go:172, php stringify :279-284).

**Canonical behaviour.** Parameters keep the existing canonical form (whole numbers keep `.0`). For JSON bodies only numeric value equality is guaranteed: the SDK emits a valid finite JSON number that parses to the same IEEE-754 double; the textual spelling of whole numbers and exponents is a documented FORCED difference, and non-finite values stay rejected everywhere. Record the node `-0.0` sign loss as part of the exception.

**Why.** RFC 8259 defines number syntax, not a canonical text, and 3 and 3.0 are the same number (a). Making the text identical would need custom number writers in node (JSON.rawJSON, Node 21+ only and per field), swift (JSONEncoder has no Double hook, every Double property would need a wrapper), go (MarshalJSON on every float64 field) and php/csharp flags, and the exponent spelling would still differ among the languages that already agree on `.0`, so full byte parity is not reachable anyway (d). No caller data changes value (c).

**Rejected.** Align the five whole-number languages to `.0`: rejected as high churn with no value change and incomplete parity (exponents). Align everything to the shortest form: rejected, moves 7 languages the other way.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** None to add beyond existing ones. DryFood.weightKg and PhotoMetadataLocation.lat already exist; optionally one table row per ObjectSerializer test asserting the body parses back to the same double (3.0, 1e21, 1e-7), which locks value equality rather than spelling.

**Risk.** None (documentation only). If byte-identical double bodies are ever required, that is a new product requirement, not a defect.

## D87 swift stays a documented FORCED exception for the 512-level decoder cap and first-wins duplicate keys (php and rust are fixed)

- Ledger ids: F078 F242 | Theme: serialization-wire-edge-cases | Still true: **partly** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Re-checked. F078: php fixed, JSON_DECODE_DEPTH = MAX_JSON_DEPTH + 1 and json_decode with JSON_THROW_ON_ERROR (ObjectSerializer.php:51-57, 564-820). swift still pre-scans at 1000 (ObjectSerializer.swift:22, 237) but Foundation's JSONDecoder refuses about 512 levels itself; the in-code comment at :14-21 says so but AGENTS.md has no entry and the 'All twelve SDKs use the same cap' wording is gone. F242: rust fixed, a serde 'duplicate field' error is retried through the generic tree where the last occurrence wins (object_serializer.rs:412-413, 434, tests :1260). java/csharp/go/python/node/ruby/php/dart/elixir/kotlin keep the last duplicate. swift JSONDecoder keeps the FIRST ({"id":1,"id":2} -> 1) and has no hook to change it.

**Canonical behaviour.** Depth cap 1000 and last-duplicate-wins are the cross-SDK behaviour. swift is a documented FORCED exception on both: Foundation's JSONDecoder enforces its own ~512-level limit (so 513..1000 is rejected with SerializationError, not decoded) and resolves duplicate keys first-wins; neither is configurable without replacing the parser.

**Why.** php and rust are unforced and already fixed (b). Replacing JSONDecoder for swift would drop its typed-decoding, Decimal and date strategies, a large risk for two exotic inputs (duplicate keys are non-conformant I-JSON, RFC 8259 section 4 says behaviour is unpredictable). The swift behaviour is deterministic and safe (an over-deep body is rejected, never silently altered) (c).

**Rejected.** Lower the shared cap to 512 in all 12: rejected, would reject payloads java/csharp/etc. legitimately accept. Pre-scan swift bodies and reject duplicate keys: rejected, adds a second parser pass and a different error contract for a non-conformant input.

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: Add an AGENTS.md forced-divergence entry (Foundation decoder: ~512 levels, first-wins duplicates); correct the maxJsonDepth comment to say 'cap is 1000 but Foundation rejects beyond ~512'. (AGENTS.md, templates/swift/object_serializer.mustache:14-21)
- elixir: already canonical

**Tests.** No YAML. swift Tests/PetstoreClientTests/ObjectSerializerTests.swift (near :1248): assert a 600-deep body throws SerializationError and a duplicate-key Order body decodes to the first value (locks the documented exception). php tests/ObjectSerializerTest.php:1324 and rust object_serializer.rs:1260 already lock the fixes; add the same duplicate-key row to the other ObjectSerializer tests if absent.

**Risk.** None (documentation). Keep the ObjectSerializerTests.swift depth tests (:1248) and add the duplicate-key assertion so a Foundation change is noticed.

## D88 Protobuf durations beyond ~292 years: rust fixed, go a documented FORCED exception

- Ledger ids: F249 | Theme: serialization-wire-edge-cases | Still true: **partly** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Re-checked. rust fixed: proto_duration.rs bounds seconds with MAX_SECONDS = 315_576_000_000 (:23, :108) and adds Duration::seconds + Duration::nanoseconds separately; rust tests/object_serializer_test.rs:127-136 lock max/min. go still rejects anything outside time.Duration (models/duration.go: `secs > (math.MaxInt64-nanos)/int64(time.Second)`), about +-292 years; the other 10 accept 315576000000s.

**Canonical behaviour.** The accepted range is the google.protobuf.Duration range of +-315,576,000,000 seconds. go is a FORCED exception: its public type is time.Duration (int64 nanoseconds, about +-292 years) and changing that type breaks the public API; the error stays a SerializationError.

**Why.** Matches the protobuf spec (a) and 11 of 12 SDKs (b). go's limit follows from the chosen stdlib type (named feature: time.Duration is int64 ns).

**Rejected.** Replace go's Duration with a struct of seconds+nanos: rejected, breaks every caller's arithmetic with time.Duration and is not wire-visible.

**Per language.**

- rust: already canonical
- go: Document the +-292-year bound as FORCED in AGENTS.md and in the Duration doc comment (already says 'about +-292 years' in UnmarshalText). (AGENTS.md, templates/go/models/duration.mustache)
- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** rust tests/object_serializer_test.rs:127-136 already lock it. go pkg/object_serializer_test.go: add one row asserting `315576000000s` yields a SerializationError whose message names the range (locks the forced exception). No YAML (existing duration fields).

**Risk.** None (documentation).

## D89 Multipart parts carry only Content-Disposition (+ Content-Type where meaningful): no Content-Transfer-Encoding, no per-part Content-Length

- Ledger ids: F252 | Theme: serialization-wire-edge-cases | Still true: **partly** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Re-checked. php: text parts are fixed (DefaultApiClient.php:140-159 plainFormPart removes Content-Type and Content-Transfer-Encoding) and Psr18ApiClient now backslash-escapes the field name ($quotedName, Psr18ApiClient.php:~180-215); but the JSON parts and file parts are still plain Symfony DataPart (DefaultApiClient.php:~192-250), and FormDataPart forces encoding 8bit on every part, so each of those still sends `Content-Transfer-Encoding: 8bit` (RFC 7578 4.7: SHOULD NOT), unlike Psr18ApiClient. kotlin: parts are built with Ktor FormBuilder.append(name, ByteArray, headers) (DefaultApiClient.kt:~451-500) and Ktor's formData() adds `Content-Length: N` to ByteArray parts (ledger audit of ktor 3.5.0 bytecode, not re-run here); nothing in the template removes it. The other 10 send Content-Disposition (+Content-Type) only.

**Canonical behaviour.** Every multipart part carries Content-Disposition, plus Content-Type for file and JSON parts, and nothing else: no Content-Transfer-Encoding, no Content-Length, no charset Content-Type on plain text parts. Field names are backslash-escaped in `name="..."`.

**Why.** RFC 7578 section 4.7 (a) and 10 of 12 SDKs (b); php's own second transport already proves feasibility (d); extra headers can trip strict servers/WAFs (c).

**Rejected.** Accept the extra headers as library defaults: rejected, php's two transports disagree with each other. Hand-write php's multipart body in DefaultApiClient: rejected, a DataPart subclass is smaller.

**Per language.**

- php: Add a DataPart subclass (same getPreparedHeaders override as plainFormPart) for JSON and file parts, including DataPart::fromPath, so no part emits Content-Transfer-Encoding. (templates/php/default_api_client.mustache)
- kotlin: Build the part list with PartData.BinaryItem/FormItem and send MultiPartFormDataContent(parts), bypassing formData()/FormBuilder.append that injects Content-Length. (templates/kotlin/default_api_client.mustache)
- java: already canonical
- csharp: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** No YAML: addPetPhotos (files + JSON metadata part, openapi.yaml:420-456) and uploadPetDocument (file + scalar companions, :611-662) cover all part kinds. Shared fixture/table row in every DefaultApiClientTest/UnitTest (php tests/DefaultApiClientUnitTest.php, kotlin DefaultApiClientUnitTest.kt via MockEngine reading the outgoing body text, plus the other 10 as table rows): for each part header block assert the set of header names is a subset of {Content-Disposition, Content-Type}. This also locks the php Psr18ApiClient/Symfony parity.

**Risk.** php: the DataPart subclass must still produce a correct Content-Disposition with filename/filename*. kotlin: building PartData items directly changes how the body is streamed; verify against the existing mock-server specs.

## D90 Multipart filename directive: one rule (go/php/rust form) in all 12

- Ledger ids: F253 | Theme: serialization-wire-edge-cases | Still true: **yes** | Effort: M | Wire-visible: True | Needs user: False

**Current state.** Re-checked for `résumé*(1).pdf` and an emoji name. Canonical form (go base_api.go:649-692, php AbstractApiClient.php:720-745, rust default_api_client.rs:791-830): ASCII-only names -> `filename="..."` with `\` and `"` escaped; otherwise `filename="<fallback>"; filename*=UTF-8''<enc>` where fallback replaces each Unicode code point that is non-ASCII or a control with one `_`, and enc percent-encodes every UTF-8 byte except A-Z a-z 0-9 - . _ ~. Divergent: java (DefaultApiClient.java:1178-1200) fallback regex works per UTF-16 unit (emoji -> `__`), filename* already correct; kotlin (DefaultApiClient.kt:797-815), csharp (DefaultApiClient.cs:1021-1050) and dart (abstract_api_client.dart:903-920) iterate UTF-16 units (emoji -> `__`), filename* correct; python (default_api_client.py:150-168) fallback is `?` (encode('ascii', errors='replace')), filename* correct; elixir (default_api_client.ex:1174-1182) fallback replaces per BYTE (`r__sum__`), filename* correct; ruby (default_api_client.rb:~608-625) has NO ASCII fallback (raw UTF-8 in filename=) and encode_www_form_component keeps `*` and encodes `~`; node (abstract-api-client.ts:~810-823) filename* uses encodeURIComponent, which keeps ! ' ( ) *; swift (BaseApi.swift:~528-545) fallback per scalar (fine) but its filename* safe set adds !#$&+^`|.

**Canonical behaviour.** The go/php/rust rule above: per-code-point `_` fallback (never `?`, never per byte/UTF-16 unit) and a filename* that keeps exactly the RFC 3986 unreserved set `A-Za-z0-9-._~`.

**Why.** RFC 6266/5987: the fallback only has to be ASCII, and attr-char is a superset of unreserved, so the strict set is valid everywhere and removes the `'` ext-value-delimiter hazard (a). Already implemented by go, php, rust (and filename* by 8 languages) (b). Pure string code, no dependency (d).

**Rejected.** Keep per-language safe sets: rejected, node's `'` is the RFC 5987 delimiter and python's `?` makes distinct names collide. Adopt the larger RFC attr-char set (swift): rejected, no benefit and more divergence.

**Per language.**

- java: Fallback: iterate code points (String.codePoints) and replace each non-ASCII/control code point with one `_`. (templates/java/default_api_client.mustache (buildFilenameDirective))
- kotlin: Iterate code points (codePoints()/StringBuilder.appendCodePoint) in the fallback. (templates/kotlin/default_api_client.mustache (buildFilenameDirective))
- csharp: Enumerate runes (EnumerateRunes) instead of chars in the fallback. (templates/csharp/default_api_client.mustache (BuildFilenameDirective))
- php: already canonical
- python: Replace encode('ascii', errors='replace') with a per-code-point `_` join. (templates/python/default_api_client.mustache)
- ruby: Add the ASCII fallback (each non-ASCII/control character -> `_`) and a hand-written percent-encoder keeping only unreserved characters. (templates/ruby/default_api_client.mustache (build_content_disposition))
- node: Replace encodeURIComponent with a byte-wise unreserved-only encoder. (templates/node/abstract_api_client.mustache (buildContentDisposition))
- dart: Iterate runes (filename.runes) instead of codeUnits in the fallback. (templates/dart/abstract_api_client.mustache (buildFilenameDirective))
- go: already canonical
- rust: already canonical
- swift: Drop the extra `!#$&+^`|` from the allowed set for filename*, keep `-._~`. (templates/swift/base_api.mustache (buildContentDisposition))
- elixir: Make to_ascii_fallback iterate graphemes/codepoints (String.codepoints/1) instead of bytes. (templates/elixir/default_api_client.mustache (to_ascii_fallback))

**Tests.** Unit table in every DefaultApiClient unit test (the buildFilenameDirective/build_content_disposition helpers are public or testable in all 12, e.g. csharp DefaultApiClient.BuildFilenameDirective, go buildFilenameDirective, elixir build_content_disposition/2): inputs `résumé*(1).pdf`, `a"b\\c.txt`, `😀.png`, `plain.txt`; expected `filename="r_sum_*(1).pdf"; filename*=UTF-8''r%C3%A9sum%C3%A9%2A%281%29.pdf`, the emoji case `filename="_.png"; filename*=UTF-8''%F0%9F%98%80.png`, and the ASCII cases unchanged. No YAML.

**Risk.** Low; filenames are only ever caller-supplied via file handles or the field name. Existing filename tests assert current strings and must be updated.

## D91 Declared-charset decoding: documented minimum set plus lenient UTF-8 fallback that always yields a valid string

- Ledger ids: F258 | Theme: serialization-wire-edge-cases | Still true: **partly** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Re-checked. dart now decodes latin1/ascii/windows-1252 (via a table)/utf-16 BOM-aware, anything else falls back to lenient UTF-8 with U+FFFD (abstract_api_client.dart:706-748). elixir now has windows-1252 (default_api_client.ex:586-599), latin1, utf-16/32, but any unknown label returns the RAW body bytes (decode_text_body :480-530), which can be an invalid UTF-8 string. go supports utf-8/ascii/latin1/windows-1252/utf-16 and returns string(body) raw for anything else (default_api_client.go:526-552), also possibly invalid UTF-8. swift decodes about 16 labels and falls back leniently with U+FFFD. java/kotlin (JVM charsets), csharp (CodePages), python, ruby, php (mbstring), node (TextDecoder) and rust (encoding_rs) decode any registered charset (ledger audit).

**Canonical behaviour.** Minimum guaranteed set in every SDK: utf-8, us-ascii, iso-8859-1, windows-1252, utf-16 (BOM-aware; bare label big-endian without BOM), utf-16le/be. Languages with a charset registry (java, kotlin, csharp, python, ruby, php, node, rust) decode everything it knows; languages without one (go, dart, elixir, swift) decode the minimum set and, for any other label (shift_jis, iso-8859-2, ...), fall back to a lenient UTF-8 decode that substitutes U+FFFD, so the returned string is always valid. This is a documented FORCED difference: go, dart, elixir and swift have no multi-charset stdlib and a dependency is not allowed.

**Why.** Same-state across the four no-registry languages after the cp1252 fixes (b); a valid string with U+FFFD never crashes downstream JSON/UTF-8 consumers while raw invalid bytes can (c); a charset library would be a new dependency (d).

**Rejected.** Ship a shift_jis/iso-8859-x table in each of the four: rejected, large data tables for rare use. Raise on unknown charset: rejected, the documented contract is 'fall back, never throw'.

**Per language.**

- go: default branch: strings.ToValidUTF8(string(body), "�") instead of the raw string; AGENTS forced note. (templates/go/default_api_client.mustache (decodeBodyByCharset))
- elixir: Unknown label (nil encoding) returns String.replace_invalid(body, "�") (Elixir 1.16+, floor is 1.17) instead of body. (templates/elixir/default_api_client.mustache (decode_text_body))
- dart: already canonical
- swift: already canonical
- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- rust: already canonical

**Tests.** Existing text-plain endpoint (/test/text-plain, openapi.yaml:1406) in each DefaultApiClientUnitTest/BaseApiTest: charset=windows-1252 byte 0x80 -> '€' in all 12; charset=shift_jis with bytes 0x83 0x41 -> a valid string in all 12 (exact text only asserted for the 8 registry languages, 'contains U+FFFD and is valid UTF-8' for go/dart/elixir/swift). No YAML.

**Risk.** Low.

## D92 Response headers returned to the caller drop Content-Encoding and Content-Length whenever the body was decoded

- Ledger ids: F261 | Theme: serialization-wire-edge-cases | Still true: **yes** | Effort: L | Wire-visible: False | Needs user: False

**Current state.** Re-checked rust: after decompress_body succeeds it removes content-encoding and content-length from the returned map (default_api_client.rs:457-465). csharp: AutomaticDecompression = DecompressionMethods.All (DefaultApiClient.cs:146) makes HttpClient strip both from Content.Headers before the SDK sees them. swift: URLSession decodes gzip/deflate itself and on Apple strips Content-Encoding; on Linux the header survives (AGENTS.md notes this). java, kotlin, python, ruby, php, node (fetch), dart, go (Accept-Encoding set explicitly) and elixir hand the wire content-encoding and the compressed content-length through ApiHttpResponse.headers and ApiException.responseHeaders (ledger audit, not re-run here).

**Canonical behaviour.** ApiHttpResponse.headers and ApiException.responseHeaders describe the body the caller receives: whenever the SDK (or its runtime) decoded a non-identity Content-Encoding, Content-Encoding and Content-Length are removed from the returned headers. An identity or absent Content-Encoding leaves both untouched.

**Why.** This is what the mainstream HTTP clients do when they decompress transparently (Go net/http, OkHttp, Apache HttpClient, .NET, Apple URLSession, reqwest, which the rust code comment already cites), so csharp/swift/rust are right and the nine are the minority (b). A caller comparing Content-Length with the body length then never sees a mismatch (c). Preserving wire headers is not reachable: csharp and swift cannot restore headers their runtime already removed (named features: HttpClientHandler.AutomaticDecompression and URLSession), whereas stripping is feasible everywhere (d).

**Rejected.** Preserve wire headers everywhere (rust stops stripping): rejected, csharp and swift would become forced exceptions and the preserved Content-Length would be wrong for the delivered body. Leave it as documented runtime-dependent behaviour: viable and cheaper, but keeps a caller-visible difference with no forcing reason.

**Per language.**

- rust: already canonical
- csharp: already canonical
- swift: After URLSession returns, if Content-Encoding is gzip/deflate (decoded by Foundation) remove Content-Encoding and Content-Length; no-op on Apple where it is already gone. (templates/swift/default_api_client.mustache)
- java: Remove both headers from the response map after the successful manual decompress. (templates/java/default_api_client.mustache)
- kotlin: same, after decompress (templates/kotlin/default_api_client.mustache)
- python: same, after decompress (case-insensitive keys) (templates/python/default_api_client.mustache)
- ruby: same, after decompress (templates/ruby/default_api_client.mustache)
- php: same, after decompress (templates/php/default_api_client.mustache, templates/php/abstract_api_client.mustache)
- node: fetch already decoded: when Content-Encoding names gzip/deflate/br/zstd remove it and Content-Length from the header object handed back. (templates/node/abstract_api_client.mustache)
- dart: same, after decompress (io path); web path follows node rule (templates/dart/abstract_api_client.mustache)
- go: same, after decompress (templates/go/default_api_client.mustache)
- elixir: same, after decompress (templates/elixir/default_api_client.mustache)

**Tests.** Existing compressed endpoints /test/compressed/gzip, /br, /zstd (openapi.yaml:1260-1300): in each DefaultApiClientTest add to the existing gzip test an assertion that the returned headers contain neither content-encoding nor content-length while the body decodes (table row for br/zstd where the language supports them). Add one identity-response assertion that both headers are untouched. No YAML.

**Risk.** Low, header contents only. Do not strip when the SDK did not decode (identity, or the AGENTS-documented node/swift/dart-web pass-through of an unknown coding), and strip case-insensitively.

## D93 dart findPetsBornOn comment says date is a String, but it is a DateTime passed through stringifyDate

- Ledger ids: F241 | Theme: serialization-wire-edge-cases | Still true: **yes** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** Re-checked: pet_api.dart:614-619 comment says 'A `format: date` parameter is mapped to `String` (already in YYYY-MM-DD form), so it flows through serializeStyled like any other string with no special casing', but the code below takes `DateTime bornOn` and calls stringifyDate(bornOn) (:627). The same text lives in the template (templates/dart/api/api.mustache:250-253). The other 11 generated comments for this op are accurate.

**Canonical behaviour.** The comment states the truth: a `format: date` parameter is a DateTime, converted to YYYY-MM-DD by stringifyDate before serializeStyled, and the result is still encoded exactly once.

**Why.** Doc-truth after R5-1 changed dart date params to DateTime (a stale comment is a defect, AGENTS wants generated prose to match code).

**Rejected.** Delete the sentence: acceptable, but the double-encoding explanation is useful, so correct it instead.

**Per language.**

- dart: Reword the comment in the api template: 'A `format: date` parameter is a DateTime; stringifyDate renders it as YYYY-MM-DD before serializeStyled.' (templates/dart/api/api.mustache:250-253)
- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** No test; the regenerated golden diff is the lock. Behaviour is already covered by dart test/pet_api_test.dart findPetsBornOn and the bornOn path assertions.

**Risk.** None; comment-only change, regenerate the dart golden.

## D94 Verified fixed in the current goldens (no action; locks exist)

- Ledger ids: F073 F074 F077 F079 F083 F248 F250 F251 F255 F256 F257 F259 | Theme: serialization-wire-edge-cases | Still true: **no** | Effort: S | Wire-visible: False | Needs user: False

**Current state.** F073: java rejects non-string OffsetDateTime (ObjectSerializer.java:465-478), python _reject_non_string_date_time (_types.py:~85-100), node @Transform rejects non-string (models/order.ts:34-51); tests java ObjectSerializerTest.java:398, python test_object_serializer.py:1185-1195, node object-serializer.test.ts:896. F074: go isJSONNull guard in all 33 convenience ops (pet_api.go:209.., store_api.go; object_serializer.go:92-97; test :1637), rust deserialize returns Ok(None) for null (object_serializer.rs:385, test :603). F077: ruby raises SerializationError (object_serializer.rb:456-459, tests object_serializer_test.rb:415-431), elixir fallback clause raises (object_serializer.ex:1096-1101), node Pet constructor throws 'category must be an object' (models/pet.ts:109-115). F079: go dropMiscasedKeys in every UnmarshalJSON (models/json_marshal.go:37, order.go:130), python deserialize uses by_alias=True, by_name=False (object_serializer.py:442-489, tests test_object_serializer.py:1470-1490). F083: python LaxFloat has allow_inf_nan=False and _decimal_to_json_number rejects non-finite (_types.py:51-64, 118-130, tests :716-740). F248: php asJsonObject wraps list-shaped maps in ArrayObject (ObjectSerializer.php:~270-290, tests ObjectSerializerTest.php:1276,1284). F250: swift formatDateTime truncates (ObjectSerializer.swift:~35-45, tests ObjectSerializerTests.swift:282-289). F251: rust/swift/dart/node keep an explicit empty User-Agent and use presence checks (default_api_client.rs:170-178, DefaultApiClient.swift:263, abstract-api-client.ts:245, tests default_api_client_unit_test.rs:222, DefaultApiClientUnitTests.swift:59, default-api-client-unit.test.ts:214). F255: ruby builds form pairs via ObjectSerializer.stringify and skips nil elements (base_api.rb:~266-276, test base_api_test.rb:519). F256: rust base_api retains-then-pushes auth query params (base_api.rs:171-177, test base_api_test.rs:1240). F257: csharp/swift/elixir sniff UTF-16 BOMs (DefaultApiClient.cs:~1215, DefaultApiClient.swift:596-607, default_api_client.ex:559-570, tests csharp DefaultApiClientUnitTest.cs:722, elixir default_api_client_unit_test.exs:524, swift :853). F259: go deletes Content-Type/Content-Length when the follow-up has no body (default_api_client.go:~378-385, test default_api_client_unit_test.go:850), python drop_body_headers also covers method demotion (default_api_client.py:~679-690), rust keeps Content-Length 0 only when the method is kept (default_api_client.rs:~375-392).

**Canonical behaviour.** Already canonical: non-string date-time rejected; bare null body is an absent body; non-object where a model is expected raises SerializationError; only exact wire property names populate fields; NaN/Infinity rejected on model bodies; numeric-only additionalProperties still encodes an object; swift truncates (never rounds) sub-millisecond; an explicit empty User-Agent is sent as given; ruby form bodies go through ObjectSerializer.stringify and skip nil elements; the auth query param replaces a same-named operation param; a bare utf-16 body honours its BOM; a body-less redirect follow-up drops Content-Type and Content-Length.

**Why.** Each matches the ledger's own proposed fix and the majority behaviour, verified against the current goldens and backed by a test.

**Rejected.** None needed. Mark the ledger entries fixed (F073 F074 F077 F079 F083 F248 F250 F251 F255 F256 F257 F259).

**Per language.**

- java: already canonical
- kotlin: already canonical
- csharp: already canonical
- php: already canonical
- python: already canonical
- ruby: already canonical
- node: already canonical
- dart: already canonical
- go: already canonical
- rust: already canonical
- swift: already canonical
- elixir: already canonical

**Tests.** Existing locks cited above. Two gaps worth closing with no YAML: go has no model-level assertion beyond pkg/object_serializer_test.go for dropMiscasedKeys on every model (add an it.each-style row over Order/Pet), and the F251 explicit-empty-User-Agent case is covered in rust, swift and node only (add the same unit test to dart default_api_client_unit_test.dart).

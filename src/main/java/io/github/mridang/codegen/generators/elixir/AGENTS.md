# Elixir SDK generator

The `elixir-plus` generator (`BetterElixirCodegen`) emits a Mix project: a `lib/<package>/` tree of one module per API group, model, error and authenticator, plus `mix.exs`, `.formatter.exs`, `.credo.exs`, a `Makefile`, `README.md`, `SKILLS.md` and an ExUnit suite under `test/`. The HTTP stack is Req (`{:req, "~> 0.5"}`, which sits on Finch and Mint), not a hand-rolled client; JSON is Jason, with optional Brotli. The test runner is ExUnit with ExCoveralls and `junit_formatter`, and the containerised fixtures use `testcontainers`. The floor is Elixir `~> 1.17` (`mix.exs`), the specs run on `elixir:1.19-alpine`, and the lint gates are `mix compile --warnings-as-errors`, `mix credo --strict`, Dialyzer (flags in `mix.exs`) and `mix format --check-formatted`.

## Read first

- Shared cross-language rules: [`../AGENTS.md`](../AGENTS.md)
- Repository root: [`../../../../../../../../../AGENTS.md`](../../../../../../../../../AGENTS.md)
- Codegen class: [`BetterElixirCodegen.java`](BetterElixirCodegen.java)
- Templates: [`src/main/resources/templates/elixir/`](../../../../../../../resources/templates/elixir/) (its pointer file leads back here)
- Spec classes: `src/spec/java/io/github/mridang/codegen/spec/elixir/` (`ElixirSpec` holds the image and cache environment; `ElixirClientSpec`, `ElixirLintingSpec`, `ElixirFormattingSpec`, `ElixirReservedWordsSpec`)
- Golden: `src/spec/resources/generated/elixir/`
- Reserved words: `src/main/resources/reserved-words/elixir.txt`

## Layout

| Path | What lives there | Edit this to change |
| --- | --- | --- |
| `BetterElixirCodegen.java` | type and naming rules, supporting-file list (`getSupportingFileSpecs`), OAuth wrapper rendering, escaping, Mustache lambdas | file names, identifier casing, default literals, string escaping, which tests are emitted |
| `templates/elixir/errors/*.mustache` | one module per error type (`ApiError`, status errors, `NetworkError`, `NetworkTimeoutError`, `SerializationError`, `OAuth2ServerError`, `OAuth2TokenError`, the root `<Prefix>Error`) | error classes and their rendering |
| `templates/elixir/auth/`, `auth/oauth/` | one authenticator per scheme kind, the OAuth2 token manager (an Agent), OIDC discovery | auth, token handling |
| `templates/elixir/transport_options.mustache`, `default_api_client.mustache`, `client.mustache`, `configuration.mustache` | transport settings, the Req-backed client (proxy, TLS, redirects, deadlines, decompression, multipart), the facade, `Configuration.new/1` | transport options, redirect and timeout behaviour |
| `templates/elixir/base_api.mustache`, `value_serializer.mustache`, `object_serializer.mustache`, `header_selector.mustache` | request building, parameter styles, JSON encode and decode | wire encoding and decoding |
| `templates/elixir/api/api.mustache`, `api/options.mustache` | one module per API group, the per-operation options structs | operations and their signatures |
| `templates/elixir/models/model.mustache`, `models_module.mustache` | structs, typespecs, oneOf/anyOf builders, discriminator dispatch | models |
| `templates/elixir/test/*.mustache` | the ExUnit suite emitted into every generated SDK | tests |
| `readme.mustache`, `skills.mustache`, `mix_exs.mustache`, `dev_dependencies.mustache` | README, SKILLS.md, the manifest and the generator-owned list of test dependencies | docs, dependencies |

## Idioms (deliberate differences from the other eleven)

| Topic | What Elixir does | Why |
| --- | --- | --- |
| Construction | `Configuration.new/1` and `TransportOptions.new/1` take a keyword list; there is no builder (`default/0` mirrors the other languages' default configuration) | Data is immutable and there are no mutable objects to chain. Allow-listed in `etc/api-surface-allow.json`. |
| Receiver | The api struct is the first argument of every operation | No instance methods. |
| Optional arguments | Trailing optional arguments (server override, content-type selector, refresh URL) fold into one `opts` keyword list, validated with `Keyword.validate!` | Function arity and keyword lists are the language's way to take options. |
| Required operation options | A missing required option raises `ArgumentError` at call time; options structs do not use `@enforce_keys` | `@enforce_keys` would raise a `KeyError` when the struct is built, so the error would differ from the other SDKs. |
| Error hierarchy | No inheritance: `api_error?/1` is the is-a predicate and `exceptions/0` lists the hierarchy for `rescue` clauses | Exceptions are plain structs. |
| Union models | oneOf/anyOf `build` returns the matched variant (anyOf may return an `AnyOfComposite` struct); there is no wrapper object, so `actual_instance`/`matched_instances` are module functions | Elixir has no wrapper class to hold a value. |
| Authenticators | A behaviour plus `__using__/1`; the token manager is an Agent started by `start_link/1` and stopped by `Client.close/1` (optional `close/1` callback); `NO_AUTH` is a unique atom | OTP conventions; no implementation inheritance. |
| Sync only | One `get_auth_headers`, no async variant | The HTTP client is synchronous. |
| Binary and `format: number` | `format: binary` is raw bytes; bare `number` is the native `float()`, and a caller who needs exact decimals parses the body with `Decimal` (documented in the README) | Erlang floats are IEEE-754 doubles. |
| uniqueItems | Held as a `MapSet`, sent in the `MapSet`'s own order, not sorted (S6) | The natural collection order is the rule across all languages. |
| Secrets in structs | Structs are transparent maps; the authenticators mask secrets through `defimpl Inspect` | There is no private field. See "Inspect bypass" below. |
| Deprecation | Deprecated operations carry `@deprecated`; deprecated model fields get a `**Deprecated field**` note in `@moduledoc` | `@deprecated` attaches to functions only, not struct fields. |

## Settled decisions (do not re-open in review)

The cross-language rules live once in the shared file; each entry says how Elixir realises one. "Intended" in a Rule line means the decision is agreed but I could not confirm it in the templates at the time of writing, so a reviewer should treat the template as the thing to fix, not the decision as the thing to debate.

### Cross-language decisions of the 2026-11-16 session (S1 to S16)

Rule: Elixir follows the shared decisions S1 to S16 (strict containers, content parameters, schema constraints, allOf discriminator, required plus nullable, uniqueItems order, cancellation, parameter order, injected transport, dependencies, out-of-scope list, test speed, scope order and ASCII-blank keys, secrets redaction, timeouts) and the entries below state the Elixir realisation of each. Why: one reading for all twelve. Where: see each entry. Test: see each entry.

### allOf with a discriminator flattens into each struct (S4, F057, F802, 2026-11-16)

Rule: intended. Each subtype is a flat struct; the base module carries a dispatch function that reads the discriminator, and a behaviour declares the shared accessors. A model reused in two unions is never re-parented. Why: structs cannot inherit; a class-style parent was rejected. Where: `models/model.mustache`, `BetterElixirCodegen` (`setsDiscriminatorDefaultOnChildren` already fills the discriminator default on subtypes). The behaviour and the allOf flattening are not in the template yet. Test: `test/composed_schema_test.mustache`.

### Required plus nullable (S5, F215, D66, 2026-11-16)

Rule: a required, nullable property must be present when decoding (missing is a `SerializationError`), an explicit `nil` is kept, and encoding always writes the key. Why: the key and its value are separate facts. Where: `models/model.mustache` (`required_fields/0` for required non-nullable keys, `required_nullable_fields/0` for key presence only). Test: `test/composed_schema_test.mustache`, `test/object_serializer_test.mustache`.

### Content parameters use the SDK serializer (S2, F054, F068, 2026-11-16)

Rule: a `content: application/json` parameter in query, header, path or cookie goes through `ObjectSerializer.serialize/1`, never `Jason.encode!`. Why: identical bytes in all twelve. Where: `api/api.mustache` (`vendorExtensions.param.queryContent` branches in each location). Test: `test/value_serializer_test.mustache`, `test/base_api_test.mustache`.

### Schema constraints are not enforced (S3, F056, F137, 2026-11-16)

Rule: `pattern`, length, range, `multipleOf` and item-count keywords are not checked by the client; the model layer checks only that required properties are present and values have the declared type. The README section "Schema constraints" says so. Why: Elixir structs and typespecs carry no constraints, and the server owns validation. Where: `readme.mustache`. Test: README text only; no behaviour to test.

### uniqueItems order is unspecified (S6, F319, 2026-11-16)

Rule: `MapSet` values are sent in the `MapSet`'s own order; do not add sorting. Why: the owner decided against sorting, which supersedes the earlier "sorted" wording in F319 and F072. Where: `object_serializer.mustache` (`sanitize_for_serialization` for `%MapSet{}`), `readme.mustache`. Test: `test/object_serializer_test.mustache`.

### Cancellation has no new API (S7, F228)

Rule: there is no cancellation parameter. A caller stops a call by terminating its own process, and the end-to-end timeout kills the request task (`Task.shutdown`). Why: Elixir cancels with process exit signals; a token or context argument would be foreign. Where: `default_api_client.mustache`. Test: `test/default_api_client_test.mustache`. The cancellation error versus the timeout error is intended and not confirmed in the template.

### Parameter order (S8, F803, owner confirmation pending)

Rule: required arguments come first and everything optional (server, content type) travels in the trailing `opts` keyword list, with or without security, so adding security never moves an argument. Per-operation auth rides in the options struct, not in `opts`. Why: keyword options are the Elixir idiom for what the other languages order positionally. Where: `api/api.mustache`. Test: `test/base_api_test.mustache`.

### Injected transport is Req options (S9, F405)

Rule: `TransportOptions.new(req_options: [...])` accepts any `Req.new/1` options (a custom `:adapter` or `:plug`); the SDK's own settings win and are merged key by key. Passing `:connect_options` or `:finch` together with `proxy`, `ca_cert_path` or `verify_ssl: false` raises `ArgumentError`. Why: Req is the standard HTTP client object here, and a silent override of the SDK's proxy or TLS settings is worse than a failure. The SDK never starts or stops the global Finch pool, so there is nothing of the caller's to close. Where: `transport_options.mustache`, `default_api_client.mustache` (`merge_req_options`). Test: `test/transport_options_test.mustache`, `test/default_api_client_unit_test.mustache`.

### Dependencies (S10)

Rule: Req, Jason, Mime and an optional Brotli are the runtime dependencies. A pure-Elixir library or a version bump is acceptable; a dependency that needs a native binary is not. Why: the cross-language rule, applied here. Where: `mix_exs.mustache`, `dev_dependencies.mustache`. Test: the generated suite fails with a message naming `DEV-DEPENDENCIES` when a test dependency is missing from `mix.exs`.

### Out of scope (S11, issues #53, #70, #78, #88, #90)

Rule: callbacks and webhooks, XML bodies, Link Objects and mutual TLS are not generated, and the README says so. Why: closed backlog issues. Where: `readme.mustache` (the "Not supported" section names webhooks and callbacks, `dependentRequired` and SOCKS; the XML, Link Object and mutual TLS lines landed in batch 6). Test: none.

### Scope order, UUID case, blank keys, error cause (S14, F018, F288, F217, F274, F275)

Rule: OAuth scopes keep the spec's declared order (the wrapper test in `oauth2_client_credentials_authenticator_test.mustache` sorts them today, so it cannot see order; assert the unsorted list); uuid is written lowercase and validated (`encode_uuid_param`); an API key or bearer token is blank only when empty or ASCII whitespace (intended: the templates still use `String.trim/1`, which strips Unicode whitespace); `ApiError` accepts a `:cause`. Why: shared decision. Where: `auth/api_key_authenticator.mustache`, `auth/bearer_authenticator.mustache`, `errors/api_error.mustache`, `AbstractBetterCodegen` (scope order). Test: `test/api_key_authenticator_test.mustache`, `test/bearer_authenticator_test.mustache`, `test/api_error_test.mustache`.

### Secrets are redacted (S15)

Rule: `inspect/1` of `TransportOptions` shows the proxy password and every sensitive default header (`Authorization`, `Proxy-Authorization`, `Cookie`, anything containing key, token or secret) as `***`; the authenticators mask their secrets through `defimpl Inspect`. Why: logs must not leak credentials. Where: `transport_options.mustache` (`redact_proxy/1`, `redact_headers/1`), `auth/**`. Test: `test/transport_options_test.mustache`, the authenticator tests.

### Timeouts, redirects, proxy and CA validation (S16, F094, F407, F295, F121)

Rule: `timeout` is one end-to-end budget: the deadline is fixed once and every redirect hop spends what is left, enforced by running each hop in a `Task` and shutting it down on overrun. `ca_cert_path` is validated whenever it is set, even with `verify_ssl: false`. A caller-supplied stream or file handle is never closed by the SDK. `timeout <= 0` is rejected with `Invalid timeout: must be a positive number of milliseconds (got N)` (`nil` still means no timeout). Moving proxy validation to `build()` has no Elixir meaning (there is no builder); it is checked in `new/1`. Why: Req's `:receive_timeout` is per chunk and resets on every packet, so it cannot bound a slow trickle. Where: `default_api_client.mustache` (`do_request_with_redirects`), `transport_options.mustache`. Test: `test/default_api_client_test.mustache`, `test/transport_options_test.mustache`.

### No ambient proxy, caCertPath pins, SDK proxy credentials survive redirects (F090, F020, F091)

Rule: no proxy unless `TransportOptions.proxy` is set; `ca_cert_path` replaces the system trust store; the SDK's own `Proxy-Authorization` is built into the connection options and is not stripped on a cross-origin redirect. Why: one transport rule for all twelve. Where: `default_api_client.mustache` (`request_options`, `proxy_request_options`), `transport_options.mustache`. Test: `test/default_api_client_test.mustache`. The template does not pass `proxy: :none` and relies on Req not reading the environment; that was not re-verified.

### 429 error and redirect methods (F831, F839)

Rule: HTTP 429 maps to `TooManyRequestsError` (a `ClientError`, listed in `ApiError.from_response/3`, `ClientError`, the root error list and the README-style docs). Redirects follow RFC 9110 section 15.4 through `DefaultApiClient.redirect_method/2`: 307 and 308 keep the method, 303 turns everything but HEAD into GET, 301 and 302 turn only POST into GET. Content cookies are `URI.encode(..., &URI.char_unreserved?/1)` of the serializer JSON, which is already the shared unreserved set (F833). Why: one rule for all twelve. Where: `errors/too_many_requests_error.mustache`, `errors/api_error.mustache`, `default_api_client.mustache`. Test: `test/api_error_test.mustache`, `test/default_api_client_unit_test.mustache`, `test/value_serializer_test.mustache`.

### Header injection is case-insensitive (F299)

Rule: `User-Agent` and `X-Request-ID` are injected only when no header of any casing is already present; the caller's header always wins. Why: HTTP field names are case-insensitive. Where: `default_api_client.mustache` (`has_header?/2`). Test: `test/default_api_client_unit_test.mustache`.

### OAuth token handling (F016, F017, F119, F112, F113, F286, F292, F293, F116)

Rule: OIDC rediscovery keeps the token manager and its exchanged flag; a refresh carries client authentication through `get_access_token/5` with a `:refresh_url` option; `exchange/4` replaces the session only on success and restores it on failure; an empty implicit-flow token counts as unset; the token body and authorize URL use one fixed key order and drop an empty `state`; OIDC always requests `openid`. Still intended and not in the templates: the single-flight discovery fetch, the one-year ceiling and decimal grammar for `expires_in`, and the canonical message `Must call exchange_code() before making API requests` (the template word differs). Why: shared OAuth contract. Where: `auth/oauth/*.mustache`. Test: `test/oauth2_token_manager_test.mustache` and the per-flow authenticator tests.

### Error messages (F115, F245, F260, F267, F409, F099, F100)

Rule: landed in batch 6. One wording set for token and discovery errors (`Token response is not valid JSON` versus `... is not a JSON object`), serializer errors (`Failed to serialize object to JSON: <detail>`, `Failed to deserialize JSON: <detail>`), redirect refusals (capitalised canonical strings), `Response headers: <compact JSON, sorted keys>`, use after close (`ApiClient has been closed and can no longer send requests`, a `RuntimeError`), and real response headers on every `ApiError` raised for a response that arrived but was unusable. Pinned by tests in `api_error_test`, `object_serializer_test`, `oauth2_token_manager_test`, `default_api_client_test` and `default_api_client_unit_test`. Why: byte-identical messages across twelve. Where: `default_api_client.mustache`, `object_serializer.mustache`, `errors/api_error.mustache`, `auth/oauth/*.mustache`. Test: `test/api_error_test.mustache` (add table rows for the exact rendering).

### Response bodies: binary, byte, non-JSON (F033, F085, F149, F150, F151, F153, F155)

Rule: a `format: binary` return is raw bytes, never JSON-parsed; `format: byte` follows the JSON path with one strict base64 decoder; a non-JSON success Content-Type returns the text for a `String.t()` return and raises `SerializationError` otherwise. The strict final clause landed in batch 6 (`String` and `String.t()` returns keep the text). Why: one rule per Content-Type class. Where: `base_api.mustache`, `api/api.mustache`. Test: `test/base_api_test.mustache`.

### Request bodies: binary, multipart, form (F035, F084, F214, F216, F087)

Rule: a binary body is sent verbatim under any selected content type (`{:raw_body, bytes}`); multipart scalar parts, including `DateTime`, `Date`, `Time` and `Duration`, are plain text parts built with `ObjectSerializer.stringify` and only a map or model becomes a JSON part; a form-urlencoded field that is a map or nested list raises `SerializationError` (`ensure_form_scalar!`). Query, form and multipart order follows spec declaration order through lists of `{key, value}` tuples. Why: no per-language repr of nested values on the wire. Where: `base_api.mustache`, `default_api_client.mustache`, `api/api.mustache`. Test: `test/base_api_test.mustache`, `test/default_api_client_test.mustache`.

### Encoding: allowReserved, percent-encoding, path dots (F066, F387, F086, F064, F069, F072)

Rule: query and form encoding keeps only `A-Za-z0-9-._~` literal; a path value of exactly `.` or `..` is rejected before dispatch (`ArgumentError`); `format: byte` parameters are base64-encoded (`encode_base64_param`). Narrowing `allowReserved` to the RFC 3986 query set and expanding a `MapSet` like an array are intended, not confirmed. Why: shared encoding contract. Where: `value_serializer.mustache`, `base_api.mustache`, `object_serializer.mustache`. Test: `test/value_serializer_test.mustache`, `test/base_api_test.mustache`.

### Models: decode, defaults, equality (F025, F055, F080, F081, F129, F131, F138, F139, F206, F227, F316, F321, F322, F325)

Rule: `readOnly` and `writeOnly` have no wire effect and appear as a `Read-only` or `Write-only` note in `@moduledoc`; an inline enum atom outside the declared values raises `SerializationError` on encode; required properties are checked on decode; a required property with a default is filled when absent; the declared value wins over a colliding `additionalProperties` key. Untyped struct fields and wrong-typed scalars are still not validated on construction. Why: shared model contract. Where: `models/model.mustache`, `object_serializer.mustache`. Test: `test/object_serializer_test.mustache`, `test/composed_schema_test.mustache`.

### Per-operation servers (F122, F145, F148, F306)

Rule: one module per server variable per operation, named `<Variant><Var>` only where a name repeats across variants; the typed server variants validate enum values in `url/1`; the `:server` option exists only on operations that declare `servers:` and resolves through the variant's own `url/1`. Why: one type per variable, no dead variant modules. Where: `api/api.mustache`, `BetterElixirCodegen.enrichOperationServers`. Test: `test/server_variable_test.mustache`.

### Identifiers, enum literals, reserved words (F172, F363, F173, F044, F047, F045, F309, F364)

Rule: a digit-leading name gets the letter `n` before casing; model names that collide with SDK module names take the `Model` prefix; enum string literals escape the quote, backslash and the interpolation opener `#{` (written `\#{`); text that lands in an interpolating heredoc goes through `escapeUnsafeCharacters`. Dropping the escape for `and`, `or` and `not` is intended only if they compile as struct keys under `--warnings-as-errors`; that was not verified. Why: generated source must evaluate to exactly the spec's bytes. Where: `BetterElixirCodegen` (`toDefaultValue`, `escapeUnsafeCharacters`, `toHeredocText`, `getEnumStringEscapes`), `models/model.mustache`, `reserved-words/elixir.txt`. Test: `ElixirReservedWordsSpec`, `ElixirFormattingSpec`.

### Charset and compression (F258, F261, AGENTS.md rounds 7 and 8)

Rule: `deflate` decodes zlib-wrapped data only, with no raw fallback; an unsupported `Content-Encoding` raises; an empty body passes through for any encoding; `windows-1252` decodes through a C1 table. Replacing an unknown charset with a lossy-valid string and dropping `Content-Encoding` and `Content-Length` from decoded responses are intended. Why: one codec contract. Where: `default_api_client.mustache` (`decode_text_body`, decompress helpers). Test: `test/default_api_client_test.mustache`.

### Redirect downgrade guard anchors on the current hop (AGENTS.md round 6)

Rule: the HTTPS to HTTP body-replay guard compares the current hop, not the original request, so an http, https, http chain is caught on the last hop. Why: the original-anchored check replayed a TLS body in cleartext. Where: `default_api_client.mustache`. Test: `test/default_api_client_test.mustache`.

### The "security: []" comment is gated (AGENTS.md round 6)

Rule: the no-auth sentinel note appears only on operations with `vendorExtensions.op.securityNone`; inherited-auth operations say they inherit the global requirement. Why: the old comment contradicted the code. Where: `api/api.mustache`. Test: `ElixirFormattingSpec`.

### Fixture names in templates (F476)

Rule: the Elixir test templates are petstore fixtures by design and the OAuth wrapper tests are registered without scheme-name flags (`getOAuthTestFileSpecs`); `FixtureVocabularyLeakTest` guards what a real client sees. Why: guarding them would need new codegen context across six test files, and the other SDKs bind to petstore the same way. Where: `BetterElixirCodegen.java`. Test: `FixtureVocabularyLeakTest`.

### Inspect bypass is accepted (F452, forced)

Rule: `inspect(auth, structs: false)` prints raw credentials and will keep doing so. Why: it is an Elixir opt-out that skips the `Inspect` protocol, so no `defimpl` can intercept it; closing it would mean not storing the secret in the struct, which breaks the authenticator design. Where: `auth/oauth/*_authenticator.mustache`. Test: none.

### Surface allow-list entries are decided (etc/api-surface-allow.json)

Rule: the Elixir rules in the allow-list are settled and the API-surface check must not report them: error members through `api_error?/1`, `extra*` props on transparent structs, `start_link/1`, `__using__/1`, `exceptions/0`, the receiver-first argument, the keyword-list constructors and `default/0`, no `BaseAuthenticator` members (a behaviour), `AnyOfComposite`, the module functions `actual_instance` and `matched_instances`, `Metadata.additionalProperties` being non-nil by its `%{}` default, no `@enforce_keys` on options structs, the trailing `opts` keyword list, and extra helpers on the support modules. Why: each is forced by structs, behaviours or the lack of visibility across files. Where: `etc/api-surface-allow.json`. Test: `etc/api-surface.py check`.

## Known limitations and out of scope

- Charsets: `shift_jis` and the other `iso-8859-x` labels return the raw bytes, because Erlang's `:unicode` has no multi-charset support (F258, partial).
- Struct fields are not validated on construction beyond inline enums: untyped fields and wrong-typed scalars pass (F316).
- Int32 and int64 range and fractional rejection on decode is intended (F213, F006) but not in the template; locking today's behaviour was refused because it pins an undecided result. The probe operation it needs is a spec change (needs-spec).
- Object-valued query and header parameters (F063), `format: time` sub-millisecond truncation (F071) and `format: uri` validation (F132) are parked or not reached.
- The warnings-fatal docs build is not run by any spec (F191, deferred): `makefile.mustache` runs `mix docs --warnings-as-errors`, but no `ElixirLintingSpec` step does.
- Spec gates differ from the client `Makefile`: `make build` and `make lint` run plain `mix compile`, while `ElixirLintingSpec` adds `--warnings-as-errors` (F382). The client suite does not use `--warnings-as-errors` because it calls deprecated operations.
- SOCKS proxies are rejected at construction (README).
- No per-field example or description in model docs (F311, F393); the `@moduledoc` carries the model description, named examples and the deprecated-field note.
- Webhooks, callbacks, XML bodies, Link Objects and mutual TLS are out of scope (S11). OpenAPI 3.1 and 3.2 support is tracked in epics #96 and #49 with sub-issues; loading 3.2 documents is #50.

## Build-breaking rules

- Generated code is compiled with `--warnings-as-errors` in `ElixirLintingSpec`, so an unused variable, alias or import fails the build; comments are `#` only.
- `mix credo --strict` runs over `lib/` and `test/` with the generated `.credo.exs`; name a rule in that file with a reason rather than suppressing in source.
- Dialyzer runs with `:error_handling`, `:extra_return`, `:missing_return`, `:underspecs`, `:unknown` and `:unmatched_returns`; every public function needs a correct `@spec`.
- `mix format --check-formatted` must pass; the generator formats twice (`getFormatterCommands`) because a super call ending in a keyword option is not a fixed point of one pass.
- Text from the spec that lands in an interpolating string or heredoc must be escaped for `#{` (`escapeUnsafeCharacters`, `toHeredocText`); a raw `#{` interpolates an undefined variable and breaks compilation.
- Keep `_build/` project-local in the specs. Relocating it (`MIX_BUILD_PATH`) made `Code.ensure_loaded?/1` miss generated structs and broke discriminator decoding.
- `testcontainers` pulls `fs`, which is declared `runtime: false, override: true` in `mix.exs` to stop an `inotifywait` warning; do not remove that. `TESTCONTAINERS_RYUK_DISABLED=true` is set by the shared container.
- Do not add a new test template; extend the existing ones in `templates/elixir/test/` (root rule).
- A Dialyzer or Credo step that hangs is bounded by `timeout` in `ElixirLintingSpec`; keep the bounds.

## Verify

- `etc/ci-local.sh check Elixir`
- `mvn verify -Dgroups=elixir` for the four spec classes end to end.
- Regenerate the golden with `mvn test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false`, then commit all twelve goldens current.
- Save build output to a file and grep it; never pipe it into `tail`.
- `python3 etc/api-surface.py check` after any change to a public Elixir signature.

## Decisions taken in batch 6

Each entry uses the audit finding's own proposal as the default. The owner may revisit any of them.

### Token expiry and refresh (F854, F1046, F1045, F1054)

Rule: one safety margin, applied once when the expiry is stored: `min(60 s, expires_in / 2)`, so a 60 s token is reused for 30 s. Expiry is kept on the monotonic clock (`System.monotonic_time(:millisecond)`), in the token manager and in the OIDC discovery expiry. A grant or refresh response without `expires_in` clears the expiry (cached until a 401 or `invalidate_access_token/1`); `expires_in <= 0` or unparseable is stale at once. Where: `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`. Test: `test/oauth2_token_manager_test.mustache` (31, 60, 90 and 120 s tokens are reused; `expires_in` 0 refetches; refresh without `expires_in` is cached).

### OIDC discovery (F1052, F859, F860)

Rule: a failed rediscovery keeps serving the previous delegate (stale-if-error) and retries on the next call; `max-age=0` still means a fetch per use (the floor was not added). The document's `issuer` must equal the discovery URL minus `/.well-known/openid-configuration` (trailing slash ignored; a missing issuer is refused; a discovery URL without the suffix is not compared), and `authorization_endpoint` and `token_endpoint` must be https unless the host is loopback; both raise `SerializationError`. Token and discovery requests pass `no_default_headers: true` to `send_request/6`, so `TransportOptions.default_headers` never reach the identity provider. Not applied: https checks on the `tokenUrl`/`authorizationUrl` the wrapper constructors receive. Test: `test/openid_connect_authenticator_test.mustache`, `test/default_api_client_unit_test.mustache`.

### token_type (F863)

Rule: `token_type` of any case of `bearer`, or absent, is accepted; anything else raises `OAuth2TokenError` with `Unsupported token_type in token response` (the value is not echoed). Test: `test/oauth2_token_manager_test.mustache`.

### Closing (F824, F1049)

Rule: `Client.close/1` closes the `DefaultApiClient` the client built and calls the authenticator's optional `close/1` (`HttpAwareAuthenticator` callback; the three token-flow authenticators and OIDC implement it by stopping their `OAuth2TokenManager` and the discovery cache). It is idempotent. The `Client` struct now carries `api_client` and `authenticator`. A call on a stopped token manager raises `OAuth2TokenError` (`The token manager is stopped (the client was closed)`) instead of exiting with `:noproc`. Not done: starting the managers under an SDK-owned supervisor; they stay `Agent.start_link` processes linked to the caller that built the authenticator, so closing is the way to release them. Test: `test/client_test.mustache`, `test/oauth2_token_manager_test.mustache`.

### Error messages and URLs (F861, F925, F927, F922, F926, F931, F933, F924)

Rule: redirect refusals use the D16 strings (`Refusing to follow redirect to non-HTTP(S) URL: <url>`, `Refusing to replay request body across HTTPS->HTTP redirect: <current> -> <next>`, `Too many redirects (exceeded N)`) and the URLs in them go through `DefaultApiClient.redact_url/1` (query, userinfo and fragment dropped) so a query-parameter API key never lands in a message. `ApiError.message/1` renders `Response headers:` as compact JSON with sorted keys. Token errors are `Token response is not valid JSON`, `... is not a JSON object`, `... missing or empty access_token field`. Serializer errors are `Failed to serialize object to JSON: <detail>` and `Failed to deserialize JSON: <detail>`. Test: the mirror test of each source file above.

### Headers (F1044)

Rule: the transport default headers and the caller's headers merge case-insensitively (the later layer wins and keeps its spelling), and a caller's `accept-encoding` of any casing suppresses the SDK's. Where: `default_api_client.mustache` (`merge_headers/2`, `has_header?/2`). Test: `test/default_api_client_unit_test.mustache`.

### Non-JSON success bodies (F149)

Rule: a non-JSON success Content-Type on a `String`/`String.t()` return keeps the text; any other non-binary return raises `SerializationError`. Test: `test/base_api_test.mustache`.

### format: password (F867)

Rule: a model with a `format: password` property derives `Inspect` with those fields excluded (`vendorExtensions.x-has-password-field`, set in `BetterElixirCodegen.postProcessModelProperty`). The field type stays `String.t()`. Test: `test/object_serializer_test.mustache`.

### Documentation (F883, F884, F885, F887, F888, F889, F890)

Rule: the README gained a `Usage` section (install line, quick start, link to SKILLS.md, custom HTTP client, cancellation, closing, raw responses and request id), and `Not supported` now names XML bodies, Link Objects, mutual TLS, automatic retries and per-call options. The word "retries" is gone from the custom-transport text. SKILLS `Models` builds the first model with its enforced keys set to `nil` placeholders, and gained `Closing the client`, `Cancellation` and `Timeouts and retries`.

### Skipped in batch 6 (needs a cross-language decision or shared Java)

F056, F057, F823, F827, F828, F830, F834, F835, F836, F837, F838, F840, F843, F855, F856, F857, F858, F862, F865, F866, F872, F893, F894, F929, F1048: each changes the public surface of all twelve SDKs or the shared generator, or (F872) rests on a compile check that was not run. F258 stays a documented limitation. F090, F215, F228, F278, F405, F803, F831, F839, F054 were already conforming.

## Decisions taken in batch 7

Each entry uses the audit finding's own proposal as the default. The owner may revisit any of them.

### Object-valued query and header parameters (F1143, D47)

Rule: a model struct or a map in a query, header or cookie parameter is written by its wire names: a model in `json_key_order/0` order, a map in key order; unset properties are left out. `form` with `explode: true` in the query repeats each property as its own `name=value` pair (`ValueSerializer.serialize_styled/7` returns `{:exploded_object, pairs}`, which `BaseApi.query_string_pairs` unpacks); `form` with `explode: false` and `simple` write `k1,v1,k2,v2`; `deepObject` writes `name[k]=v`. A nested object or array leaf raises `SerializationError`. Where: `value_serializer.mustache` (`object_pairs/1`), `base_api.mustache`. Test: `test/value_serializer_test.mustache`, `test/api/pet_api_test.mustache`.

### Per-call authenticators get the transport (F856)

Rule: an authenticator passed in an operation's options struct is handed the client's `ApiClient` through `set_api_client/2` before it is used, like the client-level one. Where: `base_api.mustache` (`inject_api_client/2`). Test: `test/base_api_test.mustache`.

### expires_in and max-age ceiling (F1138, D08)

Rule: a server-supplied `expires_in` and an OIDC discovery `max-age` are clamped to 31 536 000 seconds before the expiry is computed. Where: `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`. Test: `test/oauth2_token_manager_test.mustache`, `test/openid_connect_authenticator_test.mustache`.

### Protobuf durations are bounded (F1149, D88)

Rule: a `format: duration` value above +-315 576 000 000 seconds is a `SerializationError` on decode. Where: `object_serializer.mustache` (`parse_protobuf_duration/1`). Test: `test/object_serializer_test.mustache`.

### Proxy URL errors mask the userinfo (F1086, D79)

Rule: the two `Invalid proxy URL` messages print the value with `user:pass@` replaced by `***@`. Where: `transport_options.mustache` (`mask_userinfo/1`). Test: `test/transport_options_test.mustache`.

### Redirect handling (F1132, F1093)

Rule: a 3xx whose `Location` is absent, cannot be resolved, or names an http(s) URL the transport cannot send (`http://[bad`, no host) comes back as that response, never as an exception. On a cross-origin hop every header whose name contains `key`, `token` or `secret` is removed together with the static list (the S15 redaction rule), so a default header or a custom authenticator's header does not follow the hop. Where: `default_api_client.mustache` (`resolve_url/2`, `strip_sensitive_headers/1`). Test: `test/default_api_client_unit_test.mustache`.

### Generated literals and parameter names (F1154, F1055)

Rule: every spec value written into an authenticator constructor call (api key name, token, authorization and OpenID Connect URLs, scopes) goes through `BetterElixirCodegen.toElixirStringLiteral` (backslash, quote, `#{`, line breaks and tab escaped). A path or body parameter named like a binding the operation function makes itself (`api`, `opts`, `options`, `auth`, `path`, `server` and the request-building locals) gets the suffix `_param`; the wire name is unaffected. Where: `BetterElixirCodegen`. Test: `BetterElixirCodegenTest`.

### Decoded responses drop Content-Encoding and Content-Length (F1165, D92)

Rule: when a non-empty body was decoded (any Content-Encoding other than none or `identity`) the returned headers no longer carry `content-encoding` or `content-length`; an identity or empty body and an undecodable one (the `ApiError`) keep them. Where: `default_api_client.mustache`. Test: `test/default_api_client_test.mustache` (gzip, zstd), `test/default_api_client_unit_test.mustache` (identity).

### SKILLS error section (F1084)

Rule: the Error Handling section names the root grouping (`<Prefix>Error`, `exceptions/0`, `<prefix>_error?/1`), `SerializationError`, `OAuth2ServerError` and `OAuth2TokenError`, and ends the example with the root predicate. Where: `skills.mustache`.

### Skipped in batch 7 (needs shared Java, a cross-language decision or a toolchain run)

F056 (S3: not enforced), F057, F1062, F865, F866 (shared generator), F1058, F1060, F1055's petstore fixture row (spec change), F1100, F1153, F1168, F1174, F1176 (gates and shared tests), F855 and F857 (401 retry and per-scheme credentials change the surface of all twelve), F838, F840, F1048, F1080 (size bounds and error rendering are cross-language), F1150, F1151 (no proxy fixture for Elixir; Elixir takes upload bytes, not a handle), F1152 (needs a compile gate), F1161 (only `/test/response-headers`, `/test/empty` and the Set-Cookie row are covered).

## Decisions taken in batch 9

Each entry uses the audit finding's own proposal as the default. The owner may revisit any of them.

### A 401 replays the request once (F855)

Rule: a 401 on a call authenticated by the OAuth2 client-credentials, password, authorization-code or OpenID Connect authenticator drops the cached access token (`invalidate_access_token/1`, public on all four; the refresh token is kept) and the request is sent once more, rebuilt, so it carries fresh headers. A second 401 comes back as the usual `UnauthorizedError`. API-key, basic and bearer authenticators have no `invalidate_access_token/1` and never replay. The replay lives in `Api.BaseApi` (`attempt/5`), because only there are the authentication headers built. Elixir has no stream or file-handle bodies (the transport takes bytes, and multipart parts are bytes built in memory), so a body is replayable unless it is a function or a struct (`replayable?/1`); multipart is replayed, which is a deliberate difference from the SDKs that hold a handle. Where: `base_api.mustache`, `auth/oauth/*_authenticator.mustache`. Test: `test/base_api_test.mustache`, the four authenticator tests.

### PKCE S256 on the authorization-code flow (F858)

Rule: `build_authorization_url/2` draws a 43-character verifier (32 random bytes, base64url, no padding; `:crypto`, already in `extra_applications`), keeps it in the token manager (`begin_pkce/1`, `code_verifier/1`) and appends `code_challenge` and `code_challenge_method=S256` after `state`; `exchange_code/2` sends `code_verifier` when one exists. The verifier lives in the token manager, not the struct, so every copy of the authenticator and the OIDC delegate that survives a rediscovery see it. No nonce. Each URL replaces the verifier, so the last URL built is the one the exchange proves. Where: `auth/oauth/oauth2_token_manager.mustache`, `oauth2_auth_code_authenticator.mustache`. Test: `test/oauth2_auth_code_authenticator_test.mustache`, `test/openid_connect_authenticator_test.mustache`.

### Automatic retries with backoff (F823)

Rule: `TransportOptions.new(max_retries: n)` (default 2, 0 disables, a negative or non-integer value raises `ArgumentError`). A call is retried after a `NetworkError` (a failure before any response; a `NetworkTimeoutError` is not) and after the statuses 408, 409, 429 and 500 to 599 except 501. The wait is 0.5 s doubling to 8 s less up to 25% jitter; a `Retry-After` (seconds or an IMF-fixdate) replaces it, capped at 60 s. The call has one deadline, fixed when the first request has been built and handed to `DefaultApiClient.send_request/6` as `deadline:`; a wait that would end past it means no retry. Every retry rebuilds the request, so authentication headers are re-applied. A client without `transport_options` (a custom `ApiClient`) is never retried; the existing per-API tests that expect a single failing request use `max_retries: 0`. Cancellation is the process exit that ends the `Process.sleep`. Where: `base_api.mustache` (`send_with_retries/4`, `retry_after_ms/2`, `backoff_delay/2`), `transport_options.mustache`, `default_api_client.mustache`. Test: `test/base_api_test.mustache`, `test/transport_options_test.mustache`, `test/default_api_client_unit_test.mustache`.

### Rendered ApiError text is capped and masked (F1080)

Rule: `ApiError.message/1` renders the response body cut at 1 KiB with `... (N more bytes)`, control characters as `\n`, `\r`, `\t` or `\xNN`, and the response headers with `authorization`, `proxy-authorization`, `cookie`, `set-cookie`, `www-authenticate`, `proxy-authenticate` and any name containing key, token or secret masked as `***`. `response_body` and `response_headers` keep the full values. Where: `errors/api_error.mustache`. Test: `test/api_error_test.mustache`.

### Skipped in batch 9

F825 is Rust only.

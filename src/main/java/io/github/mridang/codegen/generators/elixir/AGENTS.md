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
| Construction | `Configuration.new/1` and `TransportOptions.new/1` take a keyword list; there is no builder (`default/0` mirrors the other languages' default configuration) | Data is immutable and there are no mutable objects to chain. |
| Receiver | The api struct is the first argument of every operation | No instance methods. |
| Optional arguments | Trailing optional arguments (server override, content-type selector, refresh URL) fold into one `opts` keyword list, validated with `Keyword.validate!` | Function arity and keyword lists are the language's way to take options. |
| Required operation options | A missing required option raises `ArgumentError` at call time; options structs do not use `@enforce_keys` | `@enforce_keys` would raise a `KeyError` when the struct is built, so the error would differ from the other SDKs. |
| Error hierarchy | No inheritance: `api_error?/1`, `client_error?/1`, `server_error?/1` and one predicate per status error (`not_found?/1`) are the is-a predicates, `exceptions/0` lists the hierarchy for `rescue` clauses, and the error generated for a declared error schema names the errors above it in `__ancestors__/0`, which `ApiError.specialises?/2` reads. An operation passes its own `%{status => error module}` map to `ApiError.from_response/4`; `retryable?/1` and `request_id/1` take any error of the family | Exceptions are plain structs. |
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

### allOf with a discriminator flattens into each struct (S4, 2026-11-16)

Rule: intended. Each subtype is a flat struct; the base module carries a dispatch function that reads the discriminator, and a behaviour declares the shared accessors. A model reused in two unions is never re-parented. Why: structs cannot inherit; a class-style parent was rejected. Where: `models/model.mustache`, `BetterElixirCodegen` (`setsDiscriminatorDefaultOnChildren` already fills the discriminator default on subtypes). The base module's `build/1` dispatches a payload to the subtype its mapping names (`hasChildren` plus `discriminator` gate it), and `ObjectSerializer.convert_to_type/2` routes to it. The shared behaviour for accessors is not in the template yet. Test: `test/composed_schema_test.mustache`.

### Required plus nullable (S5, D66, 2026-11-16)

Rule: a required, nullable property must be present when decoding (missing is a `SerializationError`), an explicit `nil` is kept, and encoding always writes the key. Why: the key and its value are separate facts. Where: `models/model.mustache` (`required_fields/0` for required non-nullable keys, `required_nullable_fields/0` for key presence only). Test: `test/composed_schema_test.mustache`, `test/object_serializer_test.mustache`.

### Content parameters use the SDK serializer (S2, 2026-11-16)

Rule: a `content: application/json` parameter in query, header, path or cookie goes through `ObjectSerializer.serialize/1`, never `Jason.encode!`. Why: identical bytes in all twelve. Where: `api/api.mustache` (`vendorExtensions.param.queryContent` branches in each location). Test: `test/value_serializer_test.mustache`, `test/base_api_test.mustache`.

### Schema constraints are not enforced (S3, 2026-11-16)

Rule: `pattern`, length, range, `multipleOf` and item-count keywords are not checked by the client; the model layer checks only that required properties are present and values have the declared type. The README section "Schema constraints" says so. Why: Elixir structs and typespecs carry no constraints, and the server owns validation. Where: `readme.mustache`. Test: README text only; no behaviour to test.

### uniqueItems order is unspecified (S6, 2026-11-16)

Rule: `MapSet` values are sent in the `MapSet`'s own order; do not add sorting. Why: the owner decided against sorting, which supersedes the earlier "sorted" wording in F319 and F072. Where: `object_serializer.mustache` (`sanitize_for_serialization` for `%MapSet{}`), `readme.mustache`. Test: `test/object_serializer_test.mustache`.

### Cancellation has no new API (S7)

Rule: there is no cancellation parameter. A caller stops a call by terminating its own process, and the end-to-end timeout kills the request task (`Task.shutdown`). Why: Elixir cancels with process exit signals; a token or context argument would be foreign. Where: `default_api_client.mustache`. Test: `test/default_api_client_test.mustache`. The cancellation error versus the timeout error is intended and not confirmed in the template.

### Parameter order (S8, owner confirmation pending)

Rule: required arguments come first and everything optional (server, content type) travels in the trailing `opts` keyword list, with or without security, so adding security never moves an argument. Per-operation auth rides in the options struct, not in `opts`. Why: keyword options are the Elixir idiom for what the other languages order positionally. Where: `api/api.mustache`. Test: `test/base_api_test.mustache`.

### Injected transport is Req options (S9)

Rule: `TransportOptions.new(req_options: [...])` accepts any `Req.new/1` options (a custom `:adapter` or `:plug`); the SDK's own settings win and are merged key by key. Passing `:connect_options` or `:finch` together with `proxy`, `ca_cert_path` or `verify_ssl: false` raises `ArgumentError`. Why: Req is the standard HTTP client object here, and a silent override of the SDK's proxy or TLS settings is worse than a failure. The SDK never starts or stops the global Finch pool, so there is nothing of the caller's to close. Where: `transport_options.mustache`, `default_api_client.mustache` (`merge_req_options`). Test: `test/transport_options_test.mustache`, `test/default_api_client_unit_test.mustache`.

### Dependencies (S10)

Rule: Req, Jason, Mime and an optional Brotli are the runtime dependencies. A pure-Elixir library or a version bump is acceptable; a dependency that needs a native binary is not. Why: the cross-language rule, applied here. Where: `mix_exs.mustache`, `dev_dependencies.mustache`. Test: the generated suite fails with a message naming `DEV-DEPENDENCIES` when a test dependency is missing from `mix.exs`.

### Out of scope (S11, issues #53, #70, #78, #88, #90)

Rule: callbacks and webhooks, XML bodies, Link Objects and mutual TLS are not generated, and the README says so. Why: closed backlog issues. Where: `readme.mustache` (the "Not supported" section names webhooks and callbacks, `dependentRequired` and SOCKS; the XML, Link Object and mutual TLS lines landed in batch 6). Test: none.

### Scope order, UUID case, blank keys, error cause (S14)

Rule: OAuth scopes keep the spec's declared order (the wrapper test in `oauth2_client_credentials_authenticator_test.mustache` sorts them today, so it cannot see order; assert the unsorted list); uuid is written lowercase and validated (`encode_uuid_param`); an API key or bearer token is blank only when empty or ASCII whitespace (intended: the templates still use `String.trim/1`, which strips Unicode whitespace); `ApiError` accepts a `:cause`. Why: shared decision. Where: `auth/api_key_authenticator.mustache`, `auth/bearer_authenticator.mustache`, `errors/api_error.mustache`, `AbstractBetterCodegen` (scope order). Test: `test/api_key_authenticator_test.mustache`, `test/bearer_authenticator_test.mustache`, `test/api_error_test.mustache`.

### Secrets are redacted (S15)

Rule: `inspect/1` of `TransportOptions` shows the proxy password and every sensitive default header (`Authorization`, `Proxy-Authorization`, `Cookie`, anything containing key, token or secret) as `***`; the authenticators mask their secrets through `defimpl Inspect`. Why: logs must not leak credentials. Where: `transport_options.mustache` (`redact_proxy/1`, `redact_headers/1`), `auth/**`. Test: `test/transport_options_test.mustache`, the authenticator tests.

### Timeouts, redirects, proxy and CA validation (S16)

Rule: `timeout` is one end-to-end budget: the deadline is fixed once and every redirect hop spends what is left, enforced by running each hop in a `Task` and shutting it down on overrun. `ca_cert_path` is validated whenever it is set, even with `verify_ssl: false`. A caller-supplied stream or file handle is never closed by the SDK. `timeout <= 0` is rejected with `Invalid timeout: must be a positive number of milliseconds (got N)` (`nil` still means no timeout). Moving proxy validation to `build()` has no Elixir meaning (there is no builder); it is checked in `new/1`. Why: Req's `:receive_timeout` is per chunk and resets on every packet, so it cannot bound a slow trickle. Where: `default_api_client.mustache` (`do_request_with_redirects`), `transport_options.mustache`. Test: `test/default_api_client_test.mustache`, `test/transport_options_test.mustache`.

### No ambient proxy, caCertPath pins, SDK proxy credentials survive redirects

Rule: no proxy unless `TransportOptions.proxy` is set; `ca_cert_path` replaces the system trust store; the SDK's own `Proxy-Authorization` is built into the connection options and is not stripped on a cross-origin redirect. Why: one transport rule for all twelve. Where: `default_api_client.mustache` (`request_options`, `proxy_request_options`), `transport_options.mustache`. Test: `test/default_api_client_test.mustache`. The template does not pass `proxy: :none` and relies on Req not reading the environment; that was not re-verified.

### 429 error and redirect methods

Rule: HTTP 429 maps to `TooManyRequestsError` (a `ClientError`, listed in `ApiError.from_response/3`, `ClientError`, the root error list and the README-style docs). Redirects follow RFC 9110 section 15.4 through `DefaultApiClient.redirect_method/2`: 307 and 308 keep the method, 303 turns everything but HEAD into GET, 301 and 302 turn only POST into GET. Content cookies are `URI.encode(..., &URI.char_unreserved?/1)` of the serializer JSON, which is already the shared unreserved set. Why: one rule for all twelve. Where: `errors/too_many_requests_error.mustache`, `errors/api_error.mustache`, `default_api_client.mustache`. Test: `test/api_error_test.mustache`, `test/default_api_client_unit_test.mustache`, `test/value_serializer_test.mustache`.

### Header injection is case-insensitive

Rule: `User-Agent` and `X-Request-ID` are injected only when no header of any casing is already present; the caller's header always wins. Why: HTTP field names are case-insensitive. Where: `default_api_client.mustache` (`has_header?/2`). Test: `test/default_api_client_unit_test.mustache`.

### OAuth token handling

Rule: OIDC rediscovery keeps the token manager and its exchanged flag; a refresh carries client authentication through `get_access_token/5` with a `:refresh_url` option; `exchange/4` replaces the session only on success and restores it on failure; an empty implicit-flow token counts as unset; the token body and authorize URL use one fixed key order and drop an empty `state`; OIDC always requests `openid`. Still intended and not in the templates: the single-flight discovery fetch, the one-year ceiling and decimal grammar for `expires_in`, and the canonical message `Must call exchange_code() before making API requests` (the template word differs). Why: shared OAuth contract. Where: `auth/oauth/*.mustache`. Test: `test/oauth2_token_manager_test.mustache` and the per-flow authenticator tests.

### Error messages

Rule: landed in batch 6. One wording set for token and discovery errors (`Token response is not valid JSON` versus `... is not a JSON object`), serializer errors (`Failed to serialize object to JSON: <detail>`, `Failed to deserialize JSON: <detail>`), redirect refusals (capitalised canonical strings), `Response headers: <compact JSON, sorted keys>`, use after close (`ApiClient has been closed and can no longer send requests`, a `RuntimeError`), and real response headers on every `ApiError` raised for a response that arrived but was unusable. Pinned by tests in `api_error_test`, `object_serializer_test`, `oauth2_token_manager_test`, `default_api_client_test` and `default_api_client_unit_test`. Why: byte-identical messages across twelve. Where: `default_api_client.mustache`, `object_serializer.mustache`, `errors/api_error.mustache`, `auth/oauth/*.mustache`. Test: `test/api_error_test.mustache` (add table rows for the exact rendering).

### Response bodies: binary, byte, non-JSON

Rule: a `format: binary` return is raw bytes, never JSON-parsed; `format: byte` follows the JSON path with one strict base64 decoder; a non-JSON success Content-Type returns the text for a `String.t()` return and raises `SerializationError` otherwise. The strict final clause landed in batch 6 (`String` and `String.t()` returns keep the text). Why: one rule per Content-Type class. Where: `base_api.mustache`, `api/api.mustache`. Test: `test/base_api_test.mustache`.

### Request bodies: binary, multipart, form

Rule: a binary body is sent verbatim under any selected content type (`{:raw_body, bytes}`); multipart scalar parts, including `DateTime`, `Date`, `Time` and `Duration`, are plain text parts built with `ObjectSerializer.stringify` and only a map or model becomes a JSON part; a form-urlencoded field that is a map or nested list raises `SerializationError` (`ensure_form_scalar!`). Query, form and multipart order follows spec declaration order through lists of `{key, value}` tuples. Why: no per-language repr of nested values on the wire. Where: `base_api.mustache`, `default_api_client.mustache`, `api/api.mustache`. Test: `test/base_api_test.mustache`, `test/default_api_client_test.mustache`.

### Encoding: allowReserved, percent-encoding, path dots

Rule: query and form encoding keeps only `A-Za-z0-9-._~` literal; a path value of exactly `.` or `..` is rejected before dispatch (`ArgumentError`); `format: byte` parameters are base64-encoded (`encode_base64_param`). Narrowing `allowReserved` to the RFC 3986 query set and expanding a `MapSet` like an array are intended, not confirmed. Why: shared encoding contract. Where: `value_serializer.mustache`, `base_api.mustache`, `object_serializer.mustache`. Test: `test/value_serializer_test.mustache`, `test/base_api_test.mustache`.

### Models: decode, defaults, equality

Rule: `readOnly` and `writeOnly` have no wire effect and appear as a `Read-only` or `Write-only` note in `@moduledoc`; an inline enum atom outside the declared values raises `SerializationError` on encode; required properties are checked on decode; a required property with a default is filled when absent; the declared value wins over a colliding `additionalProperties` key. Untyped struct fields and wrong-typed scalars are still not validated on construction. Why: shared model contract. Where: `models/model.mustache`, `object_serializer.mustache`. Test: `test/object_serializer_test.mustache`, `test/composed_schema_test.mustache`.

### Per-operation servers

Rule: one module per server variable per operation, named `<Variant><Var>` only where a name repeats across variants; the typed server variants validate enum values in `url/1`; the `:server` option exists only on operations that declare `servers:` and resolves through the variant's own `url/1`. Why: one type per variable, no dead variant modules. Where: `api/api.mustache`, `BetterElixirCodegen.enrichOperationServers`. Test: `test/server_variable_test.mustache`.

### Identifiers, enum literals, reserved words

Rule: a digit-leading name gets the letter `n` before casing; model names that collide with SDK module names take the `Model` prefix; enum string literals escape the quote, backslash and the interpolation opener `#{` (written `\#{`); text that lands in an interpolating heredoc goes through `escapeUnsafeCharacters`. Dropping the escape for `and`, `or` and `not` is intended only if they compile as struct keys under `--warnings-as-errors`; that was not verified. Why: generated source must evaluate to exactly the spec's bytes. Where: `BetterElixirCodegen` (`toDefaultValue`, `escapeUnsafeCharacters`, `toHeredocText`, `getEnumStringEscapes`), `models/model.mustache`, `reserved-words/elixir.txt`. Test: `ElixirReservedWordsSpec`, `ElixirFormattingSpec`.

### Charset and compression (AGENTS.md rounds 7 and 8)

Rule: `deflate` decodes zlib-wrapped data only, with no raw fallback; an unsupported `Content-Encoding` raises; an empty body passes through for any encoding; `windows-1252` decodes through a C1 table. Replacing an unknown charset with a lossy-valid string and dropping `Content-Encoding` and `Content-Length` from decoded responses are intended. Why: one codec contract. Where: `default_api_client.mustache` (`decode_text_body`, decompress helpers). Test: `test/default_api_client_test.mustache`.

### Redirect downgrade guard anchors on the current hop (AGENTS.md round 6)

Rule: the HTTPS to HTTP body-replay guard compares the current hop, not the original request, so an http, https, http chain is caught on the last hop. Why: the original-anchored check replayed a TLS body in cleartext. Where: `default_api_client.mustache`. Test: `test/default_api_client_test.mustache`.

### The "security: []" comment is gated (AGENTS.md round 6)

Rule: the no-auth sentinel note appears only on operations with `vendorExtensions.op.securityNone`; inherited-auth operations say they inherit the global requirement. Why: the old comment contradicted the code. Where: `api/api.mustache`. Test: `ElixirFormattingSpec`.

### Fixture names in templates

Rule: the Elixir test templates are petstore fixtures by design and the OAuth wrapper tests are registered without scheme-name flags (`getOAuthTestFileSpecs`); `FixtureVocabularyLeakTest` guards what a real client sees. Why: guarding them would need new codegen context across six test files, and the other SDKs bind to petstore the same way. Where: `BetterElixirCodegen.java`. Test: `FixtureVocabularyLeakTest`.

### Inspect bypass is accepted (forced)

Rule: `inspect(auth, structs: false)` prints raw credentials and will keep doing so. Why: it is an Elixir opt-out that skips the `Inspect` protocol, so no `defimpl` can intercept it; closing it would mean not storing the secret in the struct, which breaks the authenticator design. Where: `auth/oauth/*_authenticator.mustache`. Test: none.

### Idiom differences from the other eleven

Rule: these Elixir differences are settled and are not defects: error members through `api_error?/1`, `extra*` props on transparent structs, `start_link/1`, `__using__/1`, `exceptions/0`, the receiver-first argument, the keyword-list constructors and `default/0`, no `BaseAuthenticator` members (a behaviour), `AnyOfComposite`, the module functions `actual_instance` and `matched_instances`, `Metadata.additionalProperties` being non-nil by its `%{}` default, no `@enforce_keys` on options structs, the trailing `opts` keyword list, and extra helpers on the support modules. Why: each is forced by structs, behaviours or the lack of visibility across files.

### Mock seam (mocking001 to 009, issue #122 and the Invoker line of #131)
Rule: every API group has an interface and the client has one where the language can state it, additive next to the existing classes, using only SDK and standard-library types. `<Group>ApiBehaviour` in the group file, with a `@callback` for the full arity of each operation, its bang form and its `_with_http_info` form; `PetApi` declares `@behaviour` and `@impl true`. A fake is a module (Elixir calls a module, not a value), so code under test takes the group module. There is no client behaviour: `Client` is a struct of group structs. The tests use a hand-written module, not Mox. Tests: test/client_test.mustache (001, 002), test/api/pet_api_test.mustache (003 to 006), test/fake_group.mustache (the shared fake). Where: the templates named above, `BetterElixirCodegen`, the README template (`Testing your code`), `docs/languages/elixir.md`.

## Known limitations and out of scope

- Charsets: `shift_jis` and the other `iso-8859-x` labels return the raw bytes, because Erlang's `:unicode` has no multi-charset support (partial).
- Struct fields are not validated on construction beyond inline enums: untyped fields and wrong-typed scalars pass.
- Fractional and quoted values are rejected on decode, in containers too (wave 2). An int32 or int64 range check is not possible: the descriptor is the width-less `integer()`.
- Object-valued query and header parameters, `format: time` sub-millisecond truncation and `format: uri` validation are parked or not reached.
- The warnings-fatal docs build is not run by any spec (deferred): `makefile.mustache` runs `mix docs --warnings-as-errors`, but no `ElixirLintingSpec` step does.
- Spec gates differ from the client `Makefile`: `make build` and `make lint` run plain `mix compile`, while `ElixirLintingSpec` adds `--warnings-as-errors`. The client suite does not use `--warnings-as-errors` because it calls deprecated operations.
- SOCKS proxies are rejected at construction (README).
- No per-field example or description in model docs; the `@moduledoc` carries the model description, named examples and the deprecated-field note.
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

## Decisions taken in batch 6

Each entry uses the audit finding's own proposal as the default. The owner may revisit any of them.

### Token expiry and refresh

Rule: one safety margin, applied once when the expiry is stored: `min(60 s, expires_in / 2)`, so a 60 s token is reused for 30 s. Expiry is kept on the monotonic clock (`System.monotonic_time(:millisecond)`), in the token manager and in the OIDC discovery expiry. A grant or refresh response without `expires_in` clears the expiry (cached until a 401 or `invalidate_access_token/1`); `expires_in <= 0` or unparseable is stale at once. Where: `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`. Test: `test/oauth2_token_manager_test.mustache` (31, 60, 90 and 120 s tokens are reused; `expires_in` 0 refetches; refresh without `expires_in` is cached).

### OIDC discovery

Rule: a failed rediscovery keeps serving the previous delegate (stale-if-error) and retries on the next call; `max-age=0` still means a fetch per use (the floor was not added). The document's `issuer` must equal the discovery URL minus `/.well-known/openid-configuration` (trailing slash ignored; a missing issuer is refused; a discovery URL without the suffix is not compared), and `authorization_endpoint` and `token_endpoint` must be https unless the host is loopback; both raise `SerializationError`. Token and discovery requests pass `no_default_headers: true` to `send_request/6`, so `TransportOptions.default_headers` never reach the identity provider. Not applied: https checks on the `tokenUrl`/`authorizationUrl` the wrapper constructors receive. Test: `test/openid_connect_authenticator_test.mustache`, `test/default_api_client_unit_test.mustache`.

### token_type

Rule: `token_type` of any case of `bearer`, or absent, is accepted; anything else raises `OAuth2TokenError` with `Unsupported token_type in token response` (the value is not echoed). Test: `test/oauth2_token_manager_test.mustache`.

### Closing

Rule (made graceful in round 4, see  below): `Client.close/1` closes the `DefaultApiClient` the client built and calls the optional `close/1` of the authenticator and of each scheme credential (`HttpAwareAuthenticator` callback; the three token-flow authenticators and OIDC implement it by stopping their `OAuth2TokenManager` and the discovery cache). It is idempotent. The `Client` struct carries `api_client`, `authenticator` and `schemes`. A call on a stopped token manager raises `OAuth2TokenError` (`The token manager is stopped (the client was closed)`) instead of exiting with `:noproc`. Not done: starting the managers under an SDK-owned supervisor; they stay `Agent.start_link` processes linked to the caller that built the authenticator, so closing is the way to release them. Test: `test/client_test.mustache`, `test/oauth2_token_manager_test.mustache`.

### Error messages and URLs

Rule: redirect refusals use the D16 strings (`Refusing to follow redirect to non-HTTP(S) URL: <url>`, `Refusing to replay request body across HTTPS->HTTP redirect: <current> -> <next>`, `Too many redirects (exceeded N)`) and the URLs in them go through `DefaultApiClient.redact_url/1` (query, userinfo and fragment dropped) so a query-parameter API key never lands in a message. `ApiError.message/1` renders `Response headers:` as compact JSON with sorted keys. Token errors are `Token response is not valid JSON`, `... is not a JSON object`, `... missing or empty access_token field`. Serializer errors are `Failed to serialize object to JSON: <detail>` and `Failed to deserialize JSON: <detail>`. Test: the mirror test of each source file above.

### Headers

Rule: the transport default headers and the caller's headers merge case-insensitively (the later layer wins and keeps its spelling), and a caller's `accept-encoding` of any casing suppresses the SDK's. Where: `default_api_client.mustache` (`merge_headers/2`, `has_header?/2`). Test: `test/default_api_client_unit_test.mustache`.

### Non-JSON success bodies

Rule: a non-JSON success Content-Type on a `String`/`String.t()` return keeps the text; any other non-binary return raises `SerializationError`. Test: `test/base_api_test.mustache`.

### format: password

Rule: a model with a `format: password` property derives `Inspect` with those fields excluded (`vendorExtensions.x-has-password-field`, set in `BetterElixirCodegen.postProcessModelProperty`). The field type stays `String.t()`. Test: `test/object_serializer_test.mustache`.

### Documentation

Rule: the README gained a `Usage` section (install line, quick start, link to SKILLS.md, custom HTTP client, cancellation, closing, raw responses and request id), and `Not supported` now names XML bodies, Link Objects, mutual TLS, automatic retries and per-call options. The word "retries" is gone from the custom-transport text. SKILLS `Models` builds the first model with its enforced keys set to `nil` placeholders, and gained `Closing the client`, `Cancellation` and `Timeouts and retries`.

## Decisions taken in batch 7

Each entry uses the audit finding's own proposal as the default. The owner may revisit any of them.

### Object-valued query and header parameters (D47)

Rule: a model struct or a map in a query, header or cookie parameter is written by its wire names: a model in `json_key_order/0` order, a map in key order; unset properties are left out. `form` with `explode: true` in the query repeats each property as its own `name=value` pair (`ValueSerializer.serialize_styled/7` returns `{:exploded_object, pairs}`, which `BaseApi.query_string_pairs` unpacks); `form` with `explode: false` and `simple` write `k1,v1,k2,v2`; `deepObject` writes `name[k]=v`. A nested object or array leaf raises `SerializationError`. Where: `value_serializer.mustache` (`object_pairs/1`), `base_api.mustache`. Test: `test/value_serializer_test.mustache`, `test/api/pet_api_test.mustache`.

### Scheme credentials get the transport

Rule: an authenticator the client holds, the default one and each scheme credential, is handed the client's `ApiClient` through `set_api_client/2` when the client is built (`Client.new/3`), and a credential resolved by `BaseApi.invoke_api_for_result` is handed it again (`inject_api_client/2`). There is no per-call authenticator since F857 (see Round 4 below). Where: `client.mustache`, `base_api.mustache`. Test: `test/client_test.mustache`, `test/base_api_test.mustache`.

### expires_in and max-age ceiling (D08)

Rule: a server-supplied `expires_in` and an OIDC discovery `max-age` are clamped to 31 536 000 seconds before the expiry is computed. Where: `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`. Test: `test/oauth2_token_manager_test.mustache`, `test/openid_connect_authenticator_test.mustache`.

### Protobuf durations are bounded (D88)

Rule: a `format: duration` value above +-315 576 000 000 seconds is a `SerializationError` on decode. Where: `object_serializer.mustache` (`parse_protobuf_duration/1`). Test: `test/object_serializer_test.mustache`.

### Proxy URL errors mask the userinfo (D79)

Rule: the two `Invalid proxy URL` messages print the value with `user:pass@` replaced by `***@`. Where: `transport_options.mustache` (`mask_userinfo/1`). Test: `test/transport_options_test.mustache`.

### Redirect handling

Rule: a 3xx whose `Location` is absent, cannot be resolved, or names an http(s) URL the transport cannot send (`http://[bad`, no host) comes back as that response, never as an exception. On a cross-origin hop every header whose name contains `key`, `token` or `secret` is removed together with the static list (the S15 redaction rule), so a default header or a custom authenticator's header does not follow the hop. Where: `default_api_client.mustache` (`resolve_url/2`, `strip_sensitive_headers/1`). Test: `test/default_api_client_unit_test.mustache`.

### Generated literals and parameter names

Rule: every spec value written into an authenticator constructor call (api key name, token, authorization and OpenID Connect URLs, scopes) goes through `BetterElixirCodegen.toElixirStringLiteral` (backslash, quote, `#{`, line breaks and tab escaped). A path or body parameter named like a binding the operation function makes itself (`api`, `opts`, `options`, `auth`, `path`, `server` and the request-building locals) gets the suffix `_param`; the wire name is unaffected. Where: `BetterElixirCodegen`. Test: `BetterElixirCodegenTest`.

### Decoded responses drop Content-Encoding and Content-Length (D92)

Rule: when a non-empty body was decoded (any Content-Encoding other than none or `identity`) the returned headers no longer carry `content-encoding` or `content-length`; an identity or empty body and an undecodable one (the `ApiError`) keep them. Where: `default_api_client.mustache`. Test: `test/default_api_client_test.mustache` (gzip, zstd), `test/default_api_client_unit_test.mustache` (identity).

### SKILLS error section

Rule: the Error Handling section names the root grouping (`<Prefix>Error`, `exceptions/0`, `<prefix>_error?/1`), `SerializationError`, `OAuth2ServerError` and `OAuth2TokenError`, and ends the example with the root predicate. Where: `skills.mustache`.

## Decisions taken in batch 9

Each entry uses the audit finding's own proposal as the default. The owner may revisit any of them.

### A 401 replays the request once

Rule: a 401 on a call authenticated by the OAuth2 client-credentials, password, authorization-code or OpenID Connect authenticator drops the cached access token (`invalidate_access_token/1`, public on all four; the refresh token is kept) and the request is sent once more, rebuilt, so it carries fresh headers. A second 401 comes back as the usual `UnauthorizedError`. API-key, basic and bearer authenticators have no `invalidate_access_token/1` and never replay. The replay lives in `Api.BaseApi` (`attempt/5`), because only there are the authentication headers built. Elixir has no stream or file-handle bodies (the transport takes bytes, and multipart parts are bytes built in memory), so a body is replayable unless it is a function or a struct (`replayable?/1`); multipart is replayed, which is a deliberate difference from the SDKs that hold a handle. Where: `base_api.mustache`, `auth/oauth/*_authenticator.mustache`. Test: `test/base_api_test.mustache`, the four authenticator tests.

### PKCE S256 on the authorization-code flow

Rule: `build_authorization_url/2` draws a 43-character verifier (32 random bytes, base64url, no padding; `:crypto`, already in `extra_applications`), keeps it in the token manager (`begin_pkce/1`, `code_verifier/1`) and appends `code_challenge` and `code_challenge_method=S256` after `state`; `exchange_code/2` sends `code_verifier` when one exists. The verifier lives in the token manager, not the struct, so every copy of the authenticator and the OIDC delegate that survives a rediscovery see it. No nonce. Each URL replaces the verifier, so the last URL built is the one the exchange proves. Where: `auth/oauth/oauth2_token_manager.mustache`, `oauth2_auth_code_authenticator.mustache`. Test: `test/oauth2_auth_code_authenticator_test.mustache`, `test/openid_connect_authenticator_test.mustache`.

### Automatic retries with backoff

Rule: `TransportOptions.new(max_retries: n)` (default 2, 0 disables, a negative or non-integer value raises `ArgumentError`). A call is retried after a `NetworkError` (a failure before any response; a `NetworkTimeoutError` is not) and after the statuses 408, 409, 429 and 500 to 599 except 501. The wait is 0.5 s doubling to 8 s less up to 25% jitter; a `Retry-After` (seconds or an IMF-fixdate) replaces it, capped at 60 s. The call has one deadline, fixed when the first request has been built and handed to `DefaultApiClient.send_request/6` as `deadline:`; a wait that would end past it means no retry. Every retry rebuilds the request, so authentication headers are re-applied. A client without `transport_options` (a custom `ApiClient`) is never retried; the existing per-API tests that expect a single failing request use `max_retries: 0`. Cancellation is the process exit that ends the `Process.sleep`. Where: `base_api.mustache` (`send_with_retries/4`, `retry_after_ms/2`, `backoff_delay/2`), `transport_options.mustache`, `default_api_client.mustache`. Test: `test/base_api_test.mustache`, `test/transport_options_test.mustache`, `test/default_api_client_unit_test.mustache`.

### Rendered ApiError text is capped and masked

Rule: `ApiError.message/1` renders the response body cut at 1 KiB with `... (N more bytes)`, control characters as `\n`, `\r`, `\t` or `\xNN`, and the response headers with `authorization`, `proxy-authorization`, `cookie`, `set-cookie`, `www-authenticate`, `proxy-authenticate` and any name containing key, token or secret masked as `***`. `response_body` and `response_headers` keep the full values. Where: `errors/api_error.mustache`. Test: `test/api_error_test.mustache`.

## Decisions taken in batch 10

Each entry uses the audit finding's own proposal as the default. The owner may revisit any of them.

### Lists in messages render as `[a, b, c]` (D20)

Rule: the allowed values of a server variable (global `ServerConfiguration.url/2` and the per-operation server variants) and the declared content types of an operation are joined with `, ` inside brackets, unquoted, not printed through `inspect/1`. Where: `server_configuration.mustache`, `api/api.mustache`. Test: `test/server_configuration_test.mustache` (three-element list), `test/api/pet_api_test.mustache`.

### Server URL substitution is one pass

Rule: `ServerConfiguration.url/2` and the per-operation server variants replace each `{name}` placeholder once, in a single `Regex.replace/3`, so a placeholder introduced by a substituted value is never expanded again; a placeholder with no declared variable stays as written. Where: `server_configuration.mustache`, `api/api.mustache`. Test: `test/server_configuration_test.mustache`. The per-operation variant has no test for the re-expansion: its variables are enums, so a value holding a placeholder is refused first.

### Auth-code precondition wording (D09)

Rule: the call before `exchange_code/2` raises `Must call exchange_code() before making API requests`, with the call parentheses. The use-after-close sentence was already canonical. Test: `test/oauth2_auth_code_authenticator_test.mustache`.

### Options modules document the examples of optional parameters

Rule: the named examples of an optional parameter are written into the `@moduledoc` of its `Api.Options.*` module as `### <name> example` blocks, the way the operation function documents a required parameter. Where: `BetterElixirCodegen.toExampleDocs`, `api/options.mustache`. Test: `BetterElixirCodegenTest`.

### Documentation layout

Rule: SKILLS `Configuration` follows the java and kotlin order and level: `Custom Transport Options`, `Retries`, `Custom HTTP client` (renamed from `Custom HTTP Transport`), `Cancellation`, `Closing the client`, and `Raw responses, timeouts and the request id` (replaces `Timeouts and retries`). README gained a `Dependency audit` section. Where: `skills.mustache`, `readme.mustache`.

### Dependency audit target

Rule: the generated `Makefile` has `make audit`, which runs `mix hex.audit`. Hex reports retired packages only, so it is a floor and not a vulnerability scan. Where: `makefile.mustache`, `readme.mustache`.

### Tests added for behaviour that already held

F1073 and F1074 (`test/bearer_authenticator_test.mustache`: a Unicode-space token is rejected by the printable-ASCII rule, `bearerxyz` is kept whole, a doubled prefix is stripped once), F1065 (an IPv6 literal proxy host is accepted verbatim; the unterminated form is not asserted because `URI.parse/1` accepts it and the result could not be run here), F1123 (`close/1` is an optional callback, the default transport implements it), F1129 and F1112 (`test/composed_schema_test.mustache`, `test/object_serializer_test.mustache`), F1124 (the hostile third server, `Pet.nickname` longer than its maximum, `Order.quantity` 500), F1161 (the typed `/test/status/<code>` routes through `getOrderById`, `test/api/store_api_test.mustache`), F1167 (the default user agent equals `{{userAgentDefault}}` on the options and on the wire), F1064 (a caller shut down mid-call ends with no reply and no SDK error), F927 (`DefaultApiClient.replay_refusal_message/2` carries the D16 replay sentence and is tested).

## Decisions taken in wave 2

Owner decisions of 2026-11-17 and the triaged parity findings for elixir. Test names are generated-test names (templates under `templates/elixir/test/`) unless a generator test class is named.

### Error context on every API error
Rule: `ApiError` and every error that specialises it (the 4xx and 5xx errors, `NetworkError`, `NetworkTimeoutError`) carry `request_method` (upper case, `"GET"`) and `request_path` (the URL's path, query string and fragment dropped). `ApiError.with_request/3` sets them and leaves an error that already has them alone; `Api.BaseApi` calls it on every error it returns, a response error and a transport error alike. `ApiError.message/1` adds a `Request: GET /path` line after the `HTTP status code:` line when both are set; the detail message is unchanged (`API returned status code N`). The line wording is the java one; the integrator reconciles it with the other eleven. Not done: OpenID Connect discovery failures do not carry the request, because they are built outside `BaseApi` from `ApiError.from_response/3`.
Where: `errors/*.mustache`, `base_api.mustache`. Test: `test/api_error_test.mustache` (message line, `with_request`, every error module), `test/base_api_test.mustache` (response and network errors, query stripped), `test/api/pet_api_test.mustache`, `test/api/store_api_test.mustache`.

### Per-call options
Rule: every operation takes `:timeout` (positive milliseconds), `:headers` (a map) and `:max_retries` (not negative) in its trailing `opts` keyword list, and every options struct has the same three fields (`timeout`, `headers`, `max_retries`). The `opts` key wins over the struct field, a nil is the client's own setting, and a bad value raises `ArgumentError` with the transport's wording before any request is built (`BaseApi.call_settings/2`). `timeout` replaces the transport's connect, idle and total deadlines for that call (`send_request/6` option `:timeout`); `max_retries` replaces the retry count (a client with no transport options is retried only when the call asks); `headers` are merged after the default and operation headers and before the authentication headers, replacing a header of the same name in any casing. `invoke_api/11` and `invoke_api_for_result/11` take the settings as their last argument. A parameter named `timeout`, `headers`, `max_retries` or `call` gets the `_param` suffix (`getInjectedArgumentNames`).
Every operation gets an options struct, even one with no query, header, form or cookie parameter (`mintsOptionsForEveryOperation()` returns true): that struct has only the parameters of the operation and the three per-call fields, never an `auth` field, and an operation with no parameter says so in its moduledoc (`callSettingsOnly`). The options argument therefore sits before the `opts` keyword list on every operation (`get_inventory(api, options, opts)`; pass `nil` to give only `opts`), as in the other SDKs.
Where: `api/options.mustache`, `api/api.mustache`, `base_api.mustache`, `default_api_client.mustache`, `BetterElixirCodegen.getInjectedArgumentNames`. Test: `test/base_api_test.mustache` (retries, headers, timeout, `call_settings`), `test/api/pet_api_test.mustache`, `test/api/store_api_test.mustache` (`get_inventory`, an operation with no parameters), `GenerateElixirClientTest.everyOptionsStructCarriesThePerCallSettings`, `BetterElixirCodegenTest.suffixesAParameterNamedLikeABindingTheOperationMakesItself`.

### Several 2xx responses decode by the status
Rule: an operation with more than one 2xx schema passes `{union_type, [{status, type}, ...]}` as its return type. `BaseApi` decodes the body as the type of the status that arrived (a `"2XX"` row matches the whole range, listed last) and as the union when no row names the status. The result is the decoded variant itself, as for any union here (no wrapper), so a 200 gives the first schema's struct and a 201 the second's.
Where: `api/api.mustache` (`vendorExtensions.op.successResponses`), `base_api.mustache` (`return_type_for_status/2`). Test: `test/base_api_test.mustache`, `test/api/store_api_test.mustache` (`reserve_stock`), `GenerateElixirClientTest.severalSuccessResponsesDecodeByStatus`.

### Credentials
Rule: an authenticator constructor takes `nil` for a credential and then reads one environment variable, once, at construction. A security scheme's authenticator (`auth/scheme_authenticator.mustache`) reads the variable the shared generator names for that scheme and field (`securitySchemeEnvVars`, `envVars`, `envName`: `{PREFIX}_{FIELD}`, or `{PREFIX}_{SCHEME}_{FIELD}` where two schemes share a field), resolves every credential argument (`\\ nil` defaults) through `BaseAuthenticator.credential/3` and then delegates to the base authenticator with concrete values. An authenticator built on its own (`BearerAuthenticator`, `BasicAuthenticator`, `ApiKeyAuthenticator`, the OAuth ones) reads `{PREFIX}_{FIELD}` through `credential/2`, where `{PREFIX}` is `envVarPrefix` (the option, else the API title without a trailing SDK). A value that was passed always wins (an explicit empty string still fails its own check), an empty variable is unset, and a credential found nowhere raises `ArgumentError` (`Missing the credential 'token': pass it, or set the environment variable X_TOKEN`) when the authenticator is built, so before the client. A bearer token and an API key may also be a zero-argument function (the token-provider callback, so it is the explicit value and wins over the environment), called for every request and checked like a passed value at call time; `inspect/1` still shows `***`. Not done: a provider for the OAuth2 client credentials or password (they are exchanged for a token already).
Where: `auth/base_authenticator.mustache` (`credential/2`, `credential/3`), `auth/scheme_authenticator.mustache`, `auth/*_authenticator.mustache`, `auth/oauth/*_authenticator.mustache`, `BetterElixirCodegen.renderSchemeAuthenticator`. Test: `test/scheme_authenticator_test.mustache` (every scheme: precedence, read once, empty and missing; it mirrors the source `scheme_authenticator.mustache` and the python and ruby SDKs have the same file), and the bearer, API key, basic and client credentials authenticator tests for the standalone variables.

### Union accessors
Rule: a union module has `from_<variant>/1`, `<variant>?/1` and `as_<variant>/1` for every model variant of a oneOf or anyOf (`composedSchemas`, `isModel`). `from_` checks the struct and returns it (another value raises `ArgumentError`), `<variant>?` is `match?/2`, `as_` returns the value or `nil`. The predicate is `<variant>?`, not `is_<variant>`: Credo's `PredicateFunctionNames` rejects an `is_` prefix, the prefix belongs to guards, and the surface extractor keys `reservation?` and `is_reservation` alike. The first parameter of the predicate and of `as_` is named after the union module in snake case (`reserve_stock_response`) because the extractor drops the receiver by that name and so reads them as zero-argument accessors. Where: `models/model.mustache` (oneOf and anyOf blocks, `elixirSnake` lambda). Test: `test/composed_schema_test.mustache`.

### Sibling default of a $ref property
Rule: a property that is a `$ref` to an enum with a `default` beside it starts at that default: `defstruct` writes `availability: :on_hold` from `vendorExtensions.siblingDefaultEnumVar` lower-cased, which is the atom the enum module defines. A pair must come after the plain atoms in `defstruct`, so `postProcessAllModels` sorts every defaulted property last, counting a sibling default (`hasStructDefault`); the shared sort on `defaultValue` alone would write `[:a, b: 1, :c]`, which does not compile. Where: `models/model.mustache`, `BetterElixirCodegen.postProcessAllModels`. Test: `test/object_serializer_test.mustache`, `BetterElixirCodegenTest.writesAPropertyAsADefaultedPairWhenItHasADefaultOrASiblingEnumDefault`.

### Manifest options
Rule: the generator options `repositoryUrl`, `homepageUrl`, `documentationUrl`, `issueTrackerUrl` and `authors` (comma separated) are written to `mix.exs` (`source_url`, `homepage_url`, Hex `maintainers` and a `links` map with Repository, Homepage, Documentation and Issues) and to the README `Package` list; an option that is not set writes nothing. The license is the shared `licenseId`, which already feeds `licenses` and the README. Every value goes through `toElixirStringLiteral`. The options live in `BetterElixirCodegen` (`putManifestProperties`) because the shared plumbing in `AbstractBetterCodegen` does not exist yet: when it lands, drop `putManifestProperties` and keep the template variables `authorList`, `packageLinksLiteral`, `repositoryUrlLiteral`, `homepageUrlLiteral`, `hasAuthors`, `hasPackageLinks` or rename them there.
Where: `mix_exs.mustache`, `readme.mustache`, `BetterElixirCodegen`. Test: `GenerateElixirClientTest.manifestOptionsReachTheMixProjectAndTheReadme` (the golden is generated with the five options).

### Container returns are strict (S1)
Rule: already satisfied by `convert_element/2`: a map or list of integers is checked element by element with the scalar `integer()` clause, so a fractional, quoted or boolean element raises `SerializationError` (`Expected Integer, got 1.5`). The out-of-range element row does not apply: the descriptor is the format-less `integer()` and Elixir integers have no width, so there is no int32 limit to check (forced).
Test: `test/object_serializer_test.mustache` (map and grid rows).

### Immutable models
Rule: already satisfied. A model is a struct and never mutates; the copy method is the struct update `%{model | field: value}`, documented in the README and SKILLS. No generated `with_` helper: it would only rename the language's own syntax.

### Unions
Rule: no change. A oneOf or anyOf here is the matched variant itself (see Union models above), so the typed factory is the variant's own struct constructor and the typed accessor is a pattern match or `is_struct/2`. A `Models.<Union>.build/1` that finds no variant raises `SerializationError`, which is the "rejects non-variants" rule.

### Documentation notes
README `Behaviours that differ from what you may expect` states the optional-versus-nullable collapse, the loud failure on an unknown enum or discriminator value, the joined repeated response header, that streaming is not supported (inject a transport), and that `Configuration` and `TransportOptions` stay separate; README `Response compression` and `Versioning` and the `Raw responses` section name the compression negotiation, that the SDK does not log, and the support policy.

### Floor
Rule: the Elixir floor stays `~> 1.17` and OTP 27: 1.17 still gets security fixes, so it is not newer than the oldest supported release.

### Not done in elixir, with the reason
- F1151: Elixir takes upload bytes, never a handle, so there is nothing for the SDK to close and nothing to test. F1140: Req reads each redirect response whole, so a hop holds no connection to release. Both are the language's limit.
- F1154: the S8 rule is checked on the golden (`GenerateElixirClientTest.theServerTravelsInTheTrailingOptsAfterTheOptionsStruct`); there is no positional server argument.

## Decisions taken in round 4 (owner decisions of 2026-11-17)

### One credential per security scheme
Rule: `Client.new(authenticator, transport_options \\ nil, schemes \\ [])` takes the optional authenticator first and a keyword list second, one optional credential per distinct security scheme, named after the scheme in snake case (`pet_store_basic:`); the keys come from the shared `clientSchemes` data (`{{#lambda.elixirSnake}}` on `paramName`), a key that is no scheme raises `ArgumentError` (`Keyword.validate!/2`), and a client with neither an authenticator nor a credential raises `ArgumentError`. The client keeps the credentials in `client.schemes` (a map by the scheme's name in the spec) and hands them, with the authenticator, to every API struct (`Api.<Group>.new/4`, field `scheme_authenticators`). Each operation resolves its credential in `api.mustache`: `BaseApi.scheme_auth(api, [ids])` over the operation's own `vendorExtensions.op.securitySchemeOrder` (the first scheme it lists that the client holds), `nil` when none matches (the call then uses the client-level authenticator), and the no-auth sentinel for `securityNone` (`security: []`, or an empty list). The base URL comes from the authenticator, else the first credential in the spec's scheme order. There is no per-call scheme override: the options structs have no `auth` field (`hasAuthField` is gone from the template) and an `:auth` key in `opts` is rejected as an unknown key by `Keyword.validate!/2`. Where: `client.mustache`, `api/api.mustache`, `api/options.mustache`, `base_api.mustache` (`scheme_auth/2`), `BetterElixirCodegen.generateOptionsFileContent`. Test: `test/client_test.mustache` (construction, host, unknown key, no credential), `test/base_api_test.mustache` (`scheme_auth/2`, precedence), `test/api/pet_api_test.mustache` (first scheme in the order wins, fallback, a scheme the operation does not name is never sent, no `auth` on the options), `test/api/store_api_test.mustache` (`security: []`, the inherited root requirement).

### Cookie values are raw or refused
Rule: a cookie value, from an operation's cookie parameter or from an authenticator, goes into the `Cookie` header exactly as given when every character is an RFC 6265 cookie-octet (`%x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E`); any other character (space, double quote, comma, semicolon, backslash, a control character, a non-ASCII one) raises `ArgumentError` ("contains characters forbidden by RFC 6265") before a request is built, and the SDK never percent-encodes (the caller pre-encodes). Already so; round 4 added the tests. Where: `api/api.mustache` (operation cookie parameters), `base_api.mustache` (authenticator cookies). Test: `test/api/pet_api_test.mustache` (`delete_pet`: a table of illegal values, a table of legal values sent verbatim), `test/base_api_test.mustache` (the same two tables for an authenticator cookie).

### Graceful close
Rule: `Client.close/1` marks the client closed and is idempotent. A request that was admitted before it runs to the end, a request that starts after it raises `RuntimeError` "ApiClient has been closed and can no longer send requests" (the one canonical error), and what the SDK started for the client (the authenticators' token-manager and discovery processes, through `close/1` of each held authenticator) is stopped by whoever wins `DefaultApiClient.claim_release/1`: `close/1` when nothing is in flight, else the last admitted request to finish. `BaseApi.invoke_api_for_result` admits a request (`begin_request/1`) and leaves it in an `after` block (`finish_request/1`, then `claim_release/1`); the state is one `:atomics` word (bit 0 closed, the rest the in-flight count) and a second one for the release claim, changed by compare-and-exchange. While a request is in flight the client still serves that request's own follow-up calls (a token fetch, a retry): `send_request` refuses only a closed client with nothing in flight. Req and Finch own the HTTP pool, so no transport is released; a transport in `req_options` is never touched by the SDK (the forced elixir shape of "the SDK-created transport is released with the last request"). A client with a custom `api_client` that is not a `DefaultApiClient` is not counted. Where: `default_api_client.mustache`, `base_api.mustache`, `client.mustache`. Test: `test/client_test.mustache` (in-flight request finishes and the release follows it, new request refused, second close is a no-op, supplied transport untouched), `test/default_api_client_unit_test.mustache` (the counter).

### No validation at construction
Rule: a struct is built with `%Model{}`, which never validates a type or an enum member; validation (a wrong type, a missing required property, an unknown enum string, an unknown discriminator value) happens when a response is decoded and raises `SerializationError`. The enforced keys of a model are the one construction check, and a language feature. Already so; round 4 added the tests and corrected the docs ("not validated beyond inline enums" was wrong: an inline enum atom is checked when the model is encoded, not when it is built). Test: `test/object_serializer_test.mustache` (`constructing a model does not validate, decoding does`).

## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs, so a template edit is unproven until `etc/ci-local.sh check Elixir` is green (ElixirFormattingSpec, ElixirLintingSpec, ElixirClientSpec). `ElixirClientSpec` must exist and run in Maven: its absence once hid Elixir from CI for months. Check that every language has the same Spec set.
- Credo `--strict` runs under `timeout -k 30 900` in ElixirLintingSpec. A hang at 0 percent CPU or a timeout is infrastructure: rerun solo before touching code. Never use `credo:disable-for-this-file`. Credo bans negated if/else (`if x not in [...], do: a, else: b`; invert the condition), prefers sigils over escaped-quote strings, enforces an arity limit after adding parameters, and wants test helper modules defined before use.
- Never pipe into `in` (`x |> in(list)` does not compile) and keep remote calls out of guards (`String.starts_with?`); pattern-match or use `when x in [...]`. Enum function names start lowercase (`{{value}}`, not `{{name}}`).
- Dialyzer is the slowest step: update every `@spec` when a type widens (`invoke_api/10`), write fully qualified typespecs (`PetstoreClient.Models.Pet.t()`), and use `MapSet.t()` for uniqueItems. Re-raise with `__STACKTRACE__`, never discard a variable silently under `--warnings-as-errors`.
- Compile warnings are errors (`mix compile --warnings-as-errors --force`): Jason duplicate keys in a map literal, a literal U+2028 or U+2029 character in a string (write the escape ` `), two files defining one module (parallel compilation breaks), an unused alias, `function_exported?` on an unloaded module (call `Code.ensure_loaded` first, Elixir 1.19 loads lazily), and `:preferred_cli_env` (use `def cli`).
- mix.lock: `src/spec/resources/generated/*/mix.lock` is gitignored in this repo, so a generated project resolves fresh dependencies. Pin ranges in `mix_exs.mustache` (`{:req, "~> 0.5"}`) and decompress gzip explicitly instead of trusting one Req version; never edit or commit a lock in the golden. `*.beam` and `erl_crash.dump` stay ignored. Do not redirect `MIX_BUILD_PATH` (it breaks discriminator module discovery).
- Test doubles and fixtures: canned responses need a body the decoder accepts once required fields are enforced (`body: "{}"` fails); `photo_urls` is a `MapSet`; `DateTime.from_iso8601/1` normalizes to UTC, so build offset DateTimes by hand; add `from_value/1` to doubles when the serializer calls it; do not redefine modules inside mocks. Redaction is by omission (`@derive {Inspect, except: ...}`), so assert absence of the secret.
- Req and tasks: the method is a lowercase atom, the proxy host a binary, UUID hex lowercase; catch errors inside `Task.async`, assert `Req.TransportError` or `Finch.TransportError` resiliently, and make every deadline (pool wait included) raise the timeout error. Compare anyOf keys through `attribute_map()`.
- Testcontainers ignores `TESTCONTAINERS_HOST_OVERRIDE` (read it explicitly) and leaks containers when Ryuk is disabled: add `ExUnit.after_suite` teardown and check `docker ps -q | wc -l` before a run. `mix deps.get` "Could not find a Mix.Project" in the formatting spec is a container flake; rerun solo. Install Elixir in devbox for a quick local `mix compile --warnings-as-errors` before the Docker loop.

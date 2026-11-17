# Ruby SDK generator

This generator emits a gem (`ruby-plus`, class `BetterRubyCodegen`) from an OpenAPI document: a `Client` entry class, `Configuration` and `TransportOptions` builders, one API class per tag with an Options object per operation, dry-struct models, an authenticator per security scheme, the error hierarchy, and the minitest suite, README, SKILLS.md, RBS signatures and lint/type-check config that ship with it. HTTP goes through Faraday 2 (`DefaultApiClient` on top, following redirects itself); models use `dry-struct` and `dry-types`; `format: time` uses `tod`, `format: duration` uses `iso8601`; `brotli` and `zstd-ruby` are an optional gem group. Tests run with minitest (`bundle exec rake test`), style with RuboCop (`rubocop-minitest`, `rubocop-rake`), types with Steep over hand-generated `.rbs` files. The floor is Ruby 3.2, the oldest release that still receives security fixes (F1087: `ruby '>= 3.2'`, `required_ruby_version >= 3.2`, `TargetRubyVersion: 3.2`, test image `ruby:3.2-alpine`, formatter image `ruby:3.2`).

## Read first

- Shared cross-language rules: [../AGENTS.md](../AGENTS.md)
- Repository root: [../../../../../../../../../AGENTS.md](../../../../../../../../../AGENTS.md)
- Codegen class: [BetterRubyCodegen.java](BetterRubyCodegen.java)
- Templates: [../../../../../../../resources/templates/ruby/](../../../../../../../resources/templates/ruby/)
- Spec classes: [../../../../../../../spec/java/io/github/mridang/codegen/spec/ruby/](../../../../../../../spec/java/io/github/mridang/codegen/spec/ruby/) (`RubySpec` is the shared interface; `RubyClientSpec` runs `rake test`, `RubyLintingSpec` RuboCop, `RubyFormattingSpec` Layout cops, `RubyTypeCheckSpec` Steep, `RubyReservedWordsSpec` the keyword list)
- Golden: [../../../../../../../spec/resources/generated/ruby/](../../../../../../../spec/resources/generated/ruby/) (never hand-edit; regenerate)
- API-surface allow rules for ruby: `etc/api-surface-allow.json` (each states its reason); canonical surface: `API_SURFACE.md`

## Layout

| Path (under `src/main/resources/templates/ruby/` unless noted) | What lives there | Edit this to change |
|---|---|---|
| `errors/*.mustache`, `errors/error_parent.mustache` | The error hierarchy, one class per file under `lib/<module>/errors/` | Error classes, messages, the `apiErrorParent` root |
| `auth/*.mustache`, `auth/oauth/*.mustache` | Authenticator base, mixin `HttpAwareAuthenticator`, api-key, bearer, basic, per-scheme wrappers, OAuth2 flows, token manager, OIDC | Auth behaviour; scheme wrappers are rendered by `renderSchemeAuthenticator` in the codegen |
| `default_api_client.mustache`, `transport_options.mustache` | Faraday transport: TLS, proxy, timeouts, redirects, decompression, injected connection | Transport options and wire behaviour |
| `base_api.mustache`, `object_serializer.mustache`, `value_serializer.mustache`, `header_selector.mustache` | Parameter styles, query/form/multipart encoding, JSON (de)serialization, content negotiation | Anything about bytes on the wire |
| `api/api.mustache`, `api/api_rbs.mustache`, `api/options*.mustache` | Per-tag API classes, their RBS, per-operation Options objects (also generated in `generateOptionsFileContent`) | Operation signatures and doc comments |
| `models/model.mustache`, `models/model_rbs.mustache`, `types.mustache` | dry-struct models, enums as constant classes, oneOf/anyOf modules, `Types::Required` | Models and unions |
| `configuration.mustache`, `servers.mustache`, `server_*.mustache`, `client.mustache`, `gem.mustache` | Builder, servers, entry class, the Zeitwerk-style entry file | Public entry surface |
| `readme.mustache`, `skills.mustache` | README (Caveats, Not supported) and SKILLS.md | Docs |
| `gemfile.mustache`, `gemspec.mustache`, `rubocop.mustache`, `steepfile.mustache`, `makefile.mustache`, `rakefile.mustache`, `vendor_rbs.mustache`, `infrastructure_rbs.mustache`, `faraday_rbs.mustache` | Manifest, lint and type-check config, signatures for third-party libraries | Dependencies, cops, Steep targets |
| `test/*.mustache`, `test/api/` | The generated minitest suite (extend these, never add a new file) | Tests |
| `src/main/java/.../generators/ruby/BetterRubyCodegen.java` | Type mapping, naming, enum/default literals, supporting-file list, formatter commands, RBS type lambdas | Type mapping, reserved words, escaping, file layout |
| `src/main/resources/reserved-words/ruby.txt` | Keyword list (checked against `Ripper` by `RubyReservedWordsSpec`) | Reserved words |

## Idioms (deliberate differences from the other eleven)

| Topic | What ruby does | Why |
|---|---|---|
| Models | `dry-struct` classes with `Types::*` attributes; enums are constant classes with `VALUES`/`validate!` | Ruby has no typed fields; dry-types is the library integration point |
| oneOf/anyOf | Module functions `actual_instance(value)` and `matched_instances(value)`, a `build` returning the variant or a `*Composite` struct; no wrapper object | dry-struct has no wrapper to hold a variant; allow rules in `etc/api-surface-allow.json` |
| One constant per file | `ServerVariable` is its own file, and the golden test `declares exactly the constant each lib file path names` enforces path-to-constant | Zeitwerk-style autoloading (the same rule as java and php); see root AGENTS.md "Forced divergences" |
| Compact module form | Every file opens `module A::B` | Nested form re-indents and changes constant lookup, the failure the constant-resolution regression covers |
| Auth host mixin | `HttpAwareAuthenticator` is a mixin module beside the `Authenticator` superclass | A module cannot inherit the class's methods |
| Visibility | `BaseApi::NO_AUTH`, `OAuth2AuthorizationCodeAuthenticator#token_manager`, `OAuth2TokenManager#mark_exchanged` and `BaseApi#config` are public | Ruby has no package-private or cross-file protected visibility |
| Cause | `ApiError`, `NetworkError`, `NetworkTimeoutError` take no `cause` argument | Ruby records it with the built-in `Exception#cause` |
| Builders | Setter parameter is named `val`; `Configuration` and `TransportOptions` keyword constructors take every value, defaults come from the builder | Positional setter style; the builder supplies defaults |
| Entry class | `Petstore::Client::Client` (a class may share its namespace's name) | API_SURFACE D05 |
| Servers list | `Servers::ALL` | Constant naming |
| Numbers | `format: number` is `Float`; `Integer` is arbitrary precision so int64 is exact | Ruby JSON parsing yields `Float` (see D83, F075) |
| Binary responses | `File`/`StringIO` stream | Matches java/csharp stream typing (F332) |
| Case of names | snake_case operations, variables and files; enum constants upper case | Ruby convention |

## Settled decisions (do not re-open in review)

Ids D01-D94 are from `PARITY_DECISIONS.md`; Fnnn are ledger findings; S1-S16 are the 2026-11-16 session decisions; "Forced" and "Resolved" entries come from the root `AGENTS.md`. The shared rule is written once in `../AGENTS.md`; each entry here is how ruby realises it.

### Cancellation: no new parameter (S7, 2026-11-16)
Rule: Ruby has no cancellation API of its own; a deadline is the only bound and it raises `NetworkTimeoutError`.
Why: Ruby's native mechanisms (`Timeout`, thread kill) are not a per-call handle callers pass, so a wrapper object would be less natural.
Where: `readme.mustache` ("Per-call cancellation"), `default_api_client.mustache`.
Test: `default_api_client_test`, `default_api_client_unit_test`.

### Parameter order (S8, F803, 2026-11-16, owner confirmation pending)
Rule: required parameters, then server, then the Options object, always, whether or not the operation has security.
Why: adding security must never move an argument.
Where: `api/api.mustache`, `api/api_rbs.mustache`, `api/options.mustache`.
Test: `test/api/` and the golden RBS under Steep.

### Injected transport is a Faraday connection (S9, F405, 2026-11-16)
Rule: `TransportOptions.builder.connection(conn)` takes a `Faraday::Connection`, keeps the SDK defaults applied, never closes it, and rejects a non-connection with `ArgumentError`.
Why: Faraday is the ecosystem's standard client; the other SDKs' standard client object is accepted the same way.
Where: `transport_options.mustache` (`connection`, line about `respond_to?(:run_request)`), `default_api_client.mustache`.
Test: `transport_options_test.mustache`, `default_api_client_test.mustache`.

### Content parameters use the SDK serializer (S2, D80, D50, F054)
Rule: `content: application/json` parameters in query, header, path and cookie go through `::<Module>::ObjectSerializer.serialize`, not `JSON.generate`.
Why: one byte-identical encoder in all twelve (wire names, `.SSS+00:00` date-times, null fields omitted, NaN rejected).
Where: `base_api.mustache`, `object_serializer.mustache`.
Test: `base_api_test.mustache`, `object_serializer_test.mustache`.

### Strict containers (S1, F006, D54)
Rule: an array or map of primitives is checked element by element in `convert_to_type`; an integer leaf must be an integer token inside the int32 or int64 range, a fractional, quoted or out-of-range value raises `Errors::SerializationError`.
Why: a container is decoded exactly like a single primitive in all twelve.
Where: `object_serializer.mustache`, `models/model.mustache` (the dry attribute is constrained for construction).
Test: `object_serializer_test.mustache`.

### Schema constraints are not enforced (S3, D55, F056)
Rule: pattern, minLength, maxLength, minimum, maximum, multipleOf, minItems, maxItems and `dependentRequired`/`dependentSchemas` are not checked by the client; the server owns validation. The README says so under "Numeric / string constraint validation".
Why: only python enforces them natively and cheaply (pydantic); Ruby's dry-types could, but the decision is uniform.
Where: `readme.mustache`, `models/model.mustache`.
Test: `readme_test.mustache`.

### allOf with a discriminator uses inheritance (S4, F057, F802)
Rule: the allOf+discriminator parent is a base class and subtypes inherit; a model reused in two unions is not re-parented, unions use modules.
Why: Ruby has single inheritance, so inheritance is reserved for the one parent.
Where: `models/model.mustache`, `BetterRubyCodegen.setsDiscriminatorDefaultOnChildren` and `formatDiscriminatorDefaultValue`.
Test: `composed_schema_test.mustache`, `metadata_test.mustache`.

### Required plus nullable (S5, D66, F215)
Rule: the key must be present on decode (absent raises), an explicit `nil` is kept, and encode always writes the key (`null` when unset); the attribute is `Types::X.optional` with the key required.
Why: the one uniform rule in all twelve.
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `object_serializer_test.mustache`, `metadata_test.mustache`.

### uniqueItems order is unspecified (S6, F319, D69)
Rule: a `Set` is sent in its own iteration order; the SDK does not sort, and `Set` parameters expand like arrays (`value.to_a` at entry).
Why: decided not to sort; the READMEs document it. Supersedes the sorted-scalars proposal in D69.
Where: `base_api.mustache`, `getUniqueItemsSetType` in the codegen (`Set<`).
Test: `base_api_test.mustache`.

### Dependencies (S10)
Rule: small pure gems (`tod`, `iso8601`) are accepted; a gem needing a native binary is not a runtime dependency.
Why: `brotli` and `zstd-ruby` are C extensions, so they sit in the `:optional` Gemfile group and are `require`d under `begin/rescue LoadError`; Accept-Encoding advertises `br`/`zstd` only when the constant is defined.
Where: `gemfile.mustache`, `gemspec.mustache`, `default_api_client.mustache`.
Test: `default_api_client_unit_test.mustache` (codec present and absent paths).

### Scope order, UUID case, blank keys, error cause (S14)
Rule: OAuth scopes keep the spec's declared order (D04, `SchemeAuthSpec` stores a LinkedHashMap); uuid is validated and written lowercase (D67, `validate_uuid` plus `downcase` on encode and decode); an api key or bearer token is blank only for empty or ASCII whitespace (`/\A[ \t\n\v\f\r]*\z/`, not `strip`, which also strips NUL; D19) and a leading `Bearer ` is stripped once in `initialize`; every status-specific error accepts an optional cause through `Exception#cause`.
Why: byte-identical behaviour in all twelve.
Where: `auth/api_key_authenticator.mustache`, `auth/bearer_authenticator.mustache`, `value_serializer.mustache`, `BetterRubyCodegen.formatRubyScopes`.
Test: `api_key_authenticator_test`, `bearer_authenticator_test`, `scheme_authenticator_test`, `value_serializer_test`.

### Secrets are redacted (S15)
Rule: `Configuration` and `TransportOptions` string forms replace the proxy password and any default header named Authorization, Proxy-Authorization, Cookie or containing key, token or secret with `***`.
Why: logging a client must not leak credentials.
Where: `configuration.mustache`, `transport_options.mustache`.
Test: `configuration_test`, `transport_options_test`.

### Timeouts, redirects, proxy validation (S16, D76, D77, D73, D79)
Rule: `timeout` is one end-to-end monotonic budget across redirect hops (each hop gets the remaining time, none left raises `NetworkTimeoutError`); a non-positive timeout raises `ArgumentError` in `Builder#timeout`; proxy URL validation happens in `build`, not in the setter; `caCertPath` is validated even with `verify_ssl` false; error text is `Invalid proxy URL: <url>` with credentials redacted; a caller-supplied stream or file is never closed.
Why: one grammar and one budget in all twelve.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `default_api_client_unit_test`, `transport_options_test`.

### Query space encoding is %20 (Resolved cross-language fix, D49, D78)
Rule: `DefaultApiClient::QueryParamsEncoder` carries the query `BaseApi` built as one opaque string under `__raw_query__`; any params Faraday encodes itself get `+` normalised to `%20`. allowReserved leaves the RFC 3986 reserved set literal except `#`, `[`, `]`.
Why: Faraday would otherwise decode the query into a Hash, sort and re-encode it. Fixing it in `build_query_string` was rejected; the custom `params_encoder` is the working path (it needs RBS declarations in `faraday_rbs.mustache` or Steep crashes). Ruby's `URI` class rewrites `'` in allowReserved values: documented FORCED residual for node, rust and ruby, not worked around.
Where: `default_api_client.mustache`, `base_api.mustache`, `faraday_rbs.mustache`.
Test: `default_api_client_test`, `base_api_test`.

### Ambient proxy settings are ignored (D74, F090)
Rule: the proxy is exactly `TransportOptions.proxy`; Faraday's `proxy` is assigned unconditionally (nil when unset) and `http.proxy_from_env = false` in the adapter block.
Why: one policy in all twelve.
Where: `default_api_client.mustache`.
Test: `default_api_client_test.mustache`.

### Redirect, serializer and error wording (D15, D16, D17, D18, D20, D21)
Rule (all of it is implemented as written since batch 6): serialize errors read `Failed to serialize object to JSON: <detail>`, deserialize `Failed to deserialize JSON: <detail>`, depth `JSON nesting depth <N> exceeds limit 1000`; redirects read `Refusing to follow redirect to non-HTTP(S) URL: <url>`, `Refusing to replay request body across HTTPS->HTTP redirect: <from> -> <to>`, `Too many redirects (exceeded <N>)`; `Response headers:` is `JSON.generate(response_headers.sort.to_h)`; one leading BOM is stripped inside `parse_json`; lists render `[a, b, c]`; User-Agent and X-Request-ID injection is case-insensitive (`casecmp?`), the caller's header wins.
Why: one wording and one byte form in all twelve; the BOM strip must live in the helper both the success path and `from_response` call.
Where: `object_serializer.mustache`, `default_api_client.mustache`, `errors/api_error.mustache`, `server_configuration.mustache`, `api/api.mustache`.
Test: `object_serializer_test`, `default_api_client_unit_test`, `api_error_test`, `server_configuration_test`.

### Auth precondition messages and OIDC rediscovery (D01, D02, D03, D05, D06, D07, D08, D09, D11)
Rule: messages are `Authorization code must not be empty` and `Must call exchange_code() before making API requests`; the OIDC delegate is rebuilt around the existing token manager (constructor takes a trailing `token_manager:`), with a `Mutex` around `resolve_delegate` and an expiry re-check inside; the `exchanged` flag lives in the manager; refresh carries client authentication through a `refresh_url:` keyword; `exchange_code` always POSTs the authorization_code grant under the manager mutex and restores state on failure; `openid` is always requested; an empty implicit access token is unset; an empty `state` is omitted; `expires_in` and `max-age` have a one-year ceiling; discovery errors drop the parser detail and keep the cause.
Why: the session must survive a discovery refresh and the text must be identical everywhere.
Where: `auth/oauth/*.mustache`.
Test: `openid_connect_authenticator_test`, `oauth2_*_authenticator_test`, `oauth2_token_manager_test`.

### Sync auth-header accessor on token flows (D12)
Rule: nothing to change; ruby's accessor is synchronous and token fetching happens inside it.
Why: ruby is a sync-only language (root AGENTS.md: auth-header method shape tracks the concurrency model).
Where: `authenticator.mustache`, `auth/*.mustache`.
Test: `*_authenticator_test`.

### Error contract tests and ApiError details (D13, D14, D27, F325)
Rule: an unusable response attaches `response.headers.to_h` to the `ApiError`; `api_error_test.rb` carries the empty-headers, empty-body and `OAuth2ServerError` rows; `ApiResult#headers` is `headers.dup.freeze`.
Why: the transport-failure signal (`nil` headers means no response) must stay distinct from an empty map.
Where: `errors/api_error.mustache`, `api_result.mustache`.
Test: `api_error_test`, `api_result_test`.

### Content negotiation and bodies (D30, D32, D35, D36, D39, D90)
Rule: a `format: binary` response is raw bytes, base64 failures wrap `ArgumentError` into `SerializationError`; a non-JSON 2xx on a non-binary return raises `SerializationError` naming the Content-Type unless the return type is `String` and the Content-Type is text-class; a binary request body is sent verbatim under any selected Content-Type; multipart scalar parts use `ObjectSerializer.stringify`; form-urlencoded rejects Hash, Struct and model items; the multipart filename has a `_` ASCII fallback per code point and a `filename*` that keeps only `A-Za-z0-9-._~`.
Why: one rule across the twelve; the multipart filename form is the go/php/rust form. The multi-consumes content-type selector rejects an unrecognised value with `ArgumentError`.
Where: `base_api.mustache`, `object_serializer.mustache`.
Test: `base_api_test`, `object_serializer_test`.

### Literals, doc prose and escaping (D40, D41, D44, D45, D46, D43)
Rule: spec strings in code are single-quoted literals with only `\\` and `\'` escaped (`toDefaultValue`, `toRubyStringLiteral`); enum string values escape only `\\` and `\'` and keep control characters raw (`escapesEnumControlCharacters`); the `escapeUnsafeCharacters` override that rewrote `#{` is removed and the gemspec description uses `lambda.stringLiteral`; enum arrays are emitted as quoted arrays, not `%w[]`; model docs use `lambda.unescapeDocComment`/`rubyDocComment` so `"` is not JSON-escaped; digit-leading names get an `n` prefix; properties keep `and`, `or`, `not`, `return` plain (`PLAIN_PROPERTY_KEYWORDS`) while parameters escape every keyword; the model-name collision set includes `Types`.
Why: a literal must evaluate to the spec's bytes; a keyword is legal as an attribute reader but not as a keyword argument.
Where: `BetterRubyCodegen.java`, `reserved-words/ruby.txt`, `servers.mustache`, `gemspec.mustache`.
Test: `RubyReservedWordsSpec`, `model_doc_test`, `readme_test`.

### Per-operation servers (D42)
Rule: server enums are emitted once per operation with a matching RBS declaration; server variants are named uniquely.
Why: duplicate variable types per variant broke the build.
Where: `api/api.mustache`, `api/api_rbs.mustache`.
Test: `server_configuration_test`, `server_variable_test`.

### Parameter value rules (D47, D48, D51, D81, D82, D62, D63)
Rule: object-valued query/header parameters convert through `JSON.parse(ObjectSerializer.serialize(v))` then expand by style; a path value of `.` or `..` raises `ArgumentError`; `format: byte` parameters are wrapped in `Base64.strict_encode64`; `format: time` is `strftime('%H:%M:%S')` plus `.%L` when non-zero; `format: uri` is checked in the `apply_format` hook while staying a `String`; an additionalProperties key colliding with a declared property is not written twice.
Why: one rule in all twelve.
Where: `base_api.mustache`, `value_serializer.mustache`, `models/model.mustache`.
Test: `value_serializer_test`, `base_api_test`, `object_serializer_test`.

### Decoding strictness (D57, D65)
Rule: `date-time` decode requires RFC 3339 (regex gate, then `Time.iso8601`; `Date.parse` is never used for date-time); a nil element in an array or map of models raises `SerializationError`.
Why: no host clock or locale dependence; no incidental NoMethodError later.
Where: `object_serializer.mustache`.
Test: `object_serializer_test`.

### Default of a `$ref` enum (D60)
Rule: the rendered default is the enum's wire value inside `Types::String.enum(...).default(...)`; if a constant name is rendered it is a defect.
Why: dry-types enums compare wire values.
Where: `fixEnumDefaultValue`, `fixEnumRefDefault` in the codegen.
Test: `metadata_test`.

### Resolved: compressed responses (Resolved cross-language fixes, D92)
Rule: when `brotli`/`zstd-ruby` is absent and a `br` or `zstd` response arrives, ruby raises rather than returning still-encoded bytes; an unsupported Content-Encoding raises; an empty body passes through for any Content-Encoding; the returned headers drop `Content-Encoding` and `Content-Length` once the body was decoded.
Why: the "unsupported Content-Encoding raises" contract.
Where: `default_api_client.mustache`.
Test: `default_api_client_unit_test`.

### Resolved: deprecation marker on every variant
Rule: a deprecated operation carries the doc-comment `@deprecated` on the data method and on `*_with_http_info`.
Why: ruby has no deprecation attribute.
Where: `api/api.mustache`.
Test: `model_doc_test`.

### Forced: `format: number` is native Float (D83)
Rule: bare `format: number` and `decimal` map to `Float` and the README carries the "Decimal / `format: number` precision" caveat; `JSON.generate(BigDecimal)` would emit scientific or quoted text and break the unquoted wire form, which the `object_serializer` test asserts (`weightKg:1.5` unquoted).
Why: documented and consistent with the seven native-float languages. Making it lossless is not decided.
Where: `typeMapping` in the codegen, `readme.mustache`.
Test: `object_serializer_test`, `readme_test`.

### Forced: free-form null retention (F314, Second parity-audit tail)
Rule: ruby strips `null` entries nested in free-form model values, as kotlin does; node and the others differ.
Why: reachable only through a petstore free-form fixture edge; aligning needs per-field metadata the schema does not carry.
Where: `object_serializer.mustache`.
Test: `object_serializer_test`.

### Forced: multipart filename file-handle Options (Second parity-audit tail)
Rule: ruby (with go and php) sends a real filename and sniffed MIME through file-handle Options types.
Why: aligning to the other nine would break the published client Options API of ruby and php.
Where: `api/options.mustache`, `base_api.mustache`.
Test: `base_api_test`.

### Known-thin gates: Steep (Known-thin gates)
Rule: Steep's `:lib` target is full strength; the `:test` target disables NoMethod, UnknownConstant, UndeclaredMethodDefinition and UnannotatedEmptyCollection because the minitest DSL has no signatures.
Why: accepted; do not over-strengthen it.
Where: `steepfile.mustache`.
Test: `RubyTypeCheckSpec`.

### RuboCop: only named cops are off (Guardrails, linting rule)
Rule: no generated file carries `# rubocop:disable`; `.rubocop.yml` turns off only the cops it names with a reason (LineLength, the Metrics cops, `Minitest/MultipleAssertions` and `EmptyLineBeforeAssertionMethods`, `Naming/AccessorMethodName`, `Lint/InterpolationCheck`, `Naming/MethodParameterName`, `Style/ArgumentsForwarding`, `Style/ClassAndModuleChildren`, `Lint/EmptyClass`, `Style/Documentation`, `Style/RedundantConstantBase`).
Why: Ruby passed its linters vacuously before this was enforced. Do not remove `Minitest/MultipleAssertions`: real clients' hand-written suites rely on it.
Where: `rubocop.mustache`, the allowlist in `getFormatterCommands`.
Test: `RubyLintingSpec`, `RubyFormattingSpec`.

### Constant resolution regression
Rule: the petstore configuration uses a client-like module (`Petstore::Client`, class `Petstore`) so a constant that resolves wrongly inside a nested namespace fails the golden.
Why: that is how the Ruby constant-resolution bug was caught.
Where: `RubySpec.getCodegenProperties`, `test/client_test.mustache`.
Test: `RubyClientSpec`.

### API surface allow rules (etc/api-surface-allow.json)
Rule: the ruby entries are idiom only: `Types` module and the Faraday params encoder, `*Composite` structs, the `HttpAwareAuthenticator` mixin, no `actual_instance` wrapper, `Servers::ALL`, `resolve_oneof/anyof` inside model code, nested `Client`, public `NO_AUTH`/`token_manager`/`mark_exchanged`/`config`, ctor without `cause`, positional `val` setters, `Configuration`/`TransportOptions` keyword constructors.
Why: each states the Ruby feature that forces it in its `reason`.
Where: `etc/api-surface-allow.json`.
Test: `python3 etc/api-surface.py check`.

## Known limitations and out of scope

- No per-call cancellation handle; a timeout is the only bound (S7 realisation above).
- No SOCKS proxy: `proxy` accepts `http://` and `https://` only.
- Webhooks, callbacks, XML bodies, Link Objects and mutual TLS are out of scope (S11, closed issues #53, #70, #78, #88, #90); the README says so.
- OpenAPI 3.2 and 3.1 loading is tracked in epics #49 and #96, 3.2 documents in #50.
- `format: number` precision is lossy (Float); lossless parsing is undecided.
- Connection reuse is open for ruby: Faraday's default `net_http` adapter opens a connection per request (F229); keep-alive would need `net_http_persistent`, an owner decision. The misleading code comment was removed.
- `ruby` models do no type or enum validation on construction beyond dry-types `Any` for untyped fields (F316, parked).
- An overflowing double literal such as `1e400` decodes to Infinity (F390, parked).
- Docs build (`yard --fail-on-warning`) is shipped in the Makefile but run by no spec (F191, deferred).
- No `LICENSE` file is generated.
- Reserved-word narrowing (D44) is conditional on Steep/RBS passing; otherwise it is recorded as forced.

## Build-breaking rules

- Do not add `# rubocop:disable` anywhere in generated code; name the cop in `rubocop.mustache` with a reason.
- Do not hand-edit the golden under `src/spec/resources/generated/ruby/`; regenerate.
- Comment syntax: a mustache `{{! ... }}` comment must never contain a literal `{{tag}}`; use `#` comments in generated Ruby and keep `=begin`/`=end` out of text (`escapeUnsafeCharacters` renames them).
- Every file opens `module A::B` in compact form, and a lib file must declare exactly the constant its path names.
- Steep must pass over `lib` with the committed `.rbs` signatures; a new Faraday hook needs an RBS declaration.
- Do not use `strip` for credential blankness (it strips NUL); use the ASCII whitespace regex.
- `bigdecimal` is pinned to `3.1.8` in the Gemfile so Bundler uses the precompiled default gem; the test image installs a compiler toolchain without `build-base` (fortify-headers breaks the zstd build on musl).
- The test image must not set `BUNDLE_WITHOUT`; the `:optional` group is installed so the br/zstd tests run.
- Add dependencies to a generated test only if every client's keep-listed manifest gets them too.
- No `.github` workflow edits from this repo (S13); RuboCop cache lives under `/root/.cache/ruby/rubocop`.

## Verify

- `etc/ci-local.sh check Ruby` (add `--quick` on a clean tree for the main session)
- `mvn verify -Dgroups=ruby` for the language end to end
- Regenerate the golden: `mvn test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false`, then commit all twelve goldens current
- `python3 etc/api-surface.py check` must not get worse after a change
- Inside a generated tree: `bundle exec rake test`, `bundle exec rubocop`, `bundle exec steep check`, `bundle exec yard doc --fail-on-warning`

### Batch 5 fixes (F824, F831, F832, F833, F839)
Rule: `Client#close` closes the `DefaultApiClient` the SDK built (a supplied Faraday connection is never closed; a closed client raises the existing closed `RuntimeError`); `Errors::TooManyRequestsError` (429, a `ClientError`) is selected in `ApiError.from_response`; the unexpected-Content-Type `SerializationError` names `Class.operation` through the `operation_id:` keyword of `invoke_api_for_result`; a `content:` cookie is percent-encoded by `ValueSerializer.encode_content_cookie` (only A-Z a-z 0-9 - . _ ~ literal); on 301/302 only POST becomes GET, on 303 every method but HEAD becomes GET, 307/308 never change the method.
Where: `client.mustache`, `errors/too_many_requests_error.mustache`, `base_api.mustache`, `value_serializer.mustache`, `default_api_client.mustache`, `infrastructure_rbs.mustache`.
Test: `client_test`, `api_error_test`, `base_api_test`, `value_serializer_test`, `default_api_client_test`.

## Decisions taken in batch 6

Each follows the audit's own proposal unless noted.

### Redirect, header and message wording (F916, F921, F922, F924, F925, F927, F928, F931)
Rule: the too-many-redirects error is raised only when the exhausted budget leaves a 3xx that still has a `Location` (a terminal 3xx is returned); the three redirect messages are the D16 text exactly; `ApiError#message` renders `Response headers: ` as `JSON.generate(headers.sort.to_h)`; `Builder#timeout` accepts only a positive number (`Invalid timeout: must be a positive number of milliseconds (got N)`, so `0` is rejected); `exchange_code('')` raises `Authorization code must not be empty`.
Where: `default_api_client.mustache`, `errors/api_error.mustache`, `transport_options.mustache`, `auth/oauth/oauth2_auth_code_authenticator.mustache`.
Test: `default_api_client_unit_test`, `api_error_test`, `transport_options_test`, `oauth2_auth_code_authenticator_test`, `api/pet_api_test` (the messages are asserted byte for byte).

### OAuth2 token expiry (F854, F910, F911, F863)
Rule: the real expiry (`now + expires_in`) is stored and one margin, `min(60, expires_in / 2)` seconds, is applied when it is read, so a 60 s token is reused for 30 s; a response without `expires_in` clears the previous expiry (no `expires_in` means no expiry); a `token_type` other than `bearer` (any case) or missing raises `OAuth2TokenError` with `Unsupported token_type in token response`, never echoing the value.
Where: `auth/oauth/oauth2_token_manager.mustache`.
Test: `oauth2_token_manager_test`.

### Per-call authenticators and discovery (F856, F859)
Rule: `BaseApi` hands its own `ApiClient` to a per-call `HttpAwareAuthenticator` (one that is not the client authenticator) before asking it for headers; an OIDC discovery document with an `issuer` that differs (trailing slash ignored) from the discovery URL minus `/.well-known/openid-configuration` raises `SerializationError`, and a discovered `authorization_endpoint` or `token_endpoint` must be https unless the host is loopback. An absent `issuer` is accepted (partial: the spec requires it).
Where: `base_api.mustache`, `auth/oauth/openid_connect_authenticator.mustache`.
Test: `base_api_test`, `openid_connect_authenticator_test`.

### Custom HTTP verbs and header merge (F838, F860 partial)
Rule: verbs Faraday's `run_request` accepts go through it; any other (QUERY, custom) is built with `build_request` and `builder.build_response`; transport default headers lose to a caller header of the same name in any case.
Where: `default_api_client.mustache` (`dispatch`), `infrastructure_rbs.mustache`.
Test: `default_api_client_unit_test`.

### Docs (F883, F884, F885, F887, F888, F889, F890, F894 partial)
Rule: the README opens with consumer Installation and Usage, then Configuration (timeout unit, proxy, request id), Custom HTTP client, Raw responses and Cancellation; Not supported lists webhooks, XML bodies and `nodeType`, Link Objects, mutual TLS and automatic retries before the ruby-specific items; build, test and lint moved to a final Development section; the `uniqueItems` text now says the order is unspecified (S6); SKILLS builds the sample model from its required attributes and documents timeouts, request id, cancellation and `*_with_http_info`; a binary response is documented as a `StringIO`.
Where: `readme.mustache`, `skills.mustache`.
Test: `readme_test`, `skills_test`.

## Decisions taken in batch 7

Each follows the audit's own proposal unless noted.

### Object-valued parameters are expanded by style (D47, F1143, F1158)
Rule: a model or Hash value of a styled parameter is reduced to its wire-named fields (`JSON.parse(ObjectSerializer.serialize(v))`) and expanded by style and explode: an exploded form query value becomes one `name=value` entry per property (`ValueSerializer.query_entries`, which the generated API methods call for every styled query parameter); `form` non-exploded, `simple`, `matrix`, `label`, `spaceDelimited` and `pipeDelimited` render through `ValueSerializer.serialize_object`. Before this batch the value went through `to_s`.
Where: `value_serializer.mustache`, `api/api.mustache`, `infrastructure_rbs.mustache`.
Test: `value_serializer_test` (object values), `api/pet_api_test` (wire bytes of `getPetTag`).

### Returned headers describe the decoded body (D92, F1165)
Rule: when a response body was decoded (a non-identity `Content-Encoding` and a non-empty body) `Content-Encoding` and `Content-Length` are dropped from `ApiHttpResponse#headers`; identity and empty bodies keep them.
Where: `default_api_client.mustache`.
Test: `default_api_client_unit_test`, `default_api_client_test` (gzip, br, zstd).

### Rendered ApiError is bounded and redacted (F1080)
Rule: `ApiError#message` renders at most 1 KiB of the body (`... (N more bytes)` suffix, control characters escaped as `\n`, `\r`, `\t`, `\xNN`) and redacts the headers with the S15 list plus `Set-Cookie`. `response_body` and `response_headers` keep the full values.
Where: `errors/api_error.mustache`, `infrastructure_rbs.mustache`.
Test: `api_error_test`.

### Proxy errors mask the userinfo; redirects strip every credential-like header (F1086, F1093)
Rule: every `Invalid proxy URL` message replaces the userinfo of any URL in it with `***@` (`TransportOptions.mask_userinfo`, D79). A cross-origin redirect hop also drops every transport default header and every header whose name contains `auth` or matches the S15 name pattern (key, token, secret), besides the fixed list and the spec's apiKey names.
Where: `transport_options.mustache`, `default_api_client.mustache`.
Test: `transport_options_test`, `default_api_client_unit_test`.

### `format: password` is masked in `inspect` (F867)
Rule: the field stays a plain value and is still sent and decoded; `Model#inspect` prints it as `***` (`PASSWORD_ATTRIBUTES`, a private constant); a model without password fields keeps the default `inspect`.
Where: `models/model.mustache`, `models/model_rbs.mustache`.
Test: `object_serializer_test` (Account).

### Protobuf duration bound (D88)
Rule: a protobuf-JSON duration is accepted up to +-315 576 000 000 s (nanoseconds only below the bound); beyond it `SerializationError`.
Where: `object_serializer.mustache`.
Test: `object_serializer_test`.

### SKILLS.md names the whole error tree (F1084)
Rule: the Error Handling section names the root type (`apiErrorParent`), `SerializationError`, `OAuth2ServerError` and `OAuth2TokenError`, and its example ends with a rescue of `SerializationError` and of the root.
Where: `skills.mustache`.
Test: `skills_test`.

### Not done in batch 7 (needs shared Java, another language, or an owner decision)
- F860 (token and discovery requests still merge transport default headers): needs a change to the `ApiClient#send_request` contract in all twelve.
- F865 (a typed `additionalProperties` schema does not check the extra values): the model never receives `additionalPropertiesType`, which the shared Java has to supply; the extras are captured and round-tripped untyped.
- F866 (sibling `default` on a `$ref` property is dropped before the template sees it), F1062 (several 2xx schemas), F1055 (a parameter named like an injected argument), F1058 (Encoding Object), F1060 (one OAuth2 flow per scheme): the loss is in `AbstractBetterCodegen`.
- F855 (401 invalidation and replay), F857 (per-scheme credentials), F858 (PKCE), F840 (form-style cookies), F229 (keep-alive), F912 (end-to-end timeout), F1100 (pagination): design decisions that are not taken.
- F1152, F1153, F1174, F1161: gates and fixtures outside the ruby tree.

## Decisions taken in batch 9

### A 401 on an OAuth2 call renews the token and replays once (F855)
Rule: `invalidate_access_token` is public on the client-credentials, password, authorization-code and OpenID Connect authenticators (the OIDC one does nothing before its first discovery); it drops the cached access token and keeps the refresh token. `BaseApi#invoke_api_for_result` calls it when the response is 401 and the authenticator responds to it, rebuilds the headers and sends the request once more; a second 401 is the usual `UnauthorizedError`, a failed renewal (any SDK error) leaves the first 401, a multipart or stream body (anything but `nil` or a `String`) is not replayed, and API key, basic, bearer, implicit and `NO_AUTH` never replay.
Why: a revoked or rotated token otherwise keeps failing until it expires.
Where: `auth/oauth/*.mustache`, `base_api.mustache`, `infrastructure_rbs.mustache`.
Test: `base_api_test` (`ReplayApiClient`), `oauth2_auth_code_authenticator_test`, `openid_connect_authenticator_test`.

### PKCE S256 on the authorization code flow (F858)
Rule: every `build_authorization_url` makes a new 43-character verifier (`SecureRandom.urlsafe_base64(32)`), keeps it in the authenticator (`pkce_verifier`, `@api private`) and adds `code_challenge` (SHA-256, base64url without padding) and `code_challenge_method=S256`; `exchange_code` sends `code_verifier` while one is held and forgets it after a successful exchange. The OIDC delegate rebuilt on rediscovery is handed the previous verifier (`pkce_verifier:`). No `nonce`. The Steep targets list the stdlib `digest`.
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`, `auth/oauth/openid_connect_authenticator.mustache`, `steepfile.mustache`.
Test: `oauth2_auth_code_authenticator_test`, `openid_connect_authenticator_test` (verifier survives a rediscovery).

### Automatic retries with backoff (F823)
Rule: `TransportOptions.builder.max_retries(n)` (default 2, `0` disables, a negative or non-integer raises `ArgumentError`; the keyword constructor takes an optional `max_retries: 2`). `DefaultApiClient#send_request` sends again after a network error that came before any response and after 408, 409, 429 and 5xx except 501; a TLS failure is not retried (another try cannot fix a certificate). The wait is 0.5 s doubling to 8 s with up to 25% jitter, or `Retry-After` (seconds or HTTP date, capped at 60 s). The deadline of the single `timeout` budget is shared by every attempt: a retry whose wait would end past it is not started and the last response or network error is returned. A body that is not `nil` or a `String` (multipart Hash, stream) is never retried. Each retry is a fresh `transmit`: the transport merges its headers again (new `X-Request-ID`), and `BaseApi` passes `retry_headers:` (a lambda rebuilding authentication and trace headers) to the SDK's own `DefaultApiClient`; a custom `ApiClient` is called as before and does its own retrying. A failed header rebuild (an SDK error) ends the retrying. Ruby has no cancellation handle (S7), so there is nothing else to honour. The README moved "Automatic retries" from Not supported to Configuration.
Why: the decided Stainless-style default.
Where: `default_api_client.mustache` (`transmit_with_retries`, `pause` is overridden by tests), `transport_options.mustache`, `base_api.mustache`, `readme.mustache`, `skills.mustache`.
Test: `default_api_client_unit_test` (retry count, statuses, `Retry-After`, budget, no retry on 4xx, `max_retries` 0, headers rebuilt), `transport_options_test`, `readme_test`, `skills_test`. Tests that expect one failing request build their client with `max_retries(0)`.

### Rendered ApiError masks the challenge headers too (F1080)
Rule: already in place since batch 7 (1 KiB body cap with `... (N more bytes)`, control characters escaped, credential headers masked). The masked set now also holds `WWW-Authenticate` and `Proxy-Authenticate` (with `Set-Cookie`); `response_headers` and `response_body` keep the full values.
Where: `errors/api_error.mustache`.
Test: `api_error_test`.

### Skipped: F825
Rust only.

## Decisions taken in batch 10

### Transport default headers stay off the identity provider (F860)
Rule: `DefaultApiClient#send_request` takes `no_default_headers: false`; when true the transport's `default_headers` are not merged into that request (the SDK headers, `User-Agent` and `X-Request-ID` still are). The OAuth2 token POST and the OIDC discovery GET pass it when the injected client is a `DefaultApiClient`; a custom `ApiClient` is called as before (its signature is unchanged, as with `retry_headers:`).
Why: an `Authorization` or API-key default header meant for the API must not reach another origin.
Where: `default_api_client.mustache`, `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`, `infrastructure_rbs.mustache`.
Test: `default_api_client_unit_test`, `oauth2_token_manager_test`, `openid_connect_authenticator_test`.

### Wording, lists and one-pass substitution (F1101, F1102, F1104, F1118)
Rule: `Must call exchange_code() before making API requests` (with the parentheses); a failed parse or decode reads `Failed to deserialize JSON: <detail>` (the target type is no longer in the stem); lists in the server-variable and content-type messages render `[a, b, c]` (no quotes); a server URL is substituted in one pass (`gsub` over `{name}` with a lookup), so a placeholder introduced by a value is never expanded, in `ServerConfiguration#url` and in the per-operation server classes.
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`, `object_serializer.mustache`, `server_configuration.mustache`, `api/api.mustache`.
Test: `oauth2_auth_code_authenticator_test`, `object_serializer_test`, `server_configuration_test`, `api/pet_api_test`.

### anyOf variants are tried in declaration order (D64, F1129)
Rule: `openapi_any_of` is rendered from `composedSchemas.anyOf` (declaration order, `AnyType` kept, duplicates removed with `uniq`) instead of the alphabetically sorted `anyOf` name set, as `openapi_one_of` already was. Before this a `TreatmentRecord` kept Medication first.
Where: `models/model.mustache`.
Test: `composed_schema_test` (TreatmentRecord).

### `format: date` parameters drop the time of day (F1083)
Rule: `ValueSerializer.serialize_styled` turns a `DateTime` or `Time` into a `Date` (an array element too) when the parameter type is `Date` or `Array<Date>`; a `DateTime` is a `Date` in Ruby, so the type check did not stop it.
Where: `value_serializer.mustache`, `infrastructure_rbs.mustache`.
Test: `value_serializer_test`.

### A parameter named like an injected argument is suffixed (F1055)
Rule: `BetterRubyCodegen#getInjectedArgumentNames` lists the names the operation method binds (`options`, `server`, `request_content_type`, `path`, `server_url`, `query_params`, `header_params`, `cookie_parts`, `cookie_value`, `request_body`, `declared_consumes`, `resolved_content_type`, `result`) and the suffix is `_param`; the wire name stays in `baseName`.
Where: `BetterRubyCodegen.java`.
Test: `BetterRubyCodegenTest`.

### Examples are attributed to their parameter (F893, F1071)
Rule: a named example reads `# Example for +<param>+ — <summary>: <value>` under the parameter in the API class, and the same line is written for each optional parameter in its options file (it was dropped there). The old three-line summary/description/value block is gone.
Where: `api/api.mustache`, `api/options.mustache`, `BetterRubyCodegen.generateOptionsFileContent`.
Test: `RubyFormattingSpec` (`pluralExamplesShouldBeAttributedToParameter`, `pluralExamplesOfAnOptionalParameterShouldBeAttributedToParameter`).

### The redirect middleware gem is gone (F1098)
Rule: `faraday-follow_redirects` is no longer a dependency or a `require`; the SDK follows redirects itself so it can strip credentials per origin.
Where: `gemspec.mustache`, `gemfile.mustache`, `default_api_client.mustache`.
Test: `default_api_client_unit_test` (the middleware is not loaded, the gemspec does not declare it).

### SKILLS.md Configuration order (F890)
Rule: `### Custom Transport Options`, `### Custom HTTP client` (was Custom Faraday Connection), `### Retries`, `### Cancellation`, then `### Timeouts` and `### Request id`.
Where: `skills.mustache`.
Test: `skills_test`.

### Tests added for behaviour already in place
F926 (token error sentences), F933 (depth and serialize sentences), F1065 (IPv6 proxy), F1073 and F1074 (bearer), F1112 (BinaryVault equality), F1124 (nickname, slug), F1137 (jpg, jpeg, gif, json parts), F1161 (response headers, empty body, typed status routes), F1167 (default User-Agent), F1170 (no body, no `Content-Type`).

### Skipped in batch 10
- F229: keep-alive needs a new adapter dependency (an owner decision); only the false code comment was corrected.
- F912: Faraday's `net_http` read timeout bounds one read; an end-to-end deadline over a trickled body needs a streaming reader.
- F1140: Faraday buffers each hop and `net_http` finishes the exchange, so an intermediate 3xx holds no connection to release.
- F1145: finished in wave 2 (see "Decisions taken in wave 2").
- F831: the `retry_after` accessor shape is a cross-language decision not yet taken (the 429 type exists).
- F858 nonce, F840, F857, F862, F843, F1081, F1087, F894, F1077: cross-language decisions not yet taken. F866, F1056, F1061, F1085, F1157, F1172: shared Java, fixtures or gates.
- F855 and F1180 are conforming since batch 9 (public `invalidate_access_token`, replay in `base_api.mustache`).

## Decisions taken in wave 2

Every item of the wave-2 work list and of the owner decisions of 2026-11-17, with its outcome for ruby. Implemented items name where and how they are tested; the rest say why they are not.

### Implemented

- **F830, error request context.** `ApiError` and every subclass (`NetworkError` and `NetworkTimeoutError` included) carry `request_method` (capitals) and `request_path` (no query string, which can hold a credential). `BaseApi#throw_api_error` passes them to `ApiError.from_response`, and `BaseApi#dispatch_request` adds them to any other `ApiError` that has none (a network failure, a refused redirect, an undecodable body). The message gains one line after the status line: `Request: GET /items/1`. Where: `errors/*.mustache`, `base_api.mustache`. Test: `api_error_test`, `base_api_test` (status route loop, the query-string case, the network case).
- **F827, per-call options.** Every Options object takes `timeout:` (milliseconds), `headers:` and `max_retries:` after `auth:`; `nil` keeps the client's value. `BaseApi#invoke_api_for_result(request_options:)` merges the headers (a name replaces one of the same name whatever its casing) and passes `timeout:` and `max_retries:` to `DefaultApiClient#send_request`, where the timeout is the one end-to-end budget and the retry count replaces `TransportOptions#max_retries` for that call. A custom `ApiClient` receives the headers only. The values are checked when the call is made (`ArgumentError`). A parameter named `timeout`, `headers` or `max_retries` takes the `_param` suffix (`getInjectedArgumentNames`). Where: `api/options.mustache`, `api/api.mustache`, `base_api.mustache`, `default_api_client.mustache`, `BetterRubyCodegen.generateOptionsFileContent`. Test: `base_api_test` (headers, timeout and retries handed to the transport, invalid values), `BetterRubyCodegenTest`.
- **F868, immutable models with a copy.** `Model#with(**changes)` returns a copy (`self.class.new(**attributes.merge(changes))`) and raises `ArgumentError` for a name that is not an attribute. A dry-struct has no writers, so the model is immutable already; instances are not frozen because the serializer builds them in two steps. Where: `models/model.mustache`, `models/model_rbs.mustache`, `vendor_rbs.mustache` (`Dry::Struct#attributes`). Test: `metadata_test` (`with`). A property named `with` would replace the method: none exists, and the reserved-word list is the Ripper keyword list only.
- **F1062, several 2xx responses.** An operation whose 2xx responses have different schemas passes `{ 200 => 'Reservation', 201 => 'ReservationReceipt' }` (a `'2XX'` key for the range) as its return type, and `BaseApi` decodes the body as the type of the status the response carries. Where: `api/api.mustache`, `base_api.mustache` (`return_type_for_status`). Test: `test/api/store_api_test` (`reserve_stock` 200 and 201).
- **F878, typed union accessors.** A oneOf or anyOf module gets, per model variant, `as_<variant>(value)` (the variant or nil), `<variant>_variant?(value)` and `from_<variant>(variant)` (checks the type, returns it, raises `ArgumentError` otherwise). A union value is the variant itself, so there is no wrapper object (see Idioms). The predicate carries the `_variant` suffix so it cannot be read as an attribute. Primitive and array variants get none. Where: `models/model.mustache`, `models/model_rbs.mustache`, the `rubySnake` lambda. Test: `composed_schema_test`.
- **F894, manifest options.** `authors` (comma separated), `repositoryUrl`, `homepageUrl`, `documentationUrl` and `issueTrackerUrl` are options of the ruby generator (`putManifestMetadata`); they reach the gemspec (`authors`, `homepage`, `source_code_uri`, `documentation_uri`, `bug_tracker_uri`) and the README. The licence is the SPDX `licenseId` that the shared generator already carries. An absent option stays absent. Test: `BetterRubyCodegenTest`. The options are declared in the ruby generator class, not in `AbstractBetterCodegen`: a shared declaration would give every language one spelling and validation.
- **F1087, floor.** Ruby 3.2: `required_ruby_version`, the `Gemfile` ruby line, `TargetRubyVersion`, the test image `ruby:3.2-alpine` and the formatter image `ruby:3.2`. `bigdecimal` is pinned to 3.1.3, the default gem of Ruby 3.2, for the same reason it was pinned to the bundled version before. The formatter also removes the `Gemfile.lock` it creates: the lock names the formatter image's platform and the musl test image cannot install from it.
- **F862, credentials (reworked in the adoption of the shared generator, see "Adopting the shared generator").** The first version read one `<PREFIX>_TOKEN` for every bearer scheme; it is superseded by a variable per scheme and field.
- **F390, non-finite doubles.** A `Float` that decodes to Infinity (`1e400`) raises `SerializationError` (`Expected a finite number`). Test: `object_serializer_test` (`PhotoMetadataLocation.lat`).
- **F1145, `format: time` milliseconds.** `Tod::TimeOfDay` holds whole seconds, so `ObjectSerializer.parse_time_of_day` keeps the fraction (cut to the millisecond) in an instance variable of the value and `format_time_of_day` writes `.SSS` when it is not zero. A `Tod::TimeOfDay` built by hand carries no fraction. Test: `object_serializer_test` (`EdgeCases.cutoff`).
- **F889 and the README notes (F828, F834, F835, F836, F837).** The README documents per-call options, `Configuration` against `TransportOptions` (they stay two objects), error request context, response compression, logging (none; use a Faraday middleware), streaming (not supported; inject a connection), versioning and the Ruby floor policy, credentials, optional against nullable, unknown enum and discriminator failures, joined repeated headers, and several 2xx responses. Test: `readme_test`.
- **G5 in `basic_authenticator_test`.** The template opened with a blanket `# rubocop:disable all`; it is gone and the file is clean under every cop.

### Already satisfied

- **F1124.** The nickname (`ZZZZZZZZZZ`) and slug (`12/ab`) rows are in `object_serializer_test`.

### Forced, or not reachable from ruby

- **F1154 (S8 server before options).** The server is the keyword argument `server:`, after the Options object by Ruby's own rule for optional keywords; there is no positional order to assert. The apiKey-name escaping test exists (`BetterRubyCodegenTest`).
- **F1140 (intermediate 3xx released).** Faraday buffers each hop and `net_http` finishes the exchange before returning it, so there is no response object left to close.
- **F229 (connection reuse).** The `net_http` adapter opens a socket per request. Keep-alive needs `faraday-net_http_persistent` and its pool, a new dependency whose timeout, TLS and `close` behaviour cannot be verified without a dedicated gate; the false comment was corrected in batch 10. Open for an owner decision.

### Needs the shared generator, the spec or `etc/` (not done here)

- **F1060** (several OAuth2 flows), **F1061** (other http schemes: the warning text), **F1153** (rule tests), **F1157** (the `petstore_auth` requirement in the spec), **F1172** (one shared spec that scans every golden for blanket disables) and **F1174** (`api-surface.py check` in `etc/ci-local.sh` and the workflows).
- **F827 for operations without an Options object** and **F866** are done: see "Adopting the shared generator".
- **API surface.** `etc/api-surface-allow.json` carries rules for the new ruby members (`with`, the per-call options, `request_method`, `request_path`, the union accessors, the time helpers); the `request_*` and `with` rules are temporary and go when the other languages' wave branches are merged and `API_SURFACE.md` names them.

## Adopting the shared generator (F827, F862, F866)

- **F827, an Options object for every operation.** `BetterRubyCodegen.mintsOptionsForEveryOperation()` returns `true`, so the shared generator writes `<Operation>Options` (and its RBS) for every operation and puts the `options` argument in every signature. An operation with no parameter and no security has an Options object that carries only `timeout:`, `headers:` and `max_retries:`, with no `auth` member (`hasAuthField` is false). `api.mustache` always passes `request_options: options` to `invoke_api_for_result`, and documents the argument as the per-call options. Test: `store_api_test` (`get_inventory`: the members, the header on the wire, an invalid timeout and retry count, still callable with no argument), `pet_api_test` (the doc block), `BetterRubyCodegenTest`.
- **F862, a variable per scheme and field.** The shared generator names the variable of every credential field of every scheme (`securitySchemeEnvVars`, `envVarPrefix`; `<PREFIX>_<FIELD>`, or `<PREFIX>_<SCHEME>_<FIELD>` when two schemes share a field). `scheme_authenticator.mustache` gives each wrapper a constructor with optional credentials and `env: ENV`. A bearer, basic or API-key wrapper hands its names to the base (`token_env:`, `username_env:`, `password_env:`, `api_key_env:`); an OAuth wrapper resolves each credential itself through `BaseAuthenticator#require_credential` and raises `ArgumentError` when it is missing. The generic bases keep the defaults `<PREFIX>_TOKEN`, `_USERNAME`, `_PASSWORD` and `_API_KEY` (`BaseAuthenticator::ENV_PREFIX` is now `envVarPrefix`, the API title, not the gem name), which `Client.with_token` uses. Order: the value given, then the variable (read once, an empty value counting as unset), then the `token_provider:` callback. The earlier order put the provider before the variable; it is now after it. Test: `scheme_authenticator_test` (built from its own variables alone, missing, empty, given value wins, read once, provider after the variable, driven by `schemeEnvironmentEntries`), `bearer_authenticator_test`, `basic_authenticator_test`, `api_key_authenticator_test`, `client_test`, `readme_test`, `skills_test`.
- **F866, a `$ref` with a sibling default.** `models/model.mustache` adds `.default(<Enum>::<MEMBER>)` from `vendorExtensions.siblingDefaultIsEnum`, `siblingDefaultEnumType` and `siblingDefaultEnumVar` when the property has no `defaultValue` of its own. Only a referenced enum is rendered; a sibling default on a `$ref` to anything else is not read yet. Test: `object_serializer_test` (`StockItem.availability`: built without it, decoded without it, a given value kept).
- **API surface.** The F862 constructor rule in `etc/api-surface-allow.json` keeps only the `env:` and `*_env:` keywords (a Ruby seam, see the rule); the `token_provider:` part and the F827 rules are gone because the other languages have them.

## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs; Java unit tests and a green golden diff prove nothing about it. Run `etc/ci-local.sh check Ruby` (client, lint, Steep, formatting) before every push, never only `RubyClientSpec`; a quick check skips lint and Steep. CI takes about 25 minutes, so push in batches.
- Every new method, constant and adapter needs an RBS signature in the same change, with exact return types (`parse_max_age`, `DEFAULT_DISCOVERY_MAX_AGE_SECONDS`, `UUID_REGEX`, the variadic `adapter`, `Base64.strict_decode64` in `vendor_rbs.mustache`). A module with no RBS declaration (`QueryParamsEncoder`) crashes Steep with `Unexpected self_type: untyped`. Catch it with `bundle exec steep check` (RubyTypeCheckSpec).
- Steep does not narrow instance variables: assign `@pkce_verifier` to a local, check the local, then pass it. Annotate empty collections (`matched = [] # : Array[String]`, `# : Hash[String, String]`). Destructuring union-typed arrays crashes Steep, so index or use named locals. Faraday fields are nilable, so use `&.` or assert non-nil first. Use `# steep:ignore ArgumentTypeMismatch` only in tests, matching the existing store tests.
- Hand-written `sig/vendor.rbs` stubs can conflict with the RBS collection (`Faraday::TimeoutError`); run `rbs collection install` before `steep check`. A private constant used from a test trips Steep; make it public or test through the API.
- RuboCop: use `clamp` (Style/ComparableClamp), drop redundant `%q` (Style/RedundantPercentQ), no rescue modifiers (Style/RescueModifier), no identical branches (Lint/DuplicateBranch), no needless `require` (Lint/RedundantRequireStatement). Respect Metrics (AbcSize, MethodLength, BlockLength 25, ParameterLists, CyclomaticComplexity) by extracting helpers; `.to_s.downcase` beats `&.downcase` for complexity. Catch it with `bundle exec rubocop`.
- Never `# rubocop:disable all` and never leave an unneeded disable (Lint/RedundantCopDisableDirective); block disables need a matching enable. A removed inline disable moves to `rubocop.mustache` with the reason written down, and keep `Minitest/MultipleAssertions` disabled there.
- Run Ruby with `-w`: overlapping regex ranges and redundant `_` placeholders print warnings. Duplicate keys in a JSON literal raise a `json` duplicate-key warning on new Ruby; build the hash without repeating a key.
- One public class or constant per lib file, required by full path, or Zeitwerk raises `uninitialized constant` (do not fold `ServerVariable` into `server_configuration.rb`; the convention test enforces it). Anchor constants with `::` in module-wrapped templates so a nested `Client` does not shadow the top-level one.
- Faraday limits: `net_http` read timeout bounds one read, not the whole exchange; it buffers each hop; a connect timeout surfaces as a connection failure and must be classified explicitly; it re-encodes query spaces (see the query encoding decision). Keep-alive needs a new adapter dependency.
- Retry tests: the default 10 s budget ends retrying before a 60 s Retry-After, so give the test a 300 s budget. Never use a 1 ms total timeout (use 1000 ms) and do not assert an absent User-Agent (Net::HTTP injects one).
- A local formatter exit 17 or 55 unformatted files is a Docker gem-fetch network timeout; grep the regen log for `failed after` and rerun instead of editing code. Keep-list the house `Rakefile`, `Steepfile` and `.rubocop.yml` or regeneration clobbers them.
- Check `docker ps -q | wc -l` before a run; leaked chasm or squid containers cause exit 137 and setup flakes. A flake (`apk add`, `bundle install`) is rerun, not patched.

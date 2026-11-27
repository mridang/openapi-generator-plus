# Ruby SDK generator

This generator emits a gem (`ruby-plus`, class `BetterRubyCodegen`) from an OpenAPI document: a `Client` entry class, `Configuration` and `TransportOptions` builders, one API class per tag with an Options object per operation, dry-struct models, an authenticator per security scheme, the error hierarchy, and the minitest suite, README, SKILLS.md, RBS signatures and lint/type-check config that ship with it. HTTP goes through Faraday 2 (`DefaultApiClient` on top, following redirects itself); models use `dry-struct` and `dry-types`; `format: time` uses `tod`, `format: duration` uses `iso8601`; `brotli` and `zstd-ruby` are an optional gem group. Tests run with minitest (`bundle exec rake test`), style with RuboCop (`rubocop-minitest`, `rubocop-rake`), types with Steep over hand-generated `.rbs` files. The floor is Ruby 3.2, the oldest release that still receives security fixes (: `ruby '>= 3.2'`, `required_ruby_version >= 3.2`, `TargetRubyVersion: 3.2`, test image `ruby:3.2-alpine`, formatter image `ruby:3.2`).

## Read first

- Shared cross-language rules: [../AGENTS.md](../AGENTS.md)
- Repository root: [../../../../../../../../../AGENTS.md](../../../../../../../../../AGENTS.md)
- Codegen class: [BetterRubyCodegen.java](BetterRubyCodegen.java)
- Templates: [../../../../../../../resources/templates/ruby/](../../../../../../../resources/templates/ruby/)
- Spec classes: [../../../../../../../spec/java/io/github/mridang/codegen/spec/ruby/](../../../../../../../../spec/java/io/github/mridang/codegen/spec/ruby/) (`RubySpec` is the shared interface; `RubyClientSpec` runs `rake test`, `RubyLintingSpec` RuboCop, `RubyFormattingSpec` Layout cops, `RubyTypeCheckSpec` Steep, `RubyReservedWordsSpec` the keyword list)
- Golden: [../../../../../../../spec/resources/generated/ruby/](../../../../../../../../spec/resources/generated/ruby/) (never hand-edit; regenerate)

## Layout

| Path (under `src/main/resources/templates/ruby/` unless noted) | What lives there | Edit this to change |
|---|---|---|
| `errors/*.mustache`, `errors/error_parent.mustache` | The error hierarchy, one class per file under `lib/<module>/errors/` | Error classes, messages, the `apiErrorParent` root |
| `auth/*.mustache`, `auth/oauth/*.mustache` | Authenticator base, mixin `HttpAwareAuthenticator`, api-key, bearer, basic, per-scheme wrappers, OAuth2 flows, token manager, OIDC | Auth behaviour; scheme wrappers are rendered by `renderSchemeAuthenticator` in the codegen |
| `default_api_client.mustache`, `transport_options.mustache` | Faraday transport: TLS, proxy, timeouts, redirects, decompression, injected connection | Transport options and wire behaviour |
| `event_stream.mustache`, `server_sent_event.mustache`, `stream_options.mustache`, `json_lines_stream.mustache`, `sse_stream.mustache` | The line splitter and SSE parser, `ServerSentEvent`, `StreamOptions`, `JsonLinesStream`, `SseStream` | JSON Lines and SSE streams |
| `body_source.mustache`, `paginator.mustache`, `page.mustache`, `link_header.mustache`, `errors/pagination_error.mustache` | `BodySource` (the upload carrier), `Paginator`, `Page`, `LinkHeader` (the RFC 8288 parser), `PaginationError` | Uploads and pagination |
| `byte_stream.mustache`, `api_stream_response.mustache`, `stream_body.mustache`, `errors/stream_error.mustache` | `ByteStream` (the binary response carrier), `ApiStreamResponse` (the transport result with an unread body), `StreamBody` (the open body, internal), `StreamError` | Streaming types |
| `base_api.mustache`, `object_serializer.mustache`, `value_serializer.mustache`, `header_selector.mustache` | Parameter styles, query/form/multipart encoding, JSON (de)serialization, content negotiation | Anything about bytes on the wire |
| `api/api.mustache`, `api/api_rbs.mustache`, `api/options*.mustache` | Per-tag API classes, their RBS, per-operation Options objects (also generated in `generateOptionsFileContent`) | Operation signatures and doc comments |
| `models/model.mustache`, `models/model_rbs.mustache`, `types.mustache` | dry-struct models, enums as constant classes, oneOf/anyOf modules, `Types::Required` | Models and unions |
| `configuration.mustache`, `servers.mustache`, `server_*.mustache`, `client.mustache`, `gem.mustache` | Builder, servers, entry class, the Zeitwerk-style entry file | Public entry surface |
| `call_hook.mustache`, `call_info.mustache`, `call_outcome.mustache`, `call_hook_recipe.mustache` | The call hook: `CallHook.chain`, the `CallInfo` and `CallOutcome` values (one file each), and the OpenTelemetry recipe partial the README and `test/call_hook_recipe_test` both include | Hook surface, recipe text |
| `readme.mustache`, `skills.mustache` | README (Caveats, Not supported) and SKILLS.md | Docs |
| `gemfile.mustache`, `gemspec.mustache`, `rubocop.mustache`, `steepfile.mustache`, `makefile.mustache`, `rakefile.mustache`, `vendor_rbs.mustache`, `infrastructure_rbs.mustache`, `faraday_rbs.mustache` | Manifest, lint and type-check config, signatures for third-party libraries | Dependencies, cops, Steep targets |
| `test/*.mustache`, `test/api/` | The generated minitest suite (extend these, never add a new file) | Tests |
| `src/main/java/.../generators/ruby/BetterRubyCodegen.java` | Type mapping, naming, enum/default literals, supporting-file list, formatter commands, RBS type lambdas | Type mapping, reserved words, escaping, file layout |
| `src/main/resources/reserved-words/ruby.txt` | Keyword list (checked against `Ripper` by `RubyReservedWordsSpec`) | Reserved words |

## Idioms (deliberate differences from the other eleven)

| Topic | What ruby does | Why |
|---|---|---|
| Models | `dry-struct` classes with `Types::*` attributes; enums are constant classes with `VALUES`/`validate!` | Ruby has no typed fields; dry-types is the library integration point |
| oneOf/anyOf | Module functions `actual_instance(value)` and `matched_instances(value)`, a `build` returning the variant or a `*Composite` struct; no wrapper object | dry-struct has no wrapper to hold a variant |
| One constant per file | `ServerVariable` is its own file, and the golden test `declares exactly the constant each lib file path names` enforces path-to-constant | Zeitwerk-style autoloading (the same rule as java and php); see root AGENTS.md "Forced divergences" |
| Compact module form | Every file opens `module A::B` | Nested form re-indents and changes constant lookup, the failure the constant-resolution regression covers |
| Auth host mixin | `HttpAwareAuthenticator` is a mixin module beside the `Authenticator` superclass | A module cannot inherit the class's methods |
| Visibility | `BaseApi::NO_AUTH`, `OAuth2AuthorizationCodeAuthenticator#token_manager`, `OAuth2TokenManager#mark_exchanged` and `BaseApi#config` are public | Ruby has no package-private or cross-file protected visibility |
| Cause | `ApiError`, `NetworkError`, `NetworkTimeoutError` take no `cause` argument | Ruby records it with the built-in `Exception#cause` |
| Builders | Setter parameter is named `val`; `Configuration` and `TransportOptions` keyword constructors take every value, defaults come from the builder | Positional setter style; the builder supplies defaults |
| Entry class | `Petstore::Client::Client` (a class may share its namespace's name) | Ruby lets a class share its namespace's name |
| Servers list | `Servers::ALL` | Constant naming |
| Numbers | `format: number` and `decimal` are `BigDecimal` (exact, plain notation, decoded decimal places kept in `WRITTEN_SCALE`); `ipv4`/`ipv6` are `IPAddr`; `Integer` is arbitrary precision so int64 is exact | typed fields are parsed with `decimal_class: BigDecimal`, an untyped number stays `Float` (D83, D95, D96) |
| Binary responses | `ByteStream` (D119) responding to `read`, `each`, `read_all` and `close` | The response is read as consumed |
| Binary request bodies | `BodySource` (D121) with `of`, `of_file`, `from_factory`, `once` and `from` | A raw binary body is sent as it is read; a multipart part stays an `IO`/`StringIO` |
| Paged lists | `<operation>_pages` returning an `Enumerable` `Paginator` with `items` and `pages` (`Enumerator`s) and `Page` values (D123, D124) | Ruby's lazy sequence is an `Enumerator`; the cursor lives in the Options object whose `copy_with` gives each page its own |
| Case of names | snake_case operations, variables and files; enum constants upper case | Ruby convention |
| Client credentials | `Client.new(authenticator = nil, transport_options = nil, <scheme>: nil, ...)`, one keyword per security scheme in snake case | Ruby has named arguments, so the credential of each scheme is a keyword named after it |
| Server argument | `server:` is a keyword argument after `options` | A Ruby keyword argument follows every positional one; the call site names it |
| Call hook | Duck type: any object with `start(info, headers)` returning an object with `finish(outcome)`, or `nil`; `CallInfo` (`operation_id`, `http_method`, `url_template`, `url`, `server_address`) and `CallOutcome` (`status_code`, `headers`, `error`) are `Data.define` values; the hook goes in through `Configuration::Builder#call_hook` and the `call_hook:` keyword of `Client` (it has no builder) | Ruby has no interface to implement; `method` would shadow `Object#method`, hence `http_method`; `Data` needs the 3.2 floor |

## Settled decisions (do not re-open in review)

Ids D01-D94 are the numbered decisions of the shared file; an Fnnn names an owner decision there or a decision written out next to it; S1-S16 are the 2026-11-16 session decisions; "Forced" and "Resolved" entries come from the shared file. The shared rule is written once in `../AGENTS.md`; each entry here is how ruby realises it.

### Cancellation: no new parameter (S7, 2026-11-16)
Rule: Ruby has no cancellation API of its own; a deadline is the only bound and it raises `NetworkTimeoutError`.
Why: Ruby's native mechanisms (`Timeout`, thread kill) are not a per-call handle callers pass, so a wrapper object would be less natural.
Where: `readme.mustache` ("Per-call cancellation"), `default_api_client.mustache`.
Test: `default_api_client_test`, `default_api_client_unit_test`.

### Parameter order (S8, 2026-11-16, owner confirmation pending)
Rule: required parameters, then server, then the Options object, always, whether or not the operation has security.
Why: adding security must never move an argument.
Where: `api/api.mustache`, `api/api_rbs.mustache`, `api/options.mustache`.
Test: `test/api/` and the golden RBS under Steep.

### Injected transport is a Faraday connection (S9, 2026-11-16)
Rule: `TransportOptions.builder.connection(conn)` takes a `Faraday::Connection`, keeps the SDK defaults applied, never closes it, and rejects a non-connection with `ArgumentError`.
Why: Faraday is the ecosystem's standard client; the other SDKs' standard client object is accepted the same way.
Where: `transport_options.mustache` (`connection`, line about `respond_to?(:run_request)`), `default_api_client.mustache`.
Test: `transport_options_test.mustache`, `default_api_client_test.mustache`.

### Content parameters use the SDK serializer (S2, D80, D50)
Rule: `content: application/json` parameters in query, header, path and cookie go through `::<Module>::ObjectSerializer.serialize`, not `JSON.generate`.
Why: one byte-identical encoder in all twelve (wire names, `.SSS+00:00` date-times, null fields omitted, NaN rejected).
Where: `base_api.mustache`, `object_serializer.mustache`.
Test: `base_api_test.mustache`, `object_serializer_test.mustache`.

### Strict containers (S1, D54)
Rule: an array or map of primitives is checked element by element in `convert_to_type`; an integer leaf must be an integer token inside the int32 or int64 range, a fractional, quoted or out-of-range value raises `Errors::SerializationError`.
Why: a container is decoded exactly like a single primitive in all twelve.
Where: `object_serializer.mustache`, `models/model.mustache` (the dry attribute is constrained for construction).
Test: `object_serializer_test.mustache`.

### Schema constraints are not enforced (S3, D55)
Rule: pattern, minLength, maxLength, minimum, maximum, multipleOf, minItems, maxItems and `dependentRequired`/`dependentSchemas` are not checked by the client; the server owns validation. The README says so under "Numeric / string constraint validation".
Why: only python enforces them natively and cheaply (pydantic); Ruby's dry-types could, but the decision is uniform.
Where: `readme.mustache`, `models/model.mustache`.
Test: `readme_test.mustache`.
Opt-in validation (D189): `Configuration#validate_constraints` (builder `validate_constraints(val)`, keyword `validate_constraints:`, default false); the validator is `ConstraintValidator` in `constraint_validator.mustache` (`@api private`, signatures in `infrastructure_rbs.mustache`), called first in `BaseApi#invoke_api_for_result`; it matches the model class exactly (`instance_of?`) and raises `ArgumentError`. Test: `test/api/store_api_test.mustache` (`bhv_models_706` to `709`).

### allOf with a discriminator uses inheritance (S4)
Rule: the allOf+discriminator parent is a base class and subtypes inherit; a model reused in two unions is not re-parented, unions use modules.
Why: Ruby has single inheritance, so inheritance is reserved for the one parent.
Where: `models/model.mustache`, `BetterRubyCodegen.setsDiscriminatorDefaultOnChildren` and `formatDiscriminatorDefaultValue`.
Test: `composed_schema_test.mustache`, `metadata_test.mustache`.
Landed (#104): the base class carries `OPENAPI_DISCRIMINATOR_NAME` and `OPENAPI_DISCRIMINATOR_MAPPING` and `build_subtype`, which `ObjectSerializer.convert_to_type` calls for a base (a const check without inheritance, so a subtype decodes as itself); a missing, empty or unknown value raises `Errors::SerializationError` naming it. A child file requires its base first (the loader sorts alphabetically, so `car.rb` would otherwise load before `vehicle.rb`). Test: `composed_schema_test.mustache` (bhv_unions_301-304, bhv_serializationobject_301).

### Required plus nullable (S5, D66)
Rule: the key must be present on decode (absent raises), an explicit `nil` is kept, and encode always writes the key (`null` when unset); the attribute is `Types::X.optional` with the key required.
Why: the one uniform rule in all twelve.
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `object_serializer_test.mustache`, `metadata_test.mustache`.

### uniqueItems order is unspecified (S6, D69)
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
Rule: `timeout` is one end-to-end monotonic budget across redirect hops (each hop gets the remaining time, none left raises `NetworkTimeoutError`); a timeout below one millisecond (milliseconds are the unit) raises `ArgumentError` in `Builder#timeout` and per call; proxy URL validation happens in `build`, not in the setter; `caCertPath` is validated even with `verify_ssl` false; error text is `Invalid proxy URL: <url>` with credentials redacted; a caller-supplied stream or file is never closed.
Why: one grammar and one budget in all twelve.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `default_api_client_unit_test`, `transport_options_test`.

### Query space encoding is %20 (Resolved cross-language fix, D49, D78)
Rule: `DefaultApiClient::QueryParamsEncoder` carries the query `BaseApi` built as one opaque string under `__raw_query__`; any params Faraday encodes itself get `+` normalised to `%20`. allowReserved leaves the RFC 3986 reserved set literal except `#`, `[`, `]`.
Why: Faraday would otherwise decode the query into a Hash, sort and re-encode it. Fixing it in `build_query_string` was rejected; the custom `params_encoder` is the working path (it needs RBS declarations in `faraday_rbs.mustache` or Steep crashes). Ruby's `URI` class rewrites `'` in allowReserved values: documented FORCED residual for node, rust and ruby, not worked around.
Where: `default_api_client.mustache`, `base_api.mustache`, `faraday_rbs.mustache`.
Test: `default_api_client_test`, `base_api_test`.

### Ambient proxy settings are ignored (D74)
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

### Error contract tests and ApiError details (D13, D14, D27)
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

### `format: number` is BigDecimal (D83, D96)
Rule: bare `format: number` and `decimal` map to `BigDecimal`. A typed field is parsed with `JSON.parse(decimal_class: BigDecimal)` (only `deserialize` asks for it, so OAuth and error bodies keep `Float`), written through a marked string that `serialize` replaces with the bare plain-notation number (`JSON.generate` would quote a `BigDecimal`), and an untyped or free-form number is converted back to `Float`. A `type: string` decimal uses the `decimal` entry of `OPENAPI_FORMATS`: read from a JSON string only, written as a JSON string. Test: `object_serializer_test`, ids 002, 410 to 412.

### Forced: free-form null retention (Second parity-audit tail)
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

### Idiom differences from the other eleven
Rule: the ruby entries are idiom only: `Types` module and the Faraday params encoder, `*Composite` structs, the `HttpAwareAuthenticator` mixin, no `actual_instance` wrapper, `Servers::ALL`, `resolve_oneof/anyof` inside model code, nested `Client`, public `NO_AUTH`/`token_manager`/`mark_exchanged`/`config`, ctor without `cause`, positional `val` setters, `Configuration`/`TransportOptions` keyword constructors.
Why: each states the Ruby feature that forces it in its `reason`.

### Mock seam (mocking001 to 009, issue #122 and the Invoker line of #131)
Rule: every API group has an interface and the client has one where the language can state it, additive next to the existing classes, using only SDK and standard-library types. `interface _<Group>ApiInterface` in each group's `.rbs` and `interface _<Client>Interface` in `sig/infrastructure.rbs`; no runtime module (duck typing needs none, and Steep's `:test` target has core diagnostics off). The tests read the signature files and compare the interface members with `public_instance_methods(false)`. Tests: test/client_test.mustache (001, 002, 007, 008), test/api/pet_api_test.mustache (003 to 006), test/readme_test.mustache (009). Where: the templates named above, `BetterRubyCodegen`, the README template (`Testing your code`), `docs/languages/ruby.md`.

## Compiled examples (#111)

- `examples/quickstart.rb` (`templates/ruby/examples/quickstart.mustache`, registered by `BetterRubyCodegen.getSupportingFileSpecs` when `generateExamples` is on) is the magic comment, `require` of the gem, then the statements of `usage_statements.mustache`, which the README Usage fence and the SKILLS Quick Start fence include too (`langruby093`). The host and token come from `ENV.fetch` (a missing variable raises `KeyError`), the client is closed in `ensure`, and with no example operation the client is only built and closed. RuboCop already covers `examples/`.
- `make examples` is `ruby -wc` on every file under `examples` plus `steep check --group=examples`; the Steepfile's `examples` target (only with `generateExamples`) uses the default diagnostics, nothing turned off. `make examples-run` maps `API_BASE_URL` to `{PREFIX}_BASE_URL`, passes a placeholder `{PREFIX}_TOKEN` and runs `bundle exec ruby -Ilib examples/quickstart.rb` (the Gemfile has no `gemspec` line, so `-Ilib` puts the gem on the load path).
- `RubyExamplesSpec` writes every fence to `.out/fences/<file>_<nn>.rb` behind the magic comment (Ruby needs no shell and no hoisting: `require` and statements are legal at the top of a file), runs `ruby -c` on each and Steep over the directory through a harness Steepfile written beside them (`--steepfile=.out/fences/Steepfile`, `signature '../../sig'`). Steep is the real gate: `ruby -c` accepts an undeclared local or a wrong constant. The harness turns off only `UndeclaredMethodDefinition`, because a fence that defines a method (an operation fence, the fake authenticator) is the reader's code with no RBS of its own. Known-thin: the parameters of such a method are `untyped`, so the call inside it is not checked; statement fences and everything built inside a method body (Options, transports, rescued error readers) are.
- Every operation fence is a method taking the client and the operation's own arguments (`def add_pet_or_report(client, pet)`, `def <op>_within_two_seconds(client)`, `client_with_transport(authenticator)`); every fence requires the gem. The Gemfile line was a `ruby` fence that Steep cannot type (`gem` is Bundler DSL), so Installation is now a `bash` fence with `bundle add`. Behaviours `langruby091` to `langruby096` are in `test/readme_test.mustache`, under `{{#generateExamples}}`.

## Known limitations and out of scope

- No per-call cancellation handle; a timeout is the only bound (S7 realisation above).
- No SOCKS proxy: `proxy` accepts `http://` and `https://` only.
- Webhooks, callbacks, XML bodies, Link Objects and mutual TLS are out of scope (S11, closed issues #53, #70, #78, #88, #90); the README says so.
- OpenAPI 3.2 and 3.1 loading is tracked in epics #49 and #96, 3.2 documents in #50.
- A `BigDecimal` keeps no trailing zero, so `parse_json` reads through `ScaledDecimal` and `parse_decimal_string` through `scaled_decimal`, which record the decimal places of the text in `WRITTEN_SCALE` (an `ObjectSpace::WeakMap`, identity keys); `decimal_to_plain` pads back to them, so `12.50` stays `12.50` (misc088).
- Connection reuse is open for ruby: Faraday's default `net_http` adapter opens a connection per request; keep-alive would need `net_http_persistent`, an owner decision. The misleading code comment was removed.
- `ruby` models do no type or enum validation on construction beyond dry-types `Any` for untyped fields (parked).
- An overflowing double literal such as `1e400` decodes to Infinity (parked).
- Docs build (`yard --fail-on-warning`) is shipped in the Makefile but run by no spec (deferred).
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
- Inside a generated tree: `bundle exec rake test`, `bundle exec rubocop`, `bundle exec steep check`, `bundle exec yard doc --fail-on-warning`

### Batch 5 fixes
Rule: `Client#close` closes the `DefaultApiClient` the SDK built (a supplied Faraday connection is never closed; a closed client raises the existing closed `RuntimeError`); `Errors::TooManyRequestsError` (429, a `ClientError`) is selected in `ApiError.from_response`; the unexpected-Content-Type `SerializationError` names `Class.operation` through the `operation_id:` keyword of `invoke_api_for_result`; a `content:` cookie is percent-encoded by `ValueSerializer.encode_content_cookie` (only A-Z a-z 0-9 - . _ ~ literal); on 301/302 only POST becomes GET, on 303 every method but HEAD becomes GET, 307/308 never change the method.
Where: `client.mustache`, `errors/too_many_requests_error.mustache`, `base_api.mustache`, `value_serializer.mustache`, `default_api_client.mustache`, `infrastructure_rbs.mustache`.
Test: `client_test`, `api_error_test`, `base_api_test`, `value_serializer_test`, `default_api_client_test`.

## Decisions taken in batch 6

Each follows the audit's own proposal unless noted.

### Redirect, header and message wording
Rule: the too-many-redirects error is raised only when the exhausted budget leaves a 3xx that still has a `Location` (a terminal 3xx is returned); the three redirect messages are the D16 text exactly; `ApiError#message` renders `Response headers: ` as `JSON.generate(headers.sort.to_h)`; `Builder#timeout` accepts only a positive number (`Invalid timeout: must be a positive number of milliseconds (got N)`, so `0` is rejected); `exchange_code('')` raises `Authorization code must not be empty`.
Where: `default_api_client.mustache`, `errors/api_error.mustache`, `transport_options.mustache`, `auth/oauth/oauth2_auth_code_authenticator.mustache`.
Test: `default_api_client_unit_test`, `api_error_test`, `transport_options_test`, `oauth2_auth_code_authenticator_test`, `api/pet_api_test` (the messages are asserted byte for byte).

### OAuth2 token expiry
Rule: the real expiry (`now + expires_in`) is stored and one margin, `min(60, expires_in / 2)` seconds, is applied when it is read, so a 60 s token is reused for 30 s; a response without `expires_in` clears the previous expiry (no `expires_in` means no expiry); a `token_type` other than `bearer` (any case) or missing raises `OAuth2TokenError` with `Unsupported token_type in token response`, never echoing the value.
Where: `auth/oauth/oauth2_token_manager.mustache`.
Test: `oauth2_token_manager_test`.

### Shared transport for credentials, and discovery
Rule: `Client#initialize` hands its own `ApiClient` to every `HttpAwareAuthenticator` it holds, the default `authenticator` and each scheme credential (removed the per-call authenticator `BaseApi` used to hand it to); an OIDC discovery document with an `issuer` that differs (trailing slash ignored) from the discovery URL minus `/.well-known/openid-configuration` raises `SerializationError`, and a discovered `authorization_endpoint` or `token_endpoint` must be https unless the host is loopback. An absent `issuer` is accepted (partial: the spec requires it).
Where: `client.mustache`, `auth/oauth/openid_connect_authenticator.mustache`.
Test: `client_test` (the shared transport reaches every credential), `openid_connect_authenticator_test`.

### Custom HTTP verbs and header merge (partial)
Rule: verbs Faraday's `run_request` accepts go through it; any other (QUERY, custom) is built with `build_request` and `builder.build_response`; transport default headers lose to a caller header of the same name in any case.
Where: `default_api_client.mustache` (`dispatch`), `infrastructure_rbs.mustache`.
Test: `default_api_client_unit_test`.

### Docs (partial)
Rule: the README opens with consumer Installation and Usage, then Configuration (timeout unit, proxy, request id), Custom HTTP client, Raw responses and Cancellation; Not supported lists webhooks, XML bodies and `nodeType`, Link Objects, mutual TLS and automatic retries before the ruby-specific items; build, test and lint moved to a final Development section; the `uniqueItems` text now says the order is unspecified (S6); SKILLS builds the sample model from its required attributes and documents timeouts, request id, cancellation and `*_with_http_info`; a binary response is documented as a `StringIO`.
Where: `readme.mustache`, `skills.mustache`.
Test: `readme_test`, `skills_test`.

## Decisions taken in batch 7

Each follows the audit's own proposal unless noted.

### Object-valued parameters are expanded by style (D47)
Rule: a model or Hash value of a styled parameter is reduced to its wire-named fields (`JSON.parse(ObjectSerializer.serialize(v))`) and expanded by style and explode: an exploded form query value becomes one `name=value` entry per property (`ValueSerializer.query_entries`, which the generated API methods call for every styled query parameter); `form` non-exploded, `simple`, `matrix`, `label`, `spaceDelimited` and `pipeDelimited` render through `ValueSerializer.serialize_object`. Before this batch the value went through `to_s`.
Where: `value_serializer.mustache`, `api/api.mustache`, `infrastructure_rbs.mustache`.
Test: `value_serializer_test` (object values), `api/pet_api_test` (wire bytes of `getPetTag`).

### Returned headers describe the decoded body (D92)
Rule: when a response body was decoded (a non-identity `Content-Encoding` and a non-empty body) `Content-Encoding` and `Content-Length` are dropped from `ApiHttpResponse#headers`; identity and empty bodies keep them.
Where: `default_api_client.mustache`.
Test: `default_api_client_unit_test`, `default_api_client_test` (gzip, br, zstd).

### Rendered ApiError is bounded and redacted
Rule: `ApiError#message` renders at most 1 KiB of the body (`... (N more bytes)` suffix, control characters escaped as `\n`, `\r`, `\t`, `\xNN`) and redacts the headers with the S15 list plus `Set-Cookie`. `response_body` and `response_headers` keep the full values.
Where: `errors/api_error.mustache`, `infrastructure_rbs.mustache`.
Test: `api_error_test`.

### Proxy errors mask the userinfo; redirects strip every credential-like header
Rule: every `Invalid proxy URL` message replaces the userinfo of any URL in it with `***@` (`TransportOptions.mask_userinfo`, D79). A cross-origin redirect hop also drops every transport default header and every header whose name contains `auth` or matches the S15 name pattern (key, token, secret), besides the fixed list and the spec's apiKey names.
Where: `transport_options.mustache`, `default_api_client.mustache`.
Test: `transport_options_test`, `default_api_client_unit_test`.

### `format: password` is masked in `inspect`
Rule: the field stays a plain value and is still sent and decoded; `Model#inspect` prints it as `***` (`PASSWORD_ATTRIBUTES`, a private constant); a model without password fields keeps the default `inspect`.
Where: `models/model.mustache`, `models/model_rbs.mustache`.
Test: `object_serializer_test` (Account).

### Protobuf duration bound (D88)
Rule: a protobuf-JSON duration is accepted up to +-315 576 000 000 s (nanoseconds only below the bound); beyond it `SerializationError`.
Where: `object_serializer.mustache`.
Test: `object_serializer_test`.

### SKILLS.md names the whole error tree
Rule: the Error Handling section names the root type (`apiErrorParent`), `SerializationError`, `OAuth2ServerError` and `OAuth2TokenError`, and its example ends with a rescue of `SerializationError` and of the root.
Where: `skills.mustache`.
Test: `skills_test`.

### Not done in batch 7 (needs shared Java, another language, or an owner decision)
- F860 (token and discovery requests still merge transport default headers): needs a change to the `ApiClient#send_request` contract in all twelve.
- F1152, F1153, F1174, F1161: gates and fixtures outside the ruby tree.

## Decisions taken in batch 9

### A 401 on an OAuth2 call renews the token and replays once
Rule: `invalidate_access_token` is public on the client-credentials, password, authorization-code and OpenID Connect authenticators (the OIDC one does nothing before its first discovery); it drops the cached access token and keeps the refresh token. `BaseApi#invoke_api_for_result` calls it when the response is 401 and the authenticator responds to it, rebuilds the headers and sends the request once more; a second 401 is the usual `UnauthorizedError`, a failed renewal (any SDK error) leaves the first 401, a multipart or stream body (anything but `nil` or a `String`) is not replayed, and API key, basic, bearer, implicit and `NO_AUTH` never replay.
Why: a revoked or rotated token otherwise keeps failing until it expires.
Where: `auth/oauth/*.mustache`, `base_api.mustache`, `infrastructure_rbs.mustache`.
Test: `base_api_test` (`ReplayApiClient`), `oauth2_auth_code_authenticator_test`, `openid_connect_authenticator_test`.

### PKCE S256 on the authorization code flow
Rule: every `build_authorization_url` makes a new 43-character verifier (`SecureRandom.urlsafe_base64(32)`), keeps it in the authenticator (`pkce_verifier`, `@api private`) and adds `code_challenge` (SHA-256, base64url without padding) and `code_challenge_method=S256`; `exchange_code` sends `code_verifier` while one is held and forgets it after a successful exchange. The OIDC delegate rebuilt on rediscovery is handed the previous verifier (`pkce_verifier:`). D140: the OIDC `build_authorization_url` appends a fresh `nonce` (`SecureRandom.urlsafe_base64(32)`) last and keeps it in the OIDC authenticator; `exchange_code` consumes it and checks the response's `id_token` in a block passed to `exchange_code_checking_id_token` (`@api private`) and `OAuth2TokenManager#exchange`: nonce, discovered issuer, client id in `aud`, `exp` with 60 s skew, else `OAuth2TokenError` with the previous session restored; no id_token, no check; no signature check (`Base64` and `JSON` only). The Steep targets list the stdlib `digest`.
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`, `auth/oauth/openid_connect_authenticator.mustache`, `steepfile.mustache`.
Test: `oauth2_auth_code_authenticator_test`, `openid_connect_authenticator_test` (verifier survives a rediscovery).

### Automatic retries with backoff
Rule: `TransportOptions.builder.max_retries(n)` (default 2, `0` disables, a negative or non-integer raises `ArgumentError`; the keyword constructor takes an optional `max_retries: 2`). `DefaultApiClient#send_request` sends again after a network error that came before any response and after 408, 409, 429 and 5xx except 501; a TLS failure is not retried (another try cannot fix a certificate). The wait is 0.5 s doubling to 8 s with up to 25% jitter, or `Retry-After` (seconds or HTTP date, capped at 60 s). The deadline of the single `timeout` budget is shared by every attempt: a retry whose wait would end past it is not started and the last response or network error is returned. A body that is not `nil` or a `String` (multipart Hash, stream) is never retried. Each retry is a fresh `transmit`: the transport merges its headers again (new `X-Request-ID`), and `BaseApi` passes `retry_headers:` (a lambda rebuilding authentication and trace headers) to the SDK's own `DefaultApiClient`; a custom `ApiClient` is called as before and does its own retrying. A failed header rebuild (an SDK error) ends the retrying. Ruby has no cancellation handle (S7), so there is nothing else to honour. The README moved "Automatic retries" from Not supported to Configuration.
Why: the decided Stainless-style default.
Where: `default_api_client.mustache` (`transmit_with_retries`, `pause` is overridden by tests), `transport_options.mustache`, `base_api.mustache`, `readme.mustache`, `skills.mustache`.
Test: `default_api_client_unit_test` (retry count, statuses, `Retry-After`, budget, no retry on 4xx, `max_retries` 0, headers rebuilt), `transport_options_test`, `readme_test`, `skills_test`. Tests that expect one failing request build their client with `max_retries(0)`.

### A call hook around every call; OpenTelemetry as a recipe (#158, #108)
Rule: `Configuration` carries one optional hook (`call_hook`, also the `call_hook:` keyword of `Client.new`, `with_token` and `with_authenticator`). `BaseApi#invoke_api_for_result` starts it right after `authenticated_headers` (the trace context is in the headers), before the body is encoded, once per call (the plain and the `_with_http_info` variant are one call, and the transport's retries and the 401 replay are inside it), and finishes it from an `ensure` on every exit, so an `Interrupt` raised into the thread is observed and re-raised, a timeout is the `NetworkTimeoutError`, and a decode failure is the `SerializationError` with the status that arrived. `spec_operation_id:` (`operationIdOriginal`, else `operationId`) and `url_template:` (`path`) come from the operation, passed through `lambda.rubyQuoted`. An error from `start` propagates, one from `finish` is swallowed. What the hook changed is kept as a delta (`header_delta`) and put back by `rebuilt_headers` on every transport retry (the hook wins) and on the replay (the fresh credentials win over a hook header of the same name). There is no adapter: the metrics API gem is 0.x, so the README "Hooks and telemetry" section shows the `TelemetryCallHook` recipe, printed from `call_hook_recipe.mustache`, which `test/call_hook_recipe_test` includes too.
Why: one hook is the only seam for editing, observing and tracing in all twelve; the core has no OpenTelemetry in the hook types; a recipe that is tested like code cannot drift from its README text.
Where: `base_api.mustache`, `api/api.mustache`, `configuration.mustache`, `client.mustache`, `call_hook.mustache`, `call_info.mustache`, `call_outcome.mustache`, `call_hook_recipe.mustache`, `infrastructure_rbs.mustache` (interfaces `_CallHook` and `_CallObserver`, the `$ERROR_INFO` global), `readme.mustache`, `skills.mustache`.
Test: `base_api_test` (bhv_hooks_001-008, 010-019, 021), `api/pet_api_test` (bhv_hooks_002 and 009 through a real operation), `call_hook_test` (bhv_hooks_020), `configuration_test`, `client_test`, `call_hook_recipe_test` (bhv_telemetry_001-014). `RecordingCallHook` is in `test_helper`. The recipe tests need `opentelemetry-metrics-sdk` as a dev dependency (in `DEV-DEPENDENCIES`, the `Gemfile` and the `test_helper` probe).

### Rendered ApiError masks the challenge headers too
Rule: already in place since batch 7 (1 KiB body cap with `... (N more bytes)`, control characters escaped, credential headers masked). The masked set now also holds `WWW-Authenticate` and `Proxy-Authenticate` (with `Set-Cookie`); `response_headers` and `response_body` keep the full values.
Where: `errors/api_error.mustache`.
Test: `api_error_test`.

## Decisions taken in batch 10

### Transport default headers stay off the identity provider
Rule: `DefaultApiClient#send_request` takes `no_default_headers: false`; when true the transport's `default_headers` are not merged into that request (the SDK headers, `User-Agent` and `X-Request-ID` still are). The OAuth2 token POST and the OIDC discovery GET pass it when the injected client is a `DefaultApiClient`; a custom `ApiClient` is called as before (its signature is unchanged, as with `retry_headers:`).
Why: an `Authorization` or API-key default header meant for the API must not reach another origin.
Where: `default_api_client.mustache`, `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`, `infrastructure_rbs.mustache`.
Test: `default_api_client_unit_test`, `oauth2_token_manager_test`, `openid_connect_authenticator_test`.

### Wording, lists and one-pass substitution
Rule: `Must call exchange_code() before making API requests` (with the parentheses); a failed parse or decode reads `Failed to deserialize JSON: <detail>` (the target type is no longer in the stem); lists in the server-variable and content-type messages render `[a, b, c]` (no quotes); a server URL is substituted in one pass (`gsub` over `{name}` with a lookup), so a placeholder introduced by a value is never expanded, in `ServerConfiguration#url` and in the per-operation server classes.
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`, `object_serializer.mustache`, `server_configuration.mustache`, `api/api.mustache`.
Test: `oauth2_auth_code_authenticator_test`, `object_serializer_test`, `server_configuration_test`, `api/pet_api_test`.

### anyOf variants are tried in declaration order (D64)
Rule: `openapi_any_of` is rendered from `composedSchemas.anyOf` (declaration order, `AnyType` kept, duplicates removed with `uniq`) instead of the alphabetically sorted `anyOf` name set, as `openapi_one_of` already was. Before this a `TreatmentRecord` kept Medication first.
Where: `models/model.mustache`.
Test: `composed_schema_test` (TreatmentRecord).

### `format: date` parameters drop the time of day
Rule: `ValueSerializer.serialize_styled` turns a `DateTime` or `Time` into a `Date` (an array element too) when the parameter type is `Date` or `Array<Date>`; a `DateTime` is a `Date` in Ruby, so the type check did not stop it.
Where: `value_serializer.mustache`, `infrastructure_rbs.mustache`.
Test: `value_serializer_test`.

### A parameter named like an injected argument is suffixed
Rule: `BetterRubyCodegen#getInjectedArgumentNames` lists the names the operation method binds (`options`, `server`, `request_content_type`, `path`, `server_url`, `query_params`, `header_params`, `cookie_parts`, `cookie_value`, `request_body`, `declared_consumes`, `resolved_content_type`, `result`) and the suffix is `_param`; the wire name stays in `baseName`.
Where: `BetterRubyCodegen.java`.
Test: `BetterRubyCodegenTest`.

### Examples are attributed to their parameter
Rule: a named example reads `# Example for +<param>+ — <summary>: <value>` under the parameter in the API class, and the same line is written for each optional parameter in its options file (it was dropped there). The old three-line summary/description/value block is gone.
Where: `api/api.mustache`, `api/options.mustache`, `BetterRubyCodegen.generateOptionsFileContent`.
Test: `RubyFormattingSpec` (`pluralExamplesShouldBeAttributedToParameter`, `pluralExamplesOfAnOptionalParameterShouldBeAttributedToParameter`).

### The redirect middleware gem is gone
Rule: `faraday-follow_redirects` is no longer a dependency or a `require`; the SDK follows redirects itself so it can strip credentials per origin.
Where: `gemspec.mustache`, `gemfile.mustache`, `default_api_client.mustache`.
Test: `default_api_client_unit_test` (the middleware is not loaded, the gemspec does not declare it).

### SKILLS.md Configuration order
Rule: `### Custom Transport Options`, `### Custom HTTP client` (was Custom Faraday Connection), `### Retries`, `### Cancellation`, then `### Timeouts` and `### Request id`.
Where: `skills.mustache`.
Test: `skills_test`.

### Tests added for behaviour already in place
F926 (token error sentences), F933 (depth and serialize sentences), F1065 (IPv6 proxy), F1073 and F1074 (bearer), F1112 (BinaryVault equality), F1124 (nickname, slug), F1137 (jpg, jpeg, gif, json parts), F1161 (response headers, empty body, typed status routes), F1167 (default User-Agent), F1170 (no body, no `Content-Type`).

### Not done in ruby, with the reason
- F229: keep-alive needs a new adapter dependency (#127); only the false code comment was corrected.
- F912: Faraday's `net_http` read timeout bounds one read; an end-to-end deadline over a trickled body needs a streaming reader (a stream call has one: the idle timeout, see "Streaming: binary responses").
- F1140: Faraday buffers each hop and `net_http` finishes the exchange, so an intermediate 3xx holds no connection to release.
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
- **F889 and the README notes.** The README documents per-call options, `Configuration` against `TransportOptions` (they stay two objects), error request context, response compression, logging (none; use a Faraday middleware), streaming (not supported; inject a connection), versioning and the Ruby floor policy, credentials, optional against nullable, unknown enum and discriminator failures, joined repeated headers, and several 2xx responses. Test: `readme_test`.
- **G5 in `basic_authenticator_test`.** The template opened with a blanket `# rubocop:disable all`; it is gone and the file is clean under every cop.

### Already satisfied

- **F1124.** The nickname (`ZZZZZZZZZZ`) and slug (`12/ab`) rows are in `object_serializer_test`.

### Forced, or not reachable from ruby

- **F1154 (S8 server before options).** The server is the keyword argument `server:`, after the Options object by Ruby's own rule for optional keywords; there is no positional order to assert. The apiKey-name escaping test exists (`BetterRubyCodegenTest`).
- **F1140 (intermediate 3xx released).** Faraday buffers each hop and `net_http` finishes the exchange before returning it, so there is no response object left to close.
- **F229 (connection reuse).** The `net_http` adapter opens a socket per request. Keep-alive needs `faraday-net_http_persistent` and its pool, a new dependency whose timeout, TLS and `close` behaviour cannot be verified without a dedicated gate; the false comment was corrected in batch 10. Open: #127.



## Adopting the shared generator

- **F827, an Options object for every operation.** `BetterRubyCodegen.mintsOptionsForEveryOperation()` returns `true`, so the shared generator writes `<Operation>Options` (and its RBS) for every operation and puts the `options` argument in every signature. An operation with no parameter and no security has an Options object that carries only `timeout:`, `headers:` and `max_retries:`, with no `auth` member (removed it from every Options object). `api.mustache` always passes `request_options: options` to `invoke_api_for_result`, and documents the argument as the per-call options. Test: `store_api_test` (`get_inventory`: the members, the header on the wire, an invalid timeout and retry count, still callable with no argument), `pet_api_test` (the doc block), `BetterRubyCodegenTest`.
- **F862, a variable per scheme and field.** The shared generator names the variable of every credential field of every scheme (`securitySchemeEnvVars`, `envVarPrefix`; `<PREFIX>_<FIELD>`, or `<PREFIX>_<SCHEME>_<FIELD>` when two schemes share a field). `scheme_authenticator.mustache` gives each wrapper a constructor with optional credentials and `env: ENV`. A bearer, basic or API-key wrapper hands its names to the base (`token_env:`, `username_env:`, `password_env:`, `api_key_env:`); an OAuth wrapper resolves each credential itself through `BaseAuthenticator#require_credential` and raises `ArgumentError` when it is missing. The generic bases keep the defaults `<PREFIX>_TOKEN`, `_USERNAME`, `_PASSWORD` and `_API_KEY` (`BaseAuthenticator::ENV_PREFIX` is now `envVarPrefix`, the API title, not the gem name), which `Client.with_token` uses. Order: the value given, then the variable (read once, an empty value counting as unset), then the `token_provider:` callback. The earlier order put the provider before the variable; it is now after it. Test: `scheme_authenticator_test` (built from its own variables alone, missing, empty, given value wins, read once, provider after the variable, driven by `schemeEnvironmentEntries`), `bearer_authenticator_test`, `basic_authenticator_test`, `api_key_authenticator_test`, `client_test`, `readme_test`, `skills_test`.
- **D108, D102-D107 and the exception audit (ruby).** A `token_provider` passed to `BearerAuthenticator` outranks the environment variable, which is read only when neither a token nor a provider was passed. A string result under a binary content type is the base64-decoded body (`BaseApi#decode_carrier`). A per-call timeout that credential lookup already used up raises `NetworkTimeoutError` before the transport is touched (`BaseApi#remaining_deadline`). `OAuth2TokenManager#stop` is idempotent and every later token request raises `OAuth2TokenError`. A refresh that fails with an SDK error falls back to the original grant; an exception raised into the thread (a cancellation) is never swallowed. A multipart type the caller declared keeps its subtype only when it is a plain `multipart/<token>`; anything else becomes `multipart/form-data`.
- **F866, a `$ref` with a sibling default.** `models/model.mustache` adds `.default(<Enum>::<MEMBER>)` from `vendorExtensions.siblingDefaultIsEnum`, `siblingDefaultEnumType` and `siblingDefaultEnumVar` when the property has no `defaultValue` of its own. Only a referenced enum is rendered; a sibling default on a `$ref` to anything else is not read yet. Test: `object_serializer_test` (`StockItem.availability`: built without it, decoded without it, a given value kept).

- **F857, one credential per security scheme.** `AbstractBetterCodegen` supplies `clientSchemes` (`schemeId`, `paramName`, `className`) and, per operation, `vendorExtensions.op.securitySchemeOrder` (the operation's own order, only schemes the client holds a credential for), `hasSecuritySchemes` and `securityNone`. `BetterRubyCodegen.processOpenAPI` adds `argName` to each client scheme: the snake-case scheme name, suffixed `_credential` (then a number) when it is a Ruby keyword, `authenticator`, `transport_options` or an earlier credential's name (`nameCredentialArguments`, `BetterRubyCodegenTest`). `client.mustache` emits `initialize(authenticator = nil, transport_options = nil, <scheme>: nil, ...)`, collects the credentials in a `schemes` hash keyed by the spec's scheme name, takes the base URL from the first credential (`authenticator` first), raises `ArgumentError` when it has none, and passes `(api_client, config, authenticator, schemes)` to each API class. `BaseApi#scheme_authenticator(names)` returns the credential of the first name held, else `nil`, which `invoke_api_for_result` resolves to the default `authenticator` (and to no credential without one); a `security: []` operation passes `NO_AUTH`. `api.mustache` calls `scheme_authenticator([...])` with the operation's own order. The Options objects have no `auth` member. A client without schemes keeps the plain `initialize(authenticator, transport_options = nil)`. Test: `base_api_test` (`security scheme credentials`: first held wins, unheld skipped, fallback, no credential, `security: []`), `client_test` (the keyword names, base URL, no credential, shared transport), `api/pet_api_test` (the credential on the wire, no `auth` member, the docs).
- **F840, cookie parameters.** The operation sends the value raw when every character is an RFC 6265 cookie-octet and raises `ArgumentError` ("Cookie value for 'x' contains characters forbidden by RFC 6265") before any request otherwise; `api.mustache` and `BaseApi#authenticated_headers` use the same pattern. Test: `api/pet_api_test` (`operation cookie parameter`: space, quote, comma, semicolon, backslash, non-ASCII and control characters fail, legal values go out untouched).
- **F1078, graceful close.** `DefaultApiClient#send_request` counts the request in flight (`enter_request`/`leave_request` under a mutex) and runs `perform_request`; `close` marks the client closed and releases the SDK-built connection at once when nothing is in flight, otherwise the last request to finish does (`release_connection`). A request after `close` raises the one `RuntimeError` "ApiClient has been closed and can no longer send requests"; a second `close` changes nothing; a connection from `TransportOptions` is never closed. Test: `default_api_client_unit_test` (in-flight finishes, second close, closed rejects, caller-supplied untouched), `client_test`.
- **F316, no validation at construction.** An inline enum property is now `Types::Any` like every other (it was `Types::String.enum`, which raised at `new`), and its `OPENAPI_TYPES` entry names the enum class (`Pet::StatusEnum`), so `ObjectSerializer.convert_to_type` checks `VALUES` on decode and the decode fails with `SerializationError`. A missing required value still raises at construction. Test: `object_serializer_test` (`enum unknown value detection`: construction with an unknown enum string and with wrong types does not raise, decode of the same fails and names the enum).

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
- Retry tests: the default 60 s budget (D137) ends retrying before a 60 s Retry-After, so give the test a 300 s budget. Never use a 1 ms total timeout (use 1000 ms) and do not assert an absent User-Agent (Net::HTTP injects one).
- A local formatter exit 17 or 55 unformatted files is a Docker gem-fetch network timeout; grep the regen log for `failed after` and rerun instead of editing code. Keep-list the house `Rakefile`, `Steepfile` and `.rubocop.yml` or regeneration clobbers them.
- Check `docker ps -q | wc -l` before a run; leaked chasm or squid containers cause exit 137 and setup flakes. A flake (`apk add`, `bundle install`) is rerun, not patched.

- Trace context from the caller (#136): `OpenTelemetry.propagation.inject` reads `OpenTelemetry::Context.current` on the caller's thread (`Context.with_current`); no thread or fiber is created on the header path (D95).

- **F865 (typed `additionalProperties`, #103), done.** The model gets `ADDITIONAL_PROPERTIES_TYPE`, and `ADDITIONAL_PROPERTIES_FORMAT` (`byte`, `uuid`) and `ADDITIONAL_PROPERTIES_NULLABLE` when the value schema needs them; `ObjectSerializer.deserialize_model` decodes each extra through `convert_to_type`, rejects a null unless nullable, and `sanitize_for_serialization` writes bytes as base64. The RBS is `Hash[String, V]?`.

## Streaming: binary responses (capability 1 of #114, D118 to D122)

### The transport returns an unread body
Rule: `ApiClient#send_streaming(method, url, headers, body, timeout:, max_retries:, idle_timeout:)` returns an `ApiStreamResponse` (`status_code`, lower-cased `headers`, the open `body` that responds to `read(length)` and `close`, `read_bounded(max_bytes)`, an `inspect` of the status only). The default implementation raises `::NotImplementedError` with `<transport class> cannot stream: it does not implement send_streaming, ...`, and `BaseApi#dispatch_stream` raises the same for a transport object that has no `send_streaming` at all (a duck-typed client), so an injected `ApiClient` keeps working for buffered calls and never falls back to buffering. A non-2xx response is returned like any other (open body), as `send_request` returns it, and `BaseApi` reads at most 64 KiB of it, closes it and raises the typed error of a buffered call.
Where: `api_client.mustache`, `api_stream_response.mustache`, `base_api.mustache` (`invoke_api_for_stream` is `invoke_api_for_result(stream: true)`, so the hook, the headers, the authentication and the 401 replay are the buffered code), `default_api_client.mustache`.
Test: `default_api_client_unit_test` (`bhv_streaming_001` and the rest), `base_api_test` (008, 017, 018, 030), `client_test` (021), golden `pet_api_test` (009).

### One header phase for both paths
Rule: `transmit_with_retries`, `transmit` and `run_hop` take an optional `StreamSpec` (nil for a buffered call). The header merge, the retry loop, the redirect loop and the deadline are shared; only `run_hop` differs: for a stream it calls `open_hop`, which runs the Faraday request on a producer thread (`StreamBody#start`) and returns the `StreamBody` as soon as the status and headers are known, so the response that the retry and redirect code looks at has `status` and `headers` like a Faraday response and a hop that is retried or followed is closed (`abort_hop`). The call `timeout` is the wait for that head, so it stops at the headers. A stream call advertises `gzip, deflate` only. The head is reported by `DefaultApiClient::HeadSignal`, mixed into the SDK's own `Net::HTTP` object through the adapter block; a supplied connection has no such hook, so its head is reported with the first body chunk (or at the end of an empty body).
Where: `default_api_client.mustache`.
Test: every existing `default_api_client_unit_test` row, `bhv_streaming_010`, `014`, `016`.

### The body: idle timeout, truncation, decoding, cancellation
Rule: the producer pushes the head, the chunks (`on_data`) and the end through a `SizedQueue` of 8, so a slow reader makes it wait. A read waits on the queue for at most `idle_timeout` (`Queue#pop(timeout:)`, which is why the floor of Ruby 3.2 matters) and raises `NetworkTimeoutError`; the producer's own read timeout is set to a year, since the SDK bounds the wait itself. A producer failure after the head is raised from the read as `StreamError` (`NetworkTimeoutError` for a Faraday timeout, an `ApiError` with the real status for a gzip or deflate body that does not decode), and `net_http` already raises when a body ends before its `Content-Length` (`ignore_eof = false`). `close` and `cancel` stop the producer thread, which closes the socket without draining the body, and close the queue to wake a blocked reader; an exception raised into a reading thread (a cancel) aborts the stream and propagates as it is, never as a timeout. A stream that is not closed and not read to its end keeps its producer thread and socket until the process ends.
Where: `stream_body.mustache`, `default_api_client.mustache`, `transport_options.mustache` (`idle_timeout`, milliseconds, default 60000, `0` or `nil` disables), `errors/stream_error.mustache`.
Test: `default_api_client_unit_test` (`bhv_streaming_011`, `012`, `013`, `015`, `019`, `020`, `031`, `032`, `033`).

### Closing the client cancels open streams
Rule: `DefaultApiClient` counts an open stream as an in-flight request until it ends, is closed or is cancelled, and tracks it. `close` marks the client closed, cancels every open stream (each frees its count, the last one releases the connection) and returns; requests in flight that are not streams finish as before. A stream whose head arrives after `close` is closed and raises the closed-client error. The next read of a cancelled stream raises `RuntimeError` ("ApiClient has been closed and can no longer send requests").
Where: `default_api_client.mustache`, `stream_body.mustache`.
Test: `client_test` (`bhv_streaming_021`).

### Binary operations return `ByteStream`
Rule: `BetterRubyCodegen.markBinaryStream` sets `streamsBinary` on `vendorExtensions.op` and rewrites `returnType` to `ByteStream` (and clears `isResponseFile`) for an operation whose `streamKind` is `binary`, so the operation, `..._with_http_info`, the YARD tags and the RBS say `ByteStream` and `api.mustache` calls `invoke_api_for_stream`. `ByteStream` has `status_code`, `headers`, `bytes_delivered`, `read(length = nil, outbuf = nil)` (the semantics of `IO#read`), `each(chunk_size)`, an idempotent `close`, `closed?`, a `read_all` that reads everything and closes, and an `inspect` of status and bytes delivered; `ApiResult#raw_body` is empty. The README and SKILLS Streaming fence calls the first binary operation of the spec (`postProcessSupportingFileData`).
Where: `BetterRubyCodegen`, `api/api.mustache`, `api/api_rbs.mustache`, `byte_stream.mustache`, `readme.mustache`, `skills.mustache`.
Test: golden `pet_api_test` (`bhv_streaming_009`, the existing avatar, document and photo rows now close their stream), `default_api_client_unit_test` (`bhv_streaming_029`).

## Streaming: JSON Lines and server-sent events (capability 2 of #114, D120 to D122)

### Stream-only operations
Rule: `BetterRubyCodegen.markBinaryStream` also sets `streamsSse`, `streamsJsonLines`, `streamTyped` and `streamItemRuby` for an operation whose `streamOnly` decorator is true, and rewrites `returnType` to `SseStream` or `JsonLinesStream`. `api.mustache` then emits only `<operation>_stream(<parameters>, options = nil, stream_options = nil)` (no buffered method, no `_with_http_info`), built from the same partials as the buffered method (`api/operation_prelude`, `operation_doc_args`, `operation_call_tail`), and `api_rbs.mustache` lists only the stream method. `sseTerminator` reaches the SSE call as `terminator:`. The README and SKILLS fences call the first JSON Lines and the first SSE operation of the spec (`postProcessSupportingFileData`).
Where: `BetterRubyCodegen`, `api/api.mustache`, `api/api_rbs.mustache`, `api/operation_*.mustache`, `readme.mustache`, `skills.mustache`.
Test: golden `store_api_test` (`bhv_streaming_028`), `client_test` (the interface rows).

### The parser and the two carriers
Rule: `EventStream::LineReader` splits on CRLF, LF and a lone CR with a skip-LF flag (a CR at the end of a chunk followed by an LF in the next one is one line end), drops a BOM, raises `TooLongError` past `max_event_bytes`, and reports an unterminated last line through `terminated?`; `SseParser` follows the WHATWG grammar (a cut-off event is dropped, an empty data buffer keeps the id, a retry value only counts as digits, an id with NUL is ignored). `JsonLinesStream` skips blank lines, decodes each line with `ObjectSerializer.deserialize`, and raises `SerializationError` naming the 1-based line; a plain `String` item type is not decoded. Both are `Enumerable`; a failure ends the stream and closes the connection.
Where: `event_stream.mustache`, `json_lines_stream.mustache`.
Test: `event_stream_test` (the one mirror file: `bhv_streaming_002` to `005`, `bhv_sse_001` to `005`, `008` to `011`, `014`, `016`, `017`, `019`, `021` to `023`), `default_api_client_unit_test` (`bhv_streaming_006`, `007`).

### Reconnect
Rule: `SseStream` opens its first connection in `SseStream.open` (a failure is raised there) and reconnects after a clean end, a `NetworkError` or an idle timeout. A clean end counts against `max_reconnects` like a failure: `StreamError` once it is used up, nothing raised for `0`. The wait is the server `retry`, else `Retry-After` (429, 502, 503, 504, capped at 60 s), else `reconnect_delay` doubling to 30 s with up to 25% jitter (`reconnect_wait_ms`, pure, tested without sleeping). Each connection is a call through `invoke_api_for_result(stream: true)`, so the hook, headers and token are built again, and `Last-Event-ID` is sent only when it is not empty. No reconnect after a 204, a status other than 408, 429 and 5xx, a response that is not `text/event-stream` (`SerializationError`), a cancellation or `client.close`; the counter resets after an event; a request with a body does not reconnect unless `max_reconnects` is set. An event named `error` raises `ApiError` with the real status and the data as its body.
Where: `sse_stream.mustache`, `base_api.mustache` (`invoke_api_for_sse`, `invoke_api_for_json_lines`).
Test: `default_api_client_unit_test` (`bhv_sse_007`, `015`, `018`), `base_api_test` (`bhv_sse_006`, `012`, `013`, `020`).

## Uploads and pagination (capabilities 3 and 4 of #114, D121, D123, D124)

### A raw binary request body is a `BodySource`
Rule: `BetterRubyCodegen.markBinaryUpload` (before the shared pass, so the signature arguments copy it) sets the body type of an operation whose body is `format: binary`, not multipart, to `BodySource`, which `toRbsApiType` qualifies as `::<Module>::BodySource`. `BodySource` (`body_source.mustache`) is replayable (`of`, `of_file`, `from_factory`) or single use (`once`); `BaseApi#serialize_body` hands it through untouched. `DefaultApiClient#dispatch` opens the source for one send, frames it (`Content-Length` for a known length, chunked otherwise, a `String` for bytes) and `release`s what the SDK opened; a retry, a 307 or 308 and a 401 replay open it again when it is replayable, and a single-use source is never retried and refuses a 307 or 308 with `Refusing to replay a streamed request body` (`ApiError`). `HeadSignal#request` turns Net::HTTP's own retry off for a streamed request. `SseStream.open(single_use_body: true)` never reconnects.
Where: `body_source.mustache`, `default_api_client.mustache`, `base_api.mustache`, `sse_stream.mustache`, `BetterRubyCodegen`.
Test: `default_api_client_unit_test` (`bhv_streaming_023` to `027`), `event_stream_test` (`bhv_sse_016`), golden `pet_api_test` (the avatar rows).

### `<operation>_pages`, `Paginator`, `Page`, `PaginationError`
Rule: the shared detector computes `vendorExtensions.op.pagination`; the templates only render it. `api/operation_pages.mustache` emits `<op>_pages` with the base method's parameters, which calls one factory per style, each page being `<op>_with_http_info(paged_options.copy_with(<param>: page_cursor))`: `Paginator.of_cursor(start:, fetch:, items:, next_cursor:, request_path:, has_more: nil)` for a token cursor, and for an id cursor with `has_more:` declared; `Paginator.of_offset(start:, fetch:, items:, request_path:, has_more: nil)`, where the next offset is the offset sent (or 0) plus the items received; and `Paginator.of_page(start:, fetch:, items:, request_path:, has_more: nil)`, where the next page is the page sent (or 1) plus one; the offset and page lists end on an empty page or when a declared `has_more` is false. `Paginator.of_link(base_url:, fetch:, items:, request_path:)` follows the `rel="next"` of the Link header, and `Paginator.of_next_url(base_url:, fetch:, items:, next_url:, request_path:)` follows the URL the body names, a next URL list ending when that property is absent, nil or empty. The two URL styles each page through `<op>_page(..., page_url:)`, whose body is the buffered call with the path replaced by the absolute URL and the query cleared; `invoke_api_for_result` joins an authentication query to a URL that already has one with `&`. Ruby has no language limit on these styles. `Paginator` is an `Enumerable` whose `items` and `pages` build a fresh walker per call, so nothing is read before the first read, there is one request per page, and a second loop starts again. An end is an absent, nil or empty cursor or no `rel="next"`; a cursor equal to the one just sent (or a `Link` to the page just read, or a foreign origin) is held back: its page is delivered and the next read raises `PaginationError`, a direct child of the root error that carries the path without the query. The Options object of a paged operation gets `copy_with`.
Where: `paginator.mustache`, `page.mustache`, `link_header.mustache`, `errors/pagination_error.mustache`, `api/api.mustache`, `api/api_rbs.mustache`, `api/operation_pages.mustache`, `api/options*.mustache`, `base_api.mustache`, `BetterRubyCodegen` (`hasPagesMethod`, the `pagedOperation` of the README and SKILLS).
Test: golden `pet_api_test` (`bhv_pagination_001` to `032`, over `PagedServer` in `test_helper`, an in-process stateful server).

## Response body limit (D138, issue #112)

Rule: `TransportOptions#max_response_bytes` (builder `max_response_bytes(val)`, constructor keyword, default `nil`) is `nil` or a positive `Integer`, checked by `validate_max_response_bytes!` in both with `Invalid maxResponseBytes: must be a positive number of bytes (got N)`; it is in `inspect`. `DefaultApiClient#buffered_dispatch` reads a buffered hop (redirect hops included) through Faraday's `on_data` and raises once the running count passes the limit, which leaves the read unfinished so `StrictEof` closes the socket; `check_decoded_size!` then counts the decoded size by feeding Zlib, `Brotli::Decompressor` or `Zstd::StreamingDecompress` 256 compressed bytes at a time before `decompress_body` runs. Both raise `Errors::ResponseTooLargeError` (`errors/response_too_large_error.mustache`, a direct `ApiError` with the real status, lower-cased headers, an empty body, `limit`, and `retryable?` always false), which the retry loop never retries because it is not a `NetworkError`. Streamed calls are not bounded. The RBS is in `infrastructure_rbs.mustache` and `vendor_rbs.mustache`.
Where: `transport_options.mustache`, `default_api_client.mustache`, `errors/response_too_large_error.mustache`, `gem.mustache`, `infrastructure_rbs.mustache`, `vendor_rbs.mustache`, `BetterRubyCodegen`.
Test: `transport_options_test` (`bhv_options_069`), `default_api_client_unit_test` (`bhv_transport_095`, `bhv_transport_096`), `api_error_test` (`bhv_errors_002`).
- D190 (#91): `auth/scheme_authenticator.mustache` adds the deprecated-scheme sentence to the class comment when `schemeDeprecated`, and `METADATA_URL` (a `toRubyStringLiteral` literal, `metadataUrlLiteral`) when `hasOAuth2MetadataUrl`; `scheme_authenticator_rbs.mustache` declares `METADATA_URL: String` for Steep. Test: `bhv_authscheme_700` in `scheme_authenticator_test` (first scheme with a metadata URL, `metadataUrlTest`).

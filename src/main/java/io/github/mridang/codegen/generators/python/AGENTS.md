# Python SDK generator

This generator emits a Python client package: pydantic v2 models, an `async` operation API per tag, a synchronous `DefaultApiClient` on a urllib3 `PoolManager` that the async layer calls through `asyncio.to_thread`, frozen-dataclass `Configuration` and `TransportOptions`, and the shared authenticator, error and serializer set. The generated tests run on pytest with `pytest-asyncio`; the quality gates are `ruff check`, `ruff format --check` and `mypy` (non-strict, default rule set) over sources and tests. The generated `pyproject.toml` declares `requires-python = ">=3.10"` (the oldest non-end-of-life CPython, F1087; `[tool.mypy] python_version = "3.10"` checks it) and the specs pin the test image to `python:3.14-alpine`; runtime dependencies are `urllib3`, `python_dateutil` and `pydantic`, with `brotli` and `zstandard` as an optional `[compression]` extra.

## Read first

- Shared cross-language rules: [../AGENTS.md](../AGENTS.md)
- Repository rules: [AGENTS.md](../../../../../../../../../AGENTS.md)
- Codegen class: [BetterPythonCodegen.java](BetterPythonCodegen.java)
- Templates: [src/main/resources/templates/python/](../../../../../../../resources/templates/python/)
- Spec classes: [src/spec/java/io/github/mridang/codegen/spec/python/](../../../../../../../../spec/java/io/github/mridang/codegen/spec/python/) (`PythonSpec` shared config, `PythonClientSpec`, `PythonFormattingSpec`, `PythonLintingSpec`, `PythonTypeCheckSpec`, `PythonReservedWordsSpec`)
- Golden: [src/spec/resources/generated/python/](../../../../../../../../spec/resources/generated/python/) (never hand-edit; regenerate)

## Layout

| Path (under `templates/python/` unless noted) | What lives there | Edit this to change |
|---|---|---|
| `BetterPythonCodegen.java` | type mapping, reserved words, enum and default rendering, formatter commands, supporting-file list | a Python type, a default literal, escaping, the ruff formatter step |
| `models/model.mustache` | one pydantic `BaseModel` per schema, oneOf/anyOf `RootModel` wrappers, discriminator maps | model fields, constraints, composed schemas |
| `_types.mustache`, `_duration.mustache` | package-qualified `Annotated` aliases (`OffsetDateTime`, `JsonNumber`, `LaxFloat`, `UrlStr`, `ProtobufDuration`, `PasswordStr`, `Int*`) | the wire form or validation of a scalar format |
| `object_serializer.mustache` | JSON parse, serialize, deserialize, `stringify`, strict leaf checks | decode strictness, depth cap, base64, date and uuid forms |
| `value_serializer.mustache` | path, query, header styles, allowReserved encoder | parameter encoding |
| `base_api.mustache`, `api/api.mustache`, `api/options*.mustache` | the request pipeline and the per-tag operations and options classes | operation signatures, content-type selection, server enums |
| `default_api_client.mustache`, `api_client.mustache`, `transport_options.mustache`, `configuration.mustache` | transport: redirects, proxy, TLS, timeout, decompression, redaction | anything on the wire below the operation |
| `errors.mustache`, `errors/*.mustache` | `ApiError` hierarchy, OAuth2 errors, header rendering, `ErrorFactory`, `is_retryable`, `request_id`; `errors/schema_exception.mustache` renders one class per declared error schema (issue 123) | error classes and messages |
| `auth/*.mustache`, `auth/oauth/*.mustache` | authenticators, token manager, OIDC | auth behaviour |
| `server_configuration.mustache`, `servers.mustache` | server list and variables | server constants |
| `paginator.mustache`, `page.mustache`, `link_header.mustache`, `errors/pagination_exception.mustache`, `api/operation_pages*.mustache` | `Paginator[T, C]` (the lazy async sequence a `<op>_pages` method returns), `Page[T, C]`, the RFC 8288 `Link` parser and `PaginationException`, and the per-operation pages method | pagination |
| `body_source.mustache` | `BodySource` (a raw binary request body, replayable or single use) and `OpenedBody` (one send of it) | uploads |
| `byte_stream.mustache`, `api_stream_response.mustache`, `errors/stream_exception.mustache` | `ByteStream` (the binary response carrier), `ApiStreamResponse` (the transport result with an unread body) and `StreamBody` (its Protocol), `StreamException` | streaming types |
| `readme.mustache`, `skills*.mustache`, `pyproject_toml.mustache`, `makefile.mustache` | README, SKILLS.md, manifest, Makefile (`typecheck`, `docs`) | docs, dependencies, ruff and mypy config |
| `test/*.mustache`, `test/*.py` | the tests shipped inside the generated SDK | generated test files (add rows to the existing mirror file) |

## Idioms (deliberate differences from the other eleven)

| Topic | What Python does | Why |
|---|---|---|
| Async surface | operations are `async def`; the blocking urllib3 call runs in `asyncio.to_thread`; the authenticator `get_auth_headers` is a single sync method | urllib3 is synchronous; sync-only auth languages have one `getAuthHeaders` (root AGENTS, forced divergences) |
| Models | pydantic models on the `ApiModel` base with `Field(alias=wire_name)`; constructors take attribute or wire names and never validate | pydantic v2 is the idiomatic validator and serializer |
| Scheme credentials | one keyword-only `Optional[Authenticator]` argument per security scheme on `Client`, named after the scheme in snake case | Python has keyword arguments; the scheme id stays the key BaseApi looks the credential up by |
| Configuration | frozen `@dataclass` with a builder and also a public `__init__` | Python has no private constructors |
| Authenticators | frozen dataclasses; secrets are `repr=False` | dataclass fields are the constructor parameters |
| Cause on errors | error constructors take an optional `cause` that also sets `__cause__` | `raise ... from` chaining is the native mechanism |
| Decimals | bare `type: number` is `JsonNumber` (`Decimal`), exact on decode, widened to float on encode (FORCED, see D83) | stdlib json cannot emit a raw `Decimal` without a dependency |
| Date-time | model fields use the `OffsetDateTime` alias, not bare `AwareDatetime` (see FD-datetime) | pydantic's default JSON form is not the canonical `.SSS+00:00` |
| Servers | module-level constants `SERVER_0..` and one `Enum` per server variable | module constants are the Python namespace |
| Composed schemas | `RootModel` wrappers with plumbing members (`actual_instance`, `oneof_schemas` ...) | pydantic hooks |
| Cancellation | no parameter; `asyncio` task cancellation (S7) | asyncio is the native handle |
| Lifecycle | context manager plus graceful, idempotent `close()`, which the `ApiClient` interface does not require; a closed client raises `RuntimeError` | the interface does not require `close` |
| Transport injection | a trailing `pool_manager` argument on `DefaultApiClient` and `Client` | urllib3 pool manager is the transport object |
| Streaming transport | a second `runtime_checkable` Protocol `StreamingApiClient` (`send_streaming`), checked with `isinstance` at call time; the unsupported error is `NotImplementedError` naming the transport | a Protocol that a transport matches structurally cannot hand it a default method, so the default is the check (D118: second interface where defaults cannot exist) |
| Binary response | `ByteStream`, an async context manager and async iterator of `bytes` with `read(n)`, `read_all()` and an idempotent sync `close()` | Python has no `InputStream`; the SDK is async-first, so the carrier is async (D119) |
| Binary request | `BodySource` (`of`, `of_file`, `from_factory`, `once`), sent as it is read from the worker thread; the constructors take bytes, binary file objects, iterables of `bytes` and async iterables of `bytes` | an upload must never be buffered; the SDK is async-first, so an async source is read on the loop of the call through `run_coroutine_threadsafe` (D121) |
| Optional codecs | `brotli` and `zstandard` are imported behind `try/except ImportError` and advertised only when installed | keeps the mandatory dependency set small (FD-compression) |

## Settled decisions (do not re-open in review)

Cross-language rules are written once in the shared file; each entry below says only how Python realises it. Decisions found already canonical (D09 to D14, D22 aside, D23 to D26, D28, D31, D33, D34, D36 to D38, D44, D52, D56, D58, D59, D63 to D65, D67, D68, D70, D72, D74, D75, D84 to D89, D91, D93, D94) need nothing Python specific and are covered by the shared file.

### Injected transport (S9, 2026-11-16)
Rule: the client accepts the language's standard HTTP client object, keeps SDK defaults, never closes a caller-supplied one and fails fast on conflicts. For Python the standard object in this SDK is the urllib3 `PoolManager`, passed as `DefaultApiClient(pool_manager=...)`; a supplied manager makes the TLS and proxy settings of `TransportOptions` inert, which the docstring states.
Why: every comparable SDK accepts the standard client. The session text names httpx for Python; the code, dependencies and tests use urllib3, so urllib3 is what this entry records (see Known limitations).
Where: `default_api_client.mustache` (constructor, `close`), `client.mustache`.
Test: `test_default_api_client_unit.mustache`, `test_client.mustache`.

### Cancellation (S7, 2026-11-16)
Rule: no new parameter. Cancelling the awaiting asyncio task aborts that call; cancellation raises `asyncio.CancelledError`, never `NetworkTimeoutException`. `close()` no longer cancels anything (see "Decisions taken in round 4").
Why: a wrapper or options object is less natural than the handle Python callers already use.
Where: `base_api.mustache`, `default_api_client.mustache`, `readme.mustache` (section "Per-call cancellation").
Test: `test_base_api.mustache`, `test_default_api_client_unit.mustache`. The README section "Per-call cancellation" now states the behaviour.

### Parameter order (S8, 2026-11-16, owner confirmation pending)
Rule: required parameters, then `server`, then the optional-parameters object, with no cancellation handle in Python; the same whether or not the operation has security.
Why: adding security must never move an argument.
Where: `api/api.mustache` (`signatureArgsNoServer`, `server=server`).
Test: the petstore operations in `test/api/*`.

### Secrets in string forms (S15, 2026-11-16)
Rule: `Configuration` and `TransportOptions` reprs show the proxy password and any default header named `Authorization`, `Proxy-Authorization`, `Cookie` or containing `key`, `token` or `secret` as `***`.
Why: a repr reaches logs.
Where: `transport_options.mustache` (`_redact_headers`), `configuration.mustache`.
Test: `test_transport_options.mustache`, `test_configuration.mustache`.

### Timeout budget, stream ownership, proxy and CA validation (S16, D76, D77, D73, D79, 2026-11-16)
Rule: `timeout` is one end-to-end budget (a `time.monotonic()` deadline captured in `send_request`, each hop gets `urllib3.Timeout(total=remaining)`, `NetworkTimeoutException` at or below zero before a hop is sent); `timeout` must be `None` or at least 1 ms, enforced in `__post_init__` so direct construction is covered; `ca_cert_path` is validated whenever set, even with `verify_ssl=False`; proxy and CA errors use the shared grammar with userinfo shown as `***@`; a caller-supplied stream or file is never closed by the SDK.
Why: the frozen dataclass has a public `__init__`, so validation cannot live only in the builder setter; a lenient CA check hid a bad path behind `verify_ssl=False`.
Where: `default_api_client.mustache`, `transport_options.mustache`. urllib3 read timeouts stay per socket operation (library limit, documented).
Test: `test_default_api_client_unit.mustache`, `test_transport_options.mustache`.

### Scope order, uuid case, blank keys, error cause (S14, 2026-11-16)
Rule: OAuth scopes keep the spec's declared order (generator side, D04); uuid is written lowercase and validated; an api key or bearer token is blank only when empty or ASCII whitespace `" \t\n\x0b\x0c\r"` (D19), so the code uses `.strip(' \t\n\x0b\x0c\r')`, never bare `.strip()`; every status-specific error accepts an optional `cause`.
Why: `str.strip()` also removes non-ASCII whitespace, which the other eleven keep.
Where: `auth/api_key_authenticator.mustache`, `auth/bearer_authenticator.mustache`, `errors.mustache`, `AbstractBetterCodegen` (scopes).
Test: `test_api_key_authenticator.mustache`, `test_bearer_authenticator.mustache`, `test_api_error.mustache`.

### Typed errors per declared schema (issue 123)
Rule: each operation passes its own `{status: factory}` map as `error_schemas` to `_invoke_api_for_result`, and `ApiException.from_response` picks the factory by exact status, then `4XX`/`5XX`, then `default`. A schema error class subclasses its status class, its `error_body` property returns the decoded model, and a body that does not match leaves the status exception with the raw body and `__cause__`. `SerializationException` raised while decoding a response carries `status_code`, `response_headers`, `response_body`, `request_method` and `request_path` (no query), filled in `_invoke_api_for_result`. Where: `errors.mustache`, `errors/schema_exception.mustache`, `errors/serialization_exception.mustache`, `base_api.mustache`, `api/api.mustache`. Test: `test_api_error.mustache` (errors201 to errors204) and `test_base_api.mustache` (errors205).

### Required plus nullable (S5, D66, 2026-11-16)
Rule: the key must be present on decode, explicit `null` is legal and kept, and encode writes the key (`null` when unset).
Why: the OpenAPI distinction between optional and nullable is part of the contract.
Where: `models/model.mustache` (required field is `Optional[X] = Field(alias=...)` with no default; serialization must not drop it under `exclude_none`).
Test: `test_composed_schema.mustache` and the model tests in the golden `test/`.

Query side (D98): a required nullable query parameter is `Optional[T]` with no default on the Options dataclass, and `None` is sent as `query_params[name] = ''`.

### allOf with a discriminator (S4, 2026-11-16)
Rule: Python uses class inheritance, and only the allOf-plus-discriminator parent is a parent class; a model reused in two unions is not re-parented, unions are `RootModel` wrappers.
Why: a class has one parent. The discriminator map and `get_discriminator_value` live on the parent.
Where: `models/model.mustache` (`{{#parent}}`, `discriminator_value_class_map`).
Landed (#104): the child imports its base at the top of the module (`x-allof-child`, `decorateClassicDiscriminatorChild` drops the duplicate bottom import) and the base carries a `model_validator(mode='wrap')` (`_route_to_subtype`) that decodes into the subtype the discriminator names, so a top-level decode and a property typed as the base both return the subtype; a missing, empty or unknown value raises `ValueError` naming it.
Test: `test_composed_schema.mustache` (`TestAllOfVehicle`, bhv unions 301-304, bhv serializationobject 301).

### Schema constraints (S3, 2026-11-16)
Rule: the client does not promise to enforce `pattern`, length, range, `multipleOf` or item-count keywords; the server owns validation. Python is the one language that enforces them, because pydantic does so natively and cheaply: `Field(min_length, max_length, ge/gt, le/lt, multiple_of)`, and `pattern` through a `re` validator (never `Field(pattern=)`, because pydantic-core has no look-around). A violation raises `SerializationException` when a document is decoded; constructing a model never validates.
Why: this supersedes the D55 Python line, which deleted the Field constraints; the owner chose to keep native pydantic enforcement.
Where: `models/model.mustache` (lines with `min_length`, the `re.match` validator), `readme.mustache`.  The README has the single section "Schema constraints" (the older "Numeric / string constraint validation" section is gone) and says a constructor sends the value it was given while decoding raises `SerializationException`.
Test: golden model tests (petstore declares no constraint, so the pattern validator is covered only by which needs a spec property).
Opt-in check (D189): `Configuration.validate_constraints` (default False) runs the private `_constraint_validator.py` (`_constraint_validator.mustache`, dispatch on the model class through the MRO) from `BaseApi._invoke_api_for_result` before the call hook. Python needs it like the others because construction never validates (F316); decoding still validates through pydantic. Test: `test/test_base_api.mustache` (`TestConstraintValidation`, bhv models 706-709).

### Strict containers (S1, 2026-11-16)
Rule: a response that is a container of primitives is checked element by element like a single primitive.
Why: a map of int32 must not accept a quoted number.
Where: `object_serializer.mustache` (`_STRICT_LEAF_TYPES`, `_STRICT_LEAF_ADAPTERS`).
Test: `test_object_serializer.mustache`, `test_base_api.mustache`.

### Content parameters (S2, D80, D50, 2026-11-16)
Rule: a `content: application/json` parameter in query, header, path or cookie is encoded with `self._object_serializer.serialize(...)`, then encoded once by the location's normal rule; any failure is `SerializationException`.
Why: `json.dumps` produced different bytes (key names, date-times, spacing).
Where: `api/api.mustache` (the content branch, no inline `import json`).
Test: `test_value_serializer.mustache`, petstore content-parameter operation.

### uniqueItems order (S6, 2026-11-16)
Rule: left unspecified; a set is sent in the language's natural collection order and nobody sorts it. The README says so.
Why: Python `set` has no declared order; this supersedes D69.
Where: `object_serializer.mustache` `_sanitize_for_serialization` still sorts comparable set members; that is the pre-decision behaviour and conflicts with S6 (see Known limitations).
Test: none that pins an order.

### Out of scope (S11, S12, 2026-11-16)
Rule: callbacks and webhooks, XML bodies and nodeType, Link Objects and mutual TLS are not generated and the README says so; OpenAPI 3.1 and 3.2 are tracked by epics #96 and #49.
Why: closed backlog issues #53, #70, #78, #88, #90; 3.2 documents load through the shared parser extension (D150).
Where: `readme.mustache`.
Test: none.

### Dependencies and test runner (S10, S13, 2026-11-16)
Rule: a small pure library or a version bump is acceptable; no dependency needing a native binary (brotli and zstandard are optional and probed). Caches live under `/root/.cache/python/{pip,mypy,ruff}` (`PythonSpec.getCacheEnv`); do not touch `.github` workflows from this repository.
Why: the CI test job is CPU bound and spec parallelism is 3; workflow issues are filed in `mridang/action-runner-common` #47 to #50.
Where: `PythonSpec.java`, `pyproject_toml.mustache`.
Test: the Python specs themselves.

### Reserved words and model-name collisions (D45, D43)
Rule: model names that collide with typing or pydantic names the models import get the `Model` prefix; a digit-leading name gets a leading `n` before snake-casing; `escapeReservedWord` stays for Python keywords only.
Why: shared base fix; the Python set is the typing and pydantic names the model template imports.
Where: `BetterPythonCodegen.java` (reserved model names, `escapeReservedWord`), `reserved-words/python.txt`.
Test: `PythonReservedWordsSpec`, golden model names.

### String literals, descriptions and enums (D40, D41, D46)
Rule: spec strings written into a Python literal go through the shared `escapeStringLiteral` (backslash, quote, newline, `\xHH`), including defaults, server urls and variables, OAuth urls and scopes (`formatPythonScopes`); the quote-only `replace` and the dead `'''` override of `escapeUnsafeCharacters` are gone; `pyproject_toml.mustache` uses `lambda.jsonString` over the plain description; the enum quote is escaped, not stripped (`shouldEscapeQuotationMark` is not overridden to false).
Why: a literal must evaluate to exactly the spec's bytes.
Where: `BetterPythonCodegen.java` (`toDefaultValue`, `escapeUnsafeCharacters`), `servers.mustache`, `api/api.mustache`, `pyproject_toml.mustache`, `models/model.mustache`.
Test: golden build plus `PythonFormattingSpec` (which forbids HTML numeric entities outside `test/`).

### Per-operation server enums (D42)
Rule: one `Enum` class per server-variable entry of an operation; no silent re-definition where the last one wins.
Why: differing enum sets for one variable name were wrong in Python.
Where: `api/api.mustache` (server enum block), `AbstractBetterCodegen.enrichOperationServers`.
Test: petstore per-operation servers in `test/api/*`.

### Defaults and numeric literals (D60, D61)
Rule: a default on a `$ref` enum is the enum member; `fixEnumDefaultValue` acts only when `prop.isEnum`; a `Decimal` default is rendered `Decimal('1.5')`.
Why: the prefixing step was corrupting every default containing a dot.
Where: `BetterPythonCodegen.java` (`toDefaultValue`, `fixEnumDefaultValue`), `models/model.mustache` (`default_factory` for enum refs).
Test: golden models with defaults.

### Integers (D54)
Rule: `Int32` and `Int64` aliases (`Annotated[StrictInt, Field(ge=..., le=...)]`) in `_types`, used by models and by the container leaf checks; fractional, quoted and out-of-range values raise `SerializationException`.
Why: containers and models must share one range check.
Where: `BetterPythonCodegen.java` (`typeMapping`), `_types.mustache`, `object_serializer.mustache`.
Test: `test_object_serializer.mustache`.

### Scalar formats (D57, D62, D81, D51, D58, F-base64)
Rule: `date-time` decodes only RFC 3339 with an offset (regex gate then `isoparse`; the alias is strict, so epoch numbers and space separators fail); `uri` is `UrlStr`, an `AfterValidator` on `str` that returns the original text; `time` is `HH:mm:ss`, plus `.SSS` only when the millisecond part is non-zero (truncated, not rounded); `format: byte` parameters are base64-encoded in `stringify`; malformed base64 on decode raises (`b64decode(validate=True)`).
Why: pydantic's `HttpUrl` normalises and re-encodes `https://example.com` with a trailing slash; dateutil's `parse()` is lenient.
Where: `_types.mustache`, `object_serializer.mustache`, `BetterPythonCodegen.java` (`typeMapping`, `postProcessModelProperty`).
Test: `test_object_serializer.mustache`.

### Bare number precision (D83, FD-number)
Rule: bare `type: number` is exact on decode (`json.loads(..., parse_float=Decimal)`, a `LaxFloat` before-validator converts `Decimal` to `float` for `format: float/double`, `JsonNumber` is strict and rejects a quoted string) and is a FORCED float widening on encode; the README Decimal section and the root AGENTS state this.
Why: pydantic and stdlib json cannot emit a raw `Decimal` as a JSON number without adding a dependency.
Where: `_types.mustache`, `object_serializer.mustache` (`parse_json`, `default=` hook), `readme.mustache`.
Test: `test_object_serializer.mustache`.

### Parameter encoding (D47, D48, D49, D53, D78, D82, D90)
Rule: object-valued query and header parameters become a wire-keyed map via `json.loads(self.serialize(model))` and expand per style and explode; a path value of exactly `.` or `..` raises `ValueError`; `allowReserved` keeps `safe="/?@!$&'()*+,;=:"` (so `#`, `[`, `]` are percent-encoded); query, form and multipart parts are built in one in-order pass (`quote(safe='')`, or `encode_query_allowing_reserved` for `AllowReservedValue`); sets and tuples expand like lists; multipart filename fallback is `_` per code point.
Why: declaration order and one encoder make the wire bytes identical to the other eleven.
FORCED: urllib3 rewrites a literal `[` and `]` after the SDK encodes them (the value is preserved, the bytes differ); this is documented in the `value_serializer` allowReserved doc and in the shared forced list.
Where: `value_serializer.mustache`, `base_api.mustache`, `default_api_client.mustache`, `api/api.mustache`.
Test: `test_value_serializer.mustache`, `test_base_api.mustache`.

### Bodies and content negotiation (D30, D32, D35, D39)
Rule: a binary success response is a `ByteStream` (see "Streaming: binary responses"; the `bytes` decode of `_invoke_api_for_result` stays for `format: byte` and for a caller that passes the `'bytes'` return type); a non-JSON content type on a non-binary return is the text for `str` and otherwise `SerializationException` naming the Content-Type; a binary request body is sent verbatim under whatever content type the caller selected (`raw_body`, `read()` for a file-like); a form-urlencoded field that is a model or dict raises `SerializationException`; an unrecognised `request_content_type` raises `ValueError`.
Why: a typo must never ride the wire as an undeclared Content-Type, and a binary body must never go through the JSON serializer.
Where: `base_api.mustache`, `api/api.mustache`, `object_serializer.mustache`.
Test: `test_base_api.mustache`.

### Errors and messages (D15, D16, D17, D18, D20, D21, D29, D14)
Rule: the serialize, deserialize and depth-cap messages are the shared three (the deserialize message has no ` to <type>`); redirect refusals use the shared text (`Too many redirects (exceeded N)`); the `Response headers:` line is `json.dumps(headers, sort_keys=True, separators=(',', ':'), ensure_ascii=False)`; a leading BOM is stripped inside `parse_json`, so the error-body and success paths agree; lists in messages render `[a, b, c]` (`'[' + ', '.join(x) + ']'`); the User-Agent and X-Request-ID checks are case-insensitive (`any(k.lower() == name.lower() ...)`); the use-after-close message is the shared one (`RuntimeError`); an `ApiError` for an unusable response carries the real headers; the empty-body message is `Expected a response body for <op> but received none`.
Why: one wording across twelve; the case-insensitive check follows RFC 9110.
Where: `object_serializer.mustache`, `default_api_client.mustache`, `errors.mustache`, `errors/*.mustache`, `server_configuration.mustache`, `api/api.mustache`.
Test: `test_api_error.mustache`, `test_object_serializer.mustache`, `test_default_api_client_unit.mustache`, `test_server_configuration.mustache`.

### Redirects and headers on the response (D71, D92)
Rule: an unparseable `Location` behaves like a missing one (`_resolve_url` is total, validated with `urllib3.util.parse_url`, and the loop stops and returns the 3xx); after a non-identity `Content-Encoding` was decoded the returned headers drop `Content-Encoding` and `Content-Length` (case-insensitive keys); an unsupported coding raises, an empty body passes through for any coding, and `br` and `zstd` raise when the codec package is absent.
Why: the returned headers must describe the body the caller receives.
Where: `default_api_client.mustache`.
Test: `test_default_api_client_unit.mustache` (`TestConnectionPoolSizing` covers the pool, : pools use `maxsize=10`).

### Batch 5 fixes
Rule: `close()` and the context manager on the client release only the pool the SDK built, a closed client raises `RuntimeError`, and a supplied `pool_manager` is never closed. HTTP 429 maps to `TooManyRequestsException`, a `ClientException`. The unexpected Content-Type error names `<Class>.<operation id>` through the `operation_name` argument the generated operation passes to the invoke helper. A `content:` cookie is `quote(json, safe='')`, which keeps exactly `A-Za-z0-9-._~` literal. Redirects follow RFC 9110 15.4: 301 and 302 turn only POST into GET, 303 turns every method except HEAD into GET, 307 and 308 keep method and body. F826 does not affect Python.
Where: `client.mustache`, `errors/too_many_requests_exception.mustache`, `base_api.mustache`, `value_serializer.mustache`, `default_api_client.mustache`.
Test: `test_client.mustache`, `test_api_error.mustache`, `test_base_api.mustache`, `test_value_serializer.mustache`, `test_default_api_client.mustache`.

### ApiResult headers (D27)
Rule: `headers` is typed `Mapping[str, str]` and `__post_init__` stores `MappingProxyType(dict(headers))` through `object.__setattr__`.
Why: a read-only copy so the result never aliases the transport's map.
Where: `api_result.mustache`.
Test: `test_api_result.mustache`.

### OAuth realisation (D01 to D08, D19)
Rule: the token manager owns the session (an `exchanged` flag, an optional `token_manager` argument on the auth-code authenticator) so OIDC rediscovery keeps it, with a `threading.Lock` and an expiry re-check around `_get_delegate`; a refresh sends client authentication (`refresh_url=None` keyword on `get_access_token`; the password authenticator no longer branches on a refresh token); `exchange_code` always POSTs the authorization_code grant under `self._lock` and restores state on failure; OIDC always requests `openid` (in `__init__`); an empty implicit-flow token raises `RuntimeError` (`if not self._access_token`); token errors say `Token response is not valid JSON` and use `raise ... from None`, discovery errors drop the parser detail but keep the cause; `expires_in` and `max-age` use `re.fullmatch(r'[0-9]+(\.[0-9]+)?', re.ASCII)` and clamp to one year.
Why: see the shared file; Python needs only `threading.Lock` and the standard library.
Where: `auth/oauth/oauth2_token_manager.mustache`, `oauth2_auth_code_authenticator.mustache`, `oauth2_password_authenticator.mustache`, `oauth2_implicit_authenticator.mustache`, `openid_connect_authenticator.mustache`.
Test: `test_oauth2_*.mustache`, `test_openid_connect_authenticator.mustache`.

### User-Agent token (D22)
Rule: the product token is the pyproject `projectName` (PyPI name), not `packageName`.
Why: users type the PyPI name into pip.
Where: `BetterPythonCodegen.java` (`processOpts`).
Test: `test_default_api_client_unit.mustache`.

### Python-only guardrails from the root file (G2, FD-datetime, FD-base64, FD-compression, FD-image)
Rule: (1) `models/model.mustache` imports `Self` from `typing_extensions` and `pyproject_toml.mustache` declares `typing-extensions>=4.7.1`; keep both or neither, because on 3.13 `ruff check --fix` strips an unused `Self` import and FawltyDeps then fails the client. (2) Date-time model fields are the `OffsetDateTime` alias (`Annotated[AwareDatetime, PlainSerializer(isoformat(timespec="milliseconds"), when_used="json")]`), set in `postProcessModelProperty`; `typeMapping` stays `AwareDatetime` for parameter metadata. (3) `brotli` and `zstandard` are the optional `[compression]` extra, also in the `dev` group. (4) The test image is pinned to `python:3.14-alpine` because the floating tag reached 3.15 where `zstandard` has no wheel. (5) Docstring deprecation uses `.. deprecated::` on every public variant including `_with_http_info`. (6) A model field doc carries `# Example:`. (7) `null` versus empty `response_headers`: `None` means no HTTP response arrived (pinned by `test_none_headers_and_body_mark_transport_no_response`); do not default to `{}`.
Why: each one reintroduced a real failure when "simplified".
Where: `models/model.mustache`, `pyproject_toml.mustache`, `BetterPythonCodegen.java`, `PythonSpec.java`, `errors.mustache`, `api/api.mustache`.
Test: `PythonLintingSpec`, `PythonClientSpec`, `test_api_error.mustache`.

### Per-model oneOf/anyOf no-match text (FD-oneof)
Rule: Python keeps its own no-match wording (`No match found when setting \`actual_instance\` in <X> with anyOf schemas: ...`) instead of the central helper string.
Why: it is built on a different code path (the model's own `RootModel`); unifying needs unrelated work.
Where: `models/model.mustache`.
Test: `test_composed_schema.mustache`.

### Mock seam (mocking001 to 009, issue #122 and the Invoker line of #131)
Rule: every API group has an interface and the client has one where the language can state it, additive next to the existing classes, using only SDK and standard-library types. `<Group>ApiProtocol(Protocol)` in the group file and `ClientProtocol(Protocol)` in the client file, both exported from `__init__`. `PetApi` is not changed: it satisfies the protocol structurally, and a fake subclasses the protocol so only the operations it fakes are written (the stubs return `None`). Tests: test/test_client.mustache (001, 002, 007, 008), test/api/test_pet_api.mustache (003 to 006). Where: the templates named above, `BetterPythonCodegen`, the README template (`Testing your code`), `docs/languages/python.md`.

## Known limitations and out of scope

Not decided or not done, each with its reason.

- The session decision S9 names httpx as the Python client object; the SDK uses urllib3 and the dependencies and the injection argument all say `pool_manager`. Decide whether the SDK keeps urllib3 (current code) or moves to httpx before editing; until then `Client` does not forward a transport (open) and only `DefaultApiClient` accepts one.
- Cancellation (S7): a call running in `asyncio.to_thread` cannot be interrupted mid-socket-read; the socket is shut down from the cancelling task (`test_default_api_client.mustache`, `TestCancellation`).
- uniqueItems order (S6): `_sanitize_for_serialization` sorts comparable sets, which conflicts with "do not sort"; the code, the README and the D69 line need to agree.
- Schema constraints (S3): `pattern` has no golden coverage until a petstore property exists (needs a spec).
- `format: password` is `PasswordStr`; F450 and F046 need a petstore `secret` property to test that the real value reaches the wire.
- `format: ipv4`, `ipv6` and `email` use validating pydantic types; whether to drop them is an owner decision, not made.
- Constructors accept the attribute name but mypy flags `Metadata(created_at=...)` because pydantic types `__init__` by alias; fixing it needs the pydantic mypy plugin or hand-written signatures for every model (F462 deferred).
- `PythonClientSpec` runs `pytest -v` with the Makefile mypy path limited to `petstore_client/` and `test/`, so hand-written `spec/` files are not covered by the spec (F541 open).
- The warnings-fatal docs build (`make docs`, `sphinx-build -W`) exists but no spec runs it (deferred, needs a `PythonDocsSpec`).
- The `Makefile` target is `typecheck`; renaming it alone would widen divergence, and a cross-SDK naming decision comes first.
- Retry behaviour, SOCKS proxies (rejected at construction) and ambient proxy variables (ignored, D74) are deliberate non-features; the generated `LICENSE` file is not emitted.
- The codegen class javadoc and the manifest floor are both Python 3.10.
- OpenAPI 3.1 and 3.2 features: epics #96 and #49; closed out of scope: #53, #70, #78, #88, #90.
- ruff runs its default ruleset with no extra `select` and mypy is non-strict, so a missing annotation is not flagged.

## Decisions taken in batch 6

The audit's own proposal was used as the default where no owner decision existed.

### OAuth token expiry
Rule: one safety margin, applied once when a token is stored: `min(60 s, expires_in / 2)`. A token whose response omits `expires_in` has no expiry (the previous token's instant is cleared on every store, not only on `exchange`). A zero or negative `expires_in` is stale at once. Token errors carry no parser detail: `Token response is not valid JSON`, `... is not a JSON object`, `... missing or empty access_token field` (D07).
Where: `auth/oauth/oauth2_token_manager.mustache`.
Test: `test_oauth2_token_manager.mustache` (`test_a_sixty_second_token_is_cached_for_half_its_lifetime`, `test_a_refresh_response_without_expires_in_clears_the_expiry`, `test_token_error_messages_are_the_canonical_text`).

### Transport headers, byte bodies and redirects
Rule: header names merge case-insensitively everywhere (`_merge_headers`, `_remove_header` in `default_api_client`, used by `base_api` too), so a lower-case default `accept` replaces the selector's `Accept`; `bytearray` and `memoryview` bodies are sent as `bytes`; the three redirect refusals use the D16 wording and carry lower-cased response headers (`_lower_headers`); a transport error message is built from the underlying cause, never from `MaxRetryError`, and a URL quoted in a redirect refusal has its query replaced with `***`, so a query-parameter API key never reaches a log.
Where: `default_api_client.mustache`, `base_api.mustache`.
Test: `test_default_api_client_unit.mustache` (`TestRedirectRefusals`, `TestHeaderMergeIsCaseInsensitive`, `TestRedirectMessagesKeepTheQueryOut`, `TestNetworkErrorMessageKeepsTheQueryOut`), `test_base_api.mustache`.

### Messages now matching the shared contract (D15, D17, D20, D29, D77)
Rule: `Failed to deserialize JSON: <detail>` for every decode failure (the `to <type>` and the `Failed to parse JSON` variants are gone); `Response headers:` is compact sorted JSON; the unsupported content-type error lists `[a, b, c]`; use-after-close says `ApiClient has been closed and can no longer send requests`; `timeout` must be `None` or at least 1 ms, checked in `__post_init__` and the builder with `Invalid timeout: must be a positive number of milliseconds (got N)` (this makes S16 true in the code, it was only written down).
Where: `object_serializer.mustache`, `errors.mustache`, `api/api.mustache`, `default_api_client.mustache`, `transport_options.mustache`.
Test: `test_object_serializer.mustache`, `test_api_error.mustache`, `test_pet_api.mustache`, `test_default_api_client_unit.mustache`, `test_transport_options.mustache`, `test_oauth2_auth_code_authenticator.mustache` (D09).

### README taxonomy
Rule: schema constraints, the custom transport and cancellation are supported features, filed under `## Caveats`; only genuinely unsupported things stay under `## Not supported`.
Where: `readme.mustache`.

### Not done in batch 6 (python)
- F541 needs a change to `PythonClientSpec.java` (spec Java, outside the batch).
- F829 (a synchronous client) and F405 (httpx) are the S9 transport decision and a design change; urllib3 stays.
- F215, F923, F834 (required plus nullable, S5): done in batch 10 (the optional tri-state, stays open).
- F929: python's `get_auth_headers` is the blocking path that really performs the token exchange (the async layer runs it in `asyncio.to_thread`), so the D12 "use the async method" message does not apply; the matrix row saying python raises is wrong and is left for the owner.

## Decisions taken in batch 7

The audit's own proposal was used as the default where no owner decision existed.

### Decoded bodies, proxy messages and the one-year ceiling (D92, D79, D08)
Rule: when the body was decoded (non-empty, `Content-Encoding` other than identity) the returned headers drop `content-encoding` and `content-length`; an empty or undecoded body keeps them. Proxy validation errors show userinfo as `***@` (`_proxy_in_message`); the S15 repr redaction is unchanged. `expires_in` and an OIDC `max-age` are clamped to 31 536 000 s before any arithmetic.
Where: `default_api_client.mustache`, `transport_options.mustache`, `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`.
Test: `test_default_api_client.mustache` (`TestHttpCompression`), `test_default_api_client_unit.mustache`, `test_transport_options.mustache`, `test_oauth2_token_manager.mustache`, `test_openid_connect_authenticator.mustache`.

### multipart/* bodies
Rule: every `multipart/*` content type builds the parts envelope; the transport writes `<declared type>; boundary=...` (only a token matching `multipart/[a-z0-9!#$&^_.+-]+` is taken over from the caller header, anything else falls back to `multipart/form-data`). The header selector passes a non-form-data multipart subtype through to the transport.
D165: a form-urlencoded field whose Encoding Object sets `style` or `explode` (`formStyled`) is written by the operation prelude through `serialize_styled(..., 'query', ...)` and `query_entries`, so a joined value is one field encoded with the form rules and an exploded list repeats the key.
D166: a multipart field whose Encoding Object declares a Content-Type or fixed part headers (`partEncoded`) is wrapped in `EncodedPart` (`value_serializer`, beside `AllowReservedValue`); `DefaultApiClient._apply_part_encoding` writes the declared Content-Type in place of the part's own and then each header, a list applies it per item, and the form-urlencoded encoder, the binary-part extraction and `_is_replayable` see the plain value.
Where: `base_api.mustache`, `header_selector.mustache`, `default_api_client.mustache`, `api/operation_prelude.mustache`, `value_serializer.mustache`.
Test: `test_base_api.mustache`, `test_header_selector.mustache`, `test_default_api_client_unit.mustache`.

### File header
Rule: the info description in the generated file header goes through `lambda.unescapeDocComment`, so `"quoted"` is not written as `\"quoted\"`.
Where: `api_info.mustache`. Test: `test_metadata.mustache`.

### Tests added for parity
StrictTag extra-key rejection, the `getPetTag` byte parameters on the wire, the required header and cookie guards, `getPetSummary` (matrix date, date-array query, cookie, text/plain), ambient proxy variables ignored, the auth cookie reaching the wire, the token request headers, an explicit per-call `Accept`, D84 characters written literally, the exact dot-segment message.

### Cross-origin redirects and the error tree
Rule: on a cross-origin hop the strip list is the fixed names plus the spec's api-key header names plus any header whose name the S15 rule calls a credential (contains `key`, `token` or `secret`), so a default header or custom authenticator header is not forwarded. SKILLS.md names the root `<prefix>Exception` and its siblings (`SerializationException`, `OAuth2ServerException`, `OAuth2TokenException`) and ends the example with a catch of the root.
Where: `default_api_client.mustache` (`_is_sensitive_header` from `transport_options`), `skills.mustache`. Test: `test_default_api_client.mustache` (`TestRedirectSecurityGuards`).

### Fixture rows now asserted
`test_store_api.mustache` (`TestStoreApiFixtureOperations`: createAccount, swatch, swatch groups, timestamp groups, department, tree, defaults, stock item), `test/api/test_pet_api.mustache` (PetCriteria styles, addPetTreatment, scalar multipart parts, Mirror server, matrix and label path, deepObject filter, `2ndPage`), `test_object_serializer.mustache` (`TestFixtureModelsOnTheWire`: uuid, Error and Result, hostile Availability members, phase, Separator, biometricChip, prefixItems location, keyword property names, duplicate key), `test_server_configuration.mustache` (hostile third server), `test_default_api_client.mustache` (redirect-chain budget, multi-value headers, Set-Cookie not replayed).
Not applicable to Python: the Pet.nickname and Order.quantity rows of F1124, because Python enforces schema constraints (S3).

### Object-valued parameters and text/plain strings
Rule: a model or mapping passed as a query, header, cookie or path parameter is written per style (`ValueSerializer._serialize_object`): exploded form in a query becomes one `name=value` entry per property (`ValueSerializer.query_entries` merges them into the query), unexploded form and simple are a comma list of name,value pairs, matrix, label, spaceDelimited and pipeDelimited follow the OAS table. A `text/*` response for a `StrictStr` operation is returned as the text (`return_type` is the pydantic type name, not `str`).
Where: `value_serializer.mustache`, `api/api.mustache`, `base_api.mustache`. Test: `test_value_serializer.mustache`, `test/api/test_pet_api.mustache`.

### Not done in batch 7 (python)
- F1080 (bounded, redacted ApiError rendering) changes the shared D14/D17 rendering of all twelve SDKs; it needs one cross-language decision first.
- F1058, F1060, F1055 need the shared codegen (`AbstractBetterCodegen`); F1100 is an API-shape decision.
- F1152 (compile the README and SKILLS fences) is a gate change, F1151 is already covered for multipart parts by `test_bhv_transport_061_multipart_body_leaves_a_caller_supplied_handle_open`.

### Earlier not-done list (kept for the record)
- F1056 (header parameters named Accept, Content-Type, Authorization) needs the shared codegen filter.

## Decisions taken in batch 9

### Retries and the 401 replay
Rule: `TransportOptions.max_retries` (default 2, `0` disables, validated by `_assert_valid_max_retries` in the constructor and the builder). The retry loop is SDK-level, in `BaseApi._send_with_retries`, not `urllib3.Retry`, so every retry rebuilds the headers (`build_headers`, authentication and trace context included) and the one end-to-end timeout is a `time.monotonic()` deadline handed to `DefaultApiClient.send_request(deadline=...)`. Retried: `NetworkException` that is not a `NetworkTimeoutException`, and statuses 408, 409, 429, 500-599 except 501. Wait: `Retry-After` (seconds or HTTP date, at most 60 s) else 0.5 s doubling to 8 s with up to 25% taken off; a wait that does not fit in the remaining budget ends the retries and the last outcome stands. A body that is not `None`, `str` or bytes-like (a stream, a multipart dict) is neither retried nor replayed. A custom `ApiClient` that is not a `DefaultApiClient` is never retried. A 401 on a call whose authenticator has `invalidate_access_token` (the OAuth2 and OIDC authenticators) drops the cached access token, rebuilds the headers and sends once more inside the same budget; a failed re-authentication or a second 401 leaves the usual `UnauthorizedException`. Token-endpoint and discovery calls go straight to `ApiClient.send_request` and are never retried.
Where: `transport_options.mustache`, `default_api_client.mustache` (`_is_retryable_status`, `_retry_delay`, `deadline`), `base_api.mustache`, the four OAuth authenticators.
Test: `test_base_api.mustache` (`TestAutomaticRetries`, `TestUnauthorizedReplay`), `test_default_api_client_unit.mustache` (`TestRetryPolicy`), `test_transport_options.mustache`, `test_oauth2_*_authenticator.mustache` and `test_openid_connect_authenticator.mustache` (`TestInvalidateAccessToken`). Tests that expect one failing request use `max_retries(0)` (the `api` fixture, `_create_mock_server`).

### PKCE on the authorization-code flow
Rule: `build_authorization_url` creates a 43-character verifier (`secrets.token_urlsafe(32)`), keeps it in the authenticator and adds `code_challenge` (base64url SHA-256, `hashlib`) and `code_challenge_method=S256` after `state`; `exchange_code` sends `code_verifier` and clears it after a successful exchange; an exchange without a built URL sends none. OIDC passes the verifier to the delegate it rebuilds on a rediscovery with new endpoints. D140: the OIDC `build_authorization_url` appends a fresh `nonce` (`secrets.token_urlsafe(32)`) last and keeps it in the OIDC authenticator; `exchange_code` consumes it and hands the response's `id_token` (through `OAuth2TokenManager.exchange(id_token_check=)` and the delegate's `_exchange_code`) to a check of nonce, discovered issuer, client id in `aud` and `exp` with 60 s skew, raising `OAuth2TokenException` and restoring the previous session on failure; no id_token, no check; the signature is not verified (base64url and `json` only).
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`, `auth/oauth/openid_connect_authenticator.mustache`. Test: `test_oauth2_auth_code_authenticator.mustache` (`TestPkce`), `test_openid_connect_authenticator.mustache` (`TestOidcPkceAndInvalidation`).

### ApiException rendering cap
Rule: `str(error)` renders the body cut at 1 KiB of UTF-8 with `... (N more bytes)`, control characters as `\n`, `\r`, `\t` or `\xNN`, and masks the values of `authorization`, `proxy-authorization`, `cookie`, `set-cookie`, `www-authenticate`, `proxy-authenticate` and any name containing `key`, `token` or `secret`; `response_body` and `response_headers` stay whole. Where: `errors.mustache`. Test: `test_api_error.mustache` (`TestApiErrorRendering`).

### Not applicable in batch 9 (python)
F825 is rust only.

## Decisions taken in batch 10

### Required and nullable properties (S5)
Rule: a required nullable property is `Optional[T]` with no default, so the key must be present on decode (missing is an error) and an explicit null is kept; a model that has one gets a `@model_serializer(mode='wrap')` that adds the key back as null, because the serializer runs with `exclude_none`. The flag is `vendorExtensions.hasRequiredNullable`, set in `postProcessModelProperty`; a model with `additionalProperties` folds the same lines into its flattening serializer (a class holds one).
Where: `models/model.mustache`, `BetterPythonCodegen.java`. Test: `test_object_serializer.mustache` (`TestRequiredNullableOnTheWire`).

### Auth
Rule: `BaseApi` hands its own `ApiClient` to a per-call authenticator that is an `HttpAwareAuthenticator`; OIDC discovery rejects a document whose `issuer` (trailing slash ignored) differs from the discovery URL minus `/.well-known/openid-configuration` (no issuer is accepted) and an `authorization_endpoint` or `token_endpoint` that is not https unless loopback (`SerializationException`); a token response whose `token_type` is not `bearer` in any case is `OAuth2TokenException('Unsupported token_type in token response')`, an absent one is accepted; a `no_redirect` request (the token POST) carries no `TransportOptions.default_headers`. Discovery GET still carries them (it must follow redirects, so it cannot use the flag); stopping that needs a flag on the public `ApiClient.send_request`, a cross-language decision not taken.
Where: `base_api.mustache`, `auth/oauth/openid_connect_authenticator.mustache`, `auth/oauth/oauth2_token_manager.mustache`, `default_api_client.mustache`, `api_client.mustache`.
Test: `test_base_api.mustache` (`TestPerCallAuthenticatorGetsTheClient`), `test_openid_connect_authenticator.mustache` (`TestDiscoveryDocumentIsChecked`), `test_oauth2_token_manager.mustache`, `test_default_api_client_unit.mustache` (`TestAuthRequestsOmitTheCallersDefaultHeaders`).

### Strings, messages and literals
Rule: an apiKey scheme name goes into the generated wrapper through `pythonStringBody` (quote, backslash and control characters escaped) and into the redirect strip list through `lambda.quotedLiteral`; an enum member renders as its wire value (`__str__`); a `datetime` passed for a `format: date` parameter sends only the date; the server-variable rejection reads `Allowed values: [a, b, c]`; an unreadable CA file is `failed to read CA certificate from "<path>": <cause>` and an unparseable one `failed to parse CA certificate from "<path>": <cause>`; server variables are substituted in one pass, so a value holding `{other}` is never expanded; an unterminated IPv6 literal in a proxy URL is `Invalid proxy URL (unterminated IPv6 literal): <url>`.
Where: `BetterPythonCodegen.java`, `default_api_client.mustache`, `models/model.mustache`, `value_serializer.mustache`, `server_configuration.mustache`, `transport_options.mustache`.
Test: `BetterPythonCodegenTest` (new), `test_object_serializer.mustache` (`TestEnumStringFormIsTheWireValue`), `test_value_serializer.mustache`, `test_server_configuration.mustache`, `test_default_api_client_unit.mustache`, `test_transport_options.mustache`.

### Declared variant order
Rule: `postProcessModels` puts `model.oneOf` and `model.anyOf` back in spec order (the parent keeps them sorted), so `one_of_schemas`, `any_of_schemas` and the union annotations follow declaration order and a payload that fits several variants resolves to the first declared one. Test: `test_composed_schema.mustache` (`TestAnyOfTreatmentRecord`).

### Docs and layout
Rule: the README opens with Installation and Usage (linking SKILLS.md), then Configuration (timeouts, proxy, request id, Custom HTTP client, retries, OAuth2, response compression, raw responses, cancellation), Package, type mapping, Caveats, Not supported (now also XML bodies, Link Objects, mutual TLS) and a final Development section; the SKILLS model sample passes the required fields and its Configuration has Custom Transport Options, Custom HTTP client, retries, timeouts, request id, compression and cancellation at `###`; an options class keeps the examples of its parameters as comments; the operation docstring (the main method and the stream-only method, not `_with_http_info`, through `api/operation_doc_examples.mustache`) ends with one `<heading> example: <summary>` line, the description and a `# >>> <value>` line per request-body and 2xx response example (D169), then one `Extension <name>: <value>` line per extension the `exposedExtensions` option names (D170), and a typed response-headers field doc adds `Example: >>> <value>`; `make lint` also runs `ruff format --check .`.
Where: `readme.mustache`, `skills.mustache`, `api/options.mustache`, `api/operation_doc_examples.mustache`, `api/response_headers.mustache`, `makefile.mustache`. Test: `test_metadata.mustache`, `PythonFormattingSpec` (`pluralExamplesShouldBeAttributedToParameter`, `optionalParameterExamplesShouldBeKept`).

### Tests only
`test_store_api.mustache` (lowercase uuid in the `getOrderByTrackingId` path), `test_bearer_authenticator.mustache` (`Bearer Bearer xyz` and `bearerxyz`), `test_object_serializer.mustache` (byte-array equality), `test_default_api_client_unit.mustache` (identity encoding passes through), `test_base_api.mustache` (a null body is not sent).

### Already conforming in python
F229 (pool `maxsize=10`, `default_api_client.mustache`, test `TestConnectionPoolSizing`), F823 and F855 (batch 9: `base_api.mustache` replay at the `invalidate_access_token` lookup), F858 PKCE (`oauth2_auth_code_authenticator.mustache:126`), F1080 (`errors.mustache:46`, `TestApiErrorRendering`), F1180 (`invalidate_access_token` is public on all four OAuth authenticators), F831 (429 is `TooManyRequestsException`; the `retryAfter` accessor is not decided).

### Not done in python, with the reason
- F912 (D137): a buffered body is read by `_read_within` with `read1`, the socket timeout reset to what remains of the deadline before every read, so a trickled body cannot outlast the budget; a response without `read1` (a supplied pool manager, the unit tests' fakes) is read whole. A streamed body is read with `read1` and bounded by the idle timeout instead (see "Streaming: binary responses").

## Decisions taken in wave 2

Owner decisions of 2026-11-17 realised in Python, with the triage of every item on the wave-2 work list.

### Implemented
- **F830 (request context on errors).** `ApiException` and every subclass take `request_method` and `request_path` (after `cause`, so no call breaks) and expose them as read-only properties; `from_response(status, headers, body, request_method, request_path)` records them. `BaseApi` passes the method and the URL path without its query string (`urlsplit(url).path`), and tags a `NetworkException` through `_with_request` before re-raising it. `str(error)` gets a `Request: METHOD /path` line between the message and the status line, omitted when the request is unknown. Wording proposed for the other eleven: `Request: <METHOD> <path>`. Where: `errors.mustache`, `errors/*.mustache`, `base_api.mustache`. Test: `test_api_error.mustache` (`TestRequestContext`), `test_base_api.mustache` (`TestErrorRequestContext`).
- **F827 (per-call options).** Every Options dataclass gains `timeout` (ms, at least 1), `headers` (read-only copy, replaces a same-named default header) and `max_retries` (at least 0), validated by `_validated_call_headers` in `transport_options.mustache`. `BetterPythonCodegen.mintsOptionsForEveryOperation()` returns `true`, so the shared pass mints an Options class (and the signature entry, the import and the barrel line) for every operation, including one with no query/header/form/cookie parameter and no security; that Options class has no `auth` field. The Python-local `addCallOptionsWhereMissing` and its barrel hook are gone. `BaseApi` takes `request_options` and applies the three settings to that call (`_send_with_policy`); a custom `ApiClient` gets the headers and the retry budget, and the timeout only bounds its retry waits, because it owns its own timeout. `timeout`, `headers`, `max_retries` and `options` are injected names: a parameter called like one gets the `_param` suffix. Test: `test_base_api.mustache` (`TestPerCallOptions`), `test_metadata.mustache` (`TestOptionsPerCallSettings`, `TestEveryOperationTakesOptions`), `BetterPythonCodegenTest.everyOperationGetsAnOptionsObject`.
- **F868 and F870 (immutable, hashable models, copy).** Every model, composed wrappers included, is `ConfigDict(frozen=True)` and defines `__hash__` through `hash_model` in `_types.mustache`, which freezes list, map and set fields (pydantic's own frozen hash fails on them). The copy is pydantic's `model_copy(update=...)`; it does not validate, which the README says. `validate_assignment` is gone (assignment is forbidden); the oneOf validators that used `Model().field = v` to test a variant now call `TypeAdapter(type).validate_python(v)`. Test: `test_object_serializer.mustache` (`TestModelsAreImmutable`).
- **F1062 (several 2xx responses).** `api/api.mustache` emits a local `decode_success(status_code, body)` for an operation with `hasMultiple2xx`: it decodes the body as the schema of that status (a `2XX` range last) and wraps it in the union model; an undeclared 2xx falls back to the normal union decode. `BaseApi` calls it in place of the generic decode and still checks the response Content-Type. Test: `test_base_api.mustache` (`TestMultipleSuccessResponses`, a body that fits both schemas).
- **F862 (credentials).** Every per-scheme authenticator (`scheme_authenticator.mustache`) takes each credential as an optional argument and resolves it with `resolve_credential` (`auth/_credentials.mustache`, private): the argument wins (an explicit empty string is kept and rejected by the base authenticator's own check), then the shared environment variable of `securitySchemeEnvVars` (`{PREFIX}_{FIELD}`, qualified by the scheme id when two schemes share a field; read once when the authenticator is built; an empty variable is unset), and a credential found in neither place raises `ValueError` naming the variable. A bearer scheme also takes `token_provider` (a callable run for every request): it wins over the environment variable, and `token` together with `token_provider` raises `ValueError`. `{PREFIX}` is the shared `envVarPrefix` (the option, else the API title without a trailing SDK); `processOpenAPI` copies it to `envPrefix`, so `Client.with_token(host=None, access_token=None, ..., token_provider=None)` reads `{PREFIX}_BASE_URL` / `{PREFIX}_ACCESS_TOKEN` with the same prefix (it used to be the upper-cased project name). The base authenticators take explicit values only. Test: `test_credentials.mustache` (precedence, empty, missing), `test_scheme_authenticators.mustache` (every scheme: environment alone, explicit, missing, empty, read once, provider), `test_client.mustache` (`TestClientCredentials`), `test_bearer_authenticator.mustache` (`TestBearerTokenProvider`), `BetterPythonCodegenTest.aSchemeAuthenticatorReadsItsSharedEnvironmentVariable`.
- **F866 (`$ref` with a sibling default).** Python already initialised it (`default_factory` on the property); `test_object_serializer.mustache` (`TestReferencedEnumDefault`) now pins construction and decode with `StockItem.availability`.
- **F894 (package metadata).** Options `repositoryUrl`, `homepageUrl`, `documentationUrl`, `issueTrackerUrl` and `authors` (comma-separated) are registered in `BetterPythonCodegen` and written to `[project.urls]`, `authors` and the README Package list when given; the licence already comes from `licenseId` (SPDX). The shared option plumbing is still open (see the shared needs in the wave report). Test: `BetterPythonCodegenTest`.
- **F1087 (runtime floor).** `requires-python = ">=3.10"`, README and `[tool.mypy] python_version = "3.10"`. The tests that read `pyproject.toml` use `tomllib` on 3.11+ and the `tomli` backport (a dev dependency) on 3.10. The suite was run on `python:3.10` and `python:3.14`.
- **F860 (OIDC discovery and default headers).** The discovery request is sent with `no_redirect=True`, which also leaves out the transport's default headers, so an API key or `Authorization` default header never reaches the identity provider; a redirecting discovery URL is now an error. Test: `test_openid_connect_authenticator.mustache`.
- **F1055 (injected argument names).** `getInjectedArgumentNames` lists `self`, `options`, `server`, `request_content_type`, `timeout`, `headers`, `max_retries`; the suffix is `_param`. Test: `BetterPythonCodegenTest`. The petstore fixture row stays open because it needs the shared spec.
- **F1154 (S8 test).** `BetterPythonCodegenTest.theServerComesBeforeTheOptionsInEverySignature` reads the golden.
- **F1124 (nickname row).** Python enforces `minLength`, `maxLength` and `pattern` (S3), so `nickname: "ZZZZZZZZZZ"` is rejected on decode instead of round-tripping as the fixture text says; the test pins that and that the models package imports with the look-around pattern.
- **F889, F828, F834, F835, F836, F837 (README notes).** The README sections "Credentials", "Per-call options", "Errors", "Operations with several success responses", "Models are immutable", "Logging and tracing", "Versioning and supported Python" and "Behaviours to know" (optional versus nullable, unknown values fail loudly, joined repeated headers, no streaming, `Configuration` versus `TransportOptions`). Test: `test_metadata.mustache`.

### Already satisfied
- **F215.** Python already rejects an absent required nullable key on decode, keeps an explicit null and writes the key on encode (`TestRequiredNullableOnTheWire`); the `setdefault` the finding quotes is the encode side (S5), not a missed rejection.
- **F390.** `LaxFloat` is `Field(allow_inf_nan=False)`, so `1e400` and `1e999` are rejected on decode (`test_deserialize_double_field_overflowing_to_infinity_raises`).

- **F878.** A oneOf wrapper is built from a variant (`PetFood(DryFood(...))`, one positional argument), its validator rejects anything that is not a declared variant, and the variant is read with `isinstance` on `actual_instance`; per-variant constructors and accessors would only repeat that. F877, F879 (go), F1178 (java) and F1082 (node) do not apply to Python.


## Decisions taken in round 4

Owner decisions of 2026-11-17, realised in Python's idiom.

- **F857 (one credential per security scheme).** `Client.__init__(authenticator=None, transport_options=None, pool_manager=None, *, <scheme>=None, ...)` takes one keyword-only `Optional[Authenticator]` per distinct scheme, named after the scheme id in snake case. The names come from the shared `clientSchemes` data, which `BetterPythonCodegen.pythonClientSchemes` renames (`schemeParamName`: snake case, non-identifier characters become `_`, a leading digit gets `scheme_`, a Python keyword or `self`, `authenticator`, `transport_options`, `pool_manager` gets a trailing `_`, two ids that read the same stay apart with more underscores) and gives a `schemeLiteral` for the double-quoted dictionary key. The client builds `{schemeId: credential}`, gives every credential that is `HttpAwareAuthenticator` the client's own transport, takes the base URL from the first credential (`authenticator` first) and raises `ValueError` when it gets none. Each API group takes `scheme_authenticators`; an operation passes `self._scheme_authenticator("a", "b")` with the ids of its own `vendorExtensions.op.securitySchemeOrder`, which returns the credential of the first scheme the client holds or `None`, and `None` falls back to the client-level `authenticator` (and sends no credential when there is none). An operation with `securityNone` passes `NO_AUTH`. Options classes carry no `auth` field (`generateOptionsFileContent` no longer sets `hasAuthField` or the authenticator import). `NO_AUTH` and the three-state `auth` argument of `_invoke_api_for_result` stay, as the internal carrier of the resolved credential. Where: `client.mustache`, `base_api.mustache`, `api/api.mustache`, `api/options.mustache`, `BetterPythonCodegen`. Test: `test_client.mustache` (`TestClientSchemeCredentials`), `test_base_api.mustache` (`TestSchemeCredentials`), `test_pet_api.mustache` (the scheme credential reaches the wire, no Options class has `auth`), `BetterPythonCodegenTest`.
- **F840 (cookie parameters).** Already as decided: the value is sent raw when every character is an RFC 6265 cookie-octet, otherwise `ValueError` before anything is sent. The message now says what to do: `Cookie value for 'x' contains characters outside the RFC 6265 cookie-octet set; percent-encode the value before passing it` (same wording as csharp), for operation cookie parameters and for the cookies an authenticator returns. Test: `test_base_api.mustache` (`TestOperationCookieParam`: raw octets sent, space, comma, semicolon, quote, backslash and non-ASCII refused, nothing sent).
- **F1078 (graceful close).** `DefaultApiClient.close()` marks the client closed under a lock and releases the pool the SDK created (`PoolManager.clear()`, only when it owns it) when the in-flight count reaches zero, at once when it is zero, once. It cancels nothing. A call is counted in flight by `_in_flight()`, a context manager that raises `RuntimeError("ApiClient has been closed and can no longer send requests")` on a closed client; `BaseApi._invoke_api_for_result` holds that scope for the whole operation through the `_tracked` decorator, and a `ContextVar` (`_ADMITTED`, it follows `asyncio.to_thread` into the worker thread) marks calls already counted, so the retries of a call and its token requests may start after `close()` and finish. `send_request` called on its own counts itself. A caller-supplied `pool_manager` is never cleared. `RequestHandle.cancel()` and cancelling the awaiting task still abort one request. Where: `default_api_client.mustache`, `base_api.mustache`, `client.mustache`, `readme.mustache`, `skills.mustache`. Test: `test_client.mustache` (`TestClientClose`: in-flight finishes then the transport is released, last of several, nothing in flight releases at once, second close is a no-op, closed client rejects with the canonical message, caller-supplied untouched), `test_base_api.mustache` (`TestCloseFinishesCallsInFlight`: a retry after `close()` still finishes), `test_default_api_client.mustache`.
- **F316 (no validation at construction).** Every generated model extends `ApiModel` (`_types.mustache`) instead of `BaseModel`. Its `__init__` first requires the required fields (a missing one raises `TypeError`), then tries the normal pydantic validation so a valid value is still coerced (a list for a set field), and when that fails keeps every value exactly as given through `model_construct` (the extras of an `additionalProperties` model still go to `additional_properties`). So construction never raises on a wrong type, an unknown enum string or a constraint violation. Decoding is unchanged: `ObjectSerializer` calls `model_validate`, which must not reach the lenient `__init__`, so the `__init__` of `ApiModel` and of the oneOf/anyOf wrappers carries `@lenient_init`, which sets the `__pydantic_base_init__` marker pydantic puts on its own `BaseModel.__init__` (without it pydantic would route `model_validate` through the custom `__init__`). The composed decode builds the wrapper with `klass.model_validate({'actual_instance': ...})`, the validating entry. The serializer calls `model_dump_json(..., warnings=False)` so a kept value goes out as it is. Where: `_types.mustache`, `models/model.mustache`, `object_serializer.mustache`. Test: `test_object_serializer.mustache` (`TestConstructionKeepsWhatItIsGiven`, `TestDecodeValidates`), `test_composed_schema.mustache`. The marker is a pydantic internal; the decode tests fail first if a pydantic release stops honouring it.
- **F878.** The typed-union accessors other languages landed are not realised in Python (see "Already satisfied"): a validated wrapper that takes the variant is the Python shape for `reservestockresponse`, `petfood`, `pettreatment` and `treatmentrecord`.

## Decisions taken for the call hook and telemetry (#158, #108)

- **Call hook.** `call_hook.mustache` emits `CallHook` and `CallObserver` (runtime-checkable `Protocol`s), the frozen dataclasses `CallInfo` and `CallOutcome` and `chain(*hooks)`, all exported from the package. `CallOutcome` lower-cases its header names and keeps them read-only; `headers` is `None`, never empty, when no response arrived. The hook is the `call_hook` field of `Configuration` (a builder method too) and the `call_hook` argument of `Client`, `Client.with_token` and `Client.with_authenticator`; `call_hook` joins the reserved names a scheme argument may not use (`CLIENT_ARGUMENT_NAMES`). It is left out of the `Configuration` repr. Where: `call_hook.mustache`, `configuration.mustache`, `client.mustache`, `__init__.mustache`.
- **Where it runs.** `BaseApi._invoke_api_for_result` calls `start` right after the first `build_headers()` (which ends with `inject_trace_context`) and wraps everything after it in `try`/`except BaseException`, so `finish` runs once on every exit path (success, `ApiException`, network error, timeout, `asyncio.CancelledError`, an unencodable body, an undecodable response) through `_finish_call`, which swallows an `Exception` from the observer. The status and headers come from the response the call received, so a decode error carries the status that arrived. An error from `start` propagates before anything is sent. The call, not the send, is the unit: retries and the 401 replay stay inside it, and it sits above the transport, so a supplied `pool_manager` or `ApiClient` still fires it.
- **Header delta.** The headers are rebuilt for every attempt (`build_headers`), so the edits `start` made (headers set, names removed, compared with a copy taken before the call) are stored and applied again after each rebuild. On the replay after a 401 (`build_headers(True)`) a hook header whose name equals a fresh authentication header is not reapplied, so the fresh authentication wins; the first send keeps the hook edit.
- **Operation id and template.** Each operation passes `operation_id` (`operationIdOriginal`, the spec id, falling back to `operationId`) and `url_template` (`path`) through `lambda.quotedLiteral`; Python has no `lambda.stringLiteral`, `quotedLiteral` is the same escaping that writes the quotes. `operation_name` (the snake-cased name) stays for error text only. The hook URL is the request URL without query, fragment and userinfo, and the server address is `urlsplit(...).hostname` (lower-case, no port, no brackets).
- **Adapter.** `opentelemetry_call_hook.mustache` emits `OpenTelemetryCallHook(tracer_provider=None, meter_provider=None)`: span named with the operation id, kind client, `http.request.method`, `url.template`, `server.address`, `http.response.status_code` and `error.type` (`timeout`, `network_error`, `serialization_error`, `cancelled`, `_OTHER`, or the status for 400 and up), counter `http.client.request.count` (`{request}`), histogram `http.client.request.duration` (`s`, monotonic clock), scope `projectName` and `packageVersion`. It injects `traceparent` with `TraceContextTextMapPropagator` from the span it started, so the client span is the parent on the wire; the package `__init__` and `base_api` never import it, and the optional extra is `opentelemetry = ["opentelemetry-api"]`. `BetterPythonCodegen.hasOpenTelemetryAdapter()` is `true`. The existing `trace_context_util` stays (propagation only, FD-19).
- **Tests.** `test_base_api.mustache` (`TestCallHook`, hooks001 to hooks019 and hooks021), `test_call_hook.mustache` (hooks020 and `CallOutcome`), `test_opentelemetry_call_hook.mustache` (telemetry001 to telemetry014, on the in-memory span exporter and metric reader of `opentelemetry-sdk`, already a dev dependency), `test_configuration.mustache` and `test_client.mustache` (wiring). The two new test files are spec-independent and ship with `generateUnitTests`; the adapter test needs `opentelemetry-sdk`, which `DEV-DEPENDENCIES` already lists.

## Build-breaking rules

- No generated file may carry `# ruff: noqa` or any blanket suppression; the only per-file ignores are `E402` for `models/*.py` and `errors/__init__.py`, named with a reason in `pyproject_toml.mustache`.
- Comments in the generated Python must not contain HTML entities; `PythonFormattingSpec` rejects numeric entities outside `test/` (the one allowed is the decimal 39 reference in a test).
- `ruff format --check .` must pass; the generator runs `ruff check --select F401,F811 --fix --exit-zero --quiet .` then `ruff format .` in a pinned `python:3-slim` image, so over-wide import blocks are pruned, not hand-trimmed.
- `mypy petstore_client/ test/` must pass; `brotli` is the single `ignore_missing_imports` override.
- A mustache `{{! ... }}` comment must never contain a literal `{{tag}}`.
- Keep `typing_extensions` import and the pyproject dependency together.
- Do not return the test image to a floating tag; it is `python:3.14-alpine` in `PythonSpec`.
- Goldens are regenerated, never hand-edited; the Docker formatter step needs the network, so commit all twelve goldens current (see the shared file).

## Verify

```bash
etc/ci-local.sh check Python
etc/ci-local.sh mvn -B test -Dtest='GeneratePythonClientTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false
etc/ci-local.sh check --quick
```

The first command runs the Python specs end to end (client tests, ruff format, ruff check, mypy, reserved words).

## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs; Java unit tests and a green golden diff prove nothing about it. Run `etc/ci-local.sh check Python` (client, build, ruff format, ruff check, mypy, static analysis) before every push, never only `PythonClientSpec`. CI takes about 25 minutes, so push in batches.
- The HTTP stack is urllib3. Fake responses in tests must be `urllib3.HTTPHeaderDict` (import it from `urllib3._collections`), never a plain dict, or lookups become case-sensitive. Example: two redirect tests ended in `DID NOT RAISE ApiException`. Catch it with `etc/ci-local.sh check Python`.
- mypy runs strict. Do not call untyped helpers (`urllib3.util.make_headers` fails `no-untyped-call`; build the Basic header with base64 by hand). Narrow unions before use: `str | list[str] | None` needs `isinstance` before `.startswith`. Every test helper needs a return annotation. Catch it with `mypy --strict` on the generated tree (PythonTypeCheckSpec).
- Drop a `# type: ignore[...]` once it is unneeded (`warn_unused_ignores`), and type optional-import fallbacks as `Optional[ModuleType]` instead of `otel = None`.
- Ruff: remove unused locals and imports after every template edit (F841 in the `model.mustache` oneOf validator, F401 for `quote_plus` or `Self`, an unused dependency such as `typing-extensions` fails Lint Dependencies). Enable the full rule set once (`ruff check --select ALL --statistics` on the golden) and fix in one pass.
- Never loosen the gate: no project-wide `ignore = [...]`, no `# ruff: noqa`, no `mypy: ignore-errors`. Use a per-line `# noqa` or a scoped `[[tool.mypy.overrides]]` for missing stubs.
- Pydantic v2: do not set `strict=True` globally (it rejects list to Set and str to Enum coercions). `model.mustache` wraps `model_serializer(mode='wrap')`, so a discriminator or oneOf change needs `model_rebuild` handling and a re-run of the model round-trip tests. Keep oneOf variants in declared order and let the `bytes` branch reject list/tuple/dict so `List[bytes]` still decodes.
- Never turn a literal into an unescaped one: a global quote unescape broke `Field(description="...")`. Apply doc-text changes only to comment sites.
- Imports must go into the template that is actually rendered (`api/api.mustache` is registered; the top-level `api.mustache` is only a `{{>api}}` partial), and the import list is rewritten by `super.postProcessOperationsWithModels`, so add imports after the super call. `Optional[...]` in options modules needs `from __future__ import annotations`; `py_compile` misses the NameError, so run `python -c "import <pkg>"` on the golden.
- The facade and API classes are async-capable: tests that call them must `await` or use the sync wrapper consistently, and never block inside a running loop. Catch it with the client specs.
- Chasm: the test must use the host-mapped URL for the client under test and the internal alias (`chasm_internal_http_url`, host `chasm`) only for the proxy container; a certificate names `chasm` and `localhost`, so a new host needs a SAN. Chasm lower-cases header keys and returns empty bodies in static mode, so do not assert absent headers (urllib3 sends a default User-Agent) or case-sensitive keys.
- Never use a 1 ms total timeout in tests; use 1000 ms, or it expires during connect under load and raises a network error.
- Output JSON is compact (no spaces), matching the other SDKs; the Makefile `format` target must keep the `ruff check --select F401,F811 --fix` step before `ruff format`.
- Run the whole existing Python suite after any change, not just the new test: a non-ASCII multipart field name broke `TestMultipartFieldNameUtf8`. Check `docker ps -q | wc -l` before a run; above 20 leaked chasm or squid containers cause exit 137 and `409 not running`.

- Trace context from the caller (#136): `inject` reads the `ContextVar` context of the awaiting task (`use_span` or `context.attach`); headers are built in the coroutine, not offloaded (D95).

- **F15 (typed `additionalProperties`, #103).** The bucket is `Dict[str, V]` (`Optional[V]` when nullable); pydantic validates the dict on decode, so the strict scalar names reject a wrong type and a null.

## Streaming: binary responses (capability 1 of #114, D118 to D122)

### The transport returns an unread body
Rule: `StreamingApiClient.send_streaming(method, url, headers, body, idle_timeout)` (a second `runtime_checkable` Protocol in `api_client.py`) returns an `ApiStreamResponse` (status code, lower-cased headers, the open `StreamBody` with blocking `read(size)` and `close()`; its `repr` shows the status only). `DefaultApiClient.send_streaming` also takes the `handle` and `deadline` of `send_request`. `BaseApi` checks `isinstance(client, StreamingApiClient)` before anything is built or sent and raises `NotImplementedError` naming the transport, so a buffered-only transport keeps working for every buffered call and a stream call never falls back to buffering.
Why: the stream call must fail clearly (D118); a structural Protocol has no default method to inherit, so the check is the default.
Where: `api_client.mustache`, `api_stream_response.mustache`, `base_api.mustache` (`_invoke_api_for_stream` runs the one `_invoke_api_for_result` with `stream=True`, so the hook, headers, authentication and 401 replay are the buffered code), `default_api_client.mustache`.
Test: `test_default_api_client_unit.mustache` (`TestByteStreamTransport`), `test_base_api.mustache` (`TestBinaryStreams`: 008, 012, 014, 015, 017, 018, 020, 030), `test_client.mustache` (021), golden `test_pet_api` (009); the raw-socket `RawServer` fixture is in `test/conftest.py`.

### One header phase for both paths; retries stay in BaseApi
Rule: `DefaultApiClient._open` merges the headers, encodes the body and runs the redirect loop under the call budget, and returns the urllib3 response with the body unread; `_send` (buffered) and `send_streaming` both start from it. The retries, the `Retry-After` wait and the 401 replay are in `BaseApi._send_under_policy` and `_send_with_retries`, parametrised by the send function, so a stream is retried exactly as a buffered call until the headers and a refused response is closed unread; nothing is sent again after the first body byte because a returned stream is never sent again. A non-2xx status reads at most 64 KiB (`_read_bounded`), closes the stream and raises the typed error of a buffered call.
Where: `default_api_client.mustache`, `base_api.mustache`.

### The body: idle timeout, truncation, decoding, cancellation
Rule: `_StreamBody` reads with `HTTPResponse.read1` (returns as soon as bytes arrive; decodes gzip, deflate, and br or zstd when installed; enforces `Content-Length`). Once the headers are in, the socket timeout is set to `TransportOptions.idle_timeout` (default 60000 ms, `0` or `None` disables), so a stall raises `NetworkTimeoutException`; a reset, a decode failure or a body shorter than `Content-Length` is `StreamException` (a `NetworkException`, status 0). The `RequestHandle` keeps the socket itself (`attach_socket`), because a response that ends with its connection (`Connection: close`) leaves `HTTPConnection.sock` empty. `close()` shuts the socket down and closes the response without draining; once the body has ended nothing is touched, because its connection is back in the pool. Cancelling the task awaiting a read closes the stream. `urllib3>=2.2.0` is the floor for `read1`.
Where: `default_api_client.mustache`, `byte_stream.mustache`, `transport_options.mustache`, `errors/stream_exception.mustache`.
Test: `TestByteStreamTransport` (010, 011, 013, 016, 019, 022, 029, 031, 032, 033, 001), `test_transport_options.mustache`.

### Closing the client cancels open streams
Rule: `DefaultApiClient` tracks each open `_StreamBody`; `close()` marks the client closed, calls `cancel()` on each (the next read raises `RuntimeError` with the closed-client message) and releases the pool as before. An open stream is not counted as an in-flight call: only the header phase is.
Where: `default_api_client.mustache`, `client.mustache`.
Test: `test_client.mustache` (021).

### Binary operations return `ByteStream`
Rule: `BetterPythonCodegen.markBinaryStream` (run after the shared pass that fills the decorators) sets `streamsBinary` on `vendorExtensions.op` and rewrites `returnType` to `ByteStream` for an operation whose `streamKind` is `binary`, so every template says `ByteStream` and `api/api.mustache` calls `_invoke_api_for_stream` (no `return_type`, no `decode_success`); the `ByteStream` import is added to the API file. `postProcessSupportingFileData` hands the README and SKILLS the first such operation (`binaryStreamOperation`, `binaryStreamGroup`, `binaryStreamExample` with the imports, typed parameters and arguments) for the Streaming fence, which is a `file` fence a mypy gate checks.
Where: `BetterPythonCodegen`, `api/api.mustache`, `readme.mustache`, `skills.mustache`.
Test: golden `test_pet_api` (the existing avatar, document and photo rows close their stream), `TestByteStreamTransport` (029).

## Streaming: JSON Lines and server-sent events (capability 2 of #114, D120 to D122, D125)

### The stream method and the types
Rule: an operation with `streamOnly` (decorators `streamsSse` or `streamsJsonLines`, set by `BetterPythonCodegen.markBinaryStream`, which also sets `streamTyped`, `streamItemPython`, `streamMethodPython` and a `SseStream[T]` or `JsonLinesStream[T]` return type) has no buffered method and no `_with_http_info` variant in the class or the `Protocol`; `api/api.mustache` emits `<op>_stream(...signatureArgs, stream_options=None)`, with the path, query, header and options code shared with `_with_http_info` through the `api/operation_prelude` partial and the argument list through `api/operation_args`. `StreamOptions` is a frozen dataclass; `ServerSentEvent[T]` a frozen dataclass with `raw`; both streams are async iterators and async context managers with an idempotent sync `close()`.
Where: `event_stream.mustache`, `server_sent_event.mustache`, `stream_options.mustache`, `json_lines_stream.mustache`, `sse_stream.mustache`, `base_api.mustache` (`_invoke_api_for_json_lines`, `_invoke_api_for_sse`), `api/*.mustache`, `BetterPythonCodegen`.
Test: `test_event_stream.mustache` (the one mirror file: `streaming002` to `005` and the `sse` parser and engine rows), `test_default_api_client_unit.mustache` (`TestEventStreamTransport`: `streaming006`, `007`, `sse007`, `015`, `018` over the raw server), `test_base_api.mustache` (`TestEventStreams`: `streaming028`, `sse006`, `012`, `013`, `020`), `test_client.mustache` (`TestMocking` lists `<op>_stream` for a stream-only operation).

### The engine
Rule: `SseStream.open` awaits the first request (its failure is raised from the operation method) and checks the answer; each reconnect is `opener(last_event_id)`, which `BaseApi` implements as a fresh `_invoke_api_for_stream` (so the headers and the credential are built again and the call hook runs again) with `Last-Event-ID` added only when the id is not empty. A clean end counts as a failure against `max_reconnects` (`StreamException` when exhausted; `0` ends quietly). The wait is the server `retry`, else `Retry-After` of a 429, 502, 503 or 504, else `reconnect_delay` doubling to 30 s with up to 25% jitter, computed by the pure `reconnect_wait_millis` that the tests call without sleeping. `ByteStream` closed by the caller reads as `ValueError`, which ends the stream quietly; the closed-client `RuntimeError` is never retried. The idle timeout of `StreamOptions` is passed to `send_streaming`, so a stall is a `NetworkTimeoutException` and reconnects. A `DecodeError` of a compressed body is an `ApiException` with the real status, never a `StreamException`.
Where: `sse_stream.mustache`, `base_api.mustache`, `default_api_client.mustache`.
Test: `test_event_stream.mustache`, `TestEventStreams`.

## Streaming: uploads (capability 3 of #114, D121)

### BodySource
Rule: `BetterPythonCodegen.markBinaryUpload` (run before the shared pass, which copies the body type into `signatureArgs`) types a raw binary request body (`bodyParam.isBinary`, not multipart, no form parameters) as `BodySource` in the operation, `_with_http_info` and the `Protocol`, and adds `from <pkg>.body_source import BodySource` to the api file; every other body kind keeps its type. `BodySource` is replayable (`of`, `of_file`, `from_factory`) or single use (`once`); a known length is a `Content-Length` and an unknown one is chunked; `open()` returns an `OpenedBody`, an iterator of `bytes` with `close()`, which closes what the SDK opened (a file, a factory's stream) and never a stream passed to `once`. A single-use source opens once; a second `open()` raises `RuntimeError`. `repr` shows the kind and the length, never the content or the path.
Why: a large upload must not have to fit in memory, and a retry must know whether there is anything left to send.
Where: `body_source.mustache`, `api/api.mustache` (through the type), `BetterPythonCodegen`, `__init__.mustache`.
Test: `TestUploadTransport` in `test_default_api_client_unit.mustache` (`streaming023`, `024`, `025`, `027`), `test_pet_api.mustache` (the avatar rows send a `BodySource`).

### Sending, retrying and redirecting
Rule: `DefaultApiClient._open` sets `Content-Length` from `BodySource.length` (and removes the caller's framing headers), hands urllib3 an iterator of `bytes` (`BodySource._wire`: in-memory and empty bodies go as `bytes`; the iterator is checked against the declared length), opens the source again for every redirect hop that keeps the body, closes everything it opened when the send ends, and refuses a 307 or 308 (any hop that keeps the method) for a single-use source with `ApiException` `Refusing to replay a streamed request body`. `BaseApi` treats a replayable source like bytes (retries, 401 replay) and a single-use one like a stream (never retried, the original error is raised, no 401 replay), binds the running loop to a source before sending so a worker thread can read an async iterable, and passes `single_use_body` to `SseStream.open`, which then never reconnects.
Where: `default_api_client.mustache` (`_open`, `_wire_body`, `_follow_redirects`), `base_api.mustache` (`_is_replayable`, `_send_under_policy`, `_invoke_api_for_sse`), `sse_stream.mustache`.
Test: `streaming026` and `retries045` in `test_base_api.mustache` (`TestAutomaticRetries`, `TestUnauthorizedReplay`), `sse016` in `test_event_stream.mustache`. The `retries045` exception line is gone: Python now has a streamed request body.

## Pagination (#163, D123, D124)

### The detector is shared, the Python templates only render it
Rule: `AbstractBetterCodegen.populatePaginationDecorators` computes `vendorExtensions.op.pagination` (`isToken`, `isId`, `isOffset`, `isPage`, `isLink`, `isBody`, `isUrlPaged`, `requestParamName`, `cursorType`, `responsePropertyName`, `itemsPropertyName`, `itemsFromBody`, `itemType`, `itemModel`) and `hasPagesMethod`/`pagesMethodName`; the property names are the pydantic attribute names, so the templates read `page.<name>`. `BetterPythonCodegen.addPagedImports` adds `Paginator`, the item model (the operation's own imports cover the page model only) and, for a list whose request carries a cursor (token, id, offset or page), `dataclasses.replace as _replace_options` to the api file, and `postProcessSupportingFileData` hands the first paged operation to the README and SKILLS as `pagedOperation`, `pagedGroup` and `pagedExample` (a compiling `python file` fence).
Where: `AbstractBetterCodegen`, `BetterPythonCodegen`, `api/api.mustache`, `api/operation_pages.mustache`, `api/operation_pages_protocol.mustache`.
Test: `OperationDecoratorTest`.

### `Paginator[T, C]`, `Page[T, C]`, `PaginationException`
Rule: the `<op>_pages` method is a plain `def` that returns a `Paginator`; nothing is awaited until the first read. `Paginator` (`paginator.mustache`) keeps a factory of page iterators: `__aiter__` and `items()` wrap a fresh one in an item iterator and `pages()` returns a fresh one, so nothing is read before the first `__anext__`, one request is made per page and there is no read-ahead, and a second loop starts again. The page iterators are classes (`_CursorPages`, `_LinkPages`), not async generators, so an abandoned loop leaves no suspended generator behind. An end is an absent, `None` or empty cursor, or no `rel="next"`. An id list ends when its `has_more` is false; an offset or page list ends on an empty page or when its `has_more` is false, and the next offset is the offset sent plus the items received, the next page the page sent plus one. A next URL in the body ends when it is absent, `None` or empty. A cursor equal to the one just sent (or a `Link` or next URL to the page just read, or a foreign origin) is held back: the page it arrived with is delivered and the next read raises `PaginationException`. Any failure of a fetch, `CancelledError` included, ends the iteration. `Page` is a frozen dataclass (`items` copied, `next_cursor`, `result`). `PaginationException` (`errors/pagination_exception.mustache`) extends the root exception directly and carries `request_path` without the query.
Where: `paginator.mustache`, `page.mustache`, `link_header.mustache`, `errors/pagination_exception.mustache`.
Test: `TestPagedLists` in `test_base_api.mustache` (`pagination001` to `032`) over the raw server of `conftest.py`.

### Each page is an ordinary call; a `Link` page replaces the path
Rule: a token page calls `<op>_with_http_info` with `dataclasses.replace(page_options, <cursor>=token)`, so retries, timeouts, redirects, authentication, redaction and per-call options apply to every page. A `Link` or next URL (`isUrlPaged`) operation's `_with_http_info` delegates to a private `_<op>_page(...args, page_url=None)` whose body is the buffered call, and the next URL of a body list is read from the page's `next_url` property (a `UrlStr`, a `str` at run time, so it needs no conversion); when `page_url` is set it replaces `path` and clears the query, and `_invoke_api_for_result` joins an authentication query to a URL that already has one with `&`. The origin of a next URL is checked against `config.base_url`; an operation with a server of its own is not paged.
Where: `api/api.mustache`, `base_api.mustache`, `paginator.mustache`.
Test: `TestPagedLists` (`pagination004`, `005`, `006`, `007`, `008`, `023`, `027`, `029`).

## Compiled examples (#111)

- `examples/quickstart.py` (`templates/python/examples/quickstart.mustache`, registered by `BetterPythonCodegen.getSupportingFileSpecs` when `generateExamples` is on) wraps the README Usage statements in `async def main()` and runs it with `asyncio.run(main())`. The statements live once in `usage_statements.mustache`, included by the README Usage fence, the SKILLS Quick Start fence and the quickstart (`langpython212`); the client is closed by the `with` block. With no example operation the `with` body is `pass`.
- `PythonExamplesSpec` writes every fence to `.out/fences/fence_<file>_<nn>.py` and runs one `mypy .out/fences/` from the SDK root with the SDK's own mypy config. A `file` fence is the module as written; a `statements` fence has its `import` and `from` lines (a parenthesised import with its continuation lines) hoisted and the rest indented into `async def fence() -> None:`, so `await` is legal in it. Compile means type-check here: a fence is never imported or run. mypy checks only annotated functions, so every fence function carries annotations.
- A fence that calls an operation is a function taking the client and the operation's path, body and options parameters as typed arguments (`add_pet_or_report(client: Client, pet: Pet, options: Optional[AddPetOptions] = None)`), never a model built with `model_construct()` (the old `skills_example_arg.mustache` is gone). A scheme authenticator takes the host as its first argument. Behaviours `langpython210` to `langpython215` are in `test/test_metadata.mustache` (`TestReadmeAndSkillsText`).
- `make examples` runs `mypy examples/` and `python -m py_compile examples/*.py`; `make examples-run` maps `API_BASE_URL` to `{PREFIX}_BASE_URL` and passes a placeholder `{PREFIX}_ACCESS_TOKEN`.

## Response body limit (D138, issue #112)

Rule: `TransportOptions.max_response_bytes` (builder `max_response_bytes(n)`, default `None`) is `None` or a positive `int`, checked in `__post_init__` and the builder by `_assert_valid_max_response_bytes` with `Invalid maxResponseBytes: must be a positive number of bytes (got N)`; it is in the `repr`. `DefaultApiClient._read_within` reads at most `limit + 1` raw bytes through `read1` and `_decompress_body` decodes through `_decompress_capped` (zlib `max_length` for gzip and deflate, brotli's `output_buffer_limit` or 256-byte input pieces, zstd in 256-byte input pieces); passing the limit aborts the response and raises `ResponseTooLargeException` (`errors/response_too_large_exception.mustache`, a direct `ApiException` with the real status, lower-cased headers, an empty body, `limit`, and `is_retryable` always `False`). A drained redirect body is bounded the same way. Streamed calls are not bounded.
Where: `transport_options.mustache`, `default_api_client.mustache`, `errors/response_too_large_exception.mustache`, `errors.mustache`, `__init__.mustache`, `BetterPythonCodegen`.
Test: `test_transport_options.mustache` (`options069`), `test_default_api_client_unit.mustache` (`transport095`, `transport096`), `test_api_error.mustache` (`ERROR_TREE`).

## Response media type and typed response headers (D167, D168)

- D167: a per-call `Accept` in `<Operation>Options.headers` replaces the selector's declared list through `_merge_headers` (case-insensitive), so exactly one `Accept` goes on the wire; decoding still follows the declared return type. Test: `operations501` in `test/api/test_pet_api.mustache`.
- D168: `BetterPythonCodegen.writeResponseHeadersFile` renders `api/response_headers.mustache` to `api/options/<operation>_response_headers.py` for every operation with `vendorExtensions.op.hasResponseHeaders`, exported by the options barrel; fields are typed by the parsed builtin (`str`, `int`, `float`, `bool`, `List[...]`) rather than the pydantic `dataType`, and `from_headers` raises `SerializationException` for a value that does not parse. Tests: `headers501`, `headers502` in `test/api/test_store_api.mustache`.

## Deprecated security schemes, device authorization and HTTP Digest (D190-D192)

- **D190 (security scheme 3.2 fields).** `auth/scheme_authenticator.mustache` adds a plain sentence to the doc comment of a scheme marked `deprecated: true` (`schemeDeprecated`; never a deprecation attribute) and exposes a declared `oauth2MetadataUrl` as `METADATA_URL: ClassVar[str]` (`oauth2MetadataUrlLiteral`, escaped in `renderSchemeAuthenticator`). Test: `test/test_scheme_authenticators.mustache` (`test_bhv_authscheme_700`).
- **D191 (device authorization flow).** `auth/oauth/oauth2_device_authorization_authenticator.mustache` holds the authenticator and the frozen `DeviceAuthorization` dataclass (both re-exported from `auth/oauth/__init__`); the device request goes through `OAuth2TokenManager._post_form` (shared with the token request) and the poll through `exchange`; `_sleep`/`_monotonic` are the test seam. `recordDeviceAuthorizationScheme` feeds `deviceAuthorizationSchemes` to the test. Test: `test/test_oauth2_token_manager.mustache` (`TestOAuth2DeviceAuthorizationAuthenticator`, bhv authoauthtwodevice 700-706).
- **D192 (HTTP Digest).** `auth/digest_authenticator.mustache` holds `DigestAuthenticator` (a lock guards the challenge and the nonce count; re-exported from `auth/__init__`) and the internal `ChallengeAuthenticator` runtime-checkable `Protocol`; `base_api.mustache` adds the answer in `build_headers`, so every send and retry answers with the next nonce count, and `_send_under_policy` replays a 401 whose challenge is accepted once (buffered and streaming), all under `{{#hasDigestAuth}}`. `_cnonce_source` is the test seam. A digest scheme is a client scheme like basic. Tests: `test/test_basic_authenticator.mustache` (`TestDigestAuthenticator`, bhv authdigest 700, 701, 704-706), `test/test_base_api.mustache` (`TestDigestChallenge`, 702, 703).

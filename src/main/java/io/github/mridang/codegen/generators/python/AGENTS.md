# Python SDK generator

This generator emits a Python client package: pydantic v2 models, an `async` operation API per tag, a synchronous `DefaultApiClient` on a urllib3 `PoolManager` that the async layer calls through `asyncio.to_thread`, frozen-dataclass `Configuration` and `TransportOptions`, and the shared authenticator, error and serializer set. The generated tests run on pytest with `pytest-asyncio`; the quality gates are `ruff check`, `ruff format --check` and `mypy` (non-strict, default rule set) over sources and tests. The generated `pyproject.toml` declares `requires-python = ">=3.13"` and the specs pin the test image to `python:3.14-alpine`; runtime dependencies are `urllib3`, `python_dateutil` and `pydantic`, with `brotli` and `zstandard` as an optional `[compression]` extra.

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
| `errors.mustache`, `errors/*.mustache` | `ApiError` hierarchy, OAuth2 errors, header rendering | error classes and messages |
| `auth/*.mustache`, `auth/oauth/*.mustache` | authenticators, token manager, OIDC | auth behaviour |
| `server_configuration.mustache`, `servers.mustache` | server list and variables | server constants |
| `readme.mustache`, `skills*.mustache`, `pyproject_toml.mustache`, `makefile.mustache` | README, SKILLS.md, manifest, Makefile (`typecheck`, `docs`) | docs, dependencies, ruff and mypy config |
| `test/*.mustache`, `test/*.py` | the tests shipped inside the generated SDK | generated test files (add rows to the existing mirror file) |

## Idioms (deliberate differences from the other eleven)

| Topic | What Python does | Why |
|---|---|---|
| Async surface | operations are `async def`; the blocking urllib3 call runs in `asyncio.to_thread`; the authenticator `get_auth_headers` is a single sync method | urllib3 is synchronous; sync-only auth languages have one `getAuthHeaders` (root AGENTS, forced divergences) |
| Models | pydantic `BaseModel` with `Field(alias=wire_name)`; constructors take attribute or wire names | pydantic v2 is the idiomatic validator and serializer |
| Configuration | frozen `@dataclass` with a builder and also a public `__init__` | Python has no private constructors |
| Authenticators | frozen dataclasses; secrets are `repr=False` (api-surface rule) | dataclass fields are the constructor parameters |
| Cause on errors | error constructors take an optional `cause` that also sets `__cause__` | `raise ... from` chaining is the native mechanism (api-surface rule for ctor) |
| Decimals | bare `type: number` is `JsonNumber` (`Decimal`), exact on decode, widened to float on encode (FORCED, see D83) | stdlib json cannot emit a raw `Decimal` without a dependency |
| Date-time | model fields use the `OffsetDateTime` alias, not bare `AwareDatetime` (see FD-datetime) | pydantic's default JSON form is not the canonical `.SSS+00:00` |
| Servers | module-level constants `SERVER_0..` and one `Enum` per server variable | module constants are the Python namespace (api-surface rule `servers`) |
| Composed schemas | `RootModel` wrappers with plumbing members (`actual_instance`, `oneof_schemas` ...) | pydantic hooks (api-surface rule for composed plumbing) |
| Cancellation | no parameter; `asyncio` task cancellation (S7) | asyncio is the native handle |
| Lifecycle | context manager plus `close()`, which the `ApiClient` interface does not require | api-surface rule for `close` |
| Transport injection | a trailing `pool_manager` argument on `DefaultApiClient` and `Client` (api-surface rule for ctor) | urllib3 pool manager is the transport object |
| Optional codecs | `brotli` and `zstandard` are imported behind `try/except ImportError` and advertised only when installed | keeps the mandatory dependency set small (FD-compression) |

## Settled decisions (do not re-open in review)

Cross-language rules are written once in the shared file; each entry below says only how Python realises it. Decisions marked "already canonical" in the ledger (D09 to D14, D22 aside, D23 to D26, D28, D31, D33, D34, D36 to D38, D44, D52, D56, D58, D59, D63 to D65, D67, D68, D70, D72, D74, D75, D84 to D89, D91, D93, D94) need nothing Python specific and are covered by the shared file.

### Injected transport (S9, 2026-11-16)
Rule: the client accepts the language's standard HTTP client object, keeps SDK defaults, never closes a caller-supplied one and fails fast on conflicts. For Python the standard object in this SDK is the urllib3 `PoolManager`, passed as `DefaultApiClient(pool_manager=...)`; a supplied manager makes the TLS and proxy settings of `TransportOptions` inert, which the docstring states.
Why: every comparable SDK accepts the standard client. The session text names httpx for Python; the code, dependencies and tests use urllib3, so urllib3 is what this entry records (see Known limitations).
Where: `default_api_client.mustache` (constructor, `close`), `client.mustache`.
Test: `test_default_api_client_unit.mustache`, `test_client.mustache`.

### Cancellation (S7, 2026-11-16)
Rule: no new parameter. Cancelling the awaiting asyncio task aborts the call and `close()` cancels in-flight requests; cancellation raises `asyncio.CancelledError`, never `NetworkTimeoutException`.
Why: a wrapper or options object is less natural than the handle Python callers already use.
Where: `base_api.mustache`, `default_api_client.mustache`, `readme.mustache` (section "Per-call cancellation").
Test: `test_base_api.mustache`, `test_default_api_client_unit.mustache`. The current README still says there is no cancellation API (F228 is open), so the code and README must change together.

### Parameter order (S8, 2026-11-16, owner confirmation pending)
Rule: required parameters, then `server`, then the optional-parameters object, with no cancellation handle in Python; the same whether or not the operation has security.
Why: adding security must never move an argument (F803).
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

### Required plus nullable (S5, D66, 2026-11-16)
Rule: the key must be present on decode, explicit `null` is legal and kept, and encode writes the key (`null` when unset).
Why: the OpenAPI distinction between optional and nullable is part of the contract.
Where: `models/model.mustache` (required field is `Optional[X] = Field(alias=...)` with no default; serialization must not drop it under `exclude_none`).
Test: `test_composed_schema.mustache` and the model tests in the golden `test/`.

### allOf with a discriminator (S4, F057, F802, 2026-11-16)
Rule: Python uses class inheritance, and only the allOf-plus-discriminator parent is a parent class; a model reused in two unions is not re-parented, unions are `RootModel` wrappers.
Why: a class has one parent. The discriminator map and `get_discriminator_value` live on the parent.
Where: `models/model.mustache` (`{{#parent}}`, `discriminator_value_class_map`).
Test: `test_composed_schema.mustache`.

### Schema constraints (S3, F056, 2026-11-16)
Rule: the client does not promise to enforce `pattern`, length, range, `multipleOf` or item-count keywords; the server owns validation. Python is the one language that enforces them, because pydantic does so natively and cheaply: `Field(min_length, max_length, ge/gt, le/lt, multiple_of)`, and `pattern` through a `re` validator (never `Field(pattern=)`, because pydantic-core has no look-around). A violation raises `SerializationException`.
Why: this supersedes the D55 Python line, which deleted the Field constraints; the owner chose to keep native pydantic enforcement.
Where: `models/model.mustache` (lines with `min_length`, the `re.match` validator), `readme.mustache`. The README section still carries the older title "Numeric / string constraint validation" and must be renamed to "Schema constraints" with the not-promised wording.
Test: golden model tests (petstore declares no constraint, so the pattern validator is covered only by F448, which needs a spec property).

### Strict containers (S1, F006, 2026-11-16)
Rule: a response that is a container of primitives is checked element by element like a single primitive.
Why: a map of int32 must not accept a quoted number.
Where: `object_serializer.mustache` (`_STRICT_LEAF_TYPES`, `_STRICT_LEAF_ADAPTERS`).
Test: `test_object_serializer.mustache`, `test_base_api.mustache`.

### Content parameters (S2, D80, D50, F054, 2026-11-16)
Rule: a `content: application/json` parameter in query, header, path or cookie is encoded with `self._object_serializer.serialize(...)`, then encoded once by the location's normal rule; any failure is `SerializationException`.
Why: `json.dumps` produced different bytes (key names, date-times, spacing).
Where: `api/api.mustache` (the content branch, no inline `import json`).
Test: `test_value_serializer.mustache`, petstore content-parameter operation.

### uniqueItems order (S6, F319, 2026-11-16)
Rule: left unspecified; a set is sent in the language's natural collection order and nobody sorts it. The README says so.
Why: Python `set` has no declared order; this supersedes D69.
Where: `object_serializer.mustache` `_sanitize_for_serialization` still sorts comparable set members; that is the pre-decision behaviour and conflicts with S6 (see Known limitations).
Test: none that pins an order.

### Out of scope (S11, S12, 2026-11-16)
Rule: callbacks and webhooks, XML bodies and nodeType, Link Objects and mutual TLS are not generated and the README says so; OpenAPI 3.1 and 3.2 are tracked by epics #96 and #49.
Why: closed backlog issues #53, #70, #78, #88, #90; 3.2 loading is #50.
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
Rule: a `format: binary` response is raw bytes (text-class or absent Content-Type gives the UTF-8 bytes of the body string, anything else `b64decode(validate=True)` wrapped in `SerializationException`) through a binary marker distinct from the `bytes` used for `format: byte`; a non-JSON content type on a non-binary return is the text for `str` and otherwise `SerializationException` naming the Content-Type; a binary request body is sent verbatim under whatever content type the caller selected (`raw_body`, `read()` for a file-like); a form-urlencoded field that is a model or dict raises `SerializationException`; an unrecognised `request_content_type` raises `ValueError`.
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
Test: `test_default_api_client_unit.mustache` (`TestConnectionPoolSizing` covers the pool, F229: pools use `maxsize=10`).

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
Why: it is built on a different code path (the model's own `RootModel`); unifying needs unrelated work (F396).
Where: `models/model.mustache`.
Test: `test_composed_schema.mustache`.

## Known limitations and out of scope

Not decided or not done, each with its reason.

- The session decision S9 names httpx as the Python client object; the SDK uses urllib3 and the dependencies, the injection argument and the api-surface rule all say `pool_manager`. Decide whether the SDK keeps urllib3 (current code) or moves to httpx before editing; until then `Client` does not forward a transport (F405 open) and only `DefaultApiClient` accepts one.
- Cancellation (S7, F228): the README still says there is no cancellation API, and a call running in `asyncio.to_thread` cannot be interrupted mid-socket-read; whether `close()` cancels in-flight requests is not yet pinned by a test.
- uniqueItems order (S6): `_sanitize_for_serialization` sorts comparable sets, which conflicts with "do not sort"; the code, the README and the D69 line need to agree.
- Schema constraints (S3): the README section still has the old title; `pattern` has no golden coverage until a petstore property exists (F448, needs a spec).
- Python-specific API-surface rule: `apierror` constructors are listed as not taking a cause, but `errors.mustache` has a `cause` argument; reconcile the allow rule with S14.
- `format: password` is `PasswordStr`; F450 and F046 need a petstore `secret` property to test that the real value reaches the wire.
- `format: ipv4`, `ipv6` and `email` use validating pydantic types (F449); whether to drop them is an owner decision, not made.
- Constructors accept the attribute name but mypy flags `Metadata(created_at=...)` because pydantic types `__init__` by alias; fixing it needs the pydantic mypy plugin or hand-written signatures for every model (F462 deferred).
- `PythonClientSpec` runs `pytest -v` with the Makefile mypy path limited to `petstore_client/` and `test/`, so hand-written `spec/` files are not covered by the spec (F541 open).
- The warnings-fatal docs build (`make docs`, `sphinx-build -W`) exists but no spec runs it (F191 deferred, needs a `PythonDocsSpec`).
- The `Makefile` target is `typecheck`; renaming it alone would widen divergence, and a cross-SDK naming decision comes first (F371).
- Retry behaviour, SOCKS proxies (rejected at construction) and ambient proxy variables (ignored, D74) are deliberate non-features; the generated `LICENSE` file is not emitted.
- The codegen class javadoc says "Python 3.10+"; the manifest floor is 3.13. The manifest is the truth.
- OpenAPI 3.1 and 3.2 features: epics #96 and #49; closed out of scope: #53, #70, #78, #88, #90.
- ruff runs its default ruleset with no extra `select` and mypy is non-strict, so a missing annotation is not flagged.

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
python3 etc/api-surface.py check
etc/ci-local.sh check --quick
```

The first command runs the Python specs end to end (client tests, ruff format, ruff check, mypy, reserved words).

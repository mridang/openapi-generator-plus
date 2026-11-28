# Python

The `python-plus` generator produces a Python package built on pydantic v2 models and an `async` API. This page covers what the generated SDK needs, how it maps OpenAPI types, the options the generator accepts, and where Python differs from the other eleven languages. For the type table across all languages see [../types.md](../types.md); for the pipeline see [../how-it-works.md](../how-it-works.md).

## Versions and support policy

The generated `pyproject.toml` declares `requires-python = ">=3.10"`, and the mypy setting `python_version = "3.10"` checks the SDK's own code against that floor, so a standard-library call that only exists on a newer Python fails the type check instead of failing on a user's machine. The generator's own tests run in the `python:3.14-alpine` image. The image is pinned because a floating tag moved to a release for which `zstandard` had no wheel.

The policy behind the floor is the oldest Python release that still receives security fixes. A review of the type mapping (see "Under review") found that Python 3.10 reached end of life on 2026-10-01, so the floor is now behind that policy. Whether to raise it is not settled.

## Type mapping

| OpenAPI | Python type | Notes |
|---|---|---|
| `integer`, `int32`, `int64` | `StrictInt` | Python integers have arbitrary precision, so 64-bit values are exact. Strict mode rejects strings and floats. |
| `number` with `format: float` or `double` | `LaxFloat` | A strict float that also accepts an integral JSON number such as `5`. NaN and infinity are rejected. |
| `number` with no format | `JsonNumber` (`decimal.Decimal`) | The digits are kept exactly on decode. On encode the value is written as a bare JSON number, never a quoted string. A quoted number is rejected on decode. |
| `boolean` | `StrictBool` | |
| `string` | `StrictStr` | |
| `string` with `format: date` | `datetime.date` | |
| `string` with `format: date-time` | `OffsetDateTime` (a timezone-aware `datetime`) | A datetime without a timezone is rejected. It is written with exactly three fractional digits and a numeric offset such as `+00:00`, never `Z`. |
| `string` with `format: time` | `TimeOfDay` (`datetime.time`) | Written as `HH:MM:SS`, with milliseconds only when they are not zero. |
| `string` with `format: duration` | `ProtobufDuration` (`datetime.timedelta`) | The wire form is the protobuf-JSON form such as `3600s`, not ISO 8601. |
| `string` with `format: uuid` | `uuid.UUID` | |
| `string` with `format: uri` | `UrlStr` (a `str`) | Validated as an absolute RFC 3986 URI and returned unchanged, so the text round-trips byte for byte. `uri-reference` and `uri-template` stay plain strings. |
| `string` with `format: email`, `hostname` | `StrictStr` | No format validation. |
| `string` with `format: byte` | `Base64Bytes` (`bytes`) | Raw bytes in memory, base64 on the wire. Decoding is strict. |
| `string` with `format: binary`, file upload and download | `BodySource` for a raw request body, `ByteStream` for a response | The response is read as you consume it and the request body is sent as it is read, see Streaming. |
| `string` with `format: password` | `PasswordStr` (`SecretStr`) | The value is masked in `repr` but the real value is sent on the wire. |
| `array` | `List[T]` | |
| `array` with `uniqueItems` | `Set[T]` | |
| `additionalProperties` map | `Dict[str, T]` | `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form object | `object` | |
| nullable or optional | `Optional[T]` | |
| `enum` | a class derived from `str` and `Enum` | |
| `oneOf`, `anyOf` | a pydantic `RootModel` wrapper | The chosen value is `actual_instance`. |

The mapping in the table comes from `BetterPythonCodegen.java` and the aliases in the generated `_types.py` and `_duration.py`.

## Dependencies

Runtime dependencies are `urllib3` (2.8 or newer, below 3), `python_dateutil` (2.8.2 or newer) and `pydantic` (2.11 or newer, below 3). The HTTP transport is a synchronous urllib3 `PoolManager`, called from the async API through `asyncio.to_thread`.

Response decompression for `br` and `zstd` is an optional extra: `pip install <project-name>[compression]` installs `brotli` (1.0.0 or newer) and `zstandard` (0.23.0 or newer). The client imports them inside `try`/`except ImportError` and offers a codec in `Accept-Encoding` only when its package is installed. Without them the client still handles gzip and deflate.

The OpenTelemetry call hook is a second optional extra: `pip install <project-name>[opentelemetry]` installs `opentelemetry-api` (1.20.0 or newer), the API package only and never the SDK. The core package never imports it; only `<package>/opentelemetry_call_hook.py` does, so importing the client works without it.

The development group in the manifest adds pytest, `pytest-asyncio`, mypy, ruff, sphinx, `pip-audit` (run by `make audit`, D181), the test containers library and `docker`, which the test bootstrap imports itself. These are not needed at runtime.

## Options

Pass options with `--additional-properties` or a config file. Options that apply to every language, such as `clientClassName`, `errorPrefix`, `generateTests`, `generateUnitTests`, `licenseId` and `licenseUrl`, are shared by all twelve generators and are not repeated here. The Python generator adds these:

| Option | Meaning |
|---|---|
| `packageName` | The import package name, for example `petstore_client`. |
| `projectName` | The published distribution name, for example `petstore-client`. It is the `name` in `pyproject.toml` and the product token in the default `User-Agent`. Defaults to `packageName`. |
| `packageVersion` | The version written to the manifest. Defaults to `1.0.0`. |
| `authors` | Comma-separated author names for the manifest. |
| `repositoryUrl`, `homepageUrl`, `documentationUrl`, `issueTrackerUrl` | Written to the manifest and README only when given. |
| `envVarPrefix` | The prefix of the environment variables the credentials default from (see "Credentials and environment variables"). It is shared by all twelve generators. Defaults to the API title without a trailing `SDK`, in upper snake case. |

## Credentials and environment variables

Every authenticator the generator writes for a security scheme (for example `PetStoreBearerAuthenticator`) takes its credentials from the constructor, and falls back to an environment variable. The order is fixed:

1. The argument you pass wins, even an empty string (the authenticator then rejects it with its own message).
2. Otherwise the environment variable, read once when the authenticator is built. An empty variable counts as unset.
3. For a bearer scheme, `token_provider` (a callable that returns the current token and runs for every request) is an alternative to `token`. It wins over the environment variable, and giving both `token` and `token_provider` raises `ValueError`.

A credential found in none of these places raises `ValueError` when the authenticator is built, naming the variable, so the failure comes before the client exists and not on the first request.

The variable for a field is `{PREFIX}_{FIELD}` in upper snake case, for example `ACME_TOKEN`, `ACME_API_KEY`, `ACME_USERNAME`, `ACME_CLIENT_ID`. `{PREFIX}` is the `envVarPrefix` option, or else the API title without a trailing `SDK`. When two schemes have a field of the same name, each name also carries the scheme id (`ACME_SERVICE_TOKEN_TOKEN`) so that no scheme reads another's secret. The generated README lists the exact names for the API. `Client.with_token()` reads `{PREFIX}_BASE_URL` and `{PREFIX}_ACCESS_TOKEN` in the same order. The base authenticators (`BearerAuthenticator`, `BasicAuthenticator`, `ApiKeyAuthenticator`) and the OAuth2 classes they build on take explicit values only; the environment is read by the per-scheme classes.

HTTP Digest (D192): a `type: http, scheme: digest` scheme gets a `DigestAuthenticator(host, username, password)`. The first request goes without credentials; the base API answers the 401 `WWW-Authenticate: Digest` challenge and replays the request once (buffered and streaming), and later requests carry the answer up front with the nonce count incremented. MD5 and SHA-256 come from `hashlib`; no dependency was added. Negotiate is not supported.

## Idiom notes

- Every operation takes an optional trailing `options` argument, an Options object (`<Operation>Options`), even one with no parameters and no security. Besides the operation's own parameters it carries `timeout` (milliseconds, at least 1), `headers` (they replace a default header of the same name) and `max_retries` (at least 0; `0` turns retrying off) for that call only. There is no per-call authenticator: the credential is set on the client, per security scheme. A custom `ApiClient` owns its own timeout, so only the headers and the retry budget reach it. The per-call timeout, like the transport one (default 60 seconds), is one end-to-end deadline that covers connecting, every redirect hop and the whole body read of a buffered call (D137).
- An operation whose success response declares headers (other than `Content-Type`) also gets a frozen dataclass `<Operation>ResponseHeaders` in `api/options/`, one `Optional` field per header typed `str`, `int`, `float`, `bool` or a `List` of them, and `<Operation>ResponseHeaders.from_headers(result.headers)` reads the declared headers case-insensitively, raising `SerializationException` for a value that does not parse (D168).
- **Streaming.** An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns `ByteStream`, an async context manager and async iterator of `bytes`, from the one operation method (there is no second method), and `..._with_http_info` returns `ApiResult[ByteStream]` with an empty `raw_body`. A `ByteStream` has `status_code`, the lower-cased `headers`, `read(n)` (up to `n` bytes as soon as some arrive, `b''` at the end), `read_all()` (reads the rest and leaves the stream closed), an idempotent `close()` that aborts the connection instead of draining it, and a `repr` that shows only the status and the bytes delivered. The transport gets a second interface, the `runtime_checkable` Protocol `StreamingApiClient` with `send_streaming(method, url, headers, body, idle_timeout)`, which returns an `ApiStreamResponse` (status code, headers, open body); a custom `ApiClient` that only implements `send_request` keeps working for buffered calls, and a stream call on it raises `NotImplementedError` naming it. `DefaultApiClient` runs the buffered call's header phase and `BaseApi` its retries, redirects and token refresh up to the headers: the `timeout` ends at the response headers, `TransportOptions.builder().idle_timeout(milliseconds)` (default 60 seconds, `0` or `None` disables) bounds the gap between two reads of the body and raises `NetworkTimeoutException`, and nothing is retried after the first body byte. A non-2xx status raises the usual typed error with its body read up to 64 KiB. A reset, or a body shorter than its `Content-Length`, raises `StreamException` (a `NetworkException`, status 0). `client.close()` cancels the streams still open; their next read raises `RuntimeError` ("ApiClient has been closed and can no longer send requests"). 
- **Uploads.** An operation whose request body is raw binary takes a `BodySource` (in the SDK package) in every signature (the operation, `_with_http_info`, the `Protocol`): `BodySource.of(bytes)`, `BodySource.of_file(path)` and `BodySource.from_factory(factory, length=None)` are replayable, `BodySource.once(stream, length=None)` is single use. A stream is a binary file object, an iterable of `bytes` or an async iterable of `bytes` (read on the event loop of the call); the constructors take the native types, so the wrapper costs one line at the call site. The body is sent as it is read, never buffered: a known length sends `Content-Length`, an unknown one is chunked. A replayable body is opened again for a retry after a 5xx, a 429 or a network error and for a 307 or 308; a single-use body is never retried (the original error is raised unchanged), a redirect that would need it again raises `ApiException` with `Refusing to replay a streamed request body`, and a 401 token refresh and an SSE reconnect follow the same rule. The SDK never closes a stream passed to `once`, and closes what it opened from a file or a factory when the send is over. `repr` shows the kind and the length, never the content or the path. A custom `ApiClient` receives the `BodySource` as the `body` and reads it with `open()`, which returns an iterator of `bytes` with `close()`. Multipart bodies stay buffered.
- **Pagination.** A GET list that the shared detector finds paged (a token cursor, an id cursor, an offset and limit, a page number, a `Link` header declared on the response, or a next URL in the body; D123, D124) has `<operation>_pages(...args, options=None)` beside its plain method, in the class and in its `Protocol`. It is a plain `def`, not a coroutine, and returns `Paginator[T, C]` (`T` the item type, `C` the cursor type, `int` for an offset or a page number, `None` for a `Link` or next URL list), whose `__aiter__` and `items()` are async iterators of the items and whose `pages()` is an async iterator of `Page[T, C]`, a frozen dataclass of `items`, `next_cursor` (`None` on the last page, always `None` for `Link` and for a next URL in the body) and `result` (the `ApiResult` of the page). The cursor is an optional query parameter, so it lives in the `<Operation>Options` dataclass, and `dataclasses.replace` gives each page its own cursor and leaves the caller's options untouched. The method sends nothing; each `items()`, `pages()` or `async for` builds a fresh page iterator, so a second loop starts again, a page is an ordinary `_with_http_info` call (so cancelling the awaiting task aborts the page in flight), and a failed page raises from the read that needed it and ends the iteration. A cursor equal to the one just sent, or a `Link` that points back at the page just read or leaves the origin of the client, delivers the page it came with and raises `PaginationException` (a direct child of the root exception, not an `ApiException`) from the next read. A `Link` page is requested through a private `_<operation>_page(...args, page_url=None)` that replaces the path with the absolute URL, so authentication, retries and the call hook apply to it unchanged; the link is parsed per RFC 8288 in `link_header.py`.
- **Pagination ends and resumes.** How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationException` as a token cursor does.
- **Pagination by next URL.** A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationException`, nothing is sent to it, and such a method cannot resume from a saved position.
- **JSON Lines and server-sent events.** An operation whose success response is `application/x-ndjson`, `application/jsonl`, `application/jsonlines` or `text/event-stream` has no buffered method and no `_with_http_info` variant, in the class or in its `Protocol`. It has `<operation>_stream(...args, options=None, stream_options=None)` returning `JsonLinesStream[T]` or `SseStream[T]`, an async iterator and async context manager with `status_code`, the lower-cased `headers`, an idempotent `close()` and a `repr` that never shows the data; `await` of the method sends the first request, so its failure is raised there. `StreamOptions` is a frozen dataclass of `idle_timeout`, `max_reconnects`, `reconnect_delay`, `last_event_id` and `max_event_bytes`, all `None` for the default. `ServerSentEvent[T]` is a frozen dataclass of `id`, `event` (`'message'` by default), `data`, `retry` and `raw`. The grammar is WHATWG (`event_stream.py`: `LineReader`, `SseParser`); an event stream reconnects as the README says, with a fresh `_invoke_api_for_stream` call per connection (so the hook, headers, authentication and 401 replay run again), `Last-Event-ID` only when the id is not empty, and a wait of the server `retry`, `Retry-After` (429, 502, 503, 504) or `reconnect_delay` doubling to 30 s with jitter. An SSE request sends `Cache-Control: no-cache` and `Accept-Encoding: identity` unless the caller set them; a gzip, deflate (and br or zstd when installed) body is decoded as it arrives, and a corrupt one raises `ApiException` with the real status and the decoder error as its cause. The `sseTerminator` generator option ends the stream at a matching `data` value.
- The client takes one optional credential per distinct security scheme, as a keyword argument named after the scheme in snake case (`Client(pet_store_bearer=..., admin_basic=...)`), plus an optional default `authenticator` that serves an operation no scheme credential matches. Each operation uses the credential of the first scheme its own spec lists that you configured; an operation with `security: []` sends none. With no matching credential and no default, no credential is sent. The base URL comes from the first credential given, `authenticator` first, and a client built with none raises `ValueError`. Every credential that needs the transport (OAuth 2 and OpenID Connect) is given the client's own.
- **Security scheme metadata (D190).** A security scheme marked `deprecated: true` gets a sentence saying so in its generated authenticator's doc comment (no deprecation attribute), and a scheme that declares `oauth2MetadataUrl` exposes that URL on its authenticator as the class attribute `METADATA_URL: ClassVar[str]`; it is not fetched.
- **Device authorization flow (D191).** A scheme with a `deviceAuthorization` flow gets an `OAuth2DeviceAuthorizationAuthenticator`: `request_device_authorization()` returns a frozen `DeviceAuthorization` dataclass to show to the user, and `poll_for_token()` blocks until the approval; until a token is polled `get_auth_headers()` raises `RuntimeError('Must call poll_for_token() before making API requests')`.
- A cookie parameter is sent exactly as given when every character is a legal RFC 6265 cookie-octet; otherwise the call raises `ValueError` before anything is sent, so percent-encode the value yourself.
- Every operation is `async def`. Cancelling the awaiting asyncio task aborts the call; there is no cancellation parameter. The blocking socket read runs in a worker thread and cannot be interrupted part way.
- `TransportOptions` takes `max_response_bytes` (off by default): a buffered response whose body, raw or decompressed, passes it fails with `ResponseTooLargeException` (D138). `DefaultApiClient` counts the bytes as `read1` returns them and decodes gzip and deflate through `zlib` with a `max_length`, brotli with its output limit and zstd in small input pieces, so decoding stops once the limit is passed; the connection is dropped, not drained.
- `DefaultApiClient` accepts a `pool_manager` argument to supply your own urllib3 `PoolManager`. When you supply one, the TLS and proxy settings in `TransportOptions` have no effect, and the SDK never closes a manager it did not create.
- The client works as a context manager and also has `close()`. Closing is graceful and idempotent: the client is marked closed at once, calls already in flight (retries and token requests included) run to their end, the `PoolManager` the SDK created is released when the last of them completes, and a call started afterwards raises `RuntimeError` (`ApiClient has been closed and can no longer send requests`). A `pool_manager` you supplied is never closed. Abandoning one call is done by cancelling the asyncio task that awaits it.
- Constructing a model never validates and never raises on a value: a value that fits its field is coerced (a list given for a set field becomes a set), and one that does not, such as a wrong type, an unknown enum string or a value outside a constraint, is kept as given and sent. A required field left out raises `TypeError`. Validation happens, and an unknown enum value decodes to the catch-all member (D117), when a document is decoded (`SerializationException`, or `pydantic.ValidationError` from `model_validate`).
- `Configuration` and `TransportOptions` are frozen dataclasses with a builder and also a public constructor. Their `repr` hides the proxy password and any default header named `Authorization`, `Proxy-Authorization` or `Cookie`, or containing `key`, `token` or `secret`.
- `Configuration` takes `validate_constraints` (a dataclass field and `Configuration.builder().validate_constraints(True)`), `False` by default (D189). Because construction never validates, a body built with values outside its constraints is sent as given unless the option is on. When it is on, the request body of every call is checked before the call hook runs and before anything is serialized or sent, against the `minLength`, `maxLength`, `pattern`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minItems`, `maxItems`, `minProperties` and `maxProperties` keywords of its schema and of every model it holds or lists, and the first violation raises `ValueError` with the message `Invalid request body: <path> <rule>` (for example `Invalid request body: parts[1].level must be greater than or equal to 0`). Lengths count code points and a pattern is an `re.search`; a pattern `re` cannot compile is not enforced. The check is the private `<package>/_constraint_validator.py`; decoding keeps validating through pydantic whatever the option says.
- One optional call hook runs around every operation call. `CallHook` and `CallObserver` are `Protocol` classes, `CallInfo` (`operation_id`, `method`, `url_template`, `url`, `server_address`) and `CallOutcome` (`status_code`, `headers`, `error`) are frozen dataclasses, and `chain(*hooks)` combines hooks. Set it with `Client(authenticator, call_hook=hook)` or `Configuration.builder().call_hook(hook)`. `start` and `finish` are plain functions called on the event loop, so they must not block. Because the headers are rebuilt for every send, the edits a hook makes are kept and applied again to each rebuilt set; the fresh authentication headers of a replay after a `401` still replace a hook header of the same name. `OpenTelemetryCallHook(tracer_provider=None, meter_provider=None)` in `<package>.opentelemetry_call_hook` records the span, `http.client.request.count` and `http.client.request.duration`; it writes `traceparent` itself.
- Models accept either the attribute name or the wire name in the constructor.
- Error constructors take an optional `cause`, which also sets `__cause__`.
- Server URLs are module constants `SERVER_0`, `SERVER_1` and so on, with one `Enum` per server variable; `by_name(name)` returns the server an OpenAPI 3.2 document names and raises `ValueError` for an unknown name.
- Names follow PEP 8: snake_case operations, attributes and modules.
- Testing your code: `<Group>ApiProtocol(Protocol)` in the group file and `ClientProtocol(Protocol)` in the client file, both exported from `__init__`. `PetApi` is not changed: it satisfies the protocol structurally, and a fake subclasses the protocol so only the operations it fakes are written (the stubs return `None`).
- With `generateExamples` on, the package ships `examples/quickstart.py`, a script that builds a client from `{PREFIX}_BASE_URL` and `{PREFIX}_ACCESS_TOKEN`, calls the example operation inside `with ... as client:` and runs through `asyncio.run(main())`; `make examples` type-checks everything under `examples/` with mypy and byte-compiles it, and `make examples-run` runs the quickstart. `pyproject.toml` packages only the SDK package, so `examples/` never ships in the wheel.
- `OAuth2TokenManager.stop()` ends the manager: a later token request raises `OAuth2TokenException` with the message "The token manager has been stopped." and sends nothing. Stopping twice does nothing.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `CallHook` is the name to look for in this SDK.

```python
class TenantHook:
    def start(self, info: CallInfo, headers: dict[str, str]):
        headers['X-Tenant'] = 'a'
        return None

client = Client(authenticator, call_hook=TenantHook())
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```python
with Client.with_token("https://eu.api.example.com", "eu-token") as eu, \
        Client.with_token("https://us.api.example.com", "us-token") as us:
    ...
```

Closing one client does not affect the other.

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `models/animal.py`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `..._with_http_info` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiException` that holds the status code, the response headers and the response body. A live response object is not exposed, because that would need streaming (see [decisions](../decisions.md)); to see or change what goes over the wire, use the call hook above or the transport you supply.

```python
result = await client.pet.add_pet_with_http_info(pet)
status = result.status_code
etag = result.headers.get("etag")
raw = result.raw_body

try:
    await client.pet.add_pet(pet)
except ApiException as e:
    print(e.status_code, e.response_headers, e.response_body)
```

## Known limits

- The transport is urllib3, not httpx. A session decision named httpx, but the generated code, its dependencies and its tests use urllib3. The generated `Client` does not yet forward a transport; only `DefaultApiClient` accepts one.
- A request body and a JSON, text or form response body are held in memory. A binary, JSON Lines or server-sent events response is a stream, and a raw binary request body is sent from a `BodySource` as it is read; multipart bodies stay buffered.
- Constructors accept the attribute name, but mypy reports `Metadata(created_at=...)` because pydantic types `__init__` by alias.
- Retry is not built in. SOCKS proxies are rejected at construction, and proxy environment variables are ignored on purpose; only `TransportOptions.proxy` is used.
- Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope. OpenAPI 3.1 and 3.2 support is tracked in the epics #96 and #49.
- The generated documentation build (`make docs`) exists but no test runs it, and the Makefile type-check target is named `typecheck`.
- No `LICENSE` file is generated.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- Operations are asynchronous only, as `async def` coroutines, by decision D111. There is no blocking variant of an operation, because each SDK follows the style of its own language. Call it from your language's async machinery.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.
- A security requirement that names several schemes together (for example a bearer token and an API key) sends the credentials of all of them, by decision D116. The client must hold every credential of the requirement; an alternative the client holds only in part is used only when no other alternative is complete. A requirement that names one scheme works as before.
- The OpenID Connect authenticator sends a nonce and checks the `id_token` claims (nonce, issuer, audience, expiry), but does not verify the `id_token` signature, by decision D140. Signature verification can be added later without changing the public API, because the discovery document already gives the `jwks_uri`.

## Under review

These are ideas from a review of the type mapping against current Python. None is decided, and none changes what the SDK does today.

- Raise the floor to Python 3.11, because 3.10 is past end of life. That would allow `enum.StrEnum` in place of the `str, Enum` classes, `datetime.UTC`, the standard `tomllib` for the tests, and `datetime.fromisoformat` for any RFC 3339 form. The last point might let the `python_dateutil` dependency be dropped if nothing else uses it.
- Write annotations with built-in generics and `X | None` instead of `List`, `Dict`, `Set` and `Optional`. This works on 3.10 and does not change the floor.
- Use `frozenset` for `uniqueItems` so frozen models hash without the helper that freezes sets today. This needs a test of pydantic strict JSON handling.
- Type free-form objects as `Any` rather than `object`, so mypy allows attribute access.
- Not planned for now: `frozendict` (Python 3.15) and `uuid7` generation (3.14), because both are above the floor and the SDK generates no ids.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `inject` reads the `ContextVar` context of the awaiting task (`use_span` or `context.attach`); headers are built in the coroutine, not offloaded.

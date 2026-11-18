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
| `string` with `format: binary`, file upload and download | `bytes` | Bodies are held in memory. |
| `string` with `format: password` | `PasswordStr` (`SecretStr`) | The value is masked in `repr` but the real value is sent on the wire. |
| `array` | `List[T]` | |
| `array` with `uniqueItems` | `Set[T]` | |
| `additionalProperties` map | `Dict[str, T]` | |
| free-form object | `object` | |
| nullable or optional | `Optional[T]` | |
| `enum` | a class derived from `str` and `Enum` | |
| `oneOf`, `anyOf` | a pydantic `RootModel` wrapper | The chosen value is `actual_instance`. |

The mapping in the table comes from `BetterPythonCodegen.java` and the aliases in the generated `_types.py` and `_duration.py`.

## Dependencies

Runtime dependencies are `urllib3` (2.1 or newer, below 3), `python_dateutil` (2.8.2 or newer) and `pydantic` (2.11 or newer, below 3). The HTTP transport is a synchronous urllib3 `PoolManager`, called from the async API through `asyncio.to_thread`.

Response decompression for `br` and `zstd` is an optional extra: `pip install <project-name>[compression]` installs `brotli` (1.0.0 or newer) and `zstandard` (0.23.0 or newer). The client imports them inside `try`/`except ImportError` and offers a codec in `Accept-Encoding` only when its package is installed. Without them the client still handles gzip and deflate.

The development group in the manifest adds pytest, `pytest-asyncio`, mypy, ruff, sphinx and the test containers library. These are not needed at runtime.

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

## Idiom notes

- Every operation takes an optional trailing `options` argument, an Options object (`<Operation>Options`), even one with no parameters and no security. Besides the operation's own parameters it carries `timeout` (milliseconds, at least 1), `headers` (they replace a default header of the same name) and `max_retries` (at least 0; `0` turns retrying off) for that call only. There is no per-call authenticator: the credential is set on the client, per security scheme. A custom `ApiClient` owns its own timeout, so only the headers and the retry budget reach it.
- The client takes one optional credential per distinct security scheme, as a keyword argument named after the scheme in snake case (`Client(pet_store_bearer=..., admin_basic=...)`), plus an optional default `authenticator` that serves an operation no scheme credential matches. Each operation uses the credential of the first scheme its own spec lists that you configured; an operation with `security: []` sends none. With no matching credential and no default, no credential is sent. The base URL comes from the first credential given, `authenticator` first, and a client built with none raises `ValueError`. Every credential that needs the transport (OAuth 2 and OpenID Connect) is given the client's own.
- A cookie parameter is sent exactly as given when every character is a legal RFC 6265 cookie-octet; otherwise the call raises `ValueError` before anything is sent, so percent-encode the value yourself.
- Every operation is `async def`. Cancelling the awaiting asyncio task aborts the call; there is no cancellation parameter. The blocking socket read runs in a worker thread and cannot be interrupted part way.
- `DefaultApiClient` accepts a `pool_manager` argument to supply your own urllib3 `PoolManager`. When you supply one, the TLS and proxy settings in `TransportOptions` have no effect, and the SDK never closes a manager it did not create.
- The client works as a context manager and also has `close()`. Closing is graceful and idempotent: the client is marked closed at once, calls already in flight (retries and token requests included) run to their end, the `PoolManager` the SDK created is released when the last of them completes, and a call started afterwards raises `RuntimeError` (`ApiClient has been closed and can no longer send requests`). A `pool_manager` you supplied is never closed. Abandoning one call is done by cancelling the asyncio task that awaits it.
- Constructing a model never validates and never raises on a value: a value that fits its field is coerced (a list given for a set field becomes a set), and one that does not, such as a wrong type, an unknown enum string or a value outside a constraint, is kept as given and sent. A required field left out raises `TypeError`. Validation, and the loud failure on an unknown enum value, happen when a document is decoded (`SerializationException`, or `pydantic.ValidationError` from `model_validate`).
- `Configuration` and `TransportOptions` are frozen dataclasses with a builder and also a public constructor. Their `repr` hides the proxy password and any default header named `Authorization`, `Proxy-Authorization` or `Cookie`, or containing `key`, `token` or `secret`.
- Models accept either the attribute name or the wire name in the constructor.
- Error constructors take an optional `cause`, which also sets `__cause__`.
- Server URLs are module constants `SERVER_0`, `SERVER_1` and so on, with one `Enum` per server variable.
- Names follow PEP 8: snake_case operations, attributes and modules.

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

- An `integer` with no format is 32 bits, the same as `int32`, by decision D95 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)), and a value outside the range is meant to fail the decode with a `SerializationError`. This SDK decodes it into a pydantic `StrictInt`, so a larger value is accepted today; closing that gap changes no public type. Declare `format: int64` where a field needs more.
- The transport is urllib3, not httpx. A session decision named httpx, but the generated code, its dependencies and its tests use urllib3. The generated `Client` does not yet forward a transport; only `DefaultApiClient` accepts one.
- Large downloads and uploads are held in memory.
- Constructors accept the attribute name, but mypy reports `Metadata(created_at=...)` because pydantic types `__init__` by alias.
- Retry is not built in. SOCKS proxies are rejected at construction, and proxy environment variables are ignored on purpose; only `TransportOptions.proxy` is used.
- Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope. OpenAPI 3.1 and 3.2 support is tracked in the epics #96 and #49.
- The generated documentation build (`make docs`) exists but no test runs it, and the Makefile type-check target is named `typecheck`.
- No `LICENSE` file is generated.

## Under review

These are ideas from a review of the type mapping against current Python. None is decided, and none changes what the SDK does today.

- Raise the floor to Python 3.11, because 3.10 is past end of life. That would allow `enum.StrEnum` in place of the `str, Enum` classes, `datetime.UTC`, the standard `tomllib` for the tests, and `datetime.fromisoformat` for any RFC 3339 form. The last point might let the `python_dateutil` dependency be dropped if nothing else uses it.
- Write annotations with built-in generics and `X | None` instead of `List`, `Dict`, `Set` and `Optional`. This works on 3.10 and does not change the floor.
- Use `frozenset` for `uniqueItems` so frozen models hash without the helper that freezes sets today. This needs a test of pydantic strict JSON handling.
- Map `format: ipv4` and `ipv6` to `ipaddress.IPv4Address` and `IPv6Address`, with a string-only validator.
- Type free-form objects as `Any` rather than `object`, so mypy allows attribute access.
- Not planned for now: `frozendict` (Python 3.15) and `uuid7` generation (3.14), because both are above the floor and the SDK generates no ids.

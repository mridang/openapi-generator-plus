# How it works

This page explains how one OpenAPI document becomes twelve SDKs, how the project proves the result is correct, and what every generated SDK has in common. It is written for people who want to change the generator and for users who want to know why the SDKs behave alike. Per-language details live in [languages](languages/), the type table in [types](types.md) and the settled decisions in [decisions](decisions.md).

## The idea

The generator is a set of twelve code generators for the OpenAPI Generator toolkit, registered as `java-plus`, `kotlin-plus`, `csharp-plus`, `php-plus`, `python-plus`, `ruby-plus`, `node-plus`, `dart-plus`, `go-plus`, `rust-plus`, `swift-plus` and `elixir-plus`. They share one rule: the twelve SDKs have the same public API shape, send the same bytes on the wire, accept and reject the same inputs and raise the same errors. They differ only where a language forces it, such as a builder in one language and a keyword list in another. A difference between two languages is a defect until someone can name the language feature that requires it.

## The pipeline

A run goes through these steps in order.

1. The OpenAPI document is loaded and, if you asked for it, rewritten by the normaliser.
2. The shared generator reads the document, prepares it further and builds the model that the templates render.
3. The language's Mustache templates produce the SDK source, its tests and its build files.
4. A formatter for that language runs in a Docker container over the output.
5. The result is checked in as a golden, and a spec builds, lints, formats and tests the golden.

### Normalising the specification

The normaliser is `AdvancedOpenAPINormalizer`. It first runs the stock OpenAPI Generator normalisation and then applies any of these rules whose key you passed in `--openapi-normalizer "RULE=VALUE,..."`, in this order:

| Rule | What it does |
|---|---|
| `CONTENT_ENCODING` | Turns the OpenAPI 3.1 `contentEncoding` and `contentMediaType` keywords into the 3.0 `format: byte` and `format: binary` form the generators understand. |
| `STRIP_PARAMS` | Removes the named parameters (a `\|`-separated, case-insensitive list) from every operation. The shared components stay. |
| `CLEAN_EMPTY_REQUEST_BODIES` | Finds request bodies that are effectively empty. In the default `Tag` mode a required empty body is tagged; in `Remove` mode an optional empty body is deleted. |
| `ONLY_ALLOW_JSON` | Keeps `application/json` as the only content type of request and response bodies, then fails the run if anything else is left. |
| `FILTER_PATHS` | Keeps only the paths that match at least one of the given regular expressions (a comma-separated list), so you can generate an SDK for part of an API. |
| `RUN_SCRIPT` | Runs your own JavaScript transformation through GraalVM. |
| `GARBAGE_COLLECT_COMPONENTS` | Removes components nothing refers to any more. Run it last so it sees the effect of the other rules. |

Some rules need no configuration because the generator applies them to every document when it processes it. They run in this order: OpenAPI 3.1 `prefixItems` tuples are turned into a plain array of any value, with the original per-index types written into the description, because no language has a portable fixed-length mixed tuple; `contentEncoding` is translated as above; operations marked `x-internal: true` are dropped from the generator's view; and an operation with two or more `2xx` responses of different schemas gets one generated `oneOf` result type named `<OperationId>Response`, with the status-to-schema mapping kept so the right schema is used for the status that arrives. An operation whose `2xx` responses share one schema, or that has no body next to one schema, is left alone. Human-facing text (titles, summaries, descriptions) has its HTML entities decoded, because some specs produced by protocol-buffer tooling arrive with quotes and angle brackets escaped.

The `x-internal` rule is how the shared test specification works: transport-test endpoints (echo, delay, compression, redirects, error statuses) live in the same file that the mock server serves, and are removed before generation so they do not become SDK methods.

### The shared generator

`AbstractBetterCodegen` is about six thousand lines and holds everything the twelve have in common, so changing it changes every SDK. It escapes literals and enum values, normalises doc comments, decides which model names collide with reserved words, adds per-operation server information, renders each operation's Options class, detects the security schemes in the document and exposes them to the templates, and prepares the data the templates need. Each language adds a thin `Better<Lang>Codegen` class with only what is its own: its type mappings, its reserved words, its identifier casing and its file layout.

Three small helpers sit beside it. `NamingConvention` names the casing strategies a language uses. `BarrelFileEmitter` is an opt-in interface for the index file that re-exports the Options classes, implemented by Python, Node, Dart and Rust. `WithTypeSignatureSupport` is an opt-in interface for companion signature files, implemented only by Ruby (RBS files for the Steep type checker). A change that must apply to every language goes into the shared class behind an opt-in flag where it could break one of them.

### Templates

The SDK source is written only in Mustache templates under `src/main/resources/templates/<lang>/`. File content is never assembled in Java code, and templates never read vendor extensions. A template is the only durable source: the goldens are output and are never edited by hand. The same trees hold the templates for each SDK's tests, which is how every SDK ships tests with the same names and structure.

A few rules keep templates from breaking: a Mustache comment must never contain a literal tag; generated PHP and C# code may not use `//` comments; no generated file may contain an HTML entity (write `&#38;` where one is needed); and no generated file may disable a whole linter or rule set. A specific rule may be suppressed only by name, with the reason in the shipped configuration.

### Formatting

After generation each language's own formatter runs in a Docker container through `DockerFormatter`, a few containers at a time. It fails loudly on any error; a swallowed formatter failure once left a language unformatted from run to run. Formatters install their tooling from the network, so regenerating on a laptop can show spurious differences, mostly in PHP and Swift. The rule is to commit all twelve goldens current, never a subset, and to treat a run that changes more than a hundred files as a missing formatter container, not as a real change.

## Goldens

A golden is a complete SDK generated from the petstore-derived fixture specification `src/spec/resources/specs/petstore/openapi.yaml`, committed under `src/spec/resources/generated/<lang>/`. It includes the source, the generated tests, the build manifest, a README and a `SKILLS.md` reference for AI assistants. Because it is committed, a change to a template shows up as a diff in the golden, and CI fails if regenerating leaves the tree dirty.

The fixture specification holds every shape the templates must handle. A new template shape is added to the `probe` specification (`src/spec/resources/specs/probe/openapi.yaml`) and all twelve goldens are regenerated. `FixtureVocabularyLeakTest` fails the build if petstore vocabulary such as `pet` or `swatch` leaks into a template or into generated test sample data, because real clients use the same templates.

Regenerate the goldens with `mvn test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false`. The build manifest of each golden is also where this documentation reads each language's version floor from.

## The specs and the gates

Each language has a set of specs under `src/spec/java/io/github/mridang/codegen/spec/<lang>/`. They do not generate anything. They take the committed golden and, inside a Docker container with that language's toolchain, build it, lint it, check its formatting, type-check or statically analyse it where the language has such a tool, run its generated tests, and check its reserved words. The exact classes differ a little by language (Java has a static-analysis spec, Go has a WebAssembly spec), but the client spec that runs the generated tests and the formatting spec exist everywhere. Linters and type checkers run in check mode over sources and tests, and a golden that fails one fails the build. Run one language end to end with `mvn verify -Dgroups=<lang>`, or everything with `mvn verify`.

`etc/ci-local.sh` is the single definition of green and CI runs the same steps. `etc/ci-local.sh check <Lang>` runs spotless, spotbugs, the unit tests and that language's specs, then requires a clean tree after regeneration. `check --quick` skips the integration specs. A commit is allowed only after the check has passed on it locally. A `ContainerLaunchException`, a `404 exec` or a container killed with exit code 137 is infrastructure, not a code failure; re-run that language once before concluding anything.

### Shared helper containers

Two services support the generated tests. Chasm is a mock server that serves the full fixture specification, so each SDK's tests talk to a real HTTP server; Squid is a proxy used for the proxy and authentication tests. `SharedHelperContainers` starts one Chasm and one Squid per Maven run on a shared network and stops them in the same shutdown hook that closes the network. `SharedRuntimeContainer` hands their addresses to every language container through environment variables (`CHASM_HTTP_URL`, `CHASM_HTTPS_URL` and the internal variants, `PROXY_URL` and others).

Every language's test fixture checks `CHASM_HTTP_URL` and `PROXY_URL`. When both are set it uses those instances and starts nothing; when they are not set it starts its own containers, so a generated SDK's tests still run on their own in a real project. Both servers are stateless, and no test may assume exclusive access to them. A new fixture must keep both paths.

No private key is committed, in the repository or in a generated SDK. `TestCertificates` generates a throwaway CA and one server certificate (names `chasm`, `wiremock`, `localhost`, `host.docker.internal` and `127.0.0.1`) with the JDK's `keytool` once per Maven run, into a temporary directory that is deleted on exit. `SharedHelperContainers` gives the server certificate and key to the shared Chasm, and `SharedRuntimeContainer` mounts the directory read-only into each language container at the path that language's tests read `ca.pem`, `server.pem` and `server-key.pem` from. The generated SDK does not contain them. `NoCommittedPrivateKeysTest` fails the build if a private key appears under `src/main` or `src/spec/resources`.

### The API surface check

`etc/api-surface.py` reads the non-test source of all twelve goldens and extracts each SDK's public surface: its types, members, parameter names and order, optional parameters, return types and enum cases. It folds away idiom before it compares, such as naming case, the `Exception` and `Error` suffix, a getter against a property, an `Async` suffix or an overloaded method set, and then reports every remaining difference. `etc/api-surface.py check` must not get worse with a change. A difference that is genuine idiom is recorded in `etc/api-surface-allow.json` with a reason that names the language feature forcing it. The canonical result is written to `API_SURFACE.md`.

### The parity ledger

`PARITY_FINDINGS.json` is the only list of open work. It is maintained only through `etc/parity-findings.py`, which reports the open count and is the one progress number. Reviews, audits and the parity guard report into it, and a finding that is not written there with an id is lost. The ledger tracks work; settled decisions live in `AGENTS.md` and are summarised in [decisions](decisions.md), so a reviewer does not reopen them.

The default way to fix findings is in direct batches of fifteen to twenty grouped by the kind of bug: edit the templates, regenerate, run the API surface check and the quick gate, commit once per batch, and push once per round. Every fix needs a test or a golden that fails without it, in all twelve languages when the bug could exist in all twelve. Standing read-only audits (OpenAPI feature coverage, test parity and developer experience against reference SDKs) rerun at any time and write into the ledger.

## The standard every SDK follows

Every generated SDK has the same parts in the same order. Names follow the language's own casing and error suffix (`Exception` in Java, Kotlin, C#, PHP, Python and Dart; `Error` in Go, Node, Ruby, Rust, Swift and Elixir), and the examples below use the shape of the Java SDK.

### The client

The entry point is a class named `Client`; the `clientClassName` option renames it. It owns the connection, and each tag in the API is a typed field on it (`client.pet`). Operations are methods with required parameters first, then the server, then an options object for the optional parameters, then the per-call cancellation handle where the language has one. A client that is closed raises the language's built-in state error when used again. C# spells this `Dispose`, Java uses `AutoCloseable`, and Python uses a context manager.

Every operation also has a variant that returns the raw response (`...WithHttpInfo` in Java), carrying the status code, the body as received and the lower-cased headers. It is the way to read a response header.

### Configuration and transport

Two objects configure a client, and they stay separate on purpose. `Configuration` holds what belongs to the API: the base URL, the default headers and the authenticator. `TransportOptions` holds how bytes travel: proxy, TLS settings, timeout, retries, redirects and an optional caller-supplied HTTP client. Both are built with a builder in every language except Elixir, which takes a keyword list because its data is immutable. The string forms of both hide the proxy password and any default header that carries a credential.

A caller may pass the language's standard HTTP client object. The SDK keeps its own defaults on top of it, never closes a client it did not create, and refuses combinations that conflict, such as a supplied client together with a proxy.

`timeout` is one end-to-end budget in milliseconds, covering connecting, TLS, redirects, retries and the body. Redirects are limited: a redirect to a non-HTTP address and a replay of a request body from HTTPS to HTTP are refused. The SDK ignores proxy settings from the environment; only the `proxy` option counts. A custom CA certificate replaces the system store and is validated whenever it is set.

### Servers and authentication

If the document declares servers, the SDK exposes each one as a constant, and server variables are checked against their allowed values. Authentication is a set of authenticators, one per security scheme in the document: bearer token, HTTP basic, API key (header or query), OAuth 2 client credentials, authorization code, password and implicit flows, and OpenID Connect. `Client.withToken(url, token)` is the shortcut for the common bearer case. A `NO_AUTH` value means a request needs no credentials.

OAuth 2 and OpenID Connect share one token manager. It caches the access token, refreshes it sixty seconds before its declared expiry, uses the refresh token when the server issues one, and, when an API call is answered with 401, drops the access token and sends the request once more with a fresh one. A second 401 is raised as an error; a request with a stream or multipart body is not replayed, and API key, basic and bearer authenticators never replay. Credentials may be sent in the token request body or in a Basic header. Scopes are sent in the order the document declares them, and OpenID Connect always requests `openid`.

### Options and retries

An operation that has optional parameters takes an Options object. Every Options object also accepts three per-call settings that apply to that call only: `timeout`, `maxRetries` and `headers`. The `headers` setting adds request headers after the default headers; authentication headers are applied last and still win.

Retries are on by default: two retries, and `0` turns them off. A call is retried after a network error that happened before any response, and after HTTP 408, 409, 429 and 500 to 599 except 501. The wait starts at half a second, doubles up to eight seconds and is shortened by up to a quarter at random, and a `Retry-After` header (seconds or an HTTP date, capped at sixty seconds) replaces it. Retries share the one end-to-end timeout and never extend it, and a request whose body is a stream is never retried. The SDK adds a unique `X-Request-ID` header and a `User-Agent` of the form `<package>/<version> (<language>)` unless you set your own.

### Errors

Every SDK raises the same error for the same failure. All errors descend from one root, whose prefix is the `errorPrefix` option (default `OpenAPI`), and each one lives in the SDK's errors namespace:

```
OpenAPIError                      root
├── ApiError                      an HTTP response came back, or none did
│   ├── ClientError               4xx: BadRequest 400, Unauthorized 401, Forbidden 403,
│   │                             NotFound 404, Conflict 409, UnprocessableEntity 422,
│   │                             TooManyRequests 429
│   ├── ServerError               5xx: InternalServerError 500
│   └── NetworkError              no response: refused, DNS, TLS, reset (status 0)
│       └── NetworkTimeoutError   the request timed out (status 0)
├── SerializationError            any encode or decode failure, either direction
├── OAuth2ServerError             the token endpoint answered with an error
└── OAuth2TokenError              the token endpoint answered 2xx but unusably
```

An `ApiError` carries the status code, the response headers and body, and the request method and path (without the query string, which may hold a credential). A missing `responseHeaders` means no response arrived; an empty map means a response arrived with no headers. A mistake by the caller, such as a bad proxy URL, an empty credential or a server variable outside its allowed values, is not an SDK error: it raises the language's own argument error. The SDK has no cancellation API of its own. It uses the language's native one, and a cancelled call raises the platform's own cancellation signal and is never reported as a timeout.

### Models and serialisation

Each schema becomes a model, each enum a typed enum, each `oneOf` and `anyOf` a union (see [types](types.md)). The wire rules are the same everywhere: object keys are written in declaration order with `additionalProperties` after them; strings are written literally; a request body is never decoded and re-encoded when it is binary; a space is `%20` in a query string and `+` in a form body; and unknown enum values, discriminators that match nothing and malformed values are a `SerializationError`. Void responses are never decoded. The client does not enforce schema constraints such as `pattern` or `minimum`; the server owns validation.

Out of scope for every SDK: callbacks and webhooks, XML bodies, Link Objects and mutual TLS. OpenAPI 3.1 and 3.2 support is tracked as separate epics, and the public API shape is fixed now so that those features are additive.

## Where to go next

For the types each SDK uses, see [types](types.md). For what one language needs and accepts, see its page under [languages](languages/). For the choices that are settled and the work that is deferred, see [decisions](decisions.md). For the contributor rules in full, read the repository `AGENTS.md` and the scoped `AGENTS.md` for the generators and for the language you touch.

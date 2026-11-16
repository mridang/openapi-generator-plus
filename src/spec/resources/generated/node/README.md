# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated TypeScript SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **Node.js**: 22 LTS or newer (`engines.node >= 22`)
- **Package manager**: npm 10.x (bundled with Node 22) or pnpm
- **TypeScript**: 5.7 or newer (installed as a devDependency)

## Runtime support

The HTTP transport ships in two variants resolved automatically by the
package's [subpath imports](https://nodejs.org/api/packages.html#subpath-imports)
(`#transport`); both share a single `AbstractApiClient` built on Web-standard
`fetch` / `crypto` / `DecompressionStream`, so redirect handling, decompression,
charset decoding, and header injection are identical everywhere:

- **Node.js** resolves to the `undici`-backed transport, with full support for
  proxy routing, custom CA certificates, and TLS-verification toggling. `undici`
  is an `optionalDependency` — installed on Node, skipped elsewhere.
- **Deno, Bun, and edge runtimes** (Cloudflare Workers, Vercel Edge) resolve to a
  portable transport that uses only the Fetch API and imports no `undici` or
  `node:*` module, so the package loads on runtimes that provide `fetch` but no
  Node built-ins. Proxy / custom-CA / TLS-off are not expressible through the
  standard Fetch API there, so requesting one throws a `TypeError` rather
  than being silently ignored; everything else (including the `maxRedirects`
  cap) works unchanged. Browsers are best-effort only — the manual redirect loop
  relies on reading `Location` from a `redirect: 'manual'` response, which CORS
  makes opaque.

## Install

```bash
npm install
```

## Test

```bash
npm test
```

## Tooling

The package ships with a formatter, linter and static analyser, all
runnable via the matching npm scripts:

```bash
npm run format     # prettier (uses .prettierrc.json)
npm run lint       # eslint 9 flat config (eslint.config.js)
npm run typecheck  # tsc --noEmit (strict mode)
npm run build      # tsc (emit to ./dist)
```

## Package

- Name: `petstore-client`
- Version: `1.0.0`

## Caveats

### Decimal / `format: number` precision

JavaScript's only number type is a 64-bit IEEE-754 float, which can
exactly represent integers only up to `Number.MAX_SAFE_INTEGER`
(2^53 − 1 = 9 007 199 254 740 991). API responses containing a
`format: int64` value larger than this — Twitter / X IDs, Snowflake
IDs, Discord IDs, and similar 64-bit auto-increment IDs — are
**silently rounded** by `JSON.parse`.

If you call an endpoint that returns such an ID, treat the returned
number as opaque (do not do arithmetic on it) or fetch it as a string
when the API offers it. Fixing this language-level limitation would
require returning `bigint` instead of `number`, which breaks the
arithmetic operators (`+`, `<`, etc.) for every existing consumer.

Source: ECMA-262 §6.1.6.1.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### Schema constraints

The constraints `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,
`multipleOf`, `minItems` and `maxItems` are validated by the server. The
generated models do not check them: the class-transformer model layer has no
native constraint validation, so none of them is enforced client-side (only
Python validates them natively, through pydantic). If you want fast-fail
validation before the network round trip, plug in a JSON Schema validator
library.

`uniqueItems` arrays are sent in the natural order of the collection
(a `Set` keeps its insertion order), not sorted.

### SOCKS proxies

`TransportOptionsBuilder.proxy()` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### Per-call cancellation

Every operation takes a trailing `RequestOptions` object carrying an
`AbortSignal`; aborting it cancels that call:

```typescript
const controller = new AbortController();
const pending = api.someOperation(requiredArg, { signal: controller.signal });
controller.abort();
await pending; // rejects with the signal's reason (an AbortError by default)
```

Pass a client-level signal as the third constructor argument
(`new Client(authenticator, undefined, { signal })`) or with
`Configuration.builder().signal(signal)` to cancel every call made through
the client. The client-level and the per-call signals are merged: either one
cancels the call. Cancellation is distinct from the timeout, which still fails
with `NetworkTimeoutError`.

### Custom `fetch`

`TransportOptions.builder().fetch(myFetch).fetchOptions({ ... })` routes every
request through your own `fetch` implementation. The SDK still applies its
timeout, user agent, redirect policy and header handling on top, and never
closes anything you supply. Combining a custom `fetch` with `proxy`,
`caCertPath` or `verifySsl(false)` throws a `TypeError` at `build()`, because
those belong on the `fetch` you provide.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.

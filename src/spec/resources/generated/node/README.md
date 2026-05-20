#  SDK

Auto-generated TypeScript SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Install

```bash
npm install
```

## Test

```bash
npm test
```

## Package

- Name: ``
- Version: `1.0.0`

## Caveats

### Integer precision above 2^53

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

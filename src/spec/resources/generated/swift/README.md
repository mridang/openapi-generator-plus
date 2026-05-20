# PetstoreClient SDK

Auto-generated Swift SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Build

```bash
swift build
```

## Test

```bash
swift test
```

## Package

- Name: `PetstoreClient`

## Caveats

### Decimal / `format: number` precision

Foundation's `JSONDecoder` parses JSON numbers into `Double` for
`Decodable` model fields typed as `Double`/`Float`. `format: decimal`
/ `format: number` values are therefore stored as `Double`, so
monetary values lose exact decimal representation. `0.1 + 0.2` in
Swift is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed
fields. If you need exact decimal arithmetic, parse the raw response
body yourself with `JSONDecoder().userInfo[.useDecimal] = true` and
map the field to Foundation's `Decimal`.

`format: int64` is unaffected — Swift's `Int` is 64-bit on all
supported platforms and represents the full range without loss.

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

### Numeric / string constraint validation

OpenAPI keywords like `minLength`, `maxLength`, `minimum`, `maximum`,
`pattern`, `minItems`, `maxItems`, `uniqueItems`, `multipleOf` are
**not enforced** by this SDK. The server is the authoritative
validator; client-side enforcement is a DX nicety, not a correctness
requirement. If you want fast-fail validation before the network
round trip, plug in a JSON Schema validator library for your language.

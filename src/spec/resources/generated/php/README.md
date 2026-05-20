#  SDK

Auto-generated PHP SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Install

```bash
composer install
```

## Test

```bash
vendor/bin/phpunit
```

## Package

- Name: `PetstoreClient`

## Caveats

### Integer and decimal precision

Symfony's serializer (used internally for JSON deserialization)
downgrades `format: int64` values through PHP `float` before assigning
them to model fields. On 64-bit PHP this means values larger than
2^53 silently lose precision — the same IEEE-754 ceiling that bites
JavaScript and Dart. Snowflake / Twitter / Discord-style IDs above
9 007 199 254 740 991 are rounded.

`format: decimal` / `format: number` fields are likewise stored as
PHP `float`, so monetary values lose their exact decimal
representation. `0.1 + 0.2` in PHP is `0.30000000000000004` — do not
do arithmetic on prices, balances, or other money-typed fields. Pass
them through as strings via `BCMath` if you need exact arithmetic.

Fixing this end-to-end would require switching the wire layer to
emit JSON numbers from internal string/GMP representations, which
breaks `format: int64` schema validation against strict mock servers
(verified with Prism). Documented as a known limitation.

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

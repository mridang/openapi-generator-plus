# petstore_client SDK

Auto-generated Ruby SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Install

```bash
bundle install
```

## Test

```bash
bundle exec rake spec
```

## Gem

- Name: `petstore_client`
- Version: `1.0.0`

## Caveats

### Decimal / `format: number` precision

Ruby's stdlib `JSON.parse` returns `Float` for JSON numbers with a
decimal point. `format: decimal` / `format: number` values are
therefore stored as `Float`, so monetary values lose exact decimal
representation. `0.1 + 0.2` in Ruby is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed
fields. If you need exact decimal arithmetic, parse the raw response
body via `JSON.parse(body, decimal_class: BigDecimal)` and use
`BigDecimal` (stdlib `bigdecimal`) for the math.

`format: int64` is unaffected — Ruby's `Integer` is arbitrary-
precision (`Bignum`) and represents the full 64-bit range without
loss.

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

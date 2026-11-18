# Frequently asked questions

This page answers the questions people and agents ask most often about what the generator does and does not do. Each answer names the decision behind it. Settled decisions are in [decisions](decisions.md), and the Go and Node details are in [Go](languages/go.md) and [Node](languages/node.md).

## Does the generator produce a server?

No. Decision: this project generates client SDKs only. Server generation and webhook client generation are out of scope, because a client SDK does not need a server to be useful. Callbacks and webhooks are not generated in any of the twelve languages.

## Can I add struct tags such as `db` or `validate` to Go models?

No. Decision: the project does not add extra struct tags to Go models. Tags such as `db` and `validate` are database and server concerns, and this project generates client SDKs only.

## Can I choose my own Go initialisms, such as `Id` instead of `ID`?

No. Decision: custom initialisms are not a Go option. Initialisms are written in upper case (`URL`, `ID`, `HTTP`) in every generated Go SDK. To get other names, rename the schema or operation through the OpenAPI normaliser: the `RUN_SCRIPT` rule runs your own JavaScript transformation through GraalVM, and you set it with `--openapi-normalizer`.

## Are declared error responses returned as values?

Current behaviour, under review. A declared error response is returned to the caller as a typed error, and it is never returned as a value with a nil error. This is what the SDK does today, and the choice is not yet a settled decision.

## Do generated Go models have a `Hash` method?

Current behaviour, under review. A generated Go model has no `Hash` method. This is what the Go SDK does today, and the choice is not yet a settled decision.

## Does the SDK validate requests or responses against schema constraints?

Current behaviour, under review. The client does not check schema constraints such as `pattern`, length or range on requests or responses, because the server is the authority. This is what the SDK does today, and the choice is not yet a settled decision.

## Why does the Go SDK have its own duration type, and what does it look like on the wire?

Current behaviour, under review. The Go SDK wraps a duration in its own `models.Duration` type, a wrapper around `time.Duration`. On the wire a duration is the protobuf-JSON string, such as `"3600s"`, and not an ISO 8601 value. Whether that wire form should stay, or move to ISO 8601 in every language, is not yet decided.

## Under review

These questions are open. Each one links to its issue, and none is decided.

* [Typed response headers (#77)](https://github.com/mridang/openapi-generator-plus/issues/77).
* [Typed date and time values (#126)](https://github.com/mridang/openapi-generator-plus/issues/126).
* [OpenTelemetry spans and metrics (#108)](https://github.com/mridang/openapi-generator-plus/issues/108).
* [Streaming responses and uploads (#114)](https://github.com/mridang/openapi-generator-plus/issues/114).
* [The ogen parity list (#131)](https://github.com/mridang/openapi-generator-plus/issues/131).

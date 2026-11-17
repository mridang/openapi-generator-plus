# Opigen: Opinionated OpenAPI Codegen

Opigen generates client SDKs from an OpenAPI 3 specification in twelve languages: Java, Kotlin, C#, Node.js / TypeScript, Python, Ruby, PHP, Go, Rust, Swift, Dart and Elixir. It is built on OpenAPI Generator, but it emits only the source a client needs (models, operations, authentication, errors and a transport) plus a manifest, a README and a `SKILLS.md` reference for each SDK, instead of a full project with its own scaffolding. The SDKs are meant to be dropped into an existing code base.

The twelve SDKs are deliberately alike. They have the same entry class, the same way to configure a transport, the same error hierarchy, the same retry and timeout behaviour and the same wire format. They differ only where a language's own idiom demands it, so a team that uses more than one language reads the same API in each.

## How it works

Generation has two steps. First the specification is normalized: an optional set of rules standardizes the document so that every language sees the same input (JSON as the only content type, path filtering, handling of empty request bodies, and custom rules written in JavaScript). Then a language generator (`java-plus`, `node-plus` and so on) renders the SDK from templates. The SDK produced for a language is checked against a committed copy of the output, compiled, linted and exercised by tests against a mock server before a change is accepted.

The pipeline, the normalizer rules and the standard shape of a generated SDK are described in [docs/how-it-works.md](docs/how-it-works.md).

## Installation

A pre-built Docker image is published to the GitHub Container Registry and is the recommended way to run the generator.

```shell
docker pull ghcr.io/mridang/openapi-generator-plus:latest
```

To run the generator without Docker, fetch the OpenAPI Generator CLI jar and the `codegen-plus` jar and put both on the classpath:

```shell
java -cp "./openapi-generator-cli.jar:./codegen-plus.jar" \
  org.openapitools.codegen.OpenAPIGenerator generate \
  --input-spec spec.json --output client --generator-name java-plus
```

## Usage

Mount your working directory and pass the usual `generate` arguments:

```shell
docker run --rm -v "${PWD}:/local" ghcr.io/mridang/openapi-generator-plus:latest generate \
  --input-spec /local/spec.json \
  --generator-name java-plus \
  --output /local/client \
  --config /local/config.yml
```

The key arguments are:

* `--generator-name`: the generator to use. The names are in the table below.
* `--input-spec`: the path to your OpenAPI specification.
* `--output`: the directory the SDK is written to.
* `--config`: an optional YAML file of generator options. Each language accepts its own options, which are listed on its page.
* `--openapi-normalizer "RULE=VALUE,..."`: an optional string that configures the normalizer rules.

Two options are accepted by every generator:

| Option | Default | Description |
|---|---|---|
| `clientClassName` | `Client` | The name of the entry class that users instantiate. Setting `clientClassName: Zitadel` produces a class named `Zitadel`. |
| `errorPrefix` | `OpenAPI` | The prefix of the root error type that every SDK error descends from. Each language appends its own suffix, so the default produces `OpenAPIException` (Java, Kotlin, C#, PHP, Python, Dart) or `OpenAPIError` (Go, Node, Ruby, Rust, Swift, Elixir). File names and snake-case identifiers follow the prefix. |

## Languages

Each language has a page with its supported versions, type mapping, dependencies, options and known limits. The minimum version is read from the build manifest of the generated example SDK; the language page is the one place that states it in full. The package name is the example the generator produces from the petstore specification used in this repository, and the option that sets it is shown in brackets.

| Language | Generator | Generated package name (option) | Minimum version | Page |
|---|---|---|---|---|
| Java | `java-plus` | `petstore-client` (`artifactId`) | Java 21 | [docs/languages/java.md](docs/languages/java.md) |
| Kotlin | `kotlin-plus` | `petstore-client` (`artifactId`) | Kotlin 2.2 on JVM 17 | [docs/languages/kotlin.md](docs/languages/kotlin.md) |
| C# | `csharp-plus` | `PetstoreClient` (`packageName`) | .NET 8.0, C# 12 | [docs/languages/csharp.md](docs/languages/csharp.md) |
| Node.js / TypeScript | `node-plus` | `petstore-client` (`npmName`) | Node.js 22 | [docs/languages/node.md](docs/languages/node.md) |
| Python | `python-plus` | `petstore-client` (`projectName`) | Python 3.10 | [docs/languages/python.md](docs/languages/python.md) |
| Ruby | `ruby-plus` | `petstore_client` (`gemName`) | Ruby 3.2 | [docs/languages/ruby.md](docs/languages/ruby.md) |
| PHP | `php-plus` | `openapi/petstoreclient` (`packageName`) | PHP 8.3 | [docs/languages/php.md](docs/languages/php.md) |
| Go | `go-plus` | `petstore` (`packageName`) | Go 1.26 | [docs/languages/go.md](docs/languages/go.md) |
| Rust | `rust-plus` | `petstore` (`packageName`) | Rust 1.85 | [docs/languages/rust.md](docs/languages/rust.md) |
| Swift | `swift-plus` | `PetstoreClient` (`projectName`) | Swift 6.0, macOS 12, iOS 15 | [docs/languages/swift.md](docs/languages/swift.md) |
| Dart | `dart-plus` | `petstore_client` (`pubName`) | Dart 3.11 | [docs/languages/dart.md](docs/languages/dart.md) |
| Elixir | `elixir-plus` | `petstore_client` (`packageName`) | Elixir 1.17 | [docs/languages/elixir.md](docs/languages/elixir.md) |

## More documentation

* [docs/how-it-works.md](docs/how-it-works.md): the pipeline, the normalizer, the generated SDK format and the checks a change must pass.
* [docs/types.md](docs/types.md): how every OpenAPI type and format maps to a type in each language.
* [docs/decisions.md](docs/decisions.md): the behaviour all twelve SDKs share, and the work that is still open.

## Contributing

Contributions are welcome. If you find a bug or have a suggestion, open an issue or submit a pull request. Read [AGENTS.md](AGENTS.md) first: it holds the working rules, including the requirement to update the documentation in the same change.

## License

Apache License 2.0, copyright 2024 Mridang Agarwalla.

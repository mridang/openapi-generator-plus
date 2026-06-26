# Opigen: Opinionated OpenAPI Codegen

A toolkit for normalizing OpenAPI specifications and generating clean, minimal, and opinionated client SDKs.

This project was created to address the verbosity and extensive boilerplate often produced by standard code generators. Instead of creating a complete project with build scripts, documentation, and tests, these tools focus on producing only the essential source code: the models and the API interfaces. This allows developers to integrate the generated code seamlessly into their existing projects without unnecessary clutter.

The core philosophy is to enforce a set of "sane defaults" based on modern development practices, ensuring the output is immediately useful and easy to maintain.

### Features

The toolkit consists of two main components: an OpenAPI normalizer and a suite of custom code generators.

**OpenAPI Normalizer**

Before code generation, the tool can preprocess and standardize your OpenAPI v3 specification. This step ensures consistency and can fix common issues, leading to more reliable code generation downstream. Key normalization features include:

* **JSON-Only Content**: Enforces `application/json` as the only allowed content type across the entire API specification, removing other defined types.
* **Path Filtering**: Selectively includes or excludes API paths using regular expressions, allowing you to generate SDKs for just a subset of your API.
* **Empty Request Body Handling**: Automatically processes operations with empty request bodies, either by removing them or tagging them based on your configuration.
* **Scriptable Custom Rules**: Hook into your own JavaScript-based transformations via GraalVM.

**Opinionated Code Generators**

A set of custom generators that produce lean, modern clients for 12 languages. They are "opinionated" in that they make specific technology choices and generate only what is necessary. All generators share a consistent architecture: a `BaseApi` for request construction, a `DefaultApiClient` for HTTP transport, an `ObjectSerializer` for JSON handling, a `ValueSerializer` for OpenAPI style/explode serialization, and `TransportOptions` for client configuration.

| Generator | Language | HTTP Client | Serialization |
|-----------|----------|-------------|---------------|
| `java-plus` | Java | `java.net.http.HttpClient` | Jackson |
| `kotlin-plus` | Kotlin | OkHttp | kotlinx.serialization |
| `csharp-plus` | C# | `System.Net.Http.HttpClient` | `System.Text.Json` |
| `node-plus` | Node.js / TypeScript | Fetch API (undici) | Native JSON |
| `python-plus` | Python | urllib3 | Native JSON |
| `ruby-plus` | Ruby | Faraday | Native JSON |
| `php-plus` | PHP | Symfony HttpClient | Native JSON |
| `go-plus` | Go | `net/http` | `encoding/json` |
| `rust-plus` | Rust | reqwest | serde |
| `swift-plus` | Swift | URLSession | `JSONEncoder`/`JSONDecoder` |
| `dart-plus` | Dart | `package:http` | `dart:convert` |
| `elixir-plus` | Elixir | Req | Jason |

## Installation

A pre-built Docker image is available from the GitHub Container Registry (GHCR) and is the recommended way to run the tool.

First, pull the latest image:
```shell
docker pull ghcr.io/mridang/openapi-generator-plus:latest
````

You can then run the generator by mounting your current directory and passing the arguments directly to the container. For example:

```shell
docker run --rm -v "${PWD}:/local" ghcr.io/mridang/openapi-generator-plus:latest generate --input-spec /local/spec.json --output /local/output --generator-name java-plus
```

#### Advanced Usage

If you prefer to run the application directly, you can fetch the required JARs from their respective repositories and build the classpath manually.

```shell
wget -O artifact.jar "https://repo1.maven.org/maven2/com/example/my-artifact/1.0.0/my-artifact-1.0.0.jar"

java -cp "./openapi-generator-cli.jar:./codegen-plus.jar" \
  org.openapitools.codegen.OpenAPIGenerator generate \
# ... add other arguments here
```

## Usage

Regardless of whether you use Docker or run the JARs directly, the core of this tool is the generate command. It requires a set of arguments to control the code generation process.

The generate command accepts the following key arguments:

* `--generator-name=<generator_name>`: Specifies which custom generator to use (e.g., ruby-plus, java-plus).
* `--input-spec=<path_to_spec.json>`: The path to your OpenAPI specification file.
* `--output=<output_directory>`: The directory where the generated code will be saved.
* `--config=<config.yml>`: An optional path to a configuration file for generator-specific options. See the examples below.
* `--openapi-normalizer "RULE=VALUE,..."`: An optional string to configure the OpenAPI normalizer rules.

### Common Configuration Options

All generators support the following option in the config file:

| Option | Default | Description |
|---|---|---|
| `clientClassName` | `Client` | The name of the main entrypoint class that users instantiate. For example, setting `clientClassName: Zitadel` produces a class named `Zitadel` instead of `Client`. |

## Configuration

Here’s how you can use a simple YAML config file to drive the generator for each language. Just create a file with the content below and point to it with the `--config` flag.

### Generate a Java Client

For a Java client, your `config.yml` might look like this:

```yaml
groupId: com.testme
invokerPackage: com.testme
apiPackage: com.testme.api
modelPackage: com.testme.model
artifactId: client
artifactVersion: 0.0.1
library: apache-httpclient
dateLibrary: java8
developerName: Opigen
developerOrganization: example.com
developerOrganizationUrl: [https://example.com/](https://example.com/)
developerEmail: developer@example.com
licenseName: Apache License, Version 2.0
licenseUrl: [https://www.apache.org/licenses/LICENSE-2.0](https://www.apache.org/licenses/LICENSE-2.0)
serializationLibrary: jackson
disallowAdditionalPropertiesIfNotPresent: false
useOneOfDiscriminatorLookup: true
hideGenerationTimestamp: true
```

Assuming you have your OpenAPI specification in a file named `spec.json` and the following configuration saved as `config.yml` in your current directory.
To generate the client, run the following command with your `config.yml`. This config file sets up all the important details for a Maven project, like the `groupId` and `artifactId`, along with developer info and our choice of libraries.

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="java-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate a Node.js / TypeScript Client

To generate a modern TypeScript client, you could use a `config.yml` like this:

```yaml
npmName: "@opigen/client"
npmVersion: 0.0.1
supportsES6: true
ensureUniqueParams: true
modelPropertyNaming: original
disallowAdditionalPropertiesIfNotPresent: false
withInterfaces: false
useSingleRequestParameter: true
enumUnknownDefaultCase: true
importFileExtension: ".js"

```

Assuming you have your OpenAPI specification in a file named `spec.json` and the following configuration saved as `config.yml` in your current directory.
You can generate the client by running the command below with your `config.yml`. This setup is perfect for a modern Node.js project because it configures the NPM package details and ensures the output uses modern features like ES6 and `.js` file extensions for imports, which is great for ES Modules.

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="node-plus" \
    --output="/local/client" \
    --config="/local/config.yml"

```

### Generate a PHP Client

For PHP, here's a sample `config.yml`:

```yaml
packageName: opigen/client
variableNamingConvention: camelCase
invokerPackage: Opigen\Client
disallowAdditionalPropertiesIfNotPresent: false

```

Assuming you have your OpenAPI specification in a file named `spec.json` and the following configuration saved as `config.yml` in your current directory.
Use the following command along with your `config.yml` to generate the client. The config here sets up the project for Composer by defining the `packageName` and the root PHP namespace (`invokerPackage`), and also switches the code style to `camelCase` to match common PHP conventions.

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="php-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate a Python Client

Here's how you might configure the Python generator.

```yaml
projectName: opigen-client
packageName: opigen_client
packageVersion: 0.0.1
library: urllib3
packageUrl: [https://github.com/opigen/client](https://github.com/opigen/client)
disallowAdditionalPropertiesIfNotPresent: false
useOneOfDiscriminatorLookup: true

```

Assuming you have your OpenAPI specification in a file named `spec.json` and the following configuration saved as `config.yml` in your current directory.
Run the generator with the following command, pointing to your `config.yml`. This config provides the necessary details for a Python package, like the `projectName` and `packageName`, which will be used to create the `setup.py` file.

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="python-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate a Ruby Client

And for Ruby, your `config.yml` could be:

```yaml
projectName: opigen-client
gemName: opigen-client
gemVersion: 0.0.1
library: typhoeus
gemAuthorEmail: developer@example.com
gemAuthor: Opigen
gemHomepage: [https://example.com/](https://example.com/)
gemLicense: Apache-2.0
moduleName: Opigen::Client
disallowAdditionalPropertiesIfNotPresent: false

```

Assuming you have your OpenAPI specification in a file named `spec.json` and the following configuration saved as `config.yml` in your current directory.
To generate the Ruby client, execute the command below using your `config.yml`. This file is all about setting up the metadata for the Ruby gem, defining everything that will go into the `.gemspec` file, from the gem's name and author to the top-level module (`moduleName`) that will namespace all the generated code.

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="ruby-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate a C# Client

```yaml
packageName: PetstoreClient
sourceFolder: src
```

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="csharp-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate a Go Client

```yaml
packageName: petstore
```

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="go-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate a Kotlin Client

```yaml
groupId: com.example
invokerPackage: com.example.petstore
apiPackage: com.example.petstore.api
modelPackage: com.example.petstore.models
```

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="kotlin-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate a Rust Client

```yaml
packageName: petstore
```

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="rust-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate a Swift Client

```yaml
projectName: PetstoreClient
```

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="swift-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate a Dart Client

```yaml
pubName: petstore_client
pubVersion: 1.0.0
```

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="dart-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

### Generate an Elixir Client

```yaml
packageName: petstore_client
```

```shell
docker run --rm \
  --volume="${PWD}:/local" \
  my-generator generate \
    --input-spec="/local/spec.json" \
    --generator-name="elixir-plus" \
    --output="/local/client" \
    --config="/local/config.yml"
```

## Caveats

The following OAS 3.0 features are deliberately out of scope for this project:

| Feature | Notes |
|---|---|
| Callbacks | Silently ignored |
| Links (`links:` in responses) | Silently ignored |
| Webhooks | OAS 3.1 feature; not targeted |
| Mutual TLS (mTLS) | Not implemented |
| XML serialization (`xml:` object) | JSON-only; XML is not supported |

### Known Limitations

The following OAS 3.0 features are partially supported or have known constraints:

| Feature | Notes |
|---|---|
| `allowReserved` on query params | Not exposed on CodegenParameter upstream |
| Default response / wildcard status codes (`2XX`, `4XX`) | Current exception hierarchy adequate |
| Typed response headers (schema on Header Object) | Headers returned as `Map<String, String>` |
| `not` composition | Not supported by upstream framework |
| Advanced multipart encoding (headers, style, explode in Encoding Object) | Only `contentType` used in encoding object |
| Path-level `servers` (inherited by all ops in a path) | Upstream framework limitation |

## Contributing

Contributions are welcome! If you find a bug or have suggestions for improvement,
please open an issue or submit a pull request.

## License

Apache License 2.0 © 2024 Mridang Agarwalla

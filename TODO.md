# TODO

## OAS3 Feature Gaps

### Cookie Parameters (`in: cookie`)

OpenAPI 3.x supports `in: cookie` on operation parameters (alongside `query`, `header`,
and `path`). Currently, the `UnsupportedFeaturesValidator` explicitly rejects any operation
that uses cookie parameters, throwing a `RuntimeException` during generation.

Cookie-based **authentication** (apiKey security schemes with `in: cookie`) is already
supported across all 6 languages. What's missing is support for regular operation-level
cookie parameters — encoding values into the `Cookie` request header.

**Affected files:**



- `UnsupportedFeaturesValidator.java` — remove the cookie parameter rejection
- All 6 `base_api.mustache` templates — add cookie header assembly from cookie parameters
- `ValueSerializer` in each language — may need a `toCookieValue` method

---

### `application/x-www-form-urlencoded` Request Bodies

The `UnsupportedFeaturesValidator` explicitly rejects operations with
`application/x-www-form-urlencoded` content type. This is a valid OAS3 content type
used for simple form submissions (login forms, token endpoints, etc.).

**Affected files:**
- `UnsupportedFeaturesValidator.java` — remove the form-urlencoded rejection
- All 6 `base_api.mustache` templates — add form-urlencoded body serialization
- `DefaultApiClient` in each language — handle URL-encoded body encoding

---

### `text/plain` Request Bodies

The `OnlyAllowJsonRule` silently filters out `text/plain` from request body content
types during spec normalization. No error is thrown — the content type is just removed.
This means APIs that accept plain text (log submissions, markdown content, etc.) lose
that content type without warning.

Response-side `text/plain` is already handled correctly (raw string returned).

**Affected files:**
- `OnlyAllowJsonRule.java` — allow `text/plain` through (or make it configurable)
- All 6 `base_api.mustache` templates — pass string body as-is for `text/plain`
- `DefaultApiClient` in each language — send raw string body without JSON serialization

---

### `deepObject` Query Parameter Style

OAS3 supports `style: deepObject` on query parameters, which serializes objects as
`filter[status]=active&filter[color]=red` instead of a JSON string. This is the only
non-default style commonly used in real APIs (`label` and `matrix` are academic spec
features that virtually no API uses, and `explode` for arrays is already handled via
`collectionFormat`).

Currently the codegen does not extract the `style` property from OpenAPI Parameter
objects, so `deepObject` parameters are serialized incorrectly as flat values.

**Step 1 — Codegen: extract `style` from parameters**
- In `AbstractBetterCodegen.java` (or the per-language codegen classes), read
  `parameter.getStyle()` during `fromOperation()` processing
- Add it as a vendor extension or template variable (e.g., `paramStyle`) on the
  `CodegenParameter` so it's available in mustache templates

**Step 2 — Templates: pass `style` to `ValueSerializer`**
- In all 6 `api.mustache` templates, where query parameters call
  `ValueSerializer.serialize(value, "query", schemaType, collectionFormat)`, pass
  the `paramStyle` as an additional argument (or replace `collectionFormat` when
  `style` is present, since `deepObject` supersedes collection format)

**Step 3 — `ValueSerializer`: implement `deepObject` encoding**
- In all 6 `value_serializer.mustache` templates, when `style == "deepObject"` and the
  value is a map/object, iterate over entries and emit `paramName[key]=urlEncode(value)`
  pairs instead of the default flat serialization
- Non-map values with `deepObject` should fall back to default behavior

---

### Response Headers on Success (`withHttpInfo` pattern)

OAS3 allows named response headers to be defined per response. The `ApiResponse` class
in all 6 languages already has a `headers` field, but response headers are only accessible
via exceptions (error responses). On successful 2xx responses, headers are silently
discarded and not returned to the caller.

Every API method should have a companion `withHttpInfo` variant that returns an
`ApiResponse<T>` (or language equivalent) containing the deserialized body, status code,
and response headers. The existing method stays as-is for convenience (returns just `T`).

**Examples by language:**
- Java: `Pet getPet(id)` + `ApiResponse<Pet> getPetWithHttpInfo(id)`
- Python: `get_pet(id) -> Pet` + `get_pet_with_http_info(id) -> ApiResponse[Pet]`
- Ruby: `get_pet(id) -> Pet` + `get_pet_with_http_info(id) -> ApiResponse`
- PHP: `getPet($id): Pet` + `getPetWithHttpInfo($id): ApiResponse`
- Node: `getPet(id): Promise<Pet>` + `getPetWithHttpInfo(id): Promise<ApiResponse<Pet>>`
- C#: `GetPetAsync(id): Task<Pet>` + `GetPetWithHttpInfoAsync(id): Task<ApiResponse<Pet>>`

**Step 1 — `ApiResponse<T>` already exists in all 6 languages** (with `statusCode`,
`headers`, `data` fields). No changes needed to the response class.

**Step 2 — Refactor `base_api` to return `ApiResponse<T>` internally**
- The internal `invokeApi` method should always build and return `ApiResponse<T>` with
  headers populated from the HTTP response
- Currently headers are discarded on 2xx; instead, capture them into `ApiResponse`

**Step 3 — Generate `withHttpInfo` variants in `api.mustache`**
- For each operation, generate two methods: one that returns `T` (calls the other and
  extracts `.data`), and one that returns `ApiResponse<T>`
- The `withHttpInfo` variant is the "real" method; the convenience method delegates to it

**Affected files:**
- All 6 `base_api.mustache` templates — return `ApiResponse<T>` from `invokeApi`
- All 6 `api.mustache` templates — generate `withHttpInfo` method per operation

---

### `readOnly` / `writeOnly` Properties

OAS3 supports `readOnly` (include in responses, exclude from requests) and `writeOnly`
(include in requests, exclude from responses) on schema properties. Currently both are
treated as regular properties — they are serialized and deserialized unconditionally.

**Affected files:**
- Codegen classes — extract readOnly/writeOnly flags
- Model templates — skip readOnly fields during request serialization, skip writeOnly
  fields during response deserialization

---

### User-Agent Header

The new clients have `TransportOptions.userAgent` but it defaults to `null`. There is no
automatic User-Agent generation with library name, version, language, OS, or architecture.

Consider generating a default User-Agent in each language that includes the package name
and version (from the OpenAPI spec `info` block), the language name/version, and OS/arch.
E.g. `petstore-client/1.0.0 (lang=java; lang_version=17.0.1; os=linux; arch=amd64)`

**Affected files:**
- All 6 `transport_options` or `default_api_client` templates — add a default User-Agent
  string built from runtime info

---

### `uniqueItems: true` on Arrays

OAS3 supports `uniqueItems: true` on array schemas, indicating the array should contain
only unique elements. Currently all arrays generate `List` (Java), `list` (Python),
`Array` (Ruby/Node/PHP), regardless of this flag. Java even has `set` → `LinkedHashSet`
in its `instantiationTypes` map, but the codegen never switches to it.

**Affected files:**
- Codegen classes — detect `uniqueItems: true` and switch type from List to Set
- Type mappings in each language (e.g., Java `LinkedHashSet`, Python `Set`, C# `HashSet`,
  Ruby `Set`, Node `Set`, PHP `array` with unique constraint)

---

### `encoding` Object on Multipart Parts (low priority)

OAS3 allows per-part `encoding` in `multipart/form-data` request bodies to specify
`contentType`, `headers`, `style`, and `explode` for individual parts. Currently multipart
parts use hardcoded content types (`application/octet-stream` for files,
`application/json` for objects). Per-part encoding metadata from the spec is ignored.

**Affected files:**
- Codegen classes — extract `encoding` objects from `MediaType.getEncoding()`
- All 6 `api.mustache` templates — pass encoding metadata per form parameter
- `DefaultApiClient` in each language — apply per-part content type and headers

---

### `content` on Parameters (low priority)

OAS3 allows a parameter to use `content` (with a media type key like `application/json`)
instead of `schema` for complex serialization. This is used when a query parameter should
be a JSON-encoded string (e.g., `?filter={"status":"active"}`). Currently all parameters
are assumed to use `schema`; `parameter.getContent()` is never read.

**Affected files:**
- Codegen classes — detect `parameter.getContent()` and extract the media type + schema
- All 6 `api.mustache` templates — JSON-serialize the parameter value when content type
  is `application/json`
- `ValueSerializer` — add a content-aware serialization path

---

### Default and Wildcard Response Status Codes (low priority)

OAS3 supports a `default` response (catches any undeclared status code) and wildcard
patterns like `2XX`, `4XX`, `5XX` with different schemas per range. Currently all 2xx
responses are deserialized identically (single return type), and undeclared status codes
fall through to a generic exception. The exception hierarchy already maps specific codes
(400, 401, 403, 404, 409, 422, 500) to named exceptions.

**Affected files:**
- Codegen classes — handle `default` and wildcard response codes
- All 6 `base_api.mustache` templates — add fallback deserialization for default/wildcard
  response schemas

---

### `additionalProperties` Consistency (low priority)

Objects with `additionalProperties` (free-form key-value pairs beyond declared properties)
are only fully supported in the Python generator (`additional_properties: Dict[str, Any]`).
Java, C#, PHP, Ruby, and Node have no explicit free-form property map on generated models.

**Affected files:**
- Model templates for Java, C#, PHP, Ruby, Node — add a `Map<String, Object>` (or
  language equivalent) field when `additionalProperties` is true or has a schema

---

### `default` Values on Properties (low priority)

Default values from the schema (`default: "available"`) are only fully applied in C#.
Java has the template mechanism (`{{#defaultValue}}`) but the codegen doesn't populate it.
Python, PHP, Ruby, and Node do not initialize properties with their spec-defined defaults.

**Affected files:**
- Codegen classes — ensure `defaultValue` is populated on `CodegenProperty`
- Model templates for Python, PHP, Ruby, Node — use `{{#defaultValue}}` to initialize
  properties with their spec-defined defaults

---

### Per-Operation Servers (low priority)

OAS3 allows `servers` to be overridden at the path or operation level, enabling different
base URLs per operation (e.g., file uploads go to a CDN, auth goes to an identity
provider). Currently `AbstractBetterCodegen.processServers()` only reads global servers
from `openAPI.getServers()`. `operation.getServers()` is never called.

**Affected files:**
- `AbstractBetterCodegen.java` — read `operation.getServers()` and pass to templates
- All 6 `api.mustache` templates — allow per-operation server URL override
- All 6 `base_api.mustache` templates — accept optional server URL parameter in `invokeApi`

---

### `example` / `examples` in Generated Docs (low priority)

OAS3 supports `example` and `examples` on parameters, schemas, and media types. These
values are parsed from the spec but silently discarded during code generation — they
never appear in generated docstrings, Javadoc, JSDoc, or comments.

**Affected files:**
- All 6 `api.mustache` templates — include `{{example}}` in parameter docstrings
- All 6 model templates — include property examples in field documentation

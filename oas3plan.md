# Plan: Fix remaining OAS 3.0 feature gaps

## Context

An audit revealed several OAS 3.0 features that are parsed by the upstream openapi-generator framework (7.14.0) but silently ignored in generated client code. The upstream `CodegenParameter` already exposes fields like `style`, `isExplode`, `isDeepObject`, `isMatrix`, `isAllowEmptyValue`, and `CodegenModel` exposes `additionalPropertiesType` — but none of these are used in our templates today.

Additionally, the serializer architecture has a design flaw: **all 6 languages** have two classes — `ValueSerializer` and `ObjectSerializer` — that both contain identical scalar-to-string conversion logic. Every `ValueSerializer` has a private `stringify()` method that is a copy-paste of the scalar conversion logic already present in ObjectSerializer's `toPathValue`/`toQueryValue`/`toHeaderValue`/`toFormValue` methods. This duplication needs to be fixed before adding new style serialization, because the new `serializeStyled()` method should only handle transport formatting and delegate type conversion to a single source of truth.

This plan uses **only native CodegenParameter/CodegenModel fields** — no vendor extensions. No inline comments in generated code.

---

## Summary

| # | Gap | Action |
|---|-----|--------|
| 0 | Unify serializer architecture (remove duplicated `stringify()` from ValueSerializer) | **Refactor** |
| 1 | Parameter styles (matrix, label, spaceDelimited, pipeDelimited) + `explode: false` | **Implement** |
| 2 | `allowEmptyValue` on query params | **Implement** |
| 3 | Per-operation servers | **Implement** |
| 4 | `additionalProperties` with typed schema | **Implement** |
| 5 | `refreshUrl` on OAuth flows | **Implement** |
| 6 | Runtime server variable overrides via Configuration | **Implement** |

### Deferred (add to README)

| Gap | Reason |
|-----|--------|
| `allowReserved` on query params | Not on CodegenParameter upstream |
| Default response / wildcard status codes (`2XX`, `4XX`) | Current exception hierarchy adequate |
| Typed response headers (schema on Header Object) | Large architectural change, marginal value |
| `not` composition | Not supported by upstream framework |
| Advanced multipart encoding (headers, style, explode in Encoding Object) | Complex, basic multipart works |
| Path-level `servers` (inherited by all ops in a path) | Upstream framework limitation |
| `examples` (plural) on Parameter/Media Type | Documentation-only, not exposed by framework |
| Schema `title` field | Documentation-only |
| Info `contact`, `license`, `termsOfService` | Metadata-only, no runtime impact |
| Security Scheme `description` | Documentation-only |
| Path Item `summary` / `description` | Documentation-only, not exposed by framework |

### OAS 3.0 coverage verification

With the 7 functional gaps above implemented and the above items deferred, **every field of every object in the OAS 3.0.3 specification** is accounted for — either already supported, being implemented in this plan, explicitly deferred with rationale, or excluded by user decision (schema validation constraints).

---

## Execution order: tests first, then implementation

For each gap:
1. Add test cases to the OpenAPI spec (if applicable)
2. Write failing tests in the test templates (matching existing test patterns exactly)
3. Implement the feature in the source templates
4. Regenerate and verify tests pass

---

## Gap 0: Unify serializer architecture

### 0.1 Problem

Both `ValueSerializer` and `ObjectSerializer` exist in **all 6 languages**. Both contain identical scalar-to-string conversion logic.

**Current duplicated `stringify()` in each ValueSerializer:**

| Language | File | Method | Line numbers | Logic |
|----------|------|--------|-------------|-------|
| Java | `templates/java/value_serializer.mustache` | `private static String stringify(Object)` | 136-147 | Boolean → "true"/"false", TemporalAccessor → ISO_OFFSET_DATE_TIME, Date → StdDateFormat, fallback → String.valueOf() |
| Node | `templates/node/value_serializer.mustache` | `private static stringify(value: unknown): string` | 76-87 | null/undefined → '', boolean → "true"/"false", Date → toISOString(), fallback → String() |
| Python | `templates/python/value_serializer.mustache` | `@classmethod _stringify(cls, value)` | 82-99 | None → '', bool → "true"/"false", datetime → isoformat(), date → isoformat(), fallback → str() |
| Ruby | `templates/ruby/value_serializer.mustache` | `private_class_method def self.stringify(value)` | 68-79 | nil → '', TrueClass/FalseClass → "true"/"false", Time/DateTime → strftime('%Y-%m-%dT%H:%M:%S.%L%z'), fallback → to_s |
| PHP | `templates/php/value_serializer.mustache` | `private static function stringify(mixed $value): string` | 102-129 | null → '', bool → "true"/"false", DateTimeInterface → format(ATOM), int/float → (string), string → pass-through, SplFileObject → getRealPath(), fallback → '' |
| C# | `templates/csharp/value_serializer.mustache` | `private static string Stringify(object? value)` | 99-109 | null → "", bool → "true"/"false", DateTimeOffset → ToString("o"), DateTime → ToString("o"), fallback → ToString() ?? "" |

**Identical logic already in each ObjectSerializer (repeated 4 times each — in toPathValue, toQueryValue, toHeaderValue, toFormValue):**

| Language | File | Methods with duplicate scalar logic |
|----------|------|-------------------------------------|
| Java | `templates/java/object_serializer.mustache` | `toPathValue()` (96-110), `toQueryValue()` (121-155), `toHeaderValue()` (163-184), `toFormValue()` (192-206) |
| Node | `templates/node/object_serializer.mustache` | `toPathValue()` (76-87), `toQueryValue()` (97-116), `toHeaderValue()` (124-138), `toFormValue()` (146-157) |
| Python | `templates/python/object_serializer.mustache` | `to_path_value()` (163-171), `to_query_value()` (174-196), `to_header_value()` (199-209), `to_form_value()` (212-220) |
| Ruby | `templates/ruby/object_serializer.mustache` | `to_path_value()` (69-75), `to_query_value()` (79-99), `to_header_value()` (102-115), `to_form_value()` (118-129) |
| PHP | `templates/php/object_serializer.mustache` | `toString()` (102-121) shared by `toPathValue()` (276-279), `toQueryValue()` (287-305), `toHeaderValue()` (310-317), `toFormValue()` (342-348) |
| C# | `templates/csharp/object_serializer.mustache` | `ToPathValue()` (54-63), `ToQueryValue()` (68-103), `ToHeaderValue()` (108-132), `ToFormValue()` (137-146) |

**The correct responsibility split:**
- **ObjectSerializer**: ALL type conversion (scalar → string). Owns the canonical `stringify()` method. Also owns JSON body serialization/deserialization.
- **ValueSerializer**: Transport formatting ONLY — URL encoding for path params, collection format joining (csv/ssv/tsv/pipes/multi), deep object expansion, and (after Gap 1) style-specific formatting (matrix/label/pipe/space). ValueSerializer calls ObjectSerializer for converting individual values to strings.

### 0.2 What ValueSerializer does besides stringify

Each ValueSerializer's `serialize()` method does three things beyond stringify:
1. **Null handling** — returns `null` for query (to omit the param) vs `""` for other locations
2. **Collection handling** — joins arrays with delimiters based on collectionFormat (csv/ssv/tsv/pipes/multi) for query, comma for header
3. **URL encoding** — encodes path values (Java: `URLEncoder.encode` + `%20` fix, Node: `encodeURIComponent`, Python: `quote(safe='')`, Ruby: `CGI.escape`, PHP: `rawurlencode`, C#: `Uri.EscapeDataString`)

These are transport concerns and should stay in ValueSerializer. Only the `stringify()` call on individual scalar items should delegate to ObjectSerializer.

### 0.3 Write tests (before implementation)

The existing ValueSerializer and ObjectSerializer tests serve as regression tests for the refactoring (external behavior of `ValueSerializer.serialize()` does not change). However, the new public `ObjectSerializer.stringify()` method needs direct unit test coverage in all 6 languages.

**Files to modify (6 ObjectSerializer test templates):**

| Language | File |
|----------|------|
| Java | `src/main/resources/templates/java/test/ObjectSerializerTest.mustache` |
| Node | `src/main/resources/templates/node/test/object-serializer.test.ts` |
| Python | `src/main/resources/templates/python/test/test_object_serializer.mustache` |
| Ruby | `src/main/resources/templates/ruby/test/object_serializer_spec.mustache` |
| PHP | `src/main/resources/templates/php/test/ObjectSerializerTest.mustache` |
| C# | `src/main/resources/templates/csharp/test/ObjectSerializerTest.mustache` |

Add a new `"stringify"` test group with these test cases (6 tests per language):

| Test name | Input | Expected |
|-----------|-------|----------|
| null returns empty string | `stringify(null)` | `""` |
| boolean true returns lowercase string | `stringify(true)` | `"true"` |
| boolean false returns lowercase string | `stringify(false)` | `"false"` |
| integer returns string representation | `stringify(42)` | `"42"` |
| date-time returns ISO 8601 string | `stringify(<language-specific datetime>)` | ISO 8601 formatted string (e.g., `"2024-01-15T10:30:00..."`) |
| plain string passes through unchanged | `stringify("hello")` | `"hello"` |

### 0.4 Implementation

For each of the 6 languages:

**Step A — Make ObjectSerializer's stringify public:**

Currently ObjectSerializer has the scalar conversion logic inline in each `toXValue()` method (or in PHP's case, in a private `toString()` helper). Extract it as a single public static method:

| Language | New method signature | File |
|----------|---------------------|------|
| Java | `public static String stringify(@Nullable Object value)` | `templates/java/object_serializer.mustache` |
| Node | `static stringify(value: unknown): string` | `templates/node/object_serializer.mustache` |
| Python | `@classmethod def stringify(cls, value: Any) -> str` | `templates/python/object_serializer.mustache` |
| Ruby | `def self.stringify(value)` | `templates/ruby/object_serializer.mustache` |
| PHP | Make existing `toString()` public, or add `public static function stringify(mixed $value): string` | `templates/php/object_serializer.mustache` |
| C# | `public static string Stringify(object? value)` | `templates/csharp/object_serializer.mustache` |

Then refactor each `toPathValue`/`toQueryValue`/`toHeaderValue`/`toFormValue` to call `stringify()` instead of duplicating the logic inline. This eliminates the duplication WITHIN ObjectSerializer too (currently the boolean/date/number check is copy-pasted 4 times in each language's ObjectSerializer).

**Step B — Remove ValueSerializer's private stringify, delegate to ObjectSerializer:**

| Language | File | Change |
|----------|------|--------|
| Java | `templates/java/value_serializer.mustache` | Delete lines 136-147 (`private static String stringify`). Add `import` for ObjectSerializer. Replace all `stringify(x)` calls with `ObjectSerializer.stringify(x)`. |
| Node | `templates/node/value_serializer.mustache` | Delete lines 76-87 (`private static stringify`). Add `import { ObjectSerializer }` statement. Replace all `stringify(x)` calls with `ObjectSerializer.stringify(x)`. |
| Python | `templates/python/value_serializer.mustache` | Delete lines 82-99 (`@classmethod _stringify`). Add `from .object_serializer import ObjectSerializer`. Replace all `cls._stringify(x)` calls with `ObjectSerializer.stringify(x)`. |
| Ruby | `templates/ruby/value_serializer.mustache` | Delete lines 68-79 (`private_class_method def self.stringify`). Replace all `stringify(x)` calls with `ObjectSerializer.stringify(x)`. No import needed (same module namespace). |
| PHP | `templates/php/value_serializer.mustache` | Delete lines 102-129 (`private static function stringify`). Replace all `self::stringify(x)` calls with `ObjectSerializer::stringify(x)`. No import needed (same namespace). |
| C# | `templates/csharp/value_serializer.mustache` | Delete lines 99-109 (`private static string Stringify`). Replace all `Stringify(x)` calls with `ObjectSerializer.Stringify(x)`. No import needed (same namespace). |

**Step C — Verify no API template changes needed:**

The API templates call `ValueSerializer.serialize()` (or `ValueSerializer::serialize()`, `ValueSerializer.Serialize()`, etc.). This method's signature and behavior don't change — only its internal implementation changes to delegate type conversion. No API template modifications are needed for Gap 0.

**Files modified (12 total):**

| File | Change |
|------|--------|
| `src/main/resources/templates/java/object_serializer.mustache` | Add public `stringify()`, refactor 4 toXValue methods to use it |
| `src/main/resources/templates/node/object_serializer.mustache` | Add public `stringify()`, refactor 4 toXValue methods to use it |
| `src/main/resources/templates/python/object_serializer.mustache` | Add public `stringify()`, refactor 4 toXValue methods to use it |
| `src/main/resources/templates/ruby/object_serializer.mustache` | Add public `stringify()`, refactor 4 toXValue methods to use it |
| `src/main/resources/templates/php/object_serializer.mustache` | Make `toString()` public (rename to `stringify`), toXValue methods already delegate |
| `src/main/resources/templates/csharp/object_serializer.mustache` | Add public `Stringify()`, refactor 4 ToXValue methods to use it |
| `src/main/resources/templates/java/value_serializer.mustache` | Remove private `stringify()`, call `ObjectSerializer.stringify()` |
| `src/main/resources/templates/node/value_serializer.mustache` | Remove private `stringify()`, call `ObjectSerializer.stringify()` |
| `src/main/resources/templates/python/value_serializer.mustache` | Remove private `_stringify()`, call `ObjectSerializer.stringify()` |
| `src/main/resources/templates/ruby/value_serializer.mustache` | Remove private `stringify()`, call `ObjectSerializer.stringify()` |
| `src/main/resources/templates/php/value_serializer.mustache` | Remove private `stringify()`, call `ObjectSerializer::stringify()` |
| `src/main/resources/templates/csharp/value_serializer.mustache` | Remove private `Stringify()`, call `ObjectSerializer.Stringify()` |

---

## Gap 1: Parameter styles + explode (matrix, label, spaceDelimited, pipeDelimited)

### 1.1 Test spec additions

**File:** `src/spec/resources/specs/petstore/openapi.yaml`

Add a new operation exercising all non-default parameter styles:

```yaml
/pet/{petId}/tag/{tagName}:
  get:
    operationId: getPetTag
    parameters:
      - name: petId
        in: path
        required: true
        style: matrix
        explode: false
        schema:
          type: integer
          format: int64
      - name: tagName
        in: path
        required: true
        style: label
        schema:
          type: string
      - name: colors
        in: query
        style: pipeDelimited
        explode: false
        schema:
          type: array
          items:
            type: string
      - name: sizes
        in: query
        style: spaceDelimited
        explode: false
        schema:
          type: array
          items:
            type: string
    responses:
      '200':
        description: OK
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Pet'
```

### 1.2 Write tests (before implementation)

Add a new `serializeStyled()` test group to each ValueSerializer test template, following each language's existing test patterns exactly.

**Existing test patterns by language:**

- **Java** (`ValueSerializerTest.mustache`): `@Nested @DisplayName("...") class ...Tests` containing `@Test @DisplayName("...") void ...()` methods using `assertEquals`/`assertNull`.
- **Node** (`value-serializer.test.ts`): `describe('...')` containing `test('...')` with `expect().toBe()` / `expect().toEqual()` / `expect().toBeUndefined()`.
- **Python** (`test_value_serializer.mustache`): `class TestValueSerializer` with `def test_...` methods using `assert`.
- **Ruby** (`value_serializer_spec.mustache`): `describe ValueSerializer` with `context '...'` / `it '...'` / `expect(...).to eq(...)`.
- **PHP** (`ValueSerializerTest.mustache`): `class ValueSerializerTest extends TestCase` with `public function test...()` using `$this->assertSame()` / `$this->assertNull()`.
- **C#** (`ValueSerializerTest.mustache`): `[TestClass] public class ValueSerializerTest` with `[TestMethod] public void ...()` using `Assert.AreEqual()`.

**Files to modify (6 files):**

| Language | File |
|----------|------|
| Java | `src/main/resources/templates/java/test/ValueSerializerTest.mustache` |
| Node | `src/main/resources/templates/node/test/value-serializer.test.ts` |
| Python | `src/main/resources/templates/python/test/test_value_serializer.mustache` |
| Ruby | `src/main/resources/templates/ruby/test/value_serializer_spec.mustache` |
| PHP | `src/main/resources/templates/php/test/ValueSerializerTest.mustache` |
| C# | `src/main/resources/templates/csharp/test/ValueSerializerTest.mustache` |

**Test cases to add** (as new nested groups/describe blocks, one per style):

**"matrix style" group (4 tests):**

| Test name | Input | Expected |
|-----------|-------|----------|
| scalar returns semicolon-prefixed name=value | `serializeStyled("color", "blue", "path", "string", null, "matrix", true)` | `";color=blue"` |
| array with explode false joins with comma | `serializeStyled("color", ["blue","black"], "path", "array", null, "matrix", false)` | `";color=blue,black"` |
| array with explode true repeats name | `serializeStyled("color", ["blue","black"], "path", "array", null, "matrix", true)` | `";color=blue;color=black"` |
| null returns empty string | `serializeStyled("color", null, "path", "string", null, "matrix", true)` | `""` |

**"label style" group (4 tests):**

| Test name | Input | Expected |
|-----------|-------|----------|
| scalar returns dot-prefixed value | `serializeStyled("color", "blue", "path", "string", null, "label", true)` | `".blue"` |
| array with explode false joins with dot then comma | `serializeStyled("color", ["blue","black"], "path", "array", null, "label", false)` | `".blue,black"` |
| array with explode true joins with dot separator | `serializeStyled("color", ["blue","black"], "path", "array", null, "label", true)` | `".blue.black"` |
| null returns empty string | `serializeStyled("color", null, "path", "string", null, "label", true)` | `""` |

**"spaceDelimited style" group (2 tests):**

| Test name | Input | Expected |
|-----------|-------|----------|
| array joins with space | `serializeStyled("color", ["blue","black"], "query", "array", null, "spaceDelimited", false)` | `"blue black"` |
| scalar returns stringified value | `serializeStyled("color", "blue", "query", "string", null, "spaceDelimited", false)` | `"blue"` |

**"pipeDelimited style" group (2 tests):**

| Test name | Input | Expected |
|-----------|-------|----------|
| array joins with pipe | `serializeStyled("color", ["blue","black"], "query", "array", null, "pipeDelimited", false)` | `"blue\|black"` |
| scalar returns stringified value | `serializeStyled("color", "blue", "query", "string", null, "pipeDelimited", false)` | `"blue"` |

**"form style with explode" group (2 tests):**

| Test name | Input | Expected |
|-----------|-------|----------|
| array with explode false joins with comma | `serializeStyled("color", ["blue","black"], "query", "array", null, "form", false)` | `"blue,black"` |
| array with explode true returns list | `serializeStyled("color", ["blue","black"], "query", "array", null, "form", true)` | `["blue","black"]` |

**"simple style backward compatibility" group (2 tests):**

| Test name | Input | Expected |
|-----------|-------|----------|
| scalar returns stringified value | `serializeStyled("id", "5", "path", "string", null, "simple", false)` | `"5"` |
| array joins with comma | `serializeStyled("id", ["3","4","5"], "path", "array", null, "simple", false)` | `"3,4,5"` |

**"null style falls back to location default" group (1 test):**

| Test name | Input | Expected |
|-----------|-------|----------|
| path with null style behaves like simple | `serializeStyled("id", "5", "path", "string", null, null, false)` | `"5"` |

### 1.3 Integration tests for `getPetTag` operation

In addition to the ValueSerializer unit tests above, add an integration test for the new `getPetTag` operation in each PetApi test template. This verifies that the API templates correctly wire `serializeStyled()` with the style/explode values from the spec, and that the full HTTP request is constructed correctly.

**Files to modify (6 PetApi test templates):**

| Language | File |
|----------|------|
| Java | `src/main/resources/templates/java/test/api/PetApiTest.mustache` |
| Node | `src/main/resources/templates/node/test/Api/pet-api.test.ts` |
| Python | `src/main/resources/templates/python/test/Api/test_pet_api.mustache` |
| Ruby | `src/main/resources/templates/ruby/test/Api/pet_api_spec.mustache` |
| PHP | `src/main/resources/templates/php/test/Api/PetApiTest.mustache` |
| C# | `src/main/resources/templates/csharp/test/Api/PetApiTest.mustache` |

**Test case (1 test per language):**

| Test name | Behavior |
|-----------|----------|
| getPetTag sends matrix and label path params and pipe/space query params | Call `getPetTag(petId=5, tagName="cute", colors=["blue","black"], sizes=["S","M"])`, capture the outgoing HTTP request, and assert: (1) path contains `;petId=5` (matrix style) and `.cute` (label style), (2) query string contains `colors=blue|black` (pipeDelimited) and `sizes=blue%20black` or `sizes=blue+black` (spaceDelimited) |

Note: The exact assertion format depends on whether the mock server (WireMock/Prism) captures the raw request URL. If the mock server doesn't support these styles for validation, this test may need to capture the request at the HTTP client level instead.

### 1.4 Implementation

**Add `serializeStyled()` to all 6 ValueSerializer templates:**

| Language | File |
|----------|------|
| Java | `src/main/resources/templates/java/value_serializer.mustache` |
| Node | `src/main/resources/templates/node/value_serializer.mustache` |
| Python | `src/main/resources/templates/python/value_serializer.mustache` |
| Ruby | `src/main/resources/templates/ruby/value_serializer.mustache` |
| PHP | `src/main/resources/templates/php/value_serializer.mustache` |
| C# | `src/main/resources/templates/csharp/value_serializer.mustache` |

**New public method signature** (language-specific naming conventions apply):
```
serializeStyled(paramName, value, location, schemaType, collectionFormat, style, explode)
  → returns String, String[], or null
```

**Method logic** (pseudo-code):
```
if value is null → return "" (path) or null (query)
if style is null/empty → delegate to existing serialize()
switch(style):
  "simple":  same as current path behavior (comma-join arrays)
  "form":    same as current query behavior (respects explode flag)
  "matrix":  ";name=value" prefix, comma-join or repeat-name for arrays
  "label":   ".value" prefix, comma or dot join for arrays
  "spaceDelimited": space-join arrays
  "pipeDelimited":  pipe-join arrays
```

This method calls `ObjectSerializer.stringify()` (from Gap 0) for converting each individual value to a string, then applies the style-specific formatting.

**`deepObject` style is NOT handled by `serializeStyled()`** — it continues to use the existing `serializeDeepObject()` code path. In all 6 API templates, `deepObject` params are already handled via the `{{#isDeepObject}}` conditional, which calls `ValueSerializer.serializeDeepObject()` (a separate method that returns a `Map<String, String>` of expanded key-value pairs). This is architecturally different from the other styles (which return a single serialized string) and should remain as a separate path. The `serializeStyled()` method does not need a `"deepObject"` case.

**Update 6 API templates** to call `serializeStyled()` instead of `serialize()`, passing `"{{style}}"` and `{{isExplode}}` from the native `CodegenParameter` fields:

| Language | File | Current call | New call |
|----------|------|-------------|----------|
| Java | `templates/java/api/api.mustache` | `ValueSerializer.serialize(val, "path", "{{dataType}}", null)` | `ValueSerializer.serializeStyled("{{baseName}}", val, "path", "{{dataType}}", null, "{{style}}", {{isExplode}})` |
| Node | `templates/node/api/apis.mustache` | `ValueSerializer.serialize(val, 'path', '{{dataType}}')` | `ValueSerializer.serializeStyled('{{baseName}}', val, 'path', '{{dataType}}', null, '{{style}}', {{isExplode}})` |
| Python | `templates/python/api/api.mustache` | `ValueSerializer.serialize(val, 'path', '{{dataType}}')` | `ValueSerializer.serialize_styled('{{baseName}}', val, 'path', '{{dataType}}', None, '{{style}}', {{isExplode}})` |
| Ruby | `templates/ruby/api/api.mustache` | `ValueSerializer.serialize(val, :path, '{{dataType}}')` | `ValueSerializer.serialize_styled('{{baseName}}', val, :path, '{{dataType}}', nil, '{{style}}', {{isExplode}})` |
| PHP | `templates/php/api/api.mustache` | `ValueSerializer::serialize($val, 'path', '{{dataType}}')` | `ValueSerializer::serializeStyled('{{baseName}}', $val, 'path', '{{dataType}}', null, '{{style}}', {{isExplode}})` |
| C# | `templates/csharp/api/api.mustache` | `ValueSerializer.Serialize(val, "path", "{{dataType}}")` | `ValueSerializer.SerializeStyled("{{baseName}}", val, "path", "{{dataType}}", null, "{{style}}", {{isExplode}})` |

This applies to ALL parameter locations (path, query, header, cookie) — not just path.

---

## Gap 2: `allowEmptyValue` on query params

### 2.1 Test spec additions

**File:** `src/spec/resources/specs/petstore/openapi.yaml`

Add `allowEmptyValue: true` to a query parameter on an existing or new operation. For example, on the `getPetTag` operation from Gap 1, add:
```yaml
      - name: filter
        in: query
        allowEmptyValue: true
        schema:
          type: string
```

### 2.2 Write tests (before implementation)

Add to the existing `"query parameters"` nested group in each BaseApi test template.

**Existing patterns by language:**
- **Java**: `@Nested @DisplayName("query parameters") class QueryParameters` with `@Test @DisplayName("...") void ...()` using `TestableApi` to capture HTTP requests and assert query string contents.
- **Node**: `describe('query parameters', ...)` with `test('...')`.
- Other languages follow their respective patterns.

**Files to modify (6 files):**

| Language | File |
|----------|------|
| Java | `src/main/resources/templates/java/test/BaseApiTest.mustache` |
| Node | `src/main/resources/templates/node/test/base-api.test.mustache` |
| Python | `src/main/resources/templates/python/test/test_base_api.mustache` |
| Ruby | `src/main/resources/templates/ruby/test/base_api_spec.mustache` |
| PHP | `src/main/resources/templates/php/test/BaseApiTest.mustache` |
| C# | `src/main/resources/templates/csharp/test/BaseApiTest.mustache` |

**Test case (1 test per language):**

| Test name | Behavior |
|-----------|----------|
| includes empty value param in query string when value is empty string | Pass `queryParams.put("filter", "")` with allowEmptyValue → query string contains `filter=` (param is NOT omitted) |

### 2.3 Implementation

Use the native `{{#isAllowEmptyValue}}` boolean field on `CodegenParameter`. In the query param section of each API template, when `isAllowEmptyValue` is true, include the parameter in the query string even when the serialized value is null or empty string.

Currently, query params with null serialized values are omitted. The change adds a conditional: if `isAllowEmptyValue`, add the param key with an empty value instead of omitting it.

**Files:** Same 6 API templates as Gap 1.

---

## Gap 3: Per-operation servers

### 3.1 Test spec additions

**File:** `src/spec/resources/specs/petstore/openapi.yaml`

Add an operation with per-operation `servers` override:
```yaml
/pet/{petId}/external:
  get:
    servers:
      - url: https://external-api.example.com/v1
    operationId: getExternalPetInfo
    parameters:
      - name: petId
        in: path
        required: true
        schema:
          type: integer
          format: int64
    responses:
      '200':
        description: OK
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Pet'
```

### 3.2 Write tests (before implementation)

Add a test to each PetApi test class, following the existing integration test pattern. The test calls the generated `getExternalPetInfo()` method, captures the outgoing HTTP request, and asserts the URL starts with `https://external-api.example.com/v1` instead of the global base URL.

**Files to modify (6 files):**

| Language | File |
|----------|------|
| Java | `src/main/resources/templates/java/test/api/PetApiTest.mustache` |
| Node | `src/main/resources/templates/node/test/Api/pet-api.test.ts` |
| Python | `src/main/resources/templates/python/test/Api/test_pet_api.mustache` |
| Ruby | `src/main/resources/templates/ruby/test/Api/pet_api_spec.mustache` |
| PHP | `src/main/resources/templates/php/test/Api/PetApiTest.mustache` |
| C# | `src/main/resources/templates/csharp/test/Api/PetApiTest.mustache` |

### 3.3 Implementation

The upstream framework populates `CodegenOperation.servers` when an operation has per-operation servers defined. The API templates already have a `{{#servers}}` block that uses the first server URL. Verify this works correctly by checking:

1. Does `{{#servers}}` inside `{{#operation}}` resolve to `CodegenOperation.servers`?
2. If not populated, does `AbstractBetterCodegen.fromOperation()` need to copy `operation.getServers()`?

The existing API template server handling (e.g., Java api.mustache lines ~228-232: `{{#servers}}{{#-first}}if ("{{url}}".startsWith("http://")...{{/-first}}{{/servers}}`) already has logic for this. It may just need verification that the upstream framework correctly populates the field.

**Files potentially modified:**
- `src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java` — only if needed for field population
- 6 API templates — only if the server URL logic needs adjustment

---

## Gap 4: Typed `additionalProperties`

### 4.1 Test spec additions

**File:** `src/spec/resources/specs/petstore/openapi.yaml`

Add a model with typed `additionalProperties`:
```yaml
Metadata:
  type: object
  properties:
    createdAt:
      type: string
      format: date-time
  additionalProperties:
    type: string
```

This differs from `additionalProperties: true` (which allows any type). With `type: string`, the generated code should use `Map<String, String>` (Java), `Record<string, string>` (Node), `Dict[str, str]` (Python), etc. instead of the current hardcoded untyped fallback.

### 4.2 Write tests (before implementation)

Create a new model test template per language for the `Metadata` model, following the existing `DryFoodTest` pattern in each language. The test constructs a Metadata object, serializes it to JSON (including additional string properties), deserializes it back, and asserts the additional properties are preserved with the correct type.

**Files to create (6 new test templates):**

| Language | File | Pattern to follow |
|----------|------|-------------------|
| Java | New mustache in `src/main/resources/templates/java/test/` | `DryFoodTest`: `@Test @DisplayName("...")`, ObjectSerializer round-trip, `assertEquals` |
| Node | New `.test.ts` in `src/main/resources/templates/node/test/` | `dry-food.test.ts`: `describe`/`test`/`expect`, `ObjectSerializer.deserialize()`/`serialize()` |
| Python | New mustache in `src/main/resources/templates/python/test/` | `test_dry_food.py`: `def test_...`/`assert`, `json.loads()`/`json.dumps()` |
| Ruby | New mustache in `src/main/resources/templates/ruby/test/` | `dry_food_test.rb`: `describe`/`it`/`expect`, `ObjectSerializer.deserialize()`/`serialize()` |
| PHP | New mustache in `src/main/resources/templates/php/test/` | `DryFoodTest.php`: `public function test...()`/`$this->assertSame()` |
| C# | New mustache in `src/main/resources/templates/csharp/test/` | Existing C# model test pattern |

**Test cases per file (3 tests):**

| Test name | Behavior |
|-----------|----------|
| deserializes additional string properties | JSON `{"createdAt":"2024-01-01T00:00:00Z","customField":"hello"}` → `metadata.getAdditionalProperties().get("customField")` equals `"hello"` |
| round-trip preserves additional properties | Construct → serialize → deserialize → assert additional properties match |
| compiles with typed additional properties | Compile-time check: `Map<String, String>` (Java) / `Record<string, string>` (Node) / etc. — wrong types cause compile error |

### 4.3 Implementation

Use the native `{{additionalPropertiesType}}` field from `CodegenModel`. Replace hardcoded untyped containers with a conditional that uses the typed version when available:

**Java** (`models/model.mustache` lines 313-326):
- Current: `java.util.Map<String, Object> additionalProperties`
- New: `java.util.Map<String, {{#additionalPropertiesType}}{{{additionalPropertiesType}}}{{/additionalPropertiesType}}{{^additionalPropertiesType}}Object{{/additionalPropertiesType}}> additionalProperties`
- Apply same pattern to: field declaration, `@JsonAnySetter` parameter type, `@JsonAnyGetter` return type

**Node** (`models/model.mustache` line 236):
- Current: `[key: string]: unknown;`
- New: `[key: string]: {{#additionalPropertiesType}}{{{additionalPropertiesType}}}{{/additionalPropertiesType}}{{^additionalPropertiesType}}unknown{{/additionalPropertiesType}};`

**Python** (line ~202):
- Current: `Dict[str, Any]`
- New: `Dict[str, {{#additionalPropertiesType}}{{{additionalPropertiesType}}}{{/additionalPropertiesType}}{{^additionalPropertiesType}}Any{{/additionalPropertiesType}}]`

**PHP**:
- Current: `mixed` parameter type
- New: conditional `{{#additionalPropertiesType}}{{{additionalPropertiesType}}}{{/additionalPropertiesType}}{{^additionalPropertiesType}}mixed{{/additionalPropertiesType}}`

**Ruby**:
- Add a `ADDITIONAL_PROPERTIES_TYPE` constant for runtime introspection

**C#**:
- Current: `Dictionary<string, object>`
- New: `Dictionary<string, {{#additionalPropertiesType}}{{{additionalPropertiesType}}}{{/additionalPropertiesType}}{{^additionalPropertiesType}}object{{/additionalPropertiesType}}>`

**Files (6 model templates):**
- `src/main/resources/templates/java/models/model.mustache`
- `src/main/resources/templates/node/models/model.mustache`
- `src/main/resources/templates/python/models/model.mustache`
- `src/main/resources/templates/ruby/models/model.mustache`
- `src/main/resources/templates/php/models/model.mustache`
- `src/main/resources/templates/csharp/models/model.mustache`

---

## Gap 5: `refreshUrl` on OAuth flows

### 5.1 Problem

The OAS 3.0 `OAuth Flow Object` has a `refreshUrl` field — a URL for obtaining refresh tokens that may differ from `tokenUrl`. Currently, all auth code and password authenticators reuse `tokenUrl` for refresh token requests.

**Example of the bug** (Java `oauth2_auth_code_authenticator.mustache` line 118):
```java
String token = tokenManager.getAccessToken(tokenUrl, params);
```
When `refreshUrl` differs from `tokenUrl`, refresh requests go to the wrong endpoint.

### 5.2 Write tests (before implementation)

OAuth authenticator tests are generated by the Java codegen classes (`generatePerSchemeAuthenticators()`), not from Mustache test templates. The tests need to be added to the test template files that generate per-scheme authenticator tests.

**Auth code authenticator test case:** Construct an auth code authenticator with `tokenUrl="https://auth.example.com/token"` and `refreshUrl="https://auth.example.com/refresh"`. After `exchangeCode()`, call `getAuthHeaders()` and verify the refresh request goes to `refreshUrl`, not `tokenUrl`.

**Password authenticator test case:** Construct a password authenticator with `tokenUrl="https://auth.example.com/token"` and `refreshUrl="https://auth.example.com/refresh"`. After initial `authenticate(username, password)`, call `getAuthHeaders()` and verify the refresh request goes to `refreshUrl`, not `tokenUrl`.

**Files to modify (auth code authenticator test templates, 6 languages):**

| Language | File |
|----------|------|
| Java | Generated test in `src/spec/resources/generated/java/.../OAuth2AuthorizationCodeAuthenticatorTest.java` |
| Node | Generated test in `src/spec/resources/generated/node/.../oauth2-auth-code-authenticator.test.ts` |
| Python | Generated test in `src/spec/resources/generated/python/.../test_oauth2_auth_code_authenticator.py` |
| Ruby | Generated test in `src/spec/resources/generated/ruby/.../oauth2_auth_code_authenticator_test.rb` |
| PHP | Generated test in `src/spec/resources/generated/php/.../OAuth2AuthorizationCodeAuthenticatorTest.php` |
| C# | Generated test in `src/spec/resources/generated/csharp/.../OAuth2AuthorizationCodeAuthenticatorTest.cs` |

**Files to modify (password authenticator test templates, 6 languages):**

| Language | File |
|----------|------|
| Java | Generated test in `src/spec/resources/generated/java/.../OAuth2PasswordAuthenticatorTest.java` |
| Node | Generated test in `src/spec/resources/generated/node/.../oauth2-password-authenticator.test.ts` |
| Python | Generated test in `src/spec/resources/generated/python/.../test_oauth2_password_authenticator.py` |
| Ruby | Generated test in `src/spec/resources/generated/ruby/.../oauth2_password_authenticator_test.rb` |
| PHP | Generated test in `src/spec/resources/generated/php/.../OAuth2PasswordAuthenticatorTest.php` |
| C# | Generated test in `src/spec/resources/generated/csharp/.../OAuth2PasswordAuthenticatorTest.cs` |

### 5.3 Implementation

**OAuth authenticator templates** — add `refreshUrl` parameter to constructor. When `refreshUrl` is null, fall back to `tokenUrl`. In `getAuthHeaders()`, use `refreshUrl` for refresh token requests.

**Authenticator template files to modify (12 templates — auth code + password, 6 languages):**

| Language | Auth Code Authenticator | Password Authenticator |
|----------|------------------------|----------------------|
| Java | `templates/java/auth/oauth/oauth2_auth_code_authenticator.mustache` | `templates/java/auth/oauth/oauth2_password_authenticator.mustache` |
| Node | `templates/node/auth/oauth/oauth2-auth-code-authenticator.mustache` | `templates/node/auth/oauth/oauth2-password-authenticator.mustache` |
| Python | `templates/python/auth/oauth/oauth2_auth_code_authenticator.mustache` | `templates/python/auth/oauth/oauth2_password_authenticator.mustache` |
| Ruby | `templates/ruby/auth/oauth/oauth2_auth_code_authenticator.mustache` | `templates/ruby/auth/oauth/oauth2_password_authenticator.mustache` |
| PHP | `templates/php/auth/oauth/oauth2_auth_code_authenticator.mustache` | `templates/php/auth/oauth/oauth2_password_authenticator.mustache` |
| C# | `templates/csharp/auth/oauth/oauth2_auth_code_authenticator.mustache` | `templates/csharp/auth/oauth/oauth2_password_authenticator.mustache` |

**Java codegen classes** — update `generatePerSchemeAuthenticators()` to extract `refreshUrl` from `OAuthFlow.getRefreshUrl()` and pass it to the generated per-scheme authenticator constructor:

| Language | Codegen class |
|----------|--------------|
| Java | `src/main/java/io/github/mridang/codegen/generators/java/BetterJavaCodegen.java` |
| Node | `src/main/java/io/github/mridang/codegen/generators/node/BetterNodeCodegen.java` |
| Python | `src/main/java/io/github/mridang/codegen/generators/python/BetterPythonCodegen.java` |
| Ruby | `src/main/java/io/github/mridang/codegen/generators/ruby/BetterRubyCodegen.java` |
| PHP | `src/main/java/io/github/mridang/codegen/generators/php/BetterPhpCodegen.java` |
| C# | `src/main/java/io/github/mridang/codegen/generators/csharp/BetterCSharpCodegen.java` |

---

## Gap 6: Runtime server variable overrides via Configuration

### 6.1 Problem

The OAS 3.0 `Server Object` supports URL templates with variables:

```yaml
servers:
  - url: https://{environment}.example.com/{version}
    variables:
      environment:
        default: api
        enum: [api, staging, sandbox]
      version:
        default: v2
        enum: [v1, v2]
```

The infrastructure for this is **already 90% built**:

1. **`AbstractBetterCodegen.processServers()`** (lines 113-157) already extracts all server metadata: URL template, description, variables (name, default, description, enum values), and sets `hasServers`, `hasAnyServerVariables`, and `serverConfigs` on `additionalProperties`.

2. **`ServerConfiguration` class** is generated in all 6 languages with a `getUrl(overrides)` method that performs URL template substitution with enum validation:

| Language | Template | Method | Enum validation |
|----------|----------|--------|-----------------|
| Java | `templates/java/server_configuration.mustache` | `getUrl(Map<String, String> overrides)` | Throws `IllegalArgumentException` for invalid enum values (lines 95-103) |
| Node | `templates/node/server_configuration.mustache` | `getUrl(overrides?: Record<string, string>)` | Throws `Error` for invalid enum values (lines 87-90) |
| Python | `templates/python/server_configuration.mustache` | `get_url(overrides: Optional[Dict[str, str]] = None)` | Raises `ValueError` for invalid enum values (lines 79-82) |
| Ruby | `templates/ruby/server_configuration.mustache` | `url(overrides = {})` | Raises `ArgumentError` for invalid enum values (lines 99-104) |
| PHP | `templates/php/server_configuration.mustache` | `getUrl(array $overrides = []): string` | Throws `\InvalidArgumentException` for invalid enum values (lines 53-64) |
| C# | `templates/csharp/server_configuration.mustache` | `GetUrl(Dictionary<string, string> overrides)` | Throws `ArgumentException` for invalid enum values (lines 79-85) |

3. **`ServerVariable` class** is generated in all 6 languages (immutable, with default value, description, and enum values).

4. **`Servers` class** is generated in all 6 languages with static constants (`SERVER_0`, `SERVER_1`, etc.) and an `ALL` list.

**What's missing:** The API templates hardcode the first server URL at code generation time:

```java
// Java api.mustache lines 317-323
{{#servers}}
{{#-first}}
if ("{{url}}".startsWith("http://") || "{{url}}".startsWith("https://")) {
    path = "{{url}}" + path;
}
{{/-first}}
{{/servers}}
```

This bakes `"{{url}}"` (e.g., `"https://api.example.com/v2"`) into the generated code as a literal string. Server variables are resolved at generation time using their defaults — there is no way to override them at runtime (e.g., to switch from `api` to `staging`).

**Configuration** has `baseUrl` as an immutable field with a default from `{{{basePath}}}` (the first server's resolved URL):

| Language | Configuration field | Builder default |
|----------|-------------------|-----------------|
| Java | `private final String baseUrl` | `private String baseUrl = "{{{basePath}}}"` |
| Node | `public readonly baseUrl: string` | `private _baseUrl: string = '{{{basePath}}}'` |
| Python | `base_url: str = '{{{basePath}}}'` (frozen dataclass) | `self._base_url: str = '{{{basePath}}}'` |
| Ruby | `attr_reader :base_url` (frozen) | `@base_url = '{{{basePath}}}'` |
| PHP | `public readonly string $baseUrl` | Constructor default `= '{{{basePath}}}'` |
| C# | `public string BaseUrl { get; }` | `private string _baseUrl = "{{{basePath}}}"` |

Users can override `baseUrl` via the builder, but they must manually construct the full URL string. There's no way to say "use server 1 with `environment=staging`" — even though `ServerConfiguration.getUrl({"environment": "staging"})` would do exactly that.

### 6.2 Test spec additions

**File:** `src/spec/resources/specs/petstore/openapi.yaml`

Add a second server with variables to the existing `servers` block. **The relative `/api/v3` server MUST remain first** to preserve `{{{basePath}}}` as `/api/v3` — otherwise, the upstream framework resolves the first server's URL template with defaults and all existing integration tests (which override `baseUrl` to a local WireMock/Prism URL) would see a changed default, and any test that does NOT explicitly override `baseUrl` would break.

```yaml
servers:
  - url: /api/v3
    description: Relative URL (no variables)
  - url: https://{environment}.example.com/api/{version}
    description: Main API server with variables
    variables:
      environment:
        default: api
        description: API environment
        enum:
          - api
          - staging
          - sandbox
      version:
        default: v3
        description: API version
        enum:
          - v2
          - v3
```

This keeps `/api/v3` as the first server (preserving `{{{basePath}}}`) and adds a second server with variables. Gap 6 tests will explicitly reference `Servers.SERVER_1` (the variable server) to exercise server variable overrides.

### 6.3 Write tests (before implementation)

Add tests to the Configuration test suite and BaseApi test suite in each language. These tests verify that:

1. **Configuration resolves server variables at construction time** — constructing a Configuration with `server(Servers.SERVER_0, Map.of("environment", "staging", "version", "v3"))` results in `baseUrl = "https://staging.example.com/api/v3"`.
2. **Default server variables produce correct baseUrl** — constructing a Configuration with `server(Servers.SERVER_0)` (no overrides) results in `baseUrl = "https://api.example.com/api/v3"` (defaults applied).
3. **Invalid enum values are rejected** — constructing a Configuration with `server(Servers.SERVER_0, Map.of("environment", "invalid"))` throws an error.
4. **BaseApi uses the resolved URL** — constructing a Configuration with server variable overrides, then making an API call, results in the request going to the resolved URL.

**Files to modify (6 BaseApi test templates + 6 configuration/unit test files):**

| Language | BaseApi Test | Configuration Test |
|----------|-------------|-------------------|
| Java | `src/main/resources/templates/java/test/BaseApiTest.mustache` | New nested class in existing test, or standalone test if one exists |
| Node | `src/main/resources/templates/node/test/base-api.test.mustache` | New describe block |
| Python | `src/main/resources/templates/python/test/test_base_api.mustache` | New test class or methods |
| Ruby | `src/main/resources/templates/ruby/test/base_api_spec.mustache` | New context block |
| PHP | `src/main/resources/templates/php/test/BaseApiTest.mustache` | New test methods |
| C# | `src/main/resources/templates/csharp/test/BaseApiTest.mustache` | New test methods |

**Test cases (4 tests per language):**

Note: `Servers.SERVER_1` is the variable server (`https://{environment}.example.com/api/{version}`). `Servers.SERVER_0` is the relative URL (`/api/v3`, no variables).

| Test name | Behavior |
|-----------|----------|
| server variable overrides resolve in base URL | `Configuration.builder().server(Servers.SERVER_1, Map.of("environment", "staging")).build().getBaseUrl()` equals `"https://staging.example.com/api/v3"` |
| default server variables produce correct base URL | `Configuration.builder().server(Servers.SERVER_1).build().getBaseUrl()` equals `"https://api.example.com/api/v3"` |
| invalid enum value throws error | `Configuration.builder().server(Servers.SERVER_1, Map.of("environment", "invalid"))` throws `IllegalArgumentException` / `Error` / `ValueError` / etc. |
| API request uses resolved server URL | Construct Configuration with `server(Servers.SERVER_1, Map.of("environment", "staging"))`, make API call, assert request URL starts with `https://staging.example.com/api/v3` |

### 6.4 Implementation

**Step A — Add `server()` method to Configuration Builder (6 configuration templates):**

Each language's `configuration.mustache` template gets a new builder method that accepts a `ServerConfiguration` and optional variable overrides map. This method calls `serverConfiguration.getUrl(overrides)` at build time and uses the result as `baseUrl`.

| Language | Template | New Builder Method |
|----------|----------|--------------------|
| Java | `templates/java/configuration.mustache` | `public Builder server(ServerConfiguration server, Map<String, String> variables)` and overload `public Builder server(ServerConfiguration server)` |
| Node | `templates/node/configuration.mustache` | `server(server: ServerConfiguration, variables?: Record<string, string>): ConfigurationBuilder` |
| Python | `templates/python/configuration.mustache` | `def server(self, server: ServerConfiguration, variables: Optional[Dict[str, str]] = None) -> 'ConfigurationBuilder'` |
| Ruby | `templates/ruby/configuration.mustache` | `def server(server_config, variables = {})` |
| PHP | `templates/php/configuration.mustache` | `public function server(ServerConfiguration $server, array $variables = []): self` |
| C# | `templates/csharp/configuration.mustache` | `public ConfigurationBuilder Server(ServerConfiguration server, Dictionary<string, string>? variables = null)` |

**Method logic** (same across all languages):
```
server(serverConfig, variables = {}) {
    this.baseUrl = serverConfig.getUrl(variables);
    return this;
}
```

This is a convenience method — it resolves the server URL template with the given variables and sets it as the base URL. The enum validation already happens inside `ServerConfiguration.getUrl()`. If the user also calls `baseUrl("...")` after `server(...)`, the explicit `baseUrl` wins (last-write-wins).

**Step B — Add import for ServerConfiguration in Configuration templates:**

Each Configuration template needs to import the `ServerConfiguration` class. The import path depends on the language:

| Language | Import statement |
|----------|-----------------|
| Java | `import {{invokerPackage}}.ServerConfiguration;` |
| Node | `import { ServerConfiguration } from './server-configuration{{importFileExtension}}';` |
| Python | `from .server_configuration import ServerConfiguration` |
| Ruby | No import needed (same module namespace) |
| PHP | `use {{invokerPackage}}\ServerConfiguration;` |
| C# | No import needed (same namespace) |

**Step C — No changes to BaseApi or API templates:**

The `baseUrl` field on Configuration is already used by BaseApi to construct request URLs. Since the `server()` builder method resolves the URL and stores it as `baseUrl`, no changes are needed to BaseApi's URL resolution logic. The existing flow works:

1. User builds Configuration with `server(Servers.SERVER_1, Map.of("environment", "staging"))`
2. Builder calls `ServerConfiguration.getUrl({"environment": "staging"})` → `"https://staging.example.com/api/v3"`
3. Builder stores result as `baseUrl`
4. BaseApi reads `config.getBaseUrl()` and prepends it to operation paths
5. Request goes to `https://staging.example.com/api/v3/pet/123`

**Step D — Update default baseUrl in Configuration to use Servers class (conditional):**

When the spec has servers with variables, the default `baseUrl` in the builder should be resolved via `Servers.SERVER_0.getUrl()` (using default variable values) instead of the hardcoded `{{{basePath}}}` string. This ensures the default Configuration uses proper server variable resolution.

However, `{{{basePath}}}` is already the resolved URL with defaults applied (the upstream framework resolves it). So the current default is already correct for the default case. The `server()` method is only needed when the user wants to override variables to non-default values.

**No change needed for the default** — `{{{basePath}}}` already equals what `Servers.SERVER_0.getUrl()` would return with default values.

**Files modified (6 total):**

| File | Change |
|------|--------|
| `src/main/resources/templates/java/configuration.mustache` | Add `server()` method to Builder, add ServerConfiguration import |
| `src/main/resources/templates/node/configuration.mustache` | Add `server()` method to ConfigurationBuilder, add ServerConfiguration import |
| `src/main/resources/templates/python/configuration.mustache` | Add `server()` method to ConfigurationBuilder, add ServerConfiguration import |
| `src/main/resources/templates/ruby/configuration.mustache` | Add `server()` method to Builder |
| `src/main/resources/templates/php/configuration.mustache` | Add `server()` method to ConfigurationBuilder, add ServerConfiguration use statement |
| `src/main/resources/templates/csharp/configuration.mustache` | Add `Server()` method to ConfigurationBuilder |

---

## Step 7: Update README caveats (renumbered from Step 6)

**File:** `README.md`

Update the caveats table to have two sections:

**Out of scope** (already listed): Callbacks, Links, Webhooks, mTLS, XML serialization

**Known limitations** (new section):

| Feature | Notes |
|---------|-------|
| `allowReserved` on query params | Not exposed on CodegenParameter upstream |
| Default response / wildcard status codes (`2XX`, `4XX`) | Current exception hierarchy adequate |
| Typed response headers (schema on Header Object) | Headers returned as `Map<String, String>` |
| `not` composition | Not supported by upstream framework |
| Advanced multipart encoding (headers, style, explode in Encoding Object) | Only `contentType` used in encoding object |
| Path-level `servers` (inherited by all ops in a path) | Upstream framework limitation |

---

## Step 8: Regenerate and verify (renumbered from Step 7)

1. `devbox run -- mvn compile` — templates compile
2. `devbox run -- mvn test -Dtest=GenerateClientsTest` — regenerate all 6 clients
3. Verify generated ValueSerializer no longer has private `stringify()` — all 6 languages delegate to `ObjectSerializer.stringify()`
4. Verify generated ObjectSerializer has public `stringify()` method, and `toPathValue`/`toQueryValue`/`toHeaderValue`/`toFormValue` use it
5. Verify generated ValueSerializer has `serializeStyled()` method in all 6 languages
6. Verify generated API classes call `serializeStyled()` with style and explode arguments
7. Verify generated models use typed `additionalProperties` (e.g., `Map<String, String>` for the Metadata model)
8. Verify OAuth authenticators accept `refreshUrl` parameter
9. Verify generated Configuration Builder has `server()` method accepting `ServerConfiguration` + variable overrides in all 6 languages
10. Verify `Configuration.builder().server(Servers.SERVER_1, Map.of("environment", "staging")).build().getBaseUrl()` resolves correctly
11. `devbox run test` — full test suite passes (all 6 language integration tests)

---

## Complete file inventory

### Test files (write first)

| File count | Description | New tests |
|------------|-------------|-----------|
| 6 ObjectSerializer test templates | All 6 languages | Gap 0: 6 new tests for `stringify()`: null, bool true, bool false, integer, date-time, string |
| 6 ValueSerializer test templates | All 6 languages | Gap 1: 17 new tests for `serializeStyled()`: matrix (4), label (4), spaceDelimited (2), pipeDelimited (2), form+explode (2), simple compat (2), null fallback (1) |
| 6 BaseApi test templates | All 6 languages | Gap 2: 1 new test each for allowEmptyValue query param + Gap 6: 4 new tests each for server variable overrides via Configuration |
| 6 PetApi test templates | All 6 languages | Gap 1: 1 new test each for `getPetTag` styled param integration + Gap 3: 1 new test each for per-operation server URL assertion |
| 6 new Metadata model test templates | All 6 languages (new files) | Gap 4: 3 new tests each: deserialize, round-trip, type check |
| 6 OAuth auth code test templates | All 6 languages | Gap 5: 1 new test each for refreshUrl used in refresh requests |
| 6 OAuth password test templates | All 6 languages | Gap 5: 1 new test each for refreshUrl used in refresh requests |

### Source templates (implement after tests)

| File count | Description | Change |
|------------|-------------|--------|
| 6 `object_serializer.mustache` | All 6 languages | Gap 0: Add public `stringify()`, refactor toXValue methods to use it |
| 6 `value_serializer.mustache` | All 6 languages | Gap 0: Remove private `stringify()`, delegate to ObjectSerializer. Gap 1: Add `serializeStyled()` |
| 6 API templates (`api.mustache` / `apis.mustache`) | All 6 languages | Gap 1: Call `serializeStyled()` with style/explode. Gap 2: Add allowEmptyValue handling |
| 6 `models/model.mustache` | All 6 languages | Gap 4: Use `{{additionalPropertiesType}}` for typed additional properties |
| 12 OAuth authenticator templates | Auth code + password, all 6 languages | Gap 5: Add `refreshUrl` parameter, use for refresh requests |
| 6 `configuration.mustache` | All 6 languages | Gap 6: Add `server()` method to Builder accepting `ServerConfiguration` + variable overrides |

### Other files

| File | Change |
|------|--------|
| `src/spec/resources/specs/petstore/openapi.yaml` | Add test operations for styles, allowEmptyValue, per-op servers, typed additionalProperties, refreshUrl in OAuth, server variables |
| 6 language codegen Java classes | Gap 5: Extract `refreshUrl` from OAuthFlow and pass to generated authenticators |
| `src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java` | Gap 3: Only if needed for per-op server field population |
| `README.md` | Step 7: Add known limitations section |

### Total file count

- **Test files:** 42 (6 modified ObjectSerializer + 6 modified ValueSerializer + 6 modified BaseApi + 6 modified PetApi + 6 new Metadata + 6 modified OAuth auth code + 6 modified OAuth password)
- **Source templates:** 42 (6 ObjectSerializer + 6 ValueSerializer + 6 API + 6 model + 12 OAuth + 6 Configuration)
- **Other files:** 9 (1 spec + 6 codegen classes + 1 AbstractBetterCodegen + 1 README)
- **Grand total:** ~93 files touched

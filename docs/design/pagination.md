# Pagination design (issue #163)

Status: proposal for owner review. Nothing here is built. This is the design document that #163 asks for as its first step; no template, generator or golden changes with it. Where a fact could not be read it is marked **not verified**. Every code sketch is a sketch: none was compiled, and stage 0 of section 10 compiles them before any template changes.

## 1. Goal

A caller of a list operation that the server pages should write one loop over the items, in the idiom of the language, and never read a token, offset or `Link` header by hand. The same capabilities and the same behaviour in all twelve SDKs (java, kotlin, csharp, php, python, ruby, node, dart, go, rust, swift, elixir), differing only by idiom. An operation that the generator cannot prove is paged is left alone: the existing manual call stays the only call, and the generator never guesses.

## 2. Owner decisions that bind this work

- Clients only, twelve languages, one API shape (root `AGENTS.md`).
- No vendor extensions in templates, and none read from the spec to decide anything: detection uses only native OpenAPI fields (parameter names and schemas, response schemas, declared response headers). A value the generator computes goes to the templates under a non-`x-` name (`vendorExtensions.op.pagination`, see section 5).
- No client-side validation (S3): the pager moves tokens and counts items; it never checks a schema constraint, a page size bound or a token's format.
- Typed errors are thrown, never returned as values; the error tree of `generators/AGENTS.md` is the only one. Retries are F823, per-call options are F827, cancellation is S7, `close()` is F1078.
- Native types where the language has one, and no invented wrapper where a native iteration type exists (same rule as `streaming.md`).
- The public API shape is fixed before it is built so that later work is additive; this design only adds methods.

## 3. How other tools do it

| Tool | What it does | Source |
| --- | --- | --- |
| OpenAPI Generator | Nothing in the generator documentation read for this revision: the `java` generator page lists no pagination option or extension. Users write the loop by hand. | [docs/generators/java.md](https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/java.md) (read, searched for the word) |
| Speakeasy | An operation-level extension `x-speakeasy-pagination` with three types: `offsetLimit` (inputs of type `page` or `offset`, optional `limit`; outputs `results` or `numPages` as JSONPath), `cursor` (input `cursor`, output `nextCursor` as JSONPath, input and output types must match) and `url` (output `nextUrl`). The generated call returns a response with `next()`; it returns null at the end, which is not an error. If a JSONPath does not match, pagination stops. | [speakeasy.com/docs/customize/runtime/pagination](https://www.speakeasy.com/docs/customize/runtime/pagination) (read) |
| Stainless (OpenAI, Anthropic, Cloudflare SDKs) | Configured outside the spec in the Stainless config, with named schemes (offset, page number, cursor and similar; the scheme names were **not verified**, the config reference could not be read). The generated list call is iterable: `for job in client.fine_tuning.jobs.list(limit=20)`, `async for` in the async client, and `for await (const job of client.fineTuning.jobs.list({ limit: 20 }))` in node. A page object also exposes `has_next_page()`, `next_page_info()` and `get_next_page()` for manual control. | [openai-python README, Pagination](https://github.com/openai/openai-python/blob/main/README.md), [openai-node README, Auto-pagination](https://github.com/openai/openai-node/blob/master/README.md) (both read) |
| Azure SDK guidelines | Iterate over items, not pages; use the language's canonical iterator (async where the ecosystem has it); do not provide a method that loads a whole collection into an array; paging APIs take a continuation token and a maximum item count and return a token so iteration can resume, possibly elsewhere; the list verb is `list_<noun>` returning a `Pageable`. The per-language names (for example `ByPage`) are on the per-language pages and were **not verified** here. | [azure.github.io/azure-sdk/general_design.html](https://azure.github.io/azure-sdk/general_design.html) (read) |
| AWS (boto3, and the other SDKs by the same model) | A separate paginator object, `client.get_paginator('list_objects_v2').paginate(...)`, returns a page iterator. `PaginationConfig` has `MaxItems` (a cap on items across all pages), `PageSize` and `StartingToken` (resume). Operation parameters pass straight through to every page request. The paginator definitions are service model data, not inferred. | [boto3 paginators guide](https://docs.aws.amazon.com/boto3/latest/guide/paginators.html) (read) |
| Google (API Improvement Proposals) | AIP-158 fixes the convention: request `page_size` and `page_token`, response `next_page_token`; an empty `next_page_token` is the only end signal; tokens are opaque and may expire; client libraries iterate items automatically and give paged methods a different signature. Because the names are a standard, the libraries can detect a paged method from the proto. | [google.aip.dev/158](https://google.aip.dev/158) (read) |
| progenitor and Dropshot (Oxide) | The server side marks a paged endpoint with the `x-dropshot-pagination` extension; its schema carries `limit` and `page_token` query parameters and a `ResultsPage { items, next_page }` response whose token is opaque, versioned and capped at 512 bytes. progenitor turns marked operations into streams; its README mentions only that paginated interfaces use `Streams` (names not stated there). Pagination works only through the extension. | [dropshot pagination.rs](https://github.com/oxidecomputer/dropshot/blob/main/dropshot/src/pagination.rs), [progenitor README](https://github.com/oxidecomputer/progenitor/blob/main/README.md) (both read) |
| ogen | No pagination feature found: the one issue the search returned (#1228, extensions in `ogen.Spec`) does not concern it, and #131 of this repository recorded the same. | [ogen issues](https://github.com/ogen-go/ogen/issues?q=pagination) (read) |
| Orval | **Not verified.** The option page could not be read. Recollection (unchecked): Orval's react-query output can emit infinite queries, which needs the caller to name the query parameter; it is a front-end data layer, not an SDK iterator. |  |
| `Link` header | RFC 8288 defines the header: `Link: <URI-Reference>; rel="next"`, relative targets resolve against the URI of the response, `rel` may hold several space-separated types. The IANA registry has `next`, `prev`/`previous`, `first` and `last`. GitHub's REST API is the best-known user (**not verified** here). | [RFC 8288](https://datatracker.ietf.org/doc/html/rfc8288), [IANA link relations](https://www.iana.org/assignments/link-relations/link-relations.xhtml) (both read) |

What the sources agree on, and this design takes: the caller iterates items; a page object exists for the caller who wants tokens, headers or resumption; a token to resume and a cap exist; the end of data is a normal end, never an error; and every tool that works without hand-written configuration works only where the convention is a standard (Google, Dropshot). What the sources disagree on, and this design decides: detection. Speakeasy, Stainless, AWS and Dropshot all need an explicit declaration (an extension, a config file or model data). Declarations are not available here (no extension), so detection is by convention and is deliberately narrow (section 4).

## 4. Detecting a paged operation with no vendor extension

A paged operation has exactly one **style**, found by the rules below. The detector runs once per operation in `AbstractBetterCodegen`, reads only native fields, and either produces a record or produces nothing. There is no generator option and no spec field to force or suppress it (O5).

Common preconditions, all required:

1. Method `GET`. A `POST` with a cursor in the body is not detected in v1 (O3).
2. Exactly one 2xx response with exactly one JSON media type (F1062 several-schema operations are out).
3. The item list is unambiguous: the body schema is an array, or an object with exactly one property whose schema is an array (the property is the **items field**; a `oneOf` item type is allowed, it is just the item type). Two array properties, or none, is ambiguous: nothing.
4. The operation is not streamed (`streaming.md`): an SSE or JSON Lines operation is never paged.

Parameter names are compared after lower-casing and removing `_` and `-`, so `pageToken`, `page_token` and `page-token` are one name. The lists below are closed; a new name is added to the document and the detector together.

| Style | Request signal (query parameters) | Response signal | End of data | Fallback when it does not hold |
| --- | --- | --- | --- | --- |
| **Cursor token** | one parameter named `cursor`, `pagetoken`, `nexttoken`, `continuationtoken`, `after`, `startingafter` or `marker`, schema `string` | a body property of the object named `nextcursor`, `nextpagetoken`, `nexttoken`, `continuationtoken`, `next`, `nextmarker` or `cursor`, schema `string` (nullable allowed), on the same object as the items field | the property is absent, null or the empty string | nothing |
| **Offset and limit** | a parameter named `offset` or `skip` with schema integer, and a parameter named `limit`, `pagesize`, `perpage` or `size` with schema integer | none required | a page with no items, or with fewer items than the limit that was sent; when the caller sent no limit, only an empty page | nothing |
| **Page number** | a parameter named `page` or `pagenumber` with schema integer that declares a `default` or a `minimum` (the first page number), and optionally a size parameter as above | none required; a body integer property named `totalpages`, `pagecount` or `pages` ends iteration at that page when present | a page with no items, or with fewer items than the size that was sent, or the page number past `totalpages` | nothing |
| **Link header** | none | the 2xx response declares a header named `Link` (any case) | the response carries no `rel="next"` link | nothing |

Rules that apply to all styles:

- **Both sides or nothing** for cursor tokens: a request parameter without a response property of the cursor family, or the reverse, is not paged. This is what makes name matching safe enough: a coincidental `after` filter on a list that returns no token is never picked up.
- **One style per operation.** If two styles match (for example `offset` and `cursor`, or a `Link` header with `page`), the operation is ambiguous and gets nothing. Never choose by priority.
- **Types decide.** A parameter with a matching name and the wrong type (an `offset` string, a `page` boolean) disqualifies the style.
- The `Link` style needs no parameter at all, because the next URL is in the response; the declared header is the only evidence the generator has, and a server that sends `Link` without declaring it is not detected.
- A required parameter of the family (a required `cursor`) is not detected: the first page would have no legal call. A required size parameter is allowed and is simply a required argument of the paged method.
- Not detected on purpose: a body-only total (`total` with no page signal), `from`/`to` ranges, time-window cursors (`since`, `until`), GraphQL-style `edges`/`pageInfo` connections, and a `next` URL in the body (Speakeasy's `url` type). Each is a later additive style (section 8).

### 4.1 The fixture: which operations qualify

`src/spec/resources/specs/petstore/openapi.yaml` was read operation by operation. **None qualifies today**, and the near misses show the rules working:

| Operation | Why it does not qualify |
| --- | --- |
| `findPetsByStatus` (`GET /pet/findByStatus`, array of `Pet`) | The array body fits, but the only integer query parameter is `2ndPage`, which is not in any list (and not `page`); there is no size parameter. No style matches: nothing. |
| `findPetsBornOn` (`GET /pet/born-on/{bornOn}`, array of `Pet`) | A list with no paging parameter and no `Link` header: nothing. |
| `searchStore` (`GET /store/search`, array of `Order`) | A list with `q`, `labels`, `at`; none is a paging name: nothing. |
| `testResponseHeaders` (`GET /test/response-headers`) | It declares a `Link` header whose example carries `rel="next"`, but the body is `type: object` with no array: precondition 3 fails, so nothing. |
| `getInventory`, `getGroupedCategories`, `getMatrix`, `getSwatchGroups`, `getTimestampGroups` and the other list-shaped reads | Arrays or maps with no paging signal: nothing. |

Consequence: to test any of this, stage 1 adds fixture operations with neutral names (`/items`, not petstore words, per the fixture rules): one per style (`listItemsByCursor`, `listItemsByOffset`, `listItemsByPage`, `listItemsByLink`), one ambiguous (`listItemsAmbiguous`, both `cursor` and `offset`), and one near miss that must stay unpaged (a list with an `after` filter and no token property). `FixtureVocabularyLeakTest` keeps the second spec honest.

## 5. The shared surface and the generator change

**Additive.** The existing operation method is untouched. A paged operation gains one more method, named `<operation>Paged` in the language's casing (`listItemsPaged`, `list_items_paged`, `ListItemsPaged`). Adding it never moves an argument, so the manual call stays the escape hatch and a false positive costs one extra method (O5).

**Arguments.** Exactly the operation's own arguments in the same order (S8), except the paging parameters of the style: `cursor`, `offset`, `page` are dropped from the required/optional lists and carried by the pager; the size parameter (`limit` and family) stays, because it is the page size. The per-call Options object (F827) is the same and applies to every page request. One new object, `PagerOptions`, with three fields and no defaults that limit anything:

| Field | Meaning |
| --- | --- |
| `startToken` | The opaque token, offset, page number or `Link` URL to start from instead of the first page (resumption; the same string a `Page` exposes as `nextToken`). |
| `maxPages` | Stop after this many page requests. Absent: no limit. |
| `maxItems` | Stop after this many items have been delivered. Absent: no limit. |

**Two views, one source.** The returned `Pager<T>` is itself the item iteration in the language's native type (section 6), and has one extra member, `pages()`, that yields `Page<T>` objects: `items` (the page's items), `nextToken` (string or none; for the `Link` style the next URL), `statusCode` and `headers` (as `ApiResult.headers`, D27 read-only copy). A caller who wants tokens, resumption or headers uses `pages()`; a caller who wants items iterates the pager. `Pager` and `Page` are named once for all twelve and are generated once per SDK, not per operation, with the item type as a type parameter where the language has generics.

**Generator change** (all in `AbstractBetterCodegen.java`, no language Codegen change expected; **not verified** that no `Better*Codegen` overrides the hooks used):

1. A new private `detectPagination(CodegenOperation)` returning an optional record `{style, requestParam, sizeParam, nextProperty, itemsProperty, itemType, firstPage}`.
2. The record goes into `vendorExtensions.op.pagination` (a generator-owned key, not `x-`), plus the boolean mirror `vendorExtensions.op.isPaged` for the Mustache section, next to the other operation flags in `getOperationContextFlags`.
3. Unit test in the mirror file of the changed source (`AbstractBetterCodegen` tests; the decorator cases live in `OperationDecoratorTest`, verified in `nullable-query.md`): one row per style, per ambiguity, per precondition, and per near miss of section 4.1.

Per-language templates: the api template renders the `Paged` method inside `{{#vendorExtensions.op.isPaged}}`; a new `pager` template renders `Pager`, `Page` and `PagerOptions` once; the README template gets one section "Pagination" with the same heading and prose in all twelve (character-level alignment), and its code example is copied from the generated signature.

## 6. Behaviour shared by all twelve (language-neutral promises)

Ids use a new area `pagination` (format `areaNNN | promise`), carried in test names as `bhvpagination001` in the language's casing. All apply to all twelve; the transport is the in-process server of each language's `DefaultApiClientUnitTest`, and the operation-level ones use the new fixture operations.

```
pagination001 | A paged call delivers the items of the first page in order without a second request until they are consumed
pagination002 | Items of consecutive pages arrive in one sequence in page order
pagination003 | A cursor style call sends the previous page's token as the cursor parameter and stops when the token is absent, null or empty
pagination004 | An offset style call advances the offset by the number of items received and stops on an empty page
pagination005 | An offset style call that sent a limit stops after a page shorter than the limit
pagination006 | A page number style call starts at the declared default (else minimum), adds one per page and stops on an empty or short page
pagination007 | A page number style call stops after totalpages when the response carries it
pagination008 | A Link style call follows rel="next" and stops when the response has none
pagination009 | A Link target is resolved against the URI of the response, accepts several rel values and parameter order in any case, and reads every Link line
pagination010 | A Link target on another origin than the one requested is refused with the typed error and never requested
pagination011 | The caller's arguments, per-call headers and timeout are sent on every page request, and the paging parameter is never taken from the caller's arguments
pagination012 | A page that fails raises the typed error from the next-item call, the items before it were already delivered, and iteration ends
pagination013 | A retryable status on a later page is retried under F823 for that page request only, and items already delivered are never redelivered
pagination014 | maxRetries 0 on the call turns retries off for every page request
pagination015 | Stopping early (break, close, drop, take) sends no further request and releases nothing the caller owns
pagination016 | maxPages stops after that many page requests and ends normally
pagination017 | maxItems delivers exactly that many items, ends normally and never requests a page it does not need
pagination018 | Cancellation aborts the request in flight or the wait for the next page and raises the cancellation error, never a timeout
pagination019 | The call timeout applies to each page request and never to the whole iteration
pagination020 | pages() yields one Page per request with its items, nextToken, status and read-only headers
pagination021 | startToken resumes at the page a Page's nextToken names, delivering the same items an uninterrupted run would
pagination022 | A token equal to the one just sent ends the iteration with a serialization error naming the parameter, so a server loop cannot spin forever
pagination023 | A response whose items field is missing or not an array raises a serialization error naming the field
pagination024 | An empty first page ends normally with no items
pagination025 | Two iterations of the same Pager object start again from the first page and never share state with each other
pagination026 | A paged operation gets the paged method and the manual method, an ambiguous or unmatched operation gets only the manual method
pagination027 | The string form of a Pager, a Page and PagerOptions shows style, page count and item count, never a token, a URL query or an item
pagination028 | Closing the client while iterating lets the page in flight finish and fails the next page request with the closed-client error
```

The promises in prose:

- **Lazy, one page at a time.** Nothing is requested when the method returns; the first request goes out when the first item (or page) is demanded, and the next page is requested only when the caller demands an item past the last one delivered. No read-ahead, no background fetch (a caller who wants concurrency starts several pagers). Same for languages whose iteration is eager by nature (php, ruby): they use a lazy construct.
- **Errors mid-iteration.** The failing page raises the typed error of the error contract exactly as a manual call would (same class, status, request method and path, F830), from the call that asked for the next item. Iteration is over afterwards. The caller resumes with `startToken` set to the `nextToken` of the last good page, which is why `pages()` exists. No error is swallowed, no partial list is returned as success.
- **Retries.** Each page request is a normal call: F823 applies to it with its own attempt count, and the end-to-end timeout of F827 covers that request. A retry never redelivers items, because a failed attempt delivered none.
- **Cancellation and early stop.** The language-native mechanism of S7 aborts the request in flight and ends the iteration with the cancellation error. Leaving the loop early is also native (break, return, `close`, `take`, dropping the value) and sends nothing further. No pager needs `close()` because it holds no connection between pages; where the language's iteration protocol has a close hook (java `Stream`, go yield return, python async generator, php generator destructor), it is honoured.
- **Limits.** `maxPages` and `maxItems` are the caller's caps; neither is on by default and the SDK sets no hidden ceiling (AWS and Azure both offer the same two caps).
- **Per-call options.** One Options object for the whole iteration; `headers`, `timeout`, `maxRetries` of F827 apply to every page. Authentication is asked again for every request (refresh works across pages).
- **No schema validation.** The pager does not check that a token looks like a token, that a page is not longer than the limit, or that offsets are consistent. It checks only what it needs to move on: the items field exists and is an array (`pagination023`), and a cursor did not repeat (`pagination022`).
- **Token and URL handling.** Tokens are opaque strings, URL-encoded as the query encoding of D52 requires (`%20` for a space). A `Link` target is used as given after resolving; credentials are never sent to another origin (`pagination010`, the same stance as the redirect rule of `streaming016`).
- **Stable on the wire.** The first request of a paged call is byte-identical to the manual call with the same arguments; later requests differ only by the paging parameter (or the `Link` URL).

## 7. Per language

All names follow the language's casing; `Item` is the operation's item type. Each sketch is the call-site view of `listItemsByCursor(limit)` and the shape of its return type; none was compiled. "Verified in" is deliberately absent: no template of this repository was read for this section, so every claim about a language feature is from the language, not from our templates (the section 10 stage 0 reads each template).

| Language | Native iteration type | Why |
| --- | --- | --- |
| java | `Pager<T> implements Iterable<T>`, with `stream()` (a lazy `Stream<T>` over the iterator) and `pages()` returning `Iterable<Page<T>>` | `Iterable` works in a for loop, `Stream` in pipelines; the SDK is synchronous (S7: interrupt cancels) |
| kotlin | `Flow<T>` (cold), `pages()` as `Flow<Page<T>>` | the operations are `suspend`; `Flow` is the coroutine idiom, `take(n)` and cancellation work natively |
| csharp | `IAsyncEnumerable<T>` with `[EnumeratorCancellation] CancellationToken`, `PagesAsync()` as `IAsyncEnumerable<Page<T>>` | `await foreach`, trailing token as S7 requires |
| php | `\Generator<int, Item>` from `listItemsPaged(...)`, `pages()` as `\Generator<int, Page>` | php is synchronous; generators are lazy and stop by being dropped |
| python | `AsyncIterator[Item]` implemented as an async generator, with `pages()`; usable in `async for` | the Python SDK is asyncio (S7); cancellation is task cancel |
| ruby | `Enumerator::Lazy` over `Enumerator.new`, `pages` as another lazy enumerator; `each` with a block also works | `first(n)`, `take(n)` and `each` are native; lazy by construction |
| node | `AsyncIterable<Item>` (an async generator), `pages(): AsyncIterable<Page<Item>>` | `for await`, `AbortSignal` in the per-call options (S7) |
| dart | `Stream<Item>`, `pages()` as `Stream<Page<Item>>` | `await for`, `take`, subscription cancel |
| go | `iter.Seq2[Item, error]`, `Pages() iter.Seq2[Page[Item], error]` | range-over-func, `ctx` first (S7); the Go floor must reach 1.23 (**not verified** against F1087 and the go manifest; if it does not, the fallback is a `Next(ctx) bool`/`Item()`/`Err()` cursor, one shape either way) |
| rust | `impl Stream<Item = Result<Item, Error>>` (a `Pager<T>` implementing `futures::Stream`), `pages()` the same over `Page<T>` | the crate-native async iteration; dropping the stream stops it; same new dependency as streaming (O6 there) |
| swift | `AsyncThrowingStream<Item, Error>` (or a `Pager<T>: AsyncSequence`), `pages()` | `for try await`, task cancellation |
| elixir | a lazy `Enumerable` built with `Stream.resource/3`, `pages/1` as another `Stream` | `Enum.take/2`, `Stream.take/2`; a typed exception is raised from the enumeration (a `Stream` cannot return a tuple per element, O9) |

Sketches:

```java
// java: Pager<Item> implements Iterable<Item>
for (Item item : client.items().listItemsByCursorPaged(50, options)) { use(item); }
List<Item> firstTen = client.items().listItemsByCursorPaged(50).stream().limit(10).toList();
for (Page<Item> page : client.items().listItemsByCursorPaged(50).pages()) { save(page.nextToken()); }
```

```kotlin
// kotlin: suspend entry, cold Flow
client.items.listItemsByCursorPaged(limit = 50).take(10).collect { use(it) }
client.items.listItemsByCursorPaged(limit = 50).pages().collect { save(it.nextToken) }
```

```csharp
// csharp: IAsyncEnumerable<Item>, token linked as S7
await foreach (var item in client.Items.ListItemsByCursorPagedAsync(50, options, cancellationToken)) { Use(item); }
await foreach (var page in client.Items.ListItemsByCursorPagedAsync(50).PagesAsync()) { Save(page.NextToken); }
```

```php
// php: \Generator
foreach ($client->items()->listItemsByCursorPaged(50) as $item) { use($item); }
foreach ($client->items()->listItemsByCursorPaged(50)->pages() as $page) { save($page->nextToken); }
```

```python
# python: async generator
async for item in client.items.list_items_by_cursor_paged(limit=50):
    use(item)
async for page in client.items.list_items_by_cursor_paged(limit=50).pages():
    save(page.next_token)
```

```ruby
# ruby: Enumerator::Lazy
client.items.list_items_by_cursor_paged(limit: 50).first(10).each { |item| use(item) }
client.items.list_items_by_cursor_paged(limit: 50).pages.each { |page| save(page.next_token) }
```

```ts
// node: async iterable
for await (const item of client.items.listItemsByCursorPaged({ limit: 50 }, { signal })) { use(item); }
for await (const page of client.items.listItemsByCursorPaged({ limit: 50 }).pages()) { save(page.nextToken); }
```

```dart
// dart: Stream
await for (final item in client.items.listItemsByCursorPaged(limit: 50)) { use(item); }
await client.items.listItemsByCursorPaged(limit: 50).pages().forEach((p) => save(p.nextToken));
```

```go
// go: iter.Seq2, ctx first
for item, err := range client.Items.ListItemsByCursorPaged(ctx, 50) {
	if err != nil { return err }
	use(item)
}
```

```rust
// rust: futures::Stream of Result
let mut items = client.items().list_items_by_cursor_paged(50, None, PagerOptions::default());
while let Some(item) = items.try_next().await? { use_item(item); }
```

```swift
// swift: AsyncSequence
for try await item in client.items.listItemsByCursorPaged(limit: 50) { use(item) }
for try await page in client.items.listItemsByCursorPaged(limit: 50).pages() { save(page.nextToken) }
```

```elixir
# elixir: lazy Stream, raises a typed exception mid-stream
api |> Items.list_items_by_cursor_paged(50) |> Stream.take(10) |> Enum.each(&use/1)
api |> Items.list_items_by_cursor_paged(50) |> Pager.pages() |> Enum.each(&save(&1.next_token))
```

Differences that are idiom, not defects: go and rust return errors in the sequence item; the exception languages raise from the next-item call; go takes `ctx` first and csharp a trailing token (S7); elixir raises where its operations return `{:ok, _}`, because a `Stream` cannot carry a tuple per element (O9); swift and rust need no `close`.

## 8. What is not covered, and later additive styles

POST bodies with a cursor (O3); a `next` URL in the body; GraphQL connections; time-window cursors; `total` counts surfaced on `Page` (a later additive field, not detection). A caller of these uses the manual call. Each can be added as a new style with its own detection table and ids, without changing any shape above.

## 9. Tests

- Detector: generator unit tests in the mirror file of `AbstractBetterCodegen`, rows for every style, every precondition, every ambiguity and every near miss of section 4.1, so a change to the lists is a failing row, not a silent drift.
- Wire and iteration behaviour (`pagination001-025`, `028`): the in-process server of each language's `DefaultApiClientUnitTest`, as table rows named with the ids, because the fixture server cannot hold per-test page sequences (the same finding as `streaming.md`, risk 1); the four languages not yet checked for an in-process server (php, rust, swift, elixir) are checked in stage 0.
- Operation level (`pagination026`, `027`, and presence of the paged method per golden): `BaseApiTest` and the petstore API tests, using the new neutral fixture operations of section 4.1. Per the repository rule no new test files; a new `Pager` source file would get a mirror test file per the global default, so the same one-time exception as `streaming.md` O7 is asked for in O8.
- Each id asserts what its promise says and nothing more. Goldens: all twelve regenerated and committed together, never a subset.

## 10. Recommended first step and order of work

**First step (one pull request, small, no public API):** stage 0 and stage 1 only, which can ship alone because they change no generated file except the goldens' fixture additions:

1. Stage 0: resolve O1 to O10 with the owner; read the twelve templates for the facts marked not verified (Go floor, in-process servers of php/rust/swift/elixir, the hooks `AbstractBetterCodegen` offers); compile the twelve sketches of section 7 against current goldens by hand.
2. Stage 1: the detector with its unit tests and the neutral fixture operations (including the ambiguous and near-miss ones), the `pagination` catalogue lines and a draft of `test-catalogue-exceptions.txt`, and the goldens regenerated. No template renders the paged method yet, so no SDK changes, and the detector's decisions are already reviewable in the unit tests.

Then, reusing the order of `streaming.md`: java as the reference (L), the languages nearest to it (kotlin, csharp, go, dart), then node, python, php, elixir, then ruby, rust, swift; a cross-language parity pass comparing public members by hand (the API guard #122 does not exist); then `docs/decisions.md`, `docs/types.md`, the twelve language pages and the `generators/AGENTS.md` decisions. All twelve release together (standing rule). Size is M per language once the java reference exists; it is smaller than streaming because no new transport path is needed: a paged call is a loop over the existing operation call.

## 11. Open questions for the owner (one verdict each)

- **O1. Method name and additive shape.** Verdict: `<operation>Paged`, added next to the unchanged manual method, never replacing it. A replaced signature would break every user of a list call and make a wrong detection destructive.
- **O2. How strict is name matching?** Verdict: closed name lists, matched after case and separator folding, and for cursors both sides (request parameter and response property) are required; widening a list needs a design change and a table row. The risk is a false negative (a real paged API left manual), which is cheap; a false positive is also cheap because it is additive.
- **O3. POST and body cursors.** Verdict: out of v1 (section 8). Many search APIs page by POST body; they stay manual until a later style adds them.
- **O4. Offset and page-number end detection.** Verdict: stop on an empty page, or on a page shorter than the limit when the SDK sent one. This can cost one extra request when the last page is exactly full, which is the standard price without a total; a `totalpages` property ends it sooner (`pagination007`).
- **O5. A way to force or suppress detection.** Verdict: none in v1 (no generator option, no spec field). Both directions are covered by the manual method staying; revisit with a real false positive as evidence.
- **O6. Page-number start.** Verdict: detect the page style only when the `page` parameter declares a `default` or a `minimum`, and start there; otherwise do nothing, because "page 0 or 1" is a guess and the owner rule is never to guess.
- **O7. Go floor.** Verdict: `iter.Seq2` (Go 1.23), consistent with `streaming.md`; if the manifest floor is lower and F1087 says to keep it, fall back to the `Next`/`Item`/`Err` cursor (one shape, stated in the go language page). **Not verified** against the manifest.
- **O8. One new mirror test file per language** for the `Pager` source file. Verdict: allow it for this change only, as asked for `event_stream` in `streaming.md` O7; the catalogue compares by id.
- **O9. Elixir errors.** Verdict: the stream raises the typed exception from the enumeration, and the manual call keeps returning `{:ok, _}`; `pages/1` follows the same rule.
- **O10. A repeated token.** Verdict: a cursor or `Link` URL equal to the one just sent ends the iteration with a serialization error (`pagination022`), not silently and not forever. This is a protocol guard against a server loop, not schema validation (S3).
- **O11. Cross-origin `Link` targets.** Verdict: refused with the typed error and never requested (`pagination010`), because the SDK would otherwise send the caller's credentials to a host the server named.
- **O12. Caps on by default.** Verdict: none; `maxPages` and `maxItems` are opt-in, and the README says an unbounded loop over a large collection is the caller's call.

## 12. Decisions to record in `generators/AGENTS.md` when accepted

Next free numbers after the current last decision, assigned at write time: the closed detection tables and the "ambiguous means nothing" rule; the `<operation>Paged` name and additive shape; `Pager`, `Page`, `PagerOptions` and their fields; the lazy and no read-ahead rule; mid-iteration errors, retries and cancellation (section 6); the repeated-token and cross-origin rules; the per-language idiom row added to the matrix; the `pagination` catalogue area; the one-time mirror test file exception if approved.

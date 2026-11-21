# Pagination design (issue #163)

Status: accepted by the owner on 2026-11-21 and recorded as decisions D123 and D124 in [docs/decisions.md](../decisions.md) and in the shared `generators/AGENTS.md`. Nothing here is built and no template was touched; this page is the design the implementation follows. It benchmarks how mature SDKs and generators paginate, says how a paged operation is detected from an OpenAPI document without a vendor extension in a template, and fixes one shape for all twelve SDKs (java, kotlin, csharp, php, python, ruby, node, dart, go, rust, swift, elixir). Section 0 lists what the owner decided; sections 1 to 6 are the design with the decisions written in; section 7 checks it against the 2025 and 2026 releases; sections 8 and 9 are the sources.

Source tags such as [S6] refer to the list in section 8. Every external claim carries a tag. Each source was fetched on 2026-11-20 unless section 8 marks it as partial, secondary or failed. Statements that are my own judgement are written as "Recommendation" or "My reading".

## 0. What the owner decided

Everything in the open-question list of the research draft (Q1 to Q13) is settled, except the test fixture (Q10), which is an open item.

| Q | Decision |
|---|---|
| Q1 name | `<operation>Pages` in the language's casing (C# `<Operation>PagesAsync`). |
| Q2 shape | `<op>Pages` returns a paginator with `items()` and `pages()`. There is no page wrapper as the primary type (no `Page` object with `getNextPage` as the thing the method returns). `pages()` yields a plain `Page<T>` value that holds the items, the next cursor and the same result the `...WithHttpInfo` variant returns; it is an element, not an API for walking. |
| Q3 repeated cursor | A cursor that comes back equal to the one just sent, or an offset that did not advance, raises a new `PaginationError`, a direct child of the SDK's root error (not a network error, not an API error). Stopping silently would truncate data. |
| Q4 totals | No `maxItems` option and no collect-all helper in v1. The language's own `limit`, `take`, `toList` and `Take` do it. |
| Q5 re-iteration | Iterating a paginator again restarts from the starting position, in every language. In Rust the two views take `&self` and build a fresh stream, so the rule holds there too (to be confirmed when built). |
| Q6 Elixir | The enumerable raises the SDK exception on a failed page; it does not return an error tuple mid-stream. |
| Q7 inference tiers | Ship tiers 1 (token cursor) and 3 (`Link` header) first. Tiers 2 (id cursor with `has_more`), 4 (offset or page number) and the new tier 5 (next URL in the body) come later, behind the explicit override option. |
| Q8 vendor extensions | The Java generator, never a template, reads the vendor extensions as hints into the same generator-owned model, and an explicit override option beats a hint. |
| Q9 out of v1 | A cursor in a POST body, and a cursor in a response header other than `Link`, are out of v1. HTTP QUERY (RFC 10008, June 2026) is the standards-backed later path for a read-only search that takes a body. |
| Q10 fixture | Open. See section 6.2. |
| Q11 cursor type | `nextCursor` has the type of the request parameter (string or integer). |
| Q12 F1100 | Rewritten to drop `x-pagination` (done in the java `AGENTS.md` with this design). |
| Q13 | Covered by Q7 and Q8. |

The experimental and above-floor features that could touch pagination are in section 7 ("What a consumer would have to switch on").

## 1. What the repository already says

- `src/main/java/.../generators/java/AGENTS.md` F1100: "no pagination is generated. When added, it lives in a separate `<operation>Pages`-style method returning an `Iterator` or `Stream`, driven by an `x-pagination` extension or a generator option, never by changing the base method's return type."
- `src/main/java/.../generators/csharp/AGENTS.md` ("Batch 7 follow-up"): pagination is "a separate additively named method per operation (`<Operation>PagesAsync` returning `IAsyncEnumerable<T>`), never a change to the return type of the existing data method."
- Issue #163: the progenitor comparison (#134) found pagination only through Dropshot vendor extensions and the ogen comparison (#131) found none; detection must work "without vendor extensions", an ambiguous spec must "do nothing and leave the manual call, never guess", and there is "no client-side validation, and no vendor extensions in templates".
- Root `AGENTS.md`: never depend on a spec-sourced `x-*` key in a template; a value the generator computes is handed to the template under a name without an `x-` prefix (`vendorExtensions.<name>`). The public API shape is fixed so later features are additive.
- `docs/decisions.md`: repeated response headers are joined with `, ` (so a `Link` header arrives as one comma-joined string, which matters in section 4), `...WithHttpInfo` returns status, headers and decoded body, and errors carry the method and the path without the query.
- The fixture `testResponseHeaders` in `src/spec/resources/specs/petstore/openapi.yaml` already declares a two-value `Link` header. No fixture operation has a cursor, offset or page parameter, so a paged fixture has to be added (open item, 6.2).

This design keeps the separate additive method, named `<operation>Pages` in the language's casing, and drops the `x-pagination` extension that F1100 named: F1100 is rewritten with this design (Q12).

## 2. Benchmark: how mature SDKs paginate

### 2.1 Comparison table

Columns: what the caller gets, whether the primary unit is the item or the page, sync versus async handling, how the next page is surfaced, and how errors and early stop behave.

| SDK / generator | API shape | Item or page | Sync / async | Next page surfaced as | Errors and early stop | Source |
|---|---|---|---|---|---|---|
| Stripe Ruby | call `auto_paging_each` on the list object | item | sync | implicit | not documented on the page fetched | [S1] |
| Stripe Python | `auto_paging_iter()`; "implements both AsyncIterable and Iterable"; `list_async()` for async | item | both | implicit | not documented | [S1] [S4] |
| Stripe PHP | `autoPagingIterator()` | item | sync | implicit | not documented | [S1] |
| Stripe Java | `autoPagingIterable()` returns `Iterable<T>` (a `PagingIterable`); `StripeCollection` also holds `hasMore` | item | sync | implicit; the collection is the first page | not documented | [S1] [S3] |
| Stripe .NET | `ListAutoPaging` with the same parameters as `List` | item | sync | implicit | not documented | [S1] |
| Stripe Node | `for await (const c of stripe.customers.list())`; `autoPagingEach(fn)` where returning `false` stops; `autoPagingToArray({limit})` where `limit` is mandatory | item | async | implicit | `break` stops; the docs say `limit` is required to "prevent runaway list growth" | [S1] [S2] |
| Stripe Go | `for pi, err := range sc.V1PaymentIntents.List(ctx, params)`; the older pattern is `i.Next()`, `i.Customer()`, `i.Err()` and is marked for deprecation; `Single: true` turns auto-paging off | item | sync, ctx | implicit | error is yielded per item; example returns on it | [S1] [S5] |
| AWS SDK Java v2 | `s3.listObjectsV2Paginator(req)` returns `ListObjectsV2Iterable`, an `Iterable` of pages; `.stream()` over pages; `.contents()` returns an `SdkIterable` of items (for-each or `.stream()`); async returns a `Publisher` (`ListTablesPublisher`), `.subscribe(Consumer)` returns `CompletableFuture<Void>`, `tableNames()` returns an `SdkPublisher` of items; manual token loop still documented | page, with item helper | both | implicit; the next token is in the response for manual use | `maxResults` is the page size, not a total; a total cap is `.items().stream().limit(n)` or `SdkPublisher.limit(n)`; async `Subscriber.onError` | [S6] |
| AWS SDK Go v2 | `NewListObjectsV2Paginator(client, params, optFns)`; `HasMorePages()`; `NextPage(ctx, optFns...)`; options `Limit` (page size) and `StopOnDuplicateToken` (stop "if the service returns a pagination token that matches the most recent token provided"); calling `NextPage` after the end returns "no more pages available" | page | sync, ctx | explicit loop | the error comes back from `NextPage`; stopping is leaving the loop | [S7] (generated source; the developer guide URL returned 404) |
| AWS SDK Rust | `client.list_objects_v2().bucket(..).into_paginator().page_size(10).send()` returns a `PaginationStream<Item>`; `try_collect()` collects all; `while let Some(r) = paginator.next().await` gives pages | page (with `PaginationStream<Item>` as the type) | async | implicit | each `next()` yields a `Result`; dropping stops | [S8] |
| boto3 | `get_paginator(op)` then `paginate(**params)` returns a `PageIterator`; `PaginationConfig` with `MaxItems` (total), `PageSize`, `StartingToken` (resume); `search(jmespath)` flattens and filters client-side | page | sync | implicit | `MaxItems` stops early; `StartingToken` resumes. The `build_full_result` helper is not on the page fetched | [S9] |
| AWS SDK Kotlin | `s3.listObjectsV2Paginated(req)` returns `Flow<ListObjectsV2Response>`; items by `transform { it.contents?.forEach(::emit) }`; cold flow | page | async (coroutines) | implicit | "Exceptions are transparent"; cancellation is cooperative; `take` stops | [S10] |
| AWS SDK Swift | `client.listBucketsPaginated(input:)` returns `PaginatorSequence<Input, Output>`, an `AsyncSequence` of pages; nothing is requested until iteration starts | page | async | implicit | errors surface during iteration; early break not covered on the page | [S11] |
| Soto (Swift) | `AWSClient.PaginatorSequence<Input, Output>: AsyncSequence` built from an input, a command closure, an output-token key path, an optional input-token key path and an optional `moreResults` key path; `next()` stops when the token is nil, equals the input token, or `moreResults` is not true | page | async | implicit | the equal-token rule is the loop guard | [S12] (source; no user guide found) |
| Google Java (gax) | `Page<T>`: `hasNextPage()`, `getNextPageToken()`, `getNextPage()` (null if none), `getValues()` (this page), `iterateAll()` (every element, later pages loaded on demand) | both | sync | explicit and implicit | `getNextPage()` returns null at the end | [S13] |
| Google Go | iterator with `Next()` returning `iterator.Done` at the end; `iterator.NewPager(it, pageSize, token)` and `Pager.NextPage(&slice)` return the next token, empty at the end; "you must not call Next on the iterator" when a Pager is used; `PageInfo.Remaining()` | item, with a Pager for pages | sync | explicit token for pages | the end sentinel is `Done`; `NextPage` never returns `Done` | [S14] |
| Google Python (api_core) | `page_iterator.Iterator` yields items; `.pages` yields `Page` objects; `next_page_token`, `num_results`, `max_results`; async `AsyncIterator`; "An iterator can only be started once" (second start raises `ValueError`) | item, with `.pages` | both | implicit | single-use | [S15] |
| Azure .NET | `Pageable<T>` / `AsyncPageable<T>` (an `IAsyncEnumerable<T>`) iterate items; `AsPages()` yields `Page<T>` with `Values` and `ContinuationToken` "used in AsPages call to resume enumeration"; `Take(n)` via System.Linq.Async makes the fewest calls | item, with `AsPages` | both | implicit | `ContinuationToken` resumes; warns that `CountAsync()` fetches everything | [S17] |
| Azure Java | `PagedIterable<T>` (sync) and `PagedFlux<T>` (async); items by for-each, `stream()`, `iterator()`; pages by `iterableByPage()` and `streamByPage()` (both accept a continuation token) and `byPage()` for the flux; `PagedResponse` carries status code and continuation token | item, with by-page | both | implicit | "if you unsubscribe from the `PagedFlux` at any time, there are no further calls to the service"; same call count by item or by page | [S18] |
| Azure Python | `ItemPaged` yields items; `by_page(continuation_token=None)` returns an iterator of pages (each page an iterator) | item, with by-page | sync (async twin not verified) | implicit | `continuation_token` resumes | [S19] |
| OpenAI Python | `for job in client.fine_tuning.jobs.list(limit=20)` auto-pages; `async for` on `AsyncOpenAI`; the awaited result is a page with `.has_next_page()`, `.next_page_info()`, `.get_next_page()`, `.data` and cursor `.after` | item (the returned page is itself iterable) | both | implicit and explicit | not documented | [S20] |
| OpenAI Node | `for await (const j of client.fineTuning.jobs.list({limit: 20}))`; `page.data`, `page.hasNextPage()`, `await page.getNextPage()` | item | async | implicit and explicit | `break` | [S21] |
| Anthropic Python / TS | same Stainless shape: `for batch in client.messages.batches.list(limit=20)`, `async for`, `has_next_page()`, `next_page_info()`, `get_next_page()`, cursor `last_id`; TS `for await`, `hasNextPage()`, `getNextPage()` | item | both | implicit and explicit | `break` | [S22] |
| Octokit | `octokit.paginate(route, params, mapFn)` returns all items as an array; `mapFn(response, done)` calls `done()` to stop; `paginate.iterator()` yields one response per page and supports `break`; the next page comes from the `Link` header's `rel="next"` | item array or page iterator | async | `Link` header | `done()` or `break` | [S23] |
| Fern generated | TS `Page<T>` is `AsyncIterable`; Python `Pager[T]` (generator, async twin); Go `*core.Page[T]` with `Iterator()` (`Next(ctx)`, `Current()`, `Err()`) and `GetNextPage(ctx)`; Java `SyncPagingIterable<T>` (for-each, `streamItems()`, `nextPage()`); C# `Pager<T>` as `IAsyncEnumerable<T>`; Rust `list_paginated` returning `AsyncPaginator<T>` as a `Stream` | item | both | implicit; explicit in Go and Java | Rust yields `Err` per item | [S24] |
| Speakeasy | generic shape `response = sdk.op(page=1)` then `response.next()` until it returns null | page | per language (only the generic form is documented on the page fetched) | explicit `next()` | null ends | [S25] |
| Stainless | server-declared schemes (`cursor`, `cursor_id`, `cursor_url`, `offset`, `page_number`) wired per method; generated SDKs are the OpenAI and Anthropic shapes above | item, page objects | both | implicit and explicit | as the OpenAI row | [S26] secondary, [S20] [S22] |
| progenitor (Rust) | for operations marked `x-dropshot-pagination`, an extra `<operation>_stream(...)` method returning `impl Stream<Item = Result<T, Error<E>>> + Unpin`, with an optional `limit` | item | async | implicit | `try_next()` yields `Ok(Some)`, `Ok(None)`, `Err` | [S27] |
| ogen (Go) | no pagination support found | none | none | none | none | [S28] |
| Kiota, AutoRest | `x-ms-pageable` (`nextLinkName`, `itemName`, `operationName`) is the long-standing precedent | n/a | n/a | n/a | n/a | [S31] secondary |

### 2.2 What the table shows

1. Two families, both mature. AWS (every language) makes the page the primary unit and adds item helpers (`contents()`, `items()`, `flatMap`, `transform`). Stripe, OpenAI, Anthropic, Azure, Octokit, Fern and progenitor make the item the primary unit and add a page escape hatch (`by_page`, `AsPages`, `iterableByPage`, `pages`, `getNextPage`, `paginate.iterator`). Google does both through one object (`iterateAll()` plus `getNextPage()`). Every one of them offers both views. No mature SDK offers only one. [S6][S8][S9][S10][S17][S18][S19][S20][S23][S13]
2. The language protocol is the API. Java is `Iterable`, .NET `IAsyncEnumerable`, Kotlin `Flow`, Swift `AsyncSequence`, Rust `Stream`, Python `__iter__` and `__aiter__`, Node `AsyncIterable`, Go `iter.Seq2` (Stripe's current Go client moved to range-over-func [S5]; AWS Go and Google Go keep the older explicit `HasMorePages/NextPage` and `iterator.Done` shapes [S7][S14], which predate Go 1.23). Nobody invented a bespoke loop API where a native protocol exists.
3. Laziness is universal and early stop costs nothing. AWS Swift makes no request until iteration starts [S11]; Azure Java makes no call after you unsubscribe [S18]; Kotlin flows are cold [S10]; Azure .NET `Take(n)` "will make the fewest service calls required" [S17]. Between pages no connection is open, so unlike streaming there is nothing to close.
4. Page size and total are different knobs. AWS says `maxResults` is items per page and "you still get all matching items" [S6]; boto3 separates `PageSize` from `MaxItems` [S9]; Stripe separates the list `limit` from `autoPagingToArray({limit})` [S2]. Eager collect-all helpers exist (Stripe `autoPagingToArray`, Octokit `paginate`, boto3 `build_full_result`) but Stripe makes the total cap mandatory and Azure's guide warns against client-side `CountAsync()` [S2][S17].
5. Resume is a first-class need. boto3 `StartingToken`, Azure `continuation_token` and `ContinuationToken`, Google `PageInfo.Token`, Google Go `NewPager(..., pageToken)` all let a caller restart from a saved position [S9][S17][S18][S19][S14]. Stripe and OpenAI resume by passing the cursor parameter to `list` again, because the auto-pager starts from whatever the first call asked for [S1][S20].
6. Loop guards exist because servers misbehave. AWS Go has `StopOnDuplicateToken` as an option (off by default) [S7]; Soto stops when the output token equals the input token [S12]; Google Python is one-pass [S15]. AIP-158 says an empty token is "the _only_ way to communicate end-of-collection", and that a server may return fewer results, even zero, before the end, so an empty page alone must not end iteration [S16].
7. Only the AWS Go and Google Go shapes are explicit-loop. Both predate range-over-func.

### 2.3 How the next page is found, by SDK

| Style | Examples | Source |
|---|---|---|
| Opaque token in the body, fed back as a query parameter | AWS `NextToken` / `ContinuationToken`, Google `next_page_token` and `page_token`, Smithy `inputToken` / `outputToken` | [S7] [S16] [S30] |
| ID of the last item plus a "more" flag | Stripe (`starting_after`, `has_more` in `StripeCollection`), OpenAI (`after`, cursor `.after`), Anthropic (`last_id`) | [S3] [S20] [S22] |
| Next-page URL in the body | `x-ms-pageable.nextLinkName`, Fern `next_uri`, Speakeasy `url` type | [S31] [S24] [S25] |
| `Link` header with `rel="next"` | Octokit | [S23] |
| Offset or page number plus size | Speakeasy `offsetLimit` (end when the results array is empty), Fern `offset` with `step` | [S25] [S24] |

## 3. Options and how each maps to the twelve languages

### 3.1 The options

| Option | What it is | Verdict |
|---|---|---|
| A. Page object returned by the base method | The existing method returns a `Page<T>` with `hasNext`/`getNextPage` and is itself iterable (Stripe, OpenAI, Anthropic, Fern, Google Java) | Rejected for the existing method: it changes its return type, which F1100 and the C# rule forbid. Acceptable as the element type of a separate method (section 5). |
| B. Items-only lazy sequence | A new method returns only items (progenitor `_stream`, Azure's default view) | Too thin alone: loses status, headers, totals and the resume cursor, and the AWS family shows callers want pages. |
| C. Paginator with an item view and a page view | A new method returns one object that iterates items and has `pages()` for pages (AWS Java `Iterable` plus `contents()`, Azure `PagedIterable` plus `iterableByPage`, Google `iterateAll` plus `getNextPage`) | Recommended. |
| D. Explicit cursor object (`hasMorePages` / `nextPage`) | AWS Go and Google Go shape | Rejected as the shared shape: it makes every caller write the loop #163 says they are tired of writing. It remains reachable through `pages()`. |
| E. Callback / visitor | Octokit `mapFn(response, done)`, Stripe `autoPagingEach`, AWS Java `subscribe(Consumer)` | Not a primary shape. Ruby's block form of `each` is this idiom for free. |
| F. Reactive publisher | AWS Java async `Publisher` | Later adapter for Java, not the base (our Java client is blocking). |
| G. Eager collect-all | Octokit `paginate()`, Stripe `autoPagingToArray`, boto3 `build_full_result` | Not generated. Unbounded memory; Stripe itself forces a `limit`. The language's own collect (`toList`, `list()`, `collect`) does it when a caller wants it. |

### 3.2 Idiom per language for option C

All of these are lazy, make no request until the first read, fetch the next page only when the consumer asks for it, and need no close.

| Language | Method returns | Item view | Page view | Native stop | Existing idiom it follows |
|---|---|---|---|---|---|
| java | `Paginator<T>` implements `Iterable<T>` | for-each, `stream()` | `pages()` returns `Iterable<Page<T>>` | `break`, `Stream.limit` | AWS Java `Iterable` + `stream()` [S6]; Azure `PagedIterable` [S18] |
| kotlin | `Paginator<T>` implements `Flow<T>` | `collect`, `take` | `pages()` returns `Flow<Page<T>>` | cancellation of the collector, `take` | AWS Kotlin `Flow` [S10] |
| csharp | `Paginator<T>` implements `IAsyncEnumerable<T>` | `await foreach` | `Pages()` returns `IAsyncEnumerable<Page<T>>` | `break`, token, `Take` | Azure `AsyncPageable` and `AsPages` [S17]; matches the recorded `IAsyncEnumerable<T>` default |
| php | `Paginator` implements `IteratorAggregate` (generator), `@template T` in docblocks | `foreach` | `pages()` returns `Generator<Page>` | `break` | no PHP benchmark was fetched; generators are the PSR-friendly lazy form (My reading) |
| python | `Paginator[T]` with `__iter__`; async facade `AsyncPaginator[T]` with `__aiter__` | `for`, `async for` | `pages()` iterator | `break` | Stripe `auto_paging_iter` [S4], OpenAI [S20], Azure `by_page` [S19] |
| ruby | `Paginator` includes `Enumerable` | `each`, `lazy` | `pages` returns an `Enumerator` | `break`, `first(n)`, `take` | no Ruby benchmark was fetched except Stripe `auto_paging_each` [S1] |
| node | `Paginator<T>` implements `AsyncIterable<T>` | `for await` | `pages()` returns `AsyncIterable<Page<T>>` | `break` (calls `return()`) | Stripe, OpenAI, Anthropic, Fern, Octokit `paginate.iterator` [S2][S21][S22][S24][S23] |
| dart | `Paginator<T>` | `items()` returns `Stream<T>` | `pages()` returns `Stream<Page<T>>` | cancel the subscription, `take` | no Dart benchmark found; `Stream` is the language's async sequence (My reading) |
| go | `*Paginator[T]` | `Items()` returns `iter.Seq2[T, error]` | `Pages()` returns `iter.Seq2[*Page[T], error]` | `break` in `range` | Stripe Go current shape [S5]; the Go floor in the manifest is 1.25 per the earlier streaming page |
| rust | `Paginator<T>` | `items()` returns `impl Stream<Item = Result<T, Error>>` | `pages()` returns `impl Stream<Item = Result<Page<T>, Error>>` | drop | AWS Rust `PaginationStream` [S8]; progenitor `_stream` [S27]; Fern `Stream` [S24] |
| swift | `Paginator<T>: AsyncSequence` | `for try await` | `pages()` returns an `AsyncSequence` of `Page<T>` | `break`, task cancellation | AWS Swift `PaginatorSequence` [S11]; Soto [S12] |
| elixir | `%Paginator{}` implements `Enumerable` | `Enum` / `Stream` functions | `Paginator.pages/1` returns a `Stream` | `Enum.take`, `Enum.reduce_while` | no Elixir benchmark found; `Stream.resource` is the lazy form (My reading). Open question Q6 on errors. |

Method names follow the recorded default: `<operation>Pages` in the language's casing (`listItemsPages`, `list_items_pages`, `ListItemsPages`, C# `ListItemsPagesAsync` because the other C# operations carry the `Async` suffix). The `Paginator<T>` object has the same two accessors, `items()` and `pages()` (C# `Items()`, `Pages()`), in all twelve languages, even where the object is also directly iterable. That is the single shape; only the sequence type differs.

## 4. How pagination is detected from a specification

### 4.1 What the generators do

| Generator | Detection | Source |
|---|---|---|
| openapi-generator (stock) | No client pagination. For the Spring server generators only: option `autoXSpringPaginated` adds `x-spring-paginated` to operations that have query parameters `page` and `size` (and `sort` for `page-size-sort`); `substituteGenericPagedModel` detects response schemas with a `content` array and a `page` metadata property. The extension is documented as "ignored for other (client) libraries". Open issues ask for more (#2493, #5753, #14062). | [S29] |
| ogen | None. The README has no pagination section; a search of its issues for "pagination" found nothing relevant. This is evidence of absence, not a statement by the maintainers. | [S28] |
| progenitor | Only operations carrying `x-dropshot-pagination`. Then it requires query parameters `page_token` and `limit`, every query parameter optional, no raw-body parameter (a streamed body "can only be streamed once"), exactly one 2xx response typed as a struct with exactly two properties named `items` and `next_page`. Otherwise it silently does nothing. | [S27] |
| Stainless | Explicit configuration: pagination schemes (`cursor`, `cursor_id`, `cursor_url`, `offset`, `page_number`) declared once, a method opts in with `paginated: <scheme>`, and spec properties tagged with `x-stainless-pagination-property` and a purpose (for example `next_cursor_id_param`, `cursor_item_id`). | [S26] secondary: the page fetched returned an unrelated announcement; this comes from a search-result summary of the official docs |
| Speakeasy | Explicit `x-speakeasy-pagination` on the operation: `type` (`offsetLimit`, `cursor`, `url`), `inputs` (name, location `parameters` or `requestBody`, role `page`, `offset`, `limit`, `cursor`), `outputs` as JSONPath (`results`, `numPages`, `nextCursor`, `nextUrl`). "The page doesn't describe automatic detection." | [S25] |
| Fern | Explicit `x-fern-pagination` with `cursor` / `offset` / `next_uri` / `results` / `step` pointing at `$request.*` and `$response.*` | [S24] |
| Kiota, AutoRest | `x-ms-pageable` with `nextLinkName`, `itemName` (default `value`), `operationName`; a validator checks the named property exists in the 200 schema | [S31] secondary, [S32] |
| AWS (all SDKs), Smithy | The service model: trait `smithy.api#paginated` with `inputToken`, `outputToken`, `items`, `pageSize`; "Smithy clients SHOULD provide abstractions that can be used to automatically iterate over paginated responses" | [S30] |
| Google client libraries | Structural convention from AIP-158: request `page_size` and `page_token`, response `next_page_token` (empty at the end) with the repeated field first; the page says clients recognise these from the method shape | [S16] |
| Octokit | The response `Link` header (`rel="next"`) at run time, no spec involved | [S23] |

My reading: nobody infers pagination from an arbitrary OpenAPI document. Either a vendor extension or a model trait declares it (Speakeasy, Fern, Stainless, progenitor, Kiota, Smithy), or a closed convention does (Google AIP-158), or the runtime follows a header (Octokit). The only name-based inference in the stock generator is the Spring server option, which is the precedent for putting the name lists behind a generator option.

### 4.2 Detection: conservative inference, vendor extensions as hints, an explicit override

An OpenAPI document has no standard pagination keyword, so detection is a Java-side rule in `AbstractBetterCodegen` that computes a generator-owned context object (for example `vendorExtensions.op.pagination`, no `x-` prefix, per the root rule) which the twelve templates render. It never reads an `x-*` key in a template.

Eligibility, all required, otherwise the operation gets no `Pages` method and generation logs one line naming the operation and the reason:

1. Method is GET. (POST search endpoints with a cursor in the body are out of v1; HTTP QUERY, RFC 10008, is the later path, Q9 and section 7.)
2. Exactly one 2xx response, with an `application/json` body that is an object (after `allOf` flattening) or, for the `Link` style only, an array.
3. Exactly one property whose schema is an array is the item list; another array-valued property makes it ambiguous.
4. Exactly one style below matches, and exactly one request-parameter candidate and one response candidate exist for it. Two candidates means no match.
5. The cursor, offset or page parameter is an optional query parameter (a required one has no first-request value).

Styles, in priority order. Names are compared case-insensitively with `_` and `-` removed.

| Tier | Style | Request signal (query) | Response signal | Ends when | Based on |
|---|---|---|---|---|---|
| 1 | token cursor | one of `cursor`, `after`, `page_token`, `next_token`, `continuation_token`, `marker` | one string property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `nextcontinuationtoken`, `continuation_token`, `next`, optional or nullable | the property is absent, null or empty | AIP-158 [S16], Smithy [S30], AWS [S7] |
| 2 | id cursor | `starting_after` or `after` or `after_id` | boolean `has_more` AND a string property `last_id` or `next_cursor`; if only item `id`s exist and no such property, the operation is left manual | `has_more` is false | Stripe, OpenAI, Anthropic [S3][S20][S22] |
| 3 | `Link` header | none | the 2xx response declares a `Link` header | no `rel="next"` | Octokit [S23] |
| 4 | offset and limit, or page and size | `offset` with `limit`, or `page` with one of `per_page`, `page_size`, `size`, `limit` | none required; `has_more` / `hasMore` is used if declared | the items array is empty, or the declared `has_more` is false | Speakeasy `offsetLimit` ends on an empty results array [S25] |
| 5 | next URL in the body | none | one string property with `format: uri` or `uri-reference`, or named `next_url`, `next_uri`, `nextlink`, `next_link`, optional or nullable (a plain string named `next` is tier 1, a token) | the property is absent, null or empty | Fern `next_uri`, `x-ms-pageable.nextLinkName`, Speakeasy `url` [S24][S31][S25] |

Order of shipping (Q7): tiers 1 and 3 are inferred and generated first. Tiers 2, 4 and 5 are the weakest inferences (or, for 5, new); they come later and are reached through the override option or a vendor hint (below) before any inference for them is switched on. Tier 5 follows the same origin check as tier 3: a next URL on another scheme or origin is refused.

Rule for offset: end on an empty page, never on a short page, because AIP-158 allows a server to return fewer items than requested before the end [S16]. This costs one extra request; a declared `has_more` avoids it.

The page-size parameter (`limit`, `page_size`, `max_results`) is not removed or renamed: it stays a parameter of the paginated method and is repeated unchanged on every page, as in AWS where it "sets the maximum number of items in each page" [S6].

Override. A generator option (not a spec key; the names `paginationOverrides` and `pagination` are proposed), maps an `operationId` to `{style, requestParam, responseProperty, itemsProperty}` or to `none`, and a global `pagination=off` disables the feature. This is how a spec author says "this one is paged" or "this one only looks paged" when inference stays silent or is wrong. It is the analogue of Smithy's `paginated` trait members [S30] and of progenitor's explicit marker [S27], held outside the spec.

Vendor hints (Q8, decided). The Java generator, never a template, reads the industry extensions that specs in the wild already carry (`x-ms-pageable`, `x-fern-pagination`, `x-speakeasy-pagination`, `x-stainless-pagination-property`, `x-liblab-pagination`, APIMatic `x-pagination`, `x-dropshot-pagination`) as hints and maps them into the same generator-owned model. The root rule forbids templates from depending on them, not Java code. Precedence: the override option, then a vendor hint, then inference. A hint that names a style whose tier has not shipped (2, 4, 5) takes effect when that tier ships. `x-pagination` of APIMatic is the name F1100 used to mention, with different semantics, which is why F1100 is rewritten.

Link-header details that need care:

- `docs/decisions.md` says repeated headers are joined with `, `, so a Link value reaches the paginator as one string. The parser must follow RFC 8288 (angle-bracketed URI, `rel` parameter, commas inside the URI or quoted strings). The fixture already carries a two-value `Link` example.
- The next URL must be refused if it changes scheme or origin, because following it would send the credentials to another host. Redirect handling in the SDK already strips credentials across origins; a `Link` hop must be at least as strict.
- A Link-style `Pages` method has no starting-cursor parameter, so it cannot resume from a saved position in v1.

## 5. The single shape

### 5.1 Surface

For each paged operation `listItems(params..., options)` the generator adds, and does not change anything else:

```
listItemsPages(params..., options)  ->  Paginator<Item>      // same parameters, same order, same options
Paginator<Item>.items()             ->  the language's lazy sequence of Item
Paginator<Item>.pages()             ->  the language's lazy sequence of Page<Item>
Page<Item>:  items, nextCursor, result
```

- Same parameters as the base method, including the cursor, offset or page parameter, which becomes the starting position. Resuming after a failure is `listItemsPages(..., cursor = lastPage.nextCursor)`. No separate resume API (compare boto3 `StartingToken` and Azure `continuation_token`, which this subsumes) [S9][S19].
- `Page<Item>` has `items`, `nextCursor` (typed like the request parameter, null or absent at the end) and `result`, which is the same value the `...WithHttpInfo` variant returns (status, headers, decoded body). No new response type; headers and totals stay reachable.
- The method itself performs no request. The first request happens on the first read of items or pages.
- Base method unchanged. The `ApiClient` transport contract is unchanged. A page request is an ordinary buffered call, so retries, redirects, the end-to-end timeout (default 10 seconds), per-call options, redaction, authentication refresh and tracing apply to each page exactly as to a single call.
- Iteration state is in the paginator. Re-iterating starts again from the starting position (the iterable, cold flow and async-sequence languages), never from where the last loop stopped. In Rust the two views take `&self` and build a fresh stream each time, so the rule holds there too (Q5, decided).

Exact names:

| Language | Method | Item view | Page view |
|---|---|---|---|
| java | `listItemsPages(...)` | `for (Item i : p)`, `p.stream()` | `p.pages()` |
| kotlin | `listItemsPages(...)` | `p.collect { }` or `p.items()` | `p.pages()` |
| csharp | `ListItemsPagesAsync(..., CancellationToken ct = default)` | `await foreach (var i in p)` or `p.Items()` | `p.Pages()` |
| php | `listItemsPages(...)` | `foreach ($p as $i)` or `$p->items()` | `$p->pages()` |
| python | `list_items_pages(...)` | `for i in p`, `async for i in p` or `p.items()` | `p.pages()` |
| ruby | `list_items_pages(...)` | `p.each { }`, `p.lazy` or `p.items` | `p.pages` |
| node | `listItemsPages(params, options)` | `for await (const i of p)` or `p.items()` | `p.pages()` |
| dart | `listItemsPages(...)` | `p.items()` | `p.pages()` |
| go | `ListItemsPages(ctx, ...)` | `range p.Items()` | `range p.Pages()` |
| rust | `list_items_pages(...)` | `p.items()` | `p.pages()` |
| swift | `listItemsPages(...)` | `for try await i in p` or `p.items()` | `p.pages()` |
| elixir | `list_items_pages(client, params, opts)` | `Enum` on `p` or `Paginator.items(p)` | `Paginator.pages(p)` |

### 5.2 Behaviours (the semantics every language implements)

- Lazy and demand-driven: no request before the first read, one request per page, the next request only when the consumer needs more, no read-ahead. [S11][S18][S10]
- End: by the style's end rule in section 4.2, never by an empty page alone for token and Link styles. [S16]
- Errors: a failed page request raises (or yields, in go and rust) the SDK error for that failure from the read that needed it, after the items of earlier pages were delivered. The iteration is then finished. The SDK does not skip a page and does not continue. Retries inside the page request follow the existing rules first.
- Per-page budget: the per-call timeout, retry count and headers apply to each page request, not to the whole iteration. A caller who wants an overall limit cancels.
- Cancellation uses each language's existing mechanism (S7): `ctx` in go, `CancellationToken` in csharp, `signal` in node, coroutine cancellation in kotlin, task cancellation in swift and python. It aborts the in-flight page request and is never reported as a timeout.
- Early stop: leaving the loop sends nothing further and leaves nothing to close, because no connection stays open between pages.
- Loop guard: if a page returns the same cursor that was sent, or an offset that did not advance, iteration stops with a `PaginationError` (Q3, decided), mirroring AWS Go `StopOnDuplicateToken` and Soto [S7][S12], but loud rather than silent. `PaginationError` is a direct child of the SDK's root error.
- Decoding is the base method's decoding: an unknown enum value fails with the SDK serialization error, mid-iteration if it appears on page 3.
- Authentication is applied per page, so a token refreshed during a long iteration is used on the next page.
- Errors that carry a request path never include the query, so a cursor or a `Link` URL query is not leaked (existing rule "Errors say which request failed").
- A paged operation that is also streamed (see the streaming page) is excluded: progenitor refuses to paginate an operation with a streamed body for the same reason [S27].

## 6. Test ids and open questions

### 6.1 Catalogue ids

Format `areaNNN | promise` (see `src/spec/resources/test-catalogue.txt`). The area is new, `pagination`, separate from the `streaming` and `sse` areas of the streaming design. Every id needs a test in all twelve languages or an entry in `test-catalogue-exceptions.txt` with a reason. The ids are proposed here and are added to `src/spec/resources/test-catalogue.txt` with the implementation, not before.

```
pagination001 | A paged operation reads no page until the first item or page is requested
pagination002 | A token-cursor operation yields every item of every page in order and sends the next page's cursor
pagination003 | The next request is sent only when the consumer asks for more items
pagination004 | An offset-and-limit operation advances the offset by the items received and ends on an empty page
pagination005 | A page-number operation starts at the supplied page and increments it
pagination006 | An id-cursor operation ends when has_more is false
pagination007 | A Link-header operation follows rel=next and ends when it is absent
pagination008 | A Link next URL on another origin is refused and no credentials are sent to it
pagination009 | An empty page with a next cursor continues and an absent, null or empty cursor ends
pagination010 | Pages yields one page per response with its items, status, headers, decoded body and next cursor
pagination011 | The cursor passed to the paged method is the starting position and the first request sends it
pagination012 | Resuming with the next cursor of a page continues with the following page
pagination013 | Every other parameter, including the page size, is repeated unchanged on each page request
pagination014 | A cursor equal to the one just sent, or an offset that did not advance, raises a PaginationError instead of looping
pagination015 | An error on a later page is raised after the earlier pages' items were delivered and ends iteration
pagination016 | A 503 on the second page is retried under the retry rules and iteration continues
pagination017 | The per-call timeout, headers and retry count apply to each page request
pagination018 | Stopping early sends no further request and leaves nothing to close
pagination019 | Cancelling mid-iteration aborts the in-flight page request and is not reported as a timeout
pagination020 | Iterating a second time starts again from the starting position
pagination021 | An unknown enum value on a later page fails with the serialization error
pagination022 | A refreshed credential is used for the next page
pagination023 | An error from a Link-driven page request carries the path without the query
pagination024 | An operation with two candidate cursors, two array properties, a non-GET method or several 2xx bodies gets no paged method
pagination025 | A paged method has the same parameters in the same order as the base method and the base method is unchanged
pagination026 | The generator option overrides inference and can switch an operation or the whole feature off
pagination027 | Headers and options given to the paged method are sent on every page request
pagination028 | The cursor for the next page is taken from the accepted response, never from a retried attempt
pagination029 | A next URL in the body is followed and a next URL on another origin is refused (ships with tier 5)
pagination030 | A vendor extension on an operation is read as a hint, and the override option wins over it
pagination031 | The repeated-cursor failure is a PaginationError and is not a network error or an API error
pagination032 | An operation that is streamed is never also given a paged method
```

Unit tests for the detector (names, the tiers, ambiguity) belong in the Java test of the detector class, mirroring its source file, per the root test-layout rule. Spec-level behaviour (the 32 ids above; `pagination008`, `023` and `029` ship with tiers 3 and 5) lives in the existing generated test templates, not in new files.


### 6.2 Open items

- **Test fixture (Q10, open).** The shared Chasm mock answers from the spec alone, and when the design was written it was not verified whether it can return a different response per cursor value (the streaming design found that it is stateless and cannot serve a chunked body or a dropping SSE feed). Options: extend Chasm so that a response can depend on a query value; keep an in-process stateful server in the existing `*ApiClientUnitTest` templates for the paged behaviours; or add a stateful fixture operation. Go does not need Chasm: Go 1.27 adds `httptest.NewTestServer` on an in-memory network for `testing/synctest`, so a per-cursor server can be tested deterministically in the Go test templates. The owner decides which of the three the other eleven use.
- **Page element (Q2).** `Page<T>` stays as the element `pages()` yields. If the owner meant that even that value must go (items only, with the result reachable another way), `pages()` would have to yield the `...WithHttpInfo` result type directly.
- **Rust restart (Q5).** The `&self` views are a design choice that makes the restart rule hold in Rust; it is confirmed or changed when the Rust paginator is written.

## 7. Check against the 2025 and 2026 releases (as of 2026-11-21)

The full check, with confidence tags and its list of unverified items, is section 12 of the [streaming design](streaming.md). These are the findings that touch pagination.

1. HTTP QUERY is RFC 10008 (June 2026, Proposed Standard, HIGH) and OpenAPI 3.2 added the `query` operation. A read-only search that takes a body should be a QUERY operation, so the "GET only" eligibility rule has a standards-backed successor: a cursor in the body of a QUERY operation is a later tier. Out of v1 (Q9). swift-openapi-generator 1.14.0 already added QUERY.
2. OpenAPI 3.2 has no pagination keyword (HIGH). The only pagination-shaped text is a collection example (4.21.2, modelling Link headers) showing `first`, `prev`, `next` and `last` as a linkset in a body or a `Link` header, which supports tier 3 and not a spec-level cursor. Arazzo 1.1.0 (2026-05-17) mentions pagination only in an example. No standardised `x-pagination` exists; vendors still use their own (`x-fern-pagination`, `x-speakeasy-pagination`, `x-stainless-pagination-property`, `x-liblab-pagination`, APIMatic `x-pagination`, `x-ms-pageable`, `x-dropshot-pagination`).
3. Vendors (MED): Fern added `nextUri` and `nextPath` conversion (February 2026) and fixed a TypeScript case with the page property nested in a request body; Speakeasy has URL pagination for C# and Java (v1.747.0) and a cursor input inside a nested request body (v1.726.0); nobody added inference. The next-URL-in-body style is therefore added to this design as tier 5 (4.2), and the cursor-in-body style stays deferred.
4. Go: 1.27 allows a method to declare type parameters, so `func (c *Client) ListItemsPages` could be generic; 1.27 also adds `httptest.NewTestServer` on an in-memory network for `testing/synctest`, which answers Q10 for Go (a server that answers a different page per cursor, deterministic, no Chasm). The SDK's Go floor is 1.25, so generic methods are not used (an open point if the floor is raised).
5. Ruby 4.0: `Enumerator.produce` accepts `size:`, useful for the paginator `Enumerator` (an unknown or infinite size can be declared). Ruby floor not read; not used unless the floor is 4.0 or later.
6. PHP 8.5 (2025-11-20): the URI extension (`Uri\Rfc3986\Uri`) could parse the `Link` `rel="next"` target and compare origins without a hand-written URL parser. The SDK's PHP floor is 8.3, so it is not used (open for the owner if the floor is raised).
7. Rust: `AsyncIterator` is still nightly (tracking issue rust-lang/rust#79024 open), so `items()` and `pages()` return `futures::Stream` types, which are stable. Elixir 1.19 and 1.20 RC type-check protocol dispatch more strictly, so the `Enumerable` implementation of the paginator must be warning-free under both.
8. No SDK in the benchmark changed its pagination shape in 2025 or 2026 in a way that argues against option C.

### What a consumer would have to switch on

| Feature | State | Consumer needs | Recommendation | Open for the owner |
|---|---|---|---|---|
| Rust `AsyncIterator` | nightly | a nightly toolchain | keep `futures::Stream` | yes, same point as in the streaming design |
| Go generic methods (1.27) | stable, above the floor | Go 1.27 | not used | yes, if the Go floor is raised |
| Ruby `Enumerator.produce(size:)` | Ruby 4.0 | Ruby 4.0 | not used unless the floor allows | no |
| PHP 8.5 URI extension | stable, above the floor | PHP 8.5 | not used | yes, if the PHP floor is raised |


## 8. Sources

Status: OK = fetched and read; PARTIAL = fetched but the relevant part was truncated or thin; SECONDARY = summary from a search result, official page not retrieved; FAILED = could not be fetched.

| Tag | URL | Status and note |
|---|---|---|
| S1 | https://docs.stripe.com/api/pagination/auto | OK |
| S2 | https://raw.githubusercontent.com/stripe/stripe-node/master/README.md | OK |
| S3 | https://raw.githubusercontent.com/stripe/stripe-java/master/src/main/java/com/stripe/model/StripeCollection.java | OK (source; the README has no pagination text) |
| S4 | https://raw.githubusercontent.com/stripe/stripe-python/master/README.md | OK (only the async example) |
| S5 | https://raw.githubusercontent.com/stripe/stripe-go/master/README.md | OK |
| S6 | https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/pagination.html | OK |
| S7 | https://raw.githubusercontent.com/aws/aws-sdk-go-v2/main/service/s3/api_op_ListObjectsV2.go | OK (generated source). The developer guide URLs https://aws.github.io/aws-sdk-go-v2/docs/making-requests/ and https://docs.aws.amazon.com/sdk-for-go/v2/developer-guide/making-requests.html returned 404 or no section; https://pkg.go.dev/github.com/aws/aws-sdk-go-v2/service/s3 was truncated. |
| S8 | https://docs.aws.amazon.com/sdk-for-rust/latest/dg/paginating.html | OK |
| S9 | https://docs.aws.amazon.com/boto3/latest/guide/paginators.html | OK; `build_full_result` is not on the page |
| S10 | https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/pagination.html | OK |
| S11 | https://docs.aws.eu/sdk-for-swift/latest/developer-guide/using-paginators.html | OK (the AWS-hosted mirror) |
| S12 | https://github.com/soto-project/soto-core/blob/main/Sources/SotoCore/AWSClient%2BPaginate.swift | OK (source via the GitHub API). No Soto user-guide page was found. |
| S13 | https://docs.cloud.google.com/java/docs/reference/gax/latest/com.google.api.gax.paging.Page | OK |
| S14 | https://pkg.go.dev/google.golang.org/api/iterator | OK |
| S15 | https://googleapis.dev/python/google-api-core/latest/page_iterator.html | OK (api_core; the generated per-API pagers were not read) |
| S16 | https://google.aip.dev/158 | OK |
| S17 | https://learn.microsoft.com/en-us/dotnet/azure/sdk/pagination | OK |
| S18 | https://learn.microsoft.com/en-us/azure/developer/java/sdk/pagination | OK |
| S19 | https://learn.microsoft.com/en-us/python/api/azure-core/azure.core.paging.itempaged | OK; the async twin was not read |
| S20 | https://raw.githubusercontent.com/openai/openai-python/main/README.md | OK |
| S21 | https://raw.githubusercontent.com/openai/openai-node/master/README.md | OK |
| S22 | https://platform.claude.com/docs/en/cli-sdks-libraries/sdks/python and https://platform.claude.com/docs/en/cli-sdks-libraries/sdks/typescript | OK |
| S23 | https://github.com/octokit/plugin-paginate-rest.js | OK |
| S24 | https://buildwithfern.com/learn/sdks/deep-dives/auto-pagination.md and https://buildwithfern.com/learn/api-definition/openapi/extensions/pagination | OK; the Ruby and PHP output is not on the page |
| S25 | https://www.speakeasy.com/docs/customize/runtime/pagination | OK; only a generic example is documented |
| S26 | https://www.stainless.com/docs/sdks/configure/pagination/ | SECONDARY: every direct fetch (this URL, /docs/configure/pagination, /docs/configure/pagination/index.md, /docs/guides/add-pagination) returned an unrelated "Stainless has joined Anthropic" page or 404. The scheme names and the `x-stainless-pagination-property` purposes come from a search-result summary of the official docs. |
| S27 | https://raw.githubusercontent.com/oxidecomputer/progenitor/main/docs/positional-generation.md and https://github.com/oxidecomputer/progenitor/blob/main/progenitor-impl/src/method.rs | OK (docs and source: `dropshot_pagination_data`, lines about `page_token`, `limit`, `RawBody`, `_stream`) |
| S28 | https://raw.githubusercontent.com/ogen-go/ogen/main/README.md | OK; absence of pagination in the README. A `gh search issues pagination --repo ogen-go/ogen` returned one unrelated issue. |
| S29 | https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/spring.md | OK (options `autoXSpringPaginated`, `substituteGenericPagedModel`, extension `x-spring-paginated`); open issues found with `gh search issues` (#2493, #5753, #14062) |
| S30 | https://smithy.io/2.0/spec/behavior-traits.html | OK |
| S31 | https://github.com/Azure/autorest/blob/main/docs/extensions/readme.md and https://cdn.jsdelivr.net/npm/@microsoft.azure/autorest.testserver@3.3.51/swagger/paging.json | SECONDARY: the extension section itself was not retrieved; the members come from search-result summaries. |
| S32 | https://baywet.github.io/lessons-from-api-client-generation-pagin | OK (a blog post, an opinion, not a specification) |

Not verified at all: the pagers generated for individual Google APIs, Azure Python's async paging type, Ruby/PHP/Dart/Elixir pagination in any mainstream SDK, and how the chasm fixture server answers per cursor.

## 9. Vendor generators

Added at the owner's request. Same source rules as above: tags V1 to V14 are in the list at the end of this section, with a status for each.

### 9.1 Comparison table

| Vendor | How the generator learns an operation paginates | Generated shape per language | Next page and early stop | Errors | Source |
|---|---|---|---|---|---|
| Stainless | Configuration: pagination schemes (`cursor`, `cursor_id`, `cursor_url`, `offset`, `page_number`) and a per-method `paginated:` reference; spec properties tagged `x-stainless-pagination-property`. A search-result summary of the docs adds that methods whose names start with `list_` are paginated by default. | Page classes per scheme. In the generated OpenAI Python package: `SyncCursorPage`, `AsyncCursorPage`, `SyncPage`, `SyncNextCursorPage`, `SyncTokenPage` with `has_next_page()` and `next_page_info()`; the iterable is the page itself (`for x in client...list()`), plus `get_next_page()`; Node `for await`, `hasNextPage()`, `getNextPage()`. | A `has_more` of false ends it. For an id cursor the next request uses `after = last item's id`; when the last item has no `id`, `next_page_info` returns `None` and the iteration silently ends (a TODO comment says a warning should be logged). | not documented on the pages fetched | V1 (secondary), V2 (generated source), [S20][S21][S22] |
| Speakeasy | Explicit `x-speakeasy-pagination` on the operation: `offsetLimit`, `cursor` or `url`, inputs and JSONPath outputs. No inference. | A generated `next()` on each response; loop until it returns null (Go style: `nil, nil` at the end). The page fetched shows only the Python loop; per-language iterators are not documented there. | `next()` returns null when the path does not match or a short page is returned with `limit`. No early-stop feature is described. | not covered | V3, [S25] |
| Fern | Explicit `x-fern-pagination` (`cursor`, `offset`, `next_uri`, `results`, `step`). | Item iterator by default (TS `Page<T>` async iterable, Python `Pager[T]`, Go `Page[T].Iterator()`, Java `SyncPagingIterable<T>`, C# `Pager<T>`, Rust `AsyncPaginator<T>`), explicit `GetNextPage(ctx)` and `nextPage()` in Go and Java. | iterator or explicit call; `break` | Rust yields `Err` per item | [S24] |
| Microsoft Kiota (as used by Microsoft Graph SDKs) | Not a generator feature that infers: the helper `PageIterator` follows the typed `@odata.nextLink` property of the response; a maintainer said pagination is not automatic for every operation in Kiota clients (a GitHub issue discussion, secondary). | A helper class per language: C# `PageIterator<T, TResponse>.CreatePageIterator(client, firstPage, callback, requestConfigurator)` then `IterateAsync()`; Go `NewPageIterator` and `Iterate(ctx, cb)`; Java builder with `processPageItemCallback` and `iterate()`; PHP `PageIterator` with `iterate($callback)`; TypeScript `PageIterator`. The caller passes in the first page they already fetched. | The per-item callback returns `true` to continue and `false` to pause; `State` / `isComplete()` and `resume()` continue later. | Go and Java: the error comes back from `Iterate`. The doc warns that a token from a retried request must not be used for the next page: "persist the token from the last successful response", and that extra headers on the first request are NOT sent on later page requests unless the caller re-adds them. | V4, V5 |
| liblab | `x-liblab-pagination` on the endpoint, or the same data in `liblab.config.json`: `type` (`offset-limit` or `cursor`), `inputFields`, `resultsArray` JSONPath, `nextCursor`. | Only TypeScript/JavaScript, Java/Kotlin and Python; C#, Go and PHP: no. Two patterns: an iterable (`for await`, `for`, an iterator stream in Java) and manual (`next()`, or `hasNext()`/`next()` in Java). Cursor pagination accepts a saved token to resume. | iteration ends when the API returns no next token | "manages failed requests gracefully"; no error types documented. Only the query location is supported. | V6 |
| APIMatic | `x-pagination` in the spec (JSON Pointers for request inputs and response fields); strategies cursor, link, page number, offset. The docs do not describe inference. | Two views everywhere: items and pages. Java `PagedIterable` / `PagedFlux` with `pages()`; .NET `Pageable` / `AsyncPageable` with `GetPagesAsync()` (v4 beta: `AsPages()`); Python iterator with `pages()`; Ruby `each` and `pages`; TypeScript async iterable with `pages`. Each page carries the inputs that produced it (`getNextCursor()`, `getNextLink()`, `getPageNumber()`, `getOffset()`), items, body, headers, status. | implicit; page metadata for explicit resume | exceptions raised during iteration (`ApiException`, `APIException`, async error handlers in Java) | V7 |
| Konfig | not verified | not verified | not verified | not verified | V8: the docs site failed to load (TLS error); a search found only a marketing page listing "streaming" among features, nothing on pagination |
| Postman SDK Generator | not applicable: the product is deprecated and its docs point to Fern | n/a | n/a | n/a | V9 |
| openapi-generator (stock) | No client-side pagination. Server-only `x-spring-paginated` and two Spring options (section 4.1). | none | none | none | [S29] |

### 9.2 What the vendors change in the recommendation

1. Nobody infers freely. Stainless, Speakeasy, Fern, liblab and APIMatic all require a declaration (extension or config); Kiota relies on a typed `nextLink` property; the only default-by-name case is Stainless's `list_` rule, and that comes from a secondary source. My section 4 inference is therefore more adventurous than any vendor. Consequence for the recommendation: ship tiers 1 and 3 first (Q7), keep the override option mandatory, and make Q8 sharper: the five vendor extensions (`x-stainless-pagination-property`, `x-speakeasy-pagination`, `x-fern-pagination`, `x-liblab-pagination`, `x-pagination` from APIMatic, plus `x-ms-pageable` and `x-dropshot-pagination`) are what real specs carry, so reading them in the Java generator (never in templates) into the same internal model would cover most specs that are already annotated. Note that APIMatic's `x-pagination` is the same name F1100 mentions, with different semantics.
2. Both views are universal. APIMatic, liblab, Fern and Azure give items and pages; Speakeasy's `next()` and Kiota's callback are page-driven. This confirms option C. No vendor makes the existing method return a page except Stainless and Fern, which design their SDK that way from the start, so for us (additive, F1100) a separate method stays right.
3. A callback with `return false` to stop (Kiota, Octokit `done()`, Stripe `autoPagingEach`) is the vendor-world pattern for early stop in languages without a lazy sequence. Our shape needs no callback, because every one of our twelve languages has a lazy sequence or an `each` with `break`; I left callbacks out (option E stays "not primary").
4. Kiota's two warnings become requirements: (a) repeat the per-call headers and options on every page request (added as pagination027); (b) the next cursor comes from the response that was accepted, never from a discarded retry attempt (pagination028). Our retry loop discards failed responses before the page is built, so (b) is satisfied by construction but must be tested.
5. Stainless's id cursor silently ends when the last item has no `id` (V2 source). This is the failure the issue forbids ("never guess"), so Tier 2 requires an explicit `last_id` or `next_cursor` property and otherwise leaves the operation manual (already in section 4.2).
6. Coverage: liblab ships pagination in three language families out of nine, which shows the cost of per-language divergence. This project's twelve-language parity rule is the differentiator, and the catalogue ids in 6.1 are how it is enforced.
7. Konfig and Postman could not be benchmarked; I did not use them.

The two catalogue ids this section produced (headers and options on every page, cursor from the accepted response) are `pagination027` and `pagination028` in 6.1.

### 9.3 Vendor sources

| Tag | URL | Status |
|---|---|---|
| V1 | https://www.stainless.com/docs/sdks/configure/pagination/ | SECONDARY (search-result summaries; the direct fetch returned an unrelated page) |
| V2 | https://github.com/openai/openai-python/blob/main/src/openai/pagination.py | OK (Stainless-generated source, read through the GitHub API) |
| V3 | https://www.speakeasy.com/docs/sdks/customize/runtime/pagination | OK; per-language iterators are not on the page |
| V4 | https://learn.microsoft.com/en-us/graph/sdks/paging | OK (Microsoft Graph SDKs, which are Kiota-generated; there is no Kiota-owned pagination page in my results, the https://learn.microsoft.com/en-us/openapi/kiota/pagination URL returned 404) |
| V5 | https://github.com/microsoftgraph/msgraph-sdk-javascript/blob/dev/docs/tasks/PageIterator.md | not fetched; surfaced by search only |
| V6 | https://liblab.com/docs/concepts/pagination | OK |
| V7 | https://docs.apimatic.io/generate-sdks/sdk-features/pagination/ | OK |
| V8 | https://docs.konfigthis.com | FAILED (TLS error) |
| V9 | https://learning.postman.com/docs/sdk-generator/overview | OK (deprecation notice) |


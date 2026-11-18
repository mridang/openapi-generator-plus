# JSON performance: where a generated codec is worth building

Issue 107 asks whether the SDKs should generate their own reflection-free JSON codec instead of leaning on each language's serializer, and issue 131 item F31 asks for the numbers before anyone builds one. This page is the baseline and the recommendation. The harness that produced the numbers is in [benchmarks/](../../benchmarks/README.md) and the full tables are in [benchmarks/RESULTS.md](../../benchmarks/RESULTS.md).

## What was measured

Each SDK's `ObjectSerializer` has two entry points that every request and response passes through: serialize a model to JSON text and deserialize JSON text into a model. The harness runs those two calls on the committed golden SDK of the language, on three payloads: one `Tag` (27 bytes, two fields), one `Pet` (459 bytes, a nested category, three tags, three URLs and six scalar fields) and an array of 200 `Pet` (96 KB). It reports time per operation, and bytes and allocations per operation where the runtime can count them. Each implementation is checked to round-trip the fixture before it is timed.

Each language is compared with its best alternative that does not use runtime reflection, or with the same library without the SDK's wrapper:

| Language | Alternatives measured |
|---|---|
| go | `encoding/json` on the same models, `encoding/json/v2` (GOEXPERIMENT=jsonv2), easyjson code generation on plain mirror structs |
| java | the same mapper with Jackson Blackbird and with Afterburner, and a hand-written Jackson streaming codec (what a generated codec would emit), each in its own JVM |
| node | `JSON.parse`/`JSON.stringify` plus a hand-written mapper into the model classes, and the bare native calls as the floor |
| python | pydantic called directly (`model_validate_json`, `model_dump_json`), `json` plus a hand-written mapper into plain classes, and orjson plus the same mapper |
| rust | `serde_json` called directly, simd-json and sonic-rs (all three work on the same serde derive; serde itself is already compile-time) |
| php | `json_decode`/`json_encode` plus a hand-written mapper into the model classes |
| csharp | `System.Text.Json` source generation on the generated models |

## Results

Medians of five rounds, medium and large payloads, in microseconds unless marked ms. "Alt" is the best alternative of the table above that is a fair stand-in for a generated codec, and the factor is generated divided by alt (above 1 means the alternative is faster). The machine was shared and busy (see the caveat below), so a factor under about 1.3 is noise.

| Language | Op | Medium generated | Medium alt | Factor | Large generated | Large alt | Factor |
|---|---|---|---|---|---|---|---|
| go | decode | 34.9 | 1.6 (easyjson) | 22x | 10.3 ms | 0.46 ms | 22x |
| go | encode | 5.6 | 3.3 (easyjson) | 1.7x | 2.6 ms | 0.34 ms | 7.6x |
| java | decode | 2.5 | 2.3 (hand streaming) | 1.1x | 490 | 444 | 1.1x |
| java | encode | 0.82 | 0.87 (hand streaming) | 0.9x | 263 | 158 | 1.7x (spread 57%) |
| node | decode | 32.0 | 3.0 (manual) | 10.7x | 5.95 ms | 0.57 ms | 10.5x |
| node | encode | 8.1 | 0.86 (manual) | 9.4x | 1.53 ms | 0.19 ms | 8.2x |
| python | decode | 31.2 | 7.4 (pydantic direct) | 4.2x | 5.92 ms | 2.22 ms | 2.7x |
| python | encode | 9.9 | 3.7 (pydantic direct) | 2.7x | 3.97 ms | 0.93 ms | 4.3x |
| rust | decode | 1.8 | 1.1 (serde_json) | 1.7x | 428 | 275 | 1.6x |
| rust | encode | 0.70 | 0.59 (serde_json) | 1.2x | 121 | 97 | 1.25x |
| php | decode | 133.9 | 5.1 (manual) | 26x | 28.7 ms | 1.12 ms | 26x |
| php | encode | 35.8 | 2.5 (manual) | 14x | 7.3 ms | 0.54 ms | 14x |
| csharp | decode | 2.7 | 2.6 (source gen) | 1.0x | 481 | 552 | 0.9x |
| csharp | encode | 1.9 | 1.4 (source gen) | 1.4x (spread 42%) | 506 | 481 | 1.1x |

Allocations tell the same story where they are counted. For the medium `Pet`, go's generated decode makes 260 allocations and 12 KB, easyjson 33 allocations and 768 bytes; rust's generated decode makes 14 allocations, identical to `serde_json` directly. Java's bytes per operation are identical across Jackson, Blackbird and Afterburner (3752 bytes for the medium decode) and within 12% of the hand-written codec; csharp's source generation allocates more on decode (2400 bytes against 1944), not less.

### Caveats on the numbers

- One run, on an Apple M1 Max inside Docker Desktop's Linux VM, while other builds loaded the host (load average 14 to 18 on 10 cores during the go, java, node, python and rust runs). Absolute times are not a reference. Spreads are in the full tables; csharp has spreads up to 230% and go up to 165% on small payloads, so a small difference in those rows means nothing. Re-run `benchmarks/run.sh` on a quiet machine before quoting an absolute number outside this page.
- The factor is the useful result when it is large (go, node, php, python) and when it is close to 1 with a small spread (java, rust decode). Everything between 1.1 and 1.5 is inside what this run can resolve.
- The alternatives are not drop-in: easyjson, the node and php manual mappers, and the java streaming codec do not do the checks the SDK's contract requires (see the next section), so they are a ceiling on what a generated codec can reach, not a prediction of it. A codec that keeps the checks will land between the generated column and the alt column.
- The csharp source generation cannot use the generated models as they are: each enum keeps its converter in a private nested class, which the generator cannot name (SYSLIB1220). The csharp row falls back to reflection for the enums only.
- The generated rust crate at commit 1439c2a does not compile as a whole (70 errors, for example `PaymentRequiredError` is not in scope in `errors/open_api_error.rs`), so the rust harness builds a reduced crate from the golden's `object_serializer.rs`, `value_serializer.rs`, models and the three helper modules they import. The codec code measured is the golden's, unchanged.
- Node, python and php do not report allocations; python and php times are with CPython 3.14 and PHP 8.5 with opcache and the tracing JIT enabled.

## Why the gaps are where they are

The cost is not the same thing in each language, and that decides the remedy.

- **php (14 to 26x).** The serializer builds the model through Symfony's `Serializer` with reflection and property-info extractors, and a 27-byte `Tag` already costs 18 us, so there is a large fixed cost per call as well as a per-field cost. The hand-written mapper into the very same classes is 48x faster on the small decode and 21x on the small encode. This is the largest win in the set.
- **go (22x decode).** Every generated model's `UnmarshalJSON` decodes the same bytes several times: a scan for the absolute-URI check, a copy with miscased keys dropped, a decode into an alias type, and a decode into a `map[string]json.RawMessage` to find missing required fields. That happens at every nesting level, so a `Pet` with a category and three tags runs the decoder many times over the same bytes (the 260 allocations for a 459-byte payload). Plain `encoding/json` on the same models costs the same as the SDK's wrapper (the wrapper adds nothing measurable), so the cost is in the models, and `encoding/json/v2` only recovers about 15%. A generated single-pass decoder removes the repeated work. The encoder is already close to the standard library; its remaining gap is reflection and the custom `MarshalJSON` methods.
- **node (about 10x).** Deserialization goes through class-transformer's `plainToInstance` and a post-pass of validation; serialization uses a `JSON.stringify` replacer function that runs on every value. A generated mapper (a constructor call per model, as in the manual column) is within 2.5x of bare `JSON.parse` for decode.
- **python (2.7 to 4.3x).** Pydantic's core is already compiled, and calling it directly is the floor for pydantic models. Most of the 2.7 to 4.2x on decode is not pydantic but the SDK's own wrapper: a profile of the medium decode (28 us) shows the pure-Python nesting-depth scan alone takes 12.6 us, more than `json.loads` (3.4 us). A hand-written mapper into plain classes (4.9 us) is only 1.5x faster than pydantic (7.4 us), so a generated codec would not beat the library it replaces by much.
- **rust (1.2 to 1.7x).** The derive macros are the generated codec. The difference is the SDK's shell: the byte-level depth scan before decode and the `FiniteFloatCheck` pre-walk before encode. simd-json and sonic-rs are faster on large payloads (0.46 to 0.53x of the generated time), but simd-json needs a mutable copy of the buffer and allocates 5.6x as many bytes on decode.
- **java (no gap).** Jackson with the SDK's modules is within 10% of a hand-written streaming codec on every payload except large encode, where one round was slow (spread 57%) and the generated mapper was 1.7x slower than the others in that row only. Blackbird and Afterburner change nothing measurable. Java is not a candidate.
- **csharp (no gap).** `System.Text.Json` reflection is already within noise of source generation on decode; encode is 0.7x to 0.8x in the small and medium rows with spreads around 45%. The reason to adopt source generation in csharp is trimming and native AOT, not speed.

## What a generated codec must still do

Any codec has to keep the contract the serializer enforces today, or it is faster because it is wrong. From the decisions in `generators/AGENTS.md`: strict primitive types with no coercion (D54), a null element rejected in a container of a model type, an unknown enum or discriminator value rejected with the SDK's `SerializationError` naming the value (F835), the 1000-deep nesting cap with the exact message (D15), a leading BOM stripped, a repeated key keeping the last value, NaN and Infinity rejected on encode, exact decimal digits for `type: number`, wire names only, nulls omitted unless the property is required and nullable, and one `SerializationError` message per failure. The existing ObjectSerializer test tables already pin each of these in every language, so the acceptance test for a codec is that the table passes unchanged. That also bounds the saving: the checks cost something, and the factors above are upper bounds.

## Recommendation

| Language | Build a generated codec? | Reason, with the measured number |
|---|---|---|
| php | Yes, first | 14x to 26x on medium and large, and 48x on a tiny payload; the Symfony reflection path is the entire cost. |
| go | Yes, second | 22x on decode, 7.6x on large encode; first fix the repeated decoding in the model `UnmarshalJSON` (a single pass over the bytes), which is a smaller template change and takes most of it. |
| node | Yes, third | About 10x both ways against a hand-written mapper; the mapper is also what removes the class-transformer and replacer cost. |
| python | No | Remove the pure-Python depth scan and the wrapper first (about 12 of 28 us on a medium decode); a pure-Python codec is only about 1.5x faster than pydantic-core. |
| rust | No | serde derive is the codec; the 1.2 to 1.7x gap is the shell's depth scan and float pre-walk, which a cheaper single pass would close. simd-json and sonic-rs are an opt-in, not a default. |
| java | No | Within 10% of a hand-written codec, and Blackbird and Afterburner change nothing. |
| csharp | Not for speed | Within noise. Source generation is worth doing only for trimming and AOT, and needs the enum converters made accessible first. |
| kotlin, dart, swift, elixir, ruby | Not measured | Expected not to need one: kotlin already uses kotlinx.serialization (compile-time plugin, same JVM as java), dart and elixir already map fields in generated code, swift uses synthesized Codable. Ruby was not read closely enough to say. These are readings of the generated sources, not measurements. |

The order is php, go, node. The go finding (repeated decoding) and the python finding (the depth scan) are cheaper to fix than a codec and should be done whether or not a codec is built; the box for each is its own issue.

## What was not done

- kotlin, ruby, dart, swift and elixir have no runner. The harness has one directory per language with a `run.sh` that receives the golden SDK; adding one means copying a sibling and replacing the two calls.
- The run is single and noisy. Before a codec decision is final for php, go or node, run `benchmarks/run.sh <lang>` alone on an idle machine and check that the factors hold; they are large enough that noise is unlikely to change the order.
- Node, python and php allocations were not measured, nor was memory held (only allocated bytes per operation, and only for go, java, csharp and rust).
- No codec was built. The easyjson, hand-written Jackson, node, python and php columns are hand-written stand-ins.

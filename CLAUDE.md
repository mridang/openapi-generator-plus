# Claude Code Instructions

## Devbox Requirement

This project uses [Devbox](https://www.jetify.com/devbox) to manage all development tools (JDK, Maven, etc.). **All shell commands must be run inside `devbox shell`.**

Prefix every Bash command with `devbox run --` or wrap it in `devbox shell`:

```bash
# Correct
devbox run -- mvn test -Dtest=GenerateClientsTest
devbox run -- mvn compile
devbox run -- mvn spotless:apply

# Wrong - do NOT call mvn, java, etc. directly
mvn test
```

The `devbox.json` in the project root defines all available packages and scripts. Use `devbox run <script>` for predefined scripts:

- `devbox run test` - Run `mvn verify`
- `devbox run format` - Run `mvn spotless:apply`
- `devbox run check` - Run `mvn compile`

## Use Mustache Templates for File Generation

When generating file content programmatically (e.g., per-operation Options classes, authenticator classes), **always use Mustache template files** rendered with a context map. Never build file content with Java `StringBuilder` or string concatenation. Create `.mustache` template files under `src/main/resources/templates/<language>/` and render them using `renderOptionsTemplate()` or the same pattern. The structure and layout of generated files belong in templates, not in Java code.

## No Vendor Extensions in Templates

**Never use vendor extensions** (`vendorExtensions.x-*`) in Mustache templates. Always use native CodegenOperation/CodegenParameter fields instead (e.g., `{{#content}}`, `{{#servers}}`, `{{#cookieParams}}`). Vendor extensions are fragile and require custom Java code to set them.

## Integration Tests

Integration tests for generated clients (Ruby, Python, PHP, Java) run inside Docker containers. Docker itself does not need to be inside devbox, but Maven commands that trigger these tests do.

## Running Tests — Always Save to a File

**Never tail or pipe `mvn verify` output directly.** Long-running test suites produce too much output; tailing means you lose context on failure and must re-run to see errors.

**Always save to a file, then read the file:**

```bash
# Correct
devbox run -- mvn verify -pl . > /tmp/mvn-output.txt 2>&1
cat /tmp/mvn-output.txt | grep -E "FAIL|ERROR|BUILD" | head -40
# Then read the full file if needed

# Wrong — do NOT do this
devbox run -- mvn verify -pl . 2>&1 | tail -60
```

This way:
- The full output is preserved even if the build takes minutes
- You can grep/read specific sections without re-running
- If a test fails, the error details are already on disk — no need to rerun

## The Twelve SDKs Must Be Character-Level Aligned

The twelve generators exist to produce **the same SDK in twelve languages**. Someone
who has used one must be able to read another and recognise it line for line. Treat
any difference between two languages as a defect until you can name the language
feature that forces it.

**Generated prose is identical across all twelve, character for character.** A
sentence in `SKILLS.md` or `README.md` is written once and repeated verbatim in every
`skills.mustache`. The only permitted variation is a token that names something the
language genuinely calls by another name — `interface` / `protocol` / `trait` /
`behaviour`, `property` / `field` / `attribute`, a type such as `byte[]` / `Vec<u8>`
/ `Data`. Everything around that token — word choice, order, punctuation — stays the
same. "All API errors extend X. The exception hierarchy is:" in one language and "All
API errors inherit from Y. The error hierarchy is:" in another is a defect, not a
style choice.

**Sections appear in the same order with the same headings in all twelve.** Do not
add a section to one language alone. If a language needs something the others do not,
that is a signal the others are missing it.

**Every code example must compile and run as written, pasted verbatim.** This is the
rule that keeps being broken, and it has shipped in every SDK more than once:

- A testing example named a `RequestContext` parameter in seven languages. No
  generator emits such a type, and no interface takes that argument.
- Examples documented `url()` where the accessor is `getUrl`, `get_url`, `GetUrl` or
  `URL`, and a `Servers::SERVER_0` constant where the class declares `server0()`.
- Error-handling examples called suspending operations without `await`, so the
  `catch` arms could never fire.
- A Maven coordinate used `LATEST`, which Maven 3 cannot resolve.

Before writing an example, **read the generated file it describes** and copy the real
signature out of it. Never write one from memory of another language's template, and
never assume a helper type exists because a sibling language has it.

**Verify examples by compiling them, not by reading them.** Extract the snippet into
a scratch file and build it against the generated package. Every defect above was
invisible to review and obvious to a compiler.

**A change to one generator applies to all twelve.** After fixing one, check the other
eleven for the same fault in a different shape: a phantom parameter may appear as a
typed argument in one language, an untyped one in another, and a wrongly-named method
in a third. Grepping for the literal you just fixed will miss those, so compare each
example against that language's real interface.

**The fixture cannot leak.** The petstore spec is what the generator is developed
against, so its vocabulary reads as correct in that one output and wrong everywhere
else. `FixtureVocabularyLeakTest` generates every SDK from a second spec that shares
no vocabulary and fails the build on any fixture identifier. When it reports a word
you believe is legitimate, prove it comes from the spec under generation before
adding it to the allow-list.

package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Proves that no vocabulary from the generator's own fixture spec reaches a
 * generated SDK.
 *
 * <p>The templates are authored while looking at petstore output, so it is easy
 * to type one of its type or operation names into a template as a literal.
 * Templates render for EVERY spec, so such a literal ships to customers whose
 * spec has no such type — producing documentation that references classes their
 * package never contained, and in several cases test sources that could not
 * compile at all.
 *
 * <p>The existing suite cannot catch this. It generates from petstore, where
 * every leaked literal is by definition correct. So this test generates from a
 * SECOND spec that deliberately shares no vocabulary with the first, and fails
 * on any petstore identifier appearing in the output.
 *
 * <p>The forbidden list is derived from the petstore spec itself rather than
 * hand-maintained, so it cannot drift as that spec grows. A hand-written regex
 * was tried first and proved unreliable: word boundaries miss the identifiers
 * that matter, because {@code \bpet\b} matches neither {@code SetPetAvatar} nor
 * {@code pet_api}.
 */
class FixtureVocabularyLeakTest {

    /** Generated with client-like options: unit tests on, golden-only tests off. */
    private static final Map<String, Object> CLIENT_LIKE_OPTIONS =
            Map.of("generateUnitTests", "true", "skipFormatter", "true");

    /**
     * Fixture-specific words that carry no "pet" in their spelling and so are
     * not caught by the rule below, but are unmistakably from the fixture.
     */
    private static final Set<String> DISTINCTIVE_EXTRAS = Set.of("petstore", "swatch", "fido", "doggie");

    /**
     * Property names the fixture happens to share with the vocabulary of HTTP
     * and OAuth itself.
     *
     * <p>Every SDK has a Content-Type guess and an access-token expiry, so
     * {@code mimeTypeForFilename} and a comment about {@code expiresAt} are
     * the generator's own words, not the fixture's. Nothing here may be a
     * name the fixture invented — that is the whole point of the check.
     */
    private static final Set<String> SHARED_WITH_PROTOCOL_VOCABULARY =
            Set.of("mimeType", "expiresAt");

    @Test
    void generatedSdksContainNoFixtureVocabulary() throws IOException {
        final Set<String> forbidden = forbiddenVocabulary();
        assertTrue(
                forbidden.size() > 8,
                "expected a substantial vocabulary from the fixture spec, got " + forbidden);

        final List<String> violations = new ArrayList<>();
        for (final String generator : ProbeGenerator.ALL_GENERATORS) {
            violations.addAll(leaksFor(generator, forbidden));
        }

        assertTrue(
                violations.isEmpty(),
                "Fixture-spec vocabulary leaked into generated SDKs. Each line is a template\n"
                        + "literal that ships to every customer, including those whose spec has no\n"
                        + "such type. Derive the value from the spec instead.\n\n"
                        + String.join("\n", violations));
    }

    /**
     * Collects the schema names, operation ids and tags declared by the fixture
     * spec, plus the fixture's own name. These are exactly the words that must
     * never appear in output generated from a different spec.
     */
    private static Set<String> forbiddenVocabulary() throws IOException {
        final URL spec =
                FixtureVocabularyLeakTest.class
                        .getClassLoader()
                        .getResource("specs/petstore/openapi.yaml");
        if (spec == null) {
            throw new IllegalStateException("fixture spec not found on the test classpath");
        }
        final String text = Files.readString(Path.of(spec.getPath()), StandardCharsets.UTF_8);

        final Set<String> candidates = new LinkedHashSet<>();
        collect(candidates, Pattern.compile("^\\s{4}([A-Z][A-Za-z0-9]+):\\s*$", Pattern.MULTILINE), text);
        collect(candidates, Pattern.compile("operationId:\\s*([A-Za-z0-9_]+)"), text);

        /* Property names too. Matching only schema and operation names let
           "weightKg" — a property of the fixture's Pet — reach 1,197 generated
           model files unnoticed, because it does not spell "pet". Only
           multi-word camelCase names are taken: a single lowercase word such
           as "name" or "status" occurs everywhere by coincidence, while
           "weightKg" does not. */
        final Set<String> properties = declaredPropertyNames(text);

        /* Keep only what is DISTINCTIVE to the fixture. Its schemas are largely
           ordinary English nouns — Order, Category, Photo, Tag — which occur
           legitimately throughout any SDK, so matching them reports noise rather
           than leaks. What cannot occur by accident is the fixture's own subject
           matter, so keep the names that spell "pet" plus a few named extras. */
        final Set<String> words = new LinkedHashSet<>(DISTINCTIVE_EXTRAS);
        for (final String candidate : candidates) {
            if (lower(candidate).contains("pet")) {
                words.add(candidate);
            }
        }

        /* A property name is only evidence of a leak if it belongs to THIS
           spec alone. Anything the probe spec also declares is a word both
           specs happened to choose, so its presence in probe output says
           nothing. */
        final String probe = readProbeSpec();
        for (final String property : properties) {
            if (SHARED_WITH_PROTOCOL_VOCABULARY.contains(property)) {
                continue;
            }
            if (!lower(probe).contains(lower(property))) {
                words.add(property);
            }
        }
        return words;
    }

    /**
     * The multi-word camelCase keys declared under a {@code properties:} block.
     *
     * <p>Scoping to that block is what separates the spec's vocabulary from
     * its structure: a bare scan for camelCase keys also picks up {@code oneOf}
     * and {@code anyOf}, which are OpenAPI keywords rather than anything the
     * fixture named. Single lowercase words such as "name" are skipped for the
     * opposite reason — they occur everywhere by coincidence.
     *
     * @param text the spec's raw YAML
     * @return the property names the spec declares
     */
    private static Set<String> declaredPropertyNames(String text) {
        final Set<String> names = new LinkedHashSet<>();
        final Pattern camelKey = Pattern.compile("^(\\s+)([a-z][a-z0-9]*[A-Z][A-Za-z0-9]*):\\s*$");
        int propertiesIndent = -1;
        for (final String line : text.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            final int indent = line.length() - line.stripLeading().length();
            if (propertiesIndent >= 0 && indent <= propertiesIndent) {
                propertiesIndent = -1;
            }
            if (line.stripTrailing().endsWith("properties:")
                    && !line.stripLeading().startsWith("additionalProperties:")) {
                propertiesIndent = indent;
                continue;
            }
            if (propertiesIndent < 0) {
                continue;
            }
            final Matcher matcher = camelKey.matcher(line);
            /* Only the keys directly beneath the block, not nested schemas. */
            if (matcher.matches() && matcher.group(1).length() == propertiesIndent + 2) {
                names.add(matcher.group(2));
            }
        }
        return names;
    }

    /** The probe spec's raw text, used to discard vocabulary the two specs share. */
    private static String readProbeSpec() throws IOException {
        final URL spec =
                FixtureVocabularyLeakTest.class
                        .getClassLoader()
                        .getResource("specs/probe/openapi.yaml");
        if (spec == null) {
            throw new IllegalStateException("probe spec not found on the test classpath");
        }
        return Files.readString(Path.of(spec.getPath()), StandardCharsets.UTF_8);
    }

    private static void collect(Set<String> into, Pattern pattern, String text) {
        final Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }

    /** Generates one SDK from the probe spec and reports any forbidden word in it. */
    private static List<String> leaksFor(String generator, Set<String> forbidden)
            throws IOException {
        /* Generate under target/ rather than the system temp dir: the codegen
           runs its formatters in Docker, which can only mount paths shared with
           the daemon, and /var/folders is not one of them. */
        final Path out = Path.of("target", "leakcheck", generator).toAbsolutePath();
        if (Files.exists(out)) {
            deleteRecursively(out);
        }
        ProbeGenerator.generate(generator, CLIENT_LIKE_OPTIONS, out);

        final List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(out)) {
            for (final Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                final String relative = out.relativize(file).toString();
                final String body;
                try {
                    body = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException notUtf8Text) {
                    /* Binary artefacts cannot carry a leaked identifier. */
                    continue;
                }
                final String haystack = relative + "\n" + body;
                for (final String word : forbidden) {
                    if (occursAsIdentifierSegment(haystack, word)) {
                        found.add("  " + generator + ": " + relative + " contains \"" + word + "\"");
                    }
                }
            }
        }
        return found;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> entries = Files.walk(dir)) {
            for (final Path path :
                    entries.sorted(java.util.Comparator.reverseOrder())
                            .collect(Collectors.toList())) {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Whether {@code word} occurs in {@code text} at an identifier-segment
     * boundary, rather than merely as a run of characters.
     *
     * <p>A plain substring search is unusable here: "typeToConvert" contains
     * "pet" across the seam between "type" and "To", and so does
     * "ContentTypeToApplicationJson". Requiring the match to START a segment —
     * preceded by a non-letter, or capitalised after a lowercase character as
     * camelCase does — keeps "setPetAvatar", "pet_api" and "/pet//details"
     * while dropping those.
     *
     * @param text the haystack to search
     * @param word the fixture word to look for
     * @return whether the word appears as its own identifier segment
     */
    private static boolean occursAsIdentifierSegment(String text, String word) {
        final Matcher matcher =
                Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE).matcher(text);
        while (matcher.find()) {
            final int start = matcher.start();
            if (start == 0) {
                return true;
            }
            final char before = text.charAt(start - 1);
            final char first = text.charAt(start);
            if (!Character.isLetter(before)) {
                return true;
            }
            if (Character.isUpperCase(first) && !Character.isUpperCase(before)) {
                return true;
            }
        }
        return false;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}

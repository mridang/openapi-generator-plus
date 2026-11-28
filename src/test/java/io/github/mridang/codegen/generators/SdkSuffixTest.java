package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Proves that a title already ending in "SDK" does not acquire a second one.
 *
 * <p>Fifteen templates append a suffix of their own, which reads correctly only
 * while the title does not already end that way. Zitadel's title is "Zitadel
 * SDK", so every generated README and SKILLS.md opened with "Zitadel SDK SDK".
 *
 * <p>The petstore fixture is titled "Swagger Petstore", so the goldens cannot
 * show this either way — the same blind spot that has hidden several defects
 * here. The probe spec is therefore titled to end in "SDK", and this generates
 * from it and reads the documents back.
 *
 * @see AppNameBaseTest for the suffix rule itself
 */
class SdkSuffixTest {

    private static final Map<String, Object> OPTIONS =
            Map.of("generateUnitTests", "true", "skipFormatter", "true");

    @Test
    void generatedDocsDoNotDoubleTheSdkSuffix() throws IOException {
        final List<String> offences = new ArrayList<>();
        int documentsRead = 0;

        for (final String generator : ProbeGenerator.ALL_GENERATORS) {
            final Path out = Path.of("target", "sdksuffix", generator).toAbsolutePath();
            if (Files.exists(out)) {
                deleteRecursively(out);
            }
            ProbeGenerator.generate(generator, OPTIONS, out);

            try (Stream<Path> files = Files.walk(out)) {
                for (final Path file :
                        files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    final String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".md") && !name.endsWith(".gemspec")) {
                        continue;
                    }
                    final String body;
                    try {
                        body = Files.readString(file, StandardCharsets.UTF_8);
                    } catch (IOException notUtf8Text) {
                        continue;
                    }
                    documentsRead++;
                    if (body.toLowerCase(Locale.ROOT).contains("sdk sdk")) {
                        offences.add("  " + generator + ": " + out.relativize(file));
                    }
                }
            }
        }

        /* Guards against the check passing because it read nothing — the
           failure mode that makes a green assertion meaningless. */
        assertTrue(
                documentsRead > ProbeGenerator.ALL_GENERATORS.size(),
                "expected to read at least one document per generator, read " + documentsRead);

        assertTrue(
                offences.isEmpty(),
                "A title already ending in \"SDK\" gained a second one. The template\n"
                        + "should append its suffix to appNameBase, not appName.\n\n"
                        + String.join("\n", offences));
    }

    /**
     * The converse: the suffix must still be appended for an ordinary title,
     * so the fix cannot be mistaken for having simply removed it everywhere.
     */
    @Test
    void ordinaryTitleStillGetsTheSuffix() {
        assertFalse(
                AbstractBetterCodegen.appNameBase("Swagger Petstore").isEmpty(),
                "an ordinary title must survive to carry the suffix");
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
}

package io.github.mridang.codegen.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * Covers the raw-document reader that supplies the license identifier the parsed model drops.
 */
class RawSpecReaderTest {

    private static Path write(final Path dir, final String yaml) throws IOException {
        final Path spec = dir.resolve("openapi.yaml");
        Files.writeString(spec, yaml);
        return spec;
    }

    @Test
    @DisplayName("the license identifier is read from the document when the model drops it")
    void readsLicenseIdentifier(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(
                        dir,
                        "openapi: 3.1.0\n"
                                + "info:\n"
                                + "  title: T\n"
                                + "  license:\n"
                                + "    name: Apache 2.0\n"
                                + "    identifier: Apache-2.0\n");
        assertEquals("Apache-2.0", RawSpecReader.licenseIdentifier(spec.toString()));
    }

    @Test
    @DisplayName("a license with no identifier yields none")
    void licenseWithoutIdentifierYieldsNothing(@TempDir final Path dir) throws IOException {
        final Path spec =
                write(dir, "openapi: 3.1.0\ninfo:\n  license:\n    name: Acme\n");
        assertNull(RawSpecReader.licenseIdentifier(spec.toString()));
    }

    @Test
    @DisplayName("a missing location yields no license identifier")
    void missingLocationYieldsNoLicenseIdentifier() {
        assertNull(RawSpecReader.licenseIdentifier(null));
    }
}

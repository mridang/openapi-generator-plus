package io.github.mridang.codegen.generators.php;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mridang.codegen.generators.ProbeGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@code format: byte} property documents that it holds raw bytes whether or not the spec
 * describes it. The shared fixtures describe every byte property, so this renders a model whose
 * byte properties carry no description.
 */
class BetterPHPCodegenByteDocTest {

    private static final String SPEC =
            String.join(
                    "\n",
                    "openapi: 3.0.3",
                    "info: {title: Bytes, version: '1'}",
                    "paths: {}",
                    "components:",
                    "  schemas:",
                    "    Blob:",
                    "      type: object",
                    "      properties:",
                    "        seal: {type: string, format: byte}",
                    "        shards: {type: array, items: {type: string, format: byte}}",
                    "        labels: {type: object, additionalProperties: {type: string, format: byte}}",
                    "        note: {type: string}",
                    "");

    @Test
    void undescribedByteProperties(@TempDir Path tmp) throws IOException {
        final Path spec = tmp.resolve("openapi.yaml");
        Files.writeString(spec, SPEC);
        final Path out = tmp.resolve("out");
        ProbeGenerator.generate(
                "php-plus",
                Map.of("invokerPackage", "BytesClient", "skipFormatter", "true"),
                out,
                spec.toString());

        final String model = Files.readString(out.resolve("lib/Models/Blob.php"));
        final String doc = "     * Raw bytes; base64-encoded only on the wire.\n";
        for (final String property : new String[] {"seal", "shards", "labels"}) {
            final int at = model.indexOf("#[SerializedName('" + property + "')]");
            assertTrue(at > 0, property + " is rendered");
            assertTrue(
                    model.substring(model.lastIndexOf("/**", at), at).contains(doc),
                    property + " carries the raw bytes doc line");
        }
        final int note = model.indexOf("#[SerializedName('note')]");
        assertTrue(
                !model.substring(model.lastIndexOf("\n\n", note), note).contains("Raw bytes"),
                "a plain string stays undocumented");
    }
}

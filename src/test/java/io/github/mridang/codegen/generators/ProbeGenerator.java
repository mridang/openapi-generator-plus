package io.github.mridang.codegen.generators;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

/**
 * Generates an SDK from the PROBE spec rather than the petstore fixture.
 *
 * <p>The probe spec shares no vocabulary with petstore, which is what lets
 * {@link FixtureVocabularyLeakTest} tell a value derived from the spec apart
 * from a literal hardcoded in a template. It also carries the shapes petstore
 * lacks — a leading-acronym tag, a map of byte arrays, and a schema named after
 * the root package — each of which concealed a real defect behind a green suite.
 *
 * <p>Unlike {@link ClientGenerator} this does NOT set {@code generateTests}, so
 * only the templates a real client receives are rendered.
 */
public final class ProbeGenerator {

    private static final String SPEC_RESOURCE = "specs/probe/openapi.yaml";

    /** Every generator this project ships, by registered name. */
    public static final List<String> ALL_GENERATORS =
            List.of(
                    "csharp-plus",
                    "dart-plus",
                    "elixir-plus",
                    "go-plus",
                    "java-plus",
                    "kotlin-plus",
                    "node-plus",
                    "php-plus",
                    "python-plus",
                    "ruby-plus",
                    "rust-plus",
                    "swift-plus");

    private ProbeGenerator() {}

    /**
     * Renders {@code generatorName} from the probe spec into {@code outputDir}.
     *
     * @param generatorName the registered generator name, e.g. {@code java-plus}
     * @param properties additional properties to pass to the codegen
     * @param outputDir the directory to generate into
     * @throws IOException if the spec cannot be read or the output written
     */
    public static void generate(
            String generatorName, Map<String, Object> properties, Path outputDir)
            throws IOException {
        Files.createDirectories(outputDir);

        final URL specUrl = ProbeGenerator.class.getClassLoader().getResource(SPEC_RESOURCE);
        if (specUrl == null) {
            throw new IllegalStateException("Could not find probe spec: " + SPEC_RESOURCE);
        }

        final CodegenConfigurator configurator =
                new CodegenConfigurator()
                        .setGeneratorName(generatorName)
                        .setInputSpec(specUrl.getPath())
                        .setOutputDir(outputDir.toString().replace("\\", "/"))
                        .setValidateSpec(false)
                        .setAdditionalProperties(new HashMap<>(properties));

        final DefaultGenerator generator = new DefaultGenerator();
        generator.setGenerateMetadata(false);
        generator.opts(configurator.toClientOptInput()).generate();
    }
}

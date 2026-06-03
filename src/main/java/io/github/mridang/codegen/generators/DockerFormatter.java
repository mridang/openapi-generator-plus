package io.github.mridang.codegen.generators;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a language's source formatter inside a Docker container, serialized
 * across the whole JVM and failing loudly on any error.
 *
 * <p>Every language formatter installs its tooling from the network at run
 * time — {@code npm install}, {@code composer install}, {@code cargo}/{@code
 * rustup}, a {@code ktlint} download, {@code apt-get}, and so on. The
 * per-language {@code Generate*ClientTest} regenerators run in parallel
 * (Surefire {@code <parallel>methods</parallel>}), so without coordination a
 * dozen containers hammer the network at the same moment and some installs
 * fail transiently. The previous implementation logged such a failure and
 * continued, leaving that language unformatted; because which container lost
 * the race varied from run to run, the committed output drifted
 * non-deterministically.
 *
 * <p>This helper removes both causes. {@link #run} holds {@link #LOCK} so only
 * one formatter container is ever active (no contention), and it throws on a
 * non-zero exit or any execution failure so a formatting failure can never be
 * silently swallowed. Generation therefore either produces fully formatted,
 * reproducible output or fails with the captured container log.
 */
final class DockerFormatter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerFormatter.class);

    /** Serializes formatter containers across all languages in this JVM. */
    private static final Object LOCK = new Object();

    /** Number of trailing log lines included in a failure message. */
    private static final int TAIL_LINES = 40;

    /**
     * How many times a formatter container is attempted before giving up. The
     * formatters install their tooling from the network at run time (composer,
     * bundler, npm, cargo, …), which fails transiently; retrying absorbs those
     * blips so a flake does not abort generation, while a persistent failure
     * still surfaces loudly after the final attempt.
     */
    private static final int MAX_ATTEMPTS = 3;

    private DockerFormatter() {}

    /**
     * Runs {@code dockerCmd} (a fully built {@code docker run …} invocation)
     * with {@code workDir} as the working directory, serialized on
     * {@link #LOCK} and retried up to {@link #MAX_ATTEMPTS} times. Throws if the
     * container still exits non-zero, or cannot be run, after the final attempt.
     *
     * @param dockerImage the formatter image, for diagnostics
     * @param workDir the generated-output directory bind-mounted into the
     *     container
     * @param dockerCmd the complete process command line
     */
    static void run(String dockerImage, String workDir, List<String> dockerCmd) {
        synchronized (LOCK) {
            RuntimeException last = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    runOnce(dockerImage, workDir, dockerCmd);
                    return;
                } catch (RuntimeException e) {
                    last = e;
                    LOGGER.warn(
                            "Docker formatter {} attempt {}/{} failed in {}: {}",
                            dockerImage,
                            attempt,
                            MAX_ATTEMPTS,
                            workDir,
                            e.getMessage());
                }
            }
            throw new IllegalStateException(
                    "Docker formatter "
                            + dockerImage
                            + " failed after "
                            + MAX_ATTEMPTS
                            + " attempts in "
                            + workDir,
                    last);
        }
    }

    @SuppressFBWarnings(
            value = {"COMMAND_INJECTION", "PATH_TRAVERSAL_IN"},
            justification = "Commands and paths are hardcoded by subclasses, not user input")
    private static void runOnce(String dockerImage, String workDir, List<String> dockerCmd) {
        final List<String> output = new ArrayList<>();
        try {
            LOGGER.debug("Running formatter in Docker: {}", dockerCmd);
            final Process process =
                    new ProcessBuilder(dockerCmd)
                            .directory(new File(workDir))
                            .redirectErrorStream(true)
                            .start();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines()
                        .forEach(
                                line -> {
                                    output.add(line);
                                    LOGGER.debug("[formatter] {}", line);
                                });
            }
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "Docker formatter "
                                + dockerImage
                                + " exited with code "
                                + exitCode
                                + " in "
                                + workDir
                                + ". Last output:\n"
                                + tail(output));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Docker formatter " + dockerImage + " could not be run in " + workDir, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Docker formatter " + dockerImage + " was interrupted in " + workDir, e);
        }
    }

    private static String tail(List<String> lines) {
        final int from = Math.max(0, lines.size() - TAIL_LINES);
        return String.join("\n", lines.subList(from, lines.size()));
    }
}

package io.github.mridang.codegen.generators;

import com.google.common.base.Splitter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a language's source formatter inside a Docker container, limited to a
 * few concurrent containers per JVM and failing loudly on any error.
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
 * <p>This helper removes both causes. {@link #run} holds one of {@link #PARALLELISM}
 * slots so only a bounded number of formatter containers is ever active, and it throws on a
 * non-zero exit or any execution failure so a formatting failure can never be
 * silently swallowed. Generation therefore either produces fully formatted,
 * reproducible output or fails with the captured container log.
 */
final class DockerFormatter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerFormatter.class);

    /**
     * How many formatter containers may run at once in this JVM. Formatters share a
     * cache volume for their downloads, so a few can run side by side; the cap keeps a
     * cold cache from turning into a dozen simultaneous downloads. Override with
     * {@code -Dformatter.parallelism=N}; {@code 1} restores strict serialization.
     */
    static final int PARALLELISM = Math.max(1, Integer.getInteger("formatter.parallelism", 4));

    private static final Semaphore SLOTS = new Semaphore(PARALLELISM);

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
     * with {@code workDir} as the working directory, limited to
     * {@link #PARALLELISM} concurrent containers and retried up to {@link #MAX_ATTEMPTS} times. Throws if the
     * container still exits non-zero, or cannot be run, after the final attempt.
     *
     * @param dockerImage the formatter image, for diagnostics
     * @param workDir the generated-output directory bind-mounted into the
     *     container
     * @param dockerCmd the complete process command line
     */
    static void run(String dockerImage, String workDir, List<String> dockerCmd) {
        if (skipFormatting()) {
            LOGGER.warn(
                    "Skipping {} formatting in {} — docker is unavailable or"
                            + " SKIP_DOCKER_FORMAT is set; generated output is left"
                            + " unformatted.",
                    dockerImage,
                    workDir);
            return;
        }
        inSlot(
                () -> {
                    runWithRetries(dockerImage, workDir, dockerCmd);
                    return null;
                });
    }

    /** Runs {@code action} once one of the {@link #PARALLELISM} formatter slots is free. */
    static <T> T inSlot(Supplier<T> action) {
        SLOTS.acquireUninterruptibly();
        try {
            return action.get();
        } finally {
            SLOTS.release();
        }
    }

    private static void runWithRetries(String dockerImage, String workDir, List<String> dockerCmd) {
        {
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

    /**
     * Whether to skip Docker-based formatting. True when {@code
     * SKIP_DOCKER_FORMAT} is set (explicit control) or when the {@code docker}
     * executable is not on {@code PATH} (e.g. the generator itself is running
     * inside a container with no docker-in-docker). When docker IS present, a
     * formatter failure still surfaces loudly per {@link #run}.
     */
    private static boolean skipFormatting() {
        if (Boolean.parseBoolean(System.getenv().getOrDefault("SKIP_DOCKER_FORMAT", "false"))) {
            return true;
        }
        return !dockerAvailable();
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification =
                    "Scans the trusted process PATH for the docker executable; not user input")
    private static boolean dockerAvailable() {
        final String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : Splitter.on(File.pathSeparatorChar).split(path)) {
            if (!dir.isEmpty() && new File(dir, "docker").canExecute()) {
                return true;
            }
        }
        return false;
    }

    private static String tail(List<String> lines) {
        final int from = Math.max(0, lines.size() - TAIL_LINES);
        return String.join("\n", lines.subList(from, lines.size()));
    }
}

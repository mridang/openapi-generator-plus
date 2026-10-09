package io.github.mridang.codegen.spec.kotlin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-kotlin")
public class KotlinFormattingSpec extends AbstractFormattingSpec implements KotlinSpec {

    @Override
    protected String[] getBuildCommands() {
        /* Use the project's own ktlint Gradle plugin
         * (org.jlleitschuh.gradle.ktlint, pinned to ktlint 1.5.0 in
         * build.gradle.kts with ignoreFailures=false) rather than
         * curl-downloading the ktlint binary from GitHub releases. The
         * plugin already lints the same source on every build, and the
         * separate download was both redundant and a network-flake source. */
        return new String[] {"gradle ktlintCheck"};
    }

    @Override
    protected String getFileExtension() {
        return ".kt";
    }

    @Override
    protected Path getSourceRoot() {
        return tempOutputDir.resolve("src");
    }

    @Override
    @Nullable
    protected String getInlineCommentPattern() {
        return null;
    }

    @Test
    void generatedCodeShouldBeProperlyFormatted() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Kotlin code is not properly formatted:\n%s", result.output())
                .isTrue();
    }
}

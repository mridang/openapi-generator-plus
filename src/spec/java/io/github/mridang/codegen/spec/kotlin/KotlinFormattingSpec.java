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
        return new String[] {
            "curl -sL -o /tmp/ktlint https://github.com/pinterest/ktlint/releases/download/1.5.0/ktlint && chmod +x /tmp/ktlint",
            "/tmp/ktlint --relative 'src/**/*.kt'"
        };
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

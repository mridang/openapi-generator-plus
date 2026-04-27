package io.github.mridang.codegen.spec.rust;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-rust")
public class RustFormattingSpec extends AbstractFormattingSpec implements RustSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"rustup component add rustfmt", "cargo fmt -- --check"};
    }

    @Override
    protected String getFileExtension() {
        return ".rs";
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
                        "Generated Rust code is not properly formatted:\n%s", result.output())
                .isTrue();
    }
}

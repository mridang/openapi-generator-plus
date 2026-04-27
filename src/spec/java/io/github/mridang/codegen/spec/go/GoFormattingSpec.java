package io.github.mridang.codegen.spec.go;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-go")
public class GoFormattingSpec extends AbstractFormattingSpec implements GoSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"test -z \"$(gofmt -l .)\""};
    }

    @Override
    protected String getFileExtension() {
        return ".go";
    }

    @Override
    protected Path getSourceRoot() {
        return tempOutputDir;
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
                        "Generated Go code is not properly formatted:\n%s", result.output())
                .isTrue();
    }
}

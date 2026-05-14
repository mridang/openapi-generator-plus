package io.github.mridang.codegen.spec.swift;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-swift")
public class SwiftFormattingSpec extends AbstractFormattingSpec implements SwiftSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"swift-format lint --recursive Sources Tests"};
    }

    @Override
    protected String getFileExtension() {
        return ".swift";
    }

    @Override
    protected Path getSourceRoot() {
        return tempOutputDir.resolve("Sources");
    }

    @Override
    protected String getInlineCommentPattern() {
        return "^\\s*//($|[^/])";
    }

    @Override
    protected boolean skipFileHeaderComments() {
        return true;
    }

    @Test
    void generatedCodeShouldBeProperlyFormatted() {
        ExecResult result = executeInRuntimeContainer(getBuildCommands());

        assertThat(result.isSuccess())
                .withFailMessage(
                        "Generated Swift code is not properly formatted:\n%s", result.output())
                .isTrue();
    }
}

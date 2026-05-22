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

    @Test
    void pluralExamplesShouldBeAttributedToParameter() throws java.io.IOException {
        Path petApi = tempOutputDir.resolve("Sources/PetstoreClient/Api/PetApi.swift");
        String src = java.nio.file.Files.readString(petApi);
        assertThat(src)
                .as("swift PetApi.swift should attribute plural examples to their parameter")
                .contains("/// ### `petId` — Small breed ID")
                .contains("/// 1")
                .contains("/// ### `petId` — Large breed ID")
                .contains("/// 42");
    }
}

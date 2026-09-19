package io.github.mridang.codegen.spec.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-elixir")
public class ElixirFormattingSpec extends AbstractFormattingSpec implements ElixirSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"mix format --check-formatted"};
    }

    @Override
    protected String getFileExtension() {
        return ".ex";
    }

    @Override
    protected Path getSourceRoot() {
        return tempOutputDir.resolve("lib");
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
                        "Generated Elixir code is not properly formatted:\n%s", result.output())
                .isTrue();
    }

    @Test
    void pluralExamplesShouldBeAttributedToParameter() throws java.io.IOException {
        Path petApi = tempOutputDir.resolve("lib/petstore_client/api/pet_api.ex");
        String src = java.nio.file.Files.readString(petApi);
        assertThat(src)
                .as("elixir pet_api.ex should attribute plural examples to their parameter")
                .contains("### `pet_id` example — Small breed ID")
                .contains("### `pet_id` example — Large breed ID");
    }
}

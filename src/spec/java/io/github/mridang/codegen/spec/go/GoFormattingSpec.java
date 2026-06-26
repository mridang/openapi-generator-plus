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

    @Test
    void pluralExamplesShouldBeAttributedToParameter() throws java.io.IOException {
        Path petApi = tempOutputDir.resolve("pkg/pet_api.go");
        String src = java.nio.file.Files.readString(petApi);
        assertThat(src)
                .as("go pet_api.go should attribute plural examples to their parameter")
                .contains("// Example for `petId` — Small breed ID: 1")
                .contains("// Example for `petId` — Large breed ID: 42");
    }

    @Test
    void preSeedDefaultCommentOnlyWhenAnOptionalDefaultExists() throws java.io.IOException {
        // A model with no required fields and at least one optional field that
        // declares a schema default (Defaults: retries/mode/label) must keep the
        // "pre-seed optional fields" comment because the UnmarshalJSON body
        // actually seeds those defaults.
        String withDefaults =
                java.nio.file.Files.readString(
                        tempOutputDir.resolve("pkg/models/defaults.go"));
        assertThat(withDefaults)
                .as("defaults.go pre-seeds optional defaults, so it keeps the explanatory comment")
                .contains("pre-seed optional fields that declare a schema")
                .contains("aux.Retries = &defaultRetries");

        // A model with no required fields and no optional field carrying a
        // default (ApiResponse: code/type/message) must NOT emit the comment,
        // because there is nothing to pre-seed — emitting it would be misleading.
        String withoutDefaults =
                java.nio.file.Files.readString(
                        tempOutputDir.resolve("pkg/models/api_response.go"));
        assertThat(withoutDefaults)
                .as("api_response.go has no optional defaults, so the pre-seed comment is omitted")
                .doesNotContain("pre-seed optional fields that declare a schema");
    }
}

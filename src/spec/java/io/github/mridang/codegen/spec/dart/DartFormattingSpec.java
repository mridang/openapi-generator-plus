package io.github.mridang.codegen.spec.dart;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-dart")
public class DartFormattingSpec extends AbstractFormattingSpec implements DartSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {"dart pub get", "dart format --set-exit-if-changed ."};
    }

    @Override
    protected String getFileExtension() {
        return ".dart";
    }

    @Override
    protected Path getSourceRoot() {
        return tempOutputDir.resolve("lib");
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
                        "Generated Dart code is not properly formatted:\n%s", result.output())
                .isTrue();
    }
}

package io.github.mridang.codegen.spec.swift;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-swift")
public class SwiftClientSpec extends AbstractClientSpec implements SwiftSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {
            "mkdir -p .out/reports",
            "swift test --build-system swiftbuild --enable-code-coverage --xunit-output .out/reports/junit.xml"
        };
    }

    @Override
    protected void assertGeneratedStructure(Path outputDir) {
        assertThat(outputDir.resolve("Package.swift")).exists();
        assertThat(outputDir.resolve("Sources")).isDirectory();
        assertThat(outputDir.resolve("Tests")).isDirectory();
    }
}

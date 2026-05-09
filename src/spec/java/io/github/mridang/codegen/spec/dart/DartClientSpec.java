package io.github.mridang.codegen.spec.dart;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-dart")
public class DartClientSpec extends AbstractClientSpec implements DartSpec {

    @Override
    protected String[] getBuildCommands() {
        return new String[] {
            "dart pub get",
            "mkdir -p .out/reports",
            "dart test --coverage=.out/coverage --reporter json | dart run junitreport:tojunit --output .out/reports/junit.xml",
            "dart run coverage:format_coverage --lcov --in=.out/coverage --out=.out/coverage.xml --report-on=lib"
        };
    }

    @Override
    protected void assertGeneratedStructure(Path outputDir) {
        assertThat(outputDir.resolve("pubspec.yaml")).exists();
        assertThat(outputDir.resolve("lib/src/api")).isDirectory();
        assertThat(outputDir.resolve("lib/src/models")).isDirectory();
        assertThat(outputDir.resolve("lib/src/errors")).isDirectory();
        assertThat(outputDir.resolve("lib/src/auth")).isDirectory();
    }
}

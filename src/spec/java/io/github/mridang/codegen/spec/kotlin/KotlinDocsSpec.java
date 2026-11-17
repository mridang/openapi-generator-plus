package io.github.mridang.codegen.spec.kotlin;

import io.github.mridang.codegen.spec.AbstractDocsSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated Kotlin SDK's {@code make docs} target builds the Dokka documentation
 * with {@code failOnWarning} enabled, so an undocumented public member or a broken KDoc link fails
 * the build, and that it writes the documentation. If this test fails, the Kotlin templates need to
 * be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-kotlin")
public class KotlinDocsSpec extends AbstractDocsSpec implements KotlinSpec {

    @Override
    protected String[] getDocsCommands() {
        return new String[] {"make docs"};
    }

    @Override
    protected String getDocsOutputDirectory() {
        return ".out/docs";
    }
}

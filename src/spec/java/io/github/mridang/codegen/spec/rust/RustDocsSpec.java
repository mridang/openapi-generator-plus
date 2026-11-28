package io.github.mridang.codegen.spec.rust;

import io.github.mridang.codegen.spec.AbstractDocsSpec;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated Rust SDK's {@code make docs} target builds the rustdoc with warnings
 * treated as errors, and that it writes the documentation. If this test fails, the Rust templates
 * need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-rust")
public class RustDocsSpec extends AbstractDocsSpec implements RustSpec {

    @Override
    protected String[] getDocsCommands() {
        return new String[] {"make docs"};
    }

    @Override
    protected String getDocsOutputDirectory() {
        return ".out/docs";
    }
}

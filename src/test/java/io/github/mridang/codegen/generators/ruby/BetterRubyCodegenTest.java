package io.github.mridang.codegen.generators.ruby;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

class BetterRubyCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("ruby-plus", Paths.get("src/test/java/io/github/mridang/codegen/generators/ruby/.out"));
    }
}

package io.github.mridang.codegen.generators.java;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

class BetterJavaCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("java-plus", Paths.get("src/test/java/io/github/mridang/codegen/generators/java/.out"));
    }
}

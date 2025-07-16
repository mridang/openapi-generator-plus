package io.github.mridang.codegen.generators.node;

import io.github.mridang.codegen.generators.AbstractBetterCodegenTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

class BetterNodeCodegenTest extends AbstractBetterCodegenTest {

    @Test
    public void testGeneratedAuthClassesJersey() {
        super.doGenerate("node-plus", Paths.get("src/test/java/io/github/mridang/codegen/generators/node/.out"));
    }
}

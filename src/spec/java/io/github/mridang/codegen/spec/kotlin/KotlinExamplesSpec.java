package io.github.mridang.codegen.spec.kotlin;

import io.github.mridang.codegen.spec.AbstractExamplesSpec;
import io.github.mridang.codegen.spec.fences.Fence;
import io.github.mridang.codegen.spec.fences.HarnessFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Builds the Kotlin quickstart, runs it against the mock server and compiles every {@code kotlin}
 * fence of the README and SKILLS files. Each fence is a file in a package of its own under
 * {@code src/spec/kotlin/fences}, a source root of the jvmTest compilation, so it compiles against
 * the SDK and its dependencies: a {@code file} fence is the body of the file after the package
 * clause, a {@code statements} fence has its imports hoisted above a function {@code fence()} that
 * holds the rest. The function is not {@code suspend}, so a fence that calls an operation must
 * open its own coroutine. The working copy is restored before every command batch, so the files
 * never outlive the build.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-kotlin")
public class KotlinExamplesSpec extends AbstractExamplesSpec implements KotlinSpec {

    @Override
    protected String getFenceLanguage() {
        return "kotlin";
    }

    @Override
    protected List<HarnessFile> wrap(List<Fence> fences) {
        List<HarnessFile> files = new ArrayList<>();
        for (Fence fence : fences) {
            String name = fenceName(fence);
            String path = "src/spec/kotlin/fences/" + name + "/Fence.kt";
            String header = "package fences." + name + "\n\n";
            if (fence.kind().equals("file")) {
                files.add(new HarnessFile(path, header + fence.body()));
                continue;
            }
            StringBuilder imports = new StringBuilder();
            StringBuilder code = new StringBuilder();
            for (String line : fence.body().split("\n", -1)) {
                if (line.startsWith("import ")) {
                    imports.append(line).append('\n');
                } else {
                    code.append(line.isEmpty() ? "" : "    ").append(line).append('\n');
                }
            }
            files.add(new HarnessFile(path, header + imports + "\nfun fence() {\n" + code + "}\n"));
        }
        return files;
    }

    @Override
    protected String[] getFenceBuildCommands() {
        return new String[] {"gradle compileTestKotlinJvm --console=plain"};
    }
}

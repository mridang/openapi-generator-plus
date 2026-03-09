package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated Java code passes SpotBugs static analysis.
 * If this test fails, the Java templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class JavaStaticAnalysisSpec extends AbstractIntegrationSpec {

  private static final String PACKAGE_NAME = "com.example.petstore";
  private static final Path TEST_PROJECT_PATH =
      Paths.get("src/spec/resources/testprojects/javatest");

  @Override
  protected String getGeneratorName() {
    return "java-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("maven:3.9-eclipse-temurin-21");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "mvn compile dependency:copy-dependencies -DoutputDirectory=target/lib -q -B",
      "curl -sL -o /tmp/spotbugs.tgz"
          + " https://github.com/spotbugs/spotbugs/releases/download/4.8.6/spotbugs-4.8.6.tgz"
          + " && tar xzf /tmp/spotbugs.tgz -C /tmp/"
          + " && chmod +x /tmp/spotbugs-4.8.6/bin/spotbugs",
      "/tmp/spotbugs-4.8.6/bin/spotbugs -textui -effort:default -low"
          + " -exclude spotbugs-exclude.xml"
          + " -auxclasspath \"$(find target/lib -name '*.jar' | tr '\\n' ':')\""
          + " target/classes"
    };
  }

  @BeforeEach
  void copyTestProject() throws IOException {
    if (!Files.exists(TEST_PROJECT_PATH)) {
      throw new IllegalStateException(
          "Could not find test project at: " + TEST_PROJECT_PATH.toAbsolutePath());
    }
    copyDirectory(TEST_PROJECT_PATH, tempOutputDir);
  }

  @Test
  void generatedCodeShouldPassStaticAnalysis() throws IOException {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.MODEL_PACKAGE, PACKAGE_NAME + ".models",
            CodegenConstants.API_PACKAGE, PACKAGE_NAME + ".api",
            CodegenConstants.INVOKER_PACKAGE, PACKAGE_NAME),
        tempOutputDir);

    Files.writeString(
        tempOutputDir.resolve("spotbugs-exclude.xml"),
        String.join(
            "\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<FindBugsFilter>",
            "  <Match>",
            "    <Bug pattern=\"EI_EXPOSE_REP,EI_EXPOSE_REP2\" />",
            "  </Match>",
            "  <Match>",
            "    <Bug pattern=\"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD\" />",
            "  </Match>",
            "</FindBugsFilter>",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Java code has SpotBugs violations:\n%s", result.output())
        .isTrue();
  }
}

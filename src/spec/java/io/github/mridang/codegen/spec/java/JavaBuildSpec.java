package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated Java code compiles cleanly under Error Prone and NullAway with zero
 * warnings. Uses failOnWarning and -Xlint:all with no package exclusions. If this test fails, the
 * Java templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class JavaBuildSpec extends AbstractIntegrationSpec {

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
    return new String[] {"mvn compile -B"};
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @BeforeEach
  void copyTestProject() throws IOException {
    if (!Files.exists(TEST_PROJECT_PATH)) {
      throw new IllegalStateException(
          "Could not find test project at: " + TEST_PROJECT_PATH.toAbsolutePath());
    }
    copyDirectory(TEST_PROJECT_PATH, tempOutputDir);
  }

  private void copyDirectory(
      @SuppressWarnings("SameParameterValue") Path source, Path target) throws IOException {
    try (Stream<Path> stream = Files.walk(source)) {
      stream.forEach(
          sourcePath -> {
            try {
              Path targetPath = target.resolve(source.relativize(sourcePath));
              if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(targetPath);
              } else {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
              }
            } catch (IOException e) {
              throw new RuntimeException("Failed to copy " + sourcePath, e);
            }
          });
    }
  }

  @Test
  void generatedCodeShouldCompileWithErrorProne() throws IOException {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.MODEL_PACKAGE, PACKAGE_NAME + ".models",
            CodegenConstants.API_PACKAGE, PACKAGE_NAME + ".api",
            CodegenConstants.INVOKER_PACKAGE, PACKAGE_NAME),
        tempOutputDir);

    Files.writeString(
        tempOutputDir.resolve("pom.xml"),
        String.join(
            "\n",
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"",
            "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"",
            "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0"
                + " http://maven.apache.org/maven-v4_0_0.xsd\">",
            "  <modelVersion>4.0.0</modelVersion>",
            "  <groupId>org.openapitools</groupId>",
            "  <artifactId>openapi-java-client</artifactId>",
            "  <packaging>jar</packaging>",
            "  <version>1.0.0</version>",
            "",
            "  <build>",
            "    <plugins>",
            "      <plugin>",
            "        <groupId>org.apache.maven.plugins</groupId>",
            "        <artifactId>maven-compiler-plugin</artifactId>",
            "        <version>3.14.0</version>",
            "        <configuration>",
            "          <release>17</release>",
            "          <fork>true</fork>",
            "          <failOnWarning>true</failOnWarning>",
            "          <compilerArgs>",
            "            <arg>-XDcompilePolicy=simple</arg>",
            "            <arg>--should-stop=ifError=FLOW</arg>",
            "            <arg>-Xlint:all</arg>",
            "            <arg>-Xplugin:ErrorProne"
                + " -Xep:NullAway:ERROR"
                + " -XepOpt:NullAway:AnnotatedPackages=com.example.petstore</arg>",
            "            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED</arg>",
            "            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED</arg>",
            "            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED</arg>",
            "            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED</arg>",
            "            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED</arg>",
            "            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED</arg>",
            "            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>",
            "            <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED</arg>",
            "            <arg>-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED</arg>",
            "            <arg>-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED</arg>",
            "            <arg>-J-Xss4m</arg>",
            "          </compilerArgs>",
            "          <annotationProcessorPaths>",
            "            <path>",
            "              <groupId>com.google.errorprone</groupId>",
            "              <artifactId>error_prone_core</artifactId>",
            "              <version>2.40.0</version>",
            "            </path>",
            "            <path>",
            "              <groupId>com.uber.nullaway</groupId>",
            "              <artifactId>nullaway</artifactId>",
            "              <version>0.12.7</version>",
            "            </path>",
            "          </annotationProcessorPaths>",
            "        </configuration>",
            "      </plugin>",
            "    </plugins>",
            "  </build>",
            "",
            "  <dependencies>",
            "    <dependency>",
            "      <groupId>com.google.code.findbugs</groupId>",
            "      <artifactId>jsr305</artifactId>",
            "      <version>3.0.2</version>",
            "    </dependency>",
            "    <dependency>",
            "      <groupId>org.apache.httpcomponents.client5</groupId>",
            "      <artifactId>httpclient5</artifactId>",
            "      <version>${httpclient-version}</version>",
            "    </dependency>",
            "    <dependency>",
            "      <groupId>com.fasterxml.jackson.core</groupId>",
            "      <artifactId>jackson-core</artifactId>",
            "      <version>${jackson-version}</version>",
            "    </dependency>",
            "    <dependency>",
            "      <groupId>com.fasterxml.jackson.core</groupId>",
            "      <artifactId>jackson-annotations</artifactId>",
            "      <version>${jackson-version}</version>",
            "    </dependency>",
            "    <dependency>",
            "      <groupId>com.fasterxml.jackson.core</groupId>",
            "      <artifactId>jackson-databind</artifactId>",
            "      <version>${jackson-databind-version}</version>",
            "    </dependency>",
            "    <dependency>",
            "      <groupId>com.fasterxml.jackson.jaxrs</groupId>",
            "      <artifactId>jackson-jaxrs-json-provider</artifactId>",
            "      <version>${jackson-version}</version>",
            "    </dependency>",
            "    <dependency>",
            "      <groupId>com.fasterxml.jackson.datatype</groupId>",
            "      <artifactId>jackson-datatype-jsr310</artifactId>",
            "      <version>${jackson-version}</version>",
            "    </dependency>",
            "    <dependency>",
            "      <groupId>org.openapitools</groupId>",
            "      <artifactId>jackson-databind-nullable</artifactId>",
            "      <version>${jackson-databind-nullable-version}</version>",
            "    </dependency>",
            "    <dependency>",
            "      <groupId>jakarta.annotation</groupId>",
            "      <artifactId>jakarta.annotation-api</artifactId>",
            "      <version>${jakarta-annotation-version}</version>",
            "      <scope>provided</scope>",
            "    </dependency>",
            "  </dependencies>",
            "",
            "  <properties>",
            "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>",
            "    <httpclient-version>5.2.1</httpclient-version>",
            "    <jackson-version>2.17.1</jackson-version>",
            "    <jackson-databind-version>2.17.1</jackson-databind-version>",
            "    <jackson-databind-nullable-version>0.2.6</jackson-databind-nullable-version>",
            "    <jakarta-annotation-version>1.3.5</jakarta-annotation-version>",
            "  </properties>",
            "</project>",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated Java code has Error Prone/NullAway violations:\n%s", result.output())
        .isTrue();
  }

  private void generateClientToDirectory(
      Map<String, Object> additionalProperties, Path outputDir) {
    URL specUrl = getClass().getClassLoader().getResource(getSpecResourcePath());
    if (specUrl == null) {
      throw new IllegalStateException("Could not find spec resource: " + getSpecResourcePath());
    }

    String specPath = specUrl.getPath();

    org.openapitools.codegen.config.CodegenConfigurator configurator =
        new org.openapitools.codegen.config.CodegenConfigurator()
            .setGeneratorName(getGeneratorName())
            .setInputSpec(specPath)
            .setOutputDir(outputDir.toString().replace("\\", "/"))
            .setAdditionalProperties(additionalProperties);

    org.openapitools.codegen.DefaultGenerator generator =
        new org.openapitools.codegen.DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();
  }
}

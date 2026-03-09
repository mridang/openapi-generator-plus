package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated PHP code is already modern according to Rector.
 * Rector in --dry-run mode exits 0 if no changes are needed (code is already modern).
 * If this test fails, the PHP templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PhpModernizationSpec extends AbstractIntegrationSpec {

  @Override
  protected String getGeneratorName() {
    return "php-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("composer:2");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "cd /app"
          + " && composer require --quiet --no-interaction"
          + " guzzlehttp/guzzle:^7.3"
          + " guzzlehttp/psr7:^2.0"
          + " symfony/serializer:^7.0"
          + " symfony/property-access:^7.0"
          + " symfony/property-info:^7.0"
          + " phpdocumentor/reflection-docblock:^5.3"
          + " && composer require --dev --quiet --no-interaction rector/rector"
          + " && composer dump-autoload",
      "vendor/bin/rector process lib/ --dry-run"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @Test
  void generatedCodeShouldBeModern() throws IOException {
    generateClientToDirectory(
        Map.of(CodegenConstants.INVOKER_PACKAGE, "PetstoreClient"), tempOutputDir);

    // Write composer.json with PSR-4 autoloading
    Files.writeString(
        tempOutputDir.resolve("composer.json"),
        String.join(
            "\n",
            "{",
            "  \"autoload\": {",
            "    \"psr-4\": {",
            "      \"PetstoreClient\\\\\": \"lib/\"",
            "    }",
            "  }",
            "}",
            ""));

    // Write Rector config
    Files.writeString(
        tempOutputDir.resolve("rector.php"),
        String.join(
            "\n",
            "<?php",
            "",
            "declare(strict_types=1);",
            "",
            "use Rector\\Config\\RectorConfig;",
            "use Rector\\Set\\ValueObject\\SetList;",
            "",
            "return RectorConfig::configure()",
            "    ->withPaths([__DIR__ . '/lib'])",
            "    ->withSets([",
            "        SetList::CODE_QUALITY,",
            "        SetList::DEAD_CODE,",
            "        SetList::EARLY_RETURN,",
            "        SetList::TYPE_DECLARATION,",
            "    ])",
            "    ->withSkip([",
            "        // PHPDoc tags are kept for IDE support even when typed",
            "        \\Rector\\DeadCode\\Rector\\ClassMethod\\RemoveUselessReturnTagRector::class,",
            "        \\Rector\\DeadCode\\Rector\\ClassMethod\\RemoveUselessParamTagRector::class,",
            "        \\Rector\\DeadCode\\Rector\\Property\\RemoveUselessVarTagRector::class,",
            "        // Generated code uses null checks, not instanceof",
            "        \\Rector\\CodeQuality\\Rector\\Identical\\FlipTypeControlToUseExclusiveTypeRector::class,",
            "        // Template uses concatenation for path parameter substitution",
            "        \\Rector\\CodeQuality\\Rector\\Concat\\JoinStringConcatRector::class,",
            "        // API subclass constructors delegate intentionally for extensibility",
            "        \\Rector\\DeadCode\\Rector\\ClassMethod\\RemoveParentDelegatingConstructorRector::class,",
            "        // Return types are declared via PHPDoc for generics support",
            "        \\Rector\\TypeDeclaration\\Rector\\ClassMethod\\ReturnTypeFromStrictTypedCallRector::class,",
            "        // Early return patterns are kept for readability in generated code",
            "        \\Rector\\EarlyReturn\\Rector\\If_\\RemoveAlwaysElseRector::class,",
            "        // empty() is used intentionally for falsy checks",
            "        \\Rector\\Strict\\Rector\\Empty_\\DisallowedEmptyRuleFixerRector::class,",
            "        // Explicit bool comparisons differ from template style",
            "        \\Rector\\CodeQuality\\Rector\\If_\\ExplicitBoolCompareRector::class,",
            "        // settype() is used intentionally in deserializer",
            "        \\Rector\\CodeQuality\\Rector\\FuncCall\\SetTypeToCastRector::class,",
            "        // Closure types are not added for brevity in templates",
            "        \\Rector\\TypeDeclaration\\Rector\\ArrowFunction\\AddArrowFunctionReturnTypeRector::class,",
            "        \\Rector\\TypeDeclaration\\Rector\\Closure\\AddClosureVoidReturnTypeWhereNoReturnRector::class,",
            "        // Null args on default params kept for clarity",
            "        \\Rector\\DeadCode\\Rector\\MethodCall\\RemoveNullArgOnNullDefaultParamRector::class,",
            "        // Useless variable simplification kept for readability",
            "        \\Rector\\DeadCode\\Rector\\Return_\\RemoveDeadConditionAboveReturnRector::class,",
            "        \\Rector\\DeadCode\\Rector\\Assign\\RemoveUnusedVariableAssignRector::class,",
            "        // Unused constants may be used by consumers",
            "        \\Rector\\DeadCode\\Rector\\ClassConst\\RemoveUnusedPrivateClassConstantRector::class,",
            "    ]);",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated PHP code has modernization suggestions:\n%s", result.output())
        .isTrue();
  }

  private void generateClientToDirectory(
      Map<String, Object> additionalProperties, java.nio.file.Path outputDir) {
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

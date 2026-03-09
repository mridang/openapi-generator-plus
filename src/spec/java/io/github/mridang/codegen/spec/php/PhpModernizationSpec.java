package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
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
      "composer install --no-interaction --prefer-dist",
      "vendor/bin/rector process lib/ --dry-run"
    };
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
            "        // Mustache template engine cannot produce '{petId}' directly",
            "        \\Rector\\CodeQuality\\Rector\\Concat\\JoinStringConcatRector::class,",
            "        // Temp variables needed for PHPStan @var type assertions",
            "        \\Rector\\DeadCode\\Rector\\Assign\\RemoveUnusedVariableAssignRector::class,",
            "    ]);",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated PHP code has modernization suggestions:\n%s", result.output())
        .isTrue();
  }
}

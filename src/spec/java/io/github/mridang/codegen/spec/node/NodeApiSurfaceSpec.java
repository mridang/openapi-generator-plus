package io.github.mridang.codegen.spec.node;

import io.github.mridang.codegen.spec.AbstractApiSurfaceSpec;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The public API guard of the generated Node SDK (D180): a script run against the TypeScript
 * compiler the SDK's dev dependencies already install lists every declaration the package's
 * entry points export, one signature per line, and marks each type of another package a
 * signature names. If this test fails on the snapshot, the public API changed; if it fails on a
 * leak, a public signature names a type of decimal.js, undici, class-transformer, OpenTelemetry
 * or another dependency.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-node")
public class NodeApiSurfaceSpec extends AbstractApiSurfaceSpec implements NodeSpec {

  @Override
  protected List<String> getExtractorScripts() {
    return List.of("node-surface.cjs");
  }

  @Override
  protected String[] getSurfaceCommands() {
    return new String[] {"node /tmp/api-surface/node-surface.cjs"};
  }

  @Override
  protected List<Pattern> getThirdPartyPatterns() {
    /* The extractor ends a signature with "[uses <package>:<Type>, ...]" for every type of
     * another package it names, and lists a re-exported declaration as "export <name> from
     * <package>". */
    String deps =
        "(decimal\\.js|temporal-polyfill|temporal-spec|class-transformer|reflect-metadata"
            + "|undici|undici-types|@opentelemetry/[\\w.-]+)";
    return List.of(
        Pattern.compile("\\[uses (?:[^\\]]*, )?" + deps + ":"),
        Pattern.compile("^export \\S+ from " + deps + "$"));
  }
}

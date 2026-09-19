package io.github.mridang.codegen.spec.node;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class NodeReservedWordsSpec extends AbstractReservedWordsSpec implements NodeSpec {

  @Override
  public String getDockerImage() {
    return "node:24-slim";
  }

  @Override
  protected String getDumpScriptResource() {
    return "scripts/dump-node-keywords.sh";
  }

  @Override
  protected String getKeywordsSourceResource() {
    return "scripts/node_keywords.js";
  }

  @Override
  protected String getKeywordsSourceContainerPath() {
    return "/scripts/node_keywords.js";
  }

  @Override
  protected String getReservedWordsResource() {
    return "/reserved-words/node.txt";
  }
}

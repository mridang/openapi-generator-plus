package io.github.mridang.codegen.spec.java;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class JavaReservedWordsSpec extends AbstractReservedWordsSpec implements JavaSpec {

  @Override
  protected String getDumpScriptResource() {
    return "scripts/dump-java-keywords.sh";
  }

  @Override
  protected String getKeywordsSourceResource() {
    return "scripts/JavaKeywords.java";
  }

  @Override
  protected String getKeywordsSourceContainerPath() {
    return "/scripts/JavaKeywords.java";
  }

  @Override
  protected String getReservedWordsResource() {
    return "/reserved-words/java.txt";
  }
}

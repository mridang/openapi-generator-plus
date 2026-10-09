package io.github.mridang.codegen.spec.csharp;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class CSharpReservedWordsSpec extends AbstractReservedWordsSpec implements CSharpSpec {

  @Override
  protected String getDumpScriptResource() {
    return "scripts/dump-csharp-keywords.sh";
  }

  @Override
  protected String getKeywordsSourceResource() {
    return "scripts/CSharpKeywords.cs";
  }

  @Override
  protected String getKeywordsSourceContainerPath() {
    return "/scripts/CSharpKeywords.cs";
  }

  @Override
  protected String getReservedWordsResource() {
    return "/reserved-words/csharp.txt";
  }
}

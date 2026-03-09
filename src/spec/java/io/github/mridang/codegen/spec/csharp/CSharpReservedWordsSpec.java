package io.github.mridang.codegen.spec.csharp;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class CSharpReservedWordsSpec extends AbstractReservedWordsSpec {

  @Override
  protected String getDockerImage() {
    return "mcr.microsoft.com/dotnet/sdk:9.0";
  }

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

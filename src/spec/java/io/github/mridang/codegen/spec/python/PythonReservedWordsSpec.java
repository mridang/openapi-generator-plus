package io.github.mridang.codegen.spec.python;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class PythonReservedWordsSpec extends AbstractReservedWordsSpec implements PythonSpec {

  @Override
  protected String getDumpScriptResource() {
    return "scripts/dump-python-keywords.sh";
  }

  @Override
  protected String getKeywordsSourceResource() {
    return "scripts/python_keywords.py";
  }

  @Override
  protected String getKeywordsSourceContainerPath() {
    return "/scripts/python_keywords.py";
  }

  @Override
  protected String getReservedWordsResource() {
    return "/reserved-words/python.txt";
  }
}

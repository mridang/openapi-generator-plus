package io.github.mridang.codegen.spec.ruby;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class RubyReservedWordsSpec extends AbstractReservedWordsSpec {

  @Override
  protected String getDockerImage() {
    return "ruby:3.4-slim";
  }

  @Override
  protected String getDumpScriptResource() {
    return "scripts/dump-ruby-keywords.sh";
  }

  @Override
  protected String getKeywordsSourceResource() {
    return "scripts/ruby_keywords.rb";
  }

  @Override
  protected String getKeywordsSourceContainerPath() {
    return "/scripts/ruby_keywords.rb";
  }

  @Override
  protected String getReservedWordsResource() {
    return "/reserved-words/ruby.txt";
  }
}

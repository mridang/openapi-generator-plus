package io.github.mridang.codegen.spec.php;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class PhpReservedWordsSpec extends AbstractReservedWordsSpec {

  @Override
  protected String getDockerImage() {
    return "php:8.3-cli";
  }

  @Override
  protected String getDumpScriptResource() {
    return "scripts/dump-php-keywords.sh";
  }

  @Override
  protected String getKeywordsSourceResource() {
    return "scripts/php_keywords.php";
  }

  @Override
  protected String getKeywordsSourceContainerPath() {
    return "/scripts/php_keywords.php";
  }

  @Override
  protected String getReservedWordsResource() {
    return "/reserved-words/php.txt";
  }
}

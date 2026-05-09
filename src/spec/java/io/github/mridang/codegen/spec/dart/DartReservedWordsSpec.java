package io.github.mridang.codegen.spec.dart;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class DartReservedWordsSpec extends AbstractReservedWordsSpec implements DartSpec {

    @Override
    public String getDockerImage() {
        return "dart:stable";
    }

    @Override
    protected String getDumpScriptResource() {
        return "scripts/dump-dart-keywords.sh";
    }

    @Override
    protected String getKeywordsSourceResource() {
        return "scripts/dart_keywords.dart";
    }

    @Override
    protected String getKeywordsSourceContainerPath() {
        return "/scripts/dart_keywords.dart";
    }

    @Override
    protected String getReservedWordsResource() {
        return "/reserved-words/dart.txt";
    }
}

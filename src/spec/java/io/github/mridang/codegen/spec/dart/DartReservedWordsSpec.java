package io.github.mridang.codegen.spec.dart;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class DartReservedWordsSpec extends AbstractReservedWordsSpec implements DartSpec {

    @Override
    public String getDockerImage() {
        return "dart:stable@sha256:6440c7d5fd8713b0706d0b6190eb2be7ad896101e225fc9d7657034b23ab0592";
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

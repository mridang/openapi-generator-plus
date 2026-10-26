package io.github.mridang.codegen.spec.go;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class GoReservedWordsSpec extends AbstractReservedWordsSpec implements GoSpec {

    @Override
    protected String getDumpScriptResource() {
        return "scripts/dump-go-keywords.sh";
    }

    @Override
    protected String getKeywordsSourceResource() {
        return "scripts/go_keywords.go";
    }

    @Override
    protected String getKeywordsSourceContainerPath() {
        return "/scripts/go_keywords.go";
    }

    @Override
    protected String getReservedWordsResource() {
        return "/reserved-words/go.txt";
    }
}

package io.github.mridang.codegen.spec.kotlin;

import io.github.mridang.codegen.spec.AbstractReservedWordsSpec;

@SuppressWarnings("NewClassNamingConvention")
class KotlinReservedWordsSpec extends AbstractReservedWordsSpec implements KotlinSpec {

    @Override
    protected String getDumpScriptResource() {
        return "scripts/dump-kotlin-keywords.sh";
    }

    @Override
    protected String getKeywordsSourceResource() {
        return "scripts/kotlin_keywords.kts";
    }

    @Override
    protected String getKeywordsSourceContainerPath() {
        return "/scripts/kotlin_keywords.kts";
    }

    @Override
    protected String getReservedWordsResource() {
        return "/reserved-words/kotlin.txt";
    }
}
